package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.testserver.SimTreeAccess.asList
import io.github.pt9912.tdswire.testserver.SimTreeAccess.asMap
import io.github.pt9912.tdswire.testserver.SimTreeAccess.child
import io.github.pt9912.tdswire.testserver.SimTreeAccess.fail
import io.github.pt9912.tdswire.testserver.SimTreeAccess.fields
import io.github.pt9912.tdswire.testserver.SimTreeAccess.required

private val DOCUMENT_READ = setOf("schema_format", "name", "version", "tables")
private val DOCUMENT_IGNORED = setOf(
    "description", "encoding", "locale", "custom_types", "procedures", "functions", "views", "triggers", "sequences",
)
private val TABLE_READ = setOf("columns", "primary_key", "indices", "constraints")
private val TABLE_IGNORED = setOf("description", "partitioning", "metadata")
private val COLUMN_READ = setOf(
    "type", "required", "max_length", "precision", "scale", "timezone", "ordinal", "unique", "default", "references",
)
private val COLUMN_IGNORED = setOf(
    "auto_increment", "generation",
    "length", "float_precision", "values", "ref_type", "element_type", "geometry_type", "srid",
)

/** Längste Textlänge des Katalogs der Bibliothek (`SPEC-003`); ein größeres `max_length` gilt als dieser Wert. */
private const val TEXT_LENGTH_LIMIT = 4000

/** Eine Spalte, wie die Datei sie beschreibt, vor der Nullbarkeit durch den Schlüssel. */
private class RawColumn(
    val name: String,
    val type: SqlType,
    val notNull: Boolean,
    val ordinal: Pair<Int, SimPosition>?,
    val maxLength: Int,
    val elements: ColumnElement,
)

/** Standardwert, Eindeutigkeit und Fremdschlüssel, die die Datei an einer Spalte nennt. */
private class ColumnElement(val defaultDefinition: String?, val unique: Boolean, val reference: PendingForeignKey?)

/** Eine gelesene Tabelle mit den Fremdschlüsseln, deren Ziel erst nach allen Tabellen geprüft wird. */
private class ReadTable(val table: SimTable, val pending: List<PendingForeignKey>)

/**
 * Der Leser der Tabellenbeschreibung (`SPEC-035` „Beschreibung als Datei“): macht aus einem [SimNode]-Baum
 * die Tabellen des neutralen Schema-Modells von d-migrate (`schema_format` `1.0`, Belegstand in `SPEC-035`).
 * Er kennt keine YAML-Bibliothek. Gelesen wird die Teilmenge der Spezifikation, die Ignorierliste wird
 * übergangen, jedes andere Feld ist ein Ladefehler; ein Fehler bricht ab, ein Teilergebnis gibt es nicht.
 * Tabellen aus der Datei tragen eine leere Zeilenquelle und keine Callbacks.
 *
 * Festlegung ohne Messung: `tables` als leere Abbildung ist erlaubt (das Schema verlangt kein
 * Mindestmaß); Namen von Tabellen und Spalten dürfen nicht leer sein; `ordinal` darf nicht doppelt
 * vorkommen; `scale` und `precision` bei `decimal` sind Pflicht (ADR-0012 Offen 7).
 */
object SimDescriptionReader {
    /** Liest [root] oder wirft eine [SimLoadException]. */
    fun read(root: SimNode): List<SimTable> {
        val path = SimTreeAccess.ROOT
        val map = asMap(root, path)
        val doc = fields(map, path, DOCUMENT_READ, DOCUMENT_IGNORED)
        val format = required(doc, "schema_format", map, path)
        if (SimTreeAccess.text(format.value, child(path, "schema_format")) != "1.0") {
            fail(child(path, "schema_format"), format.value.position, "schema_format muss 1.0 sein")
        }
        SimTreeAccess.text(required(doc, "name", map, path).value, child(path, "name"))
        SimTreeAccess.text(required(doc, "version", map, path).value, child(path, "version"))
        val tablesPath = child(path, "tables")
        val tables = asMap(required(doc, "tables", map, path).value, tablesPath)
        checkNames(tables.entries, tablesPath)
        val read = tables.entries.map { readTable(it, tablesPath) }
        val known = read.associate { item ->
            NameFold.fold(item.table.name) to item.table.columns.map { NameFold.fold(it.name) }.toSet()
        }
        SimElementReader.checkTargets(read.flatMap { it.pending }, known)
        return read.map { it.table }
    }

    private fun checkNames(entries: List<SimEntry>, path: String) {
        entries.firstOrNull { it.key.isEmpty() }?.let { fail(child(path, it.key), it.keyPosition, "Name leer") }
        NameFold.firstDuplicate(entries.map { it.key })?.let {
            val entry = entries[it]
            fail(child(path, entry.key), entry.keyPosition, "Name doppelt (ohne Beachtung der Schreibweise)")
        }
    }

    private fun readTable(entry: SimEntry, tablesPath: String): ReadTable {
        val path = child(tablesPath, entry.key)
        val map = asMap(entry.value, path)
        val f = fields(map, path, TABLE_READ, TABLE_IGNORED)
        val columnsEntry = required(f, "columns", map, path)
        val columnsPath = child(path, "columns")
        val columnsMap = asMap(columnsEntry.value, columnsPath)
        if (columnsMap.entries.isEmpty()) fail(columnsPath, columnsMap.position, "mindestens eine Spalte erforderlich")
        val raw = readColumns(columnsMap, columnsPath, entry.key)
        val key = f["primary_key"]
            ?.let { readKey(asList(it.value, child(path, "primary_key")), child(path, "primary_key"), raw) }
            .orEmpty()
        val keySet = key.map { NameFold.fold(it) }.toSet()
        val columns = raw.map {
            val nullable = !(it.notNull || NameFold.fold(it.name) in keySet)
            SimColumn(it.name, it.type, nullable, it.maxLength, it.elements.defaultDefinition)
        }
        val elements = readElements(entry.key, f, path, raw)
        val table = runCatching {
            SimTable(
                name = entry.key,
                columns = columns,
                primaryKey = key,
                rows = { RowSource { null } },
                uniqueKeys = elements.uniqueKeys,
                foreignKeys = elements.foreignKeys.map { it.key },
                indexes = elements.indexes,
                checks = elements.checks,
            )
        }.getOrElse { fail(path, map.position, "Name einer Bedingung oder eines Index doppelt") }
        return ReadTable(table, elements.foreignKeys)
    }

    /** Eindeutigkeiten, Fremdschlüssel, Indizes und Prüfbedingungen: die der Spalten zuerst, dann die der Tabelle. */
    private fun readElements(
        table: String,
        f: Map<String, SimEntry>,
        path: String,
        raw: List<RawColumn>,
    ): TableElementsRead {
        val into = TableElementsRead()
        for (column in raw) {
            if (column.elements.unique) into.uniqueKeys += SimElementReader.columnUnique(table, column.name)
            column.elements.reference?.let { into.foreignKeys += it }
        }
        val names = raw.map { NameFold.fold(it.name) }.toSet()
        f["indices"]?.let { SimElementReader.indexes(it.value, child(path, "indices"), names, into) }
        f["constraints"]?.let { SimElementReader.constraints(it.value, child(path, "constraints"), names, into) }
        return into
    }

    private fun readColumns(map: SimMap, path: String, table: String): List<RawColumn> {
        checkNames(map.entries, path)
        val raw = map.entries.map { readColumn(it, path, table) }
        val withOrdinal = raw.count { it.ordinal != null }
        return when (withOrdinal) {
            0 -> raw
            raw.size -> sortByOrdinal(raw, map, path)
            else -> fail(path, map.position, "ordinal bei allen Spalten oder bei keiner")
        }
    }

    private fun sortByOrdinal(raw: List<RawColumn>, map: SimMap, path: String): List<RawColumn> {
        val seen = HashSet<Int>(raw.size)
        for ((index, column) in raw.withIndex()) {
            val ordinal = column.ordinal ?: continue
            if (!seen.add(ordinal.first)) {
                fail(child(child(path, map.entries[index].key), "ordinal"), ordinal.second, "ordinal doppelt")
            }
        }
        return raw.sortedBy { it.ordinal?.first }
    }

    private fun readColumn(entry: SimEntry, columnsPath: String, tableName: String): RawColumn {
        val path = child(columnsPath, entry.key)
        val map = asMap(entry.value, path)
        val f = fields(map, path, COLUMN_READ, COLUMN_IGNORED)
        val typeEntry = required(f, "type", map, path)
        val typeName = SimTreeAccess.text(typeEntry.value, child(path, "type"))
        val notNull = f["required"]?.let { SimTreeAccess.boolean(it.value, child(path, "required")) } ?: false
        val maxLength = f["max_length"]?.let { SimTreeAccess.integer(it.value, child(path, "max_length"), 1) }
        val precision = number(f, "precision", path, 1)
        val scale = number(f, "scale", path, 0)
        val timezone = f["timezone"]?.let { SimTreeAccess.boolean(it.value, child(path, "timezone")) } ?: false
        val ordinal = number(f, "ordinal", path, 1)
        val default = f["default"]?.let { SimElementReader.defaultDefinition(it.value, child(path, "default")) }
        val unique = f["unique"]?.let { SimTreeAccess.boolean(it.value, child(path, "unique")) } ?: false
        val reference = f["references"]?.let {
            SimElementReader.columnReference(tableName, entry.key, it.value, child(path, "references"))
        }
        val fieldsOfType = ColumnTypeFields(typeName, typeEntry.value.position, precision, scale, timezone)
        val type = SimColumnTypes.map(fieldsOfType, path, map.position)
        val length = minOf(maxLength ?: TEXT_LENGTH_LIMIT, TEXT_LENGTH_LIMIT)
        val notNullType = notNull || SimColumnTypes.isIdentifier(typeName)
        return RawColumn(entry.key, type, notNullType, ordinal, length, ColumnElement(default, unique, reference))
    }

    private fun number(f: Map<String, SimEntry>, key: String, path: String, min: Int): Pair<Int, SimPosition>? =
        f[key]?.let { SimTreeAccess.integer(it.value, child(path, key), min) to it.value.position }

    private fun readKey(list: SimList, path: String, columns: List<RawColumn>): List<String> {
        val byName = columns.associateBy { NameFold.fold(it.name) }
        val resolved = ArrayList<String>(list.items.size)
        for ((index, node) in list.items.withIndex()) {
            val here = SimTreeAccess.item(path, index)
            val name = SimTreeAccess.text(node, here)
            val column = byName[NameFold.fold(name)] ?: fail(here, node.position, "Schlüsselspalte nicht vorhanden")
            if (column.name in resolved) fail(here, node.position, "Schlüsselspalte doppelt")
            resolved += column.name
        }
        return resolved
    }
}
