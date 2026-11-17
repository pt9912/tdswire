package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicReference

/** Präfix der Zeilen des Servers der Heap- und Abschluss-Messung; `tools/heap-abschluss.sh` liest sie. */
internal const val HEAP_PREFIX = "heapabschluss:"

/** Die Tabelle des Heap-Fensters: liest die Stichproben seit dem letzten Lesen und beginnt ein neues Fenster. */
internal const val HEAP_TABLE = "heap_stand"

/** Die Tabelle des Abschluss-Ergebnisses `SELECT * FROM abschluss /*zeilen=…,form=…,verzoegerung=…*/` (Aufbau B). */
internal const val ABSCHLUSS_TABLE = "abschluss"

/** Die Tabelle der Wanduhr-Stempel des letzten Laufs von [ABSCHLUSS_TABLE]. */
internal const val ABSCHLUSS_STAND_TABLE = "abschluss_stand"

private const val BYTES_PER_KIB = 1024L
private const val DEFAULT_ROWS = 10_000_000L
private const val DEFAULT_INTERVAL_MS = 500L
private const val DEFAULT_POOL = 64
private const val DEFAULT_ABSCHLUSS_ROWS = 1000L
private const val PERCENTILE = 0.95
private const val DOUBLE_VALUE = 2L

/**
 * Das Fenster der Stichproben des Live-Heaps seit dem letzten Lesen. „Offen“ sind Stichproben, bei denen mindestens ein
 * Strom offen war; die Zahlen des Heaps in KiB gelten nur für sie, die Stichproben ohne offenen Strom ([leer]) zählen
 * getrennt ([leerMaxKb] ist ihr Höchstwert). [gcMillis] ist die Zeit der Speicherbereinigungen in der JVM im Fenster
 * (dazu gehören die Bereinigungen der Stichprobe selbst).
 */
internal data class HeapFenster(
    val offen: Int,
    val leer: Int,
    val minKb: Long,
    val medianKb: Long,
    val p95Kb: Long,
    val maxKb: Long,
    val leerMaxKb: Long,
    val gcMillis: Long,
)

/**
 * Die Stichprobe des Live-Heaps im Serverprozess: [sample] ruft [collect] (Speicherbereinigung), liest danach
 * [usedKb] und [openStreams] und ordnet die Stichprobe nach den offenen Strömen ein; [read] liefert das Fenster seit
 * dem letzten Lesen. Es ist eine Stichprobe nach der Bereinigung, keine Spitze: was zwischen zwei Stichproben
 * geschieht, bleibt unsichtbar (Aussage wie im Spike, `ADR-0006`). Mehrere Threads dürfen [sample] und [read] rufen.
 */
internal class HeapStichprobe(
    private val collect: () -> Unit,
    private val usedKb: () -> Long,
    private val openStreams: () -> Int,
    private val gcMillis: () -> Long,
) {
    private val lock = Any()
    private val loaded = ArrayList<Long>()
    private var idleCount = 0
    private var idleMaxKb = 0L
    private var gcStart = gcMillis()

    /** Eine Stichprobe: Bereinigung, benutzter Heap, dann die Zahl der offenen Ströme. */
    fun sample() {
        collect()
        val used = usedKb()
        val open = openStreams()
        synchronized(lock) {
            if (open > 0) {
                loaded += used
            } else {
                idleCount++
                idleMaxKb = maxOf(idleMaxKb, used)
            }
        }
    }

    /** Das Fenster seit dem letzten Lesen; danach beginnt ein neues. */
    fun read(): HeapFenster = synchronized(lock) {
        val sorted = loaded.sorted()
        val gc = gcMillis()
        val window = HeapFenster(
            offen = sorted.size,
            leer = idleCount,
            minKb = sorted.firstOrNull() ?: 0L,
            medianKb = if (sorted.isEmpty()) 0L else sorted[sorted.size / 2],
            p95Kb = if (sorted.isEmpty()) 0L else sorted[Math.ceil(PERCENTILE * sorted.size).toInt() - 1],
            maxKb = sorted.lastOrNull() ?: 0L,
            leerMaxKb = idleMaxKb,
            gcMillis = gc - gcStart,
        )
        loaded.clear()
        idleCount = 0
        idleMaxKb = 0L
        gcStart = gc
        window
    }

    companion object {
        /** Die Stichprobe der laufenden JVM; die Ströme zählt [zaehler]. */
        fun laufend(zaehler: RueckstauZaehler): HeapStichprobe {
            val memory = ManagementFactory.getMemoryMXBean()
            val runtime = Runtime.getRuntime()
            return HeapStichprobe(
                collect = { memory.gc() },
                usedKb = { (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_KIB },
                openStreams = { zaehler.offen.get() },
                gcMillis = { ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0L) } },
            )
        }
    }
}

/**
 * Die Wanduhr-Stempel des letzten Laufs von [ABSCHLUSS_TABLE] in Mikrosekunden seit 1970: [zeile] vor der Übergabe der
 * letzten Zeile an den Strom, [ende] vor der Übergabe des Ergebnisendes, [naechstes] nach der Verzögerung, unmittelbar
 * bevor das nächste Ereignis (das nächste Ergebnis) oder das Ende des Stroms folgt.
 */
internal data class AbschlussStempel(val zeile: Long, val ende: Long, val naechstes: Long)

/** Wie der Strom nach dem Ende des ersten Ergebnisses weitergeht. */
internal enum class AbschlussForm(val schluessel: String) {
    /** Das Backend verzögert das Ende des Stroms; kein weiteres Ereignis folgt. */
    ENDE("ende"),

    /** Das Backend verzögert das nächste Ereignis, ein zweites Ergebnis mit einer Zeile. */
    FOLGE("folge"),
    ;

    companion object {
        fun of(text: String): AbschlussForm = entries.firstOrNull { it.schluessel == text }
            ?: error("form: ${entries.joinToString { it.schluessel }}, nicht $text")
    }
}

/** Die Angaben einer Anfrage an [ABSCHLUSS_TABLE]: Zeilen des ersten Ergebnisses, Form und Verzögerung in Millisekunden. */
internal data class AbschlussAnfrage(val rows: Long, val form: AbschlussForm, val delayMillis: Long) {
    companion object {
        fun of(angaben: String?): AbschlussAnfrage {
            val map = angaben.orEmpty().split(',').filter { it.contains('=') }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            return AbschlussAnfrage(
                rows = map["zeilen"]?.toLong() ?: DEFAULT_ABSCHLUSS_ROWS,
                form = map["form"]?.let(AbschlussForm::of) ?: AbschlussForm.ENDE,
                delayMillis = map["verzoegerung"]?.toLong() ?: 0L,
            ).also { require(it.rows >= 1 && it.delayMillis >= 0) { "zeilen ab 1, verzoegerung ab 0" } }
        }
    }
}

private val HEAP_COLUMNS = listOf(
    "ProbenOffen" to SqlType.Int64,
    "ProbenLeer" to SqlType.Int64,
    "MinKb" to SqlType.Int64,
    "MedianKb" to SqlType.Int64,
    "P95Kb" to SqlType.Int64,
    "MaxKb" to SqlType.Int64,
    "LeerMaxKb" to SqlType.Int64,
    "GcMs" to SqlType.Int64,
)

private val ABSCHLUSS_COLUMNS = listOf(
    ResultColumn("Nr", SqlType.Int64, nullable = false),
    ResultColumn("Wert", SqlType.Int64, nullable = false),
)

private val STAMP_COLUMNS = listOf(
    "ZeileUs" to SqlType.Int64,
    "EndeUs" to SqlType.Int64,
    "NaechstesUs" to SqlType.Int64,
)

private fun columnsOf(columns: List<Pair<String, SqlType>>): List<ResultColumn> =
    columns.map { (name, type) -> ResultColumn(name, type, nullable = false) }

private fun HeapFenster.values(): List<Any?> =
    listOf(offen.toLong(), leer.toLong(), minKb, medianKb, p95Kb, maxKb, leerMaxKb, gcMillis)

private fun AbschlussStempel.values(): List<Any?> = listOf(zeile, ende, naechstes)

private val ABSCHLUSS_REQUEST = Regex("""SELECT \* FROM abschluss(?:\s*/\*(.*)\*/)?""", RegexOption.IGNORE_CASE)

private fun oneRow(columns: List<ResultColumn>, values: List<Any?>): QueryExecution = object : QueryExecution {
    override val events: Flow<QueryEvent> = flow {
        emit(QueryEvent.ResultStart(columns))
        emit(QueryEvent.Row(values))
        emit(QueryEvent.ResultEnd(1))
    }

    override suspend fun close() = Unit
}

/**
 * Das eigene Backend des Aufbaus B der Heap- und Abschluss-Messung: `SELECT * FROM ref_zeilen` und
 * `SELECT * FROM rueckstau_stand` beantwortet [rueckstau], [HEAP_TABLE] liefert das Heap-Fenster ([heap], liest und
 * beginnt ein neues), [ABSCHLUSS_TABLE] ein Ergebnis mit verzögertem Abschluss ([AbschlussAnfrage]) und
 * [ABSCHLUSS_STAND_TABLE] die Stempel des letzten Laufs. Die Stempel nimmt [clock] (Mikrosekunden seit 1970).
 */
internal class HeapAbschlussBackend(
    private val rueckstau: QueryBackend,
    private val heap: HeapStichprobe,
    private val clock: () -> Long = ::epochMicros,
) : QueryBackend {
    private val stempel = AtomicReference(AbschlussStempel(0, 0, 0))

    override suspend fun execute(request: QueryRequest): QueryExecution {
        val sql = request.sql.trim()
        val abschluss = ABSCHLUSS_REQUEST.matchEntire(sql)
        return when {
            sql.equals("SELECT * FROM $HEAP_TABLE", ignoreCase = true) ->
                oneRow(columnsOf(HEAP_COLUMNS), heap.read().values())

            sql.equals("SELECT * FROM $ABSCHLUSS_STAND_TABLE", ignoreCase = true) ->
                oneRow(columnsOf(STAMP_COLUMNS), stempel.get().values())

            abschluss != null -> abschlussStrom(AbschlussAnfrage.of(abschluss.groupValues[1]))

            else -> rueckstau.execute(request)
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit

    private fun abschlussStrom(anfrage: AbschlussAnfrage): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> = flow {
            var zeile = 0L
            emit(QueryEvent.ResultStart(ABSCHLUSS_COLUMNS))
            for (n in 1..anfrage.rows) {
                if (n == anfrage.rows) zeile = clock()
                emit(QueryEvent.Row(listOf(n, n * DOUBLE_VALUE)))
            }
            val ende = clock()
            emit(QueryEvent.ResultEnd(anfrage.rows))
            delay(anfrage.delayMillis)
            stempel.set(AbschlussStempel(zeile, ende, clock()))
            if (anfrage.form == AbschlussForm.FOLGE) {
                emit(QueryEvent.ResultStart(ABSCHLUSS_COLUMNS))
                emit(QueryEvent.Row(listOf(1L, DOUBLE_VALUE)))
                emit(QueryEvent.ResultEnd(1))
            }
        }

        override suspend fun close() = Unit
    }

    override fun toString(): String = "HeapAbschlussBackend"
}

/** Eine einzeilige Tabelle des Aufbaus C: die Werte liefert [values] beim Lesen. */
private fun einzeilTabelle(name: String, columns: List<Pair<String, SqlType>>, values: () -> List<Any?>): SimTable {
    val simColumns = columns.map { (column, type) -> SimColumn(column, type, nullable = false) }
    return SimTable(
        name = name,
        columns = simColumns,
        primaryKey = listOf(columns.first().first),
        rows = { query ->
            val row = values()
            var gelesen = false
            RowSource {
                if (gelesen) {
                    null
                } else {
                    gelesen = true
                    query.columns.map { row[simColumns.indexOfFirst { c -> c.name == it.name }] }
                }
            }
        },
    )
}

/** Die Tabellen des Aufbaus C der Heap-Messung: [rueckstauTabellen] ohne Wartezeit des Callbacks und [HEAP_TABLE]. */
internal fun heapTabellen(
    rows: Long,
    seed: Long,
    zaehler: RueckstauZaehler,
    wartender: Wartender,
    heap: HeapStichprobe,
): List<SimTable> =
    rueckstauTabellen(rows, seed, zaehler, wartender, Callback(Warten.SUSPENDIEREND, 0)) +
        einzeilTabelle(HEAP_TABLE, HEAP_COLUMNS) { heap.read().values() }

/**
 * Der Serverprozess der Heap- und Abschluss-Messung (`tools/heap-abschluss.sh`, Image der Stage `lasttestserver`,
 * diese Hauptklasse). Umgebung: `HEAPABSCHLUSS_AUFBAU` (`eigen`: [HeapAbschlussBackend] über [RueckstauBackend],
 * `sim`: Simulations-Backend, [heapTabellen]; Vorgabe `eigen`), `HEAPABSCHLUSS_ZEILEN` (Zeilen je Export, Vorgabe
 * 10 000 000), `HEAPABSCHLUSS_SEED`, `HEAPABSCHLUSS_INTERVALL_MS` (Abstand der Stichproben, Vorgabe 500 wie im Spike). Ein
 * Thread nimmt alle Stichproben (Bereinigung, dann benutzter Heap); die erste Zeile nennt die Einstellung, den Heap, die
 * Prozessoren und die Speicherbereiniger der JVM.
 */
fun main() {
    val environment = System.getenv()
    val aufbau = environment["HEAPABSCHLUSS_AUFBAU"] ?: "eigen"
    val rows = environment["HEAPABSCHLUSS_ZEILEN"]?.toLong() ?: DEFAULT_ROWS
    val seed = environment["HEAPABSCHLUSS_SEED"]?.toLong() ?: REF_DEFAULT_SEED
    val interval = environment["HEAPABSCHLUSS_INTERVALL_MS"]?.toLong() ?: DEFAULT_INTERVAL_MS
    val zaehler = RueckstauZaehler()
    val wartender = Wartender(DEFAULT_POOL)
    val heap = HeapStichprobe.laufend(zaehler)
    val collectors = ManagementFactory.getGarbageCollectorMXBeans().joinToString("+") { it.name.replace(' ', '_') }
    println(
        "$HEAP_PREFIX server aufbau=$aufbau zeilen=$rows seed=$seed intervall_ms=$interval " +
            "heap_max=${Runtime.getRuntime().maxMemory()} prozessoren=${Runtime.getRuntime().availableProcessors()} " +
            "gc=$collectors",
    )
    Thread {
        while (true) {
            heap.sample()
            Thread.sleep(interval)
        }
    }.apply {
        isDaemon = true
        name = "heap-stichprobe"
    }.start()
    runTestserver {
        when (aufbau) {
            "eigen" -> BackendSource.Custom(
                HeapAbschlussBackend(RueckstauBackend(seed, rows, zaehler, wartender), heap),
            )

            "sim" -> BackendSource.Simulation(heapTabellen(rows, seed, zaehler, wartender, heap))

            else -> error("HEAPABSCHLUSS_AUFBAU: eigen oder sim, nicht $aufbau")
        }
    }
}
