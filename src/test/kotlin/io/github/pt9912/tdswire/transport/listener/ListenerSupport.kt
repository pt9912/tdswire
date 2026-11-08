package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.RecordingChannel
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SocketChannelAdapter
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.tls.TlsChannel
import io.github.pt9912.tdswire.transport.tls.TlsPath
import io.github.pt9912.tdswire.transport.tls.TlsResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLContext
import kotlin.test.AfterTest
import kotlin.time.Duration

private const val POLL_MILLIS = 5L
private const val POLL_ROUNDS = 1000
private const val NANOS_PER_MILLI = 1_000_000L
private const val PACKET_SIZE = 4096
private const val READ_SIZE = 256

/** Wartet [condition] ab, höchstens fünf Sekunden; sonst schlägt der Test fehl. */
internal suspend fun eventually(what: String, condition: () -> Boolean) {
    repeat(POLL_ROUNDS) {
        if (condition()) return
        delay(POLL_MILLIS)
    }
    throw AssertionError("nicht eingetreten: $what")
}

/**
 * Wartet [condition] ab; sonst schlägt der Test nach der Schutzgrenze [hangGuardMillis] in Wanduhr fehl. Die Grenze
 * schützt vor einem hängenden Test und sagt nichts über die Dauer des Wartens; sie zählt die Uhr, nicht die Abfragen
 * (eine Abfrage dauert unter Last länger als [POLL_MILLIS]). Anders als [eventually], dessen Grenze von rund fünf
 * Sekunden Tests als Beleg für eine kurze Frist nutzen.
 */
internal suspend fun eventuallyGuarded(what: String, hangGuardMillis: Long, condition: () -> Boolean) {
    val deadline = System.nanoTime() + hangGuardMillis * NANOS_PER_MILLI
    while (!condition()) {
        if (System.nanoTime() > deadline) throw AssertionError("nicht eingetreten: $what")
        delay(POLL_MILLIS)
    }
}

/**
 * Steuerbare Zeit: jedes [delay] wartet, bis der Test [releaseAll] ruft. [pending] nennt die Fristen,
 * auf die der Listener gerade wartet.
 */
internal class ManualDelayer : Delayer {
    private class Waiting(val duration: Duration, val release: CompletableDeferred<Unit>)

    private val waiting = CopyOnWriteArrayList<Waiting>()

    private val changes = MutableStateFlow(0)

    val pending: List<Duration> get() = waiting.filter { it.release.isActive }.map { it.duration }

    /** Kehrt zurück, sobald [pending] genau [duration] nennt; wartet auf die Änderung, nicht auf die Uhr. */
    suspend fun awaitPending(duration: Duration) {
        changes.first { pending == listOf(duration) }
    }

    override suspend fun delay(duration: Duration) {
        val entry = Waiting(duration, CompletableDeferred())
        waiting += entry
        changes.update { it + 1 }
        try {
            entry.release.await()
        } finally {
            entry.release.cancel()
            changes.update { it + 1 }
        }
    }

    fun releaseAll() = waiting.forEach { it.release.complete(Unit) }
}

/** Sammelt die Ereignisse des Listeners. */
internal class RecordingEvents : ListenerEvents {
    val events = CopyOnWriteArrayList<ListenerEvent>()

    override fun onEvent(event: ListenerEvent) {
        events += event
    }

    fun closed(): List<ListenerEvent.Closed> = events.filterIsInstance<ListenerEvent.Closed>()

    fun endsOf(end: ConnectionEnd): List<ListenerEvent.Closed> = closed().filter { it.end == end }
}

private const val CLEANUP_MILLIS = 3_000L
private val started = ConcurrentLinkedQueue<TdsListener>()

/**
 * Basis der Listener-Tests: nach jedem Test, auch nach einem Fehlschlag, werden die Listener beendet,
 * die der Test über [startOnLoopback] oder [track] gestartet hat. Ein hängendes Beenden bricht nach
 * einer Frist ab und räumt dabei selbst auf. Die Tests laufen nacheinander (die Testkonfiguration
 * setzt keine Parallelität); `listenerThreads` und [assertNoNewListenerThreads] setzen das voraus.
 *
 * Belegt ist, dass das Aufräumen die gestarteten Listener beendet (`ListenerTestBaseTest`). Nicht
 * gemessen ist, dass ein fehlschlagender Test ohne es Folgefehler auslöste: in einem Lauf mit
 * eingeschleustem Fehlschlag blieben sie mit und ohne Aufräumen aus.
 */
internal open class ListenerTestBase {
    @AfterTest
    fun shutDownStartedListeners() {
        runBlocking {
            while (true) {
                val listener = started.poll() ?: break
                withContext(NonCancellable) { withTimeoutOrNull(CLEANUP_MILLIS) { listener.shutdown() } }
            }
        }
    }
}

/** Merkt sich [result] für das Aufräumen nach dem Test und gibt es zurück. */
internal fun track(result: StartResult): StartResult = result.also {
    if (it is StartResult.Started) started += it.listener
}

/** Startet einen Listener auf `127.0.0.1` mit einem freien Port; ein gescheiterter Start bricht den Test ab. */
internal suspend fun startOnLoopback(
    handler: ConnectionHandler,
    events: ListenerEvents,
    times: ListenerTimes = ListenerTimes(),
): TdsListener {
    val result = track(TdsListener.start(ListenerConfig(LOOPBACK, 0, times), handler, events))
    if (result !is StartResult.Started) throw AssertionError("Start gescheitert: $result")
    return result.listener
}

/** Die lebenden Threads des Listeners (Namensanfang [TdsListener.THREAD_NAME_PREFIX]). */
internal fun listenerThreads(): List<Thread> =
    Thread.getAllStackTraces().keys.filter { it.isAlive && it.name.startsWith(TdsListener.THREAD_NAME_PREFIX) }

/** Prüft sofort, ohne zu warten: kein Thread des Listeners lebt, der nicht schon in [before] war. */
internal fun assertNoNewListenerThreads(before: List<Thread>) {
    val extra = listenerThreads().filter { it !in before }
    if (extra.isNotEmpty()) throw AssertionError("Threads des Listeners leben: ${extra.map { it.name }}")
}

/** Verbindet blockierend mit dem Listener auf `127.0.0.1`; der Kanal gehört dem Aufrufer. */
internal fun connectTo(port: Int): SocketChannelAdapter {
    val channel = AsynchronousSocketChannel.open()
    channel.connect(InetSocketAddress(InetAddress.getByName(LOOPBACK), port)).get()
    return SocketChannelAdapter(channel)
}

internal const val LOOPBACK = "127.0.0.1"

/** Liest bis zum Ende des Stroms oder einem Fehler; `true`, wenn die Gegenstelle geschlossen hat. */
internal suspend fun SuspendingChannel.awaitClosedByPeer(): Boolean {
    val buffer = ByteBuffer.allocate(READ_SIZE)
    while (true) {
        buffer.clear()
        when (val read = read(buffer)) {
            is ChannelResult.Failed -> return true
            is ChannelResult.Ok -> if (read.value < 0) return true
        }
    }
}

/**
 * Handler des Smoke: schaltet TLS im vorgegebenen Pfad ein, meldet danach „Login abgeschlossen“ und
 * spiegelt jede Nachricht zurück (kein Protokoll). [wires] hält die aufgezeichneten Drähte.
 */
internal class TlsEchoHandler(private val context: SSLContext, private val path: TlsPath) : ConnectionHandler {
    val wires = ConcurrentLinkedQueue<RecordingChannel>()

    override suspend fun handle(connection: Connection) {
        val wire = RecordingChannel(connection.channel).also { wires += it }
        when (val accepted = TlsChannel.accept(wire, context, path)) {
            is TlsResult.Failed -> Unit

            is TlsResult.Ok -> {
                connection.loginCompleted()
                echo(accepted.value)
                accepted.value.close()
            }
        }
    }

    private suspend fun echo(channel: SuspendingChannel) {
        val reader = MessageReader(channel)
        val writer = MessageWriter(channel, PACKET_SIZE)
        while (true) {
            val read = reader.next()
            if (read !is ReadOutcome.Message) return
            writer.write(read.message)
        }
    }
}

/**
 * Handler ohne TLS: merkt sich jede [Connection], meldet „Login abgeschlossen“ und liest, bis die
 * Verbindung endet. Mit [crashOnFirstByte] wirft er [crash], wenn das erste empfangene Byte diesem Wert
 * gleicht, sonst spiegelt er die Bytes.
 */
internal class ScriptedHandler(
    private val crashOnFirstByte: Int? = null,
    private val crash: () -> Throwable = { IllegalStateException("Absturz im Handler") },
) : ConnectionHandler {
    val connections = CopyOnWriteArrayList<Connection>()

    override suspend fun handle(connection: Connection) {
        connections += connection
        connection.loginCompleted()
        val buffer = ByteBuffer.allocate(READ_SIZE)
        var first = true
        while (true) {
            buffer.clear()
            val read = connection.channel.read(buffer)
            if (read !is ChannelResult.Ok || read.value < 0) return
            if (first && crashOnFirstByte == buffer.get(0).toInt()) throw crash()
            first = false
            buffer.flip()
            connection.channel.writeFully(buffer)
        }
    }
}
