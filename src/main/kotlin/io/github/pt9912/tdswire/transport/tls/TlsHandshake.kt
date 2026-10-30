package io.github.pt9912.tdswire.transport.tls

import java.nio.ByteBuffer
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLException

/**
 * Treibt den Handshake einer [TlsRecordIo] bis `FINISHED`: `NEED_WRAP`, `NEED_UNWRAP` und
 * `NEED_TASK` laufen als Zustandsschleife über den Status, den die Engine nach jedem Schritt meldet.
 * Vor jedem Warten auf die Gegenstelle schreibt die Schleife die gesammelte Sendefolge ([NetOutbound.flush]);
 * mit `FINISHED` ist auch die letzte Folge geschrieben.
 *
 * Anwendungsdaten der Gegenstelle im Handshake beenden ihn als Protokollverstoß; dieser Zweig ist
 * ungetestet (eine JDK-Gegenstelle sendet vor `FINISHED` keine).
 */
internal class TlsHandshake(private val io: TlsRecordIo) {
    private val scratch = ByteBuffer.allocate(io.applicationBufferSize)
    private val nothing = ByteBuffer.allocate(0)

    suspend fun run(): TlsResult<Unit> {
        val begun = begin()
        return if (begun is TlsResult.Failed) begun else loop()
    }

    private fun begin(): TlsResult<Unit> = try {
        io.engine.beginHandshake()
        TlsResult.Ok(Unit)
    } catch (broken: SSLException) {
        failed(TlsFailure.HANDSHAKE_FAILED, broken.javaClass.simpleName)
    }

    private suspend fun loop(): TlsResult<Unit> {
        var status = io.engine.handshakeStatus
        while (status != HandshakeStatus.FINISHED) {
            when (val next = step(status)) {
                is TlsResult.Failed -> return next
                is TlsResult.Ok -> status = next.value
            }
        }
        return io.outbound.flush()
    }

    private suspend fun step(status: HandshakeStatus): TlsResult<HandshakeStatus> = when (status) {
        HandshakeStatus.NEED_TASK -> {
            io.runTasks()
            TlsResult.Ok(io.engine.handshakeStatus)
        }

        HandshakeStatus.NEED_WRAP -> wrapStep()

        HandshakeStatus.NEED_UNWRAP -> unwrapStep()

        else -> failed(TlsFailure.HANDSHAKE_FAILED, "Status $status")
    }

    private suspend fun wrapStep(): TlsResult<HandshakeStatus> = when (val wrapped = io.wrap(nothing, FAILURE)) {
        is TlsResult.Failed -> wrapped
        is TlsResult.Ok -> afterEngine(wrapped.value)
    }

    private suspend fun unwrapStep(): TlsResult<HandshakeStatus> {
        val flushed = io.outbound.flush()
        if (flushed is TlsResult.Failed) return flushed
        scratch.clear()
        return when (val unwrapped = io.unwrap(scratch, FAILURE)) {
            is TlsResult.Failed -> unwrapped
            is TlsResult.Ok -> afterUnwrap(unwrapped.value)
        }
    }

    private suspend fun afterUnwrap(result: SSLEngineResult): TlsResult<HandshakeStatus> = when {
        result.bytesProduced() > 0 -> failed(TlsFailure.PROTOCOL_VIOLATION, "Anwendungsdaten im Handshake")
        result.status == SSLEngineResult.Status.BUFFER_UNDERFLOW -> pulled()
        else -> afterEngine(result)
    }

    private suspend fun pulled(): TlsResult<HandshakeStatus> = when (val more = io.pull()) {
        is Pulled.Data -> TlsResult.Ok(HandshakeStatus.NEED_UNWRAP)
        is Pulled.EndOfStream -> failed(TlsFailure.CONNECTION_ABORTED, "Strom endet im Handshake")
        is Pulled.ChannelFailed -> failed(TlsFailure.CONNECTION_ABORTED, "Kanal ${more.error.failure}")
        is Pulled.Violation -> failed(TlsFailure.PROTOCOL_VIOLATION, more.detail)
    }

    private fun afterEngine(result: SSLEngineResult): TlsResult<HandshakeStatus> = when (result.status) {
        SSLEngineResult.Status.CLOSED -> failed(TlsFailure.CONNECTION_ABORTED, "close_notify im Handshake")
        SSLEngineResult.Status.BUFFER_OVERFLOW -> failed(FAILURE, "Puffer zu klein")
        else -> TlsResult.Ok(result.handshakeStatus)
    }

    private companion object {
        val FAILURE = TlsFailure.HANDSHAKE_FAILED
    }
}
