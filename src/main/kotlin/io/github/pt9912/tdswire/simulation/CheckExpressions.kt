package io.github.pt9912.tdswire.simulation

/**
 * Der Ausdruck einer Prüfbedingung `CHECK ( … )` (`SPEC-036` Form D) und seine Form im Katalog. Erkannt ist die
 * Teilmenge: Vergleiche (`=`, `<`, `>`, `<=`, `>=`, `<>`) zwischen Spalten, Zahlen und Textliteralen, `<Operand> IS
 * [NOT] NULL`, verbunden mit `AND` und `OR`, Klammern bis zur Tiefe 16. Alles andere (Funktionen, `IN`, `BETWEEN`,
 * `LIKE`, `NOT`, Rechenzeichen) macht den Text nicht erkannt.
 *
 * Die Form des Katalogs folgt dem gemessenen Fall (`Menge > 0` ergibt `([Menge]>(0))`, Messprotokoll M-21): Bezeichner
 * in `[…]`, eine Zahl in Klammern, Vergleichszeichen ohne Leerzeichen, `AND`, `OR`, `IS NULL` mit Leerzeichen,
 * Klammern des Textes bleiben, der ganze Ausdruck steht in einem Klammerpaar. Alles über den einen Fall hinaus
 * (mehrere Vergleiche, Texte, `IS NULL`, Klammerung) ist Setzung nach dem Handbuch des Servers, nicht gemessen.
 */
internal class CheckExpressions(private val stream: TokenStream, private val parts: Parts) {
    /** Liest `( <Ausdruck> )` und liefert den Ausdruck in der Form des Katalogs, in einem Klammerpaar. */
    fun check(): String {
        stream.symbol('(')
        val text = expression(1)
        stream.symbol(')')
        return "($text)"
    }

    private fun expression(depth: Int): String {
        if (depth > MAX_DEPTH) throw unexpected()
        val items = arrayListOf(group(depth))
        val joins = ArrayList<String>()
        while (true) {
            val join = when {
                stream.acceptKeyword("AND") -> "AND"
                stream.acceptKeyword("OR") -> "OR"
                else -> break
            }
            joins += join
            items += group(depth)
        }
        return buildString {
            append(items[0])
            joins.forEachIndexed { at, join -> append(' ').append(join).append(' ').append(items[at + 1]) }
        }
    }

    private fun group(depth: Int): String {
        if (!stream.acceptSymbol('(')) return comparison()
        val inner = expression(depth + 1)
        stream.symbol(')')
        return "($inner)"
    }

    private fun comparison(): String {
        val left = operand()
        if (stream.acceptKeyword("IS")) {
            val negated = stream.acceptKeyword("NOT")
            stream.keyword("NULL")
            return left + (if (negated) " IS NOT NULL" else " IS NULL")
        }
        return left + operator() + operand()
    }

    private fun operator(): String = when {
        stream.acceptSymbol('=') -> "="

        stream.acceptSymbol('<') -> when {
            stream.acceptSymbol('=') -> "<="
            stream.acceptSymbol('>') -> "<>"
            else -> "<"
        }

        stream.acceptSymbol('>') -> if (stream.acceptSymbol('=')) ">=" else ">"

        else -> throw unexpected()
    }

    private fun operand(): String = when (val token = stream.peek()) {
        is Token.Number -> {
            stream.next()
            "(${token.text})"
        }

        is Token.Text -> {
            stream.next()
            (if (token.national) "N" else "") + "'" + token.value.replace("'", "''") + "'"
        }

        else -> "[" + parts.identifier().replace("]", "]]") + "]"
    }

    private companion object {
        const val MAX_DEPTH = 16
    }
}
