package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/** Gemeinsame Hilfen der Wert-Schreiber: Zahlbereiche, Little-Endian-Bytes, Längenbyte. */
internal object ValueSupport {
    val INT_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    val LONG_RANGE = Long.MIN_VALUE..Long.MAX_VALUE
    private val shortRange = Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
    private val byteRange = 0L..BYTE_MASK.toLong()
    private const val BYTE_MASK = 0xFF
    private const val BYTE_BITS = 8
    private const val SIZE_1 = 1
    private const val SIZE_2 = 2
    private const val SIZE_4 = 4

    const val MAX_BYTE = 255L

    fun inRange(name: String, value: Long, max: Long) {
        if (value < 0 || value > max) {
            violation(ProtocolFailure.VALUE_OUT_OF_RANGE, "$name $value ausserhalb 0..$max")
        }
    }

    fun mismatch(value: TdsValue): Nothing =
        violation(ProtocolFailure.TYPE_MISMATCH, "Wert ${value::class.simpleName} passt nicht zum Typ der Spalte")

    fun integer(value: TdsValue, range: LongRange): Long {
        val number = (value as? TdsValue.IntegerValue)?.value ?: mismatch(value)
        if (number !in range) {
            violation(ProtocolFailure.VALUE_OUT_OF_RANGE, "Ganzzahl ausserhalb ${range.first}..${range.last}")
        }
        return number
    }

    /** Der Wertebereich von INTN der Länge `size`: 1 Byte ohne Vorzeichen (tinyint), sonst mit Vorzeichen. */
    fun rangeOfSize(size: Int): LongRange = when (size) {
        SIZE_1 -> byteRange
        SIZE_2 -> shortRange
        SIZE_4 -> INT_RANGE
        else -> LONG_RANGE
    }

    fun prefixed(out: ByteWriter, bytes: ByteArray) {
        out.u8(bytes.size)
        out.bytes(bytes)
    }

    fun littleEndian(number: Long, count: Int): ByteArray =
        ByteArray(count) { index -> ((number ushr (index * BYTE_BITS)) and BYTE_MASK.toLong()).toByte() }
}
