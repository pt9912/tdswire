package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.response.ResponseForm
import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.HandshakeSession
import io.github.pt9912.tdswire.protocol.session.HandshakeSession.GuardedReply

/**
 * Das Ende einer Anfrage nach dem Lauf unter [AbortableRun] (`SPEC-029`, `SPEC-031`), gemeinsam für die Anfragen an das
 * Backend und für die Antwort auf die Freigabe von Handles: aus dem Ende des Laufs wird das Ereignis, das die Anfrage
 * beendet. Hat die Gegenstelle oder ein Schreibfehler die Sitzung schon geschlossen, ist das Ergebnis `null`.
 */
internal class RunConclusion(
    private val live: HandshakeSession,
    private val errors: ErrorMapper,
    private val aborted: AbortedAnswer,
) {
    /** Das Ereignis, das die Anfrage nach dem Lauf [end] beendet; [onOutcome] sieht den Ausgang, wenn es einen gibt. */
    suspend fun conclude(
        end: RunEnd,
        reply: GuardedReply,
        form: ResponseForm,
        onOutcome: suspend (SequenceOutcome) -> Unit = {},
    ): HandshakeEvent.RequestFinished? = when (end) {
        is RunEnd.Finished -> {
            onOutcome(end.outcome)
            val fate = errors.fateOf(end.outcome)
            if (live.context.state == ConnectionState.CLOSED) null else finished(fate, reply)
        }

        is RunEnd.Cut -> {
            onOutcome(end.outcome ?: SequenceOutcome.CLOSE_WITHOUT_COMPLETION)
            if (live.context.state == ConnectionState.CLOSED) null else aborted.conclude(end, reply, form)
        }

        RunEnd.Abandoned -> {
            live.abandon(CloseReason.CANCEL_TIMEOUT)
            null
        }

        RunEnd.Disconnected -> {
            onOutcome(SequenceOutcome.CLOSE_WITHOUT_COMPLETION)
            null
        }
    }

    /** Schließt die Antwort mit dem EOM-Paket ab; scheitert das, schließt die Verbindung ohne weitere Antwort. */
    private suspend fun finished(fate: ConnectionFate, reply: GuardedReply): HandshakeEvent.RequestFinished {
        if (fate == ConnectionFate.CLOSE_WITHOUT_REPLY || !reply.finish()) {
            return HandshakeEvent.RequestFinished(null, connectionOpen = false)
        }
        return HandshakeEvent.RequestFinished(null, connectionOpen = fate == ConnectionFate.OPEN, replyWritten = true)
    }
}
