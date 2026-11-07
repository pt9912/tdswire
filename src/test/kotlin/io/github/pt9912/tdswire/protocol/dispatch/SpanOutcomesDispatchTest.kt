package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.telemetry.RequestKind
import io.github.pt9912.tdswire.domain.telemetry.SessionInstruments
import io.github.pt9912.tdswire.domain.telemetry.Span
import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Ein Span, der jedes Ende mitschreibt; mehr als ein Eintrag heißt: das Ende kam mehrfach an. */
private class RecordedSpan(val requestId: String, val kind: RequestKind?) : Span {
    val outcomes = CopyOnWriteArrayList<SpanOutcome>()

    override fun end(outcome: SpanOutcome) {
        outcomes += outcome
    }
}

/** Beobachtung einer Sitzung, die die Spans von Anfrage und Backend-Aufruf aufbewahrt. */
private class RecordingSession : SessionInstruments {
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

    private fun loop(session: Session, backend: QueryBackend, observed: SessionInstruments, gate: ShutdownGate) =
        RequestLoop(
            session.ready,
            session.channel,
            backend,
            times = fixedTimes(),
            hooks = RequestHooks(Counter()::next, gate = gate, session = observed),
        )

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
        loop(Session.login(BatchVectors.message("spaet")), backend, observed, gate).run()
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
        loop(session, backend, observed, ShutdownGate.NONE).run()
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
}
