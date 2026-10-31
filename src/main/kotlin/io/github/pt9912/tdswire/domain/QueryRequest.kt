package io.github.pt9912.tdswire.domain

/**
 * Eine Anfrage an das Backend ohne TDS-Bytes (`SPEC-002`).
 *
 * Offen (ADR-0006): die Semantik von [deadlineEpochMillis] — wer die Frist durchsetzt und was bei
 * Ablauf geschieht. Das Feld wird nur getragen. Festlegung ohne Messung: `null` heißt: keine Frist.
 *
 * Die Textdarstellung nennt vom [sql] nur die Länge: der Text kann Literale und Kennwörter tragen
 * (`TDS-QA-03`).
 */
data class QueryRequest(
    val sessionId: String,
    val requestId: String,
    val sql: String,
    val parameters: List<QueryParameter>,
    val deadlineEpochMillis: Long?,
) {
    override fun toString(): String = "QueryRequest(sessionId=$sessionId, requestId=$requestId, " +
        "sqlChars=${sql.length}, parameters=$parameters, deadlineEpochMillis=$deadlineEpochMillis)"
}

/**
 * Ein typisierter Parameter. [value] ist `null` für NULL; die JVM-Klassen je [SqlType] sind die der Zeilendarstellung
 * (ADR-0013, `ValueMapping`).
 *
 * Die Textdarstellung nennt den Wert nie und den Namen nur als Länge: Parameter sind Nutzerdaten
 * (`TDS-QA-03`).
 */
data class QueryParameter(
    val name: String,
    val type: SqlType,
    val value: Any?,
    val direction: ParameterDirection = ParameterDirection.INPUT,
) {
    override fun toString(): String =
        "QueryParameter(nameChars=${name.length}, type=$type, valueIsNull=${value == null}, direction=$direction)"
}

/** Richtung eines Parameters. */
enum class ParameterDirection { INPUT, OUTPUT, INPUT_OUTPUT }
