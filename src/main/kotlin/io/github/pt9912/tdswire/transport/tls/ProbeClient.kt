package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLException

/**
 * Die Gegenstelle der Startprobe: eine Client-`SSLEngine` auf rohem TLS über [wire], die nur den
 * Handschlag ausführt (`SPEC-043`). Unabhängig von der Schleife des Servers geschrieben; sie kennt
 * keine TDS-Pakete.
 */
internal class ProbeClient(private val engine: SSLEngine, private val wire: SuspendingChannel) {
    private val network = ByteBuffer.allocate(engine.session.packetBufferSize)
    private val application = ByteBuffer.allocate(engine.session.applicationBufferSize)

    /**
     * `null`, wenn der Handschlag aus Sicht des Clients gelang; sonst der Typname der Ausnahme oder
     * `"Abbruch"`, wenn der Draht endete. Bei einer Ausnahme geht ein Alert hinaus.
     */
    suspend fun handshake(): String? = try {
        engine.beginHandshake()
        if (run()) null else "Abbruch"
    } catch (rejected: SSLException) {
        flushAlert()
        rejected.javaClass.simpleName
    }

    private suspend fun run(): Boolean {
        while (true) {
            val done = when (engine.handshakeStatus) {
                HandshakeStatus.NEED_TASK -> {
                    runTasks()
                    null
                }

                HandshakeStatus.NEED_WRAP -> wrapOnce()

                HandshakeStatus.NEED_UNWRAP, HandshakeStatus.NEED_UNWRAP_AGAIN -> unwrapOnce()

                else -> true
            }
            if (done != null) return done
        }
    }

    private fun runTasks() {
        var task = engine.delegatedTask
        while (task != null) {
            task.run()
            task = engine.delegatedTask
        }
    }

    /** `null`: weiter; sonst das Ende des Handschlags. */
    private suspend fun wrapOnce(): Boolean? {
        val out = ByteBuffer.allocate(engine.session.packetBufferSize)
        val result = engine.wrap(EMPTY, out)
        out.flip()
        val sent = if (out.hasRemaining()) wire.writeFully(out) else ChannelResult.Ok(Unit)
        return when {
            sent is ChannelResult.Failed -> false
            result.handshakeStatus == HandshakeStatus.FINISHED -> true
            result.status == SSLEngineResult.Status.CLOSED -> false
            else -> null
        }
    }

    /** `null`: weiter; sonst das Ende des Handschlags. */
    private suspend fun unwrapOnce(): Boolean? {
        network.flip()
        val result = engine.unwrap(network, application)
        network.compact()
        application.clear()
        return when {
            result.status == SSLEngineResult.Status.CLOSED -> false
            result.handshakeStatus == HandshakeStatus.FINISHED -> true
            result.status == SSLEngineResult.Status.BUFFER_UNDERFLOW -> if (fill()) null else false
            else -> null
        }
    }

    private suspend fun fill(): Boolean {
        val read = wire.read(network)
        return read is ChannelResult.Ok && read.value >= 0
    }

    /** Schreibt den Alert, den die Engine nach einem Fehler bereithält; ein Fehler dabei zählt nicht. */
    private suspend fun flushAlert() {
        var rounds = 0
        while (engine.handshakeStatus == HandshakeStatus.NEED_WRAP && rounds++ < ALERT_ROUNDS) {
            try {
                wrapOnce()
            } catch (ignored: SSLException) {
                return
            }
        }
    }

    private companion object {
        val EMPTY: ByteBuffer = ByteBuffer.allocate(0)
        const val ALERT_ROUNDS = 4
    }
}
