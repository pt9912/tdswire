package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.BulkLoadingBackend
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.protocol.bulk.BulkLoadReader
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.response.ResponseForm
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Der Sitzungszustand „Massenladen erwartet“ der [RequestLoop] (`SPEC-036`, Pakettypen; `ADR-0033` Nr. 5): ein SQL-Text
 * `INSERT BULK` setzt das Ziel, jede folgende Nachricht vom Typ 0x07 lädt in dieses Ziel, und jede Anfrage, die kein
 * `INSERT BULK` ist, löscht es.
 *
 * Gemessen mit `SqlBulkCopy` am echten Server (Messprotokoll M-8 bis M-12, `Microsoft.Data.SqlClient`): `INSERT BULK`
 * ist ein SQL_BATCH und wird mit einem DONE ohne Zähler beantwortet (Befehl `0x00fd`, hier das Ende eines Stroms ohne
 * Ereignis); jeder Batch der Quelle sendet einen eigenen `INSERT BULK` und genau eine Nachricht 0x07, die mit DONE,
 * `COUNT` und Befehl `0x00f0` beantwortet wird ([ResponseForm.BULK]). Bricht der Client ab, kommt statt der Nachricht
 * ein ATTENTION ([abandon]). Setzung, mit mssql-jdbc (d-migrate) am Testserver gemessen, am echten Server nicht: das
 * Ziel bleibt nach einer Nachricht stehen, weil der Treiber nach der letzten Zeile eine weitere Nachricht mit
 * COLMETADATA ohne Zeile sendet (Messprotokoll des Spikes, Abschnitt (b)).
 *
 * Trägt das [backend] die Fähigkeit [BulkLoadingBackend] nicht, ist ein `INSERT BULK` ein gewöhnlicher Text für das
 * Backend, und eine Nachricht 0x07 ergibt `TDS_UNSUPPORTED_SQL`. Das IGNORE-Bit einer Nachricht verwirft sie ohne
 * Aufruf des Backends und wird mit dem Änderungszähler 0 beantwortet (Setzung: die Antwort auf eine verworfene
 * Nachricht ist nicht gemessen). Eine Nachricht, die der Leser abweist ([BulkLoadReader]), ruft das Backend nicht;
 * der Fehler ist der des Parsers, wie beim SQL_BATCH. Die Nachricht gilt als Ganzes: der Gegendruck des Backends
 * wirkt zwischen den Nachrichten, weil die Schleife die nächste erst nach der Antwort liest.
 */
internal class BulkDispatch(backend: QueryBackend) {
    private val loading = backend as? BulkLoadingBackend

    /** Das Ziel des letzten `INSERT BULK`; `null` ohne ein solches oder nach einer anderen Anfrage. */
    private var target: BulkTarget? = null

    /** Wahr, solange das Ziel gesetzt ist und noch keine Nachricht 0x07 in es geladen hat. */
    private var awaiting = false

    /**
     * Wie die Schleife eine Nachricht 0x07 beantwortet: mit einer Antwort aus [Reply.via] in der Form [Reply.form]
     * oder dem Parserfehler.
     */
    sealed interface Answer {
        class Reply(val via: QueryBackend, val form: ResponseForm = ResponseForm.BULK) : Answer

        class Malformed(val error: ProtocolError) : Answer
    }

    /**
     * Prüft einen SQL-Text: ist er ein `INSERT BULK`, setzt er das Ziel und liefert das Backend, das ihn beantwortet;
     * sonst `null` und das Ziel entfällt.
     */
    fun statement(text: String): QueryBackend? {
        target = null
        awaiting = false
        val result = loading?.bulkTarget(text) ?: return null
        return when (result) {
            is BulkTargetResult.Ready -> {
                target = result.target
                awaiting = true
                Fixed(null)
            }

            is BulkTargetResult.Rejected -> Fixed(QueryEvent.Failure(result.code, result.message))
        }
    }

    /** Ein RPC oder eine andere Anfrage beendet das Massenladen. */
    fun clear() {
        target = null
        awaiting = false
    }

    /**
     * Ein ATTENTION ohne laufende Anfrage: wahr, wenn ein `INSERT BULK` auf seine Nachricht 0x07 wartet. Der Client hat
     * das Massenladen dann abgebrochen (er erkennt Verstöße gegen Typ, Länge und NULL vor dem Senden, Messprotokoll
     * M-11); das Ziel entfällt.
     */
    fun abandon(): Boolean {
        val waiting = awaiting
        clear()
        return waiting
    }

    fun answer(message: TdsMessage): Answer {
        val loader = loading
        val current = target
        return when {
            loader == null || current == null -> Answer.Reply(Fixed(NOT_EXPECTED), ResponseForm.BATCH)

            message.ignore -> Answer.Reply(Fixed(QueryEvent.UpdateCount(0)))

            else -> when (val read = BulkLoadReader.read(message.payload)) {
                is ProtocolResult.Failed -> Answer.Malformed(read.error)

                is ProtocolResult.Ok -> {
                    awaiting = false
                    Answer.Reply(Loading { loader.load(current, read.value) })
                }
            }
        }
    }

    /** Eine Antwort mit einem einzigen Ereignis, das erst beim Sammeln entsteht (so läuft `load`). */
    private class Loading(private val event: suspend () -> QueryEvent?) : QueryBackend {
        override suspend fun execute(request: QueryRequest): QueryExecution = object : QueryExecution {
            override val events: Flow<QueryEvent> = flow { event()?.let { emit(it) } }

            override suspend fun close() = Unit
        }

        override suspend fun cancel(sessionId: String, requestId: String) = Unit
    }

    private class Fixed(event: QueryEvent?) : QueryBackend by Loading({ event })

    private companion object {
        /** Eine Nachricht 0x07 ohne vorangehendes `INSERT BULK` oder an einem Backend ohne Massenladen. */
        val NOT_EXPECTED = QueryEvent.Failure(ErrorCode.TDS_UNSUPPORTED_SQL.name, "Massenladen nicht erwartet")
    }
}
