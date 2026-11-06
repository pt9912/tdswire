package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Der Bestand des Testservers (`TDS-FA-12`, `SPEC-035` „Katalog-Anbieter“): der Integrator hält die Tabellen, die
 * Bibliothek fragt ihn bei jeder Anfrage und meldet ihm jede Strukturänderung. Die Anfragen sind Texte, wie d-migrate
 * 1.12.0 sie sendet (`schema migrate --execute`); die Erwartungen sind von Hand geschrieben.
 */
class TableStoreTest {
    private val kunden = SimTable(
        name = "kunden",
        columns = listOf(SimColumn("Id", SqlType.Int64, nullable = false), SimColumn("Name", SqlType.UnicodeText)),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
    )

    private fun ask(backend: QueryBackend, sql: String, vararg parameters: QueryParameter): List<QueryEvent> =
        runBlocking {
            val execution = backend.execute(QueryRequest("s", "r", sql, parameters.toList(), null))
            try {
                execution.events.toList()
            } finally {
                execution.close()
            }
        }

    private val tablesQuery = """
        SELECT t.name AS table_name, s.name AS schema_name
        FROM sys.tables t
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        WHERE s.name =  @P0 AND t.is_ms_shipped = 0
        ORDER BY t.name
    """.trimIndent()

    private fun listed(backend: QueryBackend): List<Any?> =
        ask(backend, tablesQuery, QueryParameter("@P0", SqlType.UnicodeText, "dbo"))
            .filterIsInstance<QueryEvent.Row>().map { it.values[0] }

    /**
     * Happy: `CREATE TABLE` über das Backend des Bestands legt die Tabelle an, `ALTER TABLE … ADD` erweitert sie, und
     * die Katalogabfrage zeigt jeweils den neuen Stand; die Tabelle der Datei bleibt.
     * Wächter: ein Bestand, der nur den Stand bei der Übergabe liefert (Gegenprobe c4 des Spikes), oder ein `replace`,
     * das die Tabelle nicht ersetzt.
     * @spec SPEC-035
     */
    @Test
    fun `der Bestand folgt den Strukturaenderungen`() {
        val backend = TableStore(listOf(kunden)).backend()
        assertEquals(listOf("kunden"), listed(backend))

        val ddl = "CREATE TABLE [produkte] (\n    [Id] BIGINT NOT NULL,\n    [Preis] DECIMAL(10,2),\n" +
            "    CONSTRAINT [pk_produkte] PRIMARY KEY ([Id])\n);"
        val created = ask(backend, ddl)
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), created)
        assertEquals(listOf("kunden", "produkte"), listed(backend))

        ask(backend, "ALTER TABLE [produkte] ADD [Name] NVARCHAR(50);")
        val columns = ask(
            backend,
            "SELECT * FROM produkte WHERE 1 = 0",
        ).filterIsInstance<QueryEvent.ResultStart>().single().columns
        assertEquals(listOf("Id", "Preis", "Name"), columns.map { it.name })
    }

    /**
     * Happy: ein zweites `CREATE TABLE` derselben Tabelle ist abgewiesen und lässt den Bestand unverändert; die Tabelle
     * ohne Zeilen liefert beim Lesen eine leere Auswahl.
     * @spec SPEC-035
     */
    @Test
    fun `eine doppelte Tabelle aendert den Bestand nicht`() {
        val backend = TableStore(listOf(kunden)).backend()
        val failure = assertIs<QueryEvent.Failure>(
            ask(backend, "CREATE TABLE KUNDEN (a INT, PRIMARY KEY (a))").single(),
        )
        assertEquals("TDS_UNSUPPORTED_SQL", failure.code)
        assertEquals(listOf("kunden"), listed(backend))
        val rows = ask(backend, "SELECT Id, Name FROM kunden")
        assertEquals(listOf(QueryEvent.ResultEnd(0)), rows.drop(1))
    }

    /**
     * Happy: der Katalog, den der Bestand liefert, ist bei jedem Aufruf neu gebaut; ein Katalog aus der Vergangenheit
     * ändert sich nicht (Wächter: ein Katalog, der den Bestand live spiegelt, würde einer laufenden Anfrage die Tabelle
     * unter den Händen ändern).
     * @spec SPEC-035
     */
    @Test
    fun `jeder Aufruf von catalog baut einen Stand`() {
        val store = TableStore(listOf(kunden))
        val before = store.catalog()
        store.replace("dbo", "neu", listOf(SimColumn("a", SqlType.Int32, nullable = false)), listOf("a"))
        assertEquals(listOf("kunden"), before.tables.map { it.name })
        assertEquals(listOf("kunden", "neu"), store.catalog().tables.map { it.name })
    }
}
