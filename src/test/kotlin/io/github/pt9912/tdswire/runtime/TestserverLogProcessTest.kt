package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val EXIT_START_FAILED = 1
private const val STUCK_LOGINS = 1_500
private const val STUCK_EXIT_SECONDS = 15L
private const val EXIT_SIGTERM = 143
private const val READ_DELAY_MILLIS = 500L
private const val STUCK_START_MILLIS = 30_000L
private const val STUCK_POLL_MILLIS = 50L
private const val CONNECT_MILLIS = 500
private const val LOGIN_MILLIS = 4000

/** Wörter, die in keiner Zeile der Standardausgabe stehen: Passwörter, Prüfwerte und die Kennung der Ablehnungen. */
private val FORBIDDEN = listOf("secret", "geheim-bob", "falsch", "mallory", "pbkdf2", "wertprobe")

/**
 * Die Zeilen des Log-Adapters am Serverprozess (Kind-JVM über das `main` des Testservers, Standardausgabe gelesen):
 * die Inspektion aller Zeilen eines Laufs mit Anmeldung angenommen und abgelehnt, die Diagnose eines abgebrochenen
 * Starts, `LOG_LEVEL` und das Prozessende bei einem Kanal, den niemand liest (`ADR-0020`, `ADR-0022` Entscheidung 1:
 * ein Kindprozess ist ein E2E-Lauf, dieser Test hängt wie `ServerSmokeTest` an der Stage `test`).
 */
internal class TestserverLogProcessTest : TempFilesTest() {
    private fun environment(vararg more: Pair<String, String>): Map<String, String> {
        val users = "alice\t{plain}secret\nbob\t${pbkdf2Value("geheim-bob")}\n"
        return mapOf(
            ConfigKeys.LISTEN_HOST to LOOPBACK,
            ConfigKeys.LISTEN_PORT to "0",
            ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
            ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
            ConfigKeys.AUTH_USERS_FILE to file("users.txt", users),
        ) + more
    }

    /** PRELOGIN ohne Verschlüsselung, dann LOGIN7; `true`, wenn die Antwort ein LOGINACK trägt. */
    private fun login(port: Int, user: String, password: String): Boolean = RawClient(port).use { client ->
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = user
            this.password = password.toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, LOGIN_MILLIS))
        val answer = assertNotNull(client.readMessage())
        answer.payload.any { it.toInt() and BYTE_MASK == LOGINACK }
    }

    private fun jsonLines(server: ServerProcess): List<Map<String, Any>> =
        server.output.filter { it.startsWith("{") }.map {
            assertNotNull(FlatJson.parse(it), "keine gültige JSON-Zeile: $it")
        }

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }

    /**
     * Ein Lauf mit Anmeldung angenommen (zwei Benutzer) und abgelehnt (unbekannter Benutzer, falsches Passwort),
     * dann SIGTERM: jede Zeile der Standardausgabe ist entweder die Zeile des Testservers oder gültiges JSON; die
     * Bereitschaftszeile steht unverändert da und `tdswire: gestoppt` steht zuletzt; weder Passwort noch Prüfwert noch
     * die Kennung einer abgelehnten Anmeldung steht in einer Zeile, und `principal` steht nur im Ereignis der
     * Annahme.
     *
     * @spec SPEC-012
     * @spec SPEC-052
     */
    @Test
    fun `Alle Zeilen eines Laufs sind gueltig und nennen kein Geheimnis`() {
        ServerProcess(environment()).use { server ->
            val port = server.port()
            assertTrue(login(port, "alice", "secret"))
            assertTrue(login(port, "bob", "geheim-bob"))
            assertFalse(login(port, "mallory", "falsch"))
            assertFalse(login(port, "alice", "falsch"))
            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
            assertNotNull(server.awaitLine("${ServerProcess.PREFIX} gestoppt"), server.output.toString())

            val plain = server.output.filterNot { it.startsWith("{") }
            val ready = "${ServerProcess.PREFIX} gestartet, Port $port"
            assertEquals(listOf(ready, "${ServerProcess.PREFIX} gestoppt"), plain)
            assertEquals("${ServerProcess.PREFIX} gestoppt", server.output.last())

            val events = jsonLines(server)
            val names = events.map { it["event"] }
            assertTrue("server_started" in names, names.toString())
            assertEquals("server_stopped", names.last())
            assertEquals(2, names.count { it == "login_accepted" }, names.toString())
            assertEquals(4, names.count { it == "encryption_not_offered" }, names.toString())
            assertEquals(setOf("alice", "bob"), events.mapNotNull { it["principal"] }.toSet())
            assertTrue(events.filter { it.containsKey("principal") }.all { it["event"] == "login_accepted" })
            assertEquals(
                listOf("UNKNOWN_USER", "WRONG_PASSWORD"),
                events.filter { it["event"] == "login_rejected" }.map { it["reason"] },
            )
            assertEquals(port.toLong(), events.first()["port"])
            events.forEach { assertTrue(it.keys.containsAll(listOf("ts", "level", "event", "client_profile"))) }

            val text = server.outputWithoutPrincipal().joinToString("\n")
            FORBIDDEN.forEach { assertFalse(text.contains(it), "Ausgabe nennt $it: $text") }
            assertFalse(text.contains("mallory"))
        }
    }

    /**
     * Ein belegter Port, ein Wert außerhalb des Bereichs und ein ungültiger `LOG_LEVEL` beenden den Start mit Exit-Code
     * 1; die Diagnose steht vor dem Prozessende in der Ausgabe: beim belegten Port die Zeile `bind_failed` mit Adresse
     * und Port neben der Zeile des Testservers, beim Wert außerhalb des Bereichs die Zeile `config_invalid` mit
     * Schlüssel und Art, bei `LOG_LEVEL` die Zeile des Testservers. Kein Wert steht in der Ausgabe.
     *
     * @spec SPEC-043
     * @spec SPEC-052
     */
    @Test
    fun `Ein abgebrochener Start schreibt seine Diagnose vor dem Prozessende`() {
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            val port = occupied.localPort
            ServerProcess(environment(ConfigKeys.LISTEN_PORT to "$port")).use { server ->
                assertEquals(EXIT_START_FAILED, server.awaitExit())
                val bind = jsonLines(server).single()
                assertEquals("bind_failed", bind["event"])
                assertEquals(LOOPBACK, bind["address"])
                assertEquals(port.toLong(), bind["port"])
                val reason = server.output.any { it.startsWith(ServerProcess.PREFIX) && it.contains("Port belegt") }
                assertTrue(reason, server.output.toString())
            }
        }
        ServerProcess(environment(ConfigKeys.LOGIN_TIMEOUT to "0")).use { server ->
            assertEquals(EXIT_START_FAILED, server.awaitExit())
            val invalid = jsonLines(server).single()
            assertEquals("config_invalid", invalid["event"])
            assertEquals("LOGIN_TIMEOUT", invalid["key"])
            assertEquals("OUT_OF_RANGE", invalid["issue_kind"])
            assertTrue(server.output.any { it.endsWith("LOGIN_TIMEOUT: OUT_OF_RANGE") }, server.output.toString())
        }
        ServerProcess(environment(LOG_LEVEL to "geheim-wertprobe")).use { server ->
            assertEquals(EXIT_START_FAILED, server.awaitExit())
            val expected = "${ServerProcess.PREFIX} Konfiguration ungueltig: LOG_LEVEL: INVALID_VALUE"
            assertEquals(listOf(expected), server.output.toList())
        }
    }

    /**
     * `LOG_LEVEL` filtert am Prozess: bei `ERROR` steht keine JSON-Zeile da (die Bereitschaftszeile und `gestoppt`
     * sind keine Ereignisse und stehen), bei `WARN` stehen die zwei Abschwächungen der Sicherheit des Testmodus, je
     * Anmeldung ohne angebotene Verschlüsselung `encryption_not_offered` und die Ablehnung, bei `DEBUG` jedes Ereignis
     * des Laufs.
     *
     * @spec SPEC-052
     */
    @Test
    fun `LOG_LEVEL filtert am Prozess`() {
        val weakened = listOf("security_weakened", "security_weakened")
        val notOffered = listOf("encryption_not_offered", "encryption_not_offered")
        val expected = mapOf(
            "ERROR" to emptyList<String>(),
            "WARN" to (weakened + notOffered + "login_rejected").sorted(),
            "DEBUG" to (
                weakened + notOffered + listOf(
                    "server_started",
                    "simulation_backend_ready",
                    "login_accepted",
                    "login_rejected",
                    "server_stopped",
                )
                ).sorted(),
        )
        for ((level, names) in expected) {
            val description = file("sim-$level.yaml", PERSON_YAML)
            ServerProcess(environment(LOG_LEVEL to level, SIMULATION_FILE to description)).use { server ->
                val port = server.port()
                assertTrue(login(port, "alice", "secret"))
                assertFalse(login(port, "mallory", "falsch"))
                assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
                assertNotNull(server.awaitLine("${ServerProcess.PREFIX} gestoppt"), server.output.toString())
                assertEquals(names, jsonLines(server).map { it["event"] as String }.sorted(), "LOG_LEVEL $level")
            }
        }
    }

    /**
     * Niemand liest die Standardausgabe: der Kanal des Kindes füllt sich, der Schreiber hängt, die Warteschlange läuft
     * über. Die Anmeldungen werden trotzdem beantwortet (der Anfragepfad blockiert nicht). Nach SIGTERM beginnt der
     * Test nach einer halben Sekunde zu lesen, also innerhalb der Frist von 2 Sekunden: der Prozess endet mit dem
     * Exit-Code des Signals, `tdswire: gestoppt` steht als letzte Zeile, und kein Ereignis geht unbemerkt verloren:
     * die Zeilen des Schreibers und die Zahlen in `dropped` ergeben zusammen die Zahl der Ereignisse (je Anmeldung
     * zwei, `encryption_not_offered` und `login_accepted`, dazu `server_stopped`).
     *
     * @spec SPEC-052
     */
    @Test
    fun `Ein Kanal, den niemand liest, haelt Anmeldungen nicht auf und Close leert, was die Frist erlaubt`() {
        val port = freePort()
        ServerProcess(environment(ConfigKeys.LISTEN_PORT to "$port"), drain = false).use { server ->
            awaitListening(server, port)
            repeat(STUCK_LOGINS) { assertTrue(login(port, "alice", "secret"), "Anmeldung $it") }
            server.signalTerm()
            Thread.sleep(READ_DELAY_MILLIS)
            server.startDraining()
            assertEquals(EXIT_SIGTERM, server.awaitExit(STUCK_EXIT_SECONDS), "der Prozess endet nach SIGTERM")

            val last = server.output.takeLast(3)
            assertEquals("${ServerProcess.PREFIX} gestoppt", last.last(), last.toString())
            val events = jsonLines(server)
            val synchronous = 3
            val dropped = events.sumOf { (it["dropped"] as? Long) ?: 0L }
            val written = events.count { it["event"] != "log_dropped" } - synchronous
            assertEquals(2L * STUCK_LOGINS + 1, written + dropped, "geschrieben $written, verworfen $dropped")
            assertTrue(dropped > 0, "die Warteschlange ist übergelaufen")
        }
    }

    /**
     * Niemand liest die Standardausgabe, auch nicht nach SIGTERM: der Schreiber hängt für immer. Die Anmeldungen
     * werden beantwortet, und der Prozess endet nach SIGTERM trotzdem innerhalb der Frist des Adapters (2 s und zweimal
     * 250 ms) plus Anlauf, mit dem Exit-Code des Signals; der Hook wartet nicht auf den Kanal.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Ein Kanal, der nie gelesen wird, haelt das Prozessende nicht auf`() {
        val port = freePort()
        ServerProcess(environment(ConfigKeys.LISTEN_PORT to "$port"), drain = false).use { server ->
            awaitListening(server, port)
            repeat(STUCK_LOGINS) { assertTrue(login(port, "alice", "secret"), "Anmeldung $it") }
            assertEquals(EXIT_SIGTERM, server.terminate(STUCK_EXIT_SECONDS), "der Prozess endet nach SIGTERM")
        }
    }

    private fun awaitListening(server: ServerProcess, port: Int) {
        val deadline = System.nanoTime() + STUCK_START_MILLIS * NANOS_PER_MILLI
        var open = false
        while (!open && System.nanoTime() < deadline && server.alive()) {
            open = runCatching {
                Socket().use { it.connect(InetSocketAddress(LOOPBACK, port), CONNECT_MILLIS) }
            }.isSuccess
            if (!open) Thread.sleep(STUCK_POLL_MILLIS)
        }
        assertTrue(open, "der Server nimmt Verbindungen an")
    }

    private companion object {
        const val LOG_LEVEL = "LOG_LEVEL"
        const val SIMULATION_FILE = "SIMULATION_DESCRIPTION_FILE"
        val PERSON_YAML = """
            schema_format: "1.0"
            name: demo
            version: "1"
            tables:
              person:
                columns:
                  id:
                    type: identifier
                primary_key: [id]
        """.trimIndent() + "\n"
        const val BYTE_MASK = 0xFF
        const val LOGINACK = 0xAD
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
