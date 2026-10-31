package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests der Auswahl (`TDS-FA-11.a` Schritte 4 und 5) gegen eine Zeilenquelle, die den Vorfilter ignoriert und
 * jede Zeile liefert: das Ergebnis hängt allein an der Bedingung, die das Backend selbst anwendet. Die Regeln der
 * Bedingung (dreiwertige Logik, Textvergleich, Dezimalvergleich) sind Festlegungen aus `SPEC-035` und `ADR-0012`,
 * an einem echten SQL Server nicht gemessen; gemessen wird der Code gegen handgebaute Zeilen.
 */
class SimulationBackendSelectTest {
    private val ada = personRow(1, "Ada", 36, "10.50", true).bornOn("1990-01-02")
    private val bob = personRow(2, "bob", null, "10.5", false)
    private val nameless = personRow(3, null, 36, "7.25", null).bornOn("2000-12-31")
    private val shout = personRow(4, "ADA", 20, null, true).bornOn("1990-01-02")
    private val all = listOf(ada, bob, nameless, shout)

    private val everyColumn = personColumns.map { ResultColumn(it.name, it.type, it.nullable) }

    private fun select(
        sql: String,
        rows: List<List<Any?>> = all,
        parameters: List<QueryParameter> = emptyList(),
    ): List<QueryEvent> = collectEvents(Probe(rows).backend, sql, parameters)

    /** Die Ids der gelieferten Zeilen einer Auswahl `SELECT id FROM person WHERE …`. */
    private fun ids(where: String, parameters: List<QueryParameter> = emptyList()): List<Any?> {
        val events = select("SELECT id FROM person WHERE $where", parameters = parameters)
        assertIs<QueryEvent.ResultStart>(events.first(), where)
        val end = assertIs<QueryEvent.ResultEnd>(events.last(), where)
        val rows = events.filterIsInstance<QueryEvent.Row>().map { it.values.single() }
        assertEquals(rows.size.toLong(), end.affectedRows, where)
        return rows
    }

    /**
     * Happy: `*` liefert `ResultStart` mit allen Spalten in der Reihenfolge der Beschreibung, jede Zeile des
     * Ergebnisses in dieser Reihenfolge und `ResultEnd` mit der Zeilenzahl.
     * @spec SPEC-002
     * @spec SPEC-035
     */
    @Test
    fun `Stern liefert alle Spalten alle Zeilen und die Zahl`() {
        val events = select("SELECT * FROM person")

        val expected = listOf<QueryEvent>(QueryEvent.ResultStart(everyColumn)) +
            all.map { QueryEvent.Row(it) } + QueryEvent.ResultEnd(4)
        assertEquals(expected, events)
    }

    /**
     * Happy: die Auswahlliste bestimmt Spalten und Werte in der Reihenfolge des Textes, mit Wiederholung; die Namen
     * und die Nullbarkeit sind die der Beschreibung, nicht die der Schreibweise im Text.
     * @spec SPEC-035
     */
    @Test
    fun `die Auswahlliste bestimmt Metadaten und Werte`() {
        val events = select("SELECT AGE, id, age FROM PERSON WHERE id = 1")

        val columns = listOf(
            ResultColumn("Age", SqlType.Int32, true),
            ResultColumn("Id", SqlType.Int32, false),
            ResultColumn("Age", SqlType.Int32, true),
        )
        val row = QueryEvent.Row(listOf(36, 1, 36))
        assertEquals(listOf(QueryEvent.ResultStart(columns), row, QueryEvent.ResultEnd(1)), events)
    }

    /**
     * Boundary: ohne Treffer und ohne Zeilen der Quelle kommt `ResultStart` mit den Spalten der Auswahlliste und
     * `ResultEnd` mit der Zahl 0, keine Zeile.
     * @spec SPEC-035
     */
    @Test
    fun `ohne Treffer kommen die Metadaten und die Zahl 0`() {
        val listed = listOf(QueryEvent.ResultStart(listOf(ResultColumn("Name", SqlType.UnicodeText, true))))
        val noMatch = select("SELECT name FROM person WHERE id = 99")
        assertEquals(listed + QueryEvent.ResultEnd(0), noMatch)

        val emptySource = select("SELECT name FROM person", rows = emptyList())
        assertEquals(listed + QueryEvent.ResultEnd(0), emptySource)
    }

    /**
     * Happy: die Zahl in `ResultEnd` ist die Zahl der gelieferten Zeilen, nicht die der gelesenen.
     * @spec SPEC-035
     */
    @Test
    fun `die Zeilenzahl zaehlt die gelieferten Zeilen`() {
        val probe = Probe(all)

        val events = collectEvents(probe.backend, "SELECT id FROM person WHERE age = 36")

        assertEquals(QueryEvent.ResultEnd(2), events.last())
        assertEquals(5, probe.nexts)
    }

    /**
     * Happy und Boundary: Gleichheit, Liste und IS [NOT] NULL, einzeln und mit UND verknüpft.
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `Gleichheit Liste und NULL-Pruefung waehlen die Zeilen`() {
        assertEquals(listOf<Any?>(2), ids("id = 2"))
        assertEquals(listOf<Any?>(1, 3), ids("id IN (1, 3, 99)"))
        assertEquals(listOf<Any?>(2), ids("age IS NULL"))
        assertEquals(listOf<Any?>(1, 3, 4), ids("age IS NOT NULL"))
        assertEquals(listOf<Any?>(3), ids("age = 36 AND name IS NULL"))
        assertEquals(listOf<Any?>(1), ids("age = 36 AND name IS NOT NULL"))
        assertEquals(listOf<Any?>(1, 4), ids("born = '1990-01-02'"))
        assertEquals(listOf<Any?>(1, 4), ids("active IN (1)"))
    }

    /**
     * Boundary: NULL in einem Vergleich oder einer Liste trifft nie, auch keine Zeile mit NULL in der Spalte
     * (dreiwertige Logik, Festlegung); `IS NULL` ist der einzige Weg zu diesen Zeilen. Der Wert 36 in der Liste mit
     * NULL trifft weiter.
     * @spec SPEC-035
     */
    @Test
    fun `NULL im Vergleich trifft nie`() {
        assertEquals(emptyList<Any?>(), ids("age = NULL"))
        assertEquals(emptyList<Any?>(), ids("name = NULL"))
        assertEquals(emptyList<Any?>(), ids("age IN (NULL)"))
        assertEquals(listOf<Any?>(1, 3), ids("age IN (36, NULL)"))
        assertEquals(listOf<Any?>(2), ids("age IS NULL"))
    }

    /**
     * Boundary: dieselbe Spalte in mehreren Termen; ein Widerspruch trifft keine Zeile, zwei sich deckende Terme
     * lassen die Zeile durch.
     * @spec SPEC-035
     */
    @Test
    fun `ein Widerspruch trifft keine Zeile`() {
        assertEquals(emptyList<Any?>(), ids("id = 1 AND id = 2"))
        assertEquals(emptyList<Any?>(), ids("age IS NULL AND age IS NOT NULL"))
        assertEquals(listOf<Any?>(1), ids("id = 1 AND id IN (1, 2)"))
    }

    /**
     * Boundary: Text wird ohne Beachtung der Schreibweise verglichen, nachgestellte Leerzeichen zählen
     * (Setzung, nur im JDK gemessen).
     * @spec SPEC-035
     */
    @Test
    fun `Text wird ohne Beachtung der Schreibweise verglichen`() {
        assertEquals(listOf<Any?>(1, 4), ids("name = 'aDa'"))
        assertEquals(listOf<Any?>(1, 4), ids("name = N'ada'"))
        assertEquals(listOf<Any?>(2), ids("name IN ('BOB', 'zed')"))
        assertEquals(emptyList<Any?>(), ids("name = 'ada '"))
    }

    /**
     * Boundary: eine Dezimalzahl wird über `compareTo` verglichen, 10.5 trifft 10.50 und umgekehrt.
     * @spec SPEC-035
     */
    @Test
    fun `Dezimalzahlen werden ueber compareTo verglichen`() {
        assertEquals(listOf<Any?>(1, 2), ids("score = 10.5"))
        assertEquals(listOf<Any?>(1, 2), ids("score = 10.50"))
        assertEquals(listOf<Any?>(1, 2), ids("score = 10.500"))
        assertEquals(listOf<Any?>(3), ids("score = 7.25"))
    }

    /**
     * Happy: ein Parameter wird als typisierter Wert eingesetzt, nie als Text; der Name gilt ohne Beachtung der
     * Schreibweise.
     * @spec SPEC-035
     */
    @Test
    fun `Parameter werden typisiert eingesetzt`() {
        val age = QueryParameter("@Age", SqlType.Int32, 36)
        val name = QueryParameter("@n", SqlType.UnicodeText, "ADA")

        assertEquals(listOf<Any?>(1, 3), ids("age = @age", listOf(age)))
        assertEquals(listOf<Any?>(1), ids("age = @age AND name = @N", listOf(age, name)))
        assertEquals(listOf<Any?>(1, 4), ids("name = @n", listOf(name)))
        assertEquals(emptyList<Any?>(), ids("age = @x", listOf(QueryParameter("@x", SqlType.Int32, 5))))
        assertEquals(emptyList<Any?>(), ids("age = @a", listOf(QueryParameter("@a", SqlType.Int32, null))))
    }

    /**
     * Negative: ein nicht überführbarer Wert endet mit `TDS_SIM_INVALID_VALUE` als einziges Ereignis, bevor
     * die Zeilenquelle geöffnet oder gelesen wird; der erste ungültige Wert gewinnt, auch hinter gültigen Termen.
     * @spec SPEC-039
     */
    @Test
    fun `ein ungueltiger Wert endet vor dem Lesen`() {
        val cases = mapOf<String, List<QueryParameter>>(
            "SELECT id FROM person WHERE age = 'x'" to emptyList(),
            "SELECT id FROM person WHERE age = 2147483648" to emptyList(),
            "SELECT id FROM person WHERE age = 1.5" to emptyList(),
            "SELECT id FROM person WHERE born = '30.10.2026'" to emptyList(),
            "SELECT id FROM person WHERE score = 123456789.12" to emptyList(),
            "SELECT id FROM person WHERE id = 1 AND name = 7" to emptyList(),
            "SELECT id FROM person WHERE age IN (1, 2, 'x')" to emptyList(),
            "SELECT id FROM person WHERE age = @missing" to emptyList(),
            "SELECT id FROM person WHERE age = @wide" to listOf(QueryParameter("@wide", SqlType.Int64, 1L)),
            "SELECT id FROM person WHERE name = @num" to listOf(QueryParameter("@num", SqlType.Int32, 1)),
        )
        for ((sql, parameters) in cases) {
            val probe = Probe(all)

            val events = collectEvents(probe.backend, sql, parameters)

            val failure = assertIs<QueryEvent.Failure>(events.single(), sql)
            assertEquals("TDS_SIM_INVALID_VALUE", failure.code, sql)
            assertEquals(0, probe.opens, sql)
            assertEquals(0, probe.nexts, sql)
        }
    }

    /**
     * Negative und Boundary: eine nicht erkannte Anweisung, eine unbekannte Tabelle oder Spalte und die
     * Änderungsformen enden als einziges `Failure` vor dem Öffnen der Zeilenquelle; `TDS_UNSUPPORTED_SQL`
     * für Text außerhalb der Formen und für Einfügen, Ändern, Löschen (nicht gebaut), `TDS_SIM_UNKNOWN_OBJECT`
     * für Unbekanntes.
     * @spec SPEC-037
     * @spec SPEC-038
     */
    @Test
    fun `Anweisungen ohne Auswahl enden vor dem Lesen`() {
        val cases = mapOf(
            "SELECT 1" to "TDS_UNSUPPORTED_SQL",
            "SELECT * FROM person ORDER BY id" to "TDS_UNSUPPORTED_SQL",
            "INSERT INTO person (id) VALUES (1)" to "TDS_UNSUPPORTED_SQL",
            "UPDATE person SET age = 1" to "TDS_UNSUPPORTED_SQL",
            "DELETE FROM person" to "TDS_UNSUPPORTED_SQL",
            "SELECT * FROM nothing" to "TDS_SIM_UNKNOWN_OBJECT",
            "SELECT nothing FROM person" to "TDS_SIM_UNKNOWN_OBJECT",
            "SELECT id FROM person WHERE nothing = 1" to "TDS_SIM_UNKNOWN_OBJECT",
        )
        for ((sql, code) in cases) {
            val probe = Probe(all)

            val events = collectEvents(probe.backend, sql)

            assertEquals(code, assertIs<QueryEvent.Failure>(events.single(), sql).code, sql)
            assertEquals(0, probe.opens, sql)
        }
    }

    /**
     * Happy: die Zeilenquelle bekommt alle Spalten der Beschreibung und den Vorfilter als Hinweis (Spaltennamen der
     * Beschreibung, überführte Werte, NULL in der Liste erhalten); ohne `WHERE` ist der Hinweis `null`. Die
     * Quelle ignoriert ihn in diesem Test und das Ergebnis stimmt trotzdem.
     * @spec SPEC-035
     */
    @Test
    fun `die Quelle bekommt den Vorfilter als Hinweis`() {
        val probe = Probe(all)

        val events = collectEvents(probe.backend, "SELECT id FROM person WHERE AGE IN (36, NULL) AND name IS NOT NULL")

        val query = probe.lastQuery
        assertEquals(personColumns, query?.columns)
        val terms = listOf(FilterTerm.In("Age", listOf(36, null)), FilterTerm.IsNull("Name", negated = true))
        assertEquals(RowFilter(terms), query?.filter)
        assertEquals(listOf<Any?>(1), events.filterIsInstance<QueryEvent.Row>().map { it.values.single() })

        val unfiltered = Probe(all)
        collectEvents(unfiltered.backend, "SELECT id FROM person")
        assertNull(unfiltered.lastQuery?.filter)
    }
}
