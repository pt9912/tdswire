package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.transport.framing.FramingLimits

/**
 * Die Übergänge um LOGIN7 für [HandshakeAutomaton]: Prüfung und Adapteraufruf, Annahme, Ablehnung.
 * Ende nach abgelehntem Login: Setzung des Nutzers (`ADR-0007`), nicht gemessen. Die Antwort an den
 * Client ist für jeden Ablehnungsgrund dieselbe (`SPEC-012`).
 */
internal class LoginTransitions(private val config: HandshakeConfig) {
    fun onLogin7(context: HandshakeContext, payload: ByteArray): Transition {
        if (payload.size > FramingLimits.MAX_PRELOGIN_MESSAGE_BYTES) {
            return context.closing(CloseReason.MESSAGE_TOO_LARGE)
        }
        val encryption = context.encryption ?: NegotiatedEncryption.REQUIRED
        return when (val admission = LoginAdmission.admit(payload, encryption)) {
            is Admission.Malformed -> context.closing(CloseReason.PROTOCOL_VIOLATION)

            is Admission.Refused -> reject(context, admission.rejection, null)

            is Admission.Admitted -> Transition(
                context.copy(state = ConnectionState.AUTHENTICATING, pending = admission.pending),
                listOf(HandshakeAction.Authenticate(admission.request)),
            )
        }
    }

    fun accepted(context: HandshakeContext, event: HandshakeEvent.AuthAccepted): Transition {
        val pending = checkNotNull(context.pending) { "Zustand AUTHENTICATING ohne Anmeldedaten" }
        val database = pending.database.ifEmpty { config.defaultDatabase }
        val packetSize = LoginResponses.negotiatedPacketSize(pending.requestedPacketSize)
        val session = AuthenticatedSession(event.sessionId, event.principal, database, packetSize)
        return Transition(
            context.copy(state = ConnectionState.READY, pending = null, session = session),
            listOf(
                HandshakeAction.Send(LoginResponses.acceptance(config.order, database, packetSize)),
                HandshakeAction.SwitchToSessionLimits,
            ),
        )
    }

    fun rejected(context: HandshakeContext, event: HandshakeEvent.AuthRejected): Transition =
        reject(context, event.rejection, event.adapterFailureType)

    /** Das feste Ablehnungsmuster: Log, ERROR 18456 mit DONE, Verbindungsende. */
    private fun reject(context: HandshakeContext, rejection: LoginRejection, failureType: String?): Transition {
        val actions = buildList {
            add(HandshakeAction.Log(LogEntry.LoginRejected(rejection)))
            if (failureType != null) add(HandshakeAction.Log(LogEntry.AdapterFailed(failureType)))
            add(HandshakeAction.Send(LoginResponses.rejection))
            add(HandshakeAction.Close(CloseReason.LOGIN_REJECTED))
        }
        return Transition(context.closed(), actions)
    }
}
