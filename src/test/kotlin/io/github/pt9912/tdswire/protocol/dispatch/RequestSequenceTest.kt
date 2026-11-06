package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.execute
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int4
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.prepExec
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorLog
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Gesamtfolge der Anfragearten auf einer Sitzung (`TDS-FA-03`, `TDS-FA-04`, `TDS-FA-10`, `TDS-QA-02`)
 * gegen das Test-Backend [ScriptedBackend], das den Backend-Port implementiert. Die Aussage gilt für
 * Nachrichten (Pakettyp und Nutzlast) einer Sitzung, die [Session] dem Dispatch als Rohbytes gibt; Paket-Framing,
 * Socket und Login trägt dieser Test nicht, und über den Referenzclient oder einen echten SQL Server sagt er
 * nichts.
 *
 * Lesart: Der Anfragezustand `IDLE` (`SPEC-001`) heißt im Code `READY`; `COMPLETED` ist nicht abgebildet.
 *
 * Belegt ist, was die Einzeltests einer Anfrageart nicht belegen. Gemessen (der Test besteht): die Antwort-
 * Bytes der Erfolgsfälle als Handvektoren, deren Quellen die Tests `SqlBatchDispatchTest`,
 * `AnswerFormsDispatchTest` und `PreparedHandlesDispatchTest` tragen; Zahl, Text und Parameter der
 * Backend-Aufrufe; die Fehlermeldungen. Festlegung (weder am Referenzclient noch an einem SQL Server
 * gemessen): Tokenfolge der RPC-Antworten, Fehlercodes 50002 und 50009, Handle-Wert 1 und das Ende der Folge.
 */
class RequestSequenceTest {
    private val oneRow = listOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private val doneInProcMore = "FF 11 00 C1 00 01 00 00 00 00 00 00 00"
    private val doneProcFinal = "FE 00 00 E0 00 00 00 00 00 00 00 00 00"

    /** Handvektor: COLMETADATA, ROW, DONE (`COUNT`, 1) der Antwort auf einen Batch; Quelle `SqlBatchDispatchTest`. */
    private val batchReply = listOf(
        BatchVectors.COLMETADATA_N,
        BatchVectors.row(7),
        BatchVectors.DONE_COUNT_1,
    ).joinToString(" ")

    /** Handvektor: dasselbe Ergebnis als RPC-Antwort (DONEINPROC, DONEPROC); Quelle `PreparedHandlesDispatchTest`. */
    private val rpcReply =
        listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), doneInProcMore, "79 00 00 00 00", doneProcFinal)
            .joinToString(" ")

    /**
     * Handvektor: `sp_prepexec`, RETURNSTATUS 0 und Handle 1 als RETURNVALUE ohne Namen vor dem DONEPROC; Quelle
     * `PreparedHandlesDispatchTest` (dort zu den Grenzen gegenüber dem Mitschnitt `prepare` der Referenzinstanz).
     */
    private val prepReply = listOf(
        BatchVectors.COLMETADATA_N,
        BatchVectors.row(7),
        doneInProcMore,
        "79 00 00 00 00",
        "AC 00 00 00 01 00 00 00 00 00 00 26 04 04 01 00 00 00",
        doneProcFinal,
    ).joinToString(" ")

    /**
     * Selbstkonsistenz: Bausteine (RETURNSTATUS, RETURNVALUE `@o`, DONEPROC) aus `AnswerFormsDispatchTest` und
     * `PreparedHandlesDispatchTest`; die Zusammensetzung ohne vorangehende Ergebnismenge ist nur dadurch belegt,
     * dass dieser Test besteht.
     */
    private val outputReply = listOf(
        "79 00 00 00 00",
        "AC 03 00 02 40 00 6F 00 01 00 00 00 00 01 00 26 04 04 2A 00 00 00",
        doneProcFinal,
    ).joinToString(" ")

    /** Handvektor: zwei Ergebnisse, `MORE` am ersten Abschluss; Quelle `AnswerFormsDispatchTest`. */
    private val twoResultsReply = listOf(
        BatchVectors.COLMETADATA_N,
        BatchVectors.row(1),
        "FD 11 00 C1 00 01 00 00 00 00 00 00 00",
        BatchVectors.COLMETADATA_N,
        BatchVectors.row(2),
        BatchVectors.DONE_COUNT_1,
    ).joinToString(" ")

    private val unsupportedRpc = ServerMessage(50002, 1, 16, "Unsupported RPC.")
    private val unknownHandle = ServerMessage(50009, 1, 16, "Unknown prepared handle.")

    private fun rpc(payload: ByteArray) = BatchVectors.raw(PacketType.RPC, payload)

    /** Die elf Nachrichten der zehn Schritte (Schritt 5 trägt zwei: `sp_prepexec`, `sp_execute`), danach das Ende. */
    private fun messages(): List<Inbound> = listOf(
        BatchVectors.message("select 7"),
        BatchVectors.message("select 7 where 1=0"),
        BatchVectors.message("select 1; select 2"),
        rpc(
            executeSql(
                "select @a, @o",
                "@a int, @o int output",
                param("@a", "26 04 00"),
                param("@o", "26 04 00", status = 1),
            ),
        ),
        rpc(prepExec(-1, "select @a", "@a int", param("@a", int4(7)))),
        rpc(execute(1, param("@a", int4(8)))),
        rpc(RpcWire.procId(UNKNOWN_PROC_ID)),
        rpc(execute(UNKNOWN_HANDLE)),
        BatchVectors.message("select 7 again"),
        rpc(executeSql("select 9", "")),
        rpc(execute(1, param("@a", int4(10)))),
        Inbound.EndOfStream,
    )

    /** Ein Ereignisstrom je Backend-Aufruf; die Fehlerfälle und `sp_execute` mit unbekanntem Handle rufen es nicht. */
    private fun runs(): Array<List<QueryEvent>> = arrayOf(
        oneRow,
        listOf(resultStart(), QueryEvent.ResultEnd(0)),
        listOf(
            resultStart(),
            QueryEvent.Row(listOf(1)),
            QueryEvent.ResultEnd(1),
            resultStart(),
            QueryEvent.Row(listOf(2)),
            QueryEvent.ResultEnd(1),
        ),
        listOf(QueryEvent.ReturnStatus(0), QueryEvent.OutputParameter("@o", SqlType.Int32, 42)),
        oneRow,
        oneRow,
        oneRow,
        oneRow,
        oneRow,
    )

    /** Die Texte und Parameter der neun Backend-Aufrufe in der Reihenfolge der Folge. */
    private fun expectedCalls(): List<Pair<String, List<QueryParameter>>> = listOf(
        "select 7" to emptyList(),
        "select 7 where 1=0" to emptyList(),
        "select 1; select 2" to emptyList(),
        "select @a, @o" to listOf(
            QueryParameter("@a", SqlType.Int32, null, ParameterDirection.INPUT),
            QueryParameter("@o", SqlType.Int32, null, ParameterDirection.OUTPUT),
        ),
        "select @a" to listOf(QueryParameter("@a", SqlType.Int32, 7, ParameterDirection.INPUT)),
        "select @a" to listOf(QueryParameter("@a", SqlType.Int32, 8, ParameterDirection.INPUT)),
        "select 7 again" to emptyList(),
        "select 9" to emptyList(),
        "select @a" to listOf(QueryParameter("@a", SqlType.Int32, 10, ParameterDirection.INPUT)),
    )

    /**
     * Zehn Schritte auf einer Sitzung ab `READY`: (1) Batch mit Ergebnis, (2) Batch mit leerem Ergebnis, (3) Batch
     * mit zwei Ergebnissen, (4) `sp_executesql` mit NULL- und OUTPUT-Parameter, (5) `sp_prepexec` und danach
     * `sp_execute` mit dem gelieferten Handle, (6) ein unbekannter RPC, (7) `sp_execute` mit unbekanntem Handle,
     * (8) ein Batch, (9) `sp_executesql`, (10) `sp_execute` mit dem Handle aus (5). Die Schritte (9) und (10)
     * sind der Zusatz: sie prüfen die Handle-Tabelle nach `sp_executesql`. Der Lauf enthält keine PLP- und keine
     * NBCROW-Form (nur Int32-Spalten und `D1`-ROW); deren Belege liegen bei der Datentyp-Erweiterung.
     * Zählung: Schritte 1 bis 10; Antworten 0-basiert in der Reihenfolge der elf Nachrichten, Schritt 5 belegt die
     * Antworten 4 und 5, ab Schritt 6 ist der Antwortindex gleich der Schrittnummer; die neun Backend-Aufrufe
     * sind die Schritte 1 bis 5 (zwei Aufrufe), 8, 9 und 10 (Schritt 6 und 7 rufen es nicht).
     * Der Anfragezustand ist bei jedem Backend-Aufruf `RUNNING`; dass er nach jeder Anfrageart nach `READY`
     * zurückkehrt, belegt die Annahme der nächsten Nachricht (außerhalb von `READY` schließt die Schleife), und
     * auf jede Anfrageart folgt eine weitere. Die Handle-Tabelle hält das Handle aus (5) über Batch, RPC und
     * beide definierten Fehler bis zu (10); ein definierter Fehler beendet die Sitzung nicht. Die Antworten sind
     * elf Nachrichten, die Backend-Aufrufe neun, und der Kanal ist ganz gelesen. Wächter: ein Zustand, der nach
     * einer Anfrageart nicht nach `READY` zurückkehrt; eine Leerung der Handle-Tabelle nach Batch,
     * `sp_executesql` oder im Fehlerpfad; ein Fehler, der die Verbindung schließt.
     *
     * @spec SPEC-001
     * @spec SPEC-002
     * @spec SPEC-030
     * @spec SPEC-057
     * @spec SPEC-013
     */
    @Test
    fun `Die Gesamtfolge der Anfragearten laeuft auf einer Sitzung ohne Zustandsverlust`() = blocking {
        val backend = ScriptedBackend.inSequence(*runs())
        val session = Session.login(*messages().toTypedArray())
        val states = mutableListOf<ConnectionState>()
        val logged = mutableListOf<ErrorLogEntry>()
        val probe = StateProbe(backend) { states += session.ready.live.context.state }
        session.serve(probe, ErrorMapper(ErrorLog { logged += it }))

        val answered = session.replies.size
        assertEquals(11, answered, "Antworten: nach $answered folgt Antwort ${REPLY_KINDS.getOrNull(answered)}")
        val called = backend.requests.size
        assertEquals(9, called, "Backend-Aufrufe: nach $called folgt Aufruf ${CALL_KINDS.getOrNull(called)}")
        assertEquals(List(9) { ConnectionState.RUNNING }, states)
        assertEquals(expectedCalls(), backend.requests.map { it.sql to it.parameters })
        assertEquals(List(9) { "s1" }, backend.requests.map(QueryRequest::sessionId))
        assertEquals(9, backend.closeCalls)
        assertTrue(session.channel.exhausted)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
        assertReplies(session)
        assertEquals(listOf(ErrorCode.TDS_UNSUPPORTED_RPC, ErrorCode.TDS_UNKNOWN_HANDLE), logged.map { it.code })
    }

    private fun assertReplies(session: Session) {
        val hexes = listOf(
            batchReply,
            BatchVectors.COLMETADATA_N + " " + BatchVectors.DONE_COUNT_0,
            twoResultsReply,
            outputReply,
            prepReply,
            rpcReply,
        )
        hexes.forEachIndexed { index, expected -> assertEquals(expected, session.hex(index), "Antwort ${index + 1}") }
        assertEquals(unsupportedRpc, session.tokens(UNKNOWN_RPC_REPLY).errorMessage())
        assertEquals(unknownHandle, session.tokens(UNKNOWN_HANDLE_REPLY).errorMessage())
        listOf(UNKNOWN_RPC_REPLY, UNKNOWN_HANDLE_REPLY).forEach {
            assertEquals(DoneStatus.ERROR, session.tokens(it).lastDone().status, "Antwort ${it + 1}")
            assertEquals(2, session.tokens(it).size, "Antwort ${it + 1}")
        }
        assertEquals(batchReply, session.hex(AFTER_ERRORS_BATCH_REPLY))
        assertEquals(rpcReply, session.hex(AFTER_ERRORS_BATCH_REPLY + 1))
        assertEquals(rpcReply, session.hex(AFTER_ERRORS_BATCH_REPLY + 2))
    }

    /** Liest bei jedem Aufruf den Zustand der Sitzung ([onExecute]) und reicht an [inner] durch. */
    private class StateProbe(private val inner: QueryBackend, private val onExecute: () -> Unit) :
        QueryBackend by inner {
        override suspend fun execute(request: QueryRequest): QueryExecution {
            onExecute()
            return inner.execute(request)
        }
    }

    private companion object {
        /** ProcID 15 (`sp_unprepare`) liegt außerhalb des Katalogs von `SPEC-030`. */
        const val UNKNOWN_PROC_ID = 15

        /** Ein Handle, das die Sitzung nie vergibt (sie zählt ab 1 und vergibt hier nur das Handle 1). */
        const val UNKNOWN_HANDLE = 99

        /** Die Antworten nach Index (0-basiert, ohne Login) mit Schritt und Anfrageart. */
        val REPLY_KINDS = listOf(
            "0 (Schritt 1, Batch)",
            "1 (Schritt 2, Batch leer)",
            "2 (Schritt 3, Batch mit zwei Ergebnissen)",
            "3 (Schritt 4, sp_executesql)",
            "4 (Schritt 5, sp_prepexec)",
            "5 (Schritt 5, sp_execute)",
            "6 (Schritt 6, unbekannter RPC)",
            "7 (Schritt 7, sp_execute mit unbekanntem Handle)",
            "8 (Schritt 8, Batch)",
            "9 (Schritt 9, sp_executesql)",
            "10 (Schritt 10, sp_execute mit Handle aus Schritt 5)",
        )

        /** Die Backend-Aufrufe nach Index (0-basiert) mit Schritt und Anfrageart. */
        val CALL_KINDS = listOf(
            "0 (Schritt 1, Batch)",
            "1 (Schritt 2, Batch leer)",
            "2 (Schritt 3, Batch mit zwei Ergebnissen)",
            "3 (Schritt 4, sp_executesql)",
            "4 (Schritt 5, sp_prepexec)",
            "5 (Schritt 5, sp_execute)",
            "6 (Schritt 8, Batch)",
            "7 (Schritt 9, sp_executesql)",
            "8 (Schritt 10, sp_execute mit Handle aus Schritt 5: Handle-Tabelle geleert?)",
        )

        /** Antwortindex (0-basiert) der Schritte 6, 7 und 8; Schritt 5 belegt die Indizes 4 und 5. */
        const val UNKNOWN_RPC_REPLY = 6
        const val UNKNOWN_HANDLE_REPLY = 7
        const val AFTER_ERRORS_BATCH_REPLY = 8
    }
}
