package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn

/**
 * Die Fähigkeit eines Backends, das Massenladen (Paket `BULK_LOAD`, `INSERT BULK`) anzunehmen (`ADR-0033` Nr. 5,
 * `SPEC-035`). Sie ist kein Teil von [QueryBackend] und nicht öffentlich: ein Backend des Integrators, das sie nicht
 * trägt, bekommt weder den Text `INSERT BULK` als Besonderheit noch ein Paket 0x07 zu sehen; das Paket ergibt dort
 * einen Fehler. Das Simulations-Backend trägt sie.
 *
 * Ablauf: der Protokollkern fragt bei jedem SQL-Text mit [bulkTarget], ob er ein `INSERT BULK` ist, und merkt sich
 * das Ziel für die folgenden Nachrichten vom Typ 0x07 derselben Sitzung (der Sitzungszustand liegt beim Kern, das
 * Backend hält keinen); jede dieser Nachrichten dekodiert er zu [BulkRows] und gibt sie mit [load] weiter.
 */
internal interface BulkLoadingBackend {
    /**
     * Erkennt `INSERT BULK`: `null`, wenn der Text keines ist (er geht den üblichen Weg über
     * [QueryBackend.execute]); sonst das Ziel oder die Ablehnung. Die Funktion fragt höchstens den Katalog-Anbieter und
     * ruft keinen Callback für Zeilen.
     */
    fun bulkTarget(sql: String): BulkTargetResult?

    /**
     * Erkennt die Probe der Zieltabelle, die `SqlBulkCopy` (Referenzclient) vor `INSERT BULK` stellt (`SPEC-036`,
     * Form B): `null`, wenn der Text keine ist; sonst die Spalten der Tabelle, aus denen der Protokollkern die Antwort
     * baut, oder die Ablehnung. Die Funktion fragt höchstens den Katalog-Anbieter und ruft keinen Callback für Zeilen.
     */
    fun bulkProbe(sql: String): BulkProbeResult?

    /**
     * Gibt die Zeilen einer Nachricht an das Ziel weiter und liefert das Ereignis, mit dem die Antwort endet:
     * [QueryEvent.UpdateCount] mit der Zahl der geladenen Zeilen oder [QueryEvent.Failure]. Die Funktion wirft für
     * keinen Fehler der Anfrage; mehrere Zeilen sind keine Transaktion, wenn das Ziel keinen Stapel-Callback hat.
     */
    suspend fun load(target: BulkTarget, rows: BulkRows): QueryEvent
}

/** Das Ziel eines Massenladens: Schema und Name der Tabelle in der Schreibweise der Beschreibung. */
internal data class BulkTarget(val schema: String, val table: String)

/** Der Ausgang der Erkennung von `INSERT BULK`. */
internal sealed interface BulkTargetResult {
    /** Das Ziel steht fest; die nächsten Nachrichten 0x07 laden in diese Tabelle. */
    data class Ready(val target: BulkTarget) : BulkTargetResult

    /** Die Anweisung endet mit dem Fehler [code] (ein Name aus `ErrorCode`), ohne ein Ziel zu setzen. */
    data class Rejected(val code: String, val message: String) : BulkTargetResult
}

/** Eine Spalte der Zieltabelle für die Probe: ihr Ergebnistyp und, für Text, die Länge in Zeichen (1 bis 4000). */
internal data class BulkProbeColumn(val column: ResultColumn, val maxChars: Int)

/** Der Ausgang der Erkennung der Probe der Zieltabelle. */
internal sealed interface BulkProbeResult {
    /** Die Tabelle ist beschrieben; die Antwort der Probe trägt ihre [columns] in der Reihenfolge der Beschreibung. */
    data class Ready(val columns: List<BulkProbeColumn>) : BulkProbeResult

    /** Die Anweisung endet mit dem Fehler [code] (ein Name aus `ErrorCode`). */
    data class Rejected(val code: String, val message: String) : BulkProbeResult
}

/**
 * Der Inhalt einer Nachricht 0x07: die Spalten aus COLMETADATA (Name, Typ nach der Zuordnung des Kerns, nullbar) und
 * die Zeilen in der Reihenfolge der Spalten. Die Werte tragen die JVM-Klassen von `ValueMapping`; ein Text, den der
 * Client für eine Datums- oder Zeitspalte als `VARCHAR` sendet, bleibt ein `String`.
 */
internal class BulkRows(val columns: List<ResultColumn>, val rows: List<List<Any?>>) {
    override fun toString(): String = "BulkRows(columns=${columns.size}, rows=${rows.size})"
}
