package io.github.pt9912.tdswire.config

/**
 * Die Prüfung der Konfiguration (`TDS-FA-09.a`), gleich für jeden Weg zu einer [ServerConfig]: der Start über den
 * Einstieg ruft [check] auf dem fertigen Wert. Ein Fehler nennt Schlüssel und Art, nie einen Wert.
 *
 * Wertebereiche sind Festlegungen, nicht gemessen:
 * - `LISTEN_HOST` nicht leer.
 * - `LISTEN_PORT` 0 bis 65535 (`SPEC-005`); 0 wählt einen freien Port.
 * - `LOGIN_TIMEOUT` ganze Sekunden von 1 bis 3600 (`SPEC-008`, vorläufig).
 * - `MAX_PRELOGIN_MESSAGE_BYTES` 512 bis 65536 (`SPEC-046`, vorläufig); die obere Grenze ist die der
 *   PRELOGIN-Verhandlung.
 * - `REQUIRED` verlangt Schlüsselspeicher und Kennwort, `TEST_UNENCRYPTED` lässt beide nicht zu (`SPEC-010`).
 * - `QUERY_TIMEOUT` und `CANCEL_TIMEOUT` ganze Sekunden von 1 bis 3600 (`SPEC-009`, `SPEC-033`, vorläufig).
 * - `MAX_CONNECTIONS` ganze Zahl von 1 bis 100000 (`SPEC-034`, vorläufig); `SHUTDOWN_GRACE` ganze Sekunden von 0
 *   bis 3600, 0 = sofort (`SPEC-047`, vorläufig).
 *
 * Eine [ServerConfig] kann ungeprüft existieren; erst [check] und der Start weisen sie ab.
 */
internal object ConfigValidation {
    private const val MAX_PORT = 65535
    private const val MAX_LOGIN_TIMEOUT = 3600
    private const val MAX_REQUEST_TIMEOUT = 3600
    private const val MAX_CONNECTION_LIMIT = 100_000
    private const val MAX_SHUTDOWN_GRACE = 3600
    private const val MIN_PRELOGIN_BYTES = 512
    private const val MAX_PRELOGIN_BYTES = 65536

    /** Alle Fehler von [config] in fester Reihenfolge; leer, wenn die Konfiguration gültig ist. */
    fun check(config: ServerConfig): List<ConfigIssue> {
        val keyMaterial = config.tls != null
        return listOfNotNull(
            host(config.listen.host),
            port(config.listen.port),
            loginTimeout(config.login.timeoutSeconds),
            preloginBytes(config.login.maxPreloginMessageBytes),
            queryTimeout(config.requests.queryTimeoutSeconds),
            cancelTimeout(config.requests.cancelTimeoutSeconds),
            maxConnections(config.listen.maxConnections),
            shutdownGrace(config.requests.shutdownGraceSeconds),
        ) + pairing(config.encryption, hasKeystore = keyMaterial, hasPassword = keyMaterial)
    }

    internal fun host(host: String): ConfigIssue? =
        if (host.isBlank()) ConfigIssue(ConfigKeys.LISTEN_HOST, IssueKind.INVALID_VALUE) else null

    internal fun port(port: Int): ConfigIssue? = range(ConfigKeys.LISTEN_PORT, port, 0..MAX_PORT)

    internal fun loginTimeout(seconds: Int): ConfigIssue? =
        range(ConfigKeys.LOGIN_TIMEOUT, seconds, 1..MAX_LOGIN_TIMEOUT)

    internal fun queryTimeout(seconds: Int): ConfigIssue? =
        range(ConfigKeys.QUERY_TIMEOUT, seconds, 1..MAX_REQUEST_TIMEOUT)

    internal fun cancelTimeout(seconds: Int): ConfigIssue? =
        range(ConfigKeys.CANCEL_TIMEOUT, seconds, 1..MAX_REQUEST_TIMEOUT)

    internal fun maxConnections(count: Int): ConfigIssue? =
        range(ConfigKeys.MAX_CONNECTIONS, count, 1..MAX_CONNECTION_LIMIT)

    internal fun shutdownGrace(seconds: Int): ConfigIssue? =
        range(ConfigKeys.SHUTDOWN_GRACE, seconds, 0..MAX_SHUTDOWN_GRACE)

    internal fun preloginBytes(bytes: Int): ConfigIssue? =
        range(ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES, bytes, MIN_PRELOGIN_BYTES..MAX_PRELOGIN_BYTES)

    /** Die Paarung von [encryption] mit dem Schlüsselmaterial; Speicher und Kennwort werden einzeln gemeldet. */
    internal fun pairing(
        encryption: EncryptionSetting,
        hasKeystore: Boolean,
        hasPassword: Boolean,
    ): List<ConfigIssue> {
        val store = ConfigKeys.TLS_KEYSTORE_FILE
        val password = ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE
        return when (encryption) {
            EncryptionSetting.TEST_UNENCRYPTED -> listOfNotNull(
                ConfigIssue(store, IssueKind.CONFLICT).takeIf { hasKeystore },
                ConfigIssue(password, IssueKind.CONFLICT).takeIf { hasPassword },
            )

            EncryptionSetting.REQUIRED -> listOfNotNull(
                ConfigIssue(store, IssueKind.MISSING).takeUnless { hasKeystore },
                ConfigIssue(password, IssueKind.MISSING).takeUnless { hasPassword },
            )
        }
    }

    private fun range(key: String, value: Int, allowed: IntRange): ConfigIssue? =
        if (value in allowed) null else ConfigIssue(key, IssueKind.OUT_OF_RANGE)
}
