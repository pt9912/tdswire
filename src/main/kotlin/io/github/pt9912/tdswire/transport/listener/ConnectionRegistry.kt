package io.github.pt9912.tdswire.transport.listener

/**
 * Die offenen Verbindungen eines Listeners, die Grenze [maxConnections] (`MAX_CONNECTIONS`, `SPEC-034`) und der
 * Beginn des geordneten Beendens (`SPEC-044`). Die Aufnahme einer Verbindung und der Beginn des Beendens sind
 * gegeneinander gesperrt: eine Verbindung ist entweder vor dem Beginn aufgenommen und im Schnappschuss von
 * [beginShutdown] oder wird abgewiesen. Dieselbe Sperre zählt die Grenze: höchstens [maxConnections] Verbindungen
 * sind zugleich aufgenommen, die nächste wird abgewiesen, bis eine [release] sie freigibt.
 */
internal open class ConnectionRegistry<T>(private val maxConnections: Int = Int.MAX_VALUE) {
    private val lock = Any()
    private val connections = LinkedHashMap<Long, T>()

    @Volatile
    var shuttingDown = false
        private set

    val size: Int get() = synchronized(lock) { connections.size }

    /** `false`, wenn das Beenden begonnen hat oder die Grenze erreicht ist; die Verbindung ist nicht aufgenommen. */
    open fun admit(id: Long, connection: T): Boolean = synchronized(lock) {
        val admitted = !shuttingDown && connections.size < maxConnections
        if (admitted) connections[id] = connection
        admitted
    }

    /** Warum [admit] eine Verbindung abgewiesen hat: die Grenze, sonst das Beenden. */
    open fun refusal(): ConnectionEnd = synchronized(lock) {
        if (!shuttingDown && connections.size >= maxConnections) {
            ConnectionEnd.REFUSED_AT_LIMIT
        } else {
            ConnectionEnd.REFUSED_AT_SHUTDOWN
        }
    }

    fun release(id: Long) {
        synchronized(lock) { connections.remove(id) }
    }

    /** Beginnt das Beenden und liefert die offenen Verbindungen; `null`, wenn es schon begonnen hat. */
    fun beginShutdown(): List<T>? = synchronized(lock) {
        if (shuttingDown) {
            null
        } else {
            shuttingDown = true
            connections.values.toList()
        }
    }
}
