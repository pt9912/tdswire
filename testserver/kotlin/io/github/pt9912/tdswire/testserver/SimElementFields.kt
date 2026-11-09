package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.simulation.SimForeignKey
import io.github.pt9912.tdswire.testserver.SimTreeAccess.asList
import io.github.pt9912.tdswire.testserver.SimTreeAccess.asMap
import io.github.pt9912.tdswire.testserver.SimTreeAccess.child
import io.github.pt9912.tdswire.testserver.SimTreeAccess.fail
import io.github.pt9912.tdswire.testserver.SimTreeAccess.fields
import io.github.pt9912.tdswire.testserver.SimTreeAccess.required

private val REFERENCE_COLUMNS_READ = setOf("table", "columns", "on_delete", "on_update")
private val ACTIONS = mapOf(
    "cascade" to "CASCADE",
    "set_null" to "SET_NULL",
    "set_default" to "SET_DEFAULT",
    "no_action" to "NO_ACTION",
    "restrict" to "NO_ACTION",
)

/** Die Felder, die [SimElementReader] aus dem Baum liest: Spaltenlisten, Aktionen, Ziel eines Fremdschlüssels. */
internal object SimElementFields {
    fun foreignKey(
        name: String,
        columns: List<String>,
        f: Map<String, SimEntry>,
        map: SimMap,
        path: String,
    ): PendingForeignKey {
        val refPath = child(path, "references")
        val refMap = asMap(required(f, "references", map, path).value, refPath)
        val r = fields(refMap, refPath, REFERENCE_COLUMNS_READ, emptySet())
        val target = SimTreeAccess.text(required(r, "table", refMap, refPath).value, child(refPath, "table"))
        val targetPath = child(refPath, "columns")
        val targetColumns = columnList(required(r, "columns", refMap, refPath).value, targetPath)
        if (targetColumns.size != columns.size) {
            fail(targetPath, refMap.position, "Zielspalten ungleich viele wie Spalten")
        }
        val key = SimForeignKey(
            name = name,
            columns = columns,
            referencedTable = target,
            referencedColumns = targetColumns,
            onDelete = action(r["on_delete"], refPath, "on_delete"),
            onUpdate = action(r["on_update"], refPath, "on_update"),
        )
        return PendingForeignKey(key, path, map.position)
    }

    fun expression(f: Map<String, SimEntry>, map: SimMap, path: String): String =
        SimTreeAccess.text(required(f, "expression", map, path).value, child(path, "expression"))

    fun namesOf(f: Map<String, SimEntry>, map: SimMap, path: String, columns: Set<String>): List<String> =
        columnNames(required(f, "columns", map, path).value, child(path, "columns"), columns)

    fun action(entry: SimEntry?, path: String, key: String): String {
        if (entry == null) return "NO_ACTION"
        val here = child(path, key)
        return ACTIONS[SimTreeAccess.text(entry.value, here)] ?: fail(here, entry.value.position, "Aktion unbekannt")
    }

    /** Die Spaltennamen einer Liste; jeder steht in [columns] (die Spalten der Tabelle, gefaltet). */
    fun columnNames(node: SimNode, path: String, columns: Set<String>): List<String> {
        val names = columnList(node, path)
        for ((at, name) in names.withIndex()) {
            if (NameFold.fold(name) !in columns) {
                fail(SimTreeAccess.item(path, at), asList(node, path).items[at].position, "Spalte nicht vorhanden")
            }
        }
        return names
    }

    fun columnList(node: SimNode, path: String): List<String> {
        val list = asList(node, path)
        if (list.items.isEmpty()) fail(path, list.position, "mindestens eine Spalte erforderlich")
        val names = list.items.mapIndexed { at, item -> SimTreeAccess.text(item, SimTreeAccess.item(path, at)) }
        NameFold.firstDuplicate(names)?.let {
            fail(SimTreeAccess.item(path, it), list.items[it].position, "Spalte doppelt")
        }
        return names
    }
}
