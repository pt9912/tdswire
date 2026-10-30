package io.github.pt9912.tdswire.transport.framing

import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PacketHeaderTest {
    private val limits = FramingLimits()

    private fun parsed(text: String, withLimits: FramingLimits = limits): FramingResult<PacketHeader> =
        PacketHeader.parse(hex(text), 0, withLimits)

    private fun failureOf(result: FramingResult<PacketHeader>): FramingFailure =
        assertIs<FramingResult.Failed>(result).error.failure

    /**
     * Orakel: der Beispiel-Header der PRELOGIN-Anfrage aus MS-TDS 4.1.1 (`12 01 00 2F 00 00 01 00`).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Header der PRELOGIN-Anfrage aus MS-TDS wird gelesen`() {
        val header = assertIs<FramingResult.Ok<PacketHeader>>(parsed("12 01 00 2F 00 00 01 00")).value
        assertEquals(PacketType.PRELOGIN, header.type)
        assertEquals(PacketStatus.END_OF_MESSAGE, header.status)
        assertEquals(0x2F, header.length)
        assertEquals(0, header.spid)
        assertEquals(1, header.packetId)
        assertEquals(0, header.window)
    }

    /**
     * Orakel: Länge und SPID sind Big-Endian (MS-TDS 2.2.3.1), Antwort-Header `04 01 01 02 00 35 07 00`.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Laenge und SPID sind Big-Endian`() {
        val header = assertIs<FramingResult.Ok<PacketHeader>>(parsed("04 01 01 02 00 35 07 00")).value
        assertEquals(0x0102, header.length)
        assertEquals(0x0035, header.spid)
        assertEquals(7, header.packetId)
        assertEquals("04 01 01 02 00 35 07 00", header.toBytes().toHex())
    }

    /**
     * Orakel: die Status-Bits NORMAL, EOM, IGNORE, RESETCONNECTION und RESETCONNECTIONSKIPTRAN
     * (MS-TDS 2.2.3.1.2) liegen auf 0x00, 0x01, 0x02, 0x08 und 0x10.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Status-Bits haben die Werte aus MS-TDS`() {
        assertEquals(0x00, PacketStatus.NORMAL)
        assertEquals(0x01, PacketStatus.END_OF_MESSAGE)
        assertEquals(0x02, PacketStatus.IGNORE)
        assertEquals(0x08, PacketStatus.RESET_CONNECTION)
        assertEquals(0x10, PacketStatus.RESET_CONNECTION_SKIP_TRAN)
        val header = assertIs<FramingResult.Ok<PacketHeader>>(parsed("01 09 00 08 00 00 01 00")).value
        assertEquals(true, header.endOfMessage)
    }

    /**
     * Orakel: die Pakettyp-Codes aus MS-TDS 2.2.3.1.1.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Pakettyp-Codes haben die Werte aus MS-TDS`() {
        val expected = mapOf(
            PacketType.SQL_BATCH to 0x01,
            PacketType.PRE_TDS7_LOGIN to 0x02,
            PacketType.RPC to 0x03,
            PacketType.TABULAR_RESULT to 0x04,
            PacketType.ATTENTION to 0x06,
            PacketType.BULK_LOAD to 0x07,
            PacketType.FEDERATED_AUTH to 0x08,
            PacketType.TRANSACTION_MANAGER to 0x0E,
            PacketType.LOGIN7 to 0x10,
            PacketType.SSPI to 0x11,
            PacketType.PRELOGIN to 0x12,
        )
        assertEquals(expected.size, PacketType.entries.size)
        expected.forEach { (type, code) -> assertEquals(code, type.code) }
    }

    /**
     * Grenze: die kleinste zulässige Länge ist der Header selbst (8), 7 ist ein Fehler.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Laenge 8 ist zulaessig und 7 nicht`() {
        val ok = assertIs<FramingResult.Ok<PacketHeader>>(parsed("06 01 00 08 00 00 01 00")).value
        assertEquals(0, ok.payloadLength)
        assertEquals(FramingFailure.LENGTH_BELOW_HEADER, failureOf(parsed("06 01 00 07 00 00 01 00")))
        assertEquals(FramingFailure.LENGTH_BELOW_HEADER, failureOf(parsed("06 01 00 00 00 00 01 00")))
    }

    /**
     * Grenze: die konfigurierte Obergrenze gilt einschließlich, eins darüber ist ein Fehler;
     * ohne Konfiguration ist 65535 (`FF FF`) die Obergrenze des Längenfelds.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Obergrenze der Paketlaenge gilt einschliesslich`() {
        val small = FramingLimits(maxPacketBytes = 4096)
        assertIs<FramingResult.Ok<PacketHeader>>(parsed("01 01 10 00 00 00 01 00", small))
        assertEquals(FramingFailure.LENGTH_ABOVE_PACKET_LIMIT, failureOf(parsed("01 01 10 01 00 00 01 00", small)))
        assertIs<FramingResult.Ok<PacketHeader>>(parsed("01 01 FF FF 00 00 01 00"))
    }

    /**
     * Negativfall: ein Pakettyp außerhalb des Katalogs (0x05, 0x00, 0xFF) ist ein Protokollfehler
     * der Kategorie `TDS_PROTOCOL_ERROR`.
     *
     * @spec SPEC-011
     */
    @Test
    fun `Unbekannter Pakettyp ist ein Protokollfehler`() {
        listOf("05", "00", "FF", "13").forEach { code ->
            val error = assertIs<FramingResult.Failed>(parsed("$code 01 00 10 00 00 01 00")).error
            assertEquals(FramingFailure.UNKNOWN_PACKET_TYPE, error.failure)
            assertEquals(FramingErrorCategory.TDS_PROTOCOL_ERROR, error.category)
        }
    }

    /**
     * Negativfall: weniger als 8 Bytes ergeben `HEADER_TRUNCATED`, nie eine Ausnahme.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zu kurzer Header ist ein Ergebniswert`() {
        for (size in 0 until PacketHeader.SIZE) {
            assertEquals(
                FramingFailure.HEADER_TRUNCATED,
                failureOf(PacketHeader.parse(ByteArray(size), 0, limits)),
            )
        }
        assertEquals(
            FramingFailure.HEADER_TRUNCATED,
            failureOf(PacketHeader.parse(hex("12 01 00 10 00 00 01 00"), 1, limits)),
        )
    }

    /**
     * Festlegung: die Gültigkeit der Grenzen ist Konfiguration, kein Eingabewert; eine Packet-Size-Grenze
     * außerhalb 8..65535 weist der Konstruktor zurück.
     *
     * @spec SPEC-006
     */
    @Test
    fun `Grenzen ausserhalb des Längenfelds werden zurueckgewiesen`() {
        assertFailsWith<IllegalArgumentException> { FramingLimits(maxPacketBytes = 7) }
        assertFailsWith<IllegalArgumentException> { FramingLimits(maxPacketBytes = 65536) }
    }
}
