package io.github.pt9912.tdswire.domain

/** Eine Spalte eines Ergebnisses; [nullable] sagt, ob die Spalte NULL enthalten darf. */
data class ResultColumn(val name: String, val type: SqlType, val nullable: Boolean)

/**
 * Ein Ereignis im Ergebnisstrom eines Backend-Aufrufs (`SPEC-002`). Das Backend kennt weder
 * Tokens noch das Bit `DONE_MORE`; beides gehört dem Sequenzierer.
 *
 * Festlegung (nicht gemessen): der Response Sequencer zählt ein [Failure] in der Vorschau als nächstes
 * Ereignis und prüft die Ereignisreihenfolge. Offen (ADR-0006): die Unterscheidung statement- und
 * batch-abbrechender Fehler; dieses Modell trägt dazu nichts.
 */
sealed interface QueryEvent {
    /** Beginn eines Ergebnisses mit seinen Spalten; kommt vor den [Row] dieses Ergebnisses. */
    data class ResultStart(val columns: List<ResultColumn>) : QueryEvent

    /**
     * Eine Zeile. Die Darstellung ist `List<Any?>` mit festen JVM-Klassen je Spaltenart
     * (ADR-0013, Zeilendarstellung; die Klassen nennt `ValueMapping`).
     */
    data class Row(val values: List<Any?>) : QueryEvent

    /**
     * Ende eines Ergebnisses. Festlegung ohne Messung: [affectedRows] ist `null`, wenn das Backend
     * keine Zahl liefert; `SPEC-002` und die Messungen sagen dazu nichts.
     */
    data class ResultEnd(val affectedRows: Long?) : QueryEvent

    /** Zahl der betroffenen Zeilen einer Anweisung ohne Ergebnis. */
    data class UpdateCount(val affectedRows: Long) : QueryEvent

    /** Rückgabewert (Return Status) einer Prozedur. */
    data class ReturnStatus(val value: Int) : QueryEvent

    /** Wert eines OUTPUT-Parameters; [value] ist `null` für NULL. */
    data class OutputParameter(val name: String, val type: SqlType, val value: Any?) : QueryEvent

    /**
     * Ein Fehler des Backends, auch beim Start (siehe `QueryBackend.execute`). Trägt keine
     * Fehlerklasse (offen, ADR-0006).
     */
    data class Failure(val code: String, val message: String) : QueryEvent
}
