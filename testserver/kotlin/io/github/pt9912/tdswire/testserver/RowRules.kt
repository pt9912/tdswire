package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.simulation.FilterTerm
import io.github.pt9912.tdswire.simulation.RowFilter
import io.github.pt9912.tdswire.simulation.SimColumn
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Die Regeln des Bestands für einzelne Werte und Zeilen (`TDS-FA-12`): die Prüfung eines Werts gegen seine Spalte und
 * das Zutreffen eines Filters auf eine Zeile. Der Filter wird wie der Vorfilter der Zeilenquelle gelesen: `=` und `IN`
 * treffen nie NULL, Texte werden ohne Beachtung der Schreibweise verglichen, Dezimalzahlen mit `compareTo` (Setzung,
 * am echten Server nicht gemessen).
 */
internal object RowRules {
    private const val UBYTE_MAX = 255

    /** `SimColumn.maxLength` einer `nvarchar(max)`-Spalte. */
    private const val UNBOUNDED_TEXT = -1

    /** Höchste Länge eines Textes im Ergebnis einer Auswahl; die Länge, die `nvarchar(max)` dort meldet. */
    private const val RESULT_TEXT_LIMIT = 4000

    /**
     * Wirft, wenn [value] nicht in [column] steht: NULL nur in nullbaren Spalten, sonst die Klasse der Spaltenart
     * (`Int16` und `UInt8`: ein `Int` im Wertebereich); ein Text steht nur bis zu `SimColumn.maxLength`
     * UTF-16-Einheiten in der Spalte, bei `nvarchar(max)` (-1) bis 4000, der Länge, die eine Auswahl dafür meldet. Die
     * Bibliothek meldet die Ausnahme beim Massenladen als 2627 und beim `UPDATE` als `TDS_BACKEND_ERROR`; der Bestand
     * hält so nur Text, den eine Auswahl ausliefert (Setzung, am echten Server nicht gemessen).
     */
    fun check(column: SimColumn, value: Any?) {
        if (value == null) {
            require(column.nullable) { "NULL in nicht nullbarer Spalte" }
        } else {
            require(hasClassOf(column.type, value)) { "Wert nicht vom Typ der Spalte" }
            require(fitsLength(column, value)) { "Text länger als die Spalte" }
        }
    }

    /**
     * Kopplung: dieselbe Regel und Zählung wie `SimulationMerges.truncates` und `SimColumn.reportedLength` der
     * Bibliothek (`internal`, aus diesem Modul nicht lesbar); ändert sich dort die Zählung oder die Länge für -1,
     * ändert sich diese Stelle mit (`TableStoreTest`).
     */
    private fun fitsLength(column: SimColumn, value: Any): Boolean {
        if (column.type != SqlType.UnicodeText) return true
        val limit = if (column.maxLength == UNBOUNDED_TEXT) RESULT_TEXT_LIMIT else column.maxLength
        return (value as String).length <= limit
    }

    /** Wahr, wenn [where] auf [row] (Werte in der Reihenfolge von [columns]) zutrifft; `null` trifft jede Zeile. */
    fun matches(columns: List<SimColumn>, row: List<Any?>, where: RowFilter?): Boolean =
        where == null || where.terms.all { term ->
            when (term) {
                is FilterTerm.In -> {
                    val cell = row[indexOf(columns, term.column)]
                    cell != null && term.values.any { it != null && same(cell, it) }
                }

                is FilterTerm.IsNull -> (row[indexOf(columns, term.column)] == null) != term.negated
            }
        }

    private fun indexOf(columns: List<SimColumn>, name: String): Int =
        columns.indexOfFirst { NameFold.fold(it.name) == NameFold.fold(name) }

    private fun same(cell: Any, wanted: Any): Boolean = when {
        cell is BigDecimal && wanted is BigDecimal -> cell.compareTo(wanted) == 0
        cell is String && wanted is String -> cell.equals(wanted, ignoreCase = true)
        else -> cell == wanted
    }

    private fun hasClassOf(type: SqlType, value: Any): Boolean = when (type) {
        SqlType.UInt8 -> value is Int && value in 0..UBYTE_MAX
        SqlType.Int16 -> value is Int && value in Short.MIN_VALUE..Short.MAX_VALUE
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
