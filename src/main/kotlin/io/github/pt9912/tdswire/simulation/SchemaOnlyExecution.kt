package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Die Antwort der Form F (`SPEC-036`, `ExecuteReader(SchemaOnly)`, `ADR-0059` Entscheidung 3 und 4): vor der Antwort
 * [inner] der Auswahl (Spalten ohne Zeile, oder ihr Fehler) steht je führender `SET`-Anweisung ein Änderungszähler 0
 * der Art `SET_FMTONLY_OFF` und `SET_FMTONLY_ON`. Schließt der Text mit `SET FMTONLY OFF` ([closed]), endet die Antwort
 * nach einer erfolgreichen Auswahl mit dem Zähler 0 der Art `SET_FMTONLY_OFF`; nach einem Fehler der Auswahl folgt
 * nichts. Cancel und `close()` wirken auf [inner], die Ausführung der Auswahl, die das Backend unter Sitzung und
 * Anfrage führt; ein Cancel gilt nicht als Fehler, das schließende `SET` folgt auch dann (Setzung, am Server nicht
 * gemessen).
 */
internal class SchemaOnlyExecution(private val inner: QueryExecution, private val closed: Boolean) : QueryExecution {
    override val events: Flow<QueryEvent> = flow { answer() }

    override suspend fun close() = inner.close()

    private suspend fun FlowCollector<QueryEvent>.answer() {
        emit(QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_OFF))
        emit(QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_ON))
        var failed = false
        inner.events.collect {
            failed = failed || it is QueryEvent.Failure
            emit(it)
        }
        if (closed && !failed) emit(QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_OFF))
    }
}
