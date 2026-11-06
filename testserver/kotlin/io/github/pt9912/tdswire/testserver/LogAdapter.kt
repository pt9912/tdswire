package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.telemetry.EventLevel
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Zeilen der Warteschlange (Setzung der `ADR-0020`, Frage 7, nicht gemessen). */
internal const val QUEUE_CAPACITY = 1024

/** Frist, in der [LogAdapter.close] die Warteschlange leert (Setzung der `ADR-0020`, Frage 7). */
internal val CLOSE_GRACE: Duration = Duration.ofSeconds(2)

/** Frist je Zeile, die [LogAdapter.writeLast] und das Schließen noch schreiben (Setzung, nicht gemessen). */
internal val LAST_LINE_GRACE: Duration = Duration.ofMillis(250)

private const val POLL_MILLIS = 50L

/** Das Ziel der Zeilen: die Standardausgabe im Betrieb. Eine Zeile steht ganz oder gar nicht; sie wird nie zerteilt. */
internal fun interface LineChannel {
    fun write(line: String)
}

/** Die Standardausgabe des Prozesses. Ein Schreiben kann blockieren, wenn der Leser stockt (`ADR-0020`, Messung i). */
internal object StandardOutput : LineChannel {
    override fun write(line: String) = System.out.println(line)
}

private enum class Phase { BEFORE_START, RUNNING, CLOSING, CLOSED }

/**
 * Die Senke des Testservers (`ADR-0020`, Fragen 1 und 7; `SPEC-052`): schreibt jedes Ereignis, dessen Ebene die
 * gewählte nicht unterschreitet ([LogLevelSetting.passes]), als eine JSON-Zeile ([TelemetryJson]) in [channel].
 *
 * Bis [startWriter] schreibt [accept] synchron im Thread des Aufrufers und ohne Warteschlange: Ereignisse vor dem
 * erfolgreichen Start (Konfiguration, Binden, Startprobe) und der Start selbst. Mit [startWriter] legt [accept] das
 * Ereignis in eine Warteschlange von [capacity] Ereignissen, die ein Schreiber-Thread (Daemon) in den Kanal leert;
 * [accept] blockiert nicht. Ist die Warteschlange voll, wird das neue Ereignis verworfen und gezählt, die Zahl steht
 * im Feld `dropped` der nächsten geschriebenen Zeile. [close] leert die Warteschlange mit einer Frist, ein Rest wird
 * mit seiner Zahl in einer letzten Zeile (`log_dropped`) gemeldet, soweit der Kanal annimmt; nach [close] verwirft
 * [accept] jedes Ereignis. Gemessen ist die Wirkung unter Last nicht; die Tests belegen das Verhalten.
 *
 * Die Zeilen von [writeDirect] und [writeLast] gehen ohne Warteschlange in denselben Kanal; ihre Reihenfolge zu den
 * Zeilen der Warteschlange ist nicht festgelegt. Ein hängender Kanal hält [writeDirect] an; [close] und
 * [writeLast] kehren nach ihrer Frist zurück.
 */
internal class LogAdapter(
    private val chosen: EventLevel,
    private val channel: LineChannel,
    capacity: Int = QUEUE_CAPACITY,
) : TelemetrySink {
    private val queue = ArrayBlockingQueue<TelemetryEvent>(capacity)
    private val dropped = AtomicLong()
    private val phase = AtomicReference(Phase.BEFORE_START)

    @Volatile
    private var writer: Thread? = null

    override fun accept(event: TelemetryEvent) {
        if (!LogLevelSetting.passes(event.level, chosen)) return
        when (phase.get()) {
            Phase.BEFORE_START -> deliver(event)
            Phase.RUNNING, Phase.CLOSING -> if (!queue.offer(event)) dropped.incrementAndGet()
            Phase.CLOSED -> dropped.incrementAndGet()
        }
    }

    /** Startet den Schreiber; ab hier geht [accept] über die Warteschlange. Ein zweiter Aufruf bleibt ohne Wirkung. */
    fun startWriter() {
        if (phase.compareAndSet(Phase.BEFORE_START, Phase.RUNNING)) {
            writer = thread(isDaemon = true, name = "tdswire-log-writer") { drain() }
        }
    }

    /** Schreibt [line] sofort in den Kanal, im Thread des Aufrufers, nie über die Warteschlange. */
    fun writeDirect(line: String) = channel.write(line)

    /**
     * Wartet höchstens [grace] darauf, dass der Schreiber die Warteschlange leert, und meldet, was verloren ist, in
     * einer Zeile `log_dropped` mit der Zahl in `dropped` (bis zu [LAST_LINE_GRACE] Wartezeit). Ein zweiter Aufruf
     * schreibt nur noch, was seither verworfen wurde.
     */
    fun close(grace: Duration = CLOSE_GRACE) {
        if (phase.compareAndSet(Phase.RUNNING, Phase.CLOSING)) writer?.join(grace.toMillis())
        phase.set(Phase.CLOSED)
        val left = ArrayList<TelemetryEvent>()
        queue.drainTo(left)
        val lost = left.size + dropped.getAndSet(0)
        if (lost > 0) writeLast(TelemetryJson.droppedLine(Instant.now(), lost))
    }

    /**
     * Schreibt [line] in einem eigenen Thread und wartet höchstens [grace]; hängt der Kanal, kehrt der Aufruf nach der
     * Frist zurück und die Zeile geht verloren. So endet der Prozess auch bei einem Kanal, der nicht annimmt.
     */
    fun writeLast(line: String, grace: Duration = LAST_LINE_GRACE) {
        val last = thread(isDaemon = true, name = "tdswire-log-last") {
            try {
                channel.write(line)
            } catch (ignored: IOException) {
                dropped.incrementAndGet()
            }
        }
        last.join(grace.toMillis())
    }

    private fun drain() {
        var running = true
        while (running) {
            val event = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS)
            if (event != null) {
                deliver(event)
            } else {
                running = phase.get() == Phase.RUNNING
            }
        }
    }

    private fun deliver(event: TelemetryEvent) {
        val pending = dropped.getAndSet(0)
        try {
            channel.write(TelemetryJson.line(event, pending))
        } catch (ignored: IOException) {
            dropped.addAndGet(pending + 1)
        }
    }
}
