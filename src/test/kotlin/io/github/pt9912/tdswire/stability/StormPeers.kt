package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.awaitClosedByPeer
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import kotlin.random.Random
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
