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
import io.github.pt9912.tdswire.simulation.SimQuery
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

/** Präfix der Zeilen des Rückstau-Servers auf der Standardausgabe; `tools/rueckstau.sh` liest sie. */
internal const val RUECKSTAU_PREFIX = "rueckstau:"

/** Name der Tabelle mit den Zählern des Servers; `_gc` ruft vor dem Lesen die Speicherbereinigung. */
internal const val STAND_TABLE = "rueckstau_stand"
private const val STAND_TABLE_GC = "rueckstau_stand_gc"

private const val NANOS_PER_MILLI = 1_000_000L
private const val BYTES_PER_MIB = 1024L * 1024L
private const val DEFAULT_ROWS = 100_000L
private const val DEFAULT_POOL = 64

/** Wie ein Aufruf des Backends wartet (`docs/messungen/performance-rueckstau-2026-11-17.md`, Abschnitt Aufbau). */
internal enum class Warten(val schluessel: String) {
    /** Suspendiert (`delay`): kein Thread ist belegt. */
    SUSPENDIEREND("suspendierend"),

    /** Ein Thread eines begrenzten Pools schläft; ein Abbruch unterbricht ihn. */
    UNTERBRECHBAR("unterbrechbar"),

    /** Ein Thread des Pools schläft und ignoriert die Unterbrechung bis zum Ende der Wartezeit. */
    NICHT_UNTERBRECHBAR("nicht-unterbrechbar"),

    /** Der Thread des Aufrufers schläft (ein Verstoß gegen „Suspendierend“ in „Pflichten des Backends“, `SPEC-035`). */
    DIREKT("direkt"),
    ;

    companion object {
        fun of(text: String): Warten = entries.firstOrNull { it.schluessel == text }
            ?: error("Warten: ${entries.joinToString { it.schluessel }}, nicht $text")
    }
}

/** Lässt Aufrufe nach [Warten] warten; [poolSize] begrenzt die Threads der beiden Modi mit Pool. */
internal class Wartender(poolSize: Int) : AutoCloseable {
    private val pool = Executors.newFixedThreadPool(poolSize) { task ->
        Thread(task, "rueckstau-pool").apply { isDaemon = true }
    }
    private val dispatcher = pool.asCoroutineDispatcher()

    suspend fun warte(modus: Warten, millis: Long) {
        if (millis <= 0) return
        when (modus) {
            Warten.SUSPENDIEREND -> delay(millis)
            Warten.UNTERBRECHBAR -> withContext(dispatcher) { runInterruptible { schlafe(millis) } }
            Warten.NICHT_UNTERBRECHBAR -> withContext(dispatcher) { schlafeUnbeirrt(millis) }
            Warten.DIREKT -> schlafe(millis)
        }
    }

    override fun close() {
        pool.shutdownNow()
    }
}

/** Schläft [millis] Millisekunden im Thread des Aufrufers; eine Unterbrechung beendet den Schlaf. */
private fun schlafe(millis: Long) = Thread.sleep(millis)

/** Wartet [millis] Millisekunden, auch wenn der Thread unterbrochen wird; stellt das Zeichen am Ende wieder her. */
private fun schlafeUnbeirrt(millis: Long) {
    val end = System.nanoTime() + millis * NANOS_PER_MILLI
    var unterbrochen = false
    while (true) {
        val rest = end - System.nanoTime()
        if (rest <= 0) break
        if (Thread.interrupted()) unterbrochen = true
        LockSupport.parkNanos(rest)
    }
    if (unterbrochen) Thread.currentThread().interrupt()
}

/**
 * Die Zähler des Serverprozesses, die der Client über die Tabelle [STAND_TABLE] liest: Zeilen, die das Backend dem
 * Strom übergeben hat ([erzeugt], gezählt bevor die Zeile den Strom verlässt, die Zeile „in Arbeit“ zählt mit),
 * Zeilen, die der Stapel-Callback verarbeitet hat ([importiert]), offene, beendete und abgebrochene Ströme und die
 * Threads und den Heap der JVM zum Zeitpunkt des Lesens.
 */
internal class RueckstauZaehler {
    val erzeugt = AtomicLong()
    val importiert = AtomicLong()
    val offen = AtomicInteger()
    val beendet = AtomicLong()
    val abgebrochen = AtomicLong()

    /** Eine Zeile der Tabelle [STAND_TABLE]; die Spitze der Threads gilt seit dem letzten Lesen. */
    fun stand(bereinigen: Boolean): List<Any?> {
        if (bereinigen) ManagementFactory.getMemoryMXBean().gc()
        val threads = ManagementFactory.getThreadMXBean()
        val runtime = Runtime.getRuntime()
        val spitze = threads.peakThreadCount
        threads.resetPeakThreadCount()
        return listOf(
            erzeugt.get(),
            importiert.get(),
            offen.get(),
            beendet.get(),
            abgebrochen.get(),
            threads.threadCount,
            spitze,
            (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MIB,
        )
    }
}

private val STAND_COLUMNS = listOf(
    "Erzeugt" to SqlType.Int64,
    "Importiert" to SqlType.Int64,
    "Offen" to SqlType.Int32,
    "Beendet" to SqlType.Int64,
    "Abgebrochen" to SqlType.Int64,
    "Threads" to SqlType.Int32,
    "SpitzeThreads" to SqlType.Int32,
    "HeapMb" to SqlType.Int64,
)

private val STAND_RESULT_COLUMNS = STAND_COLUMNS.map { (name, type) ->
    ResultColumn(name, type, nullable = false)
}

/** Anfrage des Exports, optional mit Angaben `/*zeilen=…,modus=…,block=…,jede=…*/` hinter dem Text. */
private val RUECKSTAU_EXPORT = Regex("""SELECT \* FROM ref_zeilen(?:\s*/\*(.*)\*/)?""", RegexOption.IGNORE_CASE)
private val RUECKSTAU_STAND = Regex("""SELECT \* FROM (rueckstau_stand(?:_gc)?)""", RegexOption.IGNORE_CASE)

/** Die Angaben einer Anfrage: Zeilen, Art und Dauer des Wartens, gewartet wird vor jeder [jede]-ten Zeile. */
internal data class Anfrage(val rows: Long, val modus: Warten, val blockMillis: Long, val jede: Long) {
    companion object {
        fun of(angaben: String?, defaultRows: Long): Anfrage {
            val map = angaben.orEmpty().split(',').filter { it.contains('=') }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            return Anfrage(
                rows = map["zeilen"]?.toLong() ?: defaultRows,
                modus = map["modus"]?.let(Warten::of) ?: Warten.SUSPENDIEREND,
                blockMillis = map["block"]?.toLong() ?: 0L,
                jede = map["jede"]?.toLong() ?: 1L,
            )
        }
    }
}

/**
 * Das eigene Backend des Rückstau-Aufbaus (B): `SELECT * FROM ref_zeilen` liefert Zeilen aus [ReferenzZeilen], zählt
 * jede Zeile in [zaehler], bevor sie den Strom verlässt, und wartet vor jeder [Anfrage.jede]-ten Zeile nach
 * [Anfrage.modus]; `SELECT * FROM rueckstau_stand` liefert die Zähler. Es hält keine Zeile.
 */
internal class RueckstauBackend(
    private val seed: Long,
    private val defaultRows: Long,
    private val zaehler: RueckstauZaehler,
    private val wartender: Wartender,
) : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution {
        val sql = request.sql.trim()
        val export = RUECKSTAU_EXPORT.matchEntire(sql)
        val stand = RUECKSTAU_STAND.matchEntire(sql)
        val events: Flow<QueryEvent> = when {
            export != null -> export(Anfrage.of(export.groupValues[1], defaultRows))

            stand != null -> flow {
                emit(QueryEvent.ResultStart(STAND_RESULT_COLUMNS))
                emit(QueryEvent.Row(zaehler.stand(stand.groupValues[1].endsWith("_gc"))))
                emit(QueryEvent.ResultEnd(1))
            }

            else -> flow { emit(QueryEvent.Failure("TDS_UNSUPPORTED_SQL", "Text des Backends, nicht für den Client")) }
        }
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = events

            override suspend fun close() = Unit
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit

    private fun export(anfrage: Anfrage): Flow<QueryEvent> = flow {
        zaehler.offen.incrementAndGet()
        var fertig = false
        try {
            emit(QueryEvent.ResultStart(ReferenzZeilen.resultColumns))
            for (n in 1..anfrage.rows) {
                if (n % anfrage.jede == 0L) wartender.warte(anfrage.modus, anfrage.blockMillis)
                zaehler.erzeugt.incrementAndGet()
                emit(QueryEvent.Row(ReferenzZeilen.row(seed, n)))
            }
            emit(QueryEvent.ResultEnd(anfrage.rows))
            fertig = true
        } finally {
            zaehler.offen.decrementAndGet()
            if (fertig) zaehler.beendet.incrementAndGet() else zaehler.abgebrochen.incrementAndGet()
        }
    }

    override fun toString(): String = "RueckstauBackend"
}

/** Die Zeilenquelle des Aufbaus C: zählt jede Zeile vor der Rückgabe aus [next] und Ende oder Abbruch in [close]. */
private class RueckstauQuelle(
    private val query: SimQuery,
    private val rows: Long,
    private val seed: Long,
    private val zaehler: RueckstauZaehler,
) : RowSource {
    private var next = 0L
    private val names = ReferenzZeilen.columns.map { it.name }

    init {
        zaehler.offen.incrementAndGet()
    }

    override suspend fun next(): List<Any?>? {
        if (next >= rows) return null
        zaehler.erzeugt.incrementAndGet()
        val full = ReferenzZeilen.row(seed, ++next)
        return query.columns.map { full[names.indexOf(it.name)] }
    }

    override fun close() {
        zaehler.offen.decrementAndGet()
        if (next >= rows) zaehler.beendet.incrementAndGet() else zaehler.abgebrochen.incrementAndGet()
    }
}

/** Das Warten des Stapel-Callbacks: Art und Dauer je Stapel. */
internal data class Callback(val modus: Warten, val millis: Long)

/**
 * Die Tabellen des Aufbaus C: `ref_zeilen` mit der zählenden Quelle und einem Stapel-Callback, der nach [Warten] und
 * [callbackMillis] wartet und die Zeilen in [RueckstauZaehler.importiert] zählt, und die Tabellen [STAND_TABLE].
 */
internal fun rueckstauTabellen(
    rows: Long,
    seed: Long,
    zaehler: RueckstauZaehler,
    wartender: Wartender,
    callback: Callback,
): List<SimTable> {
    val reference = SimTable(
        name = REF_TABLE,
        columns = ReferenzZeilen.columns,
        primaryKey = listOf("Id"),
        rows = { query -> RueckstauQuelle(query, rows, seed, zaehler) },
        onInsertBatch = { batch ->
            wartender.warte(callback.modus, callback.millis)
            zaehler.importiert.addAndGet(batch.size.toLong())
            batch.size.toLong()
        },
    )
    return listOf(reference, standTabelle(STAND_TABLE, zaehler, false), standTabelle(STAND_TABLE_GC, zaehler, true))
}

private fun standTabelle(name: String, zaehler: RueckstauZaehler, bereinigen: Boolean): SimTable {
    val columns = STAND_COLUMNS.map { (column, type) -> SimColumn(column, type, nullable = false) }
    return SimTable(
        name = name,
        columns = columns,
        primaryKey = listOf("Erzeugt"),
        rows = { query ->
            val values = zaehler.stand(bereinigen)
            var gelesen = false
            RowSource {
                if (gelesen) {
                    null
                } else {
                    gelesen = true
                    query.columns.map { values[columns.indexOfFirst { c -> c.name == it.name }] }
                }
            }
        },
    )
}

/**
 * Der Serverprozess der Rückstau-Messung (`tools/rueckstau.sh`, Image der Stage `lasttestserver`, diese Hauptklasse).
 * Umgebung: `RUECKSTAU_AUFBAU` (`eigen`: [RueckstauBackend], `sim`: Simulations-Backend, [rueckstauTabellen], Vorgabe
 * `eigen`), `RUECKSTAU_ZEILEN` (Zeilen je Export, bei `eigen` Vorgabe der Anfrage, Vorgabe 100 000), `RUECKSTAU_SEED`,
 * `RUECKSTAU_POOL` (Threads des Pools, Vorgabe 64), `RUECKSTAU_CALLBACK_MODUS` und `RUECKSTAU_CALLBACK_MS` (Warten des
 * Stapel-Callbacks bei `sim`, Vorgabe `suspendierend` und 0). Die erste Zeile nennt die Einstellung, den Heap und die
 * Prozessoren der JVM.
 */
fun main() {
    val environment = System.getenv()
    val aufbau = environment["RUECKSTAU_AUFBAU"] ?: "eigen"
    val rows = environment["RUECKSTAU_ZEILEN"]?.toLong() ?: DEFAULT_ROWS
    val seed = environment["RUECKSTAU_SEED"]?.toLong() ?: REF_DEFAULT_SEED
    val pool = environment["RUECKSTAU_POOL"]?.toInt() ?: DEFAULT_POOL
    val callbackModus = Warten.of(environment["RUECKSTAU_CALLBACK_MODUS"] ?: Warten.SUSPENDIEREND.schluessel)
    val callbackMillis = environment["RUECKSTAU_CALLBACK_MS"]?.toLong() ?: 0L
    val zaehler = RueckstauZaehler()
    val wartender = Wartender(pool)
    println(
        "$RUECKSTAU_PREFIX server aufbau=$aufbau zeilen=$rows seed=$seed pool=$pool " +
            "callback=${callbackModus.schluessel}/" +
            "${callbackMillis}ms heap_max=${Runtime.getRuntime().maxMemory()} " +
            "prozessoren=${Runtime.getRuntime().availableProcessors()}",
    )
    runTestserver {
        when (aufbau) {
            "eigen" -> BackendSource.Custom(RueckstauBackend(seed, rows, zaehler, wartender))

            "sim" -> BackendSource.Simulation(
                rueckstauTabellen(rows, seed, zaehler, wartender, Callback(callbackModus, callbackMillis)),
            )

            else -> error("RUECKSTAU_AUFBAU: eigen oder sim, nicht $aufbau")
        }
    }
}
