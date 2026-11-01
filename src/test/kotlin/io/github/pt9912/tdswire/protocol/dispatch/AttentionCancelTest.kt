package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ATTENTION während der Anfrage, Cancel und Bestätigung (`TDS-FA-07`, `TDS-QA-07`, `SPEC-029`, `ADR-0027`
 * Entscheidung 1 und 2): Gegenstelle ohne Netz mit Rohbytes-Handvektoren gegen ein Fake-Backend. Die Tests belegen
 * nicht den Referenzclient. Die Reihenfolge der Ereignisse steuert der Test (Tore und Eingriffe zwischen zwei
 * Schritten des Sequencers), nicht die Wiederholung bis zum Zufallsfund.
 */
class AttentionCancelTest {
    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
    private val oneRowReply = listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1)
        .joinToString(" ")

    private fun List<Token>.names() = map { it::class.simpleName }

    /**
     * Ein ATTENTION in `RUNNING` (das Backend suspendiert vor dem ersten Ergebnis) löst genau einen Cancel aus; die
     * Antwort ist genau ein `DONE[ATTN]` (Handvektor) ohne ERROR und ohne zweite Nachricht, die Verbindung bleibt
     * offen, und die unmittelbar folgende Anfrage erhält nur ihre eigene Antwort.
     *
     * @spec SPEC-029
     * @spec TDS-FA-07
     * @spec TDS-QA-07
     */
    @Test
    fun `ATTENTION in RUNNING loest einen Cancel aus und wird mit DONE ATTN allein beantwortet`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()), delivering(*oneRow))
        val session = Session.login(BatchVectors.message("slow"))
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        assertEquals(ConnectionState.RUNNING, session.ready.live.context.state)
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(BatchVectors.message("next"))
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(2, session.replies.size)
        assertEquals(BatchVectors.DONE_ATTN, session.hex(0))
        assertTrue(session.tokens(0).none { it is ErrorToken })
        assertEquals(oneRowReply, session.hex(1))
        assertEquals(listOf("s1" to "r1"), backend.cancelled)
        assertEquals(listOf("slow", "next"), backend.requests.map { it.sql })
        assertEquals(2, backend.closeCalls)
    }

    /**
     * Ein ATTENTION in `STREAMING` (zwei Zeilen sind geschrieben, das Backend liefert weiter): die Zeilen hören auf,
     * die Antwort endet mit genau einem `DONE[ATTN]` hinter den schon gesendeten Tokens, ohne ERROR, und das
     * Backend erzeugt nach dem Abbruch keine Zeile mehr.
     *
     * @spec SPEC-029
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION in STREAMING stoppt die Zeilen und schliesst mit einem DONE ATTN ab`() = blocking {
        var produced = 0
        val endless: (FlowBackend) -> Flow<QueryEvent> = { backend ->
            flow {
                emit(resultStart())
                while (true) {
                    emit(QueryEvent.Row(listOf(produced++)))
                    if (produced == 3) backend.begin()
                    yield()
                }
            }
        }
        val backend = FlowBackend(endless)
        val session = Session.login(BatchVectors.message("big"))
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        val atEnd = produced
        yield()
        assertEquals(atEnd, produced, "das Backend erzeugte nach dem Abbruch Zeilen")
        val tokens = session.tokens(0)
        assertEquals(ColMetadataToken::class, tokens.first()::class)
        assertTrue(tokens.drop(1).dropLast(1).all { it is RowToken }, "${tokens.names()}")
        assertTrue(tokens.count { it is RowToken } >= 3)
        assertEquals(1, tokens.count { it is DoneToken })
        assertEquals(DoneStatus.ATTENTION, tokens.lastDone().status)
        assertTrue(tokens.none { it is ErrorToken })
        assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ATTN))
        assertEquals(1, session.replies.size)
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Ein Backend, das einen Thread blockiert (hinter dem Pool des Integrators, unterbrechbar, `ADR-0013`), wird vom
     * Cancel erreicht: der Aufruf endet, die Antwort ist `DONE[ATTN]` allein. Der Test belegt nicht das Verhalten
     * eines echten blockierenden Treibers.
     *
     * @spec SPEC-029
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION erreicht ein blockierendes Backend`() = blocking {
        val backend = FlowBackend(blockingAfter(CountDownLatch(1)))
        val session = Session.login(BatchVectors.message("blocked"))
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(1, session.replies.size)
        assertEquals(BatchVectors.DONE_ATTN, session.hex(0))
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Kreuzen sich das abschließende DONE und ATTENTION, und das DONE ging zuerst hinaus: die Antwort bleibt
     * vollständig und ohne zweites DONE, ATTENTION wird danach als „ATTENTION nach Ende der Antwort“ bestätigt, und
     * es gibt keinen Cancel. Die Reihenfolge steuert ein Eingriff der Senke hinter dem DONE.
     *
     * @spec SPEC-029
     * @spec SPEC-001
     */
    @Test
    fun `ATTENTION nach dem abschliessenden DONE wird als eigene Nachricht bestaetigt ohne Cancel`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val session = Session.login(BatchVectors.message("q"))
        val hook = interposing({ _, token -> isFinalDone(token) }) {
            session.channel.feed(BatchVectors.attention())
            session.channel.feed(Inbound.EndOfStream)
            session.ready.awaitState(ConnectionState.CANCELLING)
        }
        session.serve(backend, sinkAround = hook)
        assertEquals(2, session.replies.size)
        assertEquals(oneRowReply, session.hex(0))
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
        assertEquals(1, session.tokens(0).count { it is DoneToken })
        assertEquals(emptyList(), backend.cancelled)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Gewinnt ATTENTION vor dem nächsten Token der Antwort, schreibt die reguläre Antwort nicht weiter: die Antwort
     * besteht aus den schon geschriebenen Tokens und genau einem `DONE[ATTN]`, ohne die Zeile und ohne das gehaltene
     * DONE; der Cancel geht genau einmal an das Backend, obwohl zwei Wege zu ihm führen (Abbruch der Coroutine und
     * Senke, die das Token nicht annimmt).
     *
     * @spec SPEC-029
     * @spec SPEC-031
     */
    @Test
    fun `ATTENTION vor dem naechsten Token verwirft den Rest und bestaetigt einmal`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val session = Session.login(BatchVectors.message("q"))
        val hook = interposing({ index, _ -> index == 1 }) {
            session.channel.feed(BatchVectors.attention())
            session.channel.feed(Inbound.EndOfStream)
            session.ready.awaitState(ConnectionState.CANCELLING)
        }
        session.serve(backend, sinkAround = hook)
        assertEquals(1, session.replies.size)
        assertEquals(BatchVectors.COLMETADATA_N + " " + BatchVectors.DONE_ATTN, session.hex(0))
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Ein wiederholtes ATTENTION löst keinen zweiten Cancel aus. Der erste Auslöser gewinnt; jedes weitere ATTENTION
     * ist danach ein ATTENTION nach Ende der Antwort und wird einzeln mit `DONE[ATTN]` bestätigt, damit kein Client auf
     * eine Bestätigung wartet: drei ATTENTION ergeben einen Cancel und drei Bestätigungen. Das ist eine Setzung ohne
     * Messung; den Beleg liefert erst das Cancel-Szenario am Referenzclient.
     *
     * @spec SPEC-029
     */
    @Test
    fun `Ein wiederholtes ATTENTION loest keinen zweiten Cancel aus (Setzung N Bestaetigungen)`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = Session.login(BatchVectors.message("slow"))
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        repeat(3) { session.channel.feed(BatchVectors.attention()) }
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(1, backend.cancelled.size)
        assertEquals(3, session.replies.size)
        session.replies.indices.forEach { assertEquals(BatchVectors.DONE_ATTN, session.hex(it), "Antwort $it") }
    }

    /**
     * ATTENTION im Leerlauf nach der Antwort wird mit `DONE[ATTN]` bestätigt, ohne Cancel und ohne Backend-Aufruf; die
     * Verbindung bleibt offen und die folgende Anfrage erhält nur ihre eigene Antwort.
     *
     * @spec SPEC-029
     * @spec SPEC-001
     */
    @Test
    fun `ATTENTION im Leerlauf wird bestaetigt und die Verbindung bleibt offen`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val session = Session.login(BatchVectors.message("first"))
        val serving = launch { session.serve(backend) }
        awaitUntil { session.replies.isNotEmpty() && session.ready.live.context.state == ConnectionState.READY }
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(BatchVectors.message("next"))
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(3, session.replies.size)
        assertEquals(oneRowReply, session.hex(0))
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
        assertEquals(oneRowReply, session.hex(2))
        assertEquals(emptyList(), backend.cancelled)
        assertEquals(listOf("first", "next"), backend.requests.map { it.sql })
        assertFalse(session.ready.live.context.state == ConnectionState.RUNNING)
    }

    /**
     * Endet die Anfrageschleife mit einer Ausnahme beim Mitlesen (der Kanal wirft), bricht sie den Lauf des Backends
     * ab: `cancel()` und `close()` je genau einmal, kein Lauf bleibt zurück.
     *
     * @spec SPEC-031
     * @spec TDS-FA-07
     */
    @Test
    fun `Eine Ausnahme beim Mitlesen bricht den Lauf des Backends ab`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = Session.login(BatchVectors.message("slow"))
        var reads = 0
        session.channel.onReceive = { if (++reads == 2) error("Lesefehler") }
        val outcome = runCatching { session.serve(backend) }
        assertTrue(outcome.isFailure)
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }
}
