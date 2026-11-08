package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val LOOPBACK = "127.0.0.1"

private val PERSON_FILE = """
    schema_format: "1.0"
    name: demo
    version: "1"
    tables:
      person:
        columns:
          id:
            type: identifier
          name:
            type: text
            max_length: 50
        primary_key: [id]
""".trimIndent() + "\n"

/**
 * Der Start des Testservers im Prozess der Tests: Lader, Datei der Beschreibung und `TdsServer.start` der Bibliothek
 * über ihre öffentliche Fläche (`SPEC-052`, `SPEC-059`). Die Zeilen, die der Testserver schreibt, gehen an einen
 * Sammler statt auf die Standardausgabe. Den Weg über die Prozessgrenze (Kind-JVM, Rohbytes-Client) prüft der Smoke
 * in `src/test`.
 */
internal class TestserverStartTest : TempFilesTest() {
    private val lines = ArrayList<String>()
    private val started = ConcurrentLinkedQueue<StartOutcome.Running>()

    private fun environment(vararg more: Pair<String, String>) = testModeEnvironment(
        file("users.txt", "alice\t{plain}secret\n"),
        EnvKeys.LISTEN_HOST to LOOPBACK,
        EnvKeys.LISTEN_PORT to "0",
        *more,
    )

    private fun start(
        environment: Map<String, String>,
        choose: (List<SimTable>) -> BackendSource? = { BackendSource.Simulation(it) },
    ): StartOutcome = runBlocking {
        lines.clear()
        startTestserver(environment, choose, lines::add).also { if (it is StartOutcome.Running) started += it }
    }

    private fun table(name: String) = SimTable(
        name = name,
        columns = listOf(SimColumn("Id", SqlType.Int32, nullable = false)),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
    )

    private fun assertFree(port: Int) {
        ServerSocket(port, 1, InetAddress.getByName(LOOPBACK)).use { assertEquals(port, it.localPort) }
    }

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }

    @AfterTest
    fun stopStartedServers() {
        runBlocking { generateSequence { started.poll() }.forEach { it.server.stop() } }
    }

    /**
     * Die Bereitschaftszeile ist `tdswire: gestartet, Port <port>` mit dem Port des laufenden Servers, nicht dem
     * angefragten Port 0; sie ist die einzige Zeile, und der Port ist gebunden. Die Skripte der E2E-Läufe warten
     * auf diese Zeichenfolge.
     *
     * @spec SPEC-005
     * @spec SPEC-052
     */
    @Test
    fun `Die Bereitschaftszeile nennt den Port des laufenden Servers`() {
        val running = assertIs<StartOutcome.Running>(start(environment()))
        assertNotEquals(0, running.server.port)
        assertEquals(listOf("tdswire: gestartet, Port ${running.server.port}"), lines)
        java.net.Socket(LOOPBACK, running.server.port).close()
    }

    /**
     * Fehler des Laders und Bereichsfehler der Bibliothek brechen den Start ab, je Fehler eine Zeile mit Schlüssel
     * und Art, kein Wert; die Bereitschaftszeile fehlt.
     *
     * @spec SPEC-043
     * @spec SPEC-005
     */
    @Test
    fun `Konfigurationsfehler stehen je Fehler in einer Zeile ohne Wert`() {
        val form = assertIs<StartOutcome.Aborted>(start(environment(EnvKeys.LOGIN_TIMEOUT to "geheim-wert-4711")))
        assertEquals(listOf("tdswire: Konfiguration ungueltig: LOGIN_TIMEOUT: INVALID_VALUE"), lines)
        assertEquals(listOf(ConfigIssue(EnvKeys.LOGIN_TIMEOUT, IssueKind.INVALID_VALUE)), form.issues)

        val outside = environment(EnvKeys.LOGIN_TIMEOUT to "0", EnvKeys.LISTEN_PORT to "70000")
        assertIs<StartOutcome.Aborted>(start(outside))
        assertEquals(
            listOf(
                "tdswire: Konfiguration ungueltig: LISTEN_PORT: OUT_OF_RANGE",
                "tdswire: Konfiguration ungueltig: LOGIN_TIMEOUT: OUT_OF_RANGE",
            ),
            lines,
        )
    }

    /**
     * Treten ein Formfehler (Lader) und ein Bereichsfehler (Start der Bibliothek) zugleich auf, meldet der Testserver
     * nur den Formfehler; der Bereichsfehler erscheint erst, wenn die Form stimmt (Setzung).
     *
     * @spec SPEC-043
     */
    @Test
    fun `Formfehler verdecken Bereichsfehler bis die Form stimmt`() {
        val mixed = environment(EnvKeys.LOGIN_TIMEOUT to "abc", EnvKeys.LISTEN_PORT to "70000")
        assertIs<StartOutcome.Aborted>(start(mixed))
        assertEquals(listOf("tdswire: Konfiguration ungueltig: LOGIN_TIMEOUT: INVALID_VALUE"), lines)
        val fixed = environment(EnvKeys.LOGIN_TIMEOUT to "15", EnvKeys.LISTEN_PORT to "70000")
        assertIs<StartOutcome.Aborted>(start(fixed))
        assertEquals(listOf("tdswire: Konfiguration ungueltig: LISTEN_PORT: OUT_OF_RANGE"), lines)
    }

    /**
     * Eine Benutzerliste mit Fehler bricht den Start ab: die Zeile nennt Schlüssel, Fehlerart und Zeile, weder
     * Benutzername noch Prüfwert noch Pfad, und der Port wird nicht gebunden.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Eine fehlerhafte Benutzerliste bricht den Start ab`() {
        val port = freePort()
        val path = file("kennung-4711.txt", "kennung-4711\t{pbkdf2-sha256}passwort-4711\n")
        assertIs<StartOutcome.Aborted>(
            start(testModeEnvironment(path, EnvKeys.LISTEN_HOST to LOOPBACK, EnvKeys.LISTEN_PORT to "$port")),
        )
        val expected = "tdswire: Konfiguration ungueltig: AUTH_USERS_FILE: USER_LIST_INVALID (FIELD_COUNT, Zeile 1)"
        assertEquals(listOf(expected), lines)
        assertFree(port)
    }

    /**
     * Ein Ladefehler der Datei der Beschreibung bricht den Start ab, bevor ein Port gebunden wird: die Zeile nennt
     * Pfad im Dokument, Zeile und Spalte und weder Wert noch Dateipfad. Geprüft sind ein unbekanntes Feld, ein Typ
     * ohne Entsprechung, ein Syntaxfehler, ein Anker und eine Datei, die kein UTF-8 ist. Kontrollfall: die gültige
     * Datei startet.
     *
     * @spec SPEC-035
     */
    @Test
    fun `Ein Ladefehler der Beschreibung bricht den Start ab bevor ein Port gebunden wird`() {
        val broken = mapOf(
            "unbekanntes Feld" to PERSON_FILE.replace("max_length: 50", "unknown_field: geheim-wert-4711"),
            "Typ ohne Entsprechung" to PERSON_FILE.replace("type: text", "type: uuid"),
            "Syntaxfehler" to "tables: [geheim-inhalt-4711\n",
            "Anker" to PERSON_FILE.replace("columns:", "columns: &geheim-anker"),
        )
        for ((label, text) in broken) {
            val port = freePort()
            val environment = environment(
                EnvKeys.SIMULATION_DESCRIPTION_FILE to file("sim.yaml", text),
                EnvKeys.LISTEN_PORT to "$port",
            )
            assertIs<StartOutcome.Aborted>(start(environment), label)
            val line = lines.single()
            assertTrue(line.startsWith("tdswire: Beschreibung der Tabellen ungueltig: "), "$label: $line")
            assertTrue(line.contains("Zeile ") && line.contains("Spalte "), "$label: $line")
            val leaked = listOf("4711", "sim.yaml", "geheim-anker").filter { it in line }
            assertEquals(emptyList(), leaked, "$label: $line")
            assertFree(port)
        }
        val latin = file("latin.yaml", PERSON_FILE.toByteArray() + byteArrayOf(0xE4.toByte()))
        assertIs<StartOutcome.Aborted>(start(environment(EnvKeys.SIMULATION_DESCRIPTION_FILE to latin)))
        assertTrue(lines.single().contains("kein gültiges UTF-8"), lines.toString())
        val valid = environment(EnvKeys.SIMULATION_DESCRIPTION_FILE to file("ok.yaml", PERSON_FILE))
        assertIs<StartOutcome.Running>(start(valid))
    }

    /**
     * Eine Tabelle ohne `primary_key` (so schreibt `schema reverse` von d-migrate eine Tabelle ohne Schlüssel zurück,
     * die `CREATE TABLE` am Server anlegt) und eine mit leerer Liste starten den Testserver; die Tabellen tragen einen
     * leeren Schlüssel. Wächter: verlangte der Leser den Schlüssel wieder, brächen beide Starts ab.
     *
     * @spec SPEC-035
     */
    @Test
    fun `eine Datei mit Tabellen ohne Schluessel startet den Testserver`() {
        val withoutKey = PERSON_FILE.replace("    primary_key: [id]\n", "")
        assertTrue("primary_key" !in withoutKey)
        for ((label, text) in mapOf("ohne Feld" to withoutKey, "leere Liste" to PERSON_FILE.replace("[id]", "[]"))) {
            val keys = ArrayList<List<List<String>>>()
            val outcome = start(environment(EnvKeys.SIMULATION_DESCRIPTION_FILE to file("$label.yaml", text))) {
                keys += it.map { table -> table.primaryKey }
                BackendSource.Simulation(it)
            }
            assertIs<StartOutcome.Running>(outcome, "$label: $lines")
            assertEquals(listOf(listOf(emptyList<String>())), keys, label)
        }
    }

    /**
     * Ein Name, der in der Datei und in den Tabellen des Testservers vorkommt (auch in anderer Schreibweise), bricht
     * den Start der Bibliothek ab; eine Quelle, die `null` liefert (ein fertiges Backend neben der Datei), bricht den
     * Start des Testservers ab. Beide Male bleibt der Port frei.
     *
     * @spec SPEC-035
     */
    @Test
    fun `Widersprueche zwischen Datei und Tabellen des Testservers brechen den Start ab`() {
        val port = freePort()
        val environment = environment(
            EnvKeys.SIMULATION_DESCRIPTION_FILE to file("sim.yaml", PERSON_FILE),
            EnvKeys.LISTEN_PORT to "$port",
        )
        val duplicate = assertIs<StartOutcome.Aborted>(
            start(environment) { BackendSource.Simulation(it + table("PERSON")) },
        )
        assertEquals(listOf("tdswire: Tabellen ungueltig: ein Name kommt doppelt vor"), lines)
        assertEquals("Tabellen ungueltig: ein Name kommt doppelt vor", duplicate.reason)
        assertFree(port)

        assertIs<StartOutcome.Aborted>(start(environment) { null })
        assertEquals(listOf("tdswire: Backend und Simulations-Backend gleichzeitig konfiguriert"), lines)
        assertFree(port)

        val extra = start(environment) { BackendSource.Simulation(it + table("kunde")) }
        val joined = assertIs<StartOutcome.Running>(extra)
        assertEquals(listOf("tdswire: gestartet, Port ${joined.server.port}"), lines)
    }
}
