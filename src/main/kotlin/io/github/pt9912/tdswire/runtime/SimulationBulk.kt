package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.BulkLoadingBackend
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.simulation.SimulationBackend

/**
 * Stellt dem Simulations-Backend die Fähigkeit des Massenladens bereit (`ADR-0033` Nr. 5): die Klasse
 * [SimulationBackend] ist öffentlich und darf die interne Schnittstelle [BulkLoadingBackend] nicht tragen, die
 * Verdrahtung hüllt sie deshalb vor der Anfrageschleife ein. Jedes andere [QueryBackend], auch ein Backend des
 * Integrators, bleibt ohne die Fähigkeit.
 */
internal class SimulationBulk(private val backend: SimulationBackend) :
    QueryBackend by backend,
    BulkLoadingBackend {
    override fun bulkTarget(sql: String): BulkTargetResult? = backend.bulkTarget(sql)

    override suspend fun load(target: BulkTarget, rows: BulkRows): QueryEvent = backend.load(target, rows)

    companion object {
        /** [backend] mit der Fähigkeit, wenn es ein [SimulationBackend] ist, sonst unverändert. */
        fun of(backend: QueryBackend): QueryBackend =
            if (backend is SimulationBackend) SimulationBulk(backend) else backend
    }
}
