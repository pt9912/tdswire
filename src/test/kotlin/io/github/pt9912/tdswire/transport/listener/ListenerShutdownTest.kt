package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ExecutionException
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Geordnetes Beenden (`SPEC-044`) mit Test-Handlern: die Tests belegen Fristen, Reihenfolge und Trennen
 * des Transports. Eine laufende Anfrage gegen ein Backend und `TDS_SHUTDOWN` gehören der Session und
 * sind hier nicht belegt; „Anfrage läuft“ meldet der Test-Handler selbst.
 */
class ListenerShutdownTest : ListenerTestBase() {
    private val grace = 7.seconds
    private val shortTimeout = 3_000L

    private class Fixture(
        val handler: ScriptedHandler,
        val events: RecordingEvents,
        val delayer: ManualDelayer,
        val listener: TdsListener,
    ) {
        val clients = mutableListOf<SocketChannelAdapter>()

        suspend fun connect(): SocketChannelAdapter {
            val client = connectTo(listener.port)
            clients += client
            eventually("Handler ${clients.size} läuft") { handler.connections.size == clients.size }
            return client
        }

        suspend fun awaitShutdownWaiting(shutdown: Deferred<Unit>, frist: Duration) {
            eventually("Beenden wartet auf die Frist") { delayer.pending == listOf(frist) }
            assertTrue(shutdown.isActive)
        }
    }

    private suspend fun fixture(shutdownGrace: Duration = grace): Fixture {
        val handler = ScriptedHandler()
        val events = RecordingEvents()
        val delayer = ManualDelayer()
        val times = ListenerTimes(shutdownGrace = shutdownGrace, delayer = delayer)
        return Fixture(handler, events, delayer, startOnLoopback(handler, events, times))
    }

    /**
     * Verbindungen ohne laufende Anfrage enden sofort: das Beenden kehrt zurück, ohne dass die Frist
     * abläuft (die Zeit bleibt stehen), jede Verbindung endet mit `SHUTDOWN`, und kein Thread bleibt.
     *
     * @spec SPEC-044
     */
    @Test
    fun `idle connections end at once without waiting for the grace`() = blocking {
        val before = listenerThreads()
        val f = fixture()
        repeat(3) { f.connect() }
        withTimeout(shortTimeout) { f.listener.shutdown() }
        assertEquals(3, f.events.endsOf(ConnectionEnd.SHUTDOWN).size)
        f.clients.forEach { assertTrue(it.awaitClosedByPeer()) }
        assertNoNewListenerThreads(before)
    }

    /**
     * Eine Verbindung mit laufender Anfrage bleibt, bis der Handler „Anfrage beendet“ meldet, aber
     * höchstens `SHUTDOWN_GRACE` (hier 7 s); die Verbindung ohne Anfrage endet sofort, das Beenden
     * kehrt erst nach der letzten zurück.
     *
     * @spec SPEC-044 SPEC-047
     */
    @Test
    fun `a running request keeps its connection until it reports completion`() = blocking {
        val f = fixture()
        f.connect()
        val idle = f.connect()
        f.handler.connections[0].requestRunning(true)
        val shutdown = async { f.listener.shutdown() }
        eventually("die Verbindung ohne Anfrage endet") { f.events.endsOf(ConnectionEnd.SHUTDOWN).size == 1 }
        assertTrue(idle.awaitClosedByPeer())
        f.awaitShutdownWaiting(shutdown, grace)
        assertEquals(1, f.listener.activeConnections)
        f.handler.connections[0].requestRunning(false)
        withTimeout(shortTimeout) { shutdown.await() }
        assertEquals(2, f.events.endsOf(ConnectionEnd.SHUTDOWN).size)
        assertTrue(f.events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).isEmpty())
    }

    /**
     * Endet die Anfrage nicht, trennt der Listener nach Ablauf der Frist (gesteuerte Zeit); vorher
     * bleibt die Verbindung offen, und das Beenden kehrt nicht zurück.
     *
     * @spec SPEC-044 SPEC-047
     */
    @Test
    fun `the grace expiry disconnects a connection whose request does not end`() = blocking {
        val f = fixture()
        val busy = f.connect()
        f.handler.connections[0].requestRunning(true)
        val shutdown = async { f.listener.shutdown() }
        f.awaitShutdownWaiting(shutdown, grace)
        assertEquals(1, f.listener.activeConnections)
        assertTrue(f.events.closed().isEmpty())
        f.delayer.releaseAll()
        withTimeout(shortTimeout) { shutdown.await() }
        assertEquals(1, f.events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).size)
        assertTrue(busy.awaitClosedByPeer())
    }

    /**
     * Mit der Frist 0 endet auch eine Verbindung mit laufender Anfrage sofort, ohne dass eine Frist
     * abgewartet wird.
     *
     * @spec SPEC-044 SPEC-047
     */
    @Test
    fun `a grace of zero disconnects at once`() = blocking {
        val f = fixture(Duration.ZERO)
        f.connect()
        f.handler.connections[0].requestRunning(true)
        withTimeout(shortTimeout) { f.listener.shutdown() }
        assertEquals(1, f.events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).size)
        assertEquals(emptyList(), f.delayer.pending)
    }

    /**
     * Der Listener schließt zuerst: solange eine laufende Anfrage das Beenden aufhält, nimmt der
     * Port keine neue Verbindung mehr an.
     *
     * @spec SPEC-044
     */
    @Test
    fun `no new connection is accepted while the shutdown waits`() = blocking {
        val f = fixture()
        f.connect()
        f.handler.connections[0].requestRunning(true)
        val shutdown = async { f.listener.shutdown() }
        f.awaitShutdownWaiting(shutdown, grace)
        assertFailsWith<ExecutionException> { connectTo(f.listener.port) }
        f.delayer.releaseAll()
        withTimeout(shortTimeout) { shutdown.await() }
    }

    /**
     * Eine zweite Shutdown-Anforderung ist wirkungslos: sie kehrt sofort zurück, setzt keine zweite
     * Frist, beendet die laufende Verbindung nicht und erzeugt kein zweites Ende-Ereignis.
     *
     * @spec SPEC-044
     */
    @Test
    fun `a second shutdown request has no effect`() = blocking {
        val f = fixture()
        f.connect()
        f.handler.connections[0].requestRunning(true)
        val first = async { f.listener.shutdown() }
        f.awaitShutdownWaiting(first, grace)
        withTimeout(shortTimeout) { f.listener.shutdown() }
        assertEquals(listOf(grace), f.delayer.pending)
        assertTrue(first.isActive)
        assertEquals(1, f.listener.activeConnections)
        f.delayer.releaseAll()
        withTimeout(shortTimeout) { first.await() }
        assertEquals(1, f.events.closed().size)
    }

    /**
     * Eine im selben Moment angenommene Verbindung endet sofort: nach dem Beginn des Beendens nimmt
     * die Registrierung keine Verbindung mehr auf, und der Beginn gelingt nur einmal.
     *
     * @spec SPEC-044
     */
    @Test
    fun `registry refuses admission once the shutdown has begun`() {
        val registry = ConnectionRegistry<String>()
        assertTrue(registry.admit(1, "a"))
        assertEquals(listOf("a"), registry.beginShutdown())
        assertFalse(registry.admit(2, "b"))
        assertEquals(1, registry.size)
        assertNull(registry.beginShutdown())
        registry.release(1)
        assertEquals(0, registry.size)
    }

    /**
     * Eine abgewiesene Verbindung wird sofort geschlossen und mit `REFUSED_AT_SHUTDOWN` gemeldet; ihr
     * Handler läuft nie.
     *
     * @spec SPEC-044
     */
    @Test
    fun `a refused connection is closed and reported`() = blocking {
        val (server, peer) = MemoryChannel.pair(MEMORY_BYTES)
        val events = RecordingEvents()
        val registry = ConnectionRegistry<ManagedConnection>()
        val dispatcher = currentCoroutineContext()[ContinuationInterceptor] as CoroutineDispatcher
        val environment = ConnectionEnvironment(Job(), dispatcher, ListenerTimes(), events, registry)
        ManagedConnection(1, server, environment).refuse()
        val expected = ListenerEvent.Closed(1, ConnectionEnd.REFUSED_AT_SHUTDOWN, null)
        assertEquals(listOf<ListenerEvent>(expected), events.events)
        assertTrue(peer.awaitClosedByPeer())
    }

    /**
     * Wird der Aufruf von `shutdown()` abgebrochen, während eine Anfrage die Frist abwartet, räumt er
     * trotzdem auf: die Verbindung ist getrennt und aus dem Listener entfernt, und kein Thread des
     * Listeners lebt, sobald der abgebrochene Aufruf zurück ist (`TDS-QA-02`). Die Verbindung endet durch
     * den Abbruch ihres Scopes mit `SHUTDOWN`, nicht als Handlerfehler.
     *
     * @spec SPEC-044
     */
    @Test
    fun `a cancelled shutdown still releases the connections and the threads`() = blocking {
        val before = listenerThreads()
        val f = fixture()
        val busy = f.connect()
        f.handler.connections[0].requestRunning(true)
        val shutdown = async { f.listener.shutdown() }
        f.awaitShutdownWaiting(shutdown, grace)
        shutdown.cancelAndJoin()
        assertNoNewListenerThreads(before)
        assertTrue(busy.awaitClosedByPeer())
        eventually("Verbindung aufgeräumt") { f.listener.activeConnections == 0 }
        eventually("Ende SHUTDOWN") { f.events.endsOf(ConnectionEnd.SHUTDOWN).size == 1 }
        assertTrue(f.events.endsOf(ConnectionEnd.HANDLER_FAILED).isEmpty())
    }

    /**
     * Weist das Register eine Verbindung ab, schließt der Listener sie sofort, meldet
     * `REFUSED_AT_SHUTDOWN` und startet keinen Handler: die Verdrahtung von `TdsListener` mit dem
     * Register, nicht nur die beiden Teile.
     *
     * @spec SPEC-044
     */
    @Test
    fun `the listener closes a connection its registry refuses and starts no handler`() = blocking {
        val handler = ScriptedHandler()
        val events = RecordingEvents()
        val setup = ListenerSetup(ListenerConfig(LOOPBACK, 0), handler, events, RefusingRegistry())
        val started = track(TdsListener.start(setup))
        assertIs<StartResult.Started>(started)
        val client = connectTo(started.listener.port)
        assertTrue(client.awaitClosedByPeer())
        eventually("Ende REFUSED_AT_SHUTDOWN") { events.endsOf(ConnectionEnd.REFUSED_AT_SHUTDOWN).size == 1 }
        assertEquals(1, events.closed().size)
        assertTrue(handler.connections.isEmpty())
        assertEquals(0, started.listener.activeConnections)
    }

    private class RefusingRegistry : ConnectionRegistry<ManagedConnection>() {
        override fun admit(id: Long, connection: ManagedConnection): Boolean = false
    }

    private companion object {
        const val MEMORY_BYTES = 64
    }
}
