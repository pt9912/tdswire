package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.ChannelError
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.FramingResult
import io.github.pt9912.tdswire.transport.framing.PacketHeader
import io.github.pt9912.tdswire.transport.framing.PacketType
import java.nio.ByteBuffer

/** Ausgang eines Füllvorgangs von [NetInbound]. */
internal sealed interface Pulled {
    /** [count] TLS-Bytes (mindestens eines) liegen im Zielpuffer. */
    data class Data(val count: Int) : Pulled

    data object EndOfStream : Pulled

    data class ChannelFailed(val error: ChannelError) : Pulled

    /** Die Bytes verletzen die Kapselung; [detail] nennt Typ oder Grund, nie Nutzdaten. */
    data class Violation(val detail: String) : Pulled
}

/**
 * Liefert der Engine die TLS-Bytes der Gegenstelle aus dem Strom. Im Rohmodus gehen die Bytes des
 * Kanals unverändert durch. Im Paketmodus (Handshake von TDS 7.4) liest er TDS-Header über den
 * Paket-Codec, verlangt den Pakettyp `0x12` ([PacketType.PRELOGIN]) und liefert nur die Nutzdaten.
 *
 * Der Lesepuffer `raw` gehört dem Strom, nicht der Engine: was hinter dem letzten Handshake-Paket
 * darin liegt, bleibt beim Umschalten in [endPackets] erhalten und ist das Erste, was der Rohmodus
 * liefert. Nicht thread-sicher: ein Leser.
 */
internal class NetInbound(private val channel: SuspendingChannel, readBufferSize: Int) {
    private val raw = ByteBuffer.allocate(readBufferSize).apply { limit(0) }
    private val header = ByteArray(PacketHeader.SIZE)
    private val limits = FramingLimits.preLogin()
    private var headerFill = 0
    private var payloadLeft = 0
    private var packetMode = false

    /** Schaltet in den Paketmodus; gilt ab dem nächsten Füllvorgang. */
    fun startPackets() {
        packetMode = true
    }

    /**
     * Schaltet in den Rohmodus. Ist ein Handshake-Paket noch nicht aufgebraucht, bleibt der Modus
     * unverändert und das Ergebnis ist `false`: die Bytes gehören dem Paket, nicht dem rohen Strom.
     */
    fun endPackets(): Boolean {
        val clean = payloadLeft == 0 && headerFill == 0
        if (clean) packetMode = false
        return clean
    }

    /** Hängt mindestens ein Byte an [target] an (Schreibmodus, Platz verlangt) oder meldet, warum nicht. */
    suspend fun fill(target: ByteBuffer): Pulled = if (packetMode) fillFromPacket(target) else fillRaw(target)

    private suspend fun fillRaw(target: ByteBuffer): Pulled =
        ensureRaw() ?: Pulled.Data(transfer(target, raw.remaining()))

    private suspend fun fillFromPacket(target: ByteBuffer): Pulled {
        var ended: Pulled? = null
        while (payloadLeft == 0 && ended == null) ended = nextHeader()
        return ended ?: ensureRaw() ?: movePayload(target)
    }

    private fun movePayload(target: ByteBuffer): Pulled {
        val moved = transfer(target, payloadLeft)
        payloadLeft -= moved
        return Pulled.Data(moved)
    }

    private suspend fun nextHeader(): Pulled? {
        while (headerFill < PacketHeader.SIZE) {
            val unavailable = ensureRaw()
            if (unavailable != null) return unavailable
            val take = minOf(PacketHeader.SIZE - headerFill, raw.remaining())
            raw.get(header, headerFill, take)
            headerFill += take
        }
        headerFill = 0
        return when (val parsed = PacketHeader.parse(header, 0, limits)) {
            is FramingResult.Failed -> Pulled.Violation("Paketheader ${parsed.error.failure}")
            is FramingResult.Ok -> admit(parsed.value)
        }
    }

    private fun admit(packet: PacketHeader): Pulled? {
        if (packet.type != PacketType.PRELOGIN) return Pulled.Violation("Pakettyp ${packet.type} im Handshake")
        payloadLeft = packet.length - PacketHeader.SIZE
        return null
    }

    /** `null`, wenn `raw` Bytes hält; sonst der Grund, aus dem der Kanal keine liefert. */
    private suspend fun ensureRaw(): Pulled? {
        if (raw.hasRemaining()) return null
        raw.clear()
        val read = channel.read(raw)
        raw.flip()
        return when {
            read is ChannelResult.Failed -> Pulled.ChannelFailed(read.error)
            read is ChannelResult.Ok && read.value < 0 -> Pulled.EndOfStream
            else -> null
        }
    }

    private fun transfer(target: ByteBuffer, atMost: Int): Int {
        val count = minOf(raw.remaining(), atMost, target.remaining())
        val originalLimit = raw.limit()
        raw.limit(raw.position() + count)
        target.put(raw)
        raw.limit(originalLimit)
        return count
    }
}
