package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.domain.telemetry.AcceptFailed
import io.github.pt9912.tdswire.domain.telemetry.AdapterFailed
import io.github.pt9912.tdswire.domain.telemetry.ClientProfile
import io.github.pt9912.tdswire.domain.telemetry.ConnectionFailed
import io.github.pt9912.tdswire.domain.telemetry.EncryptionNotOffered
import io.github.pt9912.tdswire.domain.telemetry.EventContext
import io.github.pt9912.tdswire.domain.telemetry.LoginRejected
import io.github.pt9912.tdswire.domain.telemetry.LoginRejectionReason
import io.github.pt9912.tdswire.domain.telemetry.RequestFailed
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
import io.github.pt9912.tdswire.protocol.session.LogEntry
import io.github.pt9912.tdswire.protocol.session.LoginRejection
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
 * Das Ereignis zu einem Ereignis des Listeners; `null`, wo der Listener meldet, ohne dass ein Betriebsereignis
 * entsteht (Annahme und regulärer Abschluss einer Verbindung).
 */
internal fun ListenerEvent.toEvent(at: Instant): TelemetryEvent? = when (this) {
    is ListenerEvent.Closed ->
        failure?.let { ConnectionFailed(at, EventContext(connectionId = connection), it) }

    is ListenerEvent.AcceptFailed -> AcceptFailed(at, failure)

    is ListenerEvent.Accepted -> null
}

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
