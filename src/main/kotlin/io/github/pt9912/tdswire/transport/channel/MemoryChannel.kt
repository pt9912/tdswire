package io.github.pt9912.tdswire.transport.channel

import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Ein Ende eines Speicher-Kanals ohne Netz: [pair] liefert zwei verbundene Enden, was das eine
 * schreibt, liest das andere. Jede Richtung hält höchstens `capacity` Bytes; ein Schreiber
 * suspendiert, solange der Leser nichts abnimmt. Das macht den Rückstau ohne Socket messbar
 * ([bufferedBytes], [peakBufferedBytes]).
 *
 * Schließen eines Endes beendet die Richtung, die es schreibt: die Gegenstelle liest die
 * gepufferten Bytes und danach -1. Die Einschränkungen von [SuspendingChannel] gelten.
 */
class MemoryChannel private constructor(private val incoming: Pipe, private val outgoing: Pipe) : SuspendingChannel {
    /** Bytes, die dieses Ende geschrieben und die Gegenstelle noch nicht gelesen hat. */
    val bufferedBytes: Int get() = outgoing.buffered()

    /** Der größte Wert von [bufferedBytes] seit der Erzeugung. */
    val peakBufferedBytes: Int get() = outgoing.peak()

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> = closingOnCancel { incoming.read(buffer) }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> =
        closingOnCancel { outgoing.write(buffer) }

    override fun close() {
        outgoing.closeWriter()
        incoming.closeReader()
    }

    private suspend fun <T> closingOnCancel(operation: suspend () -> T): T = try {
        operation()
    } catch (cancelled: CancellationException) {
        close()
        throw cancelled
    }

    companion object {
        /** Zwei verbundene Enden; jede Richtung puffert höchstens [capacity] Bytes. */
        fun pair(capacity: Int): Pair<MemoryChannel, MemoryChannel> {
            require(capacity > 0) { "capacity muss positiv sein" }
            val toSecond = Pipe(capacity)
            val toFirst = Pipe(capacity)
            return MemoryChannel(incoming = toFirst, outgoing = toSecond) to
                MemoryChannel(incoming = toSecond, outgoing = toFirst)
        }
    }

    /**
     * Eine Richtung: ein Ringpuffer fester Größe. Der Zustand liegt hinter `lock`; die Signale
     * wecken den wartenden Leser oder Schreiber, der den Zustand danach neu prüft, ein
     * zwischenzeitlich gesetztes Signal geht also nicht verloren.
     */
    private class Pipe(private val capacity: Int) {
        private val lock = Any()
        private val ring = ByteArray(capacity)
        private var head = 0
        private var size = 0
        private var highWater = 0
        private var writerClosed = false
        private var readerClosed = false
        private val dataSignal = Channel<Unit>(Channel.CONFLATED)
        private val spaceSignal = Channel<Unit>(Channel.CONFLATED)

        fun buffered(): Int = synchronized(lock) { size }

        fun peak(): Int = synchronized(lock) { highWater }

        fun closeWriter() {
            synchronized(lock) { writerClosed = true }
            wakeBoth()
        }

        fun closeReader() {
            synchronized(lock) { readerClosed = true }
            wakeBoth()
        }

        suspend fun read(target: ByteBuffer): ChannelResult<Int> {
            while (true) {
                val outcome = synchronized(lock) { takeLocked(target) }
                if (outcome != null) {
                    spaceSignal.trySend(Unit)
                    return outcome
                }
                dataSignal.receive()
            }
        }

        suspend fun write(source: ByteBuffer): ChannelResult<Unit> {
            while (true) {
                val outcome = synchronized(lock) { putLocked(source) }
                dataSignal.trySend(Unit)
                if (outcome != null) return outcome
                spaceSignal.receive()
            }
        }

        private fun takeLocked(target: ByteBuffer): ChannelResult<Int>? = when {
            readerClosed -> failed(ChannelFailure.CLOSED, "MemoryChannel")

            size > 0 -> {
                val count = minOf(size, target.remaining())
                repeat(count) { target.put(ring[(head + it) % capacity]) }
                head = (head + count) % capacity
                size -= count
                ChannelResult.Ok(count)
            }

            writerClosed -> ChannelResult.Ok(END_OF_STREAM)

            else -> null
        }

        private fun putLocked(source: ByteBuffer): ChannelResult<Unit>? = when {
            writerClosed -> failed(ChannelFailure.CLOSED, "MemoryChannel")

            readerClosed -> failed(ChannelFailure.IO_FAILURE, "BrokenPipe")

            else -> {
                val count = minOf(capacity - size, source.remaining())
                repeat(count) { ring[(head + size + it) % capacity] = source.get() }
                size += count
                highWater = maxOf(highWater, size)
                if (source.hasRemaining()) null else ChannelResult.Ok(Unit)
            }
        }

        private fun wakeBoth() {
            dataSignal.trySend(Unit)
            spaceSignal.trySend(Unit)
        }

        private fun failed(failure: ChannelFailure, detail: String) =
            ChannelResult.Failed(ChannelError(failure, detail))

        private companion object {
            const val END_OF_STREAM = -1
        }
    }
}
