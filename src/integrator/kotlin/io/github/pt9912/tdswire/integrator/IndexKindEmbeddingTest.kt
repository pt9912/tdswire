package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val CREATE_INDEX_COMMAND = 0x00C8
private const val DROP_INDEX_COMMAND = 0x00C9
private const val REPORTED_ROWS = 5L

/**
 * Der Abschluss von `CREATE INDEX` und `DROP INDEX` (`SPEC-031`, `SPEC-059`, `SPEC-036`) von außen: die Arten
 * `CREATE_INDEX` und `DROP_INDEX` als öffentliche Fläche, die Antworten auf den Rohbytes. Die Kennungen sind am echten
 * SQL Server gemessen (Messprotokoll `docs/messungen/drop-index-d-migrate-2026-11-11.md`).
 */
class IndexKindEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun start(source: BackendSource): TdsServer {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        val config = ServerConfig(
            ListenSettings(host = LOOPBACK, port = 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = AuthSettings(parsed.list, plainTestMode = true),
        )
        val outcome = runBlocking { TdsServer.start(config, source) }
        return assertIs<StartOutcome.Running>(outcome).server.also { running += it }
    }

    private fun structureBackend(): SimulationBackend {
        val tables = CopyOnWriteArrayList<SimTable>()
        return SimulationBackend({ SimCatalog(tables.toList()) }) { shape ->
            tables.removeAll { it.schema == shape.schema && it.name == shape.name }
            tables += shape
        }
    }

    private fun doneOf(answer: Answer) = Triple(answer.doneStatus, answer.command, answer.rowCount)

    /**
     * `CREATE INDEX` und `DROP INDEX` schließen beim Simulations-Backend mit DONE ohne `COUNT`, der Zeilenzahl 0 und
     * der Befehlskennung `0x00c8` bzw. `0x00c9` (am echten Server gemessen: `CREATE INDEX` an einem gewöhnlichen,
     * einem eindeutigen und einem Index mit zwei Spalten; `DROP INDEX` bei Treffer, bei `IF EXISTS` ohne Treffer und an
     * einer unbekannten Tabelle mit `IF EXISTS`). Wächter: das Bit `COUNT`, die Kennung 0 (Art `UNKNOWN`), vertauschte
     * Kennungen.
     *
     * @spec SPEC-059
     * @spec SPEC-031
     * @spec SPEC-036
     */
    @Test
    fun dasSimulationsBackendSchliesstIndexAnweisungenOhneCountMitDerKennungDerArt() {
        val server = start(BackendSource.Custom(structureBackend()))
        WireClient(server.port).use { client ->
            client.login()
            client.batch("CREATE TABLE teile (Id BIGINT NOT NULL, Menge INT, PRIMARY KEY (Id))")
            val plain = client.batch("CREATE INDEX ix_menge ON teile (Menge)")
            val unique = client.batch("CREATE UNIQUE INDEX ux_menge ON teile (Id, Menge)")
            val dropped = client.batch("DROP INDEX ix_menge ON teile")
            val none = client.batch("DROP INDEX IF EXISTS ix_menge ON teile")
            val unknownTable = client.batch("DROP INDEX IF EXISTS x ON gibtesnicht")
            for (answer in listOf(plain, unique)) {
                assertEquals(Triple(0, CREATE_INDEX_COMMAND, 0L), doneOf(answer))
            }
            for (answer in listOf(dropped, none, unknownTable)) {
                assertEquals(Triple(0, DROP_INDEX_COMMAND, 0L), doneOf(answer))
            }
        }
    }

    /**
     * Ein eigenes Backend, das die Arten `CREATE_INDEX` und `DROP_INDEX` mit einer Zahl ungleich 0 meldet, bekommt
     * dieselbe Antwort: DONE ohne `COUNT`, die Zahl wird nicht durchgereicht. Wächter: eine durchgereichte Zahl, das
     * Bit `COUNT`, eine Kennung 0.
     *
     * @spec SPEC-059
     * @spec SPEC-031
     */
    @Test
    fun einEigenesBackendMeldetDieIndexArtenUndBekommtDenAbschlussOhneCount() {
        val server = start(BackendSource.Custom(IndexKindBackend()))
        WireClient(server.port).use { client ->
            client.login()
            assertEquals(Triple(0, CREATE_INDEX_COMMAND, 0L), doneOf(client.batch("CREATE_INDEX ix ON t (a)")))
            assertEquals(Triple(0, DROP_INDEX_COMMAND, 0L), doneOf(client.batch("DROP_INDEX ix ON t")))
        }
    }
}

/** Ein Backend, das `CREATE_INDEX` und `DROP_INDEX` im Text mit der Art und der Zahl [REPORTED_ROWS] meldet. */
private class IndexKindBackend : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution {
        val kind = when (request.sql.substringBefore(' ')) {
            "CREATE_INDEX" -> StatementKind.CREATE_INDEX
            else -> StatementKind.DROP_INDEX
        }
        val reported: List<QueryEvent> = listOf(QueryEvent.UpdateCount(REPORTED_ROWS, kind))
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = reported.asFlow()

            override suspend fun close() = Unit
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}
