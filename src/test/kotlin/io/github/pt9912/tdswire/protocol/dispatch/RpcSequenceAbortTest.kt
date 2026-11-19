package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int4
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Abbruch und Frist mitten in einer Nachricht mit mehreren RPCs (`SPEC-030`, `SPEC-029`, `SPEC-009`): der erste Aufruf
 * ist beantwortet (sein DONEPROC trägt `MORE|0x80`), der zweite hängt im Backend. Die Zeit steuert der Test
 * ([SteeredPause]). Nicht am echten SQL Server gemessen: die Tests belegen das Verhalten dieses Servers (Setzung).
 */
class RpcSequenceAbortTest {
    private val headerBytes = hex(RpcVectors.HEADERS).size
    private val update = QueryEvent.UpdateCount(1)

    private fun insert(id: Int) = executeSql("insert @Id", "@Id int", param("@Id", int4(id)))

    private fun twoCalls(): Inbound {
        val out = ByteArrayOutputStream()
        out.write(insert(1))
        out.write(0xFF)
        val second = insert(2)
        out.write(second, headerBytes, second.size - headerBytes)
        return BatchVectors.raw(PacketType.RPC, out.toByteArray())
    }

    private fun doneProcs(session: Session): List<Int> =
        session.tokens(0).filterIsInstance<DoneToken>().filter { it.kind == DoneKind.DONE_PROC }.map { it.status }

    /**
     * Läuft die Frist im zweiten Aufruf ab, bleibt die Antwort des ersten stehen (DONEPROC `0x0081`), danach folgen
     * ERROR 50005 und DONEPROC mit `ERROR` ohne `MORE`: der Client wartet nicht auf weitere Tokens. Genau ein
     * `cancel()` für den zweiten Aufruf, die Verbindung bleibt offen und die Folgeanfrage wird beantwortet. Rot, wenn
     * die Schleife die Antwort des ersten Aufrufs verwirft oder den Abschluss mit `MORE` schreibt.
     *
     * @spec SPEC-009
     * @spec SPEC-030
     */
    @Test
    fun `Die Frist im zweiten Aufruf schliesst die Nachricht mit ERROR und DONEPROC ohne MORE`() = blocking {
        val pause = SteeredPause()
        val backend = FlowBackend(delivering(update), suspendedAfter(CompletableDeferred()), delivering(update))
        val times = RequestTimes(30.seconds, 5.seconds, now = { FIXED_NOW }, pause = pause::pause)
        val session = Session.login(twoCalls(), BatchVectors.raw(PacketType.RPC, insert(3)))
        val serving = launch { session.serve(backend, times = times) }
        backend.collecting.await()
        pause.awaitWaiting(30.seconds)
        pause.fire(30.seconds)
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(2, session.replies.size)
        val error = session.tokens(0).filterIsInstance<ErrorToken>().single().message
        assertEquals(ServerMessage(50005, 1, 16, "Timeout."), error)
        assertEquals(listOf(DoneStatus.MORE or DoneStatus.RPC_IN_BATCH, DoneStatus.ERROR), doneProcs(session))
        assertEquals(listOf("s1" to "r1"), backend.cancelled)
        assertEquals(3, backend.requests.size)
    }

    /**
     * Ein ATTENTION im zweiten Aufruf beendet die erste Nachricht hinter der Antwort des ersten Aufrufs (dessen
     * DONEPROC `0x0081` trägt) mit DONEPROC `ERROR` ohne `MORE` und ohne ERROR-Token; die zweite Nachricht ist
     * `DONE[ATTN]`. Genau ein `cancel()`, die Verbindung bleibt offen.
     *
     * @spec SPEC-029
     * @spec SPEC-030
     */
    @Test
    fun `ATTENTION im zweiten Aufruf schliesst die Nachricht mit DONEPROC ERROR und DONE ATTN`() = blocking {
        val backend = FlowBackend(delivering(update), suspendedAfter(CompletableDeferred()))
        val session = Session.login(twoCalls())
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(2, session.replies.size)
        val tokens = session.tokens(0)
        assertTrue(tokens.none { it is ErrorToken })
        assertEquals(DoneStatus.ERROR, tokens.lastDone().status)
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
        assertEquals(listOf(DoneStatus.MORE or DoneStatus.RPC_IN_BATCH, DoneStatus.ERROR), doneProcs(session))
        assertEquals(listOf("s1" to "r1"), backend.cancelled)
    }
}
