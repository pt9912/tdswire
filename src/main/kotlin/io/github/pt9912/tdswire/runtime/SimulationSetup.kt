package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.telemetry.StartRefusal
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

    /** Der Start bricht aus dem Grund [refusal] ab; [reason] nennt nie den Inhalt einer Tabelle. */
    class Failed(val refusal: StartRefusal, val reason: String) : SimulationResult
}

/**
 * Verdrahtet das Simulations-Backend (`ARC-008`, `TDS-FA-11.a`, `ADR-0012`): die Tabellen, die der Integrator über
 * die Kotlin-Schnittstelle übergibt, bilden einen Katalog.
 *
 * Eine Tabelle des Integrators übernimmt der Katalog mit eigenen Kopien von `columns` und `primaryKey`
 * ([SimTable.copy] baut sie neu und prüft sie erneut): Ändert der Integrator seine Listen danach, bleibt der Katalog
 * unverändert. Ein Name, der zweimal vorkommt (ohne Beachtung der Schreibweise), bricht den Start ab (Setzung,
 * nicht am Server gemessen). Die Fangklausel umschließt nur den Katalog: eine Tabelle, die ihre Prüfung beim Kopieren
 * nicht besteht, wirft ihre eigene `IllegalArgumentException` aus dem Start.
 */
internal object SimulationSetup {
    private const val DUPLICATE_NAME = "Tabellen ungueltig: ein Name kommt doppelt vor"

    fun build(tables: List<SimTable>): SimulationResult {
        val all = tables.map(::owned)
        val catalog = catalogOf(all)
        return when {
            all.isEmpty() -> SimulationResult.Absent
            catalog == null -> SimulationResult.Failed(StartRefusal.TABLE_NAME_DUPLICATE, DUPLICATE_NAME)
            else -> SimulationResult.Ready(SimulationBackend(catalog), all.size)
        }
    }

    private fun catalogOf(all: List<SimTable>): SimCatalog? = try {
        SimCatalog(all)
    } catch (ignored: IllegalArgumentException) {
        null
    }

    private fun owned(table: SimTable): SimTable =
        table.copy(columns = table.columns.toList(), primaryKey = table.primaryKey.toList())
}
