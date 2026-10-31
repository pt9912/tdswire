package io.github.pt9912.tdswire.config

import io.github.pt9912.tdswire.config.auth.AuthFeatures
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.ConnectionContext
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserListAuthAdapter
import io.github.pt9912.tdswire.config.auth.UserListError
import io.github.pt9912.tdswire.config.auth.UserListErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Lesen und Validieren der Benutzerliste und des Testmodus-Schalters (`ADR-0011`, `SPEC-051`). */
class ConfigUsersFileTest : TempFilesTest() {
    private val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    private fun env(usersFile: String, testMode: String? = "true") =
        mapOf(ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED", ConfigKeys.AUTH_USERS_FILE to usersFile) +
            listOfNotNull(testMode?.let { ConfigKeys.AUTH_PLAIN_TESTMODE to it })

    private fun listInvalid(kind: UserListErrorKind, line: Int?) =
        ConfigIssue(ConfigKeys.AUTH_USERS_FILE, IssueKind.USER_LIST_INVALID, UserListError(kind, line))

    private fun users(path: String, testMode: String? = "true"): Boolean {
        val config = ConfigLoader.load(env(path, testMode)).loaded()
        val request = AuthRequest(
            "alice",
            PasswordChars("pw".toCharArray()),
            ConnectionContext("d", "a", "h", false),
            NegotiatedEncryption.NOT_SUPPORTED,
            AuthFeatures(integratedSecurity = false, federatedAuthentication = false, changePassword = false),
        )
        return UserListAuthAdapter(config.auth.users).authenticate(request) is AuthResult.Accepted
    }

    /**
     * Die gelesene Liste ist die der Datei: ein Eintrag aus der Datei meldet an, ein Passwort außerhalb nicht.
     * Der Test geht über den Adapter, damit eine leere oder falsche Liste auffällt.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Die Liste der Datei wird gelesen`() {
        assertTrue(users(file("a.txt", "alice\t{plain}pw\n")))
        assertFalse(users(file("b.txt", "alice\t{plain}anders\n")))
    }

    /**
     * Nicht-ASCII im Benutzernamen kommt als UTF-8 an (kein Standardzeichensatz der Plattform).
     *
     * @spec SPEC-051
     */
    @Test
    fun `Die Datei wird als UTF-8 gelesen`() {
        val path = file("u.txt", "aliçe\t{plain}pw\nalice\t{plain}pw\n")
        assertTrue(users(path))
    }

    /**
     * Ein UTF-8-BOM am Anfang wird abgewiesen, nicht entfernt (Festlegung): es würde Teil des ersten
     * Benutzernamens. Mit und ohne Zeilenende, und ein BOM mitten in der Datei bleibt Teil des Namens.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Ein BOM am Dateianfang wird abgewiesen`() {
        val body = "alice\t{plain}pw\n".toByteArray()
        assertEquals(
            ConfigIssue(ConfigKeys.AUTH_USERS_FILE, IssueKind.BYTE_ORDER_MARK),
            singleIssue(env(file("bom.txt", bom + body))),
        )
        assertEquals(
            ConfigIssue(ConfigKeys.AUTH_USERS_FILE, IssueKind.BYTE_ORDER_MARK),
            singleIssue(env(file("bom2.txt", bom))),
        )
        val inside = "x\t{plain}1\n".toByteArray() + bom + body
        ConfigLoader.load(env(file("bom3.txt", inside))).loaded()
    }

    /**
     * Die Zeilenenden werden nicht normalisiert: CRLF und ein einzelnes CR erreichen die Validierung und sind
     * dort ein Fehler mit der Zeilennummer der Datei.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Zeilenenden werden nicht normalisiert`() {
        val crlf = singleIssue(env(file("crlf.txt", "alice\t{plain}pw\r\nbob\t{plain}x\r\n")))
        assertEquals(listInvalid(UserListErrorKind.CARRIAGE_RETURN, 1), crlf)
        val later = singleIssue(env(file("cr.txt", "alice\t{plain}pw\nbob\t{plain}x\r")))
        assertEquals(listInvalid(UserListErrorKind.CARRIAGE_RETURN, 2), later)
    }

    /**
     * Ungültiges UTF-8 (ein einzelnes Fortsetzungsbyte, ein abgeschnittenes Zeichen) ist `NOT_UTF8`, ohne dass
     * ein Ersatzzeichen in einen Benutzernamen gelangt.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Ungueltiges UTF-8 wird abgewiesen`() {
        val broken = listOf(
            byteArrayOf(0x80.toByte()),
            byteArrayOf(0xC3.toByte()),
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
        )
        broken.forEach {
            val bytes = "alice\t{plain}pw\nb".toByteArray() + it + "\t{plain}x\n".toByteArray()
            val issue = singleIssue(env(file("bad.txt", bytes)))
            assertEquals(ConfigIssue(ConfigKeys.AUTH_USERS_FILE, IssueKind.NOT_UTF8), issue)
        }
    }

    /**
     * Fehler der Validierung nennen Art und Zeile, nie Name oder Prüfwert: doppelter Name, `plain` ohne Testmodus,
     * leere Liste.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Fehler der Liste nennen Art und Zeile`() {
        val duplicate = singleIssue(env(file("dup.txt", "alice\t{plain}pw\nbob\t{plain}x\nalice\t{plain}y\n")))
        assertEquals(UserListError(UserListErrorKind.DUPLICATE_USER_NAME, 3), duplicate.detail)
        assertEquals("AUTH_USERS_FILE: USER_LIST_INVALID (DUPLICATE_USER_NAME, Zeile 3)", duplicate.message)
        val notAllowed = singleIssue(env(file("plain.txt", "kennung-4711\t{plain}pw-4711\n"), testMode = null))
        assertEquals(UserListError(UserListErrorKind.PLAIN_NOT_ALLOWED, 1), notAllowed.detail)
        assertFalse(notAllowed.message.contains("4711"))
        assertEquals(UserListError(UserListErrorKind.EMPTY_LIST, null), singleIssue(env(file("empty.txt", ""))).detail)
    }

    /**
     * Der Schalter schaltet nur mit genau `true` ein. `false`, ein leerer Wert und der fehlende Schlüssel lassen
     * `plain` verboten; jeder andere Wert (auch `TRUE`, `1`, `yes`) ist ein Fehler und schaltet nicht ein.
     *
     * @spec SPEC-051
     */
    @Test
    fun `AUTH_PLAIN_TESTMODE schaltet nur mit true ein`() {
        val path = file("t.txt", "alice\t{plain}pw\n")
        assertTrue(users(path, "true"))
        listOf("false", "", null).forEach {
            assertEquals(
                UserListError(UserListErrorKind.PLAIN_NOT_ALLOWED, 1),
                singleIssue(env(path, it)).detail,
                "Wert '$it'",
            )
        }
        listOf("TRUE", "True", "1", "yes", " true", "true ").forEach {
            val issue = singleIssue(env(path, it))
            assertEquals(ConfigIssue(ConfigKeys.AUTH_PLAIN_TESTMODE, IssueKind.INVALID_VALUE), issue, "Wert '$it'")
        }
    }

    /**
     * Ein fehlender Schlüssel und eine nicht lesbare Datei (nicht vorhanden, Verzeichnis, ungültiger Pfad) brechen
     * den Start ab.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Fehlende oder unlesbare Datei bricht ab`() {
        val key = ConfigKeys.AUTH_USERS_FILE
        assertEquals(ConfigIssue(key, IssueKind.MISSING), singleIssue(env("x") - key))
        val unreadable = ConfigIssue(key, IssueKind.UNREADABLE)
        file("vorhanden.txt", "x")
        listOf("/nicht/vorhanden/users", file("d.txt", "").substringBeforeLast('/'), "a\u0000b").forEach {
            assertEquals(unreadable, singleIssue(env(it)), "Pfad $it")
        }
    }

    /**
     * Die Textdarstellung der Konfiguration nennt weder Benutzernamen, Prüfwert und Passwort noch das
     * Kennwort des Speichers noch einen Pfad (`TDS-QA-03`).
     *
     * @spec SPEC-051
     */
    @Test
    fun `Die Textdarstellung der Konfiguration nennt keinen Wert`() {
        val password = "kennwort-4711"
        val users = file("kennung-4711.txt", "kennung-4711\t{plain}passwort-4711\n")
        val environment = mapOf(
            ConfigKeys.AUTH_USERS_FILE to users,
            ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
            ConfigKeys.TLS_KEYSTORE_FILE to file("speicher-4711.p12", "inhalt-4711"),
            ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to file("pw.txt", "$password\n"),
        )
        val config = ConfigLoader.load(environment).loaded()
        val shown = listOf(
            config.toString(),
            config.auth.toString(),
            config.tls.toString(),
            ConfigLoader.load(environment).toString(),
        )
        val words = listOf("4711", "kennung", "passwort", "kennwort", "inhalt")
        shown.forEach { text -> words.forEach { assertFalse(text.contains(it), "$text nennt $it") } }
        val tls = assertIs<TlsKeyMaterial>(config.tls)
        assertEquals(password, String(tls.password.reveal()), "ein Zeilenende am Ende gehört nicht zum Kennwort")
    }
}
