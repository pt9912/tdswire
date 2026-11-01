package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Der Sitzungsrahmen einer Verbindung: hält den [HandshakeContext], führt die Aktionen des
 * Automaten aus und serialisiert Übergang und Schreiben unter einem `Mutex` je Sitzung
 * (`ADR-0004`, `SPEC-001`). Ausgeführt werden Senden, Log, Grenzwechsel und Schließen; [dispatch]
 * gibt die Aktion zurück, auf die der Aufrufer wartet ([HandshakeAction.BeginTls],
 * [HandshakeAction.Authenticate] oder [HandshakeAction.Cancel]).
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
     *
     * Abbruch (`ADR-0027`): Ab `CANCELLING` schreibt die reguläre Antwort nicht mehr; [write] und [finish]
     * liefern dann `false`, ohne die Sitzung zu schließen. Der Abbruch beendet die Antwort mit
     * [GuardedReply.conclude].
     */
    fun openReply(type: PacketType): GuardedReply = GuardedReply(channel.openReply(type))

    /** Die Antwort aus [openReply]. */
    inner class GuardedReply internal constructor(private val inner: ReplyStream) : ReplyStream {
        override suspend fun write(bytes: ByteArray): Boolean = guarded(REGULAR_STOPS) { inner.write(bytes) }

        override suspend fun finish(): Boolean = guarded(REGULAR_STOPS) { inner.finish() }

        /**
         * Beendet die Antwort im Abbruch: hängt [tail] an, falls vorhanden, und schreibt den Rest samt EOM-Bit.
         * Anders als [write] und [finish] schreibt sie auch in `CANCELLING`; `false` bei `CLOSED` und wenn
         * das Schreiben scheitert (die Sitzung schließt dann).
         */
        suspend fun conclude(tail: ByteArray?): Boolean =
            guarded(CLOSED_ONLY) { (tail == null || inner.write(tail)) && inner.finish() }
    }

    /**
     * Ein Schreibvorgang unter der Sperre. Festlegung zu `ADR-0004` (Schreiben eines Pakets und Abbruch): der Abbruch
     * einer Anfrage unterbricht kein Paket. Beide Auslöser des Abbruchs (ATTENTION, Frist) gehen über [dispatch] und
     * brauchen dieselbe Sperre; sie warten, bis das Paket im Schreiben ganz auf dem Draht ist, und erst danach, mit
     * dem Zustand `CANCELLING`, bricht die Anfrage ab. Danach beginnt kein reguläres Schreiben mehr
     * ([GuardedReply.write] und [GuardedReply.finish]), und die Bestätigung hängt an ein vollständiges Paket an.
     * Ein abgebrochener Schreibvorgang schlösse den Kanal (Vertrag von `SuspendingChannel`); den Abbruch der
     * Verbindung selbst (Disconnect, Beenden) unterbricht ein Paket weiterhin. Hängt ein Schreibvorgang am Rückstau,
     * hält er die Sperre und mit ihr den Abbruch an (Grenze); [abandon] schließt dann den Kanal ohne die Sperre.
     */
    private suspend fun guarded(stopped: Set<ConnectionState>, step: suspend () -> Boolean): Boolean = lock.withLock {
        if (context.state in stopped) return@withLock false
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
     * Das Schreiben einer Handshake-Antwort ([execute]) ist abbrechbar, nicht durch `NonCancellable`
     * geschützt. Bricht die Frist oder der Aufrufer es ab, kann ein Paket halb geschrieben sein; die
     * Verbindung wird danach geschlossen und nicht weiterverwendet. Die Antwort einer Anfrage ([openReply])
     * schreibt ihre Pakete dagegen geschützt.
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

    /**
     * Schließt die Verbindung, weil das Backend den Cancel nicht beendet hat (`SPEC-033`). Der Kanal schließt
     * zuerst und ohne die Sperre: ein Schreibvorgang, der am Rückstau hängt und die Sperre hält, endet damit.
     */
    suspend fun abandon(reason: CloseReason) {
        channel.close()
        lock.withLock {
            if (context.state != ConnectionState.CLOSED) {
                context = context.closed()
                closeReason = reason
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

                HandshakeAction.BeginTls, is HandshakeAction.Authenticate, is HandshakeAction.Cancel ->
                    awaited = action

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

    private companion object {
        val REGULAR_STOPS = setOf(ConnectionState.CLOSED, ConnectionState.CANCELLING)
        val CLOSED_ONLY = setOf(ConnectionState.CLOSED)
    }
}
