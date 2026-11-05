package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType

/**
 * Die festen Antworten der Form C (`SPEC-036`, Setzung nach der Aufzeichnung mit d-migrate 1.12.0): `SELECT 1` ist
 * eine Spalte ohne Namen vom Typ `Int32`, nicht NULL-fähig, mit der Zeile `1`; eine `SET`-Anweisung ist der
 * Änderungszähler 0. Ein Ergebnisende ohne Anfang ist kein Zähler: der Backend-Vertrag lässt eine Anweisung ohne
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

        is FixedForm.Settings -> listOf(QueryEvent.UpdateCount(0))
    }
}
