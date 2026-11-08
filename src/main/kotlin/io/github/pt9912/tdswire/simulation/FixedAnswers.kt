package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind

/**
 * Die festen Antworten der Form C (`SPEC-036`, Setzung nach der Aufzeichnung mit d-migrate 1.12.0): `SELECT 1` ist
 * eine Spalte ohne Namen vom Typ `Int32`, nicht NULL-fähig, mit der Zeile `1`; eine `SET`-Anweisung ist der
 * Änderungszähler 0, mit zwei Ausnahmen: der Text `set implicit_transactions on|off` allein meldet die Art
 * `IMPLICIT_TRANSACTIONS_ON` bzw. `IMPLICIT_TRANSACTIONS_OFF` (M-20; `off` allein ist Setzung nach dem Teil der
 * Kombination), `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN` meldet diese Art und dann
 * `COMMIT_IF_OPEN`, und `IF @@TRANCOUNT > 0 COMMIT TRAN` allein meldet `COMMIT_IF_OPEN`. Jede andere
 * Transaktionssteuerung (`ROLLBACK`, `set implicit_transactions on` mit der Bedingung) ist der Änderungszähler 0 ohne
 * Art. Ein Ergebnisende ohne Anfang ist kein Zähler: der Backend-Vertrag lässt eine Anweisung ohne
 * Ergebnis nur als Änderungszähler zu, `ResultEnd` ohne `ResultStart` beantwortet die Sitzung mit Fehler 50007 und
 * schließt.
 */
internal object FixedAnswers {
    fun of(form: FixedForm): List<QueryEvent> = when (form) {
        FixedForm.Probe -> listOf(
            QueryEvent.ResultStart(listOf(ResultColumn("", SqlType.Int32, false))),
            QueryEvent.Row(listOf(1)),
            QueryEvent.ResultEnd(1),
        )

        is FixedForm.Settings -> listOf(QueryEvent.UpdateCount(0, implicitTransactions(form)))

        is FixedForm.Transaction -> transaction(form)
    }

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
            else -> StatementKind.IMPLICIT_TRANSACTIONS_OFF
        }
    }
}
