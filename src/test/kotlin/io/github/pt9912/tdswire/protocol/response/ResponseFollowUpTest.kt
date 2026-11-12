package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.FailureScope
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private val FIRST = ServerMessage(3728, 1, 16, "erste")
private val SECOND = ServerMessage(3727, 0, 16, "zweite")

/**
 * Das zweite ERROR nach dem ersten ([MappedError.followUp], `SPEC-031`, `SPEC-036`; am echten Server gemessen als
 * `ERROR ERROR DONE`, Messprotokolle `drop-constraint-d-migrate-2026-11-11` und
 * `standardwert-ersetzen-d-migrate-2026-11-12`). Die Abbildung hier ist fest; die Zuordnung der Codes zu ihren
 * Folge-ERROR steht in `ErrorMapperTest`.
 */
class ResponseFollowUpTest {
    private val withFollowUp = ErrorMapping(
        backend = { MappedError(FIRST, closesConnection = false, followUp = SECOND) },
        typeMapping = FIXED_MAPPING.typeMapping,
        internal = FIXED_MAPPING.internal,
    )

    private fun run(
        mapping: ErrorMapping,
        form: ResponseForm,
        sink: RecordingSink,
        vararg events: QueryEvent,
    ): SequenceOutcome =
        runBlocking { ResponseSequencer(mapping).run(ScriptedBackend.ofList(*events), REQUEST, sink, form) }

    /**
     * Happy: ein Fehler, der den Batch beendet, schreibt beide ERROR in dieser Reihenfolge und danach das DONE mit
     * `ERROR`, Befehl `0x00fd` (gemessen). Wächter: das zweite ERROR fehlt, es steht hinter dem DONE.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein Fehler des Batches schreibt beide ERROR vor das DONE`() {
        val sink = RecordingSink()
        assertEquals(
            SequenceOutcome.FAILED,
            run(withFollowUp, ResponseForm.BATCH, sink, QueryEvent.Failure("x", "m")),
        )
        assertEquals(listOf<Token>(ErrorToken(FIRST), ErrorToken(SECOND), doneBatch(DoneStatus.ERROR)), sink.tokens)
    }

    /**
     * Happy: ein Fehler der Anweisung, nach dem die Antwort weiterläuft, schreibt beide ERROR, und das
     * zurückgehaltene DONE der Anweisung bekommt `MORE` (wie ohne zweites ERROR). Wächter: das zweite ERROR fehlt
     * oder steht hinter dem DONE.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein Fehler der Anweisung schreibt beide ERROR vor das DONE mit MORE`() {
        val sink = RecordingSink()
        val outcome = run(
            withFollowUp,
            ResponseForm.BATCH,
            sink,
            QueryEvent.Failure("x", "m", FailureScope.STATEMENT),
            QueryEvent.UpdateCount(1),
        )
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(
            listOf<Token>(
                ErrorToken(FIRST),
                ErrorToken(SECOND),
                doneBatch(DoneStatus.ERROR or DoneStatus.MORE),
            ),
            sink.tokens.take(3),
        )
    }

    /**
     * Setzung (nicht gemessen, die Messungen sind Batch): im RPC stehen beide ERROR, danach der RETURNSTATUS mit der
     * Nummer des letzten ERROR und das DONEPROC mit `ERROR`. Wächter: der RETURNSTATUS mit der Nummer des ersten.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Im RPC folgt dem zweiten ERROR der RETURNSTATUS mit seiner Nummer`() {
        val sink = RecordingSink()
        run(withFollowUp, ResponseForm.RPC, sink, QueryEvent.Failure("x", "m"))
        assertEquals(
            listOf<Token>(
                ErrorToken(FIRST),
                ErrorToken(SECOND),
                ReturnStatusToken(SECOND.number),
                DoneToken(DoneKind.DONE_PROC, DoneStatus.ERROR, DoneCommand.PROC, 0),
            ),
            sink.tokens,
        )
    }

    /**
     * Boundary: ohne Folge-ERROR bleibt die Antwort ERROR und DONE. Wächter: ein zweites ERROR ohne Abbildung dafür.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ohne Folge-ERROR bleibt es bei einem ERROR`() {
        val sink = RecordingSink()
        run(FIXED_MAPPING, ResponseForm.BATCH, sink, QueryEvent.Failure("x", "m"))
        assertEquals(listOf<Token>(ErrorToken(BACKEND_MESSAGE), doneBatch(DoneStatus.ERROR)), sink.tokens)
    }

    /**
     * Festlegung „synchron“: nimmt die Senke das zweite ERROR nicht an, folgt kein DONE, und der Lauf ist nicht
     * abgeschlossen. Wächter: ein Lauf, der das Ergebnis des zweiten Schreibens ignoriert.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein abgelehntes zweites ERROR schliesst ohne DONE`() {
        val sink = RecordingSink(rejectAt = 2)
        assertEquals(
            SequenceOutcome.CLOSE_WITHOUT_COMPLETION,
            run(withFollowUp, ResponseForm.BATCH, sink, QueryEvent.Failure("x", "m")),
        )
        assertEquals(listOf<Token>(ErrorToken(FIRST)), sink.tokens)
    }
}
