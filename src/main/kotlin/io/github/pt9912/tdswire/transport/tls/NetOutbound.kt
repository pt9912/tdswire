package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.ChannelFailure
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.FramingResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.PacketWriter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Schreibt die TLS-Bytes der Engine auf den Strom. Im Rohmodus geht jeder Aufruf von [stage] sofort
 * auf den Kanal. Im Paketmodus (Handshake von TDS 7.4) sammelt [stage] die Bytes einer Sendefolge,
 * und [flush] schreibt sie als eine Nachricht vom Typ `0x12` ([PacketType.PRELOGIN]) in Paketen der
 * `packetSize`; die Zerlegung gehört dem [PacketWriter].
 *
 * Festlegung: die Sendefolge ist alles, was die Engine ohne zwischenzeitliches Warten auf die
 * Gegenstelle erzeugt; ihre Größe ist die einer Handshake-Folge (Zertifikatskette), nicht begrenzt.
 * Nicht thread-sicher: ein Schreiber.
 */
internal class NetOutbound(private val channel: SuspendingChannel, private val packetSize: Int) {
    private val sequence = ByteArrayOutputStream()
    private var packetMode = false

    /** Schaltet in den Paketmodus. */
    fun startPackets() {
        packetMode = true
    }

    /** Schaltet in den Rohmodus; das Ergebnis ist `false`, solange eine Sendefolge nicht geschrieben ist. */
    fun endPackets(): Boolean {
        val clean = sequence.size() == 0
        if (clean) packetMode = false
        return clean
    }

    suspend fun stage(bytes: ByteArray): TlsResult<Unit> {
        if (packetMode) {
            sequence.write(bytes)
            return TlsResult.Ok(Unit)
        }
        return write(bytes)
    }

    suspend fun flush(): TlsResult<Unit> {
        if (sequence.size() == 0) return TlsResult.Ok(Unit)
        val payload = sequence.toByteArray()
        sequence.reset()
        return when (val split = PacketWriter.split(PacketType.PRELOGIN, payload, packetSize)) {
            is FramingResult.Failed -> failed(TlsFailure.PROTOCOL_VIOLATION, "Packet Size ${split.error.failure}")
            is FramingResult.Ok -> writeAll(split.value)
        }
    }

    private suspend fun writeAll(packets: List<ByteArray>): TlsResult<Unit> {
        for (packet in packets) {
            val sent = write(packet)
            if (sent is TlsResult.Failed) return sent
        }
        return TlsResult.Ok(Unit)
    }

    private suspend fun write(bytes: ByteArray): TlsResult<Unit> =
        when (val sent = channel.writeFully(ByteBuffer.wrap(bytes))) {
            is ChannelResult.Failed -> failed(abortedOrClosed(sent.error.failure), "Kanal ${sent.error.failure}")
            is ChannelResult.Ok -> TlsResult.Ok(Unit)
        }

    private fun abortedOrClosed(failure: ChannelFailure): TlsFailure =
        if (failure == ChannelFailure.CLOSED) TlsFailure.CLOSED else TlsFailure.CONNECTION_ABORTED
}
