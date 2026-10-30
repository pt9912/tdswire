package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation
import java.time.LocalDate
import java.time.LocalTime

/**
 * Die Bytes der Datums- und Zeittypen (MS-TDS 2.2.5.5.1.8): DATE sind die Tage seit 0001-01-01 in
 * 3 Byte Little-Endian, TIME die Ticks seit Mitternacht (Einheit `10^-scale` Sekunden) in 3, 4 oder
 * 5 Byte, DATETIME2 die Zeit gefolgt vom Datum.
 */
internal object TemporalValues {
    private const val DAYS_TO_UNIX_EPOCH = 719_162L
    private const val MAX_DAYS = 3_652_058L
    private const val NANOS_EXPONENT = 9
    private const val DATE_BYTES = 3
    private const val RADIX = 10L

    fun dateBytes(value: TdsValue): ByteArray {
        val date = (value as? TdsValue.DateValue ?: ValueSupport.mismatch(value)).value
        return ValueSupport.littleEndian(daysSinceYearOne(date), DATE_BYTES)
    }

    fun timeBytes(scale: Int, value: TdsValue): ByteArray {
        val time = (value as? TdsValue.TimeValue ?: ValueSupport.mismatch(value)).value
        return ticks(scale, time)
    }

    fun dateTime2Bytes(scale: Int, value: TdsValue): ByteArray {
        val stamp = (value as? TdsValue.DateTime2Value ?: ValueSupport.mismatch(value)).value
        val date = ValueSupport.littleEndian(daysSinceYearOne(stamp.toLocalDate()), DATE_BYTES)
        return ticks(scale, stamp.toLocalTime()) + date
    }

    private fun daysSinceYearOne(date: LocalDate): Long {
        val days = date.toEpochDay() + DAYS_TO_UNIX_EPOCH
        if (days !in 0L..MAX_DAYS) {
            violation(ProtocolFailure.VALUE_OUT_OF_RANGE, "Datum ausserhalb 0001-01-01..9999-12-31")
        }
        return days
    }

    private fun ticks(scale: Int, time: LocalTime): ByteArray {
        var unit = 1L
        repeat(NANOS_EXPONENT - scale) { unit *= RADIX }
        val nanos = time.toNanoOfDay()
        if (nanos % unit != 0L) {
            violation(ProtocolFailure.VALUE_OUT_OF_RANGE, "Uhrzeit feiner als Scale $scale")
        }
        return ValueSupport.littleEndian(nanos / unit, TypeInfoWriter.timeBytes(scale))
    }
}
