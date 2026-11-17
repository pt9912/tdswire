package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.port.BulkProbeResult
import io.github.pt9912.tdswire.port.BulkTarget
import io.github.pt9912.tdswire.port.BulkTargetResult

/**
 * Die Erkennung der Texte des Massenladens für ein eigenes Backend (`ADR-0052` Entscheidung 3: sie liegt beim Autor
 * des Backends). Der Autor sieht nur die öffentliche Fläche; die Erkennung der Bibliothek für das Simulations-Backend
 * (`BulkCopyForms`) ist `internal` und steht hier nicht zur Verfügung. Die Regeln sind kürzer als der Wortlaut von
 * `SPEC-036` Form B und für den Referenzclient (`SqlBulkCopy`) gedacht; [tables] nennt die Zieltabellen mit ihren
 * Spalten (Schlüssel ohne Beachtung der Schreibweise):
 *
 * - `INSERT BULK`: der Text beginnt (nach Leerraum, ohne Beachtung der Schreibweise) mit `insert bulk`, gefolgt
 *   vom Namen `[<Tabelle>]` oder `[<Schema>].[<Tabelle>]`; ohne Schema gilt `dbo`. Eine andere Tabelle ist
 *   `TDS_SIM_UNKNOWN_OBJECT`.
 * - Probe der Zieltabelle: der Text beginnt mit `SELECT @@TRANCOUNT` und nennt `OBJECT_ID('[<Schema>].[<Tabelle>]')`
 *   oder `OBJECT_ID('[<Tabelle>]')`; der Rest des Wortlauts bleibt ungeprüft. Eine andere Tabelle ist
 *   `TDS_SIM_UNKNOWN_OBJECT`.
 *
 * Nicht erkannt: Namen mit `]` oder `'`, ein Name mit drei Teilen (Datenbank, Schema, Tabelle).
 */
internal class BulkErkennung(tables: Map<String, List<ResultColumn>>) {
    private val tables = tables.mapKeys { it.key.lowercase() }

    fun target(sql: String): BulkTargetResult? {
        val found = INSERT_BULK.find(sql) ?: return null
        val name = found.groupValues[NAME_GROUP]
        return if (name.lowercase() in tables) {
            BulkTargetResult.Ready(BulkTarget(found.groupValues[SCHEMA_GROUP].ifEmpty { DEFAULT_SCHEMA }, name))
        } else {
            BulkTargetResult.Rejected(UNKNOWN_OBJECT, "Tabelle nicht beschrieben")
        }
    }

    fun probe(sql: String): BulkProbeResult? {
        val found = if (sql.trimStart().startsWith(PROBE_START)) OBJECT_ID.find(sql) else null
        val columns = found?.let { tables[it.groupValues[NAME_GROUP].lowercase()] }
        return when {
            found == null -> null
            columns == null -> BulkProbeResult.Rejected(UNKNOWN_OBJECT, "Tabelle nicht beschrieben")
            else -> BulkProbeResult.Ready(columns.map { BulkProbeColumn(it, it.maxLength) })
        }
    }

    private companion object {
        const val PROBE_START = "SELECT @@TRANCOUNT"
        const val UNKNOWN_OBJECT = "TDS_SIM_UNKNOWN_OBJECT"
        const val DEFAULT_SCHEMA = "dbo"
        const val SCHEMA_GROUP = 1
        const val NAME_GROUP = 2
        val INSERT_BULK = Regex("""^\s*insert\s+bulk\s+(?:\[([^\]']+)]\.)?\[([^\]']+)]""", RegexOption.IGNORE_CASE)
        val OBJECT_ID = Regex("""OBJECT_ID\('(?:\[([^\]']+)]\.)?\[([^\]']+)]'\)""")
    }
}
