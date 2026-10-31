package io.github.pt9912.tdswire.transport.framing

/**
 * Zerlegt eine Nachricht in Pakete (`SPEC-006`, `SPEC-028`). Jedes Paket außer dem letzten hat genau
 * die Länge `packetSize`, das letzte trägt den Rest und das EOM-Bit; eine leere Nachricht ist ein
 * einziges Paket aus dem Header. Die Packet-ID zählt ab `firstPacketId` und läuft nach 255 auf 0
 * über. Der Writer setzt weder RESETCONNECTION noch IGNORE.
 */
object PacketWriter {
    private const val ID_MODULUS = 256

    /**
     * Die Pakete als einzelne Byte-Arrays. `packetSize` zählt den Header mit und liegt in
     * 9..65535 (Festlegung: mindestens ein Nutzdatenbyte je Paket, höchstens das 16-Bit-Längenfeld);
     * sonst ist das Ergebnis ein [FramingResult.Failed].
     */
    fun split(
        type: PacketType,
        payload: ByteArray,
        packetSize: Int,
        spid: Int = 0,
        firstPacketId: Int = 1,
    ): FramingResult<List<ByteArray>> {
        if (packetSize <= PacketHeader.SIZE || packetSize > FramingLimits.MAX_TDS_PACKET_BYTES) {
            return FramingResult.Failed(FramingError(FramingFailure.INVALID_PACKET_SIZE, "Packet Size $packetSize"))
        }
        val chunk = packetSize - PacketHeader.SIZE
        val count = maxOf(1, (payload.size + chunk - 1) / chunk)
        val packets = List(count) { index ->
            val from = index * chunk
            val to = minOf(payload.size, from + chunk)
            val header = PacketHeader(
                type = type,
                status = if (index == count - 1) PacketStatus.END_OF_MESSAGE else PacketStatus.NORMAL,
                length = PacketHeader.SIZE + (to - from),
                spid = spid,
                packetId = (firstPacketId + index) % ID_MODULUS,
            )
            header.toBytes() + payload.copyOfRange(from, to)
        }
        return FramingResult.Ok(packets)
    }

    /**
     * Ein einzelnes Paket aus den ersten [length] Bytes von [payload]: Header mit Typ, EOM-Bit nur bei
     * [endOfMessage], SPID 0, Länge samt Header und der Packet-ID modulo 256. Die Grenzen der Packet Size
     * prüft der Aufrufer; der Strom-Schreiber des Transports baut seine Pakete hier.
     */
    fun packet(type: PacketType, payload: ByteArray, length: Int, endOfMessage: Boolean, packetId: Int): ByteArray {
        val header = PacketHeader(
            type = type,
            status = if (endOfMessage) PacketStatus.END_OF_MESSAGE else PacketStatus.NORMAL,
            length = PacketHeader.SIZE + length,
            packetId = packetId % ID_MODULUS,
        )
        return header.toBytes() + payload.copyOfRange(0, length)
    }
}
