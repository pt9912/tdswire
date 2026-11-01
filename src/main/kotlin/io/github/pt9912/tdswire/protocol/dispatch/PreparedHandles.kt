package io.github.pt9912.tdswire.protocol.dispatch

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Die Prepared-Handles einer Sitzung (`TDS-FA-04.a` Nr. 5, `SPEC-030`, `SPEC-001`): Handle-Wert und gemerkter
 * SQL-Text. Eine Tabelle gehört genau einer Sitzung; zwei Sitzungen halten zwei Tabellen und sehen die Handles
 * der jeweils anderen nicht. Jeder Zugriff läuft unter dem eigenen `Mutex` der Tabelle; das ist nicht der Mutex des
 * Sitzungsrahmens (`HandshakeSession`), der Zustandsübergänge und das Schreiben der Antwort schützt.
 *
 * Festlegungen ohne Messung: Eine Sitzung hält höchstens [MAX_HANDLES] Handles gleichzeitig (Setzung,
 * Voreinstellung, nicht konfigurierbar). Die Handle-Werte zählen je Sitzung ab 1 aufwärts und werden nicht
 * wiederverwendet; `-1`, der Wert des Clients vor dem ersten Prepare, wird nie vergeben. Gehalten wird nur der
 * Text, nicht die Deklarationsliste; seine Größe ist nicht begrenzt. Ein Handle endet mit [clear] am
 * Sitzungsende oder mit [release] (ein `sp_prepexec`, dessen Antwort nicht vollständig wurde).
 */
internal class PreparedHandles {
    private val lock = Mutex()
    private val statements = HashMap<Int, String>()
    private var last = 0

    /** Das neue Handle für [sql]; `null`, wenn die Sitzung schon [MAX_HANDLES] Handles hält. */
    suspend fun register(sql: String): Int? = lock.withLock {
        if (statements.size >= MAX_HANDLES) return@withLock null
        last++
        statements[last] = sql
        last
    }

    /** Der gemerkte Text zu [handle]; `null` für ein Handle, das die Sitzung nicht hält. */
    suspend fun sql(handle: Int): String? = lock.withLock { statements[handle] }

    /** Entfernt [handle]; der Wert wird nicht wieder vergeben. */
    suspend fun release(handle: Int) {
        lock.withLock { statements.remove(handle) }
    }

    /** Die Zahl der gehaltenen Handles. */
    suspend fun size(): Int = lock.withLock { statements.size }

    /** Verwirft alle Handles; das Sitzungsende. */
    suspend fun clear() {
        lock.withLock { statements.clear() }
    }

    companion object {
        /** Der Grenzwert je Sitzung: Setzung, Voreinstellung, nicht gemessen. */
        const val MAX_HANDLES = 1000
    }
}
