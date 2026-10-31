package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests der Überführung eines Werts in den Spaltentyp (`TDS-FA-11.a` Schritt 4). Alles ist Festlegung: die
 * Regeln sind `SPEC-035` und `ADR-0012` entnommen (streng, verlustfrei), ihr Verhalten an einem echten SQL
 * Server ist nicht gemessen. Gemessen wird hier nur der Code gegen handgebaute Eingaben.
 */
class SimValuesTest {
    private fun column(type: SqlType) = SimColumn("c", type)

    private fun literal(value: SimValue, type: SqlType): Converted = SimValues.convert(value, column(type), emptyList())

    private fun assertValue(expected: Any?, actual: Converted, message: String) {
        val converted = assertIs<Converted.Value>(actual, message)
        assertEquals(expected, converted.value, message)
        if (expected != null) assertEquals(expected.javaClass, converted.value?.javaClass, message)
    }

    private fun assertInvalid(actual: Converted, message: String) {
        assertIs<Converted.Invalid>(actual, message)
    }

    /**
     * Boundary: eine ganze Zahl passt in Int32 von -2^31 bis 2^31-1 und in Int64 von -2^63 bis 2^63-1; ein Wert
     * darüber oder darunter ist ungültig (Überlauf ist nie gültig). Eine Zahl ohne Rest in Dezimalschreibweise
     * (`1.0`, `100`) gilt, eine mit Rest (`1.5`) nicht, auch nicht gerundet.
     * @spec SPEC-035
     * @spec SPEC-039
     */
    @Test
    fun `ganze Zahlen passen genau in ihren Bereich`() {
        assertValue(2147483647, literal(num("2147483647"), SqlType.Int32), "Int32 Obergrenze")
        assertValue(-2147483648, literal(num("-2147483648"), SqlType.Int32), "Int32 Untergrenze")
        assertInvalid(literal(num("2147483648"), SqlType.Int32), "Int32 Überlauf oben")
        assertInvalid(literal(num("-2147483649"), SqlType.Int32), "Int32 Überlauf unten")
        assertValue(Long.MAX_VALUE, literal(num("9223372036854775807"), SqlType.Int64), "Int64 Obergrenze")
        assertValue(Long.MIN_VALUE, literal(num("-9223372036854775808"), SqlType.Int64), "Int64 Untergrenze")
        assertInvalid(literal(num("9223372036854775808"), SqlType.Int64), "Int64 Überlauf oben")
        assertInvalid(literal(num("-9223372036854775809"), SqlType.Int64), "Int64 Überlauf unten")
        assertValue(1, literal(num("1.0"), SqlType.Int32), "ohne Rest")
        assertValue(100, literal(num("100"), SqlType.Int32), "Nullen vor dem Komma bleiben")
        assertInvalid(literal(num("1.5"), SqlType.Int32), "mit Rest")
        assertInvalid(literal(num("1.5"), SqlType.Int64), "mit Rest, Int64")
    }

    /**
     * Boundary: eine Dezimalzahl hat höchstens so viele Nachkommastellen wie die Skala und höchstens
     * Precision minus Skala Stellen vor dem Komma; Nullen am Ende der Skala zählen nicht, Rundung gibt es nicht.
     * @spec SPEC-035
     * @spec SPEC-039
     */
    @Test
    fun `Dezimalzahlen passen ohne Rundung und ohne Ueberlauf`() {
        val money = SqlType.Decimal(10, 2)
        assertValue(BigDecimal("3.5"), literal(num("3.5"), money), "weniger Stellen")
        assertValue(BigDecimal("3.500"), literal(num("3.500"), money), "Null am Ende zählt nicht")
        assertValue(BigDecimal("12345678.99"), literal(num("12345678.99"), money), "acht Stellen vor dem Komma")
        assertValue(BigDecimal("0"), literal(num("0"), money), "null")
        assertValue(BigDecimal("-0.5"), literal(num("-0.5"), money), "negativ")
        assertInvalid(literal(num("3.555"), money), "Rundung nötig")
        assertInvalid(literal(num("123456789.12"), money), "neun Stellen vor dem Komma")
        assertValue(BigDecimal("0.12345"), literal(num("0.12345"), SqlType.Decimal(5, 5)), "nur Nachkomma")
        assertInvalid(literal(num("1"), SqlType.Decimal(5, 5)), "keine Stelle vor dem Komma frei")
        assertValue(BigDecimal("100"), literal(num("100"), SqlType.Decimal(3, 0)), "Stellen vor dem Komma, Precision 3")
        assertInvalid(literal(num("1000"), SqlType.Decimal(3, 0)), "vier Stellen bei Precision 3")
    }

    /**
     * Negative: eine Zahl geht nur in eine Zahlenspalte (und `0` und `1` in eine Bool-Spalte, Setzung); ein Text
     * nur in eine Textspalte oder, im ISO-Format, in eine Zeitspalte; es gibt keine Überführung zwischen Zahl
     * und Text.
     * @spec SPEC-035
     * @spec SPEC-039
     */
    @Test
    fun `Zahl und Text wechseln nie die Familie`() {
        assertInvalid(literal(num("42"), SqlType.UnicodeText), "Zahl in Text")
        assertInvalid(literal(num("42"), SqlType.Date), "Zahl in Datum")
        assertInvalid(literal(num("42"), SqlType.Time), "Zahl in Zeit")
        assertInvalid(literal(num("42"), SqlType.DateTime), "Zahl in Zeitpunkt")
        assertInvalid(literal(txt("42"), SqlType.Int32), "Text in Int32")
        assertInvalid(literal(txt("42"), SqlType.Int64), "Text in Int64")
        assertInvalid(literal(txt("1.5"), SqlType.Decimal(10, 2)), "Text in Dezimal")
        assertInvalid(literal(txt("true"), SqlType.Bool), "Text in Bool")
        assertInvalid(literal(txt("1"), SqlType.Bool), "Text 1 in Bool")
        assertValue(false, literal(num("0"), SqlType.Bool), "0 in Bool")
        assertValue(true, literal(num("1"), SqlType.Bool), "1 in Bool")
        assertInvalid(literal(num("2"), SqlType.Bool), "2 in Bool")
        assertInvalid(literal(num("-1"), SqlType.Bool), "-1 in Bool")
    }

    /**
     * Happy und Negative: Text, auch mit `N`, geht in eine Textspalte; Datum, Zeit und Zeitpunkt gelten nur im
     * ISO-Format (der Zeitpunkt nur mit `T`), ein unmögliches Datum und andere Schreibweisen sind ungültig.
     * @spec SPEC-035
     * @spec SPEC-039
     */
    @Test
    fun `Datum Zeit und Zeitpunkt nur im ISO-Format`() {
        assertValue("abc", literal(txt("abc"), SqlType.UnicodeText), "Text")
        assertValue("abc", literal(ntxt("abc"), SqlType.UnicodeText), "Text mit N")
        assertValue("", literal(txt(""), SqlType.UnicodeText), "leerer Text")
        assertValue(LocalDate.of(2026, 10, 30), literal(txt("2026-10-30"), SqlType.Date), "Datum")
        assertInvalid(literal(txt("2026-02-30"), SqlType.Date), "unmögliches Datum")
        assertInvalid(literal(txt("30.10.2026"), SqlType.Date), "deutsche Schreibweise")
        assertValue(LocalTime.of(12, 30, 15), literal(txt("12:30:15"), SqlType.Time), "Zeit")
        assertInvalid(literal(txt("25:00:00"), SqlType.Time), "Stunde 25")
        val moment = LocalDateTime.of(2026, 10, 30, 12, 30)
        assertValue(moment, literal(txt("2026-10-30T12:30:00"), SqlType.DateTime), "Zeitpunkt")
        assertInvalid(literal(txt("2026-10-30 12:30:00"), SqlType.DateTime), "Zeitpunkt mit Leerzeichen")
        assertInvalid(literal(txt("2026-10-30"), SqlType.DateTime), "Datum in Zeitpunkt")
        assertInvalid(literal(txt("abc"), SqlType.Date), "Text kein Datum")
    }

    /**
     * Boundary: der Formatraum der Zeitüberführung ist der der JDK-Parser für ISO-Formate (Setzung): Sekunden
     * sind optional, Bruchteile bis zu Nanosekunden gelten, ein Datum mit Vorzeichen und mehr als vier Stellen im
     * Jahr gilt; Zeitzone, Schreibweise mit Punkt und eine Stunde allein gelten nicht.
     * @spec SPEC-035
     * @spec SPEC-039
     */
    @Test
    fun `der Formatraum der Zeit ist der der ISO-Parser`() {
        assertValue(LocalTime.of(12, 30), literal(txt("12:30"), SqlType.Time), "ohne Sekunden")
        val nanos = LocalTime.of(12, 30, 15, 123456789)
        assertValue(nanos, literal(txt("12:30:15.123456789"), SqlType.Time), "Nanosekunden")
        assertValue(LocalDate.of(12026, 1, 1), literal(txt("+12026-01-01"), SqlType.Date), "Jahr mit fünf Stellen")
        assertInvalid(literal(txt("12:30:15+01:00"), SqlType.Time), "Zeitzone")
        assertInvalid(literal(txt("12.30.15"), SqlType.Time), "Punkte")
        assertInvalid(literal(txt("12"), SqlType.Time), "nur Stunde")
        val moment = LocalDateTime.of(2026, 10, 30, 12, 30)
        assertValue(moment, literal(txt("2026-10-30T12:30"), SqlType.DateTime), "Zeitpunkt ohne Sekunden")
    }

    /**
     * Boundary: NULL ist in jedem Spaltentyp gültig und bleibt `null`.
     * @spec SPEC-035
     */
    @Test
    fun `NULL passt in jeden Spaltentyp`() {
        val types = listOf(SqlType.Int32, SqlType.Int64, SqlType.Bool, SqlType.UnicodeText, SqlType.Date, SqlType.Time)
        for (type in types) assertValue(null, literal(SimValue.Null, type), "$type")
        assertValue(null, literal(SimValue.Null, SqlType.Decimal(5, 2)), "Dezimal")
        assertValue(null, literal(SimValue.Null, SqlType.DateTime), "Zeitpunkt")
    }

    private fun par(
        type: SqlType,
        value: Any?,
        name: String = "@p",
        direction: ParameterDirection = ParameterDirection.INPUT,
    ) = QueryParameter(name, type, value, direction)

    private fun via(parameter: QueryParameter, target: SqlType, reference: String = "p"): Converted =
        SimValues.convert(param(reference), column(target), listOf(parameter))

    /**
     * Happy und Negative: ein Parameter geht in dieselbe Familie und wird nur verlustfrei erweitert (Int32 zu
     * Int64 zu Dezimal); die Verengung ist ungültig, auch wenn der Wert passt.
     * @spec SPEC-035
     * @spec SPEC-039
     */
    @Test
    fun `Parameter werden nur erweitert`() {
        assertValue(7, via(par(SqlType.Int32, 7), SqlType.Int32), "Int32 in Int32")
        assertValue(7L, via(par(SqlType.Int32, 7), SqlType.Int64), "Int32 in Int64")
        assertValue(7L, via(par(SqlType.Int64, 7L), SqlType.Int64), "Int64 in Int64")
        assertValue(BigDecimal(7), via(par(SqlType.Int32, 7), SqlType.Decimal(10, 2)), "Int32 in Dezimal")
        assertValue(BigDecimal(7), via(par(SqlType.Int64, 7L), SqlType.Decimal(10, 2)), "Int64 in Dezimal")
        assertInvalid(via(par(SqlType.Int64, 7L), SqlType.Int32), "Int64 in Int32, Wert passt")
        assertInvalid(via(par(SqlType.Decimal(10, 2), BigDecimal("7.00")), SqlType.Int32), "Dezimal in Int32")
        assertInvalid(via(par(SqlType.Decimal(10, 2), BigDecimal("7.00")), SqlType.Int64), "Dezimal in Int64")
        assertInvalid(via(par(SqlType.Int32, 7), SqlType.UnicodeText), "Zahl in Text")
        assertInvalid(via(par(SqlType.UnicodeText, "7"), SqlType.Int32), "Text in Zahl")
        assertInvalid(via(par(SqlType.Date, LocalDate.of(2026, 10, 30)), SqlType.DateTime), "Datum in Zeitpunkt")
        assertInvalid(via(par(SqlType.Bool, true), SqlType.Int32), "Bool in Int32")
        assertInvalid(via(par(SqlType.Int32, 1), SqlType.Bool), "Int32 in Bool")
    }

    /**
     * Boundary: jeder Typ geht unverändert in seine eigene Spalte, mit der JVM-Klasse der Spaltenart.
     * @spec SPEC-035
     */
    @Test
    fun `Parameter gleichen Typs behalten ihre Klasse`() {
        assertValue(true, via(par(SqlType.Bool, true), SqlType.Bool), "Bool")
        assertValue("x", via(par(SqlType.UnicodeText, "x"), SqlType.UnicodeText), "Text")
        val date = LocalDate.of(2026, 10, 30)
        assertValue(date, via(par(SqlType.Date, date), SqlType.Date), "Datum")
        val time = LocalTime.of(1, 2, 3)
        assertValue(time, via(par(SqlType.Time, time), SqlType.Time), "Zeit")
        val moment = LocalDateTime.of(2026, 10, 30, 1, 2, 3)
        assertValue(moment, via(par(SqlType.DateTime, moment), SqlType.DateTime), "Zeitpunkt")
        val money = BigDecimal("1.50")
        assertValue(money, via(par(SqlType.Decimal(10, 2), money), SqlType.Decimal(10, 2)), "Dezimal")
    }

    /**
     * Boundary: ein Dezimal-Parameter wird wie ein Literal gegen Skala und Precision der Spalte gehalten (keine
     * Rundung, kein Überlauf); ein Ganzzahl-Parameter, der nicht in die Dezimalspalte passt, ist ungültig.
     * @spec SPEC-035
     * @spec SPEC-039
     */
    @Test
    fun `Dezimal-Parameter werden gegen die Spalte gehalten`() {
        val money = SqlType.Decimal(10, 2)
        assertValue(BigDecimal("1.500"), via(par(SqlType.Decimal(10, 3), BigDecimal("1.500")), money), "Null am Ende")
        assertInvalid(via(par(SqlType.Decimal(10, 3), BigDecimal("1.505")), money), "Rundung nötig")
        assertInvalid(via(par(SqlType.Decimal(12, 2), BigDecimal("123456789.12")), money), "Überlauf")
        assertInvalid(via(par(SqlType.Int32, 100000), SqlType.Decimal(5, 0)), "Int32 zu groß für Precision 5")
        assertValue(BigDecimal(99999), via(par(SqlType.Int32, 99999), SqlType.Decimal(5, 0)), "Int32 passt")
    }

    /**
     * Boundary und Negative: NULL als Parameter gilt für jeden Spaltentyp (Setzung); ein Wert, dessen Klasse nicht
     * zum Typ des Parameters passt, ist ungültig; die Richtung des Parameters ändert nichts.
     * @spec SPEC-035
     * @spec SPEC-039
     */
    @Test
    fun `NULL-Parameter und falsche Klasse`() {
        assertValue(null, via(par(SqlType.UnicodeText, null), SqlType.Int32), "NULL eines anderen Typs")
        assertValue(null, via(par(SqlType.Int32, null), SqlType.Date), "NULL, Zeitspalte")
        assertInvalid(via(par(SqlType.Int32, 7L), SqlType.Int32), "Long unter Typ Int32")
        assertInvalid(via(par(SqlType.Int32, 7L), SqlType.Int64), "Long unter Typ Int32, Int64-Spalte")
        assertInvalid(via(par(SqlType.UnicodeText, 7), SqlType.UnicodeText), "Int unter Typ Text")
        assertValue(7, via(par(SqlType.Int32, 7, direction = ParameterDirection.OUTPUT), SqlType.Int32), "OUTPUT")
    }

    /**
     * Boundary und Negative: ein Parameter wird über seinen Namen gefunden, ohne Beachtung der Schreibweise
     * und mit dem `@`; ein Parameter, den die Anfrage nicht trägt, ist ungültig.
     * @spec SPEC-035
     * @spec SPEC-036
     * @spec SPEC-039
     */
    @Test
    fun `Parameter werden ueber den Namen gefunden`() {
        val parameters = listOf(par(SqlType.Int32, 1, "@Alpha"), par(SqlType.Int32, 2, "@beta"))
        fun find(name: String) = SimValues.convert(param(name), column(SqlType.Int32), parameters)
        assertValue(1, find("alpha"), "kleingeschrieben")
        assertValue(2, find("BETA"), "großgeschrieben")
        assertInvalid(find("gamma"), "nicht übergeben")
        assertInvalid(find("@alpha"), "mit zweitem @")
        val missing = assertIs<Converted.Invalid>(SimValues.convert(param("x"), column(SqlType.Int32), emptyList()))
        assertEquals("Parameter nicht übergeben", missing.reason)
    }

    /**
     * Die Meldung einer ungültigen Überführung nennt nie den Wert (`TDS-QA-03`).
     * @spec SPEC-039
     */
    @Test
    fun `die Meldung nennt den Wert nicht`() {
        val literal = assertIs<Converted.Invalid>(literal(txt("geheim-4711"), SqlType.Int32))
        val parameter = assertIs<Converted.Invalid>(via(par(SqlType.UnicodeText, "geheim-4711"), SqlType.Int32))
        assertFalse(literal.reason.contains("4711"), literal.reason)
        assertFalse(parameter.reason.contains("4711"), parameter.reason)
        assertTrue(literal.reason.isNotEmpty())
    }

    /**
     * Gleichheit in einer Bedingung: Dezimalzahlen über `compareTo`, Texte ohne Beachtung der Schreibweise
     * (JDK-Regel: `i` gleich `İ`, `ss` ungleich `ß`, nachgestellte Leerzeichen zählen), alles andere über `equals`
     * und nur bei gleicher Klasse. Die Regeln sind Setzungen, nur im JDK gemessen.
     * @spec SPEC-035
     */
    @Test
    fun `Gleichheit nach Typ`() {
        assertTrue(SimValues.same(BigDecimal("1.0"), BigDecimal("1.00")))
        assertFalse(SimValues.same(BigDecimal("1.0"), BigDecimal("1.01")))
        assertTrue(SimValues.same("abc", "ABC"))
        assertFalse(SimValues.same("a", "a "))
        assertFalse(SimValues.same("ss", "ß"))
        assertTrue(SimValues.same("i", "İ"))
        assertTrue(SimValues.same(1, 1))
        assertFalse(SimValues.same(1, 2))
        assertFalse(SimValues.same(1, 1L), "andere Klasse")
        assertFalse(SimValues.same("1", 1))
        assertFalse(SimValues.same(BigDecimal.ONE, 1))
    }
}
