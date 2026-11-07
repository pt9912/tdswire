package io.github.pt9912.tdswire.domain.telemetry

/**
 * Die Spans der Bibliothek (`SPEC-017` bis `SPEC-019`); [spanName] ist der Name aus der Spezifikation und der Wert des
 * Feldes `span` im Ereignis.
 */
internal enum class SpanKind(val spanName: String) {
    /** Eine Verbindung vom Annehmen bis zum Ende des Handlers (`SPEC-017`). */
    CONNECTION("tds.connection"),

    /** Eine Anfrage einer Sitzung von der Annahme der Nachricht bis zum Ende der Antwort (`SPEC-018`). */
    QUERY("tds.query"),

    /** Ein Aufruf des Backends von `execute` bis `close` der Ausführung (`SPEC-019`). */
    BACKEND("backend.execute"),
}

/**
 * Wie ein Span endet (Attribut `outcome`, Festlegung dieser Bibliothek, nicht gemessen); im Ereignis steht der Name in
 * Kleinbuchstaben. Die Menge ist je Span begrenzt:
 * - `tds.connection`: [OK], [LOGIN_REJECTED], [ABORTED], [ERROR], [CANCELLED].
 * - `tds.query`: [OK], [ERROR], [PROTOCOL_ERROR], [CANCELLED], [TIMEOUT], [SHUTDOWN], [DISCONNECTED], [ABANDONED],
 *   [CLOSED].
 * - `backend.execute`: [OK], [ERROR], [CANCELLED].
 */
internal enum class SpanOutcome {
    /** Regulär zu Ende: Antwort vollständig, Verbindung ohne Ausnahme zurückgekehrt, Strom des Backends beendet. */
    OK,

    /**
     * Verbindung: der Handler hat eine Ausnahme geworfen. Anfrage: die Antwort ist ein Fehler (Fehler des Backends,
     * abgelehnter RPC, unbekanntes Handle). Backend-Aufruf: ein Fehlerereignis im Strom oder ein Fehler des Stroms.
     */
    ERROR,

    /** Die Eingabe der Anfrage hat der Nachrichten-Codec abgewiesen (`TDS_PROTOCOL_ERROR`, `TDS_UNSUPPORTED_TYPE`). */
    PROTOCOL_ERROR,

    /** Abgebrochen: ATTENTION des Clients; bei der Verbindung der Abbruch des Scopes der Verbindung. */
    CANCELLED,

    /** Die Frist der Anfrage (`QUERY_TIMEOUT`) ist abgelaufen. */
    TIMEOUT,

    /** Die Anfrage wurde im geordneten Beenden abgewiesen oder abgebrochen (`SPEC-044`). */
    SHUTDOWN,

    /** Die Gegenstelle hat die Verbindung während der Anfrage beendet. */
    DISCONNECTED,

    /** Das Backend hat den Abbruch nicht innerhalb von `CANCEL_TIMEOUT` beendet. */
    ABANDONED,

    /** Die Verbindung wurde geschlossen, ohne dass die Antwort vollständig geschrieben wurde. */
    CLOSED,

    /** Die Anmeldung wurde abgelehnt. */
    LOGIN_REJECTED,

    /** Die Verbindung endete vor dem Abschluss der Anmeldung, ohne abgelehnte Anmeldung. */
    ABORTED,
    ;

    /** Der Wert im Ereignis. */
    val label: String get() = name.lowercase()
}

/** Wo die Verbindung endete (Attribut `tds.phase`, Festlegung): vor oder nach dem Abschluss der Anmeldung. */
internal enum class ConnectionPhase {
    HANDSHAKE,
    SESSION,
    ;

    val label: String get() = name.lowercase()
}

/** Die Art einer Anfrage (Attribut `tds.request_kind`, Festlegung): SQL_BATCH oder RPC. */
internal enum class RequestKind {
    SQL_BATCH,
    RPC,
    ;

    val label: String get() = name.lowercase()
}

/**
 * Ein Span (`SPEC-017` bis `SPEC-019`). [end] beendet ihn genau einmal; jeder weitere Aufruf hat keine Wirkung. Das
 * Ende läuft auf dem Pfad des Aufrufers und ruft die Senke: eine Ausnahme der Senke fängt die Bibliothek ab, außer
 * `CancellationException` und `Error` (Vertrag von `TelemetrySink`), und eine blockierende Senke hält den Aufrufer an.
 */
internal interface Span {
    fun end(outcome: SpanOutcome)
}

/** Der Span einer Verbindung; er kennt die Sitzung, sobald die Anmeldung angenommen ist. */
internal interface ConnectionSpan : Span {
    /** Die Anmeldung ist angenommen: die Sitzung ist aktiv, bis der Span endet. */
    fun loginAccepted(context: EventContext): SessionInstruments
}

/** Die Spans der Anfragen einer angemeldeten Sitzung. */
internal interface SessionInstruments {
    /** Beginnt den Span einer Anfrage mit der Kennung [requestId]. */
    fun request(requestId: String, kind: RequestKind): Span

    /** Beginnt den Span eines Backend-Aufrufs der Anfrage [requestId]. */
    fun backend(requestId: String): Span

    companion object {
        val NONE: SessionInstruments = NoInstruments
    }
}

/**
 * Der Zugang der Bibliothek zu Spans und Metriken (`ADR-0020`): `protocol` und `runtime` rufen nur diese
 * Schnittstelle, die Aggregation liegt in `observability`.
 */
internal interface Instruments {
    /** Beginnt den Span der Verbindung mit der Nummer [connectionId]. */
    fun connection(connectionId: Long): ConnectionSpan

    companion object {
        val NONE: Instruments = NoInstruments
    }
}

/** Die Voreinstellung ohne Senke: nichts wird gezählt, nichts erzeugt. */
internal object NoInstruments : Instruments, ConnectionSpan, SessionInstruments {
    override fun connection(connectionId: Long): ConnectionSpan = this

    override fun loginAccepted(context: EventContext): SessionInstruments = this

    override fun request(requestId: String, kind: RequestKind): Span = this

    override fun backend(requestId: String): Span = this

    override fun end(outcome: SpanOutcome) = Unit
}
