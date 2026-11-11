package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType

/** Eine Spalte der Formen D1 und D2 mit der Eindeutigkeit, die ihre Klauseln benennen (`CONSTRAINT <Name> UNIQUE`). */
internal data class ParsedColumn(val column: SimColumn, val unique: SimUniqueKey?)

/** Der Standardwert in der Form, in der der Katalog ihn meldet (Messprotokoll M-21, `SimColumn.defaultDefinition`). */
internal object DefaultDefinitions {
    /**
     * Zahl `((n))`, Text `('…')`, nationaler Text `(N'…')`, das Hochkomma im Text verdoppelt. Gemessen sind `DEFAULT 1`
     * und `DEFAULT N'neu'`; Dezimalzahlen, negative Zahlen und Text ohne `N` folgen derselben Regel (Setzung).
     */
    fun of(token: Token): String? = when (token) {
        is Token.Number -> "((${token.text}))"

        is Token.Text -> {
            val body = "'" + token.value.replace("'", "''") + "'"
            if (token.national) "(N$body)" else "($body)"
        }

        else -> null
    }
}

/**
 * Die Spalte der Formen D1 und D2 (`SPEC-036`): `<Name> <Typ> {Klausel}`. Der Typ ist `INT`, `BIGINT`, `BIT`, `DATE`,
 * `TIME`, `DATETIME2`, `NVARCHAR(<n>)` mit `n` von 1 bis 4000 oder `MAX`, oder `DECIMAL(<p>, <s>)`. Eine Klausel ist
 * `NOT NULL`, `NULL`, `CONSTRAINT <Name> DEFAULT <Literal>` oder `CONSTRAINT <Name> UNIQUE`, jede höchstens einmal.
 * Ein Standardwert ohne `CONSTRAINT`-Name und jede weitere Klausel (`IDENTITY`, `PRIMARY KEY`) macht den Text nicht
 * erkannt.
 */
internal class ColumnParts(private val stream: TokenStream, private val parts: Parts) {
    /** Die Klauseln einer Spalte, soweit gelesen. */
    private class Clauses {
        var nullable: Boolean? = null
        var default: String? = null
        var unique: SimUniqueKey? = null
    }

    fun column(): ParsedColumn {
        val name = parts.identifier()
        val head = stream.next()
        if (head !is Token.Word) throw unexpected()
        var length = MAX_TEXT_LENGTH
        val type = when (asciiUpper(head.text)) {
            "INT" -> SqlType.Int32
            "BIGINT" -> SqlType.Int64
            "BIT" -> SqlType.Bool
            "DATE" -> SqlType.Date
            "TIME" -> SqlType.Time
            "DATETIME2" -> SqlType.DateTime
            "NVARCHAR" -> SqlType.UnicodeText.also { length = textLength() }
            "DECIMAL" -> decimal()
            else -> throw unexpected()
        }
        val clauses = clauses(name)
        val column = SimColumn(name, type, clauses.nullable ?: true, length, clauses.default)
        return ParsedColumn(column, clauses.unique)
    }

    private fun clauses(column: String): Clauses {
        val clauses = Clauses()
        var more = true
        while (more) more = clause(clauses, column)
        return clauses
    }

    private fun clause(clauses: Clauses, column: String): Boolean {
        when {
            stream.acceptKeyword("NOT") -> {
                stream.keyword("NULL")
                setNullability(clauses, false)
            }

            stream.acceptKeyword("NULL") -> setNullability(clauses, true)

            stream.acceptKeyword("CONSTRAINT") -> named(clauses, parts.identifier(), column)

            else -> return false
        }
        return true
    }

    private fun setNullability(clauses: Clauses, nullable: Boolean) {
        if (clauses.nullable != null) throw unexpected()
        clauses.nullable = nullable
    }

    private fun named(clauses: Clauses, constraint: String, column: String) {
        when {
            stream.acceptKeyword("DEFAULT") -> setDefault(clauses)
            stream.acceptKeyword("UNIQUE") -> setUnique(clauses, SimUniqueKey(constraint, listOf(column)))
            else -> throw unexpected()
        }
    }

    private fun setDefault(clauses: Clauses) {
        if (clauses.default != null) throw unexpected()
        clauses.default = DefaultDefinitions.of(stream.next()) ?: throw unexpected()
    }

    private fun setUnique(clauses: Clauses, key: SimUniqueKey) {
        if (clauses.unique != null) throw unexpected()
        clauses.unique = key
    }

    private fun textLength(): Int {
        stream.symbol('(')
        val token = stream.next()
        val length = if (token.isKeyword("MAX")) UNBOUNDED_TEXT else whole(token)
        stream.symbol(')')
        if (length != UNBOUNDED_TEXT && length !in 1..MAX_TEXT_LENGTH) throw Reject(Unrecognized.MALFORMED_NUMBER)
        return length
    }

    private fun decimal(): SqlType {
        stream.symbol('(')
        val precision = whole()
        stream.symbol(',')
        val scale = whole()
        stream.symbol(')')
        if (precision !in 1..MAX_PRECISION || scale !in 0..precision) throw Reject(Unrecognized.MALFORMED_NUMBER)
        return SqlType.Decimal(precision, scale)
    }

    private fun whole(token: Token = stream.next()): Int =
        (token as? Token.Number)?.let { small(it) } ?: throw Reject(Unrecognized.MALFORMED_NUMBER)

    /** Die Ganzzahl höchstens fünf Ziffern lang oder `null`. */
    private fun small(token: Token.Number): Int? =
        token.text.takeIf { it.length <= MAX_DIGITS && it.all { c -> c in '0'..'9' } }?.toInt()

    private companion object {
        const val MAX_PRECISION = 38
        const val MAX_DIGITS = 5
    }
}
