package io.github.pt9912.tdswire.protocol.wire

import java.io.ByteArrayOutputStream

/**
 * Schreibt Zahlen und Text in einen wachsenden Puffer. Zahlen sind Little-Endian, außer `u16Be`
 * (PRELOGIN-Tabelle). Die Längenpräfixe prüfen ihre Breite und melden eine Überschreitung als
 * [ProtocolFailure.TOO_LONG] ([ProtocolViolation]).
 */
internal class ByteWriter {
    private val out = ByteArrayOutputStream()

    val size: Int get() = out.size()

    fun u8(value: Int) = out.write(value and BYTE_MASK)

    fun u16Le(value: Int) {
        u8(value)
        u8(value ushr BYTE_BITS)
    }

    fun u16Be(value: Int) {
        u8(value ushr BYTE_BITS)
        u8(value)
    }

    fun i32Le(value: Int) {
        u16Le(value)
        u16Le(value ushr SHORT_BITS)
    }

    fun i32Be(value: Int) {
        u16Be(value ushr SHORT_BITS)
        u16Be(value)
    }

    fun i64Le(value: Long) {
        i32Le(value.toInt())
        i32Le((value ushr INT_BITS).toInt())
    }

    fun bytes(value: ByteArray) = out.write(value, 0, value.size)

    /** UTF-16LE ohne Längenpräfix. */
    fun utf16(text: String) = text.forEach { u16Le(it.code) }

    /** B_VARCHAR: Zeichenzahl in einem Byte, dann UTF-16LE. */
    fun bVarchar(text: String, what: String) {
        if (text.length > MAX_B_LENGTH) violation(ProtocolFailure.TOO_LONG, "$what ueber $MAX_B_LENGTH Zeichen")
        u8(text.length)
        utf16(text)
    }

    /** US_VARCHAR: Zeichenzahl in zwei Bytes, dann UTF-16LE. */
    fun usVarchar(text: String, what: String) {
        if (text.length > MAX_US_LENGTH) violation(ProtocolFailure.TOO_LONG, "$what ueber $MAX_US_LENGTH Zeichen")
        u16Le(text.length)
        utf16(text)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    companion object {
        const val MAX_B_LENGTH = 255
        const val MAX_US_LENGTH = 65535
        private const val BYTE_MASK = 0xFF
        private const val BYTE_BITS = 8
        private const val SHORT_BITS = 16
        private const val INT_BITS = 32
    }
}
