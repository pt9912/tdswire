package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.session.CancelCause

/** Das `outcome` des Spans `tds.query` zum Ausgang des Sequenzierers (`SPEC-018`). */
internal fun SequenceOutcome.spanOutcome(): SpanOutcome = when (this) {
    SequenceOutcome.COMPLETED -> SpanOutcome.OK
    SequenceOutcome.FAILED, SequenceOutcome.FAILED_CLOSE_CONNECTION -> SpanOutcome.ERROR
    SequenceOutcome.CLOSE_WITHOUT_COMPLETION -> SpanOutcome.CLOSED
}

/**
 * Das `outcome` des Spans `tds.query` zum Ende des Laufs (`SPEC-018`). War die Antwort beim Gewinn des Auslösers schon
 * vollständig, zählt ihr Ausgang (die Antwort ist nicht abgebrochen worden); sonst der Auslöser des Abbruchs.
 */
internal fun RunEnd.spanOutcome(): SpanOutcome = when (this) {
    is RunEnd.Finished -> outcome.spanOutcome()

    is RunEnd.Cut -> outcome?.takeIf { it != SequenceOutcome.CLOSE_WITHOUT_COMPLETION }?.spanOutcome()
        ?: when (cause) {
            CancelCause.ATTENTION -> SpanOutcome.CANCELLED
            CancelCause.DEADLINE -> SpanOutcome.TIMEOUT
            CancelCause.SHUTDOWN -> SpanOutcome.SHUTDOWN
        }

    RunEnd.Abandoned -> SpanOutcome.ABANDONED

    RunEnd.Disconnected -> SpanOutcome.DISCONNECTED
}
