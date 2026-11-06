package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
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

    /**
     * Happy: ein Leser, der `catalog()` in Schleife ruft, und mehrere Schreiber, die Tabellen anlegen und erweitern,
     * laufen gleichzeitig; kein Aufruf wirft, und am Ende stehen alle angelegten Tabellen und alle angefügten
     * Spalten im
     * Bestand (keine Strukturänderung geht verloren). Belegt ist der einzelne Aufruf von `catalog()` und `replace`
     * sowie die Folge „Planung, Callback“ über das Backend; ein Zugriff über mehrere Backends auf einen Bestand ist
     * nicht belegt.
     * Wächter: `catalog()` und `replace` ohne Sperre (der Leser läuft in die gleichzeitige Änderung der Tabellenliste
     * und wirft), ein Backend ohne Sperre um Planung und Callback (eine Spalte geht verloren).
     * @spec SPEC-035
     */
    @Test
    fun `gleichzeitiger Zugriff verliert nichts`() {
        val store = TableStore(listOf(kunden))
        val backend = store.backend()
        val writers = 4
        val perWriter = 150
        val failures = ConcurrentLinkedQueue<Throwable>()
        val done = AtomicBoolean(false)
        val reader = Thread {
            try {
                while (!done.get()) store.catalog().tables.size
            } catch (e: ConcurrentModificationException) {
                failures += e
            }
        }
        reader.start()
        val start = CountDownLatch(1)
        val threads = (0 until writers).map { w ->
            Thread {
                start.await()
                for (n in 0 until perWriter) {
                    val events = ask(backend, "CREATE TABLE [t_${w}_$n] (a INT NOT NULL, PRIMARY KEY (a))")
                    if (events != listOf<QueryEvent>(QueryEvent.UpdateCount(0))) failures += AssertionError("$events")
                    ask(backend, "ALTER TABLE [kunden] ADD [c_${w}_$n] INT")
                }
            }
        }
        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }
        done.set(true)
        reader.join()

        assertEquals(emptyList(), failures.toList())
        val catalog = store.catalog()
        assertEquals(1 + writers * perWriter, catalog.tables.size)
        assertEquals(2 + writers * perWriter, catalog.tables.single { it.name == "kunden" }.columns.size)
    }

    /**
     * Happy: die Quelle des Prozesses ([storeSource]) ist auch ohne Tabellen der Datei ein Backend: eine Auswahl
     * endet mit
     * `TDS_SIM_UNKNOWN_OBJECT` und „Tabelle nicht beschrieben“, `CREATE TABLE` wird angenommen und die Tabelle ist
     * danach lesbar (anders als die Bibliothek mit `BackendSource.Simulation` ohne Tabellen,
     * die keine Anfrage beantwortet).
     * Wächter: eine Quelle, die ohne Tabellen kein Backend liefert (die Anfrage bliebe unbeantwortet).
     * @spec SPEC-035
     */
    @Test
    fun `ohne Beschreibungsdatei antwortet der Prozess mit Tabelle nicht beschrieben`() {
        val source = assertIs<BackendSource.Custom>(storeSource(emptyList()))
        val backend = source.backend
        val failure = assertIs<QueryEvent.Failure>(ask(backend, "SELECT a FROM t").single())
        assertEquals("TDS_SIM_UNKNOWN_OBJECT", failure.code)
        assertEquals("Tabelle nicht beschrieben", failure.message)
        assertEquals(
            listOf<QueryEvent>(QueryEvent.UpdateCount(0)),
            ask(backend, "CREATE TABLE t (a INT NOT NULL, PRIMARY KEY (a))"),
        )
        assertEquals(listOf("t"), listed(backend))
    }
}
