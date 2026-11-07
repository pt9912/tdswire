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
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val ANLEGEN =
    "CREATE TABLE dbo.neu (Id BIGINT NOT NULL, Name NVARCHAR(50) NULL, CONSTRAINT [pk_neu] PRIMARY KEY ([Id]))"

/**
 * Die Codebeispiele von `docs/user/einbettung-und-betrieb.md`: jede Funktion mit dem Präfix `anleitung` steht dort
 * wörtlich. Der Test übersetzt sie gegen die öffentliche Fläche und fährt sie gegen den Rohbytes-Client (kein
 * Referenzclient): belegt ist, dass die Beispiele übersetzen und die beschriebene Antwort liefern.
 *
 * @spec SPEC-059
 */
class AnleitungTest {
    @Test
    fun dasEinbettungsbeispielStartetBedientUndStoppt() {
        val ereignisse = CopyOnWriteArrayList<TelemetryEvent>()
        val config = anleitungKonfiguration(port = 0, benutzerliste = "alice\t{plain}secret\n")
        val outcome = runBlocking { TdsServer.start(config, anleitungTabellen(), TelemetrySink { ereignisse += it }) }
        val server = assertIs<StartOutcome.Running>(outcome).server
        try {
            WireClient(server.port).use { client ->
                client.login()
                assertEquals(1L, client.batch("SELECT Name FROM kunden").rowCount)
            }
        } finally {
            runBlocking { server.stop() }
        }
        val arten = ereignisse.map { it.kind }
        val erwartet = listOf(EventKind.SERVER_STARTED, EventKind.LOGIN_ACCEPTED, EventKind.SERVER_STOPPED)
        assertTrue(arten.containsAll(erwartet))
    }

    @Test
    fun einAbgebrochenerStartMeldetDieBefunde() {
        val config = anleitungKonfiguration(port = 70000, benutzerliste = "alice\t{plain}secret\n")
        val outcome = runBlocking { TdsServer.start(config, BackendSource.None) }
        val befunde = anleitungBefunde(outcome)
        assertEquals(1, befunde.size)
        assertTrue(befunde.single().endsWith(": OUT_OF_RANGE"))
    }

    @Test
    fun einEigenesBackendBeantwortetDieAnfrage() {
        val config = anleitungKonfiguration(port = 0, benutzerliste = "alice\t{plain}secret\n")
        val outcome = runBlocking { TdsServer.start(config, BackendSource.Custom(AnleitungBackend())) }
        val server = assertIs<StartOutcome.Running>(outcome).server
        try {
            WireClient(server.port).use { client ->
                client.login()
                assertEquals(2L, client.batch("SELECT irgendwas").rowCount)
            }
        } finally {
            runBlocking { server.stop() }
        }
    }

    @Test
    fun dieStrukturaenderungLegtEineTabelleAn() {
        val config = anleitungKonfiguration(port = 0, benutzerliste = "alice\t{plain}secret\n")
        val outcome = runBlocking { TdsServer.start(config, BackendSource.Custom(anleitungVeraenderlich())) }
        val server = assertIs<StartOutcome.Running>(outcome).server
        try {
            WireClient(server.port).use { client ->
                client.login()
                client.batch(ANLEGEN)
                assertEquals(0L, client.batch("SELECT Id, Name FROM neu").rowCount)
            }
        } finally {
            runBlocking { server.stop() }
        }
    }
}

// --- Beispiel: Server einbinden -------------------------------------------------------------------------------------

/** Baut die Konfiguration: Loopback, Klartext (nur für Tests), Benutzerliste aus dem Text einer Datei. */
internal fun anleitungKonfiguration(port: Int, benutzerliste: String): ServerConfig {
    val users = when (val parsed = UserList.parse(benutzerliste, plainTestMode = true)) {
        is UserListResult.Valid -> parsed.list
        is UserListResult.Invalid -> error("Benutzerliste ungültig: ${parsed.error}")
    }
    return ServerConfig(
        listen = ListenSettings("127.0.0.1", port),
        encryption = EncryptionSetting.TEST_UNENCRYPTED,
        auth = AuthSettings(users, plainTestMode = true),
    )
}

/** Eine Tabelle `kunden` mit einer Zeile; die Zeilenquelle liefert die Spalten in der Reihenfolge der Anfrage. */
internal fun anleitungTabellen(): BackendSource {
    val spalten = listOf(SimColumn("Id", SqlType.Int64, nullable = false), SimColumn("Name", SqlType.UnicodeText))
    val zeilen = listOf(mapOf("Id" to 1L, "Name" to "Ada"))
    val kunden = SimTable(
        name = "kunden",
        columns = spalten,
        primaryKey = listOf("Id"),
        rows = { query ->
            val offen = zeilen.iterator()
            RowSource { if (offen.hasNext()) offen.next().let { z -> query.columns.map { z[it.name] } } else null }
        },
    )
    return BackendSource.Simulation(listOf(kunden))
}

/** Gibt die Befunde eines abgebrochenen Starts als Text aus; ein laufender Server ergibt eine leere Liste. */
internal fun anleitungBefunde(outcome: StartOutcome): List<String> = when (outcome) {
    is StartOutcome.Running -> emptyList()
    is StartOutcome.Aborted -> outcome.issues.map { it.message }
}

// --- Beispiel: eigenes Backend --------------------------------------------------------------------------------------

/** Ein Backend, das jede Anfrage mit zwei Zeilen einer Textspalte beantwortet. */
internal class AnleitungBackend : QueryBackend {
    private val spalte = ResultColumn("Text", SqlType.UnicodeText, nullable = false)

    override suspend fun execute(request: QueryRequest): QueryExecution {
        val ereignisse: List<QueryEvent> = listOf(
            QueryEvent.ResultStart(listOf(spalte)),
            QueryEvent.Row(listOf("eins")),
            QueryEvent.Row(listOf("zwei")),
            QueryEvent.ResultEnd(2),
        )
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = ereignisse.asFlow()

            override suspend fun close() = Unit
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

// --- Beispiel: Simulations-Backend mit veränderlichem Bestand -------------------------------------------------------

/** Das Backend liest den Bestand bei jeder Anfrage aus `bestand`; `CREATE TABLE` trägt die neue Tabelle ein. */
internal fun anleitungVeraenderlich(): SimulationBackend {
    val bestand = CopyOnWriteArrayList<SimTable>()
    return SimulationBackend(
        catalog = { SimCatalog(bestand.toList()) },
        onStructure = { schema, name, spalten, schluessel ->
            bestand.removeAll { it.schema.equals(schema, ignoreCase = true) && it.name.equals(name, ignoreCase = true) }
            bestand += SimTable(
                schema = schema,
                name = name,
                columns = spalten,
                primaryKey = schluessel,
                rows = { RowSource { null } },
            )
        },
    )
}
