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
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend
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
private const val CREATE_TABLE_COMMAND = 0x00C6
private const val ALTER_TABLE_COMMAND = 0x00D8
private const val IMPLICIT_ON_COMMAND = 0x00B9
private const val COMMIT_IF_OPEN_COMMAND = 0x00C0
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

    /** Das Simulations-Backend mit Katalog und Strukturänderungs-Callback, der die Tabelle einträgt. */
    private fun structureBackend(): SimulationBackend {
        val tables = CopyOnWriteArrayList<SimTable>()
        return SimulationBackend({ SimCatalog(tables.toList()) }) { schema, name, columns, key ->
            tables.removeAll { it.schema == schema && it.name == name }
            tables += SimTable(schema, name, columns, key, rows = { RowSource { null } })
        }
    }

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
     * `CREATE TABLE` und `ALTER TABLE … ADD` schließen mit DONE ohne `COUNT`, der Zeilenzahl 0 und der Befehlskennung
     * `0x00c6` bzw. `0x00d8` (Messprotokoll M-20), beim Simulations-Backend und bei einem eigenen Backend, das die Art
     * meldet (auch mit einer Zahl ungleich 0: die Bibliothek ignoriert sie). Wächter: das Bit `COUNT`, eine Kennung 0,
     * eine durchgereichte Zahl.
     *
     * @spec SPEC-059
     * @spec SPEC-031
     * @spec SPEC-035
     */
    @Test
    fun strukturaenderungenSchliessenOhneCountMitDerKennungDerArt() {
        val simulation = start(BackendSource.Custom(structureBackend()))
        val custom = start(BackendSource.Custom(KindBackend()))
        val create = "CREATE TABLE geraete (Seriennummer BIGINT NOT NULL, PRIMARY KEY (Seriennummer))"
        val alter = "ALTER TABLE geraete ADD Name NVARCHAR(40)"
        assertEquals(Triple(0, CREATE_TABLE_COMMAND, 0L), doneOf(simulation, create))
        assertEquals(Triple(0, CREATE_TABLE_COMMAND, 0L), doneOf(custom, create))
        WireClient(simulation.port).use { client ->
            client.login()
            client.batch(create.replace("geraete", "teile"))
            val answer = client.batch(alter.replace("geraete", "teile"))
            assertEquals(
                Triple(0, ALTER_TABLE_COMMAND, 0L),
                Triple(answer.doneStatus, answer.command, answer.rowCount),
            )
        }
        assertEquals(Triple(0, ALTER_TABLE_COMMAND, 0L), doneOf(custom, alter))
    }

    /**
     * Die Transaktionstexte der Form C auf den Rohbytes (Messprotokoll M-20): `set implicit_transactions on` schließt
     * mit `0x00b9`, `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN` mit `DONE MORE` `0x00ba` und `DONE`
     * `0x00c0`, alle ohne `COUNT`; ein eigenes Backend, das dieselben Arten meldet, bekommt dieselben Bytes. Wächter:
     * eine Kennung 0, `COUNT`, ein fehlendes `MORE`.
     *
     * @spec SPEC-059
     * @spec SPEC-031
     * @spec SPEC-036
     */
    @Test
    fun dieTransaktionstexteSchliessenOhneCountMitDenKennungenDerArten() {
        val on = "set implicit_transactions on "
        val off = "set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN "
        val onBytes = "FD 00 00 B9 00 00 00 00 00 00 00 00 00"
        val offBytes = "FD 01 00 BA 00 00 00 00 00 00 00 00 00  FD 00 00 C0 00 00 00 00 00 00 00 00 00"
        val custom = start(BackendSource.Custom(TransactionBackend()))
        for (server in listOf(start(BackendSource.Simulation(listOf(person()))), custom)) {
            WireClient(server.port).use { client ->
                client.login()
                assertEquals(hex(onBytes).toList(), client.batch(on).payload.toList())
                assertEquals(hex(offBytes).toList(), client.batch(off).payload.toList())
            }
        }
        assertEquals(Triple(0, IMPLICIT_ON_COMMAND, 0L), doneOf(custom, on))
        assertEquals(Triple(0, COMMIT_IF_OPEN_COMMAND, 0L), doneOf(custom, off))
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
     * Die Fläche: die neun Konstanten von `StatementKind`, das Feld `kind` mit dem Standardwert `UNKNOWN`, und für Java
     * der Konstruktor mit einem Argument (`@JvmOverloads`: `new UpdateCount(n)`). Wächter: ein fehlendes Mitglied, ein
     * anderer Standardwert, ein Konstruktor ohne Überladung für Java.
     *
     * @spec SPEC-059
     */
    @Test
    fun dieFlaecheNenntDieKonstantenDasFeldUndDenKonstruktorFuerJava() {
        assertEquals(
            listOf(
                "UNKNOWN",
                "INSERT",
                "DELETE",
                "UPDATE",
                "CREATE_TABLE",
                "ALTER_TABLE",
                "IMPLICIT_TRANSACTIONS_ON",
                "IMPLICIT_TRANSACTIONS_OFF",
                "COMMIT_IF_OPEN",
            ),
            StatementKind.entries.map { it.name },
        )
        assertEquals(StatementKind.UNKNOWN, QueryEvent.UpdateCount(1).kind)
        assertEquals(StatementKind.UPDATE, QueryEvent.UpdateCount(1, StatementKind.UPDATE).kind)
        val oneArgument = QueryEvent.UpdateCount::class.java.getConstructor(Long::class.javaPrimitiveType)
        assertEquals(QueryEvent.UpdateCount(ROWS), oneArgument.newInstance(ROWS))
    }
}

/** Ein Backend, das zu `INSERT`, `UPDATE`, `DELETE`, `CREATE` und `ALTER` im Text [ROWS] mit der Art meldet. */
private class KindBackend : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution {
        val kind = when (request.sql.substringBefore(' ')) {
            "INSERT" -> StatementKind.INSERT
            "UPDATE" -> StatementKind.UPDATE
            "DELETE" -> StatementKind.DELETE
            "CREATE" -> StatementKind.CREATE_TABLE
            "ALTER" -> StatementKind.ALTER_TABLE
            else -> StatementKind.UNKNOWN
        }
        return FixedExecution(QueryEvent.UpdateCount(ROWS, kind))
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

/** Ein Backend, das zu den Transaktionstexten ihre Arten meldet (Zähler 0, das `off`-Paar als zwei Ereignisse). */
private class TransactionBackend : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution = when {
        request.sql.startsWith("set implicit_transactions on") ->
            FixedExecution(QueryEvent.UpdateCount(0, StatementKind.IMPLICIT_TRANSACTIONS_ON))

        else -> FixedExecution(
            QueryEvent.UpdateCount(0, StatementKind.IMPLICIT_TRANSACTIONS_OFF),
            QueryEvent.UpdateCount(0, StatementKind.COMMIT_IF_OPEN),
        )
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

/** Ein Backend, das jede Anfrage mit demselben [event] beantwortet. */
private class FixedCountBackend(private val event: QueryEvent) : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution = FixedExecution(event)

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

private class FixedExecution(vararg events: QueryEvent) : QueryExecution {
    override val events: Flow<QueryEvent> = flowOf(*events)

    override suspend fun close() = Unit
}
