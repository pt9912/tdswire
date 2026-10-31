package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.config.auth.RejectReason
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.protocol.login7.Login7Parser
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Textdarstellungen der Typen des Anmeldepfads (`TDS-QA-03`): Zustand, Ereignisse, Aktionen, Übergänge,
 * Zulassungsurteil und geparstes LOGIN7 tragen weder Kennung noch Passwort noch Datenbank-, Rechner- oder
 * Anwendungsname. Die Werte kommen aus einem LOGIN7, das der Automat wirklich verarbeitet.
 */
class LoginPathTextTest {
    private val marker = "Zk9"
    private val userName = "$marker-benutzer"
    private val password = "$marker-passwort"

    private fun login7() = SessionVectors.login7 {
        userName = this@LoginPathTextTest.userName
        password = this@LoginPathTextTest.password.toCharArray()
        database = "$marker-datenbank"
        hostName = "$marker-rechner"
        appName = "$marker-anwendung"
        serverName = "$marker-server"
    }

    private fun assertHidden(shown: Map<String, String>) {
        shown.forEach { (what, text) ->
            assertTrue(!text.contains(marker), "$what nennt einen Wert der Anmeldung: $text")
        }
    }

    /** Alle Ereignisse, Kontexte, Übergänge und Aktionen einer angenommenen Anmeldung als Text. */
    private fun accepted(): Map<String, String> {
        val automaton = HandshakeAutomaton(HandshakeConfig())
        val shown = linkedMapOf<String, String>()
        var context = HandshakeContext()
        fun step(event: HandshakeEvent) {
            shown["Ereignis ${shown.size}"] = event.toString()
            val transition = automaton.step(context, event)
            context = transition.context
            shown["Kontext ${shown.size}"] = context.toString()
            shown["Uebergang ${shown.size}"] = transition.toString()
            transition.actions.forEachIndexed { i, a -> shown["Aktion ${shown.size}/$i"] = a.toString() }
        }
        step(HandshakeEvent.Start)
        step(HandshakeEvent.Message(SessionVectors.prelogin(SessionVectors.ENCRYPT_ON)))
        step(HandshakeEvent.TlsEstablished)
        step(HandshakeEvent.Message(login7()))
        assertIs<PendingLogin>(context.pending)
        step(HandshakeEvent.AuthAccepted(Principal(userName), "sitzung-1"))
        assertIs<AuthenticatedSession>(context.session)
        return shown
    }

    /**
     * Orakel: Jede Textdarstellung entlang einer angenommenen Anmeldung nennt keinen der fünf Werte; die Fälle
     * schließen `PendingLogin` und `AuthenticatedSession` ein, deren Verbergen kein Test gepinnt hatte
     * (`HandshakeContext` und `Transition` sind `data class` und reichen sie durch).
     *
     * @spec SPEC-012
     */
    @Test
    fun `Eine angenommene Anmeldung zeigt in keiner Textdarstellung einen Wert`() {
        val shown = accepted()
        assertTrue(shown.values.any { it.contains("PendingLogin") }, "der Kontext mit Anmeldedaten kam nicht vor")
        assertTrue(shown.values.any { it.contains("AuthenticatedSession") }, "der Kontext mit Sitzung kam nicht vor")
        assertHidden(shown)
    }

    /**
     * Orakel: Die Ablehnungen (vom Adapter, vor dem Adapter), die Ereignisse des Fehlerpfads, das geparste LOGIN7 und
     * das Urteil der Zulassung zeigen keinen Wert.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Ablehnung, Zulassung und geparstes LOGIN7 zeigen keinen Wert`() {
        val payload = login7().payload
        val parsed = assertIs<ProtocolResult.Ok<*>>(Login7Parser.parse(payload)).toString()
        val admitted = LoginAdmission.admit(payload, NegotiatedEncryption.REQUIRED)
        val refused = LoginAdmission.admit(
            SessionVectors.login7Payload {
                userName = "x".repeat(200) + marker
                password = "$marker-p".toCharArray()
            },
            NegotiatedEncryption.REQUIRED,
        )
        val authenticating = HandshakeContext(
            state = ConnectionState.AUTHENTICATING,
            encryption = NegotiatedEncryption.ON,
            pending = PendingLogin("$marker-datenbank", 4096),
        )
        val failure = "java.lang.IllegalStateException"
        val rejected = HandshakeAutomaton(HandshakeConfig()).step(
            authenticating,
            HandshakeEvent.AuthRejected(LoginRejection.from(RejectReason.WRONG_PASSWORD), failure),
        )
        assertIs<Admission.Admitted>(admitted)
        assertIs<Admission.Refused>(refused)
        assertHidden(
            mapOf(
                "Login7Request" to parsed,
                "Admitted" to admitted.toString(),
                "Refused" to refused.toString(),
                "Ablehnung (Transition)" to rejected.toString(),
                "AuthResult.Rejected" to AuthResult.Rejected(RejectReason.UNKNOWN_USER).toString(),
                "AuthOutcome.Failed" to AuthOutcome.Failed(failure).toString(),
                "AuthOutcome.Decided" to AuthOutcome.Decided(AuthResult.Accepted(Principal(userName))).toString(),
                "LogEntry.AdapterFailed" to LogEntry.AdapterFailed(failure).toString(),
                "HandshakeOutcome.Closed" to HandshakeOutcome.Closed(CloseReason.LOGIN_REJECTED).toString(),
            ),
        )
    }

    /**
     * Gegenprobe des Prüfers: eine Textdarstellung, die den Wert nennt, macht `assertHidden` rot. Ohne sie bewiese
     * ein grüner Lauf nur, dass der Prüfer nichts findet.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Der Pruefer erkennt eine Textdarstellung, die einen Wert nennt`() {
        val leaking = PendingLoginLeak("$marker-datenbank", 4096)
        assertFailsWith<AssertionError> { assertHidden(mapOf("Leck" to leaking.toString())) }
    }

    private data class PendingLoginLeak(val database: String, val requestedPacketSize: Int)
}
