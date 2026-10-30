package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.JdkTlsPeer
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.TlsSetup
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.channel
import io.github.pt9912.tdswire.testsupport.peerFor
import io.github.pt9912.tdswire.testsupport.runHandshake
import io.github.pt9912.tdswire.transport.channel.ChannelFailure
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Datenphase unter Last und beim Schließen: ein blockierter Schreiber, Abbruch einer Coroutine,
 * geordnetes Schließen, Schreiben nach dem Schließen und der Weg von [TlsChannel.accept] bei unerwarteten
 * Ausnahmen. Gegenstelle ist die JDK-`SSLEngine` im selben Prozess, nicht der Referenzclient.
 */
class TlsDataPhaseTest {
    private val tls13 = listOf("TLSv1.3")
    private val bothPaths = listOf(TlsPath.FROM_FIRST_BYTE, TlsPath.ENCAPSULATED_IN_TDS)

    private suspend fun JdkTlsPeer.readExactly(size: Int): ByteArray {
        val received = ByteBuffer.allocate(size)
        while (received.hasRemaining()) {
            val read = read(received)
            assertTrue(read is ChannelResult.Ok && read.value > 0, "Lesen der Gegenstelle: $read")
        }
        return received.array()
    }

    private suspend fun TlsChannel.readExactly(size: Int): ByteArray {
        val received = ByteBuffer.allocate(size)
        while (received.hasRemaining()) {
            val read = read(received)
            assertTrue(read is ChannelResult.Ok && read.value > 0, "Lesen des Servers: $read")
        }
        return received.array()
    }

    /**
     * Ein Schreiber, der auf einen vollen inneren Kanal wartet (die Gegenstelle liest nichts), hält den
     * Lesepfad nicht auf: Was die Gegenstelle währenddessen sendet, liefert `read`, auch nach einem
     * KeyUpdate der Gegenstelle (TLS 1.3), dessen Antwort der Schreiber dann nachholt. Danach liest die
     * Gegenstelle alles, was der Schreiber geschrieben hat, unversehrt; die Records verschränken sich nicht.
     *
     * @spec SPEC-028
     */
    @Test
    fun `a blocked writer does not hold up the reader`() = blocking {
        bothPaths.forEach { path ->
            val capacity = 1 shl 14
            val setup = TlsSetup(path, tls13)
            val (serverEnd, peerEnd) = MemoryChannel.pair(capacity)
            val peer = peerFor(setup, peerEnd)
            coroutineScope {
                val accepted = async {
                    TlsChannel.accept(serverEnd, TestKeys.serverContext(), path, TlsParameters(tls13, setup.suites))
                }
                assertEquals(null, peer.handshake())
                val server = (accepted.await() as TlsResult.Ok).value
                val outgoing = Random(7).nextBytes(1 shl 20)
                val writing = async { server.writeFully(ByteBuffer.wrap(outgoing)) }
                withTimeout(WAIT_MILLIS) { while (serverEnd.bufferedBytes < capacity) delay(POLL_MILLIS) }
                peer.requestKeyUpdate()
                val incoming = Random(8).nextBytes(300)
                incoming.toList().chunked(100).forEach { peer.writeFully(ByteBuffer.wrap(it.toByteArray())) }
                assertContentEquals(incoming, withTimeout(WAIT_MILLIS) { server.readExactly(incoming.size) })
                assertTrue(writing.isActive, "der Schreiber wartet weiter auf die Gegenstelle")
                assertContentEquals(outgoing, withTimeout(WAIT_MILLIS) { peer.readExactly(outgoing.size) })
                assertEquals(ChannelResult.Ok(Unit), writing.await())
            }
        }
    }

    /**
     * Bricht die Coroutine ab, die in `read` wartet, ist der innere Kanal danach geschlossen; ebenso, wenn
     * sie in `writeFully` auf einen vollen Kanal wartet.
     *
     * @spec SPEC-028
     */
    @Test
    fun `cancelling a read or a write closes the inner channel`() = blocking {
        coroutineScope {
            val reading = runHandshake(TlsSetup(TlsPath.FROM_FIRST_BYTE))
            val readingServer = reading.channel()
            val read = launch { readingServer.read(ByteBuffer.allocate(10)) }
            delay(SETTLE_MILLIS)
            read.cancelAndJoin()
            assertTrue(reading.serverWire.closed, "Abbruch in read schließt den inneren Kanal")

            val writing = runHandshake(TlsSetup(TlsPath.FROM_FIRST_BYTE))
            val writingServer = writing.channel()
            val write = launch { writingServer.writeFully(ByteBuffer.wrap(ByteArray(1 shl 20))) }
            delay(SETTLE_MILLIS)
            write.cancelAndJoin()
            assertTrue(writing.serverWire.closed, "Abbruch in writeFully schließt den inneren Kanal")
        }
    }

    /**
     * `closeGracefully` sendet `close_notify` und schließt den inneren Kanal; ein Schreiben danach ist
     * ein `Failed` mit dem Grund `CLOSED`, keine Ausnahme und kein Byte auf dem Draht. Nach `close()`
     * ist das Schreiben ebenfalls ein `Failed`.
     *
     * @spec SPEC-028
     */
    @Test
    fun `closing gracefully closes the inner channel and later writes fail`() = blocking {
        bothPaths.forEach { path ->
            val graceful = runHandshake(TlsSetup(path))
            val server = graceful.channel()
            assertEquals(ChannelResult.Ok(Unit), server.closeGracefully())
            assertTrue(graceful.serverWire.closed, "closeGracefully schließt den inneren Kanal")
            val before = graceful.serverWire.written.size
            val late = server.writeFully(ByteBuffer.wrap(ByteArray(10)))
            assertIs<ChannelResult.Failed>(late)
            assertEquals(ChannelFailure.CLOSED, late.error.failure)
            assertEquals(before, graceful.serverWire.written.size)

            val plain = runHandshake(TlsSetup(path))
            val other = plain.channel()
            other.close()
            assertIs<ChannelResult.Failed>(other.writeFully(ByteBuffer.wrap(ByteArray(10))))
        }
    }

    /**
     * Sendet die Gegenstelle `close_notify`, liest der Server das Ende (-1); `closeGracefully` schickt dann
     * das eigene `close_notify`, und die Gegenstelle liest danach das Ende und keinen Fehler. Beobachtet ist
     * nicht, dass der Server von selbst antwortet: die JDK-Engine hält ohne `jdk.tls.acknowledgeCloseNotify`
     * keine Antwort bereit, die Schleife schreibt also nichts, bevor der Aufrufer schließt.
     *
     * @spec SPEC-028
     */
    @Test
    fun `close_notify of the peer ends the read and closing afterwards answers it`() = blocking {
        bothPaths.forEach { path ->
            val outcome = runHandshake(TlsSetup(path))
            val server = outcome.channel()
            outcome.peer.sendCloseNotify()
            assertEquals(ChannelResult.Ok(-1), server.read(ByteBuffer.allocate(10)))
            assertEquals(ChannelResult.Ok(Unit), server.closeGracefully())
            assertEquals(ChannelResult.Ok(-1), withTimeout(WAIT_MILLIS) { outcome.peer.read(ByteBuffer.allocate(10)) })
        }
    }

    /** Ein Kanal, dessen `read` mit [failure] endet oder, ohne [failure], nie; er merkt sich das Schließen. */
    private class FaultyChannel(private val failure: RuntimeException?) : SuspendingChannel {
        val closed = CompletableDeferred<Unit>()

        override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
            if (failure != null) throw failure
            awaitCancellation()
        }

        override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> = ChannelResult.Ok(Unit)

        override fun close() {
            closed.complete(Unit)
        }
    }

    /**
     * Endet `accept` mit einer unerwarteten Ausnahme (hier der Kanal, der beim Lesen wirft, und eine Suite,
     * die die JVM nicht kennt), ist der innere Kanal geschlossen und die Ausnahme läuft weiter.
     *
     * @spec SPEC-028
     */
    @Test
    fun `accept closes the channel on an unexpected exception`() = blocking {
        bothPaths.forEach { path ->
            val throwing = FaultyChannel(IllegalStateException("kaputt"))
            assertFailsWith<IllegalStateException> { TlsChannel.accept(throwing, TestKeys.serverContext(), path) }
            assertTrue(throwing.closed.isCompleted, "Kanal nach Ausnahme des Kanals ($path)")

            val unknown = FaultyChannel(null)
            val parameters = TlsParameters(listOf("TLSv1.2"), listOf("TLS_KEINE_SUITE"))
            assertFailsWith<IllegalArgumentException> {
                TlsChannel.accept(unknown, TestKeys.serverContext(), path, parameters)
            }
            assertTrue(unknown.closed.isCompleted, "Kanal nach unbekannter Suite ($path)")
        }
    }

    /**
     * Ein Abbruch während `accept` läuft als Abbruch weiter (die Coroutine ist abgebrochen, nicht
     * mit einem Wert beendet) und schließt den inneren Kanal.
     *
     * @spec SPEC-028
     */
    @Test
    fun `accept cancelled during the handshake closes the channel and stays cancelled`() = blocking {
        coroutineScope {
            val waiting = FaultyChannel(null)
            val accepting = async { TlsChannel.accept(waiting, TestKeys.serverContext(), TlsPath.FROM_FIRST_BYTE) }
            delay(SETTLE_MILLIS)
            accepting.cancelAndJoin()
            assertTrue(accepting.isCancelled)
            assertTrue(waiting.closed.isCompleted)
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
        const val SETTLE_MILLIS = 100L
    }
}
