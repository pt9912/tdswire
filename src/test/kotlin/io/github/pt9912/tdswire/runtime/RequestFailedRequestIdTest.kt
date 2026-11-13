package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.RequestFailed
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.FlowBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire
import io.github.pt9912.tdswire.protocol.dispatch.delivering
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.dispatch.suspendedAfter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.eventually
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4
private const val RPC_TYPE = 0x03

/**
 * Die Kennung der Anfrage im Ereignis `REQUEST_FAILED` am Socket (`TDS-FA-09.a`, `SPEC-018`): der Server läuft im
 * Prozess, der Rohbytes-Client spricht PRELOGIN, LOGIN7 und Anfragen, die Senke ist der Sammler. Die Anfragen laufen
 * nacheinander auf einer Verbindung; jede wartet auf ihre Antwort, bevor die nächste beginnt.
 */
internal class RequestFailedRequestIdTest : RuntimeTestBase() {
    private fun environment(vararg more: Pair<String, String>) = loopbackEnvironment(
        file("users.txt", "alice\t{plain}secret\n"),
        *more,
    )

    private fun login(client: RawClient) {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    private fun batch(text: String) = handMessage(BATCH_TYPE, BatchVectors.payload(text), 4000)

    private fun RawClient.reply(what: String) {
        assertEquals(TABULAR_TYPE, assertNotNull(readMessage(), what).type, what)
    }

    private fun spans(name: String): List<TelemetryEvent> =
        sink.events.filter { it.kind == EventKind.SPAN_ENDED && it.span == name }

    /**
     * Sechs Anfragen einer Sitzung, eine davon mit Erfolg: Fehler des Backends, Erfolg, abgewiesener RPC (doppelter
     * Parametername), Freigabe eines unbekannten Handles, Ablauf der Frist und abgewiesene Nachricht (Parser, sie
     * schließt die Verbindung und steht deshalb zuletzt). Jedes
     * `REQUEST_FAILED` trägt die Kennung der Anfrage, die auch das Ende von `tds.query` trägt (beim Fehler des Backends
     * und beim Ablauf der Frist auch die des Backend-Aufrufs); die Kennung der Anfrage mit Erfolg erscheint in keinem
     * `REQUEST_FAILED`, und keines trägt `null`. Wächter: eine andere Kennung als die der Anfrage, die Kennung der
     * Vorgängerin, `null`.
     *
     * @spec SPEC-018
     * @spec SPEC-048
     * @spec TDS-FA-09
     */
    @Test
    fun `REQUEST_FAILED traegt die Kennung der Anfrage, die auch tds_query traegt`() = blocking {
        val gate = CompletableDeferred<Unit>()
        val failure = QueryEvent.Failure("TDS_BACKEND_ERROR", "kein Text fuer den Client")
        val backend = FlowBackend(
            delivering(failure),
            delivering(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1)),
            suspendedAfter(gate),
        )
        val server = running(
            environment(ConfigKeys.QUERY_TIMEOUT to "1"),
            Wiring(telemetry = sink, requests = RequestSource(backend)),
        )
        val duplicate = RpcWire.executeSql(
            "select 1",
            "d",
            RpcWire.param("@p", RpcWire.nvarchar("x")),
            RpcWire.param("@p", RpcWire.nvarchar("x")),
        )
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("backend-fehler"))
            client.reply("backend-fehler")
            client.send(batch("ergebnis"))
            client.reply("ergebnis")
            client.send(handMessage(RPC_TYPE, duplicate, 4000))
            client.reply("rpc")
            client.send(batch("EXEC sp_unprepare 99"))
            client.reply("handle")
            client.send(batch("haengt"))
            client.reply("timeout")
            client.send(handMessage(BATCH_TYPE, ByteArray(3), 4000))
            client.reply("parser")
            eventually("sechs Anfragen beendet") { spans("tds.query").size == 6 }
        }
        val queries = spans("tds.query")
        val failed = sink.of<RequestFailed>()
        assertEquals(
            listOf(
                "TDS_BACKEND_ERROR",
                "TDS_UNSUPPORTED_RPC",
                "TDS_SIM_UNKNOWN_PREPARED_HANDLE",
                "TDS_TIMEOUT",
                "TDS_PROTOCOL_ERROR",
            ),
            failed.map { it.errorCode },
        )
        assertEquals(listOf(0, 2, 3, 4, 5).map { queries[it].requestId }, failed.map { it.requestId })
        assertEquals(6, queries.map { it.requestId }.toSet().size)
        assertTrue(failed.none { it.requestId == queries[1].requestId })
        assertEquals(backend.requests[0].requestId, failed[0].requestId)
        assertEquals(backend.requests[2].requestId, failed[3].requestId)
        gate.complete(Unit)
    }
}
