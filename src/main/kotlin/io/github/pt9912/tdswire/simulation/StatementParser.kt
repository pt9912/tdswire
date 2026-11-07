package io.github.pt9912.tdswire.simulation

import java.math.BigInteger

private val INTEGER = Regex("-?[0-9]+")

/** Abbruch der Zuordnung mit Grund; ohne Stapelspur, weil er nur den Ablauf lenkt. */
internal class Reject(val reason: Unrecognized) : Exception(null, null, false, false)

/** Die fünfzehn Wörter, die nur quotiert Bezeichner sind (`SPEC-036` Scanner). */
private val RESERVED = setOf(
    "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "IS", "NULL", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
    "DELETE",
)

/** Das Wort in ASCII-Großschreibung; Schlüsselwörter sind ASCII, `ınto` ist keines. */
internal fun asciiUpper(word: String): String = buildString(word.length) {
    for (c in word) append(if (c in 'a'..'z') c - ('a' - 'A') else c)
}

internal fun Token.isKeyword(word: String): Boolean = this is Token.Word && asciiUpper(text) == word

internal fun unexpected(): Reject = Reject(Unrecognized.UNEXPECTED_TOKEN)

/** Die Token einer Anweisung, von vorn nach hinten gelesen. */
internal class TokenStream(private val tokens: List<Token>) {
    private var index = 0

    fun next(): Token {
        if (index >= tokens.size) throw Reject(Unrecognized.UNEXPECTED_END)
        return tokens[index++]
    }

    fun atEnd(): Boolean = index >= tokens.size

    fun peek(): Token? = tokens.getOrNull(index)

    fun keyword(word: String) {
        if (!next().isKeyword(word)) throw unexpected()
    }

    fun symbol(char: Char) {
        if (next() != Token.Symbol(char)) throw unexpected()
    }

    fun acceptKeyword(word: String): Boolean {
        val hit = tokens.getOrNull(index)?.isKeyword(word) == true
        if (hit) index++
        return hit
    }

    fun acceptSymbol(char: Char): Boolean {
        val hit = tokens.getOrNull(index) == Token.Symbol(char)
        if (hit) index++
        return hit
    }
}

/** Die Bausteine der Formen: Bezeichner, Wert, Tabelle, Bedingung, Liste. */
internal class Parts(private val stream: TokenStream) {
    fun identifier(): String = when (val token = stream.next()) {
        is Token.Quoted -> token.text
        is Token.Word -> token.text.takeUnless { asciiUpper(it) in RESERVED } ?: throw unexpected()
        else -> throw unexpected()
    }

    fun value(): SimValue = when (val token = stream.next()) {
        is Token.Number -> SimValue.Number(token.text)
        is Token.Text -> SimValue.Text(token.value, token.national)
        is Token.Param -> SimValue.Parameter(token.name)
        is Token.Word -> SimValue.Null.takeIf { token.isKeyword("NULL") } ?: throw unexpected()
        else -> throw unexpected()
    }

    fun tableRef(): TableRef {
        val first = identifier()
        return if (stream.acceptSymbol('.')) TableRef(first, identifier()) else TableRef(null, first)
    }

    /** [constants] erlaubt den Konstantenterm `<Ganzzahl> = <Ganzzahl>`; nur eine Auswahl darf ihn tragen. */
    fun whereClause(constants: Boolean = false): List<Term> {
        if (!stream.acceptKeyword("WHERE")) return emptyList()
        val terms = ArrayList<Term>()
        do {
            terms += if (constants) constantOrTerm() else term()
        } while (stream.acceptKeyword("AND"))
        return terms
    }

    private fun constantOrTerm(): Term {
        val left = stream.peek()
        if (left !is Token.Number) return term()
        stream.next()
        stream.symbol('=')
        val right = stream.next()
        if (right !is Token.Number) throw unexpected()
        return Term.Constant(integer(left) == integer(right))
    }

    private fun integer(token: Token.Number): BigInteger {
        if (!INTEGER.matches(token.text)) throw Reject(Unrecognized.MALFORMED_NUMBER)
        return BigInteger(token.text)
    }

    private fun term(): Term {
        val column = identifier()
        val operator = stream.next()
        return when {
            operator == Token.Symbol('=') -> Term.In(column, listOf(value()))

            operator.isKeyword("IN") -> {
                stream.symbol('(')
                val values = list { value() }
                stream.symbol(')')
                Term.In(column, values)
            }

            operator.isKeyword("IS") -> {
                val negated = stream.acceptKeyword("NOT")
                stream.keyword("NULL")
                Term.IsNull(column, negated)
            }

            else -> throw unexpected()
        }
    }

    fun <T> list(item: () -> T): List<T> {
        val items = arrayListOf(item())
        while (stream.acceptSymbol(',')) items += item()
        return items
    }
}

/**
 * Die Zuordnung (`TDS-FA-11.a` Schritt 2): ordnet die Token genau einer Form von `SPEC-036` zu, ohne SQL
 * auszuwerten. Ein einzelnes abschließendes `;` wird entfernt; jedes weitere Token, das die Form nicht
 * verlangt, macht den Text nicht erkannt.
 *
 * Festlegung (Lesart des Lastenhefts, vom Nutzer nicht bestätigt, ADR-0012 Offen 12 (a)): die Liste der
 * Formen ist abschließend; was keine Form ist, ist nicht erkannt. Ebenso festgelegt: `INSERT` hat genau eine
 * Wertezeile mit ebenso vielen Werten wie Spalten, und eine Spalte steht in einer Spaltenliste von `INSERT`
 * und `SET` höchstens einmal.
 */
internal object StatementParser {
    fun recognize(sql: String): Recognition {
        val catalog = CatalogForms.recognize(sql)
        val literal = if (catalog == null) CatalogForms.recognizeLiteral(sql) else null
        return when {
            catalog != null -> Recognition.Catalog(catalog)

            literal != null -> Recognition.Catalog(literal.first, literal.second)

            TransactionForms.matches(sql) -> Recognition.Fixed(FixedForm.Transaction)

            else -> when (val scanned = SqlScanner(sql).scan()) {
                is ScanResult.Failed -> Recognition.NotRecognized(scanned.reason)
                is ScanResult.Tokens -> parse(scanned.tokens)
            }
        }
    }

    private fun parse(scanned: List<Token>): Recognition {
        val tokens = if (scanned.lastOrNull() == Token.Symbol(';')) scanned.dropLast(1) else scanned
        if (tokens.isEmpty()) return Recognition.NotRecognized(Unrecognized.EMPTY_TEXT)
        val bulk = BulkForms.probe(tokens, ::parse) ?: BulkForms.insertBulk(tokens)
        val fixed = if (bulk == null) FixedForms.recognize(tokens) else null
        return when {
            bulk != null -> bulk
            fixed != null -> Recognition.Fixed(fixed)
            else -> table(tokens)
        }
    }

    private fun table(tokens: List<Token>): Recognition = try {
        Recognition.Recognized(statement(TokenStream(tokens)))
    } catch (reject: Reject) {
        Recognition.NotRecognized(reject.reason)
    }

    private fun statement(stream: TokenStream): Statement {
        val parts = Parts(stream)
        val first = stream.next()
        val result = when {
            first.isKeyword("SELECT") -> select(stream, parts)
            first.isKeyword("INSERT") -> insert(stream, parts)
            first.isKeyword("UPDATE") -> update(stream, parts)
            first.isKeyword("DELETE") -> delete(stream, parts)
            first.isKeyword("CREATE") -> StructureParts(stream, parts).createTable()
            first.isKeyword("ALTER") -> StructureParts(stream, parts).addColumns()
            else -> throw unexpected()
        }
        if (!stream.atEnd()) throw unexpected()
        return result
    }

    private fun select(stream: TokenStream, parts: Parts): Statement {
        val columns = if (stream.acceptSymbol('*')) null else parts.list { parts.identifier() }
        stream.keyword("FROM")
        return Statement.Select(parts.tableRef(), columns, parts.whereClause(constants = true))
    }

    private fun insert(stream: TokenStream, parts: Parts): Statement {
        stream.keyword("INTO")
        val table = parts.tableRef()
        stream.symbol('(')
        val columns = parts.list { parts.identifier() }
        stream.symbol(')')
        stream.keyword("VALUES")
        stream.symbol('(')
        val values = parts.list { parts.value() }
        stream.symbol(')')
        if (columns.size != values.size) throw Reject(Unrecognized.COUNT_MISMATCH)
        noDuplicates(columns)
        return Statement.Insert(table, columns, values)
    }

    private fun update(stream: TokenStream, parts: Parts): Statement {
        val table = parts.tableRef()
        stream.keyword("SET")
        val set = parts.list {
            val column = parts.identifier()
            stream.symbol('=')
            Assignment(column, parts.value())
        }
        noDuplicates(set.map { it.column })
        return Statement.Update(table, set, parts.whereClause())
    }

    private fun delete(stream: TokenStream, parts: Parts): Statement {
        stream.keyword("FROM")
        return Statement.Delete(parts.tableRef(), parts.whereClause())
    }

    private fun noDuplicates(columns: List<String>) {
        if (SimNames.firstDuplicate(columns) != null) throw Reject(Unrecognized.DUPLICATE_COLUMN)
    }
}
