package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType

/** Schema, das gilt, wenn eine Anweisung oder die Datei keines nennt (`TDS-FA-11.a` Schritt 3). */
const val DEFAULT_SCHEMA = "dbo"

/**
 * Eine beschriebene Tabelle (`SPEC-035`). Die Validierung gilt für die Kotlin-Schnittstelle und den Leser
 * der Datei gleichermaßen: Spaltennamen sind ohne Beachtung der Schreibweise eindeutig, es gibt mindestens
 * eine Spalte und einen Schlüssel, jeder Schlüsseleintrag nennt eine vorhandene, nicht nullbare Spalte
 * (Setzung, nicht am Server gemessen). Der Schlüssel wirkt weder auf Resultset noch auf Callbacks.
 *
 * `columns` und `primaryKey` werden nicht kopiert: ein Konstruktor mit acht Parametern erlaubt die Regel
 * `LongParameterList` nur an einer `data class`, und deren Konstruktor kann die Listen nicht ersetzen. Der Aufrufer
 * ändert sie nach der Übergabe nicht; [column] liest einen Index, der bei der Übergabe gebaut wurde.
 */
data class SimTable(
    val schema: String = DEFAULT_SCHEMA,
    val name: String,
    val columns: List<SimColumn>,
    val primaryKey: List<String>,
    val rows: suspend (SimQuery) -> RowSource,
    val onInsert: (suspend (values: Map<String, Any?>) -> Long)? = null,
    val onUpdate: (suspend (set: Map<String, Any?>, where: RowFilter?) -> Long)? = null,
    val onDelete: (suspend (where: RowFilter?) -> Long)? = null,
) {
    private val byName: Map<String, SimColumn>

    init {
        require(schema.isNotEmpty()) { "schema darf nicht leer sein" }
        require(name.isNotEmpty()) { "name darf nicht leer sein" }
        require(columns.isNotEmpty()) { "Tabelle $name braucht mindestens eine Spalte" }
        val duplicate = SimNames.firstDuplicate(columns.map { it.name })
        require(duplicate == null) { "Spalte doppelt in Tabelle $name (ohne Beachtung der Schreibweise)" }
        byName = columns.associateBy { SimNames.fold(it.name) }
        require(primaryKey.isNotEmpty()) { "Tabelle $name braucht einen Schlüssel" }
        require(SimNames.firstDuplicate(primaryKey) == null) { "Schlüssel von Tabelle $name nennt eine Spalte doppelt" }
        for (entry in primaryKey) {
            val column = byName[SimNames.fold(entry)]
            requireNotNull(column) { "Schlüsselspalte $entry ist in Tabelle $name nicht vorhanden" }
            require(!column.nullable) { "Schlüsselspalte $entry von Tabelle $name darf nicht nullbar sein" }
        }
    }

    /** Die Spalte namens [columnName] ohne Beachtung der Schreibweise oder `null`. */
    fun column(columnName: String): SimColumn? = byName[SimNames.fold(columnName)]
}

/**
 * Eine Spalte; der Typ stammt aus dem Mindestkatalog von [SqlType], [nullable] sagt, ob NULL vorkommen darf.
 * Eine Dezimalzahl hat höchstens 38 Stellen (`SPEC-003`); die Grenze gilt für Schnittstelle und Datei.
 */
data class SimColumn(val name: String, val type: SqlType, val nullable: Boolean = true) {
    init {
        require(name.isNotEmpty()) { "Spaltenname darf nicht leer sein" }
        require(type !is SqlType.Decimal || type.precision <= SimColumnTypes.MAX_PRECISION) {
            "Spalte $name: Precision über ${SimColumnTypes.MAX_PRECISION}"
        }
    }
}

/** Was das Backend der Zeilenquelle als Hinweis mitgibt: die Spalten der Zeilen und ein Vorfilter. */
data class SimQuery(val columns: List<SimColumn>, val filter: RowFilter?)

/** Die Terme einer Bedingung; alle gelten gemeinsam (UND). */
data class RowFilter(val terms: List<FilterTerm>)

/** Ein Term einer Bedingung (`SPEC-035`): `=` und `IN` als [In], `IS [NOT] NULL` als [IsNull]. */
sealed interface FilterTerm {
    /** Die Spalte [column] steht in [values]; `=` ist die Liste mit einem Wert. */
    data class In(val column: String, val values: List<Any?>) : FilterTerm

    /** Die Spalte [column] ist NULL, mit [negated] ist sie es nicht. */
    data class IsNull(val column: String, val negated: Boolean) : FilterTerm
}

/** Die Zeilen einer Tabelle, eine nach der anderen (`SPEC-035`). */
fun interface RowSource {
    /** Die Werte der nächsten Zeile in der Reihenfolge von [SimQuery.columns] oder `null` am Ende. */
    suspend fun next(): List<Any?>?

    /** Gibt die Ressourcen frei; wird genau einmal gerufen. */
    fun close() {}
}

/**
 * Der Satz beschriebener Tabellen. Tabellen sind über Schema und Name ohne Beachtung der Schreibweise
 * eindeutig (Setzung, nicht am Server gemessen).
 */
class SimCatalog(tables: List<SimTable>) {
    /** Die Tabellen in der übergebenen Reihenfolge; eine Kopie der übergebenen Liste. */
    val tables: List<SimTable> = tables.toList()

    private val byKey: Map<String, SimTable>

    init {
        val map = LinkedHashMap<String, SimTable>(tables.size)
        for (table in tables) {
            val duplicate = map.put(key(table.schema, table.name), table)
            require(duplicate == null) { "Tabelle doppelt (ohne Beachtung der Schreibweise): ${table.name}" }
        }
        byKey = map
    }

    /** Die Tabelle [name] im Schema [schema] (ohne Angabe [DEFAULT_SCHEMA]) oder `null`. */
    fun find(schema: String?, name: String): SimTable? = byKey[key(schema ?: DEFAULT_SCHEMA, name)]

    private fun key(schema: String, name: String): String = SimNames.fold(schema) + "\u0000" + SimNames.fold(name)
}
