package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.session.AttentionAck
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die Fehlerzweige des Paares aus `DONE[ERROR]` und `DONE[ATTN]` (`SPEC-029`, `ADR-0054` Entscheidung 2): scheitert
 * eines der beiden Schreiben, schließt die Sitzung mit `WRITE_FAILED` und die Schleife bekommt kein Ereignis mehr;
 * in `CLOSED` schreibt das Paar nichts. Die Tests belegen nicht das Verhalten eines echten Drahts.
 */
class PairConclusionTest {
    /**
     * Scheitert das Schreiben der zweiten Nachricht, ist die Sitzung geschlossen (`WRITE_FAILED`), der Kanal zu und die
     * Schleife beendet: die Antwort gilt nicht als gesendet, und der Client wartet nicht auf eine Sitzung, die offen
     * aussieht.
     *
     * @spec SPEC-029
     * @spec SPEC-031
     * @spec TDS-FA-07
     */
    @Test
    fun `Scheitert die zweite Nachricht des Paares, schliesst die Sitzung mit WRITE_FAILED`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = Session.login(BatchVectors.message("slow"))
        session.channel.failSendIf = { it.payload.toHex() == BatchVectors.DONE_ATTN }
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(CloseReason.WRITE_FAILED, session.ready.live.closeReason)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertTrue(session.channel.closed)
        assertEquals(1, backend.cancelled.size)
    }

    /**
     * Scheitert schon das Schreiben der ersten Nachricht, geht die zweite nicht hinaus, die Sitzung schließt mit
     * `WRITE_FAILED`, und die Antwort der Schleife ist kein Ereignis `RequestFinished` mehr (ein solches in `CLOSED`
     * wäre ein unzulässiger Übergang und änderte den Grund des Schließens).
     *
     * @spec SPEC-029
     * @spec SPEC-031
     * @spec TDS-FA-07
     */
    @Test
    fun `Scheitert die erste Nachricht des Paares, geht die zweite nicht hinaus`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = Session.login(BatchVectors.message("slow"))
        session.channel.failReplyAfterWrites = 0
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(CloseReason.WRITE_FAILED, session.ready.live.closeReason)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertEquals(emptyList(), session.replies)
    }

    /**
     * In `CLOSED` schreibt das Paar nichts: `concludeWith` meldet `false`, kein Byte geht auf den Kanal, und keine
     * Nachricht steht hinter den Antworten des Logins.
     *
     * @spec SPEC-029
     * @spec SPEC-001
     */
    @Test
    fun `Das Paar schreibt in CLOSED nichts`() = blocking {
        val session = Session.login()
        val live = session.ready.live
        live.dispatch(HandshakeEvent.PeerClosed)
        assertEquals(ConnectionState.CLOSED, live.context.state)
        val reply = live.openReply(PacketType.TABULAR_RESULT)
        assertFalse(reply.concludeWith(hex(BatchVectors.DONE_ERROR), AttentionAck.message))
        assertEquals(emptyList(), session.channel.writtenChunks)
        assertEquals(emptyList(), session.replies)
    }
}
