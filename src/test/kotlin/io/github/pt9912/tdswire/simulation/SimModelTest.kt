package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Tests der Validierung der Beschreibung (`SPEC-035` Validierung) für die Kotlin-Schnittstelle. Der Namensvergleich
 * ohne Beachtung der Schreibweise ist eine Festlegung, nicht am Server gemessen.
 */
class SimModelTest {
    private val empty = RowSource { null }

    private fun table(
        columns: List<SimColumn>,
        key: List<String>,
        name: String = "t",
        schema: String = DEFAULT_SCHEMA,
    ): SimTable = SimTable(schema = schema, name = name, columns = columns, primaryKey = key, rows = { empty })

    private val id = SimColumn("id", SqlType.Int32, nullable = false)

    /**
     * Eine gültige Tabelle; Spalten werden ohne Beachtung der Schreibweise gefunden.
     * @spec SPEC-035
     */
    @Test
    fun `nimmt eine gültige Tabelle an und findet Spalten ohne Beachtung der Schreibweise`() {
        val name = SimColumn("Name", SqlType.UnicodeText)
        val t = table(listOf(id, name), listOf("ID"))
        assertEquals(DEFAULT_SCHEMA, t.schema)
        assertSame(name, t.column("nAME"))
        assertNull(t.column("other"))
        assertNull(t.column(""))
    }

    /**
     * Doppelte Spaltennamen sind abgewiesen, auch bei anderer Schreibweise; Namen, die nur nach dem Zeichenvergleich
     * von `equalsIgnoreCase` gleich sind (`i` und `İ`), gelten als doppelt, `ss` und `ß` nicht.
     * @spec SPEC-035
     */
    @Test
    fun `weist doppelte Spaltennamen ab`() {
        for ((a, b) in listOf("id" to "id", "id" to "ID", "Id" to "iD", "i" to "İ", "Straße" to "STRAßE")) {
            val columns = listOf(SimColumn(a, SqlType.Int32, nullable = false), SimColumn(b, SqlType.Int32))
            assertFailsWith<IllegalArgumentException>("$a/$b") { table(columns, listOf(a)) }
        }
        val distinct = listOf(SimColumn("ss", SqlType.Int32, nullable = false), SimColumn("ß", SqlType.Int32))
        assertEquals(2, table(distinct, listOf("ss")).columns.size)
    }

    /**
     * Eine Tabelle braucht mindestens eine Spalte und einen Schlüssel; Namen sind nicht leer.
     * @spec SPEC-035
     */
    @Test
    fun `weist eine leere Spaltenliste, einen leeren Schlüssel und leere Namen ab`() {
        assertFailsWith<IllegalArgumentException> { table(emptyList(), emptyList()) }
        assertFailsWith<IllegalArgumentException> { table(listOf(id), emptyList()) }
        assertFailsWith<IllegalArgumentException> { table(listOf(id), listOf("id"), name = "") }
        assertFailsWith<IllegalArgumentException> { table(listOf(id), listOf("id"), schema = "") }
        assertFailsWith<IllegalArgumentException> { SimColumn("", SqlType.Int32) }
    }

    /**
     * Jeder Schlüsseleintrag nennt eine vorhandene, nicht nullbare Spalte, höchstens einmal.
     * @spec SPEC-035
     */
    @Test
    fun `prüft den Schlüssel auf Vorhandensein, Nullbarkeit und Wiederholung`() {
        val nullable = SimColumn("n", SqlType.Int32, nullable = true)
        assertFailsWith<IllegalArgumentException> { table(listOf(id), listOf("missing")) }
        assertFailsWith<IllegalArgumentException> { table(listOf(id, nullable), listOf("id", "n")) }
        assertFailsWith<IllegalArgumentException> { table(listOf(id), listOf("id", "ID")) }
        val second = SimColumn("b", SqlType.Int64, nullable = false)
        assertEquals(listOf("id", "b"), table(listOf(id, second), listOf("id", "b")).primaryKey)
    }

    /**
     * Der Katalog hält Tabellen über Schema und Name ohne Beachtung der Schreibweise eindeutig; ohne Schema gilt
     * `dbo`.
     * @spec SPEC-035
     */
    @Test
    fun `der Katalog findet Tabellen über Schema und Name`() {
        val dbo = table(listOf(id), listOf("id"), name = "Person")
        val sales = table(listOf(id), listOf("id"), name = "person", schema = "sales")
        val catalog = SimCatalog(listOf(dbo, sales))
        assertSame(dbo, catalog.find(null, "PERSON"))
        assertSame(dbo, catalog.find("DBO", "person"))
        assertSame(sales, catalog.find("Sales", "Person"))
        assertNull(catalog.find("other", "person"))
        assertNull(catalog.find(null, "missing"))
        val upper = table(listOf(id), listOf("id"), name = "PERSON")
        assertFailsWith<IllegalArgumentException> { SimCatalog(listOf(dbo, upper)) }
        assertFailsWith<IllegalArgumentException> { SimCatalog(listOf(dbo, dbo)) }
    }
}
