package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.simulation.FilterTerm
import io.github.pt9912.tdswire.simulation.RowFilter
import io.github.pt9912.tdswire.simulation.SimQuery
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Kopplung: Alter 36 und die drei Zeilen von `person` wie `ADA_AGE` und die Zeilen in KompatibilitaetsServer.kt,
// wie `testschema.sql` und die Konstanten `Geaendert`, `Geloescht`, `ZeilenMitAlter36` in SzenarienTests.cs.
private const val AGE = 36
private const val ALL_ROWS = 3L
private const val AGE_ROWS = 2L

/**
 * Die Tabellen des Kompatibilitätslaufs ohne Server: die Zahl, die `onUpdate` und `onDelete` von `person` melden, und
 * die Spaltenbeschreibung von `laufend`, die der Ansicht der Referenz (`testschema.sql`) gleicht.
 */
internal class KompatibilitaetsTablesTest {
    private fun table(name: String): SimTable = KompatibilitaetsTables.all().first { it.name == name }

    private fun id(vararg values: Any?) = RowFilter(listOf(FilterTerm.In("Id", values.toList())))

    /**
     * Änderung und Löschung zählen die Zeilen, auf die die Bedingung zutrifft.
     *
     * @spec SPEC-035
     */
    @Test
    fun `onUpdate und onDelete von person melden die Zahl der zutreffenden Zeilen`() = runBlocking {
        val person = table("person")
        val update = assertNotNull(person.onUpdate)
        val delete = assertNotNull(person.onDelete)

        assertEquals(1L, update(mapOf("Age" to 40), id(1)))
        assertEquals(1L, delete(id(3)))
        assertEquals(0L, delete(id(99)))
        assertEquals(ALL_ROWS, delete(id(1, 2, 3)))
        assertEquals(AGE_ROWS, update(mapOf("Name" to "x"), RowFilter(listOf(FilterTerm.In("Age", listOf(AGE))))))
        assertEquals(ALL_ROWS, delete(null))
        assertEquals(1L, delete(RowFilter(listOf(FilterTerm.IsNull("Age", negated = false)))))
    }

    /**
     * `Id` von `laufend` ist nullbar, wie in der Ansicht der Referenz; der Schlüssel entfällt mit ihr.
     *
     * @spec SPEC-045
     */
    @Test
    fun `laufend beschreibt Id nullbar ohne Schluessel`() {
        val running = table("laufend")

        assertTrue(running.columns.first { it.name == "Id" }.nullable)
        assertTrue(running.primaryKey.isEmpty())
    }

    /**
     * `Text` von `kaputt` hat die Länge 10 wie `nvarchar(10)` der Ansicht der Referenz (`testschema.sql`); die Zeilen,
     * die die Quelle liefert, passen hinein. Wächter: eine Beschreibung ohne Länge (4000) lässt die Länge im
     * COLMETADATA der Auswahl vom Schema der Referenz abweichen, die Regel A-14 der Ausnahmeliste des Mitschnitts.
     *
     * @spec SPEC-045
     * @spec SPEC-035
     */
    @Test
    fun `kaputt beschreibt Text mit der Laenge der Ansicht der Referenz`() = runBlocking {
        val broken = table("kaputt")

        assertEquals(BROKEN_TEXT_CHARS, broken.columns.first { it.name == "Text" }.maxLength)
        val source = broken.rows(SimQuery(broken.columns, null))
        val delivered = (1..KOMPAT_BROKEN_AFTER).map { assertNotNull(source.next()) }
        assertTrue(delivered.all { (it[1] as String).length <= BROKEN_TEXT_CHARS })
    }
}
