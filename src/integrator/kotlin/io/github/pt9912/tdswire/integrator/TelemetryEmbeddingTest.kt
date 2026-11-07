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
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.EventLevel.DEBUG
import io.github.pt9912.tdswire.domain.telemetry.EventLevel.INFO
import io.github.pt9912.tdswire.domain.telemetry.EventLevel.WARN
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 20L

/**
 * Die Senke des Integrators von außen (`SPEC-059`, `TDS-FA-09`): dieses Source-Set sieht nur die öffentliche Fläche.
 * Der Sammler ist hier eine Senke in einer Zeile; ein öffentlicher Sammler gehört nicht zur Fläche.
 */
class TelemetryEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun users(): AuthSettings {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        return AuthSettings(parsed.list, plainTestMode = true)
    }

    private fun unencrypted() =
        ServerConfig(ListenSettings(LOOPBACK, 0), encryption = EncryptionSetting.TEST_UNENCRYPTED, auth = users())

    /** Führt [block] aus und gibt zurück, was der Prozess dabei auf Standardausgabe und Fehlerkanal schrieb. */
    private fun <T> captured(block: () -> T): Pair<T, String> {
        val out = System.out
        val err = System.err
        val buffer = ByteArrayOutputStream()
        val capture = PrintStream(buffer, true, Charsets.UTF_8)
        System.setOut(capture)
        System.setErr(capture)
        try {
            val result = block()
            capture.flush()
            return result to buffer.toString(Charsets.UTF_8)
        } finally {
            System.setOut(out)
            System.setErr(err)
        }
    }

    private fun serve(telemetry: TelemetrySink, afterClose: () -> Unit = {}): Pair<Int, Long> {
        val outcome = runBlocking { TdsServer.start(unencrypted(), BackendSource.Custom(OneRowBackend()), telemetry) }
        val server = assertIs<StartOutcome.Running>(outcome).server.also { running += it }
        val rows = WireClient(server.port).use { client ->
            client.login()
            client.batch("irgendein text").rowCount
        }
        afterClose()
        runBlocking { server.stop() }
        return server.port to rows
    }

    /** Wartet, bis das Ende des Spans [span] in [events] steht; sonst endet die Verbindung erst im Stop. */
    private fun awaitEvent(events: List<TelemetryEvent>, span: String) {
        val end = System.currentTimeMillis() + WAIT_MILLIS
        while (events.none { it.span == span } && System.currentTimeMillis() < end) Thread.sleep(POLL_MILLIS)
    }

    /**
     * Eine Senke des Integrators, über den Parameter von `start` übergeben, bekommt die Ereignisse eines Laufs aus
     * Start (mit den Abschwächungen des Testmodus), Anmeldung, dem Ende der Spans von Backend-Aufruf, Anfrage und
     * Verbindung und Stop in dieser Reihenfolge, mit der Kennung der Anmeldung im Feld `principal`; Standardausgabe
     * und Fehlerkanal des Prozesses bleiben leer (im selben Prozess umgeleitet: schwächerer Beleg als ein
     * Prozesstest). Die Spans tragen ihre Felder über die öffentliche Fläche: Name, Ausgang, Dauer, Kennung der
     * Anfrage, Art der Anfrage und Phase der Verbindung.
     *
     * @spec SPEC-059
     * @spec SPEC-012
     */
    @Test
    fun eineSenkeEmpfaengtDieEreignisseUndDieStandardausgabeBleibtLeer() {
        val events = CopyOnWriteArrayList<TelemetryEvent>()
        val (served, output) = captured {
            serve({ events += it }) { awaitEvent(events, "tds.connection") }
        }
        assertEquals("", output)
        assertEquals(1L, served.second)
        assertEquals(
            listOf(
                EventKind.SERVER_STARTED,
                EventKind.SECURITY_WEAKENED,
                EventKind.SECURITY_WEAKENED,
                EventKind.ENCRYPTION_NOT_OFFERED,
                EventKind.LOGIN_ACCEPTED,
                EventKind.SPAN_ENDED,
                EventKind.SPAN_ENDED,
                EventKind.SPAN_ENDED,
                EventKind.SERVER_STOPPED,
            ),
            events.map { it.kind },
        )
        assertEquals(
            listOf(INFO, WARN, WARN, WARN, INFO, DEBUG, DEBUG, DEBUG, INFO),
            events.map { it.level },
        )
        val spans = events.filter { it.kind == EventKind.SPAN_ENDED }
        assertEquals(listOf("backend.execute", "tds.query", "tds.connection"), spans.map { it.span })
        assertEquals(listOf("ok", "ok", "ok"), spans.map { it.outcome })
        spans.forEach { assertTrue(checkNotNull(it.durationMs) >= 0, "Dauer: ${it.durationMs}") }
        val (backend, query, connection) = spans
        assertNotNull(query.requestId)
        assertEquals(query.requestId, backend.requestId)
        assertEquals("sql_batch", query.requestKind)
        assertEquals("session", connection.phase)
        assertNull(connection.requestId)
        assertEquals(
            listOf("ENCRYPTION_TEST_UNENCRYPTED", "PLAIN_TEST_MODE"),
            events.filter { it.kind == EventKind.SECURITY_WEAKENED }.map { it.reason },
        )
        assertEquals(served.first, events.single { it.kind == EventKind.SERVER_STARTED }.port)
        val accepted = events.single { it.kind == EventKind.LOGIN_ACCEPTED }
        assertEquals("alice", accepted.principal)
        assertEquals(1L, accepted.connectionId)
    }

    /**
     * Ein Start mit ungültiger Konfiguration meldet jeden Fehler als Ereignis mit Schlüssel und Art (kein Wert) und
     * schreibt nichts auf Standardausgabe oder Fehlerkanal.
     *
     * @spec SPEC-059
     * @spec SPEC-043
     */
    @Test
    fun eineUngueltigeKonfigurationWirdAlsEreignisGemeldet() {
        val events = CopyOnWriteArrayList<TelemetryEvent>()
        val config = ServerConfig(ListenSettings(LOOPBACK, 0), auth = users())
        val (aborted, output) = captured {
            runBlocking { TdsServer.start(config, BackendSource.None, TelemetrySink { events += it }) }
        }
        assertIs<StartOutcome.Aborted>(aborted)
        assertEquals("", output)
        assertEquals(
            aborted.issues.map { it.kind.name },
            events.filter { it.kind == EventKind.CONFIG_INVALID }.map { it.issueKind },
        )
        assertEquals(2, events.size, events.toString())
    }

    /**
     * Ohne Senke (Voreinstellung von `start`) läuft der Server wie mit einer: Anmeldung, Anfrage und Stop gelingen,
     * und die Bibliothek schreibt nichts auf Standardausgabe oder Fehlerkanal.
     *
     * @spec SPEC-059
     */
    @Test
    fun ohneSenkeLaeuftDerServerUndSchreibtNichts() {
        val (served, output) = captured { serve(TelemetrySink.NONE) }
        assertEquals(1L, served.second)
        assertEquals("", output)
    }
}

/** Ein Backend, das jede Anfrage mit einer Zeile beantwortet. */
private class OneRowBackend : QueryBackend {
    private val column = ResultColumn("Text", SqlType.UnicodeText, nullable = false)

    override suspend fun execute(request: QueryRequest): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> =
            flowOf(QueryEvent.ResultStart(listOf(column)), QueryEvent.Row(listOf("zeile")), QueryEvent.ResultEnd(1))

        override suspend fun close() = Unit
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}
