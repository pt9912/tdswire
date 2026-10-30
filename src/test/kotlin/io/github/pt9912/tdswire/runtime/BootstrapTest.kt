package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.assertNoNewListenerThreads
import io.github.pt9912.tdswire.transport.listener.listenerThreads
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Start-Reihenfolge und Startabbruch des Prozesses (`TDS-FA-09.a`, `SPEC-043`, `ARC-008`). */
internal class BootstrapTest : RuntimeTestBase() {
    private fun users() = file("users.txt", "kennung-4711\t{plain}passwort-4711\n")

    private fun assertFree(port: Int) {
        ServerSocket(port, 1, InetAddress.getByName(LOOPBACK)).use { assertEquals(port, it.localPort) }
    }

    /**
     * Ein Prozess ohne `-Djdk.tls.server.newSessionTicketCount=0` startet nicht: jede Eigenschaft außer genau `0`
     * (fehlend, `1`, `00`, leer, `0 `) bricht ab, bevor eine Datei gelesen oder ein Port gebunden wird. Mit `0`
     * startet derselbe Aufruf. Geprüft ist der Wert der Eigenschaft beim Start, nicht die Wirkung auf dem Draht.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Ohne das JVM-Flag startet der Server nicht`() = blocking {
        val before = listenerThreads()
        val environment = loopbackEnvironment(users())
        listOf(null, "1", "00", "", "0 ", "-1").forEach { value ->
            val properties = { key: String -> if (key == Bootstrap.TICKET_FLAG) value else null }
            val boot = Bootstrap.start(environment, properties, Wiring(log = log))
            assertEquals(BootResult.Aborted, boot, "Wert '$value'")
        }
        assertTrue(log.lines.all { it.contains("newSessionTicketCount=0") }, log.lines.toString())
        assertEquals(6, log.lines.size)
        assertNoNewListenerThreads(before)
        assertIs<BootResult.Running>(start(environment))
    }

    /**
     * Eine ungültige Konfiguration beendet den Start vor dem Binden: das Log nennt Schlüssel und Art jedes
     * Fehlers, keinen Wert; es läuft kein Thread des Servers; der gewählte Port bleibt frei.
     *
     * @spec SPEC-005
     * @spec SPEC-043
     */
    @Test
    fun `Eine ungueltige Konfiguration bricht den Start ab und nennt keinen Wert`() = blocking {
        val before = listenerThreads()
        val port = freePort()
        val environment = loopbackEnvironment(
            users(),
            ConfigKeys.LISTEN_PORT to port.toString(),
            ConfigKeys.LOGIN_TIMEOUT to "geheim-wert-4711",
            ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES to "1",
        )
        assertEquals(BootResult.Aborted, start(environment))
        assertEquals(
            listOf(
                "Konfiguration ungueltig: LOGIN_TIMEOUT: INVALID_VALUE",
                "Konfiguration ungueltig: MAX_PRELOGIN_MESSAGE_BYTES: OUT_OF_RANGE",
            ),
            log.lines.toList(),
        )
        assertNoNewListenerThreads(before)
        assertFree(port)
    }

    /**
     * Eine Benutzerliste mit Fehler bricht den Start ab: das Log nennt Schlüssel, Fehlerart und Zeile, weder
     * Benutzername noch Prüfwert noch Passwort noch Pfad, und der Port wird nicht gebunden.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Eine fehlerhafte Benutzerliste bricht den Start ab`() = blocking {
        val port = freePort()
        val path = file("kennung-4711.txt", "kennung-4711\t{pbkdf2-sha256}passwort-4711\n")
        val environment = loopbackEnvironment(path, ConfigKeys.LISTEN_PORT to port.toString())
        assertEquals(BootResult.Aborted, start(environment))
        val expected = "Konfiguration ungueltig: AUTH_USERS_FILE: USER_LIST_INVALID (FIELD_COUNT, Zeile 1)"
        assertEquals(listOf(expected), log.lines.toList())
        assertFree(port)
    }

    /**
     * Ein belegter Port beendet den Start mit der Diagnose zu Adresse und Port (`SPEC-043`); danach läuft kein
     * Thread des Servers weiter, und der Belegende bleibt unberührt.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Ein belegter Port bricht den Start ab`() = blocking {
        val before = listenerThreads()
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            val environment = loopbackEnvironment(users(), ConfigKeys.LISTEN_PORT to occupied.localPort.toString())
            assertEquals(BootResult.Aborted, start(environment))
            assertEquals(listOf("Start gescheitert: Port belegt: $LOOPBACK:${occupied.localPort}"), log.lines.toList())
            assertNoNewListenerThreads(before)
            assertFalse(occupied.isClosed)
        }
    }

    /**
     * Ein Speicher mit abgelaufenem Zertifikat beendet den Start über die Startprobe, bevor ein Port gebunden
     * ist; das Log nennt den Teil und die Form, nie Pfad oder Kennwort. Ein gültiger Speicher startet.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Eine fehlgeschlagene Startprobe bricht den Start vor dem Binden ab`() = blocking {
        val port = freePort()
        val before = listenerThreads()
        val bad = tlsEnvironment("abgelaufen.p12", port)
        assertEquals(BootResult.Aborted, start(bad))
        assertEquals(1, log.lines.size, log.lines.toString())
        val line = log.lines.single()
        assertTrue(line.startsWith("Start gescheitert: TLS-Konfiguration: CERTIFICATE"), line)
        assertNoNewListenerThreads(before)
        assertFree(port)
        val good = tlsEnvironment("gueltig.p12", port)
        assertIs<BootResult.Running>(start(good))
    }

    /**
     * Ein Speicher, den das Kennwort der Datei nicht öffnet, beendet den Start mit der Diagnose des Speichers;
     * der Start läuft nicht mit einem Rückfall auf unverschlüsselt weiter.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Ein falsches Kennwort des Speichers bricht den Start ab`() = blocking {
        val environment = tlsEnvironment("gueltig.p12", 0, password = "falsch-4711")
        assertEquals(BootResult.Aborted, start(environment))
        assertTrue(log.lines.single().startsWith("TLS-Konfiguration: CERTIFICATE"), log.lines.toString())
        assertFalse(log.lines.single().contains("4711"))
    }

    private fun tlsEnvironment(store: String, port: Int, password: String = "tdswire-test"): Map<String, String> {
        val directory = checkNotNull(System.getProperty("tdswire.testkeys"))
        return mapOf(
            ConfigKeys.LISTEN_HOST to LOOPBACK,
            ConfigKeys.LISTEN_PORT to port.toString(),
            ConfigKeys.TLS_KEYSTORE_FILE to "$directory/$store",
            ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to file("kennwort.txt", "$password\n"),
            ConfigKeys.AUTH_USERS_FILE to file("users-tls.txt", "alice\t${pbkdf2Value("pw")}\n"),
        )
    }

    /**
     * Der Start-Log nennt Port und jede Abschwächung: bei `TEST_UNENCRYPTED` und `AUTH_PLAIN_TESTMODE=true` je
     * eine Zeile `ACHTUNG`, bei `REQUIRED` mit `pbkdf2` keine. Keine Zeile nennt Benutzername, Prüfwert, Pfad oder
     * die Bind-Adresse (`SPEC-010`, `TDS-QA-03`).
     *
     * @spec SPEC-010
     */
    @Test
    fun `Der Start-Log nennt jede Abschwaechung und keinen Wert`() = blocking {
        val weak = running(loopbackEnvironment(users()))
        assertEquals(
            listOf(
                "gestartet, Port ${weak.port}",
                "ACHTUNG: ENCRYPTION_POLICY ist TEST_UNENCRYPTED, Verbindungen sind nicht verschluesselt",
                "ACHTUNG: AUTH_PLAIN_TESTMODE ist aktiv, das Verfahren plain ist zugelassen",
            ),
            log.lines.toList(),
        )
        val weakLines = log.lines.toList()
        log.lines.clear()
        val strong = running(tlsEnvironment("gueltig.p12", 0))
        assertEquals(listOf("gestartet, Port ${strong.port}"), log.lines.toList())
        val all = weakLines + log.lines
        listOf("4711", "kennung", "passwort", "users", LOOPBACK, "tdswire-test").forEach { word ->
            assertTrue(all.none { it.contains(word) }, "Log nennt $word: $all")
        }
    }

    /**
     * Ein gestarteter Server hält den Port; ein zweiter Start auf demselben Port scheitert, bis der erste
     * gestoppt ist. Nach dem Stop ist der Port frei und kein Thread des Servers läuft.
     *
     * @spec SPEC-044
     */
    @Test
    fun `Stop gibt Port und Threads frei`() = blocking {
        val before = listenerThreads()
        val server = running(loopbackEnvironment(users()))
        val environment = loopbackEnvironment(users(), ConfigKeys.LISTEN_PORT to server.port.toString())
        assertEquals(BootResult.Aborted, start(environment))
        assertFailsWith<BindException> { ServerSocket(server.port, 1, InetAddress.getByName(LOOPBACK)).close() }
        server.stop()
        assertFree(server.port)
        assertNoNewListenerThreads(before)
        assertEquals("gestoppt", log.lines.last())
    }
}
