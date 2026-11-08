package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests von Form M (`MERGE … OUTPUT $action`, `SPEC-036`, `SPEC-035`) gegen eine zählende Attrappe: die Zerlegung in
 * ein
 * Lesen mit dem Schlüssel als Vorfilter und höchstens einen Callback, die Ausgabezeile `$action`, die Prüfung des
 * Schlüssels durch die Bibliothek selbst, die Abweisungen vor dem Lesen und die Fehler der Quelle und der Callbacks.
 * Der Wortlaut ist der von d-migrate 1.12.0 (mssql-jdbc 13.4.0), gemessen am 2026-11-07 gegen den echten SQL Server
 * (Messreihe mit mssql-jdbc im Messprotokoll der Referenzabgleiche); die Antworten (Spalte `$action`, `INSERT` und
 * `UPDATE`, keine Zeile ohne Aktion) folgen dieser Messung.
 */
class SimulationBackendMergeTest {
    private val columns = listOf(
        SimColumn("Id", SqlType.Int64, nullable = false),
        SimColumn("Name", SqlType.UnicodeText),
        SimColumn("Eintritt", SqlType.Date),
    )

    /** Welchen Callback zum Einfügen die Tabelle hat. */
    private enum class Via { NONE, SINGLE, BATCH }

    /** Was die Attrappe scheitern lässt. */
    private enum class Failing { NOTHING, SOURCE, CALLBACK }

    /** Die Callbacks einer Tabelle `kunden`; jeder zählt seine Aufrufe und hält Werte und Filter des letzten. */
    private inner class Fixture(
        val existing: List<List<Any?>> = emptyList(),
        val via: Via = Via.SINGLE,
        val update: Boolean = true,
        val failure: Failing = Failing.NOTHING,
        val result: Long = 1,
        val keyType: SqlType = SqlType.Int64,
    ) {
        /** Die Länge der Spalte `Name` in Zeichen für die Tabelle, die [simTable] beim nächsten Zugriff baut. */
        var nameLength: Int = MAX_TEXT_LENGTH

        var opens = 0
        var closes = 0
        var inserts = 0
        var updates = 0
        var inserted: Map<String, Any?>? = null
        var set: Map<String, Any?>? = null
        var filter: RowFilter? = null
        var query: SimQuery? = null

        private fun answer(): Long {
            check(failure != Failing.CALLBACK) { "Geheimnis des Callbacks" }
            return result
        }

        val simTable get() = SimTable(
            name = "kunden",
            columns = listOf(SimColumn("Id", keyType, nullable = false)) + columns.drop(1).map {
                if (it.name == "Name") it.copy(maxLength = nameLength) else it
            },
            primaryKey = listOf("Id"),
            rows = { q ->
                opens++
                query = q
                check(failure != Failing.SOURCE) { "Geheimnis der Quelle" }
                val rows = existing.iterator()
                object : RowSource {
                    override suspend fun next(): List<Any?>? = if (rows.hasNext()) rows.next() else null

                    override fun close() {
                        closes++
                    }
                }
            },
            onInsert = if (via == Via.SINGLE) {
                { v ->
                    inserts++
                    inserted = v
                    answer()
                }
            } else {
                null
            },
            onInsertBatch = if (via == Via.BATCH) {
                { rows ->
                    inserts++
                    inserted = rows.single()
                    answer()
                }
            } else {
                null
            },
            onUpdate = if (update) {
                { s, w ->
                    updates++
                    set = s
                    filter = w
                    answer()
                }
            } else {
                null
            },
        )

        val backend get() = SimulationBackend(SimCatalog(listOf(simTable)))

        val calls: Int get() = inserts + updates
    }

    private val skip = "MERGE INTO [dbo].[kunden] AS tgt USING (VALUES ( @P0 ,  @P1 ,  @P2 )) AS src ([Id], [Name], " +
        "[Eintritt]) ON tgt.[Id] = src.[Id] WHEN NOT MATCHED THEN INSERT ([Id], [Name], [Eintritt]) " +
        "VALUES (src.[Id], " +
        "src.[Name], src.[Eintritt]) OUTPUT \$action;"

    private val update = "MERGE INTO [dbo].[kunden] AS tgt USING (VALUES ( @P0 ,  @P1 ,  @P2 )) AS src " +
        "([Id], [Name], " +
        "[Eintritt]) ON tgt.[Id] = src.[Id] WHEN MATCHED THEN UPDATE SET tgt.[Name] = src.[Name], tgt.[Eintritt] = " +
        "src.[Eintritt] WHEN NOT MATCHED THEN INSERT ([Id], [Name], [Eintritt]) VALUES (src.[Id], src.[Name], " +
        "src.[Eintritt]) OUTPUT \$action;"

    private fun parameters(id: Long? = 1, name: String? = "Ada", date: LocalDate? = LocalDate.of(2026, 1, 2)) = listOf(
        QueryParameter("@P0", SqlType.Int64, id),
        QueryParameter("@P1", SqlType.UnicodeText, name),
        QueryParameter("@P2", SqlType.Date, date),
    )

    private fun run(table: Fixture, sql: String, params: List<QueryParameter> = parameters()) =
        collectEvents(table.backend, sql, params)

    private val actionStart = QueryEvent.ResultStart(
        listOf(ResultColumn("\$action", SqlType.UnicodeText, nullable = false, maxLength = 10)),
        StatementKind.MERGE,
    )

    private fun existingRow(id: Long) = listOf<Any?>(id, "vorhanden", LocalDate.of(2020, 2, 2))

    /**
     * Happy: der Schlüssel fehlt, die Bibliothek ruft `onInsert` einmal mit allen Spalten unter den Namen der
     * Beschreibung und antwortet mit der Zeile `INSERT` (Messprotokoll M-13: Ergebnis `$action`, nicht nullbar, eine
     * Zeile je Aktion). Die Zeilenquelle wird mit dem Schlüssel als Vorfilter geöffnet und genau einmal geschlossen.
     * @spec SPEC-036
     */
    @Test
    fun `fehlender Schluessel ruft onInsert und antwortet INSERT`() {
        val table = Fixture(existing = listOf(existingRow(7)))

        val events = run(table, skip)

        assertEquals(
            listOf(actionStart, QueryEvent.Row(listOf("INSERT")), QueryEvent.ResultEnd(1)),
            events,
        )
        assertEquals(mapOf("Id" to 1L, "Name" to "Ada", "Eintritt" to LocalDate.of(2026, 1, 2)), table.inserted)
        assertEquals(1, table.inserts)
        assertEquals(0, table.updates)
        assertEquals(1, table.opens)
        assertEquals(1, table.closes)
        assertEquals(RowFilter(listOf(FilterTerm.In("Id", listOf(1L)))), table.query?.filter)
    }

    /**
     * Happy: der Schlüssel ist da, ohne den Zweig `WHEN MATCHED` ruft die Bibliothek keinen Callback und antwortet
     * mit dem Ergebnis `$action` ohne Zeile (Messprotokoll M-13: `COLMETADATA` ohne ROW, Zähler 0).
     * @spec SPEC-036
     */
    @Test
    fun `vorhandener Schluessel ohne Update-Zweig ruft nichts und antwortet ohne Zeile`() {
        val table = Fixture(existing = listOf(existingRow(1)))

        val events = run(table, skip)

        assertEquals(listOf(actionStart, QueryEvent.ResultEnd(0)), events)
        assertEquals(0, table.calls)
        assertEquals(1, table.closes)
    }

    /**
     * Happy: mit dem Zweig `WHEN MATCHED` ruft die Bibliothek `onUpdate` mit den Zuweisungen als `set` und dem
     * Schlüssel als `where`, die Schlüsselspalte steht nicht im `set`, die Antwort ist `UPDATE`.
     * @spec SPEC-036
     */
    @Test
    fun `vorhandener Schluessel mit Update-Zweig ruft onUpdate und antwortet UPDATE`() {
        val table = Fixture(existing = listOf(existingRow(1)))

        val events = run(table, update)

        assertEquals(listOf(actionStart, QueryEvent.Row(listOf("UPDATE")), QueryEvent.ResultEnd(1)), events)
        assertEquals(mapOf("Name" to "Ada", "Eintritt" to LocalDate.of(2026, 1, 2)), table.set)
        assertEquals(RowFilter(listOf(FilterTerm.In("Id", listOf(1L)))), table.filter)
        assertEquals(1, table.updates)
        assertEquals(0, table.inserts)
    }

    /**
     * Happy: mit dem Zweig `WHEN MATCHED` und fehlendem Schlüssel fügt die Bibliothek ein (`INSERT`); hat die Tabelle
     * nur `onInsertBatch`, ruft sie ihn mit einer Liste aus einer Zeile.
     * @spec SPEC-036
     */
    @Test
    fun `fehlender Schluessel mit Update-Zweig fuegt ein, auch ueber onInsertBatch`() {
        val single = Fixture()
        val viaBatch = Fixture(via = Via.BATCH)

        for (table in listOf(single, viaBatch)) {
            val events = run(table, update)

            assertEquals(listOf(actionStart, QueryEvent.Row(listOf("INSERT")), QueryEvent.ResultEnd(1)), events)
            assertEquals(1, table.inserts)
            assertEquals(0, table.updates)
            assertEquals(1L, table.inserted?.get("Id"))
        }
    }

    /**
     * Boundary: die Bibliothek prüft den Schlüssel der gelieferten Zeilen selbst; eine Zeile mit anderem Schlüssel,
     * die die Quelle trotz Vorfilter liefert, zählt nicht als Treffer (der vorhandene Schlüssel 2 gegen den Wert 1).
     * @spec SPEC-036
     */
    @Test
    fun `die Bibliothek prueft den Schluessel der gelieferten Zeilen selbst`() {
        val table = Fixture(existing = listOf(existingRow(2), existingRow(3)))

        val events = run(table, skip)

        assertEquals(QueryEvent.Row(listOf("INSERT")), events[1])
        assertEquals(1, table.inserts)
    }

    /**
     * Boundary: NULL im Schlüssel trifft nie (dreiwertige Logik); die Zeilenquelle wird nicht geöffnet, die
     * Bibliothek fügt ein und überlässt die Nullbarkeit dem Integrator.
     * @spec SPEC-036
     */
    @Test
    fun `NULL im Schluessel trifft nie und oeffnet die Zeilenquelle nicht`() {
        val table = Fixture(existing = listOf(existingRow(1)))

        val events = run(table, skip, parameters(id = null))

        assertEquals(QueryEvent.Row(listOf("INSERT")), events[1])
        assertEquals(0, table.opens)
        assertEquals(mapOf("Id" to null, "Name" to "Ada", "Eintritt" to LocalDate.of(2026, 1, 2)), table.inserted)
    }

    /**
     * Boundary: NULL als Parameter für Name und Datum geht an den Callback (jede Spalte nimmt NULL als Parameter).
     * @spec SPEC-036
     */
    @Test
    fun `NULL in Name und Datum geht an den Callback`() {
        val table = Fixture()

        run(table, skip, parameters(name = null, date = null))

        assertEquals(mapOf("Id" to 1L, "Name" to null, "Eintritt" to null), table.inserted)
    }

    /**
     * Boundary: ein `Int64`-Parameter in einer `Int32`-Spalte gilt im Wertebereich (d-migrate sendet jede Ganzzahl als
     * `bigint`, der echte Server nimmt `MERGE` in die `int`-Spalte an, Messreihe mit mssql-jdbc) und ist darüber
     * `TDS_SIM_INVALID_VALUE`, ohne dass etwas gelesen oder gerufen wird.
     * @spec SPEC-035
     */
    @Test
    fun `Int64-Parameter in einer Int32-Spalte gilt im Wertebereich`() {
        val ok = Fixture(keyType = SqlType.Int32)
        run(ok, skip, parameters(id = Int.MAX_VALUE.toLong()))
        assertEquals(Int.MAX_VALUE, ok.inserted?.get("Id"))

        val low = Fixture(keyType = SqlType.Int32)
        run(low, skip, parameters(id = Int.MIN_VALUE.toLong()))
        assertEquals(Int.MIN_VALUE, low.inserted?.get("Id"))

        val over = Fixture(keyType = SqlType.Int32)
        val events = run(over, skip, parameters(id = Int.MAX_VALUE.toLong() + 1))
        assertEquals("TDS_SIM_INVALID_VALUE", assertIs<QueryEvent.Failure>(events.single()).code)
        assertEquals(0, over.opens + over.calls)
    }

    /**
     * Negative: ein Wert, der nicht in den Spaltentyp passt (Text in der Datumsspalte), endet mit
     * `TDS_SIM_INVALID_VALUE` als einzigem Ereignis, bevor die Zeilenquelle geöffnet wird; ein fehlender Parameter
     * ebenso.
     * @spec SPEC-037
     */
    @Test
    fun `ungueltiger Wert endet vor dem Lesen mit TDS_SIM_INVALID_VALUE`() {
        val table = Fixture()
        val wrong = parameters().dropLast(1) + QueryParameter("@P2", SqlType.UnicodeText, "kein Datum")

        val events = run(table, skip, wrong)
        val missing = run(table, skip, parameters().take(2))

        assertEquals("TDS_SIM_INVALID_VALUE", assertIs<QueryEvent.Failure>(events.single()).code)
        assertEquals("TDS_SIM_INVALID_VALUE", assertIs<QueryEvent.Failure>(missing.single()).code)
        assertEquals(0, table.opens + table.calls)
    }

    /**
     * Negative: ohne `onInsert` und `onInsertBatch`, und mit dem Zweig `WHEN MATCHED` ohne `onUpdate`, endet das
     * `MERGE`
     * mit `TDS_UNSUPPORTED_SQL` vor dem Lesen (Entscheidung 8 der ADR: die Abweisung ist Setzung). Kontrollfall: ohne
     * den Zweig braucht die Tabelle `onUpdate` nicht.
     * @spec SPEC-037
     */
    @Test
    fun `fehlender Callback endet mit TDS_UNSUPPORTED_SQL`() {
        val noInsert = Fixture(via = Via.NONE)
        val noUpdate = Fixture(update = false)

        val first = run(noInsert, skip)
        val second = run(noUpdate, update)

        assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(first.single()).code)
        assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(second.single()).code)
        assertEquals(0, noInsert.opens + noInsert.calls + noUpdate.opens + noUpdate.calls)
        assertEquals(QueryEvent.Row(listOf("INSERT")), run(noUpdate, skip)[1])
    }

    /**
     * Negative: ein Fehler der Zeilenquelle steht hinter dem Beginn des Ergebnisses (Messprotokoll M-15: `COLMETADATA`
     * vor dem `ERROR`) als `TDS_BACKEND_ERROR` mit fester Meldung, ohne den Text der Ausnahme; kein Callback wird
     * gerufen.
     * @spec SPEC-037
     */
    @Test
    fun `Fehler der Zeilenquelle steht hinter dem Beginn des Ergebnisses`() {
        val table = Fixture(failure = Failing.SOURCE)

        val events = run(table, skip)

        assertEquals(actionStart, events[0])
        val failure = assertIs<QueryEvent.Failure>(events[1])
        assertEquals("TDS_BACKEND_ERROR", failure.code)
        assertFalse(failure.message.contains("Geheimnis"))
        assertEquals(2, events.size)
        assertEquals(0, table.calls)
    }

    /**
     * Negative: ein Fehler von `onInsert` steht hinter dem Beginn des Ergebnisses als `TDS_SIM_KEY_VIOLATION` (2627,
     * Sammelcode wie beim Massenladen) mit fester Meldung, ohne den Text der Ausnahme; ein Fehler von `onUpdate` ist
     * `TDS_BACKEND_ERROR`; eine negative Zahl ebenso. Meldet der Callback 0, bleibt die Antwort ohne Zeile. Wächter:
     * derselbe Code für beide Callbacks, ein Fehler vor dem Beginn des Ergebnisses.
     * @spec SPEC-036
     * @spec SPEC-037
     */
    @Test
    fun `Fehler von onInsert ist 2627, Fehler von onUpdate und negative Zahl sind TDS_BACKEND_ERROR`() {
        val thrown = Fixture(failure = Failing.CALLBACK)
        val updating = Fixture(existing = listOf(existingRow(1)), failure = Failing.CALLBACK)
        val negative = Fixture(result = -1)
        val none = Fixture(result = 0)

        val first = run(thrown, skip)
        val second = run(updating, update)
        val third = run(negative, skip)
        val fourth = run(none, skip)

        val codes = listOf(first, second, third).map { events ->
            assertEquals(actionStart, events[0])
            assertEquals(2, events.size)
            assertIs<QueryEvent.Failure>(events[1]).code
        }
        assertEquals(listOf("TDS_SIM_KEY_VIOLATION", "TDS_BACKEND_ERROR", "TDS_BACKEND_ERROR"), codes)
        assertFalse(assertIs<QueryEvent.Failure>(first[1]).message.contains("Geheimnis"))
        assertFalse(assertIs<QueryEvent.Failure>(second[1]).message.contains("Geheimnis"))
        assertEquals(1, updating.updates)
        assertEquals(listOf(actionStart, QueryEvent.ResultEnd(0)), fourth)
    }

    /**
     * Negative (Messprotokoll M-15, 2628): ein Text über der Länge seiner Spalte endet hinter dem Beginn des
     * Ergebnisses mit `TDS_SIM_STRING_TRUNCATED`, ohne Callback, beim Einfügen wie beim Ändern; die Zeilenquelle wurde
     * gelesen. Boundary: ein Text genau in der Länge der Spalte geht an den Callback; eine Spalte mit `maxLength` -1
     * hat keine Grenze. Der Wert zählt nur in der Aktion, die ausgeführt wird: bei vorhandenem Schlüssel ohne Zweig
     * `WHEN MATCHED` bleibt ein zu langer Text ohne Fehler. Wächter: eine Prüfung vor dem Beginn des Ergebnisses, eine
     * Prüfung für die nicht ausgeführte Aktion, ein Callback trotz Fehler, eine Grenze von 4000 statt der Spalte.
     * @spec SPEC-036
     * @spec SPEC-035
     */
    @Test
    fun `ein Text ueber der Laenge der Spalte endet mit 2628 ohne Callback`() {
        val tooLong = parameters(name = "x".repeat(6))
        val exact = parameters(name = "x".repeat(5))
        val inserting = Fixture().apply { nameLength = 5 }
        val updating = Fixture(existing = listOf(existingRow(1))).apply { nameLength = 5 }
        val skipping = Fixture(existing = listOf(existingRow(1))).apply { nameLength = 5 }
        val unbounded = Fixture().apply { nameLength = UNBOUNDED_TEXT }

        val inserted = run(inserting, skip, tooLong)
        val changed = run(updating, update, tooLong)
        val skipped = run(skipping, skip, tooLong)
        val fits = run(Fixture().apply { nameLength = 5 }, skip, exact)
        val free = run(unbounded, skip, parameters(name = "x".repeat(MAX_TEXT_LENGTH)))

        for (events in listOf(inserted, changed)) {
            assertEquals(actionStart, events[0])
            assertEquals("TDS_SIM_STRING_TRUNCATED", assertIs<QueryEvent.Failure>(events[1]).code)
            assertEquals(2, events.size)
        }
        assertEquals(1, inserting.opens)
        assertEquals(1, updating.opens)
        assertEquals(0, inserting.calls + updating.calls)
        assertEquals(listOf(actionStart, QueryEvent.ResultEnd(0)), skipped)
        assertEquals(QueryEvent.Row(listOf("INSERT")), fits[1])
        assertEquals(QueryEvent.Row(listOf("INSERT")), free[1])
    }

    /**
     * Negative: `stop` vor dem Sammeln lässt Zeilenquelle und Callback unberührt.
     * @spec SPEC-036
     */
    @Test
    fun `eine beendete Ausfuehrung liest nichts und ruft nichts`() {
        val table = Fixture()
        runBlocking {
            val execution = table.backend.execute(queryRequest(skip, parameters()))
            execution.close()
            assertEquals(emptyList(), execution.events.toList())
        }

        assertEquals(0, table.opens + table.calls)
    }

    /**
     * Negative (Erkennung): was nicht genau die gemessene Gestalt hat, ist `TDS_UNSUPPORTED_SQL`, bevor etwas gelesen
     * wird: ein Vergleich über eine Nicht-Schlüsselspalte, ein `SET` auf die Schlüsselspalte, zwei `VALUES`-Zeilen,
     * ein anderes `OUTPUT`, ein Zweig `DELETE`, ein falscher Alias; die Meldung nennt den Grund. Eine unbekannte
     * Spalte ist `TDS_SIM_UNKNOWN_OBJECT`, eine unbekannte Tabelle ebenso.
     * @spec SPEC-036
     * @spec SPEC-037
     */
    @Test
    fun `nicht erkannte Gestalten enden vor dem Lesen`() {
        val table = Fixture()
        val key = "MERGE vergleicht nicht genau den Schlüssel"
        val token = "UNEXPECTED_TOKEN"
        val unsupported = listOf(
            skip.replace("ON tgt.[Id] = src.[Id]", "ON tgt.[Name] = src.[Name]") to key,
            update.replace("UPDATE SET tgt.[Name] = src.[Name], ", "UPDATE SET tgt.[Id] = src.[Id], ") to key,
            skip.replace("( @P0 ,  @P1 ,  @P2 )", "( @P0 ,  @P1 ,  @P2 ), ( @P0 ,  @P1 ,  @P2 )") to token,
            skip.replace("OUTPUT \$action", "OUTPUT inserted.[Id]") to token,
            skip.replace("OUTPUT \$action;", "") to "UNEXPECTED_END",
            skip.replace("WHEN NOT MATCHED THEN INSERT", "WHEN NOT MATCHED THEN DELETE; INSERT") to token,
            skip.replace("tgt.[Id] = src.[Id]", "x.[Id] = src.[Id]") to token,
            skip.replace("ON tgt.[Id] = src.[Id]", "ON tgt.[Id] = src.[Id] OR tgt.[Id] = src.[Id]") to token,
            skip.replace("([Id], [Name], [Eintritt]) ON", "([Id], [Name]) ON") to "COUNT_MISMATCH",
        )
        for ((sql, reason) in unsupported) {
            assertTrue(sql != skip && sql != update, "die Abwandlung ändert den Text: $sql")
            val events = run(table, sql)

            val failure = assertIs<QueryEvent.Failure>(events.single(), sql)
            assertEquals("TDS_UNSUPPORTED_SQL", failure.code, sql)
            assertTrue(failure.message.contains(reason), "$reason in ${failure.message}: $sql")
        }
        val unknownColumn = run(
            table,
            skip.replace("INSERT ([Id], [Name], [Eintritt])", "INSERT ([Id], [Fehlt], [Eintritt])"),
        )
        val unknownTable = run(table, skip.replace("[kunden]", "[gibtesnicht]"))
        assertEquals("TDS_SIM_UNKNOWN_OBJECT", assertIs<QueryEvent.Failure>(unknownColumn.single()).code)
        assertEquals("TDS_SIM_UNKNOWN_OBJECT", assertIs<QueryEvent.Failure>(unknownTable.single()).code)
        assertEquals(0, table.opens + table.calls)
    }

    /**
     * Boundary (Erkennung): der Text gilt ohne das abschließende `;`, mit Zeilenumbrüchen, ohne Beachtung der
     * Schreibweise
     * der Schlüsselwörter und mit anderen Aliasen, solange sie durchgängig sind (Setzung); die Reihenfolge der Spalten
     * der Quelle darf von der der Tabelle abweichen.
     * @spec SPEC-036
     */
    @Test
    fun `Schreibweise, Umbrueche und Aliase aendern die Erkennung nicht`() {
        val table = Fixture()
        val text = "merge into dbo.kunden as t\nusing (values (@P0, @P1, @P2)) as s (a, b, c)\non t.id = s.a\n" +
            "when not matched then insert (name, id, eintritt) values (s.b, s.a, s.c)\noutput \$ACTION"

        val events = run(table, text)

        assertEquals(QueryEvent.Row(listOf("INSERT")), events[1])
        assertEquals(mapOf("Name" to "Ada", "Id" to 1L, "Eintritt" to LocalDate.of(2026, 1, 2)), table.inserted)
        assertTrue(events.last() is QueryEvent.ResultEnd)
    }

    /**
     * Negative: ein Abbruch (`cancel`), der zwischen dem Ende des Lesens und dem Aufruf des Callbacks eintrifft, lässt
     * den Callback ungerufen; die Antwort hat keine Zeile. Wächter: eine Prüfung von `stopped` nur am Anfang des
     * Flusses.
     * @spec SPEC-036
     */
    @Test
    fun `ein Abbruch zwischen Lesen und Callback schreibt nichts`() {
        var inserts = 0
        lateinit var backend: SimulationBackend
        val simTable = SimTable(
            name = "kunden",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = {
                RowSource {
                    backend.cancel("s1", "r1")
                    null
                }
            },
            onInsert = {
                inserts++
                1L
            },
        )
        backend = SimulationBackend(SimCatalog(listOf(simTable)))

        val events = collectEvents(backend, skip, parameters())

        assertEquals(listOf(actionStart, QueryEvent.ResultEnd(0)), events)
        assertEquals(0, inserts)
    }

    /**
     * Boundary: ein Schlüssel aus zwei Spalten wird in beliebiger Reihenfolge verglichen und gilt nur, wenn beide
     * Spalten
     * gleich sind; ein Vergleich über nur eine der beiden Schlüsselspalten ist `TDS_UNSUPPORTED_SQL`.
     * @spec SPEC-036
     */
    @Test
    fun `ein Schluessel aus zwei Spalten gilt nur mit beiden Spalten`() {
        val paarColumns = listOf(
            SimColumn("A", SqlType.Int64, nullable = false),
            SimColumn("B", SqlType.Int64, nullable = false),
            SimColumn("Name", SqlType.UnicodeText),
        )
        val inserted = mutableListOf<Map<String, Any?>>()
        val simTable = SimTable(
            name = "paar",
            columns = paarColumns,
            primaryKey = listOf("A", "B"),
            rows = {
                val rows = listOf(listOf<Any?>(1L, 2L, "x")).iterator()
                RowSource { if (rows.hasNext()) rows.next() else null }
            },
            onInsert = { values ->
                inserted += values
                1L
            },
        )
        val backend = SimulationBackend(SimCatalog(listOf(simTable)))
        val head = "MERGE INTO [dbo].[paar] AS tgt USING (VALUES ( @P0 ,  @P1 ,  @P2 )) AS src ([A], [B], [Name]) "
        val tail = "WHEN NOT MATCHED THEN INSERT ([A], [B], [Name]) VALUES (src.[A], src.[B], src.[Name]) " +
            "OUTPUT \$action;"
        val both = head + "ON tgt.[B] = src.[B] AND tgt.[A] = src.[A] " + tail
        fun pair(a: Long, b: Long) = listOf(
            QueryParameter("@P0", SqlType.Int64, a),
            QueryParameter("@P1", SqlType.Int64, b),
            QueryParameter("@P2", SqlType.UnicodeText, "n"),
        )

        assertEquals(listOf(actionStart, QueryEvent.ResultEnd(0)), collectEvents(backend, both, pair(1, 2)))
        assertEquals(QueryEvent.Row(listOf("INSERT")), collectEvents(backend, both, pair(1, 3))[1])
        assertEquals(QueryEvent.Row(listOf("INSERT")), collectEvents(backend, both, pair(2, 2))[1])
        assertEquals(listOf(1L to 3L, 2L to 2L), inserted.map { it["A"] as Long to it["B"] as Long })
        val onlyOne = collectEvents(backend, head + "ON tgt.[A] = src.[A] " + tail, pair(1, 2))
        assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(onlyOne.single()).code)
    }

    /**
     * Boundary: zwei gleichzeitige `MERGE` desselben Backends mit demselben neuen Schlüssel laufen nacheinander:
     * solange der erste im Callback steht, öffnet der zweite die Zeilenquelle nicht; nach dessen Ende sieht er die
     * Zeile, der erste antwortet `INSERT`, der zweite ohne Zeile. Wächter: Prüfen und Ändern ohne gemeinsame Sperre
     * (beide läsen die leere Quelle und meldeten `INSERT`).
     * @spec SPEC-036
     */
    @Test
    fun `zwei gleichzeitige MERGE desselben Schluessels laufen nacheinander`() {
        val stored = CopyOnWriteArrayList<List<Any?>>()
        val opens = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val simTable = SimTable(
            name = "kunden",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = {
                opens.incrementAndGet()
                val rows = stored.toList().iterator()
                RowSource { if (rows.hasNext()) rows.next() else null }
            },
            onInsert = { values ->
                entered.countDown()
                release.await()
                stored += listOf(values["Id"], values["Name"], values["Eintritt"])
                1L
            },
        )
        val backend = SimulationBackend(SimCatalog(listOf(simTable)))

        var first: List<QueryEvent> = emptyList()
        var second: List<QueryEvent> = emptyList()
        val a = thread { first = runBlocking { collectOne(backend, "r1") } }
        entered.await()
        val b = thread { second = runBlocking { collectOne(backend, "r2") } }
        Thread.sleep(WAIT_FOR_SECOND_MILLIS)
        val openedMeanwhile = opens.get()
        release.countDown()
        a.join()
        b.join()

        assertEquals(1, openedMeanwhile)
        assertEquals(listOf(actionStart, QueryEvent.Row(listOf("INSERT")), QueryEvent.ResultEnd(1)), first)
        assertEquals(listOf(actionStart, QueryEvent.ResultEnd(0)), second)
        assertEquals(1, stored.size)
    }

    /**
     * Boundary: ein Abbruch beendet das Lesen vor der nächsten Zeile (die Quelle liefert den passenden Schlüssel
     * erst in
     * der zweiten Zeile, der Abbruch trifft in der ersten ein: es wird nur einmal `next()` gerufen) und vor dem
     * Öffnen der
     * Quelle (der Abbruch trifft nach dem Beginn des Ergebnisses ein: die Quelle wird nicht geöffnet); in beiden Fällen
     * bleibt der Callback ungerufen. Wächter: die Prüfungen von `stopped` im Lesen.
     * @spec SPEC-036
     */
    @Test
    fun `ein Abbruch beendet das Lesen vor der naechsten Zeile und vor dem Oeffnen`() {
        val nexts = AtomicInteger()
        val opens = AtomicInteger()
        var inserts = 0
        lateinit var backend: SimulationBackend
        val simTable = SimTable(
            name = "kunden",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = {
                opens.incrementAndGet()
                val rows = listOf(listOf<Any?>(2L, "x", null), existingRow(1)).iterator()
                RowSource {
                    nexts.incrementAndGet()
                    backend.cancel("s1", "r1")
                    if (rows.hasNext()) rows.next() else null
                }
            },
            onInsert = {
                inserts++
                1L
            },
        )
        backend = SimulationBackend(SimCatalog(listOf(simTable)))

        val reading = collectEvents(backend, skip, parameters())

        assertEquals(listOf(actionStart, QueryEvent.ResultEnd(0)), reading)
        assertEquals(1, nexts.get())
        opens.set(0)
        val opening = runBlocking {
            val execution = backend.execute(queryRequest(skip, parameters(), "r2"))
            try {
                buildList {
                    execution.events.collect {
                        add(it)
                        if (it is QueryEvent.ResultStart) backend.cancel("s1", "r2")
                    }
                }
            } finally {
                execution.close()
            }
        }
        assertEquals(listOf(actionStart, QueryEvent.ResultEnd(0)), opening)
        assertEquals(0, opens.get())
        assertEquals(0, inserts)
    }

    /**
     * Boundary: ein Callback, der im `MERGE` hängt, hält die Sperre des Backends: ein zweites `MERGE` mit anderem
     * Schlüssel endet erst nach der Freigabe. Wächter: ein `MERGE` ohne gemeinsame Sperre (das zweite endete sofort).
     * @spec SPEC-036
     */
    @Test
    fun `ein haengender Callback haelt die Sperre fuer weitere MERGE`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val simTable = SimTable(
            name = "kunden",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
            onInsert = { values ->
                if (values["Id"] == 1L) {
                    entered.countDown()
                    release.await()
                }
                1L
            },
        )
        val backend = SimulationBackend(SimCatalog(listOf(simTable)))
        val secondDone = AtomicBoolean(false)

        val a = thread { runBlocking { collectOne(backend, "r1") } }
        entered.await()
        val b = thread {
            runBlocking { collectOne(backend, "r2", parameters(id = 2)) }
            secondDone.set(true)
        }
        Thread.sleep(WAIT_FOR_SECOND_MILLIS)
        val doneWhileBlocked = secondDone.get()
        release.countDown()
        a.join()
        b.join()

        assertFalse(doneWhileBlocked)
        assertTrue(secondDone.get())
    }

    /**
     * Boundary: die Sperre ist nicht reentrant: ein Callback, der dasselbe Backend mit einem `MERGE` ruft und die
     * Antwort
     * abwartet, kommt nicht zurück (hier nach 300 ms abgebrochen); der äußere `MERGE` endet danach regulär. Wächter:
     * eine
     * Sperre, die denselben Aufrufer zulässt, oder keine Sperre.
     * @spec SPEC-036
     */
    @Test
    fun `ein Callback, der dasselbe Backend aufruft und abwartet, kommt nicht zurueck`() {
        var nested: List<QueryEvent>? = emptyList()
        lateinit var backend: SimulationBackend
        val simTable = SimTable(
            name = "kunden",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = { RowSource { null } },
            onInsert = { values ->
                if (values["Id"] == 1L) {
                    nested = withTimeoutOrNull(NESTED_TIMEOUT_MILLIS) { collectOne(backend, "r9", parameters(id = 2)) }
                }
                1L
            },
        )
        backend = SimulationBackend(SimCatalog(listOf(simTable)))

        val outer = collectEvents(backend, skip, parameters())

        assertEquals(null, nested)
        assertEquals(listOf(actionStart, QueryEvent.Row(listOf("INSERT")), QueryEvent.ResultEnd(1)), outer)
    }

    private suspend fun collectOne(
        backend: SimulationBackend,
        requestId: String,
        params: List<QueryParameter> = parameters(),
    ): List<QueryEvent> {
        val execution = backend.execute(queryRequest(skip, params, requestId))
        return try {
            execution.events.toList()
        } finally {
            execution.close()
        }
    }

    private companion object {
        const val WAIT_FOR_SECOND_MILLIS = 1000L
        const val NESTED_TIMEOUT_MILLIS = 300L
    }
}
