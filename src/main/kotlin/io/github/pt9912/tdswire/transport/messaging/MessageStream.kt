package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.protocol.session.ReplyStream
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketHeader
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.PacketWriter
import java.nio.ByteBuffer

private const val ID_MODULUS = 256

/** Baut ein Paket aus Typ, Puffer, Nutzdatenlänge, EOM-Bit und Packet-ID (Signatur von [PacketWriter.packet]). */
internal typealias PacketBuilder = (PacketType, ByteArray, Int, Boolean, Int) -> ByteArray

/**
 * Schreibt eine Nachricht in Stücken als Pakete der Packet Size (`SPEC-006`, `SPEC-028`). Jedes Paket
 * außer dem letzten trägt genau `packetSize - 8` Nutzdatenbytes, das letzte den Rest und das EOM-Bit;
 * die Packet-ID zählt ab 1 und läuft nach 255 auf 0 über, wie bei [PacketWriter.split].
 *
 * Festlegung (Speichergrenze): ein volles Paket geht erst auf den Kanal, wenn weitere Bytes folgen
 * oder [finish] kommt, weil erst dann feststeht, ob es das letzte ist. Gehalten wird damit höchstens
 * ein Paket Nutzdaten im Puffer, dazu das Paket im Schreiben samt den Kopien beim Paketbau
 * (`copyOfRange`, Verkettung mit dem Header) und das Stück des Aufrufers; die Zahl hängt nicht an der
 * Nachrichtengröße, eine Byte-Zahl ist nicht gemessen. Eine Packet Size außerhalb von 9 bis 65535 macht
 * den Strom von Anfang an fehlerhaft ([write] und [finish] liefern `false`).
 *
 * Zusage (Token-Grenze): fällt eine Ausnahme (etwa `OutOfMemoryError`) aus dem Paketbau oder dem Kanal,
 * ist der Strom fehlerhaft, ob sie vor oder nach der Entnahme des Pakets aus dem Puffer fiel; er steht
 * dann womöglich mitten in einem Token, [write] und [finish] liefern danach `false` und schreiben
 * nichts, damit hinter ein halbes Token weder ERROR noch DONE gelangt. Nicht thread-sicher.
 */
class MessageStream internal constructor(
    private val channel: SuspendingChannel,
    private val type: PacketType,
    packetSize: Int,
    private val build: PacketBuilder,
) : ReplyStream {
    private val usable = packetSize > PacketHeader.SIZE && packetSize <= FramingLimits.MAX_TDS_PACKET_BYTES
    private val buffer = ByteArray(if (usable) packetSize - PacketHeader.SIZE else 0)
    private var fill = 0
    private var nextId = 1
    private var broken = !usable
    private var finished = false

    override suspend fun write(bytes: ByteArray): Boolean {
        var open = !broken && !finished
        var from = 0
        while (open && from < bytes.size) {
            open = fill < buffer.size || flush(endOfMessage = false)
            if (open) {
                val count = minOf(buffer.size - fill, bytes.size - from)
                bytes.copyInto(buffer, fill, from, from + count)
                fill += count
                from += count
            }
        }
        return open
    }

    override suspend fun finish(): Boolean {
        if (broken || finished) return false
        finished = true
        return flush(endOfMessage = true)
    }

    private suspend fun flush(endOfMessage: Boolean): Boolean {
        broken = true
        val packet = build(type, buffer, fill, endOfMessage, nextId)
        nextId = (nextId + 1) % ID_MODULUS
        fill = 0
        val sent = channel.writeFully(ByteBuffer.wrap(packet))
        broken = sent is ChannelResult.Failed
        return !broken
    }
}
