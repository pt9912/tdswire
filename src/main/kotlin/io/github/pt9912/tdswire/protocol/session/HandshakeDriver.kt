package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Ausgang des Handshakes einer Verbindung. */
sealed interface HandshakeOutcome {
    /**
     * Der Login ist angenommen, die Antwort gesendet, die Rahmen-Grenze gewechselt; die Verbindung
     * gehört dem Aufrufer. [live] ist der Sitzungsrahmen, in dem die Anfragezustände weiterlaufen.
     */
    class Ready internal constructor(val session: AuthenticatedSession, internal val live: HandshakeSession) :
        HandshakeOutcome {
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
 * Bricht der Aufrufer [run] ab oder endet [run] mit einer Ausnahme, schließt der Treiber den
 * Kanal und reicht die Ausnahme weiter; die Verbindung bleibt nie offen zurück. Eine
 * `CancellationException` des Adapters ist ein Adapterfehler und wird wie `Rejected` beantwortet, außer
 * der Abbruch stammt vom Aufrufer (siehe [AuthExecutor]). Nur [HandshakeOutcome.Ready]
 * lässt den Kanal offen.
 *
 * Drosselung (`ADR-0016`, `SPEC-053`): [run] nimmt die Adresse der Gegenstelle; vor dem Adapteraufruf wartet der
 * Treiber die Zeit, die die Drosselung des [AuthPool] für diese Adresse nennt. Das Warten gehört zu `LOGIN_TIMEOUT`
 * (die Frist wird nicht an die Wartezeit gebunden: bei einer Frist unter der Wartezeit endet die Anmeldung ohne
 * Antwort), belegt keinen Platz des [AuthPool] und kennt Kennung und Passwort nicht. Ein vom Adapter abgelehnter
 * Versuch zählt als Fehlversuch; Erfolg, Adapterfehler und Abbruch zählen keinen. Ohne Adresse läuft die Anmeldung
 * ungedrosselt.
 *
 * Festlegung ohne Messung: [loginTimeout] hat den vorläufigen Wert 15 s aus `SPEC-008` und beginnt mit
 * [run], nicht mit der TCP-Annahme (Abstand zu `SPEC-008` "ab Verbindungsaufbau": die Zeit
 * bis zum Aufruf von [run] zählt nicht mit); [authPool] legt Dispatcher und die Obergrenze gleichzeitiger
 * Adapteraufrufe fest (Begründung in [AuthExecutor]); die Sitzungs-ID ist eine
 * zufällige UUID.
 */
class HandshakeDriver(
    config: HandshakeConfig,
    private val adapter: AuthAdapter,
    private val log: HandshakeLog = HandshakeLog.NONE,
    private val loginTimeout: Duration = DEFAULT_LOGIN_TIMEOUT,
    authPool: AuthPool = AuthPool(),
    private val sessionIds: () -> String = { UUID.randomUUID().toString() },
) {
    private val automaton = HandshakeAutomaton(config)
    private val executor = AuthExecutor(authPool)
    private val throttle = authPool.throttle

    suspend fun run(channel: HandshakeChannel, source: InetAddress? = null): HandshakeOutcome {
        val session = HandshakeSession(automaton, channel, log)
        var handedOver = false
        try {
            val outcome = withTimeoutOrNull(loginTimeout) { drive(session, channel, source) }
            val result = outcome ?: run {
                session.expire()
                HandshakeOutcome.Closed(CloseReason.LOGIN_TIMEOUT)
            }
            handedOver = true
            return result
        } finally {
            if (!handedOver) channel.close()
        }
    }

    private suspend fun drive(
        session: HandshakeSession,
        channel: HandshakeChannel,
        source: InetAddress?,
    ): HandshakeOutcome {
        var event: HandshakeEvent = HandshakeEvent.Start
        while (true) {
            val awaited = session.dispatch(event)
            concluded(session)?.let { return it }
            event = when (awaited) {
                is HandshakeAction.Authenticate -> authenticate(awaited.request, source)

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
            context.state == ConnectionState.READY && ready != null -> HandshakeOutcome.Ready(ready, session)

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

    /**
     * Wartet die Zeit der Drosselung und ruft dann den Adapter. Bricht das Warten ab, bevor der Adapter gerufen ist,
     * ist das Passwort überschrieben und der Versuch beendet; bricht es ab, bevor der Aufruf endete, wird er
     * abgebrochen (siehe [AuthExecutor]).
     */
    internal suspend fun authenticate(request: AuthRequest, source: InetAddress?): HandshakeEvent {
        val attempt = throttle.enter(source)
        var waited = false
        try {
            attempt.waitTurn()
            waited = true
        } finally {
            if (!waited) {
                request.password.wipe()
                attempt.settle(failed = false)
            }
        }
        var failed = false
        val call = executor.start(adapter, request)
        try {
            return when (val outcome = call.await()) {
                is AuthOutcome.Failed ->
                    HandshakeEvent.AuthRejected(LoginRejection.ADAPTER_FAILURE, outcome.exceptionType)

                is AuthOutcome.Decided -> {
                    failed = outcome.result is AuthResult.Rejected
                    decided(outcome.result)
                }
            }
        } finally {
            call.cancel()
            attempt.settle(failed)
        }
    }

    private fun decided(result: AuthResult): HandshakeEvent = when (result) {
        is AuthResult.Accepted -> HandshakeEvent.AuthAccepted(result.principal, sessionIds())
        is AuthResult.Rejected -> HandshakeEvent.AuthRejected(LoginRejection.from(result.reason))
    }

    companion object {
        val DEFAULT_LOGIN_TIMEOUT = 15.seconds
    }
}
