package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Das Simulations-Backend hinter [QueryBackend] (`TDS-FA-11.a`): es beantwortet eine erkannte Auswahl (S1)
 * aus der Zeilenquelle der beschriebenen Tabelle und gibt Einfügen, Ändern und Löschen (W1 bis W3) an die Callbacks
 * der Tabelle weiter ([SimulationChanges]); es kennt weder Pakete noch Tokens. Eine Tabelle ohne Callback für die
 * Anweisung antwortet mit `TDS_UNSUPPORTED_SQL`.
 *
 * Form C (`SPEC-036`) beantwortet das Backend ohne Tabelle: `SELECT 1` mit einer Spalte und einer Zeile, eine
 * `SET`-Anweisung mit dem Änderungszähler 0. Eine Auswahl mit einem Konstantenterm, der nie gilt (`WHERE 1 = 0`),
 * liefert Spaltenmetadaten und keine Zeile, ohne die Zeilenquelle zu öffnen.
 *
 * Zusagen für die Auswahl (Festlegung, nicht am Server gemessen): `execute` wirft für keinen Fehler der
 * Anfrage, jeder erscheint als einziges [QueryEvent.Failure] im Strom, bevor eine Zeilenquelle gelesen wird.
 * Der Strom beginnt mit [QueryEvent.ResultStart] (auch ohne Treffer), liefert die passenden Zeilen und endet
 * mit [QueryEvent.ResultEnd] und der Zahl der gelieferten Zeilen. Die Zeilenquelle wird erst beim Sammeln
 * geöffnet und `next()` nur gerufen, wenn der Sammler die vorige Zeile abgenommen hat. Eine geöffnete Quelle
 * schließt das Backend genau einmal: nach dem Ende, einem Fehler der Quelle und dem Abbruch des Sammlers; nach
 * [QueryExecution.close] und [cancel] endet der Strom vor der nächsten Zeile und schließt sie dann ebenso.
 * Ein Fehler beim Öffnen der Quelle ist ein [QueryEvent.Failure] ohne Metadaten, einer danach das letzte
 * Ereignis des Stroms (jeweils `TDS_BACKEND_ERROR` mit fester Meldung, nie dem Text der Ausnahme). Ein [Error]
 * und die Abbruchausnahme der Coroutine fängt das Backend nicht.
 *
 * Grenze: [cancel] und [QueryExecution.close] beenden den Strom vor dem Lesen der nächsten Zeile; ein
 * `next()`, das selbst hängt, endet nur mit dem Abbruch des Sammlers (die Frist: offen). Gespeichert wird
 * nichts, und eine Ausführung ist für genau einen Sammler gedacht. Der Aufrufer sammelt den Strom oder ruft
 * [QueryExecution.close]: bis dahin bleibt die Ausführung unter Sitzung und Anfrage registriert (für [cancel]); eine
 * zweite Ausführung mit demselben Schlüssel ersetzt die Registrierung der ersten.
 */
public class SimulationBackend(catalog: SimCatalog) : QueryBackend {
    private val planner = StatementPlanner(catalog)
    private val running = ConcurrentHashMap<Pair<String, String>, Stoppable>()

    override suspend fun execute(request: QueryRequest): QueryExecution =
        when (val result = planner.plan(request.sql)) {
            is PlanResult.Rejected -> failed(result.code, result.message)
            is PlanResult.Planned -> start(result.plan, request)
            is PlanResult.Fixed -> fixed(result.form)
        }

    override suspend fun cancel(sessionId: String, requestId: String) {
        running[sessionId to requestId]?.stop()
    }

    private fun start(plan: Plan, request: QueryRequest): QueryExecution {
        val key = request.sessionId to request.requestId
        val select = plan.statement as? Statement.Select
        return if (select == null) {
            when (val change = SimulationChanges.prepare(plan, request.parameters)) {
                is PreparedChange.Rejected -> failed(change.code, change.message)
                is PreparedChange.Ready -> register(key, ChangeExecution(change) { running.remove(key, it) })
            }
        } else {
            when (val prepared = RowConditions.prepare(select.where, plan.table, request.parameters)) {
                is PreparedCondition.Invalid -> failed(SimCodes.INVALID_VALUE, prepared.reason)
                is PreparedCondition.Ready -> register(key, SelectExecution(plan, prepared) { running.remove(key, it) })
            }
        }
    }

    private fun <E> register(key: Pair<String, String>, execution: E): E where E : QueryExecution, E : Stoppable {
        running[key] = execution
        return execution
    }

    /** Form C (`SPEC-036`): die Antwort steht fest, es wird weder eine Zeilenquelle noch ein Callback gerufen. */
    private fun fixed(form: FixedForm): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> = FixedAnswers.of(form).asFlow()

        override suspend fun close() = Unit
    }

    private fun failed(code: String, message: String): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> = flowOf(QueryEvent.Failure(code, message))

        override suspend fun close() = Unit
    }
}

/** Eine Auswahl: öffnet die Zeilenquelle beim Sammeln und liest sie Zeile für Zeile. */
private class SelectExecution(
    private val plan: Plan,
    private val prepared: PreparedCondition.Ready,
    private val onEnd: (SelectExecution) -> Unit,
) : QueryExecution,
    Stoppable {
    private val stopped = AtomicBoolean(false)
    private val table = plan.table
    private val projection = plan.resultColumns.map { table.columns.indexOf(it) }

    override val events: Flow<QueryEvent> = flow { read() }.onCompletion { onEnd(this@SelectExecution) }

    override suspend fun close() {
        stop()
        onEnd(this)
    }

    /** Beendet das Lesen vor der nächsten Zeile; geschlossen wird die Quelle vom Strom. */
    override fun stop() {
        stopped.set(true)
    }

    private suspend fun FlowCollector<QueryEvent>.read() {
        if (stopped.get()) return
        if (prepared.condition.never) {
            emit(QueryEvent.ResultStart(plan.resultColumns.map { ResultColumn(it.name, it.type, it.nullable) }))
            emit(QueryEvent.ResultEnd(0))
            return
        }
        val opened = guarded { table.rows(SimQuery(table.columns, prepared.hint)) }.getOrNull()?.let { Opened(it) }
        if (opened == null) {
            emit(sourceFailure())
        } else {
            try {
                emit(QueryEvent.ResultStart(plan.resultColumns.map { ResultColumn(it.name, it.type, it.nullable) }))
                pump(opened)
            } finally {
                opened.close()
            }
        }
    }

    private suspend fun FlowCollector<QueryEvent>.pump(opened: Opened) {
        var count = 0L
        var finished = false
        while (!finished && !stopped.get()) {
            when (val step = step(opened.source)) {
                is Step.Row -> if (prepared.condition.matches(step.values)) {
                    emit(QueryEvent.Row(projection.map { step.values[it] }))
                    count++
                }

                Step.End -> {
                    opened.close()
                    emit(QueryEvent.ResultEnd(count))
                    finished = true
                }

                Step.Broken -> {
                    opened.close()
                    emit(sourceFailure())
                    finished = true
                }
            }
        }
    }

    private sealed interface Step {
        data class Row(val values: List<Any?>) : Step

        data object End : Step

        data object Broken : Step
    }

    /** Liest die nächste Zeile; eine Zeile mit anderer Wertezahl als Spalten ist ein Fehler der Quelle. */
    private suspend fun step(source: RowSource): Step = guarded { source.next() }.fold(
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

/**
 * Ruft [block] und liefert sein Ergebnis oder die Ausnahme. Die Abbruchausnahme der Coroutine und jeder
 * [Error] (etwa `OutOfMemoryError`) werden weitergeworfen: sie gehören nicht zu den Fehlern der Zeilenquelle.
 */
internal inline fun <T> guarded(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException || it is Error) throw it }
