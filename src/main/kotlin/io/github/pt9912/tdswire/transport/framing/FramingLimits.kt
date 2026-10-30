package io.github.pt9912.tdswire.transport.framing

/**
 * Grenzen des Paket-Codecs.
 *
 * [maxPacketBytes] ist die größte zulässige Paketlänge samt Header (`SPEC-006`: 65535, die Grenze
 * des 16-Bit-Längenfelds); der Aufrufer kann sie auf die ausgehandelte Packet Size senken.
 * [maxMessageBytes] begrenzt die Summe der Längen aller Pakete einer Nachricht samt Headern
 * (`SPEC-007`: 16 MiB; vor abgeschlossenem Login `SPEC-046`: 65536, siehe [preLogin]).
 * Festlegung: Gezählt werden die deklarierten Paketlängen einschließlich der Header.
 */
data class FramingLimits(
    val maxPacketBytes: Int = MAX_TDS_PACKET_BYTES,
    val maxMessageBytes: Int = MAX_MESSAGE_BYTES,
) {
    init {
        require(maxPacketBytes in PacketHeader.SIZE..MAX_TDS_PACKET_BYTES) { "maxPacketBytes ausserhalb 8..65535" }
        require(maxMessageBytes >= maxPacketBytes) { "maxMessageBytes kleiner als maxPacketBytes" }
    }

    companion object {
        const val MAX_TDS_PACKET_BYTES = 65535
        const val MAX_MESSAGE_BYTES = 16 * 1024 * 1024
        const val MAX_PRELOGIN_MESSAGE_BYTES = 65536

        /** Grenzen vor abgeschlossenem Login (`SPEC-046`). */
        fun preLogin(): FramingLimits = FramingLimits(maxMessageBytes = MAX_PRELOGIN_MESSAGE_BYTES)
    }
}
