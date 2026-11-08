package io.github.pt9912.tdswire.simulation

/**
 * Form M von `SPEC-036`: `MERGE INTO <Tabelle> AS <T> USING (VALUES (<Wert> {, <Wert>})) AS <Q> (<Spalte> {, <Spalte>})
 * ON <T>.<Spalte> = <Q>.<Spalte> {AND <T>.<Spalte> = <Q>.<Spalte>} [WHEN MATCHED THEN UPDATE SET <T>.<Spalte> =
 * <Q>.<Spalte> {, <T>.<Spalte> = <Q>.<Spalte>}] WHEN NOT MATCHED THEN INSERT (<Spalte> {, <Spalte>}) VALUES
 * (<Q>.<Spalte> {, <Q>.<Spalte>}) OUTPUT $action`, ein abschließendes `;` ist erlaubt. Gemessen ist der Wortlaut von
 * d-migrate 1.12.0 mit mssql-jdbc 13.4.0 (`--on-conflict skip` ohne den Zweig `WHEN MATCHED`, `update` mit ihm, Aliase
 * `tgt` und `src`); Aliase anderen Namens, mehrere Spalten im Vergleich und die Reihenfolge der Zuordnungen sind
 * Setzung. Genau eine `VALUES`-Zeile; jede Quellspalte der Zuordnungen steht in der Spaltenliste der Quelle.
 */
internal class MergeParts(private val stream: TokenStream, private val parts: Parts) {
    private class Aliases(val target: String, val source: String)

    fun merge(): Statement {
        stream.keyword("INTO")
        val table = parts.tableRef()
        stream.keyword("AS")
        val target = parts.identifier()
        stream.keyword("USING")
        stream.symbol('(')
        stream.keyword("VALUES")
        stream.symbol('(')
        val values = parts.list { parts.value() }
        stream.symbol(')')
        stream.symbol(')')
        stream.keyword("AS")
        val source = parts.identifier()
        stream.symbol('(')
        val columns = parts.list { parts.identifier() }
        stream.symbol(')')
        if (columns.size != values.size) reject(Unrecognized.COUNT_MISMATCH)
        if (SimNames.firstDuplicate(columns) != null || SimNames.fold(target) == SimNames.fold(source)) {
            reject(Unrecognized.DUPLICATE_COLUMN)
        }
        val aliases = Aliases(target, source)
        stream.keyword("ON")
        val on = arrayListOf(link(aliases, columns))
        while (stream.acceptKeyword("AND")) on += link(aliases, columns)
        stream.keyword("WHEN")
        val matched = !stream.acceptKeyword("NOT")
        stream.keyword("MATCHED")
        stream.keyword("THEN")
        val update = if (matched) updateBranch(aliases, columns) else null
        if (matched) {
            stream.keyword("WHEN")
            stream.keyword("NOT")
            stream.keyword("MATCHED")
            stream.keyword("THEN")
        }
        val insert = insertBranch(aliases, columns)
        outputAction()
        val targets = listOf(on, update.orEmpty(), insert).map { links -> links.map { it.target } }
        if (targets.any { SimNames.firstDuplicate(it) != null }) reject(Unrecognized.DUPLICATE_COLUMN)
        return Statement.Merge(table, columns, values, on, update, insert)
    }

    /** `OUTPUT $action`: die einzige erkannte `OUTPUT`-Klausel. */
    private fun outputAction() {
        stream.keyword("OUTPUT")
        val action = stream.next()
        if (action !is Token.Word || !action.text.equals("\$action", ignoreCase = true)) throw unexpected()
    }

    private fun reject(reason: Unrecognized): Nothing = throw Reject(reason)

    private fun updateBranch(aliases: Aliases, sourceColumns: List<String>): List<MergeLink> {
        stream.keyword("UPDATE")
        stream.keyword("SET")
        val links = ArrayList<MergeLink>()
        do {
            links += link(aliases, sourceColumns)
        } while (stream.acceptSymbol(','))
        return links
    }

    private fun insertBranch(aliases: Aliases, sourceColumns: List<String>): List<MergeLink> {
        stream.keyword("INSERT")
        stream.symbol('(')
        val targets = parts.list { parts.identifier() }
        stream.symbol(')')
        stream.keyword("VALUES")
        stream.symbol('(')
        val sources = parts.list { sourceColumn(qualified(aliases.source), sourceColumns) }
        stream.symbol(')')
        if (targets.size != sources.size) reject(Unrecognized.COUNT_MISMATCH)
        return targets.zip(sources) { t, s -> MergeLink(t, s) }
    }

    /** `<Alias der Tabelle>.<Spalte> = <Alias der Quelle>.<Spalte>`. */
    private fun link(aliases: Aliases, sourceColumns: List<String>): MergeLink {
        val column = qualified(aliases.target)
        stream.symbol('=')
        return MergeLink(column, sourceColumn(qualified(aliases.source), sourceColumns))
    }

    /** `<Alias>.<Spalte>`: der Alias ist genau [alias] (ohne Beachtung der Schreibweise). */
    private fun qualified(alias: String): String {
        if (SimNames.fold(parts.identifier()) != SimNames.fold(alias)) throw unexpected()
        stream.symbol('.')
        return parts.identifier()
    }

    private fun sourceColumn(name: String, sourceColumns: List<String>): String =
        sourceColumns.firstOrNull { SimNames.fold(it) == SimNames.fold(name) } ?: throw unexpected()
}
