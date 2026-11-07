package io.github.pt9912.tdswire.observability

import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import java.util.concurrent.atomic.AtomicLong

/**
 * Die Schlüssel des Schnappschusses (`SPEC-017` bis `SPEC-019`, Abschnitt 5 der Spezifikation). Die Menge ist fest und
 * unabhängig von SQL-Text, Anmeldekennung und Parameterwerten: kein Schlüssel entsteht aus Eingabe (Kardinalität).
 */
internal object MetricKeys {
    const val SESSIONS_ACTIVE = "sessions_active"
    const val LOGINS_SUCCEEDED = "logins_succeeded_total"
    const val LOGINS_FAILED = "logins_failed_total"
    const val REQUESTS = "requests_total"
    const val LATENCY_P50 = "request_latency_p50_ms"
    const val LATENCY_P95 = "request_latency_p95_ms"
    const val LATENCY_P99 = "request_latency_p99_ms"
    const val BACKEND_ACTIVE = "backend_active"
    const val ABORTS = "aborts_total"
    const val PROTOCOL_ERRORS = "protocol_errors_total"
    const val SPANS_OPEN = "spans_open"
}

/**
 * Die Zähler und Messwerte der Bibliothek. Zähler (`*_total`) wachsen seit dem Start des Servers;
 * `sessions_active`, `backend_active` und `spans_open` (ergänzt der [Observer]) sind Momentwerte. Die
 * Latenz-Perzentile gelten für alle Anfragen seit dem Start und sind die Klassengrenzen von [LatencyHistogram], nicht
 * gemessene Dauern. [read] liest die Werte einzeln: bei gleichzeitigem Ende eines Spans können `requests_total` und die
 * Verteilung um einen Eintrag auseinanderliegen.
 *
 * Eine Anfrage zählt als Abbruch, wenn sie mit [SpanOutcome.CANCELLED] oder [SpanOutcome.TIMEOUT] endet, und als
 * Protokollfehler bei [SpanOutcome.PROTOCOL_ERROR]; eine Anmeldung zählt als Fehler bei
 * [SpanOutcome.LOGIN_REJECTED] der Verbindung. Das ist eine Festlegung dieser Bibliothek.
 */
internal class Metrics(private val latency: LatencyHistogram = LatencyHistogram()) {
    private val sessions = AtomicLong()
    private val loginsSucceeded = AtomicLong()
    private val loginsFailed = AtomicLong()
    private val requests = AtomicLong()
    private val backend = AtomicLong()
    private val aborts = AtomicLong()
    private val protocolErrors = AtomicLong()

    fun sessionStarted() {
        sessions.incrementAndGet()
        loginsSucceeded.incrementAndGet()
    }

    fun sessionEnded() {
        sessions.decrementAndGet()
    }

    fun connectionEnded(outcome: SpanOutcome) {
        if (outcome == SpanOutcome.LOGIN_REJECTED) loginsFailed.incrementAndGet()
    }

    fun backendStarted() {
        backend.incrementAndGet()
    }

    fun backendEnded() {
        backend.decrementAndGet()
    }

    fun requestEnded(outcome: SpanOutcome, nanos: Long) {
        requests.incrementAndGet()
        latency.record(nanos)
        when (outcome) {
            SpanOutcome.CANCELLED, SpanOutcome.TIMEOUT -> aborts.incrementAndGet()
            SpanOutcome.PROTOCOL_ERROR -> protocolErrors.incrementAndGet()
            else -> Unit
        }
    }

    /** Die Messwerte zu den Schlüsseln von [MetricKeys]; die Perzentile fehlen, solange keine Anfrage endete. */
    fun read(): Map<String, Long> {
        val values = linkedMapOf(
            MetricKeys.SESSIONS_ACTIVE to sessions.get(),
            MetricKeys.LOGINS_SUCCEEDED to loginsSucceeded.get(),
            MetricKeys.LOGINS_FAILED to loginsFailed.get(),
            MetricKeys.REQUESTS to requests.get(),
            MetricKeys.BACKEND_ACTIVE to backend.get(),
            MetricKeys.ABORTS to aborts.get(),
            MetricKeys.PROTOCOL_ERRORS to protocolErrors.get(),
        )
        latency.percentile(P50)?.let { values[MetricKeys.LATENCY_P50] = it }
        latency.percentile(P95)?.let { values[MetricKeys.LATENCY_P95] = it }
        latency.percentile(P99)?.let { values[MetricKeys.LATENCY_P99] = it }
        return values
    }

    private companion object {
        const val P50 = 50
        const val P95 = 95
        const val P99 = 99
    }
}
