package io.github.pt9912.tdswire.stability

import com.sun.management.UnixOperatingSystemMXBean
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.RejectReason
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.protocol.session.RecordingAdapter
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.listener.ConnectionEnd
import io.github.pt9912.tdswire.transport.listener.ListenerEvent
import io.github.pt9912.tdswire.transport.listener.ListenerTestBase
import io.github.pt9912.tdswire.transport.listener.ListenerTimes
import io.github.pt9912.tdswire.transport.listener.ManualDelayer
import io.github.pt9912.tdswire.transport.listener.RecordingEvents
import io.github.pt9912.tdswire.transport.listener.TdsListener
import io.github.pt9912.tdswire.transport.listener.assertNoNewListenerThreads
import io.github.pt9912.tdswire.transport.listener.eventually
import io.github.pt9912.tdswire.transport.listener.listenerThreads
import io.github.pt9912.tdswire.transport.listener.startOnLoopback
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stabilität des Transports und des Anmeldepfads unter Last und unter Fehlverhalten der Clients
 * (`TDS-QA-02`): ein echter [TdsListener] am Loopback, der [DriverHandler] mit dem Handshake-Treiber
 * und JDK-Sockets als Gegenstelle. Es gibt weder Serverprozess noch SQL_BATCH-Pfad; die Aussagen
 * gelten für Listener, Rahmen und Anmeldung.
 */
class ConnectionStormTest : ListenerTestBase() {
    private val pool = Executors.newFixedThreadPool(CLIENT_THREADS)
    private val clients = pool.asCoroutineDispatcher()
    private val uncaught = ConcurrentLinkedQueue<String>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler { thread, error -> uncaught += "${thread.name}: $error" }
    }

    @AfterTest
    fun restoreAndClosePool() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        clients.close()
        pool.shutdownNow()
    }

    private class Run(val handler: DriverHandler, val events: RecordingEvents)

    private fun adapter() = RecordingAdapter {
        if (it.userName == "mallory") {
            AuthResult.Rejected(RejectReason.UNKNOWN_USER)
        } else {
            AuthResult.Accepted(Principal(it.userName))
        }
    }

    private fun openDescriptors(): Long =
        (ManagementFactory.getOperatingSystemMXBean() as UnixOperatingSystemMXBean).openFileDescriptorCount

    /** Führt einen Fall aus; liefert die offen gehaltene Verbindung, falls der Fall sie hält. */
    private suspend fun connected(case: Hostile, port: Int, seed: Long, index: Int): Peer? {
        val steps = case.reset
        if (steps != null) {
            ResetClient.run(port, steps)
            return null
        }
        val peer = Peer.connect(port, Random(seed + index))
        case.run(peer, Random(seed - index))
        if (!case.keepOpen) peer.close()
        return peer.takeIf { case.keepOpen }
    }

    private fun cases(copies: Int, random: Random): List<Hostile> =
        List(copies) { Hostile.entries }.flatten().shuffled(random)

    /**
     * Läuft alle Fälle [copies]-fach gleichzeitig gegen den Listener und prüft danach Ausgänge,
     * Ereignisse und Aufräumen. Hängende Verbindungen laufen erst in die Frist `LOGIN_TIMEOUT`, wenn
     * die gesteuerte Uhr freigegeben wird.
     */
    private suspend fun pass(listener: TdsListener, delayer: ManualDelayer, run: Run, copies: Int, seed: Long) {
        val random = Random(seed)
        run.handler.outcomes.clear()
        run.handler.afterLogin.clear()
        run.events.events.clear()
        val cases = cases(copies, random)
        val hung = cases.count { it.expect.hung }
        val gate = Rendezvous(cases.size)
        run.handler.gate = gate
        val open = coroutineScope {
            cases.mapIndexed { index, case ->
                async<Peer?>(clients) {
                    connected(case, listener.port, seed, index)
                }
            }.awaitAll().filterNotNull()
        }
        eventually("alle Fälle angenommen") { run.events.events.count { it is ListenerEvent.Accepted } == cases.size }
        eventually("alle Fälle ohne Hängende beendet") { run.events.closed().size == cases.size - hung }
        assertEquals(hung, listener.activeConnections, "offene Verbindungen vor Fristablauf")
        eventually("genau die Fristen der $hung hängenden Verbindungen warten") { delayer.pending.size == hung }
        delayer.releaseAll()
        eventually("Fristen beendet alle Hängenden") { listener.activeConnections == 0 }
        open.forEach { assertTrue(it.awaitClosedByServer(), "Hängende Verbindung bleibt offen") }
        open.forEach { it.close() }
        run.handler.gate = null
        assertEquals(cases.size, gate.peak, "gleichzeitig gemessene Handler")
        assertResults(run, cases)
    }

    /**
     * Ein Client, der in die Frist `LOGIN_TIMEOUT` läuft, kann noch einen Lesefehler als Ausgang des
     * Handshakes hinterlassen: der Listener schließt den Kanal vor dem Abbruch des Handlers. Solche
     * Ausgänge werden an der Nummer der Verbindung erkannt (Ende `LOGIN_TIMEOUT`), dürfen nur Lesefehler
     * sein und zählen nicht mit; alle übrigen Ausgänge sind genau die erwarteten.
     */
    private fun handshakeOutcomes(run: Run): List<String> {
        val timedOut = run.events.closed().filter { it.end == ConnectionEnd.LOGIN_TIMEOUT }.map { it.connection }
        val (late, regular) = run.handler.outcomes.entries.partition { it.key in timedOut }
        assertTrue(late.all { it.value == READ_FAILED }, "Ausgang einer Verbindung in der Frist: $late")
        return regular.map { it.value }
    }

    private suspend fun assertResults(run: Run, cases: List<Hostile>) {
        val handshaken = cases.filterNot { it.expect.hung }
        val hung = cases.size - handshaken.size
        assertOutcomes(handshakeOutcomes(run), handshaken.map { it.expect.outcomes }, "Handshake")
        val ready = handshaken.filter { "Ready" in it.expect.outcomes }
        eventually("Ausgänge nach dem Login") { run.handler.afterLogin.size == ready.size }
        assertOutcomes(run.handler.afterLogin, ready.map { it.expect.afterLogin }, "nach dem Login")
        val ends = run.events.closed().groupingBy { it.end }.eachCount()
        assertEquals(handshaken.size, ends[ConnectionEnd.HANDLER_RETURNED] ?: 0, "Handler beendet regulär, $ends")
        assertEquals(hung, ends[ConnectionEnd.LOGIN_TIMEOUT] ?: 0, "Frist, $ends")
        assertEquals(cases.size, run.events.closed().size, "Ereignisse Closed, $ends")
        assertTrue(run.events.closed().none { it.failure != null }, "Handler mit Ausnahme beendet")
    }

    /**
     * 105 Verbindungen aus 21 Fällen (5 je Fall, von 12 Threads geöffnet) sind zugleich im Handler: eine
     * Eintrittssperre hält jeden Handler, bis alle 105 eingetreten sind, und die gemessene Spitze
     * gleichzeitiger Handler ist 105. Die Fälle: gute Anmeldung, zerstückelte Anmeldung, Müll statt PRELOGIN,
     * Längen unter 8 und über der Vor-Login-Grenze, unbekannter Pakettyp, Abbruch im Header, in den
     * Nutzdaten und im LOGIN7, Verbindungsrücksetzung, ungültige PRELOGIN- und LOGIN7-Nutzdaten, abgelehnte
     * Anmeldung, schweigende Clients und Fehler nach dem Login. Der Listener läuft danach weiter, jede
     * Verbindung ist aus dem Register entfernt, kein Handler endete mit einer Ausnahme, kein Thread endete
     * ungefangen, und nach dem Beenden lebt kein Thread des Listeners.
     *
     * Dateideskriptoren: die Zahl offener Deskriptoren des ganzen Prozesses (Clients und Server liegen in
     * derselben JVM; nur Linux) ist nach der Hauptrunde um höchstens [DESCRIPTOR_SLACK] höher als nach der
     * Aufwärmrunde mit 21 Verbindungen. Der Zähler trägt gemessen schon bei einem einzigen zusätzlich
     * offen gehaltenen Deskriptor in der Hauptrunde; Lecks der Aufwärmrunde sind in der Grundlinie enthalten und
     * unsichtbar. Heap und gemeinsame Pools misst der Test nicht.
     * `MAX_CONNECTIONS` ist im Code nicht vorhanden und deshalb nicht Teil des Tests.
     *
     * @spec SPEC-027 SPEC-008 SPEC-005 SPEC-006 SPEC-007 SPEC-046 SPEC-028
     */
    @Test
    fun `Fehlerhafte und getrennte Clients gleichzeitig lassen Listener und gute Verbindungen unberuehrt`() = blocking {
        val threadsBefore = listenerThreads()
        val delayer = ManualDelayer()
        val events = RecordingEvents()
        val handler = DriverHandler(adapter())
        val listener = startOnLoopback(handler, events, ListenerTimes(delayer = delayer))
        val run = Run(handler, events)
        pass(listener, delayer, run, copies = 1, seed = WARM_SEED)
        val baseline = openDescriptors()
        pass(listener, delayer, run, copies = COPIES, seed = SEED)
        eventually("Dateideskriptoren frei (Grundlinie $baseline)") { openDescriptors() <= baseline + DESCRIPTOR_SLACK }
        val after = Peer.connect(listener.port, Random(1))
        after.prelogin()
        after.login()
        after.close()
        listener.shutdown()
        assertNoNewListenerThreads(threadsBefore)
        assertEquals(emptyList(), uncaught.toList(), "ungefangene Ausnahmen in Threads")
    }

    private companion object {
        const val READ_FAILED = "Closed:READ_FAILED"
        const val CLIENT_THREADS = 12
        const val COPIES = 5
        const val SEED = 20261030L
        const val WARM_SEED = 7L
        const val DESCRIPTOR_SLACK = 0
    }
}
