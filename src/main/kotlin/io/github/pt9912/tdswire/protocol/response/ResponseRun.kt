package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.result.ColumnMapping
import io.github.pt9912.tdswire.protocol.result.ValueMapping
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

private const val CUR_CMD_UNKNOWN = 0

/** Das DONE mit Fehlerstatus hinter jedem ERROR; die einzige Stelle, die es bildet (auch für den `ErrorMapper`). */
internal fun failedDone(): DoneToken = DoneToken(DoneKind.DONE, DoneStatus.ERROR, CUR_CMD_UNKNOWN, 0)

/**
 * Zustand eines Laufs des [ResponseSequencer]: die Spalten des offenen Ergebnisses (`null` ohne
 * offenes Ergebnis), der zurückgehaltene Abschluss und der Ausgang. Ein Lauf gehört einer Coroutine
 * und ist nicht threadsicher.
 */
internal class ResponseRun(private val errors: ErrorMapping, private val sink: TokenSink) {
    private var outcome: SequenceOutcome? = null
    private var columns: List<ColumnDef>? = null
    private var held: DoneToken? = null

    val result: SequenceOutcome get() = outcome ?: SequenceOutcome.CLOSE_WITHOUT_COMPLETION

    /** Verarbeitet ein Ereignis; `true` heißt: der Lauf ist beendet, weitere Ereignisse werden nicht gelesen. */
    suspend fun accept(event: QueryEvent): Boolean {
        if (!flushHeld(more = true)) return true
        return when (event) {
            is QueryEvent.ResultStart -> start(event)
            is QueryEvent.Row -> row(event)
            is QueryEvent.ResultEnd -> finish(event.affectedRows, inResult = true)
            is QueryEvent.UpdateCount -> finish(event.affectedRows, inResult = false)
            is QueryEvent.Failure -> report(errors.backend(event))
            is QueryEvent.ReturnStatus -> unsupported("ReturnStatus")
            is QueryEvent.OutputParameter -> unsupported("OutputParameter")
        }
    }

    /** `execute()` oder der Strom des Backends hat mit [cause] geendet. */
    suspend fun fault(cause: Throwable) {
        if (flushHeld(more = true)) report(errors.internal(cause))
    }

    /** Der Strom ist ohne Ausnahme zu Ende; schließt den Lauf ab, falls kein Ausgang feststeht. */
    suspend fun end() {
        if (outcome != null) return
        if (columns != null) {
            violation()
            return
        }
        val done = held ?: DoneToken(DoneKind.DONE, DoneStatus.FINAL, CUR_CMD_UNKNOWN, 0)
        held = null
        if (write(done)) outcome = SequenceOutcome.COMPLETED
    }

    private suspend fun start(event: QueryEvent.ResultStart): Boolean {
        if (columns != null) return violation()
        return when (val mapped = ColumnMapping.map(event.columns)) {
            is ProtocolResult.Failed -> report(errors.typeMapping(mapped.error))

            is ProtocolResult.Ok -> {
                columns = mapped.value
                !write(ColMetadataToken(mapped.value))
            }
        }
    }

    private suspend fun row(event: QueryEvent.Row): Boolean {
        val defs = columns ?: return violation()
        return when (val mapped = ValueMapping.row(defs, event.values)) {
            is ProtocolResult.Failed -> report(errors.typeMapping(mapped.error))
            is ProtocolResult.Ok -> !write(RowToken(mapped.value))
        }
    }

    private suspend fun finish(affectedRows: Long?, inResult: Boolean): Boolean {
        if ((columns != null) != inResult) return violation()
        columns = null
        val status = if (affectedRows != null) DoneStatus.COUNT else DoneStatus.FINAL
        held = DoneToken(DoneKind.DONE, status, CUR_CMD_UNKNOWN, affectedRows ?: 0)
        return false
    }

    private suspend fun unsupported(name: String): Boolean {
        val error = ProtocolError(ProtocolFailure.INVALID_TYPE, "Ereignis $name wird nicht abgebildet")
        return report(errors.typeMapping(error))
    }

    private suspend fun violation(): Boolean = report(errors.internal(null))

    private suspend fun flushHeld(more: Boolean): Boolean {
        val done = held ?: return true
        held = null
        return write(if (more) done.copy(status = done.status or DoneStatus.MORE) else done)
    }

    /** Schreibt ERROR und DONE mit Fehlerstatus und beendet den Lauf. */
    private suspend fun report(mapped: MappedError): Boolean {
        val written = write(ErrorToken(mapped.message)) && write(failedDone())
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
