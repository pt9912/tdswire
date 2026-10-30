package io.github.pt9912.tdswire.config.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserListParserTest {
    private val salt = b64(testSalt)
    private val hash = b64(ByteArray(Pbkdf2Limits.HASH_BYTES) { 7 })
    private val goodValue = "{pbkdf2-sha256}$salt\$600000\$$hash"

    private fun errorOf(text: String, testMode: Boolean = false): UserListError =
        assertIs<UserListResult.Invalid>(UserList.parse(text, testMode)).error

    private fun assertRejected(kind: UserListErrorKind, line: Int?, text: String, testMode: Boolean = false) {
        assertEquals(UserListError(kind, line), errorOf(text, testMode), "Eingabe: ${text.replace("\t", "<TAB>")}")
    }

    private fun entry(value: String) = "alice\t$value"

    private fun pbkdf2(saltText: String = salt, iterations: String = "600000", hashText: String = hash) =
        entry("{pbkdf2-sha256}$saltText\$$iterations\$$hashText")

    /**
     * Orakel: Der Vertrag nennt `Benutzername<TAB>Prüfwert`, Aufteilung am ersten TAB, UTF-8, kein Escaping.
     * Leerzeichen, `:`, `=`, `#`, `\` im Namen und ein Surrogatpaar gehen durch, ein weiteres TAB im
     * `plain`-Wert bleibt Teil des Werts, Namen werden zeichengenau verglichen.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Gueltige Liste wird angenommen und der Name zeichengenau gefuehrt`() {
        val names = listOf("a b", "x:y", "k=v", "#c", "p\\q", "😀")
        val text = names.joinToString("") { "$it\t$goodValue\n" } + "t\t{plain}a\tb\n"
        val list = parseOk(text, testMode = true)
        names.forEach { assertNotNull(list.find(it), "Name $it") }
        assertNotNull(list.find("t"))
        assertNull(list.find("A B"))
        assertNull(list.find("a b "))
    }

    /**
     * Orakel: Der Vertrag lässt `{plain}` nur im Testmodus zu; ein leerer `plain`-Wert ist zulässig, ebenfalls
     * nur im Testmodus.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Plain nur im Testmodus und der leere Wert ebenso`() {
        assertRejected(UserListErrorKind.PLAIN_NOT_ALLOWED, 1, entry("{plain}geheim"))
        assertRejected(UserListErrorKind.PLAIN_NOT_ALLOWED, 1, entry("{plain}"))
        assertNotNull(parseOk(entry("{plain}"), testMode = true).find("alice"))
        assertRejected(UserListErrorKind.PLAIN_NOT_ALLOWED, 2, "bob\t$goodValue\n" + entry("{plain}x"))
    }

    /**
     * Orakel: ADR-0011 setzt den Testmodus nur bei genau `true`; `TRUE` wurde im Spike abgewiesen.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Testmodus gilt nur bei genau true`() {
        assertTrue(UserList.plainTestModeFrom("true"))
        listOf(null, "", "TRUE", "True", "1", "yes", " true", "true ", "false").forEach {
            assertFalse(UserList.plainTestModeFrom(it), "Wert $it")
        }
    }

    /**
     * Orakel: Je Abweichung der Zeilenform genau eine Fehlerart mit der 1-basierten Zeilennummer der Datei
     * (Fehler in Zeile 2 trotz gültiger Zeile 1); ohne Zeile kein Listenplatz.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Zeilenform ergibt je eine Fehlerart mit Zeilennummer`() {
        val good = "bob\t$goodValue\n"
        assertRejected(UserListErrorKind.EMPTY_LIST, null, "")
        assertRejected(UserListErrorKind.EMPTY_LINE, 2, good + "\n" + good.replace("bob", "carl"))
        assertRejected(UserListErrorKind.EMPTY_LINE, 1, "\n")
        assertRejected(UserListErrorKind.MISSING_TAB, 2, good + "ohne-tab")
        assertRejected(UserListErrorKind.MISSING_TAB, 1, "# Kommentar $goodValue")
        assertRejected(UserListErrorKind.CARRIAGE_RETURN, 1, "bob\t$goodValue\r\n")
        assertRejected(UserListErrorKind.CARRIAGE_RETURN, 2, good + "a\r\t$goodValue")
        assertRejected(UserListErrorKind.EMPTY_USER_NAME, 2, good + "\t$goodValue")
        assertRejected(UserListErrorKind.DUPLICATE_USER_NAME, 3, good + "x\t$goodValue\n" + good)
        assertRejected(UserListErrorKind.MISSING_SCHEME, 2, good + "z\t")
    }

    /**
     * Orakel: Benutzernamen mit alleinstehendem Hochsurrogat oder Tiefsurrogat sind nicht darstellbar und ein
     * Ladefehler (ADR-0011); ein vollständiges Paar ist erlaubt.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Alleinstehendes Surrogat im Namen ist ein Fehler`() {
        assertRejected(UserListErrorKind.INVALID_USER_NAME, 1, "a\uD800b\t$goodValue")
        assertRejected(UserListErrorKind.INVALID_USER_NAME, 1, "a\uDC00\t$goodValue")
        assertRejected(UserListErrorKind.INVALID_USER_NAME, 1, "\uD800\t$goodValue")
        assertRejected(UserListErrorKind.INVALID_USER_NAME, 1, "\uDC00\uD800\t$goodValue")
        assertNotNull(parseOk("😀\t$goodValue").find("😀"))
    }

    /**
     * Orakel: Ein Eintrag ohne Verfahrenskennung und eine unbekannte oder anders geschriebene Verfahrenskennung
     * brechen den Start ab; die Kennung ist case-sensitiv und klein (ADR-0011, M2-1, M2-6).
     *
     * @spec SPEC-051
     */
    @Test
    fun `Verfahrenskennung fehlt oder ist unbekannt`() {
        listOf("{plain", "plain}x", "abc", "", "x{plain}").forEach {
            assertRejected(UserListErrorKind.MISSING_SCHEME, 1, entry(it), testMode = true)
        }
        listOf("{PBKDF2-SHA256}a", "{Plain}a", "{ plain}a", "{{plain}}a", "{}a", "{md5}a", "{plain }a").forEach {
            assertRejected(UserListErrorKind.UNKNOWN_SCHEME, 1, entry(it), testMode = true)
        }
    }

    /**
     * Orakel: Die Wertseite hat genau drei Felder `Salz$Iterationen$Hash` (ADR-0011, M2-1).
     *
     * @spec SPEC-051
     */
    @Test
    fun `Wertseite hat genau drei Felder`() {
        assertRejected(UserListErrorKind.FIELD_COUNT, 1, entry("{pbkdf2-sha256}$salt\$600000"))
        assertRejected(UserListErrorKind.FIELD_COUNT, 1, entry("{pbkdf2-sha256}$salt\$600000\$$hash\$x"))
        assertRejected(UserListErrorKind.FIELD_COUNT, 1, entry("{pbkdf2-sha256}"))
        assertNotNull(parseOk(pbkdf2()).find("alice"))
    }

    /**
     * Orakel: Die Iterationen sind nur ASCII-Ziffern; Boden 210 000, Obergrenze 10 000 000 (Setzung des
     * Nutzers). `toIntOrNull` nähme `+600000`, `-5`, arabisch-indische und vollbreite Ziffern an (M2-4).
     * Festlegung ohne Messung: 1 bis 8 Stellen, keine führende Null.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Iterationen sind streng`() {
        val invalid = "|+600000|-5|٦٠٠٠٠٠|６００００００|0600000|0|60 0000|6e5|600000 |123456789|1_0".split('|')
        invalid.forEach { assertRejected(UserListErrorKind.ITERATIONS_INVALID, 1, pbkdf2(iterations = it)) }
        assertRejected(UserListErrorKind.ITERATIONS_BELOW_FLOOR, 1, pbkdf2(iterations = "209999"))
        assertRejected(UserListErrorKind.ITERATIONS_BELOW_FLOOR, 1, pbkdf2(iterations = "1"))
        assertRejected(UserListErrorKind.ITERATIONS_ABOVE_CEILING, 1, pbkdf2(iterations = "10000001"))
        assertRejected(UserListErrorKind.ITERATIONS_ABOVE_CEILING, 1, pbkdf2(iterations = "99999999"))
        listOf("210000", "600000", "10000000").forEach { assertNotNull(parseOk(pbkdf2(iterations = it)).find("alice")) }
    }

    /**
     * Orakel: Salz und Hash sind Standard-Base64 mit verlangtem Padding (ADR-0011); ohne Padding nähme
     * `Base64.getDecoder()` die Eingabe an (M2-7), der Parser lehnt sie ab. Salz mindestens 16 Byte, Hash
     * genau 32 Byte (Festlegung ohne Messung).
     *
     * @spec SPEC-051
     */
    @Test
    fun `Salz und Hash sind Base64 mit Padding und fester Laenge`() {
        val salt16 = ByteArray(16)
        val canonical = b64(salt16)
        val nonCanonical = canonical.dropLast(3) + "B=="
        val badSalts = "AAAAAAAAAAAAAAAAAAAA-A==|AAAAAAAAAAAAAAAAAAAAAA=|AAAA=AAAAAAAAAAAAAAAAA==|!!!!".split('|')
        val shaped = listOf(canonical.trimEnd('='), "$canonical ", " $canonical", nonCanonical)
        val allBad = badSalts + shaped + canonical.replace('A', '_')
        allBad.forEach { assertRejected(UserListErrorKind.SALT_INVALID, 1, pbkdf2(saltText = it)) }
        assertRejected(UserListErrorKind.SALT_TOO_SHORT, 1, pbkdf2(saltText = b64(ByteArray(15))))
        assertRejected(UserListErrorKind.SALT_TOO_SHORT, 1, pbkdf2(saltText = ""))
        assertNotNull(parseOk(pbkdf2(saltText = b64(ByteArray(16)))).find("alice"))
        assertNotNull(parseOk(pbkdf2(saltText = b64(ByteArray(40)))).find("alice"))

        val zeros = b64(ByteArray(32))
        listOf("", hash.trimEnd('='), b64(ByteArray(31)), b64(ByteArray(33))).plus(
            listOf(zeros.dropLast(1) + "B", zeros.dropLast(2) + "B=", hash + "AAAA", "$hash "),
        ).forEach { assertRejected(UserListErrorKind.HASH_INVALID, 1, pbkdf2(hashText = it)) }
    }

    /**
     * Orakel: Fehlerwerte tragen weder Prüfwert noch Passwort noch Benutzernamen (ADR-0011, M2-2); geprüft
     * wird die Textform aller Fehler, die dieser Test erzeugt, gegen Teile des Eintrags.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Fehlerwert nennt weder Pruefwert noch Namen`() {
        val secretValue = "{plain}streng-geheimes-passwort"
        val texts = listOf(
            "kennung-xyz\t$secretValue",
            "kennung-xyz\t{pbkdf2-sha256}${salt}\$7\$$hash",
            "kennung-xyz\t{pbkdf2-sha256}$salt\$600000\$streng-geheimes-passwort",
            "kennung-xyz\t{md5}streng-geheimes-passwort",
        )
        texts.forEach { text ->
            val shown = errorOf(text).toString() + UserList.parse(text, false)
            listOf("kennung-xyz", "streng-geheimes-passwort", salt, hash).forEach {
                assertFalse(shown.contains(it), "Fehlerwert nennt $it: $shown")
            }
        }
    }
}
