package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Der Einstieg des Integrators von außen (`TDS-RB-04`, `TDS-FA-08`, `TDS-FA-11`): dieses Source-Set übersetzt gegen
 * die Klassen des Hauptmoduls, ohne mit ihm assoziiert zu sein; ein `internal`-Name des Hauptmoduls ist hier nicht
 * übersetzbar. Die Tests starten den Server über `TdsServer.start` im selben Prozess und sprechen Rohbytes über
 * `java.net`; der Rohbytes-Client ist kein Referenzclient, belegt ist die Antwort des Servers auf diese Bytes.
 */
class EmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun start(source: BackendSource, config: ServerConfig = unencrypted()): TdsServer {
        val outcome = runBlocking { TdsServer.start(config, source) }
        return assertIs<StartOutcome.Running>(outcome).server.also { running += it }
    }

    /**
     * Ein In-Memory-Backend des Integrators beantwortet Anfragen, die über den öffentlichen Einstieg gestartet wurden:
     * das Backend sieht den SQL-Text jeder Anfrage, die Antwort richtet sich nach ihm (zwei Zeilen, eine Zeile,
     * keine, ein Fehler), und die Verbindung bleibt nach dem Fehler benutzbar.
     *
     * @spec SPEC-059
     * @spec SPEC-022
     */
    @Test
    fun einInMemoryBackendBeantwortetAnfragenUeberDenEinstieg() {
        val backend = MemoryBackend()
        val server = start(BackendSource.Custom(backend))
        WireClient(server.port).use { client ->
            client.login()
            val two = client.batch("rows 2")
            assertEquals(2L, two.rowCount)
            assertTrue(two.contains("zeile-2") && !two.contains("zeile-3"))
            val one = client.batch("rows 1")
            assertEquals(1L, one.rowCount)
            assertTrue(one.contains("zeile-1") && !one.contains("zeile-2"))
            assertEquals(0L, client.batch("rows 0").rowCount)
            val failed = client.batch("fail")
            assertEquals(ERROR_TOKEN, failed.payload.first().toInt() and BYTE_MASK)
            assertTrue(failed.doneStatus and DONE_ERROR != 0)
            assertEquals(2L, client.batch("rows 2").rowCount)
        }
        assertEquals(listOf("rows 2", "rows 1", "rows 0", "fail", "rows 2"), backend.seen.toList())
    }

    /**
     * Tabellen des Integrators mit eigener Zeilenquelle: die Quelle bekommt die Spalten der Anfrage in der Reihenfolge
     * der Anfrage, liefert nur diese, und schließt mit der Anfrage genau einmal.
     *
     * @spec SPEC-059
     * @spec SPEC-035
     */
    @Test
    fun eineZeilenquelleDesIntegratorsBeantwortetEineAuswahlUndSchliesstMitIhr() {
        val closes = AtomicInteger()
        val server = start(BackendSource.Simulation(listOf(personTable(closes))))
        WireClient(server.port).use { client ->
            client.login()
            val names = client.batch("SELECT Name FROM person")
            assertEquals(2L, names.rowCount)
            assertTrue(names.contains("Ada") && names.contains("Bob") && !names.contains("Cy"))
            assertEquals(1, closes.get())
            val swapped = client.batch("SELECT Name, Id FROM person")
            assertEquals(2L, swapped.rowCount)
            assertEquals(2, closes.get())
        }
    }

    /**
     * Eine ungültige Konfiguration ergibt `StartOutcome.Aborted` mit jedem Fehler nach Schlüssel und Art, keine
     * Ausnahme und keinen gebundenen Port: `REQUIRED` ohne Schlüsselmaterial, `TEST_UNENCRYPTED` mit Material, ein
     * Wert außerhalb des Bereichs, und ein Backend zugleich mit einer Datei der Tabellenbeschreibung.
     *
     * @spec SPEC-059
     * @spec SPEC-010
     */
    @Test
    fun eineUngueltigeKonfigurationBrichtDenStartAbOhnePort() {
        val port = freePort()
        val store = ConfigKeys.TLS_KEYSTORE_FILE
        val password = ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE
        val onLoopback = ListenSettings(LOOPBACK, port)
        val required = abortedBy(ServerConfig(onLoopback, auth = users()), BackendSource.None)
        assertEquals(
            listOf(ConfigIssue(store, IssueKind.MISSING), ConfigIssue(password, IssueKind.MISSING)),
            required.issues,
        )
        val material = TlsKeyMaterial(byteArrayOf(1), PasswordChars(charArrayOf('x')))
        val mixed = ServerConfig(
            onLoopback,
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            tls = material,
            auth = users(),
        )
        assertEquals(
            listOf(ConfigIssue(store, IssueKind.CONFLICT), ConfigIssue(password, IssueKind.CONFLICT)),
            abortedBy(mixed, BackendSource.None).issues,
        )
        val outside = abortedBy(unencrypted(ListenSettings(LOOPBACK, MAX_PORT + 1)), BackendSource.None)
        assertEquals(listOf(ConfigIssue(ConfigKeys.LISTEN_PORT, IssueKind.OUT_OF_RANGE)), outside.issues)
        val withFile = unencrypted(onLoopback, DESCRIPTION.toByteArray())
        val both = abortedBy(withFile, BackendSource.Custom(MemoryBackend()))
        assertEquals(emptyList(), both.issues)
        assertEquals("Backend und Simulations-Backend gleichzeitig konfiguriert", both.reason)
        assertEquals("Konfiguration ungueltig", required.reason)
        ServerSocket(port, 1, InetAddress.getByName(LOOPBACK)).use { assertEquals(port, it.localPort) }
    }

    /**
     * `stop` beendet den Server und ist idempotent: der zweite Aufruf endet ohne Ausnahme, danach nimmt der Port
     * keine Verbindung an, und eine offene Verbindung ist getrennt.
     *
     * @spec SPEC-059
     * @spec SPEC-044
     */
    @Test
    fun stopIstIdempotentUndSchliesstPortUndVerbindungen() {
        val server = start(BackendSource.None)
        val port = server.port
        val open = WireClient(port).also { it.login() }
        runBlocking {
            server.stop()
            server.stop()
        }
        assertTrue(open.closedByServer())
        open.close()
        assertFailsWith<IOException> { Socket(InetAddress.getByName(LOOPBACK), port).close() }
    }

    private fun abortedBy(config: ServerConfig, source: BackendSource): StartOutcome.Aborted =
        assertIs<StartOutcome.Aborted>(runBlocking { TdsServer.start(config, source) })

    private fun personTable(closes: AtomicInteger): SimTable {
        val columns = listOf(SimColumn("Id", SqlType.Int32, nullable = false), SimColumn("Name", SqlType.UnicodeText))
        val people = listOf(mapOf("Id" to 1, "Name" to "Ada"), mapOf("Id" to 2, "Name" to "Bob"))
        return SimTable(
            name = "person",
            columns = columns,
            primaryKey = listOf("Id"),
            rows = { query ->
                var next = 0
                object : RowSource {
                    override suspend fun next(): List<Any?>? =
                        people.getOrNull(next++)?.let { person -> query.columns.map { person[it.name] } }

                    override fun close() {
                        closes.incrementAndGet()
                    }
                }
            },
        )
    }

    private fun users(): AuthSettings {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        return AuthSettings(parsed.list, plainTestMode = true)
    }

    private fun unencrypted(listen: ListenSettings = ListenSettings(LOOPBACK, 0), file: ByteArray? = null) =
        ServerConfig(listen, encryption = EncryptionSetting.TEST_UNENCRYPTED, auth = users(), simulationFile = file)

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }
}

/** Eine Beschreibung der Tabellen im Format der Datei, hier als Bytes der Konfiguration. */
private const val DESCRIPTION = """
schema_format: "1.0"
name: einbettung
version: "1"
tables:
  leer:
    columns:
      Id:
        type: identifier
    primary_key: [Id]
"""

private const val MAX_PORT = 65535

/** Ein Backend im Speicher: der SQL-Text `rows <n>` ergibt `n` Zeilen, `fail` einen Fehler. */
private class MemoryBackend : QueryBackend {
    val seen = CopyOnWriteArrayList<String>()
    private val column = ResultColumn("Text", SqlType.UnicodeText, nullable = false)

    override suspend fun execute(request: QueryRequest): QueryExecution {
        seen += request.sql
        val events: List<QueryEvent> = if (request.sql == "fail") {
            listOf(QueryEvent.Failure("TDS_BACKEND_ERROR", "kein Text fuer den Client"))
        } else {
            val count = request.sql.removePrefix("rows ").toInt()
            listOf<QueryEvent>(QueryEvent.ResultStart(listOf(column))) +
                (1..count).map { QueryEvent.Row(listOf("zeile-$it")) } + QueryEvent.ResultEnd(count.toLong())
        }
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = events.asFlow()

            override suspend fun close() = Unit
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}
