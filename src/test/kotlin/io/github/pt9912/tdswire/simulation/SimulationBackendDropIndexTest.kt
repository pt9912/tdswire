package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Form D6 (`DROP INDEX [IF EXISTS] <Name> ON <Tabelle>`, `SPEC-036`). Gemessen am echten Server (2022 und 2025,
 * Messprotokoll `docs/messungen/drop-index-d-migrate-2026-11-11.md`, Szenario `struktur-drop-index`): mit Treffer und
 * mit `IF EXISTS` ohne Treffer, auch an einer unbekannten Tabelle, antwortet er mit DONE, Befehl `0x00c9`, ohne
 * Zähler; ohne `IF EXISTS` ist ein Name ohne Treffer ERROR 3701, auch bei dem Index einer anderen Tabelle und an einer
 * unbekannten Tabelle; der Index einer Eindeutigkeit ist ERROR 3723, mit und ohne `IF EXISTS`; der Name gilt ohne
 * Beachtung der Schreibweise (gemessen am eindeutigen Index). Der Wortlaut von d-migrate steht in
 * [D_MIGRATE_DROP_INDEX_TEXT]. Setzung (nicht gemessen): Tabellen in einem anderen Schema als `dbo`; die Art des
 * Abschlusses ist `UNKNOWN`, weil die Aufzählung der Arten keine für `DROP INDEX` führt; der Name des Schlüssels ist im
 * Modell nicht geführt und trifft deshalb nicht.
 */
class SimulationBackendDropIndexTest {
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
            SimColumn("Email", SqlType.UnicodeText, maxLength = 200),
            SimColumn("Menge", SqlType.Int32),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
        uniqueKeys = listOf(SimUniqueKey("uq_kunden_Email", listOf("Email"))),
        indexes = listOf(
            SimIndex("ix_kunden_email", listOf("Email")),
            SimIndex("ux_kunden_menge", listOf("Menge"), unique = true),
            SimIndex("ix_gemeinsam", listOf("Menge")),
        ),
    )

    private val bestellung = SimTable(
        name = "bestellung",
        columns = listOf(SimColumn("Id", SqlType.Int64, nullable = false), SimColumn("Menge", SqlType.Int32)),
        primaryKey = listOf("Id"),
        rows = rowless,
        indexes = listOf(
            SimIndex("ix_bestellung_menge", listOf("Menge")),
            SimIndex("ix_gemeinsam", listOf("Menge")),
        ),
    )

    private fun store() = Store(listOf(kunden, bestellung))

    private fun run(store: Store, sql: String): List<QueryEvent> = collectEvents(store.backend, sql)

    private fun kind(events: List<QueryEvent>): StatementKind = assertIs<QueryEvent.UpdateCount>(events.single()).kind

    private fun failureCode(store: Store, sql: String): String =
        assertIs<QueryEvent.Failure>(run(store, sql).single(), sql).code

    private fun SimTable.indexNames() = indexes.map { it.name }

    /**
     * Happy (Wortlaut von d-migrate): der Text nimmt den Index aus der Tabelle; Spalten, Schlüssel, Eindeutigkeit und
     * die übrigen Indizes bleiben; der Abschluss ist die Art `UNKNOWN`. Die Folge `DROP` und `CREATE UNIQUE INDEX`
     * desselben Namens (Schritt s14 von d-migrate) endet mit dem eindeutigen Index.
     * Wächter: eine Planung, die den Namen ignoriert (alle bleiben), eine, die mehr entfernt als den einen Index.
     * @spec SPEC-036
     */
    @Test
    fun `der Text von d-migrate nimmt den Index aus der Tabelle`() {
        val store = store()
        assertEquals(StatementKind.UNKNOWN, kind(run(store, D_MIGRATE_DROP_INDEX_TEXT)))

        assertEquals(1, store.shapes.size)
        val customers = store.table("kunden")
        assertEquals(listOf("ux_kunden_menge", "ix_gemeinsam"), customers.indexNames())
        assertEquals(kunden.columns, customers.columns)
        assertEquals(kunden.primaryKey, customers.primaryKey)
        assertEquals(kunden.uniqueKeys, customers.uniqueKeys)
        assertEquals(bestellung.indexes, store.table("bestellung").indexes)

        run(store, "CREATE UNIQUE INDEX [ix_kunden_email] ON [kunden] ([Email]);")
        assertEquals(
            SimIndex("ix_kunden_email", listOf("Email"), unique = true),
            store.table("kunden").indexes.last(),
        )
    }

    /**
     * Happy (gemessen, Zeile 12 für den eindeutigen Index; sonst Setzung): der Name gilt ohne Beachtung der
     * Schreibweise, mit und ohne `IF EXISTS`, mit Schema und ohne `;`; genau der eine Index fällt, der gleichnamige
     * einer anderen Tabelle bleibt (Zeile 20 misst den Namen je Tabelle).
     * Wächter: ein Vergleich mit Beachtung der Schreibweise, ein `DROP`, das beide Indizes gleichen Namens entfernt.
     * @spec SPEC-036
     */
    @Test
    fun `der Name gilt ohne Beachtung der Schreibweise und je Tabelle`() {
        val store = store()
        run(store, "DROP INDEX [UX_KUNDEN_MENGE] ON dbo.kunden")
        assertEquals(listOf("ix_kunden_email", "ix_gemeinsam"), store.table("kunden").indexNames())

        run(store, "drop index if exists IX_Gemeinsam on [bestellung];")
        assertEquals(listOf("ix_bestellung_menge"), store.table("bestellung").indexNames())
        assertEquals(listOf("ix_kunden_email", "ix_gemeinsam"), store.table("kunden").indexNames())
    }

    /**
     * Happy (gemessen, Zeilen 08, 11): `IF EXISTS` ohne Treffer ist ein Erfolg, auch bei einem Namen, der nie
     * vorhanden war, und bei dem Index einer anderen Tabelle; die Tabelle bleibt unverändert (der Callback bekommt
     * dieselbe Beschreibung). Der Name des Schlüssels ist im Modell nicht geführt (Grenze): `IF EXISTS` trifft nicht.
     * Wächter: ein Fehler statt des Erfolgs, ein Name der anderen Tabelle, der doch fällt.
     * @spec SPEC-036
     */
    @Test
    fun `IF EXISTS ohne Treffer ist ein Erfolg ohne Aenderung`() {
        val store = store()
        val texts = listOf(
            "DROP INDEX IF EXISTS [gibtesnicht] ON [bestellung]",
            "DROP INDEX IF EXISTS [ix_kunden_email] ON [bestellung]",
            "DROP INDEX IF EXISTS [ix_bestellung_menge] ON [kunden]",
            "DROP INDEX IF EXISTS [pk_kunden] ON [kunden]",
        )
        for (text in texts) assertEquals(StatementKind.UNKNOWN, kind(run(store, text)), text)

        assertEquals(texts.size, store.shapes.size)
        assertEquals(kunden.indexes, store.table("kunden").indexes)
        assertEquals(bestellung.indexes, store.table("bestellung").indexes)
    }

    /**
     * Negativ (gemessen, Zeilen 09, 10, 13 und 19): ohne `IF EXISTS` ist ein Name ohne Treffer `TDS_SIM_NOT_AN_INDEX`
     * (3701), auch bei dem Index einer anderen Tabelle, nach dem ersten Entfernen und an einer unbekannten Tabelle
     * oder einem unbekannten Schema (Schema: Setzung); der Callback bleibt ungerufen. Der Name des Schlüssels ist im
     * Modell nicht geführt (Grenze): der Server meldet dafür 3723, die Bibliothek 3701.
     * Wächter: ein stiller Erfolg, ein Treffer auf den Index einer anderen Tabelle.
     * @spec SPEC-036
     */
    @Test
    fun `ohne IF EXISTS ist ein Name ohne Treffer ein Fehler`() {
        val store = store()
        val none = SimCodes.NOT_AN_INDEX
        val cases = listOf(
            "DROP INDEX [gibtesnicht] ON [bestellung]" to none,
            "DROP INDEX [ix_kunden_email] ON [bestellung]" to none,
            "DROP INDEX [ix_bestellung_menge] ON [kunden]" to none,
            "DROP INDEX [pk_kunden] ON [kunden]" to none,
            "DROP INDEX [x] ON [gibtesnicht]" to none,
            "DROP INDEX [ix_kunden_email] ON [andere].[kunden]" to none,
        )
        for ((sql, code) in cases) assertEquals(code, failureCode(store, sql), sql)
        assertTrue(store.shapes.isEmpty())

        run(store, "DROP INDEX [ix_kunden_email] ON [kunden]")
        assertEquals(none, failureCode(store, "DROP INDEX [ix_kunden_email] ON [kunden]"))
        assertEquals(1, store.shapes.size)
    }

    /**
     * Happy (gemessen, Zeile 18): `IF EXISTS` an einer unbekannten Tabelle (auch in einem anderen Schema, dort
     * Setzung) ist ein Erfolg der Art `UNKNOWN` ohne Zähler 0 Zeilen; der Callback bleibt ungerufen, weil es keine
     * Tabelle zu melden gibt.
     * Wächter: ein Fehler `TDS_SIM_OBJECT_NOT_FOUND` wie bei `DROP CONSTRAINT`, ein Aufruf des Callbacks.
     * @spec SPEC-036
     */
    @Test
    fun `IF EXISTS an einer unbekannten Tabelle ist ein Erfolg ohne Callback`() {
        val store = store()
        val texts = listOf(
            "DROP INDEX IF EXISTS [x] ON [gibtesnicht]",
            "DROP INDEX IF EXISTS [ix_kunden_email] ON [andere].[kunden]",
        )
        for (sql in texts) {
            val event = assertIs<QueryEvent.UpdateCount>(run(store, sql).single(), sql)
            assertEquals(StatementKind.UNKNOWN, event.kind, sql)
            assertEquals(0L, event.affectedRows, sql)
        }
        assertTrue(store.shapes.isEmpty())
    }

    /**
     * Negativ (gemessen, Zeilen 14 und 15): der Name einer Eindeutigkeit der Tabelle ist `TDS_SIM_INDEX_OF_CONSTRAINT`
     * (3723), mit und ohne `IF EXISTS` und in anderer Schreibweise (Schreibweise: Setzung); die Eindeutigkeit bleibt,
     * der Callback bleibt ungerufen. Der Name einer Eindeutigkeit einer anderen Tabelle ist kein Treffer (Setzung).
     * Wächter: ein Entfernen der Eindeutigkeit, der Erfolg von `IF EXISTS`, die Nummer 3701.
     * @spec SPEC-036
     */
    @Test
    fun `der Index einer Eindeutigkeit ist ein Fehler mit und ohne IF EXISTS`() {
        val store = store()
        val texts = listOf(
            "DROP INDEX [uq_kunden_Email] ON [kunden]",
            "DROP INDEX IF EXISTS [uq_kunden_Email] ON [kunden]",
            "DROP INDEX [UQ_KUNDEN_EMAIL] ON [kunden]",
        )
        for (sql in texts) assertEquals(SimCodes.INDEX_OF_CONSTRAINT, failureCode(store, sql), sql)
        assertEquals(SimCodes.NOT_AN_INDEX, failureCode(store, "DROP INDEX [uq_kunden_Email] ON [bestellung]"))
        assertTrue(store.shapes.isEmpty())
        assertEquals(kunden.uniqueKeys, store.table("kunden").uniqueKeys)
    }

    /**
     * Negativ: Texte außerhalb der Form sind nicht erkannt (`TDS_UNSUPPORTED_SQL`): kein Name, kein `ON`, die alte
     * Schreibweise `DROP INDEX <Tabelle>.<Name>`, mehrere Namen, `IF` ohne `EXISTS`, eine Klausel `WITH`, ein weiteres
     * Wort hinter der Tabelle, zwei Anweisungen in einem Text (der Server beantwortet sie mit zwei DONE, Zeilen 22 und
     * 23), `DROP TABLE` und `DROP CONSTRAINT`.
     * Wächter: ein Parser, der eine dieser Formen rät oder nur den ersten Namen nimmt.
     * @spec SPEC-036
     */
    @Test
    fun `Formen ausserhalb von D6 sind nicht erkannt`() {
        val store = store()
        val texts = listOf(
            "DROP INDEX ON [kunden]",
            "DROP INDEX IF EXISTS ON [kunden]",
            "DROP INDEX [ix_kunden_email]",
            "DROP INDEX [kunden].[ix_kunden_email]",
            "DROP INDEX [ix_kunden_email] ON [kunden], [ix_gemeinsam] ON [kunden]",
            "DROP INDEX [ix_kunden_email], [ix_gemeinsam] ON [kunden]",
            "DROP INDEX IF [ix_kunden_email] ON [kunden]",
            "DROP INDEX [ix_kunden_email] ON [kunden] WITH (ONLINE = ON)",
            "DROP INDEX [ix_kunden_email] ON [kunden] [x]",
            "DROP INDEX IF EXISTS [ix_kunden_email] ON [kunden]; DROP INDEX IF EXISTS [ix_gemeinsam] ON [kunden];",
            "DROP INDEX IF EXISTS [ix_kunden_email] ON [kunden]; SELECT 1",
            "DROP TABLE [kunden]",
            "DROP CONSTRAINT [uq_kunden_Email]",
            "DROP",
        )
        for (sql in texts) assertEquals(SimCodes.UNSUPPORTED_SQL, failureCode(store, sql), sql)
        assertTrue(store.shapes.isEmpty())
        assertEquals(kunden.indexes, store.table("kunden").indexes)
    }

    /**
     * Der Text von d-migrate ist als Form D6 erkannt: Tabelle, Name und `IF EXISTS`; ohne `IF EXISTS`, mit Schema und
     * in Kleinschreibung ebenso.
     * Wächter: ein Name mit den Klammern, ein verlorenes Schema, ein falsches `ifExists`, ein vertauschter Name und
     * Tabelle.
     * @spec SPEC-036
     */
    @Test
    fun `die Form D6 wird mit Tabelle Name und IF EXISTS erkannt`() {
        assertRecognized(
            Statement.DropIndex(tableRef("kunden"), "ix_kunden_email", ifExists = true),
            D_MIGRATE_DROP_INDEX_TEXT,
        )
        assertRecognized(Statement.DropIndex(tableRef("t", "s"), "i", ifExists = false), "DROP INDEX i ON s.t")
        assertRecognized(
            Statement.DropIndex(tableRef("t"), "i j", ifExists = true),
            "drop index if exists [i j] on [t];",
        )
    }

    /**
     * Grenze der Länge: ein Name von 100 000 Zeichen (quotiert, unquotiert, als Tabelle) erkennt der Scanner ohne
     * Rekursion je Zeichen, und die Planung antwortet; der Server begrenzt Namen auf 128 Zeichen (nicht gemessen), die
     * Bibliothek prüft die Länge nicht.
     * Wächter: eine Erkennung mit Rekursion je Zeichen (Stapelüberlauf), eine Planung, die den langen Namen verliert.
     * @spec SPEC-036
     */
    @Test
    fun `ein Name von 100000 Zeichen ist erkannt und beantwortet`() {
        val long = "a".repeat(LONG_NAME)
        assertRecognized(
            Statement.DropIndex(tableRef("kunden"), long, ifExists = true),
            "DROP INDEX IF EXISTS [$long] ON [kunden]",
        )
        assertRecognized(
            Statement.DropIndex(tableRef("kunden"), long, ifExists = false),
            "DROP INDEX $long ON kunden",
        )
        assertRecognized(Statement.DropIndex(tableRef(long), "i", ifExists = true), "DROP INDEX IF EXISTS i ON [$long]")

        val store = store()
        assertEquals(StatementKind.UNKNOWN, kind(run(store, "DROP INDEX IF EXISTS [$long] ON [kunden]")))
        assertEquals(SimCodes.NOT_AN_INDEX, failureCode(store, "DROP INDEX [$long] ON [kunden]"))
        assertEquals(kunden.indexes, store.table("kunden").indexes)
    }

    private companion object {
        const val LONG_NAME = 100_000
    }
}
