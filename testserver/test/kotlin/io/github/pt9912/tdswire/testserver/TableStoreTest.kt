package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.simulation.FilterTerm
import io.github.pt9912.tdswire.simulation.RowFilter
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimQuery
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimUniqueKey
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private const val READERS = 2

/** Die Katalogabfragen von d-migrate 1.12.0 für die Elemente einer Tabelle (Wortlaut wie `DMigrateWire.kt`). */
private const val COLUMNS = """
    SELECT c.name AS column_name, ty.name AS type_name, c.max_length, c.precision, c.scale,
           c.is_nullable, c.is_identity, c.is_computed, c.column_id,
           dc.definition AS default_definition,
           ic.seed_value, ic.increment_value,
           cc.definition AS computed_definition, cc.is_persisted AS computed_persisted
    FROM sys.columns c
    JOIN sys.types ty ON ty.user_type_id = c.user_type_id
    LEFT JOIN sys.default_constraints dc ON dc.object_id = c.default_object_id
    LEFT JOIN sys.identity_columns ic
        ON ic.object_id = c.object_id AND ic.column_id = c.column_id
    LEFT JOIN sys.computed_columns cc
        ON cc.object_id = c.object_id AND cc.column_id = c.column_id
    WHERE c.object_id = OBJECT_ID(?)
    ORDER BY c.column_id
"""

private const val UNIQUE_KEYS = """
    SELECT kc.name AS constraint_name, col.name AS column_name
    FROM sys.key_constraints kc
    JOIN sys.index_columns ic
        ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
    JOIN sys.columns col
        ON col.object_id = ic.object_id AND col.column_id = ic.column_id
    WHERE kc.parent_object_id = OBJECT_ID(?) AND kc.type = 'UQ'
    ORDER BY kc.name, ic.key_ordinal
"""

private const val FOREIGN_KEYS = """
    SELECT fk.name AS constraint_name, pc.name AS column_name,
           rt.name AS referenced_table, rc.name AS referenced_column,
           fk.delete_referential_action_desc, fk.update_referential_action_desc,
           fkc.constraint_column_id
    FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
    JOIN sys.columns pc
        ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
    JOIN sys.columns rc
        ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
    JOIN sys.tables rt ON rt.object_id = fkc.referenced_object_id
    WHERE fk.parent_object_id = OBJECT_ID(?)
    ORDER BY fk.name, fkc.constraint_column_id
"""

private const val INDEXES = """
    SELECT i.name AS index_name, i.is_unique, i.has_filter, i.filter_definition, i.type,
           col.name AS column_name, ic.key_ordinal, ic.is_descending_key, ic.is_included_column
    FROM sys.indexes i
    JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
    JOIN sys.columns col ON col.object_id = ic.object_id AND col.column_id = ic.column_id
    WHERE i.object_id = OBJECT_ID(?) AND i.type > 0
      AND i.is_primary_key = 0 AND i.is_hypothetical = 0
    ORDER BY i.name, ic.key_ordinal, ic.index_column_id
"""

private const val CHECKS = """
    SELECT cc.name AS constraint_name, cc.definition
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID(?) AND cc.is_ms_shipped = 0
    ORDER BY cc.name
"""

/** Die Strukturänderung wie früher mit Schema, Name, Spalten und Schlüssel: die Beschreibung ohne Zeilen. */
private fun TableStore.replace(schema: String, name: String, columns: List<SimColumn>, primaryKey: List<String>) =
    replace(SimTable(schema, name, columns, primaryKey, rows = { RowSource { null } }))

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
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0, StatementKind.CREATE_TABLE)), created)
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
        assertEquals("TDS_SIM_OBJECT_EXISTS", failure.code)
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
     * Happy: zwei Leser, die `catalog()` rufen, und mehrere Schreiber, die Tabellen anlegen und erweitern, laufen
     * gleichzeitig; kein Aufruf wirft, und am Ende stehen alle angelegten Tabellen und alle angefügten Spalten im
     * Bestand (keine Strukturänderung geht verloren). Die Last ist begrenzt und hängt nicht von der Geschwindigkeit
     * des Hosts ab: je Anweisung eines Schreibers werden `READERS` Lesevorgänge freigegeben (Marken eines gemeinsamen
     * Semaphors); welcher Leser sie nimmt, bestimmt die Planung. Ein Leser dreht nie leer. Jeder gelesene Katalog
     * wird geprüft: `kunden` steht darin mit mindestens den zwei Spalten des Anfangs, und kein Tabellenname kommt
     * doppelt vor. Belegt ist der einzelne Aufruf von `catalog()` und
     * `replace` sowie die Folge „Planung, Callback“ über das Backend; ein Zugriff über mehrere Backends auf einen
     * Bestand ist nicht belegt.
     * Wächter: `catalog()` und `replace` ohne Sperre (ein Leser läuft in die gleichzeitige Änderung der Tabellenliste
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
        val steps = Semaphore(0)
        val readers = (0 until READERS).map { Thread { readCatalog(store, steps, done, failures) } }
        readers.forEach { it.start() }
        val start = CountDownLatch(1)
        val threads = (0 until writers).map { w ->
            Thread {
                start.await()
                for (n in 0 until perWriter) writeStep(backend, w, n, steps, failures)
            }
        }
        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }
        done.set(true)
        steps.release(READERS)
        readers.forEach { it.join() }

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
            listOf<QueryEvent>(QueryEvent.UpdateCount(0, StatementKind.CREATE_TABLE)),
            ask(backend, "CREATE TABLE t (a INT NOT NULL, PRIMARY KEY (a))"),
        )
        assertEquals(listOf("t"), listed(backend))
    }

    /** Ein Leser: ruft `catalog()` einmal je Marke, bis [done] gesetzt ist; eine Änderung der Liste ist ein Fehler. */
    private fun readCatalog(
        store: TableStore,
        steps: Semaphore,
        done: AtomicBoolean,
        failures: ConcurrentLinkedQueue<Throwable>,
    ) {
        try {
            while (true) {
                steps.acquire()
                if (done.get()) break
                failures += checkCatalog(store.catalog()).orEmpty()
            }
        } catch (e: ConcurrentModificationException) {
            failures += e
        }
    }

    /** Der gelesene Katalog ist in sich stimmig; die Fehlermeldung, sonst `null`. */
    private fun checkCatalog(catalog: SimCatalog): List<Throwable>? {
        val names = catalog.tables.map { it.name }
        val kunden = catalog.tables.singleOrNull { it.name == "kunden" }
        return when {
            names.size != names.toSet().size -> listOf(AssertionError("Tabellenname doppelt: $names"))
            kunden == null || kunden.columns.size < 2 -> listOf(AssertionError("kunden fehlt oder verkuerzt"))
            else -> null
        }
    }

    /** Ein Schritt eines Schreibers: legt eine Tabelle an, erweitert `kunden`, gibt je Anweisung Marken frei. */
    private fun writeStep(
        backend: QueryBackend,
        writer: Int,
        n: Int,
        steps: Semaphore,
        failures: ConcurrentLinkedQueue<Throwable>,
    ) {
        val events = ask(backend, "CREATE TABLE [t_${writer}_$n] (a INT NOT NULL, PRIMARY KEY (a))")
        val created = listOf<QueryEvent>(QueryEvent.UpdateCount(0, StatementKind.CREATE_TABLE))
        if (events != created) failures += AssertionError("$events")
        steps.release(READERS)
        ask(backend, "ALTER TABLE [kunden] ADD [c_${writer}_$n] INT")
        steps.release(READERS)
    }

    private fun tableOf(store: TableStore): SimTable = store.catalog().tables.single { it.name == "kunden" }

    private fun insert(store: TableStore, vararg rows: Map<String, Any?>): Long =
        runBlocking { requireNotNull(tableOf(store).onInsertBatch)(rows.toList()) }

    private fun stored(store: TableStore): List<List<Any?>> = runBlocking {
        val table = tableOf(store)
        val source = table.rows(SimQuery(table.columns, null))
        buildList { while (true) add(source.next() ?: break) }
    }

    /**
     * Boundary: eine Spalte `Int16` nimmt einen `Int` von −32768 bis 32767 auf und eine Spalte `UInt8` von 0 bis 255;
     * ein Wert um eins daneben, ein `Long` und ein `Short` werfen und lassen den Bestand unverändert.
     * Wächter: eine Zeilenprüfung, die nur die Klasse `Int` prüft (der Bestand hielte 256 in einer `UInt8`-Spalte).
     * @spec SPEC-035
     */
    @Test
    fun `der Bestand haelt Int16 und UInt8 im Wertebereich`() {
        val narrow = kunden.copy(
            columns = listOf(
                SimColumn("Id", SqlType.Int64, nullable = false),
                SimColumn("Klein", SqlType.Int16),
                SimColumn("Winzig", SqlType.UInt8),
            ),
        )
        val store = TableStore(listOf(narrow))
        val low = mapOf<String, Any?>("Id" to 1L, "Klein" to -32768, "Winzig" to 0)
        val high = mapOf<String, Any?>("Id" to 2L, "Klein" to 32767, "Winzig" to 255)
        assertEquals(2, insert(store, low, high))
        val before = stored(store)
        val bad = listOf<Map<String, Any?>>(
            mapOf("Klein" to -32769),
            mapOf("Klein" to 32768),
            mapOf("Winzig" to -1),
            mapOf("Winzig" to 256),
            mapOf("Klein" to 1L),
            mapOf("Winzig" to 1.toShort()),
        )
        for ((at, values) in bad.withIndex()) {
            assertFailsWith<IllegalArgumentException>("$values") { insert(store, mapOf("Id" to 10L + at) + values) }
        }
        assertEquals(before, stored(store))
    }

    /**
     * Happy: der Stapel-Callback der Tabelle des Bestands nimmt die Zeilen einer Nachricht auf und liefert ihre Zahl;
     * die Zeilenquelle liefert sie in der Reihenfolge der Spalten (eine fehlende Spalte als NULL), auch aus einem
     * später
     * gebauten Katalog; die zweite Nachricht hängt an.
     * Wächter: ein Bestand ohne Zeilen (nur Struktur), eine Zeilenquelle, die den Stand der Übergabe zeigt.
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `der Stapel-Callback speichert die Zeilen der Nachricht`() {
        val store = TableStore(listOf(kunden))
        assertEquals(2, insert(store, mapOf("Id" to 1L, "Name" to "Ada"), mapOf("Id" to 2L)))
        assertEquals(listOf(listOf(1L, "Ada"), listOf(2L, null)), stored(store))
        assertEquals(1, insert(store, mapOf("Id" to 3L, "Name" to "Cäcilie")))
        assertEquals(listOf(1L, 2L, 3L), stored(store).map { it[0] })
    }

    /**
     * Negativ: eine Nachricht gilt ganz oder gar nicht. Ein doppelter Schlüssel in der Nachricht, einer gegen den
     * Bestand, ein Text für eine Ganzzahlspalte, ein NULL im Schlüssel und eine Textspalte, die sich nur in der
     * Schreibweise unterscheidet (Setzung: Textschlüssel ohne Beachtung der Schreibweise), werfen, und der Bestand
     * behält genau die Zeile davor. Wächter: ein Einfügen der ersten Zeilen vor dem Fehler (Teilbestand), eine
     * fehlende Prüfung von Schlüssel, Typ oder NULL.
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `eine fehlerhafte Nachricht aendert den Bestand nicht`() {
        val store = TableStore(listOf(kunden))
        insert(store, mapOf("Id" to 1L, "Name" to "Ada"))
        val bad = listOf(
            listOf(mapOf("Id" to 5L), mapOf("Id" to 5L)),
            listOf(mapOf("Id" to 6L), mapOf("Id" to 1L)),
            listOf(mapOf("Id" to 7L), mapOf("Id" to "8")),
            listOf(mapOf("Id" to 9L), mapOf("Id" to null)),
            listOf(mapOf("Id" to 10L), mapOf("Name" to "ohne Schluessel")),
            listOf(mapOf("Id" to 11L, "Name" to 5)),
        )
        for (batch in bad) {
            assertFailsWith<RuntimeException>("$batch") { insert(store, *batch.toTypedArray()) }
            assertEquals(listOf(listOf(1L, "Ada")), stored(store), "$batch")
        }
    }

    /**
     * Happy: `ALTER TABLE … ADD` behält die Zeilen und füllt die neue Spalte mit NULL; ein Schlüssel eines Textes
     * unterscheidet nicht nach der Schreibweise (zwei Tabellen mit Textschlüssel ohne Beachtung der Schreibweise).
     * Wächter: eine Erweiterung, die die Zeilen verwirft oder verschiebt.
     * @spec SPEC-035
     */
    @Test
    fun `die Erweiterung der Tabelle behaelt die Zeilen`() {
        val store = TableStore(listOf(kunden))
        insert(store, mapOf("Id" to 1L, "Name" to "Ada"))
        ask(store.backend(), "ALTER TABLE [kunden] ADD [Email] NVARCHAR(50)")
        assertEquals(listOf(listOf(1L, "Ada", null)), stored(store))
        assertEquals(1, insert(store, mapOf("Id" to 2L, "Email" to "a@b")))
        assertEquals(listOf(null, "a@b"), stored(store)[1].drop(1))
    }

    /**
     * Happy: Standardwert, Eindeutigkeit, Fremdschlüssel, Prüfbedingung und Index überstehen die Strukturänderungen im
     * Bestand: `CREATE TABLE` legt sie an, `ALTER TABLE … ADD` einer Spalte und `CREATE INDEX` behalten sie, die
     * Katalogabfragen zeigen den Stand, und die Zeilen der Tabelle bleiben. Eine Tabelle der Datei trägt ihre Elemente
     * in den Bestand.
     * Wächter: ein Bestand, der beim Ersetzen die Elemente oder die Zeilen verwirft (der Katalog zeigte nach `ADD`
     * keine mehr).
     * @spec SPEC-035
     */
    @Test
    fun `der Bestand haelt die Elemente der Tabelle`() {
        val described = kunden.copy(uniqueKeys = listOf(SimUniqueKey("uq_kunden_name", listOf("Name"))))
        val store = TableStore(listOf(described))
        val backend = store.backend()
        ask(
            backend,
            "CREATE TABLE [bestellung] ([Id] BIGINT NOT NULL, [KundeId] BIGINT NOT NULL, " +
                "[Menge] INT NOT NULL CONSTRAINT [df_m] DEFAULT 1, " +
                "CONSTRAINT [fk_k] FOREIGN KEY ([KundeId]) REFERENCES [kunden] ([Id]) ON DELETE CASCADE, " +
                "CONSTRAINT [ck_m] CHECK (Menge > 0), CONSTRAINT [pk_b] PRIMARY KEY ([Id]))",
        )
        insert(store, mapOf("Id" to 1L, "Name" to "Ada"))
        ask(backend, "CREATE INDEX [ix_m] ON [bestellung] ([Menge])")
        ask(backend, "CREATE INDEX [ix_name] ON [kunden] ([Name])")
        ask(backend, "ALTER TABLE [kunden] ADD CONSTRAINT [ck_id] CHECK (Id > 0)")
        ask(backend, "ALTER TABLE [bestellung] ADD [Notiz] NVARCHAR(50)")

        fun catalogRows(sql: String, table: String) = ask(
            backend,
            sql.replace("?", " @P0 "),
            QueryParameter("@P0", SqlType.UnicodeText, table),
        ).filterIsInstance<QueryEvent.Row>().map { it.values }
        assertEquals(
            listOf(listOf("fk_k", "KundeId", "kunden", "Id", "CASCADE", "NO_ACTION", 1)),
            catalogRows(FOREIGN_KEYS, "bestellung"),
        )
        assertEquals(listOf(listOf("ck_m", "([Menge]>(0))")), catalogRows(CHECKS, "bestellung"))
        assertEquals(
            listOf("ix_m"),
            catalogRows(INDEXES, "bestellung").map { it[0] },
        )
        assertEquals(
            "((1))",
            catalogRows(COLUMNS, "bestellung").single { it[0] == "Menge" }[9],
        )
        assertEquals(listOf(listOf("uq_kunden_name", "Name")), catalogRows(UNIQUE_KEYS, "kunden"))
        assertEquals(listOf("ix_name", "uq_kunden_name"), catalogRows(INDEXES, "kunden").map { it[0] }.distinct())
        assertEquals(listOf(listOf("ck_id", "([Id]>(0))")), catalogRows(CHECKS, "kunden"))
        assertEquals(listOf(listOf(1L, "Ada")), stored(store))
    }

    /**
     * Happy (gemessen, M-19): `CREATE TABLE` ohne Schlüssel legt die Tabelle an; sie hat einen leeren Schlüssel im
     * Katalog und nimmt gleiche Zeilen mehrfach an (kein Schlüssel, keine Eindeutigkeit).
     * Wächter: eine Eindeutigkeitsprüfung über den leeren Schlüssel (der Bestand wiese die zweite Zeile ab).
     * @spec SPEC-035
     */
    @Test
    fun `eine Tabelle ohne Schluessel nimmt gleiche Zeilen an`() {
        val store = TableStore(emptyList())
        assertEquals(
            listOf<QueryEvent>(QueryEvent.UpdateCount(0, StatementKind.CREATE_TABLE)),
            ask(store.backend(), "CREATE TABLE [ohneschluessel] ([Id] INT NOT NULL, [Text] NVARCHAR(10));"),
        )
        val table = store.catalog().tables.single()
        assertEquals(emptyList(), table.primaryKey)
        val same = mapOf("Id" to 1, "Text" to "a")
        assertEquals(2, runBlocking { requireNotNull(table.onInsertBatch)(listOf(same, same)) })
        assertEquals(1, runBlocking { requireNotNull(table.onInsertBatch)(listOf(same)) })
    }

    /**
     * Happy: ein Dezimalschlüssel gilt nach `compareTo` (1.0 gleich 1.00), ein Textschlüssel ohne Beachtung der
     * Schreibweise; beides ist Setzung, nicht am Server gemessen.
     * @spec SPEC-035
     */
    @Test
    fun `Dezimal- und Textschluessel sind eindeutig nach compareTo und ohne Schreibweise`() {
        val store = TableStore(emptyList())
        store.replace(
            "dbo",
            "kunden",
            listOf(SimColumn("Id", SqlType.Decimal(5, 2), nullable = false), SimColumn("Name", SqlType.UnicodeText)),
            listOf("Id"),
        )
        insert(store, mapOf("Id" to BigDecimal("1.0")))
        assertFailsWith<RuntimeException> { insert(store, mapOf("Id" to BigDecimal("1.00"))) }
        val text = TableStore(emptyList())
        text.replace("dbo", "kunden", listOf(SimColumn("Id", SqlType.UnicodeText, nullable = false)), listOf("Id"))
        insert(text, mapOf("Id" to "Ada"))
        assertFailsWith<RuntimeException> { insert(text, mapOf("Id" to "ADA")) }
    }

    /**
     * Zwei Sitzungen laden gleichzeitig dieselben Schlüssel in dieselbe Tabelle: je Runde sendet jede Sitzung eine
     * Nachricht mit denselben zehn Schlüsseln, beide starten an einer Schranke. Genau eine Nachricht gelingt je Runde,
     * die andere wirft („Schlüssel nicht eindeutig“); am Ende hat der Bestand jeden Schlüssel genau einmal. Wächter:
     * `insertBatch` ohne Sperre (beide Prüfungen bestehen, die Zeilen stehen doppelt, oder die Liste der Zeilen wirft
     * bei gleichzeitiger Änderung). Der Lauf ist zufallsabhängig: ein roter Lauf ist ein Befund, ein grüner beweist die
     * Sperre nicht; der Mutationsnachweis steht im Messprotokoll des Massenladens.
     * @spec SPEC-035
     * @spec TDS-QA-02
     */
    @Test
    fun `zwei Sitzungen mit denselben Schluesseln laden jeden Schluessel einmal`() {
        val store = TableStore(listOf(kunden))
        val rounds = 2_000
        val perRound = 10
        val barrier = CyclicBarrier(2)
        val successes = AtomicInteger()
        val unexpected = ConcurrentLinkedQueue<Throwable>()
        val sessions = (0 until 2).map {
            Thread {
                for (round in 0 until rounds) {
                    val batch = (0 until perRound).map { mapOf<String, Any?>("Id" to (round * perRound + it).toLong()) }
                    barrier.await()
                    val failure = runCatching { insert(store, *batch.toTypedArray()) }.exceptionOrNull()
                    when {
                        failure == null -> successes.incrementAndGet()
                        failure.message != "Schlüssel nicht eindeutig" -> unexpected += failure
                    }
                }
            }
        }
        sessions.forEach { it.start() }
        sessions.forEach { it.join() }

        assertEquals(emptyList(), unexpected.toList())
        assertEquals(rounds, successes.get())
        assertEquals((0L until rounds * perRound.toLong()).toList(), stored(store).map { it[0] as Long }.sorted())
    }

    /**
     * Die Obergrenze des Bestands (Setzung): bei einer Grenze von 10 Zellen nimmt der Bestand fünf Zeilen zu zwei
     * Spalten an, eine weitere Zeile wirft („Bestand voll“) und ändert nichts. Wächter: eine fehlende Prüfung (der
     * Bestand wüchse über die Grenze, bis der Heap endet), eine Prüfung nach dem Einfügen (Teilbestand), eine Zählung
     * nur in Zeilen.
     * @spec SPEC-035
     * @spec TDS-QA-02
     */
    @Test
    fun `der Bestand weist eine Nachricht ueber der Grenze der Zellen ab`() {
        val store = TableStore(listOf(kunden), cellLimit = 10)
        val five = (1L..5L).map { mapOf<String, Any?>("Id" to it) }
        assertEquals(5, insert(store, *five.toTypedArray()))
        val failure = assertFailsWith<IllegalArgumentException> { insert(store, mapOf("Id" to 6L)) }
        assertEquals("Bestand voll", failure.message)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), stored(store).map { it[0] })
    }

    private fun change(store: TableStore, set: Map<String, Any?>, where: RowFilter?): Long =
        runBlocking { requireNotNull(tableOf(store).onUpdate)(set, where) }

    private fun key(value: Any?) = RowFilter(listOf(FilterTerm.In("Id", listOf(value))))

    /**
     * Happy: `onUpdate` der Tabelle des Bestands ändert genau die Zeilen, auf die der Filter zutrifft, und liefert ihre
     * Zahl; eine nicht genannte Spalte bleibt, ein Schlüssel ohne Treffer ändert nichts (Zahl 0), ohne Filter
     * treffen alle.
     * Wächter: ein `onUpdate`, das alle Zeilen ändert, den Filter nicht liest oder eine andere Zeile trifft.
     * @spec SPEC-035
     */
    @Test
    fun `onUpdate aendert die Zeilen des Filters und zaehlt sie`() {
        val store = TableStore(listOf(kunden))
        insert(store, mapOf("Id" to 1L, "Name" to "Ada"), mapOf("Id" to 2L, "Name" to "Bob"))

        assertEquals(1, change(store, mapOf("Name" to "Ada L."), key(1L)))
        assertEquals(0, change(store, mapOf("Name" to "x"), key(9L)))
        assertEquals(listOf(listOf<Any?>(1L, "Ada L."), listOf<Any?>(2L, "Bob")), stored(store))

        assertEquals(2, change(store, mapOf("Name" to null), null))
        assertEquals(listOf(listOf<Any?>(1L, null), listOf<Any?>(2L, null)), stored(store))
    }

    /**
     * Negative: ein Wert falscher Klasse, NULL in einer nicht nullbaren Spalte, eine unbekannte Spalte und eine
     * Änderung,
     * die den Schlüssel doppelt machte, werfen und lassen den Bestand unverändert (auch die Zeilen vor dem Fehler).
     * Wächter: ein `onUpdate`, das vor der Prüfung schreibt.
     * @spec SPEC-035
     */
    @Test
    fun `onUpdate weist ungueltige Aenderungen ab und laesst den Bestand`() {
        val store = TableStore(listOf(kunden))
        insert(store, mapOf("Id" to 1L, "Name" to "Ada"), mapOf("Id" to 2L, "Name" to "Bob"))
        val before = stored(store)

        assertFailsWith<IllegalArgumentException> { change(store, mapOf("Name" to 5), key(1L)) }
        assertFailsWith<IllegalArgumentException> { change(store, mapOf("Id" to null), key(1L)) }
        assertFailsWith<IllegalArgumentException> { change(store, mapOf("Fehlt" to "x"), key(1L)) }
        assertFailsWith<IllegalArgumentException> { change(store, mapOf("Id" to 7L), null) }
        assertEquals(before, stored(store))
        assertEquals(1, change(store, mapOf("Id" to 3L), key(2L)))
        assertEquals(1, insert(store, mapOf("Id" to 2L)))
    }

    /**
     * Happy (Zusammenspiel): ein `MERGE` von d-migrate über das Backend des Bestands fügt bei fehlendem Schlüssel ein
     * (`INSERT`), lässt bei `skip` den vorhandenen unverändert und ändert bei `update` (`UPDATE`); die Auswahl danach
     * zeigt die Zeilen. Der Text ist der gemessene Wortlaut (2026-11-07).
     * Wächter: ein Bestand ohne `onInsert`/`onUpdate` (das `MERGE` wäre `TDS_UNSUPPORTED_SQL`), ein Einfügen ohne
     * Schlüsselprüfung.
     * @spec SPEC-036
     */
    @Test
    fun `ein MERGE fuegt ein, laesst bei skip und aendert bei update`() {
        val backend = TableStore(listOf(kunden)).backend()
        val head = "MERGE INTO [dbo].[kunden] AS tgt USING (VALUES ( @P0 ,  @P1 )) AS src ([Id], [Name]) " +
            "ON tgt.[Id] = src.[Id] "
        val matched = "WHEN MATCHED THEN UPDATE SET tgt.[Name] = src.[Name] "
        val tail = "WHEN NOT MATCHED THEN INSERT ([Id], [Name]) VALUES (src.[Id], src.[Name]) OUTPUT \$action;"
        fun merge(update: Boolean, id: Long, name: String) = ask(
            backend,
            head + (if (update) matched else "") + tail,
            QueryParameter("@P0", SqlType.Int64, id),
            QueryParameter("@P1", SqlType.UnicodeText, name),
        ).filterIsInstance<QueryEvent.Row>().map { it.values.single() }
        fun names() = ask(backend, "SELECT Id, Name FROM kunden").filterIsInstance<QueryEvent.Row>().map { it.values }

        assertEquals(listOf<Any?>("INSERT"), merge(update = false, id = 1, name = "Ada"))
        assertEquals(emptyList(), merge(update = false, id = 1, name = "anders"))
        assertEquals(listOf(listOf<Any?>(1L, "Ada")), names())
        assertEquals(listOf<Any?>("UPDATE"), merge(update = true, id = 1, name = "Ada L."))
        assertEquals(listOf<Any?>("INSERT"), merge(update = true, id = 2, name = "Bob"))
        assertEquals(listOf(listOf<Any?>(1L, "Ada L."), listOf<Any?>(2L, "Bob")), names())
    }

    /** Ein Bestand mit der Tabelle `notizen` ohne Schlüssel und zwei gleichen Zeilen und einer dritten. */
    private fun keyless(): TableStore {
        val store = TableStore(emptyList())
        ask(store.backend(), "CREATE TABLE [notizen] ([Id] INT NOT NULL, [Text] NVARCHAR(10));")
        val table = store.catalog().tables.single()
        val same = mapOf("Id" to 1, "Text" to "a")
        runBlocking { requireNotNull(table.onInsertBatch)(listOf(same, same, mapOf("Id" to 2, "Text" to "b"))) }
        return store
    }

    private fun notizen(store: TableStore) = store.catalog().tables.single()

    /**
     * Happy (Setzung nach der Semantik von `UPDATE`, am Server nicht gemessen): `onUpdate` auf einer Tabelle ohne
     * Schlüssel ändert beliebig viele Zeilen, auch alle ohne Filter, und macht Zeilen gleich, ohne Eindeutigkeit zu
     * prüfen; der Zähler ist die Zahl der getroffenen Zeilen. Gegenprobe: dieselbe Änderung auf einer Tabelle mit
     * Schlüssel wirft weiter (`onUpdate weist ungueltige Aenderungen ab und laesst den Bestand`).
     * Wächter: eine Eindeutigkeitsprüfung über den leeren Schlüssel in `update` (jede Änderung von mehr als einer
     * Zeile wiese ab, `Schlüssel nicht eindeutig`).
     * @spec SPEC-035
     */
    @Test
    fun `onUpdate aendert auf einer Tabelle ohne Schluessel mehrere Zeilen`() {
        val store = keyless()
        val table = notizen(store)
        val where = RowFilter(listOf(FilterTerm.In("Id", listOf(1))))
        assertEquals(2, runBlocking { requireNotNull(table.onUpdate)(mapOf("Text" to "x"), where) })
        assertEquals(3, runBlocking { requireNotNull(table.onUpdate)(mapOf("Text" to "y"), null) })
        val rows = ask(store.backend(), "SELECT Id, Text FROM notizen").filterIsInstance<QueryEvent.Row>()
        assertEquals(listOf(listOf<Any?>(1, "y"), listOf<Any?>(1, "y"), listOf<Any?>(2, "y")), rows.map { it.values })
    }

    /**
     * Grenze (Setzung, am Server nicht gemessen; der echte Server nimmt ein `MERGE` gegen eine Tabelle ohne Schlüssel
     * an): ein `MERGE` gegen eine Tabelle mit leerem Schlüssel endet mit `TDS_UNSUPPORTED_SQL`, weil der Vergleich
     * genau den Schlüssel nennen muss; der Bestand bleibt unverändert. Benannt in `SPEC-036` (Form M).
     * Wächter: ein `MERGE`, das gegen den leeren Schlüssel einfügte oder änderte (der Bestand änderte sich).
     * @spec SPEC-036
     */
    @Test
    fun `ein MERGE gegen eine Tabelle ohne Schluessel ist abgewiesen`() {
        val store = keyless()
        val sql = "MERGE INTO [dbo].[notizen] AS tgt USING (VALUES ( @P0 ,  @P1 )) AS src ([Id], [Text]) " +
            "ON tgt.[Id] = src.[Id] WHEN MATCHED THEN UPDATE SET tgt.[Text] = src.[Text] " +
            "WHEN NOT MATCHED THEN INSERT ([Id], [Text]) VALUES (src.[Id], src.[Text]) OUTPUT \$action;"
        val events = ask(
            store.backend(),
            sql,
            QueryParameter("@P0", SqlType.Int32, 3),
            QueryParameter("@P1", SqlType.UnicodeText, "c"),
        )
        assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(events.single()).code)
        val rows = ask(store.backend(), "SELECT Id, Text FROM notizen").filterIsInstance<QueryEvent.Row>()
        assertEquals(3, rows.size)
        assertEquals(listOf(listOf<Any?>(1, "a"), listOf<Any?>(1, "a"), listOf<Any?>(2, "b")), rows.map { it.values })
    }

    /**
     * Happy: ein `UPDATE` als SQL-Text ändert die Zeile im Bestand und meldet den Zähler 1; ein Schlüssel ohne Treffer
     * meldet 0. Wächter: ein Bestand ohne `onUpdate` (`TDS_UNSUPPORTED_SQL`).
     * @spec SPEC-035
     */
    @Test
    fun `ein UPDATE als SQL-Text aendert die Zeile des Bestands`() {
        val store = TableStore(listOf(kunden))
        insert(store, mapOf("Id" to 1L, "Name" to "Ada"))
        val backend = store.backend()

        assertEquals(
            listOf<QueryEvent>(QueryEvent.UpdateCount(1, StatementKind.UPDATE)),
            ask(backend, "UPDATE kunden SET Name = N'Ada L.' WHERE Id = 1"),
        )
        assertEquals(
            listOf<QueryEvent>(QueryEvent.UpdateCount(0, StatementKind.UPDATE)),
            ask(backend, "UPDATE kunden SET Name = N'x' WHERE Id = 9"),
        )
        assertEquals(listOf(listOf<Any?>(1L, "Ada L.")), stored(store))
    }
}
