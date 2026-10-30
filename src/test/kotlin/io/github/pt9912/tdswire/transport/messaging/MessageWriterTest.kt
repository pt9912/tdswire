package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.drain
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.channel.ChannelFailure
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.framing.FramingFailure
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageWriterTest {
    private val payload = ByteArray(2000) { (it * 3 + 1).toByte() }

    private suspend fun MemoryChannel.takeAll(count: Int): ByteArray {
        val all = ByteBuffer.allocate(count)
        while (all.hasRemaining()) {
            val chunk = ByteBuffer.allocate(minOf(100, all.remaining()))
            read(chunk)
            all.put(chunk.array(), 0, chunk.position())
        }
        return all.array()
    }

    /**
     * Orakel: eine RPC-Nachricht von 2000 Nutzdatenbytes mit Packet Size 512 liegt als vier Pakete
     * auf dem Kanal (504 Nutzdatenbytes je Paket, Rest 488, Status 0 und im letzten 1, IDs 1 bis 4),
     * von Hand gebaut.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Nachricht wird in Pakete der Packet Size zerlegt`() = blocking {
        val (sender, receiver) = MemoryChannel.pair(capacity = 256)
        val expected = handMessage(0x03, payload, 504)
        assertEquals(2000 + 4 * 8, expected.size)
        val writer = launch { MessageWriter(sender, packetSize = 512).write(TdsMessage(PacketType.RPC, payload)) }
        assertContentEquals(expected, receiver.takeAll(expected.size))
        writer.join()
    }

    /**
     * Was der Writer schreibt, liest der Leser als dieselbe Nachricht (Rundlauf über einen
     * Speicher-Kanal), auch bei der kleinsten Packet Size 9 und bei leerer Nachricht.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Geschriebene Nachrichten liest der Leser gleich`() = blocking {
        val messages = listOf(
            TdsMessage(PacketType.TABULAR_RESULT, payload),
            TdsMessage(PacketType.ATTENTION, ByteArray(0)),
            TdsMessage(PacketType.SQL_BATCH, ByteArray(1) { 7 }),
        )
        for (packetSize in listOf(9, 512, 65535)) {
            val (sender, receiver) = MemoryChannel.pair(capacity = 100)
            val writer = launch {
                val out = MessageWriter(sender, packetSize)
                messages.forEach { assertEquals(WriteOutcome.Written, out.write(it)) }
                sender.close()
            }
            val (read, outcome) = MessageReader(receiver, readBufferSize = 33).drain()
            writer.join()
            assertEquals(messages, read, "packetSize $packetSize")
            assertEquals(ReadOutcome.EndOfStream, outcome)
        }
    }

    /**
     * Rückstau am Speicher-Kanal des Tests (Puffer 1024 Bytes, Nachricht von 100000 Bytes, Packet
     * Size 512): ohne Leser suspendiert der Writer mit vollem Puffer; ein Leser, der in Stücken zu
     * 100 Bytes abnimmt und zwischen den Stücken die Ausführung abgibt, bekommt die ganze Nachricht,
     * und der Puffer überschreitet die 1024 Bytes nie. Die Aussage gilt für diesen Kanal, nicht
     * für ein Netz oder eine andere Größe.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Writer suspendiert gegen einen langsamen Leser und puffert hoechstens die Kapazitaet`() = blocking {
        val big = ByteArray(100_000) { it.toByte() }
        val (sender, receiver) = MemoryChannel.pair(capacity = 1024)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertEquals(WriteOutcome.Written, MessageWriter(sender, 512).write(TdsMessage(PacketType.RPC, big)))
        }
        assertTrue(job.isActive, "Writer hat ohne Leser nicht suspendiert")
        assertEquals(1024, sender.bufferedBytes)
        val expected = handMessage(0x03, big, 504)
        val received = ByteBuffer.allocate(expected.size)
        while (received.hasRemaining()) {
            val chunk = ByteBuffer.allocate(minOf(100, received.remaining()))
            receiver.read(chunk)
            received.put(chunk.array(), 0, chunk.position())
            yield()
        }
        job.join()
        assertContentEquals(expected, received.array())
        assertTrue(sender.peakBufferedBytes <= 1024, "Spitze ${sender.peakBufferedBytes}")
    }

    /**
     * Fehler sind Ergebniswerte: eine Packet Size unter 9 ergibt `INVALID_PACKET_SIZE`, ein
     * geschlossener Kanal `CLOSED`, eine geschlossene Gegenstelle `IO_FAILURE`.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Schreibfehler enden in Ergebniswerten`() = blocking {
        val (sender, receiver) = MemoryChannel.pair(capacity = 64)
        val message = TdsMessage(PacketType.RPC, payload)
        val invalid = assertIs<WriteOutcome.Failed>(MessageWriter(sender, 8).write(message))
        assertEquals(FramingFailure.INVALID_PACKET_SIZE, assertIs<TransportError.Framing>(invalid.error).error.failure)
        receiver.close()
        val broken = assertIs<WriteOutcome.Failed>(MessageWriter(sender, 512).write(message))
        assertEquals(ChannelFailure.IO_FAILURE, assertIs<TransportError.Channel>(broken.error).error.failure)
        sender.close()
        val closed = assertIs<WriteOutcome.Failed>(MessageWriter(sender, 512).write(message))
        assertEquals(ChannelFailure.CLOSED, assertIs<TransportError.Channel>(closed.error).error.failure)
    }
}
