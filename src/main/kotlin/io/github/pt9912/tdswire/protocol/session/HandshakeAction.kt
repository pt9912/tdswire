package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.RejectReason
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/** Warum der Automat die Verbindung beendet. */
internal enum class CloseReason {
    PEER_CLOSED,
    READ_FAILED,
    WRITE_FAILED,
    LOGIN_TIMEOUT,
    PROTOCOL_VIOLATION,
    MESSAGE_TOO_LARGE,
    ENCRYPTION_INCOMPATIBLE,
    LOGIN_REJECTED,

    /** Der Ausgang einer Anfrage schließt die Verbindung (`ErrorMapper.fateOf`), mit oder ohne Antwort. */
    REQUEST_ENDED,

    /** Das Backend hat den Cancel nicht innerhalb von `CANCEL_TIMEOUT` beendet (`SPEC-033`). */
    CANCEL_TIMEOUT,
}

/**
 * Warum ein Login abgelehnt wird; der Grund steht nur im Server-Log (`SPEC-012`) und nennt weder
 * Kennung noch Passwort. Die Antwort an den Client ist für jeden Grund dieselbe.
 */
internal enum class LoginRejection {
    UNKNOWN_USER,
    WRONG_PASSWORD,
    INPUT_TOO_LONG,
    UNSUPPORTED_INTEGRATED_SECURITY,
    UNSUPPORTED_FEDERATED_AUTHENTICATION,
    UNSUPPORTED_PASSWORD_CHANGE,
    ADAPTER_FAILURE,
    ;

    companion object {
        fun from(reason: RejectReason): LoginRejection = when (reason) {
            RejectReason.UNKNOWN_USER -> UNKNOWN_USER
            RejectReason.WRONG_PASSWORD -> WRONG_PASSWORD
            RejectReason.INPUT_TOO_LONG -> INPUT_TOO_LONG
        }
    }
}

/** Der minimale Log-Anschluss des Handshakes; Einträge tragen weder Kennung noch Passwort. */
internal sealed interface LogEntry {
    data class LoginRejected(val rejection: LoginRejection) : LogEntry

    /** Die Ausnahme eines Adapters; nur ihr Typ, weil ihr Text Konfigurationswerte tragen kann (`SPEC-048`). */
    data class AdapterFailed(val exceptionType: String) : LogEntry

    /** Der unverschlüsselte Testmodus wurde angeboten (`SPEC-010`: die Abschwächung ist im Log sichtbar). */
    data object EncryptionNotOffered : LogEntry
}

/** Anschluss, an den der Handshake [LogEntry] meldet. */
internal fun interface HandshakeLog {
    fun record(entry: LogEntry)

    companion object {
        val NONE = HandshakeLog { }
    }
}

/** Was der Sitzungsrahmen auf einen Übergang hin ausführt; der Automat selbst schreibt und ruft nichts. */
internal sealed interface HandshakeAction {
    data class Send(val message: TdsMessage) : HandshakeAction

    /** Der TLS-Handschlag beginnt; danach folgt [HandshakeEvent.TlsEstablished] oder [HandshakeEvent.ReadFailed]. */
    data object BeginTls : HandshakeAction

    /** Der Adapter ist zu rufen, abseits des Transport-Threads. */
    data class Authenticate(val request: AuthRequest) : HandshakeAction

    /** Die Rahmen-Grenze wechselt von der Vor-Login-Grenze (`SPEC-046`) auf die Standardgrenze (`SPEC-007`). */
    data object SwitchToSessionLimits : HandshakeAction

    data class Log(val entry: LogEntry) : HandshakeAction

    /** Die Nachricht gehört der Anfrageverarbeitung; der Handshake-Automat bearbeitet sie nicht. */
    data object Forward : HandshakeAction

    /**
     * Die Nachricht trägt das Bit RESETCONNECTION (`ADR-0027` Entscheidung 5): der Sitzungsrahmen setzt den
     * Sitzungszustand der Anfrageverarbeitung zurück ([HandshakeSession.onSessionReset]), unter seiner Sperre und
     * vor [Forward] derselben Nachricht.
     */
    data object ResetSession : HandshakeAction

    data class Close(val reason: CloseReason) : HandshakeAction

    /**
     * Der Übergang nach `CANCELLING` hat [cause] gewonnen: genau dieser Auslöser bricht die Anfrage ab. Der
     * Sitzungsrahmen führt die Aktion nicht aus, sondern gibt sie dem Aufrufer von `dispatch` zurück; ein
     * Auslöser, der verliert, bekommt sie nicht.
     */
    data class Cancel(val cause: CancelCause) : HandshakeAction
}

/** Wer den Abbruch einer laufenden Anfrage ausgelöst hat (`ADR-0027` Entscheidung 2). */
internal enum class CancelCause {
    /** ATTENTION des Clients; die Antwort ist `DONE[ATTN]` ohne ERROR. */
    ATTENTION,

    /** Ablauf der Frist der Anfrage; die Antwort ist ERROR `TDS_TIMEOUT` und DONE mit Fehlerstatus. */
    DEADLINE,
}
