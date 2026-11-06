package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.response.ErrorMapping
import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.response.failedDone
import io.github.pt9912.tdswire.protocol.session.AttentionAck
import io.github.pt9912.tdswire.protocol.session.CancelCause
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent.RequestFinished
import io.github.pt9912.tdswire.protocol.session.HandshakeSession.GuardedReply
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/**
 * Das Ende einer Antwort, die ein Auslöser des Abbruchs unterbrochen hat (`ADR-0027` Entscheidung 1 und 2):
 * - ATTENTION, Antwort unterbrochen: genau ein `DONE[ATTN]`, an die laufende Nachricht angehängt, kein ERROR.
 * - Ablauf der Frist, Antwort unterbrochen: ERROR `TDS_TIMEOUT` und DONE mit Fehlerstatus, an die laufende Nachricht
 *   angehängt (Mapping des `ErrorMapper`, Class 16, Verbindung offen).
 * - Ablauf von `SHUTDOWN_GRACE` im geordneten Beenden, Antwort unterbrochen: wie bei ATTENTION genau ein `DONE[ATTN]`
 *   ohne ERROR (`SPEC-044` Nr. 4; die Form ist Festlegung, nicht gemessen); die Verbindung endet danach
 *   (`ADR-0027` Entscheidung 2c).
 * - Die Antwort war schon vollständig, als der Auslöser gewann: sie wird beendet, wie ihr Ausgang es sagt. Ein
 *   ATTENTION wird danach als eigene Nachricht bestätigt („ATTENTION nach Ende der Antwort“, ohne Cancel), eine
 *   abgelaufene Frist ändert nichts, ein Ausgang, der die Verbindung schließt, schließt sie, und im Beenden endet die
 *   Verbindung nach der vollständigen Antwort.
 *
 * Scheitert das Schreiben, ist die Sitzung geschlossen und das Ergebnis `null` (kein Ereignis mehr).
 */
internal class AbortedAnswer(private val errors: ErrorMapper, private val mapping: ErrorMapping) {
    suspend fun conclude(end: RunEnd.Cut, reply: GuardedReply): RequestFinished? {
        val outcome = end.outcome
        return when {
            outcome != null && outcome != SequenceOutcome.CLOSE_WITHOUT_COMPLETION ->
                complete(end.cause, errors.fateOf(outcome), reply)

            end.cause == CancelCause.ATTENTION -> appended(AttentionAck.bytes, reply, connectionOpen = true)

            end.cause == CancelCause.SHUTDOWN -> appended(AttentionAck.bytes, reply, connectionOpen = false)

            else -> timedOut(reply)
        }
    }

    private suspend fun complete(cause: CancelCause, fate: ConnectionFate, reply: GuardedReply): RequestFinished? {
        if (!reply.conclude(null)) return null
        val open = fate == ConnectionFate.OPEN && cause != CancelCause.SHUTDOWN
        val written = RequestFinished(null, connectionOpen = open, replyWritten = true)
        return if (open && cause == CancelCause.ATTENTION) {
            RequestFinished(AttentionAck.message, connectionOpen = true)
        } else {
            written
        }
    }

    private suspend fun timedOut(reply: GuardedReply): RequestFinished? {
        val mapped = mapping.backend(QueryEvent.Failure(ErrorCode.TDS_TIMEOUT.name, ""))
        val encoder = TokenEncoder()
        listOf(ErrorToken(mapped.message), failedDone()).forEach {
            check(encoder.write(it) is ProtocolResult.Ok) { "Fehlerantwort nicht schreibbar" }
        }
        return appended(encoder.toByteArray(), reply, connectionOpen = !mapped.closesConnection)
    }

    private suspend fun appended(tail: ByteArray, reply: GuardedReply, connectionOpen: Boolean): RequestFinished? =
        if (reply.conclude(tail)) RequestFinished(null, connectionOpen, replyWritten = true) else null
}
