package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/** Alles, was eine [ManagedConnection] vom Listener braucht. */
internal class ConnectionEnvironment(
    val parent: Job,
    val dispatcher: CoroutineDispatcher,
    val times: ListenerTimes,
    val events: ListenerEvents,
    val registry: ConnectionRegistry<ManagedConnection>,
)

/**
 * Eine angenommene Verbindung mit eigenem Scope (`ADR-0003`): der Scope hängt an einem
 * `SupervisorJob` unter dem Listener, sodass Handler-Absturz und Trennen nur diese Verbindung
 * betreffen. Der Scope trägt den Handler und die Frist `LOGIN_TIMEOUT`. [end] schließt Kanal und Scope;
 * die erste Ursache zählt. Das Ereignis [ListenerEvent.Closed] kommt genau einmal, wenn der Handler endet.
 *
 * Einordnung einer `CancellationException` aus dem Handler (Setzung dieses Slice, nicht gemessen): ist
 * der Scope der Verbindung abgebrochen (durch [end] oder durch das Beenden des Listeners), ist es das
 * Ende durch diesen Abbruch (`SHUTDOWN`, bei [end] die dort genannte Ursache); wirft der Handler sie bei
 * unabgebrochenem Scope, ist es ein Handlerfehler (`HANDLER_FAILED`).
 */
internal class ManagedConnection(
    override val id: Long,
    override val channel: SuspendingChannel,
    private val environment: ConnectionEnvironment,
) : Connection {
    private val scope = CoroutineScope(
        SupervisorJob(environment.parent) + environment.dispatcher + CoroutineExceptionHandler { _, _ -> },
    )
    private val cause = AtomicReference<ConnectionEnd?>(null)
    private val finished = CompletableDeferred<Unit>()
    private val reported = AtomicBoolean(false)
    private lateinit var loginTimer: Job

    @Volatile
    private var loginDone = false

    override val remoteAddress: InetAddress? get() = channel.remoteAddress

    @Volatile
    private var running = false

    @Volatile
    private var partial = false

    /** Wahr, wenn eine Anfrage läuft oder eine Nachricht unvollständig empfangen ist (`SPEC-044` Nr. 2). */
    val busy: Boolean get() = running || partial

    @Volatile
    private var abort: (suspend () -> Unit)? = null

    override val shutdownBegun: Boolean get() = environment.registry.shuttingDown

    /** Startet Frist und Handler; der Aufrufer hat die Verbindung vorher im Register aufgenommen. */
    fun start(handler: ConnectionHandler) {
        loginTimer = scope.launch {
            environment.times.delayer.delay(environment.times.loginTimeout)
            if (!loginDone) end(ConnectionEnd.LOGIN_TIMEOUT)
        }
        val handlerJob = scope.launch { handler.handle(this@ManagedConnection) }
        handlerJob.invokeOnCompletion { failure -> finish(failure) }
    }

    /** Schließt eine Verbindung, die nie gestartet wurde; [reason] nennt, warum sie abgewiesen wurde. */
    fun refuse(reason: ConnectionEnd = ConnectionEnd.REFUSED_AT_SHUTDOWN) {
        channel.close()
        environment.events.onEvent(ListenerEvent.Closed(id, reason, null))
    }

    override fun loginCompleted() {
        loginDone = true
        loginTimer.cancel()
    }

    override fun requestRunning(running: Boolean) {
        this.running = running
        endIfIdleInShutdown()
    }

    override fun messageInProgress(inProgress: Boolean) {
        partial = inProgress
        endIfIdleInShutdown()
    }

    private fun endIfIdleInShutdown() {
        if (!busy && environment.registry.shuttingDown) end(ConnectionEnd.SHUTDOWN)
    }

    override fun onShutdownGraceExpired(abort: suspend () -> Unit) {
        this.abort = abort
    }

    /**
     * Der Ablauf von `SHUTDOWN_GRACE` für diese Verbindung (`SPEC-044` Nr. 4). Hat der Handler einen Abbruch
     * angemeldet und läuft eine Anfrage, läuft er im Scope der Verbindung, und die Meldung ist `true`: der Aufrufer
     * wartet `CANCEL_TIMEOUT` ab und gibt die Verbindung dann mit [abandon] auf. Sonst (keine Anfrage, etwa eine unvollständige Nachricht)
     * trennt die Verbindung sofort (`SHUTDOWN_GRACE_EXPIRED`), die Meldung ist `false`.
     */
    fun expireGrace(): Boolean {
        val action = abort
        if (action == null || !running || finished.isCompleted) {
            end(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED)
            return false
        }
        scope.launch { action() }
        return true
    }

    /** Beendet die Verbindung aus [reason]: schließt den Kanal und bricht den Scope ab; wirkungslos nach dem Ende. */
    fun end(reason: ConnectionEnd) {
        if (finished.isCompleted || !cause.compareAndSet(null, reason)) return
        channel.close()
        scope.cancel()
    }

    /** Kehrt zurück, wenn der Handler beendet und das Ereignis gemeldet ist. */
    suspend fun awaitEnd() = finished.await()

    /**
     * Gibt die Verbindung auf, ohne auf das Ende des Handlers zu warten: trennt sie mit [reason], gibt sie im Register
     * frei und meldet das Ende. Beachtet der Handler den Abbruch seiner Coroutine nicht, läuft er weiter; die Meldung
     * kommt trotzdem genau einmal. Wirkungslos nach dem Ende.
     */
    fun abandon(reason: ConnectionEnd) {
        end(reason)
        report(cause.get() ?: reason, null)
    }

    private fun finish(failure: Throwable?) {
        val end = cause.get() ?: when {
            failure == null -> ConnectionEnd.HANDLER_RETURNED
            failure is CancellationException && scope.coroutineContext.job.isCancelled -> ConnectionEnd.SHUTDOWN
            else -> ConnectionEnd.HANDLER_FAILED
        }
        report(end, if (end == ConnectionEnd.HANDLER_FAILED) failure?.javaClass?.name else null)
    }

    /** Das Ende der Verbindung, genau einmal: Kanal und Scope schließen, Register freigeben, Ereignis melden. */
    private fun report(end: ConnectionEnd, failure: String?) {
        if (!reported.compareAndSet(false, true)) return
        channel.close()
        scope.cancel()
        environment.registry.release(id)
        environment.events.onEvent(ListenerEvent.Closed(id, end, failure))
        finished.complete(Unit)
    }
}
