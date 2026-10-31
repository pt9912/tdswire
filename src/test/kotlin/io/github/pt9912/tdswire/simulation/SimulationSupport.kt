package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate

/** Die Spalten der Tabelle `person` in den Tests des Simulations-Backends. */
val personColumns = listOf(
    SimColumn("Id", SqlType.Int32, nullable = false),
    SimColumn("Name", SqlType.UnicodeText),
    SimColumn("Age", SqlType.Int32),
    SimColumn("Score", SqlType.Decimal(10, 2)),
    SimColumn("Active", SqlType.Bool),
    SimColumn("Born", SqlType.Date),
)

/** Eine Zeile von `person` in der Reihenfolge von [personColumns], `Born` ist NULL. */
fun personRow(id: Int, name: String?, age: Int?, score: String? = null, active: Boolean? = null): List<Any?> =
    listOf(id, name, age, score?.let { BigDecimal(it) }, active, null)

/** Die Zeile mit gesetztem `Born`. */
fun List<Any?>.bornOn(date: String): List<Any?> = take(personColumns.size - 1) + LocalDate.parse(date)

fun queryRequest(sql: String, params: List<QueryParameter> = emptyList(), requestId: String = "r1") = QueryRequest(
    sessionId = "s1",
    requestId = requestId,
    sql = sql,
    parameters = params,
    deadlineEpochMillis = null,
)

/**
 * Eine Zeilenquelle, die zählt, was das Backend ihr abverlangt: [opens] Aufrufe von `rows`, [nexts] Aufrufe von
 * `next()` und [closes] Aufrufe von `close()`. Sie liefert [rows] in dieser Reihenfolge und ignoriert den
 * Vorfilter (`lastQuery` hält ihn zur Ansicht). [openFailure] lässt `rows` werfen, [nextFailure] lässt `next()`
 * werfen, sobald [failAfter] Zeilen geliefert sind, [closeFailure] lässt `close()` werfen.
 */
class Probe(
    val rows: List<List<Any?>> = emptyList(),
    val openFailure: Throwable? = null,
    val nextFailure: Throwable = IllegalStateException("Geheimnis der Quelle"),
    val failAfter: Int? = null,
    val closeFailure: Throwable? = null,
) {
    var opens = 0
        private set
    var nexts = 0
        private set
    var closes = 0
        private set
    var lastQuery: SimQuery? = null
        private set

    val table = SimTable(
        name = "person",
        columns = personColumns,
        primaryKey = listOf("Id"),
        rows = { query ->
            opens++
            lastQuery = query
            openFailure?.let { throw it }
            var delivered = 0
            object : RowSource {
                override suspend fun next(): List<Any?>? {
                    nexts++
                    if (failAfter != null && delivered >= failAfter) throw nextFailure
                    return rows.getOrNull(delivered)?.also { delivered++ }
                }

                override fun close() {
                    closes++
                    closeFailure?.let { throw it }
                }
            }
        },
    )

    val backend: SimulationBackend get() = SimulationBackend(SimCatalog(listOf(table)))
}

/** Führt [sql] aus, sammelt den Strom und schließt die Ausführung wie der Protokollkern. */
fun collectEvents(
    backend: QueryBackend,
    sql: String,
    parameters: List<QueryParameter> = emptyList(),
): List<QueryEvent> = runBlocking {
    val execution = backend.execute(queryRequest(sql, parameters))
    try {
        execution.events.toList()
    } finally {
        execution.close()
    }
}
