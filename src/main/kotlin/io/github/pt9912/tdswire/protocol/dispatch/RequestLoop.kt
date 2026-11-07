package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.response.CallLayout
import io.github.pt9912.tdswire.protocol.response.ResponseForm
import io.github.pt9912.tdswire.protocol.response.ResponseSequencer
import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeChannel
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.session.ReplyStream
import io.github.pt9912.tdswire.protocol.sqlbatch.SqlBatchParser
import io.github.pt9912.tdswire.protocol.token.EnvChange
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * Die Anfrageschleife einer angemeldeten Sitzung (`ARC-005`, `SPEC-001`, `TDS-FA-03.a`): liest Nachrichten
 * aus dem [channel], gibt sie dem Sitzungsrahmen des Handshakes ([HandshakeOutcome.Ready]) und beantwortet
 * ein SQL_BATCH und einen RPC.
 *
 * Ablauf je SQL_BATCH: Der Rahmen setzt `READY` auf `RUNNING`; `SqlBatchParser` liest ALL_HEADERS und
 * Text; das [backend] bekommt Sitzungs-ID, eine neue Anfrage-ID aus [requestIds], den Text unverändert,
 * keine Parameter und die Frist (Beginn plus `QUERY_TIMEOUT` aus [times], `SPEC-009`); der `ResponseSequencer` mit
 * `ErrorMapper.sequencerMapping` schreibt die Tokens in eine Senke ([StreamedResponse]), die Pakete der Packet
 * Size der Sitzung auf den Kanal schreibt und bei langsamem Kanal suspendiert (Rückstau); das erste COLMETADATA
 * setzt `STREAMING`; das letzte Paket schreibt die Schleife nach dem Ausgang, danach folgt der Übergang
 * `RequestFinished`. Die Verbindungsfolge liest die Schleife aus `ErrorMapper.fateOf` des Ausgangs. Nach einer
 * offenen Folge ist die Sitzung `READY` und die Schleife liest die nächste Nachricht.
 *
 * Ablauf je RPC (`TDS-FA-04.a`, `SPEC-030`): derselbe Rahmen (`RUNNING`, Antwort mit `RequestFinished`).
 * `RpcBinder` bindet die Nutzlast an Text und typisierte Parameter; das [backend] bekommt sie in einem
 * `QueryRequest` mit Sitzungs-ID, neuer Anfrage-ID und Frist, der Rest wie beim Batch; die Antwort hat die
 * Form [ResponseForm.RPC] (DONEINPROC, RETURNSTATUS, RETURNVALUE, DONEPROC), die auf einen Batch die Form
 * [ResponseForm.BATCH]. Lehnt die
 * Bindung den RPC ab ([RpcRejection]), wird das Backend nicht gerufen. Weist der Parser die Nachricht ab,
 * gilt der Fehler des Parsers (`ErrorMapper.protocol`, ERROR und DONE, auch beim RPC: bei einem DONEPROC meldet der
 * Referenzclient den Fehler einer Nachricht mit mehreren RPCs nicht, `make e2e` Stufe 6) und die Verbindung schließt:
 * ein Strukturfehler ergibt `TDS_PROTOCOL_ERROR`, eine Typkennung außerhalb der Typliste (`INVALID_TYPE`, etwa `FE 00`)
 * `TDS_UNSUPPORTED_TYPE` (50003, Class 20). Jede andere Ablehnung, auch ein gültig kodierter PLP-Typ
 * (`UNSUPPORTED_PLP`), ergibt `TDS_UNSUPPORTED_RPC` (50002; ERROR und DONEPROC mit Fehlerstatus, `SPEC-013`) bei
 * offener Verbindung; der Name des Grundes steht im Log.
 *
 * Prepared-Statements (`TDS-FA-04.a` Nr. 5, `SPEC-030`): `sp_prepexec` merkt den Text unter einem neuen Handle der
 * Sitzung ([PreparedHandles]), führt ihn mit den Parametern der Nachricht über das [backend] aus und
 * liefert das Handle als RETURNVALUE hinter einem RETURNSTATUS ([HandleReturningBackend]); endet die Antwort mit einem
 * Fehler, bevor das Handle im Strom steht, gibt die Schleife es wieder frei; ein schon gesendetes Handle bleibt
 * gültig. Hält die Sitzung schon
 * [handleLimit] Handles, ergibt `sp_prepexec` `TDS_PREPARE_LIMIT`, ohne das Backend zu rufen;
 * die Handles bleiben gültig. `sp_execute` führt den gemerkten Text mit den neuen Parametern aus; ein Handle,
 * das die Sitzung nicht hält, ergibt `TDS_UNKNOWN_HANDLE` ohne Backend-Aufruf. Beide Fehler lassen die Verbindung
 * offen (Class 16). Der RPC `sp_unprepare` und `sp_prepare` sind nicht im Katalog (`TDS_UNSUPPORTED_RPC`) und geben
 * nichts frei. Die Freigabe läuft als SQL_BATCH `EXEC sp_unprepare <n>` ([UnprepareBatch]): die Schleife beantwortet
 * ihn ohne das Backend, gibt jedes gehaltene Handle der Reihe nach frei, und jede Nummer, die die Sitzung nicht hält
 * (nie vergeben, schon freigegeben, vor RESETCONNECTION), ergibt einen ERROR `TDS_SIM_UNKNOWN_PREPARED_HANDLE` in
 * derselben Antwort, die dann mit einem DONE mit Fehlerstatus endet; die Verbindung bleibt offen. Ein Text, der nicht
 * ganz diese Form hat, geht wie jeder Batch an das Backend. Die Handles enden mit der Schleife und mit dem Bit
 * RESETCONNECTION (nächster Absatz).
 *
 * RESETCONNECTION (`ADR-0027` Entscheidung 5, `SPEC-001`): Das Bit steht im Status des ersten Pakets einer
 * Nachricht. `MessageAssembler` liest es in `TdsMessage.resetConnection`, `TransportHandshakeChannel` gibt die
 * Nachricht unverändert weiter, und der Automat des Sitzungsrahmens setzt an der Anfrage in `READY`, die es trägt, die
 * Aktion `ResetSession`; der Rahmen führt sie unter seiner Sperre aus, vor der Verarbeitung dieser Anfrage. Die
 * Schleife hängt dafür [PreparedHandles.clear] ein: alle Handles sind danach unbekannt (`TDS_UNKNOWN_HANDLE`), die
 * Vergabe beginnt wieder bei 1, die Grenze von [handleLimit] gilt neu. Die Anfrage mit dem Bit wird
 * danach wie jede andere beantwortet; erstes Token jeder Antwort ist ENVCHANGE Typ 18 mit leerem neuem und leerem altem
 * Wert (am echten Server gemessen, Messprotokoll W-7, Batch; für den RPC Setzung nach derselben Regel). Das Bit an
 * einer Nachricht außerhalb von `READY` wirkt nicht: dort ist eine Anfrage unzulässig und die Verbindung schließt
 * (Festlegung; `ADR-0027` entscheidet es nicht).
 * Weiteren Sitzungszustand als die Handles führt die Schleife nicht.
 *
 * Abbruch (`SPEC-029`, `ADR-0027`): Während einer Anfrage liest die Schleife mit ([AbortableRun]). Ein ATTENTION in
 * `RUNNING` oder `STREAMING` und der Ablauf der Frist gehen denselben Weg: der erste Übergang nach `CANCELLING`
 * gewinnt, der Lauf des Sequencers wird abgebrochen (genau ein `cancel()` des Backends, `close()` der Ausführung, ein
 * zurückgehaltener Abschluss entfällt), und die Antwort endet bei ATTENTION mit genau einem `DONE[ATTN]` ohne ERROR,
 * bei der Frist mit ERROR `TDS_TIMEOUT` und DONE mit Fehlerstatus (in der Antwort auf einen RPC DONEPROC). Die
 * Verbindung bleibt in beiden Fällen offen und die folgende Anfrage bekommt nur ihre eigene Antwort. Ist die Antwort
 * beim Gewinn des Auslösers schon vollständig, entfällt der Abbruch: ATTENTION wird als eigene Nachricht bestätigt,
 * die Frist ändert nichts. Beendet das Backend den Abbruch nicht innerhalb von `CANCEL_TIMEOUT`, schließt die
 * Verbindung ohne weitere Bytes. ATTENTION in `READY` wird bestätigt, ohne Cancel. Ist der Strom nicht mehr zu
 * schließen (ein Schreibvorgang scheitert), schließt die Verbindung ohne ERROR und DONE.
 *
 * Disconnect (`TDS-FA-07.a`, `SPEC-029`, `SPEC-031`): Meldet das Lesen während einer Anfrage das Ende der Gegenstelle
 * oder einen Fehler, schließt die Sitzung, der Lauf wird abgebrochen (genau ein `cancel()` des Backends, `close()` der
 * Ausführung), und es folgt kein Schreibvorgang mehr; das gilt in `RUNNING`, `STREAMING` und `CANCELLING`. Im
 * Leerlauf endet die Schleife mit dem Lesen. Die Sitzungsressourcen (Handles) sind mit der Schleife freigegeben.
 *
 * Geordnetes Beenden (`SPEC-044`, `ADR-0027` Entscheidung 4 und 2c): Die Schleife meldet dem [ShutdownGate], wenn eine
 * Anfrage läuft. Eine Anfrage, die nach dem Beginn des Beendens angenommen wird, beantwortet sie mit ERROR
 * `TDS_SHUTDOWN` und DONE mit Fehlerstatus (im RPC DONEPROC), ohne das Backend zu rufen; danach schließt die
 * Verbindung. Endet eine
 * Anfrage im Beenden regulär, bleibt die Verbindung (als „läuft“ gemeldet) offen: jede weitere Nachricht, die
 * danach vollständig wird, erhält `TDS_SHUTDOWN` (`SPEC-044` Nr. 3), danach endet die Verbindung. Sie endet auch beim
 * Ende der Gegenstelle und beim Ablauf von `SHUTDOWN_GRACE`: ohne laufende Anfrage schließt der angemeldete Abbruch
 * dann den Kanal. Eine Verbindung, die beim Beginn ruhte, hat der Listener schon beendet (Nr. 2).
 * Nach Ablauf von `SHUTDOWN_GRACE` bricht die Schleife die laufende Anfrage wie bei ATTENTION ab
 * ([CancelCause.SHUTDOWN]): genau ein `cancel()`, die Antwort endet mit `DONE[ATTN]`, die Verbindung endet.
 *
 * Die Schleife endet immer mit geschlossenem Kanal: nach `CLOSED`, nach einer Nachricht, die sie nicht
 * beantwortet, und bei einer Ausnahme oder dem Abbruch der Coroutine.
 *
 * Festlegungen ohne Messung: Transaction Manager in `READY` bleibt unbeantwortet, die
 * Verbindung schließt; die Tokenfolge einer RPC-Antwort ist
 * nicht am Referenzclient gemessen; ein SQL_BATCH, dessen Nachricht
 * der Parser abweist, wird mit `TDS_PROTOCOL_ERROR` beantwortet (`ErrorMapper.protocol`, als ganze
 * Nachricht über den Sitzungsrahmen); jeder Schreibvorgang einer Antwort läuft unter der Sperre des Rahmens
 * (`HandshakeSession.guarded`), damit Übergänge nur zwischen zwei Schreibvorgängen stattfinden; bei Ausfall der
 * Senke bleiben
 * bereits geschriebene Pakete auf dem Draht, ein gehaltenes Paket und das EOM-Paket entfallen, und die
 * Verbindung schließt; die Frist wirkt nicht, solange ein Schreibvorgang am Rückstau hängt (er hält die Sperre,
 * die der Übergang braucht).
 *
 * Kopplung: [hooks] sind die Einhängepunkte für Anfrage-Kennung, Senke und geordnetes Beenden und die Grenze der
 * Prepared-Handles ([RequestHooks]); [times] trägt die
 * Fristen und die Uhr ([RequestTimes]).
 */
internal class RequestLoop(
    private val ready: HandshakeOutcome.Ready,
    private val channel: HandshakeChannel,
    private val backend: QueryBackend,
    private val errors: ErrorMapper = ErrorMapper(),
    private val times: RequestTimes = RequestTimes(),
    hooks: RequestHooks = RequestHooks(),
) {
    private val requestIds = hooks.requestIds
    private val sinkAround = hooks.sinkAround
    private val gate = hooks.gate
    private val handleLimit = hooks.handleLimit
    private val mapping = errors.sequencerMapping()
    private val sequencer = ResponseSequencer(mapping)
    private val aborted = AbortedAnswer(errors, mapping)
    private val bulk = BulkDispatch(backend)

    /** Der Eingang und der Bereich der Läufe; gesetzt, sobald [run] beginnt. */
    private class Rig(val tray: InboundTray, val detached: CoroutineScope)

    private lateinit var rig: Rig

    /** Die Prepared-Handles dieser Sitzung; eine Schleife bedient genau eine Sitzung. */
    internal val handles = PreparedHandles(handleLimit)

    /** Der Lauf der Anfrage, die gerade ausgeführt wird; `null` zwischen den Anfragen. */
    @Volatile
    private var active: AbortableRun? = null

    /** Was die Schleife dem [gate] zuletzt gemeldet hat; es bekommt nur Wechsel. */
    private var announced = false

    /** Wahr, solange die Antwort auf eine Anfrage mit `RESETCONNECTION` ihr ENVCHANGE Typ 18 noch nicht trägt. */
    private var resetPending = false

    init {
        ready.live.onSessionReset {
            handles.clear()
            bulk.clear()
        }
        gate.onGraceExpired {
            val run = active
            if (run != null) run.abortForShutdown() else channel.close()
        }
    }

    /** Läuft, bis die Verbindung endet; schließt den Kanal in jedem Ausgang. */
    suspend fun run() {
        try {
            coroutineScope {
                val tray = InboundTray(channel, this)
                rig = Rig(tray, CoroutineScope(currentCoroutineContext().minusKey(Job)))
                try {
                    channel.usePacketSize(ready.session.packetSize)
                    var running = true
                    while (running) running = step()
                } finally {
                    channel.close()
                    tray.release()
                }
            }
        } finally {
            announce(false)
            handles.clear()
            channel.close()
        }
    }

    private fun announce(running: Boolean) {
        if (announced == running) return
        announced = running
        gate.requestRunning(running)
    }

    /** Eine Nachricht; `false`, wenn die Schleife endet. */
    private suspend fun step(): Boolean {
        val live = ready.live
        val event = when (val inbound = rig.tray.take()) {
            is Inbound.Message -> HandshakeEvent.Message(inbound.message)
            Inbound.EndOfStream -> HandshakeEvent.PeerClosed
            Inbound.Failed -> HandshakeEvent.ReadFailed
        }
        live.dispatch(event)
        val state = live.context.state
        return when {
            event !is HandshakeEvent.Message -> false

            // Ein ATTENTION in READY hat der Rahmen bestätigt; die Sitzung bleibt READY.
            event.message.type == PacketType.ATTENTION -> state == ConnectionState.READY

            state != ConnectionState.RUNNING -> false

            else -> {
                announce(true)
                resetPending = event.message.resetConnection
                val finished = answer(event.message)
                (if (resetPending) withResetFirst(finished) else finished)?.let { live.dispatch(it) }
                resetPending = false
                val open = live.context.state == ConnectionState.READY
                if (!(open && gate.begun)) announce(false)
                open
            }
        }
    }

    /** Das Ereignis, das die Anfrage beendet; `null`, wenn die Sitzung beim Schreiben der Antwort geschlossen wurde. */
    private suspend fun answer(message: TdsMessage): HandshakeEvent.RequestFinished? = when {
        gate.begun -> refuse(ErrorCode.TDS_SHUTDOWN, "shutdown", formOf(message))

        message.type == PacketType.RPC -> {
            bulk.clear()
            answerRpc(message)
        }

        // Die Antwortform des Pakets 0x07 ist eine Setzung: BATCH (DONE, kein DONEINPROC/RETURNSTATUS); das Paket
        // ist kein Aufruf einer Prozedur. Der Mitschnitt des echten Servers (Messprotokoll, Massenladen) ist offen.
        message.type == PacketType.BULK_LOAD -> when (val loaded = bulk.answer(message)) {
            is BulkDispatch.Answer.Malformed -> {
                val response = errors.protocol(loaded.error)
                requestFinished(response.fate, response.message)
            }

            is BulkDispatch.Answer.Reply -> execute("", emptyList(), layout = null, via = loaded.via)
        }

        else -> answerBatch(message)
    }

    private suspend fun answerBatch(batch: TdsMessage): HandshakeEvent.RequestFinished? =
        when (val parsed = SqlBatchParser.parse(batch.payload)) {
            is ProtocolResult.Failed -> errors.protocol(parsed.error).let { requestFinished(it.fate, it.message) }

            is ProtocolResult.Ok -> {
                val text = parsed.value.text
                val released = UnprepareBatch.parse(text)
                if (released == null) {
                    execute(text, emptyList(), layout = null, via = bulk.statement(text) ?: backend)
                } else {
                    bulk.clear()
                    UnprepareBatch.answer(released, handles, errors)
                }
            }
        }

    private suspend fun answerRpc(rpc: TdsMessage): HandshakeEvent.RequestFinished? =
        when (val bound = RpcBinder.bind(rpc.payload)) {
            is RpcBinding.Rejected -> {
                val response = bound.error?.let { errors.protocol(it) }
                    ?: errors.code(ErrorCode.TDS_UNSUPPORTED_RPC, cause = bound.reason.name, form = ResponseForm.RPC)
                requestFinished(response.fate, response.message)
            }

            is RpcBinding.Query -> execute(bound.sql, bound.parameters, bound.layout)

            is RpcBinding.Prepare -> prepare(bound)

            is RpcBinding.ExecutePrepared -> executePrepared(bound)
        }

    private suspend fun prepare(bound: RpcBinding.Prepare): HandshakeEvent.RequestFinished? {
        val handle = handles.register(bound.sql)
            ?: return refuse(ErrorCode.TDS_PREPARE_LIMIT, "handle-limit", ResponseForm.RPC)
        val returning = HandleReturningBackend(backend, handle)
        return execute(bound.sql, bound.parameters, CallLayout.PREPARE_EXECUTE, returning) {
            if (it != SequenceOutcome.COMPLETED && !returning.returned) handles.release(handle)
        }
    }

    private suspend fun executePrepared(bound: RpcBinding.ExecutePrepared): HandshakeEvent.RequestFinished? {
        val sql = handles.sql(bound.handle)
            ?: return refuse(ErrorCode.TDS_UNKNOWN_HANDLE, "unknown-handle", ResponseForm.RPC)
        return execute(sql, bound.parameters, CallLayout.EXECUTE_PREPARED)
    }

    /** Die Antwort mit einem eigenen Fehlercode, ohne das Backend zu rufen; [cause] ist die feste Marke fürs Log. */
    private fun refuse(code: ErrorCode, cause: String, form: ResponseForm): HandshakeEvent.RequestFinished {
        val response = errors.code(code, cause = cause, form = form)
        return requestFinished(response.fate, response.message)
    }

    /** Führt die Anfrage aus; [layout] ist `null` für einen SQL_BATCH (Form BATCH), sonst die Aufrufform des RPC. */
    private suspend fun execute(
        text: String,
        parameters: List<QueryParameter>,
        layout: CallLayout?,
        via: QueryBackend = backend,
        onOutcome: suspend (SequenceOutcome) -> Unit = {},
    ): HandshakeEvent.RequestFinished? {
        val form = if (layout == null) ResponseForm.BATCH else ResponseForm.RPC
        val live = ready.live
        val reply = live.openReply(PacketType.TABULAR_RESULT)
        val response = StreamedResponse(reply) { live.dispatch(HandshakeEvent.StreamStarted) }
        val deadline = times.queryTimeout?.let { times.now() + it.inWholeMilliseconds }
        val request = QueryRequest(ready.session.id, requestIds(), text, parameters, deadline)
        val remaining = deadline?.let { (it - times.now()).milliseconds }
        val run = AbortableRun(live, rig.tray, times, rig.detached)
        val sink = if (resetPending) LeadingToken(response, EnvChange.ResetConnection) else response
        resetPending = false
        active = run
        val end = try {
            run.run(remaining) { sequencer.run(via, request, sinkAround(sink), form, layout ?: CallLayout.PROCEDURE) }
        } finally {
            active = null
        }
        return when (end) {
            is RunEnd.Finished -> {
                onOutcome(end.outcome)
                val fate = errors.fateOf(end.outcome)
                if (live.context.state == ConnectionState.CLOSED) null else finished(fate, reply)
            }

            is RunEnd.Cut -> {
                onOutcome(end.outcome ?: SequenceOutcome.CLOSE_WITHOUT_COMPLETION)
                if (live.context.state == ConnectionState.CLOSED) null else aborted.conclude(end, reply, form)
            }

            RunEnd.Abandoned -> {
                live.abandon(CloseReason.CANCEL_TIMEOUT)
                null
            }

            RunEnd.Disconnected -> {
                onOutcome(SequenceOutcome.CLOSE_WITHOUT_COMPLETION)
                null
            }
        }
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
 * Die Einhängepunkte der [RequestLoop]. [requestIds] liefert die Kennung jeder Anfrage. [sinkAround] umhüllt die
 * Senke der Anfrage, bevor der `ResponseSequencer` sie bekommt; die Voreinstellung reicht sie unverändert durch. Kein
 * Backend-Ereignis lässt den Encoder ein Token ablehnen (die Abbildungen von Spalten und Werten prüfen dieselben
 * Grenzen); der Einhängepunkt erlaubt es einem Test, eine Senke zu stellen, die ein Token nicht annimmt (Ausgang
 * `CLOSE_WITHOUT_COMPLETION`). [gate] verbindet die Schleife mit dem geordneten Beenden des Servers ([ShutdownGate]);
 * die Voreinstellung kennt keines. [handleLimit] ist die Grenze der gleichzeitig gehaltenen Prepared-Handles einer
 * Sitzung (`RequestSettings.maxPreparedHandles`, `SPEC-058`).
 */
internal class RequestHooks(
    val requestIds: () -> String = { UUID.randomUUID().toString() },
    val sinkAround: (TokenSink) -> TokenSink = { it },
    val gate: ShutdownGate = ShutdownGate.NONE,
    val handleLimit: Int = PreparedHandles.DEFAULT_MAX_HANDLES,
)

/**
 * Das Ereignis, das eine Anfrage beendet: [reply] entfällt bei [ConnectionFate.CLOSE_WITHOUT_REPLY]; offen
 * bleibt die Verbindung nur bei [ConnectionFate.OPEN].
 */
internal fun requestFinished(fate: ConnectionFate, reply: TdsMessage?): HandshakeEvent.RequestFinished =
    HandshakeEvent.RequestFinished(
        reply.takeIf { fate != ConnectionFate.CLOSE_WITHOUT_REPLY },
        connectionOpen = fate == ConnectionFate.OPEN,
    )

/** Die Antwortform zur Art der Anfrage: RPC für einen RPC, sonst die Form des Batches. */
private fun formOf(message: TdsMessage) = if (message.type == PacketType.RPC) ResponseForm.RPC else ResponseForm.BATCH

/**
 * Stellt dem fertigen Antworttext ENVCHANGE Typ 18 voran (Antwort auf eine Anfrage mit `RESETCONNECTION`); eine
 * Antwort, die der Lauf schon in den Strom geschrieben hat, trägt es und bleibt unverändert.
 */
private fun withResetFirst(finished: HandshakeEvent.RequestFinished?): HandshakeEvent.RequestFinished? {
    val reply = finished?.reply ?: return finished
    val encoder = TokenEncoder()
    check(encoder.write(EnvChange.ResetConnection) is ProtocolResult.Ok) { "ENVCHANGE nicht schreibbar" }
    val prefixed = TdsMessage(reply.type, encoder.toByteArray() + reply.payload)
    return HandshakeEvent.RequestFinished(prefixed, finished.connectionOpen, finished.replyWritten)
}
