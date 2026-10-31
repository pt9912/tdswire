package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.assertNoNewListenerThreads
import io.github.pt9912.tdswire.transport.listener.listenerThreads
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
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
        log.lines.clear()
        val before = listenerThreads()
        assertEquals(BootResult.Aborted, start(environment), label)
        assertEquals(expected, log.lines.toList(), label)
        log.lines.forEach { line ->
            listOf("Zk9", directory, ".p12", "tdswire-test", "kennwort").forEach {
                assertTrue(!line.contains(it), "$label: Logzeile nennt $it: $line")
            }
        }
        assertNoNewListenerThreads(before)
        assertFree(port)
    }

    /**
     * Die Richtlinie wird nie stillschweigend schwächer: Ohne Angabe gilt `REQUIRED` und verlangt Speicher und
     * Kennwortdatei; `TEST_UNENCRYPTED` mit TLS-Dateien ist ein Widerspruch; ein unbekannter Wert der Richtlinie
     * ist ungültig und führt nicht zu `TEST_UNENCRYPTED`. Jeder Fall bricht ab, bevor ein Port gebunden ist.
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
                "Konfiguration ungueltig: TLS_KEYSTORE_FILE: MISSING",
                "Konfiguration ungueltig: TLS_KEYSTORE_PASSWORD_FILE: MISSING",
            ),
        )
        assertAborts(
            "Testmodus mit TLS-Dateien",
            port,
            tls(port) + (ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED"),
            listOf(
                "Konfiguration ungueltig: TLS_KEYSTORE_FILE: CONFLICT",
                "Konfiguration ungueltig: TLS_KEYSTORE_PASSWORD_FILE: CONFLICT",
            ),
        )
        listOf("test_unencrypted", "UNENCRYPTED", "TEST_UNENCRYPTED ", "OFF").forEach { value ->
            assertAborts(
                "Wert '$value'",
                port,
                tls(port) + (ConfigKeys.ENCRYPTION_POLICY to value),
                listOf("Konfiguration ungueltig: ENCRYPTION_POLICY: INVALID_VALUE"),
            )
        }
    }

    /**
     * Speicher und Kennwortdatei: ein Speicher, der kein PKCS#12 ist, und eine Kennwortdatei, die es nicht gibt,
     * brechen den Start ab; das Log nennt Teil und Art, nie den Pfad. Kein Rückfall auf unverschlüsselt.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Ein unlesbarer Speicher oder eine fehlende Kennwortdatei bricht den Start ab`() = blocking {
        val port = freePort()
        val broken = file("kaputt-Zk9.p12", "kein Schluesselspeicher")
        log.lines.clear()
        assertEquals(BootResult.Aborted, start(tls(port, store = broken)))
        assertTrue(log.lines.single().startsWith("TLS-Konfiguration: CERTIFICATE"), log.lines.toString())
        val missing = tls(port) + (ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to "$directory/gibt-es-nicht-Zk9.txt")
        assertAborts(
            "Kennwortdatei fehlt",
            port,
            missing,
            listOf("Konfiguration ungueltig: TLS_KEYSTORE_PASSWORD_FILE: UNREADABLE"),
        )
        assertFree(port)
    }

    /**
     * Der Testmodus für `plain` gilt nur bei genau `true`: andere Schreibweisen sind ungültig, und eine Liste mit
     * `plain`-Eintrag startet ohne den Testmodus nicht, auch nicht mit TLS. Mit `true` startet der Server und schreibt
     * die Zeile `ACHTUNG` (Abschwächung sichtbar, `SPEC-010`).
     *
     * @spec SPEC-010
     * @spec SPEC-051
     */
    @Test
    fun `Der Testmodus fuer plain gilt nur bei genau true und ist im Log sichtbar`() = blocking {
        val port = freePort()
        val plain = users("Zk9-alice\t{plain}$secret\n", "users-plain-Zk9.txt")
        val withPlain = tls(port) + (ConfigKeys.AUTH_USERS_FILE to plain)
        listOf("True", "TRUE", "1", "yes", " true").forEach { value ->
            assertAborts(
                "Wert '$value'",
                port,
                withPlain + (ConfigKeys.AUTH_PLAIN_TESTMODE to value),
                listOf("Konfiguration ungueltig: AUTH_PLAIN_TESTMODE: INVALID_VALUE"),
            )
        }
        assertAborts(
            "plain ohne Testmodus",
            port,
            withPlain,
            listOf("Konfiguration ungueltig: AUTH_USERS_FILE: USER_LIST_INVALID (PLAIN_NOT_ALLOWED, Zeile 1)"),
        )
        log.lines.clear()
        val server = assertIs<BootResult.Running>(start(withPlain + (ConfigKeys.AUTH_PLAIN_TESTMODE to "true"))).server
        assertEquals(
            listOf(
                "gestartet, Port ${server.port}",
                "ACHTUNG: AUTH_PLAIN_TESTMODE ist aktiv, das Verfahren plain ist zugelassen",
            ),
            log.lines.toList(),
        )
    }
}
