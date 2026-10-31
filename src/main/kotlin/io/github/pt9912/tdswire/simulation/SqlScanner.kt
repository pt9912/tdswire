package io.github.pt9912.tdswire.simulation

/** Warum ein Anweisungstext keiner Form von `SPEC-036` zugeordnet wurde. */
enum class Unrecognized {
    EMPTY_TEXT,
    UNTERMINATED_LITERAL,
    UNTERMINATED_COMMENT,
    UNTERMINATED_IDENTIFIER,
    EMPTY_IDENTIFIER,
    UNSUPPORTED_CHARACTER,
    MALFORMED_NUMBER,
    UNEXPECTED_TOKEN,
    UNEXPECTED_END,
    COUNT_MISMATCH,
    DUPLICATE_COLUMN,
}

/** Ein Token des Zeichenscanners; Leerraum und Kommentare erzeugen keines. */
internal sealed interface Token {
    /** Ein Wort ohne Anführung: Schlüsselwort oder unquotierter Bezeichner. */
    data class Word(val text: String) : Token

    /** Ein Bezeichner in `[…]` oder `"…"`, entschachtelt. */
    data class Quoted(val text: String) : Token

    /** Eine Zahl wie geschrieben: `-` optional, Ziffern, optional `.` und Ziffern. */
    data class Number(val text: String) : Token

    /** Ein Textliteral, entschachtelt; [national] ist wahr bei `N'…'`. */
    data class Text(val value: String, val national: Boolean) : Token

    /** Ein Parameter `@name` ohne das `@`. */
    data class Param(val name: String) : Token

    /** Eines von `, ( ) . * = ;`. */
    data class Symbol(val char: Char) : Token
}

/** Das Ergebnis des Scanners. */
internal sealed interface ScanResult {
    data class Tokens(val tokens: List<Token>) : ScanResult

    data class Failed(val reason: Unrecognized) : ScanResult
}

/**
 * Der Zeichenscanner (`TDS-FA-11.a` Schritte 1 und 2): zerlegt einen Anweisungstext in Token und
 * überliest Leerraum und Kommentare außerhalb von Literalen und Bezeichnern. Er wertet kein SQL aus und
 * liest den Text einmal von vorn nach hinten, ohne Rekursion.
 *
 * Festlegungen ohne Messung am Server (`SPEC-036` Scanner): Leerraum sind Leerzeichen, Tab, CR und LF;
 * ein Zeilenkommentar endet an CR oder LF; Blockkommentare schachteln, ein offener Kommentar, ein offenes
 * Literal und ein offener Bezeichner sind nicht erkannt; `N'…'` gilt mit `N` oder `n` unmittelbar vor dem
 * Hochkomma; `''`, `]]` und `""` stehen für ein Zeichen; Zahlen sind ASCII-Ziffern, ein `-` unmittelbar
 * vor der ersten Ziffer gehört zur Zahl, ein Punkt braucht Ziffern auf beiden Seiten, Buchstaben, Ziffern
 * oder `.` hinter einer Zahl machen den Text nicht erkannt; Wörter bestehen aus Unicode-Buchstaben,
 * ASCII-Ziffern, `_`, `#` und `$` und beginnen nicht mit einer Ziffer; ein Parameter ist `@` und mindestens
 * ein Wortzeichen. Jedes andere Zeichen, auch ein geschütztes Leerzeichen, ist nicht erkannt.
 */
internal class SqlScanner(private val text: String) {
    private var pos = 0
    private val tokens = ArrayList<Token>()

    fun scan(): ScanResult {
        while (pos < text.length) {
            val failure = step()
            if (failure != null) return ScanResult.Failed(failure)
        }
        return ScanResult.Tokens(tokens)
    }

    private fun step(): Unrecognized? {
        val c = text[pos]
        val next = if (pos + 1 < text.length) text[pos + 1] else NUL
        return when (c) {
            ' ', '\t', '\r', '\n' -> advance(1)

            '-' -> if (next == '-') advance(SqlLexemes.lineCommentEnd(text, pos) - pos) else other(c, next)

            '/' -> blockComment(next)

            '\'' -> literal(national = false, start = pos)

            '[' -> quoted(']')

            '"' -> quoted('"')

            '@' -> parameter()

            ',', '(', ')', '.', '*', '=', ';' -> {
                tokens += Token.Symbol(c)
                advance(1)
            }

            else -> other(c, next)
        }
    }

    private fun other(c: Char, next: Char): Unrecognized? = when {
        c in '0'..'9' || (c == '-' && next in '0'..'9') -> number()
        (c == 'N' || c == 'n') && next == '\'' -> literal(national = true, start = pos + 1)
        SqlLexemes.isWordStart(c) -> word()
        else -> Unrecognized.UNSUPPORTED_CHARACTER
    }

    private fun advance(count: Int): Unrecognized? {
        pos += count
        return null
    }

    private fun blockComment(next: Char): Unrecognized? {
        if (next != '*') return Unrecognized.UNSUPPORTED_CHARACTER
        val end = SqlLexemes.blockCommentEnd(text, pos)
        return if (end == null) Unrecognized.UNTERMINATED_COMMENT else advance(end - pos)
    }

    /** Liest ein Literal; [start] ist die Stelle des öffnenden Hochkommas. */
    private fun literal(national: Boolean, start: Int): Unrecognized? {
        val (value, end) = SqlLexemes.readDelimited(text, start + 1, '\'') ?: return Unrecognized.UNTERMINATED_LITERAL
        tokens += Token.Text(value, national)
        return advance(end - pos)
    }

    /** Liest `[…]` ([close] ist `]`) oder `"…"` ([close] ist `"`). */
    private fun quoted(close: Char): Unrecognized? {
        val read = SqlLexemes.readDelimited(text, pos + 1, close)
        return when {
            read == null -> Unrecognized.UNTERMINATED_IDENTIFIER

            read.first.isEmpty() -> Unrecognized.EMPTY_IDENTIFIER

            else -> {
                tokens += Token.Quoted(read.first)
                advance(read.second - pos)
            }
        }
    }

    private fun parameter(): Unrecognized? {
        val end = wordEnd(pos + 1)
        if (end == pos + 1) return Unrecognized.UNSUPPORTED_CHARACTER
        tokens += Token.Param(text.substring(pos + 1, end))
        return advance(end - pos)
    }

    private fun number(): Unrecognized? {
        var end = SqlLexemes.digitsEnd(text, if (text[pos] == '-') pos + 1 else pos)
        if (end + 1 < text.length && text[end] == '.' && text[end + 1] in '0'..'9') {
            end = SqlLexemes.digitsEnd(text, end + 1)
        }
        if (end < text.length && (SqlLexemes.isWordPart(text[end]) || text[end] == '.')) {
            return Unrecognized.MALFORMED_NUMBER
        }
        tokens += Token.Number(text.substring(pos, end))
        return advance(end - pos)
    }

    private fun word(): Unrecognized? {
        val end = wordEnd(pos + 1)
        tokens += Token.Word(text.substring(pos, end))
        return advance(end - pos)
    }

    private fun wordEnd(from: Int): Int {
        var end = from
        while (end < text.length && SqlLexemes.isWordPart(text[end])) end++
        return end
    }

    private companion object {
        const val NUL = '\u0000'
    }
}
