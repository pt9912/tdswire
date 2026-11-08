package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.execute
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int4
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.nvarchar
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.prepExec
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorLog
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prepared-Statements einer Sitzung (`TDS-FA-04`, `SPEC-030`, `SPEC-057`, `SPEC-058`, `SPEC-001`) gegen das
 * Test-Backend [ScriptedBackend]: `sp_prepexec` und `sp_execute`, die Handle-Tabelle je Sitzung, ihre Grenze
 * und die definierten Fehler. Die Antwort-Bytes sind Handvektoren; Tokenfolge (RETURNSTATUS 0, RETURNVALUE ohne
 * Namen mit Ordinal 0) und Name des Handle-RETURNVALUE sind an den Mitschnitten der Referenzinstanz belegt
 * (`make vergleichslauf-d LAUF=mitschnitt`);
 * Handle-Werte, Grenze und Fehlercodes sind Festlegungen und weder am Referenzclient
 * noch an einem SQL Server gemessen. Aussagen über einen Pool oder einen echten Server tragen diese Tests nicht.
 */
class PreparedHandlesDispatchTest {
    private val oneRow = listOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private val doneInProcMore = "FF 11 00 C1 00 01 00 00 00 00 00 00 00"
    private val doneProcFinal = "FE 00 00 E0 00 00 00 00 00 00 00 00 00"

    /** RETURNSTATUS 0: vor dem Handle und in jeder RPC-Antwort ohne Rückgabestatus (Mitschnitte `prepare`, `rpc-*`). */
    private val returnStatusZero = "79 00 00 00 00"

    /** COLMETADATA, ROW, DONEINPROC, RETURNSTATUS 0, DONEPROC: die Antwort auf `sp_executesql` und `sp_execute`. */
    private val oneRowReply =
        listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), doneInProcMore, returnStatusZero, doneProcFinal)
            .joinToString(" ")

    /**
     * RETURNVALUE des Handles: Ordinal, Namenslänge 0, Status 1, Flag-Feld `00 00`, Typ `INTN` 4, Wert [handle].
     * Namenslänge, Status, Flag-Feld, Typ und Stellung hinter dem RETURNSTATUS sind die Bytes des Mitschnitts
     * `prepare` der Referenzinstanz (2022 und 2025: `79 00 00 00 00 AC 00 00 00 01 00 00 00 00 00 00 26 04 04 01 00
     * 00 00`; Messprotokoll A-19). Der Mitschnitt-Lauf liest das Flag-Feld nicht; dieser Test belegt es.
     */
    private fun handleValue(ordinal: Int, handle: Int) =
        "AC ${RpcWire.le(ordinal.toLong(), 2)} 00 01 00 00 00 00 00 00 26 04 04 ${RpcWire.le(handle.toLong(), 4)}"

    /** Die Antwort auf `sp_prepexec` mit [oneRow]: RETURNSTATUS und Handle stehen vor dem DONEPROC. */
    private fun prepReply(handle: Int) = listOf(
        BatchVectors.COLMETADATA_N,
        BatchVectors.row(7),
        doneInProcMore,
        returnStatusZero,
        handleValue(0, handle),
        doneProcFinal,
    ).joinToString(" ")

    private fun rpc(payload: ByteArray) = BatchVectors.raw(PacketType.RPC, payload)

    /** Die Meldung für `sp_execute` mit unbekanntem Handle (gemessen: M-6, State 4). */
    private fun unknownHandle(handle: Int) =
        ServerMessage(8179, 4, 16, "Could not find prepared statement with handle $handle.")
    private val limitReached = ServerMessage(50010, 1, 16, "Prepared handle limit reached.")
    private val unsupportedRpc = ServerMessage(50002, 1, 16, "Unsupported RPC.")

    /**
     * `sp_prepexec` führt den Text mit den Parametern der Nachricht über das Backend aus und liefert das Handle
     * als RETURNVALUE (Ordinal 0, `INTN` 4) vor dem DONEPROC; `sp_execute` ruft das Backend mit dem gemerkten
     * Text und den neuen Werten, ein zweiter und dritter Aufruf liefern je das Ergebnis ohne Handle. Wächter:
     * ein Handle-RETURNVALUE bei `sp_execute`, ein falsches Ordinal oder Handle, ein Text oder Wert aus der
     * falschen Nachricht.
     *
     * @spec SPEC-030
     * @spec SPEC-002
     * @spec SPEC-001
     */
    @Test
    fun `sp_prepexec liefert das Handle, sp_execute fuehrt den gemerkten Text mit neuen Werten aus`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select @a", "@a int", param("@a", int4(7)))),
            rpc(execute(1, param("@a", int4(8)))),
            rpc(execute(1, param("@a", int4(9)))),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(listOf("select @a", "select @a", "select @a"), backend.requests.map { it.sql })
        assertEquals(listOf(7, 8, 9), backend.requests.map { it.parameters.single().value })
        assertEquals(
            QueryParameter("@a", SqlType.Int32, 9, ParameterDirection.INPUT),
            backend.requests.last().parameters.single(),
        )
        assertEquals(listOf(prepReply(1), oneRowReply, oneRowReply), session.replies.indices.map { session.hex(it) })
        assertEquals(3, backend.closeCalls)
    }

    /**
     * Jedes `sp_prepexec` vergibt ein neues Handle ab 1 mit eigenem Text; der Wert im OUTPUT-Parameter der
     * Nachricht (−1 oder 0 beim Erstaufruf: mssql-jdbc 13.4.0 sendet 0; ein altes Handle bei einem erneuten Prepare)
     * ändert die Vergabe nicht. Wächter: ein Zähler, der bei 0 beginnt oder das Handle des Clients übernimmt; ein
     * gemeinsamer Text.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Jedes sp_prepexec vergibt ein neues Handle ab 1, der Wert des Clients bleibt unberuecksichtigt`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(prepExec(-1, "select 2", "")),
            rpc(prepExec(1, "select 3", "")),
            rpc(prepExec(0, "select 4", "")),
            rpc(execute(2)),
            rpc(execute(1)),
            rpc(execute(3)),
            rpc(execute(4)),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(
            listOf("select 1", "select 2", "select 3", "select 4", "select 2", "select 1", "select 3", "select 4"),
            backend.requests.map { it.sql },
        )
        assertEquals(listOf(prepReply(1), prepReply(2), prepReply(3), prepReply(4)), (0..3).map { session.hex(it) })
        assertEquals(List(4) { oneRowReply }, (4..7).map { session.hex(it) })
    }

    /**
     * NULL-Parameter und OUTPUT folgen den Regeln von `sp_executesql`: das Backend bekommt Richtung und
     * NULL wie dort. Liefert das Backend RETURNSTATUS und einen Ausgabewert, steht das Handle als erster
     * Ausgabewert (Ordinal 0) hinter dem RETURNSTATUS, der Ausgabewert des Backends hat die Position seines
     * Parameters in der Aufrufliste (`sp_prepexec`: Handle, Deklarationsliste und Text davor, `@o` an zweiter Stelle
     * der Wertparameter, Ordinal 4; `sp_execute`: das Handle davor, Ordinal 2; Messprotokoll W-5). Wächter: das
     * Handle hinter dem Ausgabewert des Backends oder vor dem RETURNSTATUS, ein Handle bei `sp_execute`, ein
     * Ordinal, das die Ausgabewerte zählt.
     *
     * @spec SPEC-030
     * @spec SPEC-002
     */
    @Test
    fun `NULL und OUTPUT folgen sp_executesql, das Handle ist der erste Ausgabewert`() = blocking {
        val backend = ScriptedBackend.ofList(
            QueryEvent.ReturnStatus(0),
            QueryEvent.OutputParameter("@o", SqlType.Int32, 42),
        )
        val declarations = "@a int, @o int output"
        val session = Session.login(
            rpc(prepExec(-1, "select @a, @o", declarations, param("@a", "26 04 00"), param("@o", "26 04 00", 1))),
            rpc(execute(1, param("@a", nvarchar("x")), param("@o", "26 04 00", 1))),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        fun output(ordinal: String) = "AC $ordinal 02 40 00 6F 00 01 00 00 00 00 00 00 26 04 04 2A 00 00 00"
        val status = "79 00 00 00 00"
        assertEquals(
            listOf(status, handleValue(0, 1), output("04 00"), doneProcFinal).joinToString(" "),
            session.hex(0),
        )
        assertEquals(listOf(status, output("02 00"), doneProcFinal).joinToString(" "), session.hex(1))
        assertEquals(
            listOf(
                QueryParameter("@a", SqlType.Int32, null, ParameterDirection.INPUT),
                QueryParameter("@o", SqlType.Int32, null, ParameterDirection.OUTPUT),
            ),
            backend.requests[0].parameters,
        )
        assertEquals(
            listOf(
                QueryParameter("@a", SqlType.UnicodeText, "x"),
                QueryParameter("@o", SqlType.Int32, null, ParameterDirection.OUTPUT),
            ),
            backend.requests[1].parameters,
        )
    }

    /**
     * Die Grenze: 1000 `sp_prepexec` werden angenommen (Handles 1 bis 1000); das 1001. ergibt ERROR
     * `TDS_PREPARE_LIMIT` (50010, Class 16) und DONE mit Fehlerstatus (Form des gebauten Standes), ohne das
     * Backend zu rufen; die Verbindung bleibt offen, die vorhandenen Handles bleiben gültig und der
     * Folge-RPC wird beantwortet. Das Log nennt die feste Marke `handle-limit`. Wächter: Grenze 999 oder
     * 1001, `>` statt `>=`, ein Backend-Aufruf bei Überschreitung, ein Verbindungsende.
     *
     * @spec SPEC-058
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Das 1001 Handle wird abgelehnt, die Verbindung bleibt offen und die Handles gueltig`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val limit = PreparedHandles.DEFAULT_MAX_HANDLES
        val prepares = (1..limit).map { rpc(prepExec(-1, "select $it", "")) }
        val session = Session.login(
            *prepares.toTypedArray(),
            rpc(prepExec(-1, "select over", "")),
            rpc(execute(1)),
            rpc(execute(limit)),
            rpc(prepExec(-1, "select again", "")),
            Inbound.EndOfStream,
        )
        val logged = mutableListOf<ErrorLogEntry>()
        session.serve(backend, ErrorMapper(ErrorLog { logged += it }))
        assertEquals(limit + 4, session.replies.size)
        assertEquals(prepReply(limit), session.hex(limit - 1))
        assertEquals(limitReached, session.tokens(limit).errorMessage())
        assertEquals(DoneStatus.ERROR, session.tokens(limit).lastDone().status)
        assertEquals(2, session.tokens(limit).size)
        assertEquals(oneRowReply, session.hex(limit + 1))
        assertEquals(oneRowReply, session.hex(limit + 2))
        assertEquals(limitReached, session.tokens(limit + 3).errorMessage())
        assertEquals(limit + 2, backend.requests.size)
        assertEquals("select 1", backend.requests[limit].sql)
        assertEquals("select $limit", backend.requests[limit + 1].sql)
        assertEquals(List(2) { ErrorLogEntry(ErrorCode.TDS_PREPARE_LIMIT, "handle-limit") }, logged)
        assertTrue(session.channel.exhausted)
    }

    /**
     * Ein Handle, das die Sitzung nicht hält (nie vergeben, −1, 0, über der Zählung), ergibt ERROR 8179 (Class 16,
     * State 4, Text mit der Nummer), RETURNSTATUS 8179 und DONEPROC mit Fehlerstatus, ohne das Backend zu rufen; die
     * Verbindung bleibt offen, ein gehaltenes Handle und der Folge-RPC werden beantwortet. Das Log nennt die
     * feste Marke `unknown-handle`. Nummer, Class, State und Text sind gemessen (M-6, Handle 999999); RETURNSTATUS
     * und die leeren Felder des ERROR sind Setzung. Wächter: ein Backend-Aufruf, ein Verbindungsende, ein Handle,
     * das nach dem Fehler verloren ist, 50009 oder State 1 oder 8 statt State 4, ein Text ohne die Nummer.
     *
     * @spec SPEC-057
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Ein unbekanntes Handle ergibt einen definierten Fehler bei offener Verbindung`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(execute(2)),
            rpc(execute(-1)),
            rpc(execute(0)),
            rpc(execute(1)),
            Inbound.EndOfStream,
        )
        val logged = mutableListOf<ErrorLogEntry>()
        session.serve(backend, ErrorMapper(ErrorLog { logged += it }))
        assertEquals(5, session.replies.size)
        val unknownNumbers = listOf(2, -1, 0)
        (1..3).forEach {
            assertEquals(unknownHandle(unknownNumbers[it - 1]), session.tokens(it).errorMessage(), "$it")
            assertEquals(DoneStatus.ERROR, session.tokens(it).lastDone().status, "$it")
            assertEquals(3, session.tokens(it).size, "$it")
            assertEquals(ReturnStatusToken(8179), session.tokens(it)[1], "$it")
        }
        assertEquals(oneRowReply, session.hex(4))
        assertEquals(2, backend.requests.size)
        assertEquals(List(3) { ErrorLogEntry(ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE, "unknown-handle") }, logged)
        assertTrue(session.channel.exhausted)
    }

    /**
     * `sp_unprepare` (ProcID 15) und `sp_prepare` (11) sind nicht im Katalog: ERROR `TDS_UNSUPPORTED_RPC`
     * bei offener Verbindung, das Backend wird nicht gerufen, und `sp_unprepare` gibt kein Handle frei: das
     * Handle ist danach weiter gültig und der Folge-RPC wird beantwortet.
     *
     * @spec SPEC-013
     * @spec SPEC-030
     */
    @Test
    fun `sp_unprepare und sp_prepare ergeben einen Fehler und geben kein Handle frei`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(RpcWire.procId(15)),
            rpc(RpcWire.procId(11)),
            rpc(execute(1)),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(4, session.replies.size)
        assertEquals(unsupportedRpc, session.tokens(1).errorMessage())
        assertEquals(unsupportedRpc, session.tokens(2).errorMessage())
        assertEquals(oneRowReply, session.hex(3))
        assertEquals(2, backend.requests.size)
    }

    /**
     * Endet die Antwort eines `sp_prepexec` nicht vollständig (Fehler des Backends), erreicht den Client kein
     * Handle: die Sitzung hält es nicht (`sp_execute` ergibt 8179 mit State 4), und das nächste
     * `sp_prepexec` bekommt den nächsten Wert. Eine Nachricht, die schon das Binden ablehnt (Parametertyp
     * außerhalb des Katalogs), vergibt keinen Wert. Wächter: ein Handle, das nach dem Fehler gehalten bleibt;
     * ein Handle vor dem Binden.
     *
     * @spec SPEC-030
     * @spec SPEC-057
     */
    @Test
    fun `Ein gescheitertes oder abgelehntes sp_prepexec hinterlaesst kein Handle`() = blocking {
        val backend = ScriptedBackend.inSequence(
            listOf(QueryEvent.Failure("TDS_BACKEND_ERROR", "boom")),
            oneRow,
        )
        val session = Session.login(
            rpc(prepExec(-1, "select boom", "")),
            rpc(execute(1)),
            rpc(prepExec(-1, "select 1", "", param("@a", "6D 08 00"))),
            rpc(prepExec(-1, "select 2", "")),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(4, session.replies.size)
        assertEquals(ServerMessage(50004, 1, 16, "Backend error."), session.tokens(0).errorMessage())
        assertEquals(unknownHandle(1), session.tokens(1).errorMessage())
        assertEquals(unsupportedRpc, session.tokens(2).errorMessage())
        assertEquals(prepReply(2), session.hex(3))
        assertEquals(listOf("select boom", "select 2"), backend.requests.map { it.sql })
    }

    /**
     * Bricht ATTENTION ein `sp_prepexec` ab, bevor das Handle den Client erreicht hat, gibt die Sitzung das Handle
     * frei: die Antwort ist `DONE[ATTN]` allein, die Tabelle ist danach leer. Wächter: ein abgebrochener Lauf, der das
     * Handle gehalten lässt.
     *
     * @spec SPEC-030
     * @spec SPEC-029
     */
    @Test
    fun `Ein abgebrochenes sp_prepexec ohne gesendetes Handle gibt es frei`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val session = Session.login(rpc(prepExec(-1, "select 1", "")))
        val loop = session.loop(backend)
        val serving = launch { loop.run() }
        backend.collecting.await()
        assertEquals(1, loop.handles.size())
        session.channel.feed(BatchVectors.attention())
        session.channel.feed(Inbound.EndOfStream)
        serving.join()
        assertEquals(BatchVectors.DONE_ATTN, session.hex(0))
        assertEquals(0, loop.handles.size())
    }

    /**
     * Folgt dem Handle-RETURNVALUE ein Fehler des Backends (Ausgabewert, dann `Failure`), hat das Handle den
     * Client schon erreicht: die Antwort trägt das Handle ohne Namen mit Ordinal 0 vor dem ERROR, und das Handle bleibt
     * gültig (`sp_execute` wird ausgeführt). Gemessen ist das gegen das Test-Backend; die Aussage gilt
     * nur für diesen Fall (Setzung). Wächter: ein Freigeben trotz gesendetem Handle.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein Fehler nach dem Handle laesst das gesendete Handle gueltig`() = blocking {
        val backend = ScriptedBackend.inSequence(
            listOf(QueryEvent.OutputParameter("@o", SqlType.Int32, 1), QueryEvent.Failure("TDS_BACKEND_ERROR", "x")),
            oneRow,
        )
        val session = Session.login(rpc(prepExec(-1, "select 1", "")), rpc(execute(1)), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(2, session.replies.size)
        val first = session.tokens(0)
        val value = first.filterIsInstance<ReturnValueToken>().first()
        assertEquals("", value.name)
        assertEquals(0, value.ordinal)
        assertEquals(ServerMessage(50004, 1, 16, "Backend error."), first.errorMessage())
        assertEquals(oneRowReply, session.hex(1))
    }

    /**
     * Die Handles enden mit der Sitzung: nach dem Ende der Schleife hält die Tabelle keines mehr, und ein
     * Handle derselben Nummer ist in einer neuen Sitzung unbekannt. Wächter: eine Tabelle, die am Ende nicht
     * geleert wird; eine Tabelle, die über Sitzungen hinaus lebt.
     *
     * @spec SPEC-030
     * @spec SPEC-057
     */
    @Test
    fun `Die Handles enden mit der Sitzung`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val first = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(prepExec(-1, "select 2", "")),
            Inbound.EndOfStream,
        )
        val loop = first.loop(backend)
        loop.run()
        assertEquals(2, first.replies.size)
        assertEquals(0, loop.handles.size())
        val second = Session.login(rpc(execute(1)), Inbound.EndOfStream)
        second.serve(backend)
        assertEquals(unknownHandle(1), second.tokens(0).errorMessage())
        assertEquals(2, backend.requests.size)
    }

    /**
     * Zwei gleichzeitig offene Sitzungen sehen die Handles der jeweils anderen nicht: Sitzung A hält Handle 1,
     * in Sitzung B ist Handle 1 unbekannt, und B vergibt sein eigenes Handle 1 für anderen Text, den A nicht
     * ausführt. Wächter: eine Tabelle, die Sitzungen teilt (statisch oder je Prozess).
     *
     * @spec SPEC-030
     * @spec SPEC-057
     */
    @Test
    fun `Zwei offene Sitzungen sehen die Handles der anderen nicht`() = blocking {
        val backendA = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val backendB = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val sessionA = Session.login(rpc(prepExec(-1, "select a", "")))
        val loopA = sessionA.loop(backendA)
        val running = launch { loopA.run() }
        while (sessionA.replies.isEmpty()) yield()
        assertEquals(1, loopA.handles.size())

        val sessionB = Session.login(
            rpc(execute(1)),
            rpc(prepExec(-1, "select b", "")),
            rpc(execute(1)),
            Inbound.EndOfStream,
        )
        sessionB.serve(backendB)
        assertEquals(unknownHandle(1), sessionB.tokens(0).errorMessage())
        assertEquals(prepReply(1), sessionB.hex(1))
        assertEquals(listOf("select b", "select b"), backendB.requests.map { it.sql })
        assertEquals(1, loopA.handles.size())
        assertEquals(listOf("select a"), backendA.requests.map { it.sql })
        running.cancel()
        running.join()
        assertEquals(0, loopA.handles.size())
    }

    /**
     * Ein `sp_executesql` erzeugt kein Handle: ein `sp_execute` danach findet keines. Wächter: eine Vergabe
     * außerhalb von `sp_prepexec`.
     *
     * @spec SPEC-030
     */
    @Test
    fun `sp_executesql vergibt kein Handle`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(executeSql("select 1", "")),
            rpc(execute(1)),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(oneRowReply, session.hex(0))
        assertEquals(unknownHandle(1), session.tokens(1).errorMessage())
    }
}
