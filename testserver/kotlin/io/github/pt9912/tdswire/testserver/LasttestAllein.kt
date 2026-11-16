package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimulationBackend
import kotlinx.coroutines.runBlocking

private const val NANOS_PER_MILLI = 1_000_000L
private const val DEFAULT_REPEATS = 5

/** Zieht den Strom von `SELECT * FROM ref_zeilen` bis zur Senke und gibt die Zeilenzahl zurück. */
private suspend fun pull(backend: QueryBackend): Long {
    var count = 0L
    val request = QueryRequest("lasttest", "export", "SELECT * FROM ref_zeilen", emptyList(), null)
    val execution = backend.execute(request)
    try {
        execution.events.collect { if (it is QueryEvent.Row) count++ }
    } finally {
        execution.close()
    }
    return count
}

/**
 * Aufbau A (`docs/plan/referenz-workload.md` S6 und S7): das Backend allein, ohne Server und ohne Draht; der Strom von
 * `SELECT * FROM ref_zeilen` wird im selben Prozess bis zur Senke (ein Zähler) gezogen. Gemessen werden beide Backends
 * der Aufbauten B und C: das eigene ([ZeilenBackend]) und das Simulations-Backend über [referenzTabelle]. Je Lauf eine
 * Zeile `lasttest: a <Backend> lauf <n> zeilen <Zahl> ms <Millisekunden>`; der erste Lauf je Backend ist der Warmlauf
 * der JVM und steht als `lauf 0`. Umgebung: `LASTTEST_ZEILEN`, `LASTTEST_SEED`, `LASTTEST_WDH` (Vorgabe 5 Läufe nach
 * dem Warmlauf). Dazu der Import: der Stapel-Callback der Tabelle `ref_zeilen` wird in Stapeln zu 1000 Zeilen direkt
 * gerufen (`simulation-import`, kein Draht, kein Client).
 */
fun main() {
    val setting = LasttestEinstellung.of(System.getenv())
    val repeats = System.getenv("LASTTEST_WDH")?.toInt() ?: DEFAULT_REPEATS
    println(
        "$LASTTEST_PREFIX a zeilen=${setting.rows} seed=${setting.seed} wdh=$repeats " +
            "heap_max=${Runtime.getRuntime().maxMemory()}",
    )
    val backends = listOf(
        "erzeuger" to ZeilenBackend(setting.rows, setting.seed),
        "simulation" to SimulationBackend(SimCatalog(listOf(referenzTabelle(setting.rows, setting.seed)))),
    )
    for ((name, backend) in backends) {
        for (run in 0..repeats) {
            val start = System.nanoTime()
            val rows = runBlocking { pull(backend) }
            val millis = (System.nanoTime() - start) / NANOS_PER_MILLI
            check(rows == setting.rows) { "Aufbau A $name: $rows statt ${setting.rows} Zeilen" }
            println("$LASTTEST_PREFIX a $name lauf $run zeilen $rows ms $millis")
        }
    }
    val importTable = referenzTabelle(setting.rows, setting.seed) {}
    for (run in 0..repeats) {
        val start = System.nanoTime()
        val rows = runBlocking { importBatches(importTable, setting.rows, setting.seed) }
        val millis = (System.nanoTime() - start) / NANOS_PER_MILLI
        check(rows == setting.rows) { "Aufbau A Import: $rows statt ${setting.rows} Zeilen" }
        println("$LASTTEST_PREFIX a simulation-import lauf $run zeilen $rows ms $millis")
    }
}
