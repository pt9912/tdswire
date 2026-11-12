package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.protocol.token.ServerMessage

/** Was mit der Verbindung nach einem Fehler geschieht (`TDS-FA-06.a` Nr. 6, `ADR-0007`). */
internal enum class ConnectionFate {
    /** ERROR und DONE mit Fehlerstatus (in der Antwort auf einen RPC DONEPROC), die Verbindung bleibt offen. */
    OPEN,

    /** ERROR und DONE mit Fehlerstatus (in der Antwort auf einen RPC DONEPROC), danach schließt der Server. */
    CLOSE_AFTER_REPLY,

    /** Der Server schließt ohne ERROR und DONE; gilt, wenn der Strom nicht mehr synchron ist (`SPEC-031`). */
    CLOSE_WITHOUT_REPLY,
}

/**
 * Ein Eintrag der Zuordnungstabelle: was der Client als ERROR sieht und was danach mit der
 * Verbindung geschieht. [fate] ist [ConnectionFate.OPEN] oder [ConnectionFate.CLOSE_AFTER_REPLY];
 * `CLOSE_WITHOUT_REPLY` kommt aus dem Zustand des Stroms, nicht aus dem Code. [followUp] ist das zweite ERROR, das der
 * Server nach dem ersten sendet (Zusammenfassung des Fehlers, Mechanismus wie INFO 3621 nach 2628); es steht
 * unmittelbar hinter dem ersten und teilt dessen Abschluss.
 */
internal data class ErrorRule(
    val number: Int,
    val severity: Int,
    val state: Int,
    val text: String,
    val fate: ConnectionFate,
    val namesHandle: Boolean = false,
    val followUp: ServerMessage? = null,
)

private const val REQUEST = 16
private const val FATAL = 20
private const val LOGIN = 14
private const val STATE = 1
private const val N_SQL = 50001
private const val N_RPC = 50002
private const val N_TYPE = 50003
private const val N_BACKEND = 50004
private const val N_TIMEOUT = 50005
private const val N_PROTOCOL = 50006
private const val N_INTERNAL = 50007
private const val N_SHUTDOWN = 50008
private const val N_HANDLE_LIMIT = 50010
private const val N_PREPARED_HANDLE = 8179
private const val STATE_PREPARED_HANDLE = 8

/** Der State von 8179 bei `sp_execute` mit unbekanntem Handle (M-6); die Batch-Freigabe trägt 8. */
internal const val STATE_EXECUTE_UNKNOWN_HANDLE = 4

private const val N_KEY_VIOLATION = 2627
private const val CLASS_KEY_VIOLATION = 14
private const val N_LOGIN = 18456
private const val N_OBJECT = 208
private const val N_VALUE = 245
private const val N_OBJECT_EXISTS = 2714
private const val STATE_OBJECT_EXISTS = 6
private const val N_COLUMN_EXISTS = 2705
private const val STATE_COLUMN_EXISTS = 4
private const val N_OBJECT_NOT_FOUND = 4902
private const val N_STRING_TRUNCATED = 2628
private const val N_NOT_A_CONSTRAINT = 3728
private const val N_CONSTRAINT_OTHER_TABLE = 3733
private const val STATE_CONSTRAINT_OTHER_TABLE = 2
private const val N_COLUMN_HAS_DEFAULT = 1781
private const val N_CONSTRAINT_NOT_CREATED = 1750
private const val N_CONSTRAINT_NOT_DROPPED = 3727
private const val STATE_NONE = 0
private const val N_NOT_AN_INDEX = 3701
private const val CLASS_NOT_AN_INDEX = 11
private const val STATE_NOT_AN_INDEX = 7
private const val N_INDEX_OF_CONSTRAINT = 3723
private const val STATE_INDEX_OF_UNIQUE = 5

/**
 * Das zweite ERROR nach 1781 (Class 16, State 0, Text vollständig, ohne Namen; gemessen am echten SQL Server, 2022 und
 * 2025, Szenario `struktur-standardwert-ersetzen`, Zeile 04).
 */
private val CONSTRAINT_NOT_CREATED = ServerMessage(
    N_CONSTRAINT_NOT_CREATED,
    STATE_NONE,
    REQUEST,
    "Could not create constraint or index. See previous errors.",
)

/**
 * Das zweite ERROR nach 3728 und 3733 (Class 16, State 0, Text vollständig, ohne Namen; gemessen am echten SQL Server,
 * 2022 und 2025, Szenario `struktur-drop-constraint`, Zeilen 05, 08, 10, 13, 16 und 19).
 */
private val CONSTRAINT_NOT_DROPPED =
    ServerMessage(N_CONSTRAINT_NOT_DROPPED, STATE_NONE, REQUEST, "Could not drop constraint. See previous errors.")

/**
 * Die eigenen Fehlercodes (`SPEC-011` bis `SPEC-016`, `SPEC-037` bis `SPEC-039`, `SPEC-048`,
 * `SPEC-049`, `SPEC-057`, `SPEC-058`, `SPEC-012`, `SPEC-060`). Jeder Code hat genau eine [rule]; die Zuordnung steht in
 * `spec/spezifikation.md` (Abschnitt 4).
 *
 * Herkunft der Werte, je Wert getrennt, mit denselben Stufen wie die Tabelle in der Spezifikation
 * (Abschnitt 4):
 * - Gemessen am Referenzclient gegen den Spike-Testserver, nicht am Produkt: nur "genau ein ERROR
 *   vor DONE" beim abgelehnten Login (`SPEC-012`).
 * - Vorschlag der Spezifikation, nicht am echten SQL Server gemessen: die Nummer 245 und
 *   der Benutzerbereich ab 50001 für Codes ohne SQL-Server-Entsprechung.
 * - Setzung des Nutzers (2026-10-30): Class und Folge von `TDS_PROTOCOL_ERROR`, `TDS_BACKEND_ERROR`,
 *   `TDS_UNSUPPORTED_SQL` und `TDS_INTERNAL_ERROR`; die Folge von `TDS_SHUTDOWN` und `TDS_LOGIN_FAILED`.
 * - Setzung des Nutzers (2026-11-01, `ADR-0027` Entscheidung 2): `TDS_TIMEOUT` ist ein Fehler der Anfragefehler-Gruppe
 *   (Class 16, Verbindung offen), wenn ERROR und DONE vollständig gesendet sind; ein Cancel ist kein Fehler und hat
 *   keinen Code. Die Nummer 50005 ist Festlegung.
 * - Voreinstellung: `TDS_UNSUPPORTED_RPC` und `TDS_SIM_INVALID_VALUE` bei den Anfragefehlern
 *   (Class 16, offen), nicht entschieden.
 * - Setzung (Voreinstellung, nicht gemessen): `TDS_PREPARE_LIMIT` in der Anfragefehler-Gruppe (Class 16, offen);
 *   die Nummer 50010 ist Festlegung.
 * - Gemessen am Referenzclient gegen einen echten SQL Server (Zeilen M-4, M-6 und M-7 des Messprotokolls
 *   `docs/messungen/referenzabgleich.md`, 2022 und 2025): Nummer 8179, Class 16, State 8 bei der Batch-Freigabe und
 *   State 4 bei `sp_execute` ([STATE_EXECUTE_UNKNOWN_HANDLE]) und der Text von `TDS_SIM_UNKNOWN_PREPARED_HANDLE`; der
 *   Wortlaut für andere Handle-Nummern als 1 (Batch-Freigabe) und 999999 (`sp_execute`) ist als gleich gesetzt.
 * - Gemessen am Referenzclient gegen einen echten SQL Server (Zeile M-19 des Messprotokolls, 2022 und 2025): Nummer,
 *   Class 16 und State von `TDS_SIM_OBJECT_EXISTS` (2714, State 6), `TDS_SIM_COLUMN_EXISTS` (2705, State 4) und
 *   `TDS_SIM_OBJECT_NOT_FOUND` (4902, State 1) sowie die offene Verbindung; der Text ist ein Auszug des gemessenen
 *   Textes ohne die Namen (Setzung: der Text eines Backends erreicht den Client nie).
 * - Gemessen am Referenzclient gegen einen echten SQL Server (Messprotokoll `drop-constraint-d-migrate-2026-11-11`,
 *   2022 und 2025): Nummer, Class 16, State und die offene Verbindung von `TDS_SIM_NOT_A_CONSTRAINT` (3728, State 1)
 *   und `TDS_SIM_CONSTRAINT_OTHER_TABLE` (3733, State 2); der Text ist ein Auszug ohne die Namen (Setzung).
 * - Gemessen am Referenzclient gegen einen echten SQL Server (Messprotokoll
 *   `standardwert-ersetzen-d-migrate-2026-11-12`, 2022 und 2025, Zeile 04): Nummer 1781, Class 16, State 1 und die
 *   offene Verbindung von `TDS_SIM_COLUMN_HAS_DEFAULT` sowie das zweite ERROR 1750 (Class 16, State 0); der Text von
 *   1781 ist der gemessene Text (er nennt keine Namen).
 * - Gemessen am Referenzclient gegen einen echten SQL Server (Messprotokoll `drop-index-d-migrate-2026-11-11`, 2022 und
 *   2025): Nummer, Class, State und die offene Verbindung von `TDS_SIM_NOT_AN_INDEX` (3701, Class 11, State 7) und
 *   `TDS_SIM_INDEX_OF_CONSTRAINT` (3723, Class 16, State 5); der Text ist ein Auszug ohne die Namen (Setzung).
 * - Gemessen am Referenzclient gegen einen echten SQL Server (Zeile M-15 des Messprotokolls, 2022 und 2025): Nummer,
 *   Class 16, State 1 und die offene Verbindung von `TDS_SIM_STRING_TRUNCATED` (2628, `MERGE` mit einem Text über der
 *   Länge der Spalte); der Text ist ein Auszug des gemessenen Textes ohne die Namen (Setzung).
 * - Gemessen am Referenzclient gegen einen echten SQL Server (Zeile M-15 und Schritt `fehler-vor-zeilen` des
 *   Messprotokolls): Nummer 208, Class 16, State 1 und die offene Verbindung von `TDS_SIM_UNKNOWN_OBJECT`; der Text
 *   „Unknown object.“ weicht vom gemessenen („Invalid object name '<Name>'.“) ab und ist Festlegung.
 * - Grundregel (im Zweifel schließen): nur die Folge der Codes ohne Gruppe. Die Class 20 dieser
 *   Codes ist Festlegung, nicht Grundregel.
 * - Festlegung (nicht gemessen): alle übrigen Werte, nämlich die Nummern ohne Vorschlag, jeder State
 *   (1, außer bei `TDS_SIM_UNKNOWN_PREPARED_HANDLE`), jeder Text und die Class 20 der Codes ohne Gruppe.
 *
 * Offen und nicht entschieden: die Zuordnung von [TDS_UNSUPPORTED_TYPE] zu einer Gruppe.
 */
internal enum class ErrorCode(val rule: ErrorRule) {
    /** Class und Folge: Setzung des Nutzers; die Nummer aus dem Benutzerbereich ist Festlegung. */
    TDS_PROTOCOL_ERROR(ErrorRule(N_PROTOCOL, FATAL, STATE, "Protocol error.", ConnectionFate.CLOSE_AFTER_REPLY)),

    /** Die feste Ablehnung (`SPEC-012`); Bytes gleich `LoginResponses.rejection`, ein Test belegt es. */
    TDS_LOGIN_FAILED(ErrorRule(N_LOGIN, LOGIN, STATE, "Login failed.", ConnectionFate.CLOSE_AFTER_REPLY)),

    /** Anfragefehler-Gruppe nach Voreinstellung. */
    TDS_UNSUPPORTED_RPC(ErrorRule(N_RPC, REQUEST, STATE, "Unsupported RPC.", ConnectionFate.OPEN)),

    /** Ohne Gruppe: die Folge (schließt) ist Grundregel, Class 20 ist Festlegung. */
    TDS_UNSUPPORTED_TYPE(ErrorRule(N_TYPE, FATAL, STATE, "Unsupported type.", ConnectionFate.CLOSE_AFTER_REPLY)),

    /** Anfragefehler-Gruppe (Setzung des Nutzers). */
    TDS_BACKEND_ERROR(ErrorRule(N_BACKEND, REQUEST, STATE, "Backend error.", ConnectionFate.OPEN)),

    /** Anfragefehler-Gruppe (Setzung des Nutzers, `ADR-0027`); der Ablauf der Frist einer Anfrage (`SPEC-009`). */
    TDS_TIMEOUT(ErrorRule(N_TIMEOUT, REQUEST, STATE, "Timeout.", ConnectionFate.OPEN)),

    /** Anfragefehler-Gruppe (Setzung des Nutzers); keine SQL-Server-Entsprechung. */
    TDS_UNSUPPORTED_SQL(ErrorRule(N_SQL, REQUEST, STATE, "Unsupported SQL.", ConnectionFate.OPEN)),

    /**
     * Anfragefehler-Gruppe, am Referenzclient gegen den echten SQL Server gemessen (Zeile M-15 des Messprotokolls):
     * Nummer 208, Class 16, State 1, die Verbindung bleibt offen. 207 (Spalte) bildet das Mapping nicht ab; der Text
     * ist Festlegung.
     */
    TDS_SIM_UNKNOWN_OBJECT(ErrorRule(N_OBJECT, REQUEST, STATE, "Unknown object.", ConnectionFate.OPEN)),

    /** Anfragefehler-Gruppe nach Voreinstellung. Nummer 245 ist Vorschlag; 8115 (Überlauf) fehlt im Mapping. */
    TDS_SIM_INVALID_VALUE(ErrorRule(N_VALUE, REQUEST, STATE, "Invalid value.", ConnectionFate.OPEN)),

    /** `SPEC-048`, Class und Folge: Setzung des Nutzers; fester Text ohne Ausnahmetext, danach Verbindungsende. */
    TDS_INTERNAL_ERROR(ErrorRule(N_INTERNAL, FATAL, STATE, "Internal error.", ConnectionFate.CLOSE_AFTER_REPLY)),

    /** `SPEC-049`: danach Verbindungsende (Setzung des Nutzers); Class 20 und Nummer sind Festlegung. */
    TDS_SHUTDOWN(ErrorRule(N_SHUTDOWN, FATAL, STATE, "Server is shutting down.", ConnectionFate.CLOSE_AFTER_REPLY)),

    /** `sp_prepexec` bei erreichter Handle-Grenze der Sitzung; Anfragefehler-Gruppe, Nummer: Festlegung. */
    TDS_PREPARE_LIMIT(
        ErrorRule(N_HANDLE_LIMIT, REQUEST, STATE, "Prepared handle limit reached.", ConnectionFate.OPEN),
    ),

    /**
     * Ein Fehler des Einfüge-Callbacks beim Massenladen (Nachricht 0x07, `SPEC-036`). Nummer 2627, Class 14, State 1
     * sind gemessen am Referenzclient (`SqlBulkCopy`, Schlüsselverstoß, Messprotokoll M-12, Instanz 2022 und 2025);
     * der Text ist gekürzt (Setzung: der echte Server nennt Namen und Wert). Setzung: jeder Fehler des Callbacks einer
     * Nachricht 0x07 meldet die Bibliothek so, weil sie die Ursache nicht unterscheiden kann.
     */
    TDS_SIM_KEY_VIOLATION(
        ErrorRule(
            N_KEY_VIOLATION,
            CLASS_KEY_VIOLATION,
            STATE,
            "Violation of PRIMARY KEY constraint. Cannot insert duplicate key.",
            ConnectionFate.OPEN,
        ),
    ),

    /**
     * Die Batch-Freigabe `EXEC sp_unprepare <n>` und `sp_execute` mit einem Handle, das die Sitzung nicht hält;
     * Anfragefehler-Gruppe. Nummer und Class sind gemessen, der State dieser Regel gilt für die Batch-Freigabe (M-4);
     * `sp_execute` trägt [STATE_EXECUTE_UNKNOWN_HANDLE] (M-6). Der Text endet mit der Nummer des Handles
     * ([ErrorRule.namesHandle], gebildet von [ErrorRule.message]).
     */
    TDS_SIM_UNKNOWN_PREPARED_HANDLE(
        ErrorRule(
            N_PREPARED_HANDLE,
            REQUEST,
            STATE_PREPARED_HANDLE,
            "Could not find prepared statement with handle",
            ConnectionFate.OPEN,
            namesHandle = true,
        ),
    ),

    /**
     * `CREATE TABLE` einer Tabelle, die der Katalog schon führt. Nummer, Class 16, State 6 und die offene Verbindung
     * sind gemessen (M-19); der Text lässt den Namen aus.
     */
    TDS_SIM_OBJECT_EXISTS(
        ErrorRule(
            N_OBJECT_EXISTS,
            REQUEST,
            STATE_OBJECT_EXISTS,
            "There is already an object named in the database.",
            ConnectionFate.OPEN,
        ),
    ),

    /**
     * `ALTER TABLE … ADD CONSTRAINT … DEFAULT … FOR` einer Spalte, die schon einen Standardwert hat. Nummer 1781,
     * Class 16, State 1, der Text und die offene Verbindung sind gemessen (2022 und 2025, Szenario
     * `struktur-standardwert-ersetzen`); danach folgt das zweite ERROR 1750 ([CONSTRAINT_NOT_CREATED], gemessen).
     */
    TDS_SIM_COLUMN_HAS_DEFAULT(
        ErrorRule(
            N_COLUMN_HAS_DEFAULT,
            REQUEST,
            STATE,
            "Column already has a DEFAULT bound to it.",
            ConnectionFate.OPEN,
            followUp = CONSTRAINT_NOT_CREATED,
        ),
    ),

    /**
     * `ALTER TABLE … ADD` einer Spalte, die die Tabelle schon führt. Nummer, Class 16, State 4 und die offene
     * Verbindung sind gemessen (M-19); der Text lässt die Namen aus.
     */
    TDS_SIM_COLUMN_EXISTS(
        ErrorRule(
            N_COLUMN_EXISTS,
            REQUEST,
            STATE_COLUMN_EXISTS,
            "Column names in each table must be unique.",
            ConnectionFate.OPEN,
        ),
    ),

    /**
     * `ALTER TABLE` einer Tabelle, die der Katalog nicht führt. Nummer, Class 16, State 1 und die offene Verbindung
     * sind gemessen (M-19); der Text lässt den Namen aus.
     */
    TDS_SIM_OBJECT_NOT_FOUND(
        ErrorRule(
            N_OBJECT_NOT_FOUND,
            REQUEST,
            STATE,
            "Cannot find the object because it does not exist or you do not have permissions.",
            ConnectionFate.OPEN,
        ),
    ),

    /**
     * `ALTER TABLE … DROP CONSTRAINT <Name>` ohne `IF EXISTS`, der Name ist keine Bedingung der Tabelle. Nummer 3728,
     * Class 16, State 1 und die offene Verbindung sind gemessen (2022 und 2025, Szenario `struktur-drop-constraint`);
     * danach folgt das zweite ERROR 3727 ([CONSTRAINT_NOT_DROPPED], gemessen). Der Text ist ein Auszug des gemessenen
     * Textes ohne den Namen.
     */
    TDS_SIM_NOT_A_CONSTRAINT(
        ErrorRule(
            N_NOT_A_CONSTRAINT,
            REQUEST,
            STATE,
            "Name is not a constraint.",
            ConnectionFate.OPEN,
            followUp = CONSTRAINT_NOT_DROPPED,
        ),
    ),

    /**
     * `ALTER TABLE … DROP CONSTRAINT <Name>` ohne `IF EXISTS`, der Name gehört einer Bedingung einer anderen Tabelle.
     * Nummer 3733, Class 16, State 2 und die offene Verbindung sind gemessen (wie [TDS_SIM_NOT_A_CONSTRAINT]); danach
     * folgt das zweite ERROR 3727. Der Text ist ein Auszug ohne die Namen.
     */
    TDS_SIM_CONSTRAINT_OTHER_TABLE(
        ErrorRule(
            N_CONSTRAINT_OTHER_TABLE,
            REQUEST,
            STATE_CONSTRAINT_OTHER_TABLE,
            "Constraint does not belong to table.",
            ConnectionFate.OPEN,
            followUp = CONSTRAINT_NOT_DROPPED,
        ),
    ),

    /**
     * `DROP INDEX <Name> ON <Tabelle>` ohne `IF EXISTS`, der Name ist kein Index der Tabelle. Nummer 3701, Class 11 und
     * die offene Verbindung sind gemessen (2022 und 2025, Szenario `struktur-drop-index`), State 7 für eine bekannte
     * Tabelle; für eine unbekannte Tabelle sendet der Server State 6, die Bibliothek State 7 (Grenze, `SPEC-036`). Das
     * DONE nach dem ERROR trägt beim Server den Befehl `0x00c9`, bei der Bibliothek `0x00fd` (Grenze). Der Text ist ein
     * Auszug des gemessenen Textes ohne den Namen.
     */
    TDS_SIM_NOT_AN_INDEX(
        ErrorRule(
            N_NOT_AN_INDEX,
            CLASS_NOT_AN_INDEX,
            STATE_NOT_AN_INDEX,
            "Cannot drop the index because it does not exist or you do not have permission.",
            ConnectionFate.OPEN,
        ),
    ),

    /**
     * `DROP INDEX <Name> ON <Tabelle>` mit dem Namen einer Eindeutigkeit der Tabelle, mit und ohne `IF EXISTS`. Nummer
     * 3723, Class 16, State 5 und die offene Verbindung sind gemessen (2022 und 2025, Szenario `struktur-drop-index`,
     * `UNIQUE KEY`); der Text ist ein Auszug des gemessenen Textes ohne den Namen.
     */
    TDS_SIM_INDEX_OF_CONSTRAINT(
        ErrorRule(
            N_INDEX_OF_CONSTRAINT,
            REQUEST,
            STATE_INDEX_OF_UNIQUE,
            "An explicit DROP INDEX is not allowed on the index.",
            ConnectionFate.OPEN,
        ),
    ),

    /**
     * Ein Text über der Länge der Spalte beim Einfügen oder Ändern eines `MERGE`. Nummer, Class 16, State 1 und die
     * offene Verbindung sind gemessen (M-15); der Text lässt Tabelle, Spalte und Wert aus.
     */
    TDS_SIM_STRING_TRUNCATED(
        ErrorRule(
            N_STRING_TRUNCATED,
            REQUEST,
            STATE,
            "String or binary data would be truncated.",
            ConnectionFate.OPEN,
        ),
    ),
}
