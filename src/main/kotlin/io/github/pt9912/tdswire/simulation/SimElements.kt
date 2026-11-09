package io.github.pt9912.tdswire.simulation

/** Die Aktion eines Fremdschlüssels bei `ON DELETE` und `ON UPDATE`, wie der Katalog sie nennt (`SimForeignKey`). */
private val REFERENTIAL_ACTIONS = setOf("NO_ACTION", "CASCADE", "SET_NULL", "SET_DEFAULT")

/** Die Aktion, die gilt, wenn der Text keine nennt. */
internal const val NO_ACTION = "NO_ACTION"

private fun requireColumns(owner: String, columns: List<String>) {
    require(columns.isNotEmpty()) { "$owner nennt keine Spalte" }
    require(columns.none { it.isEmpty() }) { "$owner nennt eine Spalte ohne Namen" }
    require(SimNames.firstDuplicate(columns) == null) { "$owner nennt eine Spalte doppelt" }
}

/**
 * Eine Eindeutigkeitsbedingung (`UNIQUE`) einer Tabelle (`SPEC-035`): [name] ist der Name der Bedingung, [columns]
 * sind ihre Spalten in der Reihenfolge des Schlüssels. Der Katalog nennt sie in der Abfrage der Eindeutigkeiten und
 * zugleich als Index (`is_unique` 1, Messprotokoll M-21). Die Bibliothek prüft keine Eindeutigkeit.
 */
public data class SimUniqueKey(val name: String, val columns: List<String>) {
    init {
        require(name.isNotEmpty()) { "Name der Eindeutigkeit darf nicht leer sein" }
        requireColumns("Eindeutigkeit $name", columns)
    }
}

/**
 * Ein Fremdschlüssel einer Tabelle (`SPEC-035`): [columns] der Tabelle verweisen auf [referencedColumns] der Tabelle
 * [referencedTable] im Schema der Tabelle (die Abfrage des Katalogs nennt kein Schema der Zieltabelle). [onDelete] und
 * [onUpdate] sind `NO_ACTION`, `CASCADE`, `SET_NULL` oder `SET_DEFAULT`, wie der Katalog sie meldet. Die Bibliothek
 * erzwingt keine referentielle Integrität.
 */
public data class SimForeignKey(
    val name: String,
    val columns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>,
    val onDelete: String = NO_ACTION,
    val onUpdate: String = NO_ACTION,
) {
    init {
        require(name.isNotEmpty()) { "Name des Fremdschlüssels darf nicht leer sein" }
        require(referencedTable.isNotEmpty()) { "Zieltabelle des Fremdschlüssels $name darf nicht leer sein" }
        requireColumns("Fremdschlüssel $name", columns)
        requireColumns("Ziel des Fremdschlüssels $name", referencedColumns)
        require(columns.size == referencedColumns.size) {
            "Fremdschlüssel $name: Spalten und Zielspalten ungleich viele"
        }
        require(onDelete in REFERENTIAL_ACTIONS && onUpdate in REFERENTIAL_ACTIONS) {
            "Fremdschlüssel $name: Aktion außerhalb von $REFERENTIAL_ACTIONS"
        }
    }
}

/**
 * Ein Index einer Tabelle ohne Bedingung (`SPEC-035`): [columns] sind die Schlüsselspalten in der Reihenfolge des
 * Index, [included] die eingeschlossenen Spalten (`INCLUDE`); [unique] macht ihn eindeutig. Der Katalog meldet ihn als
 * nicht gruppierten Index (`type` 2) ohne Filter und ohne absteigende Spalte; die Bibliothek nutzt ihn nicht.
 */
public data class SimIndex(
    val name: String,
    val columns: List<String>,
    val unique: Boolean = false,
    val included: List<String> = emptyList(),
) {
    init {
        require(name.isNotEmpty()) { "Name des Index darf nicht leer sein" }
        requireColumns("Index $name", columns)
        require(included.none { it.isEmpty() }) { "Index $name schließt eine Spalte ohne Namen ein" }
        require(SimNames.firstDuplicate(columns + included) == null) {
            "Index $name nennt eine Spalte doppelt (Schlüssel und eingeschlossene Spalten zusammen)"
        }
    }
}

/**
 * Eine Prüfbedingung (`CHECK`) einer Tabelle (`SPEC-035`): [definition] ist der Text, den der Katalog als `definition`
 * meldet (die Form des Servers, etwa `([Menge]>(0))`). Die Bibliothek wertet sie nicht aus und prüft keine Zeile.
 */
public data class SimCheck(val name: String, val definition: String) {
    init {
        require(name.isNotEmpty()) { "Name der Prüfbedingung darf nicht leer sein" }
        require(definition.isNotEmpty()) { "Definition der Prüfbedingung $name darf nicht leer sein" }
    }
}

/** Die Prüfungen von [SimTable] über die Elemente: Namen eindeutig, genannte Spalten vorhanden. */
internal object SimElementChecks {
    fun validate(table: SimTable) {
        val owner = table.name
        val constraintNames = table.uniqueKeys.map { it.name } + table.foreignKeys.map { it.name } +
            table.checks.map { it.name }
        require(SimNames.firstDuplicate(constraintNames) == null) { "Bedingung doppelt in Tabelle $owner" }
        require(SimNames.firstDuplicate(table.indexes.map { it.name } + table.uniqueKeys.map { it.name }) == null) {
            "Index doppelt in Tabelle $owner"
        }
        val named = table.uniqueKeys.flatMap { it.columns } + table.foreignKeys.flatMap { it.columns } +
            table.indexes.flatMap { it.columns + it.included }
        val missing = named.firstOrNull { table.column(it) == null }
        require(missing == null) { "Spalte $missing ist in Tabelle $owner nicht vorhanden" }
    }
}
