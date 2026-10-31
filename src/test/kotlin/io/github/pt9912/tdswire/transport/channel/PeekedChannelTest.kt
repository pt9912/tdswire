package io.github.pt9912.tdswire.transport.channel

import io.github.pt9912.tdswire.testsupport.blocking
import java.nio.BufferOverflowException
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PeekedChannelTest {
    private fun bytes(vararg values: Int) = ByteBuffer.wrap(ByteArray(values.size) { values[it].toByte() })

    private class FailingChannel : SuspendingChannel {
        override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> =
            ChannelResult.Failed(ChannelError(ChannelFailure.IO_FAILURE, "test"))

        override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> = ChannelResult.Ok(Unit)

        override fun close() = Unit
    }

    /** Orakel: das erste Byte `0x16` geht nicht verloren; der erste Lesevorgang liefert es, die folgenden den Rest. */
    @Test
    fun `Das erste Byte wird gelesen und danach wieder geliefert`() = blocking {
        val (local, remote) = MemoryChannel.pair(capacity = 16)
        remote.writeFully(bytes(0x16, 0x03, 0x04))
        val peeked = assertNotNull(PeekedChannel.peek(local))
        assertEquals(0x16.toByte(), peeked.firstByte)
        val first = ByteBuffer.allocate(8)
        assertEquals(ChannelResult.Ok(1), peeked.read(first))
        assertEquals(0x16.toByte(), first.get(0))
        val rest = ByteBuffer.allocate(8)
        assertEquals(ChannelResult.Ok(2), peeked.read(rest))
        assertEquals(listOf<Byte>(0x03, 0x04), rest.array().take(2))
    }

    /** Endet die Gegenstelle vor dem ersten Byte, ist das Ergebnis `null`; ebenso nach einem Lesefehler. */
    @Test
    fun `Ohne erstes Byte ist das Ergebnis null`() = blocking {
        val (local, remote) = MemoryChannel.pair(capacity = 16)
        remote.close()
        assertNull(PeekedChannel.peek(local))
        assertNull(PeekedChannel.peek(FailingChannel()))
    }

    /** Ein Puffer ohne Platz ist ein Verstoß des Aufrufers und wirft; das erste Byte ist dann nicht abgelegt. */
    @Test
    fun `Ein Puffer ohne Platz wirft`() = blocking {
        val (local, remote) = MemoryChannel.pair(capacity = 16)
        remote.writeFully(bytes(0x16))
        val peeked = assertNotNull(PeekedChannel.peek(local))
        assertFailsWith<BufferOverflowException> { peeked.read(ByteBuffer.allocate(0)) }
        assertIs<ChannelResult.Ok<Int>>(peeked.read(ByteBuffer.allocate(1)))
    }

    /** Schreiben und Schließen gehen an den inneren Kanal. */
    @Test
    fun `Schreiben und Schliessen gehen an den inneren Kanal`() = blocking {
        val (local, remote) = MemoryChannel.pair(capacity = 16)
        remote.writeFully(bytes(0x16))
        val peeked = assertNotNull(PeekedChannel.peek(local))
        peeked.writeFully(bytes(7))
        val received = ByteBuffer.allocate(1)
        assertEquals(ChannelResult.Ok(1), remote.read(received))
        assertEquals(7.toByte(), received.get(0))
        peeked.close()
        assertEquals(ChannelResult.Ok(-1), remote.read(ByteBuffer.allocate(1)))
    }
}
