package io.github.pt9912.tdswire.testserver

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadFeature
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private const val LOOPBACK = "127.0.0.1"
private const val WAIT_MILLIS = 20_000L
private const val POLL_MILLIS = 10L

/** Die Beschreibung einer Tabelle `person`: ein Start damit erzeugt das Ereignis des Simulations-Backends. */
internal val PERSON_YAML: String = """
    schema_format: "1.0"
    name: demo
    version: "1"
    tables:
      person:
        columns:
          id:
            type: identifier
        primary_key: [id]
""".trimIndent() + "\n"

private val STRICT_JSON: JsonFactory =
    JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

/**
 * Liest eine Zeile als flaches JSON-Objekt (Zeichenketten und ganze Zahlen); jede Abweichung von JSON, ein doppelter
 * Schlüssel und jedes Zeichen hinter dem Objekt brechen den Test. Der Leser ist der von `jackson-core`, nicht der
 * Schreiber des Adapters.
 */
internal fun parseFlat(line: String): Map<String, Any> {
    val result = LinkedHashMap<String, Any>()
    STRICT_JSON.createParser(line).use { parser ->
        check(parser.nextToken() == JsonToken.START_OBJECT) { "kein Objekt: $line" }
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            val name = parser.currentName()
            result[name] = when (parser.nextToken()) {
                JsonToken.VALUE_STRING -> parser.text
                JsonToken.VALUE_NUMBER_INT -> parser.longValue
                else -> error("Wert des Feldes $name ist weder Zeichenkette noch ganze Zahl: $line")
            }
        }
        check(parser.currentToken() == JsonToken.END_OBJECT) { "Objekt nicht beendet: $line" }
        check(parser.nextToken() == null) { "Zeichen hinter dem Objekt: $line" }
    }
    return result
}

/**
 * Führt [action] in einem eigenen Thread aus und bricht den Test, wenn sie nach [millis] Millisekunden nicht
 * zurückgekehrt ist: so hält eine blockierende Senke den Test an, ohne ihn hängen zu lassen.
 */
internal fun <T> within(millis: Long, what: String, action: () -> T): T {
    val outcome = AtomicReference<Result<T>>()
    val worker = thread(isDaemon = true, name = "tdswire-test-within") { outcome.set(runCatching(action)) }
    worker.join(millis)
    check(!worker.isAlive) { "$what: nicht nach $millis ms zurückgekehrt" }
    return outcome.get().getOrThrow()
}

/** Sammelt die Ereignisse, die die Bibliothek an die Senke gibt. */
internal class EventRecorder : TelemetrySink {
    val events = CopyOnWriteArrayList<TelemetryEvent>()

    override fun accept(event: TelemetryEvent) {
        events += event
    }

    fun ofKind(kind: EventKind): List<TelemetryEvent> = events.filter { it.kind == kind }

    /** Wartet bis zu 20 s darauf, dass ein Ereignis der Art [kind] eingetroffen ist. */
    fun await(kind: EventKind): TelemetryEvent {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS)
        while (ofKind(kind).isEmpty() && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)
        return checkNotNull(ofKind(kind).firstOrNull()) { "kein Ereignis $kind: ${events.map { it.kind }}" }
    }
}

/** Ein Kanal, der alle Zeilen mit dem Namen des schreibenden Threads sammelt. */
internal class RecordingChannel : LineChannel {
    val lines = CopyOnWriteArrayList<String>()
    val writers = CopyOnWriteArrayList<String>()

    override fun write(line: String) {
        lines += line
        writers += Thread.currentThread().name
    }

    /** Wartet bis zu 20 s auf mindestens [count] Zeilen. */
    fun awaitLines(count: Int): List<String> {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS)
        while (lines.size < count && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)
        return lines.toList()
    }
}

/**
 * Ein Kanal, der blockiert, bis [release] ruft: jede Zeile, die der Filter [blocks] trifft, hält den Schreiber an. So
 * sieht der Test einen Leser, der stockt, ohne einen Prozess.
 */
internal class GatedChannel(private val blocks: (String) -> Boolean = { true }) : LineChannel {
    val lines = CopyOnWriteArrayList<String>()
    val entered = CountDownLatch(1)
    private val gate = CountDownLatch(1)

    override fun write(line: String) {
        if (blocks(line)) {
            entered.countDown()
            gate.await()
        }
        lines += line
    }

    fun release() = gate.countDown()
}

/** Ereignisse, wie sie ein laufender Testserver im Prozess der Tests erzeugt. */
internal object RealEvents {
    /**
     * Startet den Testserver im Prozess mit [environment], lässt [action] mit dem Port laufen und beendet ihn; die
     * Ereignisse liegen im Rückgabewert, das Ende des Servers eingeschlossen. Der Server meldet ein Ereignis nach dem
     * Senden der Antwort; wer es braucht, nennt die Arten in [settle], auf die der Aufruf vor dem Beenden wartet (sonst
     * beendet der Wettlauf mit dem Schließen des Clients das Ereignis).
     */
    fun collect(
        environment: Map<String, String>,
        settle: List<EventKind> = emptyList(),
        action: (Int) -> Unit,
    ): EventRecorder {
        val recorder = EventRecorder()
        val outcome = runBlocking {
            startTestserver(environment, { BackendSource.Simulation(it) }, {}, recorder)
        }
        val running = outcome as? StartOutcome.Running ?: error("Start abgebrochen: $outcome")
        try {
            action(running.server.port)
            settle.forEach { recorder.await(it) }
        } finally {
            runBlocking { running.server.stop() }
        }
        return recorder
    }

    /** Die Ereignisse eines Starts auf einem belegten Port. */
    fun busyPort(environment: Map<String, String>): EventRecorder {
        val recorder = EventRecorder()
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            val busy = environment + (EnvKeys.LISTEN_PORT to occupied.localPort.toString())
            runBlocking { startTestserver(busy, { BackendSource.Simulation(it) }, {}, recorder) }
        }
        return recorder
    }
}
