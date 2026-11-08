package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private const val DONEPROC = "79 00 00 00 00 FE 00 00 E0 00 00 00 00 00 00 00 00 00"

/**
 * Die Befehlskennung (`curCmd`) des Abschlusses eines Änderungszählers folgt der Art der Anweisung, die das Backend
 * im `UpdateCount` meldet (`SPEC-031`, `SPEC-059`). Die Handvektoren sind die Antworten des echten SQL Servers der
 * Szenarien `dml-batch` und `dml-rpc` (Messprotokoll `docs/messungen/referenzabgleich.md`, W-6; Dateien
 * `referenztest-dotnet/Mitschnitt/messung/2022/dml-batch.messung` und `dml-rpc.messung`, Image
 * `mcr.microsoft.com/mssql/server:2022-latest`, `Microsoft.Data.SqlClient` 7.1.1); die Instanz 2025 zeigt dieselben
 * Bytes (je ein Fall je Instanz).
 */
class ResponseStatementKindTest {
    private val request = QueryRequest("s", "r", "INSERT", emptyList(), null)

    private fun answer(form: ResponseForm, layout: CallLayout, vararg events: QueryEvent): String {
        val sink = RecordingSink()
        val outcome = runBlocking {
            ResponseSequencer(FIXED_MAPPING).run(ScriptedBackend.ofList(*events), request, sink, form, layout)
        }
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        return encode(sink.tokens)
    }

    private fun encode(tokens: List<Token>): String {
        val encoder = TokenEncoder()
        tokens.forEach { encoder.write(it) }
        return encoder.toByteArray().toHex()
    }

    private fun batch(vararg events: QueryEvent) = answer(ResponseForm.BATCH, CallLayout.PROCEDURE, *events)

    private fun rpc(vararg events: QueryEvent) = answer(ResponseForm.RPC, CallLayout.EXECUTE_SQL, *events)

    private fun hexOf(text: String) = hex(text).toHex()

    /**
     * Im SQL_BATCH trägt das DONE `COUNT` (`0x10`), die Zeilenzahl und die Kennung der Art: INSERT `0x00c3`, UPDATE
     * `0x00c5`, DELETE `0x00c4`, je mit zwei Zeilen (Handvektoren `dml-batch`). Wächter: die Kennung 0 für jede Art,
     * vertauschte Kennungen, ein fehlendes `COUNT`.
     *
     * @spec SPEC-031
     * @spec TDS-QA-01
     */
    @Test
    fun `Im Batch traegt der Abschluss die Kennung der Art`() {
        val insert = batch(QueryEvent.UpdateCount(2, StatementKind.INSERT))
        val update = batch(QueryEvent.UpdateCount(2, StatementKind.UPDATE))
        val delete = batch(QueryEvent.UpdateCount(2, StatementKind.DELETE))
        assertEquals(hexOf("FD 10 00 C3 00 02 00 00 00 00 00 00 00"), insert)
        assertEquals(hexOf("FD 10 00 C5 00 02 00 00 00 00 00 00 00"), update)
        assertEquals(hexOf("FD 10 00 C4 00 02 00 00 00 00 00 00 00"), delete)
    }

    /**
     * Ohne Treffer bleibt `COUNT` gesetzt und die Zeilenzahl ist 0 (`DELETE` ohne Treffer in `dml-batch`). Wächter: ein
     * Abschluss, der bei der Zahl 0 das Bit `COUNT` weglässt.
     *
     * @spec SPEC-031
     * @spec TDS-QA-01
     */
    @Test
    fun `Eine Anweisung ohne Treffer traegt COUNT mit der Zahl 0 und die Kennung der Art`() {
        val delete = batch(QueryEvent.UpdateCount(0, StatementKind.DELETE))
        assertEquals(hexOf("FD 10 00 C4 00 00 00 00 00 00 00 00 00"), delete)
    }

    /**
     * `UNKNOWN` und der Aufruf ohne Art (ein Backend, das die Art nicht meldet) schreiben die Kennung 0 mit `COUNT` und
     * der Zahl, wie vor der Fläche `kind` (ADR-0042 Entscheidung 4). Wächter: ein Standardwert `INSERT` im Konstruktor.
     *
     * @spec SPEC-031
     * @spec SPEC-059
     */
    @Test
    fun `Ohne Art oder mit UNKNOWN steht die Kennung 0 mit COUNT`() {
        val expected = hexOf("FD 10 00 00 00 05 00 00 00 00 00 00 00")
        assertEquals(expected, batch(QueryEvent.UpdateCount(5)))
        assertEquals(expected, batch(QueryEvent.UpdateCount(5, StatementKind.UNKNOWN)))
    }

    /**
     * Im RPC (`sp_executesql`) ist der Abschluss ein DONEINPROC `MORE|COUNT` (`0x11`) mit der Kennung der Art, am
     * Ende RETURNSTATUS 0 und DONEPROC `0x00e0` (Handvektoren `dml-rpc`: INSERT mit zwei, UPDATE und DELETE mit drei
     * Zeilen). Wächter: die Kennung 0 im DONEINPROC, die Kennung der Art am DONEPROC.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     * @spec TDS-QA-01
     */
    @Test
    fun `Im RPC traegt das DONEINPROC die Kennung der Art`() {
        assertEquals(
            hexOf("FF 11 00 C3 00 02 00 00 00 00 00 00 00  $DONEPROC"),
            rpc(QueryEvent.UpdateCount(2, StatementKind.INSERT)),
        )
        assertEquals(
            hexOf("FF 11 00 C5 00 03 00 00 00 00 00 00 00  $DONEPROC"),
            rpc(QueryEvent.UpdateCount(3, StatementKind.UPDATE)),
        )
        assertEquals(
            hexOf("FF 11 00 C4 00 03 00 00 00 00 00 00 00  $DONEPROC"),
            rpc(QueryEvent.UpdateCount(3, StatementKind.DELETE)),
        )
    }

    /**
     * Drei Anweisungen in einem `sp_executesql` (INSERT, UPDATE, DELETE mit je einer Zeile) ergeben je ein DONEINPROC
     * mit der eigenen Kennung, dann RETURNSTATUS und DONEPROC (Handvektor `dml-rpc`, vierte Antwort). Wächter: eine
     * Kennung, die für die ganze Antwort einmal gilt.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     */
    @Test
    fun `Drei Anweisungen im RPC tragen je ihre Kennung`() {
        val one = "00 01 00 00 00 00 00 00 00"
        assertEquals(
            hexOf("FF 11 00 C3 $one  FF 11 00 C5 $one  FF 11 00 C4 $one  $DONEPROC"),
            rpc(
                QueryEvent.UpdateCount(1, StatementKind.INSERT),
                QueryEvent.UpdateCount(1, StatementKind.UPDATE),
                QueryEvent.UpdateCount(1, StatementKind.DELETE),
            ),
        )
    }

    /**
     * Die Antwort auf eine Nachricht des Massenladens trägt `0x00f0` auch dann, wenn das Ereignis eine Art nennt
     * (Messprotokoll M-8 und M-12: der Befehl gehört der Form). Wächter: eine Kennung der Art in der Form des
     * Massenladens.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     */
    @Test
    fun `In der Form des Massenladens bleibt die Kennung 0xf0`() {
        val answer = answer(ResponseForm.BULK, CallLayout.PROCEDURE, QueryEvent.UpdateCount(3, StatementKind.INSERT))
        assertEquals(hexOf("FD 10 00 F0 00 03 00 00 00 00 00 00 00"), answer)
    }

    /**
     * Im SQL_BATCH schließen `CREATE TABLE` und `ALTER TABLE … ADD` mit einem DONE ohne `COUNT`: Status 0, Kennung
     * `0x00c6` bzw. `0x00d8`, Zeilenzahl 0 (Handvektoren `katalog-struktur`, Messprotokoll M-20; Dateien
     * `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/2022/katalog-struktur.messung`, Antworten auf
     * `CREATE TABLE [produkte] …` und `ALTER TABLE [kunden] ADD [Email] …` bei `implicit_transactions off`; 2025 zeigt
     * dieselben Bytes). Das Backend meldet die Zahl 0 oder eine andere: die Bibliothek ignoriert sie. Wächter: das Bit
     * `COUNT` bei einer Strukturänderung, vertauschte Kennungen, eine Zeilenzahl ungleich 0 im Abschluss.
     *
     * @spec SPEC-031
     * @spec SPEC-035
     * @spec TDS-QA-01
     */
    @Test
    fun `Im Batch schliessen CREATE TABLE und ALTER TABLE ohne COUNT`() {
        val create = hexOf("FD 00 00 C6 00 00 00 00 00 00 00 00 00")
        val alter = hexOf("FD 00 00 D8 00 00 00 00 00 00 00 00 00")
        assertEquals(create, batch(QueryEvent.UpdateCount(0, StatementKind.CREATE_TABLE)))
        assertEquals(alter, batch(QueryEvent.UpdateCount(0, StatementKind.ALTER_TABLE)))
        assertEquals(create, batch(QueryEvent.UpdateCount(7, StatementKind.CREATE_TABLE)))
        assertEquals(alter, batch(QueryEvent.UpdateCount(7, StatementKind.ALTER_TABLE)))
    }

    /**
     * Die Strukturänderungen sind nur im SQL_BATCH gemessen. Im RPC und in der Form des Massenladens schreibt die
     * Bibliothek sie wie `UNKNOWN` beziehungsweise mit `0x00f0`: Kennung 0 mit `COUNT` und der Zahl des Ereignisses
     * (Grenze, `SPEC-031`). Wächter: die Kennung `0x00c6` im DONEINPROC, das fehlende `COUNT` im RPC.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     */
    @Test
    fun `Im RPC und im Massenladen gilt die Kennung der Strukturaenderung nicht`() {
        assertEquals(
            hexOf("FF 11 00 00 00 00 00 00 00 00 00 00 00  $DONEPROC"),
            rpc(QueryEvent.UpdateCount(0, StatementKind.CREATE_TABLE)),
        )
        assertEquals(
            hexOf("FF 11 00 00 00 00 00 00 00 00 00 00 00  $DONEPROC"),
            rpc(QueryEvent.UpdateCount(0, StatementKind.ALTER_TABLE)),
        )
        val created = QueryEvent.UpdateCount(0, StatementKind.CREATE_TABLE)
        val bulk = answer(ResponseForm.BULK, CallLayout.PROCEDURE, created)
        assertEquals(hexOf("FD 10 00 F0 00 00 00 00 00 00 00 00 00"), bulk)
    }

    /**
     * Die Transaktionstexte der Form C schließen im SQL_BATCH ohne `COUNT`: `set implicit_transactions on` mit
     * `0x00b9`; `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN` als `DONE MORE` `0x00ba` und `DONE`
     * `0x00c0` (Handvektoren `katalog-struktur`, Messprotokoll M-20, 2022 und 2025 gleich; zweiter Abschluss ohne
     * offene Transaktion). Wächter: vertauschte Kennungen, `MORE` am letzten Abschluss, `COUNT`.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Die Transaktionstexte schliessen ohne COUNT mit ihren Kennungen`() {
        assertEquals(
            hexOf("FD 00 00 B9 00 00 00 00 00 00 00 00 00"),
            batch(QueryEvent.UpdateCount(0, StatementKind.IMPLICIT_TRANSACTIONS_ON)),
        )
        assertEquals(
            hexOf("FD 01 00 BA 00 00 00 00 00 00 00 00 00  FD 00 00 C0 00 00 00 00 00 00 00 00 00"),
            batch(
                QueryEvent.UpdateCount(0, StatementKind.IMPLICIT_TRANSACTIONS_OFF),
                QueryEvent.UpdateCount(0, StatementKind.COMMIT_IF_OPEN),
            ),
        )
    }
}
