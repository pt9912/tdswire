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

    @Volatile
    private var lastRefusal = ConnectionEnd.REFUSED_AT_SHUTDOWN

    /**
     * `false`, wenn das Beenden begonnen hat oder die Grenze erreicht ist; die Verbindung ist dann nicht aufgenommen.
     * Den Grund hält [refusal] aus derselben kritischen Sektion fest.
     */
    open fun admit(id: Long, connection: T): Boolean = synchronized(lock) {
        val full = connections.size >= maxConnections
        val admitted = !shuttingDown && !full
        if (admitted) {
            connections[id] = connection
        } else {
            lastRefusal = if (!shuttingDown && full) ConnectionEnd.REFUSED_AT_LIMIT else ConnectionEnd.REFUSED_AT_SHUTDOWN
        }
        admitted
    }

    /**
     * Warum das letzte [admit], das abwies, abgewiesen hat: die Grenze, sonst das Beenden. Der Grund ist der der
     * Entscheidung, nicht der Zustand beim Lesen; der Listener nimmt in einer Coroutine an und ruft beides nacheinander.
     */
    open fun refusal(): ConnectionEnd = lastRefusal

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
