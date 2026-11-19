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

    /** Änderung und Löschung zählen die Zeilen, auf die die Bedingung zutrifft; der Bestand bleibt unverändert. */
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
        assertEquals(ALL_ROWS, drain(person))
    }

    private suspend fun drain(table: SimTable): Long {
        val source = table.rows(SimQuery(table.columns, null))
        var count = 0L
        while (source.next() != null) count++
        source.close()
        return count
    }

    /** `Id` von `laufend` ist nullbar, wie in der Ansicht der Referenz; der Schlüssel entfällt mit ihr. */
    @Test
    fun `laufend beschreibt Id nullbar ohne Schluessel`() {
        val running = table("laufend")

        assertTrue(running.columns.first { it.name == "Id" }.nullable)
        assertTrue(running.primaryKey.isEmpty())
    }
}
