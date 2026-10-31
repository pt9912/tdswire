package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Einspeisung des Handles in den Strom des Backends (`SPEC-030`, `SPEC-002`) ohne Verbindung. Name,
 * Typ und Stelle im Strom sind Festlegungen ohne Messung.
 */
class HandleReturningBackendTest {
    private val request = QueryRequest("s1", "r1", "select 1", emptyList(), null)
    private val handle = QueryEvent.OutputParameter("@handle", SqlType.Int32, 4)
    private val output = QueryEvent.OutputParameter("@o", SqlType.Int32, 1)

    private suspend fun events(vararg source: QueryEvent): List<QueryEvent> =
        HandleReturningBackend(ScriptedBackend.ofList(*source), 4).execute(request).events.toList()

    /**
     * Ohne Ausgabewert des Backends steht das Handle am Ende des Stroms; mit Ausgabewerten steht es vor dem
     * ersten, und nur einmal. Wächter: ein Handle am Ende trotz Ausgabewerten, ein Handle je Ausgabewert,
     * ein fehlendes Handle bei leerem Strom.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Das Handle steht vor dem ersten Ausgabewert, sonst am Ende`() = blocking {
        val end = QueryEvent.ResultEnd(1)
        assertEquals(listOf(end, handle), events(end))
        assertEquals(listOf(handle), events())
        assertEquals(listOf(end, handle, output, output), events(end, output, output))
    }

    /**
     * Nach einem Fehler des Backends entsteht kein Handle-Ereignis, auch wenn der Strom danach endet.
     * Wächter: ein Handle hinter dem Failure.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Nach einem Fehler des Backends entsteht kein Handle`() = blocking {
        val failure = QueryEvent.Failure("TDS_BACKEND_ERROR", "x")
        assertEquals(listOf(failure), events(failure))
    }

    /**
     * Anfrage, Abbruch und Schließen gehen unverändert an das Backend.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Anfrage, cancel und close gehen an das Backend`() = blocking {
        val inner = ScriptedBackend.ofList(QueryEvent.ResultEnd(0))
        val wrapped = HandleReturningBackend(inner, 4)
        val execution = wrapped.execute(request)
        wrapped.cancel("s1", "r1")
        execution.close()
        assertEquals(listOf(request), inner.requests)
        assertEquals(listOf("s1" to "r1"), inner.cancelled)
        assertEquals(1, inner.closeCalls)
    }
}
