package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.WireSession
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rückstau, Packet Size der Sitzung, Abbruch und Senkenfehler der Antwort eines SQL_BATCH über den
 * echten Transport (Leser, Schreiber, Paket-Codec) auf einem Draht ohne Netz ([WireSession]) gegen das
 * Test-Backend [ScriptedBackend]. Das Test-Backend suspendiert und blockiert nie; die Tests sagen nichts
 * über ein blockierendes oder echtes Backend, das Simulations-Backend oder einen Socket. Die
 * Pakete- und Bytezahlen sind aus den Handvektoren der Token (COLMETADATA 13 Byte, ROW 5 Byte, DONE
 * 13 Byte für eine Int-Spalte) gerechnet.
 */
class StreamingDispatchTest {
    private val packetSize = 512
    private val chunk = packetSize - 8

    /** Handvektoren der Int-Spalte: COLMETADATA und DONE je 13 Byte, eine ROW 5 Byte. */
    private val metaBytes = 13
    private val rowBytes = 5
    private val doneBytes = 13

    private val internalError = ServerMessage(50007, 1, 20, "Internal error.")

    private fun payloadBytes(rows: Int) = metaBytes + rows * rowBytes + doneBytes

    /** Eine Senke, die zählt, was ihr angeboten wird. */
    private class Probe(private val inner: TokenSink) : TokenSink {
        var offered = 0
            private set

        override suspend fun accept(token: Token): Boolean {
            offered++
            return inner.accept(token)
        }
    }

    /**
     * Die Packet Size der Sitzung (aus LOGIN7, hier 512 und 1000) bestimmt die Paketgröße der Antwort:
     * alle Pakete außer dem letzten haben genau diese Länge, das letzte höchstens, nur das letzte trägt
     * EOM, die IDs zählen ab 1; die Nutzdaten ergeben die erwartete Bytezahl, und die Zahl der Pakete ist
     * der aufgerundete Quotient. Die Packet Size des Schreibers vor dem Login (4096) gilt nicht mehr.
     *
     * @spec SPEC-028
     * @spec SPEC-031
     */
    @Test
    fun `Die Antwort hat Pakete in der Packet Size der Sitzung`() = blocking {
        listOf(512, 1000).forEach { size ->
            val rows = 700
            val session = WireSession.login(size, "select", retain = true)
            session.serve(ScriptedBackend.generating(rows))
            val packets = session.wire.packets
            val perPacket = size - 8
            assertEquals((payloadBytes(rows) + perPacket - 1) / perPacket, packets.size, "$size")
            assertTrue(packets.size > 2, "$size")
            assertEquals(List(packets.size - 1) { size }, packets.dropLast(1).map { it.length }, "$size")
            assertTrue(packets.last().length <= size, "$size")
            assertEquals(List(packets.size - 1) { false } + true, packets.map { it.endOfMessage }, "$size")
            assertEquals(List(packets.size) { it + 1 }, packets.map { it.packetId }, "$size")
            assertEquals(payloadBytes(rows).toLong(), session.wire.payloadBytes, "$size")
        }
    }

    /**
     * Rückstau, gezählt: nimmt der Draht nach vier Paketen nichts mehr an, steht der Lauf still, und das
     * Test-Backend hat höchstens die Ereignisse geliefert, die in vier Paketen, dem Paket im Schreiben
     * und der Zeile in Arbeit Platz haben, und mindestens die der vier Pakete; 20 000 Zeilen stehen
     * bereit. Der Stillstand ist bewiesen, nicht abgewartet: der Draht meldet sein Warten, und die
     * Anfrageschleife ist zu diesem Zeitpunkt nicht beendet. Nach dem Öffnen geht die ganze Antwort
     * hinaus, ohne Zeile zu verlieren.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein haltender Draht begrenzt die Ereignisse, die das Backend vorausliefert`() = blocking {
        val rows = 20_000
        val stallPackets = 4
        val session = WireSession.login(packetSize, "select") { stallAfterBytes = stallPackets.toLong() * packetSize }
        val backend = ScriptedBackend.generating(rows)
        val serving = launch { session.serve(backend) }
        val stalled = select {
            session.wire.stalled.onAwait { true }
            serving.onJoin { false }
        }
        assertTrue(stalled, "der Lauf endete, ohne dass der Draht zum Halten kam")
        assertEquals(stallPackets, session.wire.packetCount)
        val consumedMax = (stallPackets + 1) * chunk + rowBytes
        val upper = 1 + (consumedMax - metaBytes) / rowBytes + 1
        val lower = (stallPackets * chunk - metaBytes) / rowBytes
        assertTrue(backend.delivered in lower..upper, "geliefert: ${backend.delivered}, erlaubt $lower..$upper")
        assertTrue(backend.delivered < rows / 10)
        assertTrue(serving.isActive)
        session.wire.release()
        serving.join()
        assertEquals(rows + 2, backend.delivered)
        assertEquals(payloadBytes(rows).toLong(), session.wire.payloadBytes)
        assertTrue(session.wire.tail[1].toInt() and 1 == 1, "das letzte Paket trägt EOM")
    }

    /**
     * Ein Cancel der Coroutine, die die Anfrage bedient (Disconnect), während der Draht hält: das Lesen
     * endet, `cancel()` und `close()` am Backend kommen je genau einmal, der Kanal ist geschlossen, es
     * wird kein weiteres Ereignis geliefert, und die nächste Nachricht wird nicht beantwortet.
     *
     * @spec SPEC-031
     * @spec SPEC-029
     */
    @Test
    fun `Der Abbruch bei haltendem Draht ruft cancel und close genau einmal`() = blocking {
        val session = WireSession.login(packetSize, "first", "never") { stallAfterBytes = 4L * packetSize }
        val backend = ScriptedBackend.generating(20_000)
        val serving = launch { session.serve(backend) }
        session.wire.stalled.await()
        val deliveredAtStall = backend.delivered
        serving.cancelAndJoin()
        assertEquals(1, backend.cancelled.size)
        assertEquals("s1", backend.cancelled.single().first)
        assertEquals(backend.requests.single().requestId, backend.cancelled.single().second)
        assertEquals(1, backend.closeCalls)
        assertEquals(deliveredAtStall, backend.delivered)
        assertTrue(session.wire.closed)
        assertEquals(1, backend.requests.size)
        assertEquals(4, session.wire.packetCount)
    }

    /**
     * Ein Senkenfehler (der Kanal nimmt das dritte Paket nicht an): das Lesen endet beim Token, das
     * nicht geschrieben ging, `cancel()` und `close()` kommen je genau einmal, kein Ereignis kommt
     * danach, die Sitzung ist wegen `WRITE_FAILED` geschlossen, es geht kein EOM-Paket hinaus, und die
     * zweite Nachricht wird nicht ausgeführt.
     *
     * @spec SPEC-031
     * @spec SPEC-048
     */
    @Test
    fun `Ein Senkenfehler beendet das Lesen und schliesst die Ausfuehrung einmal`() = blocking {
        val session = WireSession.login(packetSize, "first", "never") { failAfterPackets = 2 }
        val backend = ScriptedBackend.generating(20_000)
        var probe: Probe? = null
        session.serve(backend, sinkAround = { Probe(it).also { created -> probe = created } })
        val offered = checkNotNull(probe)
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
        assertEquals(backend.delivered, offered.offered)
        assertTrue(backend.delivered < 20_000 / 10)
        assertEquals(2, session.wire.packetCount)
        assertTrue(session.wire.packets.none { it.endOfMessage })
        assertEquals(CloseReason.WRITE_FAILED, session.ready.live.closeReason)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertEquals(1, backend.requests.size)
    }

    /**
     * Ohne Fehler und Abbruch kommt `cancel()` nicht: eine vollständige Antwort und eine Antwort mit
     * Backend-Fehler nach Teilzeilen rufen nur `close()`, genau einmal.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Eine beendete Antwort ruft cancel nicht`() = blocking {
        val complete = ScriptedBackend.generating(10)
        WireSession.login(packetSize, "q").serve(complete)
        assertEquals(emptyList(), complete.cancelled)
        assertEquals(1, complete.closeCalls)

        val failing = ScriptedBackend.ofList(
            resultStart(),
            QueryEvent.Row(listOf(1)),
            QueryEvent.Failure("TDS_BACKEND_ERROR", "kein Text fuer den Client"),
        )
        WireSession.login(packetSize, "q").serve(failing)
        assertEquals(emptyList(), failing.cancelled)
        assertEquals(1, failing.closeCalls)
    }

    /** Ein Backend, dessen Strom nach [rowsBefore] Zeilen mit [error] endet. */
    private class ThrowingBackend(private val error: Throwable, private val rowsBefore: Int) : QueryBackend {
        var closeCalls = 0
            private set

        override suspend fun execute(request: QueryRequest): QueryExecution = object : QueryExecution {
            override val events: Flow<QueryEvent> = flow {
                emit(resultStart())
                repeat(rowsBefore) { emit(QueryEvent.Row(listOf(it))) }
                throw error
            }

            override suspend fun close() {
                closeCalls++
            }
        }

        override suspend fun cancel(sessionId: String, requestId: String) = Unit
    }

    private fun WireSession.tokens(): List<Token> = TokenDecoder(wire.payload()).decodeAll()

    /**
     * Ein `OutOfMemoryError` aus dem Backend, aus der Senke (auch als Folge des Encoders) und beim
     * Schreiben von ERROR und DONE selbst (ADR-0013, Proposed): Backend und Senke ergeben nach den
     * Teilzeilen ERROR (`TDS_INTERNAL_ERROR`, Class 20) und DONE mit Fehlerstatus, ohne den Text der
     * Ausnahme, die Verbindung schließt nach der Antwort und das Backend ist geschlossen; scheitert auch
     * das Schreiben der Fehlerantwort, schließt die Verbindung ohne Antwort (kein EOM-Paket). Der Fehler
     * wird nur mit einem Testdouble ausgelöst, nie durch einen echten Heap-Überlauf.
     *
     * @spec SPEC-048
     * @spec SPEC-031
     */
    @Test
    fun `Ein OutOfMemoryError im Anfragepfad ergibt TDS_INTERNAL_ERROR und schliesst`() = blocking {
        val backendFault = WireSession.login(packetSize, "q", "never", retain = true)
        val throwing = ThrowingBackend(OutOfMemoryError("Text darf nicht zum Client"), rowsBefore = 2)
        backendFault.serve(throwing)
        assertErrorAnswer(backendFault, "Backend")
        assertEquals(1, throwing.closeCalls)

        val sinkFault = WireSession.login(packetSize, "q", "never", retain = true)
        val scripted = ScriptedBackend.generating(5)
        sinkFault.serve(scripted, sinkAround = { inner -> ThrowOnce(inner, at = 4) })
        assertErrorAnswer(sinkFault, "Senke")
        assertEquals(1, scripted.closeCalls)
        assertEquals(emptyList(), scripted.cancelled)

        val silent = WireSession.login(packetSize, "q", "never", retain = true)
        val dying = ScriptedBackend.generating(5)
        silent.serve(dying, sinkAround = { inner -> ThrowOnce(inner, at = 4, repeated = true) })
        assertEquals(1, dying.closeCalls)
        assertEquals(1, dying.cancelled.size)
        assertEquals(ConnectionState.CLOSED, silent.ready.live.context.state)
        assertTrue(silent.wire.packets.none { it.endOfMessage })
        assertEquals(1, dying.requests.size)
    }

    private fun assertErrorAnswer(session: WireSession, name: String) {
        val tokens = session.tokens()
        assertEquals(internalError, tokens.filterIsInstance<ErrorToken>().single().message, name)
        assertEquals(DoneStatus.ERROR, tokens.lastDone().status, name)
        assertTrue(tokens.filterIsInstance<RowToken>().size >= 2, name)
        assertTrue(session.wire.packets.last().endOfMessage, name)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state, name)
        assertFalse(String(session.wire.payload(), Charsets.UTF_16LE).contains("Text darf nicht"), name)
    }

    /** Wirft ab dem [at]-ten Token einen `OutOfMemoryError`: einmal, oder bei [repeated] bei jedem weiteren. */
    private class ThrowOnce(private val inner: TokenSink, private val at: Int, private val repeated: Boolean = false) :
        TokenSink {
        private var offered = 0

        override suspend fun accept(token: Token): Boolean {
            offered++
            if (offered == at || (repeated && offered > at)) throw OutOfMemoryError("Text darf nicht zum Client")
            return inner.accept(token)
        }
    }

    /**
     * Ein anderer `Error` als `OutOfMemoryError` wird nicht gefangen: er läuft aus der Anfrageschleife
     * nach außen, die Ausführung wird trotzdem geschlossen und der Kanal geschlossen.
     *
     * @spec SPEC-048
     */
    @Test
    fun `Ein anderer Error laeuft nach aussen`() = blocking {
        class Other : Error("anderer Fehler")
        val session = WireSession.login(packetSize, "q")
        val backend = ScriptedBackend.generating(5)
        assertFailsWith<Other> { session.serve(backend, sinkAround = { inner -> Thrower(inner, Other()) }) }
        assertEquals(1, backend.closeCalls)
        assertTrue(session.wire.closed)
    }

    private class Thrower(private val inner: TokenSink, private val error: Error) : TokenSink {
        private var offered = 0

        override suspend fun accept(token: Token): Boolean {
            if (++offered == 3) throw error
            return inner.accept(token)
        }
    }
}
