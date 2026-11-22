package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `ExecuteReader(SchemaOnly)` von außen (`SPEC-031`, `SPEC-036` Form F, `SPEC-059`, `ADR-0059`): `SET_FMTONLY_ON` und
 * `SET_FMTONLY_OFF` als öffentliche Fläche, Antwort auf den Rohbytes. Die Abschlüsse sind am echten SQL Server gemessen
 * (Messprotokoll W-27, `schemaonly-spalten` Anfrage 01, ein Fall je Instanz).
 */
class SchemaOnlyEmbeddingTest {
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

    private fun person() = SimTable(
        name = "person",
        columns = listOf(SimColumn("Id", SqlType.Int32, nullable = false)),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
    )

    /**
     * Die Antwort beginnt mit `DONE MORE` `0x00ba` und `DONE MORE` `0x00b9` (ohne `COUNT`), endet mit `DONE` `0x00ba`
     * ohne Bits und hat davor den Abschluss `MORE|COUNT` `0x00c1` mit Zähler 0; das Simulations-Backend und ein eigenes
     * Backend, das `SET_FMTONLY_OFF`, `SET_FMTONLY_ON`, das Ergebnis und `SET_FMTONLY_OFF` meldet, bekommen dieselben
     * Abschlüsse. Wächter: eine Kennung 0 für die zwei Arten, `COUNT` an ihnen, ein fehlendes schließendes `SET`.
     *
     * @spec SPEC-059
     * @spec SPEC-031
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun schemaOnlySchliesstMitDenZweiArtenVonSetFmtonly() {
        val zero = "00 00 00 00 00 00 00 00"
        val head = hex("FD 01 00 BA 00 $zero  FD 01 00 B9 00 $zero").toList()
        val resultDone = hex("FD 11 00 C1 00 $zero").toList()
        val tail = hex("FD 00 00 BA 00 $zero").toList()
        val text = " SET FMTONLY OFF; SET FMTONLY ON;SELECT Id FROM person SET FMTONLY OFF;"
        val servers = listOf(
            start(BackendSource.Simulation(listOf(person()))),
            start(BackendSource.Custom(SchemaOnlyBackend())),
        )
        for (server in servers) {
            WireClient(server.port).use { client ->
                client.login()
                val payload = client.batch(text).payload.toList()
                assertEquals(head, payload.take(head.size))
                assertEquals(tail, payload.takeLast(tail.size))
                assertEquals(resultDone, payload.dropLast(tail.size).takeLast(resultDone.size))
            }
        }
    }
}

/** Ein Backend, das zum Text von `SchemaOnly` die Arten der `SET` und ein Ergebnis ohne Zeile meldet. */
private class SchemaOnlyBackend : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution = SchemaOnlyAnswer(
        listOf(
            QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_OFF),
            QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_ON),
            QueryEvent.ResultStart(listOf(ResultColumn("Id", SqlType.Int32, false))),
            QueryEvent.ResultEnd(0),
            QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_OFF),
        ),
    )

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

private class SchemaOnlyAnswer(events: List<QueryEvent>) : QueryExecution {
    override val events: Flow<QueryEvent> = events.asFlow()

    override suspend fun close() = Unit
}
