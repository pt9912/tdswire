package io.github.pt9912.tdswire.transport.framing

/**
 * Eine zusammengesetzte Nachricht: der Typ ihrer Pakete und die Nutzdaten ohne Header.
 *
 * [resetConnection] trägt das RESETCONNECTION-Bit des ersten Pakets, [ignore] das IGNORE-Bit des
 * letzten (Festlegung nach MS-TDS 2.2.3.1.2). Die Textdarstellung nennt Typ und Länge, nie die
 * Nutzdaten: eine LOGIN7-Nachricht trägt das verschleierte Passwort.
 */
class TdsMessage(
    val type: PacketType,
    val payload: ByteArray,
    val resetConnection: Boolean = false,
    val ignore: Boolean = false,
) {
    override fun equals(other: Any?): Boolean = other is TdsMessage &&
        type == other.type &&
        resetConnection == other.resetConnection &&
        ignore == other.ignore &&
        payload.contentEquals(other.payload)

    override fun hashCode(): Int = (type.hashCode() * HASH_FACTOR) + payload.contentHashCode()

    override fun toString(): String = "TdsMessage(type=$type, payloadBytes=${payload.size})"

    private companion object {
        const val HASH_FACTOR = 31
    }
}
