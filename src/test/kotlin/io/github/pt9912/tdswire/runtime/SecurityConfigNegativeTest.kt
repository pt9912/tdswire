package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.domain.telemetry.ConfigInvalid
import io.github.pt9912.tdswire.domain.telemetry.PlainTestModeActive
import io.github.pt9912.tdswire.domain.telemetry.ServerStarted
import io.github.pt9912.tdswire.domain.telemetry.TlsStartRejected
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.assertNoNewListenerThreads
import io.github.pt9912.tdswire.transport.listener.listenerThreads
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Konfigurationsnegativtests der Sicherheit am Start des Prozesses (`SPEC-010`, `SPEC-043`, `TDS-QA-03`): Jede
 * Abweichung beendet den Start vor dem Binden, ohne Rückfall auf einen schwächeren Modus; das Log nennt Schlüssel
 * und Art, nie einen Wert, einen Pfad oder ein Kennwort; es läuft kein Thread des Servers, der Port bleibt frei.
 */
internal class SecurityConfigNegativeTest : RuntimeTestBase() {
    private val secret = "Zk9-geheim"
    private val directory = checkNotNull(System.getProperty("tdswire.testkeys"))

    private fun users(text: String = "Zk9-alice\t${pbkdf2Value(secret)}\n", name: String = "users-Zk9.txt") =
        file(name, text)

    private fun tls(port: Int, store: String = "$directory/gueltig.p12", password: String = "tdswire-test") = mapOf(
        ConfigKeys.LISTEN_HOST to LOOPBACK,
        ConfigKeys.LISTEN_PORT to port.toString(),
        ConfigKeys.TLS_KEYSTORE_FILE to store,
        ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to file("kennwort-Zk9.txt", "$password\n"),
        ConfigKeys.AUTH_USERS_FILE to users(),
    )

    private fun assertFree(port: Int) {
        ServerSocket(port, 1, InetAddress.getByName(LOOPBACK)).use { assertEquals(port, it.localPort) }
    }

    private suspend fun assertAborts(
        label: String,
        port: Int,
        environment: Map<String, String>,
        expected: List<String>,
    ) {
        sink.events.clear()
        val before = listenerThreads()
        val aborted = assertIs<StartOutcome.Aborted>(start(environment), label)
        assertEquals(expected, sink.of<ConfigInvalid>().map { "${it.key}: ${it.kind}" }, label)
        assertEquals(expected.size, sink.events.size, label)
        (sink.events.map { it.visibleText() } + aborted.reason).forEach { text ->
            listOf("Zk9", directory, ".p12", "tdswire-test", "kennwort").forEach {
                assertTrue(!text.contains(it), "$label: Ereignis nennt $it: $text")
            }
        }
        assertNoNewListenerThreads(before)
        assertFree(port)
    }

    /**
     * Die Richtlinie wird nie stillschweigend schwächer: Ohne Angabe gilt `REQUIRED` und verlangt Speicher und
     * Kennwortdatei; `TEST_UNENCRYPTED` mit TLS-Dateien ist ein Widerspruch. Jeder Fall bricht ab, bevor ein Port
     * gebunden ist. Ein unbekannter Wert der Richtlinie ist ein Fehler des Laders des Testservers.
     *
     * @spec SPEC-010
     */
    @Test
    fun `Die Richtlinie faellt nie auf unverschluesselt zurueck`() = blocking {
        val port = freePort()
        val base = mapOf(
            ConfigKeys.LISTEN_HOST to LOOPBACK,
            ConfigKeys.LISTEN_PORT to port.toString(),
            ConfigKeys.AUTH_USERS_FILE to users(),
        )
        assertAborts(
            "ohne Angabe",
            port,
            base,
            listOf(
                "TLS_KEYSTORE_FILE: MISSING",
                "TLS_KEYSTORE_PASSWORD_FILE: MISSING",
            ),
        )
        assertAborts(
            "Testmodus mit TLS-Dateien",
            port,
            tls(port) + (ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED"),
            listOf(
                "TLS_KEYSTORE_FILE: CONFLICT",
                "TLS_KEYSTORE_PASSWORD_FILE: CONFLICT",
            ),
        )
    }

    /**
     * Speicher und Kennwortdatei: ein Speicher, der kein PKCS#12 ist, bricht den Start ab; das Log nennt Teil und
     * Art, nie den Pfad. Eine Kennwortdatei, die es nicht gibt, meldet der Lader des Testservers. Kein Rückfall auf
     * unverschlüsselt.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Ein unlesbarer Speicher bricht den Start ab`() = blocking {
        val port = freePort()
        val broken = file("kaputt-Zk9.p12", "kein Schluesselspeicher")
        sink.events.clear()
        val aborted = assertIs<StartOutcome.Aborted>(start(tls(port, store = broken)))
        assertTrue(aborted.reason.startsWith("TLS-Konfiguration: CERTIFICATE"), aborted.reason)
        assertEquals("CERTIFICATE", sink.events.filterIsInstance<TlsStartRejected>().single().part)
        assertFalse(aborted.reason.contains(directory) || aborted.reason.contains("Zk9"), aborted.reason)
        assertFree(port)
    }

    /**
     * Mit dem Testmodus für `plain` startet der Server auch mit TLS und schreibt die Zeile `ACHTUNG` (Abschwächung
     * sichtbar, `SPEC-010`). Die Schreibweisen des Schalters und die Liste ohne Testmodus prüft der Lader des
     * Testservers.
     *
     * @spec SPEC-010
     * @spec SPEC-051
     */
    @Test
    fun `Der Testmodus fuer plain ist im Log sichtbar`() = blocking {
        val port = freePort()
        val plain = users("Zk9-alice\t{plain}$secret\n", "users-plain-Zk9.txt")
        val withPlain = tls(port) + (ConfigKeys.AUTH_USERS_FILE to plain)
        sink.events.clear()
        val plainOn = withPlain + (ConfigKeys.AUTH_PLAIN_TESTMODE to "true")
        val server = assertIs<StartOutcome.Running>(start(plainOn)).server
        assertEquals(
            listOf(ServerStarted::class, PlainTestModeActive::class),
            sink.events.map { it::class },
        )
        assertEquals(server.port, (sink.events.first() as ServerStarted).port)
    }
}
