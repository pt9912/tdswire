package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.MEMORY_CAPACITY
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.TlsSetup
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.peerFor
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Der TLS-Kanal nennt die Adresse der Gegenstelle seines inneren Kanals (`ADR-0016`). */
class TlsRemoteAddressTest {
    private class Addressed(inner: SuspendingChannel, override val remoteAddress: InetAddress) :
        SuspendingChannel by inner

    /**
     * Orakel: nach einem Handschlag über einen Kanal mit der Adresse `10.1.2.3` nennt der TLS-Kanal diese Adresse.
     * Gegenstelle ist eine JDK-Engine über Speicher-Kanäle.
     */
    @Test
    fun `Der TLS-Kanal gibt die Adresse des inneren Kanals weiter`() = blocking {
        val address = InetAddress.getByAddress(byteArrayOf(10, 1, 2, 3))
        val setup = TlsSetup(TlsPath.ENCAPSULATED_IN_TDS)
        val accepted = coroutineScope {
            val (serverEnd, peerEnd) = MemoryChannel.pair(MEMORY_CAPACITY)
            val peer = peerFor(setup, peerEnd)
            val parameters = TlsParameters(setup.protocols, setup.suites)
            val server = async {
                val context = TestKeys.serverContext(setup.serverKeys)
                TlsChannel.accept(Addressed(serverEnd, address), context, setup.path, parameters)
            }
            val failure = async { peer.handshake() }
            assertEquals(null, failure.await())
            server.await()
        }
        assertEquals(address, assertIs<TlsResult.Ok<TlsChannel>>(accepted).value.remoteAddress)
    }
}
