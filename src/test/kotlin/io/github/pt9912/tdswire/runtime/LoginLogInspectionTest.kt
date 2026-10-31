package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListAuthAdapter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.RecordingEvents
import io.github.pt9912.tdswire.transport.listener.eventually
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loginspektion (`TDS-QA-03`): Der Serverprozess läuft mit seinem echten Handler über einen Socket; Kennung,
 * Passwort, Datenbank-, Rechner- und Anwendungsname tragen eine Marke, und keine Zeile des Logs nennt sie, in keinem
 * Ausgang der Anmeldung. Das Log ist das, was `ServerLog` im Prozess schreibt (`ServerLog.STDOUT` nur mit
 * Präfix); andere Senken hat der Prozess nicht.
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

    private fun assertNoMarker(lines: List<String>) {
        assertTrue(lines.isNotEmpty(), "das Log ist leer, die Inspektion bewiese nichts")
        lines.forEach { assertTrue(!it.contains(marker), "Logzeile nennt einen Wert der Anmeldung: $it") }
        lines.forEach { assertTrue(!it.contains(directoryOfUsers()), "Logzeile nennt einen Pfad: $it") }
    }

    private fun directoryOfUsers(): String = File(users()).parent

    /**
     * Annahme, falsches Passwort, unbekannter Benutzername, zu lange Eingabe und ein Adapter, dessen Ausnahme
     * Kennung und Passwort im Text trägt: das Log nennt je Fall Art und Grund (`abgelehnt (...)`, Typ der
     * Ausnahme) und keine Marke, keinen Pfad. Der Text der Ausnahme erreicht das Log nicht (`SPEC-048`).
     *
     * @spec SPEC-012
     * @spec SPEC-048
     */
    @Test
    fun `Kein Ausgang der Anmeldung schreibt Kennung oder Passwort ins Log`() = blocking {
        val wiring = Wiring(log = log, events = events, adapter = { delegateWithLeakingFailure(it) })
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
        val lines = log.lines.toList()
        assertNoMarker(lines)
        assertTrue(lines.contains("Login: abgelehnt (WRONG_PASSWORD)"), lines.toString())
        assertTrue(lines.contains("Login: abgelehnt (UNKNOWN_USER)"), lines.toString())
        assertTrue(lines.contains("Login: abgelehnt (INPUT_TOO_LONG)"), lines.toString())
        assertTrue(lines.contains("Login: abgelehnt (ADAPTER_FAILURE)"), lines.toString())
        assertTrue(
            lines.contains("Login: Adapterfehler (java.lang.IllegalStateException)"),
            "der Typ der Ausnahme fehlt: $lines",
        )
    }

    /**
     * Gegenprobe des Prüfers: Schreibt eine Zeile die Marke, schlägt `assertNoMarker` an. Ohne sie bewiese das
     * Grün der anderen Prüfung nur, dass die Marke nirgends gesucht wird.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Der Pruefer erkennt eine Logzeile mit Marke`() {
        assertFailsWith<AssertionError> { assertNoMarker(listOf("Login: abgelehnt für $user")) }
        assertFailsWith<AssertionError> { assertNoMarker(emptyList()) }
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
