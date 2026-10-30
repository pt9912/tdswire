package io.github.pt9912.tdswire.transport.tls

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException

/**
 * Die Einzelschritte der Schleife auf einer [SSLEngine]: `unwrap` aus dem Puffer der empfangenen
 * TLS-Bytes, `wrap` auf den [NetOutbound], Nachfüllen aus dem [NetInbound] und die ausgelagerten
 * Tasks der Engine. Beide Netzpuffer haben die Größe, die die Sitzung der Engine nennt; kein Puffer
 * wächst darüber hinaus, ein Record, der nicht hineinpasst, ist ein Fehler. Ein Aufruf von
 * [unwrap] und [pull] gehört dem Leser, [wrap] dem Schreiber.
 *
 * Ausnahmen der Engine (`SSLException`) werden zu [TlsResult.Failed]; ihr Text wird nicht übernommen,
 * er kann Namen aus dem Zertifikat tragen.
 */
internal class TlsRecordIo(
    val engine: SSLEngine,
    val inbound: NetInbound,
    val outbound: NetOutbound,
    private val tasks: CoroutineDispatcher,
) {
    private var netIn = ByteBuffer.allocate(engine.session.packetBufferSize)
    private var netOut = ByteBuffer.allocate(engine.session.packetBufferSize)

    /** Größe des Puffers für entschlüsselte Daten, die die Sitzung der Engine nennt. */
    val applicationBufferSize: Int get() = engine.session.applicationBufferSize

    /** Passt die Netzpuffer an die ausgehandelte Sitzung an; sie wachsen nur bis zu deren Größe. */
    fun adoptSession() {
        netIn = grown(netIn, engine.session.packetBufferSize)
        netOut = grown(netOut, engine.session.packetBufferSize)
    }

    /** Entschlüsselt die Bytes in `netIn` nach [target] (Schreibmodus); [failure] benennt einen Engine-Fehler. */
    fun unwrap(target: ByteBuffer, failure: TlsFailure): TlsResult<SSLEngineResult> {
        netIn.flip()
        val outcome: TlsResult<SSLEngineResult> = try {
            TlsResult.Ok(engine.unwrap(netIn, target))
        } catch (broken: SSLException) {
            failed(failure, broken.javaClass.simpleName)
        } finally {
            netIn.compact()
        }
        return outcome
    }

    /** Holt Bytes der Gegenstelle nach `netIn`; ein voller Puffer wächst höchstens auf die Sitzungsgröße. */
    suspend fun pull(): Pulled {
        if (!netIn.hasRemaining()) {
            val larger = grown(netIn, engine.session.packetBufferSize)
            if (larger === netIn) return Pulled.Violation("TLS-Record groesser als der Puffer")
            netIn = larger
        }
        return inbound.fill(netIn)
    }

    /** Verschlüsselt [source] und reicht die Bytes an den [NetOutbound]; Ergebnis ist das der Engine. */
    suspend fun wrap(source: ByteBuffer, failure: TlsFailure): TlsResult<SSLEngineResult> {
        val first = attemptWrap(source, failure)
        val overflowed = first is TlsResult.Ok && first.value.status == SSLEngineResult.Status.BUFFER_OVERFLOW
        val larger = if (overflowed) grown(netOut, engine.session.packetBufferSize) else netOut
        return when {
            !overflowed -> stage(first)

            larger === netOut -> failed(failure, "Ausgabepuffer zu klein")

            else -> {
                netOut = larger
                stage(attemptWrap(source, failure))
            }
        }
    }

    /** Führt die ausgelagerten Tasks der Engine auf dem Dispatcher der Tasks aus. */
    suspend fun runTasks() {
        withContext(tasks) {
            var task = engine.delegatedTask
            while (task != null) {
                task.run()
                task = engine.delegatedTask
            }
        }
    }

    private fun attemptWrap(source: ByteBuffer, failure: TlsFailure): TlsResult<SSLEngineResult> {
        netOut.clear()
        return try {
            TlsResult.Ok(engine.wrap(source, netOut))
        } catch (broken: SSLException) {
            failed(failure, broken.javaClass.simpleName)
        }
    }

    private suspend fun stage(attempt: TlsResult<SSLEngineResult>): TlsResult<SSLEngineResult> {
        if (attempt is TlsResult.Failed) return attempt
        netOut.flip()
        val bytes = ByteArray(netOut.remaining())
        netOut.get(bytes)
        val staged = if (bytes.isEmpty()) TlsResult.Ok(Unit) else outbound.stage(bytes)
        return if (staged is TlsResult.Failed) staged else attempt
    }

    /** [buffer], wenn [size] die Kapazität nicht übersteigt; sonst eine Kopie der Größe [size]. */
    private fun grown(buffer: ByteBuffer, size: Int): ByteBuffer {
        if (size <= buffer.capacity()) return buffer
        val larger = ByteBuffer.allocate(size)
        buffer.flip()
        larger.put(buffer)
        return larger
    }

    private companion object {
        const val WRAP_ATTEMPTS = 2
    }
}
