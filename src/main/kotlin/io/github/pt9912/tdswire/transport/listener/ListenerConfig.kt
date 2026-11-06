package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.transport.tls.TlsDiagnosis
import io.github.pt9912.tdswire.transport.tls.TlsMaterial
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Wartet eine Frist ab; der Listener wartet nur über diese Schnittstelle, damit Tests die Zeit steuern. */
internal fun interface Delayer {
    suspend fun delay(duration: Duration)

    companion object {
        val SYSTEM = Delayer { kotlinx.coroutines.delay(it) }
    }
}

/**
 * Fristen des Listeners. [loginTimeout] (`LOGIN_TIMEOUT`, `SPEC-008`), [shutdownGrace]
 * (`SHUTDOWN_GRACE`, `SPEC-047`; [Duration.ZERO] = sofort) und [cancelTimeout] (`CANCEL_TIMEOUT`, `SPEC-033`)
 * tragen die vorläufigen Werte der Spezifikation; [delayer] wartet sie ab. [cancelTimeout] gilt im Beenden: nach
 * Ablauf von [shutdownGrace] hat eine Verbindung mit angemeldetem Abbruch so lange Zeit, bevor der Listener sie trennt.
 */
internal data class ListenerTimes(
    val loginTimeout: Duration = 15.seconds,
    val shutdownGrace: Duration = 10.seconds,
    val delayer: Delayer = Delayer.SYSTEM,
    val cancelTimeout: Duration = 5.seconds,
)

/** TLS-Material und -Parameter, gegen die die Startprobe vor dem Binden läuft (`SPEC-043`). */
internal data class TlsServerConfig(val material: TlsMaterial, val parameters: TlsParameters)

/**
 * Was der Listener von außen bekommt: eine validierte Bind-Adresse [host] und [port] (0 = ein freier
 * Port), die Fristen, optional die TLS-Konfiguration für die Startprobe und den Dispatcher der
 * Verbindungs-Scopes. [maxConnections] ist `MAX_CONNECTIONS` (`SPEC-034`): der Listener nimmt höchstens so viele
 * Verbindungen zugleich auf. Wer die Werte aus Umgebung oder Datei liest, gehört dem Package `config`.
 * Ohne [tls] läuft keine Probe; ob und wie eine Verbindung verschlüsselt wird, entscheidet der Handler.
 */
internal data class ListenerConfig(
    val host: String,
    val port: Int,
    val times: ListenerTimes = ListenerTimes(),
    val tls: TlsServerConfig? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val maxConnections: Int = Int.MAX_VALUE,
)

/** Warum der Start nicht gelingt; [message] nennt Teil, Adresse oder Port, nie Pfad oder Inhalt (`SPEC-043`). */
internal sealed interface StartFailure {
    val message: String

    /** Die Startprobe der TLS-Konfiguration ist gescheitert; es wurde kein Port gebunden. */
    data class Tls(val diagnosis: TlsDiagnosis) : StartFailure {
        override val message: String get() = "TLS-Konfiguration: ${diagnosis.part} (${diagnosis.form})"
    }

    /** Der Port an [host] ist belegt. */
    data class PortInUse(val host: String, val port: Int) : StartFailure {
        override val message: String get() = "Port belegt: $host:$port"
    }

    /** Das Binden an [host] und [port] scheiterte aus anderem Grund; [failure] ist der Ausnahmetyp. */
    data class BindFailed(val host: String, val port: Int, val failure: String) : StartFailure {
        override val message: String get() = "Binden an $host:$port gescheitert: $failure"
    }
}

/** Ausgang von [TdsListener.start]. */
internal sealed interface StartResult {
    class Started(val listener: TdsListener) : StartResult

    data class Failed(val failure: StartFailure) : StartResult
}
