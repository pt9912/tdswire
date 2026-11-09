package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Antworten auf die Katalogabfragen für Standardwerte, Eindeutigkeiten, Fremdschlüssel, Indizes und
 * Prüfbedingungen (`SPEC-036`, Form K; `SPEC-035`, Katalog der Elemente). Die erwarteten Zeilen sind die Zeilen des
 * echten Servers aus der Messung M-21 (Referenzabgleich, `katalog-abfragen/18` bis `/33`, Instanz 2022 und 2025,
 * Tabellen `katalog_kunde` und `katalog_bestellung`); die Typen und Nullbarkeiten der Antwortspalten sind die der
 * Aufnahme (`katalog-abfragen` beider Instanzen). Mehrspaltige Elemente, Reihenfolge mehrerer Elemente und die
 * Schreibweise der Namen sind Setzung (nicht am Server gemessen).
 */
class SimulationBackendCatalogElementsTest {
    private val rowless: suspend (SimQuery) -> RowSource = { RowSource { null } }

    private val kunde = SimTable(
        name = "katalog_kunde",
        columns = listOf(
            SimColumn("Id", SqlType.Int32, nullable = false),
            SimColumn("Email", SqlType.UnicodeText, maxLength = 200),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
        uniqueKeys = listOf(SimUniqueKey("uq_katalog_kunde_email", listOf("Email"))),
    )

    private val bestellung = SimTable(
        name = "katalog_bestellung",
        columns = listOf(
            SimColumn("Id", SqlType.Int32, nullable = false),
            SimColumn("KundeId", SqlType.Int32, nullable = false),
            SimColumn("Menge", SqlType.Int32, nullable = false, defaultDefinition = "((1))"),
            SimColumn("Status", SqlType.UnicodeText, nullable = false, maxLength = 20, defaultDefinition = "(N'neu')"),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
        foreignKeys = listOf(
            SimForeignKey(
                "fk_bestellung_kunde",
                listOf("KundeId"),
                "katalog_kunde",
                listOf("Id"),
                onDelete = "CASCADE",
            ),
        ),
        indexes = listOf(SimIndex("ix_bestellung_status", listOf("Status"), included = listOf("Menge"))),
        checks = listOf(SimCheck("ck_bestellung_menge", "([Menge]>(0))")),
    )

    private fun text(value: String?) = QueryParameter("@P0", SqlType.UnicodeText, value)

    private fun rows(events: List<QueryEvent>): List<List<Any?>> =
        events.filterIsInstance<QueryEvent.Row>().map { it.values }

    private fun ask(form: CatalogForm, table: String?, vararg tables: SimTable): List<QueryEvent> = collectEvents(
        SimulationBackend(SimCatalog(tables.toList())),
        form.template.replace("?", " @P0 "),
        listOf(text(table)),
    )

    private fun answer(form: CatalogForm, table: String?, vararg tables: SimTable) = rows(ask(form, table, *tables))

    /**
     * Happy (M-21): der Standardwert steht in `default_definition` mit den Klammern des Servers; eine Spalte ohne
     * Standardwert meldet NULL. Die Zeilen sind die gemessenen (`((1))` für `DEFAULT 1`, `(N'neu')` für
     * `DEFAULT N'neu'`).
     * Wächter: ein Standardwert ohne Klammern oder ein Katalog, der `default_definition` weiter leer lässt.
     * @spec SPEC-036
     */
    @Test
    fun `die Spalten melden ihren Standardwert in der Form des Servers`() {
        val found = answer(CatalogForm.COLUMNS, "[dbo].[katalog_bestellung]", bestellung)

        assertEquals(
            listOf(
                listOf("Id", "int", 4, 10, 0, false, false, false, 1, null, null, null, null, null),
                listOf("KundeId", "int", 4, 10, 0, false, false, false, 2, null, null, null, null, null),
                listOf("Menge", "int", 4, 10, 0, false, false, false, 3, "((1))", null, null, null, null),
                listOf("Status", "nvarchar", 40, 0, 0, false, false, false, 4, "(N'neu')", null, null, null, null),
            ),
            found,
        )
    }

    /**
     * Happy (M-21): der Fremdschlüssel erscheint mit Name, Spalte, Zieltabelle, Zielspalte, den Aktionen und der
     * Nummer der Spalte ab 1: `fk_bestellung_kunde`, `KundeId`, `katalog_kunde`, `Id`, `CASCADE`, `NO_ACTION`, 1.
     * Mehrspaltig (Setzung): eine Zeile je Spalte, in der Reihenfolge des Schlüssels, Nummer 1, 2; mehrere
     * Fremdschlüssel nach Namen. Die Zieltabelle steht in der Schreibweise der Beschreibung.
     * Wächter: eine Nummer ab 0, eine Zeile je Schlüssel statt je Spalte, vertauschte Aktionen, eine leere Antwort.
     * @spec SPEC-036
     */
    @Test
    fun `Fremdschluessel kommen aus der Tabelle`() {
        assertEquals(
            listOf(listOf("fk_bestellung_kunde", "KundeId", "katalog_kunde", "Id", "CASCADE", "NO_ACTION", 1)),
            answer(CatalogForm.FOREIGN_KEYS, "[dbo].[katalog_bestellung]", kunde, bestellung),
        )
        val parent = SimTable(
            name = "Eltern",
            columns = listOf(SimColumn("A", SqlType.Int32, false), SimColumn("B", SqlType.Int32, false)),
            primaryKey = listOf("A", "B"),
            rows = rowless,
        )
        val child = SimTable(
            name = "kind",
            columns = listOf(
                SimColumn("x", SqlType.Int32),
                SimColumn("y", SqlType.Int32),
                SimColumn("z", SqlType.Int32),
            ),
            primaryKey = emptyList(),
            rows = rowless,
            foreignKeys = listOf(
                SimForeignKey("z_fk", listOf("z"), "eltern", listOf("a"), "SET_NULL", "SET_DEFAULT"),
                SimForeignKey("a_fk", listOf("y", "x"), "ELTERN", listOf("b", "a")),
            ),
        )
        assertEquals(
            listOf(
                listOf("a_fk", "y", "Eltern", "B", "NO_ACTION", "NO_ACTION", 1),
                listOf("a_fk", "x", "Eltern", "A", "NO_ACTION", "NO_ACTION", 2),
                listOf("z_fk", "z", "Eltern", "A", "SET_NULL", "SET_DEFAULT", 1),
            ),
            answer(CatalogForm.FOREIGN_KEYS, "kind", parent, child),
        )
    }

    /**
     * Happy (M-21): der Index ohne Primärschlüssel mit einer Schlüsselspalte und einer eingeschlossenen Spalte
     * liefert zwei Zeilen: erst die eingeschlossene (`Menge`, `key_ordinal` 0, `is_included_column` 1), dann die
     * Schlüsselspalte (`Status`, `key_ordinal` 1); `is_unique` 0, `has_filter` 0, Filter NULL, `type` 2.
     * Der Eindeutigkeitsschlüssel erscheint in der Indexabfrage mit `is_unique` 1 und in der Abfrage der
     * Eindeutigkeiten; der Primärschlüssel erscheint in der Indexabfrage nicht (M-21: `uq_katalog_kunde_email`).
     * Mehrere Indizes ordnet die Antwort nach Namen (Setzung), die Spalten eines Index nach `key_ordinal`.
     * Wächter: ein Primärschlüssel in der Indexabfrage, die eingeschlossene Spalte hinter der Schlüsselspalte, ein
     * Eindeutigkeitsschlüssel, der in einer der beiden Abfragen fehlt.
     * @spec SPEC-036
     */
    @Test
    fun `Indizes und Eindeutigkeiten kommen aus der Tabelle`() {
        assertEquals(
            listOf(
                listOf("ix_bestellung_status", false, false, null, 2, "Menge", 0, false, true),
                listOf("ix_bestellung_status", false, false, null, 2, "Status", 1, false, false),
            ),
            answer(CatalogForm.INDEXES, "[dbo].[katalog_bestellung]", bestellung),
        )
        assertEquals(
            listOf(listOf("uq_katalog_kunde_email", true, false, null, 2, "Email", 1, false, false)),
            answer(CatalogForm.INDEXES, "[dbo].[katalog_kunde]", kunde),
        )
        assertEquals(
            listOf(listOf("uq_katalog_kunde_email", "Email")),
            answer(CatalogForm.UNIQUE_KEYS, "[dbo].[katalog_kunde]", kunde),
        )
        assertEquals(emptyList(), answer(CatalogForm.UNIQUE_KEYS, "[dbo].[katalog_bestellung]", bestellung))
        val many = bestellung.copy(
            indexes = listOf(
                SimIndex("z_index", listOf("Menge", "KundeId"), unique = true),
                SimIndex("A_index", listOf("Status")),
            ),
            uniqueKeys = listOf(SimUniqueKey("m_unique", listOf("kundeid", "STATUS"))),
        )
        assertEquals(
            listOf(
                listOf("A_index", false, false, null, 2, "Status", 1, false, false),
                listOf("m_unique", true, false, null, 2, "KundeId", 1, false, false),
                listOf("m_unique", true, false, null, 2, "Status", 2, false, false),
                listOf("z_index", true, false, null, 2, "Menge", 1, false, false),
                listOf("z_index", true, false, null, 2, "KundeId", 2, false, false),
            ),
            answer(CatalogForm.INDEXES, "katalog_bestellung", many),
        )
        assertEquals(
            listOf(listOf("m_unique", "KundeId"), listOf("m_unique", "Status")),
            answer(CatalogForm.UNIQUE_KEYS, "katalog_bestellung", many),
        )
    }

    /**
     * Happy (M-21): die Prüfbedingung steht mit Name und Definition: `ck_bestellung_menge`, `([Menge]>(0))`; mehrere
     * nach Namen (Setzung).
     * Wächter: eine Definition, die der Katalog umschreibt, oder eine leere Antwort.
     * @spec SPEC-036
     */
    @Test
    fun `Pruefbedingungen kommen aus der Tabelle`() {
        assertEquals(
            listOf(listOf("ck_bestellung_menge", "([Menge]>(0))")),
            answer(CatalogForm.CHECKS, "[dbo].[katalog_bestellung]", bestellung),
        )
        val two = bestellung.copy(checks = listOf(SimCheck("b", "([Id]>(1))"), SimCheck("A", "([Id]<(9))")))
        assertEquals(
            listOf(listOf("A", "([Id]<(9))"), listOf("b", "([Id]>(1))")),
            answer(CatalogForm.CHECKS, "katalog_bestellung", two),
        )
    }

    /**
     * Happy: eine unbekannte Tabelle, ein unbekanntes Schema, ein NULL-Parameter und eine Tabelle ohne Elemente
     * liefern keine Zeile, aber die Spalten der Abfrage (`OBJECT_ID` des Servers liefert NULL); die Abfrage der
     * Partitionen, des Volltexts und der Sequenzen bleibt leer, auch für eine Tabelle mit Elementen.
     * Wächter: ein Fehler statt der leeren Antwort (die Sitzung schlösse) oder Zeilen einer fremden Tabelle.
     * @spec SPEC-036
     */
    @Test
    fun `ohne Tabelle oder Element ist die Antwort leer mit Spalten`() {
        val elementForms = listOf(
            CatalogForm.UNIQUE_KEYS,
            CatalogForm.FOREIGN_KEYS,
            CatalogForm.INDEXES,
            CatalogForm.CHECKS,
        )
        for (form in elementForms) {
            for (table in listOf("gibt_es_nicht", "[fremd].[katalog_bestellung]", null)) {
                val events = ask(form, table, kunde, bestellung)
                assertEquals(
                    listOf(QueryEvent.ResultStart(form.columns), QueryEvent.ResultEnd(0)),
                    events,
                    "${form.name} $table",
                )
            }
        }
        val plain = kunde.copy(uniqueKeys = emptyList())
        assertEquals(emptyList(), answer(CatalogForm.INDEXES, "katalog_kunde", plain))
        assertEquals(emptyList(), answer(CatalogForm.PARTITIONING, "katalog_bestellung", bestellung))
        assertEquals(emptyList(), answer(CatalogForm.FULLTEXT_COLUMNS, "katalog_bestellung", bestellung))
    }

    /**
     * Die Typen und Nullbarkeiten der Antwortspalten, wie die Aufnahme `katalog-abfragen` sie zeigt (Instanz 2022 und
     * 2025 gleich): `column_name` nullbar (`BITN`-Flag der Messung, Nachtrag der Verifikation zu M-16), Aktionen und
     * `referenced_column` nullbar, `type` und `key_ordinal` `tinyint` nicht nullbar, die `bit`-Spalten der Indexabfrage
     * nullbar, `definition` nullbar. Text steht als `UnicodeText` (benannte Grenze: der Server sendet `nvarchar(128)`
     * und `nvarchar(max)`).
     * Wächter: `int` statt `tinyint` bei `type`, eine nicht nullbare `column_name`.
     * @spec SPEC-036
     */
    @Test
    fun `die Antwortspalten tragen Typ und Nullbarkeit der Messung`() {
        fun text(name: String, nullable: Boolean = false) = ResultColumn(name, SqlType.UnicodeText, nullable)
        fun bit(name: String) = ResultColumn(name, SqlType.Bool, true)
        assertEquals(listOf(text("column_name", true)), CatalogForm.PRIMARY_KEY.columns)
        assertEquals(listOf(text("constraint_name"), text("column_name", true)), CatalogForm.UNIQUE_KEYS.columns)
        assertEquals(
            listOf(
                text("constraint_name"),
                text("column_name", true),
                text("referenced_table"),
                text("referenced_column", true),
                text("delete_referential_action_desc", true),
                text("update_referential_action_desc", true),
                ResultColumn("constraint_column_id", SqlType.Int32, false),
            ),
            CatalogForm.FOREIGN_KEYS.columns,
        )
        assertEquals(
            listOf(
                text("index_name", true),
                bit("is_unique"),
                bit("has_filter"),
                text("filter_definition", true),
                ResultColumn("type", SqlType.UInt8, false),
                text("column_name", true),
                ResultColumn("key_ordinal", SqlType.UInt8, false),
                bit("is_descending_key"),
                bit("is_included_column"),
            ),
            CatalogForm.INDEXES.columns,
        )
        assertEquals(listOf(text("constraint_name"), text("definition", true)), CatalogForm.CHECKS.columns)
        assertEquals(text("column_name", true), CatalogForm.COLUMNS.columns.first())
    }
}
