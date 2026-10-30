package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryRequest

/**
 * Der Backend-Port (`SPEC-002`, `contracts/query-backend.md`): der Protokollkern übergibt eine
 * [QueryRequest] und liest das Ergebnis als Strom; das Backend kennt weder Pakete noch Tokens.
 */
interface QueryBackend {
    /**
     * Startet die Anfrage und liefert ihre [QueryExecution].
     *
     * Festlegung ohne Messung: diese Funktion wirft für Fehler des Backends keine Ausnahme; ein
     * Fehler beim Start (zum Beispiel Backend nicht erreichbar) erscheint als
     * [io.github.pt9912.tdswire.domain.QueryEvent.Failure] im Strom von [QueryExecution.events].
     * Die Abbruchausnahme der Coroutine ([kotlinx.coroutines.CancellationException]) bleibt davon
     * unberührt. Offen (ADR-0006): ob ein blockierendes Backend hinter dem Strom zulässig ist und
     * wie es abbricht.
     */
    suspend fun execute(request: QueryRequest): QueryExecution

    /**
     * Beendet die laufende Operation der Anfrage; die Wirkung auf einen laufenden Strom ist die des
     * Backends. Offen (ADR-0006): was mit einem vom Sequenzierer zurückgehaltenen Abschluss
     * ([io.github.pt9912.tdswire.domain.QueryEvent.ResultEnd] oder
     * [io.github.pt9912.tdswire.domain.QueryEvent.UpdateCount]) bei Cancel geschieht.
     */
    suspend fun cancel(sessionId: String, requestId: String)
}
