package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.TicketProbe
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Das JVM-Flag `jdk.tls.server.newSessionTicketCount` im TDS-7.4-Pfad mit TLS 1.3. Das JDK liest das
 * Flag einmal je Prozess, deshalb läuft jeder Fall in einer Kind-JVM mit [TicketProbe]; die Gegenstelle
 * ist die JDK-`SSLEngine` im selben Prozess, nicht der Referenzclient.
 */
class TlsTicketFlagTest {
    /**
     * Die Ausgabe des Prozesses, oder `null`, wenn er nach [waitSeconds] nicht endet (dann wird er beendet).
     * Die Ausgabe wird nebenher gelesen: ein Lesen vor dem Warten würde das Zeitlimit aushebeln.
     */
    private fun outputOf(command: List<String>, waitSeconds: Long): String? {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        if (!process.waitFor(waitSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        return output.get(JOIN_SECONDS, TimeUnit.SECONDS)
    }

    private fun probe(flag: String?): String {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val command = mutableListOf(java, "-Xmx256m")
        if (flag != null) command += "-Djdk.tls.server.newSessionTicketCount=$flag"
        command += "-Dtdswire.testkeys=${System.getProperty("tdswire.testkeys")}"
        val classpath = checkNotNull(System.getProperty("tdswire.testclasspath"))
        command += listOf("-cp", classpath, TicketProbe::class.java.name)
        val output = outputOf(command, WAIT_SECONDS) ?: fail("Kind-JVM endet nicht")
        return output.lineSequence().firstOrNull { it.startsWith(TicketProbe.MARKER) } ?: fail(output.take(MAX_OUTPUT))
    }

    /**
     * Das Zeitlimit wirkt: ein Prozess, der nicht endet (`sleep 30`), wird nach einer Sekunde abgebrochen
     * und gemeldet, statt dass der Test bis zum Ende des Prozesses wartet.
     *
     * @spec SPEC-028
     */
    @Test
    fun `a child process that does not end is cut off at the time limit`() {
        val started = System.nanoTime()
        assertNull(outputOf(listOf("sleep", "30"), 1))
        val seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)
        assertTrue(seconds < SLEEP_SECONDS, "Wartezeit $seconds s")
    }

    /**
     * Mit `-Djdk.tls.server.newSessionTicketCount=0` kommt eine Nachricht des Servers im TDS-7.4-Pfad mit
     * TLS 1.3 bei der Gegenstelle an; ohne das Flag scheitert dieselbe Anordnung beim Lesen der Nachricht
     * (Kind-JVM, JDK 21 der Testumgebung). Das bestätigt für diese Gegenstelle die Aussage des
     * Sicherheitsprofils, nicht ihre Ursache im Einzelnen.
     *
     * @spec SPEC-028
     */
    @Test
    fun `without the session ticket flag the encapsulated TLS 1_3 exchange fails`() {
        assertEquals("${TicketProbe.MARKER} exchange_ok", probe("0"))
        assertTrue(probe(null).startsWith("${TicketProbe.MARKER} exchange_failed"), "ohne Flag")
    }

    /**
     * Die Test-JVM selbst läuft mit dem Flag; die übrigen TLS-Tests im TDS-7.4-Pfad mit TLS 1.3 stehen darauf.
     *
     * @spec SPEC-028
     */
    @Test
    fun `test JVM sets the session ticket flag`() {
        assertEquals("0", System.getProperty("jdk.tls.server.newSessionTicketCount"))
    }

    private companion object {
        const val WAIT_SECONDS = 60L
        const val JOIN_SECONDS = 10L
        const val SLEEP_SECONDS = 15L
        const val MAX_OUTPUT = 2000
    }
}
