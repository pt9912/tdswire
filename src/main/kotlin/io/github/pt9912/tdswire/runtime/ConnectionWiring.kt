package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.LoginSettings
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.domain.telemetry.EventContext
import io.github.pt9912.tdswire.domain.telemetry.LoginAccepted
import io.github.pt9912.tdswire.domain.telemetry.TlsFirstNotOffered
import io.github.pt9912.tdswire.domain.telemetry.TlsFirstRefusal
import io.github.pt9912.tdswire.domain.telemetry.TlsHandshakeFailed
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.dispatch.RequestHooks
import io.github.pt9912.tdswire.protocol.dispatch.RequestLoop
import io.github.pt9912.tdswire.protocol.dispatch.RequestTimes
import io.github.pt9912.tdswire.protocol.error.ErrorLog
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.session.AuthPool
import io.github.pt9912.tdswire.protocol.session.EncryptionPolicy
import io.github.pt9912.tdswire.protocol.session.HandshakeChannel
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.HandshakeDriver
import io.github.pt9912.tdswire.protocol.session.HandshakeLog
import io.github.pt9912.tdswire.protocol.session.HandshakeOrder
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.transport.channel.PeekedChannel
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.channel.SwitchableChannel
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.listener.Connection
import io.github.pt9912.tdswire.transport.listener.ConnectionHandler
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.TransportHandshakeChannel
import io.github.pt9912.tdswire.transport.tls.TlsChannel
import io.github.pt9912.tdswire.transport.tls.TlsMaterial
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import io.github.pt9912.tdswire.transport.tls.TlsPath
import io.github.pt9912.tdswire.transport.tls.TlsResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Was der Handler einer Verbindung aus dem Prozess braucht. Der [authPool] gehört dem Prozess: jede
 * Verbindung baut ihren Treiber mit diesem einen Pool, sodass die Obergrenze der gleichzeitigen
 * Adapteraufrufe für alle Verbindungen gilt. Der Pool trägt die Drosselung wiederholter Anmeldeversuche
 * (`AuthPool.throttle`, `ADR-0016`): ihre Tabelle der Quelladressen gilt für alle Verbindungen. [tls]
 * ist gesetzt, wenn die Richtlinie TLS verlangt.
 */
internal class HandlerSetup(
    val handshake: HandshakeConfig,
    val adapter: AuthAdapter,
    val authPool: AuthPool,
    val login: LoginSettings,
    val tls: TlsMaterial?,
    val telemetry: Telemetry,
) {
    /** Die Fristen einer Anfrage; die Voreinstellungen von `SPEC-009` und `SPEC-033`, bis der Prozess sie setzt. */
    var requests: RequestSettings = RequestSettings()
}

/**
 * Der Handler des Listeners (`ARC-008`): verbindet den Kanal der Verbindung mit Leser, Schreiber und
 * [HandshakeDriver] und meldet nach der Annahme „Login abgeschlossen“. `LOGIN_TIMEOUT` läuft ab der
 * Annahme der Verbindung im Listener; der Handler wartet bis zu `LOGIN_TIMEOUT` auf das erste Byte und startet
 * den Treiber danach. Dessen eigene Frist (gleicher Wert) beginnt mit `run` und endet damit nie vor der des
 * Listeners.
 *
 * Nach der Annahme beantwortet die [RequestLoop] die Anfragen an das [backend]; sie meldet dem Listener über
 * [ConnectionShutdownGate], ob eine Anfrage läuft (geordnetes Beenden, `SPEC-044`). Ist keines
 * gesetzt (der Betrieb hat noch kein Backend), gibt es keinen Anfragepfad (Festlegung ohne Messung): der
 * Handler wartet auf die nächste Nachricht oder das Ende der Gegenstelle und kehrt dann zurück; der
 * Listener schließt die Verbindung, eine Anfrage bleibt unbeantwortet. Der Paket-Schreiber arbeitet bis zum
 * Login mit [PACKET_SIZE]; danach stellt die `RequestLoop` ihn auf die Packet Size der Sitzung ein.
 *
 * Das Warten auf das erste Byte hat dieselbe Frist wie der Treiber (`LOGIN_TIMEOUT`); läuft sie ab, kehrt der
 * Handler zurück, und im Echtzeitbetrieb meldet der Listener das, sofern die Rückkehr seinem Zeitgeber zuvorkommt,
 * als `HANDLER_RETURNED` statt `LOGIN_TIMEOUT`. Pfadwahl je Verbindung am ersten Byte, am selben Port und ohne
 * Konfigurationsschalter (Festlegung des Slice, nicht gemessen): `0x16` (TLS-Inhaltstyp Handshake; kein
 * TDS-Pakettyp) wählt den Pfad ab dem ersten Byte (TDS 8.0, [HandshakeOrder.TLS_FIRST], ALPN `tds/8.0`:
 * `CONNECTED → TLS_NEGOTIATION → PRELOGIN`); jedes andere Byte läuft im TDS-7.4-Pfad (PRELOGIN, TLS gekapselt in
 * Paketen vom Typ `0x12`), und was dort kein gültiges PRELOGIN ist, endet ohne TDS-Antwort. Der TDS-8.0-Pfad
 * besteht nur mit TLS-Material und der Richtlinie `REQUIRED`; sonst trennt der Handler ohne Antwort. Ein
 * Klartext-Anfang nach dem ersten Byte `0x16` scheitert im TLS-Handschlag (kein Rückfall auf Klartext). Belegt ist
 * der Pfad gegen eine JDK-Gegenstelle und Rohbytes (`Tds8WiringTest`) und am Referenzclient (`Encrypt=Strict` mit
 * SQL-Authentifizierung, `make kompatibilitaet KATEGORIE=Anmeldung`); die ausgehandelte TLS-Version und Suite sind
 * dort nicht beobachtet.
 */
internal class HandshakeConnectionHandler(private val setup: HandlerSetup, private val backend: QueryBackend? = null) :
    ConnectionHandler {
    override suspend fun handle(connection: Connection) {
        val peeked = withTimeoutOrNull(setup.login.timeoutSeconds.seconds) { PeekedChannel.peek(connection.channel) }
            ?: return
        val path = when (peeked.firstByte) {
            TLS_HANDSHAKE_RECORD -> TlsPath.FROM_FIRST_BYTE
            else -> TlsPath.ENCAPSULATED_IN_TDS
        }
        val early = EventContext(connection.id, clientProfile = profileOf(path, null))
        val config = handshakeConfig(path, early) ?: return
        val wire = SwitchableChannel(peeked)
        val reader = MessageReader(
            wire,
            preLoginLimits(setup.login.maxPreloginMessageBytes),
            onMidMessage = connection::messageInProgress,
        )
        val channel = TransportHandshakeChannel(reader, MessageWriter(wire, PACKET_SIZE), wire) {
            upgrade(peeked, wire, path, early)
        }
        val driver = HandshakeDriver(
            config,
            setup.adapter,
            HandshakeLog { entry -> setup.telemetry.emit { entry.toEvent(it, early) } },
            setup.login.timeoutSeconds.seconds,
            setup.authPool,
        )
        val outcome = driver.run(channel, connection.remoteAddress)
        if (outcome is HandshakeOutcome.Ready) {
            connection.loginCompleted()
            val context = EventContext(
                connection.id,
                outcome.session.id,
                profileOf(path, outcome.live.context.encryption),
            )
            setup.telemetry.emit { LoginAccepted(it, context, outcome.session.principal.name) }
            if (backend == null) channel.receive() else serve(connection, outcome, channel, backend, context)
        }
    }

    private suspend fun serve(
        connection: Connection,
        outcome: HandshakeOutcome.Ready,
        channel: HandshakeChannel,
        backend: QueryBackend,
        context: EventContext,
    ) {
        val times = RequestTimes(
            queryTimeout = setup.requests.queryTimeoutSeconds.seconds,
            cancelTimeout = setup.requests.cancelTimeoutSeconds.seconds,
        )
        val hooks = RequestHooks(gate = ConnectionShutdownGate(connection))
        RequestLoop(outcome, channel, backend, errorMapper(context), times = times, hooks = hooks).run()
    }

    /** Die Konfiguration des Pfads; `null` (und ein Ereignis), wenn der Prozess den TDS-8.0-Pfad nicht anbietet. */
    private fun handshakeConfig(path: TlsPath, context: EventContext): HandshakeConfig? = when {
        path == TlsPath.ENCAPSULATED_IN_TDS -> setup.handshake

        setup.tls == null -> {
            setup.telemetry.emit { TlsFirstNotOffered(it, context, TlsFirstRefusal.NO_TLS_MATERIAL) }
            null
        }

        // Verteidigung: die Konfiguration lässt TLS-Material nur mit REQUIRED zu, und HandshakeConfig verweigert
        // TLS_FIRST mit einer anderen Richtlinie; ein HandlerSetup anderer Herkunft trennt statt zu werfen.
        setup.handshake.encryptionPolicy != EncryptionPolicy.REQUIRED -> {
            setup.telemetry.emit { TlsFirstNotOffered(it, context, TlsFirstRefusal.POLICY_NOT_REQUIRED) }
            null
        }

        else -> setup.handshake.copy(order = HandshakeOrder.TLS_FIRST)
    }

    private fun errorMapper(context: EventContext) =
        ErrorMapper(ErrorLog { entry -> setup.telemetry.emit { entry.toEvent(it, context) } })

    /** Die Grenzen vor dem Login: höchstens [bytes] je Nachricht, kein Paket länger als die Nachricht. */
    private fun preLoginLimits(bytes: Int): FramingLimits =
        FramingLimits(maxPacketBytes = minOf(FramingLimits.MAX_TDS_PACKET_BYTES, bytes), maxMessageBytes = bytes)

    private suspend fun upgrade(
        raw: SuspendingChannel,
        wire: SwitchableChannel,
        path: TlsPath,
        context: EventContext,
    ): Boolean {
        val material = setup.tls ?: return false
        return when (val accepted = TlsChannel.accept(raw, material.context, path, PARAMETERS)) {
            is TlsResult.Failed -> {
                setup.telemetry.emit { TlsHandshakeFailed(it, context, accepted.error.failure.name) }
                false
            }

            is TlsResult.Ok -> {
                wire.switchTo(accepted.value)
                true
            }
        }
    }

    companion object {
        const val PACKET_SIZE = 4096
        private const val TLS_HANDSHAKE_RECORD: Byte = 0x16
        private val PARAMETERS = TlsParameters.SPEC_023
    }
}
