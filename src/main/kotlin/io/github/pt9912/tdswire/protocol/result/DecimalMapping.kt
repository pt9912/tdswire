package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import java.math.BigDecimal

/**
 * Abbildung von Decimal (`SPEC-003`): ein [BigDecimal] wird nie gerundet. Er ist abbildbar, wenn er
 * ohne Verlust von Stellen auf den Scale der Spalte passt (nachlaufende Nullen sind kein Verlust) und
 * höchstens `precision - scale` Stellen vor dem Komma hat; der abgebildete Wert trägt genau den Scale
 * der Spalte. Abgewiesen wird vor jeder Skalierung, damit ein Wert wie `1E+999999999` keinen Speicher
 * bindet.
 */
internal object DecimalMapping {
    fun value(type: TdsType.DecimalType, raw: Any): ProtocolResult<TdsValue> {
        val number = (raw as? BigDecimal)?.stripTrailingZeros()
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
