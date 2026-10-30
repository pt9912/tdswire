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

/** Ein Backend mit einer Zeilenquelle, die eine Zeile je Abnahme erzeugt, samt Zähler dieser Zeilen. */
interface GeneratingBackend {
    val backend: QueryBackend

    /** Zeilen, die die Zeilenquelle bisher erzeugt hat. */
    val rowsPulled: Int
}

/**
 * Die Suite der Zusagen, die jedes Backend hinter [QueryBackend] einhalten muss. Der Port selbst hat
 * keine Logik; diese Suite prüft die Implementierung, die [backend] und [generating] liefern. Geprüft
 * werden: Metadaten vor Zeilen, Fehler als [QueryEvent.Failure] im Strom, kein Wurf aus `execute`
 * beim Start, `close` und `cancel` erreichen die Zeilenquelle, die Zeilenquelle läuft nicht über die
 * Abnahme hinaus. Jede Implementierung leitet von dieser Klasse ab und liefert die zwei Fabriken.
 *
 * Grenze: die Suite sammelt jeden Strom genau einmal (`QueryExecution.events`) und läuft in einem
 * `runBlocking` ohne eigenen Dispatcher; sie sagt nichts über blockierende oder echte Backends
 * (offen, ADR-0006). Eigenschaften eines einzelnen Backends gehören nicht hierher.
 */
abstract class QueryBackendContract {
    /**
     * Ein Backend, dessen Strom genau [script] in dieser Reihenfolge liefert. Ein Skript aus einem
     * einzigen [QueryEvent.Failure] beschreibt einen Fehler beim Start.
     */
    protected abstract fun backend(script: List<QueryEvent>): QueryBackend

    /**
     * Ein Backend mit einem Ergebnis aus einer Spalte und [rows] Zeilen; die Zeilenquelle erzeugt
     * eine Zeile je Abnahme und nach `cancel` oder `close` keine weitere.
     */
    protected abstract fun generating(rows: Int): GeneratingBackend

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
     * Happy: ein Ergebnis kommt vollständig und in Reihenfolge beim Leser an.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Ergebnis kommt vollstaendig und in Reihenfolge an`() = runBlocking {
        val script = listOf(
            QueryEvent.ResultStart(columns),
            QueryEvent.Row(listOf(1, "a")),
            QueryEvent.Row(listOf(2, null)),
            QueryEvent.ResultEnd(2),
        )

        val events = backend(script).execute(request).events.toList()

        assertEquals(script, events)
    }

    /**
     * Boundary: ein leeres Ergebnis trägt seine Metadaten und keine Zeile.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Leeres Ergebnis traegt Metadaten und keine Zeile`() = runBlocking {
        val script = listOf(QueryEvent.ResultStart(columns), QueryEvent.ResultEnd(0))

        val events = backend(script).execute(request).events.toList()

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
        val script = listOf(QueryEvent.ResultStart(columns), QueryEvent.Row(listOf(1, "a")), failure)

        val events = backend(script).execute(request).events.toList()

        assertEquals(failure, events.last())
        assertEquals(1, events.count { it is QueryEvent.Row })
    }

    /**
     * Negative, Fehlerform beim Start (Setzung, `QueryBackend.execute`): ein Backend, dessen Start
     * scheitert, wirft aus `execute` nicht; der Fehler steht als einziges `Failure` im Strom, ohne
     * vorangehendes `ResultStart`.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Fehler beim Start wirft nicht und erscheint als einziges Failure im Strom`() = runBlocking {
        val failure = QueryEvent.Failure("X2", "Backend nicht erreichbar")

        val execution = backend(listOf(failure)).execute(request)

        assertEquals(listOf<QueryEvent>(failure), execution.events.toList())
    }

    /**
     * Metadaten vor der ersten Zeile, auch bei einer Zeilenquelle als Generator.
     *
     * @spec SPEC-002
     */
    @Test
    fun `ResultStart kommt vor der ersten Zeile`() = runBlocking {
        val events = generating(rows = 5).backend.execute(request).events.toList()

        val firstRow = events.indexOfFirst { it is QueryEvent.Row }
        assertTrue(firstRow > 0, "erste Zeile an Index $firstRow")
        assertIs<QueryEvent.ResultStart>(events[0])
        assertEquals(5, events.count { it is QueryEvent.Row })
    }

    /**
     * Die Zeilenquelle erzeugt nur so viele Zeilen, wie der Leser abnimmt: nach `ResultStart` und
     * drei Zeilen hat sie drei Zeilen erzeugt.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Generator laeuft nicht ueber die Abnahme hinaus`() = runBlocking {
        val source = generating(rows = 1_000_000)

        val events = source.backend.execute(request).events.take(4).toList()

        assertEquals(4, events.size)
        assertEquals(3, source.rowsPulled)
    }

    /**
     * `close()` erreicht die Zeilenquelle während des Lesens: der Strom endet, die Zeilenquelle
     * erzeugt danach nichts mehr.
     *
     * @spec SPEC-031
     */
    @Test
    fun `close beendet den laufenden Strom an der Zeilenquelle`() = runBlocking {
        val source = generating(rows = 1_000_000)
        val execution = source.backend.execute(request)
        val reader = launch { execution.events.toList() }
        while (source.rowsPulled < 5) yield()

        execution.close()
        withTimeout(WAIT_MILLIS) { reader.join() }

        assertSourceStopped(source)
    }

    /**
     * `cancel()` erreicht die Zeilenquelle während des Lesens: der Strom endet, die Zeilenquelle
     * erzeugt danach nichts mehr.
     *
     * @spec SPEC-031
     */
    @Test
    fun `cancel beendet den laufenden Strom an der Zeilenquelle`() = runBlocking {
        val source = generating(rows = 1_000_000)
        val execution = source.backend.execute(request)
        val reader = launch { execution.events.toList() }
        while (source.rowsPulled < 5) yield()

        source.backend.cancel("s1", "r1")
        withTimeout(WAIT_MILLIS) { reader.join() }

        assertSourceStopped(source)
    }

    private suspend fun assertSourceStopped(source: GeneratingBackend) {
        val pulledAtEnd = source.rowsPulled
        yield()
        assertEquals(pulledAtEnd, source.rowsPulled)
        assertTrue(pulledAtEnd < 1_000_000)
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
    }
}
