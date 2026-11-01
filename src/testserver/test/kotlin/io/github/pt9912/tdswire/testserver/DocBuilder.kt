package io.github.pt9912.tdswire.simulation

/**
 * Baut Bäume für den Leser der Tabellenbeschreibung von Hand, ohne YAML-Bibliothek. Jeder Knoten und jeder
 * Schlüssel bekommt eine eigene, fortlaufende Stelle; ein Test vergleicht die Stelle eines Ladefehlers mit der
 * Stelle des Knotens, den er meint, nie mit einer Zahl, die der Leser selbst erzeugt hätte.
 */
class DocBuilder {
    private var counter = 0

    fun at(): SimPosition {
        counter++
        return SimPosition(counter, counter * 2)
    }

    fun s(text: String, plain: Boolean = true): SimScalar = SimScalar(text, plain, at())

    fun l(vararg items: SimNode): SimList = SimList(items.toList(), at())

    fun m(vararg entries: Pair<String, SimNode>): SimMap {
        val position = at()
        return SimMap(entries.map { SimEntry(it.first, at(), it.second) }, position)
    }

    /** Ein Dokument mit den drei Pflichtfeldern und [tables]; [extra] kommt dahinter. */
    fun doc(tables: SimNode?, vararg extra: Pair<String, SimNode>): SimMap {
        val head = listOf<Pair<String, SimNode>>("schema_format" to s("1.0"), "name" to s("n"), "version" to s("1"))
        val tail = if (tables == null) emptyList() else listOf("tables" to tables)
        return m(*(head + tail + extra.toList()).toTypedArray())
    }

    fun col(type: String, vararg extra: Pair<String, SimNode>): SimMap = m("type" to s(type), *extra)

    fun table(columns: SimNode?, key: SimNode?, vararg extra: Pair<String, SimNode>): SimMap {
        val parts = ArrayList<Pair<String, SimNode>>()
        if (columns != null) parts += "columns" to columns
        if (key != null) parts += "primary_key" to key
        parts += extra.toList()
        return m(*parts.toTypedArray())
    }

    fun keys(vararg names: String): SimList = l(*names.map { s(it) }.toTypedArray())

    /** Eine Tabelle `t` mit den Spalten [cols] (Name zu Typ) und dem Schlüssel [key]. */
    fun simple(cols: Map<String, String>, key: List<String>): SimMap =
        doc(m("t" to table(m(*cols.map { it.key to col(it.value) }.toTypedArray()), keys(*key.toTypedArray()))))
}
