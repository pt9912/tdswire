package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.GeneratingBackend
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryBackendContract
import kotlinx.coroutines.yield

/**
 * Die zweite Implementierung der Suite [QueryBackendContract]: das Simulations-Backend. Ein Skript wird zu einer
 * Tabelle `t` (Spalten aus `ResultStart`, Zeilen aus den `Row`, ein `Failure` am Ende lässt die Zeilenquelle
 * dort scheitern, ein Skript nur aus einem `Failure` lässt schon das Öffnen scheitern), die Anfrage fragt sie mit
 * `SELECT * FROM t` ab. Das Simulations-Backend legt Code und Meldung eines Fehlers der Quelle selbst fest;
 * [seen] bildet den Fehler des Skripts darauf ab.
 */
class SimulationBackendContractTest : QueryBackendContract() {
    override val sql: String get() = "SELECT * FROM t"

    override fun seen(scripted: QueryEvent.Failure): QueryEvent.Failure =
        QueryEvent.Failure("TDS_BACKEND_ERROR", "Zeilenquelle fehlgeschlagen")

    override fun backend(script: List<QueryEvent>): QueryBackend {
        val columns = script.filterIsInstance<QueryEvent.ResultStart>().firstOrNull()?.columns
        val rows = script.filterIsInstance<QueryEvent.Row>().map { it.values }
        val fails = script.any { it is QueryEvent.Failure }
        val table = SimTable(
            name = "t",
            columns = columns?.map { SimColumn(it.name, it.type, it.nullable) }
                ?: listOf(SimColumn("id", SqlType.Int32, nullable = false)),
            primaryKey = listOf((columns?.first()?.name ?: "id")),
            rows = {
                checkNotNull(columns) { "Backend nicht erreichbar" }
                scripted(rows, fails)
            },
        )
        return SimulationBackend(SimCatalog(listOf(table)))
    }

    override fun generating(rows: Int): GeneratingBackend {
        val counter = Counter()
        val table = SimTable(
            name = "t",
            columns = listOf(SimColumn("n", SqlType.Int32, nullable = false)),
            primaryKey = listOf("n"),
            rows = {
                RowSource {
                    yield()
                    if (counter.pulled < rows) listOf(++counter.pulled) else null
                }
            },
        )
        val simulated = SimulationBackend(SimCatalog(listOf(table)))
        return object : GeneratingBackend {
            override val backend: QueryBackend = simulated
            override val rowsPulled: Int get() = counter.pulled
        }
    }

    private class Counter {
        var pulled = 0
    }

    private fun scripted(rows: List<List<Any?>>, fails: Boolean): RowSource {
        var index = 0
        return RowSource {
            when {
                index < rows.size -> rows[index++]
                fails -> error("Zeilenquelle gestoert")
                else -> null
            }
        }
    }
}
