package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.MessageAssembler
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import java.nio.ByteBuffer

/**
 * Liest Nachrichten aus einem [SuspendingChannel] (`TDS-FA-01.a`, `SPEC-007`, `SPEC-046`). Die
 * Zusammensetzung und die Grenzprüfung gehören dem [MessageAssembler]; der Leser ruft ihn und
 * rechnet nichts nach. `limits` ist ein Konstruktorparameter: mit [FramingLimits.preLogin] gilt die Grenze vor
 * dem Login, mit den Standardgrenzen die danach. Ein Wechsel der Grenze an einem laufenden Leser ist
 * nicht vorgesehen (Puffer und Assembler-Zustand gehören ihm); wer vor und nach dem Login liest,
 * muss das lösen; vorgesehener Träger ist der Sitzungsautomat (`slice-handshake-login`).
 *
 * Der Assembler bricht am vollständigen Header des Pakets ab, das die Grenze überschreitet; der
 * Leser liest danach nicht weiter. Gelesen sind dann mindestens die Bytes bis zu diesem Header
 * und höchstens diese plus die Bytes des letzten Lesevorgangs von höchstens `readBufferSize`.
 *
 * Nach dem ersten Fehler oder dem Ende liefert [next] immer wieder dasselbe Ergebnis und liest
 * nicht mehr. Vor einem Fehler vollendete Nachrichten werden zuerst geliefert. Nicht thread-sicher:
 * ein Leser gehört einem Aufrufer.
 */
class MessageReader(
    private val channel: SuspendingChannel,
    limits: FramingLimits = FramingLimits(),
    readBufferSize: Int = DEFAULT_READ_BUFFER,
) {
    private val assembler = MessageAssembler(limits)
    private val buffer = ByteBuffer.allocate(readBufferSize)
    private val ready = ArrayDeque<TdsMessage>()
    private var terminal: ReadOutcome? = null

    init {
        require(readBufferSize > 0) { "readBufferSize muss positiv sein" }
    }

    /** Die nächste Nachricht, das Ende der Gegenstelle oder der Fehler, der den Strom beendet. */
    suspend fun next(): ReadOutcome {
        while (ready.isEmpty() && terminal == null) {
            terminal = fillOnce()
        }
        return ready.removeFirstOrNull()?.let { ReadOutcome.Message(it) } ?: checkNotNull(terminal)
    }

    private suspend fun fillOnce(): ReadOutcome? {
        buffer.clear()
        return when (val read = channel.read(buffer)) {
            is ChannelResult.Failed -> ReadOutcome.Failed(TransportError.Channel(read.error))
            is ChannelResult.Ok -> if (read.value < 0) endOfInput() else accept(read.value)
        }
    }

    private fun accept(count: Int): ReadOutcome? {
        val step = assembler.feed(buffer.array().copyOf(count))
        ready += step.messages
        return step.error?.let { ReadOutcome.Failed(TransportError.Framing(it)) }
    }

    private fun endOfInput(): ReadOutcome {
        val error = assembler.endOfInput().error
        return if (error == null) ReadOutcome.EndOfStream else ReadOutcome.Failed(TransportError.Framing(error))
    }

    private companion object {
        const val DEFAULT_READ_BUFFER = 8192
    }
}
