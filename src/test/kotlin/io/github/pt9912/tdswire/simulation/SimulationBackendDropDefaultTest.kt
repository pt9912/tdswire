package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Form D7 (der Text mit `DECLARE @df …`, `SPEC-036`). Gemessen am echten Server (2022 und 2025, Messprotokoll
 * `docs/messungen/standardwert-ersetzen-d-migrate-2026-11-12.md`, Szenario `struktur-standardwert-ersetzen`): der Text
 * entfernt den Standardwert der Spalte, auch den mit eigenem Namen, ohne den Namen zu kennen; ohne Standardwert, an
 * einer unbekannten Spalte und an einer unbekannten Tabelle ist er ein Erfolg ohne Änderung; Tabelle und Spalte gelten
 * ohne Beachtung der Schreibweise; andere Spalten und andere Tabellen bleiben. Die Antwort der Bibliothek ist ein
 * Abschluss der Art `ALTER_TABLE` (`0x00d8`, ohne Zähler); der Server sendet die Folge der Anweisungen des Textes
 * (Grenze, `SPEC-036`). Der Wortlaut von d-migrate steht in [dMigrateDeclareDefaultText].
 */
class SimulationBackendDropDefaultTest {
    private val rowless: suspend (SimQuery) -> RowSource = { RowSource { null } }

    private class Store(var tables: List<SimTable>) {
        val shapes = mutableListOf<SimTable>()

        val backend = SimulationBackend({ SimCatalog(tables) }) { shape ->
            shapes += shape
            tables = tables.filterNot { it.schema == shape.schema && it.name == shape.name } + shape
        }

        fun table(name: String): SimTable = tables.single { it.name == name }
    }

    private val bestellung = SimTable(
        name = "bestellung",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Menge", SqlType.Int32, defaultDefinition = "((1))"),
            SimColumn("Status", SqlType.UnicodeText, maxLength = 20, defaultDefinition = "(N'neu')"),
            SimColumn("Ohne", SqlType.Int32),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
        indexes = listOf(SimIndex("ix_bestellung_status", listOf("Status"))),
        checks = listOf(SimCheck("ck_bestellung_menge", "\"Menge\">(0)")),
    )

    private val kunden = SimTable(
        name = "kunden",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Menge", SqlType.Int32, defaultDefinition = "((5))"),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
    )

    private fun store() = Store(listOf(bestellung, kunden))

    private fun run(store: Store, sql: String): List<QueryEvent> = collectEvents(store.backend, sql)

    private fun kind(events: List<QueryEvent>): StatementKind = assertIs<QueryEvent.UpdateCount>(events.single()).kind

    private fun failureCode(store: Store, sql: String): String =
        assertIs<QueryEvent.Failure>(run(store, sql).single(), sql).code

    private fun SimTable.defaults(): Map<String, String?> = columns.associate { it.name to it.defaultDefinition }

    private fun expectedDefaults(menge: String?, status: String?): Map<String, String?> =
        mapOf("Id" to null, "Menge" to menge, "Status" to status, "Ohne" to null)

    /**
     * Happy (Wortlaut von d-migrate): der Text nimmt den Standardwert der Spalte; die übrigen Standardwerte, Spalten,
     * Indizes und Prüfbedingungen und die andere Tabelle bleiben; der Abschluss ist die Art `ALTER_TABLE`. Die Folge
     * mit dem `ADD CONSTRAINT … DEFAULT … FOR` desselben Textes (Schritt s12 von d-migrate) endet mit dem neuen
     * Standardwert; ohne den ersten Text ist der zweite Standardwert derselben Spalte abgewiesen (Gegenprobe).
     * Wächter: eine Planung, die die Spalte ignoriert (der Standardwert bleibt), eine, die alle Standardwerte der
     * Tabelle oder den der gleichnamigen Spalte der anderen Tabelle entfernt.
     * @spec SPEC-036
     */
    @Test
    fun `der Text von d-migrate nimmt den Standardwert der Spalte aus der Tabelle`() {
        val store = store()
        val add = "ALTER TABLE [bestellung] ADD CONSTRAINT [df_bestellung_Menge] DEFAULT 2 FOR [Menge];"
        assertEquals(SimCodes.COLUMN_HAS_DEFAULT, failureCode(store, add), "Gegenprobe: Standardwert schon da")

        assertEquals(StatementKind.ALTER_TABLE, kind(run(store, dMigrateDeclareDefaultText("bestellung", "Menge"))))

        assertEquals(1, store.shapes.size)
        val changed = store.table("bestellung")
        assertEquals(expectedDefaults(menge = null, status = "(N'neu')"), changed.defaults())
        assertEquals(
            bestellung.columns.map { if (it.name == "Menge") it.copy(defaultDefinition = null) else it },
            changed.columns,
        )
        assertEquals(bestellung.indexes, changed.indexes)
        assertEquals(bestellung.checks, changed.checks)
        assertEquals(bestellung.primaryKey, changed.primaryKey)
        assertEquals(kunden, store.table("kunden"))

        assertEquals(StatementKind.ALTER_TABLE, kind(run(store, add)))
        assertEquals("((2))", store.table("bestellung").column("Menge")?.defaultDefinition)
    }

    /**
     * Happy (gemessen, Zeilen 12 und 13 des Szenarios): Tabelle und Spalte gelten ohne Beachtung der Schreibweise
     * (Kollation der Datenbank ist die Voreinstellung der Instanz, nicht eigens erhoben).
     * Wächter: ein Vergleich mit Beachtung der Schreibweise.
     * @spec SPEC-036
     */
    @Test
    fun `Tabelle und Spalte gelten ohne Beachtung der Schreibweise`() {
        val store = store()
        run(store, dMigrateDeclareDefaultText("BESTELLUNG", "STATUS"))
        assertEquals(expectedDefaults(menge = "((1))", status = null), store.table("bestellung").defaults())
    }

    /**
     * Happy (gemessen, Zeilen 10, 11, 14 und 15): ohne Standardwert an der Spalte und an einer unbekannten Spalte
     * ändert der Text nichts und gelingt; der Callback bekommt die unveränderte Tabelle. An einer unbekannten Tabelle
     * gelingt er ebenfalls, dann läuft kein Callback.
     * Wächter: eine Planung, die diese Fälle ablehnt, eine, die den Callback an der unbekannten Tabelle ruft, eine, die
     * bei der unbekannten Spalte einen Standardwert verliert.
     * @spec SPEC-036
     */
    @Test
    fun `ohne Standardwert und an unbekannter Spalte oder Tabelle gelingt der Text ohne Änderung`() {
        val store = store()
        assertEquals(StatementKind.ALTER_TABLE, kind(run(store, dMigrateDeclareDefaultText("bestellung", "Ohne"))))
        val unknownColumn = dMigrateDeclareDefaultText("bestellung", "Gibtesnicht")
        assertEquals(StatementKind.ALTER_TABLE, kind(run(store, unknownColumn)))
        assertEquals(listOf(bestellung, bestellung), store.shapes.map { it.copy(rows = rowless) })
        assertEquals(bestellung.defaults(), store.table("bestellung").defaults())

        val before = store.shapes.size
        assertEquals(StatementKind.ALTER_TABLE, kind(run(store, dMigrateDeclareDefaultText("gibtesnicht", "Menge"))))
        assertEquals(before, store.shapes.size, "unbekannte Tabelle: kein Callback")
        assertEquals(kunden, store.table("kunden"))
    }

    /**
     * Ein zweiter Lauf des Textes nach dem Entfernen ist ein Erfolg ohne Änderung (gemessen, Zeile 11 des Szenarios).
     * Wächter: eine Planung, die den fehlenden Standardwert als Fehler meldet.
     * @spec SPEC-036
     */
    @Test
    fun `der Text ein zweites Mal ist ein Erfolg ohne Änderung`() {
        val store = store()
        run(store, dMigrateDeclareDefaultText("bestellung", "Menge"))
        val once = store.table("bestellung")
        assertEquals(StatementKind.ALTER_TABLE, kind(run(store, dMigrateDeclareDefaultText("bestellung", "Menge"))))
        assertEquals(once.defaults(), store.table("bestellung").defaults())
    }

    /**
     * Der Text ist als Form D7 erkannt: Tabelle und Spalte; der Wortlaut des Mitschnitts hat die Länge 419, mit
     * Wagenrücklauf und Tabulator als Leerraum, ohne abschließendes `;` und mit Leerraum am Ende ebenso; `''` in der
     * Spalte steht für `'`.
     * Wächter: eine Erkennung, die die Namen vertauscht oder verliert, eine, die den Leerraum starr verlangt.
     * @spec SPEC-036
     */
    @Test
    fun `die Form D7 wird mit Tabelle und Spalte erkannt`() {
        val text = dMigrateDeclareDefaultText("auftraege", "Menge")
        assertEquals(D_MIGRATE_DECLARE_DEFAULT_LENGTH, text.length)
        val expected = Statement.DropDefault(tableRef("auftraege"), "Menge")
        assertRecognized(expected, text)
        assertRecognized(expected, text.replace("\n", "\r\n"))
        assertRecognized(expected, text.replace("    ", "\t").replace(" FROM", "  FROM"))
        assertRecognized(expected, text.removeSuffix(";"))
        assertRecognized(expected, "\n  $text \r\n")
        assertRecognized(
            Statement.DropDefault(tableRef("Tab_1#\$ä"), "o'k  x"),
            dMigrateDeclareDefaultText("Tab_1#\$ä", "o''k  x"),
        )
    }

    /**
     * Was vom Wortlaut abweicht, ist nicht erkannt: die Tabelle an den beiden Stellen verschieden, ein Schema, ein
     * Name mit Klammer, Hochkomma, Punkt oder Leerzeichen, ein zweiter Text dahinter, ein fehlender oder anderer
     * Teil, Kleinschreibung der Schlüsselwörter (Setzung: der Vergleich ist ein Textvergleich).
     * Wächter: eine Erkennung, die nur den Anfang oder nur die Länge prüft, eine, die die Tabelle nicht an beiden
     * Stellen vergleicht.
     * @spec SPEC-036
     */
    @Test
    fun `eine Abweichung vom Wortlaut ist nicht erkannt`() {
        val text = dMigrateDeclareDefaultText("auftraege", "Menge")
        assertOnlyExtensionFails(
            text,
            text.replace("ALTER TABLE [auftraege]", "ALTER TABLE [anderer]"),
            text.replace("OBJECT_ID('auftraege')", "OBJECT_ID('dbo.auftraege')"),
            dMigrateDeclareDefaultText("dbo.auftraege", "Menge"),
            dMigrateDeclareDefaultText("auf'traege", "Menge"),
            dMigrateDeclareDefaultText("auf traege", "Menge"),
            dMigrateDeclareDefaultText("auf]traege", "Menge"),
            dMigrateDeclareDefaultText("auftraege", "Me'nge"),
            "$text DROP TABLE [auftraege]",
            "$text $text",
            text.replace("IF @df IS NOT NULL", "IF @df IS NULL"),
            text.replace("QUOTENAME(@df)", "@df"),
            text.replace("EXEC sp_executesql @sql;", "PRINT @sql;"),
            text.replace("c.name = ", "c.name <> "),
            text.replace("DECLARE", "declare"),
            text.replace("sysname", "nvarchar(128)"),
            text.removePrefix("DECLARE @df sysname, @sql nvarchar(max);"),
            text.removeSuffix("END;"),
            text.replace("'Menge'", "N'Menge'"),
            text.replace("'Menge'", "[Menge]"),
            "",
        )
    }

    /**
     * Der Text hat keine Länge, bei der die Erkennung versagt: ein Spaltenname von 100 000 Zeichen wird ohne
     * Rekursion je Zeichen erkannt und beantwortet (der Server begrenzt Namen auf 128 Zeichen, nicht gemessen).
     * Wächter: eine Erkennung mit Rekursion je Zeichen (Stapelüberlauf).
     * @spec SPEC-036
     */
    @Test
    fun `ein Name von 100000 Zeichen ist erkannt und beantwortet`() {
        val long = "a".repeat(LONG_NAME)
        val longColumn = dMigrateDeclareDefaultText("bestellung", long)
        assertRecognized(Statement.DropDefault(tableRef("bestellung"), long), longColumn)
        assertRecognized(Statement.DropDefault(tableRef(long), "Menge"), dMigrateDeclareDefaultText(long, "Menge"))

        val store = store()
        assertEquals(StatementKind.ALTER_TABLE, kind(run(store, longColumn)))
        assertEquals(bestellung.defaults(), store.table("bestellung").defaults())
    }

    /**
     * Ohne Strukturänderungs-Callback ist der Text an einer bekannten Tabelle abgewiesen wie jede Strukturänderung
     * (`TDS_UNSUPPORTED_SQL`); an einer unbekannten Tabelle ist er dagegen ein Erfolg ohne Callback.
     * Wächter: eine Planung, die den Text an einer bekannten Tabelle ohne Callback annimmt.
     * @spec SPEC-036
     */
    @Test
    fun `ohne Callback ist der Text an einer bekannten Tabelle abgewiesen`() {
        val backend = SimulationBackend(SimCatalog(listOf(bestellung)))
        val events = collectEvents(backend, dMigrateDeclareDefaultText("bestellung", "Menge"))
        assertEquals(SimCodes.UNSUPPORTED_SQL, assertIs<QueryEvent.Failure>(events.single()).code)
        assertEquals(
            StatementKind.ALTER_TABLE,
            kind(collectEvents(backend, dMigrateDeclareDefaultText("gibtesnicht", "Menge"))),
        )
    }

    private companion object {
        const val LONG_NAME = 100_000
    }
}
