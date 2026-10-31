package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.rpc.RpcType
import io.github.pt9912.tdswire.protocol.rpc.RpcTypeKind
import io.github.pt9912.tdswire.protocol.token.TypeInfoWriter
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Die Wertbytes der Dezimal-, Datums- und Zeittypen eines RPC-Parameters (Layout nach MS-TDS), die der
 * Parser unverändert liefert, als JVM-Wert: `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime`
 * (dieselben Klassen wie die Zeilenwerte der Typabbildung). DECIMAL/NUMERIC sind ein Vorzeichenbyte
 * (1 positiv, 0 negativ) und der Betrag als Little-Endian-Zahl; DATE sind die Tage seit 0001-01-01 in
 * 3 Byte; TIME sind die Ticks seit Mitternacht in Einheiten von `10^-scale` Sekunden; DATETIME2 ist die
 * Zeit, gefolgt vom Datum.
 *
 * Ein Wert außerhalb seines Bereichs (Vorzeichenbyte außer 0 und 1, Betrag mit mehr Stellen als die
 * Precision, Tag nach 9999-12-31, Zeit ab 24 Uhr) ist [RpcRejection.VALUE]. Die Grenzen sind
 * Festlegungen ohne Messung.
 */
internal object RpcWireValues {
    private const val DATE_BYTES = 3
    private const val MAX_DAYS = 3_652_058L
    private const val NANOS_EXPONENT = 9
    private const val NANOS_PER_DAY = 86_400_000_000_000L
    private const val RADIX = 10L
    private const val BYTE_MASK = 0xFFL
    private const val BYTE_BITS = 8
    private const val SIGN_NEGATIVE = 0
    private const val SIGN_POSITIVE = 1
    private val dayZero = LocalDate.of(1, 1, 1)

    /** Der JVM-Wert zu den Wertbytes eines Typs aus der Menge der Rohwerte des Parsers. */
    fun decode(type: RpcType, bytes: ByteArray): Any = when (type.kind) {
        RpcTypeKind.DECIMALN, RpcTypeKind.NUMERICN -> decimal(type, bytes)

        RpcTypeKind.DATEN -> date(bytes, 0)

        RpcTypeKind.TIMEN -> time(type.scale, bytes, 0)

        RpcTypeKind.DATETIME2N -> {
            val timeLength = TypeInfoWriter.timeBytes(type.scale)
            LocalDateTime.of(date(bytes, timeLength), time(type.scale, bytes, 0))
        }

        else -> rejectValue()
    }

    private fun decimal(type: RpcType, bytes: ByteArray): BigDecimal {
        val sign = bytes[0].toInt()
        if (sign != SIGN_NEGATIVE && sign != SIGN_POSITIVE) rejectValue()
        val magnitude = BigInteger(1, bytes.copyOfRange(1, bytes.size).reversedArray())
        if (magnitude >= BigInteger.TEN.pow(type.precision)) rejectValue()
        val unscaled = if (sign == SIGN_NEGATIVE) magnitude.negate() else magnitude
        return BigDecimal(unscaled, type.scale)
    }

    private fun date(bytes: ByteArray, offset: Int): LocalDate {
        val days = littleEndian(bytes, offset, DATE_BYTES)
        if (days > MAX_DAYS) rejectValue()
        return dayZero.plusDays(days)
    }

    private fun time(scale: Int, bytes: ByteArray, offset: Int): LocalTime {
        val ticks = littleEndian(bytes, offset, TypeInfoWriter.timeBytes(scale))
        var unit = 1L
        repeat(NANOS_EXPONENT - scale) { unit *= RADIX }
        val nanos = ticks * unit
        if (nanos >= NANOS_PER_DAY) rejectValue()
        return LocalTime.ofNanoOfDay(nanos)
    }

    private fun littleEndian(bytes: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (index in (offset + length - 1) downTo offset) {
            value = (value shl BYTE_BITS) or (bytes[index].toLong() and BYTE_MASK)
        }
        return value
    }

    private fun rejectValue(): Nothing = throw RpcRejected(RpcRejection.VALUE)
}
