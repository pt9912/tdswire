package io.github.pt9912.tdswire.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendModelTest {
    /**
     * Decimal nimmt Precision und Scale an, die der Mindestkatalog nennt, einschließlich der
     * Ränder `scale = 0` und `scale = precision`.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Decimal akzeptiert gueltige Precision und Scale`() {
        assertEquals(5, SqlType.Decimal(5, 2).precision)
        assertEquals(0, SqlType.Decimal(5, 0).scale)
        assertEquals(5, SqlType.Decimal(5, 5).scale)
    }

    /**
     * Decimal weist Precision unter 1, negativen Scale und Scale über Precision ab.
     *
     * @spec SPEC-003
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
     * Das Ereignismodell hat genau die sieben Ereignisse aus `SPEC-002`: das erschöpfende `when`
     * ohne `else` übersetzt nur, solange die Menge der Untertypen diese sieben ist.
     *
     * @spec SPEC-002
     */
    @Test
    fun `QueryEvent kennt sieben Ereignisse`() {
        val events: List<QueryEvent> = listOf(
            QueryEvent.ResultStart(emptyList()),
            QueryEvent.Row(emptyList()),
            QueryEvent.ResultEnd(null),
            QueryEvent.UpdateCount(1),
            QueryEvent.ReturnStatus(0),
            QueryEvent.OutputParameter("o", SqlType.Bool, null),
            QueryEvent.Failure("c", "m"),
        )

        val names = events.map { name(it) }

        assertEquals(7, names.toSet().size)
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
