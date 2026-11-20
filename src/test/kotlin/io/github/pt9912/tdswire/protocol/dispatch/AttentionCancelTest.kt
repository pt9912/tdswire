package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
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
 * ATTENTION während der Anfrage, Cancel und Antwort (`TDS-FA-07`, `TDS-QA-07`, `SPEC-029`, `ADR-0027`
 * Entscheidung 2, `ADR-0054`): Gegenstelle ohne Netz mit Rohbytes-Handvektoren gegen ein Fake-Backend. Die Antwort auf
 * ATTENTION in laufender Anfrage sind zwei Nachrichten, `DONE[ERROR]` und `DONE[ATTN]`; die Bytes der beiden DONE sind
 * die der Aufnahmen `cancel-streaming` und `cancel-wartend` beider Referenzinstanzen (`BatchVectors.DONE_ERROR_OPEN`,
 * `DONE_ERROR`, `DONE_ATTN`; die Zuordnung zur Datei prüft `AbbruchAntwortBelegTests` im Referenztest). Die Tests
 * belegen nicht den Referenzclient. Die Reihenfolge der Ereignisse steuert der Test (Tore und Eingriffe zwischen zwei
 * Schritten des Sequencers), nicht die Wiederholung bis zum Zufallsfund.
 */
class AttentionCancelTest {
    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
    private val oneRowReply = listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1)
        .joinToString(" ")

    private fun List<Token>.names() = map { it::class.simpleName }

    private companion object {
        const val HOPS = 8
    }

    /**
     * Ein ATTENTION in `RUNNING` (das Backend suspendiert vor dem ersten Ergebnis, ein wartender Befehl) löst genau
     * einen Cancel aus; die Antwort sind zwei Nachrichten (Handvektoren): `DONE[ERROR]` mit Befehl `0x00fd` ohne
     * ERROR-Token, dann `DONE[ATTN]`. Die Verbindung bleibt offen, und die unmittelbar folgende Anfrage erhält nur
     * ihre eigene Antwort.
     *
     * @spec SPEC-029
     * @spec TDS-FA-07
     * @spec TDS-QA-07
     */
    @Test
    fun `ATTENTION in RUNNING loest einen Cancel aus und wird in zwei Nachrichten beantwortet`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()), delivering(*oneRow))
        val session = Session.login(BatchVectors.message("slow"))
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        assertEquals(ConnectionState.RUNNING, session.ready.live.context.state)
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(BatchVectors.message("next"))
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(3, session.replies.size)
        assertEquals(BatchVectors.DONE_ERROR, session.hex(0))
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
        assertTrue(session.tokens(0).none { it is ErrorToken } && session.tokens(1).none { it is ErrorToken })
        assertEquals(oneRowReply, session.hex(2))
        assertEquals(listOf("s1" to "r1"), backend.cancelled)
        assertEquals(listOf("slow", "next"), backend.requests.map { it.sql })
        assertEquals(2, backend.closeCalls)
    }

    /**
     * Ein ATTENTION in `STREAMING` (drei Zeilen sind geschrieben, das Backend liefert weiter): die Zeilen hören auf,
     * die erste Nachricht endet hinter den schon gesendeten Tokens mit `DONE[ERROR]` bei offenem Ergebnis (Befehl
     * `0x00c1`, Handvektor), die zweite ist `DONE[ATTN]`; kein ERROR-Token, und das Backend erzeugt nach dem Abbruch
     * keine Zeile mehr.
     *
     * @spec SPEC-029
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION in STREAMING stoppt die Zeilen und endet mit DONE ERROR und DONE ATTN`() = blocking {
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
        assertEquals(DoneStatus.ERROR, tokens.lastDone().status)
        assertTrue(tokens.none { it is ErrorToken })
        assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR_OPEN))
        assertEquals(2, session.replies.size)
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Ein Backend, das einen Thread blockiert (hinter dem Pool des Integrators, unterbrechbar, `ADR-0013`), wird vom
     * Cancel erreicht: der Aufruf endet, die Antwort sind `DONE[ERROR]` und `DONE[ATTN]`. Der Test belegt nicht das
     * Verhalten eines echten blockierenden Treibers.
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
        assertEquals(2, session.replies.size)
        assertEquals(BatchVectors.DONE_ERROR, session.hex(0))
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
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
     * Gewinnt ATTENTION vor dem nächsten Token der Antwort, schreibt die reguläre Antwort nicht weiter: die erste
     * Nachricht besteht aus den schon geschriebenen Tokens und `DONE[ERROR]`, ohne die Zeile und ohne das gehaltene
     * DONE (nie zwei Abschlüsse derselben Antwort), die zweite aus `DONE[ATTN]`; der Cancel geht genau einmal an das
     * Backend, obwohl zwei Wege zu ihm führen (Abbruch der Coroutine und Senke, die das Token nicht annimmt).
     *
     * @spec SPEC-029
     * @spec SPEC-031
     */
    @Test
    fun `ATTENTION vor dem naechsten Token verwirft den Rest und schreibt nur das Paar`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val session = Session.login(BatchVectors.message("q"))
        val hook = interposing({ index, _ -> index == 1 }) {
            session.channel.feed(BatchVectors.attention())
            session.channel.feed(Inbound.EndOfStream)
            session.ready.awaitState(ConnectionState.CANCELLING)
        }
        session.serve(backend, sinkAround = hook)
        assertEquals(2, session.replies.size)
        assertEquals(BatchVectors.COLMETADATA_N + " " + BatchVectors.DONE_ERROR_OPEN, session.hex(0))
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Ein wiederholtes ATTENTION löst keinen zweiten Cancel aus (`ADR-0054` Entscheidung 4, Setzung ohne Messung). Der
     * erste Auslöser gewinnt und bekommt das Paar `DONE[ERROR]`, `DONE[ATTN]`; jedes weitere ATTENTION ist danach ein
     * ATTENTION nach Ende der Antwort und wird einzeln mit `DONE[ATTN]` bestätigt, das Paar wird nicht verdoppelt:
     * drei ATTENTION ergeben einen Cancel, ein Paar und zwei einzelne Bestätigungen.
     *
     * @spec SPEC-029
     */
    @Test
    fun `Ein wiederholtes ATTENTION loest keinen zweiten Cancel aus und wird einzeln bestaetigt`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = Session.login(BatchVectors.message("slow"))
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        repeat(3) { session.channel.feed(BatchVectors.attention()) }
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(1, backend.cancelled.size)
        assertEquals(
            listOf(BatchVectors.DONE_ERROR, BatchVectors.DONE_ATTN, BatchVectors.DONE_ATTN, BatchVectors.DONE_ATTN),
            session.replies.indices.map { session.hex(it) },
        )
    }

    /**
     * Ein ATTENTION, das während `CANCELLING` eintrifft (der Cancel läuft, das Backend hat ihn noch nicht beendet),
     * wird erst nach dem Übergang nach `READY` verarbeitet und dann einzeln mit `DONE[ATTN]` bestätigt: nie vor oder
     * zwischen den beiden Nachrichten des Paares, nie ein zweites Paar, kein zweiter Cancel (`ADR-0054` Entscheidung
     * 4). Den Zustand `CANCELLING` hält ein Backend, das den Cancel erst nach dem zweiten ATTENTION beendet.
     *
     * @spec SPEC-029
     * @spec SPEC-001
     */
    @Test
    fun `Ein ATTENTION waehrend CANCELLING wird nach dem Paar einzeln bestaetigt`() = blocking {
        val release = CompletableDeferred<Unit>()
        val backend = FlowBackend(ignoringCancel(release))
        withGate(release) {
            val session = Session.login(BatchVectors.message("slow"))
            val serving = launch { session.serve(backend) }
            backend.collecting.await()
            session.channel.feed(BatchVectors.attention())
            session.ready.awaitState(ConnectionState.CANCELLING)
            session.channel.feed(BatchVectors.attention())
            repeat(HOPS) { yield() }
            assertEquals(emptyList(), session.replies, "vor dem Ende des Cancel geht nichts hinaus")
            assertEquals(ConnectionState.CANCELLING, session.ready.live.context.state)
            release.complete(Unit)
            session.channel.feed(Inbound.EndOfStream)
            serving.join()
            assertEquals(1, backend.cancelled.size)
            assertEquals(
                listOf(BatchVectors.DONE_ERROR, BatchVectors.DONE_ATTN, BatchVectors.DONE_ATTN),
                session.replies.indices.map { session.hex(it) },
            )
        }
    }

    /**
     * Die beiden Nachrichten des Paares gehen unter derselben Sperre der Sitzung hinaus (`ADR-0054` Entscheidung 2):
     * eine Coroutine, die während des Schreibens der ersten Nachricht die Sperre anfordert, bekommt sie erst, wenn
     * auch die zweite auf dem Draht ist.
     *
     * @spec SPEC-029
     * @spec SPEC-001
     */
    @Test
    fun `Das Paar geht unter einer Sperre hinaus`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = Session.login(BatchVectors.message("slow"))
        var seenWhenLockTaken: Int? = null
        session.channel.onReplyWrite = {
            if (seenWhenLockTaken == null && session.channel.sent.size == LOGIN_REPLIES) {
                launch {
                    session.ready.live.dispatch(HandshakeEvent.DeadlineExpired)
                    seenWhenLockTaken = session.replies.size
                }
                repeat(HOPS) { yield() }
            }
        }
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        yield()
        assertEquals(2, session.replies.size)
        assertEquals(2, seenWhenLockTaken, "die Sperre wurde zwischen den beiden Nachrichten frei")
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
