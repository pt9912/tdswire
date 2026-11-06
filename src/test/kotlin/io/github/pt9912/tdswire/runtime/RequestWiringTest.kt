package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.telemetry.RequestFailed
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4

/**
 * Der Handler des Prozesses mit einem Backend (`ARC-005`, `ARC-008`): Listener, Handshake und Anfrageschleife
 * über einen Socket auf `127.0.0.1`, mit dem Rohbytes-Client (kein Referenzclient) und dem Test-Backend.
 */
internal class RequestWiringTest : RuntimeTestBase() {
    private fun environment() = loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"))

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

    /**
     * Zwei Batches auf einer Verbindung werden über den Socket beantwortet (Handvektor der Antwort); das
     * Backend sieht Sitzung, zwei verschiedene Anfrage-IDs und die Texte.
     *
     * @spec SPEC-001
     * @spec SPEC-031
     */
    @Test
    fun `Zwei Batches auf einer Verbindung werden beantwortet`() = blocking {
        val backend = ScriptedBackend.ofList(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
        val server = running(environment(), Wiring(telemetry = sink, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            login(client)
            val expected = listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1)
                .joinToString(" ")
            listOf("select 1", "select 2").forEach { text ->
                client.send(batch(text))
                val reply = assertNotNull(client.readMessage(), text)
                assertEquals(TABULAR_TYPE, reply.type, text)
                assertEquals(expected, reply.payload.toHex(), text)
            }
        }
        assertEquals(listOf("select 1", "select 2"), backend.requests.map { it.sql })
        assertEquals(2, backend.requests.map { it.requestId }.toSet().size)
        assertEquals(1, backend.requests.map { it.sessionId }.toSet().size)
    }

    /**
     * Ein schließender Ausgang (Typfehler) schließt die Verbindung am Socket nach der Fehlerantwort; ein
     * zweiter Batch bleibt unbeantwortet. Das Log nennt den Code, nicht den Text des Backends.
     *
     * @spec SPEC-031
     * @spec SPEC-048
     */
    @Test
    fun `Ein schliessender Ausgang schliesst die Verbindung am Socket`() = blocking {
        val backend = ScriptedBackend.ofList(QueryEvent.OutputParameter("p", SqlType.Int32, 1))
        val server = running(environment(), Wiring(telemetry = sink, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("q"))
            val reply = assertNotNull(client.readMessage())
            assertEquals(TABULAR_TYPE, reply.type)
            assertTrue(reply.payload.toHex().endsWith(BatchVectors.DONE_ERROR))
            assertTrue(client.closedByServerWithoutBytes())
        }
        assertEquals(1, backend.requests.size)
        val failed = sink.of<RequestFailed>().single { it.errorCode == "TDS_UNSUPPORTED_TYPE" }
        assertEquals(1L, failed.connectionId)
        assertNotNull(failed.sessionId)
        assertEquals("tds-7.4:not_supported", failed.clientProfile)
    }

    /**
     * Die Batch-Freigabe mit einem Handle, das die Sitzung nicht hält, ergibt am Socket ERROR 8179 (Class 16, State 8)
     * und DONE mit Fehlerstatus, ohne das Backend zu rufen, und genau ein Ereignis `REQUEST_FAILED` mit dem Code
     * `TDS_SIM_UNKNOWN_PREPARED_HANDLE`; die Verbindung bleibt offen und beantwortet die nächste Anfrage. Wächter: eine
     * Nummer außerhalb der Ereignisabbildung, ein Backend-Aufruf, ein Verbindungsende.
     *
     * @spec SPEC-060
     * @spec SPEC-059
     */
    @Test
    fun `Die Freigabe eines unbekannten Handles ergibt 8179 am Socket und ein Ereignis`() = blocking {
        val backend = ScriptedBackend.ofList(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
        val server = running(environment(), Wiring(telemetry = sink, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("EXEC sp_unprepare 99"))
            val reply = assertNotNull(client.readMessage())
            assertEquals(TABULAR_TYPE, reply.type)
            val tokens = TokenDecoder(reply.payload).decodeAll()
            assertEquals(
                ServerMessage(8179, 8, 16, "Could not find prepared statement with handle 99."),
                tokens.filterIsInstance<ErrorToken>().single().message,
            )
            assertTrue(reply.payload.toHex().endsWith(BatchVectors.DONE_ERROR))
            client.send(batch("select 1"))
            assertTrue(assertNotNull(client.readMessage()).payload.toHex().endsWith(BatchVectors.DONE_COUNT_1))
        }
        assertEquals(listOf("select 1"), backend.requests.map { it.sql })
        assertEquals(
            listOf("TDS_SIM_UNKNOWN_PREPARED_HANDLE"),
            sink.of<RequestFailed>().map { it.errorCode },
        )
    }
}
