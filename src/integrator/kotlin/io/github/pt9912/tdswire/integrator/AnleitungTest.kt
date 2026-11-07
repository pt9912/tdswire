package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.LoginSettings
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.PasswordChars
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
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val ANLEGEN =
    "CREATE TABLE dbo.neu (Id BIGINT NOT NULL, Name NVARCHAR(50) NULL, CONSTRAINT [pk_neu] PRIMARY KEY ([Id]))"

/**
 * Die Codebeispiele von `docs/user/einbettung-und-betrieb.md`: die Funktionen und Klassen mit dem Präfix `anleitung`
 * stehen dort als Codeblöcke, die Einrückung ausgenommen, wörtlich; kein Sensor hält Test und Doku gleich, die
 * Gleichheit gilt nur, soweit beide gelesen werden. Der Test übersetzt sie gegen die öffentliche Fläche und fährt sie
 * gegen den Rohbytes-Client (kein Referenzclient): belegt ist, dass die Beispiele übersetzen und die beschriebene
 * Antwort liefern.
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

    @Test
    fun dieGrenzenGeltenUndNachStopNimmtDerPortNichtsMehrAn() {
        val server = startOhneTabellen(anleitungGrenzen(anleitungBenutzer("alice\t{plain}secret\n", true)))
        val port = server.port
        WireClient(port).use { it.login() }
        runBlocking { server.stop() }
        assertFailsWith<IOException> { Socket().use { it.connect(InetSocketAddress(LOOPBACK, port), 1000) } }
    }

    @Test
    fun eineAbgelehnteAnmeldungErscheintMitGrundInDerZeile() {
        val zeilen = LinkedBlockingQueue<String>()
        val config = anleitungKonfiguration(port = 0, benutzerliste = "alice\t{plain}secret\n")
        val sink = TelemetrySink { zeilen.offer(anleitungZeile(it)) }
        val outcome = runBlocking { TdsServer.start(config, BackendSource.None, sink) }
        val server = assertIs<StartOutcome.Running>(outcome).server
        try {
            WireClient(server.port).use { assertFalse(it.tryLogin("alice", "falsch")) }
        } finally {
            runBlocking { server.stop() }
        }
        val abgelehnt = zeilen.single { "LOGIN_REJECTED" in it }
        assertTrue("reason=WRONG_PASSWORD" in abgelehnt && "connection_id=1" in abgelehnt, abgelehnt)
    }

    @Test
    fun einPruefwertMitPbkdf2MeldetDenBenutzerAn() {
        val wert = anleitungPruefwert("geheim")
        val config = ServerConfig(
            listen = ListenSettings(LOOPBACK, 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = anleitungBenutzer("bob\t$wert\n", false),
        )
        val server = startOhneTabellen(config)
        try {
            WireClient(server.port).use { assertTrue(it.tryLogin("bob", "geheim")) }
            WireClient(server.port).use { assertFalse(it.tryLogin("bob", "falsch")) }
        } finally {
            runBlocking { server.stop() }
        }
    }

    @Test
    fun tlsMitEinemPkcs12SpeicherStartetMitRequired() {
        val verzeichnis = Files.createTempDirectory("anleitung-tls")
        val speicher = verzeichnis.resolve("server.p12")
        val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
        val rc = ProcessBuilder(
            keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
            "-storetype", "PKCS12", "-keystore", speicher.toString(), "-storepass", "speicher-geheim",
            "-dname", "CN=localhost", "-ext", "san=dns:localhost,ip:127.0.0.1",
        ).redirectErrorStream(true).start().also { it.inputStream.readAllBytes() }.waitFor()
        assertEquals(0, rc)
        val auth = anleitungBenutzer("bob\t${anleitungPruefwert("geheim")}\n", false)
        val server = startOhneTabellen(anleitungTls(speicher, "speicher-geheim", auth, port = 0))
        runBlocking { server.stop() }
        verzeichnis.toFile().deleteRecursively()
    }

    private fun startOhneTabellen(config: ServerConfig): TdsServer {
        val outcome = runBlocking { TdsServer.start(config, BackendSource.None) }
        return assertIs<StartOutcome.Running>(outcome).server
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

// --- Beispiel: Grenzen setzen ---------------------------------------------------------------------------------------

/** Höchstens 50 Verbindungen, Anmeldefrist 5 s, Anfragefrist 10 s, 200 Handles je Sitzung, geordnetes Beenden 3 s. */
internal fun anleitungGrenzen(auth: AuthSettings): ServerConfig = ServerConfig(
    listen = ListenSettings("127.0.0.1", 0, maxConnections = 50),
    login = LoginSettings(timeoutSeconds = 5),
    encryption = EncryptionSetting.TEST_UNENCRYPTED,
    auth = auth,
    requests = RequestSettings(
        queryTimeoutSeconds = 10,
        cancelTimeoutSeconds = 2,
        shutdownGraceSeconds = 3,
        maxPreparedHandles = 200,
    ),
)

/** Die Benutzerliste aus dem Text einer Datei; [plain] gilt nur im Testmodus. */
internal fun anleitungBenutzer(text: String, plain: Boolean): AuthSettings {
    val parsed = UserList.parse(text, plainTestMode = plain)
    return AuthSettings(assertIs<UserListResult.Valid>(parsed).list, plainTestMode = plain)
}

// --- Beispiel: Ereignisse als Zeilen ---------------------------------------------------------------------------------

/** Eine Zeile je Ereignis; Felder, die die Art nicht trägt, entfallen. */
internal fun anleitungZeile(event: TelemetryEvent): String = listOfNotNull(
    event.at.toString(),
    event.level.name,
    event.kind.name,
    event.connectionId?.let { "connection_id=$it" },
    event.reason?.let { "reason=$it" },
    event.errorCode?.let { "error_code=$it" },
    event.principal?.let { "principal=$it" },
).joinToString(" ")

// --- Beispiel: TLS und Prüfwert ------------------------------------------------------------------------------

/** `REQUIRED` mit dem PKCS#12-Speicher aus [speicher] und seinem Kennwort. */
internal fun anleitungTls(speicher: Path, kennwort: String, auth: AuthSettings, port: Int): ServerConfig =
    ServerConfig(
        listen = ListenSettings("127.0.0.1", port),
        encryption = EncryptionSetting.REQUIRED,
        tls = TlsKeyMaterial(Files.readAllBytes(speicher), PasswordChars(kennwort.toCharArray())),
        auth = auth,
    )

private const val SALT_BYTES = 16
private const val HASH_BYTES = 32
private const val BITS_PER_BYTE = 8
private const val ITERATIONEN = 600_000

/** Der Prüfwert `{pbkdf2-sha256}Salz$Iterationen$Hash` für [kennwort]; Salz und Hash sind Standard-Base64. */
internal fun anleitungPruefwert(kennwort: String): String {
    val salz = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
    val spec = PBEKeySpec(kennwort.toCharArray(), salz, ITERATIONEN, HASH_BYTES * BITS_PER_BYTE)
    val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    val base64 = Base64.getEncoder()
    val felder = listOf(base64.encodeToString(salz), ITERATIONEN.toString(), base64.encodeToString(hash))
    return "{pbkdf2-sha256}" + felder.joinToString("$")
}
