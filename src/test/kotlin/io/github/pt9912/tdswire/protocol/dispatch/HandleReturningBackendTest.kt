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
 * Die Einspeisung des Handles in den Strom des Backends (`SPEC-030`, `SPEC-002`) ohne Verbindung. Der leere
 * Name und der RETURNSTATUS 0 davor sind an der Referenzinstanz gemessen (Mitschnitte `prepare`); die Stelle im Strom
 * bei Ausgabewerten des Backends ist Festlegung ohne Messung.
 */
class HandleReturningBackendTest {
    private val request = QueryRequest("s1", "r1", "select 1", emptyList(), null)
    private val handle = QueryEvent.OutputParameter("", SqlType.Int32, 4)
    private val status = QueryEvent.ReturnStatus(0)
    private val output = QueryEvent.OutputParameter("@o", SqlType.Int32, 1)

    private suspend fun events(vararg source: QueryEvent): List<QueryEvent> =
        HandleReturningBackend(ScriptedBackend.ofList(*source), 4).execute(request).events.toList()

    /**
     * Ohne Ausgabewert des Backends steht das Handle am Ende des Stroms; mit Ausgabewerten steht es vor dem
     * ersten, und nur einmal. Vor dem Handle steht ein RETURNSTATUS 0. Wächter: ein Handle am Ende trotz
     * Ausgabewerten, ein Handle je Ausgabewert, ein fehlendes Handle bei leerem Strom, ein fehlender Status.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Das Handle steht hinter einem RETURNSTATUS vor dem ersten Ausgabewert, sonst am Ende`() = blocking {
        val end = QueryEvent.ResultEnd(1)
        assertEquals(listOf(end, status, handle), events(end))
        assertEquals(listOf(status, handle), events())
        assertEquals(listOf(end, status, handle, output, output), events(end, output, output))
        val atEnd = HandleReturningBackend(ScriptedBackend.ofList(end), 4)
        assertEquals(false, atEnd.returned)
        atEnd.execute(request).events.toList()
        assertEquals(true, atEnd.returned)
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

    /**
     * Sendet das Backend selbst einen RETURNSTATUS vor seinem ersten Ausgabewert, bleibt es bei dem einen; der
     * eingespeiste kommt dann nicht dazu. Wächter: ein zweiter RETURNSTATUS, der Status des Backends hinter dem Handle.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein RETURNSTATUS des Backends vor dem Handle wird nicht verdoppelt`() = blocking {
        val own = QueryEvent.ReturnStatus(5)
        assertEquals(listOf(own, handle, output), events(own, output))
        assertEquals(listOf(own, handle), events(own))
    }
}
