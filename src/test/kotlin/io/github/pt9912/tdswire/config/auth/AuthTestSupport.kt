package io.github.pt9912.tdswire.config.auth

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unabhängiges Orakel für PBKDF2-HMAC-SHA256 nach RFC 8018 §5.2 aus `Mac`, ohne `SecretKeyFactory`.
 * Für Ausgaben bis 32 Byte (ein Block).
 */
internal fun oraclePbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
    require(length in 1..32)
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec((if (password.isEmpty()) ByteArray(1) else password), "HmacSHA256"))
    var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
    val t = u.copyOf()
    repeat(iterations - 1) {
        u = mac.doFinal(u)
        for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
    }
    return t.copyOf(length)
}

internal fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

internal val testSalt = ByteArray(16) { (it + 1).toByte() }

private val derivedCache = HashMap<Pair<String, Int>, String>()

/** Der Prüfwert `{pbkdf2-sha256}Salz$Iterationen$Hash` zu [password], vom Orakel gerechnet. */
internal fun pbkdf2Value(password: String, iterations: Int = Pbkdf2Limits.ITERATIONS_FLOOR): String {
    val hash = derivedCache.getOrPut(password to iterations) {
        b64(oraclePbkdf2(password.toByteArray(Charsets.UTF_8), testSalt, iterations, Pbkdf2Limits.HASH_BYTES))
    }
    return "{pbkdf2-sha256}${b64(testSalt)}\$$iterations\$$hash"
}

internal fun parseOk(text: String, testMode: Boolean = false): UserList =
    (UserList.parse(text, testMode) as UserListResult.Valid).list

internal fun request(user: String, password: String): AuthRequest = AuthRequest(
    userName = user,
    password = PasswordChars(password.toCharArray()),
    context = ConnectionContext("master", "app", "host", false),
    encryption = NegotiatedEncryption.ON,
    features = AuthFeatures(integratedSecurity = false, federatedAuthentication = false, changePassword = false),
)

/** Reicht an die echten Rechenschritte durch und merkt sich Aufrufe und die übergebenen Felder. */
internal class RecordingPrimitives(private val real: AuthPrimitives = JdkAuthPrimitives) : AuthPrimitives {
    class Derivation(val chars: CharArray, val saltSize: Int, val iterations: Int, val bytes: Int)

    class Compared(val left: ByteArray, val right: ByteArray, val steps: Int)

    val derivations = mutableListOf<Derivation>()
    val comparisons = mutableListOf<Compared>()

    override fun derive(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int): ByteArray {
        derivations += Derivation(password, salt.size, iterations, bytes)
        return real.derive(password, salt, iterations, bytes)
    }

    override fun compare(left: ByteArray, right: ByteArray): Comparison {
        val result = real.compare(left, right)
        comparisons += Compared(left, right, result.steps)
        return result
    }
}
