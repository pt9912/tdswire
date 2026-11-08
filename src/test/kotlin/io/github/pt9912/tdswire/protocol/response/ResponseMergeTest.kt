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
import io.github.pt9912.tdswire.protocol.token.InfoToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

private const val COLUMN_TYPE_HEX = "e7 14 00 09 04 d0 00 34 07 24 00 61 00 63 00 74 00 69 00 6f 00 6e 00"
private const val COLUMN_HEX = "81 01 00 00 00 00 00 00 00  $COLUMN_TYPE_HEX"
private const val INSERT_ROW = "d1 0c 00 49 00 4e 00 53 00 45 00 52 00 54 00"
private const val DONEPROC_OK = "fe 00 00 e0 00 00 00 00 00 00 00 00 00"
private const val DONEPROC_ERROR = "fe 02 00 e0 00 00 00 00 00 00 00 00 00"
private const val MERGE_ERROR_DONE = "ff 03 00 17 01 00 00 00 00 00 00 00 00"
private const val TRUNCATED = 2628
private const val DUPLICATE_KEY = 2627
private const val UNKNOWN_OBJECT = 208
private const val CLASS_ERROR = 16
private const val CLASS_KEY = 14

/**
 * Das Ergebnis eines `MERGE … OUTPUT $action` und sein Abschluss (`SPEC-031`, `SPEC-036`, Messprotokoll M-13, M-15 und
 * M-26). Die Handvektoren sind Antworten des echten SQL Servers 2022 (`referenztest-dotnet/Mitschnitt/messung/
 * bulk-merge-katalog/2022/merge-update.messung`, `merge-skip.messung` und `merge-fehler.messung`, Image
 * `mcr.microsoft.com/mssql/server:2022-latest`, `Microsoft.Data.SqlClient` 7.1.1); die Instanz 2025 zeigt dieselben
 * Bytes. Die Texte der ERROR sind die der Zuordnung (kürzer als die des Servers), nicht gemessen. Der Server schreibt
 * vor dem Typ der Spalte zwei Byte mehr als die Bibliothek (`00 00`, nicht gedeutet, nicht Gegenstand dieser Tests);
 * ab dem Typ `e7` stimmen Spalte und Rest der Antwort byteweise überein.
 */
class ResponseMergeTest {
    private val request = QueryRequest("s", "r", "MERGE", emptyList(), null)
    private val action = ResultColumn("\$action", SqlType.UnicodeText, nullable = false, maxLength = 10)

    private fun mapping(number: Int, severity: Int) = ErrorMapping(
        backend = { MappedError(ServerMessage(number, 1, severity, "fehler"), closesConnection = false) },
        typeMapping = FIXED_MAPPING.typeMapping,
        internal = FIXED_MAPPING.internal,
    )

    private fun run(
        form: ResponseForm,
        mapping: ErrorMapping,
        vararg events: QueryEvent,
    ): Pair<SequenceOutcome, List<Token>> {
        val sink = RecordingSink()
        val outcome = runBlocking {
            ResponseSequencer(mapping).run(ScriptedBackend.ofList(*events), request, sink, form, CallLayout.EXECUTE_SQL)
        }
        return outcome to sink.tokens
    }

    private fun encode(tokens: List<Token>): String {
        val encoder = TokenEncoder()
        tokens.forEach { encoder.write(it) }
        return encoder.toByteArray().toHex()
    }

    private fun hexOf(text: String) = hex(text).toHex()

    private val start = QueryEvent.ResultStart(listOf(action), StatementKind.MERGE)

    /**
     * Happy: die Zeile `INSERT` im Ergebnis `$action` (`nvarchar(10)`, Flags 0) schließt mit DONEINPROC `MORE|COUNT`,
     * Kennung `0x0117` und Zähler 1; ab dem Typ byteweise gleich der Antwort des Servers. Wächter: die Länge 4000 der
     * Spalte, die Kennung `0x00c1`, ein Zähler ohne `COUNT`.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Ein MERGE mit einer Zeile schliesst mit 0x0117 und der Spalte nvarchar 10`() {
        val (outcome, tokens) = run(
            ResponseForm.RPC,
            FIXED_MAPPING,
            start,
            QueryEvent.Row(listOf("INSERT")),
            QueryEvent.ResultEnd(1),
        )
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals(
            hexOf("$COLUMN_HEX  $INSERT_ROW  ff 11 00 17 01 01 00 00 00 00 00 00 00  79 00 00 00 00  $DONEPROC_OK"),
            encode(tokens),
        )
    }

    /**
     * Boundary: ohne Aktion (Schlüssel vorhanden, kein Zweig `WHEN MATCHED`) steht das Ergebnis ohne Zeile mit
     * Zähler 0 und Kennung `0x0117` (Messprotokoll M-13). Wächter: ein Abschluss ohne `COUNT`, die Kennung `0x00c1`.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Ein MERGE ohne Aktion schliesst ohne Zeile mit 0x0117 und Zaehler 0`() {
        val (outcome, tokens) = run(
            ResponseForm.RPC,
            FIXED_MAPPING,
            start,
            QueryEvent.ResultEnd(0),
        )
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals(
            hexOf("$COLUMN_HEX  ff 11 00 17 01 00 00 00 00 00 00 00 00  79 00 00 00 00  $DONEPROC_OK"),
            encode(tokens),
        )
    }

    /**
     * Wächter der Gegenrichtung: ein Ergebnis ohne Art (`ResultStart(columns)`) schließt weiter mit `0x00c1`, und die
     * Art `INSERT` im `ResultStart` ändert das Ergebnis nicht (gemessen ist nur `MERGE`). Wächter: eine Kennung der Art
     * für jedes Ergebnis.
     *
     * @spec SPEC-031
     * @spec SPEC-059
     */
    @Test
    fun `Ohne die Art MERGE bleibt der Abschluss eines Ergebnisses 0x00c1`() {
        for (kind in listOf(StatementKind.UNKNOWN, StatementKind.INSERT)) {
            val (_, tokens) = run(
                ResponseForm.RPC,
                FIXED_MAPPING,
                QueryEvent.ResultStart(listOf(action), kind),
                QueryEvent.ResultEnd(0),
            )
            val done = assertIs<DoneToken>(tokens[1])
            assertEquals(DoneCommand.SELECT, done.curCmd, kind.name)
        }
    }

    /**
     * Negative (2628): der Fehler nach dem Beginn des Ergebnisses ist ERROR, INFO 3621, DONEINPROC `MORE|ERROR` mit
     * Kennung `0x0117` und ohne `COUNT`, RETURNSTATUS 2628 und DONEPROC ohne Fehlerbit; die Folge nach dem ERROR
     * ist byteweise die des Servers. Wächter: ein fehlendes INFO, die Kennung `0x00c1`, ein DONEPROC
     * mit `ERROR`, ein fehlender RETURNSTATUS.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Ein Fehler 2628 im Ergebnis hat INFO 3621 und den Abschluss 0x0117`() {
        val (outcome, tokens) = run(
            ResponseForm.RPC,
            mapping(TRUNCATED, CLASS_ERROR),
            start,
            QueryEvent.Failure("TDS_SIM_STRING_TRUNCATED", "Text"),
        )
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertIs<ColMetadataToken>(tokens[0])
        assertEquals(hexOf(COLUMN_HEX), encode(tokens.take(1)))
        assertEquals(TRUNCATED, assertIs<ErrorToken>(tokens[1]).message.number)
        assertEquals(BULK_TERMINATED, tokens[2])
        assertEquals(hexOf("$MERGE_ERROR_DONE  79 44 0a 00 00  $DONEPROC_OK"), encode(tokens.drop(3)))
    }

    /**
     * Negative (2627): die erste Zeile `INSERT` steht vor dem Fehler (so der Server bei zwei gleichen Schlüsseln in der
     * Quelle), der Fehler ist Class 14; Abschluss wie bei 2628, RETURNSTATUS 2627. Wächter: eine Zeile, die der
     * Sequenzierer nach dem Fehler schriebe, ein RETURNSTATUS der falschen Nummer.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Ein Fehler 2627 nach einer Zeile hat die Zeile vor dem Fehler`() {
        val (outcome, tokens) = run(
            ResponseForm.RPC,
            mapping(DUPLICATE_KEY, CLASS_KEY),
            start,
            QueryEvent.Row(listOf("INSERT")),
            QueryEvent.Failure("TDS_SIM_KEY_VIOLATION", "Schlüssel"),
        )
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(hexOf("$COLUMN_HEX  $INSERT_ROW"), encode(tokens.take(2)))
        assertEquals(DUPLICATE_KEY, assertIs<ErrorToken>(tokens[2]).message.number)
        assertEquals(BULK_TERMINATED, tokens[3])
        assertEquals(hexOf("$MERGE_ERROR_DONE  79 43 0a 00 00  $DONEPROC_OK"), encode(tokens.drop(4)))
    }

    /**
     * Negative (208): ein Fehler vor dem Beginn des Ergebnisses ist ERROR, RETURNSTATUS 208 und DONEPROC `ERROR`, ohne
     * INFO und ohne DONEINPROC (Server: `ERROR RETURNSTATUS DONEPROC`). Wächter: ein INFO ohne offenes Ergebnis.
     *
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `Ein Fehler 208 vor dem Ergebnis hat weder INFO noch DONEINPROC`() {
        val (outcome, tokens) = run(
            ResponseForm.RPC,
            mapping(UNKNOWN_OBJECT, CLASS_ERROR),
            QueryEvent.Failure("TDS_SIM_UNKNOWN_OBJECT", "Tabelle"),
        )
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertIs<ErrorToken>(tokens[0])
        assertFalse(tokens.any { it is InfoToken })
        assertEquals(hexOf("79 d0 00 00 00  $DONEPROC_ERROR"), encode(tokens.drop(1)))
    }

    /**
     * Wächter der Gegenrichtung: ein Fehler im Ergebnis ohne die Art `MERGE` hat kein INFO und die Kennung `0x00c1`
     * (Messprotokoll W-1 bis W-3). Wächter: ein INFO nach jedem Fehler im Ergebnis.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein Fehler im Ergebnis ohne die Art MERGE bleibt ohne INFO`() {
        val (_, tokens) = run(
            ResponseForm.RPC,
            mapping(TRUNCATED, CLASS_ERROR),
            QueryEvent.ResultStart(listOf(action)),
            QueryEvent.Failure("TDS_SIM_STRING_TRUNCATED", "Text"),
        )
        assertFalse(tokens.any { it is InfoToken })
        val done = tokens.filterIsInstance<DoneToken>().first { it.kind == DoneKind.DONE_IN_PROC }
        assertEquals(DoneStatus.MORE or DoneStatus.ERROR, done.status)
        assertEquals(DoneCommand.SELECT, done.curCmd)
    }

    /**
     * Setzung (im SQL_BATCH nicht gemessen): derselbe Fehler im Batch hat INFO und DONE `ERROR` mit der Kennung der
     * Art. Wächter: die Kennung `0x00c1` im Batch.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Im Batch traegt der Fehler im Ergebnis eines MERGE die Kennung 0x0117`() {
        val (_, tokens) = run(
            ResponseForm.BATCH,
            mapping(TRUNCATED, CLASS_ERROR),
            start,
            QueryEvent.Failure("TDS_SIM_STRING_TRUNCATED", "Text"),
        )
        assertEquals(BULK_TERMINATED, tokens[2])
        val done = assertIs<DoneToken>(tokens.last())
        assertEquals(DoneStatus.ERROR, done.status)
        assertEquals(0x0117, done.curCmd)
    }
}
