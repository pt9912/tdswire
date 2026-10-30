package io.github.pt9912.tdswire.transport.channel

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume

/**
 * [SuspendingChannel] über einen verbundenen [AsynchronousSocketChannel] (`ADR-0003`).
 *
 * Lese- und Schreibfehler des Kanals werden zu [ChannelResult.Failed]; Programmierfehler
 * (gleichzeitiger zweiter Lese- oder Schreibvorgang, nicht verbundener Kanal, schreibgeschützter
 * Puffer) bleiben Ausnahmen: das JDK meldet sie als `ReadPendingException`,
 * `WritePendingException`, `NotYetConnectedException` bzw. `IllegalArgumentException`. Der Adapter
 * puffert nichts: [writeFully] kehrt zurück, wenn das Betriebssystem alle Bytes angenommen hat.
 */
class SocketChannelAdapter(private val channel: AsynchronousSocketChannel) : SuspendingChannel {
    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> = operation { handler ->
        channel.read(buffer, null, handler)
    }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> {
        while (buffer.hasRemaining()) {
            val step = operation { handler -> channel.write(buffer, null, handler) }
            if (step is ChannelResult.Failed) return step
        }
        return ChannelResult.Ok(Unit)
    }

    /**
     * Schließt den Socket. Eine `IOException` beim Schließen wird verschluckt: der Aufrufer will den
     * Kanal los sein und kann den Fehler nicht beheben; andere Ausnahmen treten aus.
     */
    override fun close() {
        try {
            channel.close()
        } catch (ignored: IOException) {
            // Verschluckt nach Festlegung der KDoc von close().
        }
    }

    private suspend fun operation(start: (CompletionHandler<Int, Nothing?>) -> Unit): ChannelResult<Int> =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { close() }
            start(
                object : CompletionHandler<Int, Nothing?> {
                    override fun completed(result: Int, attachment: Nothing?) {
                        continuation.resume(ChannelResult.Ok(result))
                    }

                    override fun failed(exc: Throwable, attachment: Nothing?) {
                        continuation.resume(ChannelResult.Failed(describe(exc)))
                    }
                },
            )
        }

    private fun describe(failure: Throwable): ChannelError {
        val closed = failure is IOException && !channel.isOpen
        val reason = if (closed) ChannelFailure.CLOSED else ChannelFailure.IO_FAILURE
        return ChannelError(reason, failure.javaClass.simpleName)
    }
}
