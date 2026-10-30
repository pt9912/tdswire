package io.github.pt9912.tdswire.config.auth

import io.github.pt9912.tdswire.domain.Principal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserListAuthAdapterTest {
    private val listText = "alice\t${pbkdf2Value("richtig")}\n" +
        "bob\t${pbkdf2Value("")}\n" +
        "Ä\t${pbkdf2Value("😀pw")}\n" +
        "lang\t${pbkdf2Value("p".repeat(AuthRequest.MAX_INPUT_UNITS))}\n" +
        "paare\t${pbkdf2Value("😀".repeat(AuthRequest.MAX_INPUT_UNITS / 2))}\n"

    private val adapter = UserListAuthAdapter(parseOk(listText))

    private fun accepted(user: String, password: String): Boolean =
        adapter.authenticate(request(user, password)) is AuthResult.Accepted

    private fun RecordingPrimitives.Derivation.shape() = Triple(iterations, saltSize, bytes)

    private fun reasonOf(user: String, password: String): RejectReason =
        assertIs<AuthResult.Rejected>(adapter.authenticate(request(user, password))).reason

    /**
     * Orakel: bekannter Benutzername mit dem Passwort, aus dem das Orakel den Prüfwert rechnete, ergibt
     * `Accepted` mit dem Principal dieses Namens.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Richtiges Passwort wird angenommen`() {
        val result = assertIs<AuthResult.Accepted>(adapter.authenticate(request("alice", "richtig")))
        assertEquals(Principal("alice"), result.principal)
        assertTrue(accepted("Ä", "😀pw"))
    }

    /**
     * Orakel: falsches Passwort und unbekannter Benutzername sind `Rejected`; der Grund ist ein Aufzählungswert
     * ohne Text aus der Eingabe. Passwörter, die nur in Groß-/Kleinschreibung oder um ein Zeichen abweichen,
     * werden abgelehnt; Benutzernamen werden zeichengenau verglichen (Festlegung ohne Messung).
     *
     * @spec SPEC-051
     * @spec SPEC-012
     */
    @Test
    fun `Falsches Passwort und unbekannter Benutzer werden abgelehnt`() {
        assertEquals(RejectReason.WRONG_PASSWORD, reasonOf("alice", "falsch"))
        assertEquals(RejectReason.WRONG_PASSWORD, reasonOf("alice", "Richtig"))
        assertEquals(RejectReason.WRONG_PASSWORD, reasonOf("alice", "richtig "))
        assertEquals(RejectReason.WRONG_PASSWORD, reasonOf("alice", "richti"))
        assertEquals(RejectReason.UNKNOWN_USER, reasonOf("nobody", "richtig"))
        assertEquals(RejectReason.UNKNOWN_USER, reasonOf("Alice", "richtig"))
        assertEquals(RejectReason.UNKNOWN_USER, reasonOf("Ä", "😀pw"))
    }

    /**
     * Orakel: Ein unbekannter Benutzername wird auch dann abgelehnt, wenn das Passwort das des Eintrags ist,
     * dessen Prüfwert der Scheinvergleich benutzt (erster Eintrag der Liste).
     *
     * @spec SPEC-051
     */
    @Test
    fun `Scheinvergleich fuehrt nie zur Annahme`() {
        assertEquals(RejectReason.UNKNOWN_USER, reasonOf("nobody", "richtig"))
        assertEquals(RejectReason.UNKNOWN_USER, reasonOf("", "richtig"))
    }

    /**
     * Orakel: Der Client öffnet mit leerer Kennung und leerem Passwort (ADR-0009). Leere Kennung ist nie in der
     * Liste (EMPTY_USER_NAME) und wird abgelehnt; ein leeres Passwort ist ein gültiges Passwort und wird
     * abgeleitet. Mit `plain` im Testmodus gilt dasselbe.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Leere Kennung und leeres Passwort sind Eingaben ohne Ausnahme`() {
        assertEquals(RejectReason.UNKNOWN_USER, reasonOf("", ""))
        assertTrue(accepted("bob", ""))
        assertEquals(RejectReason.WRONG_PASSWORD, reasonOf("bob", "x"))
        assertEquals(RejectReason.WRONG_PASSWORD, reasonOf("alice", ""))
        val plain = UserListAuthAdapter(parseOk("p\t{plain}\nq\t{plain}a}\$b\n", testMode = true))
        assertTrue(plain.authenticate(request("p", "")) is AuthResult.Accepted)
        assertTrue(plain.authenticate(request("p", "x")) is AuthResult.Rejected)
        assertTrue(plain.authenticate(request("q", "a}\$b")) is AuthResult.Accepted)
        assertTrue(plain.authenticate(request("q", "")) is AuthResult.Rejected)
    }

    /**
     * Orakel: Das Zeilenformat beschneidet keine Leerzeichen (ADR-0011): ein `plain`-Wert mit führendem und
     * abschließendem Leerzeichen gilt mit diesen; ein Benutzername mit Leerzeichen am Rand ist ein eigener Name.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Leerzeichen am Rand bleiben erhalten`() {
        val spaced = UserListAuthAdapter(parseOk(" u \t{plain} a b \n", testMode = true))
        assertTrue(spaced.authenticate(request(" u ", " a b ")) is AuthResult.Accepted)
        assertTrue(spaced.authenticate(request(" u ", "a b")) is AuthResult.Rejected)
        assertTrue(spaced.authenticate(request("u", " a b ")) is AuthResult.Rejected)
    }

    /**
     * Orakel: Grenze der Eingabe 128 UTF-16-Einheiten: 128 Einheiten (auch 64 Surrogatpaare) werden geprüft,
     * 129 werden ohne Ausnahme und ohne Ableitung abgelehnt, auch beim Benutzernamen.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Grenze von 128 Einheiten`() {
        assertTrue(accepted("lang", "p".repeat(128)))
        assertTrue(accepted("paare", "😀".repeat(64)))
        val recording = RecordingPrimitives()
        val guarded = UserListAuthAdapter(parseOk(listText), recording)
        val tooLongPassword = guarded.authenticate(request("lang", "p".repeat(129)))
        val tooLongName = guarded.authenticate(request("n".repeat(129), "x"))
        assertEquals(AuthResult.Rejected(RejectReason.INPUT_TOO_LONG), tooLongPassword)
        assertEquals(AuthResult.Rejected(RejectReason.INPUT_TOO_LONG), tooLongName)
        assertTrue(recording.derivations.isEmpty() && recording.comparisons.isEmpty())
        assertEquals(RejectReason.UNKNOWN_USER, reasonOf("n".repeat(128), "x"))
    }

    /**
     * Orakel: ADR-0011 dokumentiert, dass ein alleinstehendes Hochsurrogat dieselbe Ableitung wie `?` ergibt
     * und dass es keine Normalisierung gibt: ein Eintrag zu `?` nimmt `\uD800` an, ein Eintrag zu NFC nimmt
     * NFD nicht an.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Dokumentierte Eigenschaften des Passworts gelten im Adapter`() {
        val nfc = "ä"
        val nfd = "ä"
        val local = UserListAuthAdapter(parseOk("q\t${pbkdf2Value("?")}\nn\t${pbkdf2Value(nfc)}\n"))
        assertTrue(local.authenticate(request("q", "\uD800")) is AuthResult.Accepted)
        assertTrue(local.authenticate(request("n", nfc)) is AuthResult.Accepted)
        assertTrue(local.authenticate(request("n", nfd)) is AuthResult.Rejected)
    }

    /**
     * Strukturtest (keine Zeitaussage): Bei unbekanntem Benutzernamen läuft dieselbe Ableitung wie für den
     * ersten Eintrag (gleiche Iterationen, Salzlänge, Ausgabelänge) und ein Vergleich über alle 32 Byte;
     * das Ergebnis ist `Rejected`. Für einen bekannten Benutzer mit falschem Passwort läuft genau eine
     * Ableitung und ein Vergleich, auch bei Unterschied im ersten Byte des Hashes.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Unbekannter Benutzer fuehrt Ableitung und Vergleich aus`() {
        val recording = RecordingPrimitives()
        val guarded = UserListAuthAdapter(parseOk(listText), recording)
        guarded.authenticate(request("alice", "falsch"))
        guarded.authenticate(request("nobody", "x"))
        val (known, decoy) = recording.derivations
        assertEquals(2, recording.derivations.size)
        assertEquals(Triple(Pbkdf2Limits.ITERATIONS_FLOOR, 16, 32), known.shape())
        assertEquals(known.shape(), decoy.shape())
        assertEquals(listOf(32, 32), recording.comparisons.map { it.steps })
    }

    /**
     * Strukturtest: Ist der erste Eintrag `plain`, läuft bei unbekanntem Benutzernamen derselbe Vergleich ohne
     * Ableitung wie für den ersten Eintrag; der Vergleich untersucht die längere der beiden Längen.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Scheinvergleich gegen einen plain-Eintrag vergleicht ohne Ableitung`() {
        val recording = RecordingPrimitives()
        val guarded = UserListAuthAdapter(parseOk("p\t{plain}geheim\n", testMode = true), recording)
        val result = guarded.authenticate(request("nobody", "ein-langes-passwort"))
        assertEquals(AuthResult.Rejected(RejectReason.UNKNOWN_USER), result)
        assertTrue(recording.derivations.isEmpty())
        assertEquals(listOf("ein-langes-passwort".length), recording.comparisons.map { it.steps })
    }

    /**
     * Strukturtest: Der Adapter überschreibt seine Kopien. Das an die Ableitung übergebene Zeichenfeld und die
     * abgeleiteten Bytes sind nach dem Aufruf null; das Passwort des Aufrufers bleibt unberührt (er
     * überschreibt es selbst). Festlegung ohne Messung: andere Kopien der JVM sind nicht erfasst.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Adapter ueberschreibt seine Kopien und laesst die Eingabe des Aufrufers`() {
        val recording = RecordingPrimitives()
        val guarded = UserListAuthAdapter(parseOk(listText), recording)
        val input = request("alice", "richtig")
        guarded.authenticate(input)
        val derivation = recording.derivations.single()
        assertContentEquals(CharArray(7), derivation.chars)
        assertContentEquals(ByteArray(32), recording.comparisons.single().left)
        assertContentEquals("richtig".toCharArray(), input.password.reveal())

        val plainRecording = RecordingPrimitives()
        val plain = UserListAuthAdapter(parseOk("p\t{plain}geheim\n", testMode = true), plainRecording)
        plain.authenticate(request("p", "geheim"))
        assertContentEquals(ByteArray("geheim".length), plainRecording.comparisons.single().left)
    }
}
