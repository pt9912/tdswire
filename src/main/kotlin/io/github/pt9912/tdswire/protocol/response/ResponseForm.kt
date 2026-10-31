package io.github.pt9912.tdswire.protocol.response

/**
 * Die Form der Antwort, die der [ResponseSequencer] schreibt (`TDS-FA-10.a` Schritt 2 und 3, `SPEC-031`).
 * Die Form gehört zur Anfrage, nicht zum Strom des Backends.
 */
enum class ResponseForm {
    /**
     * Antwort auf einen SQL_BATCH: jeder Abschluss ist ein DONE. `ReturnStatus` und `OutputParameter`
     * gibt es in dieser Form nicht; sie werden mit einem Fehler der Typabbildung abgelehnt.
     */
    BATCH,

    /**
     * Antwort auf einen RPC: der Abschluss eines Ergebnisses oder Update-Zählers ist ein DONEINPROC, der
     * letzte Token der Antwort ein DONEPROC. `ReturnStatus` wird zu RETURNSTATUS, je `OutputParameter` entsteht
     * ein RETURNVALUE. Festlegung ohne Messung am echten Server: die Tokens stehen in der Reihenfolge des Stroms.
     */
    RPC,
}
