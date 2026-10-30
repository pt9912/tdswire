package io.github.pt9912.tdswire.transport.framing

/**
 * Bits des Status-Bytes im TDS-Header (MS-TDS 2.2.3.1.2).
 *
 * Festlegung: Der Leser behält unbekannte Bits im Status-Byte und wertet sie nicht aus.
 */
object PacketStatus {
    const val NORMAL = 0x00
    const val END_OF_MESSAGE = 0x01
    const val IGNORE = 0x02
    const val RESET_CONNECTION = 0x08
    const val RESET_CONNECTION_SKIP_TRAN = 0x10
}
