package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.protocol.response.HANDLE_OUTPUT_NAME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Stellt dem Strom des [delegate] das Handle von `sp_prepexec` als [QueryEvent.OutputParameter] bei (`SPEC-030`):
 * der Dispatch speist es ein, das Backend kennt weder Handle noch Prepare (`SPEC-002`). Das Ereignis steht vor
 * dem ersten `OutputParameter` des Backends, sonst am Ende des Stroms; der Sequenzierer erkennt es am leeren Namen
 * und schreibt es mit Position 0 und dem Flag-Feld `0x0000`. Davor steht ein [QueryEvent.ReturnStatus] mit dem Wert 0,
 * wenn das Backend keinen gesendet hat (Reihenfolge RETURNSTATUS, RETURNVALUE wie in den Mitschnitten der
 * Referenzinstanz, Zeile `prepare/01/tokenfolge` des Mitschnitt-Laufs). Nach einem [QueryEvent.Failure] des Backends
 * entstehen weder Handle- noch Status-Ereignis.
 *
 * Gemessen an der Referenzinstanz (Mitschnitte `prepare`, 2022 und 2025): das RETURNVALUE des Handles hat den
 * Namen `""`, Typ `Int32`, und folgt auf ein RETURNSTATUS 0; die Stelle bei einem Ausgabewert des Backends ist nicht
 * gemessen. Sendet das Backend einen `ReturnStatus` erst hinter seinem ersten `OutputParameter`, steht der eingespeiste
 * davor und der des Backends ist ein zweiter: ein Reihenfolgeverstoß des Sequenzierers (`SPEC-048`). `execute` und
 * `cancel` gehen unverändert an das [delegate].
 */
internal class HandleReturningBackend(private val delegate: QueryBackend, private val handle: Int) : QueryBackend {
    /** Wahr, sobald das Handle-Ereignis in den Strom gegeben wurde. */
    var returned = false
        private set

    override suspend fun execute(request: QueryRequest): QueryExecution {
        val inner = delegate.execute(request)
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = withHandle(inner.events)

            override suspend fun close() = inner.close()
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = delegate.cancel(sessionId, requestId)

    private fun withHandle(events: Flow<QueryEvent>): Flow<QueryEvent> = flow {
        var statusSeen = false
        var failed = false
        events.collect { event ->
            if (event is QueryEvent.ReturnStatus) statusSeen = true
            if (event is QueryEvent.OutputParameter && !returned) giveHandle(statusSeen)
            if (event is QueryEvent.Failure) failed = true
            emit(event)
        }
        if (!returned && !failed) giveHandle(statusSeen)
    }

    private suspend fun FlowCollector<QueryEvent>.giveHandle(statusSeen: Boolean) {
        returned = true
        if (!statusSeen) emit(QueryEvent.ReturnStatus(HANDLE_STATUS))
        emit(QueryEvent.OutputParameter(HANDLE_NAME, SqlType.Int32, handle))
    }

    companion object {
        /** Der Name des Handle-RETURNVALUE: leer, wie die Referenzinstanz ihn sendet (Mitschnitt `prepare`). */
        const val HANDLE_NAME = HANDLE_OUTPUT_NAME

        /** Der Wert des RETURNSTATUS vor dem Handle (Mitschnitt `prepare`: 0). */
        const val HANDLE_STATUS = 0
    }
}
