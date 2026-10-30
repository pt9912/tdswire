package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Ereignisse des Automaten: vollständige Nachrichten aus dem Paket-Codec und Ereignisse der
 * Verbindung. Das Package `protocol.session` importiert vom Package `transport.framing` nur
 * Wertetypen und Konstanten ([TdsMessage], [PacketType], `FramingLimits`, `PacketHeader`), keine
 * Leser, Schreiber oder Kanäle.
 */
sealed interface HandshakeEvent {
    val kind: EventKind

    /** Die Verbindung steht. */
    data object Start : HandshakeEvent {
        override val kind: EventKind get() = EventKind.START
    }

    /** Der TLS-Handschlag ist abgeschlossen. */
    data object TlsEstablished : HandshakeEvent {
        override val kind: EventKind get() = EventKind.TLS_ESTABLISHED
    }

    /**
     * Eine vollständige Nachricht. Festlegung ohne Messung: SQL_BATCH, RPC und Transaction Manager
     * sind Anfragen; ATTENTION ist eigene Art; jeder andere Pakettyp ist [EventKind.OTHER_MESSAGE].
     * Die Bits RESETCONNECTION und IGNORE der Nachricht wertet der Automat nicht aus (offen,
     * `ADR-0004`).
     */
    class Message(val message: TdsMessage) : HandshakeEvent {
        override val kind: EventKind
            get() = when (message.type) {
                PacketType.PRELOGIN -> EventKind.PRELOGIN_MESSAGE
                PacketType.LOGIN7 -> EventKind.LOGIN7_MESSAGE
                PacketType.SQL_BATCH, PacketType.RPC, PacketType.TRANSACTION_MANAGER -> EventKind.REQUEST_MESSAGE
                PacketType.ATTENTION -> EventKind.ATTENTION_MESSAGE
                else -> EventKind.OTHER_MESSAGE
            }

        override fun toString(): String = "Message($message)"
    }

    /** Das erste Ergebnis einer Anfrage beginnt (COLMETADATA ist geschrieben); `RUNNING` wird `STREAMING`. */
    data object StreamStarted : HandshakeEvent {
        override val kind: EventKind get() = EventKind.STREAM_STARTED
    }

    /**
     * Die Antwort auf die Anfrage steht fest. [reply] ist die vollständige Antwort-Nachricht oder `null`,
     * wenn die Verbindung ohne Antwort schließt; [connectionOpen] sagt, ob die Sitzung danach wieder
     * `READY` ist. Eine offene Verbindung ohne Antwort gibt es nicht.
     */
    class RequestFinished(val reply: TdsMessage?, val connectionOpen: Boolean) : HandshakeEvent {
        init {
            require(reply != null || !connectionOpen) { "eine offene Verbindung braucht eine Antwort" }
        }

        override val kind: EventKind get() = EventKind.REQUEST_FINISHED

        override fun toString(): String = "RequestFinished(reply=${reply != null}, open=$connectionOpen)"
    }

    /** Der Adapter hat angenommen; [sessionId] vergibt der Aufrufer, die Übergangsfunktion bleibt rein. */
    class AuthAccepted(val principal: Principal, val sessionId: String) : HandshakeEvent {
        override val kind: EventKind get() = EventKind.AUTH_ACCEPTED

        override fun toString(): String = "AuthAccepted(verborgen)"
    }

    /** Der Adapter hat abgelehnt oder ist mit einer Ausnahme gescheitert ([adapterFailureType] nennt nur deren Typ). */
    class AuthRejected(val rejection: LoginRejection, val adapterFailureType: String? = null) : HandshakeEvent {
        override val kind: EventKind get() = EventKind.AUTH_REJECTED

        override fun toString(): String = "AuthRejected($rejection)"
    }

    /** `LOGIN_TIMEOUT` ist abgelaufen (`SPEC-008`). */
    data object LoginTimeout : HandshakeEvent {
        override val kind: EventKind get() = EventKind.LOGIN_TIMEOUT
    }

    /** Die Gegenstelle hat geschlossen. */
    data object PeerClosed : HandshakeEvent {
        override val kind: EventKind get() = EventKind.PEER_CLOSED
    }

    /** Lesen oder TLS ist gescheitert (Paket-Codec, Kanal oder Handschlag). */
    data object ReadFailed : HandshakeEvent {
        override val kind: EventKind get() = EventKind.READ_FAILED
    }
}
