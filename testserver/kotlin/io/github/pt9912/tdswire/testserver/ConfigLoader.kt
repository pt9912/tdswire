package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.LoginSettings
import io.github.pt9912.tdswire.config.ObservationSettings
import io.github.pt9912.tdswire.config.RequestLimits
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.RequestTimeouts
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList

/**
 * Ausgang von [ConfigLoader.load]: die Konfiguration samt den Bytes der Datei der Tabellenbeschreibung (oder
 * `null` ohne Schlüssel) oder alle gefundenen Fehler der Form und der Dateien.
 */
internal sealed interface ConfigResult {
    class Loaded(val config: ServerConfig, val simulationFile: ByteArray?) : ConfigResult {
        override fun toString(): String = "Loaded(verborgen)"
    }

    data class Invalid(val issues: List<ConfigIssue>) : ConfigResult
}

/**
 * Liest die Konfiguration des Testservers aus der Umgebung und den Dateien, die sie nennt (`TDS-FA-09.a`). Die
 * Bibliothek liest weder Umgebung noch Datei (`SPEC-005`, `SPEC-010`, `SPEC-051`).
 *
 * Der Lader prüft die Form der Schlüssel und liest die Dateien; die Wertebereiche (Port, Frist, Größe der
 * PRELOGIN-Nachricht, leere Bind-Adresse) prüft der Start der Bibliothek (Befunde als `Aborted.issues` von
 * `TdsServer.start`). Ein Wert mit falscher Form steht als `INVALID_VALUE` im Ergebnis und wird durch die
 * Voreinstellung ersetzt, damit er nicht doppelt gemeldet wird. Formen (Festlegungen, nicht gemessen):
 * - `LISTEN_PORT`, `LOGIN_TIMEOUT`, `QUERY_TIMEOUT`, `CANCEL_TIMEOUT`, `MAX_CONNECTIONS`, `SHUTDOWN_GRACE`,
 *   `MAX_PREPARED_HANDLES`, `MAX_MESSAGE_BYTES`, `METRICS_INTERVAL_SECONDS` und `MAX_PRELOGIN_MESSAGE_BYTES` bestehen
 *   aus ASCII-Ziffern.
 * - `LISTEN_HOST` Voreinstellung `0.0.0.0`.
 * - `ENCRYPTION_POLICY` genau `REQUIRED` oder `TEST_UNENCRYPTED`, Voreinstellung `REQUIRED`. `REQUIRED` verlangt
 *   `TLS_KEYSTORE_FILE` (PKCS#12) und `TLS_KEYSTORE_PASSWORD_FILE` (ein Zeilenende am Ende wird entfernt);
 *   `TEST_UNENCRYPTED` lässt beide nicht zu.
 * - `AUTH_USERS_FILE` Pflicht; `AUTH_PLAIN_TESTMODE` fehlt, ist leer, `false` oder genau `true`; jeder
 *   andere Wert, auch `TRUE`, ist ein Fehler und schaltet nie ein (`ADR-0011`).
 * - `SIMULATION_DESCRIPTION_FILE` optional (Name: Setzung, `ADR-0012` Offen 9): der Pfad der Datei der
 *   Tabellenbeschreibung des Simulations-Backends; fehlt der Schlüssel, läuft der Server ohne Tabellen aus der
 *   Datei. Gesetzt ist der Wert nicht leer, die Datei lesbar und höchstens `MAX_SIMULATION_FILE_BYTES` groß
 *   (gelesen werden höchstens ein Byte mehr, die Datei wird nie ganz geladen).
 *
 * Die Benutzerliste wird als UTF-8 gelesen. Ein BOM am Anfang wird abgewiesen, nicht entfernt (Festlegung,
 * Spezifikation, Abschnitt Authentifizierungsadapter); die Zeilenenden bleiben unverändert, ein CR ist dann
 * ein Fehler der Validierung. Dateirechte der Dateien prüft der Lader nicht.
 */
internal object ConfigLoader {
    private val DIGITS = Regex("[0-9]{1,9}")
    private val DEFAULT_LISTEN = ListenSettings()
    private val DEFAULT_LOGIN = LoginSettings()
    private val DEFAULT_TIMEOUTS = RequestTimeouts()
    private val DEFAULT_LIMITS = RequestLimits()
    private val DEFAULT_OBSERVATION = ObservationSettings()

    fun load(environment: Map<String, String>): ConfigResult {
        val issues = mutableListOf<ConfigIssue>()
        val host = environment[EnvKeys.LISTEN_HOST] ?: DEFAULT_LISTEN.host
        val port = number(environment, EnvKeys.LISTEN_PORT, DEFAULT_LISTEN.port, issues)
        val timeout = number(environment, EnvKeys.LOGIN_TIMEOUT, DEFAULT_LOGIN.timeoutSeconds, issues)
        val prelogin = number(
            environment,
            EnvKeys.MAX_PRELOGIN_MESSAGE_BYTES,
            DEFAULT_LOGIN.maxPreloginMessageBytes,
            issues,
        )
        val queryTimeout = number(environment, EnvKeys.QUERY_TIMEOUT, DEFAULT_TIMEOUTS.queryTimeoutSeconds, issues)
        val cancelTimeout = number(environment, EnvKeys.CANCEL_TIMEOUT, DEFAULT_TIMEOUTS.cancelTimeoutSeconds, issues)
        val maxConnections = number(environment, EnvKeys.MAX_CONNECTIONS, DEFAULT_LISTEN.maxConnections, issues)
        val grace = number(environment, EnvKeys.SHUTDOWN_GRACE, DEFAULT_TIMEOUTS.shutdownGraceSeconds, issues)
        val handles = number(environment, EnvKeys.MAX_PREPARED_HANDLES, DEFAULT_LIMITS.maxPreparedHandles, issues)
        val messageBytes = number(environment, EnvKeys.MAX_MESSAGE_BYTES, DEFAULT_LIMITS.maxMessageBytes, issues)
        val metrics = number(
            environment,
            EnvKeys.METRICS_INTERVAL_SECONDS,
            DEFAULT_OBSERVATION.metricsIntervalSeconds,
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
            val auth = AuthSettings(checkNotNull(users), checkNotNull(plain))
            val config = ServerConfig(
                ListenSettings(host = host, port = port, maxConnections = maxConnections),
                LoginSettings(timeoutSeconds = timeout, maxPreloginMessageBytes = prelogin),
                checkNotNull(encryption),
                tls,
                auth,
                RequestSettings(
                    timeouts = RequestTimeouts(
                        queryTimeoutSeconds = queryTimeout,
                        cancelTimeoutSeconds = cancelTimeout,
                        shutdownGraceSeconds = grace,
                    ),
                    limits = RequestLimits(maxPreparedHandles = handles, maxMessageBytes = messageBytes),
                    observation = ObservationSettings(metricsIntervalSeconds = metrics),
                ),
            )
            ConfigResult.Loaded(config, simulation)
        }
    }

    private fun number(
        environment: Map<String, String>,
        key: String,
        default: Int,
        issues: MutableList<ConfigIssue>,
    ): Int {
        val value = environment[key] ?: return default
        val valid = DIGITS.matches(value)
        if (!valid) issues += ConfigIssue(key, IssueKind.INVALID_VALUE)
        return if (valid) value.toInt() else default
    }

    private fun encryption(environment: Map<String, String>, issues: MutableList<ConfigIssue>): EncryptionSetting? {
        val value = environment[EnvKeys.ENCRYPTION_POLICY] ?: return EncryptionSetting.REQUIRED
        val setting = EncryptionSetting.entries.firstOrNull { it.name == value }
        if (setting == null) issues += ConfigIssue(EnvKeys.ENCRYPTION_POLICY, IssueKind.INVALID_VALUE)
        return setting
    }

    private fun plainTestMode(environment: Map<String, String>, issues: MutableList<ConfigIssue>): Boolean? {
        val value = environment[EnvKeys.AUTH_PLAIN_TESTMODE]
        val known = value.isNullOrEmpty() || value == "false" || UserList.plainTestModeFrom(value)
        if (!known) issues += ConfigIssue(EnvKeys.AUTH_PLAIN_TESTMODE, IssueKind.INVALID_VALUE)
        return if (known) UserList.plainTestModeFrom(value) else null
    }

    /**
     * Schlüsselspeicher und Kennwortdatei nach der Richtlinie (`SPEC-010`): `REQUIRED` verlangt beide,
     * `TEST_UNENCRYPTED` lässt beide nicht zu. Der Start der Bibliothek meldet dieselbe Paarung, wenn das
     * Material fehlt; hier steht sie je Schlüssel, weil der Lader die Dateien einzeln nennt.
     */
    private fun tls(
        environment: Map<String, String>,
        encryption: EncryptionSetting?,
        issues: MutableList<ConfigIssue>,
    ): TlsKeyMaterial? {
        if (encryption == null) return null
        val store = environment[EnvKeys.TLS_KEYSTORE_FILE]
        val password = environment[EnvKeys.TLS_KEYSTORE_PASSWORD_FILE]
        val keys = listOf(EnvKeys.TLS_KEYSTORE_FILE to store, EnvKeys.TLS_KEYSTORE_PASSWORD_FILE to password)
        keys.forEach { (key, path) ->
            when {
                encryption == EncryptionSetting.TEST_UNENCRYPTED && path != null ->
                    issues += ConfigIssue(key, IssueKind.CONFLICT)

                encryption == EncryptionSetting.REQUIRED && path == null ->
                    issues += ConfigIssue(key, IssueKind.MISSING)
            }
        }
        return if (encryption == EncryptionSetting.REQUIRED && store != null && password != null) {
            material(store, password, issues)
        } else {
            null
        }
    }

    private fun material(store: String, password: String, issues: MutableList<ConfigIssue>): TlsKeyMaterial? {
        val keystore = ConfigFiles.readBytes(EnvKeys.TLS_KEYSTORE_FILE, store, issues)
        val secret = ConfigFiles.readBytes(EnvKeys.TLS_KEYSTORE_PASSWORD_FILE, password, issues)
        return if (keystore == null || secret == null) null else TlsKeyMaterial(keystore, passwordOf(secret, issues))
    }

    private fun passwordOf(bytes: ByteArray, issues: MutableList<ConfigIssue>): PasswordChars {
        val chars = passwordText(bytes, issues).toCharArray()
        return PasswordChars(chars).also { chars.fill('\u0000') }
    }

    /**
     * Der Inhalt der Kennwortdatei ohne Zeilenende am Ende; ein Test liest ihn, `PasswordChars` gibt ihn nicht her.
     * Grenze: dass dieser Text im `PasswordChars` des Materials ankommt, hält dieser Zugang nicht; das belegen die
     * Prozesstests des TLS-Starts (`ServerSmokeTest`, `Tds8ProcessTest`).
     */
    internal fun passwordText(bytes: ByteArray, issues: MutableList<ConfigIssue>): String =
        ConfigFiles.decode(EnvKeys.TLS_KEYSTORE_PASSWORD_FILE, bytes, issues).orEmpty().removeSuffix("\n")
}
