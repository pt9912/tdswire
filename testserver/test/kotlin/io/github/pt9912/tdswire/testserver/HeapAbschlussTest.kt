package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val LOOPBACK = "127.0.0.1"
private const val PACKET = 4096
private const val READ_MILLIS = 30_000
private const val ROWS = 40L
private const val DELAY_MS = 400L
private const val MICROS_PER_MILLI = 1000L
private const val SEED = 42L
private const val HEAP_FIELDS = 8
private const val BYTE_BITS = 8

/**
 * Die Bausteine der Heap- und Abschluss-Messung (`tools/heap-abschluss.sh`): das Fenster der Stichproben, der Leser der
 * Antwort-Tokens des Messclients und der Weg durch einen echten Server im Prozess (Backend mit verzögertem Abschluss,
 * Rohbytes-Client). Die Wartezeiten haben nur Untergrenzen, damit Last des Hosts die Tests nicht kippt.
 */
internal class HeapAbschlussTest : TempFilesTest() {
    private val started = ArrayList<StartOutcome.Running>()

    @AfterTest
    fun stop() {
        started.forEach { runBlocking { it.server.stop() } }
    }

    /**
     * Eine Stichprobenreihe: der n-te Aufruf von `sample()` sieht `used[n]` und `open[n]`; `gc` ist die Zeit der
     * Speicherbereinigungen, die das Fenster liest (der letzte Wert zählt).
     */
    private fun stichprobe(used: List<Long>, open: List<Int>, gc: MutableList<Long> = mutableListOf(0L)): HeapStichprobe {
        var index = -1
        return HeapStichprobe(
            collect = { index++ },
            usedKb = { used[index] },
            openStreams = { open[index] },
            gcMillis = { gc.last() },
        )
    }

    /**
     * Das Fenster zählt nur Stichproben mit offenem Strom für Minimum, Median, 95. Rang und Maximum (Rang aufgerundet,
     * Median `sortiert[n / 2]`); eine Stichprobe ohne offenen Strom zählt getrennt; das Lesen beginnt ein neues Fenster.
     */
    @Test
    fun `das Fenster zaehlt nur Stichproben mit offenem Strom`() {
        val used = listOf(9_000L, 100L, 300L, 200L, 500L, 400L, 600L, 700L, 800L, 900L, 1_000L, 1_100L, 1_200L, 1_300L)
        val open = listOf(0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2)
        val gc = mutableListOf(10L)
        val heap = stichprobe(used, open, gc)
        repeat(used.size) { heap.sample() }
        gc += 25L
        val window = heap.read()
        assertEquals(13, window.offen)
        assertEquals(1, window.leer)
        assertEquals(100L, window.minKb)
        assertEquals(700L, window.medianKb)
        assertEquals(1_300L, window.p95Kb)
        assertEquals(1_300L, window.maxKb)
        assertEquals(9_000L, window.leerMaxKb)
        assertEquals(15L, window.gcMillis)
        assertEquals(HeapFenster(0, 0, 0, 0, 0, 0, 0, 0), heap.read())
    }

    /** Eine Stichprobe mit einem Wert liefert ihn als Minimum, Median, 95. Rang und Maximum. */
    @Test
    fun `eine einzelne Stichprobe ist Minimum, Median und Maximum`() {
        val heap = stichprobe(listOf(321L), listOf(1))
        heap.sample()
        val window = heap.read()
        assertEquals(listOf(321L, 321L, 321L, 321L), listOf(window.minKb, window.medianKb, window.p95Kb, window.maxKb))
    }

    private fun le(value: Long, count: Int): ByteArray =
        ByteArray(count) { index -> ((value ushr (BYTE_BITS * index)) and 0xFFL).toByte() }

    private fun metadata(vararg names: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x81)
        out.write(le(names.size.toLong(), 2))
        for (name in names) {
            out.write(ByteArray(4))
            out.write(ByteArray(2))
            out.write(0x7F)
            out.write(name.length)
            out.write(name.toByteArray(Charsets.UTF_16LE))
        }
        return out.toByteArray()
    }

    private fun row(vararg values: Long): ByteArray = byteArrayOf(0xD1.toByte()) + values.flatMap { le(it, 8).toList() }

    private fun done(status: Int): ByteArray = byteArrayOf(0xFD.toByte()) + le(status.toLong(), 2) + ByteArray(2) + ByteArray(8)

    /**
     * Ein Token zählt mit dem Paket, in dem sein letztes Byte ankam: die zweite Zeile beginnt im ersten Paket und endet
     * im zweiten; `DONE_MORE` wird am Status gelesen, ein zweites Ergebnis beginnt mit eigenen Spalten.
     */
    @Test
    fun `ein Token zaehlt mit dem Paket seines letzten Bytes`() {
        val all = metadata("Nr", "Wert") + row(1, 2) + row(3, 4) + done(0x11) + metadata("Nr", "Wert") + row(5, 6) + done(0x10)
        val splitAt = metadata("Nr", "Wert").size + row(1, 2).size + 5
        val answer = AbschlussAntwort()
        answer.feed(all.copyOfRange(0, splitAt), 1_000L)
        assertEquals(1, answer.rowsPerResult.single())
        answer.feed(all.copyOfRange(splitAt, all.size), 2_000L)
        assertTrue(answer.complete())
        assertEquals(listOf(2, 1), answer.rowsPerResult)
        assertEquals(listOf(listOf(3L, 4L), listOf(5L, 6L)), answer.lastRows.map { it.values })
        assertEquals(listOf(1_000L, 2_000L), listOf(answer.firstRows[0].micros, answer.lastRows[0].micros))
        assertEquals(listOf(1, 2), listOf(answer.firstRows[0].packet, answer.lastRows[0].packet))
        assertEquals(listOf(true, false), answer.dones.map { it.status and DONE_MORE != 0 })
        assertEquals(listOf(2, 2), answer.dones.map { it.packet })
        assertEquals(2, answer.packets)
    }

    /** Ein Token, das ein Paket überschreitet, bleibt im Puffer; ein unbekanntes Token ist ein Fehler. */
    @Test
    fun `ein unvollstaendiges Token bleibt im Puffer und ein fremdes Token ist ein Fehler`() {
        val answer = AbschlussAntwort()
        answer.feed(metadata("Nr").copyOfRange(0, 5), 1L)
        assertFalse(answer.complete())
        assertFailsWith<IllegalStateException> { AbschlussAntwort().feed(byteArrayOf(0xAA.toByte(), 0), 1L) }
    }

    @Test
    fun `die Angaben der Abschluss-Anfrage werden gelesen`() {
        assertEquals(AbschlussAnfrage(1000, AbschlussForm.ENDE, 0), AbschlussAnfrage.of(null))
        assertEquals(AbschlussAnfrage(7, AbschlussForm.FOLGE, 100), AbschlussAnfrage.of("zeilen=7,form=folge,verzoegerung=100"))
        assertFailsWith<IllegalStateException> { AbschlussAnfrage.of("form=spaeter") }
        assertFailsWith<IllegalArgumentException> { AbschlussAnfrage.of("zeilen=0") }
    }

    private fun startServer(heap: HeapStichprobe): Int {
        val environment = testModeEnvironment(
            file("users.txt", "alice\t{plain}secret\n"),
            EnvKeys.LISTEN_HOST to LOOPBACK,
            EnvKeys.LISTEN_PORT to "0",
        )
        val zaehler = RueckstauZaehler()
        val backend = HeapAbschlussBackend(RueckstauBackend(SEED, ROWS, zaehler, Wartender(1)), heap)
        val outcome = runBlocking { startTestserver(environment, { BackendSource.Custom(backend) }, {}) }
        val running = assertIs<StartOutcome.Running>(outcome)
        started += running
        return running.server.port
    }

    private fun messe(client: RohbytesClient, form: AbschlussForm, delay: Long) = messeAbschluss(client, form, delay, ROWS)

    /**
     * Gegen einen Server im Prozess: die Form `folge` setzt `DONE_MORE` am ersten Abschluss, die Form `ende` nicht; das
     * verzögerte Ereignis hält den Abschluss mindestens die Verzögerung zurück (Untergrenze), und der Stempel des
     * nächsten Ereignisses liegt vor dem Empfang des DONE.
     */
    @Test
    fun `der Abschluss bleibt mindestens die Verzoegerung zurueck`() {
        val port = startServer(stichprobe(listOf(1L), listOf(1)))
        RohbytesClient(LOOPBACK, port, PACKET, READ_MILLIS).use { client ->
            assertTrue(client.login("alice", "secret"))
            for (form in AbschlussForm.entries) {
                val prompt = messe(client, form, 0)
                val delayed = messe(client, form, DELAY_MS)
                assertEquals(form == AbschlussForm.FOLGE, delayed.moreBit, "DONE_MORE bei ${form.schluessel}")
                assertEquals(if (form == AbschlussForm.FOLGE) 2 else 1, delayed.results)
                assertTrue(delayed.zeileEmitBisDone >= DELAY_MS * MICROS_PER_MILLI, "${form.schluessel}: $delayed")
                assertTrue(delayed.naechstesBisDone in 0 until delayed.zeileEmitBisDone, "${form.schluessel}: $delayed")
                assertTrue(delayed.naechstesBisDone < DELAY_MS * MICROS_PER_MILLI / 2, "${form.schluessel}: $delayed")
                assertTrue(prompt.zeileEmitBisDone < delayed.zeileEmitBisDone, "${form.schluessel}: $prompt gegen $delayed")
            }
        }
    }

    /** Die Tabelle `heap_stand` liefert acht Felder und beginnt mit jedem Lesen ein neues Fenster. */
    @Test
    fun `die Tabelle des Heap-Fensters liefert das Fenster und beginnt ein neues`() {
        val heap = stichprobe(listOf(777L), listOf(0))
        heap.sample()
        val port = startServer(heap)
        RohbytesClient(LOOPBACK, port, PACKET, READ_MILLIS).use { client ->
            assertTrue(client.login("alice", "secret"))
            fun fenster(): List<Long> {
                val answer = AbschlussAntwort()
                client.batch("SELECT * FROM $HEAP_TABLE", answer::feed)
                return answer.lastRows.single().values
            }
            val first = fenster()
            assertEquals(HEAP_FIELDS, first.size)
            assertEquals(listOf(0L, 1L, 0L, 0L, 0L, 0L, 777L), first.take(HEAP_FIELDS - 1))
            assertEquals(0L, fenster()[1])
        }
    }
}
