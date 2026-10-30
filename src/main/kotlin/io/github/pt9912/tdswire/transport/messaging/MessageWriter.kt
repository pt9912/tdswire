package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.FramingResult
import io.github.pt9912.tdswire.transport.framing.PacketWriter
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import java.nio.ByteBuffer

/**
 * Schreibt Nachrichten in Paketen der [packetSize] auf einen [SuspendingChannel]. Die Zerlegung
 * gehört dem [PacketWriter]. Jedes Paket wird mit [SuspendingChannel.writeFully] geschrieben; der
 * Writer suspendiert, solange der Kanal nichts annimmt, und legt keine Warteschlange an.
 *
 * Festlegung: [PacketWriter.split] erzeugt alle Pakete einer Nachricht auf einmal; der Speicher
 * dieses Aufrufs wächst mit der Nachricht, nicht mit der Zahl der Nachrichten. Eine Obergrenze der
 * Nachrichtengröße prüft der Writer nicht; die Speichergrenze für Ergebnisströme liefert
 * `slice-backend-streaming-nachweis`. Nicht thread-sicher:
 * ein Writer gehört einem Aufrufer.
 */
class MessageWriter(private val channel: SuspendingChannel, private val packetSize: Int) {
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
