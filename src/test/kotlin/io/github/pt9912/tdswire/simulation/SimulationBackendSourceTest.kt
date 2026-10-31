package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests des Lesens aus der Zeilenquelle (`TDS-FA-11.a` Schritte 5 und 7, `SPEC-029`, `SPEC-031`): die Quelle wird
 * nur auf Abnahme gelesen und genau einmal geschlossen; Fehler der Quelle werden zu `TDS_BACKEND_ERROR`.
 * Gemessen wird gegen zählende Zeilenquellen und einen Sammler im Test, nicht gegen den Protokollkern.
 */
class SimulationBackendSourceTest {
    private val rows = listOf(
        personRow(1, "a", 1),
        personRow(2, "b", 2),
        personRow(3, "c", 3),
        personRow(4, "d", 4),
    )

    private val select = "SELECT id FROM person"

    private val sourceFailure = QueryEvent.Failure("TDS_BACKEND_ERROR", "Zeilenquelle fehlgeschlagen")

    /** Eine Zeilenquelle ohne Ende, die bei jeder Abnahme einmal an die Coroutine zurückgibt. */
    private class Endless {
        var pulled = 0
            private set
        var closes = 0
            private set

        val backend = SimulationBackend(
            SimCatalog(
                listOf(
                    SimTable(
                        name = "person",
                        columns = personColumns,
                        primaryKey = listOf("Id"),
                        rows = {
                            object : RowSource {
                                override suspend fun next(): List<Any?> {
                                    yield()
                                    pulled++
                                    return personRow(pulled, "x", pulled)
                                }

                                override fun close() {
                                    closes++
                                }
                            }
                        },
                    ),
                ),
            ),
        )
    }

    /**
     * Happy: `next()` wird nur gerufen, wenn der Sammler die vorige Zeile abgenommen hat. Ein Sammler, der bei jeder
     * Zeile wartet, sieht beim Eintreffen der n-ten Zeile genau n Aufrufe von `next()`.
     * @spec SPEC-031
     */
    @Test
    fun `die Quelle wird nur auf Abnahme gelesen`() = runBlocking {
        val probe = Probe(rows)
        val execution = probe.backend.execute(queryRequest(select))
        val seen = mutableListOf<Int>()

        execution.events.collect {
            if (it is QueryEvent.Row) {
                seen += probe.nexts
                delay(20)
            }
        }
        execution.close()

        assertEquals(listOf(1, 2, 3, 4), seen)
        assertEquals(5, probe.nexts)
    }

    /**
     * Boundary: ein Sammler, der nach `ResultStart` und zwei Zeilen aufhört, hat zwei Zeilen gelesen, nicht mehr; die
     * Quelle ist danach genau einmal geschlossen, auch wenn der Kern `close()` hinterherruft.
     * @spec SPEC-031
     */
    @Test
    fun `ein abbrechender Sammler beendet das Lesen und schliesst die Quelle`() = runBlocking {
        val probe = Probe(rows)
        val execution = probe.backend.execute(queryRequest(select))

        val events = execution.events.take(3).toList()
        execution.close()

        assertEquals(3, events.size)
        assertEquals(2, probe.nexts)
        assertEquals(1, probe.closes)
    }

    /**
     * Happy: nach dem Ende des Stroms ist die Quelle genau einmal geschlossen, auch nachdem der Kern `close()`
     * gerufen hat; die Quelle wird beim Ende nicht noch einmal gelesen.
     * @spec SPEC-029
     * @spec SPEC-031
     */
    @Test
    fun `nach dem Ende ist die Quelle genau einmal geschlossen`() {
        val probe = Probe(rows)

        val events = collectEvents(probe.backend, select)

        assertEquals(QueryEvent.ResultEnd(4), events.last())
        assertEquals(1, probe.opens)
        assertEquals(1, probe.closes)
        assertEquals(5, probe.nexts)
    }

    /**
     * Negative: ein Fehler der Zeilenquelle nach `ResultStart` und zwei Zeilen ist das letzte Ereignis, ein
     * `TDS_BACKEND_ERROR` mit fester Meldung, die den Text der Ausnahme nicht nennt; die Quelle ist genau einmal
     * geschlossen, die gelieferten Zeilen bleiben davor erhalten (die Folge für die Verbindung hält der
     * Sequenzierer).
     * @spec SPEC-031
     */
    @Test
    fun `ein Fehler nach den ersten Zeilen ist das letzte Ereignis`() {
        val probe = Probe(rows, failAfter = 2)

        val events = collectEvents(probe.backend, select)

        assertEquals(4, events.size)
        assertIs<QueryEvent.ResultStart>(events[0])
        assertEquals(listOf<QueryEvent>(QueryEvent.Row(listOf(1)), QueryEvent.Row(listOf(2))), events.subList(1, 3))
        assertEquals(sourceFailure, events[3])
        assertFalse((events[3] as QueryEvent.Failure).message.contains("Geheimnis"))
        assertEquals(1, probe.closes)
    }

    /**
     * Boundary: scheitert schon das erste `next()`, steht `Failure` hinter `ResultStart` ohne Zeile.
     * @spec SPEC-031
     */
    @Test
    fun `ein Fehler beim ersten Lesen steht hinter den Metadaten`() {
        val probe = Probe(rows, failAfter = 0)

        val events = collectEvents(probe.backend, select)

        assertEquals(2, events.size)
        assertIs<QueryEvent.ResultStart>(events[0])
        assertEquals(sourceFailure, events[1])
        assertEquals(1, probe.closes)
    }

    /**
     * Negative: scheitert das Öffnen der Zeilenquelle, ist `Failure` das einzige Ereignis, ohne Metadaten; `execute`
     * wirft nicht, und die Meldung nennt den Text der Ausnahme nicht.
     */
    @Test
    fun `ein Fehler vor ResultStart hat keine Metadaten`() {
        val probe = Probe(rows, openFailure = IllegalStateException("Geheimnis des Öffnens"))

        val events = collectEvents(probe.backend, select)

        assertEquals(listOf<QueryEvent>(sourceFailure), events)
        assertEquals(1, probe.opens)
        assertEquals(0, probe.nexts)
    }

    /**
     * Negative: eine Zeile mit anderer Zahl von Werten als Spalten ist ein Fehler der Quelle, auch wenn die Bedingung
     * die Zelle nicht lesen würde; die Quelle ist geschlossen.
     */
    @Test
    fun `eine Zeile falscher Breite ist ein Fehler der Quelle`() {
        val probe = Probe(listOf(personRow(1, "a", 1), listOf(2, "b")))

        val events = collectEvents(probe.backend, select)

        assertEquals(listOf(QueryEvent.Row(listOf(1))), events.filterIsInstance<QueryEvent.Row>())
        assertEquals(sourceFailure, events.last())
        assertEquals(1, probe.closes)
    }

    /**
     * Boundary: ein Fehler von `close()` ändert das Ergebnis nicht und wirft nicht; die Quelle wurde einmal
     * geschlossen (Festlegung).
     * @spec SPEC-029
     */
    @Test
    fun `ein Fehler von close bleibt ohne Folge`() {
        val probe = Probe(rows, closeFailure = IllegalStateException("zu"))

        val events = collectEvents(probe.backend, select)

        assertEquals(QueryEvent.ResultEnd(4), events.last())
        assertEquals(1, probe.closes)
    }

    /**
     * Negative: ein `Error` der Quelle (hier `OutOfMemoryError`) und die Abbruchausnahme der Coroutine werden nicht
     * zu einem Ereignis; beide verlassen den Strom, und die Quelle ist danach geschlossen.
     * @spec SPEC-031
     */
    @Test
    fun `Error und Abbruch der Quelle werden nicht gefangen`() {
        val memory = Probe(rows, nextFailure = OutOfMemoryError("Testfehler"), failAfter = 1)
        assertFailsWith<OutOfMemoryError> { collectEvents(memory.backend, select) }
        assertEquals(1, memory.closes)

        val opening = Probe(rows, openFailure = OutOfMemoryError("Testfehler"))
        assertFailsWith<OutOfMemoryError> { collectEvents(opening.backend, select) }

        val cancelled = Probe(rows, nextFailure = CancellationException("abgebrochen"), failAfter = 1)
        assertFailsWith<CancellationException> { collectEvents(cancelled.backend, select) }
        assertEquals(1, cancelled.closes)
    }

    /**
     * Happy: `close()` der Ausführung beendet das Lesen vor der nächsten Zeile und schließt die Quelle genau einmal.
     * @spec SPEC-029
     * @spec SPEC-031
     */
    @Test
    fun `close beendet das Lesen und schliesst die Quelle einmal`() = runBlocking {
        val endless = Endless()
        val execution = endless.backend.execute(queryRequest(select))
        val reader = launch { execution.events.collect { } }
        while (endless.pulled < 5) yield()

        execution.close()
        withTimeout(WAIT_MILLIS) { reader.join() }

        assertEquals(1, endless.closes)
        assertStopped(endless)
    }

    /**
     * Happy und Negative: `cancel` mit Sitzung und Anfrage der laufenden Ausführung beendet das Lesen und schließt die
     * Quelle genau einmal; `cancel` einer anderen Anfrage oder Sitzung lässt sie weiterlesen.
     * @spec SPEC-029
     */
    @Test
    fun `cancel trifft nur die benannte Anfrage`() = runBlocking {
        val endless = Endless()
        val execution = endless.backend.execute(queryRequest(select, requestId = "r1"))
        val reader = launch { execution.events.collect { } }
        while (endless.pulled < 5) yield()

        endless.backend.cancel("s1", "r2")
        endless.backend.cancel("s2", "r1")
        val before = endless.pulled
        withTimeout(WAIT_MILLIS) { while (endless.pulled < before + 5) yield() }
        assertTrue(reader.isActive)

        endless.backend.cancel("s1", "r1")
        withTimeout(WAIT_MILLIS) { reader.join() }

        assertEquals(1, endless.closes)
        assertStopped(endless)
    }

    /**
     * Boundary: `close()` vor dem Sammeln öffnet die Quelle nicht mehr; der Strom ist dann leer.
     * @spec SPEC-029
     */
    @Test
    fun `close vor dem Sammeln oeffnet die Quelle nicht`() = runBlocking {
        val probe = Probe(rows)
        val execution = probe.backend.execute(queryRequest(select))

        execution.close()
        val events = execution.events.toList()

        assertEquals(emptyList(), events)
        assertEquals(0, probe.opens)
    }

    private suspend fun assertStopped(endless: Endless) {
        val pulled = endless.pulled
        yield()
        yield()
        assertEquals(pulled, endless.pulled)
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
    }
}
