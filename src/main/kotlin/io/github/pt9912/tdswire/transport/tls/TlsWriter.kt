package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

/**
 * Die Schreibseite der Datenphase: verschlüsselt Nutzdaten, beantwortet Handshake-Daten der Engine und
 * sendet `close_notify`. Alle Schreibvorgänge auf den inneren Kanal laufen unter einer Sperre; ein
 * Record gehört ganz dem, der sie hält, sonst verschränken sich die Records auf dem Strom.
 *
 * Festlegung (Antworten der Leseseite): Die Leseseite wartet nie auf die Sperre. [answer] merkt die
 * Antwort vor und nimmt die Sperre nur, wenn sie frei ist; hält ein Schreiber sie, schreibt er die
 * Antwort selbst, sobald er sie loslässt (`settle`). So hält ein Schreiber, der auf einen vollen
 * Kanal wartet, den Lesepfad nicht auf; die Antwort geht dann mit dem Ende dieses Schreibvorgangs
 * hinaus, nach seinen Records. Ist die Sperre frei, schreibt die Leseseite die Antwort in ihrem
 * eigenen Aufruf und wartet dabei auf den inneren Kanal, wenn er voll ist. Geprüft ist beides
 * gegen eine JDK-Gegenstelle; ein Wettlauf zwischen Vormerken und Loslassen ist nicht belegt.
 */
internal class TlsWriter(private val io: TlsRecordIo) {
    private val lock = Mutex()
    private val nothing = ByteBuffer.allocate(0)
    private val answerDue = AtomicBoolean(false)

    suspend fun write(buffer: ByteBuffer): ChannelResult<Unit> {
        val written = lock.withLock { writeLocked(buffer) }
        return if (written is ChannelResult.Ok) settle() else written
    }

    /**
     * Sorgt dafür, dass hinausgeht, was die Engine nach einem gelesenen Record an Handshake-Daten oder
     * `close_notify` bereithält. Wartet nicht auf die Sperre; die Antwort schreibt, wer sie bekommt.
     */
    suspend fun answer(): ChannelResult<Unit> {
        if (!io.engine.needsWrap()) return ChannelResult.Ok(Unit)
        answerDue.set(true)
        return settle()
    }

    /** Schreibt die vorgemerkte Antwort, solange sie vorgemerkt ist und die Sperre frei ist. */
    private suspend fun settle(): ChannelResult<Unit> {
        var outcome: ChannelResult<Unit> = ChannelResult.Ok(Unit)
        while (outcome is ChannelResult.Ok && answerDue.get() && lock.tryLock()) {
            try {
                answerDue.set(false)
                outcome = drain()
            } finally {
                lock.unlock()
            }
        }
        return outcome
    }

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
