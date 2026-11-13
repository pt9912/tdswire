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
import io.github.pt9912.tdswire.domain.telemetry.ConfigEffective
import io.github.pt9912.tdswire.domain.telemetry.ConfigInvalid
import io.github.pt9912.tdswire.domain.telemetry.Instruments
import io.github.pt9912.tdswire.domain.telemetry.SecurityWeakened
import io.github.pt9912.tdswire.domain.telemetry.ServerStarted
import io.github.pt9912.tdswire.domain.telemetry.SimulationBackendReady
import io.github.pt9912.tdswire.domain.telemetry.StartRefusal
import io.github.pt9912.tdswire.domain.telemetry.StartRefused
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.domain.telemetry.TlsStartRejected
import io.github.pt9912.tdswire.domain.telemetry.WeakeningReason
import io.github.pt9912.tdswire.observability.Observer
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
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
 * [UserListAuthAdapter], kein Backend. Spans und Metriken (`SPEC-017` bis `SPEC-019`) beobachtet der Start nur, wenn
 * eine Senke gesetzt ist; ohne Senke beobachtet der Server nichts.
 */
internal class Wiring(
    val telemetry: TelemetrySink = TelemetrySink.NONE,
    val events: ListenerEvents = ListenerEvents.NONE,
    val delayer: Delayer = Delayer.SYSTEM,
    val authPool: AuthPool = AuthPool(),
    val adapter: (UserList) -> AuthAdapter = { UserListAuthAdapter(it) },
    val requests: RequestSource = RequestSource(),
)

private fun policyOf(setting: EncryptionSetting): EncryptionPolicy = when (setting) {
    EncryptionSetting.REQUIRED -> EncryptionPolicy.REQUIRED
    EncryptionSetting.TEST_UNENCRYPTED -> EncryptionPolicy.TEST_UNENCRYPTED
}

/**
 * Start-Reihenfolge (`ARC-008`, `TDS-FA-09.a`). [launch] ist der Teil, den `TdsServer.start` ruft: die
 * Konfiguration prüfen (`ConfigValidation.check`), das Simulations-Backend verdrahten (ein doppelter Tabellenname
 * bricht den Start ab, bevor ein Port gebunden wird), TLS-Material laden, Startprobe und Binden im Listener
 * (`SPEC-043`). Bis zum Binden läuft kein Thread des Servers; scheitert ein Schritt, läuft danach keiner weiter
 * und kein Port ist gebunden.
 *
 * Der Stop des Servers ist das geordnete Beenden (`SPEC-044`): `SHUTDOWN_GRACE` und `CANCEL_TIMEOUT` kommen aus
 * `ServerConfig.requests`, `MAX_CONNECTIONS` aus `ServerConfig.listen`.
 *
 * Der Start liest keine Systemeigenschaft und verlangt keine JVM-Option.
 */
internal object Bootstrap {
    private const val CONFIG_INVALID = "Konfiguration ungueltig"

    suspend fun launch(config: ServerConfig, wiring: Wiring): StartOutcome {
        val launch = Launch(config, wiring, Telemetry(wiring.telemetry))
        val issues = ConfigValidation.check(config)
        return when {
            issues.isNotEmpty() -> launch.reject(issues)
            else -> launch.bindBackend()
        }
    }

    /** Ein Start: die Konfiguration, die Verdrahtung und der Weg der Ereignisse zur Senke. */
    private class Launch(val config: ServerConfig, val wiring: Wiring, val telemetry: Telemetry) {
        /** Der Beobachter dieses Starts; `null` ohne Senke: dann entsteht kein Span und keine Metrik. */
        val observer: Observer? = if (wiring.telemetry === TelemetrySink.NONE) null else Observer(telemetry::emit)

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
                times = ListenerTimes(
                    loginTimeout = config.login.timeoutSeconds.seconds,
                    shutdownGrace = config.requests.timeouts.shutdownGraceSeconds.seconds,
                    delayer = wiring.delayer,
                    cancelTimeout = config.requests.timeouts.cancelTimeoutSeconds.seconds,
                ),
                tls = material?.let { TlsServerConfig(it, TlsParameters.SPEC_023) },
                maxConnections = config.listen.maxConnections,
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
        ).also {
            it.requests = config.requests
            it.instruments = observer ?: Instruments.NONE
        }

        private fun events(): ListenerEvents {
            prepareAcceptFailure()
            return ListenerEvents { event ->
                wiring.events.onEvent(event)
                telemetry.emit { at -> event.toEvent(at) }
                telemetry.emit { at -> event.toStateEvent(at) }
            }
        }

        private fun running(listener: TdsListener): StartOutcome {
            telemetry.emit { ConfigEffective(it, config.effectiveSettings()) }
            telemetry.emit { ServerStarted(it, config.listen.host, listener.port) }
            if (config.encryption == EncryptionSetting.TEST_UNENCRYPTED) {
                telemetry.emit { SecurityWeakened(it, WeakeningReason.ENCRYPTION_TEST_UNENCRYPTED) }
            }
            if (config.auth.plainTestMode) telemetry.emit { SecurityWeakened(it, WeakeningReason.PLAIN_TEST_MODE) }
            return StartOutcome.Running(TdsServer(listener, telemetry, snapshots()))
        }

        /**
         * Die Aufgabe, die alle `metricsIntervalSeconds` den Schnappschuss der Metriken gibt; `null` bei Abstand 0 oder
         * ohne Beobachter. Sie endet mit `TdsServer.stop`.
         */
        private fun snapshots(dispatcher: CoroutineDispatcher = Dispatchers.Default): Job? {
            val source = observer
            val interval = config.requests.observation.metricsIntervalSeconds.seconds
            if (source == null || config.requests.observation.metricsIntervalSeconds == 0) return null
            return CoroutineScope(dispatcher).launch {
                while (true) {
                    delay(interval)
                    source.snapshot()
                }
            }
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
