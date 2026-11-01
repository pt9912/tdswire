package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.protocol.session.AuthPool
import io.github.pt9912.tdswire.protocol.session.LoginResponses
import io.github.pt9912.tdswire.protocol.session.LoginThrottle
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Die Drosselung am echten Handler des Prozesses (`ADR-0016`, `SPEC-053`): echte Sockets auf `127.0.0.1`, der Schlüssel
 * ist die Adresse der Gegenstelle aus dem Listener, Uhr und Wartezeit sind gesteuert. Der Rohbytes-Client ist kein
 * Referenzclient.
 */
internal class LoginThrottleWiringTest : RuntimeTestBase() {
    @Volatile
    private var now = 0L
    private val pauses = java.util.concurrent.CopyOnWriteArrayList<Duration>()
    private val throttle = LoginThrottle(nanos = { now }, pause = { pauses += it })

    private fun environment() = loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"))

    private fun login(client: RawClient, user: String, password: String): RawMessage {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = user
            this.password = password.toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        return assertNotNull(client.readMessage())
    }

    private fun attempt(port: Int, user: String = "alice", password: String = "wrong"): RawMessage =
        RawClient(port, LOOPBACK).use { login(it, user, password) }

    /**
     * Orakel: sechs Anmeldungen mit falschem Passwort von `127.0.0.1` ergeben sechsmal die feste Ablehnung; die
     * sechste hält 250 ms an, die fünf davor nicht. Eine siebte mit richtigem Passwort wartet 500 ms und wird
     * angenommen (die Wartezeit hängt nicht vom Ergebnis ab, die Anmeldung wird nicht gesperrt). Der Schlüssel kommt
     * aus dem Listener: die Wartezeit entsteht nur mit der Adresse der Gegenstelle (Gegenprobe: ein Treiber ohne
     * Adresse wartet nie, `LoginThrottleDriverTest`). Die Drosselung schreibt heute nichts ins Log; der Test hält fest,
     * dass keine Adresse in den Zeilen steht, und ist anzupassen, sobald eine Entscheidung Adressen im Log vorsieht.
     *
     * @spec SPEC-053
     * @spec SPEC-012
     */
    @Test
    fun `Wiederholte Fehlversuche einer Adresse warten, eine Annahme folgt`() = blocking {
        val server = running(environment(), Wiring(telemetry = sink, authPool = AuthPool(throttle = throttle)))
        repeat(5) { assertContentEquals(LoginResponses.rejection.payload, attempt(server.port).payload) }
        assertEquals(emptyList<Duration>(), pauses)
        assertContentEquals(LoginResponses.rejection.payload, attempt(server.port).payload)
        assertEquals(listOf(250.milliseconds), pauses)
        val accepted = attempt(server.port, password = "secret")
        assertEquals(listOf(250.milliseconds, 500.milliseconds), pauses)
        assertFalse(accepted.payload.contentEquals(LoginResponses.rejection.payload), "Annahme trotz Wartezeit")
        assertTrue(sink.events.isNotEmpty(), "keine Ereignisse, die Prüfung bewiese nichts")
        assertTrue(sink.events.none { it.visibleText().contains(LOOPBACK) }, "Adresse im Ereignis: ${sink.events}")
    }

    /**
     * Orakel: nach 60 s ohne Fehlversuch ist die Adresse unverzögert, und eine reguläre Anmeldung wird angenommen,
     * ohne eine weitere Wartezeit.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Nach Ablauf des Fensters ist die Anmeldung nicht blockiert`() = blocking {
        val server = running(environment(), Wiring(telemetry = sink, authPool = AuthPool(throttle = throttle)))
        repeat(7) { attempt(server.port) }
        assertEquals(2, pauses.size)
        now += 60.seconds.inWholeNanoseconds
        val accepted = attempt(server.port, password = "secret")
        assertEquals(2, pauses.size, "keine weitere Wartezeit")
        assertFalse(accepted.payload.contentEquals(LoginResponses.rejection.payload))
    }
}
