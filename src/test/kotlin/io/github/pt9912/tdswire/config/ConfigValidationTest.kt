package io.github.pt9912.tdswire.config

import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Prüfung der Konfiguration auf einer von Hand gebauten [ServerConfig], dem Weg des Einbettenden: gleiche
 * Schlüssel, Arten und Grenzen wie beim Lader (`TDS-FA-09.a`).
 */
class ConfigValidationTest {
    private fun users(): AuthSettings =
        AuthSettings(assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}pw\n", true)).list, true)

    private fun keyMaterial() = TlsKeyMaterial(byteArrayOf(1), PasswordChars(charArrayOf('x')))

    private fun unencrypted(
        listen: ListenSettings = ListenSettings(),
        login: LoginSettings = LoginSettings(),
        simulationFile: ByteArray? = null,
        requests: RequestSettings = RequestSettings(),
    ) = ServerConfig(listen, login, EncryptionSetting.TEST_UNENCRYPTED, null, users(), simulationFile)
        .also { it.requests = requests }

    /**
     * Eine Konfiguration mit den Voreinstellungen und unverschlüsseltem Testmodus ist gültig; die Voreinstellungen sind
     * die der Spezifikation.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     * @spec SPEC-046
     * @spec SPEC-009
     * @spec SPEC-033
     */
    @Test
    fun `Voreinstellungen sind gueltig und entsprechen der Spezifikation`() {
        val config = unencrypted()
        assertEquals(emptyList(), ConfigValidation.check(config))
        assertEquals(ListenSettings("0.0.0.0", 1433), config.listen)
        assertEquals(LoginSettings(15, 65536), config.login)
        assertEquals(RequestSettings(30, 5), config.requests)
        assertEquals(EncryptionSetting.REQUIRED, ServerConfig(auth = users()).encryption)
    }

    /**
     * Grenzen der Zahlenwerte auf dem Weg des Einbettenden: der kleinste und der größte zulässige Wert sind gültig,
     * der Wert davor und danach `OUT_OF_RANGE` unter dem Schlüssel des Laders.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     * @spec SPEC-046
     * @spec SPEC-009
     * @spec SPEC-033
     */
    @Test
    fun `Bereichsgrenzen gelten auch ohne Lader`() {
        val cases = listOf(
            Triple(ConfigKeys.LISTEN_PORT, 0, 65535) to { v: Int -> unencrypted(listen = ListenSettings(port = v)) },
            Triple(ConfigKeys.LOGIN_TIMEOUT, 1, 3600) to { v: Int -> unencrypted(login = LoginSettings(v, 65536)) },
            Triple(ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES, 512, 65536) to
                { v: Int -> unencrypted(login = LoginSettings(15, v)) },
            Triple(ConfigKeys.QUERY_TIMEOUT, 1, 3600) to
                { v: Int -> unencrypted(requests = RequestSettings(queryTimeoutSeconds = v)) },
            Triple(ConfigKeys.CANCEL_TIMEOUT, 1, 3600) to
                { v: Int -> unencrypted(requests = RequestSettings(cancelTimeoutSeconds = v)) },
        )
        for ((bounds, build) in cases) {
            val (key, low, high) = bounds
            val outside = listOf(ConfigIssue(key, IssueKind.OUT_OF_RANGE))
            assertEquals(emptyList(), ConfigValidation.check(build(low)), "$key unten")
            assertEquals(emptyList(), ConfigValidation.check(build(high)), "$key oben")
            assertEquals(outside, ConfigValidation.check(build(low - 1)), "$key vor unten")
            assertEquals(outside, ConfigValidation.check(build(high + 1)), "$key nach oben")
        }
    }

    /**
     * `REQUIRED` ohne Schlüsselmaterial ergibt beide Fehler `MISSING`, `TEST_UNENCRYPTED` mit Material beide
     * `CONFLICT`: die Zusage „verschlüsselt heißt verschlüsselt“ gilt für jeden Weg zu einer Konfiguration.
     *
     * @spec SPEC-010
     */
    @Test
    fun `Verschluesselung und Schluesselmaterial gehoeren zusammen`() {
        val store = ConfigKeys.TLS_KEYSTORE_FILE
        val password = ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE
        val required = ServerConfig(auth = users())
        assertEquals(
            listOf(ConfigIssue(store, IssueKind.MISSING), ConfigIssue(password, IssueKind.MISSING)),
            ConfigValidation.check(required),
        )
        assertEquals(emptyList(), ConfigValidation.check(ServerConfig(tls = keyMaterial(), auth = users())))
        val mixed = ServerConfig(encryption = EncryptionSetting.TEST_UNENCRYPTED, tls = keyMaterial(), auth = users())
        assertEquals(
            listOf(ConfigIssue(store, IssueKind.CONFLICT), ConfigIssue(password, IssueKind.CONFLICT)),
            ConfigValidation.check(mixed),
        )
    }

    /**
     * Die Datei der Tabellenbeschreibung darf höchstens 1 MiB groß sein, die Grenze selbst ist gültig.
     *
     * @spec SPEC-035
     */
    @Test
    fun `Die Beschreibung der Tabellen ist auf 1 MiB begrenzt`() {
        val key = ConfigKeys.SIMULATION_DESCRIPTION_FILE
        assertEquals(emptyList(), ConfigValidation.check(unencrypted(simulationFile = ByteArray(1_048_576))))
        assertEquals(
            listOf(ConfigIssue(key, IssueKind.TOO_LARGE)),
            ConfigValidation.check(unencrypted(simulationFile = ByteArray(1_048_577))),
        )
    }

    /**
     * Mehrere Fehler werden zusammen gemeldet, in fester Reihenfolge, und die Diagnose nennt keinen Wert.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Mehrere Fehler stehen in fester Reihenfolge ohne Wert`() {
        val secret = "geheim-wert-4711"
        val config = ServerConfig(
            listen = ListenSettings(host = " ", port = 70000),
            login = LoginSettings(0, 1),
            auth = users(),
            simulationFile = ByteArray(1_048_577),
        )
        val issues = ConfigValidation.check(config)
        assertEquals(
            listOf(
                ConfigIssue(ConfigKeys.LISTEN_HOST, IssueKind.INVALID_VALUE),
                ConfigIssue(ConfigKeys.LISTEN_PORT, IssueKind.OUT_OF_RANGE),
                ConfigIssue(ConfigKeys.LOGIN_TIMEOUT, IssueKind.OUT_OF_RANGE),
                ConfigIssue(ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES, IssueKind.OUT_OF_RANGE),
                ConfigIssue(ConfigKeys.TLS_KEYSTORE_FILE, IssueKind.MISSING),
                ConfigIssue(ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE, IssueKind.MISSING),
                ConfigIssue(ConfigKeys.SIMULATION_DESCRIPTION_FILE, IssueKind.TOO_LARGE),
            ),
            issues,
        )
        assertTrue(issues.none { it.message.contains(secret) })
        assertFalse(ServerConfig(ListenSettings(host = secret), auth = users()).toString().contains(secret))
    }
}
