package io.github.pt9912.tdswire.domain.telemetry

import java.time.Instant

/**
 * Der Schnappschuss der Metriken (`SPEC-017` bis `SPEC-019`, Abschnitt 5 der Spezifikation): [metrics] ist eine
 * Kopie der Messwerte (spätere Änderungen der Quelle erreichen sie nicht; die Zusage gilt für die Quelle, nicht gegen
 * ein Verändern durch den Leser); Schlüssel und Bedeutung stehen in der Spezifikation.
 */
internal class MetricsSnapshot(at: Instant, metrics: Map<String, Long>) :
    TelemetryEvent(EventKind.METRICS, at, EventLevel.INFO) {
    override val metrics: Map<String, Long> = metrics.toMap()
}

/**
 * Was ein beendeter Span trägt: [span], [outcome] und [durationMs] immer; [requestId] bei `tds.query` und
 * `backend.execute`, [phase] bei `tds.connection`, [requestKind] bei `tds.query`.
 */
internal class SpanFacts(
    val span: SpanKind,
    val outcome: SpanOutcome,
    val durationMs: Long,
    val requestId: String? = null,
    val phase: ConnectionPhase? = null,
    val requestKind: RequestKind? = null,
)

/**
 * Das Ende eines Spans. Verbindung und Sitzung stammen aus [context]. Alle Werte sind feste Marken oder Kennungen,
 * kein SQL-Text, keine Anmeldekennung und kein Parameterwert.
 */
internal class SpanEnded(at: Instant, context: EventContext, facts: SpanFacts) :
    TelemetryEvent(EventKind.SPAN_ENDED, at, EventLevel.DEBUG, context) {
    override val span: String = facts.span.spanName
    override val outcome: String = facts.outcome.label
    override val durationMs: Long = facts.durationMs
    override val requestId: String? = facts.requestId?.let(::bounded)
    override val phase: String? = facts.phase?.label
    override val requestKind: String? = facts.requestKind?.label
}
