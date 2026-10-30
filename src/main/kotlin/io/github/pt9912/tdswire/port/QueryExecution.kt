package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent
import kotlinx.coroutines.flow.Flow

/**
 * Die laufende Operation eines Backend-Aufrufs: ein asynchroner Strom von Ereignissen. Ein
 * Backend mit kleinem Ergebnis darf ihn aus einer fertigen Liste erzeugen; die Grenze für „klein“
 * ist offen (ADR-0006).
 */
interface QueryExecution {
    /**
     * Der Ergebnisstrom. Pflicht (`contracts/query-backend.md`): `ResultStart` kommt vor den Zeilen
     * seines Ergebnisses. Die Prüfung dieser Reihenfolge gehört nicht zum Port (offen, ADR-0006).
     */
    val events: Flow<QueryEvent>

    /** Schließt die Zeilenquelle und die Ressourcen der Operation. */
    suspend fun close()
}
