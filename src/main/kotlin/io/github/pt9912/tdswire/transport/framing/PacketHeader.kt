package io.github.pt9912.tdswire.transport.framing

/**
 * Der 8-Byte-Header eines TDS-Pakets (MS-TDS 2.2.3.1): Typ, Status, Länge (Big-Endian, samt Header),
 * SPID (Big-Endian), Packet-ID, Window.
 *
 * Festlegung: Die Packet-ID zählt je Nachricht ab 1 und läuft nach 255 auf 0 über; der Leser prüft
 * sie nicht, weil MS-TDS sie als vom Empfänger ignoriert beschreibt.
 */
data class PacketHeader(
    val type: PacketType,
    val status: Int,
    val length: Int,
    val spid: Int = 0,
    val packetId: Int = 1,
    val window: Int = 0,
) {
    val payloadLength: Int get() = length - SIZE
    val endOfMessage: Boolean get() = status and PacketStatus.END_OF_MESSAGE != 0

    /** Die 8 Headerbytes; Felder außerhalb ihres Bereichs werden auf die Breite des Felds gekürzt. */
    fun toBytes(): ByteArray = byteArrayOf(
        type.code.toByte(),
        status.toByte(),
        (length ushr BYTE_BITS).toByte(),
        length.toByte(),
        (spid ushr BYTE_BITS).toByte(),
        spid.toByte(),
        packetId.toByte(),
        window.toByte(),
    )

    companion object {
        const val SIZE = 8
        private const val BYTE_BITS = 8
        private const val BYTE_MASK = 0xFF
        private const val OFFSET_STATUS = 1
        private const val OFFSET_LENGTH = 2
        private const val OFFSET_SPID = 4
        private const val OFFSET_PACKET_ID = 6
        private const val OFFSET_WINDOW = 7

        /**
         * Liest den Header ab [offset] und prüft Typ, Untergrenze (Länge mindestens 8) und die
         * Obergrenze aus [limits].
         */
        fun parse(bytes: ByteArray, offset: Int, limits: FramingLimits): FramingResult<PacketHeader> {
            if (offset < 0 || bytes.size - offset < SIZE) {
                return failed(FramingFailure.HEADER_TRUNCATED, "weniger als 8 Byte Header")
            }
            val code = bytes[offset].toInt() and BYTE_MASK
            val length = u16(bytes, offset + OFFSET_LENGTH)
            val type = PacketType.fromCode(code)
            return when {
                type == null -> failed(FramingFailure.UNKNOWN_PACKET_TYPE, "Pakettyp $code")

                length < SIZE -> failed(FramingFailure.LENGTH_BELOW_HEADER, "Paketlaenge $length")

                length > limits.maxPacketBytes ->
                    failed(FramingFailure.LENGTH_ABOVE_PACKET_LIMIT, "Paketlaenge $length")

                else -> FramingResult.Ok(
                    PacketHeader(
                        type = type,
                        status = bytes[offset + OFFSET_STATUS].toInt() and BYTE_MASK,
                        length = length,
                        spid = u16(bytes, offset + OFFSET_SPID),
                        packetId = bytes[offset + OFFSET_PACKET_ID].toInt() and BYTE_MASK,
                        window = bytes[offset + OFFSET_WINDOW].toInt() and BYTE_MASK,
                    ),
                )
            }
        }

        private fun u16(bytes: ByteArray, at: Int): Int =
            ((bytes[at].toInt() and BYTE_MASK) shl BYTE_BITS) or (bytes[at + 1].toInt() and BYTE_MASK)

        private fun failed(failure: FramingFailure, detail: String): FramingResult.Failed =
            FramingResult.Failed(FramingError(failure, detail))
    }
}
