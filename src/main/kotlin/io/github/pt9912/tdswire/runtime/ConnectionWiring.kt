package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.LoginSettings
import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.dispatch.RequestLoop
import io.github.pt9912.tdswire.protocol.error.ErrorLog
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.session.AuthPool
import io.github.pt9912.tdswire.protocol.session.EncryptionPolicy
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
 * Adapteraufrufe für alle Verbindungen gilt. [tls] ist gesetzt, wenn die Richtlinie TLS verlangt.
 */
internal class HandlerSetup(
    val handshake: HandshakeConfig,
    val adapter: AuthAdapter,
    val authPool: AuthPool,
    val login: LoginSettings,
    val tls: TlsMaterial?,
    val log: ServerLog,
)

/**
 * Der Handler des Listeners (`ARC-008`): verbindet den Kanal der Verbindung mit Leser, Schreiber und
 * [HandshakeDriver] und meldet nach der Annahme „Login abgeschlossen“. `LOGIN_TIMEOUT` läuft ab der
 * Annahme der Verbindung im Listener; der Handler wartet bis zu `LOGIN_TIMEOUT` auf das erste Byte und startet
 * den Treiber danach. Dessen eigene Frist (gleicher Wert) beginnt mit `run` und endet damit nie vor der des
 * Listeners.
 *
 * Nach der Annahme beantwortet die [RequestLoop] die Anfragen an das [backend]. Ist keines
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
 * der Pfad gegen eine JDK-Gegenstelle und Rohbytes (`Tds8WiringTest`), nicht gegen den Referenzclient.
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
        val config = handshakeConfig(path) ?: return
        val wire = SwitchableChannel(peeked)
        val reader = MessageReader(wire, preLoginLimits(setup.login.maxPreloginMessageBytes))
        val channel = TransportHandshakeChannel(reader, MessageWriter(wire, PACKET_SIZE), wire) {
            upgrade(peeked, wire, path)
        }
        val driver = HandshakeDriver(
            config,
            setup.adapter,
            HandshakeLog { setup.log.line("Login: ${describe(it)}") },
            setup.login.timeoutSeconds.seconds,
            setup.authPool,
        )
        val outcome = driver.run(channel)
        if (outcome is HandshakeOutcome.Ready) {
            connection.loginCompleted()
            if (backend == null) channel.receive() else RequestLoop(outcome, channel, backend, errorMapper()).run()
        }
    }

    /** Die Konfiguration des Pfads; `null` (und eine Logzeile), wenn der Prozess den TDS-8.0-Pfad nicht anbietet. */
    private fun handshakeConfig(path: TlsPath): HandshakeConfig? = when {
        path == TlsPath.ENCAPSULATED_IN_TDS -> setup.handshake

        setup.tls == null -> {
            setup.log.line("TLS ab dem ersten Byte nicht angeboten: kein TLS-Material")
            null
        }

        // Verteidigung: die Konfiguration lässt TLS-Material nur mit REQUIRED zu, und HandshakeConfig verweigert
        // TLS_FIRST mit einer anderen Richtlinie; ein HandlerSetup anderer Herkunft trennt statt zu werfen.
        setup.handshake.encryptionPolicy != EncryptionPolicy.REQUIRED -> {
            setup.log.line("TLS ab dem ersten Byte nicht angeboten: Richtlinie nicht REQUIRED")
            null
        }

        else -> setup.handshake.copy(order = HandshakeOrder.TLS_FIRST)
    }

    private fun errorMapper() = ErrorMapper(
        ErrorLog { setup.log.line("Fehler: ${it.code}${it.cause?.let { cause -> " ($cause)" }.orEmpty()}") },
    )

    /** Die Grenzen vor dem Login: höchstens [bytes] je Nachricht, kein Paket länger als die Nachricht. */
    private fun preLoginLimits(bytes: Int): FramingLimits =
        FramingLimits(maxPacketBytes = minOf(FramingLimits.MAX_TDS_PACKET_BYTES, bytes), maxMessageBytes = bytes)

    private suspend fun upgrade(raw: SuspendingChannel, wire: SwitchableChannel, path: TlsPath): Boolean {
        val material = setup.tls ?: return false
        return when (val accepted = TlsChannel.accept(raw, material.context, path, PARAMETERS)) {
            is TlsResult.Failed -> {
                setup.log.line("TLS-Handschlag gescheitert: ${accepted.error.failure}")
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
