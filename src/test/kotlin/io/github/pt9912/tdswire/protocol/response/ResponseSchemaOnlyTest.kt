package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Antwort auf `ExecuteReader(SchemaOnly)` am Draht (`SPEC-031`, `SPEC-036` Form F, `ADR-0059` Entscheidung 3, 4
 * und 7). Die Handvektoren sind die Antworten des echten SQL Servers aus der Ablage der Messung
 * (`referenztest-dotnet/Mitschnitt/messung/2022/schemaonly-spalten.messung`, Anfrage 01, und
 * `schemaonly-sonderfaelle.messung`, Anfragen 03, 04 und 05; Messprotokoll `docs/messungen/referenzabgleich.md`, W-27
 * bis W-29; Image `mcr.microsoft.com/mssql/server:2022-latest`, `Microsoft.Data.SqlClient` 7.1.1); die Instanz 2025
 * zeigt dieselben Bytes (je ein Fall je Instanz). Die Abschlüsse und ihre Reihenfolge sind die Bytes der Messung, das
 * COLMETADATA ist das der Auswahl ohne `SET`; die Spaltenflags `0x0008` der Referenz schreibt die Bibliothek für keine
 * Auswahl aus einer Tabelle (siehe [colmetadata]).
 */
class ResponseSchemaOnlyTest {
    private val request = QueryRequest("s", "r", "SELECT", emptyList(), null)
    private val off = QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_OFF)
    private val on = QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_ON)

    private val person = listOf(
        ResultColumn("Id", SqlType.Int32, false),
        ResultColumn("Name", SqlType.UnicodeText, true),
        ResultColumn("Age", SqlType.Int32, true),
        ResultColumn("Score", SqlType.Decimal(10, 2), true),
    )

    private fun tokens(
        form: ResponseForm,
        layout: CallLayout,
        mapping: ErrorMapping,
        vararg events: QueryEvent,
    ): List<Token> {
        val sink = RecordingSink()
        runBlocking { ResponseSequencer(mapping).run(ScriptedBackend.ofList(*events), request, sink, form, layout) }
        return sink.tokens
    }

    private fun encode(tokens: List<Token>): String {
        val encoder = TokenEncoder()
        tokens.forEach { encoder.write(it) }
        return encoder.toByteArray().toHex()
    }

    private fun batch(vararg events: QueryEvent) =
        encode(tokens(ResponseForm.BATCH, CallLayout.PROCEDURE, FIXED_MAPPING, *events))

    private fun rpc(vararg events: QueryEvent) =
        encode(tokens(ResponseForm.RPC, CallLayout.EXECUTE_SQL, FIXED_MAPPING, *events))

    private fun hexOf(text: String) = hex(text).toHex()

    /**
     * Das COLMETADATA, das die Bibliothek für [columns] schreibt (das der Auswahl ohne `SET`). Die Spaltenflags der
     * Referenz tragen für Tabellenspalten `0x0008` (Messprotokoll W-27); die Bibliothek schreibt sie für keine Auswahl
     * aus einer Tabelle, auch nicht ohne `SET`: verglichen wird daher die Gleichheit mit der Auswahl ohne `SET` (W-27),
     * nicht die Bytes der Referenz.
     */
    private fun colmetadata(columns: List<ResultColumn>, form: ResponseForm = ResponseForm.BATCH): String {
        val events = arrayOf(QueryEvent.ResultStart(columns), QueryEvent.ResultEnd(0))
        val plain = tokens(form, CallLayout.EXECUTE_SQL, FIXED_MAPPING, *events)
        return encode(plain.filterIsInstance<ColMetadataToken>())
    }

    /**
     * Batch (W-27, Anfrage 01: `SELECT Id, Name, Age, Score FROM person` zwischen den `SET`): `DONE DONE COLMETADATA
     * DONE DONE`, die Abschlüsse `MORE` `0x00ba`, `MORE` `0x00b9`, `MORE|COUNT` `0x00c1` mit Zähler 0 und der letzte
     * ohne Bits `0x00ba`; keine Zeile. Wächter: eine Kennung 0 an einem `SET`, vertauschte `0x00b9` und `0x00ba`,
     * `COUNT` an einem `SET`, ein fehlendes `MORE`, ein Abschluss des Ergebnisses ohne `COUNT`.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Im Batch folgt der Antwort der Messung DONE DONE COLMETADATA DONE DONE`() {
        val answer = batch(off, on, QueryEvent.ResultStart(person), QueryEvent.ResultEnd(0), off)
        val expected = "$DONE_OFF_MORE $DONE_ON_MORE ${colmetadata(person)} $DONE_RESULT_MORE $DONE_OFF_LAST"
        assertEquals(hexOf(expected), answer)
    }

    /**
     * Batch ohne schließendes `SET` (die Auswahl ist das letzte Ereignis): der Abschluss der Auswahl trägt kein `MORE`
     * (Setzung: der Lauf setzt `MORE` nur, wenn ein Ereignis folgt; nicht gemessen). Wächter: ein festes `MORE`.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ohne schliessendes SET endet die Antwort mit dem Abschluss der Auswahl ohne MORE`() {
        val answer = batch(off, on, QueryEvent.ResultStart(person), QueryEvent.ResultEnd(0))
        assertTrue(answer.endsWith(hexOf(DONE_RESULT_LAST)), answer)
    }

    /**
     * Batch mit der Tabelle ohne Schlüssel (W-26, Anfrage 03 von `schemaonly-sonderfaelle`: `A int NOT NULL`, `B
     * nvarchar(10) NULL`): dieselbe Form, das COLMETADATA hat `nvarchar(10)` mit der Länge 20 Bytes.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Eine Tabelle ohne Schluessel antwortet in derselben Form`() {
        val columns = listOf(ResultColumn("A", SqlType.Int32, false), ResultColumn("B", SqlType.UnicodeText, true, 10))
        val answer = batch(off, on, QueryEvent.ResultStart(columns), QueryEvent.ResultEnd(0), off)
        val expected = "$DONE_OFF_MORE $DONE_ON_MORE ${colmetadata(columns)} $DONE_RESULT_MORE $DONE_OFF_LAST"
        assertEquals(hexOf(expected), answer)
    }

    /**
     * RPC (W-28, Anfrage 04: `sp_executesql` ohne schließendes `SET`): `DONEINPROC DONEINPROC COLMETADATA DONEINPROC
     * RETURNSTATUS DONEPROC`, die zwei Arten mit ihren Kennungen `0x00ba` und `0x00b9` ohne `COUNT`, der Abschluss der
     * Auswahl `MORE|COUNT` `0x00c1` mit Zähler 0, RETURNSTATUS 0, DONEPROC `0x00e0`. Wächter: die Kennung 0 der zwei
     * Arten im RPC (jede andere Art ohne Zeilenzahl zählt dort wie `UNKNOWN`), `COUNT` an ihnen, ein DONE statt
     * DONEINPROC.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     * @spec TDS-QA-01
     */
    @Test
    fun `Im RPC tragen die zwei SET ihre Kennungen als DONEINPROC`() {
        val columns = listOf(ResultColumn("Id", SqlType.Int32, false), ResultColumn("Name", SqlType.UnicodeText, true))
        val answer = rpc(off, on, QueryEvent.ResultStart(columns), QueryEvent.ResultEnd(0))
        val expected = "$DONEINPROC_OFF $DONEINPROC_ON ${colmetadata(columns, ResponseForm.RPC)} " +
            "$DONEINPROC_RESULT $RETURNSTATUS_DONEPROC"
        assertEquals(hexOf(expected), answer)
    }

    /**
     * Fehler (W-29, Anfrage 05: unbekannte Tabelle): `DONE DONE ERROR DONE`, die zwei führenden Abschlüsse stehen vor
     * dem ERROR, das letzte `DONE` trägt `ERROR` und `0x00fd` (Batch ohne offenes Ergebnis); kein Abschluss für ein
     * schließendes `SET`, kein SESSIONSTATE. Die ERROR-Token sind die des Mappings dieser Tests, verglichen wird die
     * Folge. Wächter: ein Lauf, der die zwei Abschlüsse nach dem ERROR schreibt oder weglässt.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Bei einer unbekannten Tabelle stehen die zwei Abschluesse vor dem ERROR`() {
        val invalid = ServerMessage(208, 1, 16, "Invalid object name 'gibtesnicht'.")
        val mapping = ErrorMapping(
            backend = { MappedError(invalid, closesConnection = false) },
            typeMapping = FIXED_MAPPING.typeMapping,
            internal = FIXED_MAPPING.internal,
        )
        val failure = QueryEvent.Failure("208", "Tabelle")
        val actual = tokens(ResponseForm.BATCH, CallLayout.PROCEDURE, mapping, off, on, failure)
        assertEquals(
            listOf<Token>(
                DoneToken(DoneKind.DONE, DoneStatus.MORE, 0x00BA, 0),
                DoneToken(DoneKind.DONE, DoneStatus.MORE, 0x00B9, 0),
                ErrorToken(invalid),
                DoneToken(DoneKind.DONE, DoneStatus.ERROR, DoneCommand.BATCH, 0),
            ),
            actual,
        )
    }

    private companion object {
        /** Die Abschlüsse der Messung 2022: `schemaonly-spalten` Anfrage 01, `schemaonly-sonderfaelle` Anfrage 04. */
        const val DONE_OFF_MORE = "fd0100ba000000000000000000"
        const val DONE_ON_MORE = "fd0100b9000000000000000000"
        const val DONE_RESULT_MORE = "fd1100c1000000000000000000"
        const val DONE_RESULT_LAST = "fd1000c1000000000000000000"
        const val DONE_OFF_LAST = "fd0000ba000000000000000000"
        const val DONEINPROC_OFF = "ff0100ba000000000000000000"
        const val DONEINPROC_ON = "ff0100b9000000000000000000"
        const val DONEINPROC_RESULT = "ff1100c1000000000000000000"
        const val RETURNSTATUS_DONEPROC = "7900000000fe0000e0000000000000000000"
    }
}
