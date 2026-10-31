package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.transport.listener.TdsListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Woher die Anfragen eines eingebetteten Servers ihre Antworten bekommen (`TDS-FA-08`, `TDS-FA-11.a`).
 *
 * - [Custom]: ein fertiges [QueryBackend] des Integrators.
 * - [Simulation]: [SimTable]-Werte mit Zeilenquelle und Callbacks, die Kotlin-Schnittstelle des Simulations-Backends
 *   (`SPEC-035`). Der Server übernimmt eigene Kopien von `columns` und `primaryKey`; eine Zeilenquelle schließt
 *   mit ihrer Anfrage, `close()` wird genau einmal gerufen.
 * - [None]: weder Backend noch Tabellen; wie [Simulation] mit leerer Liste.
 *
 * Bei [Simulation] und [None] kommen die Tabellen aus `ServerConfig.simulationFile` hinzu und bilden mit den
 * übergebenen einen Katalog; ein Name, der doppelt vorkommt, bricht den Start ab. [Custom] zusammen mit gesetzter
 * `simulationFile` bricht den Start ab; [Custom] mit Tabellen ist nicht darstellbar.
 */
sealed interface BackendSource {
    /** Ein Backend des Integrators; der Server ruft es für jede Anfrage. */
    class Custom(val backend: QueryBackend) : BackendSource

    /** Tabellen mit Zeilenquellen des Integrators. */
    class Simulation(val tables: List<SimTable>) : BackendSource

    /** Weder Backend noch Tabellen. */
    data object None : BackendSource
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
sealed interface StartOutcome {
    /** Der Server läuft und ist gebunden. */
    class Running(val server: TdsServer) : StartOutcome

    /**
     * Der Start ist abgebrochen; kein Port ist gebunden und kein Thread des Servers läuft. [reason] ist ein Text ohne
     * Konfigurationswert, Pfad oder Ausnahmetext. [issues] nennt bei einer ungültigen Konfiguration jeden Fehler mit
     * Schlüssel und Art (kein Wert), sonst ist die Liste leer.
     */
    class Aborted(val reason: String, issues: List<ConfigIssue> = emptyList()) : StartOutcome {
        val issues: List<ConfigIssue> = issues.toList()
    }
}

/**
 * Der laufende Server (`ARC-008`). [stop] schließt den Listener und alle Verbindungen.
 *
 * Lebenszyklus: [start] bis [stop]. [stop] ist idempotent: jeder weitere Aufruf endet ohne Wirkung. Aus welchem Thread
 * [start] und [stop] gerufen werden, ist nicht festgelegt; getestet sind sie nacheinander aus einem Thread.
 *
 * Eine [ServerConfig] gilt für einen Start: der Start löscht das Passwort im Schlüsselmaterial. Wer neu startet,
 * baut eine neue Konfiguration.
 *
 * Das JVM-Flag `-Djdk.tls.server.newSessionTicketCount=0` setzt der Aufrufer; [start] prüft es und setzt es nicht
 * (`SPEC-023`). Der Einstieg schreibt kein Log; ein Abbruch steht in [StartOutcome.Aborted].
 */
class TdsServer internal constructor(private val listener: TdsListener, private val log: ServerLog) {
    private val stopped = AtomicBoolean(false)

    /** Der gebundene Port. */
    val port: Int get() = listener.port

    /** Beendet den Server; ein weiterer Aufruf hat keine Wirkung. */
    suspend fun stop() {
        listener.shutdown()
        if (stopped.compareAndSet(false, true)) log.line("gestoppt")
    }

    companion object {
        /**
         * Startet den Server mit [config] und [source] (`SPEC-059`). Geprüft werden das JVM-Flag, die Konfiguration
         * ([io.github.pt9912.tdswire.config.ConfigValidation]), die Tabellen und das TLS-Material; erst danach wird
         * gebunden.
         */
        suspend fun start(config: ServerConfig, source: BackendSource): StartOutcome =
            Bootstrap.launch(config, Wiring(requests = source.requests()), System::getProperty)
    }
}
