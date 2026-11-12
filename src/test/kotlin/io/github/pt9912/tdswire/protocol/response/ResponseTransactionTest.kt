package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private const val DONE_ZERO = "00 00 00 00 00 00 00 00"
private const val DESCRIPTOR = "01 00 00 00 35 00 00 00"

/** `set implicit_transactions on`: DONE `0x00b9` (Handvektor `katalog-struktur`, Messprotokoll M-20). */
private const val ON = "FD 00 00 B9 00 $DONE_ZERO"

/** `CREATE TABLE` bei `implicit_transactions on`: ENVCHANGE Typ 8 mit dem Deskriptor, dann DONE `0x00c6`. */
private const val CREATE_BEGIN = "E3 0B 00 08 08 $DESCRIPTOR 00  FD 00 00 C6 00 $DONE_ZERO"

/** `CREATE TABLE` ohne Transaktionsbeginn: nur DONE `0x00c6`. */
private const val CREATE_PLAIN = "FD 00 00 C6 00 $DONE_ZERO"

/** `ALTER TABLE … ADD` in der offenen Transaktion: DONE `0x00d8`, kein ENVCHANGE. */
private const val ALTER = "FD 00 00 D8 00 $DONE_ZERO"

/** `CREATE INDEX`: DONE `0x00c8` ohne ENVCHANGE. */
private const val INDEX_CREATE = "FD 00 00 C8 00 $DONE_ZERO"

/** `DROP INDEX`: DONE `0x00c9` ohne ENVCHANGE. */
private const val INDEX_DROP = "FD 00 00 C9 00 $DONE_ZERO"

/** `IF @@TRANCOUNT > 0 COMMIT TRAN` bei offener Transaktion: DONE `MORE` `0x00c0`, ENVCHANGE Typ 9, DONE `0x00d5`. */
private const val COMMIT_OPEN = "FD 01 00 C0 00 $DONE_ZERO  E3 0B 00 09 00 08 $DESCRIPTOR  FD 00 00 D5 00 $DONE_ZERO"

/** `IF @@TRANCOUNT > 0 COMMIT TRAN` ohne offene Transaktion: DONE `0x00c0`. */
private const val COMMIT_CLOSED = "FD 00 00 C0 00 $DONE_ZERO"

/**
 * `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN` ohne offene Transaktion: `0x00ba` `MORE`, `0x00c0`.
 */
private const val OFF_COMMIT_CLOSED = "FD 01 00 BA 00 $DONE_ZERO  FD 00 00 C0 00 $DONE_ZERO"

/**
 * ENVCHANGE Typ 8 und 9 und der Zustand der Transaktion einer Verbindung (`SPEC-031`, ADR-0042 Entscheidung 5): die
 * Folge der Antworten ist die Folge, die der echte SQL Server dem Referenzclient sendet (Szenario `katalog-struktur`,
 * Messprotokoll M-20, Dateien `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/2022/katalog-struktur.messung`
 * und `.../2025/...`, `Microsoft.Data.SqlClient` 7.1.1). Der Deskriptor `01 00 00 00 35 00 00 00` ist der Wert der
 * Instanz 2022; ein anderer Wert (2025: `... 33 ...`) ändert nur diese vier Bytes. Der Zustand überdauert die Anfragen
 * einer Verbindung: ein `SessionTransaction` gilt für alle Läufe eines Tests.
 */
class ResponseTransactionTest {
    private val request = QueryRequest("s", "r", "x", emptyList(), null)
    private val transaction = SessionTransaction()

    private fun answer(
        form: ResponseForm,
        layout: CallLayout,
        state: SessionTransaction,
        vararg kinds: StatementKind,
    ): String {
        val sink = RecordingSink()
        val events = kinds.map { QueryEvent.UpdateCount(0, it) }.toTypedArray()
        val outcome = runBlocking {
            ResponseSequencer(FIXED_MAPPING, state).run(ScriptedBackend.ofList(*events), request, sink, form, layout)
        }
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        val encoder = TokenEncoder()
        sink.tokens.forEach { encoder.write(it) }
        return encoder.toByteArray().toHex()
    }

    private fun batch(vararg kinds: StatementKind) =
        answer(ResponseForm.BATCH, CallLayout.PROCEDURE, transaction, *kinds)

    private fun hexOf(text: String) = hex(text).toHex()

    /**
     * Die gemessene Folge von `schema migrate`: `set implicit_transactions on`; `CREATE TABLE` (ENVCHANGE Typ 8);
     * `ALTER TABLE … ADD` in der Transaktion (kein ENVCHANGE); der bedingte `COMMIT` (DONE `MORE`, ENVCHANGE Typ 9,
     * DONE `0x00d5`); `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN` ohne offene Transaktion; ein
     * `CREATE TABLE` bei `off` ohne ENVCHANGE. Wächter: ein fehlender oder ein zweiter Typ 8, ein Typ 9 ohne die
     * Bedingung vorher, vertauschte Kennungen, der Deskriptor an der falschen Stelle des Tokens.
     *
     * @spec SPEC-031
     * @spec SPEC-035
     * @spec TDS-QA-01
     * @spec TDS-QA-07
     */
    @Test
    fun `Die gemessene Folge von schema migrate ergibt die gemessenen Bytes`() {
        assertEquals(hexOf(ON), batch(StatementKind.IMPLICIT_TRANSACTIONS_ON))
        assertEquals(hexOf(CREATE_BEGIN), batch(StatementKind.CREATE_TABLE))
        assertEquals(hexOf(ALTER), batch(StatementKind.ALTER_TABLE))
        assertEquals(hexOf(COMMIT_OPEN), batch(StatementKind.COMMIT_IF_OPEN))
        assertEquals(
            hexOf(OFF_COMMIT_CLOSED),
            batch(StatementKind.IMPLICIT_TRANSACTIONS_OFF, StatementKind.COMMIT_IF_OPEN),
        )
        assertEquals(hexOf(CREATE_PLAIN), batch(StatementKind.CREATE_TABLE))
    }

    /**
     * Ohne `set implicit_transactions on` beginnt `CREATE TABLE` keine Transaktion (M-20: die Fälle bei `off` zeigen
     * kein ENVCHANGE), der bedingte `COMMIT` schließt dann nur mit `0x00c0`. Eine zweite Strukturänderung in der
     * offenen Transaktion schreibt kein zweites ENVCHANGE. Wächter: ein Typ 8 ohne die Option, ein Typ 8 je
     * `CREATE TABLE`, ein Typ 9 ohne offene Transaktion.
     *
     * @spec SPEC-031
     * @spec TDS-QA-07
     */
    @Test
    fun `Ohne die Option und in der offenen Transaktion steht kein ENVCHANGE Typ 8`() {
        assertEquals(hexOf(CREATE_PLAIN), batch(StatementKind.CREATE_TABLE))
        assertEquals(hexOf(COMMIT_CLOSED), batch(StatementKind.COMMIT_IF_OPEN))
        batch(StatementKind.IMPLICIT_TRANSACTIONS_ON)
        assertEquals(hexOf(CREATE_BEGIN), batch(StatementKind.CREATE_TABLE))
        assertEquals(hexOf(CREATE_PLAIN), batch(StatementKind.CREATE_TABLE))
        assertEquals(hexOf(COMMIT_OPEN), batch(StatementKind.COMMIT_IF_OPEN))
        assertEquals(hexOf(COMMIT_CLOSED), batch(StatementKind.COMMIT_IF_OPEN))
        assertEquals(hexOf(CREATE_BEGIN), batch(StatementKind.CREATE_TABLE))
    }

    /**
     * `CREATE INDEX` und `DROP INDEX` beginnen bei `set implicit_transactions on` keine Transaktion und schreiben kein
     * ENVCHANGE (Setzung, nicht gemessen); sie lassen eine offene Transaktion offen. Wächter: ein Typ 8 vor dem DONE
     * einer der Arten, ein Zustand, der durch sie zurückgesetzt wird (der folgende `COMMIT` ohne Typ 9).
     *
     * @spec SPEC-031
     * @spec TDS-QA-07
     */
    @Test
    fun `CREATE INDEX und DROP INDEX beginnen keine Transaktion`() {
        batch(StatementKind.IMPLICIT_TRANSACTIONS_ON)
        assertEquals(hexOf(INDEX_CREATE), batch(StatementKind.CREATE_INDEX))
        assertEquals(hexOf(INDEX_DROP), batch(StatementKind.DROP_INDEX))
        assertEquals(hexOf(COMMIT_CLOSED), batch(StatementKind.COMMIT_IF_OPEN))
        assertEquals(hexOf(CREATE_BEGIN), batch(StatementKind.CREATE_TABLE))
        assertEquals(hexOf(INDEX_CREATE), batch(StatementKind.CREATE_INDEX))
        assertEquals(hexOf(COMMIT_OPEN), batch(StatementKind.COMMIT_IF_OPEN))
    }

    /**
     * Die gemeldete Art `IMPLICIT_TRANSACTIONS_OFF` schaltet die Option aus: das folgende `CREATE TABLE` beginnt keine
     * Transaktion. Wächter: eine Option, die nur ein- und nie ausgeschaltet wird.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Nach set implicit_transactions off beginnt CREATE TABLE keine Transaktion`() {
        batch(StatementKind.IMPLICIT_TRANSACTIONS_ON)
        batch(StatementKind.IMPLICIT_TRANSACTIONS_OFF)
        assertEquals(hexOf(CREATE_PLAIN), batch(StatementKind.CREATE_TABLE))
    }

    /**
     * Das Zurücksetzen (RESETCONNECTION, Setzung) leert den Zustand: nach `reset` beginnt `CREATE TABLE` keine
     * Transaktion, und der bedingte `COMMIT` schließt ohne Typ 9, auch wenn vorher eine Transaktion offen war.
     * Wächter: ein `reset`, das den Zustand lässt.
     *
     * @spec SPEC-031
     */
    @Test
    fun `reset leert den Zustand`() {
        batch(StatementKind.IMPLICIT_TRANSACTIONS_ON)
        batch(StatementKind.CREATE_TABLE)
        transaction.reset()
        assertEquals(hexOf(COMMIT_CLOSED), batch(StatementKind.COMMIT_IF_OPEN))
        assertEquals(hexOf(CREATE_PLAIN), batch(StatementKind.CREATE_TABLE))
    }

    /**
     * Die Transaktionsarten sind nur im SQL_BATCH gemessen. Im RPC (`sp_executesql`) schreibt die Bibliothek weder ein
     * ENVCHANGE noch führt sie den Zustand nach: ein `CREATE TABLE` im RPC beginnt keine Transaktion, und ein
     * `set implicit_transactions on` im RPC schaltet die Option nicht ein. Wächter: ein Zustand, den ein RPC verändert.
     *
     * @spec SPEC-030
     * @spec SPEC-031
     */
    @Test
    fun `Im RPC gibt es weder ENVCHANGE noch Zustand`() {
        fun rpc(vararg kinds: StatementKind) = answer(ResponseForm.RPC, CallLayout.EXECUTE_SQL, transaction, *kinds)
        val doneProc = "79 00 00 00 00 FE 00 00 E0 00 00 00 00 00 00 00 00 00"
        val plain = hexOf("FF 11 00 00 00 $DONE_ZERO  $doneProc")
        assertEquals(plain, rpc(StatementKind.IMPLICIT_TRANSACTIONS_ON))
        assertEquals(plain, rpc(StatementKind.CREATE_TABLE))
        assertEquals(hexOf(CREATE_PLAIN), batch(StatementKind.CREATE_TABLE))
        batch(StatementKind.IMPLICIT_TRANSACTIONS_ON)
        rpc(StatementKind.CREATE_TABLE)
        assertEquals(hexOf(CREATE_BEGIN), batch(StatementKind.CREATE_TABLE))
    }
}
