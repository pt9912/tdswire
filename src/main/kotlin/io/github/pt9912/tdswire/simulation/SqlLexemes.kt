package io.github.pt9912.tdswire.simulation

/** Die Teilschritte des Zeichenscanners, die nur Text und Stelle brauchen (`SPEC-036` Scanner). */
internal object SqlLexemes {
    /** Der Index am Ende des Zeilenkommentars ab [from]: an CR, LF oder am Textende. */
    fun lineCommentEnd(text: String, from: Int): Int {
        var end = from
        while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
        return end
    }

    /** Der Index hinter dem Blockkommentar, der bei [from] beginnt, oder `null`, wenn er offen bleibt. */
    fun blockCommentEnd(text: String, from: Int): Int? {
        var depth = 1
        var pos = from + 2
        while (pos < text.length && depth > 0) {
            val opens = text.startsWith("/*", pos)
            val closes = text.startsWith("*/", pos)
            if (opens) depth++
            if (closes) depth--
            pos += if (opens || closes) 2 else 1
        }
        return pos.takeIf { depth == 0 }
    }

    /**
     * Liest ab [from] bis zum Schließzeichen [close]; ein verdoppeltes [close] steht für ein Zeichen. Liefert den
     * Inhalt und den Index hinter dem Schließzeichen oder `null`, wenn der Text vorher endet.
     */
    fun readDelimited(text: String, from: Int, close: Char): Pair<String, Int>? {
        val value = StringBuilder()
        var i = from
        while (i < text.length) {
            val c = text[i]
            val doubled = c == close && i + 1 < text.length && text[i + 1] == close
            when {
                doubled -> {
                    value.append(close)
                    i += 2
                }

                c == close -> return value.toString() to (i + 1)

                else -> {
                    value.append(c)
                    i++
                }
            }
        }
        return null
    }

    /** Der Index hinter den ASCII-Ziffern ab [from]. */
    fun digitsEnd(text: String, from: Int): Int {
        var end = from
        while (end < text.length && text[end] in '0'..'9') end++
        return end
    }

    fun isWordStart(c: Char): Boolean = c.isLetter() || c == '_' || c == '#' || c == '$'

    fun isWordPart(c: Char): Boolean = isWordStart(c) || c in '0'..'9'
}
