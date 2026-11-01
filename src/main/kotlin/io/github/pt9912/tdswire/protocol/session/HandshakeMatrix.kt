package io.github.pt9912.tdswire.protocol.session

/**
 * Die Übergangsmatrix: für jede Zelle (Zustand, Ereignisart) steht fest, ob der Übergang zulässig
 * ist ([Cell.PERMITTED]), ohne Wirkung bleibt ([Cell.IGNORED]) oder unzulässig ist ([Cell.ILLEGAL]).
 * Das Ziel eines zulässigen Übergangs bestimmt [HandshakeAutomaton] aus Reihenfolge, Richtlinie und
 * Nachrichteninhalt.
 *
 * Festlegung ohne Messung / offen (`ADR-0004`): Ein unzulässiger Übergang beendet die Verbindung
 * ohne Antwort (Setzung des Spike). Das gilt auch für ATTENTION vor `READY`; das RESETCONNECTION-Bit
 * einer Nachricht wertet der Automat nicht aus.
 * `LOGIN_TIMEOUT`, `PEER_CLOSED` und `READ_FAILED` sind nach `CLOSED` ohne Wirkung; `LOGIN_TIMEOUT`
 * ist es auch in `READY`, `RUNNING` und `STREAMING`, weil die Frist für eine angemeldete Verbindung nicht
 * gilt (`SPEC-008`).
 *
 * Abbruch (`ADR-0027`): In `RUNNING` und `STREAMING` ist ATTENTION zulässig und führt mit dem Fristablauf
 * ([EventKind.DEADLINE_EXPIRED]) nach `CANCELLING`; jede andere Nachricht dort ist unzulässig. In `CANCELLING`
 * bleiben ein weiteres ATTENTION, der Fristablauf und der Beginn eines Ergebnisses ohne Wirkung (der erste
 * Auslöser hat gewonnen, ein zweiter Cancel entsteht nie); nur das Ende der Anfrage ist zulässig. In `READY`
 * und `CLOSED` ist ein Fristablauf ohne Wirkung (die Antwort ist schon gesendet oder die Verbindung zu); in
 * `CLOSED` ist es auch ein ATTENTION. ATTENTION in `READY` ist zulässig und wird bestätigt. In den
 * Zuständen des Handshakes ist ATTENTION unzulässig und schließt ohne Antwort (Grundregel A).
 */
internal object HandshakeMatrix {
    enum class Cell { PERMITTED, IGNORED, ILLEGAL }

    private val loggedIn = setOf(
        ConnectionState.READY,
        ConnectionState.RUNNING,
        ConnectionState.STREAMING,
        ConnectionState.CANCELLING,
    )

    private val inertWhileCancelling =
        setOf(EventKind.STREAM_STARTED, EventKind.ATTENTION_MESSAGE, EventKind.DEADLINE_EXPIRED)

    private val ending = setOf(EventKind.PEER_CLOSED, EventKind.READ_FAILED, EventKind.LOGIN_TIMEOUT)

    private val specific = mapOf(
        ConnectionState.CONNECTED to setOf(EventKind.START),
        ConnectionState.PRELOGIN to setOf(EventKind.PRELOGIN_MESSAGE),
        ConnectionState.TLS_NEGOTIATION to setOf(EventKind.TLS_ESTABLISHED),
        ConnectionState.LOGIN7 to setOf(EventKind.LOGIN7_MESSAGE),
        ConnectionState.AUTHENTICATING to setOf(EventKind.AUTH_ACCEPTED, EventKind.AUTH_REJECTED),
        ConnectionState.READY to setOf(EventKind.REQUEST_MESSAGE, EventKind.ATTENTION_MESSAGE),
        ConnectionState.RUNNING to setOf(
            EventKind.STREAM_STARTED,
            EventKind.REQUEST_FINISHED,
            EventKind.ATTENTION_MESSAGE,
            EventKind.DEADLINE_EXPIRED,
        ),
        ConnectionState.STREAMING to setOf(
            EventKind.REQUEST_FINISHED,
            EventKind.ATTENTION_MESSAGE,
            EventKind.DEADLINE_EXPIRED,
        ),
        ConnectionState.CANCELLING to setOf(EventKind.REQUEST_FINISHED),
        ConnectionState.CLOSED to emptySet(),
    )

    fun cell(state: ConnectionState, kind: EventKind): Cell = when {
        kind in ending && (state == ConnectionState.CLOSED) -> Cell.IGNORED
        kind == EventKind.LOGIN_TIMEOUT && state in loggedIn -> Cell.IGNORED
        kind in ending -> Cell.PERMITTED
        state == ConnectionState.CANCELLING && kind in inertWhileCancelling -> Cell.IGNORED
        isLate(state, kind) -> Cell.IGNORED
        kind in specific.getValue(state) -> Cell.PERMITTED
        else -> Cell.ILLEGAL
    }

    /** Ein Fristablauf nach dem Ende der Antwort und ein ATTENTION nach dem Schließen; beide kommen zu spät. */
    private fun isLate(state: ConnectionState, kind: EventKind): Boolean =
        (kind == EventKind.DEADLINE_EXPIRED && (state == ConnectionState.READY || state == ConnectionState.CLOSED)) ||
            (kind == EventKind.ATTENTION_MESSAGE && state == ConnectionState.CLOSED)

    /** Die Zellen je Art, aus [cell] gezählt (nicht aus einer Zahl im Text). */
    fun census(): Map<Cell, Int> = ConnectionState.entries
        .flatMap { state -> EventKind.entries.map { kind -> cell(state, kind) } }
        .groupingBy { it }
        .eachCount()
}
