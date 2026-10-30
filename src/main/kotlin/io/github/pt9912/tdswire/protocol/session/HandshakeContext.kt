package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.domain.Principal

/**
 * Die Sitzung nach der Annahme: ID, Principal, Datenbankkontext und Packet Size (`TDS-FA-02.a`).
 * Die Textdarstellung nennt keinen Wert.
 */
class AuthenticatedSession(val id: String, val principal: Principal, val database: String, val packetSize: Int) {
    override fun toString(): String = "AuthenticatedSession(verborgen)"
}

/** Werte aus LOGIN7, die der Automat über den Adapteraufruf hinweg hält: Datenbank und verlangte Packet Size. */
data class PendingLogin(val database: String, val requestedPacketSize: Int) {
    override fun toString(): String = "PendingLogin(verborgen)"
}

/**
 * Der Stand eines Automaten: der [state] und die Werte, die zwischen Zuständen mitgehen. Ein
 * Wert gehört einer Verbindung; zwei Verbindungen teilen keinen.
 */
data class HandshakeContext(
    val state: ConnectionState = ConnectionState.CONNECTED,
    val encryption: NegotiatedEncryption? = null,
    val pending: PendingLogin? = null,
    val session: AuthenticatedSession? = null,
)

/** Folgestand und Aktionen eines Übergangs. */
data class Transition(val context: HandshakeContext, val actions: List<HandshakeAction>)

/** Der Kontext nach dem Schließen: ohne Anmeldedaten und ohne Sitzung. */
internal fun HandshakeContext.closed(): HandshakeContext =
    copy(state = ConnectionState.CLOSED, pending = null, session = null)

/** Der Übergang nach `CLOSED` mit der Aktion [HandshakeAction.Close]. */
internal fun HandshakeContext.closing(reason: CloseReason): Transition =
    Transition(closed(), listOf(HandshakeAction.Close(reason)))
