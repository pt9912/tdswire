package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListAuthAdapter
import io.github.pt9912.tdswire.domain.telemetry.AdapterFailed
import io.github.pt9912.tdswire.domain.telemetry.EventContext
import io.github.pt9912.tdswire.domain.telemetry.LoginAccepted
import io.github.pt9912.tdswire.domain.telemetry.LoginRejected
import io.github.pt9912.tdswire.domain.telemetry.LoginRejectionReason
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.RecordingEvents
import io.github.pt9912.tdswire.transport.listener.eventually
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loginspektion (`TDS-QA-03`): Der Serverprozess läuft mit seinem echten Handler über einen Socket; Kennung,
 * Passwort, Datenbank-, Rechner- und Anwendungsname tragen eine Marke, und keine Zeile des Logs nennt sie, in keinem
 * Ausgang der Anmeldung. Das Log sind die Ereignisse, die der Server an die Senke gibt; andere Wege hat die
 * Bibliothek nicht. Nur das Ereignis „Anmeldung angenommen“ trägt die Kennung, im Feld `principal`.
 */
internal class LoginLogInspectionTest : RuntimeTestBase() {
    private val marker = "Zk9"
    private val user = "$marker-alice"
    private val secret = "$marker-geheim"
    private val events = RecordingEvents()

    private fun users() = file("users-$marker.txt", "$user\t{plain}$secret\n")

    private fun environment() = loopbackEnvironment(users())

    private fun login(client: RawClient, name: String, password: String): RawMessage? {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = name
            this.password = password.toCharArray()
            database = "$marker-datenbank"
            hostName = "$marker-rechner"
            appName = "$marker-anwendung"
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        return client.readMessage()
    }

    private suspend fun settle(connections: Int) =
        eventually("Verbindungen beendet") { events.closed().size >= connections }

    private fun assertNoMarker(events: List<TelemetryEvent>) {
        assertTrue(events.isNotEmpty(), "das Log ist leer, die Inspektion bewiese nichts")
        events.forEach { event ->
            val text = event.visibleText().let { if (event is LoginAccepted) it.replace(event.principal, "") else it }
            assertTrue(!text.contains(marker), "Ereignis nennt einen Wert der Anmeldung: $text")
            assertTrue(!text.contains(directoryOfUsers()), "Ereignis nennt einen Pfad: $text")
        }
    }

    private fun directoryOfUsers(): String = File(users()).parent

    /**
     * Annahme, falsches Passwort, unbekannter Benutzername, zu lange Eingabe und ein Adapter, dessen Ausnahme
     * Kennung und Passwort im Text trägt: die Ereignisse nennen je Fall Art und Grund (Grund-Marke, Typ der
     * Ausnahme) und keine Marke, keinen Pfad; die Kennung steht nur im Ereignis der Annahme. Der Text der Ausnahme
     * erreicht kein Ereignis (`SPEC-048`).
     *
     * @spec SPEC-012
     * @spec SPEC-048
     */
    @Test
    fun `Kein Ausgang der Anmeldung schreibt Kennung oder Passwort ins Log`() = blocking {
        val wiring = Wiring(telemetry = sink, events = events, adapter = { delegateWithLeakingFailure(it) })
        val server = running(environment(), wiring)
        val cases = listOf(
            user to secret,
            user to "$marker-falsch",
            "$marker-unbekannt" to secret,
            "$marker-x".padEnd(200, 'x') to secret,
            "$marker-wirft" to secret,
        )
        cases.forEach { (name, password) -> RawClient(server.port).use { login(it, name, password) } }
        settle(cases.size)
        val seen = sink.events.toList()
        assertNoMarker(seen)
        assertEquals(
            setOf(
                LoginRejectionReason.WRONG_PASSWORD,
                LoginRejectionReason.UNKNOWN_USER,
                LoginRejectionReason.INPUT_TOO_LONG,
                LoginRejectionReason.ADAPTER_FAILURE,
            ),
            sink.of<LoginRejected>().map { it.reason }.toSet(),
        )
        assertEquals(
            listOf("java.lang.IllegalStateException"),
            sink.of<AdapterFailed>().map { it.exceptionType },
            "der Typ der Ausnahme fehlt: $seen",
        )
        assertEquals(listOf(user), sink.of<LoginAccepted>().map { it.principal })
    }

    /**
     * Gegenprobe des Prüfers: Trägt ein Ereignis die Marke außerhalb von `principal`, schlägt `assertNoMarker` an.
     * Ohne sie bewiese das Grün der anderen Prüfung nur, dass die Marke nirgends gesucht wird.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Der Pruefer erkennt eine Logzeile mit Marke`() {
        val leaking = AdapterFailed(Instant.EPOCH, EventContext.NONE, user)
        assertFailsWith<AssertionError> { assertNoMarker(listOf(leaking)) }
        assertFailsWith<AssertionError> { assertNoMarker(emptyList()) }
        val accepted = LoginAccepted(Instant.EPOCH, EventContext.NONE, user)
        assertNoMarker(listOf(accepted))
    }

    /** Ein Adapter, der für eine Kennung wirft und im Text der Ausnahme Kennung und Passwort nennt. */
    private fun delegateWithLeakingFailure(list: UserList): AuthAdapter {
        val real = UserListAuthAdapter(list)
        return object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult {
                check(!request.userName.endsWith("wirft")) {
                    "Fehler fuer ${request.userName} mit ${String(request.password.reveal())}"
                }
                return real.authenticate(request)
            }
        }
    }
}
