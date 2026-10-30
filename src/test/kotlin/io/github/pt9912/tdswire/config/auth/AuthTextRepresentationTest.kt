package io.github.pt9912.tdswire.config.auth

import io.github.pt9912.tdswire.domain.Principal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame

class AuthTextRepresentationTest {
    private val userName = "kennung-xyz"
    private val password = "streng-geheimes-passwort"
    private val value = pbkdf2Value(password)
    private val listText = "$userName\t$value\n"

    private fun assertHidden(shown: String, what: String) {
        listOf(userName, password, value, b64(testSalt), "Passwort", "master", "host").forEach {
            assertFalse(shown.contains(it), "$what nennt $it: $shown")
        }
        assertFalse(shown.contains(password.length.toString()), "$what nennt die Länge: $shown")
    }

    /**
     * Orakel: Keine automatisch erzeugte Textdarstellung von Eingabe, Ausgabe, Benutzerliste, Eintrag,
     * Prüfwert und Fehlerwert nennt Passwort, Kennung oder Prüfwert (TDS-QA-03). Die Fälle schließen
     * `data class`-Typen ein (`Rejected`, `UserListError`), deren Felder keine Eingabe tragen.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Textdarstellungen nennen weder Passwort noch Kennung noch Pruefwert`() {
        val request = request(userName, password)
        val list = parseOk(listText)
        val adapter = UserListAuthAdapter(list)
        val accepted = adapter.authenticate(request)
        val rejected = adapter.authenticate(request(userName, "falsch"))
        val unknown = adapter.authenticate(request("unbekannt-$userName", password))
        val entry = checkNotNull(list.find(userName))
        val failed = UserList.parse("$userName\t{plain}$password", plainTestMode = false)
        mapOf(
            "AuthRequest" to request.toString(),
            "PasswordChars" to request.password.toString(),
            "ConnectionContext" to request.context.toString(),
            "Accepted" to accepted.toString(),
            "Principal" to Principal(userName).toString(),
            "Rejected" to rejected.toString(),
            "Rejected unbekannt" to unknown.toString(),
            "UserList" to list.toString(),
            "UserEntry" to entry.toString(),
            "StoredSecret" to entry.secret.toString(),
            "UserListResult.Valid" to UserList.parse(listText, false).toString(),
            "UserListResult.Invalid" to failed.toString(),
            "Plain" to StoredSecret.Plain(password.toByteArray()).toString(),
        ).forEach { (what, shown) -> assertHidden(shown, what) }
        assertEquals("Rejected(reason=WRONG_PASSWORD)", rejected.toString())
    }

    /**
     * Orakel: Das Passwortfeld trennt sich vom übergebenen Array (Kopie), `reveal` gibt eine Kopie und `wipe`
     * überschreibt die gehaltenen Einheiten; die Länge bleibt lesbar, die Textdarstellung nennt sie nicht.
     *
     * @spec SPEC-051
     */
    @Test
    fun `PasswordChars kopiert und ueberschreibt`() {
        val source = "geheim".toCharArray()
        val secret = PasswordChars(source)
        source.fill('x')
        assertContentEquals("geheim".toCharArray(), secret.reveal())
        assertNotSame(secret.reveal(), secret.reveal())
        secret.reveal().fill('y')
        assertContentEquals("geheim".toCharArray(), secret.reveal())
        secret.wipe()
        assertContentEquals(CharArray(6), secret.reveal())
        assertEquals(6, secret.length)
    }
}
