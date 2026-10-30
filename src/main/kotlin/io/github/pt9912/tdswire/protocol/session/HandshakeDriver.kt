package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Ausgang des Handshakes einer Verbindung. */
sealed interface HandshakeOutcome {
    /**
     * Der Login ist angenommen, die Antwort gesendet, die Rahmen-Grenze gewechselt; die Verbindung
     * gehört dem Aufrufer.
     */
    class Ready(val session: AuthenticatedSession) : HandshakeOutcome {
        override fun toString(): String = "Ready(verborgen)"
    }

    /** Die Verbindung ist beendet. */
    data class Closed(val reason: CloseReason) : HandshakeOutcome
}

/**
 * Führt den Handshake einer Verbindung aus (`ARC-004`, `SPEC-008`): liest Nachrichten aus dem
 * [HandshakeChannel], gibt sie dem [HandshakeAutomaton], führt dessen Aktionen aus und ruft den
 * Adapter. Der Treiber hält `LOGIN_TIMEOUT` ([loginTimeout]) ab Beginn von [run] bis zur
 * Annahme ein; läuft die Frist ab, schließt er ohne Antwort. Während der Adapter läuft, liest er
 * nicht weiter.
 *
 * Festlegung ohne Messung: [loginTimeout] hat den vorläufigen Wert 15 s aus `SPEC-008`;
 * [authDispatcher] ist `Dispatchers.IO` (Begründung in [AuthExecutor]); die Sitzungs-ID ist eine
 * zufällige UUID.
 */
class HandshakeDriver(
    config: HandshakeConfig,
    private val adapter: AuthAdapter,
    private val log: HandshakeLog = HandshakeLog.NONE,
    private val loginTimeout: Duration = DEFAULT_LOGIN_TIMEOUT,
    authDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionIds: () -> String = { UUID.randomUUID().toString() },
) {
    private val automaton = HandshakeAutomaton(config)
    private val executor = AuthExecutor(authDispatcher)

    suspend fun run(channel: HandshakeChannel): HandshakeOutcome {
        val session = HandshakeSession(automaton, channel, log)
        val outcome = withTimeoutOrNull(loginTimeout) { drive(session, channel) }
        if (outcome != null) return outcome
        session.expire()
        return HandshakeOutcome.Closed(CloseReason.LOGIN_TIMEOUT)
    }

    private suspend fun drive(session: HandshakeSession, channel: HandshakeChannel): HandshakeOutcome {
        var event: HandshakeEvent = HandshakeEvent.Start
        while (true) {
            val awaited = session.dispatch(event)
            concluded(session)?.let { return it }
            event = when (awaited) {
                is HandshakeAction.Authenticate -> authenticate(awaited.request)

                HandshakeAction.BeginTls ->
                    if (channel.startTls()) HandshakeEvent.TlsEstablished else HandshakeEvent.ReadFailed

                else -> receive(channel)
            }
        }
    }

    private fun concluded(session: HandshakeSession): HandshakeOutcome? {
        val context = session.context
        val ready = context.session
        return when {
            context.state == ConnectionState.READY && ready != null -> HandshakeOutcome.Ready(ready)

            context.state == ConnectionState.CLOSED ->
                HandshakeOutcome.Closed(session.closeReason ?: CloseReason.PROTOCOL_VIOLATION)

            else -> null
        }
    }

    private suspend fun receive(channel: HandshakeChannel): HandshakeEvent = when (val inbound = channel.receive()) {
        is Inbound.Message -> HandshakeEvent.Message(inbound.message)
        Inbound.EndOfStream -> HandshakeEvent.PeerClosed
        Inbound.Failed -> HandshakeEvent.ReadFailed
    }

    private suspend fun authenticate(request: AuthRequest): HandshakeEvent =
        when (val outcome = executor.start(adapter, request).await()) {
            is AuthOutcome.Failed ->
                HandshakeEvent.AuthRejected(LoginRejection.ADAPTER_FAILURE, outcome.exceptionType)

            is AuthOutcome.Decided -> decided(outcome.result)
        }

    private fun decided(result: AuthResult): HandshakeEvent = when (result) {
        is AuthResult.Accepted -> HandshakeEvent.AuthAccepted(result.principal, sessionIds())
        is AuthResult.Rejected -> HandshakeEvent.AuthRejected(LoginRejection.from(result.reason))
    }

    companion object {
        val DEFAULT_LOGIN_TIMEOUT = 15.seconds
    }
}
