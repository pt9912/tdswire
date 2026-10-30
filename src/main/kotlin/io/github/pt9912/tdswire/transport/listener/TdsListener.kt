package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import io.github.pt9912.tdswire.transport.tls.ProbeResult
import io.github.pt9912.tdswire.transport.tls.TlsStartProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.BindException
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * TCP-Listener des Servers (`SPEC-005`, `ADR-0003`). [start] prüft zuerst die TLS-Konfiguration mit
 * der Startprobe (`SPEC-043`), bindet danach Adresse und Port und nimmt Verbindungen an. Jede Verbindung
 * bekommt einen eigenen Scope ([ManagedConnection]) und wird einem [ConnectionHandler] übergeben;
 * ein Absturz oder das Trennen einer Verbindung betrifft keine andere. `LOGIN_TIMEOUT` läuft ab der
 * Annahme, umfasst also einen TLS-Handschlag des Handlers, und endet mit [Connection.loginCompleted];
 * danach schließt der Transport ohne Antwort.
 *
 * Der Listener besitzt eine eigene `AsynchronousChannelGroup` mit [IO_THREADS] Threads (Festlegung, nicht
 * gemessen), benannt `tdswire-listener-io-<n>` ([THREAD_NAME_PREFIX]). Ein fehlgeschlagener Start und
 * [shutdown] beenden die Gruppe; danach läuft kein Thread des Listeners weiter.
 *
 * Handler müssen den Abbruch ihrer Coroutine beachten: [shutdown] kehrt erst zurück, wenn jeder Handler
 * beendet ist.
 */
class TdsListener private constructor(
    private val server: AsynchronousServerSocketChannel,
    private val io: IoGroup,
    setup: ListenerSetup,
) {
    private val config = setup.config
    private val handler = setup.handler
    private val events = setup.events
    private val registry = setup.registry
    private val root = SupervisorJob()
    private val scope = CoroutineScope(root + config.dispatcher)
    private val environment = ConnectionEnvironment(root, config.dispatcher, config.times, events, registry)
    private val ids = AtomicLong()

    /** Der gebundene Port; bei [ListenerConfig.port] = 0 der vom System gewählte. */
    val port: Int = (server.localAddress as InetSocketAddress).port

    /** Zahl der Verbindungen, deren Handler noch läuft. */
    val activeConnections: Int get() = registry.size

    private fun launchAcceptLoop() {
        scope.launch { acceptConnections() }
    }

    private suspend fun acceptConnections() {
        while (true) {
            when (val accepted = acceptOne()) {
                is Accept.Connected -> admit(accepted.channel)

                is Accept.Failed -> {
                    events.onEvent(ListenerEvent.AcceptFailed(accepted.failure))
                    config.times.delayer.delay(ACCEPT_RETRY)
                }

                Accept.Closed -> return
            }
        }
    }

    private suspend fun acceptOne(): Accept = suspendCancellableCoroutine { continuation ->
        server.accept(
            null,
            object : CompletionHandler<AsynchronousSocketChannel, Nothing?> {
                override fun completed(result: AsynchronousSocketChannel, attachment: Nothing?) =
                    continuation.resume(Accept.Connected(result))

                override fun failed(exc: Throwable, attachment: Nothing?) =
                    continuation.resume(if (server.isOpen) Accept.Failed(exc.javaClass.name) else Accept.Closed)
            },
        )
    }

    private fun admit(accepted: AsynchronousSocketChannel) {
        val connection = ManagedConnection(ids.incrementAndGet(), SocketChannelAdapter(accepted), environment)
        events.onEvent(ListenerEvent.Accepted(connection.id))
        if (registry.admit(connection.id, connection)) connection.start(handler) else connection.refuse()
    }

    /**
     * Beginnt das geordnete Beenden (`SPEC-044`) und kehrt zurück, wenn jede Verbindung beendet ist:
     * der Listener schließt zuerst, eine im selben Moment angenommene Verbindung endet sofort, eine
     * Verbindung ohne laufende Anfrage ([Connection.requestRunning]) endet sofort, die übrigen
     * höchstens bis `SHUTDOWN_GRACE`; danach werden sie getrennt. Abbruch laufender Anfragen am Backend
     * und `CANCEL_TIMEOUT` gehören der Session, nicht dem Listener. Ein zweiter Aufruf ist wirkungslos
     * und kehrt sofort zurück. Wird der Aufruf abgebrochen, räumt er trotzdem auf: die Verbindungen
     * werden getrennt, die Gruppe beendet und abgewartet (`TDS-QA-02`).
     */
    suspend fun shutdown() {
        val open = registry.beginShutdown() ?: return
        server.close()
        open.filterNot { it.busy }.forEach { it.end(ConnectionEnd.SHUTDOWN) }
        try {
            awaitConnections(open)
        } finally {
            root.cancel()
            io.stop()
        }
    }

    private suspend fun awaitConnections(open: List<ManagedConnection>) {
        val grace = config.times.shutdownGrace
        coroutineScope {
            val expiry = launch {
                if (grace > Duration.ZERO) config.times.delayer.delay(grace)
                open.forEach { it.end(ConnectionEnd.SHUTDOWN_GRACE_EXPIRED) }
            }
            open.forEach { it.awaitEnd() }
            expiry.cancel()
        }
    }

    companion object {
        /** Namensanfang der Threads des Listeners. */
        const val THREAD_NAME_PREFIX = "tdswire-listener-io-"
        const val IO_THREADS = 2
        private val TERMINATION_WAIT = 5.seconds
        private val TERMINATION_POLL = 1.milliseconds
        private val ACCEPT_RETRY = 100.milliseconds

        /**
         * Startet den Listener: Startprobe (falls [ListenerConfig.tls]), dann Bind und Annahme. Scheitert
         * ein Schritt, ist kein Port gebunden, kein Thread des Listeners läuft weiter, und der Ausgang
         * nennt den Teil bzw. Adresse und Port.
         */
        suspend fun start(config: ListenerConfig, handler: ConnectionHandler, events: ListenerEvents): StartResult =
            start(ListenerSetup(config, handler, events, ConnectionRegistry()))

        /** Wie [start] mit einem vorgegebenen Register; die Tests belegen damit die Verdrahtung der Abweisung. */
        internal suspend fun start(setup: ListenerSetup): StartResult {
            val config = setup.config
            val tls = config.tls
            val probe = if (tls == null) null else TlsStartProbe.run(tls.material, tls.parameters)
            return if (probe is ProbeResult.Failed) {
                StartResult.Failed(StartFailure.Tls(probe.diagnosis))
            } else {
                bind(setup)
            }
        }

        private suspend fun bind(setup: ListenerSetup): StartResult {
            val io = newGroup()
            return when (val bound = open(setup.config, io.group)) {
                is Bound.Failed -> {
                    io.stop()
                    StartResult.Failed(bound.failure)
                }

                is Bound.Server -> launched(bound.channel, io, setup)
            }
        }

        /** Gelingt der Aufbau des Listeners nicht, sind Kanal und Gruppe wieder frei. */
        private suspend fun launched(
            server: AsynchronousServerSocketChannel,
            io: IoGroup,
            setup: ListenerSetup,
        ): StartResult {
            var ready = false
            try {
                val listener = TdsListener(server, io, setup)
                listener.launchAcceptLoop()
                ready = true
                return StartResult.Started(listener)
            } finally {
                if (!ready) {
                    server.close()
                    io.stop()
                }
            }
        }

        private fun open(config: ListenerConfig, group: AsynchronousChannelGroup): Bound = try {
            Bound.Server(AsynchronousServerSocketChannel.open(group).bind(InetSocketAddress(config.host, config.port)))
        } catch (broken: IOException) {
            Bound.Failed(bindFailure(config, broken))
        } catch (broken: UnresolvedAddressException) {
            Bound.Failed(StartFailure.BindFailed(config.host, config.port, broken.javaClass.name))
        } catch (broken: IllegalArgumentException) {
            Bound.Failed(StartFailure.BindFailed(config.host, config.port, broken.javaClass.name))
        }

        private fun bindFailure(config: ListenerConfig, broken: IOException): StartFailure =
            if (broken is BindException) {
                StartFailure.PortInUse(config.host, config.port)
            } else {
                StartFailure.BindFailed(config.host, config.port, broken.javaClass.name)
            }

        private fun newGroup(): IoGroup {
            val counter = AtomicInteger()
            val threads = CopyOnWriteArrayList<Thread>()
            val group = AsynchronousChannelGroup.withFixedThreadPool(IO_THREADS) { task ->
                Thread(task, "$THREAD_NAME_PREFIX${counter.incrementAndGet()}").also {
                    it.isDaemon = true
                    threads += it
                }
            }
            return IoGroup(group, threads)
        }
    }

    /**
     * Die Gruppe samt ihren Threads. [stop] beendet die Gruppe und kehrt zurück, wenn kein Thread mehr
     * lebt, höchstens nach [TERMINATION_WAIT]; es läuft auch in einem abgebrochenen Aufruf zu Ende.
     */
    internal class IoGroup(val group: AsynchronousChannelGroup, private val threads: List<Thread>) {
        suspend fun stop() {
            group.shutdownNow()
            withContext(NonCancellable) {
                withTimeoutOrNull(TERMINATION_WAIT) {
                    while (!group.isTerminated || threads.any { it.isAlive }) delay(TERMINATION_POLL)
                }
            }
        }
    }

    private sealed interface Bound {
        class Server(val channel: AsynchronousServerSocketChannel) : Bound

        class Failed(val failure: StartFailure) : Bound
    }

    private sealed interface Accept {
        class Connected(val channel: AsynchronousSocketChannel) : Accept

        data class Failed(val failure: String) : Accept

        data object Closed : Accept
    }
}

/** Alles, was [TdsListener.start] aus der Aufrufstelle braucht; das Register ist austauschbar für Tests. */
internal class ListenerSetup(
    val config: ListenerConfig,
    val handler: ConnectionHandler,
    val events: ListenerEvents,
    val registry: ConnectionRegistry<ManagedConnection>,
)
