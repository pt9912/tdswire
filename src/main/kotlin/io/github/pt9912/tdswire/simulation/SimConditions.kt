package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryParameter

/** Ein Term, den das Backend auf eine Zeile anwendet; [matches] liest die Zelle an einem festen Index. */
internal sealed interface RowTerm {
    fun matches(values: List<Any?>): Boolean

    /** `=` und `IN`: die Zelle ist nicht NULL und gleich einem der Werte; NULL in [wanted] trifft nie. */
    class In(private val index: Int, private val wanted: List<Any?>) : RowTerm {
        override fun matches(values: List<Any?>): Boolean {
            val cell = values[index] ?: return false
            return wanted.any { it != null && SimValues.same(cell, it) }
        }
    }

    /** `IS NULL` und mit [negated] `IS NOT NULL`. */
    class IsNull(private val index: Int, private val negated: Boolean) : RowTerm {
        override fun matches(values: List<Any?>): Boolean = (values[index] == null) != negated
    }
}

/** Die Bedingung einer Auswahl: alle Terme gelten gemeinsam (UND), ohne Terme trifft jede Zeile. */
internal class RowCondition(private val terms: List<RowTerm>) {
    fun matches(values: List<Any?>): Boolean = terms.all { it.matches(values) }
}

/** Das Ergebnis der Vorbereitung einer Bedingung (`TDS-FA-11.a` Schritt 4). */
internal sealed interface PreparedCondition {
    /** [condition] wendet das Backend an; [hint] ist der Vorfilter, den es der Zeilenquelle mitgibt. */
    data class Ready(val condition: RowCondition, val hint: RowFilter?) : PreparedCondition

    /** Ein Wert der Bedingung ist nicht überführbar. */
    data class Invalid(val reason: String) : PreparedCondition
}

/**
 * Bereitet die Bedingung einer Auswahl vor: jeder Wert wird in den Typ seiner Spalte überführt, bevor eine
 * Zeile gelesen wird. Der Vorfilter nennt die Spalten wie die Beschreibung und die überführten Werte.
 */
internal object RowConditions {
    private sealed interface Built {
        data class Done(val term: RowTerm, val hint: FilterTerm) : Built

        data class Failed(val reason: String) : Built
    }

    fun prepare(where: List<Term>, table: SimTable, parameters: List<QueryParameter>): PreparedCondition {
        val terms = ArrayList<RowTerm>(where.size)
        val hints = ArrayList<FilterTerm>(where.size)
        for (term in where) {
            val column = requireNotNull(table.column(term.column)) { "Spalte ist nach der Objektprüfung bekannt" }
            when (val built = of(term, column, table.columns.indexOf(column), parameters)) {
                is Built.Done -> {
                    terms += built.term
                    hints += built.hint
                }

                is Built.Failed -> return PreparedCondition.Invalid(built.reason)
            }
        }
        return PreparedCondition.Ready(RowCondition(terms), if (hints.isEmpty()) null else RowFilter(hints))
    }

    private fun of(term: Term, column: SimColumn, index: Int, given: List<QueryParameter>): Built = when (term) {
        is Term.IsNull -> Built.Done(RowTerm.IsNull(index, term.negated), FilterTerm.IsNull(column.name, term.negated))
        is Term.In -> buildIn(term, column, index, given)
    }

    private fun buildIn(term: Term.In, column: SimColumn, index: Int, parameters: List<QueryParameter>): Built {
        val wanted = ArrayList<Any?>(term.values.size)
        for (value in term.values) {
            wanted += when (val converted = SimValues.convert(value, column, parameters)) {
                is Converted.Value -> converted.value
                is Converted.Invalid -> return Built.Failed(converted.reason)
            }
        }
        return Built.Done(RowTerm.In(index, wanted), FilterTerm.In(column.name, wanted))
    }
}
