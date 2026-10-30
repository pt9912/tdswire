package io.github.pt9912.tdswire.protocol.wire

/** Fehlerkategorie nach `SPEC-011` und `SPEC-014`. */
enum class ProtocolErrorCategory {
    TDS_PROTOCOL_ERROR,
    TDS_UNSUPPORTED_TYPE,
}

/**
 * Grund, aus dem der Nachrichten-Codec eine Eingabe oder einen Auftrag abweist. Eingaben, die nicht
 * dem Format folgen, tragen `TDS_PROTOCOL_ERROR`; Werte und Typen, die der Encoder nicht
 * serialisieren kann, tragen `TDS_UNSUPPORTED_TYPE` (Festlegung nach `SPEC-014`).
 */
enum class ProtocolFailure(val category: ProtocolErrorCategory) {
    TRUNCATED(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    INVALID_LENGTH(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    INVALID_OFFSET(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    MISSING_TERMINATOR(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    INVALID_OPTION_LENGTH(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    INVALID_HEADER_BLOCK(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    ODD_TEXT_LENGTH(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    INVALID_TYPE_INFO(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    INVALID_RPC_FORM(ProtocolErrorCategory.TDS_PROTOCOL_ERROR),
    TOO_LONG(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE),
    VALUE_OUT_OF_RANGE(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE),
    TYPE_MISMATCH(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE),
    INVALID_TYPE(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE),
    NO_COLUMN_METADATA(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE),
}

/**
 * Ergebnisfehler des Nachrichten-Codecs. [detail] nennt Offsets, Längen und Namen von Feldern,
 * nie deren Inhalt: ein LOGIN7 trägt Kennung und Passwort.
 */
data class ProtocolError(val failure: ProtocolFailure, val detail: String) {
    val category: ProtocolErrorCategory get() = failure.category
}

/** Ausgang einer Codec-Operation: ein Wert oder ein [ProtocolError], nie eine Ausnahme. */
sealed interface ProtocolResult<out T> {
    data class Ok<out T>(val value: T) : ProtocolResult<T>

    data class Failed(val error: ProtocolError) : ProtocolResult<Nothing>
}

/**
 * Interne Abkürzung des Kontrollflusses: Lese- und Schreibhilfen melden einen Verstoß mit dieser
 * Ausnahme, [guarded] macht an der öffentlichen Grenze daraus einen [ProtocolResult.Failed].
 * Sie trägt keinen Stack-Trace.
 */
internal class ProtocolViolation(val error: ProtocolError) : RuntimeException(error.detail, null, false, false)

internal fun violation(failure: ProtocolFailure, detail: String): Nothing =
    throw ProtocolViolation(ProtocolError(failure, detail))

/** Führt [block] aus und wandelt einen [ProtocolViolation] in ein [ProtocolResult.Failed]. */
internal inline fun <T> guarded(block: () -> T): ProtocolResult<T> = try {
    ProtocolResult.Ok(block())
} catch (violation: ProtocolViolation) {
    ProtocolResult.Failed(violation.error)
}
