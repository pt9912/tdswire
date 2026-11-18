package io.github.pt9912.tdswire.domain

/** Die Länge von [SqlType.UnicodeText] in Zeichen, wenn eine Spalte keine nennt (`SPEC-003`). */
internal const val DEFAULT_TEXT_LENGTH = 4000

/**
 * Eine Spalte eines Ergebnisses; [nullable] sagt, ob die Spalte NULL enthalten darf. [maxLength] ist die Länge in
 * Zeichen, die die Metadaten für [SqlType.UnicodeText] melden (`nvarchar(<maxLength>)`, 1 bis 4000, Vorgabe 4000); sie
 * wirkt auf keinen anderen Typ. Ein Text einer [Row] über [maxLength] Zeichen weist die Bibliothek ab: die Antwort
 * endet mit einem Fehler statt mit dem Ergebnis (geprüft im Test `ResponseMergeTest`). Ein Wert außerhalb 1 bis 4000
 * wirft [IllegalArgumentException].
 */
public data class ResultColumn @JvmOverloads constructor(
    val name: String,
    val type: SqlType,
    val nullable: Boolean,
    val maxLength: Int = DEFAULT_TEXT_LENGTH,
) {
    init {
        require(maxLength in 1..DEFAULT_TEXT_LENGTH) { "Spalte $name: maxLength außerhalb 1..$DEFAULT_TEXT_LENGTH" }
    }
}

/**
 * Ein Ereignis im Ergebnisstrom eines Backend-Aufrufs (`SPEC-002`). Das Backend kennt weder
 * Tokens noch das Bit `DONE_MORE`; beides gehört dem Sequenzierer.
 *
 * Festlegung (nicht gemessen): der Response Sequencer zählt ein [Failure] in der Vorschau als nächstes
 * Ereignis und prüft die Ereignisreihenfolge. Die Klasse eines Fehlers trägt [FailureScope].
 */
public sealed interface QueryEvent {
    /**
     * Beginn eines Ergebnisses mit seinen Spalten; kommt vor den [Row] dieses Ergebnisses. [kind] ist die Art der
     * Anweisung, deren Antwort das Ergebnis ist; die Bibliothek leitet daraus die Befehlskennung des Abschlusses ab,
     * auch eines Fehlers im offenen Ergebnis (`SPEC-031`). Gemessen ist nur [StatementKind.MERGE]; jede andere Art
     * behandelt die Bibliothek wie [StatementKind.UNKNOWN], der Abschluss trägt die Kennung `0x00c1`.
     */
    public data class ResultStart @JvmOverloads constructor(
        val columns: List<ResultColumn>,
        val kind: StatementKind = StatementKind.UNKNOWN,
    ) : QueryEvent

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
     * die Anweisung oder den Batch abbricht (`SPEC-031`); ohne Angabe bricht er den Batch ab. [code] ist einer
     * dieser Namen: `TDS_BACKEND_ERROR`, `TDS_UNSUPPORTED_SQL`, `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`,
     * `TDS_SIM_INVALID_VALUE`, `TDS_SIM_KEY_VIOLATION`, `TDS_SIM_OBJECT_EXISTS`, `TDS_SIM_COLUMN_EXISTS`,
     * `TDS_SIM_COLUMN_HAS_DEFAULT`, `TDS_SIM_OBJECT_NOT_FOUND`, `TDS_SIM_NOT_A_CONSTRAINT`,
     * `TDS_SIM_CONSTRAINT_OTHER_TABLE`, `TDS_SIM_NOT_AN_INDEX`, `TDS_SIM_INDEX_OF_CONSTRAINT`,
     * `TDS_SIM_STRING_TRUNCATED`, `TDS_TIMEOUT`; ein anderer Name endet mit `TDS_INTERNAL_ERROR` (50007) und schließt
     * die Verbindung. [objectName] nennt bei `TDS_SIM_UNKNOWN_OBJECT` die unbekannte Tabelle, wie der Text sie
     * nennt (`<Schema>.<Name>` oder `<Name>`, ohne Klammern und Anführungszeichen, in der Schreibung des Textes); mit
     * ihm lautet die Meldung an den Client `Invalid object name '<objectName>'.`, ohne ihn `Unknown object.`. Die
     * Bibliothek gibt den Namen unverändert weiter, wenn er höchstens 515 Zeichen lang ist und kein Steuerzeichen
     * außer dem Zeilenumbruch enthält, und sonst nicht; [message] erreicht den Client nie.
     */
    public data class Failure @JvmOverloads constructor(
        val code: String,
        val message: String,
        val scope: FailureScope = FailureScope.BATCH,
        val objectName: String? = null,
    ) : QueryEvent
}

/**
 * Die Klasse eines Fehlers des Backends (`SPEC-031`, am echten SQL Server gemessen): [STATEMENT] bricht nur die
 * Anweisung ab, die Antwort läuft weiter und die Folgeanweisung wird ausgeführt; [BATCH] bricht den Batch ab, die
 * Antwort endet. Die Fehler des Servers selbst sind [BATCH].
 */
public enum class FailureScope { STATEMENT, BATCH }
