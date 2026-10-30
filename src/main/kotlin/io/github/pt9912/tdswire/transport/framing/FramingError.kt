package io.github.pt9912.tdswire.transport.framing

/** Fehlerkategorie nach `SPEC-011`; der Paket-Codec kennt nur den Protokollfehler. */
enum class FramingErrorCategory {
    TDS_PROTOCOL_ERROR,
}

/** Grund, aus dem der Paket-Codec Eingabe oder Auftrag abweist. */
enum class FramingFailure {
    HEADER_TRUNCATED,
    UNKNOWN_PACKET_TYPE,
    LENGTH_BELOW_HEADER,
    LENGTH_ABOVE_PACKET_LIMIT,
    MESSAGE_ABOVE_LIMIT,
    PACKET_TYPE_CHANGED,
    MESSAGE_TRUNCATED,
    INVALID_PACKET_SIZE,
}

/**
 * Ergebnisfehler des Paket-Codecs. [detail] nennt Längen und Typen, nie Nutzdaten.
 * Festlegung: jeder Grund hat die Kategorie `TDS_PROTOCOL_ERROR`, auch die Überschreitung der
 * Nachrichtengrenze, für die `SPEC-007` keine eigene Kategorie nennt.
 */
data class FramingError(val failure: FramingFailure, val detail: String) {
    val category: FramingErrorCategory get() = FramingErrorCategory.TDS_PROTOCOL_ERROR
}

/** Ausgang einer Codec-Operation: ein Wert oder ein [FramingError], nie eine Ausnahme. */
sealed interface FramingResult<out T> {
    data class Ok<out T>(val value: T) : FramingResult<T>

    data class Failed(val error: FramingError) : FramingResult<Nothing>
}
