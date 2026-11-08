package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Das Massenladen im Simulations-Backend (`SPEC-036` Form B, `SPEC-035` Weitergabe, `ADR-0033` Nr. 5): Erkennung von
 * `INSERT BULK`, die Probe der Zieltabelle (`SET FMTONLY`), die Probe der Kollation, Zuordnung und Überführung der
 * Werte einer Nachricht, Weitergabe an `onInsertBatch` oder `onInsert`. Die Texte sind die, die d-migrate 1.12.0 mit
 * mssql-jdbc 13.4.0 sendet (`docs/messungen/bulk-load-2026-11-07.md`); die Erwartungen sind von Hand geschrieben. Die
 * Überführung der Werte der Nachricht ist Setzung, nicht am Server gemessen.
 */
class SimulationBackendBulkTest {
    private val columns = listOf(
        SimColumn("Id", SqlType.Int64, nullable = false),
        SimColumn("Name", SqlType.UnicodeText),
        SimColumn("Eintritt", SqlType.Date),
        SimColumn("Preis", SqlType.Decimal(10, 2)),
        SimColumn("Aktiv", SqlType.Bool),
        SimColumn("Zeit", SqlType.Time),
        SimColumn("Stempel", SqlType.DateTime),
        SimColumn("Zahl", SqlType.Int32),
    )

    /** Die Callbacks der Tabelle `kunden`: sie halten, was sie bekommen. */
    private class Sink(val batch: Boolean = true, val single: Boolean = false) {
        val batches = mutableListOf<List<Map<String, Any?>>>()
        val singles = mutableListOf<Map<String, Any?>>()
        var failAt: Int? = null
        var result: Long? = null
        var opens = 0
    }

    private fun backend(sink: Sink): SimulationBackend = SimulationBackend(
        SimCatalog(
            listOf(
                SimTable(
                    name = "kunden",
                    columns = columns,
                    primaryKey = listOf("Id"),
                    rows = {
                        sink.opens++
                        RowSource { null }
                    },
                    onInsert = if (sink.single) {
                        { values ->
                            if (sink.singles.size == sink.failAt) error("Callback")
                            sink.singles += values
                            1
                        }
                    } else {
                        null
                    },
                    onInsertBatch = if (sink.batch) {
                        { rows ->
                            if (sink.failAt != null) error("Callback")
                            sink.batches += rows
                            sink.result ?: rows.size.toLong()
                        }
                    } else {
                        null
                    },
                ),
            ),
        ),
    )

    private fun ask(backend: SimulationBackend, sql: String): List<QueryEvent> = runBlocking {
        val execution = backend.execute(QueryRequest("s", "r", sql, emptyList(), null))
        try {
            execution.events.toList()
        } finally {
            execution.close()
        }
    }

    private fun message(vararg values: Pair<ResultColumn, List<Any?>>): BulkRows {
        val rowCount = values.first().second.size
        return BulkRows(values.map { it.first }, List(rowCount) { row -> values.map { it.second[row] } })
    }

    private fun load(backend: SimulationBackend, rows: BulkRows): QueryEvent =
        runBlocking { backend.load(BulkTarget("dbo", "kunden"), rows) }

    private fun col(name: String, type: SqlType) = ResultColumn(name, type, nullable = true)

    private val insertBulk =
        "INSERT BULK [dbo].[kunden] ([Id] BIGINT , [Name] NVARCHAR(4000) , [Eintritt] VARCHAR(10) )  " +
            "with (CHECK_CONSTRAINTS, FIRE_TRIGGERS, ROWS_PER_BATCH = 3)"

    /**
     * Happy: der Text des Treibers ergibt das Ziel in der Schreibweise der Beschreibung (auch bei anderer Schreibweise,
     * ohne Schema und mit nur dem Namen), ohne einen Callback zu rufen; als SQL-Text über `execute` zählt er 0.
     * Wächter: ein Ziel in der Schreibweise des Textes, ein Callback-Aufruf bei der Erkennung, eine Antwort ungleich 0.
     *
     * @spec SPEC-036
     */
    @Test
    fun `INSERT BULK ergibt das Ziel ohne Callback`() {
        val sink = Sink()
        val backend = backend(sink)
        val target = BulkTargetResult.Ready(BulkTarget("dbo", "kunden"))
        assertEquals(target, backend.bulkTarget(insertBulk))
        assertEquals(target, backend.bulkTarget("insert bulk KUNDEN (id BIGINT)"))
        assertEquals(target, backend.bulkTarget("INSERT BULK [kunden] ([Id] BIGINT) with (TABLOCK);"))
        assertEquals(listOf<QueryEvent>(QueryEvent.UpdateCount(0)), ask(backend, insertBulk))
        assertEquals(emptyList(), sink.batches)
        assertEquals(0, sink.opens)
    }

    /**
     * Negativ: eine unbekannte Tabelle und eine unbekannte Spalte (`TDS_SIM_UNKNOWN_OBJECT`), eine Tabelle ohne
     * Callback
     * für Zeilen (`TDS_UNSUPPORTED_SQL`) lehnen `INSERT BULK` ab, bevor eine Nachricht 0x07 folgt; ein Text, der keines
     * ist, bleibt `null` (er geht den üblichen Weg), auch ein verstümmelter `INSERT BULK` (er endet dort mit
     * `TDS_UNSUPPORTED_SQL`). Wächter: ein Ziel für eine Tabelle, die nichts annimmt.
     *
     * @spec SPEC-036
     */
    @Test
    fun `INSERT BULK wird abgelehnt, wenn Tabelle, Spalte oder Callback fehlen`() {
        val backend = backend(Sink())
        val unknown = BulkTargetResult.Rejected("TDS_SIM_UNKNOWN_OBJECT", "Tabelle nicht beschrieben")
        assertEquals(unknown, backend.bulkTarget("INSERT BULK [dbo].[nirgends] ([Id] BIGINT)"))
        val column = BulkTargetResult.Rejected("TDS_SIM_UNKNOWN_OBJECT", "Spalte nicht beschrieben")
        assertEquals(column, backend.bulkTarget("INSERT BULK [dbo].[kunden] ([Fehlt] BIGINT)"))
        val none = backend(Sink(batch = false))
        assertEquals(
            BulkTargetResult.Rejected("TDS_UNSUPPORTED_SQL", "Einfügen für diese Tabelle nicht verfügbar"),
            none.bulkTarget(insertBulk),
        )
        assertNull(backend.bulkTarget("SELECT * FROM kunden"))
        assertNull(backend.bulkTarget("INSERT INTO kunden (Id) VALUES (1)"))
        assertNull(backend.bulkTarget("INSERT BULK [dbo].[kunden] ([Id] BIGINT"))
        val broken = ask(backend, "INSERT BULK [dbo].[kunden] ([Id] BIGINT")
        assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(broken.single()).code)
        assertNull(backend.bulkTarget("INSERT BULK [dbo].[kunden] ([Id] BIGINT, [ID] BIGINT)"))
    }

    /**
     * Die Probe der Zieltabelle in den drei Formen des Treibers (`set fmtonly on <Auswahl> set fmtonly off`,
     * `sp_executesql N'SET FMTONLY ON …'` und mit `EXEC`): Spalten der Auswahl, keine Zeile, die Zeilenquelle wird
     * nicht
     * geöffnet. Wächter: eine geöffnete Quelle, eine Zeile, die Spalten der ganzen Tabelle bei benannter Liste.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Probe mit SET FMTONLY liefert Spalten ohne Zeilen`() {
        val sink = Sink()
        val backend = backend(sink)
        val named = ask(
            backend,
            "set fmtonly on SELECT [Id], [Name], [Eintritt] FROM [dbo].[kunden] WHERE 1 = 0 \nset fmtonly off",
        )
        val namedColumns = assertIs<QueryEvent.ResultStart>(named.first()).columns.map { it.name }
        assertEquals(listOf("Id", "Name", "Eintritt"), namedColumns)
        assertEquals(listOf(QueryEvent.ResultEnd(0)), named.drop(1))
        val all = ask(backend, "sp_executesql N'SET FMTONLY ON SELECT * FROM [dbo].[kunden] '")
        assertEquals(columns.map { it.name }, assertIs<QueryEvent.ResultStart>(all.first()).columns.map { it.name })
        assertEquals(listOf(QueryEvent.ResultEnd(0)), all.drop(1))
        val exec = ask(backend, "EXEC sp_executesql N'SET FMTONLY ON SELECT * FROM kunden'")
        assertEquals(2, exec.size)
        val noWhere = ask(backend, "SET FMTONLY ON SELECT Id FROM kunden SET FMTONLY OFF")
        assertEquals(listOf(QueryEvent.ResultEnd(0)), noWhere.drop(1))
        assertEquals(0, sink.opens)
    }

    /**
     * Negativ: `sp_executesql` mit einem Text ohne `SET FMTONLY ON` und `SET FMTONLY ON` vor einer anderen Anweisung
     * als
     * der Auswahl sind nicht erkannt (`TDS_UNSUPPORTED_SQL`); die Form bleibt auf die Probe begrenzt.
     *
     * @spec SPEC-036
     */
    @Test
    fun `sp_executesql ohne SET FMTONLY und FMTONLY vor einer Aenderung sind nicht erkannt`() {
        val backend = backend(Sink())
        for (sql in listOf(
            "sp_executesql N'SELECT * FROM [dbo].[kunden]'",
            "SET FMTONLY ON DELETE FROM kunden",
            "SET FMTONLY ON",
        )) {
            assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(ask(backend, sql).single()).code, sql)
        }
    }

    /**
     * Die Probe der Kollation mit dem Namen als Literal (so sendet `SQLServerBulkCopy` sie): eine Zeile je Spalte, für
     * Text die Kollation (Setzung `SQL_Latin1_General_CP1_CI_AS`), sonst NULL, `is_computed` falsch; eine unbekannte
     * Tabelle ergibt keine Zeile. Wächter: eine Zeile je Tabelle, eine Kollation für Zahlen.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Probe der Kollation liefert eine Zeile je Spalte`() {
        val backend = backend(Sink())
        val text = "select collation_name, is_computed from sys.columns where object_id=OBJECT_ID('[dbo].[kunden]') " +
            "order by column_id ASC"
        val events = ask(backend, text)
        val names = assertIs<QueryEvent.ResultStart>(events.first()).columns.map { it.name }
        assertEquals(listOf("collation_name", "is_computed"), names)
        val rows = events.filterIsInstance<QueryEvent.Row>().map { it.values }
        assertEquals(columns.size, rows.size)
        assertEquals(listOf("SQL_Latin1_General_CP1_CI_AS", false), rows[1])
        assertEquals(listOf(null, false), rows[0])
        val none = ask(backend, text.replace("kunden", "nirgends"))
        assertEquals(0, none.filterIsInstance<QueryEvent.Row>().size)
    }

    /**
     * Happy: die Zeilen einer Nachricht gehen in einem Aufruf an `onInsertBatch`, die Werte in den Typ der Spalte
     * überführt und unter den Namen der Beschreibung (die Nachricht nennt `id` kleingeschrieben): `Long` bleibt `Long`,
     * ein `Long` im Bereich wird `Int` für eine `Int32`-Spalte, Text wird Datum, Zeit und Zeitpunkt (der Zeitpunkt mit
     * Leerzeichen und Nachkommastelle, wie der Treiber ihn sendet), `BigDecimal` bleibt, ein Wahrheitswert bleibt; die
     * Antwort ist die Zahl, die der Callback liefert. Wächter: eine Überführung, die fehlt (der Callback bekäme
     * `String`),
     * ein Aufruf je Zeile, ein Name in der Schreibweise der Nachricht.
     *
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `die Zeilen gehen in einem Aufruf an den Stapel-Callback und sind ueberfuehrt`() {
        val sink = Sink().also { it.result = 5 }
        val event = load(
            backend(sink),
            message(
                col("id", SqlType.Int64) to listOf(1L, 2L),
                col("Name", SqlType.UnicodeText) to listOf("Ada", null),
                col("Eintritt", SqlType.UnicodeText) to listOf("2026-01-02", null),
                col("Preis", SqlType.Decimal(10, 2)) to listOf(BigDecimal("12.50"), null),
                col("Aktiv", SqlType.Bool) to listOf(true, null),
                col("Zeit", SqlType.UnicodeText) to listOf("13:14:15", null),
                col("Stempel", SqlType.UnicodeText) to listOf("2026-01-02 13:14:15.0", null),
                col("Zahl", SqlType.Int64) to listOf(7L, null),
            ),
        )
        assertEquals(QueryEvent.UpdateCount(5), event)
        val rows = sink.batches.single()
        assertEquals(
            mapOf(
                "Id" to 1L,
                "Name" to "Ada",
                "Eintritt" to LocalDate.of(2026, 1, 2),
                "Preis" to BigDecimal("12.50"),
                "Aktiv" to true,
                "Zeit" to LocalTime.of(13, 14, 15),
                "Stempel" to LocalDateTime.of(2026, 1, 2, 13, 14, 15),
                "Zahl" to 7,
            ),
            rows[0],
        )
        assertEquals(mapOf<String, Any?>("Id" to 2L) + columns.drop(1).associate { it.name to null }, rows[1])
        assertEquals(emptyList(), sink.singles)
    }

    /**
     * Happy und Negativ: `Int` und `Long` der Nachricht gelten in einer `Int16`-Spalte von −32768 bis 32767 und in
     * einer `UInt8`-Spalte von 0 bis 255 und kommen als `Int` beim Callback an; jeder Wert um eins daneben endet mit
     * `TDS_SIM_INVALID_VALUE`, ohne dass der Callback gerufen wird.
     * Wächter: eine Überführung ohne Bereichsprüfung (der Callback bekäme 256 in einer `UInt8`-Spalte).
     *
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `Int16 und UInt8 nehmen Zahlen der Nachricht nur im Bereich an`() {
        val seen = mutableListOf<Map<String, Any?>>()
        val backend = SimulationBackend(
            SimCatalog(
                listOf(
                    SimTable(
                        name = "zahlen",
                        columns = listOf(
                            SimColumn("Id", SqlType.Int64, nullable = false),
                            SimColumn("Klein", SqlType.Int16),
                            SimColumn("Winzig", SqlType.UInt8),
                        ),
                        primaryKey = listOf("Id"),
                        rows = { RowSource { null } },
                        onInsertBatch = { rows ->
                            seen += rows
                            rows.size.toLong()
                        },
                    ),
                ),
            ),
        )
        fun send(klein: Any?, winzig: Any?) = runBlocking {
            backend.load(
                BulkTarget("dbo", "zahlen"),
                message(
                    col("Id", SqlType.Int64) to listOf(1L),
                    col("Klein", SqlType.Int64) to listOf(klein),
                    col("Winzig", SqlType.Int64) to listOf(winzig),
                ),
            )
        }

        assertEquals(QueryEvent.UpdateCount(1), send(-32768L, 0))
        assertEquals(QueryEvent.UpdateCount(1), send(32767, 255L))
        val first = mapOf<String, Any?>("Id" to 1L, "Klein" to -32768, "Winzig" to 0)
        val second = mapOf<String, Any?>("Id" to 1L, "Klein" to 32767, "Winzig" to 255)
        assertEquals(listOf(first, second), seen)
        for ((klein, winzig) in listOf(-32769 to 0, 32768 to 0, 0 to -1, 0 to 256)) {
            val failure = assertIs<QueryEvent.Failure>(send(klein, winzig), "$klein $winzig")
            assertEquals("TDS_SIM_INVALID_VALUE", failure.code, "$klein $winzig")
        }
        assertEquals(2, seen.size)
    }

    /**
     * Negativ: ein Wert, der nicht in die Spalte passt, endet mit `TDS_SIM_INVALID_VALUE`, bevor der Callback eine
     * Zeile
     * sieht, auch wenn er in der letzten Zeile steht: `Long` außerhalb von `Int32`, Text für eine Ganzzahl, ein
     * ungültiges Datum, eine Dezimalzahl mit zu vielen Nachkommastellen, ein Zeitpunkt mit Zeitzone, eine Zahl
     * für einen Text. Wächter: ein Teilbestand beim Integrator, eine Überführung, die abschneidet oder rundet.
     *
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `ein Wert, der nicht passt, ruft keinen Callback`() {
        val cases = listOf(
            col("Zahl", SqlType.Int64) to 3_000_000_000L,
            col("Id", SqlType.UnicodeText) to "x",
            col("Eintritt", SqlType.UnicodeText) to "2026-13-45",
            col("Preis", SqlType.Decimal(20, 3)) to BigDecimal("1.234"),
            col("Stempel", SqlType.UnicodeText) to "2026-01-02 13:14:15+02:00",
            col("Name", SqlType.Int64) to 5L,
        )
        for ((column, bad) in cases) {
            val sink = Sink()
            val rows = message(column to listOf(if (column.name == "Zahl") 1L else null, bad))
            val event = load(backend(sink), rows)
            assertEquals("TDS_SIM_INVALID_VALUE", assertIs<QueryEvent.Failure>(event).code, column.name)
            assertEquals(emptyList(), sink.batches, column.name)
        }
    }

    /**
     * Eine Nachricht mit einer unbekannten Spalte (`TDS_SIM_UNKNOWN_OBJECT`) oder zweimal derselben Spalte
     * (`TDS_UNSUPPORTED_SQL`) ruft keinen Callback; eine Nachricht ohne Zeile zählt 0 und ruft ihn nicht.
     *
     * @spec SPEC-036
     */
    @Test
    fun `eine fehlerhafte Zuordnung und eine leere Nachricht rufen keinen Callback`() {
        val sink = Sink()
        val backend = backend(sink)
        val unknown = load(backend, message(col("Fehlt", SqlType.Int64) to listOf(1L)))
        assertEquals("TDS_SIM_UNKNOWN_OBJECT", assertIs<QueryEvent.Failure>(unknown).code)
        val both = message(col("Id", SqlType.Int64) to listOf(1L), col("ID", SqlType.Int64) to listOf(1L))
        val twice = load(backend, both)
        assertEquals("TDS_UNSUPPORTED_SQL", assertIs<QueryEvent.Failure>(twice).code)
        assertEquals(QueryEvent.UpdateCount(0), load(backend, BulkRows(listOf(col("Id", SqlType.Int64)), emptyList())))
        assertEquals(emptyList(), sink.batches)
    }

    /**
     * Ein Fehler des Callbacks endet mit `TDS_SIM_KEY_VIOLATION` (2627, Class 14: so antwortet der echte Server
     * auf den Schlüsselverstoß einer Nachricht, Messprotokoll M-12; Abweichung vom Wortlaut von `ADR-0033` Nr. 5
     * (iii), Folge-ADR offen), eine negative Zahl mit `TDS_BACKEND_ERROR`; beide mit der festen Meldung, die den
     * Text der Ausnahme nie nennt (Setzung nach `ADR-0033` Nr. 7). Wächter: der Text der Ausnahme in der Meldung,
     * ein durchgereichter Fehler, `TDS_BACKEND_ERROR` für den Fehler des Callbacks.
     *
     * @spec SPEC-035
     */
    @Test
    fun `ein Fehler des Callbacks ergibt TDS_SIM_KEY_VIOLATION, eine negative Zahl TDS_BACKEND_ERROR`() {
        val failing = Sink().also { it.failAt = 0 }
        val event = load(backend(failing), message(col("Id", SqlType.Int64) to listOf(1L)))
        assertEquals(QueryEvent.Failure("TDS_SIM_KEY_VIOLATION", "Änderung fehlgeschlagen"), event)
        val negative = Sink().also { it.result = -1 }
        assertEquals(
            QueryEvent.Failure("TDS_BACKEND_ERROR", "Änderung fehlgeschlagen"),
            load(backend(negative), message(col("Id", SqlType.Int64) to listOf(1L))),
        )
    }

    /**
     * Hat die Tabelle nur `onInsert`, geht jede Zeile in der Reihenfolge der Nachricht an ihn, und die Antwort ist die
     * Summe; wirft er bei der dritten Zeile, bleiben die ersten beiden beim Integrator (keine Transaktion) und die
     * Antwort ist `TDS_BACKEND_ERROR`. Wächter: eine umgekehrte Reihenfolge, ein Rückbau, eine Antwort mit der
     * Zahl der Nachricht trotz Fehler.
     *
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `ohne Stapel-Callback geht jede Zeile an onInsert, ein Fehler hinterlaesst den Teilbestand`() {
        val sink = Sink(batch = false, single = true)
        val rows = message(col("Id", SqlType.Int64) to listOf(1L, 2L, 3L))
        assertEquals(QueryEvent.UpdateCount(3), load(backend(sink), rows))
        assertEquals(listOf(1L, 2L, 3L), sink.singles.map { it["Id"] })
        val failing = Sink(batch = false, single = true).also { it.failAt = 2 }
        val event = load(backend(failing), rows)
        assertEquals("TDS_SIM_KEY_VIOLATION", assertIs<QueryEvent.Failure>(event).code)
        assertEquals(listOf(1L, 2L), failing.singles.map { it["Id"] })
    }
}
