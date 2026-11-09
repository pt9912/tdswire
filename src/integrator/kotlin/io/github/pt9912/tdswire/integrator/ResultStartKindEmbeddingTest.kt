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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val MERGE_COMMAND = 0x0117
private const val SELECT_COMMAND = 0x00C1
private const val DONE_ERROR_BIT = 0x02
private const val DONE_COUNT = 0x10
private const val ROWS = 3L
private const val ACTION_LENGTH = 10

/**
 * `ResultStart.kind` und `UpdateCount(n, MERGE)` von außen am Draht (`SPEC-059`, `SPEC-031`, `SPEC-002`): das Source-Set
 * sieht nur die öffentliche Fläche; die Antworten stehen auf den Rohbytes. Die Kennung `0x0117` ist am echten SQL Server
 * gemessen (M-15, M-26) für das Ergebnis und den Fehler im Ergebnis; `UpdateCount(n, MERGE)` ist Setzung.
 */
class ResultStartKindEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun start(events: List<QueryEvent>): TdsServer {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        val config = ServerConfig(
            ListenSettings(LOOPBACK, 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = AuthSettings(parsed.list, plainTestMode = true),
        )
        val outcome = runBlocking { TdsServer.start(config, BackendSource.Custom(FixedListBackend(events))) }
        return assertIs<StartOutcome.Running>(outcome).server.also { running += it }
    }

    /** Status und Befehlskennung des abschließenden DONE der Antwort auf einen Batch. */
    private fun doneOf(server: TdsServer): Pair<Int, Int> = WireClient(server.port).use { client ->
        client.login()
        val answer = client.batch("MERGE x")
        answer.doneStatus to answer.command
    }

    /**
     * Ein eigenes Backend, das ein Ergebnis mit der Art `MERGE` beginnt und dann scheitert, bekommt einen Abschluss
     * mit dem Fehlerbit und der Kennung `0x0117`; ohne Art (`ResultStart(columns)`) bleibt es bei `0x00c1`. Ein
     * `UpdateCount(n, MERGE)` schließt mit `COUNT` und `0x0117`. Wächter: ein `kind`, das der Sequenzierer nicht liest
     * (Kennung `0x00c1`), eine Kennung 0 für `MERGE`, ein Abschluss ohne `COUNT`.
     *
     * @spec SPEC-059
     * @spec SPEC-031
     */
    @Test
    fun resultStartKindUndUpdateCountMergeTragenDieKennungAmDraht() {
        val column = ResultColumn("a", SqlType.UnicodeText, nullable = false, maxLength = ACTION_LENGTH)
        val failure = QueryEvent.Failure("TDS_BACKEND_ERROR", "Fehler")
        val merge = doneOf(start(listOf(QueryEvent.ResultStart(listOf(column), StatementKind.MERGE), failure)))
        val other = doneOf(start(listOf(QueryEvent.ResultStart(listOf(column)), failure)))
        val count = doneOf(start(listOf(QueryEvent.UpdateCount(ROWS, StatementKind.MERGE))))
        assertEquals(MERGE_COMMAND, merge.second)
        assertEquals(DONE_ERROR_BIT, merge.first and DONE_ERROR_BIT)
        assertEquals(SELECT_COMMAND, other.second)
        assertEquals(MERGE_COMMAND, count.second)
        assertEquals(DONE_COUNT, count.first and DONE_COUNT)
    }
}

/** Ein Backend, das jede Anfrage mit denselben [events] beantwortet. */
private class FixedListBackend(private val events: List<QueryEvent>) : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> = this@FixedListBackend.events.asFlow()

        override suspend fun close() = Unit
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}
