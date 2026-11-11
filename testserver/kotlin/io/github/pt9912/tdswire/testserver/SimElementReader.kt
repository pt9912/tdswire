package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.simulation.SimCheck
import io.github.pt9912.tdswire.simulation.SimForeignKey
import io.github.pt9912.tdswire.simulation.SimIndex
import io.github.pt9912.tdswire.simulation.SimUniqueKey
import io.github.pt9912.tdswire.testserver.SimTreeAccess.asList
import io.github.pt9912.tdswire.testserver.SimTreeAccess.asMap
import io.github.pt9912.tdswire.testserver.SimTreeAccess.child
import io.github.pt9912.tdswire.testserver.SimTreeAccess.fail
import io.github.pt9912.tdswire.testserver.SimTreeAccess.fields
import io.github.pt9912.tdswire.testserver.SimTreeAccess.required

private val REFERENCE_COLUMN_READ = setOf("table", "column", "on_delete", "on_update")
private val INDEX_READ = setOf("name", "columns", "unique")
private val INDEX_IGNORED = setOf("type")
private val CONSTRAINT_READ = setOf("name", "type", "columns", "expression", "references")
private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")

/** Ein Fremdschlüssel mit der Stelle der Datei; die Zieltabelle prüft der Leser nach allen Tabellen. */
internal class PendingForeignKey(val key: SimForeignKey, val path: String, val position: SimPosition)

/** Eindeutigkeiten, Fremdschlüssel, Indizes und Prüfbedingungen einer Tabelle, wie die Datei sie nennt. */
internal class TableElementsRead {
    val uniqueKeys = ArrayList<SimUniqueKey>()
    val foreignKeys = ArrayList<PendingForeignKey>()
    val indexes = ArrayList<SimIndex>()
    val checks = ArrayList<SimCheck>()
}

/**
 * Der Teil des Lesers der Tabellenbeschreibung (`SPEC-035`) für Standardwerte, Eindeutigkeiten, Fremdschlüssel, Indizes
 * und Prüfbedingungen des neutralen Schema-Modells von d-migrate. Die Namen, die d-migrate beim Anlegen vergibt, gelten
 * auch hier: `uq_<Tabelle>_<Spalte>` für `unique: true` ohne `unique_constraint`, `fk_<Tabelle>_<Spalte>` für
 * `references` an einer Spalte.
 * Ein Standardwert ist ein Wahrheitswert (`1` oder `0`), eine Zahl oder ein Text; eine Abbildung (Funktionsaufruf)
 * weist die Datei ab. Der Text einer Prüfbedingung steht im Katalog so, wie die Datei ihn schreibt (die Form des
 * Servers entsteht nur bei einer Strukturänderung über die Bibliothek). Die Aktion `restrict` gilt als `NO_ACTION`, wie
 * d-migrate sie für SQL Server rendert.
 */
internal object SimElementReader {
    /** Der Standardwert einer Spalte in der Form des Katalogs (`((1))`, `((1.5))`, `(N'neu')`). */
    fun defaultDefinition(node: SimNode, path: String): String {
        val scalar = node as? SimScalar ?: fail(path, node.position, "Skalar erwartet")
        return when {
            scalar.plain && (scalar.text == "true" || scalar.text == "false") ->
                "((${if (scalar.text == "true") 1 else 0}))"

            scalar.plain && NUMBER.matches(scalar.text) -> "((${scalar.text}))"

            else -> "(N'${scalar.text.replace("'", "''")}')"
        }
    }

    /** `unique: true` an einer Spalte; [name] ist `unique_constraint`, ohne ihn gilt der Name von d-migrate. */
    fun columnUnique(table: String, column: String, name: String?): SimUniqueKey =
        SimUniqueKey(name ?: "uq_${table}_$column", listOf(column))

    /** `references` an einer Spalte. */
    fun columnReference(table: String, column: String, node: SimNode, path: String): PendingForeignKey {
        val map = asMap(node, path)
        val f = fields(map, path, REFERENCE_COLUMN_READ, emptySet())
        val target = SimTreeAccess.text(required(f, "table", map, path).value, child(path, "table"))
        val targetColumn = SimTreeAccess.text(required(f, "column", map, path).value, child(path, "column"))
        val key = SimForeignKey(
            name = "fk_${table}_$column",
            columns = listOf(column),
            referencedTable = target,
            referencedColumns = listOf(targetColumn),
            onDelete = SimElementFields.action(f["on_delete"], path, "on_delete"),
            onUpdate = SimElementFields.action(f["on_update"], path, "on_update"),
        )
        return PendingForeignKey(key, path, node.position)
    }

    /** `indices` einer Tabelle. */
    fun indexes(node: SimNode, path: String, columns: Set<String>, into: TableElementsRead) {
        val list = asList(node, path)
        for ((at, item) in list.items.withIndex()) {
            val here = SimTreeAccess.item(path, at)
            val map = asMap(item, here)
            val f = fields(map, here, INDEX_READ, INDEX_IGNORED)
            val name = SimTreeAccess.text(required(f, "name", map, here).value, child(here, "name"))
            val listed = required(f, "columns", map, here).value
            val names = SimElementFields.columnNames(listed, child(here, "columns"), columns)
            val unique = f["unique"]?.let { SimTreeAccess.boolean(it.value, child(here, "unique")) } ?: false
            into.indexes += SimIndex(name, names, unique)
        }
    }

    /** `constraints` einer Tabelle. */
    fun constraints(node: SimNode, path: String, columns: Set<String>, into: TableElementsRead) {
        val list = asList(node, path)
        for ((at, item) in list.items.withIndex()) {
            val here = SimTreeAccess.item(path, at)
            val map = asMap(item, here)
            val f = fields(map, here, CONSTRAINT_READ, emptySet())
            val name = SimTreeAccess.text(required(f, "name", map, here).value, child(here, "name"))
            val typeEntry = required(f, "type", map, here)
            when (SimTreeAccess.text(typeEntry.value, child(here, "type"))) {
                "unique" -> into.uniqueKeys += SimUniqueKey(name, SimElementFields.namesOf(f, map, here, columns))

                "check" -> into.checks += SimCheck(name, SimElementFields.expression(f, map, here))

                "foreign_key" -> {
                    val own = SimElementFields.namesOf(f, map, here, columns)
                    into.foreignKeys += SimElementFields.foreignKey(name, own, f, map, here)
                }

                else -> fail(child(here, "type"), typeEntry.value.position, "Typ der Bedingung ohne Entsprechung")
            }
        }
    }

    /** Prüft, dass jede Zieltabelle und Zielspalte in [known] steht; [known] bildet Tabellennamen auf Spalten ab. */
    fun checkTargets(pending: List<PendingForeignKey>, known: Map<String, Set<String>>) {
        for (item in pending) {
            val columns = known[NameFold.fold(item.key.referencedTable)]
                ?: fail(item.path, item.position, "Zieltabelle nicht vorhanden")
            if (item.key.referencedColumns.any { NameFold.fold(it) !in columns }) {
                fail(item.path, item.position, "Zielspalte nicht vorhanden")
            }
        }
    }
}
