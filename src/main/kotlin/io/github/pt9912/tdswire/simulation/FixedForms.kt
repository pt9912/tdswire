package io.github.pt9912.tdswire.simulation

/**
 * Form C von `SPEC-036`: die Texte, die keine Tabelle nennen. Erkannt sind `SELECT 1` und eine oder mehrere
 * `SET`-Anweisungen, durch `;` getrennt, jeweils eine Option aus [ON_OFF_OPTIONS] mit `ON` oder `OFF` oder
 * `LOCK_TIMEOUT` mit einer Ganzzahl. Alles andere ist nicht Form C. Die Liste ist eine Setzung nach der Aufzeichnung
 * mit d-migrate 1.12.0 (mssql-jdbc 13.4.0), nicht nach dem Umfang von `SET` am Server.
 */
internal object FixedForms {
    private val ON_OFF_OPTIONS = setOf(
        "ANSI_NULLS",
        "ANSI_PADDING",
        "ANSI_WARNINGS",
        "ARITHABORT",
        "CONCAT_NULL_YIELDS_NULL",
        "QUOTED_IDENTIFIER",
        "NUMERIC_ROUNDABORT",
        "IMPLICIT_TRANSACTIONS",
    )
    private val ON_OFF = setOf("ON", "OFF")
    private const val LOCK_TIMEOUT = "LOCK_TIMEOUT"
    private const val SET_LENGTH = 3
    private val INTEGER = Regex("-?[0-9]+")

    /** Die Form des Textes oder `null`, wenn er keine Form C ist; [tokens] ohne das abschließende `;`. */
    fun recognize(tokens: List<Token>): FixedForm? = if (isProbe(tokens)) FixedForm.Probe else settings(tokens)

    private fun isProbe(tokens: List<Token>): Boolean =
        tokens.size == 2 && tokens[0].isKeyword("SELECT") && tokens[1] == Token.Number("1")

    private fun settings(tokens: List<Token>): FixedForm? {
        val options = ArrayList<SetOption>()
        var rest = tokens
        while (true) {
            val end = rest.indexOf(Token.Symbol(';'))
            val segment = if (end < 0) rest else rest.subList(0, end)
            options += option(segment) ?: return null
            if (end < 0) return FixedForm.Settings(options)
            rest = rest.subList(end + 1, rest.size)
        }
    }

    private fun option(segment: List<Token>): SetOption? {
        val name = segment.getOrNull(1)
        val argument = segment.getOrNull(2)
        if (segment.size != SET_LENGTH || !segment[0].isKeyword("SET") || name !is Token.Word) return null
        val option = asciiUpper(name.text)
        return when {
            option in ON_OFF_OPTIONS && argument is Token.Word && asciiUpper(argument.text) in ON_OFF ->
                SetOption(option, asciiUpper(argument.text))

            option == LOCK_TIMEOUT && argument is Token.Number && INTEGER.matches(argument.text) ->
                SetOption(option, argument.text)

            else -> null
        }
    }
}
