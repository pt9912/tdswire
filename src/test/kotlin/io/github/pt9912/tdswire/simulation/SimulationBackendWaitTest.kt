package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * `WAITFOR DELAY` am Backend (`SPEC-036` Form Z, `ADR-0058` Entscheidung 5): die Wartezeit steuert der Test
 (`ManualPause`), kein Test wartet auf die Wanduhr. Das Ende der Wartezeit ohne Abbruch (Änderungszähler 0 ohne Art)
 * ist Setzung und am Server nicht gemessen; gemessen ist nur der Abbruch eines wartenden Befehls durch ATTENTION.
 */
class SimulationBackendWaitTest {
    private val sql = "WAITFOR DELAY '00:00:10'"
    private val probe = Probe(listOf(personRow(1, "Ada", 36)))

    private fun backend(pause: ManualPause) = SimulationBackend(
        { SimCatalog(listOf(probe.table)) },
        null,
        pause::pause,
    )

    private suspend fun execution(backend: SimulationBackend, sql: String = this.sql): QueryExecution =
        backend.execute(queryRequest(sql))

    /**
     * Happy: der Befehl wartet die Frist des Textes ab (die Uhr bekommt genau diese Dauer), sendet davor kein Ereignis
     * (ein wartender Befehl, kein `ResultStart`) und endet danach mit dem Änderungszähler 0 ohne Art; die Zeilenquelle
     * bleibt ungeöffnet.
     * Wächter: eine Ausführung, die sofort antwortet (der Zustand `RUNNING` hielte nicht), oder eine, die eine andere
     * Dauer wartet.
     * @spec SPEC-036
     * @spec SPEC-029
     */
    @Test
    fun `WAITFOR wartet die Frist ab und endet mit dem Zaehler 0`() = blocking {
        val pause = ManualPause()
        val received = mutableListOf<QueryEvent>()
        val collecting = launch { execution(backend(pause)).events.toList(received) }
        pause.awaitWaiting(10.seconds)
        yield()

        assertEquals(listOf(10.seconds), pause.started)
        assertTrue(received.isEmpty(), "vor dem Ende der Wartezeit kein Ereignis: $received")
        pause.fire(10.seconds)
        collecting.join()
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), received)
        assertEquals(0, probe.opens)
    }

    /**
     * Boundary: die Frist `00:00:00` ruft die Uhr nicht und endet sofort mit dem Zähler 0; die größte Frist reicht die
     * Dauer unverändert weiter.
     * @spec SPEC-036
     */
    @Test
    fun `WAITFOR 00 00 00 wartet nicht`() = blocking {
        val pause = ManualPause()

        val events = execution(backend(pause), "WAITFOR DELAY '00:00:00'").events.toList()

        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), events)
        assertTrue(pause.started.isEmpty())
        val longest = launch(start = CoroutineStart.UNDISPATCHED) {
            execution(backend(pause), "WAITFOR DELAY '23:59:59'").events.toList()
        }
        pause.awaitWaiting(86_399.seconds)
        longest.cancel()
    }

    /**
     * Der Cancel des Backends beendet die Wartezeit sofort und ohne Ereignis: der Strom endet leer, die Uhr ist
     * abgebrochen (keine wartende Frist bleibt zurück), und ein zweiter Cancel oder ein Cancel einer fremden Anfrage
     * ändert nichts. Der Protokollkern beantwortet das Ende mit dem Paar `DONE[ERROR]`, `DONE[ATTN]`.
     * Wächter: ein `cancel()`, das die Registrierung nicht findet (dann hinge der Befehl bis zum Ende der Frist).
     * @spec SPEC-029
     * @spec TDS-FA-07
     */
    @Test
    fun `cancel beendet die Wartezeit sofort und ohne Ereignis`() = blocking {
        val pause = ManualPause()
        val backend = backend(pause)
        val received = mutableListOf<QueryEvent>()
        val collecting = launch { execution(backend).events.toList(received) }
        pause.awaitWaiting(10.seconds)

        backend.cancel("s1", "fremd")
        yield()
        assertTrue(pause.isWaiting(10.seconds), "der Cancel einer fremden Anfrage trifft nicht")
        backend.cancel("s1", "r1")
        collecting.join()
        backend.cancel("s1", "r1")

        assertTrue(received.isEmpty(), "$received")
        assertFalse(pause.isWaiting(10.seconds), "die Uhr ist abgebrochen")
    }

    /**
     * Der Abbruch des Sammlers (der Protokollkern bricht die Coroutine des Laufs ab, ATTENTION und Frist) beendet die
     * Wartezeit ebenso; `close()` danach ist ein Erfolg, und nach `close()` vor dem Sammeln wartet die Ausführung
     * nicht.
     * @spec SPEC-029
     */
    @Test
    fun `Abbruch des Sammlers und close beenden die Wartezeit`() = blocking {
        val pause = ManualPause()
        val backend = backend(pause)
        val running = execution(backend)
        val collecting: Job = launch { running.events.toList() }
        pause.awaitWaiting(10.seconds)

        collecting.cancel()
        collecting.join()
        running.close()

        assertFalse(pause.isWaiting(10.seconds))
        val closed = execution(backend)
        closed.close()
        assertTrue(closed.events.toList().isEmpty())
        assertEquals(1, pause.started.size, "nach close() wird nicht mehr gewartet")
    }

    /**
     * Zwei wartende Anfragen sind unabhängig: der Cancel der einen lässt die andere warten und enden.
     * @spec SPEC-029
     */
    @Test
    fun `der Cancel trifft nur die eigene Anfrage`() = blocking {
        val pause = ManualPause()
        val backend = backend(pause)
        val first = mutableListOf<QueryEvent>()
        val second = mutableListOf<QueryEvent>()
        val one = launch { backend.execute(queryRequest(sql, requestId = "r1")).events.toList(first) }
        val two = launch { backend.execute(queryRequest(sql, requestId = "r2")).events.toList(second) }
        while (pause.started.size < 2) yield()

        backend.cancel("s1", "r1")
        one.join()
        assertTrue(two.isActive)
        pause.fire(10.seconds)
        two.join()

        assertTrue(first.isEmpty())
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), second)
    }

    /**
     * Die Registrierung der wartenden Ausführung unter Sitzung und Anfrage (die `cancel` findet) endet mit jedem Ende
     * der Ausführung: nach dem Ablauf der Wartezeit, nach dem Cancel, nach dem Abbruch des Sammlers und nach `close()`
     * ohne Sammeln. Beobachtet wird die Zahl der Registrierungen des Backends, nicht ein Folgeverhalten von `cancel`.
     * Wächter: eine Ausführung, die sich nicht austrägt (dann bliebe je `WAITFOR`-Anfrage ein Eintrag im Backend
     * zurück), geprüft je Weg: `onCompletion` des Stroms (Ablauf, Cancel, Abbruch) und `close()`.
     * @spec SPEC-029
     */
    @Test
    fun `die Registrierung endet mit Ablauf Cancel Abbruch und close`() = blocking {
        val pause = ManualPause()
        val backend = backend(pause)
        assertEquals(0, backend.registrations)

        val ended = launch { execution(backend).events.toList() }
        pause.awaitWaiting(10.seconds)
        assertEquals(1, backend.registrations, "die wartende Anfrage ist registriert")
        pause.fire(10.seconds)
        ended.join()
        assertEquals(0, backend.registrations, "nach dem Ablauf der Wartezeit")

        val cancelled = launch { execution(backend).events.toList() }
        pause.awaitWaiting(10.seconds)
        assertEquals(1, backend.registrations)
        backend.cancel("s1", "r1")
        cancelled.join()
        assertEquals(0, backend.registrations, "nach dem Cancel")

        val aborted = launch { execution(backend).events.toList() }
        pause.awaitWaiting(10.seconds)
        assertEquals(1, backend.registrations)
        aborted.cancel()
        aborted.join()
        assertEquals(0, backend.registrations, "nach dem Abbruch des Sammlers")

        val unused = execution(backend)
        assertEquals(1, backend.registrations, "ausgeführt, noch nicht gesammelt")
        unused.close()
        assertEquals(0, backend.registrations, "nach close() ohne Sammeln")
    }

    /**
     * Das Backend mit der öffentlichen Signatur wartet mit der Uhr der Coroutinen (`delay`): eine Frist von zehn
     * Sekunden endet nicht von selbst, und der Cancel beendet sie. Der Test wartet die Zeit nicht ab.
     * Wächter: eine Voreinstellung der Uhr, die nicht wartet (dann kämen Ereignisse vor dem Cancel).
     * @spec SPEC-036
     * @spec SPEC-029
     */
    @Test
    fun `die Voreinstellung der Uhr wartet wirklich und der Cancel beendet sie`() = blocking {
        val backend = SimulationBackend(SimCatalog(emptyList()))
        val received = mutableListOf<QueryEvent>()
        val collecting = launch { execution(backend).events.toList(received) }
        repeat(HOPS) { yield() }

        backend.cancel("s1", "r1")
        collecting.join()

        assertTrue(received.isEmpty(), "$received")
    }

    private companion object {
        const val HOPS = 8
    }
}

/**
 * Eine gesteuerte Uhr für das Backend: [pause] wartet, bis der Test die Dauer mit [fire] auslöst; es läuft keine echte
 * Zeit. [started] nennt jede angeforderte Wartezeit in der Reihenfolge der Anforderung.
 */
internal class ManualPause {
    private class Waiter(val duration: Duration, val gate: CompletableDeferred<Unit>)

    private val waiters = mutableListOf<Waiter>()

    val started = mutableListOf<Duration>()

    suspend fun pause(duration: Duration) {
        val waiter = Waiter(duration, CompletableDeferred())
        started += duration
        waiters += waiter
        try {
            waiter.gate.await()
        } finally {
            waiters -= waiter
        }
    }

    fun isWaiting(duration: Duration): Boolean = waiters.any { it.duration == duration }

    /** Gibt die Ausführung ab, bis die Wartezeit [duration] angefordert ist. */
    suspend fun awaitWaiting(duration: Duration) {
        while (!isWaiting(duration)) yield()
    }

    /** Löst die Wartezeit [duration] aus; sie muss angefordert sein. */
    fun fire(duration: Duration) {
        waiters.first { it.duration == duration }.gate.complete(Unit)
    }
}
