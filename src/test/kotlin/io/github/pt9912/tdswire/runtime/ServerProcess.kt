package io.github.pt9912.tdswire.runtime

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val WAIT_SECONDS = 60L
private const val POLL_MILLIS = 20L
private const val MEMORY_LIMIT = "-Xmx256m"

/**
 * Der Testserver als Kind-JVM mit `main` aus dem Klassenpfad des Source-Sets `testserver` (Systemeigenschaft
 * `tdswire.testserverclasspath`). Die Umgebung des Kindes besteht nur aus [environment]; Standardausgabe und
 * Fehlerausgabe werden zeilenweise in [output] gesammelt. Der Start der Kind-JVM setzt keine JVM-Option außer dem Heap.
 */
internal class ServerProcess(environment: Map<String, String>) : AutoCloseable {
    val output = CopyOnWriteArrayList<String>()
    private val process: Process
    private val reader: Thread

    init {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val classpath = checkNotNull(System.getProperty("tdswire.testserverclasspath"))
        val command = listOf(java, MEMORY_LIMIT, "-cp", classpath, MAIN_CLASS)
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(environment)
        process = builder.start()
        reader = thread(isDaemon = true, name = "tdswire-test-server-output") {
            process.inputStream.bufferedReader().forEachLine { output += it }
        }
    }

    /** Wartet auf eine Zeile, die mit [prefix] beginnt; `null`, wenn der Prozess vorher endet oder die Zeit abläuft. */
    fun awaitLine(prefix: String): String? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        var found = output.firstOrNull { it.startsWith(prefix) }
        while (found == null && System.nanoTime() < deadline && running()) {
            Thread.sleep(POLL_MILLIS)
            found = output.firstOrNull { it.startsWith(prefix) }
        }
        return found
    }

    private fun running(): Boolean = process.isAlive || reader.isAlive

    /** Der Port aus der Zeile `tdswire: gestartet, Port <n>`. */
    fun port(): Int = checkNotNull(awaitLine("$PREFIX gestartet, Port ")) { "kein Start: $output" }
        .substringAfterLast(' ').toInt()

    /** Wartet auf das Ende des Prozesses und gibt den Exit-Code zurück; `null` nach Ablauf der Zeit (dann beendet). */
    fun awaitExit(): Int? {
        if (!process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        reader.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
        return process.exitValue()
    }

    /**
     * Sendet SIGTERM, wie `docker stop`, und wartet auf das Ende; der Shutdown-Hook schreibt `gestoppt`. `kill`
     * statt `Process.destroy`: `destroy` schließt die Ausgabe des Kindes, bevor der Hook sie geschrieben hat.
     */
    fun terminate(): Int? {
        check(ProcessBuilder("kill", "-TERM", process.pid().toString()).start().waitFor() == 0) { "kill gescheitert" }
        return awaitExit()
    }

    /** Beendet das Kind, falls es läuft, und wartet bis zu [WAIT_SECONDS] auf sein Ende. */
    override fun close() {
        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)
        }
    }

    companion object {
        const val PREFIX = "tdswire:"
        private const val MAIN_CLASS = "io.github.pt9912.tdswire.testserver.MainKt"
    }
}
