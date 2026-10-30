package io.github.pt9912.tdswire.transport.channel

import io.github.pt9912.tdswire.testsupport.CountingChannel
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.chunked
import io.github.pt9912.tdswire.testsupport.drain
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.framing.FramingFailure
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.messaging.TransportError
import io.github.pt9912.tdswire.transport.messaging.WriteOutcome
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Tests über `127.0.0.1`: die Gegenstelle ist ein blockierender [Socket], das Testobjekt der Adapter. */
class SocketChannelLoopbackTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(loopback, 0))
    private val opened = mutableListOf<AutoCloseable>()

    private class Link(val peer: Socket, val accepted: AsynchronousSocketChannel)

    private fun connect(): Link {
        val peer = Socket(loopback, (server.localAddress as InetSocketAddress).port)
        peer.tcpNoDelay = true
        val accepted = server.accept().get()
        opened += peer
        opened += accepted
        return Link(peer, accepted)
    }

    @AfterTest
    fun cleanUp() {
        opened.forEach { runCatching { it.close() } }
        server.close()
    }

    private val payloadA = ByteArray(5000) { (it * 5 + 1).toByte() }
    private val payloadB = ByteArray(100) { (it + 9).toByte() }

    /** Von Hand gebaut: SQL_BATCH in fünf Paketen, PRELOGIN in einem, ATTENTION als reines Header-Paket. */
    private val wire = handMessage(0x01, payloadA, 1024) + handMessage(0x12, payloadB, 1024) +
        handMessage(0x06, ByteArray(0), 1024)
    private val expected = listOf(
        TdsMessage(PacketType.SQL_BATCH, payloadA),
        TdsMessage(PacketType.PRELOGIN, payloadB),
        TdsMessage(PacketType.ATTENTION, ByteArray(0)),
    )

    /** Sendet [pieces] auf einem Hintergrund-Thread, jedes Stück mit eigenem Flush. */
    private fun send(peer: Socket, pieces: List<ByteArray>, closeAfter: Boolean = true) = thread {
        runCatching {
            pieces.forEach {
                peer.getOutputStream().write(it)
                peer.getOutputStream().flush()
            }
            if (closeAfter) peer.close()
        }
    }

    /**
     * Die Gegenstelle sendet die handgebaute Folge byteweise (jedes Byte mit eigenem Flush) und
     * in zufälligen Stücken (fester Startwert); der Leser über dem Adapter liefert dieselben drei
     * Nachrichten und das saubere Ende. Ob das Netz die Stücke als getrennte Segmente überträgt,
     * ist nicht Gegenstand der Aussage.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Zerstueckelte Nachrichten kommen ueber den Adapter vollstaendig an`() = blocking {
        val random = Random(SEED)
        val variants = listOf(wire.map { byteArrayOf(it) }) +
            List(RUNS) { chunked(wire, generateSequence { random.nextInt(1, 300) }) }
        for (pieces in variants) {
            val link = connect()
            val sender = send(link.peer, pieces)
            val (messages, outcome) = MessageReader(SocketChannelAdapter(link.accepted), readBufferSize = 128).drain()
            sender.join()
            assertEquals(expected, messages)
            assertEquals(ReadOutcome.EndOfStream, outcome)
        }
    }

    /**
     * Eine Nachricht über der Grenze (200 Pakete zu 1024 Bytes bei Grenze 4096) beendet den Leser
     * mit `MESSAGE_ABOVE_LIMIT`; gelesen wurden höchstens die Bytes bis zum Header des fünften
     * Pakets (4104) plus ein Lesevorgang von 512 Bytes, nicht die 204800 gesendeten.
     *
     * @spec SPEC-046
     */
    @Test
    fun `Ueberlange Nachricht beendet den Leser bevor sie gelesen ist`() = blocking {
        val link = connect()
        val huge = handMessage(0x12, ByteArray(200 * 1016), 1016)
        val sender = send(link.peer, listOf(huge), closeAfter = false)
        val counting = CountingChannel(SocketChannelAdapter(link.accepted))
        val limits = FramingLimits(maxPacketBytes = 1024, maxMessageBytes = 4096)
        val failed = assertIs<ReadOutcome.Failed>(MessageReader(counting, limits, readBufferSize = 512).next())
        assertEquals(
            FramingFailure.MESSAGE_ABOVE_LIMIT,
            assertIs<TransportError.Framing>(failed.error).error.failure,
        )
        assertTrue(counting.bytesRead in 4096 + 8..4096 + 8 + 512, "gelesen: ${counting.bytesRead}")
        link.accepted.close()
        sender.join()
    }

    /**
     * Schließt die Gegenstelle mitten in einer Nachricht, einmal mit FIN und einmal mit RST
     * (`SO_LINGER` 0), ist das Ergebnis ein `Failed` und keine Ausnahme. Geprüft ist nur das
     * `Failed`; nur für FIN ist der Grund (`MESSAGE_TRUNCATED`) festgelegt, beim RST hängt er vom
     * Betriebssystem ab (Kanalfehler oder, falls die Daten zuvor ankamen, `MESSAGE_TRUNCATED`).
     *
     * @spec SPEC-027
     */
    @Test
    fun `Abrupt geschlossener Kanal endet in einem Ergebniswert`() = blocking {
        val half = wire.copyOf(700)
        for (reset in listOf(false, true)) {
            val link = connect()
            if (reset) link.peer.setSoLinger(true, 0)
            send(link.peer, listOf(half)).join()
            val (messages, outcome) = MessageReader(SocketChannelAdapter(link.accepted)).drain()
            assertTrue(messages.isEmpty())
            val failed = assertIs<ReadOutcome.Failed>(outcome, "reset=$reset")
            if (!reset) {
                val framing = assertIs<TransportError.Framing>(failed.error)
                assertEquals(FramingFailure.MESSAGE_TRUNCATED, framing.error.failure)
            }
        }
    }

    /**
     * Der Writer über dem Adapter schreibt dieselben Bytes, die von Hand gebaut sind: eine
     * RPC-Nachricht von 3000 Nutzdatenbytes bei Packet Size 1032 (1024 Nutzdatenbytes je Paket).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Writer schreibt ueber den Adapter die erwarteten Bytes`() = blocking {
        val link = connect()
        val payload = ByteArray(3000) { (it * 11).toByte() }
        val expectedBytes = handMessage(0x03, payload, 1024)
        val received = ByteArray(expectedBytes.size)
        link.peer.soTimeout = PEER_TIMEOUT_MILLIS
        val reading = thread { runCatching { DataInputStream(link.peer.getInputStream()).readFully(received) } }
        val writer = MessageWriter(SocketChannelAdapter(link.accepted), 1032)
        val outcome = writer.write(TdsMessage(PacketType.RPC, payload))
        reading.join()
        assertEquals(WriteOutcome.Written, outcome)
        assertContentEquals(expectedBytes, received)
    }

    /**
     * Rückstau am Adapter: liest die Gegenstelle nichts, schließt der Writer 32 MiB Nutzdaten
     * auch nach 500 ms nicht ab (die Puffer von Betriebssystem und Gegenstelle fassen sie nicht).
     * Der Abbruch der Coroutine schließt den Kanal. Gemessen ist das Suspendieren auf dieser
     * Maschine, keine Obergrenze des Speichers.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Writer suspendiert gegen eine Gegenstelle die nicht liest`() = blocking {
        val link = connect()
        val big = TdsMessage(PacketType.TABULAR_RESULT, ByteArray(32 * 1024 * 1024))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            MessageWriter(SocketChannelAdapter(link.accepted), 65535).write(big)
        }
        delay(500)
        assertTrue(job.isActive, "Writer ist fertig, obwohl niemand liest")
        job.cancel()
        job.join()
        assertFalse(link.accepted.isOpen)
    }

    /**
     * Der Abbruch eines wartenden Lesens schließt den Adapter-Kanal; ein weiteres Lesen endet
     * danach in `CLOSED`.
     */
    @Test
    fun `Abbruch eines wartenden Lesens schliesst den Socket`() = blocking {
        val link = connect()
        val adapter = SocketChannelAdapter(link.accepted)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { adapter.read(ByteBuffer.allocate(8)) }
        assertTrue(job.isActive)
        job.cancel()
        job.join()
        assertFalse(link.accepted.isOpen)
        val afterwards = assertIs<ChannelResult.Failed>(adapter.read(ByteBuffer.allocate(8)))
        assertEquals(ChannelFailure.CLOSED, afterwards.error.failure)
        assertEquals(ChannelErrorCategory.TDS_INTERNAL_ERROR, afterwards.error.category)
    }

    /** Schreibt, bis der Adapter einen Fehler meldet (höchstens [MAX_WRITES] Mal); `null`, wenn keiner kam. */
    private suspend fun writeUntilFailed(adapter: SocketChannelAdapter): ChannelResult.Failed? {
        repeat(MAX_WRITES) {
            val result = adapter.writeFully(ByteBuffer.wrap(ByteArray(WRITE_CHUNK)))
            if (result is ChannelResult.Failed) return result
        }
        return null
    }

    /**
     * Setzt die Gegenstelle den Socket mit RST zurück (`SO_LINGER` 0, Schließen), endet ein
     * späterer Schreibvorgang des Adapters in einem `Failed` mit `IO_FAILURE` und keiner Ausnahme.
     * Das erste Schreiben kann noch vom Betriebssystem angenommen werden; geprüft ist, dass
     * irgendein folgendes scheitert, nicht, welches.
     */
    @Test
    fun `Schreibfehler des Adapters endet in einem Ergebniswert`() = blocking {
        val link = connect()
        link.peer.setSoLinger(true, 0)
        link.peer.close()
        val failed = writeUntilFailed(SocketChannelAdapter(link.accepted))
        assertEquals(ChannelFailure.IO_FAILURE, failed?.error?.failure)
        assertEquals(ChannelErrorCategory.TDS_INTERNAL_ERROR, failed?.error?.category)
    }

    /** Über den Writer wird derselbe Schreibfehler zu `WriteOutcome.Failed` mit einem Kanalfehler. */
    @Test
    fun `Writer meldet den Schreibfehler des Adapters als Ergebniswert`() = blocking {
        val link = connect()
        link.peer.setSoLinger(true, 0)
        link.peer.close()
        val writer = MessageWriter(SocketChannelAdapter(link.accepted), 65535)
        val big = TdsMessage(PacketType.TABULAR_RESULT, ByteArray(WRITE_CHUNK))
        var outcome: WriteOutcome = WriteOutcome.Written
        repeat(MAX_WRITES) { if (outcome == WriteOutcome.Written) outcome = writer.write(big) }
        val failed = assertIs<WriteOutcome.Failed>(outcome)
        assertIs<TransportError.Channel>(failed.error)
    }

    private companion object {
        const val MAX_WRITES = 200
        const val WRITE_CHUNK = 64 * 1024
        const val SEED = 20_261_030
        const val RUNS = 5
        const val PEER_TIMEOUT_MILLIS = 5000
    }
}
