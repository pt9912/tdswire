package io.github.pt9912.tdswire.config

import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList

/** Die Namen der Konfigurationsschlüssel; alle kommen aus der Umgebung des Prozesses (`TDS-FA-09.a`). */
object ConfigKeys {
    const val LISTEN_HOST = "LISTEN_HOST"
    const val LISTEN_PORT = "LISTEN_PORT"
    const val LOGIN_TIMEOUT = "LOGIN_TIMEOUT"
    const val ENCRYPTION_POLICY = "ENCRYPTION_POLICY"
    const val MAX_PRELOGIN_MESSAGE_BYTES = "MAX_PRELOGIN_MESSAGE_BYTES"
    const val TLS_KEYSTORE_FILE = "TLS_KEYSTORE_FILE"
    const val TLS_KEYSTORE_PASSWORD_FILE = "TLS_KEYSTORE_PASSWORD_FILE"
    const val AUTH_USERS_FILE = "AUTH_USERS_FILE"
    const val AUTH_PLAIN_TESTMODE = "AUTH_PLAIN_TESTMODE"
}

/**
 * Verschlüsselungs-Richtlinie der Konfiguration (`SPEC-010`). [REQUIRED] ist der Standard;
 * [TEST_UNENCRYPTED] ist die ausdrücklich gewählte Abschwächung und steht im Start-Log.
 */
enum class EncryptionSetting { REQUIRED, TEST_UNENCRYPTED }

/** Bind-Adresse und Port; Port 0 lässt das System einen freien Port wählen. */
data class ListenSettings(val host: String, val port: Int)

/** Größen und Fristen vor abgeschlossenem Login (`SPEC-008`, `SPEC-046`). */
data class LoginSettings(val timeoutSeconds: Int, val maxPreloginMessageBytes: Int)

/** Die validierte Benutzerliste und der Schalter für das Verfahren `plain` (`ADR-0011`). */
class AuthSettings(val users: UserList, val plainTestMode: Boolean) {
    override fun toString(): String = "AuthSettings(verborgen)"
}

/** Inhalt des PKCS#12-Speichers und sein Kennwort; die Textdarstellung nennt keinen davon. */
class TlsKeyMaterial(val keystore: ByteArray, val password: PasswordChars) {
    override fun toString(): String = "TlsKeyMaterial(verborgen)"
}

/**
 * Die validierte Konfiguration des Serverprozesses (`TDS-FA-09.a`). Sie entsteht nur über
 * [ConfigLoader.load]; die Textdarstellung nennt keinen Wert (`TDS-QA-03`).
 */
class ServerConfig(
    val listen: ListenSettings,
    val login: LoginSettings,
    val encryption: EncryptionSetting,
    val tls: TlsKeyMaterial?,
    val auth: AuthSettings,
) {
    override fun toString(): String = "ServerConfig(verborgen)"
}
