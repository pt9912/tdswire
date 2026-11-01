package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4

/**
 * Die Verdrahtung des Simulations-Backends im Prozess (`ARC-008`, `TDS-FA-11.a`, `ADR-0012`): die Tabellen des
 * Integrators bilden den Katalog, ein Namenskonflikt bricht den Start vor dem Binden ab, und der Server läuft auch
 * ohne Tabellen. Die Datei der Beschreibung liest der Testserver, seine Tests prüfen sie. Antworten sind Tokens, vom
 * Rohbytes-Client (kein Referenzclient) gelesen.
 */
internal class SimulationWiringTest : RuntimeTestBase() {
    private fun environment(vararg more: Pair<String, String>) =
        loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"), *more)

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
     * Happy: mehrere Tabellen des Integrators (Kotlin-Schnittstelle) mit Zeilenquelle stehen im selben Katalog und
     * werden beantwortet.
     * @spec SPEC-035
     */
    @Test
    fun `Tabellen des Integrators stehen im selben Katalog`() = blocking {
        val kunde = integratorTable(
            personColumns(),
            mutableListOf("Id"),
            listOf(listOf(1, "Ada"), listOf(2, "Bob")),
        )
        val person = integratorTable(personColumns(), mutableListOf("Id"), emptyList()).copy(name = "person")
        val wiring = Wiring(log = log, requests = RequestSource(simulationTables = listOf(kunde, person)))
        val server = running(environment(), wiring)
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
     * Negative: ein Name, der in den Tabellen des Integrators zweimal vorkommt (auch in anderer Schreibweise), und
     * ein Backend zugleich mit einer Simulation brechen den Start ab; nichts startet.
     * @spec SPEC-035
     */
    @Test
    fun `Widersprueche zwischen Tabellen und Backend brechen den Start ab`() = blocking {
        val kunde = integratorTable(personColumns(), mutableListOf("Id"), emptyList())
        val twice = Wiring(
            log = log,
            requests = RequestSource(simulationTables = listOf(kunde, kunde.copy(name = "KUNDE"))),
        )
        val duplicate = assertIs<StartOutcome.Aborted>(start(environment(), twice))
        assertEquals("Tabellen ungueltig: ein Name kommt doppelt vor", log.lines.last())
        assertEquals(log.lines.last(), duplicate.reason)

        val backend = ScriptedBackend.ofList()
        val both = assertIs<StartOutcome.Aborted>(
            start(environment(), Wiring(log = log, requests = RequestSource(backend, listOf(kunde)))),
        )
        assertEquals("Backend und Simulations-Backend gleichzeitig konfiguriert", log.lines.last())
        assertEquals(log.lines.last(), both.reason)
    }

    /**
     * Negative: eine Tabelle, deren Liste der Integrator nach der Übergabe so ändert, dass die Prüfung beim Kopieren
     * scheitert, wirft ihre eigene `IllegalArgumentException` aus dem Start; sie wird nicht als doppelter Name
     * gemeldet (die Fangklausel umschließt nur den Katalog).
     * @spec SPEC-035
     */
    @Test
    fun `eine ungueltige Tabelle wird nicht als doppelter Name gemeldet`() = blocking {
        val key = mutableListOf("Id")
        val kunde = integratorTable(personColumns(), key, emptyList())
        key.add("Nope")
        val error = assertFailsWith<IllegalArgumentException> { start(environment(), wiringWith(kunde)) }
        assertTrue("Nope" in error.message.orEmpty(), error.message)
        assertTrue(log.lines.none { it.contains("doppelt") }, log.lines.toString())
    }
}
