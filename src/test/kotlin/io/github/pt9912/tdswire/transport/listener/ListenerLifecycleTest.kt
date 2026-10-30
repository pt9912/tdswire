package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.channel.ChannelFailure
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import io.github.pt9912.tdswire.transport.tls.TlsPath
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Verbindungslebenszyklus des Listeners: `LOGIN_TIMEOUT` mit steuerbarer Zeit, Absturz und Trennen
 * einzelner Verbindungen. Handler sind Test-Handler ohne Protokoll; die Tests belegen das Verhalten
 * des Transports, nicht das einer Session.
 */
class ListenerLifecycleTest : ListenerTestBase() {
    private val loginTimeout = 15.seconds
    private val shortTimeout = 3_000L

    private suspend fun SocketChannelAdapter.echoes(byte: Byte) {
        assertEquals(ChannelResult.Ok(Unit), writeFully(ByteBuffer.wrap(byteArrayOf(byte))))
        val buffer = ByteBuffer.allocate(1)
        assertEquals(ChannelResult.Ok(1), read(buffer))
        assertEquals(byte, buffer.get(0))
    }

    /**
     * `LOGIN_TIMEOUT` (hier 15 s, vorläufiger Wert) läuft ab dem Verbindungsaufbau und umfasst den
     * TLS-Handschlag: eine Gegenstelle, die im 7.4-Pfad bzw. im 8.0-Pfad nach dem ersten Byte schweigt,
     * wird nach Ablauf der gesteuerten Frist getrennt, nicht vorher; der Transport schickt nichts. Der
     * Test lässt die Frist erst ablaufen, wenn der Handler-Rumpf lief (Frist und Handler starten
     * nebenläufig).
     *
     * @spec SPEC-008
     */
    @Test
    fun `login timeout closes a connection stuck in the handshake in both paths`() = blocking {
        val first = mapOf(TlsPath.ENCAPSULATED_IN_TDS to 0x12.toByte(), TlsPath.FROM_FIRST_BYTE to 0x16.toByte())
        first.forEach { (path, firstByte) ->
            val delayer = ManualDelayer()
            val events = RecordingEvents()
            val handler = TlsEchoHandler(TestKeys.serverContext(), path)
            val listener = startOnLoopback(handler, events, ListenerTimes(loginTimeout, delayer = delayer))
            val client = connectTo(listener.port)
            client.writeFully(ByteBuffer.wrap(byteArrayOf(firstByte)))
            eventually("Frist LOGIN_TIMEOUT wird erwartet") { delayer.pending == listOf(loginTimeout) }
            eventually("Handler hat den Handschlag begonnen") { handler.wires.size == 1 }
            assertEquals(1, listener.activeConnections)
            assertTrue(events.closed().isEmpty())
            delayer.releaseAll()
            assertTrue(client.awaitClosedByPeer())
            eventually("Ende LOGIN_TIMEOUT") { events.endsOf(ConnectionEnd.LOGIN_TIMEOUT).size == 1 }
            assertEquals(1, events.closed().size)
            assertTrue(handler.wires.single().written.isEmpty(), "der Transport schickt nichts")
            eventually("Verbindung aufgeräumt") { listener.activeConnections == 0 }
            listener.shutdown()
        }
    }

    /**
     * Meldet der Handler „Login abgeschlossen“, gilt `LOGIN_TIMEOUT` nicht mehr: nach Ablauf der
     * Frist bleibt die Verbindung offen und arbeitet weiter.
     *
     * @spec SPEC-008
     */
    @Test
    fun `completed login keeps the connection when the time is up`() = blocking {
        val delayer = ManualDelayer()
        val handler = ScriptedHandler()
        val listener = startOnLoopback(handler, RecordingEvents(), ListenerTimes(loginTimeout, delayer = delayer))
        val client = connectTo(listener.port)
        client.echoes(1)
        eventually("Frist LOGIN_TIMEOUT ist beendet") { delayer.pending.isEmpty() }
        delayer.releaseAll()
        client.echoes(2)
        assertEquals(1, listener.activeConnections)
        client.close()
        listener.shutdown()
    }

    /**
     * Ein Handler, der wirft, beendet nur seine Verbindung (Ende `HANDLER_FAILED` mit dem Typnamen,
     * ohne Text); zwei andere Verbindungen arbeiten weiter, und der Listener nimmt eine neue an.
     *
     * @spec SPEC-005
     */
    @Test
    fun `a crashing handler ends only its own connection`() = blocking {
        val events = RecordingEvents()
        val listener = startOnLoopback(ScriptedHandler(crashOnFirstByte = 'X'.code), events)
        val good = listOf(connectTo(listener.port), connectTo(listener.port))
        good.forEach { it.echoes('a'.code.toByte()) }
        val bad = connectTo(listener.port)
        bad.writeFully(ByteBuffer.wrap(byteArrayOf('X'.code.toByte())))
        assertTrue(bad.awaitClosedByPeer())
        eventually("Ende HANDLER_FAILED") { events.endsOf(ConnectionEnd.HANDLER_FAILED).size == 1 }
        assertEquals("java.lang.IllegalStateException", events.endsOf(ConnectionEnd.HANDLER_FAILED).single().failure)
        good.forEach { it.echoes('b'.code.toByte()) }
        connectTo(listener.port).echoes('c'.code.toByte())
        assertEquals(3, listener.activeConnections)
        listener.shutdown()
    }

    /**
     * Eine `CancellationException`, die der Handler wirft, während der Scope der Verbindung nicht
     * abgebrochen ist (kein Trennen durch den Listener), ist ein Handlerfehler: Ende `HANDLER_FAILED`
     * mit dem Typnamen, die Verbindung schließt, zwei andere Verbindungen arbeiten weiter und es gibt
     * kein Ende `SHUTDOWN`. Die Einordnung ist eine Setzung (`ManagedConnection`), nicht gemessen.
     *
     * @spec SPEC-005
     */
    @Test
    fun `a cancellation thrown by the handler on its own is a handler failure`() = blocking {
        val events = RecordingEvents()
        val thrown = CancellationException("Handler bricht von selbst ab")
        val listener = startOnLoopback(ScriptedHandler(crashOnFirstByte = 'X'.code, crash = { thrown }), events)
        val good = listOf(connectTo(listener.port), connectTo(listener.port))
        good.forEach { it.echoes('a'.code.toByte()) }
        val bad = connectTo(listener.port)
        bad.writeFully(ByteBuffer.wrap(byteArrayOf('X'.code.toByte())))
        assertTrue(bad.awaitClosedByPeer())
        eventually("Ende HANDLER_FAILED") { events.endsOf(ConnectionEnd.HANDLER_FAILED).size == 1 }
        assertEquals(thrown.javaClass.name, events.endsOf(ConnectionEnd.HANDLER_FAILED).single().failure)
        assertTrue(events.endsOf(ConnectionEnd.SHUTDOWN).isEmpty())
        good.forEach { it.echoes('b'.code.toByte()) }
        assertEquals(2, listener.activeConnections)
        listener.shutdown()
    }

    /**
     * Trennt die Gegenstelle, endet der Handler, der Kanal ist geschlossen (Lesen meldet `CLOSED`),
     * und die Verbindung ist aus dem Listener entfernt (Ende `HANDLER_RETURNED`).
     *
     * @spec SPEC-008
     */
    @Test
    fun `client disconnect releases the channel and the scope`() = blocking {
        val events = RecordingEvents()
        val handler = ScriptedHandler()
        val listener = startOnLoopback(handler, events)
        val client = connectTo(listener.port)
        client.echoes(1)
        client.close()
        eventually("Ende HANDLER_RETURNED") { events.endsOf(ConnectionEnd.HANDLER_RETURNED).size == 1 }
        eventually("Verbindung aufgeräumt") { listener.activeConnections == 0 }
        val after = handler.connections.single().channel.read(ByteBuffer.allocate(1))
        assertIs<ChannelResult.Failed>(after)
        assertEquals(ChannelFailure.CLOSED, after.error.failure)
        listener.shutdown()
    }

    /**
     * Eine Gegenstelle, die sofort wieder trennt, stört die Annahme nicht: eine neue Verbindung wird
     * angenommen. Nach dem Beenden nimmt der Port keine Verbindung mehr an.
     *
     * @spec SPEC-005 SPEC-044
     */
    @Test
    fun `listener keeps accepting after an abrupt peer and refuses after shutdown`() = blocking {
        val listener = startOnLoopback(ScriptedHandler(), RecordingEvents())
        connectTo(listener.port).close()
        connectTo(listener.port).echoes(7)
        withTimeout(shortTimeout) { listener.shutdown() }
        assertFailsWith<ExecutionException> { connectTo(listener.port) }
    }
}
