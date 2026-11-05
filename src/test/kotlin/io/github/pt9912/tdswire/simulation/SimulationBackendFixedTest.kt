package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Die Antworten der Form C und der Konstantenterm in S1 (`SPEC-036`) am Backend. Die Antworten sind Setzungen nach
 * der Aufzeichnung mit d-migrate 1.12.0 (Treiber und Client nehmen sie an, `SPEC-036`); die Erwartungen sind von
 * Hand geschrieben, nichts ist an einem SQL Server gemessen.
 */
class SimulationBackendFixedTest {
    private val everyColumn = personColumns.map { ResultColumn(it.name, it.type, it.nullable) }
    private val rows = listOf(personRow(1, "Ada", 36), personRow(2, "Bob", null))

    private fun run(sql: String, probe: Probe = Probe(rows)): List<QueryEvent> = collectEvents(probe.backend, sql)

    /**
     * Happy: der Verbindungstest liefert eine Spalte ohne Namen vom Typ `Int32`, nicht NULL-fähig, die Zeile `1`
     * und das Ende mit der Zahl 1; die Zeilenquelle der Tabellen wird nicht berührt.
     * Wächter: eine Antwort ohne `ResultStart` (die Sitzung schließt) oder mit `ResultEnd(0)`.
     * @spec SPEC-036
     */
    @Test
    fun `SELECT 1 liefert eine Zeile und beruehrt keine Zeilenquelle`() {
        val probe = Probe(rows)
        val events = run("SELECT 1", probe)

        val column = ResultColumn("", SqlType.Int32, false)
        assertEquals(
            listOf(QueryEvent.ResultStart(listOf(column)), QueryEvent.Row(listOf(1)), QueryEvent.ResultEnd(1)),
            events,
        )
        assertEquals(0, probe.opens)
    }

    /**
     * Happy: jede `SET`-Anweisung, auch der Text mit sieben, endet als Änderungszähler 0 und nicht als Ergebnis
     * ohne Anfang; die Zeilenquelle bleibt ungeöffnet.
     * Wächter: ein `ResultEnd(0)` statt `UpdateCount(0)`, oder ein Zähler ungleich 0.
     * @spec SPEC-036
     */
    @Test
    fun `SET endet als Aenderungszaehler 0`() {
        val probe = Probe(rows)
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), run("SET LOCK_TIMEOUT 30000", probe))
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), run("set implicit_transactions off", probe))
        val seven = "SET ANSI_NULLS ON;\nSET ANSI_PADDING ON;\nSET ANSI_WARNINGS ON;\nSET ARITHABORT ON;\n" +
            "SET CONCAT_NULL_YIELDS_NULL ON;\nSET NUMERIC_ROUNDABORT OFF;\nSET QUOTED_IDENTIFIER ON"
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), run(seven, probe))
        assertEquals(0, probe.opens)
    }

    /**
     * Negativ: Text außerhalb der Form C endet als einziges `Failure` `TDS_UNSUPPORTED_SQL`, auch ein `SET` mit
     * unbekannter Option und `SELECT 2`.
     * @spec SPEC-037
     */
    @Test
    fun `Text ausserhalb der Form C ist TDS_UNSUPPORTED_SQL`() {
        listOf("SET FOO ON", "SET LOCK_TIMEOUT ON", "SELECT 2", "SET ANSI_NULLS ON; SELECT 1").forEach { sql ->
            val failure = assertIs<QueryEvent.Failure>(run(sql).single(), sql)
            assertEquals(SimCodes.UNSUPPORTED_SQL, failure.code, sql)
        }
    }

    /**
     * Happy: `WHERE 1 = 0` liefert die Metadaten der Auswahl (`*` oder Liste) und `ResultEnd(0)`, keine Zeile, und
     * öffnet die Zeilenquelle nicht — auch neben einem Spaltenterm, der Zeilen träfe.
     * Wächter: eine Bedingung, die den Konstantenterm überspringt (dann kämen beide Zeilen), oder eine Quelle, die
     * trotzdem geöffnet wird.
     * @spec SPEC-036
     */
    @Test
    fun `WHERE 1 = 0 liefert Metadaten ohne Zeile und ohne Quelle`() {
        val probe = Probe(rows)
        assertEquals(
            listOf(QueryEvent.ResultStart(everyColumn), QueryEvent.ResultEnd(0)),
            run("SELECT * FROM person WHERE 1 = 0", probe),
        )
        val listed = listOf(ResultColumn("Name", SqlType.UnicodeText, true), ResultColumn("Id", SqlType.Int32, false))
        assertEquals(
            listOf(QueryEvent.ResultStart(listed), QueryEvent.ResultEnd(0)),
            run("SELECT name, id FROM person WHERE id = 1 AND 1 = 0", probe),
        )
        assertEquals(0, probe.opens)
    }

    /**
     * Happy: `WHERE 1 = 1` ist wahr für jede Zeile, allein oder neben einem Spaltenterm; die Quelle wird gelesen
     * und der Vorfilter nennt den Konstantenterm nicht (er hat keinen Spaltennamen).
     * Wächter: ein Konstantenterm, der nie trifft (dann kämen keine Zeilen).
     * @spec SPEC-036
     */
    @Test
    fun `WHERE 1 = 1 trifft jede Zeile`() {
        val probe = Probe(rows)
        val all = run("SELECT * FROM person WHERE 1 = 1", probe)
        assertEquals(rows.map { QueryEvent.Row(it) }, all.filterIsInstance<QueryEvent.Row>())
        assertEquals(QueryEvent.ResultEnd(2), all.last())
        assertEquals(1, probe.opens)
        assertEquals(null, probe.lastQuery?.filter)

        val one = run("SELECT id FROM person WHERE 1 = 1 AND id = 2", probe)
        assertEquals(listOf(QueryEvent.Row(listOf(2))), one.filterIsInstance<QueryEvent.Row>())
        assertEquals(listOf(FilterTerm.In("Id", listOf(2))), probe.lastQuery?.filter?.terms)
    }

    /**
     * Negativ: auch mit `WHERE 1 = 0` werden Tabelle und Spalten der Auswahl gegen die Beschreibung geprüft; eine
     * unbekannte Tabelle oder Spalte ist `TDS_SIM_UNKNOWN_OBJECT`, kein leeres Ergebnis.
     * Wächter: ein Konstantenterm, der die Objektprüfung umgeht.
     * @spec SPEC-038
     */
    @Test
    fun `WHERE 1 = 0 prueft Tabelle und Spalten`() {
        listOf("SELECT * FROM other WHERE 1 = 0", "SELECT nope FROM person WHERE 1 = 0").forEach { sql ->
            val failure = assertIs<QueryEvent.Failure>(run(sql).single(), sql)
            assertEquals(SimCodes.UNKNOWN_OBJECT, failure.code, sql)
        }
    }

    /**
     * Negativ: `UPDATE` und `DELETE` mit Konstantenterm sind `TDS_UNSUPPORTED_SQL`, und kein Callback läuft.
     * Wächter: eine Zulassung des Terms für Änderungen (ein Filter ohne Spaltenterm löschte jede Zeile).
     * @spec SPEC-036
     */
    @Test
    fun `Aenderungen mit Konstantenterm sind abgelehnt`() {
        var calls = 0
        val table = Probe().table.copy(
            onUpdate = { _, _ -> calls++.toLong() },
            onDelete = { calls++.toLong() },
        )
        val backend = SimulationBackend(SimCatalog(listOf(table)))
        listOf("DELETE FROM person WHERE 1 = 0", "UPDATE person SET age = 1 WHERE 1 = 0").forEach { sql ->
            val failure = assertIs<QueryEvent.Failure>(collectEvents(backend, sql).single(), sql)
            assertEquals(SimCodes.UNSUPPORTED_SQL, failure.code, sql)
        }
        assertEquals(0, calls)
    }
}
