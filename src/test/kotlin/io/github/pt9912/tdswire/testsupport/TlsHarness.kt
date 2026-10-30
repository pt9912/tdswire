package io.github.pt9912.tdswire.testsupport

import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.messaging.WriteOutcome
import io.github.pt9912.tdswire.transport.tls.TlsChannel
import io.github.pt9912.tdswire.transport.tls.TlsOptions
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import io.github.pt9912.tdswire.transport.tls.TlsPath
import io.github.pt9912.tdswire.transport.tls.TlsResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Wie die Gegenstellen ihre Schreib- und Lesevorgänge zerstückeln. */
enum class Slicing { WHOLE, BYTEWISE, RANDOM }

/** Eine Versuchsanordnung: Server (Produktionscode) gegen eine JDK-Engine als Client über Speicher-Kanäle. */
data class TlsSetup(
    val path: TlsPath,
    val protocols: List<String> = TlsParameters.ALLOWED_PROTOCOLS,
    val suites: List<String> = TlsParameters.SPEC_023.cipherSuites,
    val slicing: Slicing = Slicing.WHOLE,
    val serverKeys: String = TestKeys.VALID,
    val clientTrust: String = TestKeys.VALID,
    /** `null`: das ALPN des Pfads (8.0: `tds/8.0`, 7.4: keines). */
    val peerAlpn: List<String>? = null,
    val options: TlsOptions = TlsOptions(),
    val seed: Long = 1,
    /** Protokolle des Servers; `null`: dieselben wie die der Gegenstelle. */
    val serverProtocols: List<String>? = null,
    /** Suiten des Servers; `null`: dieselben wie die der Gegenstelle. */
    val serverSuites: List<String>? = null,
) {
    val encapsulated: Boolean get() = path == TlsPath.ENCAPSULATED_IN_TDS
}

/** Ausgang eines Handshakes: das Ergebnis des Servers, der Fehler der Gegenstelle und die Drähte. */
class TlsOutcome(
    val server: TlsResult<TlsChannel>,
    val peerFailure: Throwable?,
    val serverWire: RecordingChannel,
    val peer: JdkTlsPeer,
)

const val MEMORY_CAPACITY = 1 shl 16
private const val RANDOM_SLICE_MAX = 64

private fun sizes(slicing: Slicing, seed: Long): Iterator<Int>? = when (slicing) {
    Slicing.WHOLE -> null

    Slicing.BYTEWISE -> generateSequence { 1 }.iterator()

    Slicing.RANDOM -> Random(seed).let { random ->
        generateSequence { random.nextInt(1, RANDOM_SLICE_MAX) }.iterator()
    }
}

private fun sliced(inner: SuspendingChannel, setup: TlsSetup, salt: Long): SuspendingChannel =
    if (setup.slicing == Slicing.WHOLE) {
        inner
    } else {
        FragmentingChannel(inner, sizes(setup.slicing, setup.seed + salt), sizes(setup.slicing, setup.seed + salt + 1))
    }

/** Der Client des Tests für [setup] auf [wire]. */
fun peerFor(
    setup: TlsSetup,
    wire: SuspendingChannel,
    holdLastFlight: Boolean = false,
    payloadPerPacket: Int = 4000,
    trailingJunk: Int = 0,
): JdkTlsPeer {
    val pathAlpn = if (setup.path == TlsPath.FROM_FIRST_BYTE) listOf(TlsChannel.ALPN_TDS_8_0) else emptyList()
    val context = TestKeys.clientContext(setup.clientTrust)
    val engine = JdkTlsPeer.clientEngine(context, setup.protocols, setup.suites, setup.peerAlpn ?: pathAlpn)
    val wired = sliced(wire, setup, PEER_SALT)
    return JdkTlsPeer(wired, engine, setup.encapsulated, holdLastFlight, payloadPerPacket, trailingJunk)
}

private const val PEER_SALT = 1000L

/** Führt Server und Client des Tests nebeneinander bis zum Ende des Handshakes. */
suspend fun runHandshake(setup: TlsSetup): TlsOutcome = coroutineScope {
    val (serverEnd, peerEnd) = MemoryChannel.pair(MEMORY_CAPACITY)
    val serverWire = RecordingChannel(serverEnd)
    val peer = peerFor(setup, peerEnd)
    val parameters = TlsParameters(setup.serverProtocols ?: setup.protocols, setup.serverSuites ?: setup.suites)
    val server = async {
        val context = TestKeys.serverContext(setup.serverKeys)
        TlsChannel.accept(sliced(serverWire, setup, 0), context, setup.path, parameters, setup.options)
    }
    val peerFailure = async { peer.handshake().also { if (it != null) peer.close() } }
    TlsOutcome(server.await(), peerFailure.await(), serverWire, peer)
}

/** Der Server-Kanal eines gelungenen Handshakes. */
fun TlsOutcome.channel(): TlsChannel {
    assertEquals(null, peerFailure, "Gegenstelle")
    assertIs<TlsResult.Ok<TlsChannel>>(server)
    return server.value
}

/** Nachrichten mit festen, zufällig gefüllten Nutzdaten, darunter eine über mehrere TLS-Records. */
fun testMessages(type: PacketType, seed: Long): List<TdsMessage> {
    val random = Random(seed)
    return listOf(1, 100, 5000, 40000).map { TdsMessage(type, random.nextBytes(it)) }
}

private const val PACKET_SIZE = 4096

/**
 * Schickt in beide Richtungen je vier Nachrichten durch [MessageWriter] und [MessageReader] auf
 * [server] und [peer] und prüft, dass sie gleich ankommen.
 */
suspend fun exchangeMessages(server: SuspendingChannel, peer: SuspendingChannel) = coroutineScope {
    val toPeer = testMessages(PacketType.TABULAR_RESULT, 11)
    val toServer = testMessages(PacketType.SQL_BATCH, 22)
    val serverSends = async { toPeer.map { MessageWriter(server, PACKET_SIZE).write(it) } }
    val peerSends = async { toServer.map { MessageWriter(peer, PACKET_SIZE).write(it) } }
    val serverReader = MessageReader(server)
    val peerReader = MessageReader(peer)
    val atServer = toServer.map { (serverReader.next() as? ReadOutcome.Message)?.message }
    val atPeer = toPeer.map { (peerReader.next() as? ReadOutcome.Message)?.message }
    assertEquals(toServer, atServer)
    assertEquals(toPeer, atPeer)
    serverSends.await().forEach { assertEquals(WriteOutcome.Written, it) }
    peerSends.await().forEach { assertEquals(WriteOutcome.Written, it) }
}
