package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.LoginSettings
import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.protocol.session.AuthPool
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.HandshakeDriver
import io.github.pt9912.tdswire.protocol.session.HandshakeLog
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
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
 * Annahme der Verbindung im Listener; der Handler startet den Treiber ohne Verzug, dessen eigene Frist
 * (gleicher Wert) beginnt mit `run` und endet damit nie vor der des Listeners.
 *
 * Festlegung ohne Messung: Es gibt noch keinen Anfragepfad. Nach der Annahme wartet der Handler auf die
 * nächste Nachricht oder das Ende der Gegenstelle und kehrt dann zurück; der Listener schließt die
 * Verbindung. Eine Anfrage bleibt damit unbeantwortet. Der Paket-Schreiber arbeitet mit
 * [PACKET_SIZE]; die angekündigte Packet Size wird nicht angewandt (`AuthenticatedSession`).
 * TLS läuft im TDS-7.4-Pfad (gekapselt in Paketen vom Typ `0x12`); der Pfad ab dem ersten Byte (TDS 8.0)
 * ist nicht verdrahtet.
 */
internal class HandshakeConnectionHandler(private val setup: HandlerSetup) : ConnectionHandler {
    override suspend fun handle(connection: Connection) {
        val wire = SwitchableChannel(connection.channel)
        val reader = MessageReader(wire, preLoginLimits(setup.login.maxPreloginMessageBytes))
        val channel = TransportHandshakeChannel(reader, MessageWriter(wire, PACKET_SIZE), wire) {
            upgrade(connection.channel, wire)
        }
        val driver = HandshakeDriver(
            setup.handshake,
            setup.adapter,
            HandshakeLog { setup.log.line("Login: ${describe(it)}") },
            setup.login.timeoutSeconds.seconds,
            setup.authPool,
        )
        if (driver.run(channel) is HandshakeOutcome.Ready) {
            connection.loginCompleted()
            channel.receive()
        }
    }

    /** Die Grenzen vor dem Login: höchstens [bytes] je Nachricht, kein Paket länger als die Nachricht. */
    private fun preLoginLimits(bytes: Int): FramingLimits =
        FramingLimits(maxPacketBytes = minOf(FramingLimits.MAX_TDS_PACKET_BYTES, bytes), maxMessageBytes = bytes)

    private suspend fun upgrade(raw: SuspendingChannel, wire: SwitchableChannel): Boolean {
        val material = setup.tls ?: return false
        return when (val accepted = TlsChannel.accept(raw, material.context, TlsPath.ENCAPSULATED_IN_TDS, PARAMETERS)) {
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
        private val PARAMETERS = TlsParameters.SPEC_023
    }
}
