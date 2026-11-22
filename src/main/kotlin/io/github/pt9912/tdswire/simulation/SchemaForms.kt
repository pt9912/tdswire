package io.github.pt9912.tdswire.simulation

/**
 * Form F von `SPEC-036`, der Text von `ExecuteReader(SchemaOnly)` (`ADR-0059` Entscheidung 2):
 * `SET FMTONLY OFF; SET FMTONLY ON; <Auswahl> [SET FMTONLY OFF] [;]`. Beide führenden `SET` mit ihrem `;` sind Pflicht,
 * das schließende `SET` ist optional und steht ohne `;` hinter der Auswahl. Die Auswahl ist genau die Form S1 aus einer
 * Tabelle (Spaltenliste oder `*`, Bedingung wie in S1); jede andere Anweisung dahinter, `SELECT <Ganzzahl>`, zwei
 * Auswahlen und eine Folge mit einer weiteren `SET`-Anweisung sind nicht erkannt. Gemessen ist die Folge mit
 * Spaltenlisten, `*` und `WHERE Id = @i` (Messprotokoll W-26 bis W-28); die übrigen Bedingungen von S1 sind Setzung.
 * Die Probe der Zieltabelle ([BulkForms.probe]) beginnt mit `SET FMTONLY ON` und überschneidet sich nicht mit Form F.
 */
internal object SchemaForms {
    private const val SET_LENGTH = 3

    /** Die zwei führenden `SET` mit ihrem `;`. */
    private const val PREFIX_LENGTH = 2 * (SET_LENGTH + 1)

    /**
     * Die Form F oder `null`, wenn die Token nicht mit `SET FMTONLY OFF ; SET FMTONLY ON ;` beginnen. [single] liest
     * die Token der Auswahl als genau eine Anweisung.
     */
    fun recognize(tokens: List<Token>, single: (List<Token>) -> Recognition): Recognition? {
        if (!hasPrefix(tokens)) return null
        val rest = tokens.subList(PREFIX_LENGTH, tokens.size)
        val closed = rest.size > SET_LENGTH && isSet(rest, rest.size - SET_LENGTH, "OFF")
        val inner = if (closed) rest.subList(0, rest.size - SET_LENGTH) else rest
        return when (val read = single(inner)) {
            is Recognition.Recognized -> (read.statement as? Statement.Select)
                ?.let { Recognition.SchemaOnly(BulkForms.withoutRows(it), closed) }
                ?: Recognition.NotRecognized(Unrecognized.UNEXPECTED_TOKEN)

            is Recognition.NotRecognized -> read

            else -> Recognition.NotRecognized(Unrecognized.UNEXPECTED_TOKEN)
        }
    }

    private fun hasPrefix(tokens: List<Token>): Boolean =
        isSet(tokens, 0, "OFF") && tokens.getOrNull(SET_LENGTH) == Token.Symbol(';') &&
            isSet(tokens, SET_LENGTH + 1, "ON") && tokens.getOrNull(PREFIX_LENGTH - 1) == Token.Symbol(';')

    private fun isSet(tokens: List<Token>, at: Int, state: String): Boolean =
        at >= 0 && tokens.size >= at + SET_LENGTH &&
            tokens[at].isKeyword("SET") && tokens[at + 1].isKeyword("FMTONLY") && tokens[at + 2].isKeyword(state)
}
