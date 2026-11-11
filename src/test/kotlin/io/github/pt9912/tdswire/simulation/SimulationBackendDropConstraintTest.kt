package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Form D5 (`ALTER TABLE … DROP CONSTRAINT [IF EXISTS] <Name>`, `SPEC-036`). Gemessen am echten Server (2022 und 2025,
 * Messprotokoll `docs/messungen/drop-constraint-d-migrate-2026-11-11.md`, Szenario `struktur-drop-constraint`): mit
 * Treffer und mit `IF EXISTS` ohne Treffer antwortet er mit DONE ohne Zähler und dem Befehl `0x00d8`; `IF EXISTS` ohne
 * Treffer ändert nichts, auch bei einem Namen, der zu einer anderen Tabelle gehört; ohne `IF EXISTS` ist ein Name ohne
 * Treffer ERROR 3728 (State 1), der einer anderen Tabelle 3733 (State 2); eine unbekannte Tabelle ist 4902 mit und
 * ohne `IF EXISTS`; der Name gilt ohne Beachtung der Schreibweise. Der Wortlaut von d-migrate steht in
 * [dMigrateDropTexts]. Setzung (nicht gemessen): der Server führt Namen je Schema, die Bibliothek je Tabelle; der Name
 * eines Standardwerts oder Schlüssels ist im Modell nicht geführt und trifft deshalb nicht.
 */
class SimulationBackendDropConstraintTest {
    private val rowless: suspend (SimQuery) -> RowSource = { RowSource { null } }

    private class Store(var tables: List<SimTable>) {
        val shapes = mutableListOf<SimTable>()

        val backend = SimulationBackend({ SimCatalog(tables) }) { shape ->
            shapes += shape
            tables = tables.filterNot { it.schema == shape.schema && it.name == shape.name } + shape
        }

        fun table(name: String): SimTable = tables.single { it.name == name }
    }

    private val kunden = SimTable(
        name = "kunden",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Email", SqlType.UnicodeText, maxLength = 200, defaultDefinition = "(N'x')"),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
        uniqueKeys = listOf(SimUniqueKey("uq_kunden_Email", listOf("Email"))),
        indexes = listOf(SimIndex("ix_kunden_email", listOf("Email"))),
    )

    private val bestellung = SimTable(
        name = "bestellung",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("KundeId", SqlType.Int64, nullable = false),
            SimColumn("Menge", SqlType.Int32, nullable = false),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
        foreignKeys = listOf(SimForeignKey("fk_bestellung_KundeId", listOf("KundeId"), "kunden", listOf("Id"))),
        checks = listOf(SimCheck("ck_bestellung_menge", "([Menge]>(0))")),
    )

    private fun store() = Store(listOf(kunden, bestellung))

    private fun run(store: Store, sql: String): List<QueryEvent> = collectEvents(store.backend, sql)

    private fun kind(events: List<QueryEvent>): StatementKind = assertIs<QueryEvent.UpdateCount>(events.single()).kind

    private fun failureCode(store: Store, sql: String): String =
        assertIs<QueryEvent.Failure>(run(store, sql).single(), sql).code

    private fun SimTable.names() = uniqueKeys.map { it.name } + foreignKeys.map { it.name } + checks.map { it.name }

    /**
     * Happy (Wortlaut von d-migrate): die drei Texte aus [dMigrateDropTexts] nehmen je ihre Bedingung aus der Tabelle;
     * der Abschluss ist `ALTER_TABLE` (DONE, Befehl `0x00d8`); Spalten, Schlüssel und Indizes bleiben.
     * Wächter: eine Planung, die den Namen ignoriert (alle drei bleiben), eine, die mehr entfernt als den einen Namen,
     * eine falsche Art des Abschlusses.
     * @spec SPEC-036
     */
    @Test
    fun `die Texte von d-migrate nehmen die Bedingung aus der Tabelle`() {
        val store = store()
        for (text in dMigrateDropTexts) assertEquals(StatementKind.ALTER_TABLE, kind(run(store, text)), text)

        assertEquals(3, store.shapes.size)
        val orders = store.table("bestellung")
        assertEquals(emptyList(), orders.foreignKeys)
        assertEquals(emptyList(), orders.checks)
        assertEquals(bestellung.columns, orders.columns)
        val customers = store.table("kunden")
        assertEquals(emptyList(), customers.uniqueKeys)
        assertEquals(kunden.indexes, customers.indexes)
        assertEquals(kunden.columns, customers.columns)
        assertEquals(kunden.primaryKey, customers.primaryKey)
    }

    /**
     * Happy: der Name gilt ohne Beachtung der Schreibweise (gemessen: `[CK_DROP_MENGE]` trifft `[ck_drop_menge]`), mit
     * und ohne `IF EXISTS`, mit Schema und ohne `;`; genau die eine Bedingung fällt, die andere bleibt. Die Folge
     * `DROP` und `ADD` derselben Prüfbedingung (die Neuplanung von d-migrate) endet mit der Bedingung in der Tabelle.
     * Wächter: ein Vergleich mit Beachtung der Schreibweise, ein `DROP`, das beide Bedingungen entfernt.
     * @spec SPEC-036
     */
    @Test
    fun `der Name gilt ohne Beachtung der Schreibweise und die Neuplanung endet mit der Bedingung`() {
        val store = store()
        run(store, "ALTER TABLE dbo.bestellung DROP CONSTRAINT [CK_BESTELLUNG_MENGE]")
        assertEquals(listOf("fk_bestellung_KundeId"), store.table("bestellung").names())

        run(store, "alter table [bestellung] drop constraint if exists FK_BESTELLUNG_KUNDEID;")
        assertEquals(emptyList(), store.table("bestellung").names())
        run(store, "ALTER TABLE kunden DROP CONSTRAINT UQ_KUNDEN_EMAIL")
        assertEquals(emptyList(), store.table("kunden").uniqueKeys)

        val replan = store()
        run(replan, dMigrateDropTexts[0])
        run(replan, "ALTER TABLE [bestellung] WITH CHECK ADD CONSTRAINT [ck_bestellung_menge] CHECK (Menge > 0);")
        assertEquals(bestellung.checks, replan.table("bestellung").checks)
        assertEquals(bestellung.foreignKeys, replan.table("bestellung").foreignKeys)
    }

    /**
     * Happy (gemessen, Zeilen 06, 07, 11, 14, 17, 18): `IF EXISTS` ohne Treffer ist ein Erfolg mit dem Abschluss
     * `ALTER_TABLE`, auch bei einem Namen, der nie vorhanden war, und bei dem einer Bedingung einer anderen Tabelle;
     * die Tabelle bleibt unverändert (der Callback bekommt dieselbe Beschreibung).
     * Wächter: ein Fehler statt des Erfolgs, ein Name der anderen Tabelle, der doch fällt.
     * @spec SPEC-036
     */
    @Test
    fun `IF EXISTS ohne Treffer ist ein Erfolg ohne Aenderung`() {
        val store = store()
        val texts = listOf(
            "ALTER TABLE [bestellung] DROP CONSTRAINT IF EXISTS [gibtesnicht]",
            "ALTER TABLE [bestellung] DROP CONSTRAINT IF EXISTS [uq_kunden_Email]",
            "ALTER TABLE [kunden] DROP CONSTRAINT IF EXISTS [ck_bestellung_menge]",
        )
        for (text in texts) assertEquals(StatementKind.ALTER_TABLE, kind(run(store, text)), text)

        assertEquals(3, store.shapes.size)
        assertEquals(kunden.names(), store.table("kunden").names())
        assertEquals(bestellung.names(), store.table("bestellung").names())
        assertEquals(bestellung.checks, store.table("bestellung").checks)
        assertEquals(bestellung.foreignKeys, store.table("bestellung").foreignKeys)
    }

    /**
     * Negativ (gemessen, Zeilen 05, 08, 10, 13, 16, 19): ohne `IF EXISTS` ist ein Name ohne Treffer
     * `TDS_SIM_NOT_A_CONSTRAINT` (3728), der Name einer Bedingung einer anderen Tabelle desselben Schemas
     * `TDS_SIM_CONSTRAINT_OTHER_TABLE` (3733); der Callback bleibt ungerufen. Der Name eines Index ist keine
     * Bedingung (Setzung), ebenso der eines Standardwerts und des Schlüssels (im Modell nicht geführt, Grenze).
     * Wächter: ein stiller Erfolg, die Nummer 3728 für den Namen der anderen Tabelle, ein Treffer auf einen Index.
     * @spec SPEC-036
     */
    @Test
    fun `ohne IF EXISTS ist ein Name ohne Treffer ein Fehler`() {
        val store = store()
        val none = SimCodes.NOT_A_CONSTRAINT
        val other = SimCodes.CONSTRAINT_OTHER_TABLE
        val cases = listOf(
            "ALTER TABLE [bestellung] DROP CONSTRAINT [gibtesnicht]" to none,
            "ALTER TABLE [bestellung] DROP CONSTRAINT [uq_kunden_Email]" to other,
            "ALTER TABLE [kunden] DROP CONSTRAINT [FK_bestellung_kundeid]" to other,
            "ALTER TABLE [kunden] DROP CONSTRAINT [ix_kunden_email]" to none,
            "ALTER TABLE [kunden] DROP CONSTRAINT [pk_kunden]" to none,
            "ALTER TABLE [kunden] DROP CONSTRAINT [df_kunden_Email]" to none,
        )
        for ((sql, code) in cases) assertEquals(code, failureCode(store, sql), sql)
        assertTrue(store.shapes.isEmpty())

        run(store, "ALTER TABLE [kunden] DROP CONSTRAINT [uq_kunden_Email]")
        assertEquals(none, failureCode(store, "ALTER TABLE [kunden] DROP CONSTRAINT [uq_kunden_Email]"))
        assertEquals(1, store.shapes.size)
    }

    /**
     * Negativ (gemessen, Zeilen 20 und 21): eine unbekannte Tabelle ist `TDS_SIM_OBJECT_NOT_FOUND` (4902) mit und ohne
     * `IF EXISTS`, ebenso eine Tabelle in einem anderen Schema.
     * Wächter: ein stiller Erfolg bei `IF EXISTS`.
     * @spec SPEC-036
     */
    @Test
    fun `eine unbekannte Tabelle ist auch mit IF EXISTS ein Fehler`() {
        val store = store()
        val texts = listOf(
            "ALTER TABLE [gibtesnicht] DROP CONSTRAINT IF EXISTS [x]",
            "ALTER TABLE [gibtesnicht] DROP CONSTRAINT [x]",
            "ALTER TABLE [andere].[kunden] DROP CONSTRAINT IF EXISTS [uq_kunden_Email]",
        )
        for (sql in texts) assertEquals(SimCodes.OBJECT_NOT_FOUND, failureCode(store, sql), sql)
        assertTrue(store.shapes.isEmpty())
    }

    /**
     * Negativ: Texte außerhalb der Form sind nicht erkannt (`TDS_UNSUPPORTED_SQL`): mehrere Namen (Setzung, nicht
     * von d-migrate gesendet), kein Name, `DROP COLUMN`, `DROP` ohne `CONSTRAINT`, `IF` ohne `EXISTS`, `WITH CHECK`
     * vor `DROP`, ein weiteres Wort hinter dem Namen und `DROP INDEX IF EXISTS` (den d-migrate vor einem geänderten
     * Index sendet, am echten Server mit dem Befehl `0x00c9` beantwortet; keine Form der Bibliothek).
     * Wächter: ein Parser, der eine dieser Formen rät oder nur den ersten Namen nimmt.
     * @spec SPEC-036
     */
    @Test
    fun `Formen ausserhalb von D5 sind nicht erkannt`() {
        val store = store()
        val texts = listOf(
            "ALTER TABLE [bestellung] DROP CONSTRAINT [a], [b]",
            "ALTER TABLE [bestellung] DROP CONSTRAINT IF EXISTS [a], [b]",
            "ALTER TABLE [bestellung] DROP CONSTRAINT",
            "ALTER TABLE [bestellung] DROP CONSTRAINT IF EXISTS",
            "ALTER TABLE [bestellung] DROP COLUMN [Menge]",
            "ALTER TABLE [bestellung] DROP [ck_bestellung_menge]",
            "ALTER TABLE [bestellung] DROP CONSTRAINT IF [ck_bestellung_menge]",
            "ALTER TABLE [bestellung] WITH CHECK DROP CONSTRAINT [ck_bestellung_menge]",
            "ALTER TABLE [bestellung] DROP CONSTRAINT [ck_bestellung_menge] WITH (ONLINE = ON)",
            "DROP INDEX IF EXISTS [ix_kunden_email] ON [kunden];",
        )
        for (sql in texts) assertEquals(SimCodes.UNSUPPORTED_SQL, failureCode(store, sql), sql)
        assertTrue(store.shapes.isEmpty())
    }

    /**
     * Der Text von d-migrate ist als Form D5 erkannt: Tabelle, Name und `IF EXISTS`; ohne `IF EXISTS` und mit
     * Schema ebenso.
     * Wächter: ein Name mit den Klammern, ein verlorenes Schema, ein falsches `ifExists`.
     * @spec SPEC-036
     */
    @Test
    fun `die Form D5 wird mit Tabelle Name und IF EXISTS erkannt`() {
        assertRecognized(
            Statement.DropConstraint(tableRef("bestellung"), "ck_bestellung_menge", ifExists = true),
            dMigrateDropTexts[0],
        )
        assertRecognized(
            Statement.DropConstraint(tableRef("t", "s"), "c", ifExists = false),
            "ALTER TABLE s.t DROP CONSTRAINT c",
        )
    }
}
