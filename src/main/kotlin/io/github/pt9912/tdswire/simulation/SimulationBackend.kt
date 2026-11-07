package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Das Simulations-Backend hinter [QueryBackend] (`TDS-FA-11.a`): es beantwortet eine erkannte Auswahl (S1)
 * aus der Zeilenquelle der beschriebenen Tabelle und gibt Einfügen, Ändern und Löschen (W1 bis W3) an die Callbacks
 * der Tabelle weiter ([SimulationChanges]); es kennt weder Pakete noch Tokens. Eine Tabelle ohne Callback für die
 * Anweisung antwortet mit `TDS_UNSUPPORTED_SQL`.
 *
 * Form C (`SPEC-036`) beantwortet das Backend ohne Tabelle: `SELECT 1` mit einer Spalte und einer Zeile, eine
 * `SET`-Anweisung und die Transaktionstexte mit dem Änderungszähler 0. Eine Auswahl mit einem Konstantenterm, der nie
 * gilt (`WHERE 1 = 0`), liefert Spaltenmetadaten und keine Zeile, ohne die Zeilenquelle zu öffnen.
 *
 * Form B (`SPEC-036`, Massenladen) erkennt das Backend als `INSERT BULK` und als die Proben davor (`SET FMTONLY ON …`,
 * Kollation); die Nachrichten 0x07 lädt es über [BulkLoads] in `SimTable.onInsertBatch` oder `onInsert`, ohne etwas zu
 * speichern (`runtime` schaltet das Laden für dieses Backend ein, ohne dass der Integrator etwas tut).
 *
 * Form K (`SPEC-036`) beantwortet die Katalogabfragen von d-migrate aus dem Katalog ([CatalogAnswers]); der Katalog
 * kommt bei jeder Anfrage frisch aus dem Anbieter des Konstruktors, das Backend hält keinen Stand. Form D
 * (`CREATE TABLE`, `ALTER TABLE … ADD`) ruft den Strukturänderungs-Callback mit der Tabelle nach der Anweisung und
 * antwortet mit dem Änderungszähler 0; das Backend nimmt nichts zurück, mehrere Callbacks sind keine Transaktion
 * (`ADR-0033`). Der Konstruktor mit festem Katalog hat keinen Callback: Form D ist dort abgewiesen
 * (`TDS_UNSUPPORTED_SQL`), ebenso mit dem Anbieter und ohne Callback.
 *
 * Form M (`SPEC-036`, `MERGE … OUTPUT $action`) zerlegt das Backend in ein Lesen über die Zeilenquelle und höchstens
 * einen Callback ([SimulationMerges]); die Antwort ist das Ergebnis `$action` mit der Zeile `INSERT` oder `UPDATE`.
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
public class SimulationBackend(
    /** Liefert bei jeder Anfrage die aktuelle Tabellenliste; wirft er, endet die Anfrage mit `TDS_BACKEND_ERROR`. */
    private val catalog: () -> SimCatalog,
    /**
     * Nimmt die Tabelle nach einer Strukturänderung entgegen: Schema, Name, Spalten in der Reihenfolge und Schlüssel;
     * ohne ihn ist jede Strukturänderung abgewiesen.
     */
    private val onStructure: (suspend (String, String, List<SimColumn>, List<String>) -> Unit)? = null,
) : QueryBackend {
    /** Das Backend mit festem Katalog; es nimmt weder Strukturänderungen an noch ändert sich sein Katalog. */
    public constructor(catalog: SimCatalog) : this({ catalog })

    private val planner = StatementPlanner(catalog)
    private val running = ConcurrentHashMap<Pair<String, String>, Stoppable>()
    private val structureLock = Mutex()

    override suspend fun execute(request: QueryRequest): QueryExecution =
        when (val result = guarded { planner.plan(request.sql) }.getOrNull()) {
            null -> failed(SimCodes.BACKEND_ERROR, "Katalog nicht lieferbar")
            is PlanResult.Rejected -> failed(result.code, result.message)
            is PlanResult.Planned -> start(result.plan, request)
            is PlanResult.Fixed -> events(FixedAnswers.of(result.form))
            is PlanResult.Catalog -> events(CatalogAnswers.of(result.form, result.catalog, parameters(result, request)))
            is PlanResult.Structure -> structure(result.plan, request)
            is PlanResult.Bulk -> events(bulkAnswer(result.result))
        }

    /** Ein Katalogtext mit Literal statt Parameter (`SPEC-036`, Form K) trägt sein Argument als einzigen Parameter. */
    private fun parameters(result: PlanResult.Catalog, request: QueryRequest): List<QueryParameter> =
        result.literal?.let { listOf(QueryParameter("", SqlType.UnicodeText, it)) } ?: request.parameters

    /** `INSERT BULK` als Text: die Anweisung selbst ruft keinen Callback und zählt 0 (Setzung, nicht gemessen). */
    private fun bulkAnswer(result: BulkTargetResult): List<QueryEvent> = when (result) {
        is BulkTargetResult.Ready -> listOf(QueryEvent.UpdateCount(0))
        is BulkTargetResult.Rejected -> listOf(QueryEvent.Failure(result.code, result.message))
    }

    /**
     * Erkennt `INSERT BULK` (Form B); die Fähigkeit `BulkLoadingBackend` stellt `runtime` darüber bereit. Nur ein Text
     * dieser Form fragt den Katalog-Anbieter; jeder andere ergibt `null` und geht den üblichen Weg über [execute].
     */
    internal fun bulkTarget(sql: String): BulkTargetResult? {
        if (StatementParser.recognize(sql) !is Recognition.Bulk) return null
        return when (val result = guarded { planner.plan(sql) }.getOrNull()) {
            is PlanResult.Bulk -> result.result
            else -> BulkTargetResult.Rejected(SimCodes.BACKEND_ERROR, "Katalog nicht lieferbar")
        }
    }

    /** Lädt die Zeilen einer Nachricht 0x07 ([BulkLoads]); wirft für keinen Fehler der Anfrage. */
    internal suspend fun load(target: BulkTarget, rows: BulkRows): QueryEvent {
        val table = guarded { catalog().find(target.schema, target.table) }
        return when {
            table.isFailure -> QueryEvent.Failure(SimCodes.BACKEND_ERROR, "Katalog nicht lieferbar")
            table.getOrNull() == null -> QueryEvent.Failure(SimCodes.UNKNOWN_OBJECT, "Tabelle nicht beschrieben")
            else -> BulkLoads.load(requireNotNull(table.getOrNull()), rows)
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) {
        running[sessionId to requestId]?.stop()
    }

    private fun start(plan: Plan, request: QueryRequest): QueryExecution {
        val key = request.sessionId to request.requestId
        return when (val statement = plan.statement) {
            is Statement.Select ->
                when (val prepared = RowConditions.prepare(statement.where, plan.table, request.parameters)) {
                    is PreparedCondition.Invalid -> failed(SimCodes.INVALID_VALUE, prepared.reason)

                    is PreparedCondition.Ready ->
                        register(key, SelectExecution(plan, prepared) { running.remove(key, it) })
                }

            is Statement.Merge -> when (val prepared = SimulationMerges.prepare(plan, request.parameters)) {
                is PreparedMerge.Rejected -> failed(prepared.code, prepared.message)
                is PreparedMerge.Ready -> register(key, MergeExecution(prepared) { running.remove(key, it) })
            }

            else -> when (val change = SimulationChanges.prepare(plan, request.parameters)) {
                is PreparedChange.Rejected -> failed(change.code, change.message)
                is PreparedChange.Ready -> register(key, ChangeExecution(change) { running.remove(key, it) })
            }
        }
    }

    private fun <E> register(key: Pair<String, String>, execution: E): E where E : QueryExecution, E : Stoppable {
        running[key] = execution
        return execution
    }

    /**
     * Die Antwort steht fest (Form C und Form K, `SPEC-036`): es wird weder eine Zeilenquelle noch ein Callback
     * gerufen.
     */
    private fun events(answer: List<QueryEvent>): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> = answer.asFlow()

        override suspend fun close() = Unit
    }

    /**
     * Form D (`SPEC-036`): ruft den Strukturänderungs-Callback genau einmal beim Sammeln und antwortet mit dem
     * Änderungszähler 0. Ohne Callback ist die Anweisung abgewiesen. Prüfen und Ändern sind je Backend atomar: beim
     * Sammeln plant das Backend den Text unter einer Sperre gegen den dann aktuellen Katalog erneut und ruft den
     * Callback mit diesem Stand; zwei gleichzeitige Strukturänderungen laufen nacheinander, die zweite sieht die
     * erste (`CREATE TABLE` derselben Tabelle ist dann abgewiesen wie bei einer schon beschriebenen Tabelle). Die
     * Sperre gilt für dieses Backend; ein Anbieter, den ein anderer Schreiber ändert, bekommt sie nicht.
     *
     * Grenze (Lesen des Codes, nicht gemessen): die Sperre ist ein [Mutex] und nicht reentrant. Ein Callback, der
     * dasselbe Backend aufruft und die Antwort abwartet, hängt; ein hängender Callback hält die Sperre und blockiert
     * alle weiteren Strukturänderungen dieses Backends, bis sein Sammler abbricht.
     */
    private fun structure(plan: StructurePlan, request: QueryRequest): QueryExecution {
        val callback = onStructure ?: return failed(SimCodes.UNSUPPORTED_SQL, "Strukturänderung nicht verfügbar")
        val key = request.sessionId to request.requestId
        val call = PreparedChange.Ready {
            structureLock.withLock {
                when (val again = planner.plan(request.sql)) {
                    is PlanResult.Structure -> {
                        callback(again.plan.schema, again.plan.name, again.plan.columns, again.plan.primaryKey)
                        0L
                    }

                    is PlanResult.Rejected -> throw ChangeRejected(again.code, again.message)

                    else -> -1L
                }
            }
        }
        return register(key, ChangeExecution(call) { running.remove(key, it) })
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
