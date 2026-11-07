package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.EventLevel
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val LOOPBACK = "127.0.0.1"
private const val SMALL_QUEUE = 4
private const val OVERFLOW = 10
private const val BOUND_MILLIS = 5_000L
private const val SHORT_GRACE_MILLIS = 300L

/**
 * Der Adapter ([LogAdapter]) mit echten Ereignissen: ein Start im Prozess liefert sie ([RealEvents]), der Test gibt
 * sie in beliebiger Zahl an einen Adapter mit einem Kanal, den er anhält oder freigibt. Gemessen ist Verhalten, keine
 * Durchsatzzahl.
 */
internal class LogAdapterTest : TempFilesTest() {
    private val gates = mutableListOf<GatedChannel>()
    private val adapters = mutableListOf<LogAdapter>()

    @AfterTest
    fun releaseAll() {
        gates.forEach { it.release() }
        adapters.forEach { it.close(Duration.ofMillis(SHORT_GRACE_MILLIS)) }
    }

    private fun gated(blocks: (String) -> Boolean = { true }) = GatedChannel(blocks).also { gates += it }

    private fun adapter(level: EventLevel, channel: LineChannel, capacity: Int = QUEUE_CAPACITY) =
        LogAdapter(level, channel, capacity).also { adapters += it }

    /**
     * Ein Lauf mit Anmeldung angenommen und abgelehnt und einer Tabelle (Ebenen `INFO`, `WARN`, `DEBUG`) und ein
     * Start auf einem belegten Port (Ebene `ERROR`).
     */
    private val run: List<TelemetryEvent> by lazy {
        val environment = testModeEnvironment(
            file("users.txt", "alice\t{plain}secret\n"),
            EnvKeys.LISTEN_HOST to LOOPBACK,
            EnvKeys.LISTEN_PORT to "0",
            EnvKeys.SIMULATION_DESCRIPTION_FILE to file("sim.yaml", PERSON_YAML),
        )
        val served = RealEvents.collect(environment) { port ->
            LoginProbe(port).use { assertTrue(it.login("alice", "secret")) }
            LoginProbe(port).use { assertFalse(it.login("alice", "falsch")) }
        }.also {
            it.await(EventKind.LOGIN_ACCEPTED)
            it.await(EventKind.LOGIN_REJECTED)
        }
        served.events.toList() + RealEvents.busyPort(environment).events.toList()
    }

    private fun first(kind: EventKind): TelemetryEvent = run.first { it.kind == kind }

    private fun names(lines: List<String>) = lines.map { parseFlat(it)["event"] }

    /**
     * Bei `LOG_LEVEL` `ERROR` kommt nur die Ebene `ERROR` durch, bei `WARN` auch `WARN`, bei `INFO` auch `INFO`, bei
     * `DEBUG` jede; der Lauf enthält alle vier Ebenen, auch `DEBUG` (`SIMULATION_BACKEND_READY`).
     *
     * @spec SPEC-052
     */
    @Test
    fun `Die Ebene filtert nach der Ebene des Ereignisses`() {
        assertEquals(EventLevel.entries.toSet(), run.map { it.level }.toSet())
        for (chosen in EventLevel.entries) {
            val levels = EventLevel.entries.filter { it.ordinal <= chosen.ordinal }.toSet()
            val channel = RecordingChannel()
            val adapter = adapter(chosen, channel)
            run.forEach(adapter::accept)
            val written = channel.lines.map { parseFlat(it)["level"] }.toSet()
            assertEquals(levels.map { it.name }.toSet(), written, "gewählt $chosen")
            assertEquals(run.count { it.level in levels }, channel.lines.size, "gewählt $chosen")
        }
    }

    /**
     * Vor `startWriter` schreibt der Adapter im Thread des Aufrufers und ohne Warteschlange: die Zeile steht, wenn
     * `accept` zurückkehrt, auch bei der Größe null der Warteschlange. Danach schreibt der Schreiber-Thread.
     *
     * @spec SPEC-052
     * @spec SPEC-043
     */
    @Test
    fun `Vor dem Start schreibt der Adapter synchron und danach der Schreiber`() {
        val channel = RecordingChannel()
        val adapter = adapter(EventLevel.DEBUG, channel, capacity = 1)
        val event = first(EventKind.SERVER_STARTED)
        repeat(3) { adapter.accept(event) }
        assertEquals(3, channel.lines.size)
        assertTrue(channel.writers.all { it.startsWith(TEST_THREAD) }, channel.writers.toString())

        adapter.startWriter()
        adapter.accept(event)
        assertEquals(4, channel.awaitLines(4).size)
        assertEquals("tdswire-log-writer", channel.writers.last())
    }

    /**
     * Ein Schreiber, der steht, hält den Aufrufer nicht an: bei einer Warteschlange von vier Zeilen und einem Kanal,
     * der die erste Zeile festhält, kehren 14 Aufrufe von `accept` ohne Wartezeit zurück. Vier Ereignisse stehen in
     * der Warteschlange, zehn sind verworfen; nach der Freigabe trägt die nächste geschriebene Zeile `dropped` 10, die
     * anderen tragen keines.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Ein Ueberlauf blockiert den Aufrufer nicht und die naechste Zeile zaehlt die verworfenen`() {
        val channel = gated()
        val adapter = adapter(EventLevel.DEBUG, channel, SMALL_QUEUE)
        val event = first(EventKind.SERVER_STARTED)
        adapter.startWriter()
        adapter.accept(event)
        assertTrue(channel.entered.await(BOUND_MILLIS, TimeUnit.MILLISECONDS), "der Schreiber hält die erste Zeile")

        within(BOUND_MILLIS, "14 Aufrufe von accept") {
            repeat(SMALL_QUEUE + OVERFLOW) { adapter.accept(event) }
        }

        channel.release()
        adapter.close()
        val parsed = channel.lines.map(::parseFlat)
        assertEquals(1 + SMALL_QUEUE, parsed.size)
        assertEquals(listOf<Any?>(null, OVERFLOW.toLong(), null, null, null), parsed.map { it["dropped"] })
    }

    /**
     * Mit `close` schreibt der Schreiber die Warteschlange leer, bevor der Aufruf zurückkehrt; es bleibt nichts
     * verloren, also steht keine Zeile `log_dropped` da.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Close leert die Warteschlange`() {
        val channel = RecordingChannel()
        val adapter = adapter(EventLevel.DEBUG, channel)
        val event = first(EventKind.SERVER_STARTED)
        adapter.startWriter()
        repeat(100) { adapter.accept(event) }
        adapter.close()
        assertEquals(List(100) { "server_started" }, names(channel.lines))
    }

    /**
     * Ein langsamer Kanal und eine kurze Frist: `close` kehrt nach der Frist zurück; die Zeilen, die der Schreiber dem
     * Kanal übergab (auch die, die er beim Ablauf der Frist hielt), und die Zahl `dropped` der letzten Zeile
     * `log_dropped` ergeben zusammen die Zahl der übergebenen Ereignisse, und die letzte Zeile steht allein da.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Was nach der Frist bleibt, meldet die letzte Zeile mit seiner Zahl`() {
        val taken = AtomicInteger()
        val finals = RecordingChannel()
        val adapter = adapter(
            EventLevel.DEBUG,
            LineChannel { line ->
                if (line.contains("log_dropped")) {
                    finals.write(line)
                } else {
                    taken.incrementAndGet()
                    Thread.sleep(SLOW_MILLIS)
                }
            },
        )
        val event = first(EventKind.SERVER_STARTED)
        val total = 40
        adapter.startWriter()
        repeat(total) { adapter.accept(event) }
        adapter.close(Duration.ofMillis(SHORT_GRACE_MILLIS))
        val last = parseFlat(finals.awaitLines(1).single())
        assertEquals("log_dropped", last["event"])
        val lost = last["dropped"] as Long
        assertTrue(lost in 1 until total, "verloren: $lost")
        assertEquals(total.toLong(), lost + taken.get())
    }

    /**
     * Ein Kanal, der nie annimmt: `close` und `writeLast` kehren nach ihren Fristen zurück (300 ms, 250 ms und 250 ms
     * für die Zeile der verlorenen Zeilen), der Aufruf endet also, obwohl der Kanal hängt.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Ein haengender Kanal haelt das Prozessende nach der Frist nicht auf`() {
        val channel = gated()
        val adapter = adapter(EventLevel.DEBUG, channel, SMALL_QUEUE)
        val event = first(EventKind.SERVER_STARTED)
        adapter.startWriter()
        within(BOUND_MILLIS, "Ereignisse bei hängendem Kanal") { repeat(SMALL_QUEUE + 2) { adapter.accept(event) } }
        assertTrue(channel.entered.await(BOUND_MILLIS, TimeUnit.MILLISECONDS))

        val started = System.nanoTime()
        within(BOUND_MILLIS, "close und writeLast bei hängendem Kanal") {
            adapter.close(Duration.ofMillis(SHORT_GRACE_MILLIS))
            adapter.writeLast("tdswire: gestoppt")
        }
        val elapsed = Duration.ofNanos(System.nanoTime() - started)
        assertTrue(elapsed >= Duration.ofMillis(SHORT_GRACE_MILLIS), "die Frist wurde abgewartet: $elapsed")
        assertEquals(emptyList(), channel.lines.toList())
    }

    /**
     * `writeLast` wartet, bis die Zeile im Kanal steht: ein Kanal, der erst nach [SLOW_MILLIS] annimmt, hat die Zeile
     * beim Rückkehren des Aufrufs. Die Frist ist hier weit über der Dauer des Kanals; rot wird der Test, wenn der
     * Aufruf die übergebene Frist nicht abwartet. Getestet ist das Warten, keine Wanduhr-Grenze.
     *
     * @spec SPEC-052
     */
    @Test
    fun `WriteLast wartet auf die Zeile`() {
        val written = AtomicInteger()
        val adapter = adapter(
            EventLevel.DEBUG,
            LineChannel {
                Thread.sleep(SLOW_MILLIS)
                written.incrementAndGet()
            },
        )
        adapter.writeLast("tdswire: gestoppt", Duration.ofMillis(BOUND_MILLIS))
        assertEquals(1, written.get())
    }

    /**
     * Die Zeile `tdswire: gestartet, Port <n>` geht nie über die Warteschlange: bei voller Warteschlange und einem
     * Schreiber, der die Zeilen des Adapters festhält, steht sie sofort im Kanal.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Die Bereitschaftszeile umgeht die Warteschlange`() {
        val channel = gated { it.startsWith("{") }
        val adapter = adapter(EventLevel.DEBUG, channel, SMALL_QUEUE)
        val event = first(EventKind.SERVER_STARTED)
        adapter.startWriter()
        adapter.accept(event)
        assertTrue(channel.entered.await(BOUND_MILLIS, TimeUnit.MILLISECONDS))
        within(BOUND_MILLIS, "Ereignisse bei hängendem Schreiber") {
            repeat(SMALL_QUEUE + OVERFLOW) { adapter.accept(event) }
        }

        within(BOUND_MILLIS, "writeDirect bei voller Warteschlange") {
            adapter.writeDirect("tdswire: gestartet, Port 1433")
        }
        assertEquals(listOf("tdswire: gestartet, Port 1433"), channel.lines.toList())
    }

    /**
     * Nach `close` nimmt der Adapter keine Zeile mehr an: ein Ereignis wird gezählt und nicht geschrieben, und
     * `close` ein zweites Mal meldet es in einer Zeile `log_dropped`.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Nach Close verwirft der Adapter`() {
        val channel = RecordingChannel()
        val adapter = adapter(EventLevel.DEBUG, channel)
        adapter.startWriter()
        adapter.close()
        adapter.accept(first(EventKind.SERVER_STARTED))
        assertEquals(emptyList(), channel.lines.toList())
        adapter.close()
        assertEquals(1L, parseFlat(channel.lines.single())["dropped"])
        assertNotEquals("", channel.lines.single())
    }

    private companion object {
        const val TEST_THREAD = "Test worker"
        const val SLOW_MILLIS = 100L
    }
}
