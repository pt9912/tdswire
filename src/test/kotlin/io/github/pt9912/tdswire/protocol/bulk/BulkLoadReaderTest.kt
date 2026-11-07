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
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Der Leser der Nutzlast einer Nachricht BULK_LOAD (`SPEC-036`, Pakettypen) gegen Handvektoren. Quelle der Vektoren
 * [MITSCHNITT_DREI_ZEILEN] und [MITSCHNITT_LEER]: Mitschnitt von mssql-jdbc 13.4.0 (d-migrate 1.12.0,
 * `data import --table kunden`, Tabelle `kunden` mit `Id` BIGINT, `Name` NVARCHAR(4000), `Eintritt` DATE als
 * `VARCHAR(10)`) am Testserver, Nutzlast je Nachricht 0x07 als Hex vor dem Leser ausgegeben
 * (`docs/messungen/bulk-load-2026-11-07.md`, Abschnitt Aufzeichnung). Die übrigen Vektoren sind von Hand nach der
 * Beschreibung von `MS-TDS` (TYPE_INFO, Wertebytes, COLMETADATA, ROW, DONE) gebaut, ohne Mitschnitt eines Clients und
 * ohne Abschnittsnummer, weil das Dokument nicht im Repo liegt; die Tage seit
 * 0001-01-01 und die Ticks sind gerechnet. Die Meldungen des Lesers nennen nie einen Wert.
 */
class BulkLoadReaderTest {
    private fun read(vector: String): ProtocolResult<BulkRows> = BulkLoadReader.read(hex(vector))

    private fun rows(vector: String): BulkRows = assertIs<ProtocolResult.Ok<BulkRows>>(read(vector)).value

    private fun failure(vector: String) = assertIs<ProtocolResult.Failed>(read(vector)).error.failure

    /**
     * Der Mitschnitt mit drei Zeilen: Spaltennamen und Typen aus COLMETADATA (`Id` INTN 8 nicht nullbar, `Name`
     * NVARCHAR, `Eintritt` BIGVARCHAR als Text), Werte mit Umlauten (UTF-16), ein NULL je Textspalte, DONE am Ende.
     * Wächter: eine falsche Zeichencodierung, ein NULL, das als Text gelesen wird, eine Spalte, deren Typ nicht aus
     * dem COLMETADATA kommt, eine Zeile weniger oder mehr.
     *
     * @spec SPEC-036
     */
    @Test
    fun `der Mitschnitt von mssql-jdbc ergibt drei Zeilen mit den Spalten der Nachricht`() {
        val message = rows(MITSCHNITT_DREI_ZEILEN)
        assertEquals(
            listOf(
                ResultColumn("Id", SqlType.Int64, nullable = false),
                ResultColumn("Name", SqlType.UnicodeText, nullable = true),
                ResultColumn("Eintritt", SqlType.UnicodeText, nullable = true),
            ),
            message.columns,
        )
        assertEquals(
            listOf(
                listOf(1L, "Ada Ünï", "2026-01-02"),
                listOf(2L, null, null),
                listOf(3L, "Cäcilie", "2025-12-31"),
            ),
            message.rows,
        )
    }

    /**
     * Der Schlussteil jedes Imports (COLMETADATA, kein ROW, DONE) ergibt die Spalten und keine Zeile.
     * Wächter: ein Leser, der eine Nachricht ohne Zeile abweist.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Nachricht ohne Zeile ergibt Spalten ohne Zeilen`() {
        val message = rows(MITSCHNITT_LEER)
        assertEquals(listOf("Id", "Name", "Eintritt"), message.columns.map { it.name })
        assertEquals(emptyList(), message.rows)
    }

    /**
     * Jede echte Teilnachricht des Mitschnitts, vom leeren Anfang bis eins vor dem letzten Byte, ist abgewiesen und
     * wirft nie: eine abgeschnittene Nachricht ergibt einen Fehler des Lesers, kein Teilergebnis. Wächter: ein
     * Leser, der DONE nicht verlangt (eine Teilnachricht an einer Zeilengrenze wäre dann gültig), oder einer, der an
     * einer Grenze mit einer Ausnahme endet.
     *
     * @spec SPEC-036
     */
    @Test
    fun `jede Teilnachricht ist ein Fehler des Lesers`() {
        val full = hex(MITSCHNITT_DREI_ZEILEN)
        for (length in 0 until full.size) {
            assertIs<ProtocolResult.Failed>(BulkLoadReader.read(full.copyOf(length)), "Länge $length")
        }
    }

    /**
     * Die Wertebytes der übrigen Spaltentypen des Solls, von Hand nach `MS-TDS`: `INTN` 4 (Int32) mit NULL, `BITN`,
     * `DECIMALN(10,2)` mit dem Wert in 5 Byte (kürzer als die Typlänge 17; so sendet der Treiber ihn im RPC, für das
     * Massenladen ist die Länge nicht aufgezeichnet), `DATEN`,
     * `TIMEN(7)`, `DATETIME2N(7)`. Wächter: eine falsche Länge der Dezimalzahl, ein falsches Datum (Tage seit
     * 0001-01-01), eine falsche Zeitauflösung, Int32 statt Int64 oder umgekehrt.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Typen des Solls werden nach MS-TDS gelesen`() {
        val columns = column("26 04", "a", nullable = true) + column("68 01", "b", nullable = true) +
            column("6A 11 0A 02", "c", nullable = true) + column("28", "d", nullable = true) +
            column("29 07", "e", nullable = true) + column("2A 07", "f", nullable = true)
        val row1 = "D1 04 2A 00 00 00  01 01  05 01 E2 04 00 00  03 21 49 0B  05 80 ED 97 F4 6E  " +
            "08 80 ED 97 F4 6E 21 49 0B"
        val row2 = "D1 00 00 00 00 00 00"
        val message = rows("81 06 00 $columns $row1 $row2 $DONE")
        assertEquals(
            listOf(
                ResultColumn("a", SqlType.Int32, true),
                ResultColumn("b", SqlType.Bool, true),
                ResultColumn("c", SqlType.Decimal(10, 2), true),
                ResultColumn("d", SqlType.Date, true),
                ResultColumn("e", SqlType.Time, true),
                ResultColumn("f", SqlType.DateTime, true),
            ),
            message.columns,
        )
        assertEquals(
            listOf(
                listOf(
                    42,
                    true,
                    BigDecimal("12.50"),
                    LocalDate.of(2026, 1, 2),
                    LocalTime.of(13, 14, 15),
                    LocalDateTime.of(2026, 1, 2, 13, 14, 15),
                ),
                listOf(null, null, null, null, null, null),
            ),
            message.rows,
        )
    }

    /**
     * Ein `INTN` mit 8 Byte ist `Int64`, mit 2 Byte `Int32` und vorzeichenbehaftet, mit 1 Byte `Int32` und
     * vorzeichenlos. Wächter: eine fehlende Vorzeichenerweiterung (`FF FF` als 65535), ein vorzeichenbehaftetes
     * `tinyint`.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Ganzzahlen folgen Groesse und Vorzeichen des Typs`() {
        val columns = column("26 08", "a") + column("26 02", "b") + column("26 01", "c")
        val message = rows("81 03 00 $columns D1 08 FF FF FF FF FF FF FF FF  02 FF FF  01 FF $DONE")
        assertEquals(listOf(SqlType.Int64, SqlType.Int32, SqlType.Int32), message.columns.map { it.type })
        assertEquals(listOf(listOf<Any?>(-1L, -1, 255)), message.rows)
    }

    /**
     * Die Negativfälle der Struktur: leere Nachricht, falsches erstes Token, keine Spalten (`0xFFFF` und 0), mehr
     * Spalten als erlaubt, ein anderes Token als ROW und DONE (NBCROW `D2`), keine Zeile ohne DONE, Bytes hinter
     * DONE. Jeder endet mit dem benannten Fehler des Lesers, nie mit einer Ausnahme.
     *
     * @spec SPEC-036
     */
    @Test
    fun `eine fehlerhafte Struktur ergibt den Fehler des Lesers`() {
        val one = column("26 08", "a")
        assertEquals(ProtocolFailure.TRUNCATED, failure(""))
        assertEquals(ProtocolFailure.NO_COLUMN_METADATA, failure("D1 00"))
        assertEquals(ProtocolFailure.NO_COLUMN_METADATA, failure("81 FF FF"))
        assertEquals(ProtocolFailure.NO_COLUMN_METADATA, failure("81 00 00 $DONE"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failure("81 01 04"))
        assertEquals(ProtocolFailure.INVALID_TYPE, failure("81 01 00 $one D2 01 $DONE"))
        assertEquals(ProtocolFailure.TRUNCATED, failure("81 01 00 $one"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failure("81 01 00 $one $DONE 00"))
    }

    /**
     * Die Negativfälle der Typen: eine Typkennung außerhalb der Liste, ein Typ der Liste von `SPEC-003`, den das
     * Massenladen nicht liest (`FLTN`), ein PLP-Typ (`NVARCHAR(MAX)`), eine falsche Größe von `INTN`.
     *
     * @spec SPEC-036
     */
    @Test
    fun `ein Typ ausserhalb des Katalogs ist ein Fehler des Lesers`() {
        assertEquals(ProtocolFailure.INVALID_TYPE, failure("81 01 00 ${column("FE 00", "a")} $DONE"))
        assertEquals(ProtocolFailure.INVALID_TYPE, failure("81 01 00 ${column("6D 08", "a")} $DONE"))
        val plp = column("E7 FF FF 09 04 D0 00 34", "a")
        assertEquals(ProtocolFailure.UNSUPPORTED_PLP, failure("81 01 00 $plp $DONE"))
        assertEquals(ProtocolFailure.INVALID_TYPE_INFO, failure("81 01 00 ${column("26 03", "a")} $DONE"))
    }

    /**
     * Die Negativfälle der Werte: ein `INTN` mit anderer Länge als der Typ, ein Text länger als die Typlänge, ein
     * Unicode-Text mit ungerader Byte-Zahl, eine Dezimalzahl mit falschem Vorzeichenbyte, ein Datum nach 9999-12-31.
     *
     * @spec SPEC-036
     */
    @Test
    fun `ein fehlerhafter Wert ist ein Fehler des Lesers`() {
        val int = "81 01 00 ${column("26 08", "a")}"
        val text = "81 01 00 ${column("E7 04 00 09 04 D0 00 34", "a")}"
        val decimal = "81 01 00 ${column("6A 11 0A 02", "a")}"
        val date = "81 01 00 ${column("28", "a")}"
        assertEquals(ProtocolFailure.INVALID_LENGTH, failure("$int D1 04 01 00 00 00 $DONE"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failure("$text D1 06 00 41 00 41 00 41 00 $DONE"))
        assertEquals(ProtocolFailure.ODD_TEXT_LENGTH, failure("$text D1 03 00 41 00 41 $DONE"))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure("$decimal D1 05 02 E2 04 00 00 $DONE"))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure("$date D1 03 FF FF FF $DONE"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failure("$decimal D1 01 01 $DONE"))
    }

    /**
     * Die Grenze der Zellen schützt die Zahl der Werte, die der Leser anlegt: [BulkLoadReader.MAX_CELLS] Zeilen einer
     * Spalte sind gültig, eine mehr ist `INVALID_LENGTH`; ebenso 1024 Spalten mit [BulkLoadReader.MAX_CELLS] / 1024
     * Zeilen gültig und mit einer Zeile mehr nicht (das Produkt, nicht die Zeilenzahl allein, ist die Grenze; die
     * Probe des Reviews mit 1024 NULL-Spalten und 16 000 Zeilen endete im Heap von 128 MiB mit `OutOfMemoryError`).
     * Wächter: eine fehlende oder höhere Grenze, eine Grenze, die nur die Zeilen zählt.
     *
     * @spec SPEC-036
     * @spec TDS-QA-02
     */
    @Test
    fun `mehr Zellen als die Grenze sind ein Fehler des Lesers`() {
        fun payload(columns: Int, count: Int): ByteArray {
            val columnCount = String.format(Locale.ROOT, "%02X %02X", columns and 0xFF, columns shr 8)
            val names = List(columns) { column("26 08", "a", nullable = true) }.joinToString("")
            val head = hex("81 $columnCount $names")
            val rowBytes = hex("D1 " + "00 ".repeat(columns))
            val done = hex(DONE)
            val bytes = ByteArray(head.size + count * rowBytes.size + done.size)
            head.copyInto(bytes)
            for (index in 0 until count) rowBytes.copyInto(bytes, head.size + index * rowBytes.size)
            done.copyInto(bytes, head.size + count * rowBytes.size)
            return bytes
        }
        val atLimit = assertIs<ProtocolResult.Ok<BulkRows>>(BulkLoadReader.read(payload(1, BulkLoadReader.MAX_CELLS)))
        assertEquals(BulkLoadReader.MAX_CELLS, atLimit.value.rows.size)
        val over = assertIs<ProtocolResult.Failed>(BulkLoadReader.read(payload(1, BulkLoadReader.MAX_CELLS + 1)))
        assertEquals(ProtocolFailure.INVALID_LENGTH, over.error.failure)

        val wide = BulkLoadReader.MAX_COLUMNS
        val wideRows = BulkLoadReader.MAX_CELLS / wide
        val wideAtLimit = assertIs<ProtocolResult.Ok<BulkRows>>(BulkLoadReader.read(payload(wide, wideRows)))
        assertEquals(wideRows, wideAtLimit.value.rows.size)
        val wideOver = assertIs<ProtocolResult.Failed>(BulkLoadReader.read(payload(wide, wideRows + 1)))
        assertEquals(ProtocolFailure.INVALID_LENGTH, wideOver.error.failure)
    }

    private companion object {
        /** DONE: Status 0, curCmd 0, Zeilenzahl 0 (Mitschnitt: der Treiber sendet sie so). */
        const val DONE = "FD 00 00 00 00 00 00 00 00 00 00 00 00"

        /** Eine Spalte von COLMETADATA: UserType 0, Flags (nullbar oder nicht), [typeInfo] und der Name [name]. */
        fun column(typeInfo: String, name: String, nullable: Boolean = false): String {
            val flags = if (nullable) "01 00" else "00 00"
            val chars = name.map { String.format(Locale.ROOT, "%02X 00", it.code) }.joinToString(" ")
            return "00 00 00 00 $flags $typeInfo ${String.format(Locale.ROOT, "%02X", name.length)} $chars "
        }

        /** Nutzlast der ersten Nachricht 0x07 von `data import` (3 Zeilen), siehe Kopf der Klasse. */
        const val MITSCHNITT_DREI_ZEILEN =
            "81030000000000000026080249006400000000000100e7401f0904d00034044e0061006d006500000000000100" +
                "a70a000904d0003408450069006e00740072006900740074" +
                "00d10801000000000000000e004100640061002000dc006e00ef000a00323032362d30312d3032" +
                "d1080200000000000000ffffffffd10803000000000000000e004300e400630069006c00690065000a" +
                "00323032352d31322d3331fd000000000000000000000000"

        /** Nutzlast der letzten Nachricht 0x07 von `data import`: dieselben Spalten, keine Zeile. */
        const val MITSCHNITT_LEER =
            "81030000000000000026080249006400000000000100e7401f0904d00034044e0061006d006500000000000100" +
                "a70a000904d0003408450069006e00740072006900740074" +
                "00fd000000000000000000000000"
    }
}
