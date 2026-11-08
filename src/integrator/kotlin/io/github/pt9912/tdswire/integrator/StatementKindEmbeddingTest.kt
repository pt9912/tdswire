package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val DONE_COUNT = 0x10
private const val INSERT_COMMAND = 0x00C3
private const val DELETE_COMMAND = 0x00C4
private const val UPDATE_COMMAND = 0x00C5
private const val ROWS = 3L

/**
 * Die Anweisungsart im Abschluss einer Antwort (`SPEC-031`, `SPEC-059`, ADR-0042) von außen: `StatementKind` und
 * `UpdateCount.kind` als öffentliche Fläche. Das Source-Set sieht nur die öffentliche Fläche; die Antworten stehen auf
 * den Rohbytes. Die Kennungen sind am echten SQL Server gemessen (Messprotokoll W-6).
 */
class StatementKindEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun start(source: BackendSource): TdsServer {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        val config = ServerConfig(
            ListenSettings(LOOPBACK, 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = AuthSettings(parsed.list, plainTestMode = true),
        )
        val outcome = runBlocking { TdsServer.start(config, source) }
        return assertIs<StartOutcome.Running>(outcome).server.also { running += it }
    }

    /** Das abschließende DONE (Status, Befehlskennung, Zeilenzahl) der Antwort auf [sql]. */
    private fun doneOf(server: TdsServer, sql: String): Triple<Int, Int, Long> = WireClient(server.port).use { client ->
        client.login()
        val answer = client.batch(sql)
        Triple(answer.doneStatus, answer.command, answer.rowCount)
    }

    /** Eine Tabelle `person` mit den Callbacks für `INSERT`, `UPDATE` und `DELETE`, die je [ROWS] melden. */
    private fun person() = SimTable(
        name = "person",
        columns = listOf(SimColumn("Id", SqlType.Int32, nullable = false)),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
        onInsert = { ROWS },
        onUpdate = { _, _ -> ROWS },
        onDelete = { ROWS },
    )

    private val statements = mapOf(
        "INSERT INTO person (Id) VALUES (1)" to INSERT_COMMAND,
        "UPDATE person SET Id = 2 WHERE Id = 1" to UPDATE_COMMAND,
        "DELETE FROM person WHERE Id = 2" to DELETE_COMMAND,
    )

    /**
     * Ein eigenes Backend, das die Art meldet (`UpdateCount(n, kind)`), bekommt dieselbe Antwort wie das
     * Simulations-Backend: DONE mit `COUNT`, der Zeilenzahl und der Befehlskennung der Art (INSERT `0x00c3`, UPDATE
     * `0x00c5`, DELETE `0x00c4`). Wächter: eine Kennung der Art, die das Backend nicht erreicht (alle 0), vertauschte
     * Kennungen.
     *
     * @spec SPEC-059
     * @spec SPEC-031
     */
    @Test
    fun einEigenesBackendMeldetDieArtUndBekommtDieAntwortDesSimulationsBackends() {
        val simulation = start(BackendSource.Simulation(listOf(person())))
        val custom = start(BackendSource.Custom(KindBackend()))
        for ((sql, command) in statements) {
            val expected = Triple(DONE_COUNT, command, ROWS)
            assertEquals(expected, doneOf(simulation, sql), "Simulation: $sql")
            assertEquals(expected, doneOf(custom, sql), "eigenes Backend: $sql")
        }
    }

    /**
     * Ein Backend, das die Art nicht meldet (`UpdateCount(n)` wie vor der Fläche `kind`), bekommt DONE mit `COUNT`,
     * der Zeilenzahl und der Befehlskennung 0, ebenso ein Backend mit `StatementKind.UNKNOWN`. Wächter: eine Kennung
     * ungleich 0 bei fehlender Art (ein Standardwert im Konstruktor), ein Verlust des Zählers.
     *
     * @spec SPEC-059
     * @spec SPEC-031
     */
    @Test
    fun einBackendOhneArtBleibtBeiDerKennungNull() {
        val old = start(BackendSource.Custom(FixedCountBackend(QueryEvent.UpdateCount(ROWS))))
        val reported = QueryEvent.UpdateCount(ROWS, StatementKind.UNKNOWN)
        val unknown = start(BackendSource.Custom(FixedCountBackend(reported)))
        assertEquals(Triple(DONE_COUNT, 0, ROWS), doneOf(old, "INSERT INTO person (Id) VALUES (1)"))
        assertEquals(Triple(DONE_COUNT, 0, ROWS), doneOf(unknown, "INSERT INTO person (Id) VALUES (1)"))
    }

    /**
     * Die Fläche: die vier Konstanten von `StatementKind`, das Feld `kind` mit dem Standardwert `UNKNOWN`, und für Java
     * der Konstruktor mit einem Argument (`@JvmOverloads`: `new UpdateCount(n)`). Wächter: ein fehlendes Mitglied, ein
     * anderer Standardwert, ein Konstruktor ohne Überladung für Java.
     *
     * @spec SPEC-059
     */
    @Test
    fun dieFlaecheNenntDieKonstantenDasFeldUndDenKonstruktorFuerJava() {
        assertEquals(
            listOf("UNKNOWN", "INSERT", "DELETE", "UPDATE"),
            StatementKind.entries.map { it.name },
        )
        assertEquals(StatementKind.UNKNOWN, QueryEvent.UpdateCount(1).kind)
        assertEquals(StatementKind.UPDATE, QueryEvent.UpdateCount(1, StatementKind.UPDATE).kind)
        val oneArgument = QueryEvent.UpdateCount::class.java.getConstructor(Long::class.javaPrimitiveType)
        assertEquals(QueryEvent.UpdateCount(ROWS), oneArgument.newInstance(ROWS))
    }
}

/** Ein Backend, das zu `INSERT`, `UPDATE` und `DELETE` im Text den Änderungszähler mit der Art meldet. */
private class KindBackend : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution {
        val kind = when (request.sql.substringBefore(' ')) {
            "INSERT" -> StatementKind.INSERT
            "UPDATE" -> StatementKind.UPDATE
            "DELETE" -> StatementKind.DELETE
            else -> StatementKind.UNKNOWN
        }
        return FixedExecution(QueryEvent.UpdateCount(ROWS, kind))
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

/** Ein Backend, das jede Anfrage mit demselben [event] beantwortet. */
private class FixedCountBackend(private val event: QueryEvent) : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution = FixedExecution(event)

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

private class FixedExecution(event: QueryEvent) : QueryExecution {
    override val events: Flow<QueryEvent> = flowOf(event)

    override suspend fun close() = Unit
}
