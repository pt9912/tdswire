package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation
import java.math.BigDecimal

/**
 * Die Bytes eines DECIMALN-/NUMERICN-Werts (MS-TDS 2.2.5.5.1.5): ein Vorzeichenbyte (1 positiv,
 * 0 negativ), dann der Betrag des Wertes ohne Komma als Little-Endian-Zahl, aufgefüllt auf die
 * Länge, die die Precision vorgibt.
 */
internal object DecimalValues {
    fun bytes(type: TdsType.DecimalType, value: TdsValue): ByteArray {
        val number = (value as? TdsValue.DecimalValue ?: ValueSupport.mismatch(value)).value
        if (number.scale() > type.scale) {
            violation(
                ProtocolFailure.VALUE_OUT_OF_RANGE,
                "Dezimalzahl mit mehr Nachkommastellen als Scale ${type.scale}",
            )
        }
        val unscaled = number.setScale(type.scale).unscaledValue()
        if (BigDecimal(unscaled.abs()).precision() > type.precision) {
            violation(ProtocolFailure.VALUE_OUT_OF_RANGE, "Dezimalzahl ueberschreitet Precision ${type.precision}")
        }
        val body = ByteArray(TypeInfoWriter.decimalBytes(type.precision))
        body[0] = if (unscaled.signum() < 0) 0 else 1
        val magnitude = unscaled.abs().toByteArray().reversedArray()
        magnitude.copyInto(body, 1, 0, minOf(magnitude.size, body.size - 1))
        return body
    }
}
