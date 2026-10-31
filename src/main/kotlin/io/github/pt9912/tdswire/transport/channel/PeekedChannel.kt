package io.github.pt9912.tdswire.transport.channel

import java.nio.ByteBuffer

/**
 * Ein Kanal, dessen erstes Byte schon gelesen ist: [read] liefert zuerst dieses Byte, danach liest er vom
 * inneren Kanal. [peek] liest das erste Byte und gibt es zurück, ohne es zu verbrauchen (`ARC-003`); der
 * Handler wählt daran den Pfad der Verbindung, ohne dass Leser oder TLS-Schleife das Byte vermissen.
 * [close] schließt den inneren Kanal. Der Vertrag von [SuspendingChannel] gilt unverändert.
 */
class PeekedChannel private constructor(val firstByte: Byte, private val inner: SuspendingChannel) :
    SuspendingChannel {
    private var served = false

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
        if (served) return inner.read(buffer)
        served = true
        buffer.put(firstByte)
        return ChannelResult.Ok(1)
    }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> = inner.writeFully(buffer)

    override fun close() = inner.close()

    companion object {
        /** Der Kanal mit gelesenem ersten Byte; `null`, wenn die Gegenstelle vorher endet oder scheitert. */
        suspend fun peek(inner: SuspendingChannel): PeekedChannel? {
            val one = ByteBuffer.allocate(1)
            val read = inner.read(one)
            return if (read is ChannelResult.Ok && read.value == 1) PeekedChannel(one.get(0), inner) else null
        }
    }
}
