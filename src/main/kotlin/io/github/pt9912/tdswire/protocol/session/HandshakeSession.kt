package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Der Sitzungsrahmen einer Verbindung: hält den [HandshakeContext], führt die Aktionen des
 * Automaten aus und serialisiert Übergang und Schreiben unter einem `Mutex` je Sitzung
 * (`ADR-0004`, `SPEC-001`). Ausgeführt werden Senden, Log, Grenzwechsel und Schließen; [dispatch]
 * gibt die Aktion zurück, auf die der Aufrufer wartet ([HandshakeAction.BeginTls] oder
 * [HandshakeAction.Authenticate]).
 */
internal class HandshakeSession(
    private val automaton: HandshakeAutomaton,
    private val channel: HandshakeChannel,
    private val log: HandshakeLog,
) {
    private val lock = Mutex()

    var context = HandshakeContext()
        private set

    var closeReason: CloseReason? = null
        private set

    suspend fun dispatch(event: HandshakeEvent): HandshakeAction? = lock.withLock {
        val transition = automaton.step(context, event)
        context = transition.context
        execute(transition.actions)
    }

    /**
     * Eine Antwort in Stücken über den Kanal: jedes [ReplyStream.write] und [ReplyStream.finish] läuft
     * unter der Sperre dieser Sitzung, ein Übergang einer zweiten Coroutine wird also zwischen zwei
     * Schreibvorgängen ausgeführt, nicht in einem. Ist die Sitzung `CLOSED`, schreibt der Strom nicht
     * mehr; scheitert ein Schreibvorgang, schließt die Sitzung wie beim Senden einer Nachricht
     * (`CloseReason.WRITE_FAILED`).
     */
    fun openReply(type: PacketType): ReplyStream {
        val inner = channel.openReply(type)
        return object : ReplyStream {
            override suspend fun write(bytes: ByteArray): Boolean = guarded { inner.write(bytes) }

            override suspend fun finish(): Boolean = guarded { inner.finish() }
        }
    }

    private suspend fun guarded(step: suspend () -> Boolean): Boolean = lock.withLock {
        if (context.state == ConnectionState.CLOSED) return@withLock false
        val written = step()
        if (!written) abortWrite()
        written
    }

    /**
     * Beendet die Sitzung nach Ablauf von `LOGIN_TIMEOUT`, auch wenn die Annahme schon beschlossen,
     * aber nicht vollständig gesendet war: Der Zustand ist dann `READY`, dort ist `LOGIN_TIMEOUT`
     * ohne Wirkung, und der Zweig unten schließt den Kanal und setzt den Zustand auf `CLOSED`
     * (fail-closed; ein Test hält diesen Zweig).
     *
     * Festlegung ohne Messung / offen (`ADR-0004`): Das Schreiben einer Antwort ist abbrechbar, nicht
     * durch `NonCancellable` geschützt. Bricht die Frist oder der Aufrufer es ab, kann ein Paket halb
     * geschrieben sein; die Verbindung wird danach geschlossen und nicht weiterverwendet. Ob das
     * Schreiben eines Pakets gegen Abbruch zu schützen ist, ist in `ADR-0004` offen, ebenso der
     * Zeitpunkt des Schließens nach `TDS_SHUTDOWN`; der Handshake berührt ihn nicht.
     */
    suspend fun expire() {
        dispatch(HandshakeEvent.LoginTimeout)
        lock.withLock {
            if (context.state != ConnectionState.CLOSED) {
                context = HandshakeContext(state = ConnectionState.CLOSED)
                closeReason = CloseReason.LOGIN_TIMEOUT
                channel.close()
            }
        }
    }

    private suspend fun execute(actions: List<HandshakeAction>): HandshakeAction? {
        var awaited: HandshakeAction? = null
        for (action in actions) {
            when (action) {
                is HandshakeAction.Send -> if (!channel.send(action.message)) {
                    abortWrite()
                    return null
                }

                is HandshakeAction.Log -> log.record(action.entry)

                HandshakeAction.SwitchToSessionLimits -> channel.useSessionLimits()

                is HandshakeAction.Close -> {
                    closeReason = action.reason
                    channel.close()
                }

                HandshakeAction.BeginTls, is HandshakeAction.Authenticate -> awaited = action

                HandshakeAction.Forward -> Unit
            }
        }
        return awaited
    }

    private fun abortWrite() {
        context = HandshakeContext(state = ConnectionState.CLOSED)
        closeReason = CloseReason.WRITE_FAILED
        channel.close()
    }
}
