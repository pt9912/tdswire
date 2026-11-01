package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.session.CancelCause
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeAction
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.WireSession
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ATTENTION über den echten Transport (Leser, Schreiber, Paket-Codec) und das Schreiben eines Pakets
 * (`TDS-FA-07`, `TDS-QA-07`, `SPEC-029`, `ADR-0004` Festlegung zum Abbruch beim Schreiben): die Gegenstelle ist ein
 * [io.github.pt9912.tdswire.testsupport.ScriptedWire], die der Test steuert.
 */
class AttentionWireTest {
    private val packetSize = 512

    private fun attentionPacket() = handPacket(PacketType.ATTENTION.code, 1, ByteArray(0))

    /** Die Pakete des Drahts nach ihren Längenfeldern; bleibt am Ende ein Rest, war ein Paket nicht vollständig. */
    private fun split(content: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var at = 0
        while (at < content.size) {
            val length = ((content[at + 2].toInt() and 0xFF) shl 8) or (content[at + 3].toInt() and 0xFF)
            assertTrue(at + length <= content.size, "halbes Paket ab Byte $at: Länge $length von ${content.size - at}")
            packets += content.copyOfRange(at, at + length)
            at += length
        }
        return packets
    }

    /**
     * Ein ATTENTION, das ankommt, während der Schreibvorgang mitten in einem Paket steht (der Draht nimmt es nicht an):
     * der Abbruch wartet, bis das Paket ganz auf dem Draht ist, kein halbes Paket bleibt, und die Bestätigung hängt
     * als `DONE[ATTN]` an dieselbe Nachricht an, die mit dem EOM-Paket endet. Es gibt genau einen Cancel, danach keine
     * Zeile mehr. Die Reihenfolge der folgenden Schritte ändert die Aussagen nicht; die Lage „mitten im Paket“ stellt
     * der Halt des Drahts her.
     *
     * @spec SPEC-029
     * @spec SPEC-031
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION mitten in einem Paket laesst kein halbes Paket und endet mit DONE ATTN`() = blocking {
        val session = WireSession.login(packetSize, "big", retain = true) {
            stallAfterBytes = 3L * packetSize
            live = true
        }
        val backend = ScriptedBackend.generating(rows = 1_000_000)
        val serving = launch { session.serve(backend) }
        session.wire.stalled.await()
        session.wire.feed(attentionPacket())
        repeat(HOPS) { yield() }
        session.wire.release()
        session.wire.endInput()
        serving.join()
        val packets = split(session.wire.wireContent)
        assertTrue(packets.size >= 4, "Pakete: ${packets.size}")
        packets.dropLast(1).forEach { assertEquals(packetSize, it.size, "unvollständiges Paket in der Nachricht") }
        assertEquals(1, packets.last()[1].toInt() and 1, "das letzte Paket trägt das EOM-Bit")
        assertTrue(packets.dropLast(1).all { it[1].toInt() and 1 == 0 })
        val tokens = TokenDecoder(session.wire.payload()).decodeAll()
        assertEquals(1, tokens.count { it is DoneToken })
        assertEquals(DoneStatus.ATTENTION, (tokens.last() as DoneToken).status)
        assertTrue(tokens.none { it is ErrorToken })
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
        val pulled = backend.rowsPulled
        yield()
        assertEquals(pulled, backend.rowsPulled)
    }

    /**
     * Der zweite Auslöser bekommt die Aktion `Cancel` nicht: ATTENTION und Fristablauf auf dieselbe Anfrage melden dem
     * Rahmen in beiden Reihenfolgen genau einmal `Cancel` mit dem Grund des ersten; der Zustand ist `CANCELLING`, und
     * der zweite Aufruf ändert nichts.
     *
     * @spec SPEC-029
     */
    @Test
    fun `Nur der erste Ausloeser bekommt die Aktion Cancel`() = blocking {
        val attention = HandshakeEvent.Message(TdsMessage(PacketType.ATTENTION, ByteArray(0)))
        val orders = listOf(
            listOf(attention to CancelCause.ATTENTION, HandshakeEvent.DeadlineExpired to CancelCause.DEADLINE),
            listOf(HandshakeEvent.DeadlineExpired to CancelCause.DEADLINE, attention to CancelCause.ATTENTION),
        )
        orders.forEach { order ->
            val session = Session.login()
            val live = session.ready.live
            live.dispatch(HandshakeEvent.Message(TdsMessage(PacketType.SQL_BATCH, ByteArray(0))))
            assertEquals(ConnectionState.RUNNING, live.context.state)
            val first = live.dispatch(order[0].first)
            val second = live.dispatch(order[1].first)
            assertEquals(HandshakeAction.Cancel(order[0].second), first)
            assertNull(second)
            assertEquals(ConnectionState.CANCELLING, live.context.state)
        }
    }

    private companion object {
        const val HOPS = 8
    }
}
