package io.github.pt9912.tdswire.config.auth

import io.github.pt9912.tdswire.domain.Principal
import java.nio.CharBuffer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Ergebnis des inhaltsunabhängigen Vergleichs: [equal] und die Zahl der untersuchten Positionen. */
internal class Comparison(val equal: Boolean, val steps: Int)

/**
 * Die zwei Rechenschritte, deren Ausführung der Strukturtest beobachtet: Ableitung und Vergleich.
 * Der Vergleich untersucht jede Position bis zur längeren Länge, ohne beim ersten Unterschied
 * abzubrechen; über Zeit sagt diese Struktur nichts (nicht gemessen, `ADR-0009` §Offen).
 */
internal interface AuthPrimitives {
    fun derive(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int): ByteArray

    fun compare(left: ByteArray, right: ByteArray): Comparison
}

/** `PBKDF2WithHmacSHA256` des JDK, ohne Fremdbibliothek (`ADR-0011`). */
internal object JdkAuthPrimitives : AuthPrimitives {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val BITS_PER_BYTE = 8

    override fun derive(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, bytes * BITS_PER_BYTE)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    override fun compare(left: ByteArray, right: ByteArray): Comparison {
        val steps = maxOf(left.size, right.size)
        var difference = left.size xor right.size
        for (index in 0 until steps) {
            val a = if (index < left.size) left[index].toInt() else 0
            val b = if (index < right.size) right[index].toInt() else 0
            difference = difference or (a xor b)
        }
        return Comparison(difference == 0, steps)
    }
}

/**
 * Einstiegsimplementierung des Adapters: feste Benutzerliste (`SPEC-051`, `ADR-0011`).
 *
 * Bauart (Anforderung, strukturell belegt, nicht gemessen): Der Vergleich bricht nicht beim ersten
 * Unterschied ab; bei unbekanntem Benutzernamen läuft derselbe Rechenschritt gegen den Prüfwert der
 * Attrappe der Liste (Scheinvergleich, Wahl siehe [UserList]: `pbkdf2`-Eintrag mit den meisten Iterationen),
 * das Ergebnis wird verworfen. Bei gemischten Listen bleibt eine Grenze (siehe [UserList]).
 * Eingaben über [AuthRequest.MAX_INPUT_UNITS] werden vor der Suche abgelehnt, ohne Ausnahme.
 * Das Passwort wird als UTF-8 der Zeichen abgeleitet, ohne Normalisierung; ein alleinstehendes
 * Hochsurrogat ergibt dieselbe Ableitung wie `?` (bekannte Eigenschaft, `ADR-0011`).
 *
 * Überschreiben (belegt durch Test): das an die Ableitung übergebene Zeichenfeld, die abgeleiteten Bytes und
 * der UTF-8-Kandidat des `plain`-Pfads. Ohne Test: `PBEKeySpec.clearPassword()` und die Nullung des
 * UTF-8-Zwischenpuffers im `plain`-Pfad. Das `SecretKey`-Objekt der JDK-Ableitung wird nicht überschrieben
 * (aus dem Code gelesen, nicht gemessen).
 */
class UserListAuthAdapter internal constructor(private val users: UserList, private val primitives: AuthPrimitives) :
    AuthAdapter {
    constructor(users: UserList) : this(users, JdkAuthPrimitives)

    override fun authenticate(request: AuthRequest): AuthResult =
        if (tooLong(request)) AuthResult.Rejected(RejectReason.INPUT_TOO_LONG) else decide(request)

    private fun tooLong(request: AuthRequest): Boolean =
        maxOf(request.userName.length, request.password.length) > AuthRequest.MAX_INPUT_UNITS

    private fun decide(request: AuthRequest): AuthResult {
        val entry = users.find(request.userName)
        val matches = verify(entry?.secret ?: users.decoy.secret, request.password)
        return when {
            entry == null -> AuthResult.Rejected(RejectReason.UNKNOWN_USER)
            matches -> AuthResult.Accepted(Principal(entry.name))
            else -> AuthResult.Rejected(RejectReason.WRONG_PASSWORD)
        }
    }

    private fun verify(secret: StoredSecret, password: PasswordChars): Boolean {
        val chars = password.reveal()
        try {
            return when (secret) {
                is StoredSecret.Plain -> compareWiped(utf8Of(chars), secret.utf8)
                is StoredSecret.Pbkdf2 -> compareWiped(derived(chars, secret), secret.hash)
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun derived(chars: CharArray, secret: StoredSecret.Pbkdf2): ByteArray =
        primitives.derive(chars, secret.salt, secret.iterations, secret.hash.size)

    private fun compareWiped(candidate: ByteArray, stored: ByteArray): Boolean {
        val result = primitives.compare(candidate, stored)
        candidate.fill(0)
        return result.equal
    }

    private fun utf8Of(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }
}
