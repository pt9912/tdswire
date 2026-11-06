package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Die Regeln, die der Testserver aus der Bibliothek kopiert, gleich ihrer Wirkung in der Bibliothek: der
 * Namensvergleich (`NameFold` gegen den Katalog `SimCatalog`) und die Obergrenze 38 der Dezimalzahl
 * (`SimColumnTypes` gegen `SimColumn`). Geprüft ist die Wirkung über die öffentliche Fläche, nicht der Quelltext.
 */
internal class NameFoldTest {
    private fun table(name: String) = SimTable(
        name = name,
        columns = listOf(SimColumn("Id", SqlType.Int32, nullable = false)),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
    )

    /**
     * Zwei Namen, die `NameFold` als gleich ansieht, weist der Katalog als doppelt ab, und umgekehrt (Schreibweisen,
     * `ß`, `İ`, das Schluss-Sigma).
     *
     * @spec SPEC-036
     */
    @Test
    fun `NameFold und Katalog nennen dieselben Namen gleich`() {
        val pairs = listOf(
            "person" to "PERSON",
            "Person" to "pErSoN",
            "straße" to "STRASSE",
            "i̇" to "İ",
            "σ" to "ς",
            "a" to "b",
            "kunde" to "kunden",
        )
        for ((first, second) in pairs) {
            val folded = NameFold.firstDuplicate(listOf(first, second)) != null
            val rejected = runCatching { SimCatalog(listOf(table(first), table(second))) }.isFailure
            assertEquals(rejected, folded, "'$first' und '$second'")
        }
    }

    /**
     * Der Leser weist eine Dezimalzahl über 38 Stellen ab, und `SimColumn` der Bibliothek tut es mit derselben Grenze.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Die Obergrenze der Dezimalzahl ist die der Bibliothek`() {
        assertEquals(38, SimColumnTypes.MAX_PRECISION)
        SimColumn("c", SqlType.Decimal(SimColumnTypes.MAX_PRECISION, 0))
        val over = SqlType.Decimal(SimColumnTypes.MAX_PRECISION + 1, 0)
        assertFailsWith<IllegalArgumentException> { SimColumn("c", over) }
    }
}
