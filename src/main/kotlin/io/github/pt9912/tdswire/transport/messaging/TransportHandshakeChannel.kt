package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.protocol.session.HandshakeChannel
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.session.ReplyStream
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Setzt [HandshakeChannel] auf [MessageReader], [MessageWriter] und [SuspendingChannel] um
 * (`ARC-003`, `ARC-004`). Der Leser trägt die Vor-Login-Grenze ([FramingLimits.preLogin]), solange der
 * Handshake läuft; [useSessionLimits] wechselt sie an demselben Leser auf die Standardgrenze, damit
 * gepufferte Bytes und fertige Nachrichten hinter dem LOGIN7 erhalten bleiben. Der TLS-Handschlag
 * gehört dem Aufrufer und kommt als [tlsHandshake]; er liest und schreibt über denselben Kanal.
 */
class TransportHandshakeChannel(
    private val reader: MessageReader,
    private val writer: MessageWriter,
    private val channel: SuspendingChannel,
    private val tlsHandshake: suspend () -> Boolean,
) : HandshakeChannel {
    override suspend fun receive(): Inbound = when (val outcome = reader.next()) {
        is ReadOutcome.Message -> Inbound.Message(outcome.message)
        ReadOutcome.EndOfStream -> Inbound.EndOfStream
        is ReadOutcome.Failed -> Inbound.Failed
    }

    override suspend fun send(message: TdsMessage): Boolean = writer.write(message) is WriteOutcome.Written

    override fun openReply(type: PacketType): ReplyStream = writer.open(type)

    override fun usePacketSize(size: Int) = writer.usePacketSize(size)

    override suspend fun startTls(): Boolean = tlsHandshake()

    override fun useSessionLimits() = reader.useLimits(FramingLimits())

    override fun close() = channel.close()
}
