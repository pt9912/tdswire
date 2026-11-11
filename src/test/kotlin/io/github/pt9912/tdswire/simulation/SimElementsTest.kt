package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Die Elemente einer Tabellenbeschreibung (`SPEC-035`): Eindeutigkeit, Fremdschlüssel, Index, Prüfbedingung und der
 * Standardwert einer Spalte. Die Prüfungen gelten für die Kotlin-Schnittstelle ebenso wie für den Leser der Datei und
 * sind Setzung (nicht am Server gemessen).
 */
class SimElementsTest {
    private val columns = listOf(
        SimColumn("Id", SqlType.Int32, nullable = false),
        SimColumn("Name", SqlType.UnicodeText),
        SimColumn("Ref", SqlType.Int32),
    )

    private fun table(
        uniqueKeys: List<SimUniqueKey> = emptyList(),
        foreignKeys: List<SimForeignKey> = emptyList(),
        indexes: List<SimIndex> = emptyList(),
        checks: List<SimCheck> = emptyList(),
    ) = SimTable(
        name = "t",
        columns = columns,
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
        uniqueKeys = uniqueKeys,
        foreignKeys = foreignKeys,
        indexes = indexes,
        checks = checks,
    )

    /**
     * Happy: eine Tabelle ohne Elemente (der Vorgabewert aller vier Listen) und eine mit allen vier; die Namen der
     * Elemente und der Spalten gelten ohne Beachtung der Schreibweise.
     * Wächter: eine Prüfung, die gültige Beschreibungen abweist.
     * @spec SPEC-035
     */
    @Test
    fun `gueltige Elemente werden angenommen`() {
        val plain = table()
        assertEquals(emptyList(), plain.uniqueKeys + plain.indexes)
        val full = table(
            uniqueKeys = listOf(SimUniqueKey("u", listOf("name"))),
            foreignKeys = listOf(SimForeignKey("f", listOf("REF"), "t", listOf("id"), "SET_NULL", "CASCADE")),
            indexes = listOf(SimIndex("i", listOf("Name"), unique = true, included = listOf("ref"))),
            checks = listOf(SimCheck("c", "([Id]>(0))")),
        )
        assertEquals(1, full.checks.size)
    }

    /**
     * Negativ: ein leerer Name, keine Spalte, eine doppelte Spalte, eine Spalte ohne Namen, ungleich viele Spalten und
     * Zielspalten, eine Aktion außerhalb der vier und eine leere Definition weist der Konstruktor ab.
     * Wächter: ein Konstruktor ohne Prüfung (der Katalog meldete Zeilen ohne Spalte).
     * @spec SPEC-035
     */
    @Test
    fun `ungueltige Elemente werden abgewiesen`() {
        assertFailsWith<IllegalArgumentException> { SimUniqueKey("", listOf("a")) }
        assertFailsWith<IllegalArgumentException> { SimUniqueKey("u", emptyList()) }
        assertFailsWith<IllegalArgumentException> { SimUniqueKey("u", listOf("a", "A")) }
        assertFailsWith<IllegalArgumentException> { SimUniqueKey("u", listOf("")) }
        assertFailsWith<IllegalArgumentException> { SimForeignKey("", listOf("a"), "t", listOf("a")) }
        assertFailsWith<IllegalArgumentException> { SimForeignKey("f", listOf("a"), "", listOf("a")) }
        assertFailsWith<IllegalArgumentException> { SimForeignKey("f", listOf("a", "b"), "t", listOf("a")) }
        assertFailsWith<IllegalArgumentException> { SimForeignKey("f", listOf("a"), "t", listOf("a"), "RESTRICT") }
        assertFailsWith<IllegalArgumentException> {
            SimForeignKey("f", listOf("a"), "t", listOf("a"), onUpdate = "no_action")
        }
        assertFailsWith<IllegalArgumentException> { SimIndex("", listOf("a")) }
        assertFailsWith<IllegalArgumentException> { SimIndex("i", emptyList()) }
        assertFailsWith<IllegalArgumentException> { SimIndex("i", listOf("a"), included = listOf("A")) }
        assertFailsWith<IllegalArgumentException> { SimIndex("i", listOf("a"), included = listOf("")) }
        assertFailsWith<IllegalArgumentException> { SimCheck("", "x") }
        assertFailsWith<IllegalArgumentException> { SimCheck("c", "") }
        assertFailsWith<IllegalArgumentException> { SimColumn("a", SqlType.Int32, defaultDefinition = "") }
    }

    /**
     * Negativ: eine Tabelle weist ab, was sich widerspricht: ein Name einer Bedingung doppelt (über Eindeutigkeit,
     * Fremdschlüssel und Prüfbedingung hinweg), ein Index, der den Namen einer Eindeutigkeit oder eines anderen
     * Index trägt, und jede Spalte, die die Tabelle nicht hat (in Eindeutigkeit, Fremdschlüssel, Schlüssel und
     * eingeschlossener Spalte des Index). Die Zielspalten eines Fremdschlüssels prüft die Tabelle nicht.
     * Wächter: eine Prüfung, die nur eine der drei Listen vergleicht, oder die eingeschlossenen Spalten übergeht.
     * @spec SPEC-035
     */
    @Test
    fun `eine Tabelle weist widerspruechliche Elemente ab`() {
        val u = SimUniqueKey("x", listOf("Name"))
        val f = SimForeignKey("X", listOf("Ref"), "t", listOf("Id"))
        val c = SimCheck("x", "([Id]>(0))")
        assertFailsWith<IllegalArgumentException> { table(uniqueKeys = listOf(u), foreignKeys = listOf(f)) }
        assertFailsWith<IllegalArgumentException> { table(uniqueKeys = listOf(u), checks = listOf(c)) }
        assertFailsWith<IllegalArgumentException> { table(foreignKeys = listOf(f), checks = listOf(c)) }
        assertFailsWith<IllegalArgumentException> { table(uniqueKeys = listOf(u, u.copy(name = "X"))) }
        assertFailsWith<IllegalArgumentException> {
            table(uniqueKeys = listOf(u), indexes = listOf(SimIndex("X", listOf("Id"))))
        }
        assertFailsWith<IllegalArgumentException> {
            table(indexes = listOf(SimIndex("i", listOf("Id")), SimIndex("I", listOf("Name"))))
        }
        assertFailsWith<IllegalArgumentException> { table(uniqueKeys = listOf(SimUniqueKey("u", listOf("Nix")))) }
        assertFailsWith<IllegalArgumentException> {
            table(foreignKeys = listOf(SimForeignKey("f", listOf("Nix"), "t", listOf("Id"))))
        }
        assertFailsWith<IllegalArgumentException> { table(indexes = listOf(SimIndex("i", listOf("Nix")))) }
        assertFailsWith<IllegalArgumentException> {
            table(indexes = listOf(SimIndex("i", listOf("Id"), included = listOf("Nix"))))
        }
        table(foreignKeys = listOf(SimForeignKey("f", listOf("Ref"), "anders", listOf("Nix"))))
    }
}
