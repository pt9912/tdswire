package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/** Abbildung von Unicode-Text (`SPEC-003`): Länge in UTF-16-Einheiten und wohlgeformte Surrogatpaare. */
internal object TextMapping {
    /** Wahr, wenn jedes hohe Surrogat von einem niedrigen gefolgt wird und kein niedriges allein steht. */
    fun isWellFormed(text: String): Boolean {
        var pendingHigh = false
        for (unit in text) {
            val valid = if (pendingHigh) unit.isLowSurrogate() else !unit.isLowSurrogate()
            if (!valid) return false
            pendingHigh = !pendingHigh && unit.isHighSurrogate()
        }
        return !pendingHigh
    }

    /**
     * Der leere String ist ein Wert, nicht NULL; mehr als `maxChars` Einheiten oder ein unpaares Surrogat
     * werden abgewiesen.
     */
    fun value(type: TdsType.NVarcharType, raw: Any): ProtocolResult<TdsValue> {
        val text = raw as? String
        return when {
            text == null -> mismatch("String", raw)

            text.length > type.maxChars -> failed(
                ProtocolFailure.TOO_LONG,
                "Text mit ${text.length} UTF-16-Einheiten, Spalte erlaubt ${type.maxChars}",
            )

            !isWellFormed(text) -> failed(ProtocolFailure.VALUE_OUT_OF_RANGE, "Text mit unpaarem Surrogat")

            else -> ProtocolResult.Ok(TdsValue.TextValue(text))
        }
    }
}
