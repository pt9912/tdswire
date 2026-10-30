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
import kotlinx.coroutines.delay
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLContext
import kotlin.time.Duration

private const val POLL_MILLIS = 5L
private const val POLL_ROUNDS = 1000
private const val PACKET_SIZE = 4096
private const val READ_SIZE = 256

/** Wartet [condition] ab, höchstens fünf Sekunden; sonst schlägt der Test fehl. */
suspend fun eventually(what: String, condition: () -> Boolean) {
    repeat(POLL_ROUNDS) {
        if (condition()) return
        delay(POLL_MILLIS)
    }
    throw AssertionError("nicht eingetreten: $what")
}

/**
 * Steuerbare Zeit: jedes [delay] wartet, bis der Test [releaseAll] ruft. [pending] nennt die Fristen,
 * auf die der Listener gerade wartet.
 */
class ManualDelayer : Delayer {
    private class Waiting(val duration: Duration, val release: CompletableDeferred<Unit>)

    private val waiting = CopyOnWriteArrayList<Waiting>()

    val pending: List<Duration> get() = waiting.filter { it.release.isActive }.map { it.duration }

    override suspend fun delay(duration: Duration) {
        val entry = Waiting(duration, CompletableDeferred())
        waiting += entry
        try {
            entry.release.await()
        } finally {
            entry.release.cancel()
        }
    }

    fun releaseAll() = waiting.forEach { it.release.complete(Unit) }
}

/** Sammelt die Ereignisse des Listeners. */
class RecordingEvents : ListenerEvents {
    val events = CopyOnWriteArrayList<ListenerEvent>()

    override fun onEvent(event: ListenerEvent) {
        events += event
    }

    fun closed(): List<ListenerEvent.Closed> = events.filterIsInstance<ListenerEvent.Closed>()

    fun endsOf(end: ConnectionEnd): List<ListenerEvent.Closed> = closed().filter { it.end == end }
}

/** Startet einen Listener auf `127.0.0.1` mit einem freien Port; ein gescheiterter Start bricht den Test ab. */
suspend fun startOnLoopback(
    handler: ConnectionHandler,
    events: ListenerEvents,
    times: ListenerTimes = ListenerTimes(),
): TdsListener {
    val result = TdsListener.start(ListenerConfig(LOOPBACK, 0, times), handler, events)
    if (result !is StartResult.Started) throw AssertionError("Start gescheitert: $result")
    return result.listener
}

/** Die lebenden Threads des Listeners (Namensanfang [TdsListener.THREAD_NAME_PREFIX]). */
fun listenerThreads(): List<Thread> =
    Thread.getAllStackTraces().keys.filter { it.isAlive && it.name.startsWith(TdsListener.THREAD_NAME_PREFIX) }

/** Wartet, bis kein Thread des Listeners mehr lebt, der nicht schon in [before] war. */
suspend fun assertNoNewListenerThreads(before: List<Thread>) {
    eventually("keine neuen Threads des Listeners") { listenerThreads().none { it !in before } }
}

/** Verbindet blockierend mit dem Listener auf `127.0.0.1`; der Kanal gehört dem Aufrufer. */
fun connectTo(port: Int): SocketChannelAdapter {
    val channel = AsynchronousSocketChannel.open()
    channel.connect(InetSocketAddress(InetAddress.getByName(LOOPBACK), port)).get()
    return SocketChannelAdapter(channel)
}

const val LOOPBACK = "127.0.0.1"

/** Liest bis zum Ende des Stroms oder einem Fehler; `true`, wenn die Gegenstelle geschlossen hat. */
suspend fun SuspendingChannel.awaitClosedByPeer(): Boolean {
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
class TlsEchoHandler(private val context: SSLContext, private val path: TlsPath) : ConnectionHandler {
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
 * Verbindung endet. Mit [crashOnFirstByte] wirft er, wenn das erste empfangene Byte diesem Wert gleicht,
 * sonst spiegelt er die Bytes.
 */
class ScriptedHandler(private val crashOnFirstByte: Int? = null) : ConnectionHandler {
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
            check(!(first && crashOnFirstByte == buffer.get(0).toInt())) { "Absturz im Handler" }
            first = false
            buffer.flip()
            connection.channel.writeFully(buffer)
        }
    }
}
