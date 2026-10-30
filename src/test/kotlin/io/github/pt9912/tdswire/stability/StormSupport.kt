package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.protocol.session.EncryptionPolicy
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.HandshakeDriver
import io.github.pt9912.tdswire.protocol.session.HandshakeLog
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.listener.Connection
import io.github.pt9912.tdswire.transport.listener.ConnectionHandler
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.awaitClosedByPeer
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.messaging.TransportError
import io.github.pt9912.tdswire.transport.messaging.TransportHandshakeChannel
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Handler der Stabilitätstests: führt den Handshake mit [HandshakeDriver] über Paket-Codec, Leser und
 * Schreiber auf dem rohen Kanal der Verbindung, meldet nach der Annahme „Login abgeschlossen“ und
 * liest danach Nachrichten, bis der Strom endet oder ein Fehler kommt. Es gibt keinen SQL_BATCH-Pfad:
 * eingehende Nachrichten nach dem Login werden verworfen. Der unverschlüsselte Testmodus ersetzt TLS.
 */
class DriverHandler(private val adapter: AuthAdapter) : ConnectionHandler {
    val outcomes = ConcurrentLinkedQueue<String>()
    val afterLogin = ConcurrentLinkedQueue<String>()

    override suspend fun handle(connection: Connection) {
        val reader = MessageReader(connection.channel, FramingLimits.preLogin())
        val writer = MessageWriter(connection.channel, PACKET_SIZE)
        val channel = TransportHandshakeChannel(reader, writer, connection.channel) { false }
        val config = HandshakeConfig(encryptionPolicy = EncryptionPolicy.TEST_UNENCRYPTED)
        val driver = HandshakeDriver(config, adapter, HandshakeLog.NONE, HANDSHAKE_TIMEOUT, sessionIds = { "s" })
        val outcome = driver.run(channel)
        outcomes += describe(outcome)
        if (outcome is HandshakeOutcome.Ready) {
            connection.loginCompleted()
            afterLogin += describe(readUntilEnd(reader))
        }
    }

    private suspend fun readUntilEnd(reader: MessageReader): ReadOutcome {
        while (true) {
            val next = reader.next()
            if (next !is ReadOutcome.Message) return next
        }
    }

    private fun describe(outcome: HandshakeOutcome): String = when (outcome) {
        is HandshakeOutcome.Ready -> "Ready"
        is HandshakeOutcome.Closed -> "Closed:${outcome.reason}"
    }

    private fun describe(outcome: ReadOutcome): String = when (outcome) {
        is ReadOutcome.Message -> "Message"
        ReadOutcome.EndOfStream -> "EndOfStream"
        is ReadOutcome.Failed -> describe(outcome.error)
    }

    private fun describe(error: TransportError): String = when (error) {
        is TransportError.Framing -> "Framing:${error.error.failure}"
        is TransportError.Channel -> "Channel"
    }

    private companion object {
        const val PACKET_SIZE = 4096
        val HANDSHAKE_TIMEOUT = 60.seconds
    }
}

/** Der Client eines Falls: ein verbundener Socket und ein Leser für die Antworten des Servers. */
class Peer(socket: AsynchronousSocketChannel, private val random: Random) {
    private val wire = SocketChannelAdapter(socket)
    val reader = MessageReader(wire)

    suspend fun send(bytes: ByteArray) {
        wire.writeFully(ByteBuffer.wrap(bytes))
    }

    /** Sendet in Stücken von 1 bis 7 Byte. */
    suspend fun sendInPieces(bytes: ByteArray) {
        var from = 0
        while (from < bytes.size) {
            val to = minOf(bytes.size, from + random.nextInt(1, MAX_PIECE + 1))
            send(bytes.copyOfRange(from, to))
            from = to
        }
    }

    suspend fun awaitClosedByServer(): Boolean = wire.awaitClosedByPeer()

    fun close() = wire.close()

    /** Sendet PRELOGIN und liest die Antwort. */
    suspend fun prelogin() {
        send(PRELOGIN)
        assertIs<ReadOutcome.Message>(reader.next())
    }

    /** Sendet LOGIN7 für [user]; mit [expectAccepted] liest sie die Antwort und prüft das LOGINACK. */
    suspend fun login(user: String = "sa", pieces: Boolean = false, expectAccepted: Boolean = true) {
        val bytes = login7(user)
        if (pieces) sendInPieces(bytes) else send(bytes)
        if (expectAccepted) {
            val answer = assertIs<ReadOutcome.Message>(reader.next()).message
            assertTrue(TokenDecoder(answer.payload).decodeAll().any { it is LoginAck }, "LOGINACK fehlt")
        }
    }

    companion object {
        private const val MAX_PIECE = 7
        private const val PACKET = 4000
        val PRELOGIN = handMessage(0x12, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_NOT_SUP), PACKET)

        fun login7(user: String): ByteArray =
            handMessage(0x10, SessionVectors.login7Payload { userName = user }, PACKET)

        fun connect(port: Int, random: Random): Peer {
            val socket = AsynchronousSocketChannel.open()
            socket.connect(InetSocketAddress(InetAddress.getByName(LOOPBACK), port)).get()
            return Peer(socket, random)
        }
    }
}

/** Ein Schritt einer Verbindung, die mit RST endet. */
sealed interface Step {
    class Send(val bytes: ByteArray) : Step

    /** Liest eine ganze Antwortnachricht (Pakete bis EOM). */
    data object ReadMessage : Step
}

/**
 * Eine blockierende Verbindung, die ihre [Step]s ausführt und dann mit RST statt FIN schließt
 * (`SO_LINGER` 0): die Gegenstelle liest einen Fehler statt des Endes.
 */
object ResetClient {
    private const val HEADER = 8
    private const val BYTE = 0xFF
    private const val BYTE_BITS = 8

    fun run(port: Int, steps: List<Step>) {
        Socket(LOOPBACK, port).use { socket ->
            socket.setSoLinger(true, 0)
            steps.forEach { step ->
                when (step) {
                    is Step.Send -> socket.getOutputStream().write(step.bytes)
                    Step.ReadMessage -> readMessage(socket)
                }
            }
        }
    }

    private fun readMessage(socket: Socket) {
        do {
            val header = socket.getInputStream().readNBytes(HEADER)
            val length = ((header[2].toInt() and BYTE) shl BYTE_BITS) or (header[3].toInt() and BYTE)
            socket.getInputStream().readNBytes(length - HEADER)
        } while (header[1].toInt() and 1 == 0)
    }
}

/**
 * Was der Server für einen Fall tun muss: der Ausgang des Handshakes, der Ausgang des Lesens nach dem
 * Login ([afterLogin], nur bei `Ready`) oder Hängen bis zur Frist ([hung]).
 */
class Expect(val outcomes: Set<String>, val afterLogin: Set<String> = emptySet(), val hung: Boolean = false)

private val CLOSED_READ_FAILED = Expect(setOf("Closed:READ_FAILED"))
private val CLOSED_VIOLATION = Expect(setOf("Closed:PROTOCOL_VIOLATION"))
private val READY_THEN_END = Expect(setOf("Ready"), setOf("EndOfStream"))
private val AFTER_FRAMING_ERROR = { failure: String -> Expect(setOf("Ready"), setOf("Framing:$failure")) }
private val HUNG = Expect(emptySet(), hung = true)

private suspend fun Peer.expectClose() = assertTrue(awaitClosedByServer(), "Server schliesst nicht")

/** Ein Paketkopf von Hand: Typ, Status, Länge Big-Endian, SPID 0, Packet-ID 1, Window 0. */
private fun header(type: Int, status: Int, length: Int) =
    byteArrayOf(type.toByte(), status.toByte(), (length ushr BYTE_BITS).toByte(), length.toByte(), 0, 0, 1, 0)

/** Ein Typbyte außerhalb des Katalogs nach MS-TDS 2.2.3.1.1. */
private fun unknownType(random: Random): Int = (0..MAX_BYTE).filter { it !in FramingOracle.KNOWN_TYPES }.random(random)

private fun randomPayload(random: Random) = random.nextBytes(PAYLOAD_NOISE)

private const val PAYLOAD_NOISE = 40
private const val HEADER_BYTES = 8
private const val BYTE_BITS = 8
private const val MAX_BYTE = 255
private const val U16_MAX = 0xFFFF
private const val PRELOGIN_PAYLOAD_MAX = 65527
private const val PARTIAL_BATCH = 18

/**
 * Die Fälle der Stabilitätstests: eine Clientverbindung, die der Server übersteht, mit dem Ausgang,
 * den die Referenz-Regel verlangt. [keepOpen] hält die Verbindung offen, bis der Test die Frist
 * `LOGIN_TIMEOUT` auslöst. Die Rohbytes sind von Hand gebaut, nicht mit dem Codec.
 */
enum class Hostile(
    val expect: Expect,
    val keepOpen: Boolean = false,
    val reset: List<Step>? = null,
    val run: suspend (Peer, Random) -> Unit = { _, _ -> },
) {
    GOOD(READY_THEN_END, run = { peer, _ ->
        peer.prelogin()
        peer.login()
    }),
    GOOD_IN_PIECES(READY_THEN_END, run = { peer, _ ->
        peer.prelogin()
        peer.login(pieces = true)
    }),
    GOOD_THEN_GARBAGE_HEADER(AFTER_FRAMING_ERROR("UNKNOWN_PACKET_TYPE"), run = { peer, random ->
        peer.prelogin()
        peer.login()
        peer.send(header(unknownType(random), 1, HEADER_BYTES))
        peer.expectClose()
    }),
    GOOD_THEN_CUT_MESSAGE(AFTER_FRAMING_ERROR("MESSAGE_TRUNCATED"), run = { peer, _ ->
        peer.prelogin()
        peer.login()
        peer.send(handPacket(0x01, 1, ByteArray(100)).copyOf(PARTIAL_BATCH))
    }),
    GOOD_THEN_RESET(
        Expect(setOf("Ready"), setOf("Channel")),
        reset = listOf(
            Step.Send(Peer.PRELOGIN),
            Step.ReadMessage,
            Step.Send(Peer.login7("sa")),
            Step.ReadMessage,
            Step.Send(handPacket(0x01, 1, ByteArray(100)).copyOf(PARTIAL_BATCH)),
        ),
    ),
    HTTP_INSTEAD_OF_PRELOGIN(CLOSED_READ_FAILED, run = { peer, _ ->
        peer.send("GET / HTTP/1.1\r\n\r\n".toByteArray())
        peer.expectClose()
    }),
    LENGTH_BELOW_HEADER(CLOSED_READ_FAILED, run = { peer, random ->
        peer.send(header(0x12, 1, random.nextInt(0, HEADER_BYTES)))
        peer.expectClose()
    }),
    UNKNOWN_PACKET_TYPE(CLOSED_READ_FAILED, run = { peer, random ->
        peer.send(header(unknownType(random), 1, HEADER_BYTES))
        peer.expectClose()
    }),
    MESSAGE_ABOVE_PRELOGIN_LIMIT(CLOSED_READ_FAILED, run = { peer, random ->
        val second = header(0x12, 1, random.nextInt(HEADER_BYTES, U16_MAX + 1))
        peer.send(handPacket(0x12, 0, ByteArray(PRELOGIN_PAYLOAD_MAX)) + second)
        peer.expectClose()
    }),
    NOISE_AS_PRELOGIN(CLOSED_VIOLATION, run = { peer, random ->
        peer.send(handMessage(0x12, randomPayload(random), 4000))
        peer.expectClose()
    }),
    NOISE_AS_LOGIN7(CLOSED_VIOLATION, run = { peer, random ->
        peer.prelogin()
        peer.send(handMessage(0x10, randomPayload(random), 4000))
        peer.expectClose()
    }),
    LOGIN7_BEFORE_PRELOGIN(CLOSED_VIOLATION, run = { peer, _ ->
        peer.send(Peer.login7("sa"))
        peer.expectClose()
    }),
    UNKNOWN_USER(Expect(setOf("Closed:LOGIN_REJECTED")), run = { peer, _ ->
        peer.prelogin()
        peer.login(user = "mallory", expectAccepted = false)
        peer.expectClose()
    }),
    ENCRYPTION_NOT_OFFERED(Expect(setOf("Closed:ENCRYPTION_INCOMPATIBLE")), run = { peer, _ ->
        peer.send(handMessage(0x12, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON), 4000))
        peer.expectClose()
    }),
    CONNECT_AND_LEAVE(Expect(setOf("Closed:PEER_CLOSED")), run = { _, _ -> }),
    CUT_IN_HEADER(CLOSED_READ_FAILED, run = { peer, _ ->
        peer.send(byteArrayOf(0x12, 1, 0, 100, 0))
    }),
    CUT_IN_PAYLOAD(CLOSED_READ_FAILED, run = { peer, _ ->
        peer.send(handPacket(0x12, 1, ByteArray(100)).copyOf(PARTIAL_BATCH))
    }),
    CUT_IN_LOGIN7(CLOSED_READ_FAILED, run = { peer, _ ->
        peer.prelogin()
        val login = Peer.login7("sa")
        peer.send(login.copyOf(login.size / 2))
    }),
    RESET_IN_LOGIN7(
        Expect(setOf("Closed:READ_FAILED")),
        reset = listOf(
            Step.Send(Peer.PRELOGIN),
            Step.ReadMessage,
            Step.Send(Peer.login7("sa").let { it.copyOf(it.size / 2) }),
        ),
    ),
    SILENT(HUNG, keepOpen = true, run = { _, _ -> }),
    SILENT_AFTER_PRELOGIN(HUNG, keepOpen = true, run = { peer, _ -> peer.prelogin() }),
}

/** Vergleicht die gezählten Ausgänge mit den Erwartungen: jeder Fall hat genau einen erwarteten Ausgang. */
fun assertOutcomes(actual: Collection<String>, expected: List<Set<String>>, what: String) {
    val want = expected.groupingBy { it.single() }.eachCount()
    assertEquals(want, actual.groupingBy { it }.eachCount(), "$what: Ausgänge nach Art")
}
