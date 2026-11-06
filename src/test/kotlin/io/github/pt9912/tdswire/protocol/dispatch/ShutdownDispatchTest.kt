package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Das geordnete Beenden aus Sicht der Anfrageschleife (`SPEC-044` Nr. 3 und 4, `SPEC-049`, `ADR-0027` Entscheidung 4
 * und 2c): Gegenstelle ohne Netz, Fake-Backend, ein Fake-[ShutdownGate] anstelle des Listeners. Die Tests belegen die
 * Schleife; Listener und Verdrahtung belegen `ListenerShutdownTest` und `ShutdownWiringTest`. Dass ein echter Client
 * „ERROR, DONE, dann Schließen“ liest, ist am Referenzclient nicht gemessen.
 */
class ShutdownDispatchTest {
    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
    private val oneRowReply = listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1)
        .joinToString(" ")
    private val shutdown = ServerMessage(50008, 1, 20, "Server is shutting down.")

    /** Das Gegenstück zum Listener: merkt sich den Beginn, die Meldungen der Schleife und den angemeldeten Abbruch. */
    private class FakeGate : ShutdownGate {
        @Volatile
        override var begun = false

        val running = CopyOnWriteArrayList<Boolean>()

        @Volatile
        var abort: (suspend () -> Unit)? = null

        override fun requestRunning(running: Boolean) {
            this.running += running
        }

        override fun onGraceExpired(abort: suspend () -> Unit) {
            this.abort = abort
        }
    }

    private fun Session.shutdownLoop(backend: FlowBackend, gate: FakeGate, times: RequestTimes = fixedTimes()) =
        RequestLoop(ready, channel, backend, times = times, hooks = RequestHooks(gate = gate))

    /**
     * Eine Anfrage, die nach dem Beginn des Beendens eintrifft, wird mit ERROR `TDS_SHUTDOWN` (50008, Class 20) und
     * DONE mit Fehlerstatus beantwortet; das Backend wird nicht gerufen, danach ist die Sitzung geschlossen, und die
     * Antwort steht vollständig vor dem Schließen (`Send` vor `Close`).
     *
     * @spec SPEC-044
     * @spec SPEC-049
     * @spec TDS-FA-07
     */
    @Test
    fun `Eine Anfrage nach dem Beginn des Beendens wird mit TDS_SHUTDOWN beantwortet`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val gate = FakeGate().also { it.begun = true }
        val session = Session.login(BatchVectors.message("spaet"))
        session.shutdownLoop(backend, gate).run()
        assertEquals(1, session.replies.size)
        assertEquals(shutdown, session.tokens(0).errorMessage())
        assertEquals(DoneStatus.ERROR, session.tokens(0).lastDone().status)
        assertEquals(0, backend.requests.size, "das Backend wird nicht gerufen")
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertTrue(session.channel.closed)
        assertEquals(CloseReason.REQUEST_ENDED, session.ready.live.closeReason)
        assertEquals(listOf(true, false), gate.running.toList())
    }

    /**
     * Eine laufende Anfrage schließt im Beenden regulär ab (die Antwort ist vollständig, kein halber Tokenstrom); eine
     * zweite Anfrage, die schon eingetroffen ist, erhält `TDS_SHUTDOWN` statt einer Ausführung, danach endet die
     * Verbindung. Die Verbindung gilt bis dahin als „Anfrage läuft“.
     *
     * @spec SPEC-044
     * @spec SPEC-049
     * @spec SPEC-031
     */
    @Test
    fun `Die laufende Anfrage endet regulaer, die vorausgeschickte zweite erhaelt TDS_SHUTDOWN`() = blocking {
        val release = CompletableDeferred<Unit>()
        val backend = FlowBackend(suspendedAfter(release, *oneRow), delivering(*oneRow))
        val gate = FakeGate()
        val session = Session.login(BatchVectors.message("laeuft"), BatchVectors.message("zweite"))
        val serving = launch { session.shutdownLoop(backend, gate).run() }
        backend.collecting.await()
        gate.begun = true
        release.complete(Unit)
        serving.join()
        assertEquals(2, session.replies.size)
        assertEquals(oneRowReply, session.hex(0))
        assertEquals(shutdown, session.tokens(1).errorMessage())
        assertEquals(1, backend.requests.size, "die zweite Anfrage erreicht das Backend nicht")
        assertEquals(0, backend.cancelled.size)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertEquals(listOf(true, false), gate.running.toList(), "zwischen den Anfragen bleibt sie 'läuft'")
    }

    /**
     * Endet die laufende Anfrage im Beenden regulär, bleibt die Verbindung als „läuft“ gemeldet und offen (`SPEC-044`
     * Nr. 3): eine Anfrage, die erst danach eintrifft (nicht vorausgeschickt), erhält `TDS_SHUTDOWN`; erst danach
     * meldet die Schleife „beendet“. Die Antwort auf die erste ist vor dem Eintreffen der zweiten vollständig geschrieben.
     *
     * @spec SPEC-044
     * @spec SPEC-049
     * @spec TDS-FA-07
     */
    @Test
    fun `Nach der regulaeren Antwort bleibt die Verbindung offen und eine spaetere Anfrage erhaelt TDS_SHUTDOWN`() =
        blocking {
            val release = CompletableDeferred<Unit>()
            val backend = FlowBackend(suspendedAfter(release, *oneRow), delivering(*oneRow))
            val gate = FakeGate()
            val session = Session.login(BatchVectors.message("laeuft"))
            val serving = launch { session.shutdownLoop(backend, gate).run() }
            backend.collecting.await()
            gate.begun = true
            release.complete(Unit)
            awaitUntil { session.replies.size == 1 }
            awaitUntil { session.ready.live.context.state == ConnectionState.READY }
            assertEquals(listOf(true), gate.running.toList(), "die Verbindung bleibt als 'läuft' gemeldet")
            assertEquals(oneRowReply, session.hex(0))
            session.channel.feed(BatchVectors.message("spaet"))
            serving.join()
            assertEquals(shutdown, session.tokens(1).errorMessage())
            assertEquals(1, backend.requests.size)
            assertEquals(listOf(true, false), gate.running.toList())
        }

    /**
     * Nach Ablauf von `SHUTDOWN_GRACE` bricht der angemeldete Abbruch die laufende Anfrage wie ATTENTION ab (`SPEC-044`
     * Nr. 4): genau ein `cancel()`, die Antwort endet mit genau einem `DONE[ATTN]` ohne ERROR, danach endet die
     * Verbindung (`ADR-0027` Entscheidung 2c), und die Schleife kehrt zurück.
     *
     * @spec SPEC-044
     * @spec SPEC-029
     * @spec TDS-FA-07
     */
    @Test
    fun `Der Ablauf der Frist bricht die Anfrage wie ATTENTION ab und beendet die Verbindung`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred(), *oneRow.take(2).toTypedArray()))
        val gate = FakeGate()
        val session = Session.login(BatchVectors.message("lang"), BatchVectors.message("nie"))
        val serving = launch { session.shutdownLoop(backend, gate).run() }
        backend.collecting.await()
        assertEquals(ConnectionState.STREAMING, session.ready.live.context.state)
        gate.begun = true
        checkNotNull(gate.abort)()
        serving.join()
        assertEquals(1, session.replies.size)
        assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ATTN), session.hex(0))
        assertTrue(session.tokens(0).none { it is ErrorToken })
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
        assertEquals(1, backend.requests.size, "die zweite Anfrage wird nicht ausgeführt")
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertTrue(session.channel.closed)
    }

    /**
     * Der Abbruch nach Ablauf der Frist trifft ohne laufende Anfrage (die Verbindung wartet im Beenden auf eine weitere
     * Nachricht) nur den Kanal: er wird geschlossen, es gibt keinen Cancel und keine Bestätigung.
     *
     * @spec SPEC-044
     */
    @Test
    fun `Der Abbruch ohne laufende Anfrage schliesst den Kanal und schreibt nichts`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val gate = FakeGate()
        val session = Session.login(BatchVectors.message("kurz"))
        val serving = launch { session.shutdownLoop(backend, gate).run() }
        awaitUntil { session.replies.size == 1 }
        awaitUntil { session.ready.live.context.state == ConnectionState.READY }
        checkNotNull(gate.abort)()
        assertTrue(session.channel.closed, "ohne laufende Anfrage schließt der Abbruch den Kanal")
        assertEquals(1, session.replies.size)
        assertEquals(0, backend.cancelled.size)
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
    }

    /**
     * Ein Backend, das den Abbruch nach Ablauf der Frist ignoriert, hält die Verbindung höchstens `CANCEL_TIMEOUT`:
     * danach schließt die Schleife ohne weitere Bytes (gesteuerte Zeit); `cancel()` ist genau einmal gerufen.
     *
     * @spec SPEC-044
     * @spec SPEC-033
     */
    @Test
    fun `Ein Backend, das den Abbruch im Beenden ignoriert, haelt die Verbindung hoechstens CANCEL_TIMEOUT`() =
        blocking {
            val pause = SteeredPause()
            val gate = CompletableDeferred<Unit>()
            val backend = FlowBackend(ignoringCancel(gate, resultStart()))
            withGate(gate) {
                val fake = FakeGate()
                val session = Session.login(BatchVectors.message("haengt"))
                val times = RequestTimes(30.seconds, 5.seconds, now = { FIXED_NOW }, pause = pause::pause)
                val serving = launch { session.shutdownLoop(backend, fake, times).run() }
                backend.collecting.await()
                fake.begun = true
                launch { checkNotNull(fake.abort)() }
                pause.awaitWaiting(5.seconds)
                assertFalse(session.channel.closed, "vor CANCEL_TIMEOUT bleibt die Verbindung")
                pause.fire(5.seconds)
                serving.join()
                assertTrue(session.channel.closed)
                assertEquals(0, session.replies.size)
                assertEquals(CloseReason.CANCEL_TIMEOUT, session.ready.live.closeReason)
                gate.complete(Unit)
                backend.closed.await()
                assertEquals(1, backend.cancelled.size)
                assertNotNull(session.ready.live.closeReason)
            }
        }
}
