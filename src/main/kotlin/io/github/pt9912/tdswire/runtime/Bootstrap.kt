package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigLoader
import io.github.pt9912.tdswire.config.ConfigResult
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListAuthAdapter
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.session.AuthPool
import io.github.pt9912.tdswire.protocol.session.EncryptionPolicy
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.transport.listener.Delayer
import io.github.pt9912.tdswire.transport.listener.ListenerConfig
import io.github.pt9912.tdswire.transport.listener.ListenerEvent
import io.github.pt9912.tdswire.transport.listener.ListenerEvents
import io.github.pt9912.tdswire.transport.listener.ListenerTimes
import io.github.pt9912.tdswire.transport.listener.StartResult
import io.github.pt9912.tdswire.transport.listener.TdsListener
import io.github.pt9912.tdswire.transport.listener.TlsServerConfig
import io.github.pt9912.tdswire.transport.tls.MaterialResult
import io.github.pt9912.tdswire.transport.tls.TlsMaterial
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Alles, was der Prozess außer der Konfiguration festlegt und ein Test ersetzt: Log, Ereignisempfänger,
 * Zeitgeber der Fristen, der [authPool] (einer je Prozess), die Wahl des Adapters und das [backend] der
 * Anfragen. Die Voreinstellung ist der Betrieb: Log auf der Standardausgabe nur über [ServerLog.STDOUT], die
 * Benutzerliste als [UserListAuthAdapter], kein Backend.
 */
internal class Wiring(
    val log: ServerLog = ServerLog.NONE,
    val events: ListenerEvents = ListenerEvents.NONE,
    val delayer: Delayer = Delayer.SYSTEM,
    val authPool: AuthPool = AuthPool(),
    val adapter: (UserList) -> AuthAdapter = { UserListAuthAdapter(it) },
    val backend: QueryBackend? = null,
)

/** Ausgang des Starts: ein laufender [Server] oder der Abbruch; ein Abbruch hat Log-Zeilen geschrieben. */
internal sealed interface BootResult {
    class Running(val server: Server) : BootResult

    data object Aborted : BootResult
}

/**
 * Start-Reihenfolge des Prozesses (`ARC-008`, `TDS-FA-09.a`): JVM-Flag prüfen, Konfiguration laden und
 * validieren, TLS-Material laden, Startprobe und Binden im Listener (`SPEC-043`). Bis zum Binden läuft kein
 * Thread des Servers; scheitert ein Schritt, läuft danach keiner weiter und kein Port ist gebunden.
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

    suspend fun start(environment: Map<String, String>, properties: (String) -> String?, wiring: Wiring): BootResult {
        if (properties(TICKET_FLAG) != "0") return abort(wiring, "JVM-Flag -D$TICKET_FLAG=0 fehlt")
        return when (val loaded = ConfigLoader.load(environment)) {
            is ConfigResult.Invalid -> {
                loaded.issues.forEach { wiring.log.line("Konfiguration ungueltig: ${it.message}") }
                BootResult.Aborted
            }

            is ConfigResult.Loaded -> launch(loaded.config, wiring)
        }
    }

    private suspend fun launch(config: ServerConfig, wiring: Wiring): BootResult {
        val material = when (val loaded = config.tls?.let { loadMaterial(it) }) {
            null -> null

            is MaterialResult.Rejected ->
                return abort(wiring, "TLS-Konfiguration: ${loaded.diagnosis.part} (${loaded.diagnosis.form})")

            is MaterialResult.Loaded -> loaded.material
        }
        val listener = ListenerConfig(
            host = config.listen.host,
            port = config.listen.port,
            times = ListenerTimes(config.login.timeoutSeconds.seconds, Duration.ZERO, wiring.delayer),
            tls = material?.let { TlsServerConfig(it, TlsParameters.SPEC_023) },
        )
        val handler = HandshakeConnectionHandler(handlerSetup(config, material, wiring), wiring.backend)
        return when (val started = TdsListener.start(listener, handler, events(wiring))) {
            is StartResult.Failed -> abort(wiring, "Start gescheitert: ${started.failure.message}")
            is StartResult.Started -> running(config, started.listener, wiring)
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

    private fun handlerSetup(config: ServerConfig, material: TlsMaterial?, wiring: Wiring) = HandlerSetup(
        handshake = HandshakeConfig(encryptionPolicy = policyOf(config.encryption)),
        adapter = wiring.adapter(config.auth.users),
        authPool = wiring.authPool,
        login = config.login,
        tls = material,
        log = wiring.log,
    )

    private fun policyOf(setting: EncryptionSetting): EncryptionPolicy = when (setting) {
        EncryptionSetting.REQUIRED -> EncryptionPolicy.REQUIRED
        EncryptionSetting.TEST_UNENCRYPTED -> EncryptionPolicy.TEST_UNENCRYPTED
    }

    private fun events(wiring: Wiring): ListenerEvents = ListenerEvents { event ->
        wiring.events.onEvent(event)
        when (event) {
            is ListenerEvent.Closed ->
                event.failure?.let { wiring.log.line("Verbindung ${event.connection}: Handlerfehler ($it)") }

            is ListenerEvent.AcceptFailed -> wiring.log.line("Annehmen gescheitert (${event.failure})")

            is ListenerEvent.Accepted -> Unit
        }
    }

    private fun running(config: ServerConfig, listener: TdsListener, wiring: Wiring): BootResult {
        wiring.log.line("gestartet, Port ${listener.port}")
        if (config.encryption == EncryptionSetting.TEST_UNENCRYPTED) {
            wiring.log.line("ACHTUNG: ENCRYPTION_POLICY ist TEST_UNENCRYPTED, Verbindungen sind nicht verschluesselt")
        }
        if (config.auth.plainTestMode) {
            wiring.log.line("ACHTUNG: AUTH_PLAIN_TESTMODE ist aktiv, das Verfahren plain ist zugelassen")
        }
        return BootResult.Running(Server(listener, wiring.log))
    }

    private fun abort(wiring: Wiring, reason: String): BootResult {
        wiring.log.line(reason)
        return BootResult.Aborted
    }
}
