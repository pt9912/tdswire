package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.assertNoNewListenerThreads
import io.github.pt9912.tdswire.transport.listener.listenerThreads
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4

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
 * Die Verdrahtung des Simulations-Backends im Prozess (`ARC-008`, `TDS-FA-11.a`, `ADR-0012`): Datei der
 * Konfiguration und Tabellen des Integrators bilden den Katalog, ein Ladefehler bricht den Start vor dem Binden ab,
 * und der Server läuft auch ohne beides. Antworten sind Tokens, vom Rohbytes-Client (kein Referenzclient) gelesen.
 */
internal class SimulationWiringTest : RuntimeTestBase() {
    private fun environment(vararg more: Pair<String, String>) =
        loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"), *more)

    private fun withDescription(text: String) =
        environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to file("sim.yaml", text))

    private fun login(client: RawClient) {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    private fun ask(client: RawClient, sql: String): List<Token> {
        client.send(handMessage(BATCH_TYPE, BatchVectors.payload(sql), 4000))
        val reply = assertNotNull(client.readMessage(), sql)
        assertEquals(TABULAR_TYPE, reply.type, sql)
        return TokenDecoder(reply.payload).decodeAll()
    }

    private fun wiringWith(table: SimTable) =
        Wiring(log = log, requests = RequestSource(simulationTables = listOf(table)))

    private fun personColumns() =
        mutableListOf(SimColumn("Id", SqlType.Int32, nullable = false), SimColumn("Name", SqlType.UnicodeText))

    private fun assertFree(port: Int) {
        ServerSocket(port, 1, InetAddress.getByName(LOOPBACK)).use { assertEquals(port, it.localPort) }
    }

    private fun integratorTable(columns: MutableList<SimColumn>, key: MutableList<String>, rows: List<List<Any?>>) =
        SimTable(
            name = "kunde",
            columns = columns,
            primaryKey = key,
            rows = {
                var next = 0
                RowSource { rows.getOrNull(next++) }
            },
        )

    /**
     * Happy: der Server lädt die Datei der Konfiguration, meldet Tabellen und beantwortet eine Auswahl der
     * beschriebenen Tabelle über den Socket mit Metadaten und einem leeren Ergebnis (eine geladene Tabelle hat eine
     * leere Zeilenquelle); ein Einfügen endet mit einem Fehler (keine Callbacks).
     * @spec SPEC-035
     */
    @Test
    fun `eine Tabelle aus der Datei liefert Metadaten und ein leeres Ergebnis`() = blocking {
        val server = running(withDescription(PERSON_FILE))
        RawClient(server.port).use { client ->
            login(client)

            val select = ask(client, "SELECT id, name FROM person")
            val meta = assertIs<ColMetadataToken>(select.first())
            assertEquals(listOf("id", "name"), meta.columns.map { it.name })
            assertTrue(select.none { it is RowToken })
            assertEquals(0L, assertIs<DoneToken>(select.last()).rowCount)

            val insert = ask(client, "INSERT INTO person (id) VALUES (1)")
            assertTrue(insert.any { it is ErrorToken }, insert.toString())
        }
        assertTrue(log.lines.contains("Simulations-Backend: 1 Tabellen"), log.lines.toString())
    }

    /**
     * Happy: Tabellen des Integrators (Kotlin-Schnittstelle) mit Zeilenquelle werden zusammen mit denen aus der
     * Datei beantwortet.
     * @spec SPEC-035
     */
    @Test
    fun `Tabellen des Integrators und der Datei stehen im selben Katalog`() = blocking {
        val kunde = integratorTable(
            personColumns(),
            mutableListOf("Id"),
            listOf(listOf(1, "Ada"), listOf(2, "Bob")),
        )
        val server = running(withDescription(PERSON_FILE), wiringWith(kunde))
        RawClient(server.port).use { client ->
            login(client)

            val rows = ask(client, "SELECT Name FROM kunde WHERE Id = 2")
            assertEquals(1, rows.count { it is RowToken })
            val empty = ask(client, "SELECT id FROM person")
            assertTrue(empty.none { it is RowToken })
        }
        assertTrue(log.lines.contains("Simulations-Backend: 2 Tabellen"), log.lines.toString())
    }

    /**
     * Boundary: der Katalog hält eigene Kopien der Listen des Integrators; entfernt er danach eine Spalte aus seiner
     * Liste, liefert `SELECT *` weiter alle Spalten, wie sie bei der Übergabe standen.
     * @spec SPEC-035
     */
    @Test
    fun `der Katalog behaelt die Listen des Integrators als Kopie`() = blocking {
        val columns = personColumns()
        val key = mutableListOf("Id")
        val kunde = integratorTable(columns, key, emptyList())
        val server = running(environment(), wiringWith(kunde))

        columns.removeAt(1)
        key.clear()

        RawClient(server.port).use { client ->
            login(client)
            val meta = assertIs<ColMetadataToken>(ask(client, "SELECT * FROM kunde").first())
            assertEquals(listOf("Id", "Name"), meta.columns.map { it.name })
        }
    }

    /**
     * Happy und Negative: ohne Datei und ohne Tabellen startet der Server, meldet kein Simulations-Backend und
     * beantwortet den Login; eine Anfrage bleibt unbeantwortet (kein Backend), die Verbindung schließt.
     * @spec SPEC-035
     */
    @Test
    fun `ohne Simulations-Backend beantwortet der Server den Login`() = blocking {
        val server = running(environment())
        RawClient(server.port).use { client ->
            login(client)
            client.send(handMessage(BATCH_TYPE, BatchVectors.payload("SELECT 1"), 4000))
            assertTrue(client.closedByServerWithoutBytes())
        }
        assertTrue(log.lines.none { it.startsWith("Simulations-Backend") }, log.lines.toString())
    }

    /**
     * Negative: ein Ladefehler der Datei bricht den Start ab, bevor ein Port gebunden wird: kein Thread des Servers
     * läuft, der gewählte Port bleibt frei, das Log nennt Pfad im Dokument, Zeile und Spalte und weder Wert noch
     * Dateipfad noch den Text der Bibliothek. Geprüft sind ein unbekanntes Feld, ein Typ ohne Entsprechung, ein
     * Syntaxfehler und ein Anker. Kontrollfall: die gültige Datei startet.
     * @spec SPEC-035
     */
    @Test
    fun `ein Ladefehler bricht den Start ab bevor ein Port gebunden wird`() = blocking {
        val broken = mapOf(
            "unbekanntes Feld" to PERSON_FILE.replace("max_length: 50", "unknown_field: geheim-wert-$WERTPROBE"),
            "Typ ohne Entsprechung" to PERSON_FILE.replace("type: text", "type: uuid"),
            "Syntaxfehler" to "tables: [geheim-inhalt-$WERTPROBE\n",
            "Anker" to PERSON_FILE.replace("columns:", "columns: &geheim-anker"),
        )
        for ((label, text) in broken) {
            val before = listenerThreads()
            val port = freePort()
            log.lines.clear()
            val boot = start(
                withDescription(text) + (ConfigKeys.LISTEN_PORT to port.toString()),
            )

            assertEquals(BootResult.Aborted, boot, label)
            val line = log.lines.single()
            assertTrue(line.startsWith("Beschreibung der Tabellen ungueltig: "), "$label: $line")
            assertTrue(line.contains("Zeile ") && line.contains("Spalte "), "$label: $line")
            val leaked = listOf(WERTPROBE, "sim.yaml", "geheim-anker").filter { it in line }
            assertEquals(emptyList(), leaked, "$label: $line")
            assertNoNewListenerThreads(before)
            assertFree(port)
        }
        assertIs<BootResult.Running>(start(withDescription(PERSON_FILE)))
    }

    /**
     * Negative: die Konfiguration weist die Datei vor dem Parsen ab, wenn sie größer als 1 MiB ist (ein Byte mehr
     * genügt), unlesbar ist oder der Wert leer ist; das Log nennt Schlüssel und Art, nie den Pfad. Kontrollfall:
     * eine Datei von genau 1 MiB (gültiges YAML mit Kommentar) startet. Ist die Datei nicht UTF-8, bricht der
     * Start mit dem Ladefehler ab.
     * @spec SPEC-035
     */
    @Test
    fun `die Groessengrenze greift vor dem Parsen`() = blocking {
        val limit = 1_048_576
        val head = PERSON_FILE.toByteArray()
        fun padded(size: Int) = head + "#".repeat(size - head.size - 1).toByteArray() + "\n".toByteArray()

        val exact = environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to file("exact.yaml", padded(limit)))
        assertIs<BootResult.Running>(start(exact))

        val over = environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to file("over.yaml", padded(limit + 1)))
        assertEquals(BootResult.Aborted, start(over))
        assertEquals("Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: TOO_LARGE", log.lines.last())

        val missing = environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to "/nicht/vorhanden/sim.yaml")
        assertEquals(BootResult.Aborted, start(missing))
        assertEquals("Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: UNREADABLE", log.lines.last())

        val blank = environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to " ")
        assertEquals(BootResult.Aborted, start(blank))
        assertEquals("Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: INVALID_VALUE", log.lines.last())

        val latinBytes = head + byteArrayOf(0xE4.toByte())
        val latin = environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to file("latin.yaml", latinBytes))
        assertEquals(BootResult.Aborted, start(latin))
        assertTrue(log.lines.last().contains("kein gültiges UTF-8"), log.lines.last())
    }

    /**
     * Negative: ein Pfad, der keine reguläre Datei ist, endet mit `UNREADABLE`, ohne gelesen zu werden: ein
     * Verzeichnis, ein Gerät (`/dev/zero` lieferte unbegrenzt Bytes und endete sonst als `TOO_LARGE`; eine FIFO ohne
     * Schreiber blockierte das Öffnen) und ein Pfad, den die Plattform nicht bildet (NUL-Zeichen).
     * @spec SPEC-035
     */
    @Test
    fun `ein Pfad ohne reguelaere Datei ist unlesbar`() = blocking {
        val directory = java.io.File(file("x.yaml", PERSON_FILE)).parent
        for (path in listOf(directory, "/dev/zero", "/dev/null", "sim\u0000.yaml")) {
            assertEquals(BootResult.Aborted, start(environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to path)), path)
            assertEquals("Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: UNREADABLE", log.lines.last(), path)
        }
    }

    /**
     * Negative: eine reguläre Datei, deren Lesen mit einem Ein-/Ausgabefehler endet, ist `UNREADABLE` und kein
     * ungefangener Fehler beim Start (der `IOException`-Zweig). Die Datei ist `/proc/self/mem`: für Java regulär,
     * das Lesen ab Stelle 0 endet mit „Input/output error“ (gemessen unter Linux, auch als Root; Rechte-Entzug taugt
     * nicht, die Tests laufen als Root). Der Test prüft die Voraussetzung selbst: ohne diesen Fehler schlägt er fehl.
     * @spec SPEC-035
     */
    @Test
    fun `eine regulaere Datei mit Lesefehler ist unlesbar`() = blocking {
        val path = "/proc/self/mem"
        assertTrue(java.nio.file.Files.isRegularFile(java.nio.file.Paths.get(path)))
        assertFailsWith<java.io.IOException> { java.io.File(path).inputStream().use { it.readNBytes(1) } }

        assertEquals(BootResult.Aborted, start(environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to path)))
        assertEquals("Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: UNREADABLE", log.lines.last())
    }

    /**
     * Happy und Negative: ein Symlink auf eine reguläre Datei lädt wie die Datei; ein Symlink auf ein Verzeichnis
     * ist `UNREADABLE`. Eine FIFO ist nicht getestet (ein Öffnen ohne Schreiber bliebe hängen); für sie gilt nur,
     * dass `Files.isRegularFile` sie nicht als reguläre Datei zählt.
     * @spec SPEC-035
     */
    @Test
    fun `ein Symlink auf eine Datei laedt und einer auf ein Verzeichnis ist unlesbar`() = blocking {
        val target = java.io.File(file("target.yaml", PERSON_FILE))
        val toFile = target.parentFile.toPath().resolve("link-file.yaml")
        java.nio.file.Files.createSymbolicLink(toFile, target.toPath())
        val toDirectory = target.parentFile.toPath().resolve("link-dir")
        java.nio.file.Files.createSymbolicLink(toDirectory, target.parentFile.toPath())

        running(environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to toFile.toString()))
        assertTrue(log.lines.contains("Simulations-Backend: 1 Tabellen"), log.lines.toString())

        val env = environment(ConfigKeys.SIMULATION_DESCRIPTION_FILE to toDirectory.toString())
        assertEquals(BootResult.Aborted, start(env))
        assertEquals("Konfiguration ungueltig: SIMULATION_DESCRIPTION_FILE: UNREADABLE", log.lines.last())
    }

    /**
     * Negative: ein Name, der in der Datei und in den Tabellen des Integrators vorkommt (auch in anderer
     * Schreibweise), und ein Backend zugleich mit einer Simulation brechen den Start ab; nichts startet.
     * @spec SPEC-035
     */
    @Test
    fun `Widersprueche zwischen Datei Schnittstelle und Backend brechen den Start ab`() = blocking {
        val person = integratorTable(personColumns(), mutableListOf("Id"), emptyList()).copy(name = "PERSON")
        assertEquals(
            BootResult.Aborted,
            start(withDescription(PERSON_FILE), wiringWith(person)),
        )
        assertEquals("Tabellen ungueltig: ein Name kommt doppelt vor", log.lines.last())

        val backend = ScriptedBackend.ofList()
        assertEquals(
            BootResult.Aborted,
            start(withDescription(PERSON_FILE), Wiring(log = log, requests = RequestSource(backend))),
        )
        assertEquals("Backend und Simulations-Backend gleichzeitig konfiguriert", log.lines.last())
    }
}
