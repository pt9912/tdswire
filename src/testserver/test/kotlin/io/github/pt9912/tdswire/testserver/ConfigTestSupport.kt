package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.ServerConfig
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Die Umgebung eines gültigen Testmodus-Starts: unverschlüsselt, Benutzerliste mit einem `plain`-Eintrag. */
internal fun testModeEnvironment(usersFile: String, vararg more: Pair<String, String>): Map<String, String> = mapOf(
    EnvKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
    EnvKeys.AUTH_PLAIN_TESTMODE to "true",
    EnvKeys.AUTH_USERS_FILE to usersFile,
) + more

internal fun ConfigResult.loaded(): ServerConfig = assertIs<ConfigResult.Loaded>(this).config

internal fun ConfigResult.issues(): List<ConfigIssue> = assertIs<ConfigResult.Invalid>(this).issues

/** Der eine Fehler, den [environment] auslöst. */
internal fun singleIssue(environment: Map<String, String>): ConfigIssue {
    val issues = ConfigLoader.load(environment).issues()
    assertEquals(1, issues.size, "Fehler: ${issues.map { it.message }}")
    return issues.single()
}
