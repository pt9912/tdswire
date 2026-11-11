package io.github.pt9912.tdswire.protocol.bulk

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.hex
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [BulkLoadReader] gegen die Nutzlast des Referenzclients `SqlBulkCopy` (`Microsoft.Data.SqlClient` 7.1.1, `SPEC-036`,
 * Form B): die 5 verschiedenen Nachrichten 0x07 der Messreihe M-8 bis M-12 (`referenztest-dotnet/Mitschnitt/messung/
 * bulk-merge-katalog/{2022,2025}/`, Dateien `bulk-typen-null`, `bulk-mehrere-nachrichten`, `bulk-verstoss-schluessel`;
 * die 10 aufgezeichneten Nachrichten beider Instanzen sind je Paar byte-gleich) und die Nachricht `typen-fest` der
 * Messreihe M-36 (`bulk-name-feste-typen`) als Hex-Dateien unter `src/test/resources/messung/bulk-referenzclient/`. Sie
 * enthalten die festen Typen BIGINT (`0x7f`), INT (`0x38`), SMALLINT (`0x34`), TINYINT (`0x30`) und BIT (`0x32`), zwei
 * Nullbyte hinter der Spaltenzahl und ein DONE von 8 Byte. Die Handvektoren in der Form von mssql-jdbc sind von Hand
 * nach der Beschreibung von `MS-TDS` gebaut, ohne Mitschnitt eines Clients.
 */
class BulkLoadReaderReferenceClientTest {
    private fun message(name: String): ByteArray =
        hex(requireNotNull(javaClass.getResource("/messung/bulk-referenzclient/$name.hex")).readText())

    private fun read(bytes: ByteArray): BulkRows =
        assertIs<ProtocolResult.Ok<BulkRows>>(BulkLoadReader.read(bytes)).value

    private fun failure(bytes: ByteArray): ProtocolFailure =
        assertIs<ProtocolResult.Failed>(BulkLoadReader.read(bytes)).error.failure

    /**
     * Die Datei `typen-fest.hex` ist die Nachricht 0x07 der Ablage `bulk-name-feste-typen` (`MessungBulkBelegTests` in
     * `referenztest-dotnet` hält dieselbe Hexfolge gegen beide Instanzen). Wächter: eine Änderung an der Datei, die der
     * Aussage „aufgezeichnet“ nicht mehr entspricht.
     *
     * @spec SPEC-036
     */
    @Test
    fun `typen-fest gleicht der aufgezeichneten Nachricht der Ablage`() {
        assertEquals(
            "810400000000000000080038014100000000000800340142000000000008003001430000000000080032014400d101000000" +
                "02000301d1fffffffffeffff00fd0000000000000000",
            HexFormat.of().formatHex(message("typen-fest")),
        )
    }

    /**
     * `bulk-typen-null`: neun Spalten (`Id` BIGINT fest, `Zahl` und `Klein` INTN, `Flag` BITN, `Preis` NUMERICN(10,2),
     * `Text` NVARCHAR, `Datum`, `Uhr`, `Zeitpunkt`), drei Zeilen: Werte, eine Zeile nur aus NULL (ohne `Id`) und
     * Randwerte
     * (-7, -1, 0). Wächter: ein Leser ohne den festen Typ (`INVALID_TYPE`), eine Spalte als `Int32` statt `Int64`, eine
     * Form, die die zwei Nullbyte oder die 8 Byte DONE nicht annimmt, ein NULL, das als Wert gelesen wird.
     *
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `bulk-typen-null ergibt neun Spalten und drei Zeilen`() {
        val loaded = read(message("typen-null"))
        assertEquals(
            listOf(
                ResultColumn("Id", SqlType.Int64, nullable = false),
                ResultColumn("Zahl", SqlType.Int32, nullable = true),
                ResultColumn("Klein", SqlType.Int32, nullable = true),
                ResultColumn("Flag", SqlType.Bool, nullable = true),
                ResultColumn("Preis", SqlType.Decimal(10, 2), nullable = true),
                ResultColumn("Text", SqlType.UnicodeText, nullable = true),
                ResultColumn("Datum", SqlType.Date, nullable = true),
                ResultColumn("Uhr", SqlType.Time, nullable = true),
                ResultColumn("Zeitpunkt", SqlType.DateTime, nullable = true),
            ),
            loaded.columns,
        )
        assertEquals(
            listOf(
                listOf(
                    1L, 10, 5, true, BigDecimal("12.34"), "Äpfel", LocalDate.of(2026, 1, 2), LocalTime.of(13, 14, 15),
                    LocalDateTime.of(2026, 1, 2, 13, 14, 15, 123_000_000),
                ),
                listOf(2L, null, null, null, null, null, null, null, null),
                listOf(
                    3L, -7, -1, false, BigDecimal("0.00"), "", LocalDate.of(1970, 1, 1), LocalTime.MIDNIGHT,
                    LocalDateTime.of(1970, 1, 1, 0, 0),
                ),
            ),
            loaded.rows,
        )
    }

    /**
     * `bulk-mehrere-nachrichten`: drei Nachrichten einer Quelle von 2500 Zeilen (1000, 1000 und 500), `Id` BIGINT fest,
     * `Text` NVARCHAR(20). Wächter: ein Leser, der eine Zeile verliert oder eine große Nachricht (bis 31 KB) abweist;
     * eine Id, die nicht aus dem festen Typ kommt.
     *
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `bulk-mehrere-nachrichten ergibt 1000, 1000 und 500 Zeilen`() {
        val expected = listOf(
            Triple("mehrere-1", 1L, 1000),
            Triple("mehrere-2", 1001L, 1000),
            Triple("mehrere-3", 2001L, 500),
        )
        for ((name, first, count) in expected) {
            val loaded = read(message(name))
            assertEquals(listOf("Id", "Text"), loaded.columns.map { it.name })
            assertEquals(count, loaded.rows.size, name)
            assertEquals(listOf(first, "zeile $first"), loaded.rows.first(), name)
            val last = first + count - 1
            assertEquals(listOf(last, "zeile $last"), loaded.rows.last(), name)
        }
    }

    /**
     * `bulk-verstoss-schluessel`: zwei Spalten (`Id` BIGINT fest, `Text` NVARCHAR(10)), drei Zeilen mit dem Schlüssel
     * 1, 2 und 3 (der Verstoß entsteht am Server, die Nachricht ist wohlgeformt). Wächter: eine Spalte, die nicht
     * `Int64` ist; eine Zeile weniger.
     *
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `bulk-verstoss-schluessel ergibt drei Zeilen`() {
        val loaded = read(message("verstoss-schluessel"))
        assertEquals(
            listOf(ResultColumn("Id", SqlType.Int64, false), ResultColumn("Text", SqlType.UnicodeText, true)),
            loaded.columns,
        )
        assertEquals(listOf(listOf(1L, "neu"), listOf(2L, "doppelt"), listOf(3L, "neu")), loaded.rows)
    }

    /**
     * `bulk-name-feste-typen`: vier nicht nullbare Spalten INT, SMALLINT, TINYINT und BIT (feste Typen `0x38`, `0x34`,
     * `0x30`, `0x32`, Messreihe M-36, Instanz 2022 und 2025 byte-gleich), zwei Zeilen mit den Randwerten (1, 2, 3, wahr
     * und -1, -2, 255, falsch). Wächter: ein TINYINT mit Vorzeichen (255 als -1), ein SMALLINT oder INT ohne
     * Vorzeichen, eine falsche Byte-Zahl eines Typs, eine Spalte, die nullbar gemeldet wird.
     *
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `bulk-name-feste-typen ergibt vier feste Spalten und zwei Zeilen`() {
        val loaded = read(message("typen-fest"))
        assertEquals(
            listOf(
                ResultColumn("A", SqlType.Int32, nullable = false),
                ResultColumn("B", SqlType.Int32, nullable = false),
                ResultColumn("C", SqlType.Int32, nullable = false),
                ResultColumn("D", SqlType.Bool, nullable = false),
            ),
            loaded.columns,
        )
        assertEquals(listOf(listOf(1, 2, 3, true), listOf(-1, -2, 255, false)), loaded.rows)
    }

    /**
     * Eine Nutzlast, die `SqlBulkCopy` an den Testserver sendet (ausgegeben vom Server in einem Lauf von `make
     * simulations-integration`, den `SqlBulkCopyTests` belegen; Tabelle `bulk_ziel` mit `Id` BIGINT, `Text`
     * NVARCHAR(10), `Zahl` INT, `Preis` DECIMAL(10,2), zwei Zeilen): kein Nullbyte hinter der Spaltenzahl, `NUMERICN`
     * mit Größe 17 und 17 Wertebytes, DONE mit 8 Byte. Wächter: ein Leser, der nur die Form mit den zwei Nullbyte oder
     * nur DONE mit 12 Byte annimmt.
     *
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `die Nutzlast des Referenzclients am Testserver ohne die zwei Nullbyte ergibt zwei Zeilen`() {
        val payload = "8104000000000008007f0249006400000000000900e714000904d0003404540065007800740000000000090026" +
            "04045a00610068006c000000000009006c110a020550007200650069007300d10a000000000000000200610004" +
            "01000000110164000000000000000000000000000000d10b000000000000000200620004020000001101c80000" +
            "00000000000000000000000000fd0000000000000000"
        val loaded = read(hex(payload))
        assertEquals(listOf("Id", "Text", "Zahl", "Preis"), loaded.columns.map { it.name })
        assertEquals(
            listOf(
                listOf(10L, "a", 1, BigDecimal("1.00")),
                listOf(11L, "b", 2, BigDecimal("2.00")),
            ),
            loaded.rows,
        )
    }

    /**
     * Jede echte Teilnachricht von `bulk-verstoss-schluessel` ist ein Fehler des Lesers und wirft nie. Wächter: eine
     * Form, die DONE nicht verlangt oder ein DONE mit weniger als 8 Byte annimmt.
     *
     * @spec SPEC-036
     * @spec TDS-QA-07
     */
    @Test
    fun `jede Teilnachricht der Aufzeichnung ist ein Fehler des Lesers`() {
        val full = message("verstoss-schluessel")
        for (length in 0 until full.size) {
            assertIs<ProtocolResult.Failed>(BulkLoadReader.read(full.copyOf(length)), "Länge $length")
        }
    }

    private fun fixedColumn(code: String, name: String) =
        "00000000 0000 $code 02 ${name[0].code.toString(16)} 00 ${name[1].code.toString(16)} 00 "

    /**
     * Handvektoren (nach `MS-TDS`, ohne Mitschnitt eines Clients) in der Form von mssql-jdbc (die Spalte folgt der
     * Spaltenzahl, DONE mit 12 Byte): die festen Typen TINYINT (`0x30`, ohne Vorzeichen), BIT (`0x32`), SMALLINT
     * (`0x34`),
     * INT (`0x38`) und BIGINT (`0x7f`), mit den Randwerten beider Richtungen. Wächter: ein TINYINT mit Vorzeichen (255
     * statt -1), ein SMALLINT oder INT ohne Vorzeichen, ein BIGINT als `Int`, eine falsche Byte-Zahl eines Typs.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die festen Typen TINYINT, BIT, SMALLINT, INT und BIGINT werden nach der Beschreibung gelesen`() {
        val columns = "81 0500 " + fixedColumn("30", "a1") + fixedColumn("32", "b2") + fixedColumn("34", "c3") +
            fixedColumn("38", "d4") + fixedColumn("7f", "e5")
        val rows = "d1 ff 01 feff 00000080 0000000000000080 " + "d1 00 00 0100 ffffff7f ffffffffffffffff "
        val parsed = read(hex(columns + rows + "fd 000000000000000000000000"))
        assertEquals(
            listOf(SqlType.Int32, SqlType.Bool, SqlType.Int32, SqlType.Int32, SqlType.Int64),
            parsed.columns.map { it.type },
        )
        assertEquals(
            listOf(
                listOf(255, true, -2, Int.MIN_VALUE, Long.MIN_VALUE),
                listOf(0, false, 1, Int.MAX_VALUE, -1L),
            ),
            parsed.rows,
        )
    }

    /**
     * Ein fester Typ, den der Leser nicht kennt (FLOAT `0x3e`), ist `INVALID_TYPE`; ebenso ein fester Typ, dessen
     * Wert abgeschnitten ist (`TRUNCATED`). Wächter: ein Leser, der jede Typkennung unter `0x80` als fest annimmt.
     *
     * @spec SPEC-036
     * @spec TDS-QA-07
     */
    @Test
    fun `ein unbekannter fester Typ und ein abgeschnittener fester Wert sind Fehler des Lesers`() {
        assertEquals(
            ProtocolFailure.INVALID_TYPE,
            failure(hex("81 0100 " + fixedColumn("3e", "a1") + "d1 0000000000000000 fd 000000000000000000000000")),
        )
        assertEquals(
            ProtocolFailure.TRUNCATED,
            failure(hex("81 0100 " + fixedColumn("38", "a1") + "d1 000000")),
        )
    }

    /**
     * In der Form mit den zwei Nullbyte hinter der Spaltenzahl müssen beide Bytes 0 sein: dieselbe Aufzeichnung
     * (`bulk-verstoss-schluessel`) mit einem Byte ungleich 0 an dieser Stelle ist ein Fehler des Lesers, in der Form
     * ohne
     * Nullbyte liest sich dieselbe Stelle als Benutzertyp und Flags und führt zu einer ungültigen Typkennung. Wächter:
     * eine Form, die die zwei Bytes nicht prüft und die Nachricht danach liest.
     *
     * @spec SPEC-036
     */
    @Test
    fun `zwei Bytes ungleich 0 hinter der Spaltenzahl sind ein Fehler des Lesers`() {
        for (offset in 3..4) {
            val damaged = message("verstoss-schluessel").also { it[offset] = 1 }
            assertEquals(ProtocolFailure.INVALID_TYPE, failure(damaged), "Offset $offset")
        }
    }
}
