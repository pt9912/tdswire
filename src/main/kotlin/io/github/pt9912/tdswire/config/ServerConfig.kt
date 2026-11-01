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
    const val SIMULATION_DESCRIPTION_FILE = "SIMULATION_DESCRIPTION_FILE"
}

/** Obergrenze der Datei der Tabellenbeschreibung in Bytes, vor dem Parsen (`SPEC-035`, Voreinstellung 1 MiB). */
const val MAX_SIMULATION_FILE_BYTES = 1_048_576

/**
 * Verschlüsselungs-Richtlinie der Konfiguration (`SPEC-010`). [REQUIRED] ist der Standard;
 * [TEST_UNENCRYPTED] ist die ausdrücklich gewählte Abschwächung und steht im Start-Log.
 */
enum class EncryptionSetting { REQUIRED, TEST_UNENCRYPTED }

/**
 * Die Voreinstellungen der Konfiguration (`SPEC-005`, `SPEC-008`, `SPEC-010`, `SPEC-046`): Lader und Einstieg
 * nutzen dieselben Werte.
 */
internal object ConfigDefaults {
    const val HOST = "0.0.0.0"
    const val PORT = 1433
    const val LOGIN_TIMEOUT_SECONDS = 15
    const val MAX_PRELOGIN_MESSAGE_BYTES = 65536
    val ENCRYPTION = EncryptionSetting.REQUIRED
}

/** Bind-Adresse und Port; Port 0 lässt das System einen freien Port wählen. */
data class ListenSettings(val host: String = ConfigDefaults.HOST, val port: Int = ConfigDefaults.PORT)

/** Größen und Fristen vor abgeschlossenem Login (`SPEC-008`, `SPEC-046`). */
data class LoginSettings(
    val timeoutSeconds: Int = ConfigDefaults.LOGIN_TIMEOUT_SECONDS,
    val maxPreloginMessageBytes: Int = ConfigDefaults.MAX_PRELOGIN_MESSAGE_BYTES,
)

/** Die validierte Benutzerliste und der Schalter für das Verfahren `plain` (`ADR-0011`). */
class AuthSettings(val users: UserList, val plainTestMode: Boolean) {
    override fun toString(): String = "AuthSettings(verborgen)"
}

/** Inhalt des PKCS#12-Speichers und sein Kennwort; die Textdarstellung nennt keinen davon. */
class TlsKeyMaterial(val keystore: ByteArray, val password: PasswordChars) {
    override fun toString(): String = "TlsKeyMaterial(verborgen)"
}

/**
 * Die Konfiguration des Servers (`TDS-FA-09.a`). Sie entsteht über den Lader des Prozesses in `runtime` oder
 * über diesen Konstruktor; der Konstruktor prüft nichts, [ConfigValidation.check] und der Start weisen ungültige
 * Werte ab. Die Textdarstellung nennt keinen Wert (`TDS-QA-03`). [simulationFile] sind die Bytes der Datei der
 * Tabellenbeschreibung des Simulations-Backends (höchstens [MAX_SIMULATION_FILE_BYTES]) oder `null` ohne
 * Konfiguration. Eine Konfiguration gilt für einen Start: sobald der Start das TLS-Material lädt, löscht er das
 * Passwort in [tls]; bricht er vorher ab (JVM-Flag, Prüfung), bleibt es lesbar (Setzung).
 */
class ServerConfig(
    val listen: ListenSettings = ListenSettings(),
    val login: LoginSettings = LoginSettings(),
    val encryption: EncryptionSetting = ConfigDefaults.ENCRYPTION,
    val tls: TlsKeyMaterial? = null,
    val auth: AuthSettings,
    val simulationFile: ByteArray? = null,
) {
    override fun toString(): String = "ServerConfig(verborgen)"
}
