package io.github.pt9912.tdswire.transport.channel

import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MemoryChannelTest {
    private fun bytes(count: Int, offset: Int = 0) = ByteArray(count) { (it + offset).toByte() }

    private suspend fun MemoryChannel.readValue(size: Int): Pair<Int, List<Byte>> {
        val buffer = ByteBuffer.allocate(size)
        val result = assertIs<ChannelResult.Ok<Int>>(read(buffer))
        return result.value to buffer.array().copyOf(maxOf(result.value, 0)).toList()
    }

    /**
     * Orakel: 20 Bytes `00..13` gehen durch einen Puffer von 7 Bytes (Umlauf des Ringpuffers) und
     * kommen gleich und in derselben Reihenfolge an; der Puffer hält nie mehr als 7.
     *
     * @spec SPEC-024
     */
    @Test
    fun `Bytes kommen durch einen kleinen Puffer gleich an`() = blocking {
        val (first, second) = MemoryChannel.pair(capacity = 7)
        val sent = bytes(20)
        val writer = launch { assertEquals(ChannelResult.Ok(Unit), first.writeFully(ByteBuffer.wrap(sent))) }
        val received = ByteBuffer.allocate(20)
        while (received.hasRemaining()) {
            val chunk = ByteBuffer.allocate(3)
            assertIs<ChannelResult.Ok<Int>>(second.read(chunk))
            received.put(chunk.array(), 0, chunk.position())
        }
        writer.join()
        assertContentEquals(sent, received.array())
        assertTrue(first.peakBufferedBytes <= 7)
        assertEquals(7, first.peakBufferedBytes)
    }

    /**
     * Ein Schreiber ohne Leser suspendiert, sobald der Puffer voll ist, und setzt fort, wenn der
     * Leser abnimmt.
     *
     * @spec SPEC-024
     */
    @Test
    fun `Schreiber suspendiert bei vollem Puffer`() = blocking {
        val (first, second) = MemoryChannel.pair(capacity = 4)
        val writer = launch(start = CoroutineStart.UNDISPATCHED) { first.writeFully(ByteBuffer.wrap(bytes(10))) }
        yield()
        assertTrue(writer.isActive)
        assertEquals(4, first.bufferedBytes)
        assertEquals(4 to bytes(4).toList(), second.readValue(4))
        assertEquals(4 to bytes(4, 4).toList(), second.readValue(4))
        assertEquals(2 to bytes(2, 8).toList(), second.readValue(4))
        writer.join()
        assertEquals(0, first.bufferedBytes)
    }

    /**
     * Schließen des Schreibers: die Gegenstelle liest die gepufferten Bytes, danach -1.
     *
     * @spec SPEC-024
     */
    @Test
    fun `Schliessen beendet die Richtung nach den gepufferten Bytes`() = blocking {
        val (first, second) = MemoryChannel.pair(capacity = 8)
        first.writeFully(ByteBuffer.wrap(bytes(3)))
        first.close()
        first.close()
        assertEquals(3 to bytes(3).toList(), second.readValue(8))
        assertEquals(-1, second.readValue(8).first)
        assertEquals(-1, second.readValue(8).first)
    }

    /**
     * Nach dem lokalen Schließen melden Lesen und Schreiben `CLOSED`; ein Schreiber, dessen
     * Gegenstelle geschlossen hat, bekommt `IO_FAILURE`.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Operationen auf geschlossenen Enden enden in Ergebniswerten`() = blocking {
        val (first, second) = MemoryChannel.pair(capacity = 8)
        second.close()
        val broken = assertIs<ChannelResult.Failed>(first.writeFully(ByteBuffer.wrap(bytes(1))))
        assertEquals(ChannelFailure.IO_FAILURE, broken.error.failure)
        val closedRead = assertIs<ChannelResult.Failed>(second.read(ByteBuffer.allocate(1)))
        assertEquals(ChannelFailure.CLOSED, closedRead.error.failure)
        val closedWrite = assertIs<ChannelResult.Failed>(second.writeFully(ByteBuffer.wrap(bytes(1))))
        assertEquals(ChannelFailure.CLOSED, closedWrite.error.failure)
        assertEquals(ChannelErrorCategory.TDS_INTERNAL_ERROR, closedWrite.error.category)
    }

    /**
     * Ein wartender Lesevorgang endet mit dem Schließen des eigenen Endes; ein abgebrochener
     * schließt den Kanal (Vertrag von `SuspendingChannel`).
     *
     * @spec SPEC-027
     */
    @Test
    fun `Abbruch eines wartenden Lesens schliesst den Kanal`() = blocking {
        val (first, second) = MemoryChannel.pair(capacity = 8)
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { first.read(ByteBuffer.allocate(1)) }
        assertTrue(pending.isActive)
        pending.cancel()
        pending.join()
        assertEquals(-1, second.readValue(1).first)
        val afterwards = assertIs<ChannelResult.Failed>(first.read(ByteBuffer.allocate(1)))
        assertEquals(ChannelFailure.CLOSED, afterwards.error.failure)
    }
}
