package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.TransportHandshakeChannel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/**
 * ATTENTION im Handshake (`ADR-0027` Entscheidung 3, `SPEC-001`, `TDS-FA-07` Boundary): in PRELOGIN, TLS-Aushandlung
 * und LOGIN7 schließt die Verbindung, ohne ein Antwortbyte zu schreiben (Grundregel A); das Paket wird weder
 * beantwortet noch ignoriert. Rohbytes über Paket-Codec, Leser und Schreiber auf einem Speicher-Kanal. Gemessen ist das
 * nur am Testserver des Spike (11 von 11), nicht an einem echten Server.
 */
class AttentionHandshakeTest {
    private val attention = handMessage(PacketType.ATTENTION.code, ByteArray(0), 4000)

    private fun prelogin() =
        handMessage(PacketType.PRELOGIN.code, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON), 4000)

    private class Peer(val client: MemoryChannel, val channel: TransportHandshakeChannel)

    private fun peer(): Peer {
        val (client, server) = MemoryChannel.pair(capacity = 1 shl 20)
        val reader = MessageReader(server, FramingLimits.preLogin())
        return Peer(client, TransportHandshakeChannel(reader, MessageWriter(server, PACKET_SIZE), server) { true })
    }

    private suspend fun MemoryChannel.readToEnd(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(PACKET_SIZE)
        while (true) {
            buffer.clear()
            val read = (read(buffer) as ChannelResult.Ok).value
            if (read < 0) return out.toByteArray()
            out.write(buffer.array(), 0, read)
        }
    }

    private fun driver(adapter: RecordingAdapter, order: HandshakeOrder) =
        HandshakeDriver(HandshakeConfig(order = order), adapter, HandshakeLog.NONE, 10.seconds)

    /**
     * ATTENTION als erste Nachricht in `PRELOGIN` (TDS 7.x): der Treiber endet mit `PROTOCOL_VIOLATION`, die
     * Gegenstelle liest 0 Antwortbytes bis zum Ende, kein Adapteraufruf.
     *
     * @spec SPEC-001
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION in PRELOGIN schliesst ohne Antwortbytes`() = blocking {
        val peer = peer()
        val adapter = RecordingAdapter.accepting()
        peer.client.writeFully(ByteBuffer.wrap(attention))
        val outcome = driver(adapter, HandshakeOrder.PRELOGIN_FIRST).run(peer.channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.PROTOCOL_VIOLATION), outcome)
        assertEquals(0, peer.client.readToEnd().size)
        assertEquals(0, adapter.calls)
    }

    /**
     * ATTENTION in `PRELOGIN` des TDS-8.0-Pfads (TLS zuerst): dasselbe Verhalten, 0 Antwortbytes. Der Handschlag
     * läuft hier als Attrappe, die Reihenfolge `TLS_NEGOTIATION → PRELOGIN` führt der Automat.
     *
     * @spec SPEC-001
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION in PRELOGIN nach TLS ab dem ersten Byte schliesst ohne Antwortbytes`() = blocking {
        val peer = peer()
        val adapter = RecordingAdapter.accepting()
        peer.client.writeFully(ByteBuffer.wrap(attention))
        val outcome = driver(adapter, HandshakeOrder.TLS_FIRST).run(peer.channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.PROTOCOL_VIOLATION), outcome)
        assertEquals(0, peer.client.readToEnd().size)
    }

    /**
     * ATTENTION in `LOGIN7` (nach der PRELOGIN-Antwort und dem TLS-Handschlag): außer der Antwort auf PRELOGIN geht
     * kein Byte hinaus, der Adapter wird nicht gerufen.
     *
     * @spec SPEC-001
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION in LOGIN7 schliesst ohne weitere Antwortbytes`() = blocking {
        val peer = peer()
        val adapter = RecordingAdapter.accepting()
        peer.client.writeFully(ByteBuffer.wrap(prelogin() + attention))
        val outcome = driver(adapter, HandshakeOrder.PRELOGIN_FIRST).run(peer.channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.PROTOCOL_VIOLATION), outcome)
        val written = peer.client.readToEnd()
        val preloginReply = ByteBuffer.wrap(written, 2, 2).short.toInt() and 0xFFFF
        assertEquals(preloginReply, written.size, "mehr als die Antwort auf PRELOGIN: ${written.size} Bytes")
        assertEquals(0, adapter.calls)
    }

    /**
     * In jedem Zustand des Handshakes, auch in `TLS_NEGOTIATION` und `AUTHENTICATING` (die der Treiber nicht über
     * Nachrichten erreicht), ist ATTENTION ein unzulässiger Übergang: `CLOSED` mit `PROTOCOL_VIOLATION`, ohne
     * Aktion `Send`.
     *
     * @spec SPEC-001
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION ist in jedem Zustand des Handshakes ein Schliessen ohne Antwort`() {
        val message = HandshakeEvent.Message(SessionVectors.message(PacketType.ATTENTION))
        listOf(
            ConnectionState.CONNECTED,
            ConnectionState.PRELOGIN,
            ConnectionState.TLS_NEGOTIATION,
            ConnectionState.LOGIN7,
            ConnectionState.AUTHENTICATING,
        ).forEach { state ->
            val step = HandshakeAutomaton().step(HandshakeContext(state), message)
            assertEquals(ConnectionState.CLOSED, step.context.state, "$state")
            assertEquals(listOf(HandshakeAction.Close(CloseReason.PROTOCOL_VIOLATION)), step.actions, "$state")
            assertIs<HandshakeAction.Close>(step.actions.single())
        }
    }

    private companion object {
        const val PACKET_SIZE = 4096
    }
}
