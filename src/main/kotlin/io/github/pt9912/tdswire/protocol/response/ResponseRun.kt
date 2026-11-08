package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.FailureScope
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.protocol.result.ColumnMapping
import io.github.pt9912.tdswire.protocol.result.ValueMapping
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.InfoToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/** Die erste Fehlernummer, die der Server selbst vergibt: SQL Server nutzt Nummern ab 50000 für eigene Meldungen. */
internal const val FIRST_OWN_ERROR_NUMBER = 50000

private const val STATEMENT_TERMINATED = 3621

/**
 * Das INFO, das auf den ERROR einer Nachricht des Massenladens folgt (Messprotokoll M-12: Nummer 3621, State 0,
 * Class 0, „The statement has been terminated.“); gemessen für den Schlüsselverstoß, für andere Fehler gesetzt.
 */
internal val BULK_TERMINATED =
    InfoToken(ServerMessage(STATEMENT_TERMINATED, 0, 0, "The statement has been terminated."))

/**
 * Das Abschluss-Token eines Fehlers ohne Fortsetzung; die einzige Stelle, die es bildet (auch für den `ErrorMapper`).
 * Batch: DONE mit `ERROR`, Befehlskennung `0x00c1` bei offenem Ergebnis ([resultOpen]), sonst `0x00fd`. RPC: DONEPROC
 * mit `ERROR` (Messprotokoll W-1, Fehler vor dem ersten Ergebnis).
 */
internal fun failedDone(form: ResponseForm = ResponseForm.BATCH, resultOpen: Boolean = false): DoneToken = when (form) {
    ResponseForm.BATCH ->
        DoneToken(DoneKind.DONE, DoneStatus.ERROR, if (resultOpen) DoneCommand.SELECT else DoneCommand.BATCH, 0)

    ResponseForm.BULK -> DoneToken(DoneKind.DONE, DoneStatus.ERROR, DoneCommand.INSERT_BULK, 0)

    ResponseForm.RPC -> DoneToken(DoneKind.DONE_PROC, DoneStatus.ERROR, DoneCommand.PROC, 0)
}

/**
 * Die Tokens einer Antwort, die vor dem Backend oder außerhalb des Stroms endet: ein ERROR je Meldung und der Abschluss
 * der Form. Im RPC steht vor dem DONEPROC der RETURNSTATUS mit der Nummer der letzten Meldung, wenn sie eine
 * SQL-Server-Nummer ist (unter 50000); sonst keiner (Setzung der Entscheidung zum Abschluss im Fehlerfall).
 */
internal fun errorAnswer(form: ResponseForm, messages: List<ServerMessage>, resultOpen: Boolean = false): List<Token> =
    messages.map { ErrorToken(it) } + errorAnswerTail(form, messages.last(), resultOpen)

/**
 * Die Tokens einer Fehlerantwort nach ihren ERROR: die Meldung [last] ist die letzte der Antwort und bestimmt den
 * RETURNSTATUS der Form RPC; danach der Abschluss der Form. [resultOpen] sagt, dass ein Ergebnis begonnen und nicht
 * abgeschlossen ist (nur der Abschluss der Form BATCH trägt es, [failedDone]).
 */
internal fun errorAnswerTail(form: ResponseForm, last: ServerMessage, resultOpen: Boolean = false): List<Token> {
    val status = last.number.takeIf { form == ResponseForm.RPC && it < FIRST_OWN_ERROR_NUMBER }
    val info = listOfNotNull(BULK_TERMINATED.takeIf { form == ResponseForm.BULK })
    val returned = listOfNotNull(status?.let { ReturnStatusToken(it) })
    return info + returned + failedDone(form, resultOpen)
}

/**
 * Zustand eines Laufs des [ResponseSequencer]: die Spalten des offenen Ergebnisses (`null` ohne
 * offenes Ergebnis), der zurückgehaltene Abschluss, die gesammelten RPC-Ausgaben und der
 * Ausgang. Ein Lauf gehört einer Coroutine und ist nicht threadsicher. Die [form] bestimmt die Art der
 * Abschlüsse und ob `ReturnStatus` und `OutputParameter` abgebildet werden.
 */
internal class ResponseRun(
    private val errors: ErrorMapping,
    private val sink: TokenSink,
    private val form: ResponseForm = ResponseForm.BATCH,
    private val outputs: RpcOutputs = RpcOutputs(CallLayout.PROCEDURE, emptyList()),
) {
    private var outcome: SequenceOutcome? = null
    private var columns: List<ColumnDef>? = null
    private var held: DoneToken? = null
    private val trailer = RpcTrailer(outputs) { write(it) }

    /** Wahr, sobald ein Ergebnis begonnen oder eine Anweisung abgeschlossen ist (RPC: Fehlerbit am DONEINPROC). */
    private var begun = false

    /** Der Reihenfolgeverstoß des Backends als Fehler des Servers (`SPEC-048`). */
    private val violation get() = errors.internal(null)

    val result: SequenceOutcome get() = outcome ?: SequenceOutcome.CLOSE_WITHOUT_COMPLETION

    /** Verarbeitet ein Ereignis; `true` heißt: der Lauf ist beendet, weitere Ereignisse werden nicht gelesen. */
    suspend fun accept(event: QueryEvent): Boolean {
        if (!flushHeld(more = true)) return true
        return when (event) {
            is QueryEvent.ResultStart -> start(event)

            is QueryEvent.Row -> row(event)

            is QueryEvent.ResultEnd -> finish(event.affectedRows, inResult = true)

            is QueryEvent.UpdateCount -> finish(event.affectedRows, inResult = false, event.kind)

            is QueryEvent.Failure -> failure(event)

            is QueryEvent.ReturnStatus ->
                rpcOutput("ReturnStatus") { if (outputs.status(event)) ProtocolResult.Ok(Unit) else null }

            is QueryEvent.OutputParameter -> rpcOutput("OutputParameter") { outputs.value(event) }
        }
    }

    /**
     * `execute()` oder der Strom des Backends hat mit [cause] geendet. Scheitert das Schreiben von ERROR
     * und DONE selbst an einem `OutOfMemoryError`, ist der Strom nicht abschließbar
     * ([SequenceOutcome.CLOSE_WITHOUT_COMPLETION]); der zweite Fehler geht nur an [ErrorMapping.internal].
     */
    suspend fun fault(cause: Throwable) {
        try {
            if (flushHeld(more = true)) report(errors.internal(cause))
        } catch (exhausted: OutOfMemoryError) {
            outcome = SequenceOutcome.CLOSE_WITHOUT_COMPLETION
            errors.internal(exhausted)
        }
    }

    /** Der Strom ist ohne Ausnahme zu Ende; schließt den Lauf ab, falls kein Ausgang feststeht. */
    suspend fun end() {
        if (outcome != null) return
        if (columns != null) {
            report(violation)
            return
        }
        val written = when (form) {
            ResponseForm.RPC -> flushHeld(more = true) && trailer.finish(errorBit = false)

            ResponseForm.BATCH -> write(held ?: DoneToken(DoneKind.DONE, DoneStatus.FINAL, DoneCommand.BATCH, 0))

            ResponseForm.BULK ->
                write(held ?: DoneToken(DoneKind.DONE, DoneStatus.FINAL, DoneCommand.INSERT_BULK, 0))
        }
        held = null
        if (written) outcome = if (trailer.failed) SequenceOutcome.FAILED else SequenceOutcome.COMPLETED
    }

    private suspend fun start(event: QueryEvent.ResultStart): Boolean {
        if (columns != null) return report(violation)
        return when (val mapped = ColumnMapping.map(event.columns)) {
            is ProtocolResult.Failed -> report(errors.typeMapping(mapped.error))

            is ProtocolResult.Ok -> {
                columns = mapped.value
                begun = true
                !write(ColMetadataToken(mapped.value))
            }
        }
    }

    private suspend fun row(event: QueryEvent.Row): Boolean {
        val defs = columns ?: return report(violation)
        return when (val mapped = ValueMapping.row(defs, event.values)) {
            is ProtocolResult.Failed -> report(errors.typeMapping(mapped.error))
            is ProtocolResult.Ok -> !write(RowToken(mapped.value))
        }
    }

    private suspend fun finish(
        affectedRows: Long?,
        inResult: Boolean,
        kind: StatementKind = StatementKind.UNKNOWN,
    ): Boolean {
        if ((columns != null) != inResult) return report(violation)
        columns = null
        begun = true
        val status = if (affectedRows != null) DoneStatus.COUNT else DoneStatus.FINAL
        val doneKind = if (form == ResponseForm.RPC) DoneKind.DONE_IN_PROC else DoneKind.DONE
        val command = when {
            inResult -> DoneCommand.SELECT
            form == ResponseForm.BULK -> DoneCommand.INSERT_BULK
            else -> DoneCommand.forKind(kind)
        }
        held = DoneToken(doneKind, status, command, affectedRows ?: 0)
        return false
    }

    /**
     * `ReturnStatus` oder `OutputParameter` (unter [name]): in der Form BATCH ein Fehler der Typabbildung,
     * in der Form RPC wird das Ereignis in den [outputs] gesammelt, sofern die Reihenfolge stimmt (kein offenes
     * Ergebnis, ein einziger `ReturnStatus`). [collect] liefert `null` bei einem Reihenfolgeverstoß.
     */
    private suspend fun rpcOutput(name: String, collect: () -> ProtocolResult<Unit>?): Boolean = when {
        form != ResponseForm.RPC -> {
            val error = ProtocolError(ProtocolFailure.INVALID_TYPE, "Ereignis $name wird nicht abgebildet")
            report(errors.typeMapping(error))
        }

        columns != null -> report(violation)

        else -> when (val mapped = collect()) {
            null -> report(violation)
            is ProtocolResult.Failed -> report(errors.typeMapping(mapped.error))
            is ProtocolResult.Ok -> false
        }
    }

    private suspend fun flushHeld(more: Boolean): Boolean {
        val done = held ?: return true
        held = null
        return write(if (more) done.copy(status = done.status or DoneStatus.MORE) else done)
    }

    /**
     * Ein [QueryEvent.Failure]. Statement-abbrechend (und ohne Verbindungsende): ERROR schreiben und den Abschluss der
     * Anweisung mit `ERROR` zurückhalten; folgt ein Ereignis, bekommt er `MORE` (Messprotokoll W-2, `DONE` bzw.
     * DONEINPROC `MORE|ERROR`), und der Lauf liest weiter. Sonst beendet [report] den Lauf.
     */
    private suspend fun failure(event: QueryEvent.Failure): Boolean {
        val mapped = errors.backend(event)
        if (event.scope != FailureScope.STATEMENT || mapped.closesConnection) return report(mapped)
        val resultOpen = columns != null
        columns = null
        val written = write(ErrorToken(mapped.message))
        if (written) {
            trailer.record(mapped.message.number, continued = true)
            begun = true
            val rpc = form == ResponseForm.RPC
            val command = when {
                resultOpen -> DoneCommand.SELECT
                rpc -> DoneCommand.NONE
                else -> DoneCommand.BATCH
            }
            held = DoneToken(if (rpc) DoneKind.DONE_IN_PROC else DoneKind.DONE, DoneStatus.ERROR, command, 0)
        }
        return !written
    }

    /**
     * Schreibt ERROR und den Abschluss des Fehlers und beendet den Lauf. Batch: DONE mit `ERROR`. RPC: vor dem ersten
     * Ergebnis DONEPROC mit `ERROR`; danach DONEINPROC `MORE|ERROR` für die betroffene Anweisung und ein DONEPROC ohne
     * Fehlerbit (Messprotokoll W-1 bis W-3).
     */
    private suspend fun report(mapped: MappedError): Boolean {
        val resultOpen = columns != null
        columns = null
        trailer.record(mapped.message.number, continued = false)
        val written = write(ErrorToken(mapped.message)) && when (form) {
            ResponseForm.BATCH -> write(failedDone(form, resultOpen))
            ResponseForm.BULK -> write(BULK_TERMINATED) && write(failedDone(form, resultOpen))
            ResponseForm.RPC -> if (begun) trailer.afterResult(resultOpen) else trailer.finish(errorBit = true)
        }
        if (written) {
            outcome = if (mapped.closesConnection) SequenceOutcome.FAILED_CLOSE_CONNECTION else SequenceOutcome.FAILED
        }
        return true
    }

    private suspend fun write(token: Token): Boolean {
        if (sink.accept(token)) return true
        outcome = SequenceOutcome.CLOSE_WITHOUT_COMPLETION
        return false
    }
}
