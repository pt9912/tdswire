package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn

/**
 * Die Fähigkeit eines Backends, das Massenladen (Paket `BULK_LOAD`, `INSERT BULK`) anzunehmen (`ADR-0052`,
 * `SPEC-035`, `SPEC-059`). Ein [QueryBackend], das diese Schnittstelle zusätzlich trägt, bekommt das Massenladen; die
 * Bibliothek wählt sie mit `backend is BulkLoadingBackend`. Ein Backend ohne sie bekommt weder den Text `INSERT BULK`
 * als Besonderheit noch ein Paket 0x07 zu sehen: der Text geht an [QueryBackend.execute], das Paket ergibt einen
 * Fehler (`TDS_UNSUPPORTED_SQL`). Das Simulations-Backend trägt die Fähigkeit selbst.
 *
 * Ablauf: der Protokollkern fragt bei jedem SQL-Text mit [bulkProbe], ob er die Probe der Zieltabelle ist, und mit
 * [bulkTarget], ob er ein `INSERT BULK` ist, und merkt sich das Ziel für die folgenden Nachrichten vom Typ 0x07
 * derselben Sitzung (der Sitzungszustand liegt beim Kern, das Backend hält keinen); jede dieser Nachrichten dekodiert
 * er zu [BulkRows] und gibt sie mit [load] weiter.
 *
 * Zusagen:
 * - **Die Erkennung liegt beim Backend.** Die Bibliothek erkennt `INSERT BULK` und die Probe für ein fremdes Backend
 *   nicht; beide Funktionen bekommen den SQL-Text und entscheiden. Die Form der Texte ist die von `SPEC-036` Form B und
 *   nur für den Referenzclient (`Microsoft.Data.SqlClient` 7.1.1, `SqlBulkCopy`) gemessen; mit anderen Clients ist sie
 *   nicht gemessen. Ändert sich Form B, ändert sich diese Zusage.
 * - **Eine Nachricht ist eine Anweisung.** [load] bekommt alle Zeilen einer Nachricht 0x07 als Ganzes (Obergrenze
 *   `SPEC-007`, 16 MiB je Nachricht in der Voreinstellung; der Heap muss sie tragen); das IGNORE-Bit einer Nachricht
 *   verwirft sie ohne Aufruf von [load].
 * - **Fehler.** Ein Fehler des Backends erreicht den Client als `TDS_BACKEND_ERROR` (Nummer 50004), wenn [load] ihn als
 *   [QueryEvent.Failure] mit diesem Code meldet; die Verbindung bleibt offen.
 * - **Keine Transaktion.** Mehrere Nachrichten sind keine Transaktion; die Bibliothek nimmt nichts zurück.
 * - **Eine Ausnahme** in [bulkProbe], [bulkTarget] oder [load] endet wie die Ausnahme von [QueryBackend.execute] mit
 *   `TDS_INTERNAL_ERROR` (50007, `SPEC-048`); der Text der Ausnahme erreicht den Client nicht, der Server bleibt
 *   verfügbar.
 */
public interface BulkLoadingBackend {
    /**
     * Erkennt `INSERT BULK`: `null`, wenn der Text keines ist (er geht den üblichen Weg über [QueryBackend.execute]);
     * sonst das Ziel oder die Ablehnung. Die Funktion ruft keine Zeilen ab und blockiert nicht.
     */
    public fun bulkTarget(sql: String): BulkTargetResult?

    /**
     * Erkennt die Probe der Zieltabelle, die `SqlBulkCopy` (Referenzclient) vor `INSERT BULK` stellt (`SPEC-036`,
     * Form B): `null`, wenn der Text keine ist; sonst die Spalten der Tabelle, aus denen der Protokollkern die Antwort
     * baut, oder die Ablehnung. Die Funktion ruft keine Zeilen ab und blockiert nicht.
     */
    public fun bulkProbe(sql: String): BulkProbeResult?

    /**
     * Gibt die Zeilen einer Nachricht an das Ziel weiter und liefert das Ereignis, mit dem die Antwort endet:
     * [QueryEvent.UpdateCount] mit der Zahl der geladenen Zeilen oder [QueryEvent.Failure]. Die Funktion wirft für
     * keinen Fehler der Anfrage; mehrere Nachrichten sind keine Transaktion.
     */
    public suspend fun load(target: BulkTarget, rows: BulkRows): QueryEvent
}

/** Das Ziel eines Massenladens: Schema und Name der Tabelle, wie [BulkLoadingBackend.bulkTarget] sie gesetzt hat. */
public data class BulkTarget(val schema: String, val table: String)

/** Der Ausgang der Erkennung von `INSERT BULK`. */
public sealed interface BulkTargetResult {
    /** Das Ziel steht fest; die nächsten Nachrichten 0x07 laden in diese Tabelle. */
    public data class Ready(val target: BulkTarget) : BulkTargetResult

    /**
     * Die Anweisung endet mit dem Fehler [code], ohne ein Ziel zu setzen. [code] ist einer dieser Namen, wie bei
     * [QueryEvent.Failure.code]: `TDS_BACKEND_ERROR`, `TDS_UNSUPPORTED_SQL`, `TDS_UNSUPPORTED_TYPE`,
     * `TDS_SIM_UNKNOWN_OBJECT`, `TDS_SIM_INVALID_VALUE`, `TDS_SIM_KEY_VIOLATION`, `TDS_SIM_OBJECT_EXISTS`,
     * `TDS_SIM_COLUMN_EXISTS`, `TDS_SIM_COLUMN_HAS_DEFAULT`, `TDS_SIM_OBJECT_NOT_FOUND`, `TDS_SIM_NOT_A_CONSTRAINT`,
     * `TDS_SIM_CONSTRAINT_OTHER_TABLE`, `TDS_SIM_NOT_AN_INDEX`, `TDS_SIM_INDEX_OF_CONSTRAINT`,
     * `TDS_SIM_STRING_TRUNCATED`, `TDS_TIMEOUT`; ein anderer Name endet mit `TDS_INTERNAL_ERROR` (50007) und schließt
     * die Verbindung. [message] erreicht den Client nicht.
     */
    public data class Rejected(val code: String, val message: String) : BulkTargetResult
}

/** Eine Spalte der Zieltabelle für die Probe: ihr Ergebnistyp und, für Text, die Länge in Zeichen (1 bis 4000). */
public data class BulkProbeColumn(val column: ResultColumn, val maxChars: Int)

/** Der Ausgang der Erkennung der Probe der Zieltabelle. */
public sealed interface BulkProbeResult {
    /** Die Tabelle ist beschrieben; die Antwort der Probe trägt ihre [columns] in der Reihenfolge der Beschreibung. */
    public data class Ready(val columns: List<BulkProbeColumn>) : BulkProbeResult

    /** Die Anweisung endet mit dem Fehler [code]; [code] und [message] gelten wie bei [BulkTargetResult.Rejected]. */
    public data class Rejected(val code: String, val message: String) : BulkProbeResult
}

/**
 * Der Inhalt einer Nachricht 0x07: die Spalten aus COLMETADATA (Name, Typ nach der Zuordnung des Kerns, nullbar) und
 * die Zeilen in der Reihenfolge der Spalten. Die Werte tragen die JVM-Klassen von [QueryEvent.Row] je Spaltenart
 * (`ADR-0013`); ein Text, den der Client für eine Datums- oder Zeitspalte als `VARCHAR` sendet, bleibt ein `String`.
 */
public class BulkRows(public val columns: List<ResultColumn>, public val rows: List<List<Any?>>) {
    override fun toString(): String = "BulkRows(columns=${columns.size}, rows=${rows.size})"
}
