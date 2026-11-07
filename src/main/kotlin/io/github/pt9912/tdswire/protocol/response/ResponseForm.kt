package io.github.pt9912.tdswire.protocol.response

/**
 * Die Form der Antwort, die der [ResponseSequencer] schreibt (`TDS-FA-10.a` Schritt 2 und 3, `SPEC-031`).
 * Die Form gehört zur Anfrage, nicht zum Strom des Backends.
 */
internal enum class ResponseForm {
    /**
     * Antwort auf einen SQL_BATCH: jeder Abschluss ist ein DONE. `ReturnStatus` und `OutputParameter`
     * gibt es in dieser Form nicht; sie werden mit einem Fehler der Typabbildung abgelehnt.
     */
    BATCH,

    /**
     * Antwort auf einen RPC: der Abschluss eines Ergebnisses oder Update-Zählers ist ein DONEINPROC, der
     * letzte Token der Antwort ein DONEPROC, davor stehen RETURNSTATUS und je `OutputParameter` ein RETURNVALUE
     * (Messprotokoll W-1 bis W-6 für `sp_executesql` und `sp_prepexec`, ein Fall je Instanz).
     */
    RPC,

    /**
     * Antwort auf eine Nachricht des Massenladens (Pakettyp 0x07), gemessen mit `SqlBulkCopy` (Messprotokoll M-8 und
     * M-12): wie [BATCH], doch der Abschluss trägt die Befehlskennung `0x00f0` (mit Zähler bei Erfolg, mit `ERROR` bei
     * einem Fehler), und auf den ERROR folgt ein INFO „The statement has been terminated.“.
     */
    BULK,
}
