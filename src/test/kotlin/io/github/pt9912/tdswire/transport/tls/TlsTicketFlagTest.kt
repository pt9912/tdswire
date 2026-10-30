package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.TicketProbe
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Das JVM-Flag `jdk.tls.server.newSessionTicketCount` im TDS-7.4-Pfad mit TLS 1.3. Das JDK liest das
 * Flag einmal je Prozess, deshalb läuft jeder Fall in einer Kind-JVM mit [TicketProbe]; die Gegenstelle
 * ist die JDK-`SSLEngine` im selben Prozess, nicht der Referenzclient.
 */
class TlsTicketFlagTest {
    private fun probe(flag: String?): String {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val command = mutableListOf(java, "-Xmx256m")
        if (flag != null) command += "-Djdk.tls.server.newSessionTicketCount=$flag"
        command += "-Dtdswire.testkeys=${System.getProperty("tdswire.testkeys")}"
        val classpath = checkNotNull(System.getProperty("tdswire.testclasspath"))
        command += listOf("-cp", classpath, TicketProbe::class.java.name)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS), "Kind-JVM endet nicht")
        return output.lineSequence().firstOrNull { it.startsWith(TicketProbe.MARKER) } ?: fail(output.take(MAX_OUTPUT))
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
        const val MAX_OUTPUT = 2000
    }
}
