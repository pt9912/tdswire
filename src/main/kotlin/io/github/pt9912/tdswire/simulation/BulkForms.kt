package io.github.pt9912.tdswire.simulation

/**
 * Die Formen des Massenladens (`SPEC-036`, Form B): `INSERT BULK <Tabelle> (<Spalte> <Typ> {, …}) [WITH (…)]` und die
 * Probe der Zieltabelle, die der Treiber davor stellt: `SET FMTONLY ON <Auswahl> [SET FMTONLY OFF]`, auch als
 * `[EXEC[UTE]] sp_executesql N'SET FMTONLY ON <Auswahl>'` im SQL-Text.
 *
 * Gelesen werden nur die Namen der Spalten von `INSERT BULK`; Typen, Zusätze wie `COLLATE …` und die Optionen von
 * `WITH (…)` bleiben ungelesen, aber geklammert ausgeglichen (Setzung: die Typen gelten nach dem COLMETADATA der
 * Nachricht 0x07, nicht nach dem Text; die Optionen wirken nicht). Eine Probe mit `SET FMTONLY ON` liefert die
 * Metadaten der Auswahl und nie eine Zeile, auch ohne Konstantenterm (Setzung nach der Wirkung von `SET FMTONLY ON` am
 * Server, hier nicht gemessen).
 */
internal object BulkForms {
    private const val FMTONLY = "FMTONLY"
    private const val FMTONLY_LENGTH = 3

    /** `INSERT BULK …`: die Tabelle und ihre Spaltennamen, oder `null`, wenn die Token keinen solchen Text bilden. */
    fun insertBulk(tokens: List<Token>): Recognition? {
        if (tokens.size < 2 || !tokens[0].isKeyword("INSERT") || !tokens[1].isKeyword("BULK")) return null
        return try {
            val stream = TokenStream(tokens.subList(2, tokens.size))
            val table = Parts(stream).tableRef()
            stream.symbol('(')
            val parts = Parts(stream)
            val columns = parts.list { column(stream, parts) }
            stream.symbol(')')
            if (stream.acceptKeyword("WITH")) skipGroup(stream)
            if (!stream.atEnd()) throw unexpected()
            if (SimNames.firstDuplicate(columns) != null) throw Reject(Unrecognized.DUPLICATE_COLUMN)
            Recognition.Bulk(table, columns)
        } catch (reject: Reject) {
            Recognition.NotRecognized(reject.reason)
        }
    }

    /**
     * Die Probe der Zieltabelle: die Auswahl hinter `SET FMTONLY ON` ohne Zeilen, oder `null`, wenn die Token nicht so
     * beginnen. [read] liest die Token der Auswahl. `sp_executesql N'…'` gilt nur, wenn der Text mit `SET FMTONLY ON`
     * beginnt.
     */
    fun probe(tokens: List<Token>, read: (List<Token>) -> Recognition): Recognition? {
        val inner = executesqlText(tokens)
        val scanned = inner?.let { SqlScanner(it).scan() as? ScanResult.Tokens }
        return when {
            inner != null -> scanned?.tokens?.takeIf { isFmtonly(it, 0, "ON") }?.let { probe(it, read) }
            !isFmtonly(tokens, 0, "ON") -> null
            else -> metadataOnly(read(tokens.subList(FMTONLY_LENGTH, tokens.size).withoutOff()))
        }
    }

    /** Die Auswahl hinter `SET FMTONLY ON` liefert Spalten und nie eine Zeile; jede andere Form ist nicht erkannt. */
    private fun metadataOnly(recognized: Recognition): Recognition {
        val select = (recognized as? Recognition.Recognized)?.statement as? Statement.Select
        return when {
            recognized !is Recognition.Recognized -> recognized
            select == null -> Recognition.NotRecognized(Unrecognized.UNEXPECTED_TOKEN)
            else -> Recognition.Recognized(select.copy(where = select.where + Term.Constant(false)))
        }
    }

    private fun List<Token>.withoutOff(): List<Token> =
        if (isFmtonly(this, size - FMTONLY_LENGTH, "OFF")) subList(0, size - FMTONLY_LENGTH) else this

    private fun isFmtonly(tokens: List<Token>, at: Int, state: String): Boolean =
        at >= 0 && tokens.size >= at + FMTONLY_LENGTH &&
            tokens[at].isKeyword("SET") && tokens[at + 1].isKeyword(FMTONLY) && tokens[at + 2].isKeyword(state)

    /** Der Text von `[EXEC[UTE]] sp_executesql N'…'` oder `null`. */
    private fun executesqlText(tokens: List<Token>): String? {
        val start = if (tokens.firstOrNull()?.let { it.isKeyword("EXEC") || it.isKeyword("EXECUTE") } == true) 1 else 0
        val rest = tokens.subList(start, tokens.size)
        val text = rest.getOrNull(1) as? Token.Text
        return if (rest.size == 2 && rest[0].isKeyword("SP_EXECUTESQL") && text != null) text.value else null
    }

    /** Eine Spalte der Liste: der Name, dann der Typ samt Zusätzen bis zum nächsten Komma oder zur Klammer. */
    private fun column(stream: TokenStream, parts: Parts): String {
        val name = parts.identifier()
        var depth = 0
        while (true) {
            val next = stream.peek() ?: throw Reject(Unrecognized.UNEXPECTED_END)
            if (depth == 0 && (next == Token.Symbol(',') || next == Token.Symbol(')'))) return name
            if (next == Token.Symbol('(')) depth++
            if (next == Token.Symbol(')')) depth--
            stream.next()
        }
    }

    /** Überliest eine geklammerte Gruppe `( … )` samt verschachtelter Gruppen. */
    private fun skipGroup(stream: TokenStream) {
        stream.symbol('(')
        var depth = 1
        while (depth > 0) {
            when (stream.next()) {
                Token.Symbol('(') -> depth++
                Token.Symbol(')') -> depth--
                else -> Unit
            }
        }
    }
}
