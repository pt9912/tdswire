package io.github.pt9912.tdswire.config.auth

import java.util.Base64

private const val SCHEME_PLAIN = "plain"
private const val SCHEME_PBKDF2 = "pbkdf2-sha256"
private const val PBKDF2_FIELDS = 3
private const val BASE64_GROUP = 4
private val ITERATIONS_SHAPE = Regex("[1-9][0-9]{0,7}")
private val BASE64_SHAPE = Regex("[A-Za-z0-9+/]*={0,2}")

/**
 * Zerlegt die Wertseite eines Eintrags, `{kennung}Wert` (`ADR-0011`). Die Verfahrenskennung ist
 * case-sensitiv und klein; die Meldungen der Aufrufe darunter werden nie weitergereicht.
 */
internal class SecretValueParser(private val plainTestMode: Boolean) {
    fun parse(value: String): Parsed<StoredSecret> {
        val close = value.indexOf('}')
        return when {
            !value.startsWith('{') || close < 0 -> Parsed.Err(UserListErrorKind.MISSING_SCHEME)
            else -> parseBySchema(value.substring(1, close), value.substring(close + 1))
        }
    }

    private fun parseBySchema(scheme: String, rest: String): Parsed<StoredSecret> = when (scheme) {
        SCHEME_PLAIN -> parsePlain(rest)
        SCHEME_PBKDF2 -> parsePbkdf2(rest)
        else -> Parsed.Err(UserListErrorKind.UNKNOWN_SCHEME)
    }

    private fun parsePlain(rest: String): Parsed<StoredSecret> = if (plainTestMode) {
        Parsed.Ok(StoredSecret.Plain(rest.toByteArray(Charsets.UTF_8)))
    } else {
        Parsed.Err(UserListErrorKind.PLAIN_NOT_ALLOWED)
    }

    private fun parsePbkdf2(rest: String): Parsed<StoredSecret> {
        val fields = rest.split('$')
        return when {
            fields.size != PBKDF2_FIELDS -> Parsed.Err(UserListErrorKind.FIELD_COUNT)

            else -> parseSalt(fields[0]).then { salt ->
                parseIterations(fields[1]).then { iterations ->
                    parseHash(fields[2]).then { hash -> Parsed.Ok(StoredSecret.Pbkdf2(salt, iterations, hash)) }
                }
            }
        }
    }

    private fun parseSalt(text: String): Parsed<ByteArray> {
        val bytes = decodeBase64(text)
        return when {
            bytes == null -> Parsed.Err(UserListErrorKind.SALT_INVALID)
            bytes.size < Pbkdf2Limits.MIN_SALT_BYTES -> Parsed.Err(UserListErrorKind.SALT_TOO_SHORT)
            else -> Parsed.Ok(bytes)
        }
    }

    private fun parseHash(text: String): Parsed<ByteArray> {
        val bytes = decodeBase64(text)
        return if (bytes != null && bytes.size == Pbkdf2Limits.HASH_BYTES) {
            Parsed.Ok(bytes)
        } else {
            Parsed.Err(UserListErrorKind.HASH_INVALID)
        }
    }

    private fun parseIterations(text: String): Parsed<Int> {
        val value = if (ITERATIONS_SHAPE.matches(text)) text.toInt() else null
        return when {
            value == null -> Parsed.Err(UserListErrorKind.ITERATIONS_INVALID)
            value < Pbkdf2Limits.ITERATIONS_FLOOR -> Parsed.Err(UserListErrorKind.ITERATIONS_BELOW_FLOOR)
            value > Pbkdf2Limits.ITERATIONS_CEILING -> Parsed.Err(UserListErrorKind.ITERATIONS_ABOVE_CEILING)
            else -> Parsed.Ok(value)
        }
    }

    /** Standard-Base64 mit Padding; ein Text, der nicht byte-gleich zurückkodiert, ist ungültig. */
    private fun decodeBase64(text: String): ByteArray? {
        val shaped = text.length % BASE64_GROUP == 0 && BASE64_SHAPE.matches(text)
        val bytes = if (shaped) runCatching { Base64.getDecoder().decode(text) }.getOrNull() else null
        return bytes?.takeIf { Base64.getEncoder().encodeToString(it) == text }
    }
}
