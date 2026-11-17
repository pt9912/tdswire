package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.BulkLoadingBackend
import io.github.pt9912.tdswire.port.BulkProbeResult
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.simulation.BulkCopyForms
import io.github.pt9912.tdswire.simulation.SimulationBackend

/**
 * Stellt dem Simulations-Backend die Fähigkeit des Massenladens bereit (`ADR-0052` Entscheidung 2). Grenze:
 * [SimulationBackend] trägt [BulkLoadingBackend] nicht selbst: als Mitglieder der Klasse wären `bulkTarget`,
 * `bulkProbe` und `load` öffentlich und Zusage von `SPEC-059`; sie bleiben `internal`. Die Verdrahtung hüllt die
 * Klasse vor der Anfrageschleife ein. Jedes andere [QueryBackend] bleibt unverändert: trägt es [BulkLoadingBackend]
 * selbst, wählt der Kern die Fähigkeit mit `is`.
 */
internal class SimulationBulk(private val backend: SimulationBackend) :
    QueryBackend by backend,
    BulkLoadingBackend {
    override fun bulkTarget(sql: String): BulkTargetResult? = backend.bulkTarget(sql)

    override fun bulkProbe(sql: String): BulkProbeResult? = BulkCopyForms.probe(sql, backend.catalog)

    override suspend fun load(target: BulkTarget, rows: BulkRows): QueryEvent = backend.load(target, rows)

    companion object {
        /** [backend] mit der Fähigkeit, wenn es ein [SimulationBackend] ist, sonst unverändert. */
        fun of(backend: QueryBackend): QueryBackend =
            if (backend is SimulationBackend) SimulationBulk(backend) else backend
    }
}
