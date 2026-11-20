package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicBoolean

/** Die Vorbereitung eines Textes mit mehreren Auswahlen (Form S2, `SPEC-036`). */
internal sealed interface PreparedSeries {
    data class Ready(val parts: List<SelectPart>) : PreparedSeries

    data class Rejected(val code: String, val message: String) : PreparedSeries
}

/** Vorbereitung und Ausgabewerte der Auswahlen (Form S1 und Form S2, `SPEC-036`). */
internal object SelectSeries {
    /**
     * Mehrere Auswahlen: eine Antwort mit einem Ergebnis je Auswahl, in der Reihenfolge des Textes. Mit Parametern
     * ist der Text abgewiesen (im Referenzabgleich nicht abgespielt); jede Bedingung ist vor dem Lesen geprüft.
     */
    fun prepare(plans: List<Plan>, parameters: List<QueryParameter>): PreparedSeries {
        val prepared = plans.map {
            RowConditions.prepare((it.statement as Statement.Select).where, it.table, parameters)
        }
        val invalid = prepared.filterIsInstance<PreparedCondition.Invalid>().firstOrNull()
        return when {
            parameters.isNotEmpty() ->
                PreparedSeries.Rejected(SimCodes.UNSUPPORTED_SQL, "Mehrere Anweisungen mit Parametern nicht verfügbar")

            invalid != null -> PreparedSeries.Rejected(SimCodes.INVALID_VALUE, invalid.reason)

            else -> PreparedSeries.Ready(
                plans.zip(prepared).map { (plan, ready) -> SelectPart(plan, ready as PreparedCondition.Ready) },
            )
        }
    }

    /**
     * Der Ausgabewert eines `InputOutput`-Parameters ist sein Eingabewert (Mitschnitt `rpc-ausgabewert`: `@id` mit
     * Wert 7, die Auswahl ändert ihn nicht); ein reiner `OUTPUT`-Parameter bekommt keinen Wert (nicht gemessen).
     */
    fun echo(parameters: List<QueryParameter>): List<QueryEvent> = parameters
        .filter { it.direction == ParameterDirection.INPUT_OUTPUT }
        .map { QueryEvent.OutputParameter(it.name, it.type, it.value) }
}

/** Eine Auswahl des Textes mit ihrer vorbereiteten Bedingung. */
internal class SelectPart(val plan: Plan, val prepared: PreparedCondition.Ready)

/**
 * Eine oder mehrere Auswahlen: öffnet je Auswahl die Zeilenquelle beim Sammeln und liest sie Zeile für Zeile. Die
 * Auswahlen laufen nacheinander; ein Fehler oder der Abbruch beendet den Strom ohne die folgenden. [echo] sind die
 * Ausgabewerte, die nach dem letzten Ergebnis folgen.
 */
internal class SelectExecution(
    private val parts: List<SelectPart>,
    private val echo: List<QueryEvent>,
    private val onEnd: (SelectExecution) -> Unit,
) : QueryExecution,
    Stoppable {
    private val stopped = AtomicBoolean(false)

    override val events: Flow<QueryEvent> = flow { readAll() }.onCompletion { onEnd(this@SelectExecution) }

    override suspend fun close() {
        stop()
        onEnd(this)
    }

    /** Beendet das Lesen vor der nächsten Zeile; geschlossen wird die Quelle vom Strom. */
    override fun stop() {
        stopped.set(true)
    }

    private suspend fun FlowCollector<QueryEvent>.readAll() {
        for (part in parts) {
            if (!read(part)) return
        }
        echo.forEach { emit(it) }
    }

    /** Liest eine Auswahl; `true`, wenn ihr Ergebnis geendet hat und die nächste folgen darf. */
    private suspend fun FlowCollector<QueryEvent>.read(part: SelectPart): Boolean = when {
        stopped.get() -> false

        part.prepared.condition.never -> {
            emit(resultStart(part))
            emit(QueryEvent.ResultEnd(0))
            true
        }

        else -> readSource(part)
    }

    /** Der Anfang des Ergebnisses einer Auswahl: die Spalten der Auswahlliste mit der Länge der Beschreibung. */
    private fun resultStart(part: SelectPart) =
        QueryEvent.ResultStart(part.plan.resultColumns.map { it.toResultColumn() })

    private suspend fun FlowCollector<QueryEvent>.readSource(part: SelectPart): Boolean {
        val table = part.plan.table
        val opened = guarded { table.rows(SimQuery(table.columns, part.prepared.hint)) }.getOrNull()?.let { Opened(it) }
        if (opened == null) {
            emit(sourceFailure())
            return false
        }
        return try {
            emit(resultStart(part))
            pump(part, opened)
        } finally {
            opened.close()
        }
    }

    private suspend fun FlowCollector<QueryEvent>.pump(part: SelectPart, opened: Opened): Boolean {
        val table = part.plan.table
        val projection = part.plan.resultColumns.map { table.columns.indexOf(it) }
        var count = 0L
        var completed = false
        var finished = false
        while (!finished && !stopped.get()) {
            when (val step = step(table, opened.source)) {
                is Step.Row -> if (part.prepared.condition.matches(step.values)) {
                    emit(QueryEvent.Row(projection.map { step.values[it] }))
                    count++
                }

                Step.End -> {
                    opened.close()
                    emit(QueryEvent.ResultEnd(count))
                    finished = true
                    completed = true
                }

                Step.Broken -> {
                    opened.close()
                    emit(sourceFailure())
                    finished = true
                }
            }
        }
        return completed
    }

    private sealed interface Step {
        data class Row(val values: List<Any?>) : Step

        data object End : Step

        data object Broken : Step
    }

    /** Liest die nächste Zeile; eine Zeile mit anderer Wertezahl als Spalten ist ein Fehler der Quelle. */
    private suspend fun step(table: SimTable, source: RowSource): Step = guarded { source.next() }.fold(
        onSuccess = { values ->
            when {
                values == null -> Step.End
                values.size == table.columns.size -> Step.Row(values)
                else -> Step.Broken
            }
        },
        onFailure = { Step.Broken },
    )

    /** Eine geöffnete Zeilenquelle; [close] schließt sie höchstens einmal und ignoriert einen Fehler von `close()`. */
    private class Opened(val source: RowSource) {
        private val closed = AtomicBoolean(false)

        fun close() {
            if (closed.compareAndSet(false, true)) guarded { source.close() }
        }
    }

    private fun sourceFailure() = QueryEvent.Failure(SimCodes.BACKEND_ERROR, "Zeilenquelle fehlgeschlagen")
}
