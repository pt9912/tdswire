package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Prüfung der Ereignisreihenfolge (Festlegung: Verstoß ist ein Fall von `SPEC-048`) und Zufallsfolgen. */
class ResponseOrderTest {
    private val internalError = ErrorToken(INTERNAL_MESSAGE)
    private val errorDone = done(DoneStatus.ERROR)

    private fun sequence(sink: RecordingSink, backend: ScriptedBackend): SequenceOutcome =
        runBlocking { ResponseSequencer(FIXED_MAPPING).run(backend, REQUEST, sink) }

    /**
     * Jeder Verstoß gegen die Reihenfolge endet in ERROR mit der festen Meldung, DONE mit
     * Fehlerstatus und dem Ausgang „Verbindung schließen“; `close()` läuft einmal.
     *
     * @spec SPEC-048 SPEC-031
     */
    @Test
    fun `Reihenfolgeverstoss endet in TDS_INTERNAL_ERROR`() {
        val violations = mapOf(
            "Row vor ResultStart" to listOf(row(1)),
            "doppeltes ResultEnd" to listOf(start(), QueryEvent.ResultEnd(0), QueryEvent.ResultEnd(0)),
            "ResultEnd ohne ResultStart" to listOf(QueryEvent.ResultEnd(0)),
            "ResultStart im offenen Ergebnis" to listOf(start(), row(1), start()),
            "UpdateCount im offenen Ergebnis" to listOf(start(), QueryEvent.UpdateCount(1)),
            "Row nach ResultEnd" to listOf(start(), QueryEvent.ResultEnd(0), row(1)),
            "Strom endet im offenen Ergebnis" to listOf(start(), row(1)),
        )
        for ((name, events) in violations) {
            val backend = ScriptedBackend.ofList(*events.toTypedArray())
            val sink = RecordingSink()
            assertEquals(SequenceOutcome.FAILED_CLOSE_CONNECTION, sequence(sink, backend), name)
            assertEquals(listOf<Token>(internalError, errorDone), sink.tokens.takeLast(2), name)
            assertEquals(1, backend.closeCalls, name)
        }
    }

    /**
     * Kein zweiter Resultset-Anfang nach einer Teilantwort (`SPEC-031`): ein zweites `ResultStart` im
     * offenen Ergebnis schreibt kein zweites COLMETADATA.
     *
     * @spec SPEC-031
     */
    @Test
    fun `kein zweites COLMETADATA nach einer Teilantwort`() {
        val backend = ScriptedBackend.ofList(start(), row(1), start(), row(2))
        val sink = RecordingSink()
        sequence(sink, backend)
        assertEquals(1, sink.tokens.count { it is ColMetadataToken })
        assertEquals(1, sink.tokens.count { it is RowToken })
    }

    /**
     * Ein Verstoß, der auf ein zurückgehaltenes `ResultEnd` folgt, zählt als nächstes Ereignis:
     * `DONE_MORE`, ERROR, DONE mit Fehlerstatus.
     *
     * @spec SPEC-048 SPEC-031
     */
    @Test
    fun `Verstoss nach ResultEnd gibt dem Abschluss DONE_MORE`() {
        val backend = ScriptedBackend.ofList(start(), QueryEvent.ResultEnd(0), row(1))
        val sink = RecordingSink()
        sequence(sink, backend)
        assertEquals(listOf<Token>(done(COUNT_MORE, 0), internalError, errorDone), sink.tokens.drop(1))
    }

    private enum class Ending { END, THROW, HANG }

    private class Plan(val events: List<QueryEvent>, val ending: Ending, val rejectAt: Int?)

    /** Erwartete Tokenformen; `outcome` ist `null`, wenn der Lauf abgebrochen wird. */
    private class Expected(val shapes: List<String>, val outcome: SequenceOutcome?)

    private val widePool = ResultColumn("t", SqlType.UnicodeText, nullable = true)
    private val tooPrecise = ResultColumn("d", SqlType.Decimal(precision = 40, scale = 0), nullable = false)

    private fun randomPlan(random: Random): Plan {
        val pool = listOf(
            start(), row(1), row(2), QueryEvent.Row(listOf("x")), QueryEvent.Row(emptyList()),
            QueryEvent.Row(listOf<Any?>(null)), QueryEvent.ResultStart(listOf(widePool)),
            QueryEvent.ResultStart(emptyList()), QueryEvent.ResultStart(listOf(tooPrecise)),
            QueryEvent.ResultEnd(1), QueryEvent.ResultEnd(null), QueryEvent.UpdateCount(2),
            QueryEvent.Failure("x", "m"), QueryEvent.ReturnStatus(1),
            QueryEvent.OutputParameter("p", SqlType.Int32, 1),
        )
        val events = List(random.nextInt(0, MAX_EVENTS)) { pool[random.nextInt(pool.size)] }
        val ending = Ending.entries[random.nextInt(Ending.entries.size)]
        val rejectAt = if (random.nextInt(REJECT_ONE_IN) == 0) random.nextInt(1, MAX_EVENTS * 2 + 2) else null
        return Plan(events, ending, rejectAt)
    }

    /**
     * Das Modell der erlaubten Ereignisfolgen, getrennt vom Sequenzierer geschrieben und ohne
     * Vorschau: ein DONE hat `MORE`, wenn danach noch ein Ereignis oder die Ausnahme des Stroms
     * kommt; erlaubt ist (Ergebnis | Update-Count)* mit höchstens einem abschließenden `Failure`,
     * alles andere ist ein Verstoß; eine Ausnahme des Stroms ist ein interner Fehler; bei `HANG`
     * wird ein zurückgehaltener Abschluss verworfen; die Senke lehnt das `rejectAt`-te Token ab.
     */
    private inner class Model(private val plan: Plan) {
        private val shapes = mutableListOf<String>()
        private var open: List<ResultColumn>? = null
        private var held = false
        private var outcome: SequenceOutcome? = null

        fun expected(): Expected {
            for ((index, event) in plan.events.withIndex()) {
                held = false
                step(event, more = index + 1 < plan.events.size || plan.ending == Ending.THROW)
                if (outcome != null) break
            }
            if (outcome == null) endOfStream()
            val rejectAt = plan.rejectAt
            if (rejectAt != null && rejectAt <= shapes.size) {
                return Expected(shapes.take(rejectAt - 1), SequenceOutcome.CLOSE_WITHOUT_COMPLETION)
            }
            return Expected(shapes, outcome)
        }

        private fun step(event: QueryEvent, more: Boolean) {
            val columns = open
            when (event) {
                is QueryEvent.ResultStart -> start(event, columns != null)
                is QueryEvent.Row -> if (columns == null) violation() else row(columns, event)
                is QueryEvent.ResultEnd -> if (columns == null) violation() else end(event.affectedRows, more)
                is QueryEvent.UpdateCount -> if (columns != null) violation() else count(event.affectedRows, more)
                is QueryEvent.Failure -> fail(BACKEND_MESSAGE.number, SequenceOutcome.FAILED)
                else -> fail(TYPE_MESSAGE.number, SequenceOutcome.FAILED)
            }
        }

        private fun start(event: QueryEvent.ResultStart, alreadyOpen: Boolean) {
            if (alreadyOpen) return violation()
            if (event.columns.any { it.type.isTooPrecise() }) return fail(TYPE_MESSAGE.number, SequenceOutcome.FAILED)
            shapes += "META"
            open = event.columns
        }

        private fun row(columns: List<ResultColumn>, event: QueryEvent.Row) {
            if (fits(columns, event.values)) shapes += "ROW" else fail(TYPE_MESSAGE.number, SequenceOutcome.FAILED)
        }

        private fun end(rows: Long?, more: Boolean) {
            open = null
            count(rows, more)
        }

        private fun count(rows: Long?, more: Boolean) {
            val base = if (rows != null) DoneStatus.COUNT else DoneStatus.FINAL
            shapes += "DONE:${base or (if (more) DoneStatus.MORE else 0)}:${rows ?: 0}"
            held = true
        }

        private fun endOfStream() {
            when (plan.ending) {
                Ending.THROW -> violation()
                Ending.END -> endOfScript()
                Ending.HANG -> if (held) shapes.removeLast()
            }
        }

        private fun endOfScript() {
            if (open != null) return violation()
            if (plan.events.isEmpty()) shapes += "DONE:${DoneStatus.FINAL}:0"
            outcome = SequenceOutcome.COMPLETED
        }

        private fun violation() = fail(INTERNAL_MESSAGE.number, SequenceOutcome.FAILED_CLOSE_CONNECTION)

        private fun fail(message: Int, result: SequenceOutcome) {
            shapes += "ERROR:$message"
            shapes += "DONE:${DoneStatus.ERROR}:0"
            outcome = result
        }
    }

    private fun model(plan: Plan): Expected = Model(plan).expected()

    private fun SqlType.isTooPrecise(): Boolean = this is SqlType.Decimal && precision > MAX_DECIMAL_PRECISION

    private fun fits(columns: List<ResultColumn>, values: List<Any?>): Boolean =
        columns.size == values.size && columns.zip(values).all { (column, value) ->
            when {
                value == null -> column.nullable
                column.type == SqlType.Int32 -> value is Int
                column.type == SqlType.UnicodeText -> value is String
                else -> false
            }
        }

    private fun shape(token: Token): String = when (token) {
        is ColMetadataToken -> "META"
        is RowToken -> "ROW"
        is DoneToken -> "DONE:${token.status}:${token.rowCount}"
        is ErrorToken -> "ERROR:${token.message.number}"
        else -> "?"
    }

    private fun streamOf(plan: Plan) = flow {
        plan.events.forEach { emit(it) }
        when (plan.ending) {
            Ending.THROW -> error("geheim")
            Ending.HANG -> awaitCancellation()
            Ending.END -> Unit
        }
    }

    /**
     * Zufällige Ereignisfolgen mit festem Startwert gegen ein getrenntes Modell (`model`): Tokenfolge
     * und Ausgang stimmen mit dem Modell überein, auch mit Ausnahme im Strom, mit Abbruch der
     * Coroutine und mit einer Senke, die ein Token ablehnt; `close()` läuft stets genau einmal und
     * zu Ende, nie entsteht eine Ausnahme nach außen. Das Modell ist ein zweiter Leser derselben
     * Festlegungen, keine Messung und kein Handvektor.
     *
     * @spec SPEC-031 SPEC-048
     */
    @Test
    fun `Zufallsfolgen stimmen mit dem Modell der erlaubten Folgen ueberein`() {
        val random = Random(SEED)
        val seen = mutableSetOf<SequenceOutcome?>()
        repeat(RUNS) { index ->
            val plan = randomPlan(random)
            val expected = model(plan)
            val backend = FlowBackend { streamOf(plan) }
            val sink = RecordingSink(plan.rejectAt)
            var outcome: SequenceOutcome? = null
            val label = "Lauf $index: ${plan.events} ${plan.ending} reject=${plan.rejectAt}"
            val cancelled = runBlocking {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    outcome = ResponseSequencer(FIXED_MAPPING).run(backend, REQUEST, sink)
                }
                job.cancel()
                job.join()
                job.isCancelled
            }
            seen += outcome
            assertEquals(expected.outcome, outcome, label)
            assertEquals(expected.outcome == null, cancelled, label)
            assertEquals(expected.shapes, sink.tokens.map(::shape), label)
            assertEquals(1, backend.closeCalls, label)
            assertEquals(1, backend.closesCompleted, label)
            val encoder = TokenEncoder()
            sink.tokens.forEach { assertIs<ProtocolResult.Ok<Unit>>(encoder.write(it), label) }
        }
        assertEquals(
            setOf(
                null,
                SequenceOutcome.COMPLETED,
                SequenceOutcome.FAILED,
                SequenceOutcome.FAILED_CLOSE_CONNECTION,
                SequenceOutcome.CLOSE_WITHOUT_COMPLETION,
            ),
            seen,
        )
    }

    private companion object {
        const val SEED = 20261030L
        const val RUNS = 4000
        const val MAX_EVENTS = 8
        const val REJECT_ONE_IN = 3
        const val MAX_DECIMAL_PRECISION = 38
    }
}
