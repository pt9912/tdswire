package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend

/**
 * Der Bestand an Tabellen des Testservers im Arbeitsspeicher (`TDS-FA-12`): der Integrator der Bibliothek. Er hält die
 * Tabellen, die die Datei der Beschreibung nennt, und die, die ein Client mit `CREATE TABLE` anlegt oder mit
 * `ALTER TABLE … ADD` erweitert; die Bibliothek fragt [catalog] bei jeder Anfrage und meldet jede Strukturänderung an
 * [replace]. Der Testserver kennt keine Katalog- und keine Parserlogik, nur diesen Bestand. Gespeichert wird die
 * Struktur; eine Tabelle hat eine leere Zeilenquelle und keine Callbacks für Zeilen (Schreiben von Zeilen ist nicht
 * Teil dieses Stands). Ein Neustart des Prozesses leert den Bestand bis auf die Tabellen der Datei. Der Zugriff ist
 * synchronisiert; eine Strukturänderung ersetzt die Tabelle als Ganzes, ohne Rücknahme über mehrere Anweisungen.
 */
internal class TableStore(initial: List<SimTable>) {
    private val tables = LinkedHashMap<String, SimTable>()

    init {
        initial.forEach { tables[key(it.schema, it.name)] = it }
    }

    /** Der Stand der Tabellen jetzt; jeder Aufruf baut einen neuen Katalog. */
    @Synchronized
    fun catalog(): SimCatalog = SimCatalog(tables.values.toList())

    /** Legt die Tabelle an oder ersetzt sie durch die Struktur nach der Anweisung (auch bei `ALTER TABLE … ADD`). */
    @Synchronized
    fun replace(schema: String, name: String, columns: List<SimColumn>, primaryKey: List<String>) {
        tables[key(schema, name)] =
            SimTable(schema, name, columns.toList(), primaryKey.toList(), rows = { RowSource { null } })
    }

    /** Das Backend der Bibliothek über diesen Bestand: Anbieter und Strukturänderung zeigen auf ihn. */
    fun backend(): SimulationBackend =
        SimulationBackend(::catalog) { schema, name, columns, key -> replace(schema, name, columns, key) }

    private fun key(schema: String, name: String): String = NameFold.fold(schema) + "\u0000" + NameFold.fold(name)
}
