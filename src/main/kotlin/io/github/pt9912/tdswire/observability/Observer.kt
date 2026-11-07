package io.github.pt9912.tdswire.observability

import io.github.pt9912.tdswire.domain.telemetry.ConnectionPhase
import io.github.pt9912.tdswire.domain.telemetry.ConnectionSpan
import io.github.pt9912.tdswire.domain.telemetry.EventContext
import io.github.pt9912.tdswire.domain.telemetry.Instruments
import io.github.pt9912.tdswire.domain.telemetry.MetricsSnapshot
import io.github.pt9912.tdswire.domain.telemetry.RequestKind
import io.github.pt9912.tdswire.domain.telemetry.SessionInstruments
import io.github.pt9912.tdswire.domain.telemetry.Span
import io.github.pt9912.tdswire.domain.telemetry.SpanEnded
import io.github.pt9912.tdswire.domain.telemetry.SpanFacts
import io.github.pt9912.tdswire.domain.telemetry.SpanKind
import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Die Aggregation von Spans und Metriken (`ADR-0020` Frage 2 und 6, `ARC-009`): zählt Verbindungen, Anfragen und
 * Backend-Aufrufe, misst deren Dauer und gibt das Ende jedes Spans sowie den Schnappschuss der Metriken über [emit] als
 * Ereignis weiter. [emit] baut das Ereignis mit dem Zeitpunkt des Zeitgebers der Bibliothek und reicht es an die
 * Senke; es fängt eine `Exception` der Senke, lässt `CancellationException` und `Error` zum Aufrufer laufen und hält
 * den Aufrufer an, solange die Senke blockiert. Die Dauer misst [nanoTime] (monoton). Der Beobachter trifft keine
 * fachliche Entscheidung: die Stellen der Bibliothek nennen ihm das Ende und dessen Marke ([SpanOutcome]).
 *
 * Ein Span endet genau einmal ([Span.end]); [openSpans] zählt die Spans, die begonnen und nicht beendet sind.
 */
internal class Observer(
    private val emit: ((Instant) -> TelemetryEvent?) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
) : Instruments {
    private val metrics = Metrics()
    private val openCount = AtomicInteger()

    /** Die Zahl der Spans, die begonnen und nicht beendet sind. */
    fun openSpans(): Int = openCount.get()

    /** Gibt den Schnappschuss der Metriken als Ereignis `metrics` weiter. */
    fun snapshot() = emit { at ->
        MetricsSnapshot(at, metrics.read() + (MetricKeys.SPANS_OPEN to openSpans().toLong()))
    }

    override fun connection(connectionId: Long): ConnectionSpan = ObservedConnection(connectionId)

    /** Gemeinsame Hülle der drei Spans: Beginn, Ende genau einmal, Dauer. */
    private abstract inner class Tracked : Span {
        private val started = nanoTime()
        private val ended = AtomicBoolean(false)

        init {
            openCount.incrementAndGet()
        }

        final override fun end(outcome: SpanOutcome) {
            if (!ended.compareAndSet(false, true)) return
            val nanos = maxOf(0L, nanoTime() - started)
            try {
                finish(outcome, nanos)
                emit { at -> event(at, outcome, nanos / NANOS_PER_MILLI) }
            } finally {
                openCount.decrementAndGet()
            }
        }

        /** Zählt das Ende in den Metriken. */
        protected abstract fun finish(outcome: SpanOutcome, nanos: Long)

        /** Das Ereignis zum Ende des Spans. */
        protected abstract fun event(at: Instant, outcome: SpanOutcome, durationMs: Long): TelemetryEvent
    }

    private inner class ObservedConnection(private val connectionId: Long) :
        Tracked(),
        ConnectionSpan {
        @Volatile
        private var session: EventContext? = null

        override fun loginAccepted(context: EventContext): SessionInstruments {
            session = context
            metrics.sessionStarted()
            return ObservedSession(context)
        }

        override fun finish(outcome: SpanOutcome, nanos: Long) {
            if (session != null) metrics.sessionEnded()
            metrics.connectionEnded(outcome)
        }

        override fun event(at: Instant, outcome: SpanOutcome, durationMs: Long): TelemetryEvent {
            val context = session
            val phase = if (context == null) ConnectionPhase.HANDSHAKE else ConnectionPhase.SESSION
            val scope = context ?: EventContext(connectionId)
            return SpanEnded(at, scope, SpanFacts(SpanKind.CONNECTION, outcome, durationMs, phase = phase))
        }
    }

    private inner class ObservedSession(private val context: EventContext) : SessionInstruments {
        override fun request(requestId: String, kind: RequestKind): Span = ObservedRequest(context, requestId, kind)

        override fun backend(requestId: String): Span = ObservedBackend(context, requestId)
    }

    private inner class ObservedRequest(
        private val context: EventContext,
        private val requestId: String,
        private val kind: RequestKind,
    ) : Tracked() {
        override fun finish(outcome: SpanOutcome, nanos: Long) = metrics.requestEnded(outcome, nanos)

        override fun event(at: Instant, outcome: SpanOutcome, durationMs: Long): TelemetryEvent =
            SpanEnded(at, context, SpanFacts(SpanKind.QUERY, outcome, durationMs, requestId, requestKind = kind))
    }

    private inner class ObservedBackend(private val context: EventContext, private val requestId: String) : Tracked() {
        init {
            metrics.backendStarted()
        }

        override fun finish(outcome: SpanOutcome, nanos: Long) = metrics.backendEnded()

        override fun event(at: Instant, outcome: SpanOutcome, durationMs: Long): TelemetryEvent =
            SpanEnded(at, context, SpanFacts(SpanKind.BACKEND, outcome, durationMs, requestId))
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
