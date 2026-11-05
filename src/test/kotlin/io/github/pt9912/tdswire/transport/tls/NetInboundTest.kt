package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Der Lesepuffer des Handshakes im TDS-7.4-Pfad ([NetInbound]) ohne Engine: was hinter dem letzten Handshake-Paket im
 * selben Lesevorgang liegt, bleibt beim Umschalten auf rohes TLS erhalten. Ein TLS-1.2-Client der JDK-Engine schreibt
 * hinter sein letztes Handshake-Paket nichts, bevor er das `Finished` des Servers gelesen hat; der Fall ist deshalb
 * auf dieser Ebene belegt, nicht mit der Engine.
 */
class NetInboundTest {
    private val handshake = byteArrayOf(1, 2, 3, 4, 5)
    private val behind = byteArrayOf(0x17, 0x03, 0x03, 0x00, 0x02, 9, 9)

    private suspend fun scripted(): NetInbound {
        val (server, peer) = MemoryChannel.pair(1 shl 12)
        peer.writeFully(ByteBuffer.wrap(handMessage(TYPE_PRELOGIN, handshake, 4000) + behind))
        return NetInbound(server, 4096)
    }

    private suspend fun NetInbound.drain(size: Int): ByteArray {
        val target = ByteBuffer.allocate(size)
        while (target.hasRemaining()) assertIs<Pulled.Data>(fill(target))
        return target.array()
    }

    /**
     * Liegen hinter dem letzten Handshake-Paket Bytes im selben Lesevorgang, liefert der Rohmodus nach dem
     * Umschalten genau diese Bytes zuerst.
     * Mutation, die den Test rot färbt: in `endPackets` den Lesepuffer verwerfen (`raw.limit(0)`).
     *
     * @spec SPEC-028
     */
    @Test
    fun `bytes behind the last handshake packet reach the data phase`() = blocking {
        val inbound = scripted()
        inbound.startPackets()
        assertContentEquals(handshake, inbound.drain(handshake.size))
        assertTrue(inbound.endPackets(), "das Paket ist aufgebraucht")
        assertContentEquals(behind, inbound.drain(behind.size))
    }

    /**
     * Ist das Paket nicht aufgebraucht, schaltet `endPackets` nicht um und meldet `false`.
     * Mutation, die den Test rot färbt: `endPackets` immer `true` melden lassen.
     *
     * @spec SPEC-028
     */
    @Test
    fun `an unfinished handshake packet blocks the switch to raw`() = blocking {
        val inbound = scripted()
        inbound.startPackets()
        assertEquals(2, inbound.drain(2).size)
        assertFalse(inbound.endPackets())
    }

    private companion object {
        const val TYPE_PRELOGIN = 0x12
    }
}
