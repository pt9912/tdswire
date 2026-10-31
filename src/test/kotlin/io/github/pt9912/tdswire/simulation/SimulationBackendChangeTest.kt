package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests von Einfügen, Ändern und Löschen (`TDS-FA-11.a` Schritt 6) gegen eine zählende Attrappe der Callbacks:
 * Aufrufe, Werte und Filter, die der Callback bekommt, und die Antwort. Die Reihenfolge der Prüfung (Werte vor
 * Callback), die feste Meldung eines Callback-Fehlers und die Ablehnung einer negativen Zahl sind Festlegungen
 * (Setzung, nicht am Server gemessen).
 */
class SimulationBackendChangeTest {
    /** Die Callbacks einer Tabelle `person`; jeder zählt seine Aufrufe und hält Werte und Filter des letzten. */
    private class Callbacks(
        val insert: Boolean = true,
        val update: Boolean = true,
        val delete: Boolean = true,
        var result: Long = 7,
        var failure: Throwable? = null,
    ) {
        var inserts = 0
        var updates = 0
        var deletes = 0
        var values: Map<String, Any?>? = null
        var set: Map<String, Any?>? = null
        var filter: RowFilter? = null
        var opens = 0

        val total: Int get() = inserts + updates + deletes

        private fun answer(): Long {
            failure?.let { throw it }
            return result
        }

        val table = SimTable(
            name = "person",
            columns = personColumns,
            primaryKey = listOf("Id"),
            rows = {
                opens++
                RowSource { null }
            },
            onInsert = if (insert) {
                { v ->
                    inserts++
                    values = v
                    answer()
                }
            } else {
                null
            },
            onUpdate = if (update) {
                { s, w ->
                    updates++
                    set = s
                    filter = w
                    answer()
                }
            } else {
                null
            },
            onDelete = if (delete) {
                { w ->
                    deletes++
                    filter = w
                    answer()
                }
            } else {
                null
            },
        )

        val backend get() = SimulationBackend(SimCatalog(listOf(table)))
    }

    private fun run(callbacks: Callbacks, sql: String, parameters: List<QueryParameter> = emptyList()) =
        collectEvents(callbacks.backend, sql, parameters)

    /**
     * Happy: W1 ruft den Callback genau einmal mit den überführten Werten unter den Namen der Beschreibung in der
     * Reihenfolge des Textes (eine nicht genannte Spalte fehlt) und sendet dessen Zahl als `UpdateCount`; die
     * Zeilenquelle wird nicht geöffnet.
     * @spec SPEC-035
     */
    @Test
    fun `Einfuegen ruft den Callback einmal mit den Werten und sendet die Zahl`() {
        val callbacks = Callbacks(result = 1)

        val events = run(callbacks, "INSERT INTO PERSON (name, ID, Age) VALUES ('Ada', 1, NULL)")

        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(1)), events)
        assertEquals(1, callbacks.inserts)
        assertEquals(1, callbacks.total)
        assertEquals(listOf("Name", "Id", "Age"), callbacks.values?.keys?.toList())
        assertEquals(mapOf("Name" to "Ada", "Id" to 1, "Age" to null), callbacks.values)
        assertEquals(0, callbacks.opens)
    }

    /**
     * Happy: ein Parameter wird als typisierter Wert eingesetzt und in den Spaltentyp überführt, auch bei anderer
     * Schreibweise des Namens; die Zahl `0` ist ein gültiger Änderungszähler.
     * @spec SPEC-035
     */
    @Test
    fun `Einfuegen setzt Parameter ein und sendet auch die Zahl null`() {
        val callbacks = Callbacks(result = 0)
        val parameters = listOf(QueryParameter("@p", SqlType.Int32, 5), QueryParameter("@n", SqlType.UnicodeText, "Bo"))

        val events = run(callbacks, "INSERT INTO person (id, name) VALUES (@P, @n)", parameters)

        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), events)
        assertEquals(mapOf("Id" to 5, "Name" to "Bo"), callbacks.values)
    }

    /**
     * Happy: W2 bekommt die Zuweisungen als Abbildung und die Bedingung als Filter mit den Spaltennamen der
     * Beschreibung und den überführten Werten; ohne `WHERE` ist der Filter `null` (alle Zeilen).
     * @spec SPEC-035
     */
    @Test
    fun `Aendern bekommt Zuweisungen und Filter und ohne WHERE keinen Filter`() {
        val callbacks = Callbacks(result = 3)

        val filtered = run(callbacks, "UPDATE person SET age = 40, NAME = 'X' WHERE id IN (1, 2) AND born IS NULL")

        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(3)), filtered)
        assertEquals(mapOf("Age" to 40, "Name" to "X"), callbacks.set)
        val terms = listOf(FilterTerm.In("Id", listOf(1, 2)), FilterTerm.IsNull("Born", negated = false))
        assertEquals(RowFilter(terms), callbacks.filter)

        val all = run(callbacks, "UPDATE person SET age = 41")

        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(3)), all)
        assertEquals(mapOf("Age" to 41), callbacks.set)
        assertNull(callbacks.filter)
        assertEquals(2, callbacks.updates)
        assertEquals(2, callbacks.total)
    }

    /**
     * Happy: W3 bekommt die Bedingung als Filter und ohne `WHERE` `null`.
     * @spec SPEC-035
     */
    @Test
    fun `Loeschen bekommt den Filter und ohne WHERE keinen`() {
        val callbacks = Callbacks(result = 2)

        val filtered = run(callbacks, "DELETE FROM person WHERE name IS NOT NULL AND age = 36")

        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(2)), filtered)
        val terms = listOf(FilterTerm.IsNull("Name", negated = true), FilterTerm.In("Age", listOf(36)))
        assertEquals(RowFilter(terms), callbacks.filter)

        run(callbacks, "DELETE FROM person")

        assertNull(callbacks.filter)
        assertEquals(2, callbacks.deletes)
        assertEquals(2, callbacks.total)
    }

    /**
     * Negative: eine Tabelle ohne den Callback der Anweisung endet mit `TDS_UNSUPPORTED_SQL` als einzigem
     * `Failure`, kein anderer Callback wird gerufen und die Zeilenquelle nicht geöffnet. Kontrollfall: dieselbe
     * Anweisung an einer Tabelle mit Callback liefert den Zähler (der Test belegt also den fehlenden Callback und nicht
     * die Anweisung).
     * @spec SPEC-037
     */
    @Test
    fun `ohne Callback endet die Anweisung mit TDS_UNSUPPORTED_SQL`() {
        val statements = listOf(
            "INSERT INTO person (id) VALUES (1)",
            "UPDATE person SET age = 1",
            "DELETE FROM person",
        )
        val withoutAny = Callbacks(insert = false, update = false, delete = false)
        for (sql in statements) {
            val events = run(withoutAny, sql)

            assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(events.single(), sql).code, sql)
            assertEquals(0, withoutAny.total, sql)
            assertEquals(0, withoutAny.opens, sql)

            val control = Callbacks(result = 9)
            assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(9)), run(control, sql), sql)
            assertEquals(1, control.total, sql)
        }

        val onlyInsert = Callbacks(update = false, delete = false)
        assertIs<QueryEvent.Failure>(run(onlyInsert, "UPDATE person SET age = 1").single())
        assertIs<QueryEvent.Failure>(run(onlyInsert, "DELETE FROM person").single())
        assertEquals(0, onlyInsert.total)
    }

    /**
     * Negative: ein nicht überführbarer Wert (Text in einer Ganzzahlspalte, Überlauf, fehlender Parameter) in den
     * Werten oder in der Bedingung endet mit `TDS_SIM_INVALID_VALUE`, ohne dass ein Callback gerufen wird. Die
     * Prüfung der Werte kommt vor der Prüfung des Callbacks: dieselbe Anweisung an einer Tabelle ohne Callback endet
     * ebenso mit `TDS_SIM_INVALID_VALUE`. Kontrollfälle: mit gültigem Wert ruft dieselbe Form den Callback einmal.
     * @spec SPEC-039
     */
    @Test
    fun `ein ungueltiger Wert endet ohne Aufruf des Callbacks mit TDS_SIM_INVALID_VALUE`() {
        val invalid = listOf(
            "INSERT INTO person (id) VALUES ('abc')",
            "INSERT INTO person (id) VALUES (2147483648)",
            "INSERT INTO person (id, age) VALUES (1, @missing)",
            "UPDATE person SET age = 'x'",
            "UPDATE person SET age = 1 WHERE id = 'x'",
            "DELETE FROM person WHERE id = 1.5",
        )
        val valid = listOf(
            "INSERT INTO person (id) VALUES (3)",
            "INSERT INTO person (id, age) VALUES (1, 4)",
            "UPDATE person SET age = 2",
            "UPDATE person SET age = 1 WHERE id = 5",
            "DELETE FROM person WHERE id = 1.0",
        )
        val callbacks = Callbacks()
        for (sql in invalid) {
            val failure = assertIs<QueryEvent.Failure>(run(callbacks, sql).single(), sql)

            assertEquals("TDS_SIM_INVALID_VALUE", failure.code, sql)
        }
        assertEquals(0, callbacks.total)
        val bare = Callbacks(insert = false, update = false, delete = false)
        for (sql in invalid) {
            assertEquals("TDS_SIM_INVALID_VALUE", assertIs<QueryEvent.Failure>(run(bare, sql).single(), sql).code, sql)
        }
        for (sql in valid) {
            assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(7)), run(callbacks, sql), sql)
        }
        assertEquals(valid.size, callbacks.total)
    }

    /**
     * Negative: eine unbekannte Spalte in einer Änderung endet mit `TDS_SIM_UNKNOWN_OBJECT`, ohne Aufruf.
     * @spec SPEC-038
     */
    @Test
    fun `eine unbekannte Spalte endet ohne Aufruf des Callbacks`() {
        val callbacks = Callbacks()

        val events = run(callbacks, "UPDATE person SET nothing = 1")

        assertEquals("TDS_SIM_UNKNOWN_OBJECT", assertIs<QueryEvent.Failure>(events.single()).code)
        assertEquals(0, callbacks.total)
    }

    /**
     * Negative: ein Fehler des Callbacks ist ein einziges `Failure` mit `TDS_BACKEND_ERROR` und fester Meldung ohne
     * den Text der Ausnahme; auch hier genau ein Aufruf. Boundary: eine negative Zahl des Callbacks ist
     * derselbe Fehler.
     * @spec SPEC-035
     */
    @Test
    fun `ein Fehler des Callbacks ist TDS_BACKEND_ERROR ohne den Text der Ausnahme`() {
        val callbacks = Callbacks(failure = IllegalStateException("Geheimnis des Integrators"))

        val events = run(callbacks, "INSERT INTO person (id) VALUES (1)")

        val failure = assertIs<QueryEvent.Failure>(events.single())
        assertEquals("TDS_BACKEND_ERROR", failure.code)
        assertFalse(failure.message.contains("Geheimnis"), failure.message)
        assertEquals(1, callbacks.total)

        val negative = Callbacks(result = -1)
        val answer = assertIs<QueryEvent.Failure>(run(negative, "DELETE FROM person").single())
        assertEquals("TDS_BACKEND_ERROR", answer.code)
        assertEquals(1, negative.total)
    }

    /**
     * Boundary: schließt die Ausführung (`close`) oder bricht `cancel` sie vor dem Sammeln ab, wird der Callback nie
     * gerufen und der Strom ist leer; ohne Abbruch ruft das Sammeln ihn einmal.
     * @spec SPEC-029
     */
    @Test
    fun `vor dem Sammeln beendet ruft die Ausfuehrung den Callback nicht`() = runBlocking {
        val callbacks = Callbacks()
        val backend = callbacks.backend

        val closed = backend.execute(queryRequest("DELETE FROM person"))
        closed.close()
        val cancelled = backend.execute(queryRequest("DELETE FROM person", requestId = "r2"))
        backend.cancel("s1", "r2")
        val ran = backend.execute(queryRequest("DELETE FROM person", requestId = "r3"))

        assertEquals(emptyList(), collect(closed))
        assertEquals(emptyList(), collect(cancelled))
        assertEquals(0, callbacks.total)
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(7)), collect(ran))
        assertEquals(1, callbacks.deletes)
    }

    /**
     * Boundary: der Strom ist kalt; zwei Sammler derselben Ausführung rufen den Callback zweimal (die Zusage „genau
     * einmal“ gilt nur für einen einzigen Sammler).
     * @spec SPEC-035
     */
    @Test
    fun `ein zweiter Sammler derselben Ausfuehrung ruft den Callback ein zweites Mal`() = runBlocking {
        val callbacks = Callbacks()
        val execution = callbacks.backend.execute(queryRequest("DELETE FROM person"))

        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(7)), collect(execution))
        assertEquals(1, callbacks.deletes)
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(7)), collect(execution))
        assertEquals(2, callbacks.deletes)
    }
}

private suspend fun collect(execution: QueryExecution): List<QueryEvent> {
    val events = ArrayList<QueryEvent>()
    execution.events.collect { events += it }
    return events
}
