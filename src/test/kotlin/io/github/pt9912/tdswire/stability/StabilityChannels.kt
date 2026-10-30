package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.transport.channel.ChannelError
import io.github.pt9912.tdswire.transport.channel.ChannelFailure
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import java.nio.ByteBuffer

/** Wie ein [ScriptedChannel] endet, wenn seine Bytes verbraucht sind. */
enum class ScriptEnd { CLEAN_EOF, IO_FAILURE }

/**
 * Eine Gegenstelle mit festem Drehbuch: liefert [chunks] der Reihe nach (ein Lesevorgang nimmt höchstens
 * den Rest des aktuellen Stücks), danach das Ende nach [end]. Schreiben wird angenommen und gezählt.
 * Die Stücke sind die Zerstückelung des Stroms; leere Stücke entfallen.
 */
class ScriptedChannel(chunks: List<ByteArray>, private val end: ScriptEnd = ScriptEnd.CLEAN_EOF) : SuspendingChannel {
    private val pending = ArrayDeque(chunks.filter { it.isNotEmpty() })
    private var offset = 0

    var bytesWritten = 0L
        private set

    var closed = false
        private set

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
        val chunk = pending.firstOrNull() ?: return ended()
        val take = minOf(buffer.remaining(), chunk.size - offset)
        buffer.put(chunk, offset, take)
        offset += take
        if (offset == chunk.size) {
            pending.removeFirst()
            offset = 0
        }
        return ChannelResult.Ok(take)
    }

    private fun ended(): ChannelResult<Int> = when (end) {
        ScriptEnd.CLEAN_EOF -> ChannelResult.Ok(-1)
        ScriptEnd.IO_FAILURE -> ChannelResult.Failed(ChannelError(ChannelFailure.IO_FAILURE, "Skript"))
    }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> {
        bytesWritten += buffer.remaining()
        buffer.position(buffer.limit())
        return ChannelResult.Ok(Unit)
    }

    override fun close() {
        closed = true
    }
}
