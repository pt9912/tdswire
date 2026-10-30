package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation
import java.math.BigDecimal

/**
 * Die Bytes eines DECIMALN-/NUMERICN-Werts (Layout nach MS-TDS): ein Vorzeichenbyte (1 positiv,
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
        if (integerDigits(number) > type.precision.toLong() - type.scale) {
            violation(ProtocolFailure.VALUE_OUT_OF_RANGE, "Dezimalzahl ueberschreitet Precision ${type.precision}")
        }
        val unscaled = number.setScale(type.scale).unscaledValue()
        val body = ByteArray(TypeInfoWriter.decimalBytes(type.precision))
        body[0] = if (unscaled.signum() < 0) 0 else 1
        val magnitude = unscaled.abs().toByteArray().reversedArray()
        magnitude.copyInto(body, 1, 0, minOf(magnitude.size, body.size - 1))
        return body
    }

    /**
     * Stellen vor dem Komma, ohne zu skalieren: `setScale` auf einen Wert wie `1E+999999999` bindet
     * Rechenzeit und Speicher oder wirft eine Ausnahme, also läuft die Bereichsprüfung davor. Long, weil
     * der Scale eines solchen Wertes den Int-Bereich der Differenz verlässt.
     */
    private fun integerDigits(number: BigDecimal): Long =
        if (number.signum() == 0) 0L else number.precision().toLong() - number.scale()
}
