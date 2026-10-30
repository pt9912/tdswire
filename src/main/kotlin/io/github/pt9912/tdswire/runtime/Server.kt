package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.transport.listener.TdsListener

/** Der laufende Server: Listener und Log. [stop] schließt den Listener und alle Verbindungen. */
internal class Server(private val listener: TdsListener, private val log: ServerLog) {
    /** Der gebundene Port. */
    val port: Int get() = listener.port

    suspend fun stop() {
        listener.shutdown()
        log.line("gestoppt")
    }
}
