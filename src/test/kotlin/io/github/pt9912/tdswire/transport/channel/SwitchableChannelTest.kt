package io.github.pt9912.tdswire.transport.channel

import io.github.pt9912.tdswire.testsupport.blocking
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SwitchableChannelTest {
    private suspend fun SuspendingChannel.readBytes(count: Int): List<Int> {
        val buffer = ByteBuffer.allocate(count)
        while (buffer.hasRemaining()) {
            val read = assertIs<ChannelResult.Ok<Int>>(read(buffer)).value
            if (read < 0) break
        }
        return buffer.array().take(buffer.position()).map { it.toInt() }
    }

    private suspend fun SuspendingChannel.writeBytes(vararg bytes: Int) {
        writeFully(ByteBuffer.wrap(ByteArray(bytes.size) { bytes[it].toByte() }))
    }

    /**
     * Vor dem Wechsel gehen Lesen und Schreiben an das erste Ziel, danach an das zweite; das erste bleibt offen.
     * Das Schließen trifft das aktuelle Ziel.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Nach dem Wechsel gehen alle Vorgaenge an das neue Ziel`() = blocking {
        val (firstLocal, firstRemote) = MemoryChannel.pair(capacity = 64)
        val (secondLocal, secondRemote) = MemoryChannel.pair(capacity = 64)
        val switchable = SwitchableChannel(firstLocal)

        switchable.writeBytes(1, 2)
        assertEquals(listOf(1, 2), firstRemote.readBytes(2))
        firstRemote.writeBytes(3)
        assertEquals(listOf(3), switchable.readBytes(1))

        switchable.switchTo(secondLocal)
        switchable.writeBytes(4)
        assertEquals(listOf(4), secondRemote.readBytes(1))
        secondRemote.writeBytes(5)
        assertEquals(listOf(5), switchable.readBytes(1))

        switchable.close()
        assertEquals(emptyList(), secondRemote.readBytes(1), "das neue Ziel ist geschlossen")
        firstRemote.writeBytes(6)
        assertEquals(listOf(6), firstLocal.readBytes(1), "das erste Ziel blieb offen")
    }
}
