package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend

/** Ausgang der Verdrahtung des Simulations-Backends beim Start. */
internal sealed interface SimulationResult {
    /** Keine Tabellen des Integrators: der Server läuft ohne Simulations-Backend. */
    data object Absent : SimulationResult

    /** Das Backend mit [tables] Tabellen. */
    class Ready(val backend: QueryBackend, val tables: Int) : SimulationResult

    /** Der Start bricht ab; [reason] nennt nie den Inhalt einer Tabelle. */
    class Failed(val reason: String) : SimulationResult
}

/**
 * Verdrahtet das Simulations-Backend (`ARC-008`, `TDS-FA-11.a`, `ADR-0012`): die Tabellen, die der Integrator über
 * die Kotlin-Schnittstelle übergibt, bilden einen Katalog.
 *
 * Eine Tabelle des Integrators übernimmt der Katalog mit eigenen Kopien von `columns` und `primaryKey`
 * ([SimTable.copy] baut sie neu und prüft sie erneut): Ändert der Integrator seine Listen danach, bleibt der Katalog
 * unverändert. Ein Name, der zweimal vorkommt (ohne Beachtung der Schreibweise), bricht den Start ab (Setzung,
 * nicht am Server gemessen).
 */
internal object SimulationSetup {
    fun build(tables: List<SimTable>): SimulationResult {
        if (tables.isEmpty()) return SimulationResult.Absent
        val all = tables.map(::owned)
        return try {
            SimulationResult.Ready(SimulationBackend(SimCatalog(all)), all.size)
        } catch (ignored: IllegalArgumentException) {
            SimulationResult.Failed("Tabellen ungueltig: ein Name kommt doppelt vor")
        }
    }

    private fun owned(table: SimTable): SimTable =
        table.copy(columns = table.columns.toList(), primaryKey = table.primaryKey.toList())
}
