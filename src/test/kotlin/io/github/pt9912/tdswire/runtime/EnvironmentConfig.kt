package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.LoginSettings
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import java.io.File
import kotlin.test.assertIs

/**
 * Baut aus der Umgebung eines Wiring-Tests die [ServerConfig], die der Test einem Start übergibt. Die Bibliothek
 * liest keine Umgebung (`SPEC-005`); den Lader mit seinen Fehlerfällen prüfen die Tests des Testservers. Diese
 * Übersetzung kennt nur gültige Formen und bricht bei einer anderen ab; Werte außerhalb der Bereiche reicht sie
 * unverändert weiter, die Prüfung des Starts meldet sie.
 */
internal fun configOf(environment: Map<String, String>): ServerConfig {
    val defaults = ListenSettings() to LoginSettings()
    val requestDefaults = RequestSettings()
    val listen = ListenSettings(
        host = environment[ConfigKeys.LISTEN_HOST] ?: defaults.first.host,
        port = environment[ConfigKeys.LISTEN_PORT]?.toInt() ?: defaults.first.port,
        maxConnections = environment[ConfigKeys.MAX_CONNECTIONS]?.toInt() ?: defaults.first.maxConnections,
    )
    val login = LoginSettings(
        timeoutSeconds = environment[ConfigKeys.LOGIN_TIMEOUT]?.toInt() ?: defaults.second.timeoutSeconds,
        maxPreloginMessageBytes = environment[ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES]?.toInt()
            ?: defaults.second.maxPreloginMessageBytes,
    )
    val encryption = environment[ConfigKeys.ENCRYPTION_POLICY]?.let { EncryptionSetting.valueOf(it) }
        ?: EncryptionSetting.REQUIRED
    val plain = UserList.plainTestModeFrom(environment[ConfigKeys.AUTH_PLAIN_TESTMODE])
    val usersText = File(environment.getValue(ConfigKeys.AUTH_USERS_FILE)).readText()
    val users = assertIs<UserListResult.Valid>(UserList.parse(usersText, plain)).list
    val store = environment[ConfigKeys.TLS_KEYSTORE_FILE]
    val password = environment[ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE]
    val tls = if (store != null && password != null) {
        val chars = File(password).readText().removeSuffix("\n").toCharArray()
        TlsKeyMaterial(File(store).readBytes(), PasswordChars(chars).also { chars.fill('\u0000') })
    } else {
        null
    }
    val requests = RequestSettings(
        queryTimeoutSeconds = environment[ConfigKeys.QUERY_TIMEOUT]?.toInt() ?: requestDefaults.queryTimeoutSeconds,
        cancelTimeoutSeconds = environment[ConfigKeys.CANCEL_TIMEOUT]?.toInt() ?: requestDefaults.cancelTimeoutSeconds,
        shutdownGraceSeconds = environment[ConfigKeys.SHUTDOWN_GRACE]?.toInt() ?: requestDefaults.shutdownGraceSeconds,
    )
    return ServerConfig(listen, login, encryption, tls, AuthSettings(users, plain), requests)
}

/** Die Umgebung eines gültigen Testmodus-Starts: unverschlüsselt, Benutzerliste mit einem `plain`-Eintrag. */
internal fun testModeEnvironment(usersFile: String, vararg more: Pair<String, String>): Map<String, String> = mapOf(
    ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
    ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
    ConfigKeys.AUTH_USERS_FILE to usersFile,
) + more
