package io.github.pt9912.tdswire.runtime

private const val HEX_RADIX = 16
private const val HEX_DIGITS = 4

/**
 * Liest eine Zeile des Log-Adapters des Testservers als flaches JSON-Objekt (Zeichenketten und ganze Zahlen) streng
 * nach der Grammatik von RFC 8259, soweit sie für diese Form gilt; jede Abweichung ist `null`. Die Bibliothek und ihre
 * Tests führen kein Jackson (`ADR-0025`), deshalb liest dieser Leser selbst; er prüft die Form der Zeile, nicht den
 * Schreiber des Adapters, der `jackson-core` nutzt. Ein doppelter Schlüssel und jedes Zeichen außerhalb von
 * druckbarem ASCII machen die Zeile ungültig.
 */
internal object FlatJson {
    private const val STRING = """"((?:[^"\\\u0000-\u001F]|\\["\\/bfnrt]|\\u[0-9a-fA-F]{4})*)""""
    private val FIELD = Regex("""($STRING):($STRING|-?[0-9]+)""")
    private val LINE = Regex("""\{(?:$FIELD(?:,$FIELD)*)?}""")

    fun parse(line: String): Map<String, Any>? {
        if (!line.all { it.code in ' '.code..'~'.code } || !LINE.matches(line)) return null
        val fields = FIELD.findAll(line.substring(1, line.length - 1)).map { match ->
            val raw = match.groupValues[4].takeIf { match.groupValues[3].startsWith("\"") }
            unescape(match.groupValues[2]) to (raw?.let(::unescape) ?: match.groupValues[3].toLong())
        }.toList()
        return fields.toMap().takeIf { it.size == fields.size }
    }

    private fun unescape(text: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            if (char != '\\') {
                out.append(char)
            } else {
                val code = text[index++]
                if (code == 'u') {
                    out.append(text.substring(index, index + HEX_DIGITS).toInt(HEX_RADIX).toChar())
                    index += HEX_DIGITS
                } else {
                    out.append(
                        when (code) {
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> code
                        },
                    )
                }
            }
        }
        return out.toString()
    }
}
