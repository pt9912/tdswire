package io.github.pt9912.tdswire.domain

/** Eine Spalte eines Ergebnisses; [nullable] sagt, ob die Spalte NULL enthalten darf. */
public data class ResultColumn(val name: String, val type: SqlType, val nullable: Boolean)

/**
 * Ein Ereignis im Ergebnisstrom eines Backend-Aufrufs (`SPEC-002`). Das Backend kennt weder
 * Tokens noch das Bit `DONE_MORE`; beides gehört dem Sequenzierer.
 *
 * Festlegung (nicht gemessen): der Response Sequencer zählt ein [Failure] in der Vorschau als nächstes
 * Ereignis und prüft die Ereignisreihenfolge. Die Klasse eines Fehlers trägt [FailureScope].
 */
public sealed interface QueryEvent {
    /** Beginn eines Ergebnisses mit seinen Spalten; kommt vor den [Row] dieses Ergebnisses. */
    public data class ResultStart(val columns: List<ResultColumn>) : QueryEvent

    /**
     * Eine Zeile. Die Darstellung ist `List<Any?>` mit festen JVM-Klassen je Spaltenart
     * (ADR-0013, Zeilendarstellung; die Klassen nennt `ValueMapping`).
     */
    public data class Row(val values: List<Any?>) : QueryEvent

    /**
     * Ende eines Ergebnisses. Festlegung ohne Messung: [affectedRows] ist `null`, wenn das Backend
     * keine Zahl liefert; `SPEC-002` und die Messungen sagen dazu nichts.
     */
    public data class ResultEnd(val affectedRows: Long?) : QueryEvent

    /**
     * Zahl der betroffenen Zeilen einer Anweisung ohne Ergebnis. [kind] ist die Art der Anweisung; die Bibliothek
     * schreibt daraus die Befehlskennung des Abschlusses (`SPEC-031`). Die Art ist eine Zusage des Backends: eine
     * falsche Art liefert die falsche Kennung an den Client. Ohne Angabe ist sie [StatementKind.UNKNOWN].
     */
    public data class UpdateCount @JvmOverloads constructor(
        val affectedRows: Long,
        val kind: StatementKind = StatementKind.UNKNOWN,
    ) : QueryEvent

    /** Rückgabewert (Return Status) einer Prozedur. */
    public data class ReturnStatus(val value: Int) : QueryEvent

    /** Wert eines OUTPUT-Parameters; [value] ist `null` für NULL. */
    public data class OutputParameter(val name: String, val type: SqlType, val value: Any?) : QueryEvent

    /**
     * Ein Fehler des Backends, auch beim Start (siehe `QueryBackend.execute`). [scope] sagt, ob der Fehler nur
     * die Anweisung oder den Batch abbricht (`SPEC-031`); ohne Angabe bricht er den Batch ab.
     */
    public data class Failure @JvmOverloads constructor(
        val code: String,
        val message: String,
        val scope: FailureScope = FailureScope.BATCH,
    ) : QueryEvent
}

/**
 * Die Klasse eines Fehlers des Backends (`SPEC-031`, am echten SQL Server gemessen): [STATEMENT] bricht nur die
 * Anweisung ab, die Antwort läuft weiter und die Folgeanweisung wird ausgeführt; [BATCH] bricht den Batch ab, die
 * Antwort endet. Die Fehler des Servers selbst sind [BATCH].
 */
public enum class FailureScope { STATEMENT, BATCH }
