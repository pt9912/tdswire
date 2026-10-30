package io.github.pt9912.tdswire.config

import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList

/** Die Art eines Konfigurationsfehlers; der Wert des Schlüssels erscheint nie in der Diagnose. */
enum class IssueKind {
    /** Eine Pflichtoption fehlt. */
    MISSING,

    /** Der Wert hat nicht die Form des Schlüssels. */
    INVALID_VALUE,

    /** Der Wert hat die Form, liegt aber außerhalb des Bereichs. */
    OUT_OF_RANGE,

    /** Der Schlüssel ist in der gewählten Betriebsart nicht erlaubt. */
    CONFLICT,

    /** Die genannte Datei ist nicht lesbar. */
    UNREADABLE,

    /** Die Datei ist kein gültiges UTF-8. */
    NOT_UTF8,

    /** Die Datei beginnt mit einem UTF-8-BOM. */
    BYTE_ORDER_MARK,

    /** Die Benutzerliste ist ungültig; [ConfigIssue.detail] nennt Fehlerart und Zeilennummer. */
    USER_LIST_INVALID,
}

/**
 * Ein Konfigurationsfehler: Schlüssel und Art, bei der Benutzerliste Fehlerart und Zeile in [detail]. Er
 * trägt nie einen Wert, einen Dateipfad, einen Prüfwert oder ein Passwort (`TDS-QA-03`).
 */
data class ConfigIssue(val key: String, val kind: IssueKind, val detail: String? = null) {
    val message: String get() = if (detail == null) "$key: $kind" else "$key: $kind ($detail)"
}

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
 * Wertebereiche und Voreinstellungen sind Festlegungen, nicht gemessen:
 * - `LISTEN_PORT` 0 bis 65535, Voreinstellung 1433 (`SPEC-005`); 0 wählt einen freien Port.
 * - `LISTEN_HOST` nicht leer, Voreinstellung `0.0.0.0`.
 * - `LOGIN_TIMEOUT` ganze Sekunden von 1 bis 3600, Voreinstellung 15 (`SPEC-008`, vorläufig).
 * - `MAX_PRELOGIN_MESSAGE_BYTES` 512 bis 65536, Voreinstellung 65536 (`SPEC-046`, vorläufig); die obere
 *   Grenze ist die der PRELOGIN-Verhandlung.
 * - `ENCRYPTION_POLICY` genau `REQUIRED` oder `TEST_UNENCRYPTED`, Voreinstellung `REQUIRED`. `REQUIRED` verlangt
 *   `TLS_KEYSTORE_FILE` (PKCS#12) und `TLS_KEYSTORE_PASSWORD_FILE` (ein Zeilenende am Ende wird entfernt);
 *   `TEST_UNENCRYPTED` lässt beide nicht zu.
 * - `AUTH_USERS_FILE` Pflicht; `AUTH_PLAIN_TESTMODE` fehlt, ist leer, `false` oder genau `true`; jeder
 *   andere Wert, auch `TRUE`, ist ein Fehler und schaltet nie ein (`ADR-0011`).
 *
 * Die Benutzerliste wird als UTF-8 gelesen. Ein BOM am Anfang wird abgewiesen, nicht entfernt (Festlegung,
 * Spezifikation, Abschnitt Authentifizierungsadapter); die Zeilenenden bleiben unverändert, ein CR ist dann
 * ein Fehler der Validierung. Dateirechte der Dateien prüft der Lader nicht.
 */
object ConfigLoader {
    private const val DEFAULT_HOST = "0.0.0.0"
    private const val DEFAULT_PORT = 1433
    private const val MAX_PORT = 65535
    private const val DEFAULT_LOGIN_TIMEOUT = 15
    private const val MAX_LOGIN_TIMEOUT = 3600
    private const val MIN_PRELOGIN_BYTES = 512
    private const val MAX_PRELOGIN_BYTES = 65536
    private val DIGITS = Regex("[0-9]{1,9}")

    fun load(environment: Map<String, String>): ConfigResult {
        val issues = mutableListOf<ConfigIssue>()
        val host = host(environment, issues)
        val port = number(environment, ConfigKeys.LISTEN_PORT, DEFAULT_PORT, 0..MAX_PORT, issues)
        val timeout = number(environment, ConfigKeys.LOGIN_TIMEOUT, DEFAULT_LOGIN_TIMEOUT, 1..MAX_LOGIN_TIMEOUT, issues)
        val prelogin = number(
            environment,
            ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES,
            MAX_PRELOGIN_BYTES,
            MIN_PRELOGIN_BYTES..MAX_PRELOGIN_BYTES,
            issues,
        )
        val encryption = encryption(environment, issues)
        val tls = tls(environment, encryption, issues)
        val plain = plainTestMode(environment, issues)
        val users = ConfigFiles.users(environment, plain, issues)
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
                ),
            )
        }
    }

    private fun host(environment: Map<String, String>, issues: MutableList<ConfigIssue>): String? {
        val value = environment[ConfigKeys.LISTEN_HOST] ?: return DEFAULT_HOST
        if (value.isBlank()) issues += ConfigIssue(ConfigKeys.LISTEN_HOST, IssueKind.INVALID_VALUE)
        return value.takeIf { it.isNotBlank() }
    }

    private fun number(
        environment: Map<String, String>,
        key: String,
        default: Int,
        range: IntRange,
        issues: MutableList<ConfigIssue>,
    ): Int? {
        val value = environment[key] ?: return default
        val parsed = if (DIGITS.matches(value)) value.toInt() else null
        val kind = when {
            parsed == null -> IssueKind.INVALID_VALUE
            parsed !in range -> IssueKind.OUT_OF_RANGE
            else -> null
        }
        if (kind != null) issues += ConfigIssue(key, kind)
        return parsed.takeIf { kind == null }
    }

    private fun encryption(environment: Map<String, String>, issues: MutableList<ConfigIssue>): EncryptionSetting? {
        val value = environment[ConfigKeys.ENCRYPTION_POLICY] ?: return EncryptionSetting.REQUIRED
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
        val store = environment[ConfigKeys.TLS_KEYSTORE_FILE]
        val password = environment[ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE]
        return when (encryption) {
            null -> null

            EncryptionSetting.TEST_UNENCRYPTED -> {
                if (store != null) issues += ConfigIssue(ConfigKeys.TLS_KEYSTORE_FILE, IssueKind.CONFLICT)
                if (password != null) issues += ConfigIssue(ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE, IssueKind.CONFLICT)
                null
            }

            EncryptionSetting.REQUIRED -> required(store, password, issues)
        }
    }

    private fun required(store: String?, password: String?, issues: MutableList<ConfigIssue>): TlsKeyMaterial? {
        if (store == null) issues += ConfigIssue(ConfigKeys.TLS_KEYSTORE_FILE, IssueKind.MISSING)
        if (password == null) issues += ConfigIssue(ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE, IssueKind.MISSING)
        if (store == null || password == null) return null
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
