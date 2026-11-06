package io.github.pt9912.tdswire.config

import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList

/** Die Namen der Konfigurationsschlüssel, die ein [ConfigIssue] nennt (`TDS-FA-09.a`). */
internal object ConfigKeys {
    const val LISTEN_HOST = "LISTEN_HOST"
    const val LISTEN_PORT = "LISTEN_PORT"
    const val LOGIN_TIMEOUT = "LOGIN_TIMEOUT"
    const val ENCRYPTION_POLICY = "ENCRYPTION_POLICY"
    const val MAX_PRELOGIN_MESSAGE_BYTES = "MAX_PRELOGIN_MESSAGE_BYTES"
    const val TLS_KEYSTORE_FILE = "TLS_KEYSTORE_FILE"
    const val TLS_KEYSTORE_PASSWORD_FILE = "TLS_KEYSTORE_PASSWORD_FILE"
    const val AUTH_USERS_FILE = "AUTH_USERS_FILE"
    const val AUTH_PLAIN_TESTMODE = "AUTH_PLAIN_TESTMODE"
    const val QUERY_TIMEOUT = "QUERY_TIMEOUT"
    const val CANCEL_TIMEOUT = "CANCEL_TIMEOUT"
    const val MAX_CONNECTIONS = "MAX_CONNECTIONS"
    const val SHUTDOWN_GRACE = "SHUTDOWN_GRACE"
}

/**
 * Verschlüsselungs-Richtlinie der Konfiguration (`SPEC-010`). [REQUIRED] ist der Standard;
 * [TEST_UNENCRYPTED] ist die ausdrücklich gewählte Abschwächung und steht im Start-Log.
 */
public enum class EncryptionSetting { REQUIRED, TEST_UNENCRYPTED }

/**
 * Die Voreinstellungen der Konfiguration (`SPEC-005`, `SPEC-008`, `SPEC-010`, `SPEC-034`, `SPEC-046`,
 * `SPEC-047`): Die Konstruktoren der
 * Einstellungen nutzen dieselben Werte.
 */
internal object ConfigDefaults {
    const val HOST = "0.0.0.0"
    const val PORT = 1433
    const val LOGIN_TIMEOUT_SECONDS = 15
    const val MAX_PRELOGIN_MESSAGE_BYTES = 65536
    const val QUERY_TIMEOUT_SECONDS = 30
    const val CANCEL_TIMEOUT_SECONDS = 5
    const val MAX_CONNECTIONS = 1000
    const val SHUTDOWN_GRACE_SECONDS = 10
    val ENCRYPTION = EncryptionSetting.REQUIRED
}

/**
 * Bind-Adresse, Port und Grenze des Listeners. Port 0 lässt das System einen freien Port wählen. [maxConnections] ist
 * `MAX_CONNECTIONS` (`SPEC-034`): die Zahl der gleichzeitig aufgenommenen Verbindungen; eine weitere wird abgewiesen,
 * bis eine endet.
 */
public data class ListenSettings(
    val host: String = ConfigDefaults.HOST,
    val port: Int = ConfigDefaults.PORT,
    val maxConnections: Int = ConfigDefaults.MAX_CONNECTIONS,
)

/** Größen und Fristen vor abgeschlossenem Login (`SPEC-008`, `SPEC-046`). */
public data class LoginSettings(
    val timeoutSeconds: Int = ConfigDefaults.LOGIN_TIMEOUT_SECONDS,
    val maxPreloginMessageBytes: Int = ConfigDefaults.MAX_PRELOGIN_MESSAGE_BYTES,
)

/**
 * Fristen einer Anfrage in ganzen Sekunden (`SPEC-009`, `SPEC-033`, `SPEC-047`): [queryTimeoutSeconds] ist
 * `QUERY_TIMEOUT`, die Frist ab dem Beginn einer Anfrage; [cancelTimeoutSeconds] ist `CANCEL_TIMEOUT`, die Frist, die
 * ein Backend für den Abbruch hat, bevor die Verbindung schließt; [shutdownGraceSeconds] ist `SHUTDOWN_GRACE`, die
 * Frist, die eine laufende Anfrage beim geordneten Beenden hat (0 = sofort).
 */
public data class RequestSettings(
    val queryTimeoutSeconds: Int = ConfigDefaults.QUERY_TIMEOUT_SECONDS,
    val cancelTimeoutSeconds: Int = ConfigDefaults.CANCEL_TIMEOUT_SECONDS,
    val shutdownGraceSeconds: Int = ConfigDefaults.SHUTDOWN_GRACE_SECONDS,
)

/** Die validierte Benutzerliste und der Schalter für das Verfahren `plain` (`ADR-0011`). */
public class AuthSettings(public val users: UserList, public val plainTestMode: Boolean) {
    override fun toString(): String = "AuthSettings(verborgen)"
}

/** Inhalt des PKCS#12-Speichers und sein Kennwort; die Textdarstellung nennt keinen davon. */
public class TlsKeyMaterial(public val keystore: ByteArray, public val password: PasswordChars) {
    override fun toString(): String = "TlsKeyMaterial(verborgen)"
}

/**
 * Die Konfiguration des Servers (`TDS-FA-09.a`). Sie entsteht über diesen Konstruktor; er prüft nichts,
 * [ConfigValidation.check] und der Start weisen ungültige Werte ab. Die Textdarstellung nennt keinen Wert
 * (`TDS-QA-03`). Eine Konfiguration gilt für einen Start: sobald der Start das TLS-Material lädt, löscht er das
 * Passwort in [tls]; bricht er vorher ab (Prüfung der Konfiguration), bleibt es lesbar (Setzung).
 */
public class ServerConfig(
    public val listen: ListenSettings = ListenSettings(),
    public val login: LoginSettings = LoginSettings(),
    public val encryption: EncryptionSetting = ConfigDefaults.ENCRYPTION,
    public val tls: TlsKeyMaterial? = null,
    public val auth: AuthSettings,
    public val requests: RequestSettings = RequestSettings(),
) {
    override fun toString(): String = "ServerConfig(verborgen)"
}
