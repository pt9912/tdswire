package io.github.pt9912.tdswire.transport.listener

/**
 * Die offenen Verbindungen eines Listeners und der Beginn des geordneten Beendens (`SPEC-044`). Die
 * Aufnahme einer Verbindung und der Beginn des Beendens sind gegeneinander gesperrt: eine Verbindung
 * ist entweder vor dem Beginn aufgenommen und im Schnappschuss von [beginShutdown] oder wird
 * abgewiesen.
 */
internal class ConnectionRegistry<T> {
    private val lock = Any()
    private val connections = LinkedHashMap<Long, T>()

    @Volatile
    var shuttingDown = false
        private set

    val size: Int get() = synchronized(lock) { connections.size }

    /** `false`, wenn das Beenden begonnen hat; die Verbindung ist dann nicht aufgenommen. */
    fun admit(id: Long, connection: T): Boolean = synchronized(lock) {
        if (!shuttingDown) connections[id] = connection
        !shuttingDown
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
