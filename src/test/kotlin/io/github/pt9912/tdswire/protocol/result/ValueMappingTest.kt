package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValueMappingTest {
    private fun col(type: SqlType, nullable: Boolean = true) = ResultColumn("a", type, nullable)

    private fun encoded(type: SqlType, value: Any?, nullable: Boolean = true): String =
        MappingSupport.encodedRow(listOf(col(type, nullable)), listOf(value))

    private fun failureOf(type: SqlType, value: Any?, nullable: Boolean = true): ProtocolFailure {
        val defs = MappingSupport.defs(col(type, nullable))
        return MappingSupport.error(ValueMapping.value(defs.single(), value)).failure
    }

    private fun mapped(type: SqlType, value: Any?, nullable: Boolean = true): TdsValue {
        val defs = MappingSupport.defs(col(type, nullable))
        return assertIs<ProtocolResult.Ok<TdsValue>>(ValueMapping.value(defs.single(), value)).value
    }

    /**
     * Handvektoren nach MS-TDS 2.2.7.17: ein ROW-Token ist `D1`, dann je Spalte der Wert; ein fester Typ
     * hat kein Längenbyte, INTN und BITN haben eines (0 = NULL). Min und Max von Int32 und Int64 sind
     * Zweierkomplement, Little-Endian.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Integer, Min und Max, in fester und N-Form`() {
        assertEquals("D1 00 00 00 80", encoded(SqlType.Int32, Int.MIN_VALUE, nullable = false))
        assertEquals("D1 FF FF FF 7F", encoded(SqlType.Int32, Int.MAX_VALUE, nullable = false))
        assertEquals("D1 04 00 00 00 80", encoded(SqlType.Int32, Int.MIN_VALUE))
        assertEquals("D1 08 FF FF FF FF FF FF FF 7F", encoded(SqlType.Int64, Long.MAX_VALUE))
        assertEquals("D1 00 00 00 00 00 00 00 80", encoded(SqlType.Int64, Long.MIN_VALUE, nullable = false))
        assertEquals("D1 08 00 00 00 00 00 00 00 80", encoded(SqlType.Int64, Long.MIN_VALUE))
    }

    /**
     * Handvektoren: NULL ist bei INTN und BITN das Längenbyte 0, bei Unicode-Text die Länge `FF FF`;
     * ein Bit-Wert ist ein Längenbyte 1 und 0 oder 1.
     *
     * @spec SPEC-003
     */
    @Test
    fun `NULL und Bit in nullbaren Spalten`() {
        assertEquals("D1 00", encoded(SqlType.Int32, null))
        assertEquals("D1 00", encoded(SqlType.Bool, null))
        assertEquals("D1 FF FF", encoded(SqlType.UnicodeText, null))
        assertEquals("D1 01 01", encoded(SqlType.Bool, true))
        assertEquals("D1 01 00", encoded(SqlType.Bool, false))
        assertEquals("D1 01", encoded(SqlType.Bool, true, nullable = false))
    }

    /**
     * NULL in einer Spalte, die NULL nicht erlaubt, ist ein definierter Fehler (Festlegung ohne Messung:
     * die Spezifikation schweigt dazu), kein Wert.
     *
     * @spec SPEC-014
     */
    @Test
    fun `NULL in einer nicht nullbaren Spalte ist ein Fehler`() {
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.Int32, null, nullable = false))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.UnicodeText, null, nullable = false))
    }

    /**
     * Festlegung ohne Messung (Quelltext): `Int` für 32 Bit, `Long` für 64 Bit, `Boolean`; jede andere
     * Klasse ist ein Typkonflikt, auch ein `Long` in einer 32-Bit-Spalte und ein `Int` in einer 64-Bit-Spalte.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Wert der falschen Klasse ist ein Typkonflikt`() {
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.Int32, 1L))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.Int64, 1))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.Bool, 1))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.Bool, "true"))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.UnicodeText, 'c'))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.Decimal(5, 2), 1.5))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.Date, LocalDateTime.of(2024, 1, 1, 0, 0)))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(SqlType.DateTime, LocalDate.of(2024, 1, 1)))
    }

    /**
     * Handvektoren: der leere String ist Länge 0 (`00 00`) und nicht NULL (`FF FF`); `a` ist
     * `02 00 61 00`; U+1F600 ist das Surrogatpaar `D83D DE00` und damit 4 Byte `3D D8 00 DE`.
     *
     * @spec SPEC-003
     */
    @Test
    fun `leerer String ist nicht NULL, Surrogatpaare bleiben Paare`() {
        assertEquals("D1 00 00", encoded(SqlType.UnicodeText, ""))
        assertEquals("D1 02 00 61 00", encoded(SqlType.UnicodeText, "a"))
        assertEquals("D1 04 00 3D D8 00 DE", encoded(SqlType.UnicodeText, "😀"))
        assertEquals(TdsValue.TextValue(""), mapped(SqlType.UnicodeText, ""))
    }

    /**
     * Die Textlänge zählt UTF-16-Einheiten: 4000 Einheiten passen, 4001 nicht; 2000 Surrogatpaare sind
     * 4000 Einheiten und passen, ein Paar über der Grenze nicht. Handvektor des Kopfes: 8000 Byte sind
     * `40 1F`.
     *
     * @spec SPEC-003
     */
    @Test
    fun `maximale Textlaenge zaehlt UTF-16-Einheiten`() {
        val maximal = encoded(SqlType.UnicodeText, "x".repeat(4000))
        assertTrue(maximal.startsWith("D1 40 1F 78 00 78 00"), maximal.take(30))
        assertEquals(1 + 2 + 8000, (maximal.length + 1) / 3)
        assertEquals(ProtocolFailure.TOO_LONG, failureOf(SqlType.UnicodeText, "x".repeat(4001)))
        mapped(SqlType.UnicodeText, "😀".repeat(2000))
        assertEquals(ProtocolFailure.TOO_LONG, failureOf(SqlType.UnicodeText, "x".repeat(3999) + "😀"))
    }

    /**
     * Ungültiger Unicode ist ein definierter Fehler vor der Zeile: ein hohes Surrogat allein, am Ende, ein
     * niedriges allein und ein Paar in umgekehrter Reihenfolge.
     *
     * @spec SPEC-014
     */
    @Test
    fun `unpaare Surrogate sind ein Fehler`() {
        for (text in listOf("\uD83D", "a\uD83D", "\uDE00", "\uDE00\uD83D", "\uD83Dx\uDE00", "\uD83D😀")) {
            assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(SqlType.UnicodeText, text), text)
        }
    }

    /**
     * Handvektoren der Decimal-Werte (Precision 5, Scale 2, Länge 5): Vorzeichenbyte (1 positiv, 0 negativ),
     * dann der Betrag ohne Komma Little-Endian. 123,45 ist 12345 = `0x3039`; 1,5 wird auf Scale 2
     * skaliert (150 = `0x96`); -0,01 ist Vorzeichen 0 und 1.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Decimal wird auf den Scale der Spalte skaliert`() {
        val type = SqlType.Decimal(5, 2)
        assertEquals("D1 05 01 39 30 00 00", encoded(type, BigDecimal("123.45")))
        assertEquals("D1 05 01 96 00 00 00", encoded(type, BigDecimal("1.5")))
        assertEquals("D1 05 00 01 00 00 00", encoded(type, BigDecimal("-0.01")))
        assertEquals(TdsValue.DecimalValue(BigDecimal("1.50")), mapped(type, BigDecimal("1.5")))
        assertEquals(TdsValue.DecimalValue(BigDecimal("100.00")), mapped(type, BigDecimal("1E+2")))
        assertEquals(TdsValue.DecimalValue(BigDecimal("1.50")), mapped(type, BigDecimal("1.5000")))
    }

    /**
     * Überlauf: 999,99 passt in (5, 2), 1000,00 hat eine Stelle zu viel; (38, 0) endet bei 10^38 - 1 (Handvektor
     * `0x4B3B4CA85A86C47A098A223FFFFFFFFF`, Little-Endian), 10^38 passt nicht. Mehr Nachkommastellen als der
     * Scale gehen verloren und werden abgewiesen statt gerundet; nachlaufende Nullen sind kein Verlust
     * (Festlegung ohne Messung: Lesart von „Genauigkeit größer als der Scale“).
     *
     * @spec SPEC-003
     * @spec SPEC-014
     */
    @Test
    fun `Decimal-Ueberlauf und Stellenverlust werden abgewiesen`() {
        val type = SqlType.Decimal(5, 2)
        mapped(type, BigDecimal("999.99"))
        mapped(type, BigDecimal("-999.99"))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(type, BigDecimal("1000.00")))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(type, BigDecimal("1E+3")))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(type, BigDecimal("1.234")))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(type, BigDecimal("0.005")))
        val wide = SqlType.Decimal(38, 0)
        assertEquals(
            "D1 11 01 FF FF FF FF 3F 22 8A 09 7A C4 86 5A A8 4C 3B 4B",
            encoded(wide, BigDecimal.TEN.pow(38).subtract(BigDecimal.ONE)),
        )
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(wide, BigDecimal.TEN.pow(38)))
        assertEquals("D1 05 01 00 00 00 00", encoded(SqlType.Decimal(5, 2), BigDecimal.ZERO))
    }

    /**
     * Ein Wert mit extremem Exponent wird abgewiesen oder abgebildet, ohne Speicher oder Zeit zu binden:
     * `1E+999999999` überläuft, `1E-999999999` verliert Stellen, Null mit extremem Exponent ist 0,00.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Decimal mit extremem Exponent bindet keinen Speicher`() {
        val type = SqlType.Decimal(5, 2)
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(type, BigDecimal("1E+999999999")))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(type, BigDecimal("1E-999999999")))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(type, BigDecimal("-1E+2147483647")))
        assertEquals(TdsValue.DecimalValue(BigDecimal("0.00")), mapped(type, BigDecimal("0E+999999999")))
        assertEquals(TdsValue.DecimalValue(BigDecimal("0.00")), mapped(type, BigDecimal("0E-999999999")))
    }

    /**
     * Handvektoren (MS-TDS 2.2.5.5.1.8): DATE sind die Tage seit 0001-01-01 in 3 Byte Little-Endian;
     * 2000-01-01 sind 730119 = `0x0B2407`, 9999-12-31 sind 3652058 = `0x37B9DA`. Außerhalb von Jahr 1 bis 9999
     * ist ein Datum ein Fehler.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Datum von 0001 bis 9999`() {
        assertEquals("D1 03 07 24 0B", encoded(SqlType.Date, LocalDate.of(2000, 1, 1)))
        assertEquals("D1 03 00 00 00", encoded(SqlType.Date, LocalDate.of(1, 1, 1)))
        assertEquals("D1 03 DA B9 37", encoded(SqlType.Date, LocalDate.of(9999, 12, 31)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(SqlType.Date, LocalDate.of(0, 12, 31)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(SqlType.Date, LocalDate.of(10000, 1, 1)))
        val tooLate = LocalDateTime.of(10000, 1, 1, 0, 0)
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(SqlType.DateTime, tooLate))
    }

    /**
     * Handvektoren: TIME bei Scale 7 sind die 100-ns-Ticks seit Mitternacht in 5 Byte; 12:00:00 sind
     * 432000000000 = `0x649534E000`, 23:59:59,9999999 ist `FF BF 69 2A C9` (Vektor aus den Encoder-Tests).
     * DATETIME2 ist die Zeit, dann das Datum (2024-01-01 = `45 46 0B`).
     *
     * @spec SPEC-003
     */
    @Test
    fun `Zeit und Datum mit Zeit bei Scale 7`() {
        assertEquals("D1 05 00 E0 34 95 64", encoded(SqlType.Time, LocalTime.NOON))
        assertEquals("D1 05 FF BF 69 2A C9", encoded(SqlType.Time, LocalTime.of(23, 59, 59, 999_999_900)))
        assertEquals("D1 08 80 96 98 00 00 45 46 0B", encoded(SqlType.DateTime, LocalDateTime.of(2024, 1, 1, 0, 0, 1)))
    }

    /**
     * Entscheidung des Nutzers (2026-10-30), Rundung: ist die Genauigkeit des Werts größer als die Scale
     * der Spalte, wird abgewiesen statt gerundet. Bei Scale 7 ist 50 ns zu fein, 100 ns nicht.
     *
     * @spec SPEC-003
     * @spec SPEC-014
     */
    @Test
    fun `feinere Zeit als Scale 7 wird abgewiesen`() {
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(SqlType.Time, LocalTime.of(12, 0, 0, 50)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(SqlType.Time, LocalTime.of(0, 0, 0, 1)))
        mapped(SqlType.Time, LocalTime.of(0, 0, 0, 100))
        val stamp = LocalDateTime.of(2024, 1, 1, 0, 0, 0, 150)
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(SqlType.DateTime, stamp))
    }

    /**
     * Entscheidung des Nutzers (2026-10-30), Zeitzone: Werte ohne Zone werden wie geliefert übernommen,
     * auch eine Ortszeit, die in einer Zone nicht existiert (2024-03-31T02:30 in Europe/Berlin); Werte mit
     * Zone sind nicht im Mindestkatalog und enden in `TDS_UNSUPPORTED_TYPE`.
     *
     * @spec SPEC-003
     * @spec SPEC-014
     */
    @Test
    fun `Werte ohne Zone unveraendert, Werte mit Zone abgewiesen`() {
        val gap = LocalDateTime.of(2024, 3, 31, 2, 30)
        assertEquals(TdsValue.DateTime2Value(gap), mapped(SqlType.DateTime, gap))
        assertEquals(TdsValue.TimeValue(LocalTime.of(2, 30)), mapped(SqlType.Time, LocalTime.of(2, 30)))
        val zoned = listOf(
            Instant.EPOCH,
            OffsetDateTime.of(gap, ZoneOffset.ofHours(2)),
            ZonedDateTime.of(gap, ZoneId.of("Europe/Berlin")),
        )
        for (type in listOf(SqlType.Date, SqlType.Time, SqlType.DateTime)) {
            for (value in zoned) {
                assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(type, value), "$type ${value::class.simpleName}")
            }
        }
    }

    /**
     * Eine Zeile wird als Ganzes abgebildet: ein ungültiger Wert in der letzten Spalte macht die Zeile zum
     * Fehler ohne Teilzeile, und eine falsche Wertzahl ist ein Fehler.
     *
     * @spec SPEC-014
     * @spec SPEC-031
     */
    @Test
    fun `Zeile ist atomar`() {
        val defs = MappingSupport.defs(col(SqlType.Int32), col(SqlType.Int32), col(SqlType.UnicodeText))
        val bad = MappingSupport.error(ValueMapping.row(defs, listOf(1, 2, "\uD800")))
        assertTrue("Spalte 2" in bad.detail, bad.detail)
        MappingSupport.error(ValueMapping.row(defs, listOf(1, 2)))
        MappingSupport.error(ValueMapping.row(defs, listOf(1, 2, "x", 4)))
        val good = assertIs<ProtocolResult.Ok<List<TdsValue>>>(ValueMapping.row(defs, listOf(1, null, "x"))).value
        assertEquals(listOf(TdsValue.IntegerValue(1), TdsValue.NullValue, TdsValue.TextValue("x")), good)
    }

    /**
     * Die Textdarstellung eines Fehlers enthält keinen Wert der Zeile: Text, Zahl und Datum
     * erscheinen nicht; die Position der Spalte erscheint.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Fehlertext enthaelt keine Zeilenwerte`() {
        val defs = MappingSupport.defs(
            col(SqlType.UnicodeText),
            col(SqlType.Decimal(5, 2)),
            col(SqlType.Date),
            col(SqlType.Int32),
        )
        val secrets = listOf("geheim-4711\uD800", BigDecimal("98765.4321"), LocalDate.of(12345, 6, 7), "pw-0815")
        val renderings = secrets.indices.map { index ->
            val row = listOf<Any?>("ok", BigDecimal("1.00"), LocalDate.of(2000, 1, 1), 1).toMutableList()
            row[index] = secrets[index]
            MappingSupport.error(ValueMapping.row(defs, row)).toString()
        }
        for (text in renderings) {
            for (marker in listOf("geheim", "4711", "98765", "4321", "12345", "pw-0815")) {
                assertFalse(marker in text, "$marker in $text")
            }
        }
        assertTrue(renderings.all { "Spalte" in it })
    }

    /**
     * Ein Spaltentyp außerhalb des Mindestkatalogs (INTN der Größe 1) ist für Werte ein definierter Fehler.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Ganzzahl ausserhalb des Mindestkatalogs ist nicht abbildbar`() {
        val tinyint = ColumnDef("t", TdsType.IntNType(1), nullable = true)
        assertEquals(ProtocolFailure.INVALID_TYPE, MappingSupport.error(ValueMapping.value(tinyint, 1)).failure)
    }
}
