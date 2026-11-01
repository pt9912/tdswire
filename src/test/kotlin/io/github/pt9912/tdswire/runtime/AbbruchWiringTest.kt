package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.FlowBackend
import io.github.pt9912.tdswire.protocol.dispatch.delivering
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.dispatch.suspendedAfter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4

/**
 * Abbruch und Frist über einen Socket auf `127.0.0.1` mit dem Rohbytes-Client und einem Fake-Backend, das nicht
 * antwortet (`TDS-FA-07`, `TDS-QA-07`, `SPEC-029`, `SPEC-009`): ATTENTION während der Anfrage und der Ablauf von
 * `QUERY_TIMEOUT` aus der Umgebung des Prozesses. Gemessen wird der Server dieses Repos, nicht der Referenzclient und
 * nicht ein echter SQL Server. Der Ablauf der Frist braucht eine Wartezeit von einer Sekunde, dem kleinsten Wert der
 * Einstellung.
 */
internal class AbbruchWiringTest : RuntimeTestBase() {
    private val rowAnswer =
        listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1).joinToString(" ")

    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private fun environment(vararg more: Pair<String, String>) =
        loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"), *more)

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

    private fun RawClient.reply(text: String): RawMessage {
        val message = assertNotNull(readMessage(), text)
        assertEquals(TABULAR_TYPE, message.type, text)
        return message
    }

    /**
     * ATTENTION während einer Anfrage, die das Backend nicht beantwortet: die Antwort ist genau ein `DONE[ATTN]`, und
     * die folgende Anfrage derselben Verbindung erhält nur ihre eigene Antwort.
     *
     * @spec SPEC-029
     * @spec TDS-FA-07
     * @spec TDS-QA-07
     */
    @Test
    fun `ATTENTION ueber den Socket wird mit DONE ATTN beantwortet und die Verbindung bleibt offen`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()), delivering(*oneRow))
        val server = running(environment(), Wiring(log = log, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("haengt"))
            backend.collecting.await()
            client.send(handMessage(PacketType.ATTENTION.code, ByteArray(0), 4000))
            assertEquals(BatchVectors.DONE_ATTN, client.reply("Bestaetigung").payload.toHex())
            client.send(batch("danach"))
            assertEquals(rowAnswer, client.reply("Folgeanfrage").payload.toHex())
        }
        assertEquals(1, backend.cancelled.size)
    }

    /**
     * `QUERY_TIMEOUT=1` aus der Umgebung des Prozesses: das Backend antwortet nicht, nach einer Sekunde kommt
     * ERROR 50005 mit Class 16 und DONE mit Fehlerstatus, und die folgende Anfrage derselben Verbindung erhält nur
     * ihre eigene Antwort. Die Frist, die das Backend sieht, liegt mindestens eine Sekunde hinter dem Senden
     * der Anfrage.
     *
     * @spec SPEC-009
     * @spec SPEC-016
     * @spec TDS-FA-07
     */
    @Test
    fun `QUERY_TIMEOUT aus der Umgebung endet in TDS_TIMEOUT bei offener Verbindung`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()), delivering(*oneRow))
        val server = running(
            environment(ConfigKeys.QUERY_TIMEOUT to "1"),
            Wiring(log = log, requests = RequestSource(backend)),
        )
        RawClient(server.port).use { client ->
            login(client)
            val before = System.currentTimeMillis()
            client.send(batch("haengt"))
            val tokens = TokenDecoder(client.reply("Timeout").payload).decodeAll()
            val error = tokens.filterIsInstance<ErrorToken>().single().message
            assertEquals(ServerMessage(50005, 1, 16, "Timeout."), error)
            assertEquals(DoneStatus.ERROR, tokens.filterIsInstance<DoneToken>().single().status)
            val deadline = assertNotNull(backend.requests.single().deadlineEpochMillis)
            assertTrue(deadline - before >= ONE_SECOND, "Frist: ${deadline - before} ms nach dem Senden")
            client.send(batch("danach"))
            assertEquals(rowAnswer, client.reply("Folgeanfrage").payload.toHex())
        }
        assertEquals(1, backend.cancelled.size)
    }

    private companion object {
        const val ONE_SECOND = 1_000L
    }
}
