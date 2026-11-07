package io.github.pt9912.tdswire.protocol.error

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
 * `CLOSE_WITHOUT_REPLY` kommt aus dem Zustand des Stroms, nicht aus dem Code.
 */
internal data class ErrorRule(
    val number: Int,
    val severity: Int,
    val state: Int,
    val text: String,
    val fate: ConnectionFate,
    val namesHandle: Boolean = false,
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
private const val N_HANDLE = 50009
private const val N_HANDLE_LIMIT = 50010
private const val N_PREPARED_HANDLE = 8179
private const val STATE_PREPARED_HANDLE = 8
private const val N_LOGIN = 18456
private const val N_OBJECT = 208
private const val N_VALUE = 245

/**
 * Die eigenen Fehlercodes (`SPEC-011` bis `SPEC-016`, `SPEC-037` bis `SPEC-039`, `SPEC-048`,
 * `SPEC-049`, `SPEC-057`, `SPEC-058`, `SPEC-012`, `SPEC-060`). Jeder Code hat genau eine [rule]; die Zuordnung steht in
 * `spec/spezifikation.md` (Abschnitt 4).
 *
 * Herkunft der Werte, je Wert getrennt, mit denselben Stufen wie die Tabelle in der Spezifikation
 * (Abschnitt 4):
 * - Gemessen am Referenzclient gegen den Spike-Testserver, nicht am Produkt: nur "genau ein ERROR
 *   vor DONE" beim abgelehnten Login (`SPEC-012`).
 * - Vorschlag der Spezifikation, nicht am echten SQL Server gemessen: die Nummern 208 und 245 und
 *   der Benutzerbereich ab 50001 für Codes ohne SQL-Server-Entsprechung.
 * - Setzung des Nutzers (2026-10-30): Class und Folge von `TDS_PROTOCOL_ERROR`, `TDS_BACKEND_ERROR`,
 *   `TDS_UNSUPPORTED_SQL` und `TDS_INTERNAL_ERROR`; die Folge von `TDS_SHUTDOWN` und `TDS_LOGIN_FAILED`.
 * - Setzung des Nutzers (2026-11-01, `ADR-0027` Entscheidung 2): `TDS_TIMEOUT` ist ein Fehler der Anfragefehler-Gruppe
 *   (Class 16, Verbindung offen), wenn ERROR und DONE vollständig gesendet sind; ein Cancel ist kein Fehler und hat
 *   keinen Code. Die Nummer 50005 ist Festlegung.
 * - Voreinstellung: `TDS_UNSUPPORTED_RPC` und `TDS_SIM_INVALID_VALUE` bei den Anfragefehlern
 *   (Class 16, offen), nicht entschieden.
 * - Setzung (Voreinstellung, nicht gemessen): `TDS_UNKNOWN_HANDLE` und `TDS_PREPARE_LIMIT` in der
 *   Anfragefehler-Gruppe (Class 16, offen); die Nummern 50009 und 50010 sind Festlegung.
 * - Gemessen am Referenzclient gegen einen echten SQL Server (Zeile M-4 des Messprotokolls
 *   `docs/messungen/referenzabgleich.md`, 2022 und 2025): Nummer 8179, Class 16, State 8 und der Text von
 *   `TDS_SIM_UNKNOWN_PREPARED_HANDLE`; der Wortlaut für andere Handle-Nummern als 1 ist als gleich gesetzt.
 * - Grundregel (im Zweifel schließen): nur die Folge der Codes ohne Gruppe. Die Class 20 dieser
 *   Codes ist Festlegung, nicht Grundregel.
 * - Festlegung (nicht gemessen): alle übrigen Werte, nämlich die Nummern ohne Vorschlag, jeder State
 *   (1, außer bei `TDS_SIM_UNKNOWN_PREPARED_HANDLE`), jeder Text und die Class 20 der Codes ohne Gruppe.
 *
 * Offen und nicht entschieden: die Zuordnung von [TDS_UNSUPPORTED_TYPE] und [TDS_SIM_UNKNOWN_OBJECT] zu einer
 * Gruppe; `slice-fehler-mapping` §6.
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
     * Ohne Gruppe: die Folge (schließt) ist Grundregel, Class 20 ist Festlegung. Nummer 208 ist
     * Vorschlag; 207 (Spalte) bildet das Mapping nicht ab. Der echte Server sendet 208 mit Class 16.
     */
    TDS_SIM_UNKNOWN_OBJECT(ErrorRule(N_OBJECT, FATAL, STATE, "Unknown object.", ConnectionFate.CLOSE_AFTER_REPLY)),

    /** Anfragefehler-Gruppe nach Voreinstellung. Nummer 245 ist Vorschlag; 8115 (Überlauf) fehlt im Mapping. */
    TDS_SIM_INVALID_VALUE(ErrorRule(N_VALUE, REQUEST, STATE, "Invalid value.", ConnectionFate.OPEN)),

    /** `SPEC-048`, Class und Folge: Setzung des Nutzers; fester Text ohne Ausnahmetext, danach Verbindungsende. */
    TDS_INTERNAL_ERROR(ErrorRule(N_INTERNAL, FATAL, STATE, "Internal error.", ConnectionFate.CLOSE_AFTER_REPLY)),

    /** `SPEC-049`: danach Verbindungsende (Setzung des Nutzers); Class 20 und Nummer sind Festlegung. */
    TDS_SHUTDOWN(ErrorRule(N_SHUTDOWN, FATAL, STATE, "Server is shutting down.", ConnectionFate.CLOSE_AFTER_REPLY)),

    /** `sp_execute` auf ein Handle, das die Sitzung nicht hält; Anfragefehler-Gruppe, Nummer: Festlegung. */
    TDS_UNKNOWN_HANDLE(ErrorRule(N_HANDLE, REQUEST, STATE, "Unknown prepared handle.", ConnectionFate.OPEN)),

    /** `sp_prepexec` bei erreichter Handle-Grenze der Sitzung; Anfragefehler-Gruppe, Nummer: Festlegung. */
    TDS_PREPARE_LIMIT(
        ErrorRule(N_HANDLE_LIMIT, REQUEST, STATE, "Prepared handle limit reached.", ConnectionFate.OPEN),
    ),

    /**
     * Die Batch-Freigabe `EXEC sp_unprepare <n>` mit einem Handle, das die Sitzung nicht hält; Anfragefehler-Gruppe.
     * Nummer, Class und State sind gemessen (M-4); der Text endet mit der Nummer des Handles
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
}
