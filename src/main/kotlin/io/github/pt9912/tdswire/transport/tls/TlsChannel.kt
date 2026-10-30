package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Einstellungen der TLS-Schleife. [packetSize] ist die Packet Size der `0x12`-Pakete im Handshake
 * von TDS 7.4 (Header eingerechnet; Festlegung ohne Messung: 4096), [readBufferSize] die Größe des
 * Lesepuffers auf dem Strom, [taskDispatcher] führt die ausgelagerten Tasks der Engine aus.
 */
data class TlsOptions(
    val packetSize: Int = DEFAULT_PACKET_SIZE,
    val readBufferSize: Int = DEFAULT_READ_BUFFER,
    val taskDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private companion object {
        const val DEFAULT_PACKET_SIZE = 4096
        const val DEFAULT_READ_BUFFER = 8192
    }
}

/**
 * Ein [SuspendingChannel], der den Strom eines anderen Kanals mit einer eigenen `SSLEngine`-Schleife
 * verschlüsselt (`ADR-0003`, `SPEC-028`). [accept] führt den Handshake als Server aus und liefert
 * den verschlüsselten Kanal oder einen [TlsError]; nach einem Fehler ist der innere Kanal geschlossen,
 * es folgt kein Klartext.
 *
 * Pfade: in [TlsPath.ENCAPSULATED_IN_TDS] reisen die Handshake-Bytes in Paketen vom Typ `0x12`, und
 * mit `FINISHED` schaltet der Kanal auf rohes TLS um; Bytes, die hinter dem letzten Handshake-Paket
 * schon gelesen sind, gehen als TLS-Bytes in die Datenphase. In [TlsPath.FROM_FIRST_BYTE] ist der
 * Strom von Anfang an TLS, und die Gegenstelle muss ALPN `tds/8.0` ausgehandelt haben
 * (Festlegung dieser Schleife, nicht gemessen).
 *
 * Datenphase: [read] liefert entschlüsselte Bytes oder -1 nach `close_notify`; endet der Strom ohne
 * `close_notify`, ist das ein [ChannelResult.Failed]. Der Puffer für entschlüsselte Daten ist auf
 * [decryptedBufferCapacity] begrenzt. [writeFully] kehrt zurück, wenn der innere Kanal alle
 * verschlüsselten Bytes angenommen hat. Höchstens ein Lese- und ein Schreibvorgang zugleich
 * (Vertrag von [SuspendingChannel]). Was die Engine in der Datenphase an Handshake-Daten zu senden hat,
 * schreibt die Schleife; geprüft sind `close_notify` und ein von einer JDK-Gegenstelle ausgelöstes
 * KeyUpdate in TLS 1.3. Neuverhandlung in TLS 1.2 ist nicht Gegenstand.
 */
class TlsChannel internal constructor(private val inner: SuspendingChannel, private val io: TlsRecordIo) :
    SuspendingChannel {
    private val writer = TlsWriter(io)
    private val reader = TlsReader(io, writer)

    /** Kapazität des Puffers für entschlüsselte Daten; er wächst nie. */
    val decryptedBufferCapacity: Int get() = reader.capacity

    /** Die ausgehandelte Protokollversion, etwa `TLSv1.3`. */
    val protocol: String get() = io.engine.session.protocol

    /** Die ausgehandelte Suite. */
    val cipherSuite: String get() = io.engine.session.cipherSuite

    /** Das ausgehandelte ALPN oder `null`, wenn keines ausgehandelt ist. */
    val applicationProtocol: String? get() = io.engine.applicationProtocol?.ifEmpty { null }

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> = closingOnCancel { reader.read(buffer) }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> = closingOnCancel { writer.write(buffer) }

    /**
     * Sendet `close_notify` und schließt den inneren Kanal. Der Ausgang meldet, ob `close_notify`
     * geschrieben werden konnte; geschlossen ist der Kanal in jedem Fall.
     */
    suspend fun closeGracefully(): ChannelResult<Unit> = closingOnCancel {
        val sent = writer.closeOutbound()
        inner.close()
        sent
    }

    /** Schließt den inneren Kanal ohne `close_notify`; ein weiterer Aufruf ist wirkungslos. */
    override fun close() {
        inner.close()
    }

    private suspend fun <T> closingOnCancel(operation: suspend () -> T): T = try {
        operation()
    } catch (cancelled: CancellationException) {
        close()
        throw cancelled
    }

    companion object {
        /** Das ALPN des TDS-8.0-Pfads. */
        const val ALPN_TDS_8_0 = "tds/8.0"

        /**
         * Führt den Handshake als Server über [channel] aus. [context] ist ein fertiger `SSLContext`
         * mit dem Schlüssel des Servers; [parameters] bestimmt Versionen und Suiten, [path] den Pfad.
         * Ein Fehler ist ein [TlsResult.Failed]; ein `IllegalArgumentException` kommt nur, wenn die
         * JVM eine genannte Version oder Suite nicht kennt (Konfigurationsfehler des Aufrufers).
         * Der innere Kanal ist geschlossen, wenn [accept] nicht mit einem Kanal endet: nach einem
         * Fehlerwert, nach einem Abbruch und nach jeder anderen Ausnahme, die dann weiterläuft.
         */
        suspend fun accept(
            channel: SuspendingChannel,
            context: SSLContext,
            path: TlsPath,
            parameters: TlsParameters = TlsParameters.SPEC_023,
            options: TlsOptions = TlsOptions(),
        ): TlsResult<TlsChannel> {
            var keep = false
            try {
                val outcome = TlsAcceptor.accept(channel, context, path, parameters, options)
                keep = outcome is TlsResult.Ok
                return outcome
            } finally {
                if (!keep) channel.close()
            }
        }
    }
}
