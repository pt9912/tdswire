package io.github.pt9912.tdswire.domain

/**
 * Eine Anfrage an das Backend ohne TDS-Bytes (`SPEC-002`).
 *
 * Offen (ADR-0006): die Semantik von [deadlineEpochMillis] — wer die Frist durchsetzt und was bei
 * Ablauf geschieht. Das Feld wird nur getragen. Festlegung ohne Messung: `null` heißt: keine Frist.
 */
data class QueryRequest(
    val sessionId: String,
    val requestId: String,
    val sql: String,
    val parameters: List<QueryParameter>,
    val deadlineEpochMillis: Long?,
)

/**
 * Ein typisierter Parameter. [value] ist `null` für NULL; die JVM-Typen je [SqlType] sind offen
 * (ADR-0006, Zeilendarstellung).
 */
data class QueryParameter(
    val name: String,
    val type: SqlType,
    val value: Any?,
    val direction: ParameterDirection = ParameterDirection.INPUT,
)

/** Richtung eines Parameters. */
enum class ParameterDirection { INPUT, OUTPUT, INPUT_OUTPUT }
