package io.github.pt9912.tdswire.config

import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList

/** Ausgang von [ConfigLoader.load]: eine gültige Konfiguration oder alle gefundenen Fehler. */
sealed interface ConfigResult {
    class Loaded(val config: ServerConfig) : ConfigResult {
        override fun toString(): String = "Loaded(verborgen)"
    }

    data class Invalid(val issues: List<ConfigIssue>) : ConfigResult
}

/**
 * Liest die Konfiguration aus der Umgebung und den Dateien, die sie nennt (`TDS-FA-09.a`).
 *
 * Wertebereiche und Paarungen prüft [ConfigValidation] je Schlüssel, die Voreinstellungen stehen in
 * [ConfigDefaults]; hier steht die Form der Schlüssel (Festlegungen, nicht gemessen):
 * - `LISTEN_PORT`, `LOGIN_TIMEOUT` und `MAX_PRELOGIN_MESSAGE_BYTES` bestehen aus ASCII-Ziffern.
 * - `LISTEN_HOST` Voreinstellung `0.0.0.0`.
 * - `ENCRYPTION_POLICY` genau `REQUIRED` oder `TEST_UNENCRYPTED`, Voreinstellung `REQUIRED`. `REQUIRED` verlangt
 *   `TLS_KEYSTORE_FILE` (PKCS#12) und `TLS_KEYSTORE_PASSWORD_FILE` (ein Zeilenende am Ende wird entfernt);
 *   `TEST_UNENCRYPTED` lässt beide nicht zu.
 * - `AUTH_USERS_FILE` Pflicht; `AUTH_PLAIN_TESTMODE` fehlt, ist leer, `false` oder genau `true`; jeder
 *   andere Wert, auch `TRUE`, ist ein Fehler und schaltet nie ein (`ADR-0011`).
 * - `SIMULATION_DESCRIPTION_FILE` optional (Name: Setzung, `ADR-0012` Offen 9): der Pfad der Datei der
 *   Tabellenbeschreibung des Simulations-Backends; fehlt der Schlüssel, läuft der Server ohne Simulations-Backend.
 *   Gesetzt ist der Wert nicht leer, die Datei lesbar und höchstens [MAX_SIMULATION_FILE_BYTES] groß (gelesen
 *   werden höchstens ein Byte mehr, die Datei wird nie ganz geladen).
 *
 * Die Benutzerliste wird als UTF-8 gelesen. Ein BOM am Anfang wird abgewiesen, nicht entfernt (Festlegung,
 * Spezifikation, Abschnitt Authentifizierungsadapter); die Zeilenenden bleiben unverändert, ein CR ist dann
 * ein Fehler der Validierung. Dateirechte der Dateien prüft der Lader nicht.
 */
object ConfigLoader {
    private val DIGITS = Regex("[0-9]{1,9}")

    fun load(environment: Map<String, String>): ConfigResult {
        val issues = mutableListOf<ConfigIssue>()
        val host = host(environment, issues)
        val port = number(environment, ConfigKeys.LISTEN_PORT, ConfigDefaults.PORT, ConfigValidation::port, issues)
        val timeout = number(
            environment,
            ConfigKeys.LOGIN_TIMEOUT,
            ConfigDefaults.LOGIN_TIMEOUT_SECONDS,
            ConfigValidation::loginTimeout,
            issues,
        )
        val prelogin = number(
            environment,
            ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES,
            ConfigDefaults.MAX_PRELOGIN_MESSAGE_BYTES,
            ConfigValidation::preloginBytes,
            issues,
        )
        val encryption = encryption(environment, issues)
        val tls = tls(environment, encryption, issues)
        val plain = plainTestMode(environment, issues)
        val users = ConfigFiles.users(environment, plain, issues)
        val simulation = ConfigFiles.simulation(environment, issues)
        return if (issues.isNotEmpty()) {
            ConfigResult.Invalid(issues.toList())
        } else {
            ConfigResult.Loaded(
                ServerConfig(
                    ListenSettings(checkNotNull(host), checkNotNull(port)),
                    LoginSettings(checkNotNull(timeout), checkNotNull(prelogin)),
                    checkNotNull(encryption),
                    tls,
                    AuthSettings(checkNotNull(users), checkNotNull(plain)),
                    simulation,
                ),
            )
        }
    }

    private fun host(environment: Map<String, String>, issues: MutableList<ConfigIssue>): String? {
        val value = environment[ConfigKeys.LISTEN_HOST] ?: return ConfigDefaults.HOST
        val issue = ConfigValidation.host(value)
        if (issue != null) issues += issue
        return value.takeIf { issue == null }
    }

    private fun number(
        environment: Map<String, String>,
        key: String,
        default: Int,
        rangeIssue: (Int) -> ConfigIssue?,
        issues: MutableList<ConfigIssue>,
    ): Int? {
        val value = environment[key] ?: return default
        val parsed = if (DIGITS.matches(value)) value.toInt() else null
        val issue = if (parsed == null) ConfigIssue(key, IssueKind.INVALID_VALUE) else rangeIssue(parsed)
        if (issue != null) issues += issue
        return parsed.takeIf { issue == null }
    }

    private fun encryption(environment: Map<String, String>, issues: MutableList<ConfigIssue>): EncryptionSetting? {
        val value = environment[ConfigKeys.ENCRYPTION_POLICY] ?: return ConfigDefaults.ENCRYPTION
        val setting = EncryptionSetting.entries.firstOrNull { it.name == value }
        if (setting == null) issues += ConfigIssue(ConfigKeys.ENCRYPTION_POLICY, IssueKind.INVALID_VALUE)
        return setting
    }

    private fun plainTestMode(environment: Map<String, String>, issues: MutableList<ConfigIssue>): Boolean? {
        val value = environment[ConfigKeys.AUTH_PLAIN_TESTMODE]
        val known = value.isNullOrEmpty() || value == "false" || UserList.plainTestModeFrom(value)
        if (!known) issues += ConfigIssue(ConfigKeys.AUTH_PLAIN_TESTMODE, IssueKind.INVALID_VALUE)
        return if (known) UserList.plainTestModeFrom(value) else null
    }

    private fun tls(
        environment: Map<String, String>,
        encryption: EncryptionSetting?,
        issues: MutableList<ConfigIssue>,
    ): TlsKeyMaterial? {
        if (encryption == null) return null
        val store = environment[ConfigKeys.TLS_KEYSTORE_FILE]
        val password = environment[ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE]
        issues += ConfigValidation.pairing(encryption, hasKeystore = store != null, hasPassword = password != null)
        return if (encryption == EncryptionSetting.REQUIRED && store != null && password != null) {
            material(store, password, issues)
        } else {
            null
        }
    }

    private fun material(store: String, password: String, issues: MutableList<ConfigIssue>): TlsKeyMaterial? {
        val keystore = ConfigFiles.readBytes(ConfigKeys.TLS_KEYSTORE_FILE, store, issues)
        val secret = ConfigFiles.readBytes(ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE, password, issues)
        return if (keystore == null || secret == null) null else TlsKeyMaterial(keystore, passwordOf(secret, issues))
    }

    private fun passwordOf(bytes: ByteArray, issues: MutableList<ConfigIssue>): PasswordChars {
        val text = ConfigFiles.decode(ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE, bytes, issues).orEmpty()
        val chars = text.removeSuffix("\n").toCharArray()
        return PasswordChars(chars).also { chars.fill('\u0000') }
    }
}
