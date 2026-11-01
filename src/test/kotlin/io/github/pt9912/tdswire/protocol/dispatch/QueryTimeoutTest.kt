package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Die Frist einer Anfrage (`QUERY_TIMEOUT`, `SPEC-009`, `ADR-0013` Entscheidung 4, `ADR-0027` Entscheidung 2):
 * `deadlineEpochMillis`, Ablauf in `TDS_TIMEOUT` als Anfragefehler (Class 16, Verbindung offen) und der Wettlauf mit
 * ATTENTION. Die Zeit steuert der Test ([SteeredPause]); kein Test wartet auf die Wanduhr. Die Fake-Backends
 * ignorieren das Feld der Frist; durchgesetzt wird sie vom Protokollkern.
 */
class QueryTimeoutTest {
    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
    private val oneRowReply = listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1)
        .joinToString(" ")
    private val timeoutMessage = ServerMessage(50005, 1, 16, "Timeout.")

    private fun timesOf(pause: SteeredPause, query: Duration? = 30.seconds, cancel: Duration = 5.seconds) =
        RequestTimes(query, cancel, now = { FIXED_NOW }, pause = pause::pause)

    /**
     * Der Ablauf der Frist bei einem Backend, das nichts liefert und das Feld der Frist ignoriert: das Backend bekommt
     * `deadlineEpochMillis` gleich Beginn plus `QUERY_TIMEOUT`, der Lauf geht den Weg des Cancel (genau ein `cancel()`,
     * `close()`), die Antwort ist ERROR 50005 mit Class 16 und DONE mit Fehlerstatus, die Verbindung bleibt offen, und
     * die unmittelbar folgende Anfrage erhält nur ihre eigene Antwort.
     *
     * @spec SPEC-016
     * @spec SPEC-009
     * @spec SPEC-022
     */
    @Test
    fun `Der Ablauf der Frist endet in TDS_TIMEOUT bei offener Verbindung`() = blocking {
        val pause = SteeredPause()
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()), delivering(*oneRow))
        val session = Session.login(BatchVectors.message("slow"))
        val serving = launch { session.serve(backend, times = timesOf(pause)) }
        backend.collecting.await()
        pause.awaitWaiting(30.seconds)
        assertEquals(FIXED_NOW + 30_000, backend.requests.single().deadlineEpochMillis)
        pause.fire(30.seconds)
        session.channel.feed(BatchVectors.message("next"))
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(2, session.replies.size)
        assertEquals(timeoutMessage, session.tokens(0).errorMessage())
        assertEquals(2, session.tokens(0).size)
        assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR), session.hex(0))
        assertEquals(oneRowReply, session.hex(1))
        assertEquals(listOf("s1" to "r1"), backend.cancelled)
        assertEquals(2, backend.closeCalls)
    }

    /**
     * Läuft die Frist ab, nachdem das Ergebnis begonnen hat, hängen ERROR und DONE mit Fehlerstatus an die schon
     * gesendeten Tokens: COLMETADATA, ROW, ERROR, DONE. Ein zurückgehaltener Abschluss entfällt, und es gibt kein
     * `DONE[ATTN]`.
     *
     * @spec SPEC-016
     * @spec SPEC-031
     */
    @Test
    fun `Die Frist im Ergebnis haengt ERROR und DONE an die gesendeten Tokens`() = blocking {
        val pause = SteeredPause()
        val backend = FlowBackend(suspendedAfter(CompletableDeferred(), resultStart(), QueryEvent.Row(listOf(7))))
        val session = Session.login(BatchVectors.message("slow"))
        val serving = launch { session.serve(backend, times = timesOf(pause)) }
        backend.collecting.await()
        pause.awaitWaiting(30.seconds)
        pause.fire(30.seconds)
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        val tokens = session.tokens(0)
        assertEquals(
            listOf(ColMetadataToken::class, RowToken::class, ErrorToken::class),
            tokens.dropLast(1).map { it::class },
        )
        assertEquals(timeoutMessage, tokens.errorMessage())
        assertEquals(DoneStatus.ERROR, tokens.lastDone().status)
        assertEquals(1, backend.cancelled.size)
    }

    /**
     * Eine Frist in der Vergangenheit (beim Beginn schon abgelaufen, auch genau null) endet sofort in
     * `TDS_TIMEOUT`: das Backend wird nicht gerufen, es wird keine Wartezeit
     * angefordert, und die Verbindung bleibt offen.
     *
     * @spec SPEC-016
     * @spec SPEC-022
     */
    @Test
    fun `Eine Frist in der Vergangenheit endet sofort in TDS_TIMEOUT ohne Backend-Aufruf`() = blocking {
        listOf((-1).seconds, Duration.ZERO).forEach { query ->
            val pause = SteeredPause()
            val backend = FlowBackend(delivering(*oneRow))
            val session = Session.login(BatchVectors.message("late"), Inbound.EndOfStream)
            session.serve(backend, times = timesOf(pause, query = query))
            assertEquals(1, session.replies.size, "$query")
            assertEquals(timeoutMessage, session.tokens(0).errorMessage(), "$query")
            assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR), "$query")
            assertEquals(emptyList(), backend.requests, "$query")
            assertEquals(emptyList(), pause.started, "$query")
            assertEquals(ConnectionState.CLOSED, session.ready.live.context.state, "$query")
        }
    }

    /**
     * Ohne Frist (`null`) bekommt das Backend `deadlineEpochMillis` gleich `null`, es läuft kein Zeitlimit, und die
     * Anfrage endet regulär.
     *
     * @spec SPEC-022
     * @spec SPEC-009
     */
    @Test
    fun `Ohne Frist gibt es keinen Zeitlimit-Lauf`() = blocking {
        val pause = SteeredPause()
        val backend = FlowBackend(delivering(*oneRow))
        val session = Session.login(BatchVectors.message("q"), Inbound.EndOfStream)
        session.serve(backend, times = timesOf(pause, query = null))
        assertEquals(null, backend.requests.single().deadlineEpochMillis)
        assertEquals(emptyList(), pause.started)
        assertEquals(oneRowReply, session.hex(0))
    }

    /**
     * Läuft die Frist ab, nachdem das abschließende DONE geschrieben ist, bleibt die Antwort vollständig und ohne
     * ERROR: die Frist ändert nichts, es gibt keinen Cancel, und die Verbindung bleibt offen.
     *
     * @spec SPEC-016
     * @spec SPEC-029
     */
    @Test
    fun `Eine Frist nach dem abschliessenden DONE aendert die Antwort nicht`() = blocking {
        val pause = SteeredPause()
        val backend = FlowBackend(delivering(*oneRow))
        val session = Session.login(BatchVectors.message("q"))
        val hook = interposing({ _, token -> isFinalDone(token) }) {
            pause.awaitWaiting(30.seconds)
            pause.fire(30.seconds)
            session.channel.feed(Inbound.EndOfStream)
            session.ready.awaitState(ConnectionState.CANCELLING)
        }
        session.serve(backend, sinkAround = hook, times = timesOf(pause))
        assertEquals(1, session.replies.size)
        assertEquals(oneRowReply, session.hex(0))
        assertEquals(emptyList(), backend.cancelled)
    }

    /**
     * Kreuzen sich die Frist und ATTENTION, gewinnt der erste Übergang; der Eingriff der Senke macht die Reihenfolge
     * eindeutig (der zweite Auslöser wird erst ausgelöst, wenn der erste den Zustand `CANCELLING` hergestellt hat).
     * Gewinnt die Frist, besteht die Antwort aus dem Timeout-Fehler; das ATTENTION, das danach kommt, wird als eigene
     * Nachricht bestätigt (ATTENTION nach Ende der Antwort), damit der Client nicht wartet. Gewinnt ATTENTION, besteht
     * die Antwort aus `DONE[ATTN]` allein, ohne Fehler; die abgelaufene Frist ändert nichts. Beide Male geht genau ein
     * Cancel an das Backend.
     *
     * @spec SPEC-029
     * @spec SPEC-016
     */
    @Test
    fun `Frist und ATTENTION kreuzen sich und der erste Uebergang gewinnt`() = blocking {
        val deadline = crossing(deadlineFirst = true)
        assertEquals(2, deadline.session.replies.size)
        assertEquals(timeoutMessage, deadline.session.tokens(0).errorMessage())
        assertEquals(DoneStatus.ERROR, deadline.session.tokens(0).lastDone().status)
        assertEquals(BatchVectors.DONE_ATTN, deadline.session.hex(1))
        val attention = crossing(deadlineFirst = false)
        assertEquals(1, attention.session.replies.size)
        assertEquals(BatchVectors.COLMETADATA_N + " " + BatchVectors.DONE_ATTN, attention.session.hex(0))
        listOf(deadline, attention).forEach {
            assertEquals(1, it.backend.cancelled.size)
            assertEquals(1, it.backend.closeCalls)
        }
    }

    private class Crossing(val session: Session, val backend: FlowBackend)

    /** Ein Lauf, in dem nach dem ersten Token der Antwort beide Auslöser in der gewählten Reihenfolge kommen. */
    private suspend fun crossing(deadlineFirst: Boolean): Crossing {
        val pause = SteeredPause()
        val backend = FlowBackend(delivering(*oneRow))
        val session = Session.login(BatchVectors.message("slow"))
        val hook = interposing({ index, _ -> index == 1 }) {
            pause.awaitWaiting(30.seconds)
            if (deadlineFirst) {
                pause.fire(30.seconds)
                session.ready.awaitState(ConnectionState.CANCELLING)
                session.channel.feed(BatchVectors.attention())
            } else {
                session.channel.feed(BatchVectors.attention())
                session.ready.awaitState(ConnectionState.CANCELLING)
                if (pause.isWaiting(30.seconds)) pause.fire(30.seconds)
            }
            session.channel.feed(Inbound.EndOfStream)
        }
        session.serve(backend, sinkAround = hook, times = timesOf(pause))
        return Crossing(session, backend)
    }

    /**
     * Ein ATTENTION nach dem vollständig gesendeten Abschluss eines Timeouts ist der Fall „ATTENTION nach Ende der
     * Antwort“: `DONE[ATTN]` als eigene Nachricht, kein zweiter Cancel, keine Wiederholung des Fehlers.
     *
     * @spec SPEC-029
     * @spec SPEC-016
     */
    @Test
    fun `ATTENTION nach dem Abschluss eines Timeouts wird ohne zweiten Cancel bestaetigt`() = blocking {
        val pause = SteeredPause()
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = Session.login(BatchVectors.message("slow"))
        val serving = launch { session.serve(backend, times = timesOf(pause)) }
        backend.collecting.await()
        pause.awaitWaiting(30.seconds)
        pause.fire(30.seconds)
        awaitUntil { session.replies.isNotEmpty() && session.ready.live.context.state == ConnectionState.READY }
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(2, session.replies.size)
        assertEquals(timeoutMessage, session.tokens(0).errorMessage())
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
        assertEquals(1, backend.cancelled.size)
    }

    /**
     * Lässt sich der Strom nicht mehr abschließen (das Schreiben von ERROR und DONE scheitert), schließt die Verbindung
     * ohne ERROR und DONE (`SPEC-031`): keine Antwort geht hinaus, die Sitzung ist `CLOSED`.
     *
     * @spec SPEC-031
     * @spec SPEC-016
     */
    @Test
    fun `Ein nicht abschliessbarer Strom schliesst bei Ablauf der Frist ohne ERROR und DONE`() = blocking {
        val pause = SteeredPause()
        val backend = FlowBackend(suspendedAfter(CompletableDeferred(), resultStart()))
        val session = Session.login(BatchVectors.message("slow"))
        session.channel.failReplyAfterWrites = 1
        val serving = launch { session.serve(backend, times = timesOf(pause)) }
        backend.collecting.await()
        pause.awaitWaiting(30.seconds)
        pause.fire(30.seconds)
        serving.join()
        assertEquals(0, session.replies.size)
        assertEquals(1, session.channel.writtenChunks.size)
        assertTrue(session.channel.closed)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertEquals(CloseReason.WRITE_FAILED, session.ready.live.closeReason)
    }

    /** Ein Lauf, dessen erster Schreibvorgang der Antwort am Rückstau hängt und dabei die Sitzungssperre hält. */
    private class Stalled(val session: Session, val backend: FlowBackend, val hold: CompletableDeferred<Unit>)

    private suspend fun CoroutineScope.stalledWrite(pause: SteeredPause, block: suspend (Stalled) -> Unit): Stalled {
        val hold = CompletableDeferred<Unit>()
        val backend = FlowBackend(suspendedAfter(CompletableDeferred(), resultStart()))
        val session = Session.login(BatchVectors.message("slow"))
        var writing = false
        session.channel.onReplyWrite = {
            if (!writing) {
                writing = true
                hold.await()
            }
        }
        val serving = launch { session.serve(backend, times = timesOf(pause)) }
        awaitUntil { writing }
        pause.awaitWaiting(30.seconds)
        val stalled = Stalled(session, backend, hold)
        try {
            block(stalled)
        } finally {
            hold.complete(Unit)
        }
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        return stalled
    }

    /**
     * Ein ATTENTION, das den Übergang an die Frist verliert (die Frist steht vor ihm an der Sperre, die ein hängender
     * Schreibvorgang hält): der Server verwirft es nicht, sondern bestätigt es nach dem Timeout-Fehler als eigene
     * Nachricht, ohne zweiten Cancel, damit der Client nicht auf die Bestätigung wartet.
     *
     * @spec SPEC-029
     * @spec SPEC-016
     */
    @Test
    fun `Ein ATTENTION, das den Uebergang an die Frist verliert, wird danach bestaetigt`() = blocking {
        val pause = SteeredPause()
        val run = stalledWrite(pause) {
            pause.fire(30.seconds)
            repeat(HOPS) { yield() }
            it.session.channel.feed(BatchVectors.attention())
            repeat(HOPS) { yield() }
        }
        assertEquals(2, run.session.replies.size)
        assertEquals(timeoutMessage, run.session.tokens(0).errorMessage())
        assertEquals(BatchVectors.DONE_ATTN, run.session.hex(1))
        assertEquals(1, run.backend.cancelled.size)
    }

    /**
     * Grenze: ein Schreibvorgang, der am Rückstau hängt, hält die Sitzungssperre. Solange er nicht zurückkehrt, wirken
     * weder die Frist noch ATTENTION: der Zustand bleibt unverändert, das Backend wird nicht abgebrochen, keine Antwort
     * geht hinaus. Nach der Rückkehr wirkt die Frist.
     *
     * @spec SPEC-009
     * @spec SPEC-029
     * @spec SPEC-033
     */
    @Test
    fun `Ein haengender Schreibvorgang haelt Frist und ATTENTION an bis zu seiner Rueckkehr`() = blocking {
        val pause = SteeredPause()
        val run = stalledWrite(pause) {
            pause.fire(30.seconds)
            it.session.channel.feed(BatchVectors.attention())
            repeat(HOPS) { yield() }
            assertTrue(it.session.ready.live.context.state != ConnectionState.CANCELLING)
            assertEquals(emptyList(), it.backend.cancelled)
            assertEquals(0, it.session.replies.size)
        }
        assertEquals(timeoutMessage, run.session.tokens(0).errorMessage())
        assertEquals(1, run.backend.cancelled.size)
    }

    private companion object {
        const val HOPS = 8
    }
}
