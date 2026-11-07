package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.domain.telemetry.ServerStopped
import io.github.pt9912.tdswire.domain.telemetry.ServerStopping
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.transport.listener.TdsListener
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Woher die Anfragen eines eingebetteten Servers ihre Antworten bekommen (`TDS-FA-08`, `TDS-FA-11.a`).
 *
 * - [Custom]: ein fertiges [QueryBackend] des Integrators.
 * - [Simulation]: [SimTable]-Werte mit Zeilenquelle und Callbacks, die Kotlin-Schnittstelle des Simulations-Backends
 *   (`SPEC-035`). Der Server übernimmt eigene Kopien von `columns` und `primaryKey`; eine Zeilenquelle schließt
 *   mit ihrer Anfrage, `close()` wird genau einmal gerufen.
 * - [None]: weder Backend noch Tabellen; wie [Simulation] mit leerer Liste.
 *
 * Die Tabellen von [Simulation] bilden einen Katalog; ein Name, der doppelt vorkommt, bricht den Start ab.
 * [Custom] mit Tabellen ist nicht darstellbar.
 */
public sealed interface BackendSource {
    /** Ein Backend des Integrators; der Server ruft es für jede Anfrage. */
    public class Custom(public val backend: QueryBackend) : BackendSource

    /** Tabellen mit Zeilenquellen des Integrators. */
    public class Simulation(public val tables: List<SimTable>) : BackendSource

    /** Weder Backend noch Tabellen. */
    public data object None : BackendSource
}

/** Die Quelle in der Form der Verdrahtung; `RequestSource` gehört nicht zur Zusage. */
internal fun BackendSource.requests(): RequestSource = when (this) {
    is BackendSource.Custom -> RequestSource(backend = backend)
    is BackendSource.Simulation -> RequestSource(simulationTables = tables)
    BackendSource.None -> RequestSource()
}

/**
 * Ausgang von [TdsServer.start]: ein laufender Server oder der Abbruch. Der Start wirft keine Ausnahme für
 * Konfigurations- und Bindefehler (`SPEC-043`); wer nicht prüft, sieht den Abbruch nicht.
 */
public sealed interface StartOutcome {
    /** Der Server läuft und ist gebunden. */
    public class Running(public val server: TdsServer) : StartOutcome

    /**
     * Der Start ist abgebrochen; kein Port ist gebunden und kein Thread des Servers läuft. [reason] ist ein Text ohne
     * Konfigurationswert, Pfad oder Ausnahmetext. [issues] nennt bei einer ungültigen Konfiguration jeden Fehler mit
     * Schlüssel und Art (kein Wert), sonst ist die Liste leer.
     */
    public class Aborted(public val reason: String, issues: List<ConfigIssue> = emptyList()) : StartOutcome {
        public val issues: List<ConfigIssue> = issues.toList()
    }
}

/**
 * Der laufende Server (`ARC-008`). [stop] schließt den Listener und alle Verbindungen.
 *
 * Lebenszyklus: [start] bis [stop]. [stop] ist idempotent und darf nebenläufig gerufen werden (getestet mit zwei
 * Aufrufen); jeder weitere Aufruf endet ohne Wirkung. Aus welchem Thread [start] gerufen wird und ein [stop] während
 * [start], ist nicht festgelegt.
 *
 * Eine [ServerConfig] gilt für einen Start: sobald der Start das TLS-Material lädt, löscht er das Passwort im
 * Schlüsselmaterial; bricht er vorher ab (Prüfung der Konfiguration), bleibt es lesbar (Setzung, getestet). Wer
 * neu startet, baut eine neue Konfiguration.
 *
 * Der Einstieg verlangt keine JVM-Option und liest keine Systemeigenschaft (`SPEC-023`). Er schreibt nirgends
 * hin; Log und Telemetrie gehen an die Senke, die [start] übergeben bekommt, ein Abbruch steht in
 * [StartOutcome.Aborted].
 */
public class TdsServer internal constructor(private val listener: TdsListener, private val telemetry: Telemetry) {
    private val stopLock = Mutex()
    private var stopBegun = false
    private var stopEnded = false

    /** Der gebundene Port. */
    public val port: Int get() = listener.port

    /**
     * Beendet den Server (`SPEC-044`). Die Aufrufe sind gegeneinander gesperrt: ein nebenläufiger Aufruf wartet auf
     * den laufenden und kehrt danach zurück, ein Aufruf nach dem Ende hat keine Wirkung. Der erste Aufruf gibt das
     * Ereignis zum Beginn des Beendens vor dem Schließen des Listeners an die Senke und das Ereignis zum Ende, nachdem
     * jede Verbindung beendet ist; es ist an die Senke gegeben, wenn ein Aufruf zurückkehrt. Wird ein Aufruf
     * abgebrochen, bevor das Beenden fertig ist, entfällt das Ereignis zum Ende in diesem Aufruf; der Listener räumt
     * trotzdem auf, und der nächste Aufruf gibt das Ereignis zum Ende ab (ohne ein zweites zum Beginn). Dann kann das
     * Ereignis zum Ende vor dem `CONNECTION_CLOSED` einer noch endenden Verbindung stehen.
     *
     * `stop` aus der Senke heraus, bei `SERVER_STOPPING` oder `SERVER_STOPPED`, kehrt nicht zurück: die Sperre ist
     * nicht wiedereintrittsfähig und wird während der Abgabe der Ereignisse gehalten (gemessen mit einer Zeitgrenze von
     * 3 s, beide Arten); eine Senke blockiert ohnehin nicht (Vertrag von `TelemetrySink`).
     */
    public suspend fun stop() {
        stopLock.withLock {
            if (!stopBegun) {
                stopBegun = true
                telemetry.emit { ServerStopping(it) }
            }
            listener.shutdown()
            if (!stopEnded) {
                stopEnded = true
                telemetry.emit { ServerStopped(it) }
            }
        }
    }

    public companion object {
        /**
         * Startet den Server mit [config] und [source] (`SPEC-059`). Geprüft werden die Konfiguration
         * (Befunde als [StartOutcome.Aborted.issues]), die Tabellen und das TLS-Material; erst danach wird
         * gebunden. [telemetry] nimmt die Ereignisse des Servers entgegen (Vertrag in [TelemetrySink]); ohne Senke
         * erzeugt und schreibt die Bibliothek nichts. Der Aufrufer besitzt die Senke und schließt sie nach [stop].
         */
        public suspend fun start(
            config: ServerConfig,
            source: BackendSource,
            telemetry: TelemetrySink = TelemetrySink.NONE,
        ): StartOutcome = Bootstrap.launch(
            config,
            Wiring(telemetry = telemetry, requests = source.requests()),
        )
    }
}
