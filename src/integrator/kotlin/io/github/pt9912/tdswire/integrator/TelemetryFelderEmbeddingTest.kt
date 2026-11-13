package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ObservationSettings
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
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
import java.net.InetAddress
import java.net.ServerSocket
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
 * Welche Felder eine Art füllt, von außen gelesen (`SPEC-059`, Abschnitt 4 der Spezifikation): `port`, `address` und
 * `exceptionType` beim Bindefehler, `part` und `form` bei der TLS-Diagnose, `reason` beim Abbruch des Starts und bei
 * der abgelehnten Anmeldung, `tables`, `key`, `errorCode`. Nicht von außen auszulösen und daher hier nicht belegt (nur
 * durch die Kotlin-Typen der internen Klassen gehalten): `exceptionType` der Arten `ACCEPT_FAILED`,
 * `CONNECTION_FAILED` und `ADAPTER_FAILED`, `failure` der Art `TLS_HANDSHAKE_FAILED`, `reason` der Art
 * `TLS_FIRST_NOT_OFFERED` und `cause` der Art `REQUEST_FAILED`.
 */
class TelemetryFelderEmbeddingTest {
    private val running = CopyOnWriteArrayList<TdsServer>()
    private val events = CopyOnWriteArrayList<TelemetryEvent>()
    private val sink = TelemetrySink { events += it }

    @AfterTest
    fun stopServers() {
        runBlocking { running.forEach { it.stop() } }
    }

    private fun users(): AuthSettings {
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        return AuthSettings(parsed.list, plainTestMode = true)
    }

    private fun unencrypted(port: Int = 0) = ServerConfig(
        ListenSettings(host = LOOPBACK, port = port),
        encryption = EncryptionSetting.TEST_UNENCRYPTED,
        auth = users(),
    )

    private fun start(config: ServerConfig, source: BackendSource): StartOutcome {
        val outcome = runBlocking { TdsServer.start(config, source, sink) }
        if (outcome is StartOutcome.Running) running += outcome.server
        return outcome
    }

    private fun only(kind: EventKind): TelemetryEvent = events.filter { it.kind == kind }.let {
        assertEquals(1, it.size, "Art $kind in $events")
        it.single()
    }

    private fun awaitKind(kind: EventKind): TelemetryEvent {
        val end = System.currentTimeMillis() + WAIT_MILLIS
        while (events.none { it.kind == kind } && System.currentTimeMillis() < end) Thread.sleep(POLL_MILLIS)
        return only(kind)
    }

    private fun table(name: String = "t") = SimTable(
        name = name,
        columns = listOf(SimColumn("Id", SqlType.Int32, nullable = false)),
        primaryKey = listOf("Id"),
        rows = {
            object : RowSource {
                override suspend fun next(): List<Any?>? = null

                override fun close() = Unit
            }
        },
    )

    /**
     * Ein belegter Port ergibt `BIND_FAILED` mit Adresse und Port und ohne Typname der Ausnahme; eine TLS-Konfiguration
     * ohne gültiges Material ergibt `TLS_START_REJECTED` mit `part` und `form`; ein doppelter Tabellenname ergibt
     * `START_REFUSED` mit der Marke `TABLE_NAME_DUPLICATE`; ein ungültiger Wert ergibt `CONFIG_INVALID`
     * mit dem Schlüssel der Diagnose in `Aborted.issues`.
     *
     * @spec SPEC-059
     * @spec SPEC-043
     */
    @Test
    fun dieAbbruchArtenTragenIhreFelder() {
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { busy ->
            assertIs<StartOutcome.Aborted>(start(unencrypted(busy.localPort), BackendSource.None))
            val bind = only(EventKind.BIND_FAILED)
            assertEquals(LOOPBACK, bind.address)
            assertEquals(busy.localPort, bind.port)
            assertNull(bind.exceptionType)
        }
        events.clear()
        val material = TlsKeyMaterial(byteArrayOf(1), PasswordChars(charArrayOf('x')))
        val broken = ServerConfig(ListenSettings(host = LOOPBACK, port = 0), tls = material, auth = users())
        assertIs<StartOutcome.Aborted>(start(broken, BackendSource.None))
        val tls = only(EventKind.TLS_START_REJECTED)
        assertTrue(!tls.part.isNullOrEmpty() && !tls.form.isNullOrEmpty(), "part und form: $events")
        events.clear()
        val twice = BackendSource.Simulation(listOf(table(), table()))
        assertIs<StartOutcome.Aborted>(start(unencrypted(), twice))
        assertEquals("TABLE_NAME_DUPLICATE", only(EventKind.START_REFUSED).reason)
        events.clear()
        val invalid = start(unencrypted(MAX_PORT + 1), BackendSource.None)
        val issue = assertIs<StartOutcome.Aborted>(invalid).issues.single()
        assertEquals(issue.key, only(EventKind.CONFIG_INVALID).key)
    }

    /**
     * Ein Start mit einer Tabelle meldet `SIMULATION_BACKEND_READY` mit der Zahl der Tabellen, eine falsche Anmeldung
     * `LOGIN_REJECTED` mit der Marke `WRONG_PASSWORD` und ohne Kennung, eine Anfrage, die das Backend mit einem Fehler
     * beantwortet, `REQUEST_FAILED` mit dem Code der Antwort und der Kennung der Anfrage; `LOGIN_REJECTED` trägt keine.
     *
     * @spec SPEC-059
     * @spec SPEC-012
     */
    @Test
    fun dieArtenImBetriebTragenIhreFelder() {
        val simulation = assertIs<StartOutcome.Running>(start(unencrypted(), BackendSource.Simulation(listOf(table()))))
        assertEquals(1, only(EventKind.SIMULATION_BACKEND_READY).tables)
        WireClient(simulation.server.port).use { assertTrue(!it.tryLogin("alice", "falsch")) }
        val rejected = awaitKind(EventKind.LOGIN_REJECTED)
        assertEquals("WRONG_PASSWORD", rejected.reason)
        assertNull(rejected.principal)
        events.clear()
        val failing = assertIs<StartOutcome.Running>(start(unencrypted(), BackendSource.Custom(FailingBackend())))
        WireClient(failing.server.port).use {
            it.login()
            it.batch("irgendein text")
        }
        val failed = awaitKind(EventKind.REQUEST_FAILED)
        assertEquals("TDS_BACKEND_ERROR", failed.errorCode)
        assertNotNull(failed.connectionId)
        assertTrue(assertNotNull(failed.requestId).isNotEmpty())
        assertNull(rejected.requestId)
    }

    /**
     * Die Zustandsarten tragen ihre Felder von außen gelesen: `CONFIG_EFFECTIVE` die Einstellungen als Schlüssel und
     * Wert (der Schlüsselspeicher als `UNSET`, kein Pfad), `SERVER_STARTED` Adresse und Port, `CONNECTION_OPENED` die
     * Nummer der Verbindung ohne Sitzung, `CONNECTION_CLOSED` den Grund `SHUTDOWN` einer beim Beenden ruhenden
     * Verbindung; `SERVER_STOPPING` steht vor dem Ende der Verbindung, `SERVER_STOPPED` danach.
     *
     * @spec SPEC-059
     * @spec SPEC-005
     * @spec SPEC-044
     */
    @Test
    fun dieZustandsartenTragenIhreFelder() {
        val server = assertIs<StartOutcome.Running>(start(unencrypted(), BackendSource.Custom(FailingBackend()))).server
        val settings = assertNotNull(only(EventKind.CONFIG_EFFECTIVE).settings)
        assertEquals("15", settings["LOGIN_TIMEOUT"])
        assertEquals("TEST_UNENCRYPTED", settings["ENCRYPTION_POLICY"])
        assertEquals("UNSET", settings["TLS_KEYSTORE_FILE"])
        assertEquals("16777216", settings["MAX_MESSAGE_BYTES"])
        val started = only(EventKind.SERVER_STARTED)
        assertEquals(LOOPBACK, started.address)
        assertEquals(server.port, started.port)
        WireClient(server.port).use {
            it.login()
            val opened = awaitKind(EventKind.CONNECTION_OPENED)
            assertEquals(1L, opened.connectionId)
            assertNull(opened.sessionId)
            runBlocking { server.stop() }
        }
        assertEquals("SHUTDOWN", only(EventKind.CONNECTION_CLOSED).reason)
        val order = events.map { it.kind }
        assertTrue(order.indexOf(EventKind.SERVER_STOPPING) < order.indexOf(EventKind.CONNECTION_CLOSED), "$order")
        assertEquals(EventKind.SERVER_STOPPED, order.last())
    }

    /**
     * Mit `ObservationSettings.metricsIntervalSeconds` von 1 liefert der Server nach einer Anmeldung und einer Anfrage
     * ein Ereignis `METRICS`, dessen Zugriff `metrics` die Zähler nennt (Anfragen, Anmeldungen, aktive Sitzungen) und
     * die Perzentile der Latenz, die erst mit der ersten beendeten Anfrage da sind; die Felder des Span-Endes
     * (`span`, `outcome`, `durationMs`, `requestId`, `requestKind`, `phase`) stehen nur an `SPAN_ENDED`, `metrics`
     * nur an `METRICS`.
     *
     * @spec SPEC-059
     * @spec SPEC-017
     */
    @Test
    fun dieMessungenTragenIhreFelder() {
        val config = ServerConfig(
            ListenSettings(host = LOOPBACK, port = 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = users(),
            requests = RequestSettings(observation = ObservationSettings(metricsIntervalSeconds = 1)),
        )
        val running = assertIs<StartOutcome.Running>(start(config, BackendSource.Custom(FailingBackend())))
        WireClient(running.server.port).use {
            it.login()
            it.batch("irgendein text")
        }
        val snapshot = checkNotNull(awaitKind(EventKind.METRICS).metrics)
        assertEquals(1L, snapshot["requests_total"])
        assertEquals(1L, snapshot["logins_succeeded_total"])
        assertNotNull(snapshot["request_latency_p50_ms"])
        assertTrue(snapshot.keys.none { "irgendein" in it })
        val ended = events.filter { it.kind == EventKind.SPAN_ENDED }
        assertEquals(listOf("backend.execute", "tds.query"), ended.map { it.span }.take(2))
        assertEquals(listOf("error", "error"), ended.map { it.outcome }.take(2))
        events.filter { it.kind != EventKind.SPAN_ENDED }.forEach { assertNull(it.span, "$it") }
        events.filter { it.kind != EventKind.METRICS }.forEach { assertNull(it.metrics, "$it") }
    }
}

private const val MAX_PORT = 65535

/** Ein Backend, das jede Anfrage mit einem Fehler beantwortet. */
private class FailingBackend : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> =
            flowOf(QueryEvent.Failure("TDS_BACKEND_ERROR", "kein Text fuer den Client"))

        override suspend fun close() = Unit
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}
