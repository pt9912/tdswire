package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private const val EXIT_ABORTED = 2
private const val MAX_HELD = 2_000
private const val CONNECT_MILLIS = 500
private const val AWAIT_SECONDS = 10L
private const val PROVOKE_SECONDS = 40L
private const val POLL_MILLIS = 20L
private const val START_SECONDS = 60L
private const val CHILD_MAIN = "io.github.pt9912.tdswire.runtime.AcceptChildKt"

/** Präfix der Zeilen, in denen die Kind-JVM ein Ereignis meldet; danach folgt der Text des Ereignisses. */
internal const val EVENT_PREFIX = "EVENT "

/** Präfix der Zeile, die den gebundenen Port nennt. */
internal const val PORT_PREFIX = "PORT "

/**
 * Das `main` der Kind-JVM von [AcceptChild]: startet den Server aus der Umgebung des Prozesses mit einer Senke, die
 * jedes Ereignis als Zeile auf die Standardausgabe schreibt, und läuft, bis der Elternprozess es beendet.
 */
fun main(): Unit = runBlocking {
    val sink = TelemetrySink { println(EVENT_PREFIX + it.visibleText()) }
    when (val outcome = Bootstrap.launch(configOf(System.getenv()), Wiring(telemetry = sink))) {
        is StartOutcome.Running -> {
            println(PORT_PREFIX + outcome.server.port)
            Thread.currentThread().join()
        }

        is StartOutcome.Aborted -> {
            println("ABORTED ${outcome.reason}")
            exitProcess(EXIT_ABORTED)
        }
    }
}

/**
 * Der Server als Kind-JVM mit einem Limit für offene Dateien (`ulimit -n`): die Verbindungen, die ein Client hält,
 * erschöpfen es, und das Annehmen scheitert am Betriebssystem, ohne dass der Test eine Naht im Produktcode braucht.
 * Die Kind-JVM lädt keine Klassen vor: ist das Limit erschöpft, kann sie keine Klassendatei öffnen, und der Test
 * belegt damit die Vorbereitung im Produktcode (`ADR-0043`). Klassen der Senke dieser Datei liegen außerhalb der
 * Vorbereitung. Der Klassenpfad ist der der Test-JVM. Standard- und Fehlerausgabe des Kindes werden zeilenweise in [output]
 * gesammelt.
 */
internal class AcceptChild(environment: Map<String, String>, descriptors: Int) : AutoCloseable {
    val output = CopyOnWriteArrayList<String>()
    private val process: Process

    init {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val command = listOf(
            "sh",
            "-c",
            "ulimit -n $descriptors && exec \"\$0\" \"\$@\"",
            java,
            "-Xmx128m",
            "-cp",
            System.getProperty("java.class.path"),
            CHILD_MAIN,
        )
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(environment)
        process = builder.start()
        thread(isDaemon = true, name = "tdswire-test-accept-child-output") {
            // Ein geschlossener Strom beendet das Lesen: das Kind ist beendet oder wird beendet.
            runCatching { process.inputStream.bufferedReader().forEachLine { output += it } }
        }
    }

    /** Wartet auf die erste Zeile, die [accept] annimmt; `null` nach [seconds] oder wenn das Kind endet. */
    fun await(seconds: Long, accept: (String) -> Boolean): String? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        var found = output.firstOrNull(accept)
        while (found == null && System.nanoTime() < deadline && process.isAlive) {
            Thread.sleep(POLL_MILLIS)
            found = output.firstOrNull(accept)
        }
        return found ?: output.firstOrNull(accept)
    }

    /** Der gebundene Port aus der Zeile `PORT <n>`. */
    fun port(): Int = checkNotNull(await(START_SECONDS) { it.startsWith(PORT_PREFIX) }) { "kein Start: $output" }
        .removePrefix(PORT_PREFIX).toInt()

    override fun close() {
        process.destroyForcibly()
        process.waitFor(START_SECONDS, TimeUnit.SECONDS)
    }
}

/**
 * Hält Verbindungen zum Server der Kind-JVM offen, bis sie ein `ACCEPT_FAILED` meldet (höchstens [MAX_HELD]), schließt
 * sie dann und gibt die Ereigniszeilen des Kindes zurück. Meldet das Kind keines, steht keine Zeile dieser Art in der
 * Liste, und der Aufrufer prüft das.
 */
internal fun AcceptChild.provokeAcceptFailure(port: Int): List<String> {
    val held = mutableListOf<Socket>()
    try {
        var count = 0
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROVOKE_SECONDS)
        while (count < MAX_HELD && System.nanoTime() < deadline && output.none { "Kind=ACCEPT_FAILED" in it }) {
            count++
            runCatching { held += Socket().also { it.connect(InetSocketAddress(LOOPBACK, port), CONNECT_MILLIS) } }
                .onFailure { Thread.sleep(CONNECT_MILLIS.toLong()) }
        }
        await(AWAIT_SECONDS) { "Kind=ACCEPT_FAILED" in it }
        output += "held=${held.size} count=$count"
    } finally {
        held.forEach { it.close() }
    }
    return output.filter { it.startsWith(EVENT_PREFIX) }
}
