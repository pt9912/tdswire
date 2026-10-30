package io.github.pt9912.tdswire.protocol.wire

/**
 * Liest Zahlen und Text aus einer Nachricht im Speicher, begrenzt auf `bytes`. Ein Zugriff über
 * das Ende hinaus meldet [ProtocolFailure.TRUNCATED] als [ProtocolViolation]. Zahlen sind
 * Little-Endian, außer `u16Be` (PRELOGIN-Tabelle).
 */
internal class ByteReader(private val bytes: ByteArray) {
    var position: Int = 0
        private set

    val remaining: Int get() = bytes.size - position

    fun seek(target: Int) {
        if (target < 0 || target > bytes.size) {
            violation(ProtocolFailure.INVALID_OFFSET, "Position $target ausserhalb 0..${bytes.size}")
        }
        position = target
    }

    fun u8(): Int {
        need(1)
        return bytes[position++].toInt() and BYTE_MASK
    }

    fun u16Le(): Int = u8() or (u8() shl BYTE_BITS)

    fun u16Be(): Int = (u8() shl BYTE_BITS) or u8()

    fun u32Le(): Long = u16Le().toLong() or (u16Le().toLong() shl SHORT_BITS)

    fun i32Le(): Int = u32Le().toInt()

    fun bytes(count: Int): ByteArray {
        need(count)
        val result = bytes.copyOfRange(position, position + count)
        position += count
        return result
    }

    fun skip(count: Int) {
        need(count)
        position += count
    }

    /** `count` UTF-16-Einheiten, unverändert übernommen (auch unpaarige Surrogate). */
    fun utf16(count: Int): CharArray {
        need(count * 2)
        return CharArray(count) { u16Le().toChar() }
    }

    private fun need(count: Int) {
        if (count < 0 || count > remaining) {
            violation(ProtocolFailure.TRUNCATED, "$count Byte ab Position $position, $remaining vorhanden")
        }
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val BYTE_BITS = 8
        const val SHORT_BITS = 16
    }
}
