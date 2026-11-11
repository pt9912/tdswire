package io.github.pt9912.tdswire.simulation

/**
 * Die Prüfungen der Planung von Strukturänderungen gegen den Katalog ([StructurePlanner]): Namen von Bedingungen und
 * Indizes sind in der Tabelle vergeben, die Zieltabelle eines Fremdschlüssels ist beschrieben, eine Bedingung wird
 * entfernt. Namen gelten ohne
 * Beachtung der Schreibweise: gemessen für den Namen einer Prüfbedingung in `DROP CONSTRAINT` (Voreinstellung der
 * Kollation der Instanz, nicht eigens erhoben), für die übrigen Prüfungen Setzung.
 */
internal object StructureChecks {
    fun same(left: String, right: String): Boolean = SimNames.fold(left) == SimNames.fold(right)

    fun indexNameTaken(table: SimTable, name: String): Boolean =
        (table.indexes.map { it.name } + table.uniqueKeys.map { it.name }).any { same(it, name) }

    private fun constraintNameTaken(table: SimTable, name: String): Boolean =
        (table.uniqueKeys.map { it.name } + table.foreignKeys.map { it.name } + table.checks.map { it.name })
            .any { same(it, name) }

    /** Der Name der Bedingung ist in der Tabelle vergeben, oder die Spalte hat schon einen Standardwert. */
    fun exists(table: SimTable, constraint: NewConstraint): Boolean = when (constraint) {
        is NewConstraint.Unique ->
            constraintNameTaken(table, constraint.key.name) || indexNameTaken(table, constraint.key.name)

        is NewConstraint.Foreign -> constraintNameTaken(table, constraint.key.name)

        is NewConstraint.Check -> constraintNameTaken(table, constraint.check.name)

        is NewConstraint.Default -> table.column(constraint.column)?.defaultDefinition != null
    }

    /** Die Tabelle ohne die Bedingung [name] (Eindeutigkeit, Fremdschlüssel, Prüfbedingung) oder `null` ohne sie. */
    fun without(table: SimTable, name: String): SimTable? {
        val shortened = table.copy(
            uniqueKeys = table.uniqueKeys.filterNot { same(it.name, name) },
            foreignKeys = table.foreignKeys.filterNot { same(it.name, name) },
            checks = table.checks.filterNot { same(it.name, name) },
        )
        return shortened.takeIf { constraintNameTaken(table, name) }
    }

    /** Eine andere Tabelle desselben Schemas (Setzung, gemessen nur in `dbo`) führt eine Bedingung namens [name]. */
    fun belongsToOther(catalog: SimCatalog, table: SimTable, name: String): Boolean = catalog.tables.any {
        same(it.schema, table.schema) && !same(it.name, table.name) && constraintNameTaken(it, name)
    }

    fun columnsOf(elements: TableElements): List<String> =
        elements.uniqueKeys.flatMap { it.columns } + elements.foreignKeys.flatMap { it.columns }

    fun columnsOf(constraint: NewConstraint): List<String> = when (constraint) {
        is NewConstraint.Unique -> constraint.key.columns
        is NewConstraint.Foreign -> constraint.key.columns
        is NewConstraint.Check -> emptyList()
        is NewConstraint.Default -> listOf(constraint.column)
    }

    /** Die Zieltabelle (auch [own] selbst, mit den Spalten [known]) ist beschrieben und hat alle Zielspalten. */
    fun targetKnown(
        key: SimForeignKey,
        catalog: SimCatalog,
        schema: String,
        own: String,
        known: Map<String, SimColumn>,
    ): Boolean {
        val target = catalog.find(schema, key.referencedTable)
        return when {
            same(key.referencedTable, own) -> key.referencedColumns.all { SimNames.fold(it) in known }
            target == null -> false
            else -> key.referencedColumns.all { target.column(it) != null }
        }
    }
}
