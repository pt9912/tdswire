package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent
import kotlinx.coroutines.flow.Flow

/**
 * Die laufende Operation eines Backend-Aufrufs: ein asynchroner Strom von Ereignissen. Ein
 * Backend mit kleinem Ergebnis darf ihn aus einer fertigen Liste erzeugen; die Grenze für „klein“
 * ist offen (ADR-0006). Offen (ADR-0006): die Behandlung von `OutOfMemoryError`, etwa bei einem
 * Backend, das sein Ergebnis materialisiert.
 */
interface QueryExecution {
    /**
     * Der Ergebnisstrom. Pflicht (`contracts/query-backend.md`): `ResultStart` kommt vor den Zeilen
     * seines Ergebnisses. Die Prüfung dieser Reihenfolge gehört nicht zum Port, sondern dem Response Sequencer.
     *
     * Festlegung ohne Messung: der Strom wird genau einmal gesammelt. Ob ein zweites Sammeln den
     * Strom neu beginnt (kalt) oder fortsetzt, legt der Port nicht fest; kein Aufrufer und keine
     * Prüfung darf davon abhängen.
     */
    val events: Flow<QueryEvent>

    /** Schließt die Zeilenquelle und die Ressourcen der Operation. */
    suspend fun close()
}
