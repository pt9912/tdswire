package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.RecordingChannel
import io.github.pt9912.tdswire.testsupport.Slicing
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.TlsSetup
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.channel
import io.github.pt9912.tdswire.testsupport.exchangeMessages
import io.github.pt9912.tdswire.testsupport.peerFor
import io.github.pt9912.tdswire.testsupport.runHandshake
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loopback-Tests der TLS-Schleife gegen eine JDK-`SSLEngine` als Gegenstelle (Zertifikat aus dem
 * Build, Dockerfile-Stage `testkeys`). Die Aussagen gelten gegen diese Gegenstelle im selben
 * Prozess, nicht gegen den Referenzclient.
 */
class TlsLoopbackTest {
    private companion object {
        const val RECORD_HEADER = 5
        const val KEY_UPDATE_MAX = 64
    }

    private val tls13 = listOf("TLSv1.3")
    private val tls12 = listOf("TLSv1.2")

    private suspend fun worksWith(setup: TlsSetup, protocol: String, suite: String) {
        val outcome = runHandshake(setup)
        val channel = outcome.channel()
        assertEquals(protocol, channel.protocol)
        assertEquals(suite, channel.cipherSuite)
        exchangeMessages(channel, outcome.peer)
    }

    private suspend fun everySuite(path: TlsPath, slicing: Slicing) {
        TlsParameters.TLS13_SUITES.forEach { suite ->
            worksWith(TlsSetup(path, tls13, listOf(suite), slicing), "TLSv1.3", suite)
        }
        TlsParameters.TLS12_SUITES.forEach { suite ->
            worksWith(TlsSetup(path, tls12, listOf(suite), slicing), "TLSv1.2", suite)
        }
    }

    /**
     * TDS-8.0-Pfad: je Suite von `SPEC-023` (TLS 1.3 und TLS 1.2) kommen vier Nachrichten je Richtung
     * durch `MessageWriter` und `MessageReader` gleich an, wenn jede Seite byteweise liest und schreibt.
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `eight zero path exchanges messages byte by byte for every suite`() =
        blocking { everySuite(TlsPath.FROM_FIRST_BYTE, Slicing.BYTEWISE) }

    /**
     * TDS-8.0-Pfad wie oben, mit zufälligen Stücklängen (fester Startwert).
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `eight zero path exchanges messages in random pieces for every suite`() =
        blocking { everySuite(TlsPath.FROM_FIRST_BYTE, Slicing.RANDOM) }

    /**
     * TDS-7.4-Pfad: Handshake in `0x12`-Paketen, danach rohes TLS; je Suite kommen vier Nachrichten
     * je Richtung gleich an, wenn jede Seite byteweise liest und schreibt.
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `encapsulated path exchanges messages byte by byte for every suite`() =
        blocking { everySuite(TlsPath.ENCAPSULATED_IN_TDS, Slicing.BYTEWISE) }

    /**
     * TDS-7.4-Pfad wie oben, mit zufälligen Stücklängen (fester Startwert).
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `encapsulated path exchanges messages in random pieces for every suite`() =
        blocking { everySuite(TlsPath.ENCAPSULATED_IN_TDS, Slicing.RANDOM) }

    /**
     * Bieten beide Seiten TLS 1.3 und TLS 1.2 mit allen Suiten von `SPEC-023` an, einigen sie sich auf
     * TLS 1.3; der 8.0-Pfad verlangt und meldet das ALPN `tds/8.0`, der 7.4-Pfad meldet keines.
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `both versions offered negotiate TLS 1_3 and the path decides about ALPN`() = blocking {
        val eight = runHandshake(TlsSetup(TlsPath.FROM_FIRST_BYTE)).channel()
        assertEquals("TLSv1.3", eight.protocol)
        assertEquals(TlsChannel.ALPN_TDS_8_0, eight.applicationProtocol)
        val seven = runHandshake(TlsSetup(TlsPath.ENCAPSULATED_IN_TDS)).channel()
        assertEquals("TLSv1.3", seven.protocol)
        assertEquals(null, seven.applicationProtocol)
    }

    /**
     * Dicht gepackt: die Gegenstelle schreibt das letzte Handshake-Paket (Client-Finished, TLS 1.3)
     * und den ersten verschlüsselten Datensatz in einem Schreibvorgang. Der Server liest beides in
     * einem Lesevorgang (dessen Größe mindestens der des Schreibvorgangs entspricht), schließt den
     * Handshake ab und liefert die Nachricht aus den Bytes hinter dem letzten Handshake-Paket.
     *
     * @spec SPEC-028
     */
    @Test
    fun `bytes behind the last handshake packet reach the data phase`() = blocking {
        coroutineScope {
            val setup = TlsSetup(TlsPath.ENCAPSULATED_IN_TDS, tls13)
            val (serverEnd, peerEnd) = MemoryChannel.pair(1 shl 16)
            val recording = RecordingChannel(serverEnd)
            val peer = peerFor(setup, peerEnd, holdLastFlight = true)
            val accepted = async {
                TlsChannel.accept(recording, TestKeys.serverContext(), setup.path, TlsParameters(tls13, setup.suites))
            }
            val message = TdsMessage(PacketType.SQL_BATCH, Random(5).nextBytes(300))
            val peerWork = async {
                assertEquals(null, peer.handshake())
                MessageWriter(peer, 4096).write(message)
            }
            val server = accepted.await()
            assertIs<TlsResult.Ok<TlsChannel>>(server)
            val received = MessageReader(server.value).next()
            peerWork.await()
            assertEquals(ReadOutcome.Message(message), received)
            assertTrue(peer.denseWriteBytes > 0, "die Gegenstelle hat dicht geschrieben")
            assertTrue(
                recording.readChunks.any { it.size >= peer.denseWriteBytes },
                "ein Lesevorgang des Servers trug Handshake-Paket und Datensatz zusammen: " +
                    "${recording.readChunks.map { it.size }} gegen ${peer.denseWriteBytes}",
            )
        }
    }

    private data class Packet(val type: Int, val length: Int)

    private fun packetsOf(bytes: ByteArray): List<Packet> {
        val packets = mutableListOf<Packet>()
        var at = 0
        while (at < bytes.size) {
            val length = ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
            packets += Packet(bytes[at].toInt() and 0xFF, length)
            at += length
        }
        assertEquals(bytes.size, at, "die Bytes enden auf einer Paketgrenze")
        return packets
    }

    /**
     * Der Server schreibt seinen Handshake in Paketen vom Typ `0x12` mit höchstens der Packet Size
     * (hier 64), und nach `FINISHED` gehen die Nutzdaten als rohe TLS-Records (Typ `0x17`, Version
     * `03 03`) auf den Strom, nicht in Paketen.
     *
     * @spec SPEC-028
     */
    @Test
    fun `server wraps handshake in typed packets and then writes raw records`() = blocking {
        val outcome = runHandshake(TlsSetup(TlsPath.ENCAPSULATED_IN_TDS, options = TlsOptions(packetSize = 64)))
        val server = outcome.channel()
        val handshakeBytes = outcome.serverWire.written
        val packets = packetsOf(handshakeBytes)
        assertTrue(packets.size > 4, "mehrere Pakete: ${packets.size}")
        assertTrue(packets.all { it.type == 0x12 && it.length <= 64 })
        assertEquals(ChannelResult.Ok(Unit), server.writeFully(ByteBuffer.wrap(ByteArray(10))))
        val afterwards = outcome.serverWire.written.copyOfRange(handshakeBytes.size, outcome.serverWire.written.size)
        assertContentEquals(byteArrayOf(0x17, 0x03, 0x03), afterwards.copyOfRange(0, 3))
    }

    /**
     * Auf dem rohen Strom (TDS-8.0-Pfad) beginnt der Server mit einem TLS-Record (Typ `0x16`), nicht
     * mit einem TDS-Paket.
     *
     * @spec SPEC-028
     */
    @Test
    fun `eight zero path starts with a TLS record`() = blocking {
        val outcome = runHandshake(TlsSetup(TlsPath.FROM_FIRST_BYTE))
        outcome.channel()
        assertEquals(0x16, outcome.serverWire.written[0].toInt())
    }

    /**
     * Nutzdaten stehen nie im Klartext auf dem Draht: eine Markierung, die der Server schreibt,
     * kommt bei der Gegenstelle an und kommt in den Bytes des Servers nicht vor (beide Pfade).
     *
     * @spec SPEC-028
     */
    @Test
    fun `payload never appears in plaintext on the wire`() = blocking {
        val marker = "KLARTEXT-MARKE-4711".toByteArray()
        listOf(TlsPath.FROM_FIRST_BYTE, TlsPath.ENCAPSULATED_IN_TDS).forEach { path ->
            val outcome = runHandshake(TlsSetup(path))
            val server = outcome.channel()
            assertEquals(ChannelResult.Ok(Unit), server.writeFully(ByteBuffer.wrap(marker)))
            val received = ByteBuffer.allocate(marker.size)
            while (received.hasRemaining()) assertIs<ChannelResult.Ok<Int>>(outcome.peer.read(received))
            assertContentEquals(marker, received.array())
            assertTrue(indexOf(outcome.serverWire.written, marker) < 0, "Marke im Klartext auf dem Draht ($path)")
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int =
        (0..haystack.size - needle.size).firstOrNull { at ->
            needle.indices.all { haystack[at + it] == needle[it] }
        } ?: -1

    /**
     * Der Puffer für entschlüsselte Daten ist begrenzt: eine Mebibyte kommt durch, ohne dass er wächst,
     * und seine Kapazität ist die Größe, die die Engine für Anwendungsdaten nennt (höchstens 32 KiB).
     *
     * @spec SPEC-028
     */
    @Test
    fun `decrypted buffer stays bounded while a mebibyte passes`() = blocking {
        val outcome = runHandshake(TlsSetup(TlsPath.FROM_FIRST_BYTE))
        val server = outcome.channel()
        val capacity = server.decryptedBufferCapacity
        assertTrue(capacity in 1..(32 * 1024), "Kapazität $capacity")
        val data = Random(3).nextBytes(1 shl 20)
        coroutineScope {
            val sender = async { outcome.peer.writeFully(ByteBuffer.wrap(data)) }
            val received = ByteBuffer.allocate(data.size)
            val slice = ByteBuffer.allocate(1000)
            while (received.hasRemaining()) {
                slice.clear()
                val read = server.read(slice)
                assertIs<ChannelResult.Ok<Int>>(read)
                slice.flip()
                received.put(slice)
            }
            assertEquals(ChannelResult.Ok(Unit), sender.await())
            assertContentEquals(data, received.array())
        }
        assertEquals(capacity, server.decryptedBufferCapacity)
    }

    /**
     * Geordnetes Schließen: `closeGracefully` des Servers sendet `close_notify`, die Gegenstelle liest
     * danach das Ende (-1) und keinen Fehler; umgekehrt liest der Server nach `close_notify` der
     * Gegenstelle -1.
     *
     * @spec SPEC-028
     */
    @Test
    fun `close_notify ends the stream cleanly in both directions`() = blocking {
        listOf(TlsPath.FROM_FIRST_BYTE, TlsPath.ENCAPSULATED_IN_TDS).forEach { path ->
            val serverCloses = runHandshake(TlsSetup(path))
            assertEquals(ChannelResult.Ok(Unit), serverCloses.channel().closeGracefully())
            assertEquals(ChannelResult.Ok(-1), serverCloses.peer.read(ByteBuffer.allocate(10)))

            val peerCloses = runHandshake(TlsSetup(path))
            val server = peerCloses.channel()
            peerCloses.peer.sendCloseNotify()
            assertEquals(ChannelResult.Ok(-1), server.read(ByteBuffer.allocate(10)))
            assertEquals(ChannelResult.Ok(-1), server.read(ByteBuffer.allocate(10)), "das Ende bleibt das Ende")
        }
    }

    /**
     * KeyUpdate: löst die Gegenstelle in TLS 1.3 nach dem Handshake ein KeyUpdate aus (JDK-Engine,
     * `beginHandshake`) und schickt danach Daten, schreibt der Server die Antwort, ohne selbst Nutzdaten zu
     * schreiben: Auf seinem Draht steht genau ein kurzer Record (Typ `0x17`, kleiner als 64 Bytes), der
     * nicht von ihm als Nutzdaten stammt. Danach laufen die Nachrichten in beiden Richtungen weiter (beide
     * Pfade). Beobachtet ist Ort und Größe der Antwort, nicht ihr Inhalt (er ist verschlüsselt).
     * Neuverhandlung in TLS 1.2 ist nicht Gegenstand und nicht getestet.
     *
     * @spec SPEC-028
     */
    @Test
    fun `key update requested by the peer is answered and data keeps flowing`() = blocking {
        listOf(TlsPath.FROM_FIRST_BYTE, TlsPath.ENCAPSULATED_IN_TDS).forEach { path ->
            val outcome = runHandshake(TlsSetup(path, tls13))
            val server = outcome.channel()
            val before = outcome.serverWire.written.size
            outcome.peer.requestKeyUpdate()
            assertEquals(ChannelResult.Ok(Unit), outcome.peer.writeFully(ByteBuffer.wrap(ByteArray(10))))
            assertEquals(ChannelResult.Ok(10), server.read(ByteBuffer.allocate(10)))
            val answer = outcome.serverWire.written.copyOfRange(before, outcome.serverWire.written.size)
            val shown = "Antwort des Servers: ${answer.size} Bytes"
            assertTrue(answer.size in RECORD_HEADER + 1 until KEY_UPDATE_MAX, shown)
            assertEquals(0x17, answer[0].toInt(), "Record-Typ der Antwort")
            val length = ((answer[3].toInt() and 0xFF) shl 8) or (answer[4].toInt() and 0xFF)
            assertEquals(answer.size - RECORD_HEADER, length, "genau ein Record")
            exchangeMessages(server, outcome.peer)
        }
    }

    private suspend fun overSockets(path: TlsPath) = coroutineScope {
        val loopback = InetAddress.getByName("127.0.0.1")
        val listener = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(loopback, 0))
        val client = AsynchronousSocketChannel.open()
        try {
            val port = (listener.localAddress as InetSocketAddress).port
            val connected = client.connect(InetSocketAddress(loopback, port))
            val accepted = listener.accept()
            connected.get()
            val setup = TlsSetup(path)
            val peer = peerFor(setup, SocketChannelAdapter(client))
            val serverSide = async {
                TlsChannel.accept(SocketChannelAdapter(accepted.get()), TestKeys.serverContext(), path)
            }
            val peerSide = async { peer.handshake() }
            assertEquals(null, peerSide.await())
            val server = serverSide.await()
            assertIs<TlsResult.Ok<TlsChannel>>(server)
            exchangeMessages(server.value, peer)
            assertNotNull(server.value.protocol)
            server.value.close()
        } finally {
            client.close()
            listener.close()
        }
    }

    /**
     * Über einen echten Loopback-Socket (`AsynchronousSocketChannel`) gelingen Handshake und
     * Nachrichtenaustausch im TDS-8.0-Pfad.
     *
     * @spec SPEC-028
     */
    @Test
    fun `eight zero path works over a real socket`() = blocking { overSockets(TlsPath.FROM_FIRST_BYTE) }

    /**
     * Über einen echten Loopback-Socket gelingen Handshake und Nachrichtenaustausch im TDS-7.4-Pfad.
     *
     * @spec SPEC-028
     */
    @Test
    fun `encapsulated path works over a real socket`() = blocking { overSockets(TlsPath.ENCAPSULATED_IN_TDS) }
}
