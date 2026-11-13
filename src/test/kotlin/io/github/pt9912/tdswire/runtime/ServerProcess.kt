package io.github.pt9912.tdswire.runtime

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val WAIT_SECONDS = 60L
private const val POLL_MILLIS = 20L
private const val MEMORY_LIMIT = "-Xmx256m"

/**
 * Der Testserver als Kind-JVM mit `main` aus dem Klassenpfad des Gradle-Moduls `testserver` (Systemeigenschaft
 * `tdswire.testserverclasspath`). Die Umgebung des Kindes besteht nur aus [environment]; Standardausgabe und
 * Fehlerausgabe werden zeilenweise in [output] gesammelt; mit `drain = false` liest der Test die Ausgabe nicht, und
 * der Kanal des Kindes füllt sich. Der Start der Kind-JVM setzt keine JVM-Option außer dem Heap und den [jvmOptions].
 */
internal class ServerProcess(
    environment: Map<String, String>,
    mainClass: String = MAIN_CLASS,
    drain: Boolean = true,
    jvmOptions: List<String> = emptyList(),
) : AutoCloseable {
    val output = CopyOnWriteArrayList<String>()
    private val process: Process
    private var reader: Thread

    init {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val classpath = checkNotNull(System.getProperty("tdswire.testserverclasspath"))
        val command = listOf(java, MEMORY_LIMIT) + jvmOptions + listOf("-cp", classpath, mainClass)
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(environment)
        process = builder.start()
        reader = thread(isDaemon = true, name = "tdswire-test-server-output") { if (drain) readOutput() }
    }

    private fun readOutput() = process.inputStream.bufferedReader().forEachLine { output += it }

    /** Beginnt, die Ausgabe zu lesen, wenn der Prozess mit `drain = false` gestartet wurde. */
    fun startDraining() {
        reader = thread(isDaemon = true, name = "tdswire-test-server-output") { readOutput() }
    }

    /** Ob der Prozess läuft. */
    fun alive(): Boolean = process.isAlive

    /**
     * Die Zeilen der Ausgabe, in denen das Feld `principal` des Ereignisses `login_accepted` leer ist: die Kennung der
     * angenommenen Anmeldung darf dort stehen (`ADR-0020`), sonst nichts, was eine Kennung oder ein Geheimnis nennt.
     */
    fun outputWithoutPrincipal(): List<String> = output.map { line ->
        if (line.contains("\"event\":\"login_accepted\"")) line.replace(PRINCIPAL_FIELD, "\"principal\":\"\"") else line
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

    /**
     * Wartet auf die erste Zeile ab dem Index [from], für die [match] gilt; liefert ihren Index in [output] oder
     * `null`, wenn der Prozess vorher endet oder die Schranke von [WAIT_SECONDS] abläuft. Die Schranke ist keine
     * Erwartung der Dauer: der Test wartet auf die Zeile.
     */
    fun awaitIndex(from: Int = 0, match: (String) -> Boolean): Int? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (true) {
            val snapshot = output.toList()
            val at = (from until snapshot.size).firstOrNull { match(snapshot[it]) }
            if (at != null) return at
            if (System.nanoTime() >= deadline || !running()) return null
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun running(): Boolean = process.isAlive || reader.isAlive

    /** Der Port aus der Zeile `tdswire: gestartet, Port <n>`. */
    fun port(): Int = checkNotNull(awaitLine("$PREFIX gestartet, Port ")) { "kein Start: $output" }
        .substringAfterLast(' ').toInt()

    /** Wartet auf das Ende des Prozesses und gibt den Exit-Code zurück; `null` nach Ablauf der Zeit (dann beendet). */
    fun awaitExit(seconds: Long = WAIT_SECONDS): Int? {
        if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        reader.join(TimeUnit.SECONDS.toMillis(seconds))
        return process.exitValue()
    }

    /**
     * Sendet SIGTERM, wie `docker stop`, und wartet auf das Ende; der Shutdown-Hook schreibt `gestoppt`. `kill`
     * statt `Process.destroy`: `destroy` schließt die Ausgabe des Kindes, bevor der Hook sie geschrieben hat.
     */
    fun terminate(seconds: Long = WAIT_SECONDS): Int? {
        signalTerm()
        return awaitExit(seconds)
    }

    /** Sendet SIGTERM und kehrt zurück, ohne auf das Ende zu warten. */
    fun signalTerm() {
        check(ProcessBuilder("kill", "-TERM", process.pid().toString()).start().waitFor() == 0) { "kill gescheitert" }
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
        private val PRINCIPAL_FIELD = Regex(""""principal":"(?:[^"\\]|\\.)*"""")
        private const val MAIN_CLASS = "io.github.pt9912.tdswire.testserver.MainKt"

        /** Das `main` des Testservers mit den Tabellen des Kompatibilitätslaufs; `laufend` liefert Zeilen ohne Ende. */
        const val RUNNING_TABLE_MAIN = "io.github.pt9912.tdswire.testserver.KompatibilitaetsServerKt"
    }
}
