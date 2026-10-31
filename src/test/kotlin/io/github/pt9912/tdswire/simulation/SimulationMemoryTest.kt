package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Speichergrenze des Simulations-Backends: eine Zeilenquelle mit mehr Text, als der Heap des Testlaufs fasst,
 * wird an einer Senke gelesen, die auf Zeilen wartet; das Backend hält das Ergebnis nicht vollständig. Der Heap des
 * Testlaufs ist `maxHeapSize` in `gradle/test-jvm.init.gradle.kts` (128 MiB); der Test prüft den Wert zur Laufzeit
 * gegen `Runtime.maxMemory()`. Die Aussage gilt für diesen Testlauf mit dieser Senke, nicht für das Produkt unter
 * Last.
 */
class SimulationMemoryTest {
    private val rowCount = 1_200_000
    private val textChars = 128

    /** Eine Zeilenquelle, die jede Zeile frisch erzeugt (ein Text von [textChars] Zeichen) und die Abnahmen zählt. */
    private class Generated(val rows: Int, val chars: Int) {
        var pulled = 0
            private set

        val backend = SimulationBackend(
            SimCatalog(
                listOf(
                    SimTable(
                        name = "big",
                        columns = listOf(SimColumn("id", SqlType.Int32, false), SimColumn("text", SqlType.UnicodeText)),
                        primaryKey = listOf("id"),
                        rows = {
                            RowSource {
                                if (pulled < rows) listOf(++pulled, String(CharArray(chars) { 'x' })) else null
                            }
                        },
                    ),
                ),
            ),
        )
    }

    /**
     * Speichergrenze und Zähler: 1 200 000 Zeilen zu 128 Zeichen haben allein als Text mehr Bytes, als der Heap des
     * Testlaufs fasst (Untergrenze: ein Byte je Zeichen). Die Senke wartet nach jeder 100 000. Zeile 50 ms; zu jeder
     * abgenommenen Zeile ist die Quelle genau bis zu dieser Zeile gelesen, vor und nach der Wartezeit (der Zähler
     * hat eine Untergrenze und eine Obergrenze: weniger wäre eine fehlende Zeile, mehr ein Vorauslesen). Das
     * Ergebnis kommt vollständig an. Die Senke bricht nach 90 Sekunden mit einem roten Test ab, damit ein Rückfall
     * (etwa das Behalten aller Zeilen) nicht als hängender Lauf endet. Der Beleg hat einen Messpunkt: 153,6 MB Text
     * gegen 134,2 MB Heap, 14 % darüber; er trägt "nicht vollständig gehalten", nicht "Speicher unabhängig von der
     * Zeilenzahl".
     * @spec SPEC-031
     */
    @Test
    fun `ein Ergebnis ueber dem Heap wird an einer wartenden Senke Zeile fuer Zeile gelesen`() = runBlocking {
        val heap = Runtime.getRuntime().maxMemory()
        assertTrue(rowCount.toLong() * textChars > heap, "der Test ist zu klein für den Heap: $heap")
        val source = Generated(rowCount, textChars)
        val tally = Tally()

        try {
            drain(source, tally)
        } catch (error: OutOfMemoryError) {
            throw AssertionError("Heap-Überlauf: das Ergebnis wurde im Speicher gehalten", error)
        }

        assertEquals(rowCount, tally.received)
        assertEquals(0, tally.behind, "Zeilen ohne gelesene Quelle")
        assertEquals(0, tally.ahead, "Quelle liest der Senke voraus")
        assertEquals(rowCount, source.pulled)
        assertEquals(QueryEvent.ResultEnd(rowCount.toLong()), tally.end)
    }

    /** Liest das Ergebnis an einer Senke, die nach jeder [CHECK_EVERY]. Zeile wartet; die Ausführung lebt nur hier. */
    private suspend fun drain(source: Generated, tally: Tally) {
        val execution = source.backend.execute(queryRequest("SELECT id, text FROM big"))
        val deadline = System.nanoTime() + LIMIT_NANOS
        execution.events.collect { event ->
            when (event) {
                is QueryEvent.Row -> {
                    if (System.nanoTime() > deadline) throw AssertionError("Zeitgrenze bei Zeile ${tally.received}")
                    tally.received++
                    tally.compare(source.pulled)
                    if (tally.received % CHECK_EVERY == 0) {
                        delay(SLOW_MILLIS)
                        tally.compare(source.pulled)
                    }
                }

                is QueryEvent.ResultEnd -> tally.end = event

                else -> Unit
            }
        }
        execution.close()
    }

    /** Was die Senke sah: abgenommene Zeilen und wie oft die Quelle weniger ([behind]) oder mehr ([ahead]) las. */
    private class Tally {
        var received = 0
        var behind = 0
        var ahead = 0
        var end: QueryEvent? = null

        fun compare(pulled: Int) {
            if (pulled < received) behind++
            if (pulled > received) ahead++
        }
    }

    private companion object {
        const val CHECK_EVERY = 100_000
        const val SLOW_MILLIS = 50L
        const val LIMIT_NANOS = 90_000_000_000L
    }
}
