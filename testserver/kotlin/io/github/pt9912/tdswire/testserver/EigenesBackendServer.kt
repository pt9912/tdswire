package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.io.IOException

/** Präfix jeder Zeile von [EigenesBackend] auf der Standardausgabe; `tools/eigenes-backend-test.sh` zählt sie. */
internal const val OWN_BACKEND_PREFIX = "eigenes-backend:"

/** Zeilen vor dem Fehler der Tabelle `fehler_nach_zeilen`; der .NET-Test erwartet dieselbe Zahl. */
internal const val OWN_BACKEND_ROWS_BEFORE_FAILURE = 3

private val OWN_ANSWER = Regex("""SELECT Id, Name FROM (\w+)""")

private val OWN_COLUMNS = listOf(
    ResultColumn("Id", SqlType.Int32, nullable = false),
    ResultColumn("Name", SqlType.UnicodeText, nullable = true),
)

/**
 * Ein eigenes In-Memory-Backend des Integrators (`TDS-RB-04`, `TDS-FA-08`): es implementiert [QueryBackend] allein,
 * delegiert nicht an das Simulations-Backend und sieht von der Bibliothek nur die Namen der öffentlichen Fläche.
 * Die Antwort richtet sich nach dem Text `SELECT Id, Name FROM <Tabelle>`:
 *
 * - `eigen`: zwei Zeilen; `leer`: Spalten und keine Zeile;
 * - `fehler`: ein [QueryEvent.Failure] am Start (`TDS_BACKEND_ERROR`), so meldet ein nicht erreichbares Backend;
 * - `fehler_nach_zeilen`: [OWN_BACKEND_ROWS_BEFORE_FAILURE] Zeilen, dann ein [QueryEvent.Failure];
 * - `wirft`: `execute` wirft eine [IOException] (eine fehlerhafte Implementierung, der Port sieht das nicht vor);
 * - jeder andere Text: ein [QueryEvent.Failure] mit `TDS_UNSUPPORTED_SQL`.
 *
 * Jede Anfrage schreibt vor der Antwort eine Zeile `eigenes-backend: anfrage <Tabelle>`; der Text einer Ausnahme und
 * der Text eines Fehlers stehen nicht in der Ausgabe.
 */
internal class EigenesBackend(private val out: (String) -> Unit = ::println) : QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution {
        val table = OWN_ANSWER.matchEntire(request.sql.trim())?.groupValues?.get(1)
        out("$OWN_BACKEND_PREFIX anfrage ${table ?: "unbekannt"}")
        if (table == "wirft") throw IOException("Backend nicht erreichbar, Geheimnis des Treibers")
        return execution(answer(table))
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit

    private fun answer(table: String?): List<QueryEvent> = when (table) {
        "eigen" -> start() + row(1, "Eins") + row(2, "Zwei") + QueryEvent.ResultEnd(2)

        "leer" -> start() + QueryEvent.ResultEnd(0)

        "fehler" -> listOf(failure("TDS_BACKEND_ERROR"))

        "fehler_nach_zeilen" ->
            start() + (1..OWN_BACKEND_ROWS_BEFORE_FAILURE).map { row(it, "Zeile $it") } + failure("TDS_BACKEND_ERROR")

        else -> listOf(failure("TDS_UNSUPPORTED_SQL"))
    }

    private fun start(): List<QueryEvent> = listOf(QueryEvent.ResultStart(OWN_COLUMNS))

    private fun row(id: Int, name: String): QueryEvent = QueryEvent.Row(listOf(id, name))

    private fun failure(code: String): QueryEvent = QueryEvent.Failure(code, "Text des Backends, nicht für den Client")

    private fun execution(events: List<QueryEvent>): QueryExecution = object : QueryExecution {
        override val events: Flow<QueryEvent> = events.asFlow()

        override suspend fun close() = Unit
    }
}

/**
 * Der Testserver des Laufs mit eigenem Backend (`make eigenes-backend`, Dockerfile-Stage `eigenesbackendserver`):
 * [runTestserver] mit [EigenesBackend] als `BackendSource.Custom`; eine Datei der Beschreibung bricht den Start ab.
 */
fun main() {
    val backend = EigenesBackend()
    println("eigenes-backend-server: Tabellen eigen, leer, fehler, fehler_nach_zeilen, wirft")
    runTestserver { fromFile -> BackendSource.Custom(backend).takeIf { fromFile.isEmpty() } }
}
