package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.telemetry.RequestKind
import io.github.pt9912.tdswire.domain.telemetry.SessionInstruments
import io.github.pt9912.tdswire.domain.telemetry.Span
import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.CancelCause
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/** Ein Span, der jedes Ende mitschreibt; mehr als ein Eintrag heißt: das Ende kam mehrfach an. */
internal class RecordedSpan(val requestId: String, val kind: RequestKind?) : Span {
    val outcomes = CopyOnWriteArrayList<SpanOutcome>()

    override fun end(outcome: SpanOutcome) {
        outcomes += outcome
    }
}

/** Beobachtung einer Sitzung, die die Spans von Anfrage und Backend-Aufruf aufbewahrt. */
internal class RecordingSession : SessionInstruments {
    val requests = CopyOnWriteArrayList<RecordedSpan>()
    val backends = CopyOnWriteArrayList<RecordedSpan>()

    override fun request(requestId: String, kind: RequestKind): Span =
        RecordedSpan(requestId, kind).also { requests += it }

    override fun backend(requestId: String): Span = RecordedSpan(requestId, null).also { backends += it }
}

/**
 * Das `outcome` der Spans `tds.query` und `backend.execute` an den Stellen der Anfrageschleife, ohne Netz (`SPEC-018`,
 * `SPEC-019`): jede Stelle, die die Antwort bestimmt, nennt dem Span ihr Ende, genau einmal, mit der Kennung der
 * Anfrage, die auch das Backend sieht. Die Verdrahtung am Socket mit `ok`, `error`, `cancelled`, `timeout`,
 * `disconnected` und `protocol_error` belegt `MetricsTracingWiringTest`.
 */
class SpanOutcomesDispatchTest {
    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private fun loop(
        session: Session,
        backend: QueryBackend,
        observed: SessionInstruments,
        times: RequestTimes = fixedTimes(),
        sinkAround: (TokenSink) -> TokenSink = { it },
    ) = RequestLoop(
        session.ready,
        session.channel,
        backend,
        times = times,
        hooks = RequestHooks(Counter()::next, sinkAround, session = observed),
    )

    private class RejectingSink(private val inner: TokenSink) : TokenSink {
        private var seen = 0

        override suspend fun accept(token: Token): Boolean = if (++seen == 2) false else inner.accept(token)
    }

    private fun rpc(payload: String) = BatchVectors.raw(PacketType.RPC, hex(payload))

    /**
     * Eine Anfrage nach dem Beginn des Beendens endet mit `shutdown`, ohne dass das Backend einen Span bekommt.
     *
     * @spec SPEC-018
     * @spec SPEC-044
     */
    @Test
    fun `Eine Anfrage nach dem Beginn des Beendens endet mit shutdown`() = blocking {
        val observed = RecordingSession()
        val backend = FlowBackend(delivering(*oneRow))
        val gate = object : ShutdownGate {
            override val begun = true

            override fun requestRunning(running: Boolean) = Unit

            override fun onGraceExpired(abort: suspend () -> Unit) = Unit
        }
        val session = Session.login(BatchVectors.message("spaet"))
        val hooks = RequestHooks(Counter()::next, gate = gate, session = observed)
        RequestLoop(session.ready, session.channel, backend, times = fixedTimes(), hooks = hooks).run()
        assertEquals(listOf(listOf(SpanOutcome.SHUTDOWN)), observed.requests.map { it.outcomes.toList() })
        assertEquals(0, observed.backends.size)
        assertEquals(0, backend.requests.size)
    }

    /**
     * Die Freigabe von Handles und die RPC-Abweisungen enden ohne Backend-Aufruf: `sp_prepexec` und die Freigabe eines
     * gehaltenen Handles mit `ok`, die zweite Freigabe derselben Nummer und `sp_execute` mit unbekanntem Handle mit
     * `error`, eine Nachricht, die der Parser abweist, mit `protocol_error`. Die Art ist `rpc` für jede RPC-Nachricht
     * und `sql_batch` für die Freigabe; die Kennung ist die der Anfrage (`r1` bis `r5`).
     *
     * @spec SPEC-018
     * @spec SPEC-030
     */
    @Test
    fun `Handle-Freigabe und RPC-Abweisung nennen ihr Ende`() = blocking {
        val observed = RecordingSession()
        val backend = FlowBackend(delivering(*oneRow))
        val session = Session.login(
            rpc(RpcVectors.PREP_EXEC),
            BatchVectors.message("EXEC sp_unprepare 1"),
            BatchVectors.message("EXEC sp_unprepare 1"),
            rpc(RpcVectors.EXECUTE),
            BatchVectors.raw(PacketType.RPC, ByteArray(3)),
        )
        loop(session, backend, observed).run()
        assertEquals(
            listOf("r1", "r2", "r3", "r4", "r5"),
            observed.requests.map { it.requestId },
        )
        assertEquals(
            listOf(RequestKind.RPC, RequestKind.SQL_BATCH, RequestKind.SQL_BATCH, RequestKind.RPC, RequestKind.RPC),
            observed.requests.map { it.kind },
        )
        assertEquals(
            listOf(
                SpanOutcome.OK,
                SpanOutcome.OK,
                SpanOutcome.ERROR,
                SpanOutcome.ERROR,
                SpanOutcome.PROTOCOL_ERROR,
            ),
            observed.requests.map { it.outcomes.single() },
        )
        assertEquals(listOf("r1"), observed.backends.map { it.requestId }, "nur sp_prepexec ruft das Backend")
        assertEquals(listOf("r1"), backend.requests.map { it.requestId })
    }

    private class Scripted(private val execution: () -> QueryExecution) : QueryBackend {
        override suspend fun execute(request: QueryRequest): QueryExecution = execution()

        override suspend fun cancel(sessionId: String, requestId: String) = Unit
    }

    private fun execution(events: Flow<QueryEvent>) = object : QueryExecution {
        override val events: Flow<QueryEvent> = events

        override suspend fun close() = Unit
    }

    private val request = QueryRequest("s1", "r9", "select 1", emptyList(), null)

    /**
     * Der Span `backend.execute` endet mit `close` der Ausführung: `ok`, wenn der Strom ohne Fehlerereignis endete,
     * `error` bei einem Fehlerereignis oder einem Fehler des Stroms, `cancelled`, wenn die Ausführung vor dem Ende des
     * Stroms geschlossen wird; vor `close` endet nichts. Die Hülle ändert die Ereignisse nicht.
     *
     * @spec SPEC-019
     */
    @Test
    fun `backend_execute endet mit close der Ausfuehrung`() = blocking {
        val failure = QueryEvent.Failure("TDS_BACKEND_ERROR", "")
        val cases = listOf(
            Triple("ok", flowOf(*oneRow), true),
            Triple("error", flowOf(failure), true),
            Triple("error", flow<QueryEvent> { throw IllegalStateException("strom kaputt") }, true),
            Triple("cancelled", flow<QueryEvent> { emit(resultStart()) }, false),
        )
        for ((expected, stream, collected) in cases) {
            val observed = RecordingSession()
            val backend = Scripted { execution(stream) }.observed(observed)
            val running = backend.execute(request)
            assertEquals(listOf("r9"), observed.backends.map { it.requestId })
            assertEquals(emptyList(), observed.backends.single().outcomes.toList(), "vor close endet nichts")
            if (collected) runCatching { running.events.collect { } }
            running.close()
            val span = observed.backends.single()
            assertEquals(listOf(expected), span.outcomes.map { it.name.lowercase() })
        }
        val observed = RecordingSession()
        val events = Scripted { execution(flowOf(*oneRow)) }.observed(observed).execute(request).events.toList()
        assertEquals(oneRow.toList(), events)
    }

    /**
     * Wirft `execute` selbst, endet der Span mit `error` und die Ausnahme erreicht den Aufrufer; bricht die Coroutine
     * ab, endet er mit `cancelled`. Ohne beobachtete Sitzung ist das Backend die Hülle nicht wert: dieselbe Instanz.
     *
     * @spec SPEC-019
     */
    @Test
    fun `Ein Fehler oder Abbruch von execute beendet den Span`() = blocking {
        val observed = RecordingSession()
        val throwing = Scripted { throw IllegalStateException("start gescheitert") }.observed(observed)
        assertFailsWith<IllegalStateException> { throwing.execute(request) }
        assertEquals(listOf(SpanOutcome.ERROR), observed.backends.single().outcomes.toList())

        val cancelled = RecordingSession()
        val cancelling = Scripted { throw CancellationException("weg") }.observed(cancelled)
        assertFailsWith<CancellationException> { cancelling.execute(request) }
        assertEquals(listOf(SpanOutcome.CANCELLED), cancelled.backends.single().outcomes.toList())

        val plain = Scripted { execution(flowOf()) }
        assertSame(plain, plain.observed(SessionInstruments.NONE))
    }

    /**
     * Die Abbildung des Laufendes auf das `outcome` von `tds.query`, Fall für Fall: der Ausgang des Sequenzierers, der
     * Auslöser des Abbruchs, wenn die Antwort nicht vollständig war, der Ausgang der Antwort, wenn sie es war, und die
     * Enden ohne Antwort.
     *
     * @spec SPEC-018
     */
    @Test
    fun `Das Ende des Laufs ergibt das outcome von tds_query`() {
        assertEquals(SpanOutcome.OK, RunEnd.Finished(SequenceOutcome.COMPLETED).spanOutcome())
        assertEquals(SpanOutcome.ERROR, RunEnd.Finished(SequenceOutcome.FAILED).spanOutcome())
        assertEquals(SpanOutcome.ERROR, RunEnd.Finished(SequenceOutcome.FAILED_CLOSE_CONNECTION).spanOutcome())
        assertEquals(SpanOutcome.CLOSED, RunEnd.Finished(SequenceOutcome.CLOSE_WITHOUT_COMPLETION).spanOutcome())
        assertEquals(SpanOutcome.CANCELLED, RunEnd.Cut(CancelCause.ATTENTION, null).spanOutcome())
        assertEquals(SpanOutcome.TIMEOUT, RunEnd.Cut(CancelCause.DEADLINE, null).spanOutcome())
        assertEquals(SpanOutcome.SHUTDOWN, RunEnd.Cut(CancelCause.SHUTDOWN, null).spanOutcome())
        val unfinished = SequenceOutcome.CLOSE_WITHOUT_COMPLETION
        assertEquals(SpanOutcome.CANCELLED, RunEnd.Cut(CancelCause.ATTENTION, unfinished).spanOutcome())
        assertEquals(SpanOutcome.OK, RunEnd.Cut(CancelCause.ATTENTION, SequenceOutcome.COMPLETED).spanOutcome())
        assertEquals(SpanOutcome.ERROR, RunEnd.Cut(CancelCause.DEADLINE, SequenceOutcome.FAILED).spanOutcome())
        assertEquals(SpanOutcome.ABANDONED, RunEnd.Abandoned.spanOutcome())
        assertEquals(SpanOutcome.DISCONNECTED, RunEnd.Disconnected.spanOutcome())
    }

    /**
     * Ein Backend, das den Abbruch ignoriert: nach `CANCEL_TIMEOUT` endet `tds.query` mit `abandoned`, der Span
     * `backend.execute` ist dann noch offen und endet erst, wenn das Backend zurückkehrt (hier mit `ok`: sein Strom
     * endet regulär, auch wenn es den Abbruch ignorierte).
     *
     * @spec SPEC-018
     * @spec SPEC-019
     * @spec SPEC-033
     */
    @Test
    fun `Ein Backend, das den Abbruch ignoriert, endet tds_query mit abandoned und backend_execute spaeter`() =
        blocking {
            val pause = SteeredPause()
            val gate = CompletableDeferred<Unit>()
            val backend = FlowBackend(ignoringCancel(gate, resultStart()))
            val observed = RecordingSession()
            val times = RequestTimes(30.seconds, 5.seconds, now = { FIXED_NOW }, pause = pause::pause)
            withGate(gate) {
                val session = Session.login(BatchVectors.message("stuck"))
                val serving = launch { loop(session, backend, observed, times = times).run() }
                backend.collecting.await()
                session.channel.feed(BatchVectors.attention())
                pause.awaitWaiting(5.seconds)
                pause.fire(5.seconds)
                serving.join()
                assertEquals(listOf(SpanOutcome.ABANDONED), observed.requests.single().outcomes.toList())
                assertEquals(emptyList(), observed.backends.single().outcomes.toList(), "Backend-Aufruf noch offen")
                gate.complete(Unit)
                awaitUntil { observed.backends.single().outcomes.isNotEmpty() }
                assertEquals(listOf(SpanOutcome.OK), observed.backends.single().outcomes.toList())
            }
        }

    /**
     * Endet die Anfrage ohne Antwort (die Senke nimmt ein Token nicht an, `CLOSE_WITHOUT_COMPLETION`), endet
     * `tds.query` mit `closed`; ebenso, wenn die Coroutine der Schleife mitten in der Anfrage abgebrochen wird (die
     * Voreinstellung der Stelle in `answer`).
     *
     * @spec SPEC-018
     * @spec SPEC-031
     */
    @Test
    fun `Eine Anfrage ohne vollstaendige Antwort endet mit closed`() = blocking {
        val rejected = RecordingSession()
        val oneRowBackend = FlowBackend(delivering(*oneRow))
        val session = Session.login(BatchVectors.message("q"))
        loop(session, oneRowBackend, rejected, sinkAround = { RejectingSink(it) }).run()
        assertEquals(listOf(SpanOutcome.CLOSED), rejected.requests.single().outcomes.toList())

        val cancelled = RecordingSession()
        val gate = CompletableDeferred<Unit>()
        val hanging = FlowBackend(suspendedAfter(gate))
        withGate(gate) {
            val running = Session.login(BatchVectors.message("haengt"))
            val serving = launch { loop(running, hanging, cancelled).run() }
            hanging.collecting.await()
            serving.cancelAndJoin()
        }
        assertEquals(listOf(SpanOutcome.CLOSED), cancelled.requests.single().outcomes.toList())
    }
}
