package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.session.CancelCause
import io.github.pt9912.tdswire.protocol.session.HandshakeAction
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.HandshakeSession
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/** Wie ein [AbortableRun] endete. */
internal sealed interface RunEnd {
    /** Der Lauf endete mit [outcome], ohne dass ein Auslöser des Abbruchs gewonnen hat. */
    class Finished(val outcome: SequenceOutcome) : RunEnd

    /**
     * Ein Auslöser hat den Übergang nach `CANCELLING` gewonnen und das Backend hat den Abbruch innerhalb von
     * `CANCEL_TIMEOUT` beendet. [outcome] ist der Ausgang des Laufs, wenn dieser bis zum Ende lief, bevor der
     * Abbruch wirkte; `null`, wenn der Abbruch ihn unterbrach oder er nie begann.
     */
    class Cut(val cause: CancelCause, val outcome: SequenceOutcome?) : RunEnd

    /** Das Backend hat den Abbruch nicht innerhalb von `CANCEL_TIMEOUT` beendet. */
    data object Abandoned : RunEnd

    /**
     * Die Gegenstelle hat die Verbindung während des Laufs beendet (Ende oder Lesefehler): die Sitzung ist
     * geschlossen, der Lauf abgebrochen (genau ein `cancel()` und `close()`), und es wird nichts mehr geschrieben.
     * Beachtet das Backend den Abbruch nicht innerhalb von `CANCEL_TIMEOUT`, ist das Ergebnis [Abandoned].
     */
    data object Disconnected : RunEnd
}

/**
 * Ein Lauf des Response Sequencers, den ATTENTION oder die Frist der Anfrage abbrechen (`SPEC-029`, `ADR-0027`
 * Entscheidung 1 und 2, `ADR-0013` Entscheidung 4).
 *
 * Beide Auslöser gehen denselben Weg: sie melden ihr Ereignis dem Sitzungsrahmen, und nur der, dessen Übergang
 * nach `CANCELLING` führt (die Aktion [HandshakeAction.Cancel] kommt zurück), bricht den Lauf ab. Der zweite
 * Auslöser, ein wiederholtes ATTENTION und ein Fristablauf nach dem Ende ändern nichts; so entsteht weder ein
 * zweiter Cancel noch ein zweites DONE. Der Abbruch ist der Abbruch der Coroutine des Laufs: der Sequencer ruft
 * dann genau einmal `cancel()` des Backends und `close()` der Ausführung und liest kein Ereignis mehr.
 *
 * Der Lauf liegt in [detached], nicht in dem Bereich, der auf ihn wartet: ein Backend, das den Abbruch nicht beachtet,
 * hält nur sich selbst. Nach `CANCEL_TIMEOUT` kehrt [run] mit [RunEnd.Abandoned] zurück, die Schleife schließt die
 * Verbindung, und der Rest des Laufs endet, sobald das Backend zurückkehrt (Festlegung; das Backend ist nach dem
 * Port-Vertrag unterbrechbar, `ADR-0013`).
 *
 * Während des Laufs liest [tray] mit: eine Nachricht, die kein ATTENTION ist, bleibt für den nächsten Schritt der
 * Schleife im Eingang liegen (Festlegung; ein ATTENTION hinter einer vorausgeschickten Anfrage wird erst nach deren
 * Antwort gesehen). Meldet das Lesen das Ende der Gegenstelle oder einen Fehler, schließt die Sitzung, und der Lauf
 * endet mit [RunEnd.Disconnected]; das gilt auch, während der Abbruch läuft (`CANCELLING`), damit die Bestätigung nicht
 * auf eine tote Verbindung geschrieben wird. Das Beobachten endet erst, wenn der Lauf beendet ist und
 * der Abbruch (falls einer lief) abgeschlossen oder aufgegeben.
 *
 * Der dritte Auslöser des Abbruchs ist [abortForShutdown] (Ablauf von `SHUTDOWN_GRACE`, `SPEC-044` Nr. 4).
 */
internal class AbortableRun(
    private val live: HandshakeSession,
    private val tray: InboundTray,
    private val times: RequestTimes,
    private val detached: CoroutineScope,
) {
    private val claim = CompletableDeferred<CancelCause>()
    private val gone = CompletableDeferred<Unit>()

    /** Wie die Wartezeit auf den Lauf endete: ohne Auslöser, durch einen Abbruch oder das Ende der Gegenstelle. */
    private sealed interface Trigger {
        data object None : Trigger

        data class Cause(val cause: CancelCause) : Trigger

        data object Gone : Trigger
    }

    /** Meldet den Ablauf von `SHUTDOWN_GRACE`; wahr, wenn dieser Auslöser den Übergang nach `CANCELLING` gewann. */
    suspend fun abortForShutdown(): Boolean = fire(HandshakeEvent.ShutdownGraceExpired)

    /**
     * Führt [body] aus. [remaining] ist die Zeit bis zum Ablauf der Frist, `null` ohne Frist; höchstens null heißt
     * abgelaufen, dann beginnt [body] nicht.
     */
    suspend fun run(remaining: Duration?, body: suspend () -> SequenceOutcome): RunEnd {
        if (remaining != null && remaining <= Duration.ZERO) {
            fire(HandshakeEvent.DeadlineExpired)
            return RunEnd.Cut(CancelCause.DEADLINE, null)
        }
        val job = detached.async { body() }
        var returned = false
        try {
            return coroutineScope { supervise(job, remaining) }.also { returned = true }
        } finally {
            if (!returned) {
                job.cancel()
                withContext(NonCancellable) { if (!job.isCompleted) endsInTime(job) }
            }
        }
    }

    private suspend fun CoroutineScope.supervise(job: Deferred<SequenceOutcome>, remaining: Duration?): RunEnd {
        val watcher = launch { watch() }
        val deadline = launch { expire(remaining) }
        val first = select<Trigger> {
            job.onJoin { Trigger.None }
            claim.onAwait { Trigger.Cause(it) }
            gone.onAwait { Trigger.Gone }
        }
        if (first != Trigger.None) job.cancel()
        deadline.cancelAndJoin()
        val ended = first == Trigger.None || job.isCompleted || endsInTime(job)
        watcher.cancelAndJoin()
        val cause = (first as? Trigger.Cause)?.cause ?: if (claim.isCompleted) claim.await() else null
        return when {
            first == Trigger.Gone -> if (ended) RunEnd.Disconnected else RunEnd.Abandoned
            cause == null -> RunEnd.Finished(job.await())
            !ended -> RunEnd.Abandoned
            else -> RunEnd.Cut(cause, outcomeOf(job))
        }
    }

    /**
     * Die nächste Nachricht während des Laufs. Ein ATTENTION, das den Übergang gewinnt, ist verbraucht: der Abbruch
     * bestätigt es. Ein ATTENTION, das verliert (der Abbruch läuft schon, etwa durch die Frist), bleibt im Eingang und
     * wird nach dem Abbruch als „ATTENTION nach Ende der Antwort“ bestätigt (Setzung, nicht gemessen: ob ein Client
     * jedes ATTENTION einzeln bestätigt haben will, zeigt erst das Cancel-Szenario am Referenzclient). Es entsteht
     * nie ein zweiter Cancel. Jede andere Nachricht bleibt ebenfalls im Eingang und beendet das Beobachten. Nach einem
     * gewonnenen ATTENTION liest [watch] ein weiteres Mal: kommt das Ende der Gegenstelle oder ein Lesefehler, schließt
     * [hangUp] die Sitzung, bevor die Bestätigung geschrieben wird.
     */
    private suspend fun watch() {
        var seen = tray.peek()
        if (seen is Inbound.Message && seen.message.type == PacketType.ATTENTION) {
            val won = withContext(NonCancellable) {
                fire(HandshakeEvent.Message(seen.message)).also { if (it) tray.drop() }
            }
            if (!won) return
            seen = tray.peek()
        }
        if (seen !is Inbound.Message) hangUp(seen)
    }

    /** Das Ende der Gegenstelle oder der Lesefehler während des Laufs: die Sitzung schließt, der Lauf endet. */
    private suspend fun hangUp(seen: Inbound) {
        withContext(NonCancellable) {
            live.dispatch(if (seen == Inbound.EndOfStream) HandshakeEvent.PeerClosed else HandshakeEvent.ReadFailed)
            gone.complete(Unit)
        }
    }

    private suspend fun expire(remaining: Duration?) {
        if (remaining == null) return
        times.pause(remaining)
        withContext(NonCancellable) { fire(HandshakeEvent.DeadlineExpired) }
    }

    /** Meldet [event] dem Rahmen; wahr, wenn dieser Auslöser den Übergang nach `CANCELLING` gewonnen hat. */
    private suspend fun fire(event: HandshakeEvent): Boolean {
        val action = live.dispatch(event)
        if (action is HandshakeAction.Cancel) claim.complete(action.cause)
        return action is HandshakeAction.Cancel
    }

    private suspend fun endsInTime(job: Job): Boolean = coroutineScope {
        val timer = launch { times.pause(times.cancelTimeout) }
        val ended = select<Boolean> {
            job.onJoin { true }
            timer.onJoin { false }
        }
        timer.cancel()
        ended
    }

    /**
     * Der Ausgang eines beendeten Laufs; `null`, wenn der Abbruch ihn unterbrach. Jede andere Ausnahme läuft nach
     * außen.
     */
    private suspend fun outcomeOf(job: Deferred<SequenceOutcome>): SequenceOutcome? = try {
        job.await()
    } catch (ignored: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    }
}
