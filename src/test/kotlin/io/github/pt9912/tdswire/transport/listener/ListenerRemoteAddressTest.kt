package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.blocking
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

/** Die Verbindung nennt dem Handler die Adresse der Gegenstelle (`ADR-0016`). */
internal class ListenerRemoteAddressTest : ListenerTestBase() {
    /**
     * Orakel: eine Verbindung von `127.0.0.1` zeigt dem Handler `127.0.0.1` als Adresse der Gegenstelle, am echten
     * Socket des Listeners.
     *
     * @spec SPEC-005
     */
    @Test
    fun `Der Handler sieht die Adresse der Gegenstelle`() = blocking {
        val handler = ScriptedHandler()
        val listener = startOnLoopback(handler, RecordingEvents())
        val client = connectTo(listener.port)
        eventually("Verbindung beim Handler") { handler.connections.isNotEmpty() }
        assertEquals(InetAddress.getByName(LOOPBACK), handler.connections.single().remoteAddress)
        client.close()
    }
}
