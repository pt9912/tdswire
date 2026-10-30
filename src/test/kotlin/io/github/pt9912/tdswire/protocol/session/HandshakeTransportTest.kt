package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListAuthAdapter
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.drain
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.framing.FramingFailure
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.messaging.TransportError
import io.github.pt9912.tdswire.transport.messaging.TransportHandshakeChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Der Handshake über Paket-Codec, Leser und Schreiber auf einem Speicher-Kanal, ohne Netz und ohne TLS. */
class HandshakeTransportTest {
    private class Wire(val client: MemoryChannel, val reader: MessageReader, val channel: TransportHandshakeChannel)

    private fun wire(): Wire {
        val (client, server) = MemoryChannel.pair(capacity = 1 shl 21)
        val reader = MessageReader(server, FramingLimits.preLogin())
        val channel = TransportHandshakeChannel(reader, MessageWriter(server, PACKET_SIZE), server) { true }
        return Wire(client, reader, channel)
    }

    private fun driver(adapter: AuthAdapter) =
        HandshakeDriver(HandshakeConfig(), adapter, HandshakeLog.NONE, 10.seconds, sessionIds = { "s1" })

    private suspend fun MemoryChannel.send(bytes: ByteArray) {
        writeFully(ByteBuffer.wrap(bytes))
    }

    private suspend fun MemoryChannel.readToEnd(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(READ_CHUNK)
        while (true) {
            buffer.clear()
            val read = (read(buffer) as ChannelResult.Ok).value
            if (read < 0) return out.toByteArray()
            out.write(buffer.array(), 0, read)
        }
    }

    private fun preloginWire(): ByteArray =
        handMessage(PRELOGIN_TYPE, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON), 4000)

    private fun userList(): UserListAuthAdapter {
        val parsed = UserList.parse("alice\t{plain}secret\n", plainTestMode = true)
        return UserListAuthAdapter(assertIs<UserListResult.Valid>(parsed).list)
    }

    /**
     * Ein LOGIN7 über mehrere Pakete (20 Nutzdatenbytes je Paket), byteweise zerstückelt gesendet, führt über
     * Paket-Codec und Automat zur Annahme. Der Vektor ist von Hand aus MS-TDS abgeleitet (Festlegung); der
     * Test belegt, dass der Automat zerstückelte Eingabe verarbeitet, nicht dass ein Client sie erzeugt.
     *
     * @spec SPEC-028
     * @spec SPEC-007
     */
    @Test
    fun `Mehrpaketiges und byteweise zerstueckeltes LOGIN7 fuehrt zur Anmeldung`() = blocking {
        val wire = wire()
        val login = SessionVectors.login7Payload()
        val packets = (login.size + PIECE - 1) / PIECE
        assertTrue(packets >= 4, "LOGIN7 nur in $packets Paketen")
        val client = launch {
            wire.client.send(preloginWire())
            handMessage(LOGIN7_TYPE, login, PIECE).forEach { wire.client.send(byteArrayOf(it)) }
        }
        val outcome = driver(RecordingAdapter.accepting()).run(wire.channel)
        client.join()
        assertIs<HandshakeOutcome.Ready>(outcome)
        val answers = MessageReader(wire.client)
        val first = assertIs<ReadOutcome.Message>(answers.next()).message
        val second = assertIs<ReadOutcome.Message>(answers.next()).message
        assertEquals(listOf(PacketType.TABULAR_RESULT, PacketType.TABULAR_RESULT), listOf(first.type, second.type))
        assertTrue(TokenDecoder(second.payload).decodeAll().any { it is LoginAck })
    }

    /**
     * Am Produkt mit Rohbytes gemessen: unbekannte Kennung und falsches Passwort ergeben dieselben Bytes auf
     * dem Draht, nämlich Paketkopf (Typ 4, EOM, Länge 64) und das feste Muster; danach schließt der Server.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Ablehnungen sind auf dem Draht byte-gleich`() = blocking {
        suspend fun attempt(user: String, password: String): ByteArray {
            val wire = wire()
            val login = SessionVectors.login7Payload {
                userName = user
                this.password = password.toCharArray()
            }
            wire.client.send(preloginWire() + handMessage(LOGIN7_TYPE, login, PACKET_SIZE - 8))
            val outcome = driver(userList()).run(wire.channel)
            assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_REJECTED), outcome)
            return wire.client.readToEnd()
        }

        val unknown = attempt("mallory", "x")
        val wrong = attempt("alice", "falsch")
        assertEquals(unknown.toHex(), wrong.toHex())
        val packetHeader = byteArrayOf(4, 1, 0, 64, 0, 0, 1, 0)
        val tail = wrong.copyOfRange(wrong.size - 64, wrong.size)
        assertEquals((packetHeader + LoginResponses.rejection.payload).toHex(), tail.toHex())
    }

    private fun followUp(): Triple<ByteArray, ByteArray, ByteArray> {
        val small = ByteArray(10) { it.toByte() }
        val big = ByteArray(BIG_BATCH) { (it % 251).toByte() }
        val bigWire = handMessage(BATCH_TYPE, big, 4000)
        val firstPacket = bigWire.copyOfRange(0, 4008)
        val rest = bigWire.copyOfRange(4008, bigWire.size)
        return Triple(handMessage(BATCH_TYPE, small, 4000) + firstPacket, rest, big)
    }

    /**
     * Der Wechsel der Rahmen-Grenze beim Login verliert keine Folgebytes: hinter dem LOGIN7 liegen schon eine
     * vollständige Nachricht und das erste Paket einer großen im Leser. Nach der Annahme liefert derselbe
     * Leser beide, die große über der Vor-Login-Grenze (100000 Byte), weil die Standardgrenze nun gilt.
     *
     * @spec SPEC-046
     * @spec SPEC-007
     */
    @Test
    fun `Folgebytes hinter LOGIN7 gehen beim Grenzwechsel nicht verloren`() = blocking {
        val wire = wire()
        val (early, rest, big) = followUp()
        wire.client.send(preloginWire() + handMessage(LOGIN7_TYPE, SessionVectors.login7Payload(), 4000) + early)
        val server = async { driver(RecordingAdapter.accepting()).run(wire.channel) }
        val answers = MessageReader(wire.client)
        assertIs<ReadOutcome.Message>(answers.next())
        assertIs<ReadOutcome.Message>(answers.next())
        wire.client.send(rest)
        assertIs<HandshakeOutcome.Ready>(server.await())

        val small = assertIs<ReadOutcome.Message>(wire.reader.next()).message
        assertEquals(PacketType.SQL_BATCH, small.type)
        assertEquals(10, small.payload.size)
        val large = assertIs<ReadOutcome.Message>(wire.reader.next()).message
        assertEquals(PacketType.SQL_BATCH, large.type)
        assertEquals(big.toList(), large.payload.toList())
    }

    /**
     * Gegenprobe zum Test davor: Dieselben Bytes über einen Leser mit der Vor-Login-Grenze, ohne Wechsel,
     * scheitern an der Nachrichtengrenze; der Test davor belegt also den Wechsel, nicht eine großzügige Grenze.
     *
     * @spec SPEC-046
     */
    @Test
    fun `Ohne Wechsel scheitert dieselbe Folgenachricht an der Vor-Login-Grenze`() = blocking {
        val wire = wire()
        val (early, rest, _) = followUp()
        wire.client.send(early + rest)
        wire.client.close()
        val (messages, last) = wire.reader.drain()
        assertEquals(1, messages.size)
        val failed = assertIs<ReadOutcome.Failed>(last)
        val error = assertIs<TransportError.Framing>(failed.error).error
        assertEquals(FramingFailure.MESSAGE_ABOVE_LIMIT, error.failure)
    }

    /**
     * Eine PRELOGIN-Nachricht über 65536 Byte scheitert schon am Leser (Vor-Login-Grenze); der Treiber
     * schließt ohne Antwort (nichts auf dem Draht außer dem Ende).
     *
     * @spec SPEC-046
     */
    @Test
    fun `Zu grosses PRELOGIN schliesst ohne Antwort`() = blocking {
        val wire = wire()
        wire.client.send(handMessage(PRELOGIN_TYPE, ByteArray(BIG_BATCH), 4000))
        val outcome = driver(RecordingAdapter.accepting()).run(wire.channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.READ_FAILED), outcome)
        assertEquals(0, wire.client.readToEnd().size)
    }

    private companion object {
        const val PACKET_SIZE = 4096
        const val PIECE = 20
        const val READ_CHUNK = 8192
        const val BIG_BATCH = 100_000
        const val PRELOGIN_TYPE = 0x12
        const val LOGIN7_TYPE = 0x10
        const val BATCH_TYPE = 0x01
    }
}
