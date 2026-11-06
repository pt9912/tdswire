package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Eine Verbindung mit unvollständig empfangener Nachricht im geordneten Beenden (`SPEC-044` Nr. 2 und Schlussabsatz):
 * sie ruht nicht, bleibt bis zum Ablauf der Frist offen und wird dann getrennt; endet die unvollständige Nachricht
 * vorher, endet die Verbindung. Der Handler meldet die unvollständige Nachricht selbst
 * ([Connection.messageInProgress]); das Erkennen im Leser belegt `MessageReaderMidMessageTest`.
 */
internal class ListenerPartialMessageTest : ListenerTestBase() {
    private val grace = 7.seconds
    private val shortTimeout = 3_000L

    private suspend fun start(handler: ScriptedHandler, events: RecordingEvents, delayer: ManualDelayer) =
        startOnLoopback(handler, events, ListenerTimes(shutdownGrace = grace, delayer = delayer))

    /**
     * Die Verbindung bleibt beim Beginn offen (das Beenden wartet auf die Frist) und wird beim Ablauf der Frist
     * sofort getrennt, ohne die Wartezeit `CANCEL_TIMEOUT`, weil keine Anfrage läuft.
     *
     * @spec SPEC-044
     */
    @Test
    fun `eine unvollstaendige Nachricht haelt die Verbindung bis zum Ablauf der Frist offen`() = blocking {
        val handler = ScriptedHandler()
        val events = RecordingEvents()
        val delayer = ManualDelayer()
        val listener = start(handler, events, delayer)
        val client = connectTo(listener.port)
        eventually("Handler läuft") { handler.connections.size == 1 }
        handler.connections[0].messageInProgress(true)
        handler.connections[0].onShutdownGraceExpired { }
        val shutdown = async { listener.shutdown() }
        eventually("Beenden wartet auf die Frist") { delayer.pending == listOf(grace) }
        assertEquals(1, listener.activeConnections)
        assertTrue(events.closed().isEmpty())
        delayer.releaseAll()
        withTimeout(shortTimeout) { shutdown.await() }
        assertEquals(1, events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).size)
        assertEquals(emptyList(), delayer.pending)
        assertTrue(client.awaitClosedByPeer())
    }

    /**
     * Endet die unvollständige Nachricht vor dem Ablauf der Frist (Meldung `false`) und läuft keine Anfrage, endet die
     * Verbindung sofort, und das Beenden kehrt zurück, ohne dass die Frist abläuft.
     *
     * @spec SPEC-044
     */
    @Test
    fun `endet die unvollstaendige Nachricht vor der Frist, endet die Verbindung`() = blocking {
        val handler = ScriptedHandler()
        val events = RecordingEvents()
        val delayer = ManualDelayer()
        val listener = start(handler, events, delayer)
        connectTo(listener.port)
        eventually("Handler läuft") { handler.connections.size == 1 }
        handler.connections[0].messageInProgress(true)
        val shutdown = async { listener.shutdown() }
        eventually("Beenden wartet auf die Frist") { delayer.pending == listOf(grace) }
        handler.connections[0].messageInProgress(false)
        withTimeout(shortTimeout) { shutdown.await() }
        assertEquals(1, events.endsOf(ConnectionEnd.SHUTDOWN).size)
        assertTrue(events.endsOf(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED).isEmpty())
    }

    /**
     * Eine Verbindung ohne unvollständige Nachricht und ohne Anfrage endet beim Beginn sofort (Gegenprobe).
     *
     * @spec SPEC-044
     */
    @Test
    fun `ohne unvollstaendige Nachricht endet die ruhende Verbindung sofort`() = blocking {
        val handler = ScriptedHandler()
        val events = RecordingEvents()
        val listener = start(handler, events, ManualDelayer())
        connectTo(listener.port)
        eventually("Handler läuft") { handler.connections.size == 1 }
        handler.connections[0].messageInProgress(true)
        handler.connections[0].messageInProgress(false)
        withTimeout(shortTimeout) { listener.shutdown() }
        assertEquals(1, events.endsOf(ConnectionEnd.SHUTDOWN).size)
    }
}
