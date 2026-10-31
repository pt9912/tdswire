package io.github.pt9912.tdswire.port

import io.github.pt9912.tdswire.domain.QueryEvent
import kotlinx.coroutines.flow.Flow

/**
 * Die laufende Operation eines Backend-Aufrufs: ein asynchroner Strom von Ereignissen. Ein
 * Backend mit kleinem Ergebnis darf ihn aus einer fertigen Liste erzeugen; der Vertrag setzt keine
 * Grenze für „klein“ (Setzung ohne Messung, ADR-0013): ein Backend, das sein Ergebnis materialisiert,
 * begrenzt es selbst. Ein `OutOfMemoryError` im Anfragepfad (Backend, Encoder, Senke) wird gefangen und
 * als `TDS_INTERNAL_ERROR` beantwortet, danach schließt die Verbindung (ADR-0013).
 */
interface QueryExecution {
    /**
     * Der Ergebnisstrom. Pflicht (`spec/spezifikation.md` `SPEC-022`): `ResultStart` kommt vor den Zeilen
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
