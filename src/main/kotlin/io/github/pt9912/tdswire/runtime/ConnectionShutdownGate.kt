package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.protocol.dispatch.ShutdownGate
import io.github.pt9912.tdswire.transport.listener.Connection

/**
 * Setzt das [ShutdownGate] der Anfrageschleife auf die [Connection] des Listeners um (`ARC-008`): der Beginn des
 * geordneten Beendens, die Meldung „Anfrage läuft“ und der Abbruch nach Ablauf von `SHUTDOWN_GRACE` laufen über die
 * Verbindung.
 */
internal class ConnectionShutdownGate(private val connection: Connection) : ShutdownGate {
    override val begun: Boolean get() = connection.shutdownBegun

    override fun requestRunning(running: Boolean) = connection.requestRunning(running)

    override fun onGraceExpired(abort: suspend () -> Unit) = connection.onShutdownGraceExpired(abort)
}
