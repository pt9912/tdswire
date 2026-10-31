package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.byName
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int4
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int8
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.nvarchar
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Die Bindung einer RPC-Nachricht an Text und typisierte Parameter (`TDS-FA-04.a`, `SPEC-030`, `SPEC-002`,
 * `SPEC-003`) ohne Verbindung und ohne Backend. Eingaben sind Handvektoren (Layout nach MS-TDS, Wertbytes
 * als Literale); die erwarteten JVM-Werte sind von Hand eingetragen. Gemessen ist nichts am Referenzclient
 * oder an einem SQL Server: die Abbildung auf `SqlType`, die JVM-Typen, die Richtungsregel, die Grammatik der
 * Namen und die Ablehnungsgründe sind Festlegungen.
 */
class RpcBinderTest {
    private fun query(payload: ByteArray): RpcBinding.Query = assertIs<RpcBinding.Query>(RpcBinder.bind(payload))

    private fun rejection(payload: ByteArray): RpcRejection =
        assertIs<RpcBinding.Rejected>(RpcBinder.bind(payload)).reason

    private fun single(typeAndValue: String, status: Int = 0): QueryParameter =
        query(executeSql("select @v", "@v int", param("@v", typeAndValue, status))).parameters.single()

    private fun valueOf(typeAndValue: String): Any? = single(typeAndValue).value

    /**
     * Der Text von `sp_executesql` kommt byte-gleich als `sql` an (Unicode, Zeilenumbruch, SQL-Syntax,
     * leer); die Deklarationsliste fließt nicht ein; die Werte stehen mit Name, Typ und Richtung in der
     * Liste, auch ein Wert, der SQL-Syntax trägt, nur als Wert.
     *
     * @spec SPEC-030
     * @spec SPEC-002
     */
    @Test
    fun `sp_executesql gibt den Text unveraendert und die Werte typisiert weiter`() {
        val injection = "x'; DROP TABLE t; --"
        val texts = listOf("select @a", "", "select '€😀' \r\n from t where a = @a", "  EXEC sp_x ; SET FMTONLY ON")
        texts.forEach { text ->
            val bound = query(
                executeSql(text, "@a int, @s nvarchar(20)", param("@a", int4(42)), param("@s", nvarchar(injection))),
            )
            assertEquals(text, bound.sql, text)
            assertEquals(
                listOf(
                    QueryParameter("@a", SqlType.Int32, 42),
                    QueryParameter("@s", SqlType.UnicodeText, injection),
                ),
                bound.parameters,
                text,
            )
            assertFalse(bound.sql.contains(injection), text)
            assertFalse(bound.sql.contains("@a int"), text)
        }
    }

    /**
     * Der Aufruftext für einen RPC per Name enthält nur den Namen und die Parameternamen, nie einen Wert
     * (kein Text aus Wert und SQL): `EXEC <name> @a = @a, @o = @o OUTPUT`; die Werte stehen in der Liste.
     *
     * @spec SPEC-030
     * @spec SPEC-002
     */
    @Test
    fun `RPC per Name baut den Aufruftext aus Namen und nie aus Werten`() {
        val secret = "G3heim-'; --€"
        val bound = query(
            byName(
                "[dbo].[p]",
                param("@a", int4(1234567)),
                param("@s", nvarchar(secret)),
                param("@o", "26 04 00", status = 1),
                param("@io", int4(9), status = 1),
            ),
        )
        assertEquals("EXEC [dbo].[p] @a = @a, @s = @s, @o = @o OUTPUT, @io = @io OUTPUT", bound.sql)
        assertFalse(bound.sql.contains("1234567"))
        assertFalse(bound.sql.contains("G3heim"))
        assertEquals(listOf("@a", "@s", "@o", "@io"), bound.parameters.map { it.name })
        assertEquals(listOf(1234567, secret, null, 9), bound.parameters.map { it.value })
        assertEquals("EXEC p", query(byName("p")).sql)
        assertEquals(emptyList(), query(byName("p")).parameters)
    }

    /**
     * Ein NULL-Parameter bleibt `null` und behält seinen Typ, für jeden Typ des Mindestkatalogs.
     *
     * @spec SPEC-003
     */
    @Test
    fun `NULL bleibt null mit Typ fuer jeden Typ des Mindestkatalogs`() {
        val nulls = listOf(
            "26 04 00" to SqlType.Int32,
            "26 08 00" to SqlType.Int64,
            "68 01 00" to SqlType.Bool,
            "E7 40 1F ${RpcVectors.COLLATION} FF FF" to SqlType.UnicodeText,
            "6C 09 12 03 00" to SqlType.Decimal(18, 3),
            "6A 05 09 02 00" to SqlType.Decimal(9, 2),
            "28 00" to SqlType.Date,
            "29 07 00" to SqlType.Time,
            "2A 03 00" to SqlType.DateTime,
        )
        nulls.forEach { (typeAndValue, type) ->
            val parameter = single(typeAndValue)
            assertNull(parameter.value, "$type")
            assertEquals(type, parameter.type)
            assertEquals(ParameterDirection.INPUT, parameter.direction)
        }
    }

    /**
     * Integer-Grenzen: Int32 und Int64 an Minimum, Maximum, 0 und −1 behalten ihren Wert und die JVM-Klasse
     * (`Int`, `Long`); Bit trägt wahr und falsch.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Integer-Grenzen und Bit behalten Wert und Klasse`() {
        listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE).forEach { assertEquals(it, valueOf(int4(it)), "$it") }
        listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE).forEach { assertEquals(it, valueOf(int8(it)), "$it") }
        assertEquals(Int.MIN_VALUE, valueOf("26 04 04 00 00 00 80"))
        assertEquals(Long.MIN_VALUE, valueOf("26 08 08 00 00 00 00 00 00 00 80"))
        assertIs<Int>(valueOf(int4(5)))
        assertIs<Long>(valueOf(int8(5)))
        assertEquals(true, valueOf("68 01 01 01"))
        assertEquals(false, valueOf("68 01 01 00"))
    }

    /**
     * Unicode-Text: leer (nicht NULL), Nicht-BMP-Zeichen (Surrogatpaar), ein unpaares Surrogat und 4000
     * Einheiten kommen unverändert an. Das unpaare Surrogat ist Festlegung: der Dispatch reicht es durch.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Unicode-Text kommt unveraendert an`() {
        val texts = listOf("", "a😀b", "😀", "é€\u0000x", "z".repeat(4000))
        texts.forEach { text -> assertEquals(text, valueOf(nvarchar(text)), "${text.length} Zeichen") }
        assertEquals(SqlType.UnicodeText, single(nvarchar("")).type)
        assertEquals("\uD800", valueOf("E7 40 1F ${RpcVectors.COLLATION} 02 00 00 D8"))
        assertEquals("ab", valueOf("EF 04 00 ${RpcVectors.COLLATION} 04 00 61 00 62 00"))
    }

    /**
     * Dezimalzahl mit Precision und Scale: Typ `Decimal(p, s)`, Wert als `BigDecimal` mit dem Scale des
     * Parameters; Vorzeichenbyte 0 ist negativ; größter Betrag von Precision 38 und seine Negation.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Dezimalzahl behaelt Precision Scale und Vorzeichen`() {
        val max38 = "FF FF FF FF 3F 22 8A 09 7A C4 86 5A A8 4C 3B 4B"
        val cases = listOf(
            "6C 05 09 02 05 01 D2 04 00 00" to Triple(9, 2, BigDecimal("12.34")),
            "6A 05 09 02 05 00 D2 04 00 00" to Triple(9, 2, BigDecimal("-12.34")),
            "6C 05 05 00 05 01 9F 86 01 00" to Triple(5, 0, BigDecimal("99999")),
            "6C 05 05 05 05 01 9F 86 01 00" to Triple(5, 5, BigDecimal("0.99999")),
            "6C 11 26 00 11 01 $max38" to Triple(38, 0, BigDecimal("99999999999999999999999999999999999999")),
            "6C 11 26 00 11 00 $max38" to Triple(38, 0, BigDecimal("-99999999999999999999999999999999999999")),
            "6C 11 26 26 11 01 $max38" to Triple(38, 38, BigDecimal("0.99999999999999999999999999999999999999")),
            "6C 05 09 02 05 01 00 00 00 00" to Triple(9, 2, BigDecimal("0.00")),
        )
        cases.forEach { (typeAndValue, expected) ->
            val parameter = single(typeAndValue)
            assertEquals(SqlType.Decimal(expected.first, expected.second), parameter.type, typeAndValue)
            assertEquals(expected.third, parameter.value, typeAndValue)
            assertEquals(expected.second, (parameter.value as BigDecimal).scale(), typeAndValue)
        }
    }

    /**
     * Ein Dezimalwert außerhalb seines Bereichs ist abgelehnt: Betrag mit mehr Stellen als die Precision
     * (genau 10^38 bei Precision 38, 100000 bei Precision 5) und ein Vorzeichenbyte außer 0 und 1.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Dezimalwert ausserhalb des Bereichs ist abgelehnt`() {
        val over38 = "00 00 00 00 40 22 8A 09 7A C4 86 5A A8 4C 3B 4B"
        listOf(
            "6C 11 26 00 11 01 $over38",
            "6C 05 05 00 05 01 A0 86 01 00",
            "6C 05 09 02 05 02 D2 04 00 00",
            "6C 05 09 02 05 FF D2 04 00 00",
        ).forEach { typeAndValue ->
            val payload = executeSql("s", "d", param("@v", typeAndValue))
            assertEquals(RpcRejection.VALUE, rejection(payload), typeAndValue)
        }
    }

    /**
     * Datum, Zeit und Datum mit Zeit an ihren Grenzen: 0001-01-01 und 9999-12-31, Mitternacht und der
     * letzte Tick des Tages je Scale (0, 3, 7), ein Wert mitten im Tag.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Datum und Zeit behalten ihre Grenzwerte`() {
        assertEquals(LocalDate.of(1, 1, 1), valueOf("28 03 00 00 00"))
        assertEquals(LocalDate.of(9999, 12, 31), valueOf("28 03 DA B9 37"))
        assertEquals(LocalDate.of(2025, 1, 15), valueOf("28 03 C1 47 0B"))
        assertEquals(LocalTime.MIDNIGHT, valueOf("29 07 05 00 00 00 00 00"))
        assertEquals(LocalTime.of(23, 59, 59, 999_999_900), valueOf("29 07 05 FF BF 69 2A C9"))
        assertEquals(LocalTime.of(23, 59, 59), valueOf("29 00 03 7F 51 01"))
        assertEquals(LocalTime.of(12, 34, 56, 789_000_000), valueOf("29 03 04 95 2C B3 02"))
        assertEquals(LocalTime.of(1, 2, 3, 100), valueOf("29 07 05 81 B7 14 AB 08"))
        assertEquals(
            LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_900),
            valueOf("2A 07 08 FF BF 69 2A C9 DA B9 37"),
        )
        assertEquals(LocalDateTime.of(1, 1, 1, 0, 0), valueOf("2A 07 08 00 00 00 00 00 00 00 00"))
        assertEquals(LocalDateTime.of(2025, 1, 15, 12, 34, 56, 789_000_000), valueOf("2A 03 07 95 2C B3 02 C1 47 0B"))
        assertEquals(SqlType.Time, single("29 00 03 00 00 00").type)
        assertEquals(SqlType.Date, single("28 03 00 00 00").type)
        assertEquals(SqlType.DateTime, single("2A 07 08 00 00 00 00 00 00 00 00").type)
    }

    /**
     * Ein Tag nach 9999-12-31 und eine Zeit ab 24 Uhr (je Scale 0 und 7) sind abgelehnt, auch als Teil
     * eines Datums mit Zeit.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Datum und Zeit ausserhalb des Bereichs sind abgelehnt`() {
        listOf(
            "28 03 DB B9 37",
            "29 07 05 00 C0 69 2A C9",
            "29 00 03 80 51 01",
            "2A 07 08 00 C0 69 2A C9 00 00 00",
            "2A 07 08 00 00 00 00 00 DB B9 37",
        ).forEach { typeAndValue ->
            assertEquals(RpcRejection.VALUE, rejection(executeSql("s", "d", param("@v", typeAndValue))), typeAndValue)
        }
    }

    /**
     * Die Richtung: ohne Bit 0 INPUT; Bit 0 mit Wert INPUT_OUTPUT, Bit 0 mit NULL OUTPUT (Festlegung; auf
     * dem Draht sind OUTPUT und InputOutput dasselbe Bit).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Die Richtung folgt Bit 0 und dem Wert`() {
        assertEquals(ParameterDirection.INPUT, single(int4(1)).direction)
        assertEquals(ParameterDirection.INPUT, single("26 04 00").direction)
        assertEquals(ParameterDirection.INPUT_OUTPUT, single(int4(1), status = 1).direction)
        assertEquals(ParameterDirection.INPUT_OUTPUT, single(nvarchar(""), status = 1).direction)
        assertEquals(ParameterDirection.OUTPUT, single("26 04 00", status = 1).direction)
        assertEquals(ParameterDirection.OUTPUT, single("E7 40 1F ${RpcVectors.COLLATION} FF FF", status = 1).direction)
    }

    /**
     * Jedes andere Bit des Statusbytes als Bit 0 ist abgelehnt (Bit 1 ist der Standardwert, nicht gemessen).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein anderes Statusbit als OUTPUT ist abgelehnt`() {
        listOf(0x02, 0x03, 0x04, 0x08, 0x80, 0xFE).forEach { status ->
            val payload = executeSql("s", "d", param("@v", int4(1), status))
            assertEquals(RpcRejection.STATUS, rejection(payload), "$status")
        }
    }

    /**
     * Typen ohne Entsprechung in `SqlType` sind abgelehnt, mit Wert und als NULL: `INTN` mit 1 und 2 Byte,
     * Gleitkomma, Geld, GUID, Binär, `varchar`, `DATETIMN`, `DATETIMEOFFSETN`.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Typen ausserhalb des Mindestkatalogs sind abgelehnt`() {
        val cases = listOf(
            "26 01 01 07", "26 02 02 07 00", "26 01 00", "6D 08 00", "6D 04 04 00 00 00 00", "6E 08 00",
            "24 10 00", "A5 10 00 FF FF", "AD 04 00 00 00", "A7 10 00 ${RpcVectors.COLLATION} FF FF",
            "AF 04 00 ${RpcVectors.COLLATION} FF FF", "6F 08 00", "2B 07 00",
        )
        cases.forEach { typeAndValue ->
            assertEquals(RpcRejection.TYPE, rejection(executeSql("s", "d", param("@v", typeAndValue))), typeAndValue)
        }
    }

    /**
     * Parameternamen: gültig sind `@` plus Bezeichnerzeichen (auch Unicode-Buchstaben) bis 128 Zeichen;
     * ohne `@`, nur `@`, mit Leerzeichen, Semikolon, Klammer oder Anführungszeichen, 129 Zeichen, leer und
     * doppelt (ohne Beachtung der Schreibweise) sind abgelehnt. Ein Name mit `@@` am Anfang ist nur beim RPC
     * per Prozedurname abgelehnt (Festlegung ohne Messung: im Aufruftext stünde er rechts von `=`, wo er eine
     * globale Funktion bezeichnen kann); bei `sp_executesql` steht der Name nicht im Text und ist
     * gültig. Ein Name bleibt wie gesendet; die Obergrenze 128 zählt das `@` mit
     * (127 Zeichen nach dem `@` gültig, 128 abgelehnt).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Parameternamen folgen der Grammatik`() {
        val valid = listOf("@a", "@A1", "@p_0", "@é", "@a#b\$c@d", "@" + "x".repeat(127))
        valid.forEach { name ->
            assertEquals(name, query(executeSql("s", "d", param(name, int4(1)))).parameters.single().name, name)
        }
        val invalid = listOf(
            "a", "@", "", "@a b", "@a;b", "@a)", "@a'", "@a-b", "@" + "x".repeat(128), "@a\n",
        )
        invalid.forEach { name ->
            assertEquals(
                RpcRejection.PARAMETER_NAME,
                rejection(executeSql("s", "d", param(name, int4(1)))),
                name,
            )
        }
        val duplicate = executeSql("s", "d", param("@a", int4(1)), param("@A", int4(2)))
        assertEquals(RpcRejection.PARAMETER_NAME, rejection(duplicate))
        invalid.filter { it != "@a\n" }.forEach { name ->
            assertEquals(RpcRejection.PARAMETER_NAME, rejection(byName("p", param(name, int4(1)))), name)
        }
        assertEquals(2, query(executeSql("s", "d", param("@a", int4(1)), param("@b", int4(2)))).parameters.size)
    }

    /**
     * `@@x` als Parametername (Festlegung ohne Messung, Nutzerentscheidung vom 2026-10-31): beim RPC per
     * Prozedurname steht der Name im Aufruftext, auch rechts von `=`, und ist abgelehnt; bei `sp_executesql`
     * steht er nicht im Text, die Anfrage trägt ihn unverändert als Parametername und ihr Text bleibt gleich.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Doppeltes At-Zeichen im Parameternamen ist nur beim RPC per Name abgelehnt`() {
        listOf("@@x", "@@rowcount").forEach { name ->
            assertEquals(RpcRejection.PARAMETER_NAME, rejection(byName("p", param(name, int4(1)))), name)
            val bound = query(executeSql("select 1", "d", param(name, int4(1))))
            assertEquals(name, bound.parameters.single().name, name)
            assertEquals("select 1", bound.sql, name)
        }
    }

    /**
     * Geprüft wird der Anfang des Namens, nicht jede Stelle: `@@` in der Mitte (`@a@@b`) ist bei beiden
     * Formen zulässig, und `@@x` als OUTPUT-Parameter beim RPC per Name ist wie als INPUT abgelehnt.
     * Wächter: `name.contains("@@")` statt `name[1]`, oder eine Prüfung, die das Statusbyte ausnimmt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Doppeltes At-Zeichen in der Mitte ist zulaessig und bei OUTPUT am Anfang abgelehnt`() {
        assertEquals(RpcRejection.PARAMETER_NAME, rejection(byName("p", param("@@x", int4(1), status = 1))))
        assertEquals(RpcRejection.PARAMETER_NAME, rejection(byName("p", param("@@x", "26 04 00", status = 1))))
        assertEquals("@a@@b", query(byName("p", param("@a@@b", int4(1)))).parameters.single().name)
        assertEquals("@a@@b", query(executeSql("select 1", "d", param("@a@@b", int4(1)))).parameters.single().name)
    }

    /**
     * Prozedurnamen: gültig sind reguläre Bezeichner und Teile in Klammern mit `]]` als Escape, ein bis
     * vier Teile, höchstens 128 Zeichen je Teil; der Text enthält den Namen unverändert. Abgelehnt sind
     * Anweisungstrenner, Kommentar, Leerzeichen außerhalb von Klammern, leere Teile, fünf Teile, 129
     * Zeichen, eine unterbrochene Klammer und Steuerzeichen.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Prozedurnamen folgen der Grammatik`() {
        val bracket128 = "[" + "a".repeat(128) + "]"
        val valid = listOf(
            "p", "dbo.p", "[dbo].[p]", "a.b.c.d", "[a b]", "[a]]b]", "[a;b]", "[--]", "#tmp", "_p1\$", "ü",
            "p".repeat(128), bracket128, "[é😀]",
        )
        valid.forEach { name -> assertEquals("EXEC $name", query(byName(name)).sql, name) }
        val invalid = listOf(
            "p;", "p; drop table t", "p--x", "p /*", "a b", "a..b", ".p", "p.", "a.b.c.d.e", "[]", "[a", "a]", "[a]b",
            "[a]]", "1p", "p\$q;", "p".repeat(129), "[" + "a".repeat(129) + "]", "[a\nb]", "a\tb", "p'", "p\"", "p(1)",
            "@p", "p;1",
        )
        invalid.forEach { name ->
            assertEquals(RpcRejection.PROCEDURE_NAME, rejection(byName(name, param("@a", int4(1)))), name)
        }
    }

    /**
     * Namen, die der Katalog führt, erreichen das Backend per Name nicht, mit Klammern, Schema und in jeder
     * Schreibweise; ähnliche Namen (Präfix, Suffix) sind gewöhnliche Prozeduren.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Katalognamen per Name sind abgelehnt, aehnliche Namen nicht`() {
        val catalog = listOf(
            "sp_executesql", "SP_ExecuteSql", "[sys].[sp_prepexec]", "master.dbo.sp_execute", "sp_prepare",
            "[SP_UNPREPARE]", "sp_cursor", "sp_cursoropen", "sp_CursorFetch", "dbo.sp_cursorclose", "sp_cursors_x",
        )
        catalog.forEach { name ->
            assertEquals(RpcRejection.UNKNOWN_PROCEDURE, rejection(byName(name)), name)
        }
        val ordinary = listOf("sp_prepared", "my_sp_prepare", "sp_executesql2", "sp_help", "[sp_prepare].p")
        ordinary.forEach { name -> assertEquals("EXEC $name", query(byName(name)).sql, name) }
    }

    /**
     * Ein Parameter ohne Namen ist abgelehnt, in `sp_executesql` wie per Name (Festlegung: die Deklarationsliste
     * wird nicht gedeutet, eine Zuordnung nach Position gibt es nicht).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein Parameter ohne Namen ist abgelehnt`() {
        assertEquals(RpcRejection.PARAMETER_NAME, rejection(executeSql("s", "d", param("", int4(1)))))
        assertEquals(RpcRejection.PARAMETER_NAME, rejection(byName("p", param("", int4(1)))))
    }

    /**
     * Katalogformen ohne Bearbeitung und fremde ProcIDs: `sp_prepexec` und `sp_execute` sind abgelehnt
     * (Handle-Verwaltung nicht gebaut), ebenso `sp_prepare` (11), `sp_unprepare` (15), `sp_cursoropen` (2),
     * ProcID 0 und 65535.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Unbekannte ProcIDs und nicht gebaute Katalogformen sind abgelehnt`() {
        assertEquals(RpcRejection.NOT_BUILT, rejection(hex(RpcVectors.PREP_EXEC)))
        assertEquals(RpcRejection.NOT_BUILT, rejection(hex(RpcVectors.EXECUTE)))
        listOf(0, 1, 2, 11, 14, 15, 99, 65535).forEach { id ->
            assertEquals(RpcRejection.UNKNOWN_PROCEDURE, rejection(RpcWire.procId(id)), "$id")
        }
    }

    /**
     * Ein gültig kodierter, nicht unterstützter PLP-Typ (`nvarchar(max)`, `varchar(max)`, `varbinary(max)`,
     * `nchar`-Form) ist `TYPE` ohne Parserfehler (Verbindung bleibt offen), als Wertparameter, als SQL-Text
     * und beim RPC per Name; dagegen bleibt die unbekannte Typkennung ein Parserfehler (Festlegung ohne
     * Messung, Nutzerentscheidung vom 2026-10-31).
     *
     * @spec SPEC-013
     * @spec SPEC-030
     */
    @Test
    fun `Ein PLP-Typ ist nicht unterstuetzt aber nicht ungueltig kodiert`() {
        val coll = RpcVectors.COLLATION
        listOf("E7 FF FF $coll 00 00", "A7 FF FF $coll 00 00", "A5 FF FF 00 00", "EF FF FF $coll 00 00")
            .forEach { typeAndValue ->
                listOf(
                    executeSql("s", "d", param("@v", typeAndValue)),
                    byName("p", param("@v", typeAndValue)),
                ).forEach { payload ->
                    val rejected = assertIs<RpcBinding.Rejected>(RpcBinder.bind(payload))
                    assertEquals(RpcRejection.TYPE, rejected.reason, typeAndValue)
                    assertNull(rejected.error, typeAndValue)
                }
            }
        val rejected = assertIs<RpcBinding.Rejected>(RpcBinder.bind(executeSql("s", "d", param("@v", "FE 00"))))
        assertEquals(RpcRejection.ENCODING, rejected.reason)
        assertNotNull(rejected.error)
    }

    /**
     * Eine Nachricht, die der Parser abweist, ist abgelehnt: leer, abgeschnitten an jeder Stelle der
     * Mustervektoren, ungerade Textlänge, falsche Wertlänge, Typkennung außerhalb des Katalogs (Länge des
     * Wertes unbestimmt), Name der Länge 0. Ein PLP-Typ (MAX) gehört nicht dazu: er ist gültig kodiert (siehe
     * den Test dazu).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ungueltig kodierte Nachrichten sind abgelehnt`() {
        val full = hex(RpcVectors.EXECUTE_SQL)
        assertIs<RpcBinding.Query>(RpcBinder.bind(full))
        assertEquals(RpcRejection.ENCODING, rejection(ByteArray(0)))
        assertEquals(RpcRejection.ENCODING, rejection(full.copyOf(10)))
        assertEquals(RpcRejection.ENCODING, rejection(full.copyOf(full.size - 1)))
        assertEquals(RpcRejection.ENCODING, rejection(full + byteArrayOf(0x01)))
        val broken = listOf(
            "E7 40 1F ${RpcVectors.COLLATION} 03 00 61 00 62",
            "26 04 03 01 00 00",
            "FE 00",
            "26 03 00",
        )
        broken.forEach { typeAndValue ->
            val payload = executeSql("s", "d", param("@v", typeAndValue))
            assertEquals(RpcRejection.ENCODING, rejection(payload), typeAndValue)
        }
        assertEquals(RpcRejection.ENCODING, rejection(hex("${RpcVectors.HEADERS} 00 00 00 00")))
        listOf(ByteArray(0), full.copyOf(10), full + byteArrayOf(0x01)).forEach { payload ->
            assertNotNull(assertIs<RpcBinding.Rejected>(RpcBinder.bind(payload)).error)
        }
        assertNull(assertIs<RpcBinding.Rejected>(RpcBinder.bind(RpcWire.procId(11))).error)
        assertNull(assertIs<RpcBinding.Rejected>(RpcBinder.bind(byName("p; x"))).error)
    }

    /**
     * Die Textdarstellung der Bindung und der Parameter nennt weder Text noch Werte (`TDS-QA-03`).
     *
     * @spec SPEC-048
     */
    @Test
    fun `Die Textdarstellung nennt keine Werte`() {
        val secret = "Kennwort-Z9"
        val bound = query(executeSql("select '$secret'", "d", param("@pw", nvarchar(secret)), param("@n", int4(31337))))
        val shown = bound.toString() + bound.parameters.toString()
        assertFalse(shown.contains(secret), shown)
        assertFalse(shown.contains("31337"), shown)
        assertFalse(shown.contains("@pw"), shown)
    }
}
