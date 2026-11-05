package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.domain.telemetry.BindFailed
import io.github.pt9912.tdswire.domain.telemetry.ConfigInvalid
import io.github.pt9912.tdswire.domain.telemetry.EncryptionWeakened
import io.github.pt9912.tdswire.domain.telemetry.PlainTestModeActive
import io.github.pt9912.tdswire.domain.telemetry.ServerStarted
import io.github.pt9912.tdswire.domain.telemetry.ServerStopped
import io.github.pt9912.tdswire.domain.telemetry.StartRefused
import io.github.pt9912.tdswire.domain.telemetry.TlsStartRejected
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Start-Reihenfolge und Startabbruch von `launch` (`TDS-FA-09.a`, `SPEC-043`, `ARC-008`); Umgebung und Dateien liest
 * der Testserver, seine Tests prüfen den Lader.
 */
internal class BootstrapTest : RuntimeTestBase() {
    private fun users() = file("users.txt", "kennung-$WERTPROBE\t{plain}passwort-$WERTPROBE\n")

    private fun assertFree(port: Int) {
        ServerSocket(port, 1, InetAddress.getByName(LOOPBACK)).use { assertEquals(port, it.localPort) }
    }

    /**
     * Der Start verlangt keine JVM-Option: ohne eine Systemeigenschaft zu setzen startet ein Server mit TLS
     * (`REQUIRED`), die Startprobe führt im 8.0-Pfad mit TLS 1.3 den Handschlag aus, dessen Server danach ein
     * NewSessionTicket schreibt (`TlsSessionTicketTest` hält das fest). Es gibt kein Ereignis `StartRefused`, und der
     * Server bindet. Wäre die Probe am Ticket gescheitert, endete der Start mit `TLS-Konfiguration: PAIR`.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Der Start mit TLS gelingt ohne JVM-Option`() = blocking {
        val server = running(tlsEnvironment("gueltig.p12", 0))
        assertTrue(server.port > 0)
        assertEquals(emptyList(), sink.of<StartRefused>())
        assertEquals(listOf(server.port), sink.of<ServerStarted>().map { it.port })
    }

    /**
     * Eine ungültige Konfiguration beendet den Start vor dem Binden: je Fehler ein Ereignis mit Schlüssel und Art,
     * keinem Wert; es läuft kein Thread des Servers; der gewählte Port bleibt frei.
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
            ConfigKeys.LOGIN_TIMEOUT to "0",
            ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES to "1",
        )
        val aborted = assertIs<StartOutcome.Aborted>(start(environment))
        assertEquals(
            listOf(
                ConfigKeys.LOGIN_TIMEOUT to "OUT_OF_RANGE",
                ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES to "OUT_OF_RANGE",
            ),
            sink.of<ConfigInvalid>().map { it.key to it.kind },
        )
        assertEquals(2, sink.events.size)
        assertEquals("Konfiguration ungueltig", aborted.reason)
        assertEquals(
            listOf(
                ConfigIssue(ConfigKeys.LOGIN_TIMEOUT, IssueKind.OUT_OF_RANGE),
                ConfigIssue(ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES, IssueKind.OUT_OF_RANGE),
            ),
            aborted.issues,
        )
        assertNoNewListenerThreads(before)
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
            val aborted = assertIs<StartOutcome.Aborted>(start(environment))
            assertEquals("Start gescheitert: Port belegt: $LOOPBACK:${occupied.localPort}", aborted.reason)
            val failed = sink.events.single() as BindFailed
            assertEquals(LOOPBACK, failed.address)
            assertEquals(occupied.localPort, failed.port)
            assertNull(failed.exceptionType)
            assertNoNewListenerThreads(before)
            assertFalse(occupied.isClosed)
        }
    }

    /**
     * Ein Speicher mit abgelaufenem Zertifikat beendet den Start über die Startprobe, bevor ein Port gebunden
     * ist; das Ereignis nennt den Teil und die Form, nie Pfad oder Kennwort. Ein gültiger Speicher startet.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Eine fehlgeschlagene Startprobe bricht den Start vor dem Binden ab`() = blocking {
        val port = freePort()
        val before = listenerThreads()
        val bad = tlsEnvironment("abgelaufen.p12", port)
        val aborted = assertIs<StartOutcome.Aborted>(start(bad))
        assertTrue(aborted.reason.startsWith("Start gescheitert: TLS-Konfiguration: CERTIFICATE"), aborted.reason)
        assertEquals("CERTIFICATE", (sink.events.single() as TlsStartRejected).part)
        assertNoNewListenerThreads(before)
        assertFree(port)
        val good = tlsEnvironment("gueltig.p12", port)
        assertIs<StartOutcome.Running>(start(good))
    }

    /**
     * Ein Speicher, den das Kennwort der Datei nicht öffnet, beendet den Start mit der Diagnose des Speichers;
     * der Start läuft nicht mit einem Rückfall auf unverschlüsselt weiter.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Ein falsches Kennwort des Speichers bricht den Start ab`() = blocking {
        val environment = tlsEnvironment("gueltig.p12", 0, password = "falsch-$WERTPROBE")
        val aborted = assertIs<StartOutcome.Aborted>(start(environment))
        assertTrue(aborted.reason.startsWith("TLS-Konfiguration: CERTIFICATE"), aborted.reason)
        val rejected = sink.events.single() as TlsStartRejected
        assertEquals("CERTIFICATE", rejected.part)
        assertFalse(rejected.visibleText().contains(WERTPROBE))
        assertFalse(aborted.reason.contains(WERTPROBE))
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
     * Der Start nennt Port und jede Abschwächung: bei `TEST_UNENCRYPTED` und `AUTH_PLAIN_TESTMODE=true` je ein
     * Ereignis der Abschwächung, bei `REQUIRED` mit `pbkdf2` keines. Kein Ereignis nennt Benutzername, Prüfwert, Pfad
     * oder die Bind-Adresse (`SPEC-010`, `TDS-QA-03`).
     *
     * @spec SPEC-010
     */
    @Test
    fun `Der Start-Log nennt jede Abschwaechung und keinen Wert`() = blocking {
        val weak = running(loopbackEnvironment(users()))
        val weakEvents = sink.events.toList()
        assertEquals(
            listOf(ServerStarted::class, EncryptionWeakened::class, PlainTestModeActive::class),
            weakEvents.map { it::class },
        )
        assertEquals(weak.port, (weakEvents.first() as ServerStarted).port)
        sink.events.clear()
        val strong = running(tlsEnvironment("gueltig.p12", 0))
        assertEquals(listOf(strong.port), sink.of<ServerStarted>().map { it.port })
        assertEquals(1, sink.events.size)
        val all = (weakEvents + sink.events).map { it.visibleText() }
        listOf(WERTPROBE, "kennung", "passwort", "users", LOOPBACK, "tdswire-test").forEach { word ->
            assertTrue(all.none { it.contains(word) }, "Ereignis nennt $word: $all")
        }
    }

    /**
     * Das Prüfwort für "keinen Wert" kann in einer Logzeile mit beliebigem Port nicht zufällig vorkommen; ein Port
     * mit der Ziffernfolge `34711` ist der Fall, der ein numerisches Prüfwort fälschlich rot machte.
     *
     * @spec SPEC-010
     */
    @Test
    fun `Das Pruefwort kollidiert nicht mit Zahlen im Log`() {
        assertFalse("gestartet, Port 34711".contains(WERTPROBE))
        assertTrue(WERTPROBE.any { it.isLetter() })
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
        assertIs<StartOutcome.Aborted>(start(environment))
        assertFailsWith<BindException> { ServerSocket(server.port, 1, InetAddress.getByName(LOOPBACK)).close() }
        server.stop()
        assertFree(server.port)
        assertNoNewListenerThreads(before)
        assertIs<ServerStopped>(sink.events.last())
    }
}
