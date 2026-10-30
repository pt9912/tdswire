package io.github.pt9912.tdswire.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendModelTest {
    /**
     * Decimal nimmt eine Precision und einen Scale an, wie sie der Mindestkatalog nennt.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Decimal akzeptiert Precision und Scale des Mindestkatalogs`() {
        assertEquals(5, SqlType.Decimal(5, 2).precision)
        assertEquals(2, SqlType.Decimal(5, 2).scale)
    }

    /**
     * Setzung des Quelltexts, nicht von `SPEC-003` belegt: die Ränder `scale = 0` und
     * `scale = precision` gelten.
     */
    @Test
    fun `Decimal akzeptiert die Raender von Scale`() {
        assertEquals(0, SqlType.Decimal(5, 0).scale)
        assertEquals(5, SqlType.Decimal(5, 5).scale)
    }

    /**
     * Setzung des Quelltexts, nicht von `SPEC-003` belegt: Precision unter 1, negativer Scale und
     * Scale über Precision werden abgewiesen.
     */
    @Test
    fun `Decimal lehnt ungueltige Precision und Scale ab`() {
        assertFailsWith<IllegalArgumentException> { SqlType.Decimal(0, 0) }
        assertFailsWith<IllegalArgumentException> { SqlType.Decimal(5, -1) }
        assertFailsWith<IllegalArgumentException> { SqlType.Decimal(5, 6) }
    }

    /**
     * Ein Parameter ohne Angabe ist ein Eingabeparameter.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Parameter ohne Richtung ist INPUT`() {
        assertEquals(ParameterDirection.INPUT, QueryParameter("p", SqlType.Int64, 1L).direction)
    }

    /**
     * Der Beleg, dass das Ereignismodell nur diese Untertypen hat, ist der Compiler: `name` ist ein
     * erschöpfendes `when` ohne `else` und übersetzt nur, solange die Menge der Untertypen von
     * [QueryEvent] genau diese ist. Der Test belegt allein die Zuordnung eines Beispiels.
     */
    @Test
    fun `name ordnet ein Ereignis seinem Untertyp zu`() {
        assertEquals("Failure", name(QueryEvent.Failure("c", "m")))
    }

    private fun name(event: QueryEvent): String = when (event) {
        is QueryEvent.ResultStart -> "ResultStart"
        is QueryEvent.Row -> "Row"
        is QueryEvent.ResultEnd -> "ResultEnd"
        is QueryEvent.UpdateCount -> "UpdateCount"
        is QueryEvent.ReturnStatus -> "ReturnStatus"
        is QueryEvent.OutputParameter -> "OutputParameter"
        is QueryEvent.Failure -> "Failure"
    }
}
