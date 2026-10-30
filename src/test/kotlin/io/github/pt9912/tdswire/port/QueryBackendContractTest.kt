package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Prüft die Pflichten des Backend-Ports, die ohne Sequenzierer prüfbar sind, an einem skriptbaren
 * Test-Backend ([ScriptedBackend]). Belegt ist damit, dass der Port diese Zusagen ausdrücken lässt
 * und das Test-Backend sie erfüllt; keine Aussage über blockierende oder echte Backends.
 */
class QueryBackendContractTest {
    private val request = QueryRequest(
        sessionId = "s1",
        requestId = "r1",
        sql = "SELECT 1",
        parameters = listOf(QueryParameter("p", SqlType.Int32, 7)),
        deadlineEpochMillis = null,
    )
    private val columns = listOf(
        ResultColumn("id", SqlType.Int32, nullable = false),
        ResultColumn("name", SqlType.UnicodeText, nullable = true),
    )

    /**
     * Happy: ein Ergebnis aus fertiger Liste kommt vollständig und in Skript-Reihenfolge beim
     * Leser an, und das Backend sieht nur den [QueryRequest] aus `domain`.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Ergebnis aus fertiger Liste kommt vollstaendig an`() = runBlocking {
        val script = listOf(
            QueryEvent.ResultStart(columns),
            QueryEvent.Row(listOf(1, "a")),
            QueryEvent.Row(listOf(2, null)),
            QueryEvent.ResultEnd(2),
        )
        val backend = ScriptedBackend.ofList(*script.toTypedArray())

        val events = backend.execute(request).events.toList()

        assertEquals(script, events)
        assertEquals(4, backend.delivered)
        assertEquals(listOf(request), backend.requests)
    }

    /**
     * Boundary: ein leeres Ergebnis trägt seine Metadaten und keine Zeile.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Leeres Ergebnis traegt Metadaten und keine Zeile`() = runBlocking {
        val backend = ScriptedBackend.ofList(QueryEvent.ResultStart(columns), QueryEvent.ResultEnd(0))

        val events = backend.execute(request).events.toList()

        assertEquals(columns, assertIs<QueryEvent.ResultStart>(events.first()).columns)
        assertTrue(events.none { it is QueryEvent.Row })
        assertEquals(QueryEvent.ResultEnd(0), events.last())
    }

    /**
     * Negative: ein Fehler nach Zeilen ist ein `Failure` als letztes Ereignis; die bereits
     * gelieferten Zeilen bleiben davor erhalten.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Fehler nach Zeilen ist ein Failure-Ereignis im Strom`() = runBlocking {
        val failure = QueryEvent.Failure("X1", "Zeilenquelle gestoert")
        val backend = ScriptedBackend.ofList(
            QueryEvent.ResultStart(columns),
            QueryEvent.Row(listOf(1, "a")),
            failure,
        )

        val events = backend.execute(request).events.toList()

        assertEquals(failure, events.last())
        assertEquals(1, events.count { it is QueryEvent.Row })
    }

    /**
     * Negative: ein Fehler vor dem ersten Ergebnis ist ein `Failure` ohne vorangehendes `ResultStart`.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Fehler vor dem Ergebnis ist ein Failure ohne ResultStart`() = runBlocking {
        val failure = QueryEvent.Failure("X2", "Syntaxfehler")
        val backend = ScriptedBackend.ofList(failure)

        assertEquals(listOf<QueryEvent>(failure), backend.execute(request).events.toList())
    }

    /**
     * Negative, Fehlerform beim Start (Setzung, `QueryBackend.execute`): ein Backend, dessen Start
     * scheitert, wirft aus `execute` nicht; der Fehler steht als einziges `Failure` im Strom.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Fehler beim Start wirft nicht und erscheint als Failure im Strom`() = runBlocking {
        val failure = QueryEvent.Failure("X3", "Backend nicht erreichbar")
        val backend = ScriptedBackend.failingAtStart(failure)

        val execution = backend.execute(request)

        assertEquals(listOf<QueryEvent>(failure), execution.events.toList())
    }

    /**
     * Metadaten vor der ersten Zeile, auch bei einem Generator als Quelle.
     *
     * @spec SPEC-002
     */
    @Test
    fun `ResultStart kommt vor der ersten Zeile`() = runBlocking {
        val backend = ScriptedBackend.generating(rows = 5)

        val events = backend.execute(request).events.toList()

        val firstRow = events.indexOfFirst { it is QueryEvent.Row }
        assertTrue(firstRow > 0, "erste Zeile an Index $firstRow")
        assertIs<QueryEvent.ResultStart>(events[0])
        assertEquals(5, events.count { it is QueryEvent.Row })
    }

    /**
     * Das Test-Backend erzeugt nur so viele Zeilen, wie der Leser abnimmt: nach `ResultStart` und
     * drei Zeilen hat der Generator drei Zeilen erzeugt. Belegt die Eigenschaft des Test-Backends,
     * nicht die eines echten.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Generator laeuft nicht ueber die Abnahme hinaus`() = runBlocking {
        val backend = ScriptedBackend.generating(rows = 1_000_000)

        val events = backend.execute(request).events.take(4).toList()

        assertEquals(4, events.size)
        assertEquals(3, backend.rowsPulled)
    }

    /**
     * `close()` erreicht die Zeilenquelle: nach dem Schließen erzeugt der Generator keine weitere
     * Zeile, auch wenn der Strom erneut gelesen wird.
     *
     * @spec SPEC-031
     */
    @Test
    fun `close erreicht die Zeilenquelle`() = runBlocking {
        val backend = ScriptedBackend.generating(rows = 1_000_000)
        val execution = backend.execute(request)
        execution.events.take(3).toList()
        val pulledBeforeClose = backend.rowsPulled

        execution.close()
        execution.events.toList()

        assertEquals(1, backend.closeCalls)
        assertTrue(backend.sourceStopped)
        assertEquals(pulledBeforeClose, backend.rowsPulled)
    }

    /**
     * `cancel()` erreicht die Zeilenquelle während des Lesens: der Strom endet, der Generator
     * erzeugt danach nichts mehr, und der Aufruf trägt Sitzung und Anfrage.
     *
     * @spec SPEC-031
     */
    @Test
    fun `cancel beendet den laufenden Strom an der Zeilenquelle`() = runBlocking {
        val backend = ScriptedBackend.generating(rows = 1_000_000)
        val execution = backend.execute(request)
        val reader = launch { execution.events.toList() }
        while (backend.rowsPulled < 5) yield()

        backend.cancel("s1", "r1")
        withTimeout(WAIT_MILLIS) { reader.join() }
        val pulledAtEnd = backend.rowsPulled
        yield()

        assertEquals(listOf("s1" to "r1"), backend.cancelled)
        assertEquals(pulledAtEnd, backend.rowsPulled)
        assertTrue(pulledAtEnd < 1_000_000)
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
    }
}
