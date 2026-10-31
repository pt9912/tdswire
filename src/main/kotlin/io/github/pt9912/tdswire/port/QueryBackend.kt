package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryRequest

/**
 * Der Backend-Port (`SPEC-002`, `spec/spezifikation.md` `SPEC-022`): der Protokollkern übergibt eine
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
     * unberührt. Vertragspflicht (ADR-0013): Backend und Zeilenquelle sind suspendierend und
     * blockieren den Thread des Aufrufers nicht; einen blockierenden Treiber legt der Integrator hinter
     * einen eigenen, begrenzten Pool und ruft ihn unterbrechbar auf.
     */
    suspend fun execute(request: QueryRequest): QueryExecution

    /**
     * Beendet die laufende Operation der Anfrage; die Wirkung auf einen laufenden Strom ist die des
     * Backends. Festlegung (nicht gemessen, Nutzer): ein vom Sequenzierer zurückgehaltener Abschluss
     * ([io.github.pt9912.tdswire.domain.QueryEvent.ResultEnd] oder
     * [io.github.pt9912.tdswire.domain.QueryEvent.UpdateCount]) wird bei Cancel verworfen.
     */
    suspend fun cancel(sessionId: String, requestId: String)
}
