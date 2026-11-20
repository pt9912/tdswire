package io.github.pt9912.tdswire.simulation

/**
 * Die größte Zahl Auswahlen in einem Text (`SPEC-036`, Form S2): gemessen ist der Text mit zwei Auswahlen
 * (Mitschnitt `mehrteilig`, 2022 und 2025); eine längere Folge ist nicht gemessen und nicht erkannt.
 */
internal const val SERIES_LIMIT = 2

/** Form S2 (`SPEC-036`): zwei Auswahlen in einem Text. */
internal object SeriesForms {
    /**
     * Zerlegt den Text an den `;`-Token des Scanners (ein `;` in Literal, Bezeichner oder Kommentar ist keines).
     * Erkannt sind zwei bis [SERIES_LIMIT] Auswahlen der Form S1; jede andere Anweisung, ein leeres Stück oder mehr
     * Stücke machen den Text nicht erkannt, und der Grund ist der des ersten Stücks, das scheitert.
     */
    fun recognize(tokens: List<Token>, single: (List<Token>) -> Recognition): Recognition {
        val pieces = splitAtSemicolons(tokens)
        val recognized = pieces.map {
            if (it.isEmpty()) Recognition.NotRecognized(Unrecognized.UNEXPECTED_TOKEN) else single(it)
        }
        val failed = recognized.filterIsInstance<Recognition.NotRecognized>().firstOrNull()
        val selects = recognized.mapNotNull { (it as? Recognition.Recognized)?.statement as? Statement.Select }
        return when {
            failed != null -> failed
            selects.size != recognized.size -> Recognition.NotRecognized(Unrecognized.UNEXPECTED_TOKEN)
            selects.size > SERIES_LIMIT -> Recognition.NotRecognized(Unrecognized.TOO_MANY_STATEMENTS)
            else -> Recognition.Series(selects)
        }
    }

    private fun splitAtSemicolons(tokens: List<Token>): List<List<Token>> {
        val pieces = arrayListOf<List<Token>>()
        var start = 0
        tokens.forEachIndexed { index, token ->
            if (token == Token.Symbol(';')) {
                pieces += tokens.subList(start, index)
                start = index + 1
            }
        }
        pieces += tokens.subList(start, tokens.size)
        return pieces
    }
}
