package io.github.pt9912.tdswire.domain.telemetry

/** Die Ebene eines Ereignisses (`TDS-FA-09.a`); sie ist je Ereignisart fest. */
public enum class EventLevel {
    /** Ein Fehler, den der Betreiber beheben muss. */
    ERROR,

    /** Eine Abweichung oder Abschwächung, die der Betreiber kennen soll. */
    WARN,

    /** Ein Zustand des Betriebs. */
    INFO,

    /** Ein Ereignis für die Diagnose im Einzelfall. */
    DEBUG,
}

/**
 * Die Senke für Log und Telemetrie (`SPEC-059`): `TdsServer.start` nimmt sie als Parameter entgegen. Die
 * Bibliothek schreibt nirgends hin; wer ein Log will, übergibt eine Senke und formatiert das [TelemetryEvent]
 * selbst.
 *
 * Vertrag der Senke:
 * - [accept] kommt aus beliebigen Threads, auf dem Anfragepfad der Bibliothek; bei Ereignissen einer Verbindung auf
 *   dem Dispatcher der Verbindung (`Dispatchers.Default`, prozessweit).
 * - [accept] blockiert nicht und wirft nicht. Eine Senke, die blockiert, hält den Anfragepfad an und belegt Threads
 *   dieses Pools, die auch andere Coroutinen des Prozesses nutzen; davor schützt die Bibliothek nicht.
 * - Wirft [accept] dennoch eine `Exception`, fängt die Bibliothek sie ab und verwirft sie; weder die Anfrage noch die
 *   Verbindung brechen. Bei einer `InterruptedException` bleibt das Flag der Unterbrechung des Threads gesetzt.
 * - Eine `CancellationException` wird nicht gefangen (Abbruch einer Coroutine bleibt sichtbar). Dass auch jeder
 *   `Error` nicht gefangen wird, ist eine Festlegung dieses Stands, aus der Behandlung der Abbruchausnahme im
 *   Anfragepfad nur für die `CancellationException` abgeleitet. Belegt ist es an der Grenze der Meldestelle
 *   (`Telemetry.emit`); für Meldestellen in der Infrastruktur des Listeners (`AcceptFailed`, `ConnectionFailed`) ist
 *   es weder gemessen noch zugesagt.
 * - Wer die Senke übergibt, besitzt sie und schließt sie; die Bibliothek schließt sie nicht.
 * - Mehrere Server in einer JVM unterscheidet das Ereignis nicht: [TelemetryEvent.connectionId] zählt je Server.
 *   Wer eine Senke für mehrere Server teilt, übergibt jedem Server eine Hülle, die den Server kennzeichnet.
 *
 * Das Ereignis trägt für Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext kein Feld.
 *
 * Ein Beispiel einer Senke liegt im Modul `testserver` (`LogAdapter`): eine JSON-Zeile je Ereignis, begrenzte
 * Warteschlange mit einem Schreiber, Leeren mit Frist beim Prozessende. Die Bibliothek ist daran nicht gebunden.
 */
public fun interface TelemetrySink {
    /** Nimmt ein Ereignis entgegen; siehe den Vertrag der Schnittstelle. */
    public fun accept(event: TelemetryEvent)

    public companion object {
        /** Keine Senke, die Voreinstellung von `start`: die Bibliothek erzeugt dann kein Ereignis. */
        public val NONE: TelemetrySink = TelemetrySink { }
    }
}
