package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.simulation.FilterTerm
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimQuery
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicLong

/** Die Anfrage des Exports (Muster 1 in `docs/plan/referenz-workload.md` S3). */
private val EXPORT_SQL = Regex("""SELECT \* FROM ref_zeilen""", RegexOption.IGNORE_CASE)

/** Die Punktabfrage (Muster 3 in `docs/plan/referenz-workload.md` S3): ein Parameter, der Schlüssel `Id`. */
private val POINT_SQL = Regex("""SELECT \* FROM ref_zeilen WHERE Id = @\w+""", RegexOption.IGNORE_CASE)

/** Präfix der Zeilen des Lasttests auf der Standardausgabe; `tools/lasttest.sh` liest sie. */
internal const val LASTTEST_PREFIX = "lasttest:"

/**
 * Aufbau B (`docs/plan/referenz-workload.md` S6 und S7): ein eigenes [QueryBackend], das auf `SELECT * FROM ref_zeilen`
 * [rows] Zeilen aus [ReferenzZeilen] liefert, auf `SELECT * FROM ref_zeilen WHERE Id = @<Name>` mit einem Parameter
 * vom Typ `Int64` die eine Zeile dieser `Id` (keine, wenn sie außerhalb von 1 bis [rows] liegt) und sonst nichts tut.
 * Die Zeilen entstehen erst, wenn der Strom sie zieht (`ADR-0006`); es liest nichts voraus und hält keine Zeile.
 * Massenladen nimmt ein eigenes Backend nicht an: die Fähigkeit dazu hat die öffentliche Fläche nur für das
 * Simulations-Backend (Befund in der Spezifikation `SPEC-036`).
 */
internal class ZeilenBackend(private val rows: Long, private val seed: Long) : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution {
        val sql = request.sql.trim()
        val point = request.parameters.singleOrNull()?.value as? Long
        val events: Flow<QueryEvent> = if (point != null && POINT_SQL.matches(sql)) {
            flow {
                emit(QueryEvent.ResultStart(ReferenzZeilen.resultColumns))
                val found = point in 1..rows
                if (found) emit(QueryEvent.Row(ReferenzZeilen.row(seed, point)))
                emit(QueryEvent.ResultEnd(if (found) 1L else 0L))
            }
        } else if (EXPORT_SQL.matches(sql)) {
            flow {
                emit(QueryEvent.ResultStart(ReferenzZeilen.resultColumns))
                for (n in 1..rows) emit(QueryEvent.Row(ReferenzZeilen.row(seed, n)))
                emit(QueryEvent.ResultEnd(rows))
            }
        } else {
            flow { emit(QueryEvent.Failure("TDS_UNSUPPORTED_SQL", "Text des Backends, nicht für den Client")) }
        }
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = events

            override suspend fun close() = Unit
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

/**
 * Aufbau C (und der Lesepfad von A): die Tabelle `ref_zeilen` für das Simulations-Backend. Die Zeilenquelle erzeugt
 * [rows] Zeilen aus [ReferenzZeilen] bei jedem Zug; nennt der Vorfilter der Anfrage genau eine `Id` (`=` auf `Id`),
 * liefert sie nur diese Zeile, sonst alle; `onInsertBatch` zählt die Zeilen und speichert keine (`S6`: der
 * Bestand des Testservers nähme höchstens 2 097 152 Zellen auf). Erreicht der Zähler ein Vielfaches von [rows],
 * schreibt er `lasttest: import <Summe>`; `tools/lasttest.sh` prüft damit, dass jeder Lauf alle Zeilen erreicht hat.
 */
internal fun referenzTabelle(rows: Long, seed: Long, out: (String) -> Unit = ::println): SimTable {
    val counted = AtomicLong()
    val names = ReferenzZeilen.columns.map { it.name }
    return SimTable(
        name = REF_TABLE,
        columns = ReferenzZeilen.columns,
        primaryKey = listOf("Id"),
        rows = { query -> ZeilenQuelle(query, rows, seed, names) },
        onInsertBatch = { batch ->
            val total = counted.addAndGet(batch.size.toLong())
            if (total / rows > (total - batch.size) / rows) out("$LASTTEST_PREFIX import $total")
            batch.size.toLong()
        },
    )
}

private class ZeilenQuelle(
    private val query: SimQuery,
    private val rows: Long,
    private val seed: Long,
    private val names: List<String>,
) : RowSource {
    private val point: Long? = query.filter?.terms
        ?.filterIsInstance<FilterTerm.In>()
        ?.firstOrNull { it.column == "Id" }
        ?.values?.singleOrNull() as? Long
    private var next = 0L

    override suspend fun next(): List<Any?>? {
        next = when {
            point == null -> next + 1
            next == 0L -> point
            else -> rows + 1
        }
        if (next !in 1..rows) return null
        val full = ReferenzZeilen.row(seed, next)
        return query.columns.map { full[names.indexOf(it.name)] }
    }
}

/** Zeilen je Stapel des Imports in Aufbau A; wie `BatchSize` des Clients (Setzung S3, 1000). */
internal const val IMPORT_BATCH = 1000

/**
 * Aufbau A für den Import: ruft den Stapel-Callback [table] in Stapeln zu [IMPORT_BATCH] Zeilen aus [ReferenzZeilen]
 * direkt auf, ohne Server, Draht und Client; gibt die Summe zurück, die der Callback meldet. Die Zeilen entstehen je
 * Stapel, nichts wird gehalten.
 */
internal suspend fun importBatches(table: SimTable, rows: Long, seed: Long): Long {
    val insert = table.onInsertBatch ?: error("onInsertBatch fehlt")
    val names = ReferenzZeilen.columns.map { it.name }
    var total = 0L
    var next = 1L
    while (next <= rows) {
        val last = minOf(next + IMPORT_BATCH - 1, rows)
        val batch = (next..last).map { n -> names.zip(ReferenzZeilen.row(seed, n)).toMap() }
        total += insert(batch)
        next = last + 1
    }
    return total
}
