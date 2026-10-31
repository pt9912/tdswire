package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.message
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.framing.PacketType
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Antwortformen der Anfrageschleife (`TDS-FA-10.a`, `SPEC-002`, `SPEC-030`, `SPEC-031`) gegen das
 * Test-Backend [ScriptedBackend]: mehrere Ergebnisse in Reihenfolge, RPC mit Rückgabestatus und OUTPUT und
 * Antwortformen, die das Profil nicht kennt. Die Bytes sind Handvektoren nach dem Tabellenlayout von
 * MS-TDS; Tokenfolge, Statusbits und Ordinale sind Festlegungen und weder am Referenzclient noch an einem
 * echten SQL Server gemessen.
 */
class AnswerFormsDispatchTest {
    private val rpcOutput = executeSql("select @o", "@o int output", param("@o", "26 04 00", status = 1))

    private fun rpc(payload: ByteArray) = BatchVectors.raw(PacketType.RPC, payload)

    private fun counted(more: Boolean, rows: Long) = "FD ${if (more) "11" else "10"} 00 00 00 ${RpcWire.le(rows, 8)}"

    private fun doneInProc(rows: Long) = "FF 11 00 00 00 ${RpcWire.le(rows, 8)}"

    private val doneProcFinal = "FE 00 00 00 00 00 00 00 00 00 00 00 00"

    private val returnStatus0 = "79 00 00 00 00"

    private fun returnValueInt(ordinal: Int, nameHex: String, valueHex: String) =
        "AC ${RpcWire.le(ordinal.toLong(), 2)} 02 $nameHex 01 00 00 00 00 01 00 26 04 $valueHex"

    private val nameO = "40 00 6F 00"

    private suspend fun runBatch(vararg events: QueryEvent): Pair<ScriptedBackend, Session> {
        val backend = ScriptedBackend.ofList(*events)
        val session = Session.login(BatchVectors.message("q"), Inbound.EndOfStream)
        session.serve(backend)
        return backend to session
    }

    private suspend fun runRpc(payload: ByteArray, vararg events: QueryEvent): Pair<ScriptedBackend, Session> {
        val backend = ScriptedBackend.ofList(*events)
        val session = Session.login(rpc(payload), Inbound.EndOfStream)
        session.serve(backend)
        return backend to session
    }

    /**
     * Zwei Ergebnisse und ein Update-Zähler in der Reihenfolge des Stroms: `MORE` an jedem Abschluss außer
     * dem letzten, `COUNT` mit der gemeldeten Zahl. Wächter: ein `MORE`, das auch den letzten Abschluss
     * trifft, oder eines, das an einem Abschluss fehlt; eine vertauschte Reihenfolge.
     *
     * @spec SPEC-002
     * @spec SPEC-031
     */
    @Test
    fun `Zwei Ergebnisse und ein Update-Zaehler stehen in der Reihenfolge des Stroms`() = blocking {
        val (backend, session) = runBatch(
            resultStart(),
            QueryEvent.Row(listOf(1)),
            QueryEvent.ResultEnd(1),
            QueryEvent.UpdateCount(5),
            resultStart(),
            QueryEvent.Row(listOf(2)),
            QueryEvent.ResultEnd(1),
        )
        val expected = listOf(
            BatchVectors.COLMETADATA_N,
            BatchVectors.row(1),
            counted(more = true, rows = 1),
            counted(more = true, rows = 5),
            BatchVectors.COLMETADATA_N,
            BatchVectors.row(2),
            counted(more = false, rows = 1),
        ).joinToString(" ")
        assertEquals(1, session.replies.size)
        assertEquals(expected, session.hex(0))
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Ein leeres Ergebnis zwischen zwei Ergebnissen hat Metadaten und einen Abschluss: ohne gemeldete Zahl
     * trägt er nur `MORE`, kein `COUNT`. Die Grenze ist an COLMETADATA und Abschluss erkennbar. Wächter:
     * ein leeres Ergebnis ohne COLMETADATA, ein `COUNT` ohne Zahl.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein leeres Ergebnis zwischen zwei Ergebnissen hat Metadaten und Abschluss`() = blocking {
        val (_, session) = runBatch(
            resultStart(),
            QueryEvent.Row(listOf(1)),
            QueryEvent.ResultEnd(1),
            resultStart(),
            QueryEvent.ResultEnd(null),
            resultStart(),
            QueryEvent.Row(listOf(2)),
            QueryEvent.ResultEnd(1),
        )
        val expected = listOf(
            BatchVectors.COLMETADATA_N,
            BatchVectors.row(1),
            counted(more = true, rows = 1),
            BatchVectors.COLMETADATA_N,
            "FD 01 00 00 00 00 00 00 00 00 00 00 00",
            BatchVectors.COLMETADATA_N,
            BatchVectors.row(2),
            counted(more = false, rows = 1),
        ).joinToString(" ")
        assertEquals(expected, session.hex(0))
    }

    /**
     * Ein RPC mit Ergebnis, Rückgabestatus und OUTPUT-Parameter: DONEINPROC, RETURNSTATUS, RETURNVALUE
     * (Name wie vom Backend genannt, Ordinal 0, Status 1) und DONEPROC. Wächter: DONE statt DONEINPROC oder
     * DONEPROC, eine andere Folge, ein Ordinal ungleich 0.
     *
     * @spec SPEC-030
     */
    @Test
    fun `RPC mit OUTPUT-Parameter und Rueckgabestatus liefert RETURNSTATUS, RETURNVALUE und DONEPROC`() = blocking {
        val (_, session) = runRpc(
            rpcOutput,
            resultStart(),
            QueryEvent.Row(listOf(7)),
            QueryEvent.ResultEnd(1),
            QueryEvent.ReturnStatus(0),
            QueryEvent.OutputParameter("@o", SqlType.Int32, 42),
        )
        val expected = listOf(
            BatchVectors.COLMETADATA_N,
            BatchVectors.row(7),
            doneInProc(1),
            returnStatus0,
            returnValueInt(0, nameO, "04 2A 00 00 00"),
            doneProcFinal,
        ).joinToString(" ")
        assertEquals(expected, session.hex(0))
    }

    /**
     * Ein NULL im OUTPUT-Parameter bleibt NULL (Längenbyte 0 im Wert); zwei OUTPUT-Parameter zählen
     * die Ordinale 0 und 1. Wächter: ein Ordinal, das nicht weiterzählt, ein NULL, das als 0 ankommt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `NULL im OUTPUT bleibt NULL und die Ordinale zaehlen`() = blocking {
        val (_, session) = runRpc(
            rpcOutput,
            QueryEvent.OutputParameter("@o", SqlType.Int32, null),
            QueryEvent.OutputParameter("@p", SqlType.Int32, 1),
        )
        val expected = listOf(
            returnValueInt(0, nameO, "00"),
            returnValueInt(1, "40 00 70 00", "04 01 00 00 00"),
            doneProcFinal,
        ).joinToString(" ")
        assertEquals(expected, session.hex(0))
        val values = session.tokens(0).filterIsInstance<ReturnValueToken>().map { it.value }
        assertEquals(listOf(TdsValue.NullValue, TdsValue.IntegerValue(1)), values)
    }

    /**
     * Ohne OUTPUT-Parameter im Strom entsteht kein RETURNVALUE; liefert das Backend auch keinen
     * Rückgabestatus, entsteht kein RETURNSTATUS, und die Antwort ist ein DONEPROC. Wächter: ein
     * RETURNVALUE oder RETURNSTATUS ohne Ereignis.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ohne Ausgabewerte im Strom entstehen weder RETURNVALUE noch RETURNSTATUS`() = blocking {
        val (_, session) = runRpc(executeSql("select 1", ""), QueryEvent.UpdateCount(2))
        assertEquals(listOf(doneInProc(2), doneProcFinal).joinToString(" "), session.hex(0))
    }

    /**
     * Zeile ohne ResultStart (Batch und RPC): die Antwort besteht aus ERROR (`TDS_INTERNAL_ERROR`) und
     * DONE mit Fehlerstatus, ohne Erfolgsabschluss davor; die Verbindung schließt. Wächter: ein Lauf, der
     * die Zeile durchreicht oder die Antwort mit einem Erfolgs-DONE beginnt.
     *
     * @spec SPEC-031
     * @spec SPEC-048
     */
    @Test
    fun `Eine Zeile ohne ResultStart beginnt keine Erfolgsantwort`() = blocking {
        val sessions = listOf(
            runBatch(QueryEvent.Row(listOf(1))).second,
            runRpc(rpcOutput, QueryEvent.Row(listOf(1))).second,
        )
        for (session in sessions) {
            val tokens = session.tokens(0)
            assertEquals(2, tokens.size)
            assertEquals(ErrorCode.TDS_INTERNAL_ERROR.rule.message(), tokens.errorMessage())
            assertEquals(DoneStatus.ERROR, tokens.lastDone().status)
            assertTrue(session.channel.closed)
        }
    }

    /**
     * Doppeltes ResultEnd nach einer Teilantwort (Batch und RPC): COLMETADATA, ROW, der Abschluss des
     * ersten ResultEnd mit `MORE`, ERROR und DONE mit Fehlerstatus; auf die Teilantwort folgt kein
     * Abschluss ohne `MORE` und ohne Fehlerstatus. Wächter: ein Lauf, der den ersten Abschluss als letzten
     * schreibt.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Auf eine Teilantwort folgt kein vollstaendiger Erfolgsabschluss`() = blocking {
        val events = arrayOf(
            resultStart(),
            QueryEvent.Row(listOf(1)),
            QueryEvent.ResultEnd(1),
            QueryEvent.ResultEnd(1),
        )
        val sessions = listOf(runBatch(*events).second, runRpc(rpcOutput, *events).second)
        for (session in sessions) {
            val tokens = session.tokens(0)
            val dones = tokens.filterIsInstance<DoneToken>()
            assertEquals(2, dones.size)
            assertEquals(DoneStatus.COUNT or DoneStatus.MORE, dones[0].status)
            assertEquals(DoneStatus.ERROR, dones[1].status)
            assertTrue(dones.none { it.status and (DoneStatus.MORE or DoneStatus.ERROR) == 0 })
            assertEquals(ErrorCode.TDS_INTERNAL_ERROR.rule.message(), tokens.errorMessage())
            assertTrue(session.channel.closed)
        }
        val rpcDone = sessions[1].tokens(0).filterIsInstance<DoneToken>()[0]
        assertEquals(DoneKind.DONE_IN_PROC, rpcDone.kind)
    }

    /**
     * OUTPUT-Parameter in der Antwort auf einen Batch ist keine Form des Profils: ERROR
     * `TDS_UNSUPPORTED_TYPE` und DONE mit Fehlerstatus, kein RETURNVALUE; ein Typ außerhalb der Abbildung
     * in der RPC-Antwort ebenso. Wächter: eine Form, die den Batch nicht unterscheidet; eine Abbildung, die
     * die Precision nicht prüft.
     *
     * @spec SPEC-031
     * @spec SPEC-014
     */
    @Test
    fun `Nicht unterstuetzte Formen ergeben TDS_UNSUPPORTED_TYPE ohne RETURNVALUE`() = blocking {
        val decimal39 = SqlType.Decimal(39, 0)
        val sessions = listOf(
            runBatch(QueryEvent.OutputParameter("@o", SqlType.Int32, 1)).second,
            runRpc(rpcOutput, QueryEvent.OutputParameter("@o", decimal39, BigDecimal.ONE)).second,
        )
        for (session in sessions) {
            val tokens = session.tokens(0)
            assertEquals(ErrorCode.TDS_UNSUPPORTED_TYPE.rule.message(), tokens.errorMessage())
            assertEquals(DoneStatus.ERROR, tokens.lastDone().status)
            assertTrue(tokens.none { it is ReturnValueToken })
        }
    }
}
