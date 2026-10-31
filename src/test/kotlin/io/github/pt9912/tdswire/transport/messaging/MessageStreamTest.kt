package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.testsupport.ScriptedWire
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die Pakete eines Antwortstroms (`MessageStream`) gegen von Hand gebaute Orakel (`handMessage`,
 * unabhängig vom Paket-Codec) und gegen einen Draht, der Schreibvorgänge zählt und anhalten kann.
 * Aussagen über einen Socket tragen diese Tests nicht.
 */
class MessageStreamTest {
    private val payload = ByteArray(2000) { (it * 3 + 1).toByte() }
    private val tabular = 0x04

    private fun streamOf(wire: ScriptedWire, packetSize: Int) =
        MessageWriter(wire, packetSize).open(PacketType.TABULAR_RESULT)

    /**
     * Eine Nachricht von 2000 Nutzdatenbytes mit Packet Size 512, in ungleichen Stücken geschrieben,
     * liegt als vier Pakete auf dem Draht, byteweise gleich der von Hand zerlegten Nachricht (504
     * Nutzdatenbytes je Paket, Rest 488, EOM nur im letzten, IDs 1 bis 4).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Stuecke ergeben dieselben Pakete wie die ganze Nachricht`() = blocking {
        val wire = ScriptedWire(ByteArray(0), retain = true)
        val stream = streamOf(wire, 512)
        var from = 0
        listOf(1, 600, 3, 504, 0, 892).forEach { size ->
            assertTrue(stream.write(payload.copyOfRange(from, from + size)))
            from += size
        }
        assertEquals(payload.size, from)
        assertTrue(stream.finish())
        assertContentEquals(handMessage(tabular, payload, 504), wire.wireContent)
    }

    /**
     * Ein volles Paket geht erst auf den Draht, wenn weitere Bytes oder das Ende folgen: genau 504 Bytes
     * ergeben nach dem Schreiben kein Paket und nach dem Ende ein einziges mit EOM (kein leeres
     * Folgepaket); ein weiteres Byte schiebt das volle Paket ohne EOM hinaus.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Ein volles Paket wird bis zum naechsten Byte oder zum Ende gehalten`() = blocking {
        val exact = ScriptedWire(ByteArray(0), retain = true)
        val stream = streamOf(exact, 512)
        assertTrue(stream.write(ByteArray(504) { 7 }))
        assertEquals(0, exact.packetCount)
        assertTrue(stream.finish())
        assertEquals(listOf(512), exact.packets.map { it.length })
        assertTrue(exact.packets.single().endOfMessage)

        val more = ScriptedWire(ByteArray(0), retain = true)
        val longer = streamOf(more, 512)
        assertTrue(longer.write(ByteArray(505) { 7 }))
        assertEquals(listOf(512), more.packets.map { it.length })
        assertFalse(more.packets.single().endOfMessage)
        assertTrue(longer.finish())
        assertEquals(listOf(512, 9), more.packets.map { it.length })
        assertEquals(listOf(false, true), more.packets.map { it.endOfMessage })
    }

    /**
     * Eine leere Nachricht ist ein einziges Paket aus dem Header mit EOM; ohne [finish] schreibt der
     * Strom kein Paket mit EOM, auch nicht nach vollen Paketen.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Leere Nachricht ist ein Header-Paket und ohne Ende gibt es kein EOM`() = blocking {
        val empty = ScriptedWire(ByteArray(0), retain = true)
        assertTrue(streamOf(empty, 512).finish())
        assertContentEquals(handMessage(tabular, ByteArray(0), 504), empty.wireContent)

        val open = ScriptedWire(ByteArray(0), retain = true)
        assertTrue(streamOf(open, 512).write(ByteArray(2000)))
        assertEquals(3, open.packetCount)
        assertTrue(open.packets.none { it.endOfMessage })
    }

    /**
     * Die Packet-ID zählt ab 1 und läuft nach 255 auf 0 über: bei 300 Paketen ist die ID des Pakets
     * mit Index k gleich (k + 1) modulo 256.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Die Packet-ID laeuft nach 255 auf 0 ueber`() = blocking {
        val wire = ScriptedWire(ByteArray(0), retain = true)
        val stream = streamOf(wire, 9)
        assertTrue(stream.write(ByteArray(300)))
        assertTrue(stream.finish())
        assertEquals(300, wire.packetCount)
        assertEquals(List(300) { (it + 1) % 256 }, wire.packets.map { it.packetId })
    }

    /**
     * Scheitert ein Schreibvorgang des Kanals, melden dieses und alle folgenden Aufrufe `false`, und es
     * geht kein weiteres Paket hinaus; auch das Ende nicht.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Nach einem Fehler des Kanals schreibt der Strom nichts mehr`() = blocking {
        val wire = ScriptedWire(ByteArray(0), retain = true)
        wire.failAfterPackets = 1
        val stream = streamOf(wire, 512)
        assertFalse(stream.write(ByteArray(2000)))
        assertEquals(1, wire.packetCount)
        assertFalse(stream.write(ByteArray(1)))
        assertFalse(stream.finish())
        assertEquals(1, wire.packetCount)
    }

    /**
     * Eine Packet Size, die der Paket-Codec nicht schreibt (8 und 65536), macht den Strom von Anfang an
     * fehlerhaft; die Grenzen 9 und 65535 sind gültig.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Eine ungueltige Packet Size macht den Strom fehlerhaft`() = blocking {
        listOf(8, 65536, 0, -1).forEach { size ->
            val wire = ScriptedWire(ByteArray(0), retain = true)
            val stream = streamOf(wire, size)
            assertFalse(stream.write(ByteArray(1)), "$size")
            assertFalse(stream.finish(), "$size")
            assertEquals(0, wire.packetCount, "$size")
        }
        listOf(9, 65535).forEach { size ->
            val wire = ScriptedWire(ByteArray(0), retain = true)
            val stream = streamOf(wire, size)
            assertTrue(stream.write(ByteArray(3)), "$size")
            assertTrue(stream.finish(), "$size")
            assertEquals(if (size == 9) 3 else 1, wire.packetCount, "$size")
            assertTrue(wire.packets.all { it.length <= size }, "$size")
            assertTrue(wire.packets.last().endOfMessage, "$size")
        }
    }

    /**
     * Rückstau: nimmt der Draht nach zwei Paketen nichts mehr an, kehrt [write] für eine große
     * Nachricht nicht zurück; der Draht sieht höchstens diese zwei Pakete. Nach dem Öffnen geht der Rest
     * hinaus. Die Reihenfolge entsteht ohne Uhr: der Draht meldet sein Warten.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein haltender Draht haelt den Schreiber an`() = blocking {
        val wire = ScriptedWire(ByteArray(0), retain = true)
        wire.stallAfterBytes = 2 * 512L
        val stream = streamOf(wire, 512)
        val writing = launch { stream.write(ByteArray(10 * 504)) }
        wire.stalled.await()
        repeat(3) { yield() }
        assertEquals(2, wire.packetCount)
        assertTrue(writing.isActive)
        wire.release()
        writing.join()
        assertTrue(stream.finish())
        assertEquals(10, wire.packetCount)
    }
}
