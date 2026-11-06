package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Grenze der Prepared-Handles und ihre Freigabe von außen (`TDS-FA-04`, `TDS-QA-02`, `SPEC-058`, `SPEC-059`):
 * die Grenze ist `RequestSettings.maxPreparedHandles` der öffentlichen Konfiguration, kein neuer Typname. Der Test
 * startet den Server über `TdsServer.start` und spricht Rohbytes (`sp_prepexec` als RPC, die Freigabe als SQL_BATCH
 * `EXEC sp_unprepare <n>`); der Rohbytes-Client ist kein Referenzclient, belegt ist die Antwort des Servers auf diese
 * Bytes.
 */
class HandleLimitEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun config(requests: RequestSettings): ServerConfig {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        return ServerConfig(
            ListenSettings(LOOPBACK, 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = AuthSettings(parsed.list, plainTestMode = true),
            requests = requests,
        )
    }

    private fun start(requests: RequestSettings): TdsServer {
        val outcome = runBlocking { TdsServer.start(config(requests), BackendSource.Custom(SilentBackend())) }
        return assertIs<StartOutcome.Running>(outcome).server.also { running += it }
    }

    private fun ByteArray.hasText(text: String): Boolean {
        val needle = utf16(text)
        return (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
    }

    private fun ByteArray.hasBytes(vararg sequence: Int): Boolean {
        val needle = sequence.map { it.toByte() }
        return (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
    }

    /**
     * Mit `maxPreparedHandles = 2` nimmt die Sitzung zwei `sp_prepexec` an, das dritte ergibt `TDS_PREPARE_LIMIT`; die
     * Batch-Freigabe eines Handles schafft Platz für genau ein weiteres, ein unbekanntes Handle ergibt 8179 und
     * DONE mit Fehlerstatus, ohne dass die Verbindung endet. Die Antwort auf `sp_prepexec` trägt RETURNSTATUS 0 vor dem
     * RETURNVALUE. Wächter: eine Grenze, die der öffentliche Eingang nicht erreicht (Voreinstellung 1000 bleibt), eine
     * Freigabe, die die Zählung nicht senkt, ein Handle-Fehler, der die Verbindung schließt.
     *
     * @spec SPEC-058
     * @spec SPEC-059
     * @spec SPEC-061
     * @spec SPEC-030
     */
    @Test
    fun dieEingestellteGrenzeUndDieBatchFreigabeWirkenUeberDenEinstieg() {
        val server = start(RequestSettings(maxPreparedHandles = 2))
        WireClient(server.port).use { client ->
            client.login()
            val first = client.rpc(prepExecPayload("select 1"))
            assertTrue(first.hasBytes(RETURN_STATUS, 0, 0, 0, 0, RETURN_VALUE), "RETURNSTATUS 0 vor dem RETURNVALUE")
            assertFalse(first.hasText(LIMIT_TEXT))
            assertFalse(client.rpc(prepExecPayload("select 2")).hasText(LIMIT_TEXT))
            assertTrue(client.rpc(prepExecPayload("select 3")).hasText(LIMIT_TEXT), "das dritte Handle über der Grenze")

            val released = client.batch("EXEC sp_unprepare 1")
            assertEquals(0, released.doneStatus, "Erfolg ohne Fehlerbit")
            assertFalse(client.rpc(prepExecPayload("select 3")).hasText(LIMIT_TEXT), "Platz durch die Freigabe")
            assertTrue(client.rpc(prepExecPayload("select 4")).hasText(LIMIT_TEXT), "nur ein Platz")

            val unknown = client.batch("EXEC sp_unprepare 99")
            assertTrue(unknown.doneStatus and DONE_ERROR != 0, "DONE mit Fehlerstatus")
            assertTrue(unknown.contains("Could not find prepared statement with handle 99."))
            val again = client.batch("EXEC sp_unprepare 1")
            assertTrue(again.contains("Could not find prepared statement with handle 1."), "doppelt freigegeben")
            assertTrue(client.batch("EXEC sp_unprepare 2").doneStatus == 0, "die Verbindung ist nach dem Fehler offen")
        }
    }

    /**
     * Die Voreinstellung bleibt 1000; null und negative Werte der Grenze weist der Start mit `OUT_OF_RANGE` ab, ohne
     * Ausnahme und ohne gebundenen Port. Wächter: eine Grenze 0, die jedes `sp_prepexec` ablehnt, statt den Start
     * abzubrechen; eine Voreinstellung, die von der Spezifikation abweicht.
     *
     * @spec SPEC-058
     * @spec SPEC-059
     * @spec SPEC-061
     */
    @Test
    fun nullUndNegativeGrenzenBrechenDenStartAbUndDieVoreinstellungIst1000() {
        assertEquals(DEFAULT_LIMIT, RequestSettings().maxPreparedHandles)
        listOf(0, -1).forEach { value ->
            val outcome = runBlocking {
                TdsServer.start(config(RequestSettings(maxPreparedHandles = value)), BackendSource.None)
            }
            val aborted = assertIs<StartOutcome.Aborted>(outcome)
            assertEquals(listOf(IssueKind.OUT_OF_RANGE), aborted.issues.map { it.kind }, "Wert $value")
        }
    }
}

private const val DEFAULT_LIMIT = 1000
private const val RETURN_STATUS = 0x79
private const val RETURN_VALUE = 0xAC
private const val LIMIT_TEXT = "Prepared handle limit reached."

/** Ein Backend ohne Ergebnis: jede Anfrage endet leer. */
private class SilentBackend : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> = emptyFlow()

        override suspend fun close() = Unit
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}
