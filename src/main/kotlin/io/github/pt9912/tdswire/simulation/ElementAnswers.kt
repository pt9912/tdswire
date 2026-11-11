package io.github.pt9912.tdswire.simulation

/**
 * Die Zeilen der Katalogabfragen für Eindeutigkeiten, Fremdschlüssel, Indizes und Prüfbedingungen einer Tabelle
 * (`SPEC-036`, Form K), in der Reihenfolge des `ORDER BY` der Abfrage; Namen ordnet die Bibliothek ohne Beachtung der
 * Schreibweise (Setzung). Ohne Tabelle sind alle Antworten leer.
 */
internal object ElementAnswers {
    /** Der Wert von `type` eines nicht gruppierten Index (`sys.indexes`). */
    private const val NONCLUSTERED = 2

    fun rows(source: CatalogSource, table: SimTable?, catalog: SimCatalog): List<List<Any?>> {
        if (table == null) return emptyList()
        return when (source) {
            CatalogSource.UNIQUE_KEYS -> uniqueKeys(table)

            CatalogSource.FOREIGN_KEYS -> foreignKeys(table, catalog)

            CatalogSource.INDEXES -> indexes(table)

            CatalogSource.CHECKS -> table.checks.sortedBy { SimNames.fold(it.name) }
                .map { listOf(it.name, it.definition) }

            else -> error("keine Quelle der Elemente: $source")
        }
    }

    /** Der Name einer Spalte, wie die Beschreibung ihn schreibt; ein Name der Elemente ist dort vorhanden. */
    private fun spelled(table: SimTable, name: String): String = table.column(name)?.name ?: name

    private fun uniqueKeys(table: SimTable): List<List<Any?>> = table.uniqueKeys.sortedBy { SimNames.fold(it.name) }
        .flatMap { key -> key.columns.map { listOf(key.name, spelled(table, it)) } }

    private fun foreignKeys(table: SimTable, catalog: SimCatalog): List<List<Any?>> =
        table.foreignKeys.sortedBy { SimNames.fold(it.name) }.flatMap { key ->
            val target = catalog.find(table.schema, key.referencedTable)
            key.columns.indices.map { at ->
                listOf(
                    key.name,
                    spelled(table, key.columns[at]),
                    target?.name ?: key.referencedTable,
                    target?.let { spelled(it, key.referencedColumns[at]) } ?: key.referencedColumns[at],
                    key.onDelete,
                    key.onUpdate,
                    at + 1,
                )
            }
        }

    /** Eindeutigkeiten und Indizes einer Tabelle als Zeilen der Indexabfrage; der Primärschlüssel fehlt dort. */
    private fun indexes(table: SimTable): List<List<Any?>> {
        val all = table.uniqueKeys.map { SimIndex(it.name, it.columns, unique = true) } + table.indexes
        return all.sortedBy { SimNames.fold(it.name) }.flatMap { index ->
            val included = index.included.map { indexRow(index, spelled(table, it), 0, included = true) }
            val keys = index.columns.mapIndexed { at, column ->
                indexRow(index, spelled(table, column), at + 1, included = false)
            }
            included + keys
        }
    }

    private fun indexRow(index: SimIndex, column: String, ordinal: Int, included: Boolean): List<Any?> =
        listOf(index.name, index.unique, false, null, NONCLUSTERED, column, ordinal, false, included)
}
