package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException

/**
 * Die Leseseite der Datenphase: entschlüsselt Records in einen Puffer fester Größe und gibt davon
 * an den Aufrufer ab. Der Puffer ist die Größe, die die Sitzung der Engine für Anwendungsdaten nennt,
 * und wächst nie; ein Record, der nicht hineinpasst, beendet den Strom mit einem Fehler.
 *
 * Nach dem ersten Fehler oder dem Ende liefert [read] immer wieder dasselbe Ergebnis und liest nicht
 * mehr. Handshake-Daten der Datenphase (Antworten der Engine, etwa auf `close_notify`) gehen über
 * [writer]. Nicht thread-sicher: ein Leser.
 */
internal class TlsReader(private val io: TlsRecordIo, private val writer: TlsWriter) {
    private val plain: ByteBuffer = ByteBuffer.allocate(io.applicationBufferSize).apply { limit(0) }
    private var end: ChannelResult<Int>? = null

    /** Kapazität des Puffers für entschlüsselte Daten. */
    val capacity: Int get() = plain.capacity()

    suspend fun read(target: ByteBuffer): ChannelResult<Int> {
        if (!plain.hasRemaining() && end == null) end = fill()
        return if (plain.hasRemaining()) ChannelResult.Ok(drain(target)) else checkNotNull(end)
    }

    private fun drain(target: ByteBuffer): Int {
        val count = minOf(plain.remaining(), target.remaining())
        val originalLimit = plain.limit()
        plain.limit(plain.position() + count)
        target.put(plain)
        plain.limit(originalLimit)
        return count
    }

    /** `null`, wenn `plain` Bytes hält; sonst das Ergebnis, das den Strom beendet. */
    private suspend fun fill(): ChannelResult<Int>? {
        var step: ReadStep = ReadStep.More
        while (step === ReadStep.More) step = advance()
        return (step as? ReadStep.End)?.result
    }

    private suspend fun advance(): ReadStep {
        plain.clear()
        val unwrapped = io.unwrap(plain, TlsFailure.RECORD_INVALID)
        plain.flip()
        return when (unwrapped) {
            is TlsResult.Failed -> ReadStep.End(ChannelResult.Failed(unwrapped.error.toChannelError()))
            is TlsResult.Ok -> afterUnwrap(unwrapped.value)
        }
    }

    private suspend fun afterUnwrap(result: SSLEngineResult): ReadStep = when (result.status) {
        SSLEngineResult.Status.BUFFER_UNDERFLOW -> pulled()

        SSLEngineResult.Status.CLOSED -> {
            writer.answer()
            ReadStep.End(ChannelResult.Ok(END_OF_STREAM))
        }

        SSLEngineResult.Status.BUFFER_OVERFLOW -> endWith(TlsFailure.RECORD_INVALID, "Puffer zu klein")

        else -> afterRecord(result)
    }

    private suspend fun afterRecord(result: SSLEngineResult): ReadStep {
        if (result.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) io.runTasks()
        val answered = writer.answer()
        return when {
            answered is ChannelResult.Failed -> ReadStep.End(answered)
            plain.hasRemaining() -> ReadStep.Data
            else -> ReadStep.More
        }
    }

    private suspend fun pulled(): ReadStep = when (val more = io.pull()) {
        is Pulled.Data -> ReadStep.More
        is Pulled.EndOfStream -> endOfStream()
        is Pulled.ChannelFailed -> ReadStep.End(ChannelResult.Failed(more.error))
        is Pulled.Violation -> endWith(TlsFailure.PROTOCOL_VIOLATION, more.detail)
    }

    private fun endOfStream(): ReadStep = try {
        io.engine.closeInbound()
        ReadStep.End(ChannelResult.Ok(END_OF_STREAM))
    } catch (truncated: SSLException) {
        endWith(TlsFailure.CONNECTION_ABORTED, "close_notify fehlt: ${truncated.javaClass.simpleName}")
    }

    private fun endWith(failure: TlsFailure, detail: String): ReadStep =
        ReadStep.End(ChannelResult.Failed(TlsError(failure, detail).toChannelError()))

    private sealed interface ReadStep {
        data object More : ReadStep

        data object Data : ReadStep

        data class End(val result: ChannelResult<Int>) : ReadStep
    }

    private companion object {
        const val END_OF_STREAM = -1
    }
}
