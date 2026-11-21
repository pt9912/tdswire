package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.ParameterDirection
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
            "SELECT id FROM person WHERE age = @wide" to
                listOf(QueryParameter("@wide", SqlType.Int64, Int.MAX_VALUE + 1L)),
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
     * Änderungsformen einer Tabelle ohne Callback enden als einziges `Failure` vor dem Öffnen der Zeilenquelle;
     * `TDS_UNSUPPORTED_SQL` für Text außerhalb der Formen und für Einfügen, Ändern, Löschen ohne Callback,
     * `TDS_SIM_UNKNOWN_OBJECT` für Unbekanntes.
     * @spec SPEC-037
     * @spec SPEC-038
     */
    @Test
    fun `Anweisungen ohne Auswahl enden vor dem Lesen`() {
        val cases = mapOf(
            "SELECT 2.5" to "TDS_UNSUPPORTED_SQL",
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
     * Das `Failure` einer unbekannten Tabelle trägt ihren Namen, das einer unbekannten Spalte keinen.
     * @spec SPEC-038
     */
    @Test
    fun `die Ablehnung einer unbekannten Tabelle nennt den Namen`() {
        val table = collectEvents(Probe(all).backend, "SELECT * FROM [dbo].[Nothing]").single()
        assertEquals("dbo.Nothing", assertIs<QueryEvent.Failure>(table).objectName)
        val column = collectEvents(Probe(all).backend, "SELECT nothing FROM person").single()
        assertNull(assertIs<QueryEvent.Failure>(column).objectName)
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

    /**
     * Happy (Form S2): zwei Auswahlen in einem Text ergeben zwei Ergebnisse in der Reihenfolge des Textes, je mit
     * `ResultStart`, ihren Zeilen und `ResultEnd`; die Zeilenquelle wird je Auswahl geöffnet und geschlossen. Gemessen
     * ist die Antwort auf `SELECT Id FROM person WHERE Id = 1; SELECT Id FROM person WHERE Id = 2` (Mitschnitt
     * `mehrteilig`); die Zeilen dieses Tests sind handgebaut.
     * @spec SPEC-036
     */
    @Test
    fun `zwei Auswahlen ergeben zwei Ergebnisse in der Reihenfolge des Textes`() {
        val probe = Probe(all)

        val sql = "SELECT id FROM person WHERE id = 1; SELECT name FROM person WHERE id = 2;"
        val events = collectEvents(probe.backend, sql)

        val id = ResultColumn("Id", SqlType.Int32, false)
        val name = ResultColumn("Name", SqlType.UnicodeText, true)
        val expected = listOf(
            QueryEvent.ResultStart(listOf(id)),
            QueryEvent.Row(listOf(1)),
            QueryEvent.ResultEnd(1),
            QueryEvent.ResultStart(listOf(name)),
            QueryEvent.Row(listOf("bob")),
            QueryEvent.ResultEnd(1),
        )
        assertEquals(expected, events)
        assertEquals(2, probe.opens)
        assertEquals(2, probe.closes)
    }

    /**
     * Boundary: eine Auswahl mit `WHERE 1 = 0` als erste öffnet keine Quelle und liefert ihre Spalten; die zweite
     * folgt. Eine dritte Auswahl ist nicht erkannt, kein Ergebnis kommt.
     * @spec SPEC-036
     */
    @Test
    fun `die Grenze der Anweisungszahl ist zwei`() {
        val probe = Probe(all)

        val sql = "SELECT id FROM person WHERE 1 = 0; SELECT id FROM person WHERE id = 3"
        val events = collectEvents(probe.backend, sql)

        assertEquals(2, events.filterIsInstance<QueryEvent.ResultStart>().size)
        assertEquals(1, probe.opens)

        val sql3 = "SELECT id FROM person; SELECT name FROM person; SELECT age FROM person"
        val three = collectEvents(Probe(all).backend, sql3)
        assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(three.single()).code)
    }

    /**
     * Fehler: eine unbekannte Tabelle in der zweiten Auswahl weist den ganzen Text ab, ohne dass die erste gelesen wird
     * (Festlegung, nicht gemessen); ein Fehler der Zeilenquelle der zweiten Auswahl ist das letzte Ereignis nach dem
     * vollständigen ersten Ergebnis.
     * @spec SPEC-036
     */
    @Test
    fun `ein Fehler in der zweiten Auswahl`() {
        val unknown = Probe(all)
        val rejected = collectEvents(unknown.backend, "SELECT id FROM person; SELECT id FROM nothing")
        assertEquals("TDS_SIM_UNKNOWN_OBJECT", assertIs<QueryEvent.Failure>(rejected.single()).code)
        assertEquals(0, unknown.opens)

        var opens = 0
        val table = SimTable(
            name = "person",
            columns = personColumns,
            primaryKey = listOf("Id"),
            rows = {
                check(++opens == 1) { "zweite Quelle" }
                object : RowSource {
                    private var done = false

                    override suspend fun next(): List<Any?>? = if (done) null else ada.also { done = true }

                    override fun close() = Unit
                }
            },
        )
        val sql = "SELECT id FROM person WHERE id = 1; SELECT id FROM person WHERE id = 1"
        val events = collectEvents(SimulationBackend(SimCatalog(listOf(table))), sql)
        val failure = assertIs<QueryEvent.Failure>(events.last())
        assertIs<QueryEvent.ResultStart>(events[0])
        assertEquals(QueryEvent.Row(listOf(1)), events[1])
        assertEquals(QueryEvent.ResultEnd(1), events[2])
        assertEquals(4, events.size)
        assertEquals("TDS_BACKEND_ERROR", failure.code)
    }

    /**
     * Grenze: ein Text mit zwei Auswahlen und Parametern ist abgewiesen (im Referenzabgleich nicht abgespielt),
     * nach der Prüfung gegen die Beschreibung: eine unbekannte Tabelle meldet `TDS_SIM_UNKNOWN_OBJECT`.
     * @spec SPEC-036
     */
    @Test
    fun `zwei Auswahlen mit Parametern sind abgewiesen`() {
        val parameters = listOf(QueryParameter("@id", SqlType.Int32, 1))

        val events = collectEvents(Probe(all).backend, "SELECT id FROM person; SELECT id FROM person", parameters)

        assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(events.single()).code)

        val unknown = collectEvents(Probe(all).backend, "SELECT id FROM person; SELECT id FROM nothing", parameters)
        assertEquals("TDS_SIM_UNKNOWN_OBJECT", assertIs<QueryEvent.Failure>(unknown.single()).code)
    }

    /**
     * Happy (A-16): ein `InputOutput`-Parameter bekommt als Ausgabewert seinen Eingabewert, nach dem Ergebnis
     * (Mitschnitt `rpc-ausgabewert`: `@id` mit Wert 7 an `SELECT Id FROM person WHERE Id = @id`, RETURNVALUE Wert 7).
     * Ein `INPUT`-Parameter bekommt keinen; für einen reinen `OUTPUT`-Parameter ist keine Messung bekannt, er bekommt
     * keinen.
     * @spec SPEC-030
     */
    @Test
    fun `ein InputOutput-Parameter bekommt seinen Eingabewert als Ausgabewert`() {
        val id = QueryParameter("@id", SqlType.Int32, 1, ParameterDirection.INPUT_OUTPUT)
        val plain = QueryParameter("@name", SqlType.UnicodeText, "x")
        val out = QueryParameter("@o", SqlType.Int32, null, ParameterDirection.OUTPUT)

        val events = select("SELECT id FROM person WHERE id = @id", parameters = listOf(id, plain, out))

        assertEquals(
            listOf(
                QueryEvent.ResultStart(listOf(ResultColumn("Id", SqlType.Int32, false))),
                QueryEvent.Row(listOf(1)),
                QueryEvent.ResultEnd(1),
                QueryEvent.OutputParameter("@id", SqlType.Int32, 1),
            ),
            events,
        )
    }

    /**
     * Fehler: ein Wert, den die Spalte nicht hält, in einer der beiden Auswahlen weist den ganzen Text mit
     * `TDS_SIM_INVALID_VALUE` ab, bevor eine Quelle geöffnet wird (Festlegung, nicht gemessen).
     * @spec SPEC-036
     */
    @Test
    fun `ein nicht überführbarer Wert in einer Auswahl weist die Folge ab`() {
        for (sql in listOf(
            "SELECT id FROM person WHERE id = 'abc'; SELECT name FROM person",
            "SELECT id FROM person; SELECT name FROM person WHERE id = 'abc'",
        )) {
            val probe = Probe(all)

            val events = collectEvents(probe.backend, sql)

            assertEquals("TDS_SIM_INVALID_VALUE", assertIs<QueryEvent.Failure>(events.single(), sql).code)
            assertEquals(0, probe.opens, sql)
        }
    }

    /**
     * Fehler: scheitert die erste Quelle, ist der Fehler das einzige Ereignis; die zweite Auswahl öffnet keine
     * Quelle, und der Ausgabewert eines `InputOutput`-Parameters folgt einem Fehler nicht.
     * @spec SPEC-036
     * @spec SPEC-030
     */
    @Test
    fun `nach einem Fehler der ersten Quelle folgen weder die zweite Auswahl noch der Ausgabewert`() {
        val series = Probe(all, openFailure = IllegalStateException("Geheimnis"))
        val events = collectEvents(series.backend, "SELECT id FROM person; SELECT name FROM person")
        assertEquals("TDS_BACKEND_ERROR", assertIs<QueryEvent.Failure>(events.single()).code)
        assertEquals(1, series.opens)

        val single = Probe(all, openFailure = IllegalStateException("Geheimnis"))
        val id = QueryParameter("@id", SqlType.Int32, 1, ParameterDirection.INPUT_OUTPUT)
        val echoed = collectEvents(single.backend, "SELECT id FROM person WHERE id = @id", listOf(id))
        assertEquals("TDS_BACKEND_ERROR", assertIs<QueryEvent.Failure>(echoed.single()).code)
    }
}
