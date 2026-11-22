package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.BulkTargetResult

/**
 * Die festen Antworten der Form C (`SPEC-036`, Setzung nach der Aufzeichnung mit d-migrate 1.12.0): `SELECT 1` ist
 * eine Spalte ohne Namen vom Typ `Int32`, nicht NULL-fähig, mit der Zeile `1`, `SELECT <Ganzzahl>` dieselbe Antwort mit
 * der Zahl als Zeile (gemessen ist der Wert 2, `ADR-0058` Entscheidung 4); eine `SET`-Anweisung ist der
 * Änderungszähler 0, mit Ausnahmen für gemessene Texte (M-20): `set implicit_transactions on` allein meldet die Art
 * `IMPLICIT_TRANSACTIONS_ON`, `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN` meldet
 * `IMPLICIT_TRANSACTIONS_OFF` und dann `COMMIT_IF_OPEN`, und `IF @@TRANCOUNT > 0 COMMIT TRAN` allein meldet
 * `COMMIT_IF_OPEN`. Jeder andere Text ist der Änderungszähler 0 ohne Art (`UNKNOWN`), darunter `set
 * implicit_transactions off` allein (nicht gemessen), `ROLLBACK` und `set implicit_transactions on` mit der
 * Bedingung. Ein Ergebnisende ohne Anfang ist kein Zähler: der Backend-Vertrag lässt eine Anweisung ohne
 * Ergebnis nur als Änderungszähler zu, `ResultEnd` ohne `ResultStart` beantwortet die Sitzung mit Fehler 50007 und
 * schließt.
 */
internal object FixedAnswers {
    fun of(form: FixedForm): List<QueryEvent> = when (form) {
        FixedForm.Probe -> constant(1)
        is FixedForm.IntegerConstant -> constant(form.value)
        is FixedForm.Settings -> listOf(QueryEvent.UpdateCount(0, implicitTransactions(form)))
        is FixedForm.Transaction -> transaction(form)
    }

    /** `INSERT BULK` als Text: die Anweisung selbst ruft keinen Callback und zählt 0 (Setzung, nicht gemessen). */
    fun bulk(result: BulkTargetResult): List<QueryEvent> = when (result) {
        is BulkTargetResult.Ready -> listOf(QueryEvent.UpdateCount(0))
        is BulkTargetResult.Rejected -> listOf(QueryEvent.Failure(result.code, result.message))
    }

    /** Ein Ergebnis mit einer Spalte ohne Namen (`Int32`, nicht NULL) und der einen Zeile [value]. */
    private fun constant(value: Int): List<QueryEvent> = listOf(
        QueryEvent.ResultStart(listOf(ResultColumn("", SqlType.Int32, false))),
        QueryEvent.Row(listOf(value)),
        QueryEvent.ResultEnd(1),
    )

    private fun transaction(form: FixedForm.Transaction): List<QueryEvent> = when {
        form.commit && form.implicit == null -> listOf(QueryEvent.UpdateCount(0, StatementKind.COMMIT_IF_OPEN))

        form.commit && form.implicit == false -> listOf(
            QueryEvent.UpdateCount(0, StatementKind.IMPLICIT_TRANSACTIONS_OFF),
            QueryEvent.UpdateCount(0, StatementKind.COMMIT_IF_OPEN),
        )

        else -> listOf(QueryEvent.UpdateCount(0))
    }

    private fun implicitTransactions(form: FixedForm.Settings): StatementKind {
        val only = form.options.singleOrNull()
        return when {
            only == null || only.option != "IMPLICIT_TRANSACTIONS" -> StatementKind.UNKNOWN
            only.argument == "ON" -> StatementKind.IMPLICIT_TRANSACTIONS_ON
            else -> StatementKind.UNKNOWN
        }
    }
}
