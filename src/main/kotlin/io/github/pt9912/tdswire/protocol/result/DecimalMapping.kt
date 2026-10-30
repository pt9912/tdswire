package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import java.math.BigDecimal

/**
 * Abbildung von Decimal (`SPEC-003`): ein [BigDecimal] wird nie gerundet. Er ist abbildbar, wenn er
 * ohne Verlust von Stellen auf den Scale der Spalte passt und höchstens `precision - scale` Stellen vor
 * dem Komma hat; der abgebildete Wert trägt genau den Scale der Spalte. Abgewiesen wird vor jeder
 * Skalierung, damit ein Wert wie `1E+999999999` keinen Speicher bindet.
 *
 * Festlegung ohne Messung: eine Mantisse mit mehr als [MAX_MANTISSA_DIGITS] Stellen wird abgewiesen, auch
 * wenn der Wert nach dem Kürzen passen würde, damit eine Eingabe mit Millionen Nullen keine Rechenzeit bindet.
 *
 * Festlegung ohne Messung und ohne Bestätigung des Nutzers: „ohne Verlust“ heißt, dass nachlaufende
 * Nullen nicht zählen (1,500 passt in Scale 2, 1,234 nicht). Die Nutzerentscheidung vom 2026-10-30 sagt
 * „Genauigkeit größer als die Scale: abweisen“; diese Lesart ist eine Auslegung davon.
 */
internal object DecimalMapping {
    /** Grenze der Mantisse vor `stripTrailingZeros`, dessen Aufwand mit der Stellenzahl quadratisch wächst. */
    const val MAX_MANTISSA_DIGITS = 1000

    fun value(type: TdsType.DecimalType, raw: Any): ProtocolResult<TdsValue> {
        val given = raw as? BigDecimal
        if (given != null && given.precision() > MAX_MANTISSA_DIGITS) {
            return failed(
                ProtocolFailure.VALUE_OUT_OF_RANGE,
                "Dezimalzahl mit mehr als $MAX_MANTISSA_DIGITS Stellen der Mantisse",
            )
        }
        val number = given?.stripTrailingZeros()
        return when {
            number == null -> mismatch("BigDecimal", raw)

            number.scale() > type.scale -> failed(
                ProtocolFailure.VALUE_OUT_OF_RANGE,
                "Dezimalzahl mit mehr Nachkommastellen als Scale ${type.scale}",
            )

            integerDigits(number) > type.precision - type.scale -> failed(
                ProtocolFailure.VALUE_OUT_OF_RANGE,
                "Dezimalzahl ueberschreitet Precision ${type.precision}",
            )

            else -> ProtocolResult.Ok(TdsValue.DecimalValue(number.setScale(type.scale)))
        }
    }

    /** Stellen vor dem Komma; Long, weil `precision - scale` bei extremem Scale den Int-Bereich verlässt. */
    private fun integerDigits(number: BigDecimal): Long =
        if (number.signum() == 0) 0L else number.precision().toLong() - number.scale()
}
