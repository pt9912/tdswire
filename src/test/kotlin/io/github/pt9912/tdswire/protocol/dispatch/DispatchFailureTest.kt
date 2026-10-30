package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.FakeHandshakeChannel
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.HandshakeDriver
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.session.RecordingAdapter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fehlerfälle des Dispatch von SQL_BATCH (`TDS-FA-03`, `SPEC-031`, `SPEC-048`): Fehler des Backends bei
 * offener Verbindung, schließende Ausgänge, ungültige Kodierung und Nachrichten, die der Dispatch nicht
 * beantwortet. Die Zuordnung der Codes, Classes und Verbindungsfolgen ist Setzung des Nutzers oder
 * Festlegung (`ErrorCode`), nicht am echten SQL Server gemessen; die Nummern unten sind von Hand
 * eingetragen und teilen keinen Code mit dem Mapping.
 */
class DispatchFailureTest {
    private val oneRow = listOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private val unsupportedSql = ServerMessage(50001, 1, 16, "Unsupported SQL.")
    private val unsupportedType = ServerMessage(50003, 1, 20, "Unsupported type.")
    private val internal = ServerMessage(50007, 1, 20, "Internal error.")
    private val timeout = ServerMessage(50005, 1, 20, "Timeout.")
    private val protocolError = ServerMessage(50006, 1, 20, "Protocol error.")

    /**
     * Meldet das Backend vor jeder Zeile einen Fehler (`TDS_UNSUPPORTED_SQL`, Class 16), antwortet der Dispatch
     * mit ERROR und DONE mit Fehlerstatus (Handvektor für DONE); die Verbindung bleibt offen, und das
     * Folgebatch derselben Sitzung wird erfolgreich beantwortet.
     *
     * @spec SPEC-031
     * @spec SPEC-048
     */
    @Test
    fun `Ein Fehler des Backends vor jeder Zeile laesst die Verbindung offen`() = blocking {
        val failure = listOf<QueryEvent>(QueryEvent.Failure("TDS_UNSUPPORTED_SQL", "kein Text fuer den Client"))
        val backend = ScriptedBackend.inSequence(failure, oneRow)
        val session = Session.login(BatchVectors.message("bad"), BatchVectors.message("good"), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(2, session.replies.size)
        assertEquals(unsupportedSql, session.tokens(0).errorMessage())
        assertEquals(2, session.tokens(0).size)
        assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR), session.hex(0))
        assertEquals(DoneStatus.ERROR, session.tokens(0).lastDone().status)
        val backendText = "kein Text".toByteArray(Charsets.UTF_16LE).toHex()
        assertFalse(session.hex(0).contains(backendText), "Text des Backends in der Antwort")
        assertEquals(listOf("bad", "good"), backend.requests.map { it.sql })
        assertEquals(2, backend.closeCalls)
        assertEquals(3, session.channel.receiveCalls - 2)
    }

    /**
     * Jeder Ausgang, der die Verbindung schließt, endet nicht als offene Verbindung: die Antwort mit ERROR (Class
     * 20) und DONE mit Fehlerstatus geht hinaus, der Dispatch liest die bereits eingereihte nächste
     * Nachricht nicht mehr, die Sitzung ist `CLOSED`, der Kanal geschlossen, und das Backend ist
     * geschlossen. Die Fälle: Typfehler (`ReturnStatus`, `OutputParameter`), Reihenfolgeverstoß, fremder und
     * nur dem Server vorbehaltener Code, `TDS_TIMEOUT` (Class 20 ohne Gruppe).
     *
     * @spec SPEC-031
     * @spec SPEC-048
     * @spec SPEC-014
     */
    @Test
    fun `Ein schliessender Ausgang endet nicht als offene Verbindung`() = blocking {
        val cases = mapOf(
            "ReturnStatus" to (listOf<QueryEvent>(QueryEvent.ReturnStatus(1)) to unsupportedType),
            "OutputParameter" to
                (listOf<QueryEvent>(QueryEvent.OutputParameter("p", SqlType.Int32, 1)) to unsupportedType),
            "Zeile ohne Ergebnis" to (listOf<QueryEvent>(QueryEvent.Row(listOf(1))) to internal),
            "fremder Code" to (listOf<QueryEvent>(QueryEvent.Failure("NOPE", "x")) to internal),
            "Code des Servers" to (listOf<QueryEvent>(QueryEvent.Failure("TDS_LOGIN_FAILED", "x")) to internal),
            "TDS_TIMEOUT" to (listOf<QueryEvent>(QueryEvent.Failure("TDS_TIMEOUT", "x")) to timeout),
        )
        cases.forEach { (name, case) ->
            val backend = ScriptedBackend.ofList(*case.first.toTypedArray())
            val session = Session.login(BatchVectors.message("q"), BatchVectors.message("never read"))
            session.serve(backend)
            assertEquals(1, session.replies.size, name)
            assertEquals(case.second, session.tokens(0).errorMessage(), name)
            assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR), name)
            assertTrue(session.channel.closed, name)
            assertFalse(session.channel.exhausted, "$name: die naechste Nachricht wurde gelesen")
            assertEquals(1, backend.requests.size, name)
            assertEquals(1, backend.closeCalls, name)
            assertEquals(ConnectionState.CLOSED, session.ready.live.context.state, name)
            assertEquals(1, session.channel.receiveCalls - 2, "$name: Leseaufrufe")
        }
    }

    /**
     * Ein Fehler nach einer Zeile hängt ERROR und DONE mit Fehlerstatus an die gepufferte Antwort
     * (COLMETADATA, ROW, DONE mit `MORE`, ERROR, DONE mit Fehlerstatus); die Verbindung bleibt offen
     * (Class 16). Das DONE vor dem ERROR trägt `MORE` und die Zeilenzahl.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein Fehler nach einer Zeile schliesst die Antwort mit ERROR und Fehlerstatus ab`() = blocking {
        val backend = ScriptedBackend.ofList(
            resultStart(),
            QueryEvent.Row(listOf(7)),
            QueryEvent.ResultEnd(1),
            QueryEvent.Failure("TDS_BACKEND_ERROR", "x"),
        )
        val session = Session.login(BatchVectors.message("q"), Inbound.EndOfStream)
        session.serve(backend)
        val hex = session.hex(0)
        val prefix = listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), "FD 11 00 00 00 01 00 00 00 00 00 00 00")
        assertTrue(hex.startsWith(prefix.joinToString(" ")), hex)
        assertTrue(hex.endsWith(BatchVectors.DONE_ERROR), hex)
        assertEquals(ServerMessage(50004, 1, 16, "Backend error."), session.tokens(0).errorMessage())
    }

    /**
     * Eine ungültige Kodierung ergibt eine definierte Fehlerantwort (`TDS_PROTOCOL_ERROR`, Class 20)
     * und schließt die Verbindung, ohne das Backend zu rufen: Text mit ungerader Byte-Zahl, abgeschnittener
     * ALL_HEADERS-Block, leere Nachricht.
     *
     * @spec SPEC-028
     * @spec SPEC-011
     */
    @Test
    fun `Eine ungueltige Kodierung ergibt eine Fehlerantwort ohne Backend-Aufruf`() = blocking {
        val payloads = mapOf(
            "ungerade Textlaenge" to BatchVectors.payload("ab") + byteArrayOf(0x61),
            "abgeschnittener Block" to BatchVectors.payload("ab").copyOf(10),
            "leer" to ByteArray(0),
        )
        payloads.forEach { (name, payload) ->
            val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
            val session = Session.login(BatchVectors.raw(PacketType.SQL_BATCH, payload), BatchVectors.message("never"))
            session.serve(backend)
            assertEquals(protocolError, session.tokens(0).errorMessage(), name)
            assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR), name)
            assertEquals(0, backend.requests.size, name)
            assertTrue(session.channel.closed, name)
            assertFalse(session.channel.exhausted, name)
            assertEquals(1, session.replies.size, name)
        }
    }

    /**
     * RPC, Transaction Manager und ATTENTION in `READY` beantwortet dieser Dispatch nicht: keine
     * Antwort, das Backend wird nicht gerufen, die Verbindung schließt (auch nach einer
     * erfolgreichen Anfrage; die nächste Nachricht wird nicht gelesen).
     *
     * @spec SPEC-001
     */
    @Test
    fun `RPC, Transaction Manager und ATTENTION bleiben unbeantwortet und schliessen`() = blocking {
        listOf(PacketType.RPC, PacketType.TRANSACTION_MANAGER, PacketType.ATTENTION).forEach { type ->
            val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
            val session = Session.login(
                BatchVectors.message("first"),
                BatchVectors.raw(type, ByteArray(4)),
                BatchVectors.message("never"),
            )
            session.serve(backend)
            assertEquals(1, session.replies.size, "$type")
            assertEquals(1, backend.requests.size, "$type")
            assertTrue(session.channel.closed, "$type")
            assertFalse(session.channel.exhausted, "$type")
        }
    }

    /**
     * Ein SQL_BATCH vor dem Login ist ein unzulässiger Übergang des Handshake-Automaten: der Treiber
     * schließt ohne Antwort auf den Batch (Test des vorhandenen Verhaltens, keine neue Zusage).
     *
     * @spec SPEC-001
     */
    @Test
    fun `Ein SQL_BATCH vor dem Login wird nicht beantwortet`() = blocking {
        val channel = FakeHandshakeChannel(
            listOf(FakeHandshakeChannel.message(SessionVectors.prelogin()), BatchVectors.message("select 1")),
        )
        val driver = HandshakeDriver(HandshakeConfig(), RecordingAdapter.accepting())
        assertEquals(HandshakeOutcome.Closed(CloseReason.PROTOCOL_VIOLATION), driver.run(channel))
        assertEquals(1, channel.sent.size)
        assertTrue(channel.closed)
    }

    /**
     * Die Verbindungsfolge bestimmt das Ereignis: offen sendet und bleibt, schließen nach der Antwort sendet
     * und schließt, schließen ohne Antwort sendet nichts, auch wenn eine gepufferte Antwort vorliegt.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Die Verbindungsfolge bestimmt Antwort und Zustand nach der Anfrage`() {
        val buffered = TdsMessage(PacketType.TABULAR_RESULT, byteArrayOf(1))
        val open = requestFinished(ConnectionFate.OPEN, buffered)
        assertEquals(buffered to true, open.reply to open.connectionOpen)
        val after = requestFinished(ConnectionFate.CLOSE_AFTER_REPLY, buffered)
        assertEquals(buffered to false, after.reply to after.connectionOpen)
        val without = requestFinished(ConnectionFate.CLOSE_WITHOUT_REPLY, buffered)
        assertEquals(null to false, without.reply to without.connectionOpen)
    }

    /**
     * Das Ende der Gegenstelle und ein Lesefehler beenden die Schleife mit geschlossenem Kanal und
     * ohne Backend-Aufruf.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Ende der Gegenstelle und Lesefehler schliessen den Kanal`() = blocking {
        listOf(Inbound.EndOfStream, Inbound.Failed).forEach { end ->
            val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
            val session = Session.login(end)
            session.serve(backend)
            assertTrue(session.channel.closed, "$end")
            assertEquals(0, backend.requests.size, "$end")
            assertEquals(0, session.replies.size, "$end")
            assertEquals(ConnectionState.CLOSED, session.ready.live.context.state, "$end")
        }
    }

    /**
     * Bricht der Aufrufer die Schleife mitten in einer Antwort ab, schließt der Kanal, das Backend wird
     * geschlossen und nichts mehr gelesen oder geschrieben; das Backend erzeugte nach dem Abbruch keine
     * Zeilen mehr (Obergrenze: höchstens eine Zeile über den Stand beim Abbruch hinaus).
     *
     * @spec SPEC-031
     */
    @Test
    fun `Der Abbruch mitten in der Antwort schliesst Kanal und Backend`() = blocking {
        val backend = ScriptedBackend.generating(rows = 1_000_000)
        val session = Session.login(BatchVectors.message("big"), BatchVectors.message("never"))
        val job = launch { session.serve(backend) }
        while (backend.rowsPulled < 3) yield()
        val atCancel = backend.rowsPulled
        job.cancelAndJoin()
        assertTrue(session.channel.closed)
        assertEquals(1, backend.closeCalls)
        assertEquals(0, session.replies.size)
        assertTrue(backend.rowsPulled <= atCancel + 1, "Zeilen nach dem Abbruch: ${backend.rowsPulled} gegen $atCancel")
        assertFalse(session.channel.exhausted)
    }

    /**
     * Die Antwort geht unter der Sperre des Sitzungsrahmens hinaus: ein Übergang, den eine zweite
     * Coroutine während des Schreibens der Antwort anstößt (Ende der Gegenstelle), wird erst nach dem
     * Ende des Schreibens ausgeführt. Die Reihenfolge entsteht ohne Uhr: das Schreiben gibt die
     * Ausführung mehrfach ab.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Die Antwort wird unter der Sperre der Sitzung geschrieben`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(BatchVectors.message("q"), Inbound.EndOfStream)
        val order = mutableListOf<String>()
        var peer: Job? = null
        session.channel.onSend = {
            if (it.type == PacketType.TABULAR_RESULT && order.isEmpty()) {
                peer = launch {
                    session.ready.live.dispatch(HandshakeEvent.PeerClosed)
                    order += "peer-closed"
                }
                repeat(5) { yield() }
                order += "send-end"
            }
        }
        session.serve(backend)
        checkNotNull(peer).join()
        assertEquals(listOf("send-end", "peer-closed"), order)
    }

    /**
     * Die Fehlerabbildung ist der übergebene `ErrorMapper`: sein Log erhält den Code des Fehlers
     * (Backend-Fehler ohne Ursache, fremder Code mit dem festen Marker, Typfehler ohne Ursache).
     *
     * @spec SPEC-048
     */
    @Test
    fun `Die Fehler gehen durch den uebergebenen ErrorMapper`() = blocking {
        val cases = listOf(
            listOf<QueryEvent>(QueryEvent.Failure("TDS_UNSUPPORTED_SQL", "x")) to
                ErrorLogEntry(ErrorCode.TDS_UNSUPPORTED_SQL, null),
            listOf<QueryEvent>(QueryEvent.Failure("NOPE", "x")) to
                ErrorLogEntry(ErrorCode.TDS_INTERNAL_ERROR, "unknown-failure-code"),
            listOf<QueryEvent>(QueryEvent.ReturnStatus(1)) to
                ErrorLogEntry(ErrorCode.TDS_UNSUPPORTED_TYPE, null),
        )
        cases.forEach { (events, expected) ->
            val entries = mutableListOf<ErrorLogEntry>()
            val session = Session.login(BatchVectors.message("q"), Inbound.EndOfStream)
            session.serve(ScriptedBackend.ofList(*events.toTypedArray()), ErrorMapper { entries += it })
            assertEquals(listOf(expected), entries, "$events")
        }
    }
}
