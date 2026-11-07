package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4
private const val MIN_MESSAGE_BYTES = "65535"
private const val BELOW_LIMIT_CHARS = 10_000
private const val ABOVE_LIMIT_CHARS = 40_000

/**
 * `MAX_MESSAGE_BYTES` wirkt am laufenden Server (`SPEC-007`): nach dem Login schließt der Server eine Verbindung, deren
 * Nachricht die eingestellte Summe der Paketlängen überschreitet, und beantwortet eine Nachricht darunter. Die Grenze
 * vor dem Login ist `MAX_PRELOGIN_MESSAGE_BYTES` (`SPEC-046`) und gehört nicht hierher. Rohbytes-Client, kein
 * Referenzclient.
 */
internal class MessageLimitWiringTest : RuntimeTestBase() {
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

    private fun batch(chars: Int) = handMessage(BATCH_TYPE, BatchVectors.payload("x".repeat(chars)), 4000)

    private fun backend() = ScriptedBackend.ofList(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    /**
     * Mit `MAX_MESSAGE_BYTES` 65535 wird ein Batch von 20 KB beantwortet; einer von 80 KB, dessen Paketlängen die
     * Grenze überschreiten, schließt die Verbindung ohne Antwort, und das Backend sieht ihn nicht. Mit der
     * Voreinstellung (16 MiB) wird derselbe Batch von 80 KB beantwortet: die Grenze kommt aus dem Schlüssel.
     *
     * @spec SPEC-007
     */
    @Test
    fun `MAX_MESSAGE_BYTES begrenzt die Nachricht nach dem Login`() = blocking {
        val limited = backend()
        val server = running(
            environment(ConfigKeys.MAX_MESSAGE_BYTES to MIN_MESSAGE_BYTES),
            Wiring(telemetry = sink, requests = RequestSource(limited)),
        )
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch(BELOW_LIMIT_CHARS))
            assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
            val failure = runCatching { client.send(batch(ABOVE_LIMIT_CHARS)) }.exceptionOrNull()
            assertTrue(failure == null || failure is IOException, "$failure")
            assertTrue(client.closedByServerWithoutBytes(), "die Verbindung schließt ohne Antwort")
        }
        assertEquals(1, limited.requests.size)

        val open = backend()
        val unlimited = running(environment(), Wiring(telemetry = sink, requests = RequestSource(open)))
        RawClient(unlimited.port).use { client ->
            login(client)
            client.send(batch(ABOVE_LIMIT_CHARS))
            assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
        }
        assertEquals(1, open.requests.size)
    }
}
