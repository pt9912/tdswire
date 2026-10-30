package io.github.pt9912.tdswire.transport.framing

import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PacketWriterTest {
    private fun split(payload: ByteArray, packetSize: Int, spid: Int = 0, firstId: Int = 1): List<ByteArray> =
        assertIs<FramingResult.Ok<List<ByteArray>>>(
            PacketWriter.split(PacketType.TABULAR_RESULT, payload, packetSize, spid, firstId),
        ).value

    /**
     * Orakel: eine Nachricht, die in ein Paket passt, hat den Header `04 01 <Länge> 00 00 01 00`
     * (Länge 8 + 3 = 11 = 0x000B) und danach die Nutzdaten.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Kleine Nachricht ist ein Paket mit EOM`() {
        val packets = split(hex("AA BB CC"), 4096)
        assertEquals(listOf("04 01 00 0B 00 00 01 00 AA BB CC"), packets.map { it.toHex() })
    }

    /**
     * Orakel: 20 Bytes bei Packet Size 16 sind drei Pakete mit 8, 8 und 4 Nutzdatenbytes; die
     * ersten beiden tragen Status 00, das letzte 01, die IDs zählen 1, 2, 3.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Nachricht wird auf die Packet Size zerlegt`() {
        val payload = ByteArray(20) { (it + 1).toByte() }
        val packets = split(payload, 16)
        assertEquals(
            listOf(
                "04 00 00 10 00 00 01 00 01 02 03 04 05 06 07 08",
                "04 00 00 10 00 00 02 00 09 0A 0B 0C 0D 0E 0F 10",
                "04 01 00 0C 00 00 03 00 11 12 13 14",
            ),
            packets.map { it.toHex() },
        )
    }

    /**
     * Grenze: ein Paket, dessen Länge genau die Packet Size ist, hat kein leeres Folgepaket.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Nachricht von genau einem Paket Laenge hat kein Folgepaket`() {
        val packets = split(ByteArray(8), 16)
        assertEquals(1, packets.size)
        assertEquals(16, packets[0].size)
        assertEquals(0x01, packets[0][1].toInt())
        assertEquals(2, split(ByteArray(9), 16).size)
    }

    /**
     * Grenze: eine leere Nachricht ist ein einziges Paket aus dem Header (Länge 8, EOM).
     *
     * @spec SPEC-006
     */
    @Test
    fun `Leere Nachricht ist ein Header-Paket`() {
        assertEquals(listOf("04 01 00 08 00 00 01 00"), split(ByteArray(0), 4096).map { it.toHex() })
    }

    /**
     * Grenze: die Packet-ID läuft nach 255 auf 0 über und zählt dann weiter; SPID steht im Header.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Packet-ID laeuft nach 255 auf 0 ueber`() {
        val packets = split(ByteArray(300), 9, spid = 0x0135)
        assertEquals(300, packets.size)
        val ids = packets.map { it[6].toInt() and 0xFF }
        assertEquals(255, ids[254])
        assertEquals(0, ids[255])
        assertEquals(1, ids[256])
        assertEquals(listOf(0x01, 0x35), listOf(packets[0][4].toInt(), packets[0][5].toInt() and 0xFF))
        assertEquals(1, packets.last()[1].toInt())
        assertEquals(0, packets.first()[1].toInt())
    }

    /**
     * Negativfall: Packet Size 8 (kein Nutzdatenbyte) und 65536 (über dem Längenfeld) sind
     * Ergebniswerte; 9 und 65535 sind die Grenzen, die gelten.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Packet Size ausserhalb 9 bis 65535 ist ein Ergebniswert`() {
        listOf(-1, 0, 8, 65536).forEach { size ->
            val result = PacketWriter.split(PacketType.TABULAR_RESULT, ByteArray(1), size)
            assertEquals(FramingFailure.INVALID_PACKET_SIZE, assertIs<FramingResult.Failed>(result).error.failure)
        }
        assertEquals(1, split(ByteArray(1), 9).size)
        assertEquals(2, split(ByteArray(65528 + 1), 65535).size)
    }

    /**
     * Roundtrip aus Writer und Assembler über viele Packet Sizes; die Orakel stehen in den Tests
     * oben, dieser belegt nur die Selbstkonsistenz.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Writer und Assembler sind zueinander konsistent`() {
        val payload = ByteArray(1000) { (it * 7).toByte() }
        for (size in listOf(9, 10, 16, 100, 512, 999, 1008, 1009, 4096)) {
            val assembler = MessageAssembler()
            val wire = split(payload, size).fold(ByteArray(0)) { acc, packet -> acc + packet }
            val step = assembler.feed(wire)
            assertEquals(null, step.error)
            assertEquals(1, step.messages.size)
            assertContentEquals(payload, step.messages[0].payload)
        }
    }
}
