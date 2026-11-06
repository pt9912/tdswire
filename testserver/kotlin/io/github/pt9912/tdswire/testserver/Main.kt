package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

/** Exit-Code eines abgebrochenen Starts (`TDS-FA-09.a`: fehlerhafte Konfiguration führt zu Startabbruch). */
internal const val EXIT_START_FAILED = 1

/** Präfix jeder Zeile, die der Testserver auf die Standardausgabe schreibt. */
internal const val LINE_PREFIX = "tdswire:"

private const val CONFIG_INVALID = "Konfiguration ungueltig"

/**
 * Der Testserver (`SPEC-052`, `SPEC-059`): ein Serverprozess, der nur der Prüfung der Bibliothek dient. Er liest die
 * Umgebung und die Dateien, die sie nennt ([ConfigLoader]), baut eine `ServerConfig` und ruft `TdsServer.start` wie
 * ein Einbettender; er kennt von der Bibliothek nur ihre öffentliche Fläche. Er bleibt laufen, bis die JVM beendet
 * wird (SIGTERM oder SIGINT); der Shutdown-Hook ruft `TdsServer.stop`. Ein abgebrochener Start beendet den Prozess
 * mit [EXIT_START_FAILED]. Der Prozess braucht keine JVM-Option.
 *
 * Standardausgabe (die Skripte der E2E-Läufe lesen sie): nach dem Start die Zeile `tdswire: gestartet, Port <port>`
 * mit dem Port des laufenden Servers, im Hook die Zeile `tdswire: gestoppt`, bei einem Abbruch der Grund oder je
 * Konfigurationsfehler eine Zeile `tdswire: Konfiguration ungueltig: <Schlüssel>: <Art>`. Aus der Bibliothek kommt
 * kein Log (der Einstieg schreibt keines); der Testserver schreibt nur diese Zeilen.
 */
fun main() = runTestserver()

/**
 * Startet den Testserver aus [environment]; [choose] bekommt die Tabellen der Datei der Beschreibung und liefert die
 * Quelle der Anfragen, `null` bricht den Start ab (Tabellen aus der Datei und ein fertiges Backend schließen sich aus).
 * Ein Abbruch beendet den Prozess, sonst läuft er bis zum Ende der JVM.
 */
internal fun runTestserver(
    environment: Map<String, String> = System.getenv(),
    choose: (List<SimTable>) -> BackendSource? = { BackendSource.Simulation(it) },
) {
    when (val boot = runBlocking { startTestserver(environment, choose, ::println) }) {
        is StartOutcome.Aborted -> exitProcess(EXIT_START_FAILED)

        is StartOutcome.Running -> {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    runBlocking { boot.server.stop() }
                    println("$LINE_PREFIX gestoppt")
                },
            )
            CountDownLatch(1).await()
        }
    }
}

/**
 * Lader, Beschreibung der Tabellen und `TdsServer.start`; jede Zeile geht an [out]. Die Zeile der Bereitschaft
 * schreibt diese Funktion erst, nachdem der Start den laufenden Server geliefert hat.
 */
internal suspend fun startTestserver(
    environment: Map<String, String>,
    choose: (List<SimTable>) -> BackendSource?,
    out: (String) -> Unit,
): StartOutcome {
    val outcome = when (val loaded = ConfigLoader.load(environment)) {
        is ConfigResult.Invalid -> StartOutcome.Aborted(CONFIG_INVALID, loaded.issues)

        is ConfigResult.Loaded -> when (val described = DescriptionFile.tables(loaded.simulationFile)) {
            is DescriptionResult.Failed -> StartOutcome.Aborted(described.reason)

            is DescriptionResult.Read -> when (val source = choose(described.tables)) {
                null -> StartOutcome.Aborted("Backend und Simulations-Backend gleichzeitig konfiguriert")
                else -> TdsServer.start(loaded.config, withEmptyCatalog(source, loaded.simulationFile != null))
            }
        }
    }
    report(outcome, out)
    return outcome
}

/**
 * Eine Datei der Beschreibung ohne Tabellen (`tables: {}`) ergibt ein Simulations-Backend mit leerem Katalog, das jede
 * Anfrage als unbekannte Tabelle beantwortet (Setzung, nicht am Server gemessen). `BackendSource.Simulation` mit leerer
 * Liste hieße der Bibliothek dagegen: kein Backend, die Verbindung schließt bei der ersten Anfrage.
 */
private fun withEmptyCatalog(source: BackendSource, fromFile: Boolean): BackendSource =
    if (fromFile && source is BackendSource.Simulation && source.tables.isEmpty()) {
        BackendSource.Custom(SimulationBackend(SimCatalog(emptyList())))
    } else {
        source
    }

private fun report(outcome: StartOutcome, out: (String) -> Unit) {
    when (outcome) {
        is StartOutcome.Running -> out("$LINE_PREFIX gestartet, Port ${outcome.server.port}")

        is StartOutcome.Aborted ->
            if (outcome.issues.isEmpty()) {
                out("$LINE_PREFIX ${outcome.reason}")
            } else {
                outcome.issues.forEach { out("$LINE_PREFIX $CONFIG_INVALID: ${it.message}") }
            }
    }
}
