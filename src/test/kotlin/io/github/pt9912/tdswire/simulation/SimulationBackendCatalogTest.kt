package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Antworten auf die Katalogabfragen von d-migrate 1.12.0 (`SPEC-036`, Form K; `SPEC-035`, Katalog-Anbieter). Die
 * Anfragetexte sind der Mitschnitt ([dMigrateCatalogTexts]); die erwarteten Zeilen sind von Hand geschrieben. Gemessen
 * ist nur, dass d-migrate 1.12.0 die Antworten liest (Messprotokoll `katalog-und-ddl-zustand-2026-11-06`); die Werte
 * für `bigint`, `bit`, `time` und `datetime2` sind Setzung nach dem Handbuch des Servers.
 */
class SimulationBackendCatalogTest {
    private val rowless: suspend (SimQuery) -> RowSource = { RowSource { null } }

    private val kunden = SimTable(
        name = "kunden",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Name", SqlType.UnicodeText, maxLength = 100),
            SimColumn("Eintritt", SqlType.Date),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
    )

    private val typen = SimTable(
        schema = "app",
        name = "typen",
        columns = listOf(
            SimColumn("a", SqlType.Int32, nullable = false),
            SimColumn("b", SqlType.Int64, nullable = false),
            SimColumn("c", SqlType.Bool),
            SimColumn("d", SqlType.UnicodeText),
            SimColumn("e", SqlType.Decimal(10, 2)),
            SimColumn("f", SqlType.Date),
            SimColumn("g", SqlType.Time),
            SimColumn("h", SqlType.DateTime),
        ),
        primaryKey = listOf("b", "a"),
        rows = rowless,
    )

    private fun backend(vararg tables: SimTable) = SimulationBackend(SimCatalog(tables.toList()))

    private fun text(value: String?) = QueryParameter("@P0", SqlType.UnicodeText, value)

    private fun rows(events: List<QueryEvent>): List<List<Any?>> =
        events.filterIsInstance<QueryEvent.Row>().map { it.values }

    private fun ask(form: CatalogForm, parameter: QueryParameter?, vararg tables: SimTable): List<QueryEvent> =
        collectEvents(backend(*tables), form.template.replace("?", " @P0 "), listOfNotNull(parameter))

    /**
     * Happy: die Tabellen eines Schemas, nach Namen sortiert, mit Name und Schema; Schemanamen gelten ohne Beachtung
     * der Schreibweise; ein anderes Schema, ein NULL-Parameter und ein leerer Katalog liefern keine Zeile, aber die
     * Spalten.
     * Wächter: eine Antwort ohne Filter auf das Schema (dann kämen beide Tabellen) oder ohne Sortierung.
     * @spec SPEC-036
     */
    @Test
    fun `die Tabellenliste kommt aus dem Katalog`() {
        val bestellungen = kunden.copy(name = "bestellungen")
        val events = ask(CatalogForm.TABLES, text("DBO"), kunden, typen, bestellungen)

        assertEquals(CatalogForm.TABLES.columns, assertIs<QueryEvent.ResultStart>(events.first()).columns)
        assertEquals(listOf(listOf("bestellungen", "dbo"), listOf("kunden", "dbo")), rows(events))
        assertEquals(QueryEvent.ResultEnd(2), events.last())
        assertEquals(listOf(listOf("typen", "app")), rows(ask(CatalogForm.TABLES, text("app"), kunden, typen)))
        assertEquals(emptyList(), rows(ask(CatalogForm.TABLES, text("fremd"), kunden)))
        assertEquals(emptyList(), rows(ask(CatalogForm.TABLES, text(null), kunden)))
        assertEquals(QueryEvent.ResultEnd(0), ask(CatalogForm.TABLES, text("dbo")).last())
    }

    /**
     * Happy: die Spalten einer Tabelle in der Reihenfolge der Beschreibung mit Typname, Länge in Byte, Precision,
     * Skala, Nullbarkeit, Identität (immer 0), Berechnung (immer 0), `column_id` ab 1 und fünf leeren Feldern
     * (Standardwert, Identitätsstart, -schritt, Berechnung). `nvarchar` hat `2 * maxLength` Byte. Die Zeile von `Name`
     * entspricht dem Lauf mit d-migrate, der `type: text, max_length: 100` zurückliest.
     * Wächter: eine Länge in Zeichen statt Byte (d-migrate läse 50), ein Typname aus `SqlType` statt `sys.types`.
     * @spec SPEC-036
     */
    @Test
    fun `die Spalten einer Tabelle sind der Katalog des Servers`() {
        val events = ask(CatalogForm.COLUMNS, text("[dbo].[kunden]"), kunden)

        assertEquals(CatalogForm.COLUMNS.columns, assertIs<QueryEvent.ResultStart>(events.first()).columns)
        assertEquals(
            listOf(
                listOf("Id", "bigint", 8, 19, 0, false, false, false, 1, null, null, null, null, null),
                listOf("Name", "nvarchar", 200, 0, 0, true, false, false, 2, null, null, null, null, null),
                listOf("Eintritt", "date", 3, 10, 0, true, false, false, 3, null, null, null, null, null),
            ),
            rows(events),
        )
    }

    /**
     * Happy: alle acht Typen von `SqlType` mit Typname und Maßen (Setzung nach dem Handbuch des Servers; gemessen ist
     * nur, dass d-migrate die Namen als `integer`, `biginteger`, `boolean`, `text`, `decimal`, `date`, `time` und
     * `datetime` liest). Das Schema `app` steht in der Anfrage als `app.typen`.
     * Wächter: eine Zuordnung, die zwei Typen auf denselben Namen legt.
     * @spec SPEC-036
     */
    @Test
    fun `jeder Typ von SqlType hat einen Typnamen im Katalog`() {
        val found = rows(ask(CatalogForm.COLUMNS, text("app.typen"), typen)).map { it.subList(0, 5) + it[5] }

        assertEquals(
            listOf(
                listOf("a", "int", 4, 10, 0, false),
                listOf("b", "bigint", 8, 19, 0, false),
                listOf("c", "bit", 1, 1, 0, true),
                listOf("d", "nvarchar", 8000, 0, 0, true),
                listOf("e", "decimal", 9, 10, 2, true),
                listOf("f", "date", 3, 10, 0, true),
                listOf("g", "time", 5, 16, 7, true),
                listOf("h", "datetime2", 8, 27, 7, true),
            ),
            found,
        )
    }

    /**
     * Happy: die Größe einer Dezimalzahl nach ihrer Precision (5, 9, 13 oder 17 Byte, Grenzen 9, 19, 28 und 38).
     * @spec SPEC-036
     */
    @Test
    fun `die Groesse einer Dezimalzahl folgt ihrer Precision`() {
        val expected = mapOf(1 to 5, 9 to 5, 10 to 9, 19 to 9, 20 to 13, 28 to 13, 29 to 17, 38 to 17)
        for ((precision, bytes) in expected) {
            val table = kunden.copy(columns = listOf(SimColumn("Id", SqlType.Decimal(precision, 0), nullable = false)))
            val row = rows(ask(CatalogForm.COLUMNS, text("kunden"), table)).single()
            assertEquals(bytes, row[2], "Precision $precision")
        }
    }

    /**
     * Happy: der Name in `OBJECT_ID(?)` darf `[dbo].[kunden]`, `dbo.kunden`, `[kunden]` oder `kunden` heißen, ohne
     * Beachtung der Schreibweise; `[a.b]` ist ein Teil. Negativ: eine unbekannte Tabelle, ein Name mit drei Teilen, ein
     * leerer Teil und ein Name mit offener Klammer liefern kein Ergebnis und keinen Fehler (der Server: `OBJECT_ID`
     * ergibt NULL).
     * Wächter: ein Parser, der nur die Form mit Klammern kennt, oder ein Fehler statt einer leeren Antwort.
     * @spec SPEC-036
     */
    @Test
    fun `OBJECT_ID nimmt qualifizierte und einfache Namen`() {
        for (name in listOf("[dbo].[kunden]", "dbo.kunden", "[kunden]", "kunden", "[DBO].[Kunden]")) {
            assertEquals(3, rows(ask(CatalogForm.COLUMNS, text(name), kunden)).size, name)
        }
        for (name in listOf("[dbo].[fremd]", "a.dbo.kunden", "dbo..kunden", "[kunden", "", "[a.b]")) {
            assertEquals(emptyList(), rows(ask(CatalogForm.COLUMNS, text(name), kunden)), name)
        }
        val dotted = kunden.copy(name = "a.b")
        assertEquals(3, rows(ask(CatalogForm.COLUMNS, text("[a.b]"), dotted)).size)
    }

    /**
     * Happy: der Schlüssel in der Reihenfolge der Beschreibung (`key_ordinal`), nicht der Spalten.
     * Negativ: ein anderer Parametertyp als Text ist `TDS_SIM_INVALID_VALUE` und liefert keine Zeile.
     * Wächter: die Reihenfolge der Spalten statt der des Schlüssels.
     * @spec SPEC-036
     */
    @Test
    fun `der Schluessel steht in seiner Reihenfolge`() {
        assertEquals(listOf(listOf("b"), listOf("a")), rows(ask(CatalogForm.PRIMARY_KEY, text("app.typen"), typen)))
        val number = QueryParameter("@P0", SqlType.Int32, 5)
        val failure = assertIs<QueryEvent.Failure>(ask(CatalogForm.PRIMARY_KEY, number, kunden).single())
        assertEquals(SimCodes.INVALID_VALUE, failure.code)
    }

    /**
     * Happy: alle Formen ohne Quelle im Katalog (Eindeutigkeiten, Fremdschlüssel, Indizes, Volltext, Partitionen,
     * Prüfbedingungen, Sequenzen, Sichten, Identität, Berechnung, Routinen) antworten mit ihren Spalten und ohne Zeile,
     * auch für eine Tabelle, die es gibt; `SELECT SCHEMA_NAME()` ist eine Zeile `dbo`.
     * Wächter: eine leere Form, die nur `ResultEnd(0)` sendet (die Sitzung schließt, `SPEC-036`), oder Spalten, die
     * von der Abfrage abweichen.
     * @spec SPEC-036
     */
    @Test
    fun `Formen ohne Quelle sind leer mit Spalten`() {
        val empty = CatalogForm.entries.filter { it.source == CatalogSource.EMPTY }
        assertEquals(16, empty.size)
        for (form in empty) {
            val events = ask(form, text("kunden"), kunden)
            assertEquals(
                listOf(QueryEvent.ResultStart(form.columns), QueryEvent.ResultEnd(0)),
                events,
                form.name,
            )
        }
        val schema = collectEvents(backend(), "SELECT SCHEMA_NAME() AS schema_name")
        assertEquals(listOf(listOf("dbo")), rows(schema))
        assertEquals(CatalogForm.SCHEMA_NAME.columns, assertIs<QueryEvent.ResultStart>(schema.first()).columns)
    }

    /**
     * Happy: der Anbieter wird bei jeder Anfrage gefragt; ändert er seinen Stand, ändert sich die Antwort. Der feste
     * Katalog (erster Konstruktor) zeigt den Stand der Übergabe.
     * Wächter: ein Backend, das den Katalog beim Bau liest (Gegenprobe c4 des Spikes: die Nachprüfung scheitert).
     * @spec SPEC-035
     */
    @Test
    fun `der Anbieter wird bei jeder Anfrage gefragt`() {
        var current = SimCatalog(listOf(kunden))
        val provider = SimulationBackend({ current })
        val sql = CatalogForm.TABLES.template.replace("?", " @P0 ")

        assertEquals(1, rows(collectEvents(provider, sql, listOf(text("dbo")))).size)
        current = SimCatalog(listOf(kunden, kunden.copy(name = "neu")))
        assertEquals(2, rows(collectEvents(provider, sql, listOf(text("dbo")))).size)

        val fixed = backend(kunden)
        assertEquals(1, rows(collectEvents(fixed, sql, listOf(text("dbo")))).size)
    }

    /**
     * Negativ: wirft der Anbieter, endet die Anfrage mit `TDS_BACKEND_ERROR` und fester Meldung, `execute` wirft nicht.
     * Wächter: ein Backend, das die Ausnahme des Anbieters weiterwirft (die Sitzung bekäme keinen Fehler).
     * @spec SPEC-035
     */
    @Test
    fun `ein werfender Anbieter ist ein Fehler des Backends`() {
        val backend = SimulationBackend({ error("Geheimnis des Anbieters") })
        val events = collectEvents(backend, "SELECT SCHEMA_NAME() AS schema_name")
        val failure = assertIs<QueryEvent.Failure>(events.single())
        assertEquals(SimCodes.BACKEND_ERROR, failure.code)
        assertTrue("Geheimnis" !in failure.message)
    }

    /**
     * Wertebereich von `SimColumn.maxLength`: 1, 4000 und -1 (`nvarchar(max)`) gelten, 0, -2 und 4001 nicht; die
     * Voreinstellung ist 4000. Ein Text mit -1 meldet der Katalog mit `max_length` -1, nicht mit der doppelten Länge.
     * @spec SPEC-035
     */
    @Test
    fun `maxLength liegt zwischen 1 und 4000`() {
        assertEquals(4000, SimColumn("x", SqlType.UnicodeText).maxLength)
        assertEquals(1, SimColumn("x", SqlType.UnicodeText, maxLength = 1).maxLength)
        assertEquals(4000, SimColumn("x", SqlType.UnicodeText, maxLength = 4000).maxLength)
        assertEquals(-1, SimColumn("x", SqlType.UnicodeText, maxLength = -1).maxLength)
        val unbounded = kunden.copy(columns = listOf(SimColumn("Id", SqlType.UnicodeText, false, maxLength = -1)))
        val row = rows(ask(CatalogForm.COLUMNS, text("kunden"), unbounded)).single()
        assertEquals(listOf("nvarchar", -1), row.subList(1, 3))
        for (bad in listOf(0, -2, 4001)) {
            val error = runCatching { SimColumn("x", SqlType.UnicodeText, maxLength = bad) }.exceptionOrNull()
            assertIs<IllegalArgumentException>(error, "maxLength $bad")
        }
    }
}
