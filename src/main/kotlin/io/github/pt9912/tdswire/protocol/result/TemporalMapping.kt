package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Abbildung von Datum und Zeit ohne Zone (`SPEC-003`). Die JVM-Typen sind [LocalDate], [LocalTime] und
 * [LocalDateTime]; ein Wert wird so übernommen, wie er geliefert wird, ohne Zeitzonenumrechnung. Ein
 * Wert mit Zone (`Instant`, `OffsetDateTime`, `ZonedDateTime`) ist ein anderer Typ und wird als
 * Typkonflikt abgewiesen. Die Spalten haben Scale 7, also Auflösung 100 ns; ein feinerer Wert wird
 * abgewiesen, nicht gerundet.
 */
internal object TemporalMapping {
    private const val MIN_YEAR = 1
    private const val MAX_YEAR = 9999
    private const val NANOS_PER_TICK = 100L
    private const val DATE_RANGE = "Datum ausserhalb 0001-01-01..9999-12-31"
    private const val TOO_FINE = "Uhrzeit feiner als Scale 7"

    fun date(raw: Any): ProtocolResult<TdsValue> {
        val date = raw as? LocalDate
        return when {
            date == null -> mismatch("LocalDate", raw)
            !yearInRange(date) -> failed(ProtocolFailure.VALUE_OUT_OF_RANGE, DATE_RANGE)
            else -> ProtocolResult.Ok(TdsValue.DateValue(date))
        }
    }

    fun time(raw: Any): ProtocolResult<TdsValue> {
        val time = raw as? LocalTime
        return when {
            time == null -> mismatch("LocalTime", raw)
            !onTick(time) -> failed(ProtocolFailure.VALUE_OUT_OF_RANGE, TOO_FINE)
            else -> ProtocolResult.Ok(TdsValue.TimeValue(time))
        }
    }

    fun dateTime(raw: Any): ProtocolResult<TdsValue> {
        val stamp = raw as? LocalDateTime
        return when {
            stamp == null -> mismatch("LocalDateTime", raw)
            !yearInRange(stamp.toLocalDate()) -> failed(ProtocolFailure.VALUE_OUT_OF_RANGE, DATE_RANGE)
            !onTick(stamp.toLocalTime()) -> failed(ProtocolFailure.VALUE_OUT_OF_RANGE, TOO_FINE)
            else -> ProtocolResult.Ok(TdsValue.DateTime2Value(stamp))
        }
    }

    private fun yearInRange(date: LocalDate): Boolean = date.year in MIN_YEAR..MAX_YEAR

    private fun onTick(time: LocalTime): Boolean = time.toNanoOfDay() % NANOS_PER_TICK == 0L
}
