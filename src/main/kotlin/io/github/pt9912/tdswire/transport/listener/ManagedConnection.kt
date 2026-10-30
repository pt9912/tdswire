package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    private lateinit var loginTimer: Job

    @Volatile
    private var loginDone = false

    @Volatile
    var busy = false
        private set

    /** Startet Frist und Handler; der Aufrufer hat die Verbindung vorher im Register aufgenommen. */
    fun start(handler: ConnectionHandler) {
        loginTimer = scope.launch {
            environment.times.delayer.delay(environment.times.loginTimeout)
            if (!loginDone) end(ConnectionEnd.LOGIN_TIMEOUT)
        }
        val handlerJob = scope.launch { handler.handle(this@ManagedConnection) }
        handlerJob.invokeOnCompletion { failure -> finish(failure) }
    }

    /** Schließt eine Verbindung, die nie gestartet wurde. */
    fun refuse() {
        channel.close()
        environment.events.onEvent(ListenerEvent.Closed(id, ConnectionEnd.REFUSED_AT_SHUTDOWN, null))
    }

    override fun loginCompleted() {
        loginDone = true
        loginTimer.cancel()
    }

    override fun requestRunning(running: Boolean) {
        busy = running
        if (!running && environment.registry.shuttingDown) end(ConnectionEnd.SHUTDOWN)
    }

    /** Beendet die Verbindung aus [reason]: schließt den Kanal und bricht den Scope ab; wirkungslos nach dem Ende. */
    fun end(reason: ConnectionEnd) {
        if (finished.isCompleted || !cause.compareAndSet(null, reason)) return
        channel.close()
        scope.cancel()
    }

    /** Kehrt zurück, wenn der Handler beendet und das Ereignis gemeldet ist. */
    suspend fun awaitEnd() = finished.await()

    private fun finish(failure: Throwable?) {
        val end = cause.get() ?: when {
            failure == null -> ConnectionEnd.HANDLER_RETURNED
            failure is CancellationException -> ConnectionEnd.SHUTDOWN
            else -> ConnectionEnd.HANDLER_FAILED
        }
        channel.close()
        scope.cancel()
        environment.registry.release(id)
        val failed = if (end == ConnectionEnd.HANDLER_FAILED) failure?.javaClass?.name else null
        environment.events.onEvent(ListenerEvent.Closed(id, end, failed))
        finished.complete(Unit)
    }
}
