package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Grenze `MAX_CONNECTIONS` (`SPEC-034`, `TDS-FA-07`, `TDS-QA-02`) am Listener mit Test-Handlern: genau
 * `MAX_CONNECTIONS` Verbindungen sind zugleich im Handler, die nächste wird abgewiesen (geschlossen ohne
 * Antwortbytes, `REFUSED_AT_LIMIT`), der Listener und die bestehenden Verbindungen bleiben unberührt, und nach dem
 * Ende einer Verbindung nimmt er wieder auf. Was der Client einer abgewiesenen Verbindung sieht (Schließen ohne
 * Antwort), ist eine Festlegung ohne Messung am Referenzclient.
 */
internal class ListenerLimitTest : ListenerTestBase() {
    private val limit = 3
    private val uncaught = ConcurrentLinkedQueue<String>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler { thread, error -> uncaught += "${thread.name}: $error" }
    }

    @AfterTest
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    private class Fixture(val handler: ScriptedHandler, val events: RecordingEvents, val listener: TdsListener)

    private suspend fun fixture(maxConnections: Int): Fixture {
        val handler = ScriptedHandler()
        val events = RecordingEvents()
        val config = ListenerConfig(LOOPBACK, 0, maxConnections = maxConnections)
        val started = track(TdsListener.start(config, handler, events))
        check(started is StartResult.Started) { "Start gescheitert: $started" }
        return Fixture(handler, events, started.listener)
    }

    /**
     * Bis zur Grenze: `MAX_CONNECTIONS` Verbindungen sind zugleich im Handler, die nächste wird ohne Antwortbytes
     * geschlossen und mit `REFUSED_AT_LIMIT` gemeldet, ihr Handler läuft nie; die bestehenden Verbindungen bleiben
     * offen und bedienbar. Nach dem Ende einer Verbindung nimmt der Listener die nächste wieder auf.
     *
     * @spec SPEC-034
     * @spec TDS-FA-07
     * @spec TDS-QA-02
     */
    @Test
    fun `genau MAX_CONNECTIONS Verbindungen sind im Handler, die naechste wird abgewiesen`() = blocking {
        val f = fixture(limit)
        val clients = mutableListOf<SocketChannelAdapter>()
        repeat(limit) {
            clients += connectTo(f.listener.port)
            eventually("Handler ${it + 1} läuft") { f.handler.connections.size == it + 1 }
        }
        assertEquals(limit, f.listener.activeConnections)
        val surplus = connectTo(f.listener.port)
        assertTrue(surplus.awaitClosedByPeer(), "die Verbindung über der Grenze wird geschlossen")
        eventually("Ende REFUSED_AT_LIMIT") { f.events.endsOf(ConnectionEnd.REFUSED_AT_LIMIT).size == 1 }
        assertEquals(limit, f.handler.connections.size, "kein Handler für die abgewiesene Verbindung")
        assertEquals(limit, f.listener.activeConnections)
        assertTrue(f.events.endsOf(ConnectionEnd.REFUSED_AT_SHUTDOWN).isEmpty())
        assertTrue(f.events.endsOf(ConnectionEnd.HANDLER_FAILED).isEmpty())

        clients[0].close()
        eventually("Platz frei") { f.listener.activeConnections == limit - 1 }
        val next = connectTo(f.listener.port)
        eventually("wieder aufgenommen") { f.handler.connections.size == limit + 1 }
        assertEquals(limit, f.listener.activeConnections)
        assertEquals(1, f.events.endsOf(ConnectionEnd.REFUSED_AT_LIMIT).size)
        assertEquals(emptyList(), uncaught.toList())
        (clients.drop(1) + next).forEach { it.close() }
        eventually("alle Verbindungen beendet") { f.listener.activeConnections == 0 }
    }

    /**
     * Eine abgewiesene Verbindung beeinträchtigt eine bestehende nicht: sie spiegelt weiter ihre Bytes.
     *
     * @spec SPEC-034
     * @spec TDS-QA-02
     */
    @Test
    fun `eine bestehende Verbindung bleibt bedienbar, wenn eine weitere abgewiesen wird`() = blocking {
        val f = fixture(1)
        val first = connectTo(f.listener.port)
        eventually("Handler läuft") { f.handler.connections.size == 1 }
        val refused = connectTo(f.listener.port)
        assertTrue(refused.awaitClosedByPeer())
        val write = first.writeFully(ByteBuffer.wrap(byteArrayOf(0x42)))
        assertTrue(write is ChannelResult.Ok)
        val echo = ByteBuffer.allocate(1)
        val read = first.read(echo)
        assertEquals(1, (read as ChannelResult.Ok).value)
        assertEquals(0x42, echo.get(0).toInt())
        first.close()
    }

    /**
     * Verbindungssturm über der Grenze: 105 Clients öffnen zugleich Verbindungen und halten sie; es sind genau
     * `MAX_CONNECTIONS` (hier 20) im Handler, die übrigen 85 sind abgewiesen und gezählt, kein Thread endet ungefangen,
     * und nach dem Ende der gehaltenen Verbindungen läuft der Listener weiter und nimmt wieder auf. Die Fälle der
     * fehlerhaften Clients prüft `ConnectionStormTest`; dieser Sturm belegt nur die Grenze.
     *
     * @spec SPEC-034
     * @spec TDS-QA-02
     */
    @Test
    fun `der Verbindungssturm ueber der Grenze nimmt genau MAX_CONNECTIONS auf`() = blocking {
        val before = listenerThreads()
        val storm = 105
        val max = 20
        val f = fixture(max)
        val clients = List(storm) { connectTo(f.listener.port) }
        eventually("Ende REFUSED_AT_LIMIT für alle Überzähligen") {
            f.events.endsOf(ConnectionEnd.REFUSED_AT_LIMIT).size == storm - max
        }
        eventually("Handler der Aufgenommenen") { f.handler.connections.size == max }
        assertEquals(max, f.listener.activeConnections)
        assertTrue(f.events.closed().none { it.end == ConnectionEnd.HANDLER_FAILED })
        clients.forEach { it.close() }
        eventually("alle Verbindungen beendet") { f.listener.activeConnections == 0 }
        val after = connectTo(f.listener.port)
        eventually("nach dem Sturm wieder aufgenommen") { f.handler.connections.size == max + 1 }
        after.close()
        f.listener.shutdown()
        assertNoNewListenerThreads(before)
        assertEquals(emptyList(), uncaught.toList())
    }

    /**
     * Das Register zählt die Grenze unter seiner Sperre: bis zur Grenze nimmt `admit` auf, danach nicht, `refusal`
     * nennt die Grenze (nach Beginn des Beendens das Beenden), und `release` gibt einen Platz frei.
     *
     * @spec SPEC-034
     */
    @Test
    fun `das Register nimmt bis zur Grenze auf und gibt Plaetze frei`() {
        val registry = ConnectionRegistry<String>(2)
        assertTrue(registry.admit(1, "a"))
        assertTrue(registry.admit(2, "b"))
        assertFalse(registry.admit(3, "c"))
        assertEquals(ConnectionEnd.REFUSED_AT_LIMIT, registry.refusal())
        registry.release(2)
        registry.release(1)
        assertEquals(ConnectionEnd.REFUSED_AT_LIMIT, registry.refusal(), "der Grund der Entscheidung, nicht des Lesens")
        assertTrue(registry.admit(1, "a"))
        assertTrue(registry.admit(2, "b"))
        assertEquals(2, registry.size)
        registry.release(1)
        assertTrue(registry.admit(4, "d"))
        assertEquals(setOf("b", "d"), registry.beginShutdown()?.toSet())
        assertFalse(registry.admit(5, "e"))
        assertEquals(ConnectionEnd.REFUSED_AT_SHUTDOWN, registry.refusal())
        assertNull(registry.beginShutdown())
    }
}
