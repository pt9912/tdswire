package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.FIXED_NOW
import io.github.pt9912.tdswire.protocol.dispatch.RequestTimes
import io.github.pt9912.tdswire.protocol.dispatch.Session
import io.github.pt9912.tdswire.protocol.dispatch.SteeredPause
import io.github.pt9912.tdswire.protocol.dispatch.errorMessage
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimulationBackend
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * `WAITFOR DELAY` (`SPEC-036` Form Z, `ADR-0058` Entscheidung 5) am Simulations-Backend hinter dem Protokollkern:
 * der wartende Befehl, ATTENTION und `QUERY_TIMEOUT`. Gegenstelle ohne Netz mit Rohbytes-Handvektoren; die Bytes der
 * beiden `DONE` bei ATTENTION sind die der Aufnahmen `cancel-wartend` und `timeout-wartend` (`BatchVectors.DONE_ERROR`,
 * `DONE_ATTN`, `AttentionCancelTest`). Die Uhr des Backends ([backendClock]) und die der Fristen des Protokollkerns
 * ([requestClock]) steuert der Test; kein Test wartet auf die Wanduhr. Das Ende der Wartezeit ohne Abbruch und
 * `TDS_TIMEOUT` durch `QUERY_TIMEOUT` sind Setzungen, am Server nicht gemessen.
 */
class WaitForWireTest {
    private companion object {
        /** `DONE` mit `COUNT`, Befehl 0 (Art `UNKNOWN`), Zähler 0: Token, Status `0x0010`, Befehl, acht Byte Zähler. */
        const val DONE_COUNT_0_UNKNOWN = "FD 10 00 00 00 00 00 00 00 00 00 00 00"
    }

    private val wait = "WAITFOR DELAY '00:00:10'"
    private val timeoutMessage = ServerMessage(50005, 1, 16, "Timeout.")

    private val backendClock = SteeredPause()
    private val requestClock = SteeredPause()
    private val backend = SimulationBackend({ SimCatalog(emptyList()) }, null, backendClock::pause)

    private fun times(query: Duration = 30.seconds) =
        RequestTimes(query, 5.seconds, now = { FIXED_NOW }, pause = requestClock::pause)

    /** Die Antwort auf `SELECT 1` der Folgeabfrage: Metadaten, eine Zeile, `DONE` mit Zähler. */
    private fun Session.assertAnsweredSelectOne(index: Int) {
        val tokens = tokens(index)
        assertTrue(tokens.first() is ColMetadataToken, "$tokens")
        assertEquals(1, tokens.count { it is RowToken })
        assertTrue(tokens.last() is DoneToken)
    }

    /**
     * Ein ATTENTION im Warten (der Befehl ist `RUNNING`, es ist nichts gesendet) beendet die Wartezeit und endet im
     * Paar `DONE[ERROR]` (Befehl `0x00fd`, ohne ERROR-Token) und `DONE[ATTN]` (Handvektoren, `ADR-0054`); die
     * Verbindung bleibt offen, die Uhr des Backends ist abgebrochen, und die unmittelbar folgende Anfrage erhält nur
     * ihre eigene Antwort.
     * Wächter: ein Backend, das den Cancel nicht beachtet (dann bliebe die Uhr stehen und die Verbindung schlösse nach
     * `CANCEL_TIMEOUT`).
     * @spec SPEC-029
     * @spec SPEC-036
     * @spec TDS-FA-07
     */
    @Test
    fun `ATTENTION im Warten endet im Paar und laesst die Verbindung offen`() = blocking {
        val session = Session.login(BatchVectors.message(wait))
        val serving = launch { session.serve(backend, times = times()) }
        backendClock.awaitWaiting(10.seconds)
        yield()
        assertEquals(ConnectionState.RUNNING, session.ready.live.context.state)
        assertEquals(0, session.replies.size, "ein wartender Befehl hat noch nichts gesendet")

        session.channel.feed(BatchVectors.attention())
        session.channel.feed(BatchVectors.message("SELECT 1"))
        session.channel.feed(Inbound.EndOfStream)
        serving.join()

        assertEquals(3, session.replies.size)
        assertEquals(BatchVectors.DONE_ERROR, session.hex(0))
        assertEquals(BatchVectors.DONE_ATTN, session.hex(1))
        assertTrue(session.tokens(0).none { it is ErrorToken } && session.tokens(1).none { it is ErrorToken })
        session.assertAnsweredSelectOne(2)
        assertFalse(backendClock.isWaiting(10.seconds), "die Uhr des Backends ist abgebrochen")
        assertTrue(session.ready.live.closeReason != CloseReason.CANCEL_TIMEOUT)
    }

    /**
     * `QUERY_TIMEOUT` kürzer als die Wartezeit (Setzung): der Ablauf der Frist geht den Weg des Cancel, die Antwort ist
     * ERROR 50005 (`TDS_TIMEOUT`, Class 16) und `DONE` mit Fehlerstatus, die Verbindung bleibt offen, die Uhr des
     * Backends ist abgebrochen, die Folgeabfrage gelingt.
     * Wächter: ein Backend, das die Wartezeit über die Frist hinaus hält (dann endete die Anfrage nicht mit dem
     * Timeout).
     * @spec SPEC-009
     * @spec SPEC-016
     * @spec SPEC-036
     */
    @Test
    fun `QUERY_TIMEOUT kuerzer als die Wartezeit endet mit TDS_TIMEOUT`() = blocking {
        val session = Session.login(BatchVectors.message(wait))
        val serving = launch { session.serve(backend, times = times(query = 2.seconds)) }
        backendClock.awaitWaiting(10.seconds)
        requestClock.awaitWaiting(2.seconds)

        requestClock.fire(2.seconds)
        session.channel.feed(BatchVectors.message("SELECT 1"))
        session.channel.feed(Inbound.EndOfStream)
        serving.join()

        assertEquals(2, session.replies.size)
        assertEquals(timeoutMessage, session.tokens(0).errorMessage())
        assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR), session.hex(0))
        session.assertAnsweredSelectOne(1)
        assertFalse(backendClock.isWaiting(10.seconds))
    }

    /**
     * Das Ende der Wartezeit ohne Abbruch (Setzung, am Server nicht gemessen): die Antwort ist ein einziges `DONE` mit
     * Zähler 0, wie die Antwort auf `SET`; die Frist der Anfrage ist danach ohne Wirkung, und die Verbindung bleibt
     * offen.
     * @spec SPEC-036
     */
    @Test
    fun `das Ende der Wartezeit antwortet mit dem Zaehler 0`() = blocking {
        val session = Session.login(BatchVectors.message(wait))
        val serving = launch { session.serve(backend, times = times()) }
        backendClock.awaitWaiting(10.seconds)

        backendClock.fire(10.seconds)
        session.channel.feed(Inbound.EndOfStream)
        serving.join()

        assertEquals(1, session.replies.size)
        assertEquals(DONE_COUNT_0_UNKNOWN, session.hex(0))
        assertFalse(requestClock.isWaiting(30.seconds), "die Frist der Anfrage ist mit dem Ende abgebrochen")
    }
}
