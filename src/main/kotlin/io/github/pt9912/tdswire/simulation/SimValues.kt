package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import java.math.BigDecimal
import java.math.BigInteger
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Das Ergebnis der Überführung eines Werts in den Typ einer Spalte (`TDS-FA-11.a` Schritt 4). */
internal sealed interface Converted {
    /** [value] hat die JVM-Klasse der Spaltenart (`ValueMapping`) oder ist `null` für NULL. */
    data class Value(val value: Any?) : Converted

    /** Der Wert ist nicht überführbar; [reason] nennt nie den Wert. */
    data class Invalid(val reason: String) : Converted
}

/**
 * Die Überführung eines Werts aus dem Anweisungstext oder einem Parameter in den Spaltentyp (`SPEC-035`):
 * streng und verlustfrei, nichts am Server gemessen. Die Meldungen nennen nie einen Wert.
 *
 * Festlegungen über `SPEC-035` hinaus (Setzung, Voreinstellung, nicht gemessen): eine Zahl gilt, wenn sie ohne
 * Rest in den Zielwert passt (`1.0` und `100` in einer Ganzzahlspalte, `3.500` in `Decimal(10,2)`); die Zahlen
 * `0` und `1` sind in einer Bool-Spalte falsch und wahr, jede andere Zahl ist ungültig; NULL als Parameter
 * gilt für jeden Spaltentyp; ein Parameter, den die Anfrage nicht trägt, ist ungültig; die Richtung eines
 * Parameters bleibt unbeachtet.
 */
internal object SimValues {
    private const val INVALID = "Wert nicht in den Spaltentyp überführbar"
    private const val MISSING = "Parameter nicht übergeben"
    private const val INT_BITS = Int.SIZE_BITS - 1
    private const val LONG_BITS = Long.SIZE_BITS - 1

    /** [value] in den Typ von [column]; [parameters] sind die der Anfrage. */
    fun convert(value: SimValue, column: SimColumn, parameters: List<QueryParameter>): Converted = when (value) {
        SimValue.Null -> Converted.Value(null)

        is SimValue.Number -> number(value.text, column.type)

        is SimValue.Text -> text(value.value, column.type)

        is SimValue.Parameter -> {
            val key = SimNames.fold("@" + value.name)
            val found = parameters.firstOrNull { SimNames.fold(it.name) == key }
            if (found == null) Converted.Invalid(MISSING) else parameter(found, column.type)
        }
    }

    /**
     * Gleich im Sinn einer Bedingung: Dezimalzahlen über `compareTo`, Texte ohne Beachtung der Schreibweise
     * (die Regel von `String.equals(ignoreCase)`), alles andere über `equals`. Eine Zelle anderer Klasse als der
     * Wert ist ungleich.
     */
    fun same(cell: Any, wanted: Any): Boolean = when {
        cell is BigDecimal && wanted is BigDecimal -> cell.compareTo(wanted) == 0
        cell is String && wanted is String -> cell.equals(wanted, ignoreCase = true)
        else -> cell == wanted
    }

    private fun invalid(): Converted = Converted.Invalid(INVALID)

    private fun number(text: String, type: SqlType): Converted {
        val number = BigDecimal(text)
        return when (type) {
            SqlType.Int32 -> integral(number, INT_BITS)?.let { Converted.Value(it.toInt()) } ?: invalid()
            SqlType.Int64 -> integral(number, LONG_BITS)?.let { Converted.Value(it.toLong()) } ?: invalid()
            SqlType.Bool -> bit(number)
            is SqlType.Decimal -> if (fits(number, type)) Converted.Value(number) else invalid()
            SqlType.UnicodeText, SqlType.Date, SqlType.Time, SqlType.DateTime -> invalid()
        }
    }

    private fun bit(number: BigDecimal): Converted = when {
        number.compareTo(BigDecimal.ZERO) == 0 -> Converted.Value(false)
        number.compareTo(BigDecimal.ONE) == 0 -> Converted.Value(true)
        else -> invalid()
    }

    /** Die ganze Zahl, wenn [number] ohne Rest eine ist und mit höchstens [bits] Bit plus Vorzeichen passt. */
    private fun integral(number: BigDecimal, bits: Int): BigInteger? {
        val whole = number.stripTrailingZeros()
        return if (whole.scale() > 0) null else whole.toBigIntegerExact().takeIf { it.bitLength() <= bits }
    }

    /** Wahr, wenn [number] ohne Rundung und ohne Überlauf in [type] steht; Nullen am Ende der Skala zählen nicht. */
    private fun fits(number: BigDecimal, type: SqlType.Decimal): Boolean {
        if (number.signum() == 0) return true
        val stripped = number.stripTrailingZeros()
        val fraction = maxOf(stripped.scale(), 0)
        val whole = stripped.precision() - stripped.scale()
        return fraction <= type.scale && whole <= type.precision - type.scale
    }

    private fun text(value: String, type: SqlType): Converted = try {
        when (type) {
            SqlType.UnicodeText -> Converted.Value(value)
            SqlType.Date -> Converted.Value(LocalDate.parse(value))
            SqlType.Time -> Converted.Value(LocalTime.parse(value))
            SqlType.DateTime -> Converted.Value(LocalDateTime.parse(value))
            SqlType.Int32, SqlType.Int64, SqlType.Bool, is SqlType.Decimal -> invalid()
        }
    } catch (ignored: DateTimeException) {
        invalid()
    }

    private fun parameter(parameter: QueryParameter, target: SqlType): Converted {
        val value = parameter.value
        return when {
            value == null -> Converted.Value(null)
            !hasClassOf(parameter.type, value) -> invalid()
            target is SqlType.Decimal -> widenToDecimal(value, target)
            parameter.type == target -> Converted.Value(value)
            target == SqlType.Int64 && value is Int -> Converted.Value(value.toLong())
            else -> invalid()
        }
    }

    private fun widenToDecimal(value: Any, target: SqlType.Decimal): Converted {
        val number = when (value) {
            is Int -> BigDecimal(value)
            is Long -> BigDecimal(value)
            is BigDecimal -> value
            else -> null
        }
        return if (number != null && fits(number, target)) Converted.Value(number) else invalid()
    }

    /** Wahr, wenn [value] die JVM-Klasse hat, die `ValueMapping` für [type] festlegt. */
    private fun hasClassOf(type: SqlType, value: Any): Boolean = when (type) {
        SqlType.Int32 -> value is Int
        SqlType.Int64 -> value is Long
        SqlType.Bool -> value is Boolean
        SqlType.UnicodeText -> value is String
        is SqlType.Decimal -> value is BigDecimal
        SqlType.Date -> value is LocalDate
        SqlType.Time -> value is LocalTime
        SqlType.DateTime -> value is LocalDateTime
    }
}
