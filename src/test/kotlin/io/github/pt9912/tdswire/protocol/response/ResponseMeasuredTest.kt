package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.FailureScope
import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.testsupport.toHex
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Antwort-Tokens in der Lesart der Messung am echten SQL Server (Messprotokoll
 * `docs/messungen/referenzabgleich.md`, Messreihe am Draht W-1 bis W-6 und Abschnitt Mitschnitt-Lauf; Einzelmessung
 * je Instanz 2022 und 2025). Die Quelle der Handvektoren sind die Dateien
 * `referenztest-dotnet/Mitschnitt/messung/2022/<Szenario>.messung` (Image `mcr.microsoft.com/mssql/server:2022-latest`,
 * Digest `sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, SQL Server 16.0.4305.5,
 * `Microsoft.Data.SqlClient` 7.1.1); die Instanz 2025 zeigt in diesen Szenarien dieselbe Tokenfolge
 * (Messprotokoll). Die ERROR-Token sind die des Mappings dieser Tests (Nummer, Text und Server sind nicht
 * Gegenstand), verglichen werden Folge, Statusbits, Befehlskennung, RETURNSTATUS und Ordinal.
 */
class ResponseMeasuredTest {
    private val divideByZero = ServerMessage(8134, 1, 16, "Divide by zero error encountered.")
    private val invalidObject = ServerMessage(208, 1, 16, "Invalid object name.")

    /** Das Mapping der Tests: die Codes „208“ und „8134“ sind SQL-Server-Nummern, jeder andere eine eigene Nummer. */
    private val mapping = ErrorMapping(
        backend = {
            when (it.code) {
                "208" -> MappedError(invalidObject, closesConnection = false)
                "8134" -> MappedError(divideByZero, closesConnection = false)
                else -> MappedError(BACKEND_MESSAGE, closesConnection = false)
            }
        },
        typeMapping = FIXED_MAPPING.typeMapping,
        internal = FIXED_MAPPING.internal,
    )

    private fun request(vararg parameters: QueryParameter) =
        QueryRequest("s", "r", "SELECT 1", parameters.toList(), null)

    private fun run(
        form: ResponseForm,
        request: QueryRequest,
        layout: CallLayout,
        vararg events: QueryEvent,
    ): Pair<SequenceOutcome, List<Token>> {
        val sink = RecordingSink()
        val outcome = runBlocking {
            ResponseSequencer(mapping).run(ScriptedBackend.ofList(*events), request, sink, form, layout)
        }
        return outcome to sink.tokens
    }

    private fun rpc(request: QueryRequest, layout: CallLayout, vararg events: QueryEvent) =
        run(ResponseForm.RPC, request, layout, *events)

    private fun batch(vararg events: QueryEvent) = run(ResponseForm.BATCH, request(), CallLayout.PROCEDURE, *events)

    private fun failure(code: String, scope: FailureScope = FailureScope.BATCH) =
        QueryEvent.Failure(code, "kein Text fuer den Client", scope)

    private fun output(name: String, value: Int?) = QueryEvent.OutputParameter(name, SqlType.Int32, value)

    private fun input(name: String) = QueryParameter(name, SqlType.Int32, 9, ParameterDirection.INPUT)

    private fun out(name: String) = QueryParameter(name, SqlType.Int32, null, ParameterDirection.OUTPUT)

    private fun doneProc(status: Int = DoneStatus.FINAL) = DoneToken(DoneKind.DONE_PROC, status, DoneCommand.PROC, 0)

    private fun doneInProc(status: Int, command: Int, rows: Long = 0) =
        DoneToken(DoneKind.DONE_IN_PROC, status, command, rows)

    private fun returnValue(ordinal: Int, name: String, value: Int) = ReturnValueToken(
        ordinal,
        name,
        1,
        ColumnDef(name, TdsType.IntNType(4), nullable = true),
        TdsValue.IntegerValue(value.toLong()),
    )

    /**
     * W-1 (`rpc-fehler-vor-zeilen/01` und `rpc-fehler-batch-abbrechend/01`, `sp_executesql`, Fehler 208): ERROR,
     * RETURNSTATUS 208 (die Fehlernummer), DONEPROC mit `ERROR` ohne `MORE` und ohne `COUNT`, Befehlskennung `0x00e0`;
     * kein DONE. Wächter: ein DONE statt DONEPROC, ein fehlender oder falsch gesetzter RETURNSTATUS, `MORE` am
     * DONEPROC.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     */
    @Test
    fun `Fehler vor dem ersten Ergebnis im RPC endet in ERROR, RETURNSTATUS und DONEPROC mit ERROR`() {
        val (outcome, tokens) = rpc(request(input("@i")), CallLayout.EXECUTE_SQL, failure("208"))
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(
            listOf(ErrorToken(invalidObject), ReturnStatusToken(208), doneProc(DoneStatus.ERROR)),
            tokens,
        )
    }

    /**
     * W-2 (`rpc-fehler-nach-zeilen/01`, Fehler 8134 nach drei Zeilen): COLMETADATA, ROW, ERROR, DONEINPROC `MORE|ERROR`
     * (`0x00c1`), RETURNSTATUS 8134, DONEPROC ohne Fehlerbit. Wächter: das Fehlerbit am DONEPROC statt am
     * DONEINPROC, ein
     * fehlendes `MORE`, ein RETURNSTATUS 0.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     */
    @Test
    fun `Fehler nach Zeilen im RPC setzt das Fehlerbit an das DONEINPROC und nicht an das DONEPROC`() {
        val (outcome, tokens) = rpc(
            request(input("@i")),
            CallLayout.EXECUTE_SQL,
            start(),
            row(1),
            row(2),
            row(3),
            failure("8134"),
        )
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(
            listOf(
                ErrorToken(divideByZero),
                doneInProc(DoneStatus.MORE or DoneStatus.ERROR, DoneCommand.SELECT),
                ReturnStatusToken(8134),
                doneProc(),
            ),
            tokens.drop(1 + 3),
        )
        assertEquals(3, tokens.count { it is RowToken })
    }

    /**
     * W-2 (`rpc-fehler-statement-abbrechend/01`, `SELECT 1/0; SELECT 2`): COLMETADATA, ERROR, DONEINPROC `MORE|ERROR`
     * (`0x00c1`), COLMETADATA, ROW, DONEINPROC `MORE|COUNT` (`0x00c1`, eine Zeile), RETURNSTATUS 0, DONEPROC ohne Bits:
     * die Antwort läuft weiter, und der RETURNSTATUS ist 0 trotz Fehler 8134. Wächter: ein Lauf, der nach dem Fehler
     * endet, ein RETURNSTATUS mit der Fehlernummer.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     */
    @Test
    fun `Statement-abbrechender Fehler im RPC laesst die Antwort weiterlaufen und der RETURNSTATUS ist 0`() {
        val (outcome, tokens) = rpc(
            request(input("@i")),
            CallLayout.EXECUTE_SQL,
            start(),
            failure("8134", FailureScope.STATEMENT),
            start(),
            row(2),
            QueryEvent.ResultEnd(1),
        )
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(
            listOf(
                ColMetadataToken(listOf(ColumnDef("n", TdsType.Int4Type, nullable = false))),
                ErrorToken(divideByZero),
                doneInProc(DoneStatus.MORE or DoneStatus.ERROR, DoneCommand.SELECT),
                ColMetadataToken(listOf(ColumnDef("n", TdsType.Int4Type, nullable = false))),
                RowToken(listOf(TdsValue.IntegerValue(2))),
                doneInProc(DoneStatus.MORE or DoneStatus.COUNT, DoneCommand.SELECT, 1),
                ReturnStatusToken(0),
                doneProc(),
            ),
            tokens,
        )
    }

    /**
     * W-3 (`rpc-fehler-ausgabewert/01`, `SET @o = 1; SELECT 1/0`): Ein Ausgabewert, den das Backend vor dem Fehler
     * meldet, bleibt in der Antwort: `DONEINPROC`, COLMETADATA, ERROR, DONEINPROC `MORE|ERROR`, RETURNSTATUS 8134,
     * RETURNVALUE (Ordinal 2, Wert 1), DONEPROC. Wächter: ein Lauf, der den Ausgabewert nach dem Fehler verwirft oder
     * vor den ERROR schreibt.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     */
    @Test
    fun `Ein Ausgabewert vor dem Fehler bleibt in der Antwort hinter dem RETURNSTATUS`() {
        val (outcome, tokens) = rpc(
            request(out("@o")),
            CallLayout.EXECUTE_SQL,
            start(),
            row(1),
            QueryEvent.ResultEnd(1),
            output("@o", 1),
            start(),
            failure("8134"),
        )
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(
            listOf(
                ErrorToken(divideByZero),
                doneInProc(DoneStatus.MORE or DoneStatus.ERROR, DoneCommand.SELECT),
                ReturnStatusToken(8134),
                returnValue(2, "@o", 1),
                doneProc(),
            ),
            tokens.drop(4),
        )
    }

    /**
     * Eine Fehlernummer ab 50000 ist keine Nummer des echten Servers: ERROR und DONEPROC mit `ERROR`, ohne RETURNSTATUS
     * (Setzung des Nutzers vom 2026-11-06, kein Messbefund), auch wenn das Backend einen `ReturnStatus` gemeldet hat.
     * Wächter: ein RETURNSTATUS mit der eigenen Nummer.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein Fehler mit eigener Nummer schreibt keinen RETURNSTATUS`() {
        val (_, tokens) = rpc(
            request(),
            CallLayout.EXECUTE_SQL,
            QueryEvent.ReturnStatus(5),
            failure("TDS_BACKEND_ERROR"),
        )
        assertEquals(listOf(ErrorToken(BACKEND_MESSAGE), doneProc(DoneStatus.ERROR)), tokens)
    }

    /**
     * Mitschnitt-Lauf (`batch-statement-abbrechend`, 2022 und 2025): COLMETADATA, ERROR, DONE `MORE|ERROR` (`0x00c1`),
     * COLMETADATA, ROW, DONE `COUNT` (`0x00c1`, eine Zeile). Wächter: ein DONE ohne `MORE` am fehlerhaften
     * Abschluss, ein
     * Lauf, der nach dem Fehler endet.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Statement-abbrechender Fehler im Batch traegt MORE und ERROR und die Folgeanweisung laeuft`() {
        val (outcome, tokens) = batch(
            start(),
            failure("8134", FailureScope.STATEMENT),
            start(),
            row(2),
            QueryEvent.ResultEnd(1),
        )
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(
            listOf(
                ErrorToken(divideByZero),
                doneSelect(DoneStatus.MORE or DoneStatus.ERROR),
            ),
            tokens.drop(1).take(2),
        )
        assertEquals(doneSelect(DoneStatus.COUNT, 1), tokens.last())
        assertEquals(1, tokens.count { it is RowToken })
    }

    /**
     * Ein statement-abbrechender Fehler als letztes Ereignis: das DONE trägt `ERROR` ohne `MORE`, weil nichts folgt.
     * Wächter: ein `MORE` am letzten Abschluss.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein statement-abbrechender Fehler am Ende des Batches traegt kein MORE`() {
        val (outcome, tokens) = batch(start(), failure("8134", FailureScope.STATEMENT))
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(doneSelect(DoneStatus.ERROR), tokens.last())
    }

    /**
     * Mitschnitt-Lauf (`batch-batch-abbrechend`, 2022 und 2025): ERROR 208, DONE mit `ERROR` ohne `MORE`
     * (`0x00fd`); die zweite Anweisung läuft nicht, ein weiteres Ereignis wird nicht gelesen. Wächter: ein Lauf, der
     * weiterliest.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Batch-abbrechender Fehler im Batch beendet die Antwort mit DONE ERROR ohne MORE`() {
        val (outcome, tokens) = batch(failure("208"), start(), row(1), QueryEvent.ResultEnd(1))
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(listOf<Token>(ErrorToken(invalidObject), doneBatch(DoneStatus.ERROR)), tokens)
    }

    /**
     * Ein statement-abbrechender Fehler, nach dem die Abbildung die Verbindung schließt, beendet den Lauf trotzdem
     * (nach einem verbindungsschließenden Fehler gibt es keine Folgeanweisung). Wächter: ein Lauf, der weiterliest.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein verbindungsschliessender Fehler beendet den Lauf auch als statement-abbrechender`() {
        val closing = ErrorMapping(
            backend = { MappedError(INTERNAL_MESSAGE, closesConnection = true) },
            typeMapping = FIXED_MAPPING.typeMapping,
            internal = FIXED_MAPPING.internal,
        )
        val sink = RecordingSink()
        val outcome = runBlocking {
            ResponseSequencer(closing).run(
                ScriptedBackend.ofList(failure("x", FailureScope.STATEMENT), start()),
                request(),
                sink,
            )
        }
        assertEquals(SequenceOutcome.FAILED_CLOSE_CONNECTION, outcome)
        assertEquals(listOf<Token>(ErrorToken(INTERNAL_MESSAGE), doneBatch(DoneStatus.ERROR)), sink.tokens)
    }

    /**
     * Befehlskennung nach der Form des Ereignisses (W-6, Mitschnitt-Lauf): `ResultEnd` `0x00c1`, Strom ohne Ereignis im
     * Batch `0x00fd`, `UpdateCount` ohne Aussage (0), DONEPROC `0x00e0`. Wächter: eine Kennung 0 am Abschluss eines
     * Ergebnisses, eine Kennung, die der Port nicht trägt, am Update-Zähler.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Die Befehlskennung folgt der Form des Ereignisses`() {
        assertEquals(doneSelect(DoneStatus.COUNT, 2), batch(start(), QueryEvent.ResultEnd(2)).second.last())
        assertEquals(listOf<Token>(doneBatch(DoneStatus.FINAL)), batch().second)
        assertEquals(listOf<Token>(done(DoneStatus.COUNT, 4)), batch(QueryEvent.UpdateCount(4)).second)
        val (_, proc) = rpc(request(), CallLayout.PROCEDURE, QueryEvent.UpdateCount(4))
        assertEquals(DoneCommand.PROC, (proc.last() as DoneToken).curCmd)
        assertEquals(DoneCommand.NONE, (proc.first() as DoneToken).curCmd)
    }

    /**
     * W-5 (`ordinal-executesql/01` bis `/06`, 2022): das Ordinal ist die Position in der Aufrufliste, Text und
     * Deklarationsliste sind die Positionen 0 und 1. `@a OUT, @w IN, @b OUT` ergibt 2 und 4, `@w IN, @a OUT, @b OUT`
     * 3 und 4, `@a OUT, @b OUT` 2 und 3; die RETURNVALUE stehen in der Reihenfolge der Aufrufliste, auch wenn das
     * Backend `@b` zuerst meldet (`ordinal-executesql/04`, Zuweisung in umgekehrter Reihenfolge). Wächter: ein Ordinal,
     * das Ausgabewerte zählt (0 und 1), ein Ordinal ohne die zwei ersten Positionen, die Reihenfolge des Stroms.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Das Ordinal der RETURNVALUE ist die Position in der Aufrufliste von sp_executesql`() {
        fun ordinals(request: QueryRequest, vararg events: QueryEvent) = rpc(request, CallLayout.EXECUTE_SQL, *events)
            .second.filterIsInstance<ReturnValueToken>().map { it.ordinal to it.name }
        assertEquals(
            listOf(2 to "@a", 4 to "@b"),
            ordinals(request(out("@a"), input("@w"), out("@b")), output("@a", 1), output("@b", 2)),
        )
        assertEquals(
            listOf(3 to "@a", 4 to "@b"),
            ordinals(request(input("@w"), out("@a"), out("@b")), output("@a", 1), output("@b", 2)),
        )
        assertEquals(
            listOf(2 to "@a", 3 to "@b"),
            ordinals(request(out("@a"), out("@b")), output("@b", 2), output("@a", 1)),
        )
        assertEquals(
            listOf(2 to "@a"),
            ordinals(request(out("@a"), input("@w")), output("@a", 1)),
        )
    }

    /**
     * W-5 (`ordinal-prepexec/01` und `/02`, `ordinal-prozedur/01` und `/02`): `sp_prepexec` mit `@w IN, @a OUT` hat das
     * Handle an Position 0 und `@a` an Position 4 (Handle, Deklarationsliste und Text davor); `sp_execute` mit
     * demselben
     * Parameter `@a` an Position 2 (das Handle davor); der Aufruf per Prozedurname zählt ab 0 (`@a OUT, @w IN, @b OUT`
     * ergibt 0 und 2). Wächter: ein Ordinal, das das Handle als ersten Ausgabewert und `@a` als 1 zählt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Das Ordinal folgt der Aufrufform von sp_prepexec, sp_execute und dem Prozedurnamen`() {
        val handle = QueryEvent.OutputParameter(HANDLE_OUTPUT_NAME, SqlType.Int32, 1)
        val prep = rpc(
            request(input("@w"), out("@a")),
            CallLayout.PREPARE_EXECUTE,
            QueryEvent.ReturnStatus(0),
            handle,
            output("@a", 1),
        ).second.filterIsInstance<ReturnValueToken>()
        assertEquals(listOf(0 to "", 4 to "@a"), prep.map { it.ordinal to it.name })
        val execute = rpc(request(input("@w"), out("@a")), CallLayout.EXECUTE_PREPARED, output("@a", 1))
            .second.filterIsInstance<ReturnValueToken>()
        assertEquals(listOf(2 to "@a"), execute.map { it.ordinal to it.name })
        val procedure = rpc(
            request(out("@a"), input("@w"), out("@b")),
            CallLayout.PROCEDURE,
            output("@a", 1),
            output("@b", 2),
        ).second.filterIsInstance<ReturnValueToken>()
        assertEquals(listOf(0 to "@a", 2 to "@b"), procedure.map { it.ordinal to it.name })
    }

    /**
     * A-19 (`ordinal-prepexec/01`, 2022): das RETURNVALUE des Handles ist
     * `AC 0000 00 01 00000000 0000 26 04 04 <Handle>`:
     * Ordinal 0, Namenslänge 0, Status 1, Flag-Feld `0000`, `INTN` 4. Wächter: das Flag-Feld `0100` (nullbar).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Das RETURNVALUE des Handles hat das Flag-Feld 0000 wie die Referenz`() {
        val (_, tokens) = rpc(
            request(input("@w")),
            CallLayout.PREPARE_EXECUTE,
            QueryEvent.ReturnStatus(0),
            QueryEvent.OutputParameter(HANDLE_OUTPUT_NAME, SqlType.Int32, 1),
        )
        val encoder = TokenEncoder()
        tokens.filterIsInstance<ReturnValueToken>().forEach { encoder.write(it) }
        assertEquals("AC 00 00 00 01 00 00 00 00 00 00 26 04 04 01 00 00 00", encoder.toByteArray().toHex())
    }

    /**
     * Jede RPC-Antwort trägt einen RETURNSTATUS (W-5, Mitschnitte `rpc-parameter` und `rpc-ausgabewert`), 0, wenn das
     * Backend keinen meldet; ein RETURNSTATUS des Backends bleibt stehen. Wächter: eine Antwort ohne RETURNSTATUS, ein
     * RETURNSTATUS, der den Wert des Backends überschreibt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Jede RPC-Antwort traegt einen RETURNSTATUS`() {
        assertEquals(
            listOf(ReturnStatusToken(0)),
            rpc(request(), CallLayout.EXECUTE_SQL, start(), row(1), QueryEvent.ResultEnd(1))
                .second.filterIsInstance<ReturnStatusToken>(),
        )
        assertEquals(
            listOf(ReturnStatusToken(7)),
            rpc(request(), CallLayout.EXECUTE_SQL, QueryEvent.ReturnStatus(7))
                .second.filterIsInstance<ReturnStatusToken>(),
        )
    }

    /**
     * W-3 und W-4: ein Ergebnis, das nach der Zuweisung des Ausgabewerts entsteht, steht vor dem RETURNSTATUS und dem
     * RETURNVALUE, auch wenn das Backend den Ausgabewert vor dem Ergebnis meldet. Wächter: ein RETURNVALUE an der
     * Stelle
     * im Strom.
     *
     * @spec SPEC-030
     */
    @Test
    fun `RETURNSTATUS und RETURNVALUE stehen hinter allen Ergebnissen`() {
        val (_, tokens) = rpc(
            request(out("@o")),
            CallLayout.EXECUTE_SQL,
            output("@o", 5),
            start(),
            QueryEvent.ResultEnd(0),
        )
        assertEquals(
            listOf(ColMetadataToken::class, DoneToken::class, ReturnStatusToken::class, ReturnValueToken::class),
            tokens.dropLast(1).map { it::class },
        )
        assertTrue(tokens.last() is DoneToken)
    }
}
