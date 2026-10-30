package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.JdkTlsPeer
import io.github.pt9912.tdswire.testsupport.Slicing
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.TlsSetup
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.peerFor
import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.messaging.WriteOutcome
import io.github.pt9912.tdswire.transport.tls.TlsPath
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smoke der Welle (Loopback, Docker): Listener, beide TLS-Pfade, Nachrichtenleser und -schreiber
 * gemeinsam, und mehrere gleichzeitige Verbindungen, darunter defekte. Die Gegenstelle ist eine
 * JDK-`SSLEngine`; die Aussagen gelten nicht für den Referenzclient. Der Handler ist ein Echo, kein Protokoll.
 */
class ListenerSmokeTest : ListenerTestBase() {
    private val parallel = Executors.newFixedThreadPool(CLIENT_THREADS).asCoroutineDispatcher()

    @AfterTest
    fun closePool() = parallel.close()

    private val packetSize = 4096
    private val message = TdsMessage(PacketType.SQL_BATCH, Random(7).nextBytes(5000))

    private class Client(val wire: SocketChannelAdapter, val peer: JdkTlsPeer)

    private suspend fun tlsClient(port: Int, path: TlsPath): Client {
        val wire = connectTo(port)
        val peer = peerFor(TlsSetup(path, slicing = Slicing.BYTEWISE), wire)
        assertNull(peer.handshake())
        return Client(wire, peer)
    }

    private suspend fun Client.roundTrip() {
        assertEquals(WriteOutcome.Written, MessageWriter(peer, packetSize).write(message))
        assertEquals(ReadOutcome.Message(message), MessageReader(peer).next())
    }

    /**
     * Der Listener nimmt eine Verbindung an; im 7.4-Pfad beginnt der Server mit einem `0x12`-Paket, im
     * 8.0-Pfad mit einem TLS-Record (`0x16`); eine Nachricht (5000 Byte, die Gegenstelle liest und
     * schreibt byteweise) geht hin und zurück durch Listener, Schleife, Nachrichtenleser und
     * -schreiber. Nach „Login abgeschlossen“ gilt `LOGIN_TIMEOUT` nicht mehr: die Verbindung arbeitet
     * auch, nachdem die gesteuerte Frist abgelaufen ist.
     *
     * @spec SPEC-005 SPEC-008 SPEC-028
     */
    @Test
    fun `both paths carry a message through listener, loop and message codec`() = blocking {
        mapOf(TlsPath.ENCAPSULATED_IN_TDS to 0x12, TlsPath.FROM_FIRST_BYTE to 0x16).forEach { (path, first) ->
            val delayer = ManualDelayer()
            val handler = TlsEchoHandler(TestKeys.serverContext(), path)
            val listener = startOnLoopback(handler, RecordingEvents(), ListenerTimes(delayer = delayer))
            val client = tlsClient(listener.port, path)
            client.roundTrip()
            assertEquals(first.toByte(), handler.wires.single().written.first())
            eventually("Frist LOGIN_TIMEOUT ist beendet") { delayer.pending.isEmpty() }
            delayer.releaseAll()
            client.roundTrip()
            assertEquals(1, listener.activeConnections)
            client.wire.close()
            listener.shutdown()
        }
    }

    private enum class Kind { GOOD, GARBAGE, TRUNCATED, ABRUPT }

    private val kinds = List(KINDS_PER_PATH) { index ->
        when {
            index < GOOD_PER_PATH -> Kind.GOOD
            index < GOOD_PER_PATH + 2 -> Kind.GARBAGE
            index < GOOD_PER_PATH + 4 -> Kind.TRUNCATED
            else -> Kind.ABRUPT
        }
    }

    private fun truncatedStart(path: TlsPath): ByteArray = when (path) {
        TlsPath.ENCAPSULATED_IN_TDS -> byteArrayOf(0x12, 0x01, 0x00, 0xC8.toByte(), 0, 0, 1, 0, 1, 2, 3)
        TlsPath.FROM_FIRST_BYTE -> byteArrayOf(0x16, 0x03, 0x01, 0x00, 0xC8.toByte(), 1, 2, 3)
    }

    private suspend fun run(port: Int, path: TlsPath, kind: Kind) {
        when (kind) {
            Kind.GOOD -> tlsClient(port, path).also { it.roundTrip() }.wire.close()

            Kind.GARBAGE -> connectTo(port).let { wire ->
                wire.writeFully(ByteBuffer.wrap("GET / HTTP/1.1\r\n\r\n".toByteArray()))
                assertTrue(wire.awaitClosedByPeer())
            }

            Kind.TRUNCATED -> connectTo(port).let { wire ->
                wire.writeFully(ByteBuffer.wrap(truncatedStart(path)))
                wire.close()
            }

            Kind.ABRUPT -> connectTo(port).close()
        }
    }

    /**
     * 24 gleichzeitige Verbindungen auf zwei Listenern (je einer pro Pfad, je 12: 6 gute, 2 mit
     * Müll statt Handshake, 2 mit abgeschnittenem Beginn und Abbruch, 2 mit sofortigem Trennen):
     * die guten tauschen ihre Nachricht aus, alle Verbindungen sind danach aus dem Listener
     * entfernt, und eine weitere gute Verbindung funktioniert. Das ist keine Lastmessung.
     *
     * @spec SPEC-008 SPEC-028
     */
    @Test
    fun `defective connections do not disturb good ones`() = blocking {
        val context = TestKeys.serverContext()
        val paths = listOf(TlsPath.ENCAPSULATED_IN_TDS, TlsPath.FROM_FIRST_BYTE)
        val running = paths.map { path ->
            val events = RecordingEvents()
            Triple(path, events, startOnLoopback(TlsEchoHandler(context, path), events))
        }
        val work = running.flatMap { (path, _, listener) ->
            kinds.map { kind -> async(parallel) { run(listener.port, path, kind) } }
        }
        work.forEach { it.await() }
        running.forEach { (path, events, listener) ->
            eventually("alle Verbindungen aufgeräumt") {
                listener.activeConnections == 0 && events.closed().size == kinds.size
            }
            tlsClient(listener.port, path).also { it.roundTrip() }.wire.close()
            listener.shutdown()
        }
    }

    private companion object {
        const val CLIENT_THREADS = 8
        const val GOOD_PER_PATH = 6
        const val KINDS_PER_PATH = 12
    }
}
