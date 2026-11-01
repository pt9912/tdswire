package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.ConfigValidation
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListAuthAdapter
import io.github.pt9912.tdswire.domain.telemetry.BindFailed
import io.github.pt9912.tdswire.domain.telemetry.ConfigInvalid
import io.github.pt9912.tdswire.domain.telemetry.EncryptionWeakened
import io.github.pt9912.tdswire.domain.telemetry.PlainTestModeActive
import io.github.pt9912.tdswire.domain.telemetry.ServerStarted
import io.github.pt9912.tdswire.domain.telemetry.SimulationBackendReady
import io.github.pt9912.tdswire.domain.telemetry.StartRefusal
import io.github.pt9912.tdswire.domain.telemetry.StartRefused
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.domain.telemetry.TlsStartRejected
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.session.AuthPool
import io.github.pt9912.tdswire.protocol.session.EncryptionPolicy
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.transport.listener.Delayer
import io.github.pt9912.tdswire.transport.listener.ListenerConfig
import io.github.pt9912.tdswire.transport.listener.ListenerEvents
import io.github.pt9912.tdswire.transport.listener.ListenerTimes
import io.github.pt9912.tdswire.transport.listener.StartFailure
import io.github.pt9912.tdswire.transport.listener.StartResult
import io.github.pt9912.tdswire.transport.listener.TdsListener
import io.github.pt9912.tdswire.transport.listener.TlsServerConfig
import io.github.pt9912.tdswire.transport.tls.MaterialResult
import io.github.pt9912.tdswire.transport.tls.TlsMaterial
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Woher die Anfragen ihre Antworten bekommen (`TDS-FA-11.a`): ein fertiges [backend] oder die [simulationTables] des
 * Integrators mit Zeilenquellen und Callbacks, die Kotlin-Schnittstelle der Verdrahtung des Simulations-Backends
 * (`ADR-0012`). Ohne Tabellen und Backend läuft der Server ohne Anfragepfad.
 */
internal class RequestSource(val backend: QueryBackend? = null, val simulationTables: List<SimTable> = emptyList())

/**
 * Alles, was der Prozess außer der Konfiguration festlegt und ein Test ersetzt: die [telemetry]-Senke,
 * Ereignisempfänger, Zeitgeber der Fristen, der [authPool] (einer je Prozess), die Wahl des Adapters und die
 * [requests]-Quelle des Backends. Die Voreinstellung ist der Betrieb: keine Senke, die Benutzerliste als
 * [UserListAuthAdapter], kein Backend.
 */
internal class Wiring(
    val telemetry: TelemetrySink = TelemetrySink.NONE,
    val events: ListenerEvents = ListenerEvents.NONE,
    val delayer: Delayer = Delayer.SYSTEM,
    val authPool: AuthPool = AuthPool(),
    val adapter: (UserList) -> AuthAdapter = { UserListAuthAdapter(it) },
    val requests: RequestSource = RequestSource(),
)

private fun flagProblem(properties: (String) -> String?): String? =
    if (properties(Bootstrap.TICKET_FLAG) != "0") "JVM-Flag -D${Bootstrap.TICKET_FLAG}=0 fehlt" else null

private fun policyOf(setting: EncryptionSetting): EncryptionPolicy = when (setting) {
    EncryptionSetting.REQUIRED -> EncryptionPolicy.REQUIRED
    EncryptionSetting.TEST_UNENCRYPTED -> EncryptionPolicy.TEST_UNENCRYPTED
}

/**
 * Start-Reihenfolge (`ARC-008`, `TDS-FA-09.a`). [launch] ist der Teil, den `TdsServer.start` ruft: JVM-Flag und
 * Konfiguration prüfen (`ConfigValidation.check`), das Simulations-Backend verdrahten (ein doppelter Tabellenname
 * bricht den Start ab, bevor ein Port gebunden wird), TLS-Material laden, Startprobe und Binden im Listener
 * (`SPEC-043`). Bis zum Binden läuft kein Thread des Servers; scheitert ein Schritt, läuft danach keiner weiter
 * und kein Port ist gebunden.
 *
 * `shutdownGrace` ist 0: Festlegung, nicht gemessen und nicht die Voreinstellung `SHUTDOWN_GRACE` von 10 s
 * (`SPEC-047`); die Frist des geordneten Beendens (`SPEC-044`) trägt `slice-abbruch-ressourcen-shutdown`, bis
 * dahin schließt der Stop ohne Frist.
 *
 * Das Flag `-Djdk.tls.server.newSessionTicketCount=0` setzt der Aufrufer des Prozesses (die JVM-Optionen
 * des Starts); [Bootstrap] liest die Systemeigenschaft vor jedem TLS-Aufruf und bricht ab, wenn sie nicht
 * genau `0` ist. Geprüft ist der Wert der Eigenschaft beim Start; dass die Wirkung auf dem Draht eintritt, belegt
 * der TLS-Test mit Kind-JVM, nicht dieser Start.
 */
internal object Bootstrap {
    const val TICKET_FLAG = "jdk.tls.server.newSessionTicketCount"
    private const val CONFIG_INVALID = "Konfiguration ungueltig"

    suspend fun launch(config: ServerConfig, wiring: Wiring, properties: (String) -> String?): StartOutcome {
        val launch = Launch(config, wiring, Telemetry(wiring.telemetry))
        val flag = flagProblem(properties)
        val issues = ConfigValidation.check(config)
        return when {
            flag != null -> launch.abort(flag) { StartRefused(it, StartRefusal.JVM_FLAG_MISSING) }
            issues.isNotEmpty() -> launch.reject(issues)
            else -> launch.bindBackend()
        }
    }

    /** Ein Start: die Konfiguration, die Verdrahtung und der Weg der Ereignisse zur Senke. */
    private class Launch(val config: ServerConfig, val wiring: Wiring, val telemetry: Telemetry) {
        suspend fun bindBackend(): StartOutcome = when (val simulation = backendOf()) {
            is SimulationResult.Failed -> abort(simulation.reason) { StartRefused(it, simulation.refusal) }

            is SimulationResult.Ready -> {
                telemetry.emit { SimulationBackendReady(it, simulation.tables) }
                bind(simulation.backend)
            }

            SimulationResult.Absent -> bind(wiring.requests.backend)
        }

        private suspend fun bind(backend: QueryBackend?): StartOutcome {
            val material = when (val loaded = config.tls?.let { loadMaterial(it) }) {
                null -> null

                is MaterialResult.Rejected -> {
                    val diagnosis = loaded.diagnosis
                    return abort("TLS-Konfiguration: ${diagnosis.part} (${diagnosis.form})") {
                        TlsStartRejected(it, diagnosis.part.name, diagnosis.form)
                    }
                }

                is MaterialResult.Loaded -> loaded.material
            }
            val listener = ListenerConfig(
                host = config.listen.host,
                port = config.listen.port,
                times = ListenerTimes(config.login.timeoutSeconds.seconds, Duration.ZERO, wiring.delayer),
                tls = material?.let { TlsServerConfig(it, TlsParameters.SPEC_023) },
            )
            val handler = HandshakeConnectionHandler(handlerSetup(material), backend)
            return when (val started = TdsListener.start(listener, handler, events())) {
                is StartResult.Failed -> failed(started.failure)
                is StartResult.Started -> running(started.listener)
            }
        }

        private fun backendOf(): SimulationResult {
            val simulation = SimulationSetup.build(wiring.requests.simulationTables)
            return if (wiring.requests.backend != null && simulation !is SimulationResult.Absent) {
                SimulationResult.Failed(
                    StartRefusal.BACKEND_AND_SIMULATION,
                    "Backend und Simulations-Backend gleichzeitig konfiguriert",
                )
            } else {
                simulation
            }
        }

        private fun loadMaterial(key: TlsKeyMaterial): MaterialResult {
            val password = key.password.reveal()
            try {
                return TlsMaterial.load(key.keystore, password)
            } finally {
                password.fill('\u0000')
                key.password.wipe()
            }
        }

        private fun handlerSetup(material: TlsMaterial?) = HandlerSetup(
            handshake = HandshakeConfig(encryptionPolicy = policyOf(config.encryption)),
            adapter = wiring.adapter(config.auth.users),
            authPool = wiring.authPool,
            login = config.login,
            tls = material,
            telemetry = telemetry,
        ).also { it.requests = config.requests }

        private fun events(): ListenerEvents = ListenerEvents { event ->
            wiring.events.onEvent(event)
            telemetry.emit { at -> event.toEvent(at) }
        }

        private fun running(listener: TdsListener): StartOutcome {
            telemetry.emit { ServerStarted(it, listener.port) }
            if (config.encryption == EncryptionSetting.TEST_UNENCRYPTED) telemetry.emit { EncryptionWeakened(it) }
            if (config.auth.plainTestMode) telemetry.emit { PlainTestModeActive(it) }
            return StartOutcome.Running(TdsServer(listener, telemetry))
        }

        /**
         * Der Grund des Abbruchs ist der Text von [StartOutcome.Aborted.reason]; das Ereignis entsteht aus denselben
         * Werten wie der Text, damit beide dasselbe nennen. Der Text enthält weder Pfad noch Ausnahmetext.
         */
        fun abort(reason: String, event: (Instant) -> TelemetryEvent): StartOutcome {
            telemetry.emit(event)
            return StartOutcome.Aborted(reason)
        }

        fun reject(issues: List<ConfigIssue>): StartOutcome {
            issues.forEach { issue -> telemetry.emit { ConfigInvalid(it, issue.key, issue.kind.name) } }
            return StartOutcome.Aborted(CONFIG_INVALID, issues)
        }

        private fun failed(failure: StartFailure): StartOutcome = abort("Start gescheitert: ${failure.message}") {
            when (failure) {
                is StartFailure.Tls -> TlsStartRejected(it, failure.diagnosis.part.name, failure.diagnosis.form)
                is StartFailure.PortInUse -> BindFailed(it, failure.host, failure.port, null)
                is StartFailure.BindFailed -> BindFailed(it, failure.host, failure.port, failure.failure)
            }
        }
    }
}
