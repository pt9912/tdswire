package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Eigenschaften des Test-Backends [ScriptedBackend], nicht des Ports: seine Zähler und Protokolle.
 * Sie belegen keine Zusage an ein Backend.
 */
class ScriptedBackendTest {
    private val request = QueryRequest(
        sessionId = "s1",
        requestId = "r1",
        sql = "SELECT 1",
        parameters = listOf(QueryParameter("p", SqlType.Int32, 7)),
        deadlineEpochMillis = null,
    )

    /** Das Test-Backend merkt sich die Anfrage und zählt die gelesenen Ereignisse. */
    @Test
    fun `Backend protokolliert Anfrage und gelesene Ereignisse`() = runBlocking {
        val backend = ScriptedBackend.ofList(QueryEvent.ReturnStatus(0), QueryEvent.UpdateCount(1))

        backend.execute(request).events.toList()

        assertEquals(listOf(request), backend.requests)
        assertEquals(2, backend.delivered)
    }

    /** `inSequence` liefert je Aufruf die nächste Liste und bleibt nach der letzten bei ihr. */
    @Test
    fun `inSequence liefert je Aufruf die naechste Liste`() = runBlocking {
        val first = listOf<QueryEvent>(QueryEvent.UpdateCount(1))
        val second = listOf<QueryEvent>(QueryEvent.UpdateCount(2), QueryEvent.UpdateCount(3))
        val backend = ScriptedBackend.inSequence(first, second)

        val seen = List(3) { backend.execute(request).events.toList() }

        assertEquals(listOf(first, second, second), seen)
    }

    /** Das Test-Backend merkt sich Sitzung und Anfrage von `cancel` und die Zahl der `close`-Aufrufe. */
    @Test
    fun `Backend protokolliert cancel und close`() = runBlocking {
        val backend = ScriptedBackend.generating(rows = 10)

        backend.execute(request).close()
        backend.cancel("s1", "r1")

        assertEquals(1, backend.closeCalls)
        assertEquals(listOf("s1" to "r1"), backend.cancelled)
    }
}
