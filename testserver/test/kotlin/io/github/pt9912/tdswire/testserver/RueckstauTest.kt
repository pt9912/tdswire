package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.simulation.SimQuery
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val SEED = 42L
private const val ROWS = 50L
private const val TAKE = 3
private const val STAND_FIELDS = 8
private const val LONG_BLOCK_MS = 5_000L
private const val SHORT_BLOCK_MS = 400L
private const val HEAD_START_MS = 100L
private const val PROMPT_LIMIT_MS = 2_000L
private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Die Bausteine der Rückstau-Messung (`tools/rueckstau.sh`) ohne Server: die Zähler des Backends, die Anfrageangaben,
 * die Arten des Wartens und die Tabellen des Aufbaus C. Die Wartezeiten der Tests haben nur Untergrenzen oder eine
 * Obergrenze weit über dem Erwartungswert, damit Last des Hosts sie nicht kippt.
 */
internal class RueckstauTest {
    private fun backend(zaehler: RueckstauZaehler, wartender: Wartender) =
        RueckstauBackend(SEED, ROWS, zaehler, wartender)

    private val threads = Executors.newCachedThreadPool { task -> Thread(task).apply { isDaemon = true } }
        .asCoroutineDispatcher()

    private fun request(sql: String) = QueryRequest("s", "r", sql, emptyList(), null)

    /** Das Backend zählt, was es dem Strom übergibt; ein Leser, der früher aufhört, lässt den Rest ungezählt. */
    @Test
    fun `das Backend zaehlt nur die Zeilen, die der Strom gezogen hat`() = runBlocking<Unit> {
        val zaehler = RueckstauZaehler()
        Wartender(1).use { wartender ->
            val events = backend(zaehler, wartender).execute(request("SELECT * FROM ref_zeilen")).events
            val taken = events.take(1 + TAKE).toList()
            assertEquals(TAKE, taken.count { it is QueryEvent.Row })
            assertEquals(TAKE.toLong(), zaehler.erzeugt.get())
            assertEquals(1L, zaehler.abgebrochen.get())
            assertEquals(0L, zaehler.beendet.get())
            assertEquals(0, zaehler.offen.get())
            backend(zaehler, wartender).execute(request("SELECT * FROM ref_zeilen /*zeilen=5*/")).events.toList()
            assertEquals(TAKE + 5L, zaehler.erzeugt.get())
            assertEquals(1L, zaehler.beendet.get())
        }
    }

    /** Die Angaben hinter dem Text wählen Zeilen, Art, Dauer und Takt des Wartens; ohne Angabe gilt die Vorgabe. */
    @Test
    fun `die Angaben der Anfrage werden gelesen`() {
        assertEquals(Anfrage(7, Warten.SUSPENDIEREND, 0, 1), Anfrage.of(null, 7))
        assertEquals(
            Anfrage(9, Warten.NICHT_UNTERBRECHBAR, 250, 3),
            Anfrage.of("zeilen=9,modus=nicht-unterbrechbar,block=250,jede=3", 7),
        )
        assertFailsWith<IllegalStateException> { Anfrage.of("modus=schnell", 7) }
    }

    /** Die Tabelle der Zähler liefert eine Zeile mit acht Feldern, die Werte folgen dem Zähler. */
    @Test
    fun `die Tabelle der Zaehler liefert eine Zeile`() = runBlocking<Unit> {
        val zaehler = RueckstauZaehler()
        zaehler.erzeugt.addAndGet(11)
        Wartender(1).use { wartender ->
            val events = backend(zaehler, wartender).execute(request("SELECT * FROM rueckstau_stand")).events.toList()
            val row = assertIs<QueryEvent.Row>(events[1])
            assertEquals(STAND_FIELDS, row.values.size)
            assertEquals(11L, row.values[0])
            val other = backend(zaehler, wartender).execute(request("DELETE FROM ref_zeilen"))
            assertIs<QueryEvent.Failure>(other.events.toList().single())
        }
    }

    /** Ein unterbrechbarer Aufruf endet mit dem Abbruch, lange vor dem Ende der Wartezeit. */
    @Test
    fun `ein unterbrechbarer Aufruf endet mit dem Abbruch`() = runBlocking<Unit> {
        Wartender(1).use { wartender ->
            val job = launch(threads) { wartender.warte(Warten.UNTERBRECHBAR, LONG_BLOCK_MS) }
            delay(HEAD_START_MS)
            val start = System.nanoTime()
            job.cancelAndJoin()
            assertTrue((System.nanoTime() - start) / NANOS_PER_MILLI < PROMPT_LIMIT_MS)
        }
    }

    /** Ein nicht unterbrechbarer Aufruf hält den Abbruch bis zum Ende seiner Wartezeit auf. */
    @Test
    fun `ein nicht unterbrechbarer Aufruf haelt den Abbruch auf`() = runBlocking<Unit> {
        Wartender(1).use { wartender ->
            val start = System.nanoTime()
            val job = launch(threads, CoroutineStart.UNDISPATCHED) {
                wartender.warte(Warten.NICHT_UNTERBRECHBAR, SHORT_BLOCK_MS)
            }
            delay(HEAD_START_MS)
            job.cancelAndJoin()
            assertTrue((System.nanoTime() - start) / NANOS_PER_MILLI >= SHORT_BLOCK_MS)
        }
    }

    /**
     * Der direkte Aufruf schläft im Thread des Aufrufers: eine zweite Coroutine auf demselben Thread läuft erst
     * nach der Wartezeit, bei `suspendierend` (Kontrolle) sofort.
     */
    @Test
    fun `der direkte Aufruf blockiert den Thread des Aufrufers`() = runBlocking<Unit> {
        val single = Executors.newSingleThreadExecutor { task -> Thread(task).apply { isDaemon = true } }
            .asCoroutineDispatcher()
        Wartender(1).use { wartender ->
            for ((modus, blockiert) in listOf(Warten.DIREKT to true, Warten.SUSPENDIEREND to false)) {
                val start = System.nanoTime()
                val first = launch(single) { wartender.warte(modus, SHORT_BLOCK_MS) }
                val second = async(single) { (System.nanoTime() - start) / NANOS_PER_MILLI }
                val secondRanAfterMs = second.await()
                first.join()
                val message = "$modus: zweite Coroutine nach $secondRanAfterMs ms"
                assertEquals(blockiert, secondRanAfterMs >= SHORT_BLOCK_MS, message)
            }
        }
        single.close()
    }

    /** Die Quelle von `ref_zeilen` zählt beim Ziehen, der Stapel-Callback den Import; Abbruch steht im Zähler. */
    @Test
    fun `die Tabellen des Aufbaus C zaehlen Quelle und Import`() = runBlocking<Unit> {
        val zaehler = RueckstauZaehler()
        Wartender(1).use { wartender ->
            val table = rueckstauTabellen(ROWS, SEED, zaehler, wartender, Callback(Warten.SUSPENDIEREND, 0)).first()
            val source = table.rows(SimQuery(table.columns, null))
            repeat(TAKE) { assertTrue(source.next() != null) }
            assertEquals(TAKE.toLong(), zaehler.erzeugt.get())
            assertEquals(1, zaehler.offen.get())
            source.close()
            assertEquals(1L, zaehler.abgebrochen.get())
            val insert = table.onInsertBatch ?: error("onInsertBatch fehlt")
            val names = table.columns.map { it.name }
            val batch = (1..TAKE).map { n -> names.zip(ReferenzZeilen.row(SEED, n.toLong())).toMap() }
            assertEquals(TAKE.toLong(), insert(batch))
            assertEquals(TAKE.toLong(), zaehler.importiert.get())
        }
    }
}
