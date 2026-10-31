package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.FramingResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.PacketWriter
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import java.nio.ByteBuffer

/**
 * Schreibt Nachrichten in Paketen der [packetSize] auf einen [SuspendingChannel]. Die Zerlegung
 * gehört dem [PacketWriter]. Jedes Paket wird mit [SuspendingChannel.writeFully] geschrieben; der
 * Writer suspendiert, solange der Kanal nichts annimmt, und legt keine Warteschlange an.
 *
 * Festlegung: [write] hat die Nachricht fertig im Speicher; [PacketWriter.split] erzeugt alle Pakete
 * auf einmal, der Speicher wächst mit der Nachricht, und eine Obergrenze der Nachrichtengröße prüft
 * der Writer nicht. Ergebnisströme gehen über [open] ([MessageStream]), dessen Speicher ein Paket
 * beträgt. Nicht thread-sicher: ein Writer gehört einem Aufrufer.
 */
class MessageWriter(private val channel: SuspendingChannel, private var packetSize: Int) {
    /** Der Paketbau der Ströme aus [open]; Tests ersetzen ihn, um Ausnahmen im Paketbau auszulösen. */
    internal var packetBuilder: PacketBuilder = PacketWriter::packet

    /** Stellt die Packet Size für folgende [write]- und [open]-Aufrufe ein; eine laufende Antwort behält ihre. */
    fun usePacketSize(size: Int) {
        packetSize = size
    }

    /** Beginnt eine Antwort vom Typ [type] in Stücken; gehalten wird ein Paket, nicht die Nachricht. */
    fun open(type: PacketType): MessageStream = MessageStream(channel, type, packetSize, packetBuilder)

    /** Schreibt [message] vollständig oder meldet den ersten Fehler; bei einem Fehler ist offen, wie viel ankam. */
    suspend fun write(message: TdsMessage): WriteOutcome =
        when (val split = PacketWriter.split(message.type, message.payload, packetSize)) {
            is FramingResult.Failed -> WriteOutcome.Failed(TransportError.Framing(split.error))
            is FramingResult.Ok -> send(split.value)
        }

    private suspend fun send(packets: List<ByteArray>): WriteOutcome {
        for (packet in packets) {
            val sent = channel.writeFully(ByteBuffer.wrap(packet))
            if (sent is ChannelResult.Failed) return WriteOutcome.Failed(TransportError.Channel(sent.error))
        }
        return WriteOutcome.Written
    }
}
