package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.domain.telemetry.AcceptFailed
import io.github.pt9912.tdswire.domain.telemetry.AdapterFailed
import io.github.pt9912.tdswire.domain.telemetry.ClientProfile
import io.github.pt9912.tdswire.domain.telemetry.ConnectionCloseReason
import io.github.pt9912.tdswire.domain.telemetry.ConnectionClosed
import io.github.pt9912.tdswire.domain.telemetry.ConnectionFailed
import io.github.pt9912.tdswire.domain.telemetry.ConnectionOpened
import io.github.pt9912.tdswire.domain.telemetry.EffectiveSettings
import io.github.pt9912.tdswire.domain.telemetry.EncryptionMode
import io.github.pt9912.tdswire.domain.telemetry.EncryptionNotOffered
import io.github.pt9912.tdswire.domain.telemetry.EventContext
import io.github.pt9912.tdswire.domain.telemetry.KeystoreState
import io.github.pt9912.tdswire.domain.telemetry.LoginRejected
import io.github.pt9912.tdswire.domain.telemetry.LoginRejectionReason
import io.github.pt9912.tdswire.domain.telemetry.RequestFailed
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
import io.github.pt9912.tdswire.protocol.session.LogEntry
import io.github.pt9912.tdswire.protocol.session.LoginRejection
import io.github.pt9912.tdswire.transport.listener.ConnectionEnd
import io.github.pt9912.tdswire.transport.listener.ListenerEvent
import io.github.pt9912.tdswire.transport.tls.TlsPath
import java.io.IOException
import java.time.Instant

/**
 * Die Abbildung der internen Anschlüsse (Handshake, Fehler-Mapping, Listener) auf das öffentliche Ereignismodell.
 * Jede Abbildung ist eine erschöpfende Fallunterscheidung: eine neue Art eines Anschlusses bricht die Übersetzung,
 * bis sie hier abgebildet ist. Kein Feld des Modells nimmt einen Text auf, der aus Eingabe oder Ausnahme stammt.
 */
internal fun LogEntry.toEvent(at: Instant, context: EventContext): TelemetryEvent = when (this) {
    is LogEntry.LoginRejected -> LoginRejected(at, context, rejection.reason())
    is LogEntry.AdapterFailed -> AdapterFailed(at, context, exceptionType)
    LogEntry.EncryptionNotOffered -> EncryptionNotOffered(at, context)
}

private fun LoginRejection.reason(): LoginRejectionReason = when (this) {
    LoginRejection.UNKNOWN_USER -> LoginRejectionReason.UNKNOWN_USER
    LoginRejection.WRONG_PASSWORD -> LoginRejectionReason.WRONG_PASSWORD
    LoginRejection.INPUT_TOO_LONG -> LoginRejectionReason.INPUT_TOO_LONG
    LoginRejection.UNSUPPORTED_INTEGRATED_SECURITY -> LoginRejectionReason.UNSUPPORTED_INTEGRATED_SECURITY
    LoginRejection.UNSUPPORTED_FEDERATED_AUTHENTICATION -> LoginRejectionReason.UNSUPPORTED_FEDERATED_AUTHENTICATION
    LoginRejection.UNSUPPORTED_PASSWORD_CHANGE -> LoginRejectionReason.UNSUPPORTED_PASSWORD_CHANGE
    LoginRejection.ADAPTER_FAILURE -> LoginRejectionReason.ADAPTER_FAILURE
}

internal fun ErrorLogEntry.toEvent(at: Instant, context: EventContext): TelemetryEvent =
    RequestFailed(at, context, code.name, cause)

/**
 * Das Fehlerereignis zu einem Ereignis des Listeners; `null`, wo der Listener ohne Fehler meldet (Annahme und Ende
 * einer Verbindung ohne Ausnahme des Handlers).
 */
internal fun ListenerEvent.toEvent(at: Instant): TelemetryEvent? = when (this) {
    is ListenerEvent.Closed ->
        failure?.let { ConnectionFailed(at, EventContext(connectionId = connection), it) }

    is ListenerEvent.AcceptFailed -> AcceptFailed(at, failure)

    is ListenerEvent.Accepted -> null
}

/**
 * Bereitet den Weg des Annahmefehlers von [ListenerEvent.AcceptFailed] bis zum Ereignis vor (`ADR-0043`): ist das
 * Limit offener Dateien erschöpft, kann die JVM keine Klassendatei mehr öffnen, und eine beim Fehler erstmals
 * benötigte Klasse endet mit `NoClassDefFoundError`. Der Aufruf baut die Ereignisse einmal und lädt ihre Klassen;
 * nichts davon erreicht die Senke.
 */
internal fun prepareAcceptFailure() {
    val event = ListenerEvent.AcceptFailed(IOException::class.java.name)
    check(event.toEvent(Instant.EPOCH) != null && event.toStateEvent(Instant.EPOCH) == null)
}

/**
 * Das Zustandsereignis zu einem Ereignis des Listeners: Annahme und Ende einer Verbindung; `null` für den
 * Annahmefehler, der kein Zustand einer Verbindung ist.
 */
internal fun ListenerEvent.toStateEvent(at: Instant): TelemetryEvent? = when (this) {
    is ListenerEvent.Accepted -> ConnectionOpened(at, EventContext(connectionId = connection))
    is ListenerEvent.Closed -> ConnectionClosed(at, EventContext(connectionId = connection), end.closeReason())
    is ListenerEvent.AcceptFailed -> null
}

private fun ConnectionEnd.closeReason(): ConnectionCloseReason = when (this) {
    ConnectionEnd.HANDLER_RETURNED -> ConnectionCloseReason.HANDLER_RETURNED
    ConnectionEnd.HANDLER_FAILED -> ConnectionCloseReason.HANDLER_FAILED
    ConnectionEnd.LOGIN_TIMEOUT -> ConnectionCloseReason.LOGIN_TIMEOUT
    ConnectionEnd.SHUTDOWN -> ConnectionCloseReason.SHUTDOWN
    ConnectionEnd.SHUTDOWN_GRACE_EXPIRED -> ConnectionCloseReason.SHUTDOWN_GRACE_EXPIRED
    ConnectionEnd.REFUSED_AT_SHUTDOWN -> ConnectionCloseReason.REFUSED_AT_SHUTDOWN
    ConnectionEnd.REFUSED_AT_LIMIT -> ConnectionCloseReason.REFUSED_AT_LIMIT
}

/**
 * Die Einstellungen, mit denen der Server läuft, für das Ereignis `CONFIG_EFFECTIVE`. Aufgenommen sind nur Zahlen,
 * Aufzählungen und Schalter (Typen von [EffectiveSettings]); Bind-Adresse und Port stehen in `SERVER_STARTED`. Für den
 * Schlüsselspeicher steht nur, ob er konfiguriert ist (`TDS-QA-03`).
 */
internal fun ServerConfig.effectiveSettings(): EffectiveSettings = EffectiveSettings(
    loginTimeoutSeconds = login.timeoutSeconds,
    maxPreloginMessageBytes = login.maxPreloginMessageBytes,
    encryption = when (encryption) {
        EncryptionSetting.REQUIRED -> EncryptionMode.REQUIRED
        EncryptionSetting.TEST_UNENCRYPTED -> EncryptionMode.TEST_UNENCRYPTED
    },
    keystore = if (tls != null) KeystoreState.SET else KeystoreState.UNSET,
    plainTestMode = auth.plainTestMode,
    queryTimeoutSeconds = requests.queryTimeoutSeconds,
    cancelTimeoutSeconds = requests.cancelTimeoutSeconds,
    shutdownGraceSeconds = requests.shutdownGraceSeconds,
    maxConnections = listen.maxConnections,
    maxPreparedHandles = requests.maxPreparedHandles,
    maxMessageBytes = requests.maxMessageBytes,
    metricsIntervalSeconds = requests.metricsIntervalSeconds,
)

/**
 * Das Profil des Clients aus dem, was der Handler weiß (`TDS-FA-09.a`): TLS ab dem ersten Byte ist TDS 8.0; im Pfad
 * TDS 7.4 steht der ausgehandelte Modus erst nach dem PRELOGIN fest, davor und für Ereignisse des Handshakes ist es
 * `unknown`.
 */
internal fun profileOf(path: TlsPath, negotiated: NegotiatedEncryption?): String = when {
    path == TlsPath.FROM_FIRST_BYTE -> ClientProfile.TDS_8_0
    negotiated == null -> ClientProfile.UNKNOWN
    else -> ClientProfile.tds74(negotiated.name.lowercase())
}
