package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.response.ResponseForm
import io.github.pt9912.tdswire.protocol.response.ResponseSequencer
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeChannel
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.session.ReplyStream
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
 * Tokens in eine Senke ([StreamedResponse]), die Pakete der Packet Size der Sitzung auf den Kanal
 * schreibt und bei langsamem Kanal suspendiert (Rückstau); das erste COLMETADATA setzt `STREAMING`; das
 * letzte Paket schreibt die Schleife nach dem Ausgang, danach folgt der Übergang `RequestFinished`.
 * Die Verbindungsfolge liest die Schleife aus `ErrorMapper.fateOf` des Ausgangs. Nach einer offenen Folge
 * ist die Sitzung `READY` und die Schleife liest die nächste Nachricht.
 *
 * Ablauf je RPC (`TDS-FA-04.a`, `SPEC-030`): derselbe Rahmen (`RUNNING`, Antwort mit `RequestFinished`).
 * `RpcBinder` bindet die Nutzlast an Text und typisierte Parameter; das [backend] bekommt sie in einem
 * `QueryRequest` mit Sitzungs-ID, neuer Anfrage-ID und ohne Frist, der Rest wie beim Batch; die Antwort hat die
 * Form [ResponseForm.RPC] (DONEINPROC, RETURNSTATUS, RETURNVALUE, DONEPROC), die auf einen Batch die Form
 * [ResponseForm.BATCH]. Lehnt die
 * Bindung den RPC ab ([RpcRejection]), wird das Backend nicht gerufen. Weist der Parser die Nachricht ab,
 * gilt wie beim Batch `TDS_PROTOCOL_ERROR` und die Verbindung schließt; jede andere Ablehnung ergibt
 * `TDS_UNSUPPORTED_RPC` (ERROR und DONE mit Fehlerstatus, `SPEC-013`) bei offener Verbindung; der Name des
 * Grundes steht im Log.
 *
 * Die Schleife endet immer mit geschlossenem Kanal: nach `CLOSED`, nach einer Nachricht, die sie nicht
 * beantwortet, und bei einer Ausnahme oder dem Abbruch der Coroutine.
 *
 * Festlegungen ohne Messung: Transaction Manager und ATTENTION in `READY` bleiben unbeantwortet, die
 * Verbindung schließt; die Tokenfolge einer RPC-Antwort ist
 * nicht am Referenzclient gemessen; ein SQL_BATCH, dessen Nachricht
 * der Parser abweist, wird mit `TDS_PROTOCOL_ERROR` beantwortet (`ErrorMapper.protocol`, als ganze
 * Nachricht über den Sitzungsrahmen); die Pakete einer Antwort gehen außerhalb der Sperre des Rahmens
 * hinaus (die Schleife ist während einer Anfrage der einzige Schreiber); bei Ausfall der Senke bleiben
 * bereits geschriebene Pakete auf dem Draht, ein gehaltenes Paket und das EOM-Paket entfallen, und die
 * Verbindung schließt; während einer Anfrage liest die Schleife nicht.
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
            channel.usePacketSize(ready.session.packetSize)
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
        answer(event.message)?.let { live.dispatch(it) }
        return live.context.state == ConnectionState.READY
    }

    /** Das Ereignis, das die Anfrage beendet; `null`, wenn die Sitzung beim Schreiben der Antwort geschlossen wurde. */
    private suspend fun answer(message: TdsMessage): HandshakeEvent.RequestFinished? =
        if (message.type == PacketType.RPC) answerRpc(message) else answerBatch(message)

    private suspend fun answerBatch(batch: TdsMessage): HandshakeEvent.RequestFinished? =
        when (val parsed = SqlBatchParser.parse(batch.payload)) {
            is ProtocolResult.Failed -> errors.protocol(parsed.error).let { requestFinished(it.fate, it.message) }
            is ProtocolResult.Ok -> execute(parsed.value.text, emptyList(), ResponseForm.BATCH)
        }

    private suspend fun answerRpc(rpc: TdsMessage): HandshakeEvent.RequestFinished? =
        when (val bound = RpcBinder.bind(rpc.payload)) {
            is RpcBinding.Rejected -> {
                val response = bound.error?.let { errors.protocol(it) }
                    ?: errors.code(ErrorCode.TDS_UNSUPPORTED_RPC, cause = bound.reason.name)
                requestFinished(response.fate, response.message)
            }

            is RpcBinding.Query -> execute(bound.sql, bound.parameters, ResponseForm.RPC)
        }

    private suspend fun execute(
        text: String,
        parameters: List<QueryParameter>,
        form: ResponseForm,
    ): HandshakeEvent.RequestFinished? {
        val live = ready.live
        val reply = live.openReply(PacketType.TABULAR_RESULT)
        val response = StreamedResponse(reply) { live.dispatch(HandshakeEvent.StreamStarted) }
        val request = QueryRequest(ready.session.id, requestIds(), text, parameters, null)
        val fate = errors.fateOf(sequencer.run(backend, request, sinkAround(response), form))
        return if (live.context.state == ConnectionState.CLOSED) null else finished(fate, reply)
    }

    /** Schließt die Antwort mit dem EOM-Paket ab; scheitert das, schließt die Verbindung ohne weitere Antwort. */
    private suspend fun finished(fate: ConnectionFate, reply: ReplyStream): HandshakeEvent.RequestFinished {
        if (fate == ConnectionFate.CLOSE_WITHOUT_REPLY || !reply.finish()) {
            return HandshakeEvent.RequestFinished(null, connectionOpen = false)
        }
        return HandshakeEvent.RequestFinished(null, connectionOpen = fate == ConnectionFate.OPEN, replyWritten = true)
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
