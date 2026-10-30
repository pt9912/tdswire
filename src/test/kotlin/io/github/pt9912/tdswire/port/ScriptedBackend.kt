package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.yield

/**
 * Ein skriptbares Test-Backend hinter dem Port; es ist Test-Quelltext und kein Simulations-Backend.
 * Es kennt nur `domain` und `port`, liefert seine Ereignisse als `Flow` aus einer Liste oder einem
 * Generator, suspendiert (blockiert nie) und zählt, was bei ihm ankommt.
 *
 * Grenze: die Zähler sind nicht threadsicher; das Backend ist für Tests gedacht, die in einem
 * `runBlocking` ohne eigenen Dispatcher laufen. Es sagt nichts über blockierende oder echte
 * Backends (offen, ADR-0006).
 */
class ScriptedBackend private constructor(private val source: (ScriptedBackend) -> Flow<QueryEvent>) : QueryBackend {
    /** Ereignisse, die ein Leser aus einem Strom dieses Backends empfangen hat. */
    var delivered = 0
        private set

    /** Zeilen, die der Generator erzeugt hat. */
    var rowsPulled = 0
        private set

    /** Aufrufe von [QueryExecution.close]. */
    var closeCalls = 0
        private set

    /** Die Anfragen, mit denen [execute] aufgerufen wurde. */
    val requests = mutableListOf<QueryRequest>()

    /** Die Paare (Sitzung, Anfrage) der Aufrufe von [cancel]. */
    val cancelled = mutableListOf<Pair<String, String>>()

    /** Wahr, sobald [cancel] oder [QueryExecution.close] kam; der Generator hört dann auf zu erzeugen. */
    var sourceStopped = false
        private set

    override suspend fun execute(request: QueryRequest): QueryExecution {
        requests += request
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = source(this@ScriptedBackend).onEach { delivered++ }

            override suspend fun close() {
                closeCalls++
                sourceStopped = true
            }
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) {
        cancelled += sessionId to requestId
        sourceStopped = true
    }

    companion object {
        /** Ein Backend, dessen Strom genau die [events] in dieser Reihenfolge liefert. */
        fun ofList(vararg events: QueryEvent): ScriptedBackend = ScriptedBackend { flowOf(*events) }

        /** Ein Backend, dessen Start scheitert: der Strom besteht aus dem einen [failure]. */
        fun failingAtStart(failure: QueryEvent.Failure): ScriptedBackend = ScriptedBackend { flowOf(failure) }

        /**
         * Ein Backend, das ein Ergebnis mit einer Spalte und [rows] Zeilen erzeugt, eine Zeile je
         * Abnahme; nach [cancel] oder [QueryExecution.close] erzeugt es keine weitere.
         */
        fun generating(rows: Int): ScriptedBackend = ScriptedBackend { backend ->
            flow {
                emit(QueryEvent.ResultStart(listOf(ResultColumn("n", SqlType.Int32, nullable = false))))
                var index = 0
                while (index < rows && !backend.sourceStopped) {
                    backend.rowsPulled++
                    emit(QueryEvent.Row(listOf(index)))
                    index++
                    yield()
                }
                if (index == rows) emit(QueryEvent.ResultEnd(rows.toLong()))
            }
        }
    }
}
