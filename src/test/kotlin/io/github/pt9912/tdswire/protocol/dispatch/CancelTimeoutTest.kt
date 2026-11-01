package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.toHex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * `CANCEL_TIMEOUT` (`SPEC-033`, `ADR-0027` Entscheidung 2 Ausnahme b): ein Backend, das den Cancel nicht beachtet,
 * führt nach der Frist zum Schließen der Verbindung; der Cancel selbst schließt nicht. Die Zeit steuert der Test
 * ([SteeredPause]). Das Fake-Backend ignoriert den Abbruch in einem nicht abbrechbaren Bereich; das belegt die Frist
 * und das Schließen, nicht das Verhalten eines echten blockierenden Treibers.
 */
class CancelTimeoutTest {
    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private fun timesOf(pause: SteeredPause) =
        RequestTimes(30.seconds, 5.seconds, now = { FIXED_NOW }, pause = pause::pause)

    /**
     * Ein Backend, das den Cancel nicht beachtet: die Frist läuft erst ab ATTENTION; vor ihrem Ablauf bleibt die
     * Verbindung offen; nach `CANCEL_TIMEOUT` ist sie geschlossen, ohne Bestätigung und ohne weitere Bytes, die
     * Schleife ist zurückgekehrt (Sitzung freigegeben), und sobald das Backend zurückkehrt, sind `cancel()` und
     * `close()` je genau einmal gerufen (der Backend-Strom ist geschlossen).
     *
     * @spec SPEC-033
     * @spec SPEC-029
     * @spec TDS-FA-07
     */
    @Test
    fun `Ein Backend, das den Cancel ignoriert, fuehrt nach CANCEL_TIMEOUT zum Schliessen`() = blocking {
        val pause = SteeredPause()
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend(ignoringCancel(gate, resultStart()))
        val session = Session.login(BatchVectors.message("stuck"))
        val serving = launch { session.serve(backend, times = timesOf(pause)) }
        backend.collecting.await()
        assertFalse(pause.isWaiting(5.seconds), "CANCEL_TIMEOUT läuft schon vor dem Cancel")
        session.channel.feed(BatchVectors.attention())
        pause.awaitWaiting(5.seconds)
        assertFalse(session.channel.closed, "der Cancel selbst schliesst")
        assertEquals(ConnectionState.CANCELLING, session.ready.live.context.state)
        pause.fire(5.seconds)
        serving.join()
        assertTrue(session.channel.closed)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertEquals(CloseReason.CANCEL_TIMEOUT, session.ready.live.closeReason)
        assertEquals(0, session.replies.size)
        val written = session.channel.writtenChunks.joinToString(" ") { it.toHex() }
        assertFalse(written.contains(BatchVectors.DONE_ATTN), written)
        gate.complete(Unit)
        backend.closed.await()
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Beachtet das Backend den Cancel, schließt die Verbindung nicht: `CANCEL_TIMEOUT` wird nie ausgelöst, der Grund
     * des Schließens ist nicht `CANCEL_TIMEOUT`, und die folgende Anfrage wird beantwortet.
     *
     * @spec SPEC-033
     * @spec SPEC-029
     */
    @Test
    fun `Ein Backend, das den Cancel beachtet, laesst die Verbindung offen`() = blocking {
        val pause = SteeredPause()
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()), delivering(*oneRow))
        val session = Session.login(BatchVectors.message("slow"))
        val serving = launch { session.serve(backend, times = timesOf(pause)) }
        backend.collecting.await()
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(BatchVectors.message("next"))
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(2, session.replies.size)
        assertEquals(BatchVectors.DONE_ATTN, session.hex(0))
        assertFalse(pause.isWaiting(5.seconds))
        assertTrue(session.ready.live.closeReason != CloseReason.CANCEL_TIMEOUT)
        assertEquals(1, backend.cancelled.size)
    }

    /**
     * Auch der Ablauf der Frist einer Anfrage geht den Weg des Cancel: ignoriert das Backend ihn, schließt die
     * Verbindung nach `CANCEL_TIMEOUT` ohne ERROR und DONE.
     *
     * @spec SPEC-033
     * @spec SPEC-016
     */
    @Test
    fun `Ein Backend, das den Abbruch nach der Frist ignoriert, schliesst ohne ERROR`() = blocking {
        val pause = SteeredPause()
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend(ignoringCancel(gate))
        val session = Session.login(BatchVectors.message("stuck"))
        val serving = launch { session.serve(backend, times = timesOf(pause)) }
        backend.collecting.await()
        pause.awaitWaiting(30.seconds)
        pause.fire(30.seconds)
        pause.awaitWaiting(5.seconds)
        pause.fire(5.seconds)
        serving.join()
        assertTrue(session.channel.closed)
        assertEquals(0, session.replies.size)
        assertEquals(CloseReason.CANCEL_TIMEOUT, session.ready.live.closeReason)
        gate.complete(Unit)
        backend.closed.await()
        assertEquals(1, backend.cancelled.size)
    }
}
