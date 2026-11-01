package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stellt dem Strom des [delegate] das Handle von `sp_prepexec` als [QueryEvent.OutputParameter] bei (`SPEC-030`):
 * der Dispatch speist es ein, das Backend kennt weder Handle noch Prepare (`SPEC-002`). Das Ereignis steht vor
 * dem ersten `OutputParameter` des Backends, sonst am Ende des Stroms; damit zählt das Handle als erster
 * Ausgabewert (Ordinal 0). Nach einem [QueryEvent.Failure] des Backends entsteht kein Handle-Ereignis.
 *
 * Festlegung ohne Messung: Name `@handle`, Typ `Int32`; die Stelle im Strom ist nicht an einem echten SQL
 * Server gemessen. `execute` und `cancel` gehen unverändert an das [delegate].
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
        val handleEvent = QueryEvent.OutputParameter(HANDLE_NAME, SqlType.Int32, handle)
        var failed = false
        events.collect { event ->
            if (event is QueryEvent.OutputParameter && !returned) {
                returned = true
                emit(handleEvent)
            }
            if (event is QueryEvent.Failure) failed = true
            emit(event)
        }
        if (!returned && !failed) {
            returned = true
            emit(handleEvent)
        }
    }

    companion object {
        /** Der Name des Handle-RETURNVALUE: Festlegung ohne Messung. */
        const val HANDLE_NAME = "@handle"
    }
}
