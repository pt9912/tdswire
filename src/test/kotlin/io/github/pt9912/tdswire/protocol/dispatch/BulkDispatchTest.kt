package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.BulkLoadingBackend
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.prepExec
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Sitzungszustand „Massenladen erwartet“ der `RequestLoop` (`SPEC-036`, Pakettypen; `ADR-0033` Nr. 5) gegen ein
 * Test-Backend mit der Fähigkeit [BulkLoadingBackend]. Die Nachrichten 0x07 sind die Nutzlast des Mitschnitts von
 * mssql-jdbc (`BulkLoadReaderTest`); die Antworten sind DONE-Token, im Draht von Hand gelesen. Nicht gemessen am echten
 * Server: die Antwort auf `INSERT BULK` (DONE mit Zähler 0) und auf eine Nachricht mit IGNORE-Bit (ebenso, Setzung).
 */
class BulkDispatchTest {
    private val insertBulk = "INSERT BULK [dbo].[t] ([Id] BIGINT , [Name] NVARCHAR(4000) ) with (CHECK_CONSTRAINTS)"

    private fun batch(text: String) = BatchVectors.message(text)

    private fun bulk(payload: String = THREE_ROWS, ignore: Boolean = false): Inbound =
        Inbound.Message(TdsMessage(PacketType.BULK_LOAD, hex(payload), ignore = ignore))

    private fun rpc(payload: ByteArray) = BatchVectors.raw(PacketType.RPC, payload)

    private fun Session.done(index: Int): DoneToken = tokens(index).single() as DoneToken

    private val unsupported = ServerMessage(50001, 1, 16, "Unsupported SQL.")

    /** Das Test-Backend: erkennt `INSERT BULK`, merkt die Aufrufe und antwortet mit [onLoad]. */
    private class Loading(
        private val target: BulkTargetResult = BulkTargetResult.Ready(BulkTarget("dbo", "t")),
        private val onLoad: (BulkRows) -> QueryEvent = { QueryEvent.UpdateCount(it.rows.size.toLong()) },
    ) : QueryBackend,
        BulkLoadingBackend {
        val asked = mutableListOf<String>()
        val executed = mutableListOf<String>()
        val loads = mutableListOf<Pair<BulkTarget, BulkRows>>()

        override fun bulkTarget(sql: String): BulkTargetResult? {
            asked += sql
            return target.takeIf { sql.startsWith("INSERT BULK") }
        }

        override suspend fun load(target: BulkTarget, rows: BulkRows): QueryEvent {
            loads += target to rows
            return onLoad(rows)
        }

        override suspend fun execute(request: QueryRequest): QueryExecution {
            executed += request.sql
            return object : QueryExecution {
                override val events: Flow<QueryEvent> = flowOf(QueryEvent.UpdateCount(7))

                override suspend fun close() = Unit
            }
        }

        override suspend fun cancel(sessionId: String, requestId: String) = Unit
    }

    /**
     * Happy: `INSERT BULK` setzt das Ziel und wird mit DONE und dem Zähler 0 beantwortet, ohne `execute`; die
     * Nachricht 0x07 lädt ihre drei Zeilen in dieses Ziel und wird mit DONE und dem Zähler des Backends (3)
     * beantwortet;
     * die Verbindung bleibt synchron (der Folge-Batch bekommt seine Antwort). Wächter: ein Backend-Aufruf für
     * `INSERT BULK`, ein Ziel, das nicht ankommt, ein Zähler 0 statt 3, eine Verbindung, die schließt.
     *
     * @spec SPEC-036
     * @spec SPEC-007
     */
    @Test
    fun `INSERT BULK und die Nachricht 0x07 laden die Zeilen in das Ziel`() = blocking {
        val backend = Loading()
        val session = Session.login(batch(insertBulk), bulk(), batch("SELECT 1"), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(3, session.replies.size)
        assertEquals(DoneToken(DoneKind.DONE, DoneStatus.COUNT, 0, 0), session.done(0))
        assertEquals(DoneToken(DoneKind.DONE, DoneStatus.COUNT, 0, 3), session.done(1))
        assertEquals(listOf(BulkTarget("dbo", "t")), backend.loads.map { it.first })
        assertEquals(3, backend.loads.single().second.rows.size)
        assertEquals(listOf("SELECT 1"), backend.executed)
        assertTrue(session.channel.exhausted)
    }

    /**
     * Das Ziel bleibt nach einer Nachricht stehen: zwei Nachrichten 0x07 hintereinander laden beide (der Schlussteil
     * des
     * Treibers ohne Zeile ist dort eine eigene Nachricht). Eine andere Anfrage löscht es: ein RPC und ein weiterer
     * Batch dazwischen machen die nächste Nachricht 0x07 zu einem Fehler. Wächter: ein Ziel, das nach dem Laden
     * entfällt; ein Ziel, das eine fremde Anfrage überlebt.
     *
     * @spec SPEC-036
     */
    @Test
    fun `das Ziel bleibt bis zur naechsten anderen Anfrage`() = blocking {
        val backend = Loading()
        val session = Session.login(
            batch(insertBulk),
            bulk(),
            bulk(EMPTY),
            rpc(prepExec(-1, "select 1", "")),
            bulk(),
            batch(insertBulk),
            batch("SELECT 1"),
            bulk(),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(listOf(3, 0), backend.loads.map { it.second.rows.size })
        assertEquals(unsupported, session.tokens(4).errorMessage())
        assertEquals(unsupported, session.tokens(7).errorMessage())
        assertEquals(2, backend.loads.size)
    }

    /**
     * Das IGNORE-Bit verwirft die Nachricht: das Backend lädt nichts, die Antwort ist DONE mit dem Zähler 0, die
     * Verbindung bleibt offen (Setzung). Wächter: eine Zeile im Backend, ein Fehler statt DONE.
     *
     * @spec SPEC-036
     */
    @Test
    fun `das IGNORE-Bit verwirft die Nachricht ohne Aufruf des Backends`() = blocking {
        val backend = Loading()
        val session = Session.login(batch(insertBulk), bulk(ignore = true), batch("SELECT 1"), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(emptyList(), backend.loads)
        assertEquals(DoneToken(DoneKind.DONE, DoneStatus.COUNT, 0, 0), session.done(1))
        assertEquals(listOf("SELECT 1"), backend.executed)
    }

    /**
     * Eine Nachricht 0x07 ohne vorangehendes `INSERT BULK` ruft das Backend nicht und ergibt `TDS_UNSUPPORTED_SQL` bei
     * offener Verbindung (Class 16). Wächter: ein Ziel, das aus dem Nichts kommt; eine geschlossene Verbindung.
     *
     * @spec SPEC-036
     */
    @Test
    fun `eine Nachricht 0x07 ohne INSERT BULK ist ein Fehler bei offener Verbindung`() = blocking {
        val backend = Loading()
        val session = Session.login(bulk(), batch("SELECT 1"), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(emptyList(), backend.loads)
        assertEquals(unsupported, session.tokens(0).errorMessage())
        assertEquals(DoneStatus.ERROR, session.tokens(0).lastDone().status)
        assertEquals(listOf("SELECT 1"), backend.executed)
    }

    /**
     * Ein Fehler des Backends beim Laden (Failure) ist ERROR mit DONE und Fehlerstatus; die Verbindung bleibt synchron
     * und beantwortet die nächste Anfrage (TDS-QA-07). Eine Ablehnung von `INSERT BULK` setzt kein Ziel: die
     * folgende Nachricht 0x07 ist ein Fehler. Wächter: ein verschlucktes Failure, ein Ziel nach der Ablehnung.
     *
     * @spec SPEC-036
     * @spec SPEC-007
     */
    @Test
    fun `ein Fehler beim Laden und die Ablehnung lassen die Verbindung synchron`() = blocking {
        val failing = Loading(onLoad = { QueryEvent.Failure("TDS_BACKEND_ERROR", "x") })
        val session = Session.login(batch(insertBulk), bulk(), batch("SELECT 1"), Inbound.EndOfStream)
        session.serve(failing)
        assertEquals(ServerMessage(50004, 1, 16, "Backend error."), session.tokens(1).errorMessage())
        assertEquals(listOf("SELECT 1"), failing.executed)

        val rejecting = Loading(target = BulkTargetResult.Rejected("TDS_SIM_INVALID_VALUE", "x"))
        val second = Session.login(batch(insertBulk), bulk(), batch("SELECT 1"), Inbound.EndOfStream)
        second.serve(rejecting)
        assertEquals(ServerMessage(245, 1, 16, "Invalid value."), second.tokens(0).errorMessage())
        assertEquals(unsupported, second.tokens(1).errorMessage())
        assertEquals(emptyList(), rejecting.loads)
        assertEquals(listOf("SELECT 1"), rejecting.executed)
    }

    /**
     * Eine abgeschnittene Nachricht ruft das Backend nicht; die Antwort ist der Fehler des Parsers
     * (`TDS_PROTOCOL_ERROR`, Class 20, die Verbindung schließt wie bei einem SQL_BATCH, den der Parser abweist).
     * Wächter: ein Backend-Aufruf mit einem Teilergebnis, eine Ausnahme der Schleife.
     *
     * @spec SPEC-036
     * @spec TDS-QA-07
     */
    @Test
    fun `eine abgeschnittene Nachricht ergibt den Fehler des Parsers ohne Backend-Aufruf`() = blocking {
        val backend = Loading()
        val cut = THREE_ROWS.substring(0, THREE_ROWS.length / 4 * 2)
        val session = Session.login(batch(insertBulk), bulk(cut), batch("SELECT 1"), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(emptyList(), backend.loads)
        assertEquals(ServerMessage(50006, 1, 20, "Protocol error."), session.tokens(1).errorMessage())
        assertEquals(emptyList(), backend.executed)
    }

    /**
     * Ein Backend ohne die Fähigkeit sieht `INSERT BULK` als gewöhnlichen Text (`execute`), und die Nachricht 0x07 ist
     * `TDS_UNSUPPORTED_SQL`. Wächter: eine Nachricht 0x07, die an ein Backend ohne Massenladen geht.
     *
     * @spec SPEC-036
     */
    @Test
    fun `ein Backend ohne Massenladen bekommt den Text und lehnt die Nachricht ab`() = blocking {
        val backend = ScriptedBackend.ofList(QueryEvent.UpdateCount(1))
        val session = Session.login(batch(insertBulk), bulk(), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(listOf(insertBulk), backend.requests.map { it.sql })
        assertEquals(unsupported, session.tokens(1).errorMessage())
    }

    private companion object {
        /** Mitschnitt von mssql-jdbc, siehe `BulkLoadReaderTest`. */
        const val THREE_ROWS =
            "81030000000000000026080249006400000000000100e7401f0904d00034044e0061006d006500000000000100" +
                "a70a000904d0003408450069006e00740072006900740074" +
                "00d10801000000000000000e004100640061002000dc006e00ef000a00323032362d30312d3032" +
                "d1080200000000000000ffffffffd10803000000000000000e004300e400630069006c00690065000a" +
                "00323032352d31322d3331fd000000000000000000000000"

        const val EMPTY =
            "81030000000000000026080249006400000000000100e7401f0904d00034044e0061006d006500000000000100" +
                "a70a000904d0003408450069006e00740072006900740074" +
                "00fd000000000000000000000000"
    }
}
