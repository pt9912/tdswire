package io.github.pt9912.tdswire.stability

import java.io.ByteArrayOutputStream

/**
 * Referenz für das Zusammensetzen von Nachrichten: liest den ganzen Strom auf einmal (kein
 * Zustandsautomat, keine Zerstückelung) und nennt, was der Codec für jede Zerlegung derselben Bytes
 * liefern muss. Der Aufbau ist vom Assembler des Codecs unabhängig; die Regeln laufen mit dem Codec
 * gleich und sind hier einzeln ausgeschrieben (`SPEC-006`, `SPEC-007`, `SPEC-028`): Typ in
 * [KNOWN_TYPES], Länge mindestens 8 und höchstens [maxPacket], Summe der Paketlängen höchstens
 * [maxMessage], ein Typ je Nachricht. Ein Fuzz-Lauf gegen diese Referenz belegt die Übereinstimmung von
 * Codec und Referenz, nicht die Übereinstimmung mit einem Standardtext. Gehört der erste Fehler zu
 * mehreren Regeln zugleich, nennt [Expected.defects] alle; der Codec darf jede davon melden.
 */
class FramingOracle(private val wire: ByteArray, private val maxPacket: Int, private val maxMessage: Int) {
    enum class Defect {
        UNKNOWN_PACKET_TYPE,
        LENGTH_BELOW_HEADER,
        LENGTH_ABOVE_PACKET_LIMIT,
        MESSAGE_ABOVE_LIMIT,
        PACKET_TYPE_CHANGED,
        MESSAGE_TRUNCATED,
    }

    class Message(val type: Int, val payload: ByteArray)

    /**
     * [defects] ist leer, wenn der Strom sauber zwischen zwei Nachrichten endet; [buffered] sind die
     * Nutzdatenbytes der angefangenen Nachricht.
     */
    class Expected(val messages: List<Message>, val defects: Set<Defect>, val buffered: Int)

    private val messages = mutableListOf<Message>()
    private val payload = ByteArrayOutputStream()
    private var position = 0
    private var type = NONE
    private var total = 0

    fun expected(): Expected {
        while (true) {
            val stop = step()
            if (stop != null) return Expected(messages, stop, payload.size())
        }
    }

    private fun step(): Set<Defect>? {
        val left = wire.size - position
        return when {
            left == 0 -> if (type == NONE) emptySet() else setOf(Defect.MESSAGE_TRUNCATED)
            left < HEADER -> setOf(Defect.MESSAGE_TRUNCATED)
            else -> packet(left)
        }
    }

    private fun packet(left: Int): Set<Defect>? {
        val packetType = wire[position].toInt() and BYTE
        val status = wire[position + 1].toInt() and BYTE
        val length = ((wire[position + 2].toInt() and BYTE) shl BYTE_BITS) or (wire[position + 3].toInt() and BYTE)
        val defects = headerDefects(packetType, length)
        return when {
            defects.isNotEmpty() -> defects

            left < length -> {
                payload.write(wire, position + HEADER, left - HEADER)
                setOf(Defect.MESSAGE_TRUNCATED)
            }

            else -> {
                payload.write(wire, position + HEADER, length - HEADER)
                type = packetType
                total += length
                position += length
                if (status and END_OF_MESSAGE != 0) finishMessage()
                null
            }
        }
    }

    private fun finishMessage() {
        messages += Message(type, payload.toByteArray())
        payload.reset()
        type = NONE
        total = 0
    }

    private fun headerDefects(packetType: Int, length: Int): Set<Defect> {
        val found = mutableSetOf<Defect>()
        if (packetType !in KNOWN_TYPES) found += Defect.UNKNOWN_PACKET_TYPE
        if (length < HEADER) found += Defect.LENGTH_BELOW_HEADER
        if (length > maxPacket) found += Defect.LENGTH_ABOVE_PACKET_LIMIT
        if (found.isEmpty()) {
            if (total + length > maxMessage) found += Defect.MESSAGE_ABOVE_LIMIT
            if (type != NONE && type != packetType) found += Defect.PACKET_TYPE_CHANGED
        }
        return found
    }

    companion object {
        /**
         * Festlegung der Referenz: die elf Typcodes 1, 2, 3, 4, 6, 7, 8, 14, 16, 17, 18. Die Menge läuft mit
         * `PacketType` gleich, ein Test hält das fest; sie ist kein Beleg gegen einen Standardtext.
         */
        val KNOWN_TYPES = setOf(0x01, 0x02, 0x03, 0x04, 0x06, 0x07, 0x08, 0x0E, 0x10, 0x11, 0x12)
        const val HEADER = 8
        private const val NONE = -1
        private const val BYTE = 0xFF
        private const val BYTE_BITS = 8
        private const val END_OF_MESSAGE = 0x01
    }
}
