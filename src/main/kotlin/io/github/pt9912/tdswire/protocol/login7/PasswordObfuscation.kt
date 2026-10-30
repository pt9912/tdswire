package io.github.pt9912.tdswire.protocol.login7

/**
 * Die Verschleierung des LOGIN7-Passworts (MS-TDS 2.2.6.4), je Byte der UTF-16LE-Darstellung:
 * Verschleiern vertauscht die Nibbles und bildet XOR mit `0xA5`; Entschleiern bildet erst XOR und
 * vertauscht dann. Das ist reversibel und kein Schutz (`ADR-0009`).
 */
object PasswordObfuscation {
    private const val MASK = 0xA5
    private const val NIBBLE_BITS = 4
    private const val BYTE_MASK = 0xFF

    fun deobfuscate(wire: ByteArray): ByteArray = ByteArray(wire.size) { index ->
        swapNibbles((wire[index].toInt() and BYTE_MASK) xor MASK).toByte()
    }

    fun obfuscate(plain: ByteArray): ByteArray = ByteArray(plain.size) { index ->
        (swapNibbles(plain[index].toInt() and BYTE_MASK) xor MASK).toByte()
    }

    private fun swapNibbles(value: Int): Int = ((value shl NIBBLE_BITS) or (value ushr NIBBLE_BITS)) and BYTE_MASK
}
