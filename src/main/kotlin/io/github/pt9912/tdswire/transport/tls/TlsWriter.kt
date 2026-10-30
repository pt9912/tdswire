package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

/**
 * Die Schreibseite der Datenphase: verschlüsselt Nutzdaten, beantwortet Handshake-Daten der Engine und
 * sendet `close_notify`. Alle Schreibvorgänge laufen unter einer Sperre, weil die Leseseite Antworten
 * (etwa auf `close_notify` der Gegenstelle) über denselben Ausgabepuffer sendet; die Sperre wird
 * nicht über ein Warten auf die Gegenstelle hinaus gehalten, nur über das Schreiben auf den Kanal.
 */
internal class TlsWriter(private val io: TlsRecordIo) {
    private val lock = Mutex()
    private val nothing = ByteBuffer.allocate(0)

    suspend fun write(buffer: ByteBuffer): ChannelResult<Unit> = lock.withLock { writeLocked(buffer) }

    /** Schreibt, was die Engine an Handshake-Daten oder `close_notify` bereithält. */
    suspend fun answer(): ChannelResult<Unit> = lock.withLock { drain() }

    /** Schließt den Ausgang der Engine und schreibt `close_notify`. */
    suspend fun closeOutbound(): ChannelResult<Unit> = lock.withLock {
        io.engine.closeOutbound()
        drain()
    }

    private suspend fun writeLocked(buffer: ByteBuffer): ChannelResult<Unit> {
        var failure: TlsError? = null
        while (buffer.hasRemaining() && failure == null) {
            val before = buffer.remaining()
            failure = when (val wrapped = io.wrap(buffer, TlsFailure.RECORD_INVALID)) {
                is TlsResult.Failed -> wrapped.error
                is TlsResult.Ok -> afterWrap(wrapped.value, consumed = before - buffer.remaining())
            }
        }
        return failure?.let { ChannelResult.Failed(it.toChannelError()) } ?: ChannelResult.Ok(Unit)
    }

    private suspend fun afterWrap(result: SSLEngineResult, consumed: Int): TlsError? = when {
        result.status == SSLEngineResult.Status.CLOSED ->
            TlsError(TlsFailure.CLOSED, "Engine ausgangsseitig geschlossen")

        result.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK -> {
            io.runTasks()
            null
        }

        consumed == 0 && result.bytesProduced() == 0 -> TlsError(TlsFailure.RECORD_INVALID, "Engine nimmt nichts an")

        else -> null
    }

    private suspend fun drain(): ChannelResult<Unit> {
        var rounds = 0
        var failure: TlsError? = null
        while (rounds < MAX_DRAIN_ROUNDS && failure == null && io.engine.needsWrap()) {
            rounds++
            failure = when (val wrapped = io.wrap(nothing, TlsFailure.RECORD_INVALID)) {
                is TlsResult.Failed -> wrapped.error
                is TlsResult.Ok -> null
            }
        }
        return failure?.let { ChannelResult.Failed(it.toChannelError()) } ?: ChannelResult.Ok(Unit)
    }

    private fun SSLEngine.needsWrap(): Boolean = handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP

    private companion object {
        const val MAX_DRAIN_ROUNDS = 8
    }
}
