package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.simulation.SimQuery
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SEED = 42L
private const val OTHER_SEED = 43L
private const val ROWS = 25L
private const val NOTE_EVERY = 10L
private const val BATCH = 10

/**
 * Die Bausteine des Lasttests (`make lasttest`, Aufbauten A bis C) ohne Server: der Erzeuger der Zeilen, das eigene
 * Backend des Aufbaus B, die Tabelle des Aufbaus C und die Einstellung aus der Umgebung. Der Lauf `make lasttest` setzt
 * sie über die Prozessgrenze voraus; dieser Test hält fest, was jeder Baustein allein liefert.
 */
internal class LasttestTest {
    private fun events(rows: Long, sql: String): List<QueryEvent> = runBlocking {
        ZeilenBackend(rows, SEED).execute(QueryRequest("s", "r", sql, emptyList(), null)).events.toList()
    }

    /**
     * Zeile n hängt allein von Seed und n ab, hat acht Werte in den Typen der Spalten, Texte der festen Länge und jede
     * zehnte Zeile eine leere Notiz.
     */
    @Test
    fun `der Erzeuger ist deterministisch und folgt den Spalten`() {
        assertEquals(ReferenzZeilen.row(SEED, 7), ReferenzZeilen.row(SEED, 7))
        assertNotEquals(ReferenzZeilen.row(SEED, 7), ReferenzZeilen.row(OTHER_SEED, 7))
        assertNotEquals(ReferenzZeilen.row(SEED, 7), ReferenzZeilen.row(SEED, 8))
        val row = ReferenzZeilen.row(SEED, 1)
        assertEquals(ReferenzZeilen.columns.size, row.size)
        assertEquals(1L, row[0])
        assertIs<Int>(row[1])
        assertEquals(2, assertIs<BigDecimal>(row[2]).scale())
        assertIs<Boolean>(row[3])
        assertEquals(listOf(12, 40, 100), listOf(5, 6, 7).map { (row[it] as String).length })
        assertNull(ReferenzZeilen.row(SEED, NOTE_EVERY)[7])
        assertIs<String>(ReferenzZeilen.row(SEED, NOTE_EVERY + 1)[7])
    }

    /**
     * Der Export liefert Spalten, genau `rows` Zeilen aus dem Erzeuger und den Abschluss; ein anderer Text ist ein
     * Fehler.
     */
    @Test
    fun `das eigene Backend liefert den Export und weist anderes ab`() {
        val all = events(ROWS, "SELECT * FROM ref_zeilen")
        assertIs<QueryEvent.ResultStart>(all.first())
        assertEquals(ROWS.toInt(), all.count { it is QueryEvent.Row })
        assertEquals(QueryEvent.ResultEnd(ROWS), all.last())
        assertEquals(ReferenzZeilen.row(SEED, ROWS), (all[all.size - 2] as QueryEvent.Row).values)
        val failure = assertIs<QueryEvent.Failure>(events(ROWS, "DELETE FROM ref_zeilen").single())
        assertEquals("TDS_UNSUPPORTED_SQL", failure.code)
    }

    /**
     * Die Quelle der Tabelle liefert die Spalten in der Reihenfolge der Anfrage; der Stapel-Callback zählt, speichert
     * nichts und schreibt erst bei einem vollen Vielfachen der Zeilenzahl eine Zeile.
     */
    @Test
    fun `die Tabelle des Aufbaus C liefert Zeilen und zaehlt den Import`() = runBlocking {
        val lines = mutableListOf<String>()
        val table = referenzTabelle(ROWS, SEED, lines::add)
        val query = SimQuery(listOf(table.columns[2], table.columns[0]), null)
        val source = table.rows(query)
        assertEquals(listOf(ReferenzZeilen.row(SEED, 1)[2], 1L), source.next())
        var count = 1
        while (source.next() != null) count++
        assertEquals(ROWS.toInt(), count)
        val batch = List(BATCH) { emptyMap<String, Any?>() }
        val insert = table.onInsertBatch ?: error("onInsertBatch fehlt")
        assertEquals(BATCH.toLong(), insert(batch))
        assertEquals(BATCH.toLong(), insert(batch))
        assertTrue(lines.isEmpty())
        insert(batch)
        assertContentEquals(listOf("$LASTTEST_PREFIX import 30"), lines)
    }

    /**
     * Ohne Angaben gilt Aufbau C mit Senke und Standardzahlen; ein unbekannter Aufbau oder eine unbekannte Senke
     * bricht ab.
     */
    @Test
    fun `die Einstellung liest die Umgebung`() {
        val default = LasttestEinstellung.of(emptyMap())
        assertEquals(LasttestEinstellung(Aufbau.C, 100_000L, REF_DEFAULT_SEED, true), default)
        val set = LasttestEinstellung.of(
            mapOf(
                "LASTTEST_AUFBAU" to "B",
                "LASTTEST_ZEILEN" to "5",
                "LASTTEST_SEED" to "9",
                "LASTTEST_SENKE" to "ohne",
            ),
        )
        assertEquals(LasttestEinstellung(Aufbau.B, 5L, 9L, false), set)
        assertFailsWith<IllegalStateException> { LasttestEinstellung.of(mapOf("LASTTEST_SENKE" to "manchmal")) }
        assertFailsWith<IllegalArgumentException> { LasttestEinstellung.of(mapOf("LASTTEST_AUFBAU" to "D")) }
    }
}
