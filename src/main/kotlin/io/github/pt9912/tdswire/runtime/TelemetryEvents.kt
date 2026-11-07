package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.domain.telemetry.AcceptFailed
import io.github.pt9912.tdswire.domain.telemetry.AdapterFailed
import io.github.pt9912.tdswire.domain.telemetry.ClientProfile
import io.github.pt9912.tdswire.domain.telemetry.ConnectionCloseReason
import io.github.pt9912.tdswire.domain.telemetry.ConnectionClosed
import io.github.pt9912.tdswire.domain.telemetry.ConnectionFailed
import io.github.pt9912.tdswire.domain.telemetry.ConnectionOpened
import io.github.pt9912.tdswire.domain.telemetry.EncryptionNotOffered
import io.github.pt9912.tdswire.domain.telemetry.EventContext
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

private const val KEYSTORE_SET = "SET"
private const val KEYSTORE_UNSET = "UNSET"

/**
 * Die Einstellungen, mit denen der Server läuft, als Schlüssel und Wert für das Ereignis `CONFIG_EFFECTIVE`.
 * Aufgenommen sind nur Zahlen, Namen von Aufzählungen und Schalter; Bind-Adresse und Port stehen in
 * `SERVER_STARTED`. Für den Schlüsselspeicher steht `SET` oder `UNSET`; Pfad, Inhalt, Passwort und Benutzerliste haben
 * kein Feld (`TDS-QA-03`).
 */
internal fun ServerConfig.effectiveSettings(): Map<String, String> = linkedMapOf(
    ConfigKeys.LOGIN_TIMEOUT to login.timeoutSeconds.toString(),
    ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES to login.maxPreloginMessageBytes.toString(),
    ConfigKeys.ENCRYPTION_POLICY to encryption.name,
    ConfigKeys.TLS_KEYSTORE_FILE to if (tls != null) KEYSTORE_SET else KEYSTORE_UNSET,
    ConfigKeys.AUTH_PLAIN_TESTMODE to auth.plainTestMode.toString(),
    ConfigKeys.QUERY_TIMEOUT to requests.queryTimeoutSeconds.toString(),
    ConfigKeys.CANCEL_TIMEOUT to requests.cancelTimeoutSeconds.toString(),
    ConfigKeys.SHUTDOWN_GRACE to requests.shutdownGraceSeconds.toString(),
    ConfigKeys.MAX_CONNECTIONS to listen.maxConnections.toString(),
    ConfigKeys.MAX_PREPARED_HANDLES to requests.maxPreparedHandles.toString(),
    ConfigKeys.MAX_MESSAGE_BYTES to requests.maxMessageBytes.toString(),
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
