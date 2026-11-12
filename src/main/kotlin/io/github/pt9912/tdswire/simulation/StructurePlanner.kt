package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.StatementKind

/**
 * Die Planung der Strukturformen D1 bis D6 (`SPEC-036`) gegen den Katalog: Tabelle und Spalten sind beschrieben oder
 * neu, Namen kommen nicht doppelt vor, ein Fremdschlüssel zeigt auf eine beschriebene Tabelle und ihre Spalten. Das
 * Ergebnis ist die Tabelle nach der Anweisung als Beschreibung ohne Zeilen ([StructurePlan]). Die Fehlercodes
 * `OBJECT_EXISTS`, `COLUMN_EXISTS` und `OBJECT_NOT_FOUND` sind für `CREATE TABLE` und `ALTER TABLE … ADD` einer Spalte
 * gemessen (M-19); für Bedingungen, Indizes und Zieltabellen sind sie Setzung (der Server nutzt dort eigene Nummern,
 * nicht gemessen).
 */
internal object StructurePlanner {
    fun create(statement: Statement.CreateTable, catalog: SimCatalog): PlanResult {
        val schema = statement.table.schema ?: DEFAULT_SCHEMA
        val key = statement.key
        val known = statement.cols.associateBy { SimNames.fold(it.name) }
        val elements = statement.elements
        val own = statement.table.name
        val names = elements.uniqueKeys.map { it.name } + elements.foreignKeys.map { it.name } +
            elements.checks.map { it.name }
        return when {
            catalog.find(schema, statement.table.name) != null ->
                reject(SimCodes.OBJECT_EXISTS, "Tabelle ist schon beschrieben")

            key != null && key.any { SimNames.fold(it) !in known } ->
                reject(SimCodes.UNKNOWN_OBJECT, "Schlüsselspalte nicht beschrieben")

            StructureChecks.columnsOf(elements).any { SimNames.fold(it) !in known } ->
                reject(SimCodes.UNKNOWN_OBJECT, "Spalte nicht beschrieben")

            SimNames.firstDuplicate(names) != null ->
                reject(SimCodes.OBJECT_EXISTS, "Bedingung ist schon beschrieben")

            elements.foreignKeys.any { !StructureChecks.targetKnown(it, catalog, schema, own, known) } ->
                reject(SimCodes.UNKNOWN_OBJECT, "Zieltabelle oder Zielspalte nicht beschrieben")

            else -> {
                val keyed = key.orEmpty().map { SimNames.fold(it) }.toSet()
                val columns = statement.cols.map {
                    if (SimNames.fold(it.name) in keyed) it.copy(nullable = false) else it
                }
                val keyNames = key.orEmpty().map { known.getValue(SimNames.fold(it)).name }
                val table = SimTable(
                    schema = schema,
                    name = own,
                    columns = columns,
                    primaryKey = keyNames,
                    rows = { RowSource { null } },
                    uniqueKeys = elements.uniqueKeys,
                    foreignKeys = elements.foreignKeys,
                    checks = elements.checks,
                )
                PlanResult.Structure(StructurePlan(table, StatementKind.CREATE_TABLE))
            }
        }
    }

    fun addColumns(statement: Statement.AddColumns, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.OBJECT_NOT_FOUND, "Tabelle nicht beschrieben")
        return if (statement.columns.any { table.column(it.name) != null }) {
            reject(SimCodes.COLUMN_EXISTS, "Spalte ist schon beschrieben")
        } else {
            result(table.copy(columns = table.columns + statement.columns), StatementKind.ALTER_TABLE)
        }
    }

    fun addConstraint(statement: Statement.AddConstraint, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.OBJECT_NOT_FOUND, "Tabelle nicht beschrieben")
        val constraint = statement.constraint
        val known = table.columns.associateBy { SimNames.fold(it.name) }
        return when {
            StructureChecks.columnsOf(constraint).any { SimNames.fold(it) !in known } ->
                reject(SimCodes.UNKNOWN_OBJECT, "Spalte nicht beschrieben")

            StructureChecks.exists(table, constraint) ->
                reject(SimCodes.OBJECT_EXISTS, "Bedingung ist schon beschrieben")

            constraint is NewConstraint.Foreign &&
                !StructureChecks.targetKnown(constraint.key, catalog, table.schema, table.name, known) ->
                reject(SimCodes.UNKNOWN_OBJECT, "Zieltabelle oder Zielspalte nicht beschrieben")

            else -> result(added(table, constraint), StatementKind.ALTER_TABLE)
        }
    }

    fun createIndex(statement: Statement.CreateIndex, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.OBJECT_NOT_FOUND, "Tabelle nicht beschrieben")
        val index = statement.index
        return when {
            (index.columns + index.included).any { table.column(it) == null } ->
                reject(SimCodes.UNKNOWN_OBJECT, "Spalte nicht beschrieben")

            StructureChecks.indexNameTaken(table, index.name) ->
                reject(SimCodes.OBJECT_EXISTS, "Index ist schon beschrieben")

            else -> result(table.copy(indexes = table.indexes + index), StatementKind.UNKNOWN)
        }
    }

    /**
     * D5: die Bedingung (Eindeutigkeit, Fremdschlüssel oder Prüfbedingung) mit dem Namen fällt aus der Tabelle. Ohne
     * Treffer ist `IF EXISTS` ein Erfolg ohne Änderung (die Tabelle im Callback unverändert), ohne `IF EXISTS` trägt
     * der Name einer Bedingung einer anderen Tabelle desselben Schemas (Setzung, gemessen nur in `dbo`)
     * `CONSTRAINT_OTHER_TABLE`, jeder andere Name `NOT_A_CONSTRAINT`. Eine unbekannte Tabelle ist auch mit `IF EXISTS`
     * `OBJECT_NOT_FOUND` (4902, gemessen).
     */
    fun dropConstraint(statement: Statement.DropConstraint, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
            ?: return reject(SimCodes.OBJECT_NOT_FOUND, "Tabelle nicht beschrieben")
        val shortened = StructureChecks.without(table, statement.name)
        return when {
            shortened != null -> result(shortened, StatementKind.ALTER_TABLE)

            statement.ifExists -> result(table, StatementKind.ALTER_TABLE)

            StructureChecks.belongsToOther(catalog, table, statement.name) ->
                reject(SimCodes.CONSTRAINT_OTHER_TABLE, "Bedingung gehört zu einer anderen Tabelle")

            else -> reject(SimCodes.NOT_A_CONSTRAINT, "Name ist keine Bedingung der Tabelle")
        }
    }

    /**
     * D6: der Index mit dem Namen fällt aus der Tabelle; der Abschluss ist die Art `UNKNOWN` (die öffentliche
     * Aufzählung der Arten kennt keine für `DROP INDEX`). Der Name einer Eindeutigkeit der Tabelle ist
     * `INDEX_OF_CONSTRAINT` (3723), mit und ohne `IF EXISTS` (gemessen). Sonst ist `IF EXISTS` ohne Treffer ein Erfolg
     * ohne Änderung (die Tabelle im Callback unverändert; der Name eines Index einer anderen Tabelle trifft nicht),
     * auch an einer unbekannten Tabelle (gemessen), dann ohne Callback; ohne `IF EXISTS` ist es `NOT_AN_INDEX`
     * (3701), auch an einer unbekannten Tabelle.
     */
    fun dropIndex(statement: Statement.DropIndex, catalog: SimCatalog): PlanResult {
        val table = catalog.find(statement.table.schema, statement.table.name)
        val shortened = table?.let { StructureChecks.withoutIndex(it, statement.name) }
        return when {
            shortened != null -> result(shortened, StatementKind.UNKNOWN)

            table != null && StructureChecks.constraintIndex(table, statement.name) ->
                reject(SimCodes.INDEX_OF_CONSTRAINT, "Index gehört zu einer Eindeutigkeit")

            !statement.ifExists -> reject(SimCodes.NOT_AN_INDEX, "Name ist kein Index der Tabelle")

            table == null -> PlanResult.Unchanged(StatementKind.UNKNOWN)

            else -> result(table, StatementKind.UNKNOWN)
        }
    }

    private fun reject(code: String, message: String): PlanResult = PlanResult.Rejected(code, message)

    /** Die Tabelle ohne Zeilenquelle und ohne Callbacks: die Beschreibung, die der Callback bekommt. */
    private fun result(table: SimTable, kind: StatementKind): PlanResult = PlanResult.Structure(
        StructurePlan(
            table.copy(
                rows = { RowSource { null } },
                onInsert = null,
                onUpdate = null,
                onDelete = null,
                onInsertBatch = null,
            ),
            kind,
        ),
    )

    private fun added(table: SimTable, constraint: NewConstraint): SimTable = when (constraint) {
        is NewConstraint.Unique -> table.copy(uniqueKeys = table.uniqueKeys + constraint.key)

        is NewConstraint.Foreign -> table.copy(foreignKeys = table.foreignKeys + constraint.key)

        is NewConstraint.Check -> table.copy(checks = table.checks + constraint.check)

        is NewConstraint.Default -> table.copy(
            columns = table.columns.map {
                if (StructureChecks.same(it.name, constraint.column)) {
                    it.copy(defaultDefinition = constraint.definition)
                } else {
                    it
                }
            },
        )
    }
}
