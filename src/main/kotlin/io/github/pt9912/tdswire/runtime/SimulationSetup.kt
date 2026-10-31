package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimDescriptionReader
import io.github.pt9912.tdswire.simulation.SimLoadException
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend

/** Ausgang der Verdrahtung des Simulations-Backends beim Start. */
internal sealed interface SimulationResult {
    /** Weder Datei noch Tabellen des Integrators: der Prozess läuft ohne Simulations-Backend. */
    data object Absent : SimulationResult

    /** Das Backend mit [tables] Tabellen. */
    class Ready(val backend: QueryBackend, val tables: Int) : SimulationResult

    /** Der Start bricht ab; [reason] nennt nie den Inhalt der Datei. */
    class Failed(val reason: String) : SimulationResult
}

/**
 * Verdrahtet das Simulations-Backend (`ARC-008`, `TDS-FA-11.a`, `ADR-0012`): die Tabellen aus der Datei der
 * Konfiguration (Bytes, von [YamlTreeReader] zum Baum und vom Leser der Beschreibung zu Tabellen gelesen) und die
 * Tabellen, die der Integrator über die Kotlin-Schnittstelle übergibt, bilden einen Katalog. Tabellen aus der Datei
 * haben eine leere Zeilenquelle und keine Callbacks.
 *
 * Eine Tabelle des Integrators übernimmt der Katalog mit eigenen Kopien von `columns` und `primaryKey`
 * ([SimTable.copy] baut sie neu und prüft sie erneut): Ändert der Integrator seine Listen danach, bleibt der Katalog
 * unverändert. Ein Name, der in Datei und Schnittstelle oder zweimal in der Schnittstelle vorkommt (ohne Beachtung der
 * Schreibweise), bricht den Start ab (Setzung, nicht am Server gemessen).
 */
internal object SimulationSetup {
    fun build(file: ByteArray?, tables: List<SimTable>): SimulationResult {
        if (file == null && tables.isEmpty()) return SimulationResult.Absent
        return try {
            val described = file?.let { SimDescriptionReader.read(YamlTreeReader.read(it)) }.orEmpty()
            val all = described + tables.map(::owned)
            SimulationResult.Ready(SimulationBackend(SimCatalog(all)), all.size)
        } catch (error: SimLoadException) {
            SimulationResult.Failed("Beschreibung der Tabellen ungueltig: ${error.message}")
        } catch (ignored: IllegalArgumentException) {
            SimulationResult.Failed("Tabellen ungueltig: ein Name kommt doppelt vor")
        }
    }

    private fun owned(table: SimTable): SimTable =
        table.copy(columns = table.columns.toList(), primaryKey = table.primaryKey.toList())
}
