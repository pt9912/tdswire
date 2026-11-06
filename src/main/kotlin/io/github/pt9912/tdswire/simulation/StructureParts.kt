package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType

/**
 * Die Formen D1 und D2 von `SPEC-036`: `CREATE TABLE [<Schema>.]<Tabelle> (<Spalte> {, <Spalte>} [, [CONSTRAINT
 * <Name>] PRIMARY KEY (<Spalten>)])` und `ALTER TABLE [<Schema>.]<Tabelle> ADD <Spalte> {, <Spalte>}`. Eine Spalte ist
 * `<Name> <Typ> [NOT NULL | NULL]`; der Typ ist `INT`, `BIGINT`, `BIT`, `DATE`, `TIME`, `DATETIME2`,
 * `NVARCHAR(<n>)` mit `n` von 1 bis 4000 oder `MAX`, oder `DECIMAL(<p>, <s>)`. `NVARCHAR(MAX)` ist `maxLength` -1
 * (der Katalog meldet `max_length` -1). Gemessen ist der Wortlaut von d-migrate 1.12.0 (`CREATE TABLE`
 * mit `CONSTRAINT [pk_…] PRIMARY KEY ([Id])`, `ALTER TABLE … ADD [Email] NVARCHAR(4000)`); mehrere Spalten in `ADD`
 * sind Setzung. Ein anderer Typ oder eine weitere Klausel (`DEFAULT`, `IDENTITY`, `UNIQUE`) macht den Text nicht
 * erkannt.
 */
internal class StructureParts(private val stream: TokenStream, private val parts: Parts) {
    fun createTable(): Statement {
        stream.keyword("TABLE")
        val table = parts.tableRef()
        stream.symbol('(')
        val columns = ArrayList<SimColumn>()
        var key: List<String>? = null
        do {
            if (isKeyDefinition()) {
                if (key != null) throw unexpected()
                key = primaryKey()
            } else {
                columns += column()
            }
        } while (stream.acceptSymbol(','))
        stream.symbol(')')
        if (columns.isEmpty() || SimNames.firstDuplicate(columns.map { it.name }) != null) {
            throw Reject(Unrecognized.DUPLICATE_COLUMN)
        }
        return Statement.CreateTable(table, columns, key)
    }

    fun addColumns(): Statement {
        stream.keyword("TABLE")
        val table = parts.tableRef()
        stream.keyword("ADD")
        val columns = parts.list { column() }
        if (SimNames.firstDuplicate(columns.map { it.name }) != null) throw Reject(Unrecognized.DUPLICATE_COLUMN)
        return Statement.AddColumns(table, columns)
    }

    private fun isKeyDefinition(): Boolean {
        val next = stream.peek()
        return next != null && (next.isKeyword("CONSTRAINT") || next.isKeyword("PRIMARY"))
    }

    private fun primaryKey(): List<String> {
        if (stream.acceptKeyword("CONSTRAINT")) parts.identifier()
        stream.keyword("PRIMARY")
        stream.keyword("KEY")
        stream.symbol('(')
        val names = parts.list { parts.identifier() }
        stream.symbol(')')
        if (SimNames.firstDuplicate(names) != null) throw Reject(Unrecognized.DUPLICATE_COLUMN)
        return names
    }

    private fun column(): SimColumn {
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
        return SimColumn(name, type, nullable(), length)
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

    private fun nullable(): Boolean = when {
        stream.acceptKeyword("NOT") -> {
            stream.keyword("NULL")
            false
        }

        stream.acceptKeyword("NULL") -> true

        else -> true
    }

    private companion object {
        const val MAX_PRECISION = 38
        const val MAX_DIGITS = 5
    }
}
