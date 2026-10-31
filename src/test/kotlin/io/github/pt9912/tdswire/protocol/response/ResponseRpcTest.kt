package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Antwortform [ResponseForm.RPC] des [ResponseSequencer] (`TDS-FA-10.a` Schritt 2 und 3, `SPEC-030`,
 * `SPEC-031`) gegen das Test-Backend [ScriptedBackend]. Tokenfolge, Statusbits und Ordinale sind
 * Festlegungen (Handvektor und Selbstkonsistenz), weder am Referenzclient noch an einem echten SQL Server
 * gemessen.
 */
class ResponseRpcTest {
    private fun sequence(form: ResponseForm, vararg events: QueryEvent): Pair<SequenceOutcome, List<Token>> {
        val sink = RecordingSink()
        val outcome = runBlocking {
            ResponseSequencer(FIXED_MAPPING).run(ScriptedBackend.ofList(*events), REQUEST, sink, form)
        }
        return outcome to sink.tokens
    }

    private fun rpc(vararg events: QueryEvent) = sequence(ResponseForm.RPC, *events)

    private fun output(name: String, value: Any?, type: SqlType = SqlType.Int32) =
        QueryEvent.OutputParameter(name, type, value)

    private fun intColumn(name: String) = ColumnDef(name, TdsType.IntNType(4), nullable = true)

    private val doneProcFinal = DoneToken(DoneKind.DONE_PROC, DoneStatus.FINAL, 0, 0)

    /**
     * Ergebnis, Rückgabestatus und zwei Ausgabewerte: DONEINPROC mit `MORE` und `COUNT`, RETURNSTATUS, je
     * ein RETURNVALUE mit Ordinal ab 0 in der Reihenfolge der Ereignisse (Status 1, nullbarer Typ), zuletzt
     * DONEPROC ohne Bits. Wächter: ein DONE statt DONEINPROC, ein fehlendes `MORE` vor dem RETURNSTATUS,
     * ein Ordinal, das nicht zählt.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     */
    @Test
    fun `RPC schreibt DONEINPROC, RETURNSTATUS, RETURNVALUE mit Ordinal und DONEPROC`() {
        val (outcome, tokens) = rpc(
            start(),
            row(7),
            QueryEvent.ResultEnd(1),
            QueryEvent.ReturnStatus(-3),
            output("@a", 1),
            output("@b", null),
        )
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals(
            listOf(
                ColMetadataToken(listOf(ColumnDef("n", TdsType.Int4Type, nullable = false))),
                RowToken(listOf(TdsValue.IntegerValue(7))),
                DoneToken(DoneKind.DONE_IN_PROC, COUNT_MORE, 0, 1),
                ReturnStatusToken(-3),
                ReturnValueToken(0, "@a", 1, intColumn("@a"), TdsValue.IntegerValue(1)),
                ReturnValueToken(1, "@b", 1, intColumn("@b"), TdsValue.NullValue),
                doneProcFinal,
            ),
            tokens,
        )
    }

    /**
     * Ohne Ereignis endet die RPC-Antwort in einem DONEPROC; ohne `OutputParameter` entsteht kein
     * RETURNVALUE, und ein Update-Zähler ist ein DONEINPROC mit `COUNT` und `MORE`. Wächter: ein DONE am
     * Ende, ein RETURNVALUE ohne Ereignis, ein DONEINPROC ohne `MORE` vor dem DONEPROC.
     *
     * @spec SPEC-030
     */
    @Test
    fun `RPC ohne Ausgabewerte schreibt kein RETURNVALUE`() {
        assertEquals(listOf<Token>(doneProcFinal), rpc().second)
        val (_, counted) = rpc(QueryEvent.UpdateCount(5))
        assertEquals(
            listOf<Token>(DoneToken(DoneKind.DONE_IN_PROC, COUNT_MORE, 0, 5), doneProcFinal),
            counted,
        )
        assertEquals(listOf(ReturnStatusToken(0), doneProcFinal), rpc(QueryEvent.ReturnStatus(0)).second)
    }

    /**
     * Dasselbe Ereignis in der Form BATCH wird abgelehnt (Fehler der Typabbildung, Ausgang FAILED, nichts
     * davor geschrieben). Wächter: eine Formprüfung, die den Batch durchlässt.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Im Batch bleiben ReturnStatus und OutputParameter abgelehnt`() {
        for (event in listOf(QueryEvent.ReturnStatus(1), output("@a", 1))) {
            val (outcome, tokens) = sequence(ResponseForm.BATCH, event)
            assertEquals(SequenceOutcome.FAILED, outcome, "$event")
            assertEquals(listOf(ErrorToken(TYPE_MESSAGE), done(DoneStatus.ERROR)), tokens, "$event")
        }
    }

    /**
     * Ein zweiter `ReturnStatus`, ein `ReturnStatus` oder `OutputParameter` in einem offenen Ergebnis sind
     * Reihenfolgeverstöße: ERROR (intern), DONE mit Fehlerstatus, Ausgang „Verbindung schließen“, kein
     * RETURNVALUE. Wächter: ein Lauf, der den zweiten RETURNSTATUS schreibt oder im offenen Ergebnis
     * schreibt.
     *
     * @spec SPEC-031
     * @spec SPEC-048
     */
    @Test
    fun `RPC-Ereignisse in falscher Folge sind ein Reihenfolgeverstoss`() {
        val cases = mapOf(
            "zweiter ReturnStatus" to listOf(QueryEvent.ReturnStatus(0), QueryEvent.ReturnStatus(1)),
            "ReturnStatus im offenen Ergebnis" to listOf(start(), QueryEvent.ReturnStatus(0)),
            "OutputParameter im offenen Ergebnis" to listOf(start(), output("@a", 1)),
        )
        for ((name, events) in cases) {
            val (outcome, tokens) = rpc(*events.toTypedArray())
            assertEquals(SequenceOutcome.FAILED_CLOSE_CONNECTION, outcome, name)
            assertEquals(listOf(ErrorToken(INTERNAL_MESSAGE), done(DoneStatus.ERROR)), tokens.takeLast(2), name)
            assertTrue(tokens.none { it is ReturnValueToken }, name)
        }
        val (_, second) = rpc(QueryEvent.ReturnStatus(0), QueryEvent.ReturnStatus(1))
        assertEquals(1, second.count { it is ReturnStatusToken })
    }

    /**
     * Ein Ausgabewert, den die Typabbildung nicht schreiben kann (Precision über 38, Klasse des Werts
     * passt nicht zum Typ, Name über 255 Zeichen), ergibt einen Fehler der Typabbildung statt eines
     * RETURNVALUE. Wächter: eine Abbildung, die Typ oder Wert nicht prüft.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein nicht abbildbarer Ausgabewert ist ein Fehler der Typabbildung`() {
        val cases = mapOf(
            "Precision 39" to output("@a", BigDecimal.ONE, SqlType.Decimal(39, 0)),
            "Long in Int32" to output("@a", 1L),
            "Name zu lang" to output("@" + "x".repeat(255), 1),
        )
        for ((name, event) in cases) {
            val (outcome, tokens) = rpc(event)
            assertEquals(SequenceOutcome.FAILED, outcome, name)
            assertEquals(listOf(ErrorToken(TYPE_MESSAGE), done(DoneStatus.ERROR)), tokens, name)
        }
    }

    /**
     * Ein Fehler des Backends nach einem Ergebnis: das zurückgehaltene DONEINPROC bekommt `MORE`, danach
     * ERROR und DONE mit Fehlerstatus; kein DONEPROC folgt (`SPEC-031`).
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein Fehler nach einem Ergebnis beendet die RPC-Antwort ohne Erfolgsabschluss`() {
        val (outcome, tokens) = rpc(start(), QueryEvent.ResultEnd(null), QueryEvent.Failure("x", "m"))
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertIs<ColMetadataToken>(tokens.first())
        assertEquals(
            listOf(
                DoneToken(DoneKind.DONE_IN_PROC, DoneStatus.MORE, 0, 0),
                ErrorToken(BACKEND_MESSAGE),
                done(DoneStatus.ERROR),
            ),
            tokens.drop(1),
        )
    }
}
