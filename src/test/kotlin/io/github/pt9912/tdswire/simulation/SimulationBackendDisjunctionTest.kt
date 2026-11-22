package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * `OR` in der Auswahl (`SPEC-036`, `ADR-0058` Entscheidung 3) am Backend: eine Zeile trifft, wenn mindestens ein Term
 * gilt; das Backend wendet die Bedingung selbst an, der Vorfilter ist `null`. Die Zeilenquelle ignoriert den Vorfilter
 * und liefert jede Zeile, das Ergebnis hängt also allein an der Bedingung des Backends. Die Erwartungen sind von Hand
 * geschrieben; gemessen am Server ist nur der Erfolg von `Id = 1 OR Id = 2` (Live-Schritt `bedingung-mit-or`), alles
 * andere ist Setzung.
 */
class SimulationBackendDisjunctionTest {
    private val ada = personRow(1, "Ada", 36, "10.50", true).bornOn("1990-01-02")
    private val bob = personRow(2, "bob", null, "10.5", false)
    private val nameless = personRow(3, null, 36, "7.25", null).bornOn("2000-12-31")
    private val shout = personRow(4, "ADA", 20, null, true).bornOn("1990-01-02")
    private val all = listOf(ada, bob, nameless, shout)

    /** Die Ids der gelieferten Zeilen von `SELECT id FROM person WHERE …`; die Zahl in `ResultEnd` stimmt dazu. */
    private fun ids(where: String, parameters: List<QueryParameter> = emptyList()): List<Any?> {
        val events = collectEvents(Probe(all).backend, "SELECT id FROM person WHERE $where", parameters)
        assertIs<QueryEvent.ResultStart>(events.first(), where)
        val rows = events.filterIsInstance<QueryEvent.Row>().map { it.values.single() }
        assertEquals(QueryEvent.ResultEnd(rows.size.toLong()), events.last(), where)
        return rows
    }

    /**
     * Happy: zwei `=` auf derselben Spalte (die gemessene Form), `=` und `IN`, `IS NULL` und `IS NOT NULL`, drei Terme,
     * in der Reihenfolge der Quelle und ohne doppelte Zeile, wenn zwei Terme dieselbe Zeile treffen.
     * Wächter: eine Bedingung, die `OR` wie `AND` auswertet (dann bliebe nur eine Zeile oder keine), oder eine, die nur
     * den ersten Term anwendet.
     * @spec SPEC-036
     */
    @Test
    fun `OR waehlt die Zeilen, die einen Term erfuellen`() {
        assertEquals(listOf<Any?>(1, 2), ids("id = 1 OR id = 2"))
        assertEquals(listOf<Any?>(2, 4), ids("name = 'bob' OR age IN (20, 99)"))
        assertEquals(listOf<Any?>(2, 3), ids("age IS NULL OR name IS NULL"))
        assertEquals(listOf<Any?>(1, 2, 4), ids("id = 1 OR id = 2 OR id = 4"))
        assertEquals(listOf<Any?>(1, 3, 4), ids("age IS NOT NULL OR name = 'nobody'"))
        assertEquals(listOf<Any?>(1, 3), ids("age = 36 OR id = 1"))
        assertEquals(listOf<Any?>(1, 3, 4), ids("id = 4 OR age = 36"))
        assertEquals(listOf<Any?>(1, 2, 3, 4), ids("id = 1 OR id IS NOT NULL"))
        assertEquals(listOf<Any?>(2), ids("id = 2 OR id = 2"))
    }

    /**
     * Boundary: ein Term ohne Treffer, ein leeres Ergebnis und die Schreibweise der Schlüsselwörter; `NULL` in `=` und
     * `IN` trifft nie (wie bei `AND`), ein Term mit `NULL` verdirbt die anderen Terme nicht (abgeleitet, nicht
     * gemessen).
     * @spec SPEC-036
     */
    @Test
    fun `OR mit leerem Treffer und mit NULL`() {
        assertEquals(emptyList<Any?>(), ids("id = 98 OR id = 99"))
        assertEquals(listOf<Any?>(4), ids("age = NULL OR age = 20"))
        assertEquals(listOf<Any?>(4), ids("age IN (NULL) OR age = 20"))
        assertEquals(emptyList<Any?>(), ids("age = NULL OR name = NULL"))
        assertEquals(listOf<Any?>(1, 3, 4), ids("age IN (36, NULL) OR id = 4"))
        assertEquals(listOf<Any?>(1, 2), ids("id = 1 or id = 2"))
        assertEquals(listOf<Any?>(1, 2), ids("id = 1 Or id = 2"))
    }

    /**
     * Der Vorfilter ist bei einer `OR`-Bedingung `null`, die Zeilenquelle liefert alle Zeilen (`ADR-0058`
     * Entscheidung 3); die Quelle wird genau einmal geöffnet und bis zum Ende gelesen. Gegenprobe: dieselbe Auswahl mit
     * `AND` trägt den Vorfilter.
     * Wächter: ein Vorfilter, der die Terme als Konjunktion der Quelle mitgibt (eine Quelle, die ihn anwendet, ließe
     * Zeilen weg).
     * @spec SPEC-036
     * @spec TDS-FA-11
     */
    @Test
    fun `OR gibt der Zeilenquelle keinen Vorfilter`() {
        val probe = Probe(all)
        collectEvents(probe.backend, "SELECT id FROM person WHERE id = 1 OR id = 2")

        assertNull(probe.lastQuery?.filter)
        assertEquals(1, probe.opens)
        assertEquals(1, probe.closes)
        assertEquals(all.size + 1, probe.nexts)

        val conjunction = Probe(all)
        collectEvents(conjunction.backend, "SELECT id FROM person WHERE id = 1 AND age = 36")
        assertEquals(FilterTerm.In("Id", listOf(1)), conjunction.lastQuery?.filter?.terms?.first())
    }

    /**
     * Parameter in einer `OR`-Bedingung werden wie in jeder Auswahl in den Spaltentyp überführt; ein nicht
     * überführbarer Wert endet vor dem Lesen mit `TDS_SIM_INVALID_VALUE`, eine unbekannte Spalte mit
     * `TDS_SIM_UNKNOWN_OBJECT`.
     * @spec SPEC-036
     * @spec SPEC-038
     * @spec SPEC-039
     */
    @Test
    fun `OR prueft Spalten und Werte vor dem Lesen`() {
        val parameters = listOf(QueryParameter("@a", SqlType.Int32, 1), QueryParameter("@b", SqlType.Int32, 20))
        assertEquals(listOf<Any?>(1, 4), ids("id = @a OR age = @b", parameters))
        val invalid = Probe(all)
        val failure = collectEvents(invalid.backend, "SELECT id FROM person WHERE id = 1 OR id = 'abc'").single()
        assertEquals(SimCodes.INVALID_VALUE, assertIs<QueryEvent.Failure>(failure).code)
        val unknown = Probe(all)
        val missing = collectEvents(unknown.backend, "SELECT id FROM person WHERE id = 1 OR nothing = 2").single()
        assertEquals(SimCodes.UNKNOWN_OBJECT, assertIs<QueryEvent.Failure>(missing).code)
        assertEquals(0, invalid.opens + unknown.opens)
    }

    /**
     * Form S2: jede der zwei Auswahlen trägt ihre eigene Bedingung (Setzung), die erste mit `OR`, die zweite mit `AND`;
     * zwei Ergebnisse in der Reihenfolge des Textes.
     * @spec SPEC-036
     */
    @Test
    fun `zwei Auswahlen haben je ihre OR-Folge`() {
        val events = collectEvents(
            Probe(all).backend,
            "SELECT id FROM person WHERE id = 1 OR id = 3; SELECT id FROM person WHERE age = 36 AND name IS NULL",
        )

        val rows = events.filterIsInstance<QueryEvent.Row>().map { it.values.single() }
        assertEquals(listOf<Any?>(1, 3, 3), rows)
        val ends = events.filterIsInstance<QueryEvent.ResultEnd>()
        assertEquals(listOf(QueryEvent.ResultEnd(2), QueryEvent.ResultEnd(1)), ends)
    }

    /**
     * Hinter `SET FMTONLY ON` liefert die Auswahl mit `OR` Spalten und keine Zeile und öffnet die Quelle nicht (die
     * Probe der Massenlade-Anbindung); eine unbekannte Spalte der Bedingung bleibt geprüft.
     * @spec SPEC-036
     */
    @Test
    fun `OR hinter FMTONLY liefert Metadaten ohne Zeile`() {
        val probe = Probe(all)
        val events = collectEvents(probe.backend, "SET FMTONLY ON SELECT id FROM person WHERE id = 1 OR id = 2")

        assertEquals(2, events.size)
        assertIs<QueryEvent.ResultStart>(events.first())
        assertEquals(QueryEvent.ResultEnd(0), events.last())
        assertEquals(0, probe.opens)
        val unknown = collectEvents(probe.backend, "SET FMTONLY ON SELECT id FROM person WHERE id = 1 OR nothing = 2")
        assertEquals(SimCodes.UNKNOWN_OBJECT, assertIs<QueryEvent.Failure>(unknown.single()).code)
    }

    /**
     * Negativ (`ADR-0058` Entscheidung 3 und 1): jede Form außerhalb der reinen Folge endet als einziges `Failure`
     * `TDS_UNSUPPORTED_SQL`, ohne Zeilenquelle und ohne Callback; `OR` in `UPDATE` und `DELETE` ändert keine Zeile.
     * @spec SPEC-037
     */
    @Test
    fun `Texte ausserhalb der reinen OR-Folge sind TDS_UNSUPPORTED_SQL`() {
        val probe = Probe(all)
        listOf(
            "SELECT id FROM person WHERE id = 1 AND age = 36 OR id = 2",
            "SELECT id FROM person WHERE id = 1 OR age = 36 AND id = 2",
            "SELECT id FROM person WHERE (id = 1 OR id = 2)",
            "SELECT id FROM person WHERE id = 1 OR 1 = 1",
            "SELECT id FROM person WHERE 1 = 1 OR id = 1",
            "SELECT id FROM person WHERE NOT id = 1 OR id = 2",
            "SELECT id FROM person WHERE id = 1 OR age < 30",
            "SELECT id FROM person WHERE id = 1 OR age > 30",
            "SELECT id FROM person WHERE id = 1 OR name LIKE 'A%'",
            "SELECT id FROM person WHERE id = 1 OR age BETWEEN 1 AND 30",
            "SELECT id FROM person WHERE id = 1 OR age <> 36",
            "SELECT id FROM person WHERE id = 1 OR age != 36",
            "SELECT id FROM person WHERE id = 1 OR age NOT IN (36)",
            "UPDATE person SET age = 1 WHERE id = 1 OR id = 2",
            "DELETE FROM person WHERE id = 1 OR id = 2",
        ).forEach { sql ->
            val failure = assertIs<QueryEvent.Failure>(collectEvents(probe.backend, sql).single(), sql)
            assertEquals(SimCodes.UNSUPPORTED_SQL, failure.code, sql)
        }
        assertEquals(0, probe.opens)
    }
}
