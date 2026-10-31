package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.byName
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int4
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.nvarchar
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorLog
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.FramingResult
import io.github.pt9912.tdswire.transport.framing.MessageAssembler
import io.github.pt9912.tdswire.transport.framing.PacketHeader
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.PacketWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Annahme und Antwort eines RPC in `READY` (`TDS-FA-04`, `SPEC-013`, `SPEC-030`, `SPEC-001`) gegen das
 * Test-Backend [ScriptedBackend]. Die Antwort-Bytes sind Handvektoren; Tokenfolge und DONE-Bits der Antwort
 * auf einen RPC sind weder am Referenzclient noch an einem SQL Server gemessen, und Nummer, Class und Folge
 * von `TDS_UNSUPPORTED_RPC` sind eine Voreinstellung (nicht entschieden). Aussagen über den Referenzclient
 * oder einen echten Server tragen diese Tests nicht.
 */
class RpcDispatchTest {
    private val oneRow = listOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private val oneRowReply = listOf(
        BatchVectors.COLMETADATA_N,
        BatchVectors.row(7),
        BatchVectors.DONE_COUNT_1,
    ).joinToString(" ")

    private val unsupportedRpc = ServerMessage(50002, 1, 16, "Unsupported RPC.")

    private fun rpc(payload: ByteArray) = BatchVectors.raw(PacketType.RPC, payload)

    private val goodRpc = executeSql("select @a", "@a int", param("@a", int4(1)))

    /**
     * `sp_executesql` erreicht das Backend mit Sitzung, neuer Anfrage-ID, unverändertem Text, typisierten
     * Parametern samt Richtung und ohne Frist; die Antwort ist das Ergebnis des Backends, und die Sitzung
     * ist danach wieder `READY` (der Folge-RPC wird beantwortet). Je RPC genau ein Aufruf.
     *
     * @spec SPEC-030
     * @spec SPEC-002
     * @spec SPEC-001
     */
    @Test
    fun `sp_executesql erreicht das Backend mit Text und Parametern und die Sitzung bleibt bereit`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val first = executeSql(
            "select @a, @o",
            "@a int, @o int output",
            param("@a", int4(42)),
            param("@o", "26 04 00", status = 1),
        )
        val session = Session.login(rpc(first), rpc(executeSql("select 2", "")), Inbound.EndOfStream)
        session.serve(backend)
        val outputs = listOf(
            QueryParameter("@a", SqlType.Int32, 42, ParameterDirection.INPUT),
            QueryParameter("@o", SqlType.Int32, null, ParameterDirection.OUTPUT),
        )
        assertEquals(
            listOf(
                QueryRequest("s1", "r1", "select @a, @o", outputs, null),
                QueryRequest("s1", "r2", "select 2", emptyList(), null),
            ),
            backend.requests,
        )
        assertEquals(2, session.replies.size)
        assertEquals(oneRowReply, session.hex(0))
        assertEquals(oneRowReply, session.hex(1))
        assertEquals(2, backend.closeCalls)
        assertTrue(session.channel.closed)
    }

    /**
     * Ein RPC per Prozedurname erreicht das Backend als Aufruftext mit Parameterliste (Nutzerentscheidung
     * zum Backend-Vertrag); ein Batch, ein `sp_executesql` und ein RPC per Name wechseln auf einer Sitzung
     * mit fortlaufenden Anfrage-IDs.
     *
     * @spec SPEC-030
     * @spec SPEC-002
     */
    @Test
    fun `RPC per Name erreicht das Backend als Aufruftext und wechselt mit Batch und sp_executesql`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            BatchVectors.message("select 1"),
            rpc(byName("[dbo].[p]", param("@x", nvarchar("v")), param("@o", int4(3), status = 1))),
            rpc(goodRpc),
            BatchVectors.message("select 2"),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(listOf("r1", "r2", "r3", "r4"), backend.requests.map { it.requestId })
        assertEquals(
            listOf("select 1", "EXEC [dbo].[p] @x = @x, @o = @o OUTPUT", "select @a", "select 2"),
            backend.requests.map { it.sql },
        )
        assertEquals(
            listOf(
                QueryParameter("@x", SqlType.UnicodeText, "v"),
                QueryParameter("@o", SqlType.Int32, 3, ParameterDirection.INPUT_OUTPUT),
            ),
            backend.requests[1].parameters,
        )
        assertEquals(4, session.replies.size)
        assertTrue(session.replies.indices.all { session.hex(it) == oneRowReply })
    }

    /**
     * Jede gültig kodierte, aber nicht unterstützte Form (unbekannte ProcID, nicht gebaute Katalogform,
     * Katalogname per Name, ungültiger Name, ungültiger Parameter, Parametertyp außerhalb des Katalogs, Wert
     * außerhalb des Bereichs) ergibt
     * ERROR (Voreinstellung 50002, Class 16) und DONE mit Fehlerstatus (Handvektor), das Backend wird nicht
     * gerufen, die Verbindung bleibt offen und der Folge-RPC derselben Sitzung wird beantwortet.
     *
     * @spec SPEC-013
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Eine Ablehnung beantwortet mit ERROR und DONE und laesst die Verbindung offen`() = blocking {
        val rejected = mapOf(
            "sp_prepare" to RpcWire.procId(11),
            "sp_unprepare" to RpcWire.procId(15),
            "sp_cursoropen" to RpcWire.procId(2),
            "sp_prepexec" to hex(RpcVectors.PREP_EXEC),
            "sp_execute" to hex(RpcVectors.EXECUTE),
            "Name sp_cursorfetch" to byName("sp_cursorfetch"),
            "Name mit Anweisung" to byName("p; drop table t"),
            "Parametername" to executeSql("s", "d", param("a", int4(1))),
            "Parametertyp" to executeSql("s", "d", param("@a", "6D 08 00")),
            "Wert ausserhalb" to executeSql("s", "d", param("@a", "6C 05 05 00 05 01 A0 86 01 00")),
        )
        rejected.forEach { (name, payload) ->
            val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
            val session = Session.login(rpc(payload), rpc(goodRpc), Inbound.EndOfStream)
            session.serve(backend)
            assertEquals(2, session.replies.size, name)
            assertEquals(unsupportedRpc, session.tokens(0).errorMessage(), name)
            assertEquals(2, session.tokens(0).size, name)
            assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR), name)
            assertEquals(DoneStatus.ERROR, session.tokens(0).lastDone().status, name)
            assertEquals(oneRowReply, session.hex(1), name)
            assertEquals(1, backend.requests.size, name)
            assertEquals("select @a", backend.requests.single().sql, name)
            assertEquals(1, backend.closeCalls, name)
        }
    }

    /**
     * Eine Nachricht, die der Parser abweist (abgeschnitten, Restbytes, leer, ungültige TYPE_INFO), wird wie
     * ein SQL_BATCH mit Parserfehler beantwortet: ERROR `TDS_PROTOCOL_ERROR` (Class 20) und DONE mit
     * Fehlerstatus, die Verbindung schließt, die Folgenachricht bleibt ungelesen, das Backend wird nicht
     * gerufen. Nutzerentscheidung vom 2026-10-31, nicht gemessen. Meldet der Parser die Kategorie
     * `TDS_UNSUPPORTED_TYPE` (Typkennung außerhalb seines Katalogs), gilt die Abbildung des Batch: ERROR
     * `TDS_UNSUPPORTED_TYPE` (Class 20), die Verbindung schließt ebenfalls.
     *
     * @spec SPEC-011
     * @spec SPEC-030
     */
    @Test
    fun `Eine Kodierung, die der Parser abweist, ergibt einen Protokollfehler und schliesst`() = blocking {
        val rejected = mapOf(
            "abgeschnitten" to goodRpc.copyOf(goodRpc.size - 2),
            "leer" to ByteArray(0),
            "ungueltige TYPE_INFO" to executeSql("s", "d", param("@a", "26 03 00")),
        )
        rejected.forEach { (name, payload) ->
            val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
            val session = Session.login(rpc(payload), rpc(goodRpc))
            session.serve(backend)
            assertEquals(ServerMessage(50006, 1, 20, "Protocol error."), session.tokens(0).errorMessage(), name)
            assertTrue(session.hex(0).endsWith(BatchVectors.DONE_ERROR), name)
            assertEquals(1, session.replies.size, name)
            assertEquals(0, backend.requests.size, name)
            assertTrue(session.channel.closed, name)
            assertFalse(session.channel.exhausted, name)
        }
    }

    /**
     * Ein gültig kodierter PLP-Typ (`nvarchar(max)`) ist nicht unterstützt, aber kein Strukturfehler:
     * ERROR `TDS_UNSUPPORTED_RPC` (50002, Class 16) und DONE mit Fehlerstatus, das Backend wird nicht
     * gerufen, die Verbindung bleibt offen und die Folgenachricht wird beantwortet (Nutzerentscheidung vom
     * 2026-10-31, nicht gemessen).
     *
     * @spec SPEC-013
     */
    @Test
    fun `Ein PLP-Typ wird offen abgelehnt und die Verbindung bleibt offen`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val plp = "E7 FF FF ${RpcVectors.COLLATION} 00 00"
        val session = Session.login(rpc(executeSql("s", "d", param("@a", plp))), rpc(goodRpc), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(unsupportedRpc, session.tokens(0).errorMessage())
        assertEquals(DoneStatus.ERROR, session.tokens(0).lastDone().status)
        assertEquals(2, session.replies.size)
        assertEquals(oneRowReply, session.hex(1))
        assertEquals(1, backend.requests.size)
    }

    /**
     * Ein PLP-Typ in der Mitte der Parameterliste (vor ihm ein gültiger, hinter ihm ein weiterer Parameter)
     * ist dieselbe offene Ablehnung: ERROR 50002, kein Backend-Aufruf, die Verbindung bleibt offen, der
     * Folge-RPC wird beantwortet. Die Bytes hinter dem PLP-Typ werden nicht gelesen. Wächter: Ablehnung erst
     * nach der ganzen Liste oder Schließen bei einem Parameter hinter dem PLP-Typ.
     *
     * @spec SPEC-013
     */
    @Test
    fun `Ein PLP-Typ in der Mitte der Parameterliste wird offen abgelehnt`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val plp = "E7 FF FF ${RpcVectors.COLLATION} 00 00"
        val middle = executeSql("s", "d", param("@a", int4(1)), param("@b", plp), param("@c", int4(3)))
        val session = Session.login(rpc(middle), rpc(goodRpc), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(unsupportedRpc, session.tokens(0).errorMessage())
        assertEquals(DoneStatus.ERROR, session.tokens(0).lastDone().status)
        assertEquals(2, session.replies.size)
        assertEquals(oneRowReply, session.hex(1))
        assertEquals(1, backend.requests.size)
        assertTrue(session.channel.exhausted)
    }

    /**
     * Zwei RPC, jeder über mehrere TDS-Pakete bis EOM (3 Nutzdatenbytes je Paket), der erste mit PLP-Typ,
     * durch den [MessageAssembler] und die Schleife: der Assembler liefert genau zwei Nachrichten ohne
     * Fehler, beide werden beantwortet (erst 50002, dann das Ergebnis des Backends), das Backend sieht einen
     * Aufruf, und die Sitzung ist nicht geschlossen. Wächter: ein Assembler, der nur das erste Paket liefert.
     *
     * @spec SPEC-013
     */
    @Test
    fun `Ein mehrpaketiger RPC mit PLP-Typ und ein zweiter RPC werden beide beantwortet`() = blocking {
        val plp = "E7 FF FF ${RpcVectors.COLLATION} 00 00"
        val wire = listOf(executeSql("s", "d", param("@a", int4(1)), param("@b", plp)), goodRpc).map { payload ->
            val packets = assertIs<FramingResult.Ok<List<ByteArray>>>(
                PacketWriter.split(PacketType.RPC, payload, PacketHeader.SIZE + 3),
            ).value
            assertTrue(packets.size > 2)
            packets.fold(ByteArray(0)) { all, packet -> all + packet }
        }.reduce { all, next -> all + next }
        val assembler = MessageAssembler()
        val step = assembler.feed(wire)
        assertNull(step.error)
        assertEquals(2, step.messages.size)
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(*step.messages.map { Inbound.Message(it) }.toTypedArray(), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(unsupportedRpc, session.tokens(0).errorMessage())
        assertEquals(2, session.replies.size)
        assertEquals(oneRowReply, session.hex(1))
        assertEquals(1, backend.requests.size)
        assertTrue(session.channel.exhausted)
    }

    /** @spec SPEC-011 */
    @Test
    fun `Eine unbekannte Typkennung schliesst mit dem Fehler des Parsers`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(rpc(executeSql("s", "d", param("@a", "FE 00"))), rpc(goodRpc))
        session.serve(backend)
        assertEquals(ServerMessage(50003, 1, 20, "Unsupported type."), session.tokens(0).errorMessage())
        assertEquals(1, session.replies.size)
        assertEquals(0, backend.requests.size)
        assertTrue(session.channel.closed)
        assertFalse(session.channel.exhausted)
    }

    /**
     * Eine abgelehnte Nachricht verbraucht die Verbindung nicht: nach mehreren Ablehnungen hintereinander
     * wird derselbe Kanal weiter gelesen (Zahl der gelesenen Nachrichten) und ein Batch beantwortet.
     *
     * @spec SPEC-013
     */
    @Test
    fun `Mehrere Ablehnungen hintereinander beenden die Sitzung nicht`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(RpcWire.procId(11)),
            rpc(RpcWire.procId(15)),
            rpc(RpcWire.procId(11)),
            BatchVectors.message("select 1"),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(4, session.replies.size)
        val failed = session.replies.indices.map { session.hex(it).endsWith(BatchVectors.DONE_ERROR) }
        assertEquals(listOf(true, true, true, false), failed)
        assertEquals(1, backend.requests.size)
        assertEquals(5, session.channel.receiveCalls - 2)
    }

    /**
     * Ein unbekannter Name nach der Antwort des Backends: meldet das Backend `TDS_UNSUPPORTED_SQL` (Class 16),
     * antwortet der Dispatch mit ERROR und DONE, die Verbindung bleibt offen und der Folge-RPC wird
     * beantwortet. Der Text des Backends erreicht den Client nicht.
     *
     * @spec SPEC-013
     * @spec SPEC-030
     */
    @Test
    fun `Ein unbekannter Name wird vom Backend beantwortet und die Verbindung bleibt offen`() = blocking {
        val failure = listOf<QueryEvent>(QueryEvent.Failure("TDS_UNSUPPORTED_SQL", "Prozedur nope unbekannt"))
        val backend = ScriptedBackend.inSequence(failure, oneRow)
        val session = Session.login(rpc(byName("nope")), rpc(goodRpc), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(listOf("EXEC nope", "select @a"), backend.requests.map { it.sql })
        assertEquals(ServerMessage(50001, 1, 16, "Unsupported SQL."), session.tokens(0).errorMessage())
        assertFalse(session.hex(0).contains(RpcWire.utf16("nope")))
        assertEquals(oneRowReply, session.hex(1))
    }

    /**
     * Der Log-Eintrag der Ablehnung eines `sp_executesql` (hier ein doppelter Parametername) trägt den Code
     * und den Namen des Ablehnungsgrundes, weder Text noch Parameterwerte; das Backend wird nicht gerufen.
     * Eine Parserablehnung trägt `TDS_PROTOCOL_ERROR` ohne Grund, nie `TDS_UNSUPPORTED_RPC`.
     *
     * @spec SPEC-030
     * @spec SPEC-048
     */
    @Test
    fun `Der Log der Ablehnung nennt den Code und keine Werte`() = blocking {
        val entries = mutableListOf<ErrorLogEntry>()
        val secret = "Kennwort-Q7"
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val bad = executeSql("select '$secret'", "d", param("@pw", nvarchar(secret)), param("@pw", nvarchar(secret)))
        val session = Session.login(rpc(bad), Inbound.EndOfStream)
        session.serve(backend, errors = ErrorMapper(ErrorLog { entries += it }))
        assertEquals(listOf(ErrorLogEntry(ErrorCode.TDS_UNSUPPORTED_RPC, "PARAMETER_NAME")), entries)
        assertFalse(entries.toString().contains(secret))
        assertEquals(0, backend.requests.size)

        entries.clear()
        val parserSession = Session.login(rpc(bad.copyOf(bad.size - 2)), Inbound.EndOfStream)
        parserSession.serve(backend, errors = ErrorMapper(ErrorLog { entries += it }))
        assertEquals(listOf(ErrorLogEntry(ErrorCode.TDS_PROTOCOL_ERROR, null)), entries)
    }

    /**
     * Transaction Manager und ATTENTION in `READY` bleiben unbeantwortet und schließen die Verbindung (kein
     * Backend-Aufruf, die nächste Nachricht wird nicht gelesen); ein RPC davor wurde beantwortet.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Transaction Manager und ATTENTION schliessen auch nach einem RPC`() = blocking {
        listOf(PacketType.TRANSACTION_MANAGER, PacketType.ATTENTION).forEach { type ->
            val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
            val session = Session.login(rpc(goodRpc), BatchVectors.raw(type, ByteArray(4)), rpc(goodRpc))
            session.serve(backend)
            assertEquals(1, session.replies.size, "$type")
            assertEquals(1, backend.requests.size, "$type")
            assertTrue(session.channel.closed, "$type")
            assertFalse(session.channel.exhausted, "$type")
        }
    }
}
