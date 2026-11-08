package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.telemetry.RequestKind
import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import io.github.pt9912.tdswire.port.BulkLoadingBackend
import io.github.pt9912.tdswire.port.BulkProbeResult
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Spans `tds.query` und `backend.execute` auf dem Weg des Massenladens (Pakettyp 0x07, `SPEC-036`): jede Anfrage
 * endet genau einmal, das Paket zählt als `sql_batch` (Setzung), und das `outcome` folgt der Antwort. Gegenstelle
 * ohne Netz, wie `BulkDispatchTest`.
 */
class SpanOutcomesBulkTest {
    private val insertBulk = "INSERT BULK [dbo].[t] ([Id] BIGINT , [Name] NVARCHAR(4000) ) with (CHECK_CONSTRAINTS)"

    private fun bulk(payload: String = THREE_ROWS): Inbound =
        Inbound.Message(TdsMessage(PacketType.BULK_LOAD, hex(payload)))

    private class Loading(private val onLoad: suspend (BulkRows) -> QueryEvent) :
        QueryBackend,
        BulkLoadingBackend {
        override fun bulkTarget(sql: String): BulkTargetResult? =
            BulkTargetResult.Ready(BulkTarget("dbo", "t")).takeIf { sql.startsWith("INSERT BULK") }

        override fun bulkProbe(sql: String): BulkProbeResult? = null

        override suspend fun load(target: BulkTarget, rows: BulkRows): QueryEvent = onLoad(rows)

        override suspend fun execute(request: QueryRequest): QueryExecution = object : QueryExecution {
            override val events: Flow<QueryEvent> = flowOf(QueryEvent.UpdateCount(7))

            override suspend fun close() = Unit
        }

        override suspend fun cancel(sessionId: String, requestId: String) = Unit
    }

    private fun loop(session: Session, backend: QueryBackend, observed: RecordingSession) = RequestLoop(
        session.ready,
        session.channel,
        backend,
        times = fixedTimes(),
        hooks = RequestHooks(Counter()::next, session = observed),
    )

    private fun RecordingSession.queryOutcomes() = requests.map { it.outcomes.toList() }

    /**
     * `INSERT BULK` und die Nachricht 0x07 enden je mit `ok`, beide als `sql_batch`; jede Anfrage hat genau ein Ende,
     * und zu jeder gehört ein Backend-Aufruf, der ebenfalls genau einmal endet.
     *
     * @spec SPEC-018
     * @spec SPEC-036
     */
    @Test
    fun `Massenladen endet ok und je Anfrage genau einmal`() = blocking {
        val observed = RecordingSession()
        val session = Session.login(BatchVectors.message(insertBulk), bulk(), Inbound.EndOfStream)
        loop(session, Loading { QueryEvent.UpdateCount(it.rows.size.toLong()) }, observed).run()
        assertEquals(listOf(listOf(SpanOutcome.OK), listOf(SpanOutcome.OK)), observed.queryOutcomes())
        assertEquals(listOf(RequestKind.SQL_BATCH, RequestKind.SQL_BATCH), observed.requests.map { it.kind })
        assertEquals(observed.requests.map { it.requestId }, observed.backends.map { it.requestId })
        val backendEnds = observed.backends.map { it.outcomes.toList() }
        assertEquals(listOf(listOf(SpanOutcome.OK), listOf(SpanOutcome.OK)), backendEnds)
    }

    /**
     * Eine abgeschnittene Nachricht 0x07 weist der Parser ab: `tds.query` endet mit `protocol_error`, das Backend lädt
     * nicht und bekommt keinen Span für diese Anfrage.
     *
     * @spec SPEC-018
     * @spec SPEC-036
     */
    @Test
    fun `Eine zerlegte Nachricht 0x07 endet mit protocol_error`() = blocking {
        val observed = RecordingSession()
        val cut = THREE_ROWS.substring(0, THREE_ROWS.length / 4 * 2)
        val session = Session.login(BatchVectors.message(insertBulk), bulk(cut), Inbound.EndOfStream)
        loop(session, Loading { QueryEvent.UpdateCount(0) }, observed).run()
        assertEquals(listOf(listOf(SpanOutcome.OK), listOf(SpanOutcome.PROTOCOL_ERROR)), observed.queryOutcomes())
        assertEquals(1, observed.backends.size, "nur INSERT BULK hat einen Backend-Span")
    }

    /**
     * Antwortet das Backend auf die Ladung mit einem Fehler (etwa verletztem Schlüssel), endet `tds.query` mit `error`
     * und der Backend-Aufruf mit `error`.
     *
     * @spec SPEC-018
     * @spec SPEC-019
     */
    @Test
    fun `Ein Fehler des Backends beim Laden endet mit error`() = blocking {
        val observed = RecordingSession()
        val failing = Loading { QueryEvent.Failure("TDS_BACKEND_ERROR", "Schluessel verletzt") }
        val session = Session.login(BatchVectors.message(insertBulk), bulk(), Inbound.EndOfStream)
        loop(session, failing, observed).run()
        assertEquals(listOf(SpanOutcome.OK), observed.requests[0].outcomes.toList())
        assertEquals(listOf(SpanOutcome.ERROR), observed.requests[1].outcomes.toList())
        assertEquals(listOf(SpanOutcome.ERROR), observed.backends[1].outcomes.toList())
    }

    /**
     * ATTENTION während der Ladung bricht sie ab: `tds.query` endet mit `cancelled`, genau einmal.
     *
     * @spec SPEC-018
     * @spec SPEC-029
     */
    @Test
    fun `ATTENTION waehrend der Ladung endet mit cancelled`() = blocking {
        val observed = RecordingSession()
        val started = CompletableDeferred<Unit>()
        val hanging = Loading {
            started.complete(Unit)
            CompletableDeferred<QueryEvent>().await()
        }
        val session = Session.login(BatchVectors.message(insertBulk), bulk())
        val serving = launch { loop(session, hanging, observed).run() }
        started.await()
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(listOf(SpanOutcome.OK), observed.requests[0].outcomes.toList())
        assertEquals(listOf(SpanOutcome.CANCELLED), observed.requests[1].outcomes.toList())
    }

    private companion object {
        /** Mitschnitt von mssql-jdbc, siehe `BulkLoadReaderTest`. */
        const val THREE_ROWS =
            "81030000000000000026080249006400000000000100e7401f0904d00034044e0061006d006500000000000100" +
                "a70a000904d0003408450069006e00740072006900740074" +
                "00d10801000000000000000e004100640061002000dc006e00ef000a00323032362d30312d3032" +
                "d1080200000000000000ffffffffd10803000000000000000e004300e400630069006c00690065000a" +
                "00323032352d31322d3331fd000000000000000000000000"
    }
}
