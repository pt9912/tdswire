package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

/** Ein Typbyte außerhalb von [FramingOracle.KNOWN_TYPES]. */
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
