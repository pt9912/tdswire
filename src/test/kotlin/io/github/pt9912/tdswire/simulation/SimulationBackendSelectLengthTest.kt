package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SHORT = 10
private const val LONG = 200
private const val DEFAULT_LENGTH = 4000

/**
 * Tests der Länge einer Textspalte im Ergebnis einer Auswahl (`SPEC-035`, `SPEC-003`): `SimColumn.maxLength` erscheint
 * als `ResultColumn.maxLength` in jedem `ResultStart` (Formen S1 und S2, auch bei einer Bedingung, die nie wahr ist),
 * ohne Angabe bleibt 4000, -1 ergibt 4000. Gemessen ist `nvarchar(10)` (Länge 20) einer Ansicht am Referenzclient
 * (Messprotokoll A-14); die Zeilen der Tests sind handgebaut, die Abbildung von -1 ist Setzung.
 */
class SimulationBackendSelectLengthTest {
    private val table = SimTable(
        name = "kaputt",
        columns = listOf(
            SimColumn("Id", SqlType.Int32, nullable = false),
            SimColumn("Kurz", SqlType.UnicodeText, maxLength = SHORT),
            SimColumn("Lang", SqlType.UnicodeText, maxLength = LONG),
            SimColumn("Frei", SqlType.UnicodeText),
            SimColumn("Unbegrenzt", SqlType.UnicodeText, maxLength = -1),
        ),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
    )

    private fun run(sql: String): List<QueryEvent> = collectEvents(SimulationBackend(SimCatalog(listOf(table))), sql)

    private fun starts(sql: String): List<List<ResultColumn>> =
        run(sql).filterIsInstance<QueryEvent.ResultStart>().map { it.columns }

    private fun lengths(columns: List<ResultColumn>) = columns.associate { it.name to it.maxLength }

    /**
     * Happy (Form S1): jede Textspalte meldet die Länge ihrer Beschreibung; die Spalte ohne Angabe 4000 (Vorgabe), die
     * mit -1 ebenfalls 4000 (Setzung); eine Spalte, die keinen Text führt, behält die Vorgabe. Wächter: ein
     * `ResultStart`, der die Länge nicht aus der Beschreibung nimmt (alle 4000), eine Abbildung von -1, die -1
     * durchreicht (der Konstruktor von `ResultColumn` weist sie ab).
     * @spec SPEC-035
     * @spec SPEC-003
     */
    @Test
    fun `eine Auswahl meldet die Laenge der Beschreibung`() {
        val columns = starts("SELECT * FROM kaputt").single()

        assertEquals(
            mapOf(
                "Id" to DEFAULT_LENGTH,
                "Kurz" to SHORT,
                "Lang" to LONG,
                "Frei" to DEFAULT_LENGTH,
                "Unbegrenzt" to DEFAULT_LENGTH,
            ),
            lengths(columns),
        )
        assertEquals(ResultColumn("Kurz", SqlType.UnicodeText, true, SHORT), columns[1])
    }

    /**
     * Happy: die Auswahlliste bestimmt, welche Längen kommen, in ihrer Reihenfolge; eine Spalte zweimal gewählt meldet
     * zweimal dieselbe Länge. Wächter: eine Länge, die an der Stelle der Beschreibung statt der der Auswahlliste liest.
     * @spec SPEC-035
     */
    @Test
    fun `die Auswahlliste bestimmt die gemeldeten Laengen`() {
        val columns = starts("SELECT lang, kurz, LANG FROM kaputt").single()

        assertEquals(listOf(LONG, SHORT, LONG), columns.map { it.maxLength })
    }

    /**
     * Happy (Form S2): beide Ergebnisse tragen die Länge ihrer Auswahl, je nach Spalte. Wächter: eine Länge, die nur
     * das erste oder nur das zweite Ergebnis erreicht.
     * @spec SPEC-036
     * @spec SPEC-035
     */
    @Test
    fun `zwei Auswahlen melden je die Laenge ihrer Spalte`() {
        val both = starts("SELECT kurz FROM kaputt; SELECT lang FROM kaputt")

        assertEquals(listOf(listOf(SHORT), listOf(LONG)), both.map { columns -> columns.map { it.maxLength } })
    }

    /**
     * Boundary: bei einer Bedingung, die nie wahr ist, kommt das Ergebnis ohne Zeile und ohne geöffnete Quelle, und es
     * trägt die Länge (S1 und als erste Auswahl von S2). Wächter: der Zweig `WHERE 1 = 0`, der die Spalten ohne Länge
     * baut.
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `eine Bedingung die nie wahr ist meldet die Laenge`() {
        val single = run("SELECT kurz FROM kaputt WHERE 1 = 0")
        assertEquals(
            listOf(
                QueryEvent.ResultStart(listOf(ResultColumn("Kurz", SqlType.UnicodeText, true, SHORT))),
                QueryEvent.ResultEnd(0),
            ),
            single,
        )

        val form2 = starts("SELECT lang FROM kaputt WHERE 1 = 0; SELECT kurz FROM kaputt")
        assertEquals(listOf(listOf(LONG), listOf(SHORT)), form2.map { columns -> columns.map { it.maxLength } })
    }

    /**
     * Boundary: `SimColumn.maxLength` mit dem Wert 1 und 4000 erreicht das Ergebnis unverändert (die Grenzen des
     * Wertebereichs). Wächter: eine Abbildung, die einen Randwert verschiebt.
     * @spec SPEC-035
     */
    @Test
    fun `die Grenzen der Laenge erreichen das Ergebnis`() {
        val bounds = table.copy(
            columns = listOf(
                SimColumn("Id", SqlType.Int32, nullable = false),
                SimColumn("Eins", SqlType.UnicodeText, maxLength = 1),
                SimColumn("Voll", SqlType.UnicodeText, maxLength = DEFAULT_LENGTH),
            ),
        )
        val events = collectEvents(SimulationBackend(SimCatalog(listOf(bounds))), "SELECT eins, voll FROM kaputt")

        val columns = events.filterIsInstance<QueryEvent.ResultStart>().single().columns
        assertEquals(listOf(1, DEFAULT_LENGTH), columns.map { it.maxLength })
    }
}
