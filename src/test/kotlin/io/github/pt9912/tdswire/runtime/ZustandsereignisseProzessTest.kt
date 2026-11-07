package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EXIT_START_FAILED = 1
private const val LOGIN_MILLIS = 4000
private const val TABULAR = 4
private const val WAIT_MILLIS = 20_000L
private const val POLL_MILLIS = 20L
private const val CONNECT_MILLIS = 500

/**
 * Startdiagnose und Zustandsereignisse am Serverprozess (`TDS-FA-09`, `ADR-0020`): Kind-JVM über das `main` des
 * Testservers, Standardausgabe gelesen, Rohbytes-Client (kein Referenzclient). Der Test ist ein Prozesstest in der Form
 * von `ServerSmokeTest` und läuft in der Stage `smoke` (`make smoke`, Stufe 1 von `make e2e`) und in der Stage `test`.
 * Gelesen werden die Zeilen der Standardausgabe: die Zeilen des Testservers (`tdswire: …`) und die JSON-Zeilen des
 * Log-Adapters; die Ereignisse der Bibliothek als Objekte sieht `ZustandsereignisseTest`.
 */
internal class ZustandsereignisseProzessTest : TempFilesTest() {
    private val directory get() = checkNotNull(System.getProperty("tdswire.testkeys"))

    private fun environment(vararg more: Pair<String, String>): Map<String, String> = mapOf(
        ConfigKeys.LISTEN_HOST to LOOPBACK,
        ConfigKeys.LISTEN_PORT to "0",
        ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
        ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
        ConfigKeys.AUTH_USERS_FILE to file("users.txt", "alice\t{plain}secret\n"),
    ) + more

    /** Eine Benutzerliste ohne `plain`-Eintrag, gültig auch ohne den Testmodus. */
    private fun hashedUsers(): String = file("users-hashed.txt", "alice\t${pbkdf2Value("secret")}\n")

    private fun json(server: ServerProcess): List<Map<String, Any>> =
        server.output.filter { it.startsWith("{") }.map { assertNotNull(FlatJson.parse(it), "keine JSON-Zeile: $it") }

    private fun plain(server: ServerProcess): List<String> =
        server.output.filter { it.startsWith(ServerProcess.PREFIX) }

    private fun events(server: ServerProcess): List<String> = json(server).map { it["event"] as String }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + WAIT_MILLIS * NANOS_PER_MILLI
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)
        assertTrue(condition(), "nicht eingetreten: $what")
    }

    private fun listening(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(LOOPBACK, port), CONNECT_MILLIS) }
        true
    } catch (refused: IOException) {
        assertNotNull(refused.message ?: refused.javaClass.name)
        false
    }

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }

    private fun login(port: Int): RawClient = RawClient(port).also { client ->
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, LOGIN_MILLIS))
        assertEquals(TABULAR, assertNotNull(client.readMessage()).type)
    }

    /**
     * Ein belegter Port beendet den Start mit Exit-Code 1. Die Ausgabe nennt die Adresse und den Port in der Zeile des
     * Testservers (`Port belegt: <Adresse>:<Port>`) und im Ereignis `bind_failed`; sie meldet weder `server_started`
     * noch die Bereitschaft, und nach dem Ende des Prozesses ist der Port (von uns belegt) der einzige, der
     * antwortet: der Prozess hat nichts gebunden.
     *
     * @spec SPEC-043
     * @spec SPEC-005
     */
    @Test
    fun `Ein belegter Port beendet den Start mit Adresse und Port in der Diagnose`() {
        val occupied = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))
        val port = occupied.localPort
        ServerProcess(environment(ConfigKeys.LISTEN_PORT to "$port")).use { server ->
            assertEquals(EXIT_START_FAILED, server.awaitExit())
            val text = plain(server).single()
            assertTrue(text.contains("Port belegt: $LOOPBACK:$port"), text)
            val bind = json(server).single()
            assertEquals("bind_failed", bind["event"])
            assertEquals(LOOPBACK, bind["address"])
            assertEquals(port.toLong(), bind["port"])
            assertEquals("ERROR", bind["level"])
            assertFalse(server.output.any { it.contains("gestartet") || it.contains("server_started") })
        }
        occupied.close()
        assertFalse(listening(port), "nach dem Ende des Prozesses antwortet nichts auf dem Port")
    }

    /**
     * Ein Schlüsselspeicher mit abgelaufenem Zertifikat und einer, der kein PKCS#12-Speicher ist, beenden den Start mit
     * Exit-Code 1; das Ereignis `tls_start_rejected` nennt den Teil und die Form, die Ausgabe nennt weder Pfad noch
     * Inhalt noch Passwort (`SPEC-043`). Ein Speicher, der nicht lesbar ist (Datei fehlt), endet vor dem Start der
     * Bibliothek mit Schlüssel und Art in der Zeile des Testservers, ohne den Pfad.
     *
     * @spec SPEC-043
     * @spec SPEC-051
     */
    @Test
    fun `Ein unbrauchbarer Schluesselspeicher nennt den Teil und weder Pfad noch Inhalt`() {
        val secretName = "geheimer-pfad-$WERTPROBE"
        val broken = file("$secretName.p12", "kein-speicher-$WERTPROBE")
        val password = file("passwort-$WERTPROBE.txt", "pw-$WERTPROBE\n")
        val missing = "$directory/gibt-es-nicht-$WERTPROBE.p12"
        val tls = arrayOf(
            ConfigKeys.ENCRYPTION_POLICY to "REQUIRED",
            ConfigKeys.AUTH_PLAIN_TESTMODE to "false",
            ConfigKeys.AUTH_USERS_FILE to hashedUsers(),
        )
        val cases = listOf(
            Triple("$directory/abgelaufen.p12", file("kennwort.txt", "tdswire-test\n"), "Kette jetzt nicht gueltig"),
            Triple(broken, password, "Speicher nicht lesbar"),
        )
        cases.forEach { (store, passwordFile, form) ->
            val values = tls + (ConfigKeys.TLS_KEYSTORE_FILE to store) +
                (ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to passwordFile)
            ServerProcess(environment(*values)).use { server ->
                assertEquals(EXIT_START_FAILED, server.awaitExit(), server.output.toString())
                val rejected = json(server).single()
                assertEquals("tls_start_rejected", rejected["event"])
                assertEquals("CERTIFICATE", rejected["part"])
                assertTrue((rejected["form"] as String).startsWith(form), rejected.toString())
                assertFalse(server.output.any { it.contains("gestartet") || it.contains("server_started") })
                val text = server.output.joinToString("\n")
                listOf(WERTPROBE, directory, "abgelaufen", "tdswire-test").forEach {
                    assertFalse(text.contains(it), "Ausgabe nennt $it: $text")
                }
            }
        }
        val unreadable = tls + (ConfigKeys.TLS_KEYSTORE_FILE to missing) +
            (ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to password)
        ServerProcess(environment(*unreadable)).use { server ->
            assertEquals(EXIT_START_FAILED, server.awaitExit())
            assertEquals(
                listOf("${ServerProcess.PREFIX} Konfiguration ungueltig: TLS_KEYSTORE_FILE: UNREADABLE"),
                server.output.toList(),
            )
        }
    }

    /**
     * Eine ungültige oder unsichere Konfiguration wird abgewiesen, ohne dass der Prozess startet: `REQUIRED` (die
     * Voreinstellung) ohne Schlüsselspeicher, `TEST_UNENCRYPTED` mit Schlüsselspeicher, eine unbekannte Richtlinie und
     * ein Schalter für das Verfahren `plain`, der nicht genau `true` oder `false` ist, enden mit Exit-Code 1 und der
     * Zeile mit Schlüssel und Art. Die Ausgabe nennt den Wert nicht und meldet weder Bereitschaft noch
     * `server_started`; eine Abschwächung ist nur mit ausdrücklicher Konfiguration aktiv.
     *
     * @spec SPEC-010
     * @spec SPEC-051
     */
    @Test
    fun `Eine unsichere Konfiguration wird abgewiesen und startet nichts`() {
        val store = ConfigKeys.TLS_KEYSTORE_FILE
        val word = "unbekannt-$WERTPROBE"
        val cases = listOf(
            environment(ConfigKeys.AUTH_USERS_FILE to hashedUsers())
                .minus(ConfigKeys.ENCRYPTION_POLICY).minus(ConfigKeys.AUTH_PLAIN_TESTMODE) to
                listOf("$store: MISSING", "${ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE}: MISSING"),
            environment(store to "$directory/gueltig.p12") to listOf("$store: CONFLICT"),
            environment(ConfigKeys.ENCRYPTION_POLICY to word) to
                listOf("${ConfigKeys.ENCRYPTION_POLICY}: INVALID_VALUE"),
            environment(ConfigKeys.AUTH_PLAIN_TESTMODE to "TRUE-$WERTPROBE") to
                listOf("${ConfigKeys.AUTH_PLAIN_TESTMODE}: INVALID_VALUE"),
        )
        cases.forEach { (values, expected) ->
            ServerProcess(values).use { server ->
                assertEquals(EXIT_START_FAILED, server.awaitExit(), server.output.toString())
                val lines = plain(server)
                expected.forEach { part ->
                    assertTrue(lines.any { it.endsWith(part) }, "$part fehlt: ${server.output}")
                }
                assertTrue(lines.none { it.contains("gestartet") }, server.output.toString())
                assertEquals(emptyList(), events(server))
                assertFalse(server.output.any { it.contains(WERTPROBE) || it.contains(directory) }, "${server.output}")
            }
        }
    }

    /**
     * Ein Lauf am Prozess meldet die Zustandsereignisse als JSON-Zeilen mit demselben `event` wie die Art der
     * Bibliothek, in der Reihenfolge des Betriebs: wirksame Konfiguration, Start mit Adresse und Port, die zwei
     * Abschwächungen, je Verbindung geöffnet und geschlossen mit Grund, Anmeldung, Anfragefehler mit `error_code`,
     * dann nach SIGTERM Beginn des Beendens, Ende der ruhenden Verbindung mit Grund `SHUTDOWN` und Ende des Servers;
     * `tdswire: gestoppt` steht zuletzt. Weder Passwort noch Pfad der Benutzerliste stehen in einer Zeile.
     *
     * @spec SPEC-005
     * @spec SPEC-010
     * @spec SPEC-044
     * @spec SPEC-052
     */
    @Test
    fun `Der Prozess meldet Start, Verbindungen, Anfragefehler und Beenden als Ereignisse`() {
        ServerProcess(environment()).use { server ->
            val port = server.port()
            login(port).use { first ->
                first.send(handMessage(BATCH_TYPE, BatchVectors.payload("EXEC sp_unprepare 99"), LOGIN_MILLIS))
                assertEquals(TABULAR, assertNotNull(first.readMessage()).type)
            }
            await("erste Verbindung geschlossen") { "connection_closed" in events(server) }
            val second = login(port)
            await("zweite Anmeldung") { events(server).count { it == "login_accepted" } == 2 }
            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
            assertNotNull(server.awaitLine("${ServerProcess.PREFIX} gestoppt"), server.output.toString())
            second.close()

            assertEquals(
                listOf(
                    "config_effective",
                    "server_started",
                    "security_weakened",
                    "security_weakened",
                    "connection_opened",
                    "encryption_not_offered",
                    "login_accepted",
                    "request_failed",
                    "connection_closed",
                    "connection_opened",
                    "encryption_not_offered",
                    "login_accepted",
                    "server_stopping",
                    "connection_closed",
                    "server_stopped",
                ),
                events(server),
            )
            assertEquals("${ServerProcess.PREFIX} gestoppt", server.output.last())
            val lines = json(server)
            val effective = lines.first { it["event"] == "config_effective" }
            assertEquals(15L, effective["login_timeout"])
            assertEquals("TEST_UNENCRYPTED", effective["encryption_policy"])
            assertEquals("UNSET", effective["tls_keystore_file"])
            val started = lines.first { it["event"] == "server_started" }
            assertEquals(LOOPBACK, started["address"])
            assertEquals(port.toLong(), started["port"])
            val failed = lines.first { it["event"] == "request_failed" }
            assertEquals("TDS_SIM_UNKNOWN_PREPARED_HANDLE", failed["error_code"])
            val closed = lines.filter { it["event"] == "connection_closed" }
            assertEquals(listOf("HANDLER_RETURNED", "SHUTDOWN"), closed.map { it["reason"] })
            assertEquals(listOf(1L, 2L), closed.map { it["connection_id"] })
            lines.forEach { assertTrue(it.keys.containsAll(listOf("ts", "level", "event", "client_profile"))) }
            assertTrue(lines.filter { it.containsKey("principal") }.all { it["event"] == "login_accepted" })
            val text = server.outputWithoutPrincipal().joinToString("\n")
            listOf("secret", "users.txt").forEach { assertFalse(text.contains(it), "Ausgabe nennt $it: $text") }
        }
    }

    /**
     * Ein Prozess, der nicht startet, hält keinen Listener: ein Start mit ungültiger Konfiguration auf einem freien
     * Port lässt den Port frei, ein Verbindungsversuch scheitert, und `awaitExit` liefert den Exit-Code. Das Ende des
     * Prozesses belegt, dass kein Thread des Servers weiterläuft (die Threads des Listeners sind Daemon-Threads, der
     * Prozess endet über `exitProcess`).
     *
     * @spec SPEC-043
     */
    @Test
    fun `Nach einem abgewiesenen Start laeuft weder Listener noch Prozess`() {
        val port = freePort()
        ServerProcess(environment(ConfigKeys.LISTEN_PORT to "$port", ConfigKeys.LOGIN_TIMEOUT to "0")).use { server ->
            assertEquals(EXIT_START_FAILED, server.awaitExit())
            assertEquals(listOf("config_invalid"), events(server))
            assertNull(server.output.firstOrNull { it.contains("gestartet") })
        }
        assertFalse(listening(port))
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
