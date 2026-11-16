package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

/** Der Aufbau des Lasttests, den der Serverprozess trägt (`docs/plan/referenz-workload.md` S6 und S7). */
internal enum class Aufbau { B, C }

/**
 * Die Umgebung des Lasttest-Servers: `LASTTEST_AUFBAU` (`B` eigenes Backend, `C` Simulations-Backend, Vorgabe `C`),
 * `LASTTEST_ZEILEN` (Zeilen je Anfrage, Vorgabe 100 000), `LASTTEST_SEED` (Vorgabe [REF_DEFAULT_SEED]) und
 * `LASTTEST_SENKE` (`mit`: das Log des Testservers ist die Senke, Ebene aus `LOG_LEVEL`; `ohne`: keine Senke;
 * Vorgabe `mit`).
 */
internal data class LasttestEinstellung(val aufbau: Aufbau, val rows: Long, val seed: Long, val sink: Boolean) {
    companion object {
        private const val DEFAULT_ROWS = 100_000L

        fun of(environment: Map<String, String>): LasttestEinstellung = LasttestEinstellung(
            aufbau = Aufbau.valueOf(environment["LASTTEST_AUFBAU"] ?: "C"),
            rows = environment["LASTTEST_ZEILEN"]?.toLong() ?: DEFAULT_ROWS,
            seed = environment["LASTTEST_SEED"]?.toLong() ?: REF_DEFAULT_SEED,
            sink = when (val s = environment["LASTTEST_SENKE"] ?: "mit") {
                "mit" -> true
                "ohne" -> false
                else -> error("LASTTEST_SENKE: mit oder ohne, nicht $s")
            },
        )
    }

    fun source(): BackendSource = when (aufbau) {
        Aufbau.B -> BackendSource.Custom(ZeilenBackend(rows, seed))
        Aufbau.C -> BackendSource.Simulation(listOf(referenzTabelle(rows, seed)))
    }
}

/**
 * Der Serverprozess des Lasttests (`make lasttest`, Dockerfile-Stage `lasttestserver`): der Testserver mit dem Backend
 * des Aufbaus B oder C. Mit Senke (`LASTTEST_SENKE=mit`) läuft [runTestserver], das Log ist die Senke; ohne Senke ruft
 * er [startTestserver] mit `TelemetrySink.NONE`. Die erste Zeile nennt die Einstellung und den Heap der JVM.
 */
fun main() {
    val setting = LasttestEinstellung.of(System.getenv())
    println(
        "$LASTTEST_PREFIX server aufbau=${setting.aufbau} zeilen=${setting.rows} seed=${setting.seed} " +
            "senke=${if (setting.sink) "mit" else "ohne"} heap_max=${Runtime.getRuntime().maxMemory()}",
    )
    if (setting.sink) {
        runTestserver { setting.source() }
    } else {
        runWithoutSink(setting)
    }
}

private fun runWithoutSink(setting: LasttestEinstellung) {
    val choose = { _: List<SimTable> -> setting.source() }
    when (val boot = runBlocking { startTestserver(System.getenv(), choose, ::println, TelemetrySink.NONE) }) {
        is StartOutcome.Aborted -> exitProcess(EXIT_START_FAILED)

        is StartOutcome.Running -> {
            Runtime.getRuntime().addShutdownHook(Thread { runBlocking { boot.server.stop() } })
            CountDownLatch(1).await()
        }
    }
}
