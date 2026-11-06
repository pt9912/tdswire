package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Strukturänderung (`SPEC-036`, Form D; `ADR-0033` Nr. 3) am Backend: `CREATE TABLE` und `ALTER TABLE … ADD` in dem
 * Wortlaut, den d-migrate 1.12.0 bei `schema migrate --execute` sendet ([dMigrateStructureTexts]), rufen den Callback
 * mit der Tabelle nach der Anweisung. Die Fehlerfälle (Tabelle schon da, Typ ohne Entsprechung) sind Setzung; der
 * Server antwortet dort mit eigenen Nummern, die die Bibliothek nicht sendet.
 */
class SimulationBackendStructureTest {
    private data class Call(
        val schema: String,
        val name: String,
        val columns: List<SimColumn>,
        val primaryKey: List<String>,
    )

    /** Ein Integrator im Kleinen: Tabellen im Speicher, jede Strukturänderung ersetzt die Tabelle. */
    private class Store(var tables: List<SimTable>, private val failure: Throwable? = null) {
        val calls = mutableListOf<Call>()

        val backend = SimulationBackend({ SimCatalog(tables) }) { schema, name, columns, key ->
            calls += Call(schema, name, columns, key)
            failure?.let { throw it }
            tables = tables.filterNot { it.schema == schema && it.name == name } +
                SimTable(schema, name, columns, key, rows = { RowSource { null } })
        }
    }

    private val kunden = SimTable(
        name = "kunden",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Name", SqlType.UnicodeText, maxLength = 100),
        ),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
    )

    private val createText = dMigrateStructureTexts.single { it.startsWith("CREATE TABLE") }
    private val alterText = dMigrateStructureTexts.single { it.startsWith("ALTER TABLE") }

    private fun run(store: Store, sql: String): List<QueryEvent> = collectEvents(store.backend, sql)

    private fun failureCode(store: Store, sql: String): String =
        assertIs<QueryEvent.Failure>(run(store, sql).single(), sql).code

    /**
     * Happy: der gesendete `CREATE TABLE` legt `produkte` im Schema `dbo` an; der Callback bekommt genau einmal sieben
     * Spalten in der Reihenfolge des Textes (Typ, Nullbarkeit, Länge 200 des Texts, Dezimal 10,2) und den Schlüssel
     * `Id`, die Antwort ist der Änderungszähler 0, und die nächste Anfrage sieht die Tabelle im Katalog.
     * Wächter: ein Callback, der vor der Typabbildung gerufen wird, ein Zähler ungleich 0 oder ein Backend, das den
     * Anbieter nur beim Bau liest (die nächste Anfrage sähe die Tabelle nicht).
     * @spec SPEC-036
     */
    @Test
    fun `CREATE TABLE ruft den Callback und aendert den Katalog`() {
        val store = Store(listOf(kunden))
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), run(store, createText))

        val expected = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Bezeichnung", SqlType.UnicodeText, nullable = false, maxLength = 200),
            SimColumn("Preis", SqlType.Decimal(10, 2)),
            SimColumn("Aktiv", SqlType.Bool),
            SimColumn("Seit", SqlType.DateTime),
            SimColumn("Uhr", SqlType.Time),
            SimColumn("Zahl", SqlType.Int32),
        )
        assertEquals(listOf(Call("dbo", "produkte", expected, listOf("Id"))), store.calls)

        val tables = CatalogForm.TABLES.template.replace("?", " @P0 ")
        val param = QueryParameter("@P0", SqlType.UnicodeText, "dbo")
        val names = collectEvents(store.backend, tables, listOf(param)).filterIsInstance<QueryEvent.Row>()
        assertEquals(listOf("kunden", "produkte"), names.map { it.values[0] })
    }

    /**
     * Happy: der gesendete `ALTER TABLE [kunden] ADD [Email] NVARCHAR(200);` meldet die Tabelle mit den alten Spalten
     * und der neuen, mit dem Namen und Schlüssel des Katalogs (auch bei anderer Schreibweise im Text).
     * Wächter: ein Callback, der nur die neue Spalte meldet (der Integrator ersetzte die Tabelle durch eine mit einer
     * Spalte).
     * @spec SPEC-036
     */
    @Test
    fun `ALTER TABLE ADD meldet die Tabelle nach der Anweisung`() {
        val store = Store(listOf(kunden))
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), run(store, alterText))
        val second = run(store, "alter table KUNDEN add [Aktiv] bit not null")
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), second)

        val email = SimColumn("Email", SqlType.UnicodeText, maxLength = 200)
        val aktiv = SimColumn("Aktiv", SqlType.Bool, nullable = false)
        assertEquals(kunden.columns + email, store.calls[0].columns)
        assertEquals(Call("dbo", "kunden", kunden.columns + email + aktiv, listOf("Id")), store.calls[1])
    }

    /**
     * Happy: Schlüsselspalten sind nicht nullbar, auch wenn der Text `NULL` sagt, der Schlüssel trägt die Schreibweise
     * der Spalten, ein Schema im Text gilt, und mehrere Spalten in `ADD` kommen alle an.
     * Wächter: eine nullbare Schlüsselspalte (`SimTable` weist sie ab, der Integrator bekäme einen Fehler).
     * @spec SPEC-036
     */
    @Test
    fun `Schluesselspalten sind nicht nullbar und tragen die Schreibweise der Spalten`() {
        val store = Store(emptyList())
        run(store, "CREATE TABLE app.neu ([A] INT NULL, [b] BIGINT, PRIMARY KEY ([a], B))")
        val call = store.calls.single()
        assertEquals("app", call.schema)
        assertEquals(listOf("A", "b"), call.primaryKey)
        assertEquals(listOf(false, false), call.columns.map { it.nullable })

        run(store, "ALTER TABLE app.neu ADD c INT, d DATE NOT NULL")
        assertEquals(listOf("A", "b", "c", "d"), store.calls.last().columns.map { it.name })
    }

    /**
     * Happy: die Längen und Typen der Spalten — `NVARCHAR(MAX)` ist -1 (der Katalog meldet `max_length` -1),
     * `NVARCHAR(1)` ist 1, Schreibweise und Leerraum sind frei; die sechs übrigen Typen ergeben den passenden
     * `SqlType`.
     * @spec SPEC-036
     */
    @Test
    fun `Typen und Laengen der Spalten`() {
        val store = Store(emptyList())
        val text = "create table t (k int, a nvarchar(max), b NVARCHAR ( 1 ), c Decimal(38, 0), d datetime2, e time, " +
            "f date, g bit, h bigint, primary key (k))"
        run(store, text)
        val columns = store.calls.single().columns.associateBy { it.name }
        assertEquals(-1, columns.getValue("a").maxLength)
        assertEquals(1, columns.getValue("b").maxLength)
        assertEquals(SqlType.Decimal(38, 0), columns.getValue("c").type)
        assertEquals(
            listOf(SqlType.DateTime, SqlType.Time, SqlType.Date, SqlType.Bool, SqlType.Int64),
            listOf("d", "e", "f", "g", "h").map { columns.getValue(it).type },
        )
    }

    /**
     * Negativ: ohne Callback ist jede Strukturänderung `TDS_UNSUPPORTED_SQL`, mit festem Katalog (erster Konstruktor)
     * wie mit Anbieter; kein Katalog ändert sich.
     * Wächter: ein Backend, das die Anweisung ohne Callback mit Zähler 0 beantwortet (der Client glaubte, die Tabelle
     * sei angelegt, die Nachprüfung scheiterte).
     * @spec SPEC-036
     */
    @Test
    fun `ohne Callback ist Strukturaenderung abgewiesen`() {
        val fixed = SimulationBackend(SimCatalog(listOf(kunden)))
        val provider = SimulationBackend({ SimCatalog(listOf(kunden)) })
        for (backend in listOf(fixed, provider)) {
            for (sql in listOf(createText, alterText)) {
                val failure = assertIs<QueryEvent.Failure>(collectEvents(backend, sql).single())
                assertEquals(SimCodes.UNSUPPORTED_SQL, failure.code)
            }
        }
    }

    /**
     * Negativ: der Katalog lässt die Anweisung nicht zu (Tabelle schon da, Spalte schon da, kein Schlüssel,
     * Schlüsselspalte fehlt, unbekannte Tabelle), oder der Text ist keine Form (Typ ohne Entsprechung, weitere
     * Klauseln, Länge außerhalb, doppelte Spalte, Schlüssel doppelt): jeder Fall ist ein einziger Fehler, und der
     * Callback bleibt ungerufen.
     * Wächter: ein Weglassen der Prüfung gegen den Katalog (der Integrator bekäme eine doppelte Tabelle) oder ein Typ,
     * der still zu `INT` wird.
     * @spec SPEC-036
     */
    @Test
    fun `unzulaessige Strukturaenderungen rufen keinen Callback`() {
        val store = Store(listOf(kunden))
        val unsupported = listOf(
            "CREATE TABLE kunden (a INT, PRIMARY KEY (a))",
            "CREATE TABLE t (a INT)",
            "CREATE TABLE t (a FLOAT, PRIMARY KEY (a))",
            "CREATE TABLE t (a SMALLINT, PRIMARY KEY (a))",
            "CREATE TABLE t (a INT IDENTITY(1,1), PRIMARY KEY (a))",
            "CREATE TABLE t (a INT DEFAULT 0, PRIMARY KEY (a))",
            "CREATE TABLE t (a NVARCHAR(4001), PRIMARY KEY (a))",
            "CREATE TABLE t (a NVARCHAR(0), PRIMARY KEY (a))",
            "CREATE TABLE t (a DECIMAL(39,0), PRIMARY KEY (a))",
            "CREATE TABLE t (a DECIMAL(5,6), PRIMARY KEY (a))",
            "CREATE TABLE t (a INT, A BIT, PRIMARY KEY (a))",
            "CREATE TABLE t (a INT, PRIMARY KEY (a, a))",
            "CREATE TABLE t (a INT, PRIMARY KEY (a), PRIMARY KEY (a))",
            "CREATE TABLE t (PRIMARY KEY (a))",
            "CREATE TABLE t (a INT, PRIMARY KEY (a)) WITH (DATA_COMPRESSION = PAGE)",
            "CREATE INDEX i ON kunden (Name)",
            "ALTER TABLE kunden ADD Name INT",
            "ALTER TABLE kunden ADD x INT, X BIT",
            "ALTER TABLE kunden DROP COLUMN Name",
            "ALTER TABLE kunden ADD",
            "DROP TABLE kunden",
        )
        for (sql in unsupported) assertEquals(SimCodes.UNSUPPORTED_SQL, failureCode(store, sql), sql)
        assertEquals(SimCodes.UNKNOWN_OBJECT, failureCode(store, "CREATE TABLE t (a INT, PRIMARY KEY (b))"))
        assertEquals(SimCodes.UNKNOWN_OBJECT, failureCode(store, "ALTER TABLE fremd ADD x INT"))
        assertEquals(emptyList(), store.calls)
    }

    /**
     * Negativ: wirft der Callback, endet die Anweisung mit `TDS_BACKEND_ERROR` und fester Meldung ohne den Text der
     * Ausnahme (ein Fehler in der Mitte hinterlässt den Zustand beim Integrator, `ADR-0033` Nr. 6); wird die Ausführung
     * geschlossen, bevor sie gesammelt wird, bleibt der Callback ungerufen.
     * Wächter: eine Meldung mit dem Text der Ausnahme oder ein Callback, der schon bei `execute` läuft.
     * @spec SPEC-036
     */
    @Test
    fun `Fehler und Schliessen vor dem Sammeln`() {
        val broken = Store(emptyList(), failure = IllegalStateException("Geheimnis des Integrators"))
        val failure = assertIs<QueryEvent.Failure>(run(broken, createText).single())
        assertEquals(SimCodes.BACKEND_ERROR, failure.code)
        assertTrue("Geheimnis" !in failure.message)
        assertEquals(1, broken.calls.size)

        val quiet = Store(emptyList())
        runBlocking { quiet.backend.execute(queryRequest(createText)).close() }
        assertEquals(emptyList(), quiet.calls)
    }

    /**
     * Happy: die Transaktionstexte von `schema migrate --execute` (`IF @@TRANCOUNT > 0 COMMIT TRAN`, auch mit
     * `set implicit_transactions off` davor) und `ROLLBACK TRAN` sind der Änderungszähler 0 und rufen keinen Callback:
     * die Bibliothek hat keine Transaktion (`ADR-0033` Nr. 6, gemessen im Spike: der Bestand bleibt halb geändert).
     * Wächter: ein `ROLLBACK`, das etwas zurücknimmt, oder ein Ergebnis ohne Anfang (die Sitzung schließt).
     * @spec SPEC-036
     */
    @Test
    fun `Transaktionstexte sind der Zaehler 0 und aendern nichts`() {
        val store = Store(listOf(kunden))
        val texts = dMigrateStructureTexts.filter { it.contains("@@TRANCOUNT") } + "IF @@TRANCOUNT > 0 ROLLBACK TRAN"
        for (sql in texts) assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), run(store, sql), sql)
        run(store, "set implicit_transactions on ")
        assertEquals(emptyList(), store.calls)
        assertEquals(listOf(kunden), store.tables)
    }
}
