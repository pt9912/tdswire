package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Der Verbindungsautomat von PRELOGIN bis zur Login-Antwort (`ARC-004`, `SPEC-001`, `SPEC-028`,
 * `ADR-0004`): eine reine Funktion [step] von (Kontext, Ereignis) auf (Folgekontext, Aktionen). Sie
 * schreibt nichts, ruft keinen Adapter und liest keine Uhr; der Sitzungsrahmen führt die Aktionen
 * aus ([HandshakeDriver]). Zulässigkeit entscheidet [HandshakeMatrix], das Ziel eines zulässigen
 * Übergangs entscheiden Reihenfolge ([HandshakeOrder]), Richtlinie ([EncryptionPolicy]) und der
 * Inhalt der Nachricht.
 *
 * Festlegung ohne Messung / offen (`ADR-0004`): Ein unzulässiger Übergang, jedes Lesen mit Fehler,
 * ein ungültiges PRELOGIN oder LOGIN7 und der Fristablauf beenden die Verbindung ohne Antwort
 * ([CloseReason]); die Antwort auf einen unzulässigen Übergang und die Reichweite von ATTENTION im
 * Handshake sind dort offen. Die Fehlerkategorie `TDS_PROTOCOL_ERROR` (`SPEC-011`) verlangt in der
 * Aktionsspalte "Abbruch oder ERROR, je nach Zustand"; hier gilt Abbruch. Nachrichten über
 * [FramingLimits.MAX_PRELOGIN_MESSAGE_BYTES] weist der Automat zusätzlich zur Grenze des Paket-Codecs
 * ab (`SPEC-046`).
 */
class HandshakeAutomaton(private val config: HandshakeConfig = HandshakeConfig()) {
    private val prelogin = PreloginNegotiation(config)
    private val login = LoginTransitions(config)

    fun step(context: HandshakeContext, event: HandshakeEvent): Transition =
        when (HandshakeMatrix.cell(context.state, event.kind)) {
            HandshakeMatrix.Cell.ILLEGAL -> context.closing(CloseReason.PROTOCOL_VIOLATION)
            HandshakeMatrix.Cell.IGNORED -> Transition(context, emptyList())
            HandshakeMatrix.Cell.PERMITTED -> permitted(context, event)
        }

    private fun permitted(context: HandshakeContext, event: HandshakeEvent): Transition = when (event) {
        HandshakeEvent.Start -> start(context)
        HandshakeEvent.TlsEstablished -> tlsEstablished(context)
        is HandshakeEvent.Message -> message(context, event.message)
        HandshakeEvent.StreamStarted -> Transition(context.copy(state = ConnectionState.STREAMING), emptyList())
        is HandshakeEvent.RequestFinished -> requestFinished(context, event)
        is HandshakeEvent.AuthAccepted -> login.accepted(context, event)
        is HandshakeEvent.AuthRejected -> login.rejected(context, event)
        HandshakeEvent.LoginTimeout -> context.closing(CloseReason.LOGIN_TIMEOUT)
        HandshakeEvent.PeerClosed -> context.closing(CloseReason.PEER_CLOSED)
        HandshakeEvent.ReadFailed -> context.closing(CloseReason.READ_FAILED)
    }

    private fun start(context: HandshakeContext): Transition = when (config.order) {
        HandshakeOrder.PRELOGIN_FIRST -> Transition(context.copy(state = ConnectionState.PRELOGIN), emptyList())

        HandshakeOrder.TLS_FIRST ->
            Transition(context.copy(state = ConnectionState.TLS_NEGOTIATION), listOf(HandshakeAction.BeginTls))
    }

    private fun tlsEstablished(context: HandshakeContext): Transition {
        val next = when (config.order) {
            HandshakeOrder.PRELOGIN_FIRST -> ConnectionState.LOGIN7
            HandshakeOrder.TLS_FIRST -> ConnectionState.PRELOGIN
        }
        return Transition(context.copy(state = next), emptyList())
    }

    /**
     * PRELOGIN und LOGIN7 haben ihr eigenes Ziel; in `READY` gehört die Nachricht der
     * Anfrageverarbeitung (`Forward`). Ein SQL_BATCH und ein RPC setzen den Anfragezustand auf `RUNNING`;
     * Transaction Manager und ATTENTION lassen `READY` stehen.
     */
    private fun message(context: HandshakeContext, message: TdsMessage): Transition = when (context.state) {
        ConnectionState.PRELOGIN -> onPrelogin(context, message.payload)
        ConnectionState.LOGIN7 -> login.onLogin7(context, message.payload)
        else -> forward(context, message)
    }

    private fun forward(context: HandshakeContext, message: TdsMessage): Transition {
        val starts = message.type == PacketType.SQL_BATCH || message.type == PacketType.RPC
        val next = if (starts) ConnectionState.RUNNING else context.state
        return Transition(context.copy(state = next), listOf(HandshakeAction.Forward))
    }

    /** Die Antwort wird gesendet; danach ist die Sitzung `READY`, oder sie schließt (Senden vor Schließen). */
    private fun requestFinished(context: HandshakeContext, event: HandshakeEvent.RequestFinished): Transition {
        val reply = listOfNotNull(event.reply?.let { HandshakeAction.Send(it) })
        return if (event.connectionOpen) {
            Transition(context.copy(state = ConnectionState.READY), reply)
        } else {
            Transition(context.closed(), reply + HandshakeAction.Close(CloseReason.REQUEST_ENDED))
        }
    }

    private fun onPrelogin(context: HandshakeContext, payload: ByteArray): Transition =
        when (val negotiation = prelogin.negotiate(payload)) {
            is Negotiation.Failed -> context.closing(negotiation.reason)

            is Negotiation.Agreed -> {
                val actions = buildList {
                    add(HandshakeAction.Send(negotiation.response))
                    if (negotiation.encryption == NegotiatedEncryption.NOT_SUPPORTED) {
                        add(HandshakeAction.Log(LogEntry.EncryptionNotOffered))
                    }
                    if (negotiation.tlsFollows) add(HandshakeAction.BeginTls)
                }
                val next = if (negotiation.tlsFollows) ConnectionState.TLS_NEGOTIATION else ConnectionState.LOGIN7
                Transition(context.copy(state = next, encryption = negotiation.encryption), actions)
            }
        }
}
