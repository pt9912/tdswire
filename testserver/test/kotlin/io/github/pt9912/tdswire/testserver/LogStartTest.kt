package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.telemetry.EventLevel
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val LOOPBACK = "127.0.0.1"
private val SECRET_WORDS = listOf("secret", "falsch", "geheim", "mallory", "4711")

/**
 * Der Start des Testservers im Prozess der Tests mit dem [LogAdapter]: was vor und nach dem Start in den Kanal
 * kommt, und der Abbruch bei ungültigem `LOG_LEVEL`. Den Weg über die Prozessgrenze prüft `TestserverLogProcessTest`
 * in `src/test`.
 */
internal class LogStartTest : TempFilesTest() {
    private val plain = ArrayList<String>()
    private val started = ArrayList<StartOutcome.Running>()

    private fun environment(vararg more: Pair<String, String>) = testModeEnvironment(
        file("users.txt", "alice\t{plain}secret\n"),
        EnvKeys.LISTEN_HOST to LOOPBACK,
        EnvKeys.LISTEN_PORT to "0",
        *more,
    )

    private fun start(adapter: LogAdapter, environment: Map<String, String>): StartOutcome = runBlocking {
        plain.clear()
        startTestserver(environment, { BackendSource.Simulation(it) }, plain::add, adapter)
            .also { if (it is StartOutcome.Running) started += it }
    }

    @AfterTest
    fun stopAll() {
        runBlocking { started.forEach { it.server.stop() } }
    }

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }

    /**
     * Ein ungültiger `LOG_LEVEL` bricht den Start ab, bevor ein Port gebunden wird: eine Zeile mit Schlüssel und Art,
     * kein Wert, kein Ereignis im Kanal; ein zugleich ungültiger Wert anderer Schlüssel erscheint nicht (Setzung).
     *
     * @spec SPEC-052
     * @spec SPEC-043
     */
    @Test
    fun `Ein ungueltiger LOG_LEVEL bricht den Start ab`() {
        val port = freePort()
        for (value in listOf("geheim-wert-4711", "info", "")) {
            val channel = RecordingChannel()
            val adapter = LogAdapter(EventLevel.INFO, channel)
            val mixed = environment(
                LogLevelSetting.KEY to value,
                EnvKeys.LISTEN_PORT to "$port",
                EnvKeys.LOGIN_TIMEOUT to "abc",
            )
            val outcome = assertIs<StartOutcome.Aborted>(start(adapter, mixed), value)
            assertEquals(listOf("tdswire: Konfiguration ungueltig: LOG_LEVEL: INVALID_VALUE"), plain, value)
            assertEquals(1, outcome.issues.size)
            assertEquals(emptyList(), channel.lines.toList())
            ServerSocket(port, 1, InetAddress.getByName(LOOPBACK)).use { assertEquals(port, it.localPort) }
        }
    }

    /**
     * Ereignisse vor dem erfolgreichen Start stehen synchron im Kanal, im Thread des Aufrufers: ein Bindefehler
     * (belegter Port) als Zeile `bind_failed` mit Adresse und Port, dazu der Grund des Abbruchs als Zeile des
     * Testservers; ein Konfigurationsfehler der Bibliothek als Zeile `config_invalid` mit Schlüssel und Art, kein
     * Wert. Der Schreiber läuft nicht.
     *
     * @spec SPEC-043
     * @spec SPEC-052
     */
    @Test
    fun `Ereignisse vor dem Start stehen synchron im Kanal`() {
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            val channel = RecordingChannel()
            val adapter = LogAdapter(EventLevel.INFO, channel)
            assertIs<StartOutcome.Aborted>(start(adapter, environment(EnvKeys.LISTEN_PORT to "${occupied.localPort}")))
            val bind = parseFlat(channel.lines.single())
            assertEquals("bind_failed", bind["event"])
            assertEquals("ERROR", bind["level"])
            assertEquals(LOOPBACK, bind["address"])
            assertEquals(occupied.localPort.toLong(), bind["port"])
            assertTrue(channel.writers.all { it.startsWith("Test worker") }, channel.writers.toString())
            assertTrue(plain.single().contains("Port belegt"), plain.toString())
        }

        val channel = RecordingChannel()
        val adapter = LogAdapter(EventLevel.INFO, channel)
        assertIs<StartOutcome.Aborted>(start(adapter, environment(EnvKeys.LOGIN_TIMEOUT to "0")))
        val parsed = parseFlat(channel.lines.single())
        assertEquals("config_invalid", parsed["event"])
        assertEquals("LOGIN_TIMEOUT", parsed["key"])
        assertEquals("OUT_OF_RANGE", parsed["issue_kind"])
    }

    /**
     * Ein Lauf mit Anmeldung angenommen und abgelehnt: die Zeilen `config_effective`, `server_started` und die der zwei
     * Abschwächungen des Testmodus (`security_weakened`) stehen beim Start synchron da, die Zeilen der Anmeldungen
     * und der Verbindungen kommen vom Schreiber (dazu je Anmeldung `encryption_not_offered`: die Sonde bietet keine
     * Verschlüsselung an), `server_stopping` beginnt das Beenden und `server_stopped` kommt als letzte Zeile; die
     * Kennung steht nur im Feld `principal` der Annahme, weder Passwort noch Kennung der Ablehnung stehen in einer
     * Zeile.
     *
     * @spec SPEC-012
     * @spec SPEC-052
     */
    @Test
    fun `Ein Lauf schreibt Start, Annahme, Ablehnung und Ende, und kein Geheimnis`() {
        val channel = RecordingChannel()
        val adapter = LogAdapter(EventLevel.INFO, channel)
        val running = assertIs<StartOutcome.Running>(start(adapter, environment()))
        val before = channel.lines.map(::parseFlat)
        val expected = listOf("config_effective", "security_weakened", "security_weakened", "server_started")
        assertEquals(expected, before.map { it["event"] as String }.sorted())
        assertEquals(running.server.port.toLong(), before.single { it["event"] == "server_started" }["port"])

        adapter.startWriter()
        LoginProbe(running.server.port).use { assertTrue(it.login("alice", "secret")) }
        LoginProbe(running.server.port).use { assertFalse(it.login("mallory", "falsch-4711")) }
        channel.awaitLines(before.size + 2)
        runBlocking { running.server.stop() }
        adapter.close()

        val parsed = channel.lines.map(::parseFlat)
        val events = parsed.map { it["event"] as String }
        assertEquals("server_stopped", events.last())
        val middle = events.drop(before.size).dropLast(1)
        val states = setOf("connection_opened", "connection_closed", "server_stopping")
        assertEquals(
            setOf("encryption_not_offered", "login_accepted", "login_rejected") + states,
            middle.toSet(),
            "$events",
        )
        assertEquals("alice", parsed.single { it["event"] == "login_accepted" }["principal"])
        assertEquals("UNKNOWN_USER", parsed.single { it["event"] == "login_rejected" }["reason"])
        assertEquals(1, parsed.count { it.containsKey("principal") })
        val text = channel.lines.joinToString("\n")
        SECRET_WORDS.forEach { assertFalse(text.contains(it), "Zeile nennt $it: $text") }
    }
}
