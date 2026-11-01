package io.github.pt9912.tdswire.transport.channel

import io.github.pt9912.tdswire.testsupport.blocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Die Adresse der Gegenstelle am Kanal (`ADR-0016`): der Socket-Adapter liest sie vom verbundenen Kanal, verpackende
 * Kanäle geben sie weiter, Kanäle ohne Socket kennen keine.
 */
class RemoteAddressTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    /** Ein Kanal mit vorgegebener Adresse, der ein Byte liefert. */
    private class Addressed(override val remoteAddress: InetAddress?) : SuspendingChannel {
        override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
            buffer.put(1)
            return ChannelResult.Ok(1)
        }

        override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> = ChannelResult.Ok(Unit)

        override fun close() = Unit
    }

    /**
     * Orakel: der angenommene Kanal einer Verbindung von `127.0.0.1` hat diese Adresse (ohne Port); nach dem Schließen
     * des Adapters bleibt der beim Erzeugen gelesene Wert, und ein schon geschlossener Kanal ergibt `null`.
     */
    @Test
    fun `Der Socket-Adapter nennt die Adresse der Gegenstelle`() {
        AsynchronousServerSocketChannel.open().bind(InetSocketAddress(loopback, 0)).use { server ->
            Socket(loopback, (server.localAddress as InetSocketAddress).port).use {
                val accepted = server.accept().get()
                val adapter = SocketChannelAdapter(accepted)
                assertEquals(loopback, adapter.remoteAddress)
                adapter.close()
                assertEquals(loopback, adapter.remoteAddress)
                assertNull(SocketChannelAdapter(accepted).remoteAddress, "geschlossener Kanal")
            }
        }
    }

    /** Orakel: der Peeked-Kanal nennt die Adresse des inneren Kanals. */
    @Test
    fun `Der Peeked-Kanal gibt die Adresse weiter`() = blocking {
        val peeked = assertNotNull(PeekedChannel.peek(Addressed(loopback)))
        assertEquals(loopback, peeked.remoteAddress)
    }

    /** Orakel: der Switchable-Kanal nennt die Adresse des aktuellen Ziels; nach dem Wechsel die des neuen. */
    @Test
    fun `Der Switchable-Kanal gibt die Adresse des aktuellen Ziels weiter`() {
        val first = Addressed(loopback)
        val other = InetAddress.getByName("127.0.0.2")
        val switchable = SwitchableChannel(first)
        assertEquals(loopback, switchable.remoteAddress)
        switchable.switchTo(Addressed(other))
        assertEquals(other, switchable.remoteAddress)
    }

    @Test
    fun `Ein Speicher-Kanal kennt keine Gegenstelle`() {
        val (left, right) = MemoryChannel.pair(capacity = 16)
        assertNull(left.remoteAddress)
        assertNull(right.remoteAddress)
    }
}
