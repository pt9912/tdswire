package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.response.ResponseSequencer
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeChannel
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.sqlbatch.SqlBatchParser
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import java.util.UUID

/**
 * Die Anfrageschleife einer angemeldeten Sitzung (`ARC-005`, `SPEC-001`, `TDS-FA-03.a`): liest Nachrichten
 * aus dem [channel], gibt sie dem Sitzungsrahmen des Handshakes ([HandshakeOutcome.Ready]) und beantwortet
 * ein SQL_BATCH und einen RPC.
 *
 * Ablauf je SQL_BATCH: Der Rahmen setzt `READY` auf `RUNNING`; `SqlBatchParser` liest ALL_HEADERS und
 * Text; das [backend] bekommt Sitzungs-ID, eine neue Anfrage-ID aus [requestIds], den Text unverändert,
 * keine Parameter und keine Frist; der `ResponseSequencer` mit `ErrorMapper.sequencerMapping` schreibt die
 * Tokens in eine Senke im Speicher, das erste COLMETADATA setzt `STREAMING`; die Antwort geht mit dem
 * Übergang `RequestFinished` unter der Sperre des Rahmens hinaus, und die Verbindungsfolge liest die
 * Schleife aus `ErrorMapper.fateOf` des Ausgangs. Nach einer offenen Folge ist die Sitzung `READY` und die
 * Schleife liest die nächste Nachricht.
 *
 * Ablauf je RPC (`TDS-FA-04.a`, `SPEC-030`): derselbe Rahmen (`RUNNING`, Antwort mit `RequestFinished`).
 * `RpcBinder` bindet die Nutzlast an Text und typisierte Parameter; das [backend] bekommt sie in einem
 * `QueryRequest` mit Sitzungs-ID, neuer Anfrage-ID und ohne Frist, der Rest wie beim Batch. Lehnt die
 * Bindung den RPC ab ([RpcRejection]), wird das Backend nicht gerufen und der Client erhält
 * `TDS_UNSUPPORTED_RPC` (ERROR und DONE mit Fehlerstatus, `SPEC-013`); die Verbindung bleibt offen.
 *
 * Die Schleife endet immer mit geschlossenem Kanal: nach `CLOSED`, nach einer Nachricht, die sie nicht
 * beantwortet, und bei einer Ausnahme oder dem Abbruch der Coroutine.
 *
 * Festlegungen ohne Messung: Transaction Manager und ATTENTION in `READY` bleiben unbeantwortet, die
 * Verbindung schließt; die Antwort auf einen erfolgreichen RPC besteht aus denselben Tokens wie die auf
 * einen Batch (DONE, kein DONEPROC, kein RETURNSTATUS, kein RETURNVALUE); ein SQL_BATCH, dessen Nachricht der Parser abweist, wird mit `TDS_PROTOCOL_ERROR`
 * beantwortet (`ErrorMapper.protocol`); die Antwort wird vollständig gepuffert (`BufferedResponse`);
 * während einer Anfrage liest die Schleife nicht.
 *
 * Kopplung: [sinkAround] umhüllt die Senke der Anfrage, bevor der `ResponseSequencer` sie bekommt; die
 * Voreinstellung reicht sie unverändert durch. Kein Backend-Ereignis lässt den Encoder ein Token
 * ablehnen (die Abbildungen von Spalten und Werten prüfen dieselben Grenzen); der Einhängepunkt erlaubt
 * es einem Test, eine Senke zu stellen, die ein Token nicht annimmt (Ausgang `CLOSE_WITHOUT_COMPLETION`).
 */
class RequestLoop(
    private val ready: HandshakeOutcome.Ready,
    private val channel: HandshakeChannel,
    private val backend: QueryBackend,
    private val errors: ErrorMapper = ErrorMapper(),
    private val requestIds: () -> String = { UUID.randomUUID().toString() },
    private val sinkAround: (TokenSink) -> TokenSink = { it },
) {
    private val sequencer = ResponseSequencer(errors.sequencerMapping())

    /** Läuft, bis die Verbindung endet; schließt den Kanal in jedem Ausgang. */
    suspend fun run() {
        try {
            var running = true
            while (running) running = step()
        } finally {
            channel.close()
        }
    }

    /** Eine Nachricht; `false`, wenn die Schleife endet. */
    private suspend fun step(): Boolean {
        val live = ready.live
        val event = when (val inbound = channel.receive()) {
            is Inbound.Message -> HandshakeEvent.Message(inbound.message)
            Inbound.EndOfStream -> HandshakeEvent.PeerClosed
            Inbound.Failed -> HandshakeEvent.ReadFailed
        }
        live.dispatch(event)
        if (event !is HandshakeEvent.Message || live.context.state != ConnectionState.RUNNING) return false
        live.dispatch(answer(event.message))
        return live.context.state == ConnectionState.READY
    }

    private suspend fun answer(message: TdsMessage): HandshakeEvent.RequestFinished =
        if (message.type == PacketType.RPC) answerRpc(message) else answerBatch(message)

    private suspend fun answerBatch(batch: TdsMessage): HandshakeEvent.RequestFinished =
        when (val parsed = SqlBatchParser.parse(batch.payload)) {
            is ProtocolResult.Failed -> errors.protocol(parsed.error).let { requestFinished(it.fate, it.message) }
            is ProtocolResult.Ok -> execute(parsed.value.text, emptyList())
        }

    private suspend fun answerRpc(rpc: TdsMessage): HandshakeEvent.RequestFinished =
        when (val bound = RpcBinder.bind(rpc.payload)) {
            is RpcBinding.Rejected ->
                errors.code(ErrorCode.TDS_UNSUPPORTED_RPC).let { requestFinished(it.fate, it.message) }
            is RpcBinding.Query -> execute(bound.sql, bound.parameters)
        }

    private suspend fun execute(text: String, parameters: List<QueryParameter>): HandshakeEvent.RequestFinished {
        val live = ready.live
        val response = BufferedResponse { live.dispatch(HandshakeEvent.StreamStarted) }
        val request = QueryRequest(ready.session.id, requestIds(), text, parameters, null)
        val fate = errors.fateOf(sequencer.run(backend, request, sinkAround(response)))
        return requestFinished(fate, response.message())
    }
}

/**
 * Das Ereignis, das eine Anfrage beendet: [reply] entfällt bei [ConnectionFate.CLOSE_WITHOUT_REPLY]; offen
 * bleibt die Verbindung nur bei [ConnectionFate.OPEN].
 */
internal fun requestFinished(fate: ConnectionFate, reply: TdsMessage?): HandshakeEvent.RequestFinished =
    HandshakeEvent.RequestFinished(
        reply.takeIf { fate != ConnectionFate.CLOSE_WITHOUT_REPLY },
        connectionOpen = fate == ConnectionFate.OPEN,
    )
