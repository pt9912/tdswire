package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Disconnect der Gegenstelle in jedem Zustand einer Sitzung (`TDS-FA-07.a`, `TDS-QA-02`, `SPEC-029`, `SPEC-031`,
 * `ADR-0027`): Gegenstelle ohne Netz gegen ein Fake-Backend. Zugesagt ist je Zustand: höchstens ein `cancel()` und
 * genau ein `close()` der Ausführung, die Sitzung ist geschlossen, nach dem Ende der Gegenstelle geht kein weiterer
 * Schreibvorgang auf den Kanal, und die Schleife kehrt zurück. Der Lecktest über mehrere echte Verbindungen steht in
 * `ConnectionLeakTest`; das Fake-Backend belegt die Freigabe des Stroms, nicht die eines echten Treibers.
 */
class DisconnectTest {
    private val row = arrayOf(resultStart(), QueryEvent.Row(listOf(7)))

    private suspend fun session(): Session = Session.login(BatchVectors.message("lang")).also {
        it.channel.holdEndWhile = { false }
    }

    private fun Session.assertGone(backend: FlowBackend, writtenBefore: Int) {
        assertEquals(ConnectionState.CLOSED, ready.live.context.state)
        assertTrue(channel.closed, "der Kanal ist geschlossen")
        assertEquals(0, replies.size, "keine Antwort auf eine tote Verbindung")
        assertEquals(writtenBefore, channel.writtenChunks.size, "kein Schreibvorgang nach dem Ende der Gegenstelle")
        assertEquals(1, backend.cancelled.size, "genau ein cancel()")
        assertEquals(1, backend.closeCalls, "die Ausführung ist geschlossen")
    }

    /**
     * Das Ende der Gegenstelle in `RUNNING` (das Backend hat noch kein Ergebnis): der Lauf wird abgebrochen, die
     * Sitzung ist geschlossen, nichts wird geschrieben.
     *
     * @spec SPEC-029
     * @spec TDS-FA-07
     * @spec TDS-QA-02
     */
    @Test
    fun `Ende der Gegenstelle in RUNNING bricht die Anfrage ab und schreibt nichts`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = session()
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        assertEquals(ConnectionState.RUNNING, session.ready.live.context.state)
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        session.assertGone(backend, writtenBefore = 0)
    }

    /**
     * Das Ende der Gegenstelle in `STREAMING` (Metadaten und eine Zeile sind geschrieben): der Strom wird
     * abgebrochen, es folgen weder Zeilen noch EOM noch eine Bestätigung.
     *
     * @spec SPEC-031
     * @spec TDS-FA-07
     * @spec TDS-QA-02
     */
    @Test
    fun `Ende der Gegenstelle in STREAMING schliesst den Strom ohne weiteren Schreibvorgang`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred(), *row))
        val session = session()
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        assertEquals(ConnectionState.STREAMING, session.ready.live.context.state)
        val written = session.channel.writtenChunks.size
        assertTrue(written > 0)
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        session.assertGone(backend, written)
    }

    /**
     * Ein Lesefehler (der Abbruch mit RST kommt als Fehler des Kanals) wirkt wie das Ende der Gegenstelle.
     *
     * @spec TDS-FA-07
     * @spec TDS-QA-02
     */
    @Test
    fun `Lesefehler in STREAMING wirkt wie das Ende der Gegenstelle`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred(), *row))
        val session = session()
        val serving = launch { session.serve(backend) }
        backend.collecting.await()
        val written = session.channel.writtenChunks.size
        session.channel.feed(Inbound.Failed)
        serving.join()
        session.assertGone(backend, written)
    }

    /**
     * Das Ende der Gegenstelle in `CANCELLING`: der Abbruch durch ATTENTION läuft, das Backend beendet ihn erst nach
     * dem Ende der Gegenstelle; die Bestätigung `DONE[ATTN]` wird nicht auf die tote Verbindung geschrieben, und
     * `cancel()` wird nicht zum zweiten Mal gerufen.
     *
     * @spec SPEC-029
     * @spec TDS-FA-07
     * @spec TDS-QA-02
     */
    @Test
    fun `Ende der Gegenstelle in CANCELLING verhindert die Bestaetigung und einen zweiten Cancel`() = blocking {
        val pause = SteeredPause()
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend(ignoringCancel(gate, *row))
        withGate(gate) {
            val session = session()
            val times = RequestTimes(30.seconds, 5.seconds, now = { FIXED_NOW }, pause = pause::pause)
            val serving = launch { session.serve(backend, times = times) }
            backend.collecting.await()
            val written = session.channel.writtenChunks.size
            session.channel.feed(BatchVectors.attention())
            pause.awaitWaiting(5.seconds)
            assertEquals(ConnectionState.CANCELLING, session.ready.live.context.state)
            session.channel.feed(Inbound.EndOfStream)
            awaitUntil { session.ready.live.context.state == ConnectionState.CLOSED }
            gate.complete(Unit)
            serving.join()
            session.assertGone(backend, written)
        }
    }

    /**
     * Das Ende der Gegenstelle im Leerlauf (`READY`): die Schleife kehrt zurück, die Sitzung ist geschlossen, das
     * Backend wird nicht gerufen.
     *
     * @spec TDS-FA-07
     * @spec TDS-QA-02
     */
    @Test
    fun `Ende der Gegenstelle im Leerlauf beendet die Schleife ohne Backend-Aufruf`() = blocking {
        val backend = FlowBackend(delivering())
        val session = Session.login(Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertTrue(session.channel.closed)
        assertEquals(0, backend.requests.size)
        assertEquals(0, backend.cancelled.size)
    }

    /**
     * Gegenprobe: ein Ende der Gegenstelle nach einer vollständig beantworteten Anfrage ist kein Abbruch, `cancel()`
     * wird nicht gerufen und die Antwort ist gesendet. Der Disconnect im Handshake ist mit dem Treiber belegt
     * (`HandshakeDriverTest`).
     *
     * @spec TDS-FA-07
     */
    @Test
    fun `Ende der Gegenstelle nach der beantworteten Anfrage ruft kein cancel`() = blocking {
        val backend = FlowBackend(delivering(*row, QueryEvent.ResultEnd(1)))
        val session = Session.login(BatchVectors.message("kurz"), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(1, session.replies.size)
        assertEquals(0, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }
}
