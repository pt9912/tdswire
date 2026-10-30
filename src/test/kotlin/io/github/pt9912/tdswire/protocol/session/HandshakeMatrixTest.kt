package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.config.auth.RejectReason
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.protocol.session.ConnectionState.AUTHENTICATING
import io.github.pt9912.tdswire.protocol.session.ConnectionState.CLOSED
import io.github.pt9912.tdswire.protocol.session.ConnectionState.CONNECTED
import io.github.pt9912.tdswire.protocol.session.ConnectionState.LOGIN7
import io.github.pt9912.tdswire.protocol.session.ConnectionState.PRELOGIN
import io.github.pt9912.tdswire.protocol.session.ConnectionState.READY
import io.github.pt9912.tdswire.protocol.session.ConnectionState.TLS_NEGOTIATION
import io.github.pt9912.tdswire.protocol.session.EventKind.ATTENTION_MESSAGE
import io.github.pt9912.tdswire.protocol.session.EventKind.AUTH_ACCEPTED
import io.github.pt9912.tdswire.protocol.session.EventKind.AUTH_REJECTED
import io.github.pt9912.tdswire.protocol.session.EventKind.LOGIN7_MESSAGE
import io.github.pt9912.tdswire.protocol.session.EventKind.LOGIN_TIMEOUT
import io.github.pt9912.tdswire.protocol.session.EventKind.OTHER_MESSAGE
import io.github.pt9912.tdswire.protocol.session.EventKind.PEER_CLOSED
import io.github.pt9912.tdswire.protocol.session.EventKind.PRELOGIN_MESSAGE
import io.github.pt9912.tdswire.protocol.session.EventKind.READ_FAILED
import io.github.pt9912.tdswire.protocol.session.EventKind.REQUEST_MESSAGE
import io.github.pt9912.tdswire.protocol.session.EventKind.START
import io.github.pt9912.tdswire.protocol.session.EventKind.TLS_ESTABLISHED
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Matrix aus Zuständen und Ereignisarten gegen eine Handtabelle: Folgezustand und Aktionen jeder
 * Zelle, in beiden Reihenfolgen. Die Tabelle ist von Hand geschrieben und teilt keinen Code mit
 * [HandshakeMatrix] oder [HandshakeAutomaton].
 */
class HandshakeMatrixTest {
    private class Expect(val next: ConnectionState, val actions: List<String>)

    private fun to(next: ConnectionState, vararg actions: String) = Expect(next, actions.toList())

    private val violation = to(CLOSED, "Close:PROTOCOL_VIOLATION")
    private val timeout = to(CLOSED, "Close:LOGIN_TIMEOUT")
    private val peer = to(CLOSED, "Close:PEER_CLOSED")
    private val failed = to(CLOSED, "Close:READ_FAILED")

    /** Die zulässigen und die ignorierten Zellen je Zustand und Reihenfolge; jede andere Zelle ist unzulässig. */
    private fun table(order: HandshakeOrder): Map<ConnectionState, Map<EventKind, Expect>> {
        val ending = mapOf(LOGIN_TIMEOUT to timeout, PEER_CLOSED to peer, READ_FAILED to failed)
        val tls = order == HandshakeOrder.TLS_FIRST
        val afterStart = if (tls) to(TLS_NEGOTIATION, "BeginTls") else to(PRELOGIN)
        val afterPrelogin = if (tls) to(LOGIN7, "Send") else to(TLS_NEGOTIATION, "Send", "BeginTls")
        val afterTls = if (tls) to(PRELOGIN) else to(LOGIN7)
        return mapOf(
            CONNECTED to ending + (START to afterStart),
            PRELOGIN to ending + (PRELOGIN_MESSAGE to afterPrelogin),
            TLS_NEGOTIATION to ending + (TLS_ESTABLISHED to afterTls),
            LOGIN7 to ending + (LOGIN7_MESSAGE to to(AUTHENTICATING, "Authenticate")),
            AUTHENTICATING to ending + mapOf(
                AUTH_ACCEPTED to to(READY, "Send", "SwitchToSessionLimits"),
                AUTH_REJECTED to to(CLOSED, "Log", "Send", "Close:LOGIN_REJECTED"),
            ),
            READY to mapOf(
                REQUEST_MESSAGE to to(READY, "Forward"),
                ATTENTION_MESSAGE to to(READY, "Forward"),
                LOGIN_TIMEOUT to to(READY),
                PEER_CLOSED to peer,
                READ_FAILED to failed,
            ),
            CLOSED to mapOf(LOGIN_TIMEOUT to to(CLOSED), PEER_CLOSED to to(CLOSED), READ_FAILED to to(CLOSED)),
        )
    }

    private val authContext = HandshakeContext(
        state = AUTHENTICATING,
        encryption = NegotiatedEncryption.REQUIRED,
        pending = PendingLogin("db", 8000),
    )

    private fun context(state: ConnectionState): HandshakeContext = when (state) {
        AUTHENTICATING -> authContext
        LOGIN7 -> HandshakeContext(state, encryption = NegotiatedEncryption.REQUIRED)
        READY -> HandshakeContext(state, session = AuthenticatedSession("s1", Principal("sa"), "db", 8000))
        else -> HandshakeContext(state)
    }

    private fun event(kind: EventKind): HandshakeEvent = when (kind) {
        START -> HandshakeEvent.Start
        TLS_ESTABLISHED -> HandshakeEvent.TlsEstablished
        PRELOGIN_MESSAGE -> HandshakeEvent.Message(SessionVectors.prelogin(SessionVectors.ENCRYPT_ON))
        LOGIN7_MESSAGE -> HandshakeEvent.Message(SessionVectors.login7())
        REQUEST_MESSAGE -> HandshakeEvent.Message(SessionVectors.message(PacketType.SQL_BATCH))
        ATTENTION_MESSAGE -> HandshakeEvent.Message(SessionVectors.message(PacketType.ATTENTION))
        OTHER_MESSAGE -> HandshakeEvent.Message(SessionVectors.message(PacketType.SSPI))
        AUTH_ACCEPTED -> HandshakeEvent.AuthAccepted(Principal("sa"), "s1")
        AUTH_REJECTED -> HandshakeEvent.AuthRejected(LoginRejection.from(RejectReason.WRONG_PASSWORD))
        LOGIN_TIMEOUT -> HandshakeEvent.LoginTimeout
        PEER_CLOSED -> HandshakeEvent.PeerClosed
        READ_FAILED -> HandshakeEvent.ReadFailed
    }

    /**
     * Jede der 84 Zellen in beiden Reihenfolgen: Folgezustand und Aktionsfolge stimmen mit der
     * Handtabelle überein; eine Zelle ohne Eintrag ist unzulässig und schließt ohne Antwort.
     *
     * @spec SPEC-001
     * @spec SPEC-028
     */
    @Test
    fun `Jede Zelle der Matrix entspricht der Handtabelle in beiden Reihenfolgen`() {
        HandshakeOrder.entries.forEach { order ->
            val automaton = HandshakeAutomaton(HandshakeConfig(order = order))
            val expected = table(order)
            ConnectionState.entries.forEach { state ->
                EventKind.entries.forEach { kind ->
                    val want = expected.getValue(state)[kind] ?: violation
                    val step = automaton.step(context(state), event(kind))
                    val where = "$order $state x $kind"
                    assertEquals(want.next, step.context.state, "Folgezustand $where")
                    assertEquals(want.actions, step.actions.map { it.label() }, "Aktionen $where")
                }
            }
        }
    }

    /**
     * Die Zählung der Zellen kommt aus dem Code und weicht von der Zahl des Spike ab (140 Zellen,
     * 10 Zustände mal 14 Ereignisse): hier 7 Zustände mal 12 Ereignisarten.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Die Matrix zaehlt ihre Zellen aus dem Code`() {
        val census = HandshakeMatrix.census()
        assertEquals(7, ConnectionState.entries.size)
        assertEquals(12, EventKind.entries.size)
        assertEquals(84, census.values.sum())
        assertEquals(25, census[HandshakeMatrix.Cell.PERMITTED])
        assertEquals(4, census[HandshakeMatrix.Cell.IGNORED])
        assertEquals(55, census[HandshakeMatrix.Cell.ILLEGAL])
    }

    private fun assertClosedSilently(state: ConnectionState, message: HandshakeEvent) {
        val step = HandshakeAutomaton().step(context(state), message)
        assertEquals(CLOSED, step.context.state)
        assertEquals(listOf(HandshakeAction.Close(CloseReason.PROTOCOL_VIOLATION)), step.actions)
        assertTrue(step.actions.none { it is HandshakeAction.Send }, "keine Antwort auf einen unzulaessigen Uebergang")
    }

    /**
     * LOGIN7 vor PRELOGIN: weder im Zustand `CONNECTED` noch in `PRELOGIN` zulässig.
     *
     * @spec SPEC-001
     */
    @Test
    fun `LOGIN7 vor PRELOGIN beendet die Verbindung ohne Antwort`() {
        assertClosedSilently(CONNECTED, event(LOGIN7_MESSAGE))
        assertClosedSilently(PRELOGIN, event(LOGIN7_MESSAGE))
        assertClosedSilently(TLS_NEGOTIATION, event(LOGIN7_MESSAGE))
    }

    /**
     * Ein zweites PRELOGIN ist nach der Antwort, während der TLS-Aushandlung, in LOGIN7 und nach dem
     * Login unzulässig.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Zweites PRELOGIN beendet die Verbindung ohne Antwort`() {
        listOf(TLS_NEGOTIATION, LOGIN7, AUTHENTICATING, READY, CLOSED).forEach { state ->
            assertClosedSilently(state, event(PRELOGIN_MESSAGE))
        }
    }

    /**
     * Im Zustand `LOGIN7` löst nur ein Paket vom Typ LOGIN7 die Anmeldung aus: dieselbe gültige
     * LOGIN7-Nutzlast in einem Paket vom Typ PRELOGIN, ATTENTION, SQL_BATCH, RPC oder SSPI schließt ohne
     * Antwort und ohne Adapteraufruf. (Die Zählung der Matrix allein fängt eine geöffnete Zelle nicht,
     * weil der Inhalt sonst als LOGIN7 gelesen würde.)
     *
     * @spec SPEC-001
     */
    @Test
    fun `Eine gueltige LOGIN7-Nutzlast in einem Paket falschen Typs loest keine Anmeldung aus`() {
        val payload = SessionVectors.login7Payload()
        listOf(PacketType.PRELOGIN, PacketType.ATTENTION, PacketType.SQL_BATCH, PacketType.RPC, PacketType.SSPI)
            .forEach { type ->
                val step = HandshakeAutomaton().step(context(LOGIN7), HandshakeEvent.Message(TdsMessage(type, payload)))
                assertEquals(CLOSED, step.context.state, "$type")
                assertEquals(listOf(HandshakeAction.Close(CloseReason.PROTOCOL_VIOLATION)), step.actions, "$type")
            }
        val login = HandshakeAutomaton().step(context(LOGIN7), event(LOGIN7_MESSAGE))
        assertEquals(AUTHENTICATING, login.context.state)
    }

    /**
     * Der Kontext nach dem Schließen trägt weder die Anmeldedaten (Datenbank, verlangte Packet Size) noch
     * die Sitzung: nach einer Ablehnung und nach dem Ende der Gegenstelle in `READY`.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Der Kontext nach dem Schliessen traegt keine Anmeldedaten und keine Sitzung`() {
        val rejected = HandshakeAutomaton().step(authContext, event(AUTH_REJECTED))
        assertEquals(CLOSED, rejected.context.state)
        assertEquals(null, rejected.context.pending)
        assertEquals(null, rejected.context.session)
        val ended = HandshakeAutomaton().step(context(READY), event(PEER_CLOSED))
        assertEquals(CLOSED, ended.context.state)
        assertEquals(null, ended.context.pending)
        assertEquals(null, ended.context.session)
    }

    /**
     * SQL_BATCH, RPC und ATTENTION vor `READY` sind unzulässig; in `READY` gehören sie der
     * Anfrageverarbeitung (Aktion `Forward`).
     *
     * @spec SPEC-001
     */
    @Test
    fun `SQL_BATCH vor READY beendet die Verbindung, in READY wird sie weitergegeben`() {
        listOf(CONNECTED, PRELOGIN, TLS_NEGOTIATION, LOGIN7, AUTHENTICATING).forEach { state ->
            assertClosedSilently(state, event(REQUEST_MESSAGE))
            assertClosedSilently(state, event(ATTENTION_MESSAGE))
            assertClosedSilently(state, HandshakeEvent.Message(SessionVectors.message(PacketType.RPC)))
        }
        val ready = HandshakeAutomaton().step(context(READY), event(REQUEST_MESSAGE))
        assertEquals(READY, ready.context.state)
        assertEquals(listOf(HandshakeAction.Forward), ready.actions)
    }

    /**
     * Nach `CLOSED` ist jede Nachricht unzulässig; der Zustand bleibt `CLOSED` und es wird nichts gesendet.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Nachricht nach CLOSED bleibt CLOSED ohne Antwort`() {
        listOf(PRELOGIN_MESSAGE, LOGIN7_MESSAGE, REQUEST_MESSAGE, ATTENTION_MESSAGE, OTHER_MESSAGE).forEach { kind ->
            assertClosedSilently(CLOSED, event(kind))
        }
    }

    /**
     * Zwei Verbindungen teilen keinen Zustand: der unzulässige Übergang der einen verändert den
     * Kontext der anderen nicht (der Kontext ist ein Wert, der Automat hält keinen Zustand).
     *
     * @spec SPEC-001
     */
    @Test
    fun `Ein unzulaessiger Uebergang beruehrt den Kontext einer zweiten Verbindung nicht`() {
        val automaton = HandshakeAutomaton()
        val other = context(LOGIN7)
        val snapshot = other.copy()
        val victim = automaton.step(context(CONNECTED), event(LOGIN7_MESSAGE))
        assertEquals(CLOSED, victim.context.state)
        assertEquals(snapshot, other)
    }
}
