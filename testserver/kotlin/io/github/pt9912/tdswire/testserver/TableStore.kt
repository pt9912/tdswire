package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.simulation.RowFilter
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend
import java.math.BigDecimal

/**
 * Der Bestand an Tabellen des Testservers im Arbeitsspeicher (`TDS-FA-12`): der Integrator der Bibliothek. Er hält die
 * Tabellen, die die Datei der Beschreibung nennt, und die, die ein Client mit `CREATE TABLE` anlegt oder mit
 * `ALTER TABLE … ADD` erweitert, samt ihren Zeilen; die Bibliothek fragt [catalog] bei jeder Anfrage, meldet jede
 * Strukturänderung an [replace] und gibt die Zeilen eines Massenladens an [insertBatch]. Der Testserver kennt keine
 * Katalog- und keine Parserlogik, nur diesen Bestand. Eine übergebene Tabelle behält Schema, Name, Spalten und
 * Schlüssel; ihre Zeilenquelle und ihre Callbacks ersetzt der Bestand durch die eigenen. Ein Neustart des Prozesses
 * leert den Bestand bis auf die Tabellen der Datei (ohne Zeilen). [catalog], [replace] und [insertBatch] sind je
 * Aufruf gegeneinander gesperrt; eine Strukturänderung ersetzt die Tabelle als Ganzes, ohne Rücknahme über mehrere
 * Anweisungen. Dass die Prüfung der Anweisung und das [replace] eine Einheit sind, trägt das Backend der Bibliothek (es
 * plant unter einer Sperre erneut, `SPEC-035`), nicht dieser Bestand: das Backend aus [backend] ist der einzige
 * Schreiber, den dies deckt.
 *
 * Massenladen ([insertBatch], `ADR-0033` Nr. 5): eine Nachricht ist eine Anweisung und gilt ganz oder gar nicht. Der
 * Bestand prüft vor dem ersten Einfügen jede Zeile der Nachricht: jeder Wert hat die Klasse seiner Spalte
 * (`ValueMapping`), eine nicht nullbare Spalte, auch jede Schlüsselspalte, trägt keinen NULL, und der Schlüssel ist
 * unter den Zeilen der Nachricht und des Bestands eindeutig (eine Tabelle ohne Schlüssel nimmt gleiche Zeilen an,
 * `CREATE TABLE` ohne Schlüssel gelingt am Server, M-19); sonst wirft er, und der Bestand bleibt unverändert (die
 * Bibliothek meldet jeden Fehler des Callbacks einer Nachricht 0x07 dem Client als 2627,
 * `TDS_SIM_KEY_VIOLATION`). Setzung, am Server nicht gemessen: Textschlüssel
 * vergleicht der Bestand ohne Beachtung der Schreibweise (wie die Namen), Dezimalschlüssel mit `compareTo`. Der
 * Bestand ist durch [cellLimit] begrenzt (Setzung, am echten Server nicht gemessen; Vorgabe [CELL_LIMIT]):
 * die Zellen aller Tabellen (Zeilen mal Spalten) zusammen. Eine Nachricht, die die Grenze überschritte, wirft wie jede
 * andere abgewiesene Nachricht und lässt den Bestand unverändert; der Client bekommt einen Fehler statt eines
 * `OutOfMemoryError`, der den Prozess für alle Sitzungen beendet. Die Grenze rechnet in Zellen, nicht in Byte: sie
 * deckt keinen Heap, der kleiner ist als die Zellen der Grenze brauchen (die Zeilen des Bestands haben die Form
 * der Zeilen des Lesers, 4 bis 120 Byte je Zelle, Messprotokoll des Massenladens; der Bestand selbst ist nicht
 * gemessen), und `ALTER TABLE … ADD` auf einer gefüllten Tabelle prüft sie nicht.
 */
internal class TableStore(initial: List<SimTable>, private val cellLimit: Long = CELL_LIMIT) {
    private class Stored(
        val schema: String,
        val name: String,
        val columns: List<SimColumn>,
        val primaryKey: List<String>,
        val rows: MutableList<List<Any?>>,
        val keys: MutableSet<List<Any?>>,
    )

    private val tables = LinkedHashMap<String, Stored>()

    /** Die Zellen aller Zeilen im Bestand. */
    private var cells = 0L

    init {
        initial.forEach { tables[key(it.schema, it.name)] = stored(it.schema, it.name, it.columns, it.primaryKey) }
    }

    /** Der Stand der Tabellen jetzt; jeder Aufruf baut einen neuen Katalog. */
    @Synchronized
    fun catalog(): SimCatalog = SimCatalog(tables.values.map(::described))

    /** Legt die Tabelle an oder ersetzt sie durch die Struktur nach der Anweisung (auch bei `ALTER TABLE … ADD`). */
    @Synchronized
    fun replace(schema: String, name: String, columns: List<SimColumn>, primaryKey: List<String>) {
        val old = tables[key(schema, name)]
        val next = stored(schema, name, columns, primaryKey)
        if (old != null) {
            val index = old.columns.withIndex().associate { NameFold.fold(it.value.name) to it.index }
            val positions = columns.map { index[NameFold.fold(it.name)] }
            for (row in old.rows) next.rows.add(positions.map { at -> at?.let { row[it] } })
            next.rows.forEach { next.keys.add(keyOf(next, it)) }
        }
        tables[key(schema, name)] = next
        cells = tables.values.sumOf { it.rows.size.toLong() * it.columns.size }
    }

    /**
     * Fügt die Zeilen der Nachricht in die Tabelle [schema].[name] ein oder keine; liefert ihre Zahl. Jede Zeile ist
     * eine Abbildung von Spaltennamen der Beschreibung auf Werte, eine fehlende Spalte ist NULL.
     */
    @Synchronized
    fun insertBatch(schema: String, name: String, batch: List<Map<String, Any?>>): Long {
        val table = checkNotNull(tables[key(schema, name)]) { "Tabelle nicht im Bestand" }
        require(cells + batch.size.toLong() * table.columns.size <= cellLimit) { "Bestand voll" }
        val staged = ArrayList<List<Any?>>(batch.size)
        val newKeys = HashSet<List<Any?>>()
        for (values in batch) {
            val row = table.columns.map { values[it.name] }
            row.forEachIndexed { index, value -> RowRules.check(table.columns[index], value) }
            val rowKey = keyOf(table, row)
            require(table.primaryKey.isEmpty() || (rowKey !in table.keys && newKeys.add(rowKey))) {
                "Schlüssel nicht eindeutig"
            }
            staged.add(row)
        }
        table.rows.addAll(staged)
        table.keys.addAll(newKeys)
        cells += staged.size.toLong() * table.columns.size
        return staged.size.toLong()
    }

    /**
     * Ändert die Zeilen der Tabelle [schema].[name], auf die der Filter [where] zutrifft (ohne Filter alle), und
     * liefert ihre Zahl. [set] nennt Spalten der Beschreibung mit ihren Werten. Der Bestand prüft jeden neuen Wert
     * wie beim Einfügen (Klasse der Spalte, NULL nur in nullbaren Spalten, [RowRules]) und lässt keine Änderung zu,
     * die den Schlüssel doppelt machte (eine Tabelle ohne Schlüssel prüft keine Eindeutigkeit und ändert beliebig viele
     * Zeilen, Setzung nach der Semantik von `UPDATE`, am Server nicht gemessen); sonst wirft er, und der Bestand bleibt unverändert (die Bibliothek meldet das
     * als `TDS_BACKEND_ERROR`). Der Filter gilt wie der Vorfilter der Zeilenquelle (Setzung, am Server nicht gemessen).
     */
    @Synchronized
    fun update(schema: String, name: String, set: Map<String, Any?>, where: RowFilter?): Long {
        val table = checkNotNull(tables[key(schema, name)]) { "Tabelle nicht im Bestand" }
        val positions = set.keys.map { column -> table.columns.indexOfFirst { it.name == column } }
        require(positions.all { it >= 0 }) { "Spalte nicht im Bestand" }
        set.entries.forEachIndexed { at, (_, value) -> RowRules.check(table.columns[positions[at]], value) }
        val hit = table.rows.withIndex().filter { (_, row) -> RowRules.matches(table.columns, row, where) }
        val values = set.values.toList()
        val changed = hit.map { (_, row) ->
            row.toMutableList().also { copy -> positions.forEachIndexed { at, p -> copy[p] = values[at] } }
        }
        val keys = HashSet(table.keys)
        hit.forEach { (_, row) -> keys.remove(keyOf(table, row)) }
        if (table.primaryKey.isNotEmpty()) {
            for (row in changed) require(keys.add(keyOf(table, row))) { "Schlüssel nicht eindeutig" }
        }
        hit.forEachIndexed { at, (index, _) -> table.rows[index] = changed[at] }
        table.keys.clear()
        table.keys.addAll(keys)
        return hit.size.toLong()
    }

    /** Das Backend der Bibliothek über diesen Bestand: Anbieter und Strukturänderung zeigen auf ihn. */
    fun backend(): SimulationBackend =
        SimulationBackend(::catalog) { schema, name, columns, key -> replace(schema, name, columns, key) }

    private fun stored(schema: String, name: String, columns: List<SimColumn>, primaryKey: List<String>) =
        Stored(schema, name, columns.toList(), primaryKey.toList(), ArrayList(), HashSet())

    private fun described(table: Stored): SimTable = SimTable(
        schema = table.schema,
        name = table.name,
        columns = table.columns,
        primaryKey = table.primaryKey,
        rows = { rowsOf(table) },
        onInsertBatch = { batch -> insertBatch(table.schema, table.name, batch) },
        onUpdate = { set, where -> update(table.schema, table.name, set, where) },
    )

    @Synchronized
    private fun rowsOf(table: Stored): RowSource {
        val snapshot = table.rows.toList().iterator()
        return RowSource { if (snapshot.hasNext()) snapshot.next() else null }
    }

    private fun keyOf(table: Stored, row: List<Any?>): List<Any?> = table.primaryKey.map { name ->
        val index = table.columns.indexOfFirst { NameFold.fold(it.name) == NameFold.fold(name) }
        when (val value = row[index]) {
            is String -> value.lowercase()
            is BigDecimal -> value.stripTrailingZeros()
            else -> value
        }
    }

    private fun key(schema: String, name: String): String = NameFold.fold(schema) + "\u0000" + NameFold.fold(name)

    internal companion object {
        /** Die Obergrenze der Zellen im Bestand (Setzung), 2 097 152; der Heap bleibt Sache des Betreibers. */
        const val CELL_LIMIT = 2_097_152L
    }
}
