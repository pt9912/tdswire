package io.github.pt9912.tdswire.protocol.dispatch

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Handle-Tabelle einer Sitzung (`SPEC-030`, `SPEC-001`) ohne Verbindung. Grenze, Zählung und Vergabe sind
 * Festlegungen (Setzung, Voreinstellung, nicht gemessen).
 */
class PreparedHandlesTest {
    private val threads = Executors.newFixedThreadPool(WORKERS).asCoroutineDispatcher()

    @AfterTest
    fun closeThreads() = threads.close()

    /**
     * Die Grenze: 1000 Handles werden vergeben (Werte 1 bis 1000), das 1001. ist `null` und lässt die Tabelle
     * unverändert; nach einem `release` ist wieder Platz, der Wert wird nicht wiederverwendet. Wächter:
     * Grenze 999 oder 1001, `>` statt `>=`, ein wiederverwendeter Wert.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Die Tabelle haelt hoechstens 1000 Handles und vergibt Werte nicht erneut`() = runBlocking {
        val handles = PreparedHandles()
        val given = (1..PreparedHandles.MAX_HANDLES).map { handles.register("select $it") }
        assertEquals((1..PreparedHandles.MAX_HANDLES).toList(), given)
        assertEquals(1000, handles.size())
        assertNull(handles.register("zu viel"))
        assertEquals(1000, handles.size())
        assertEquals("select 1", handles.sql(1))
        assertEquals("select 1000", handles.sql(1000))
        assertNull(handles.sql(1001))
        handles.release(7)
        assertNull(handles.sql(7))
        assertEquals(1001, handles.register("neu"))
        assertNull(handles.register("wieder zu viel"))
    }

    /**
     * Nachschlagen: ein nie vergebenes Handle, `-1` und `0` sind unbekannt; `clear` verwirft alle Handles.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Unbekannte Handles und clear`() = runBlocking {
        val handles = PreparedHandles()
        assertEquals(1, handles.register("a"))
        listOf(-1, 0, 2).forEach { assertNull(handles.sql(it), "$it") }
        handles.clear()
        assertEquals(0, handles.size())
        assertNull(handles.sql(1))
    }

    /**
     * Gleichzeitiger Zugriff auf die Tabelle einer Sitzung: acht Worker auf mehreren Threads vergeben
     * zusammen genau 1000 Handles mit verschiedenen Werten, jedes weitere ist `null`. Wächter: eine Tabelle
     * ohne Sperre (doppelte Werte, verlorene Einträge, mehr als 1000 Handles).
     *
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Parallele Vergabe auf einer Tabelle ergibt verschiedene Werte und haelt die Grenze`() {
        repeat(ROUNDS) {
            val handles = PreparedHandles()
            val given = runBlocking(threads) {
                (1..WORKERS).map { worker ->
                    async { List(PreparedHandles.MAX_HANDLES / WORKERS + EXTRA) { handles.register("w$worker") } }
                }.awaitAll().flatten()
            }
            val granted = given.filterNotNull()
            assertEquals(PreparedHandles.MAX_HANDLES, granted.size)
            assertEquals((1..PreparedHandles.MAX_HANDLES).toSet(), granted.toSet())
            assertEquals(PreparedHandles.MAX_HANDLES, runBlocking { handles.size() })
        }
    }

    private companion object {
        const val ROUNDS = 20
        const val WORKERS = 8
        const val EXTRA = 5
    }
}
