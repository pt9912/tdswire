package io.github.pt9912.tdswire.config

import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Die Umgebung eines gültigen Testmodus-Starts: unverschlüsselt, Benutzerliste mit einem `plain`-Eintrag. */
fun testModeEnvironment(usersFile: String, vararg more: Pair<String, String>): Map<String, String> = mapOf(
    ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
    ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
    ConfigKeys.AUTH_USERS_FILE to usersFile,
) + more

fun ConfigResult.loaded(): ServerConfig = assertIs<ConfigResult.Loaded>(this).config

fun ConfigResult.issues(): List<ConfigIssue> = assertIs<ConfigResult.Invalid>(this).issues

/** Der eine Fehler, den [environment] auslöst. */
fun singleIssue(environment: Map<String, String>): ConfigIssue {
    val issues = ConfigLoader.load(environment).issues()
    assertEquals(1, issues.size, "Fehler: ${issues.map { it.message }}")
    return issues.single()
}
