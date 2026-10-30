package io.github.pt9912.tdswire.config.auth

/** Zwischenergebnis des Parsers: ein Wert oder genau eine Fehlerart. */
internal sealed interface Parsed<out T> {
    class Ok<out T>(val value: T) : Parsed<T>

    class Err(val kind: UserListErrorKind) : Parsed<Nothing>
}

internal inline fun <T, R> Parsed<T>.then(next: (T) -> Parsed<R>): Parsed<R> = when (this) {
    is Parsed.Ok -> next(value)
    is Parsed.Err -> this
}

/**
 * Zerlegt den Dateiinhalt in Zeilen und die Zeilen in Benutzername und Prüfwert. Die Meldungen der
 * Aufrufe darunter werden nie weitergereicht (`ADR-0011`: `parseInt` nennt die Eingabe).
 */
internal class UserListParser(plainTestMode: Boolean) {
    private val values = SecretValueParser(plainTestMode)

    fun parse(text: String): UserListResult {
        val lines = text.split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
        return when {
            lines.isEmpty() -> UserListResult.Invalid(UserListError(UserListErrorKind.EMPTY_LIST, null))
            else -> parseLines(lines)
        }
    }

    private fun parseLines(lines: List<String>): UserListResult {
        val entries = LinkedHashMap<String, UserEntry>()
        var error: UserListError? = null
        for ((index, line) in lines.withIndex()) {
            when (val outcome = parseLine(line, entries)) {
                is Parsed.Ok -> entries[outcome.value.name] = outcome.value
                is Parsed.Err -> error = UserListError(outcome.kind, index + 1)
            }
            if (error != null) break
        }
        return error?.let { UserListResult.Invalid(it) } ?: UserListResult.Valid(UserList.of(entries.values.toList()))
    }

    private fun parseLine(line: String, seen: Map<String, UserEntry>): Parsed<UserEntry> =
        checkLineShape(line).then { tab ->
            checkName(line.substring(0, tab), seen).then { name ->
                values.parse(line.substring(tab + 1)).then { secret -> Parsed.Ok(UserEntry(name, secret)) }
            }
        }

    private fun checkLineShape(line: String): Parsed<Int> = when {
        line.contains('\r') -> Parsed.Err(UserListErrorKind.CARRIAGE_RETURN)
        line.isEmpty() -> Parsed.Err(UserListErrorKind.EMPTY_LINE)
        line.indexOf('\t') < 0 -> Parsed.Err(UserListErrorKind.MISSING_TAB)
        else -> Parsed.Ok(line.indexOf('\t'))
    }

    private fun checkName(name: String, seen: Map<String, UserEntry>): Parsed<String> = when {
        name.isEmpty() -> Parsed.Err(UserListErrorKind.EMPTY_USER_NAME)
        hasUnpairedSurrogate(name) -> Parsed.Err(UserListErrorKind.INVALID_USER_NAME)
        name in seen -> Parsed.Err(UserListErrorKind.DUPLICATE_USER_NAME)
        else -> Parsed.Ok(name)
    }

    private fun hasUnpairedSurrogate(text: String): Boolean {
        var index = 0
        var unpaired = false
        while (index < text.length) {
            val char = text[index]
            val paired = char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()
            unpaired = unpaired || (!paired && (char.isHighSurrogate() || char.isLowSurrogate()))
            index += if (paired) 2 else 1
        }
        return unpaired
    }
}
