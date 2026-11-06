package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Das Ende des Beendens (`SPEC-044` Nr. 4 und 5, `SPEC-033`) und die Grenze `MAX_CONNECTIONS` (`SPEC-034`) am
 * Listener mit Test-Handlern und gesteuerter Zeit. Der Abbruch der Anfrage am Backend gehört der Session
 * (`ShutdownDispatchTest`); hier ist belegt, was der Listener ruft, wann er trennt und wann er zurückkehrt.
 */
internal class ListenerAbortTest : ListenerTestBase() {
    private val grace = 7.seconds
    private val cancelTimeout = 3.seconds
    private val shortTimeout = 3_000L

    /** Ein Handler mit laufender Anfrage und angemeldetem Abbruch; [stubborn] ignoriert den Abbruch der Coroutine. */
    private class AbortingHandler(private val answersAbort: Boolean, private val stubborn: Boolean = false) :
        ConnectionHandler {
        val connections = CopyOnWriteArrayList<Connection>()
        val aborts = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        private val aborted = CompletableDeferred<Unit>()

        override suspend fun handle(connection: Connection) {
            connections += connection
            connection.loginCompleted()
            connection.onShutdownGraceExpired {
                aborts.incrementAndGet()
                aborted.complete(Unit)
            }
            connection.requestRunning(true)
            when {
                stubborn -> withContext(NonCancellable) { release.await() }
                answersAbort -> aborted.await()
                else -> awaitCancellation()
            }
        }
    }

    private suspend fun start(
        handler: ConnectionHandler,
        events: RecordingEvents,
        delayer: ManualDelayer,
        shutdownGrace: Duration = grace,
    ): TdsListener {
        val times = ListenerTimes(shutdownGrace = shutdownGrace, delayer = delayer, cancelTimeout = cancelTimeout)
        return startOnLoopback(handler, events, times)
    }

    private suspend fun awaitWaiting(delayer: ManualDelayer, frist: Duration) =
        eventually("Beenden wartet auf $frist") { delayer.pending == listOf(frist) }

    /**
     * Nach Ablauf von `SHUTDOWN_GRACE` ruft der Listener den angemeldeten Abbruch genau einmal und trennt die
     * Verbindung nicht sofort: er wartet `CANCEL_TIMEOUT`. Endet die Verbindung durch den Abbruch, kehrt das
     * Beenden zurück, ohne dass getrennt wurde.
     *
     * @spec SPEC-044
     * @spec SPEC-033
     */
    @Test
    fun `der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab`() = blocking {
        val handler = AbortingHandler(answersAbort = true)
        val events = RecordingEvents()
        val delayer = ManualDelayer()
        val listener = start(handler, events, delayer)
        val client = connectTo(listener.port)
        eventually("Handler läuft") { handler.connections.size == 1 }
        val shutdown = async { listener.shutdown() }
        awaitWaiting(delayer, grace)
        assertEquals(0, handler.aborts.get(), "vor Ablauf der Frist kein Abbruch")
        delayer.releaseAll()
        withTimeout(shortTimeout) { shutdown.await() }
        assertEquals(1, handler.aborts.get())
        assertTrue(events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).isEmpty(), "der Abbruch genügte")
        assertEquals(1, events.closed().size)
        assertTrue(client.awaitClosedByPeer())
    }

    /**
     * Beendet der Abbruch die Verbindung nicht, trennt der Listener nach `CANCEL_TIMEOUT` (gesteuerte Zeit) mit
     * `SHUTDOWN_GRACE_EXPIRED` und kehrt danach zurück; vorher bleibt die Verbindung offen und das Beenden wartet.
     *
     * @spec SPEC-044
     * @spec SPEC-033
     */
    @Test
    fun `der Listener trennt nach CANCEL_TIMEOUT, wenn der Abbruch die Verbindung nicht beendet`() = blocking {
        val handler = AbortingHandler(answersAbort = false)
        val events = RecordingEvents()
        val delayer = ManualDelayer()
        val listener = start(handler, events, delayer)
        val client = connectTo(listener.port)
        eventually("Handler läuft") { handler.connections.size == 1 }
        val shutdown = async { listener.shutdown() }
        awaitWaiting(delayer, grace)
        delayer.releaseAll()
        awaitWaiting(delayer, cancelTimeout)
        assertEquals(1, handler.aborts.get())
        assertTrue(shutdown.isActive, "vor CANCEL_TIMEOUT kehrt das Beenden nicht zurück")
        assertEquals(1, listener.activeConnections)
        assertTrue(events.closed().isEmpty())
        delayer.releaseAll()
        withTimeout(shortTimeout) { shutdown.await() }
        assertEquals(1, events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).size)
        assertTrue(client.awaitClosedByPeer())
    }

    /**
     * Obergrenze des Beendens (Review F-10 von `slice-transport-listener-lebenszyklus`): ein Handler, der den Abbruch
     * seiner Coroutine nicht beachtet, hält das Beenden nicht länger als `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT`
     * (gesteuerte
     * Zeit) und eine kurze Wartezeit auf das Ende der Handler (Festlegung, 250 ms Wanduhr); danach ist die Gruppe
     * beendet und kein Thread des Listeners lebt.
     *
     * @spec SPEC-044
     */
    @Test
    fun `ein Handler, der den Abbruch ignoriert, haelt das Beenden nicht ueber die Obergrenze`() = blocking {
        val before = listenerThreads()
        val handler = AbortingHandler(answersAbort = false, stubborn = true)
        val events = RecordingEvents()
        val delayer = ManualDelayer()
        val listener = start(handler, events, delayer)
        val client = connectTo(listener.port)
        eventually("Handler läuft") { handler.connections.size == 1 }
        try {
            val shutdown = async { listener.shutdown() }
            awaitWaiting(delayer, grace)
            delayer.releaseAll()
            awaitWaiting(delayer, cancelTimeout)
            delayer.releaseAll()
            withTimeout(shortTimeout) { shutdown.await() }
            assertTrue(client.awaitClosedByPeer(), "die Verbindung ist getrennt")
            assertFalse(handler.release.isCompleted)
            assertEquals(1, events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).size, "das Ende ist gemeldet")
            assertEquals(0, listener.activeConnections)
            assertNoNewListenerThreads(before)
        } finally {
            handler.release.complete(Unit)
        }
    }

    /**
     * Ohne angemeldeten Abbruch trennt der Listener beim Ablauf der Frist sofort: es entsteht keine Wartezeit
     * `CANCEL_TIMEOUT`.
     *
     * @spec SPEC-044
     */
    @Test
    fun `ohne angemeldeten Abbruch trennt der Listener beim Ablauf der Frist sofort`() = blocking {
        val handler = ScriptedHandler()
        val events = RecordingEvents()
        val delayer = ManualDelayer()
        val listener = start(handler, events, delayer)
        connectTo(listener.port)
        eventually("Handler läuft") { handler.connections.size == 1 }
        handler.connections[0].requestRunning(true)
        val shutdown = async { listener.shutdown() }
        awaitWaiting(delayer, grace)
        delayer.releaseAll()
        withTimeout(shortTimeout) { shutdown.await() }
        assertEquals(1, events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).size)
        assertEquals(emptyList(), delayer.pending)
    }

    /**
     * Die Verbindung kennt den Beginn des Beendens: [Connection.shutdownBegun] ist davor falsch und danach wahr.
     *
     * @spec SPEC-044
     */
    @Test
    fun `die Verbindung sieht den Beginn des Beendens`() = blocking {
        val handler = AbortingHandler(answersAbort = true)
        val delayer = ManualDelayer()
        val listener = start(handler, RecordingEvents(), delayer)
        connectTo(listener.port)
        eventually("Handler läuft") { handler.connections.size == 1 }
        assertFalse(handler.connections[0].shutdownBegun)
        val shutdown = async { listener.shutdown() }
        awaitWaiting(delayer, grace)
        assertTrue(handler.connections[0].shutdownBegun)
        delayer.releaseAll()
        withTimeout(shortTimeout) { shutdown.await() }
    }
}
