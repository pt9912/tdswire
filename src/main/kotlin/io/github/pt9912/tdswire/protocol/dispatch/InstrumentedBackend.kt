package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.telemetry.SessionInstruments
import io.github.pt9912.tdswire.domain.telemetry.Span
import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** Das Backend mit dem Span `backend.execute` (`SPEC-019`); ohne beobachtete Sitzung das Backend. */
internal fun QueryBackend.observed(session: SessionInstruments): QueryBackend =
    if (session === SessionInstruments.NONE) this else InstrumentedBackend(this, session)

/**
 * Hüllt ein [QueryBackend] in den Span `backend.execute` (`SPEC-019`): der Span beginnt mit dem Aufruf von `execute`
 * und endet mit `close` der Ausführung oder, wenn `execute` selbst scheitert, mit dem Fehler. Die Hülle ändert weder
 * Ereignisse noch Reihenfolge noch Abbruch; sie liest den Strom nur mit. Ende des Spans: [SpanOutcome.OK], wenn der
 * Strom ohne Fehlerereignis endete, [SpanOutcome.ERROR] bei einem Fehlerereignis oder einem Fehler des Stroms, sonst
 * [SpanOutcome.CANCELLED] (Abbruch durch `cancel` oder `close` vor dem Ende des Stroms).
 */
private class InstrumentedBackend(private val inner: QueryBackend, private val session: SessionInstruments) :
    QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution {
        val span = session.backend(request.requestId)
        var pending: SpanOutcome? = SpanOutcome.ERROR
        try {
            val execution = inner.execute(request)
            pending = null
            return ObservedExecution(execution, span)
        } catch (cancelled: CancellationException) {
            pending = SpanOutcome.CANCELLED
            throw cancelled
        } finally {
            pending?.let(span::end)
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = inner.cancel(sessionId, requestId)
}

private class ObservedExecution(private val inner: QueryExecution, private val span: Span) : QueryExecution {
    private val failed = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)

    override val events: Flow<QueryEvent> = inner.events
        .onEach { if (it is QueryEvent.Failure) failed.set(true) }
        .onCompletion { cause ->
            when {
                cause == null -> completed.set(true)
                cause !is CancellationException -> failed.set(true)
            }
        }

    override suspend fun close() {
        try {
            inner.close()
        } finally {
            span.end(outcome())
        }
    }

    private fun outcome(): SpanOutcome = when {
        failed.get() -> SpanOutcome.ERROR
        completed.get() -> SpanOutcome.OK
        else -> SpanOutcome.CANCELLED
    }
}
