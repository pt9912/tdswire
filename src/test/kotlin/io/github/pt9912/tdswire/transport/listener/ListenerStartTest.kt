package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.KeyVariants
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.tls.MaterialResult
import io.github.pt9912.tdswire.transport.tls.TlsMaterial
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import io.github.pt9912.tdswire.transport.tls.TlsPart
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Start des Listeners: Startprobe vor dem Binden, belegter Port, keine Reste nach einem Fehlschlag. */
class ListenerStartTest : ListenerTestBase() {
    private val handler = ConnectionHandler { }

    private fun materialOf(store: ByteArray): TlsMaterial {
        val loaded = TlsMaterial.load(store, KeyVariants.password())
        assertIs<MaterialResult.Loaded>(loaded)
        return loaded.material
    }

    private companion object {
        const val FAILED_STARTS = 300
    }

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }

    /**
     * Ein belegter Port beendet den Start mit einer Diagnose, die Adresse und Port nennt; danach läuft
     * kein Thread des Listeners, und der belegende Socket blieb unberührt.
     *
     * @spec SPEC-043
     */
    @Test
    fun `occupied port ends the start with address and port and leaves no thread`() = blocking {
        val before = listenerThreads()
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            val port = occupied.localPort
            val result = TdsListener.start(ListenerConfig(LOOPBACK, port), handler, ListenerEvents.NONE)
            assertIs<StartResult.Failed>(result)
            assertEquals(StartFailure.PortInUse(LOOPBACK, port), result.failure)
            assertTrue("$LOOPBACK:$port" in result.failure.message, result.failure.message)
            assertNoNewListenerThreads(before)
            assertTrue(!occupied.isClosed)
        }
    }

    /**
     * Ein Port außerhalb von 0..65535 beendet den Start mit `BindFailed` und dem Ausnahmetyp; danach
     * läuft sofort kein Thread des Listeners.
     *
     * @spec SPEC-043
     */
    @Test
    fun `invalid port ends the start and leaves no thread`() = blocking {
        val before = listenerThreads()
        listOf(70_000, -1).forEach { port ->
            val result = TdsListener.start(ListenerConfig(LOOPBACK, port), handler, ListenerEvents.NONE)
            val failure = StartFailure.BindFailed(LOOPBACK, port, "java.lang.IllegalArgumentException")
            assertEquals(StartResult.Failed(failure), result)
            assertNoNewListenerThreads(before)
        }
    }

    /**
     * Nach jedem fehlgeschlagenen Start läuft zum Zeitpunkt der Rückkehr kein Thread des Listeners;
     * der Test prüft ohne Warten und wiederholt den Start auf einem belegten Port.
     *
     * @spec SPEC-043
     */
    @Test
    fun `a failed start returns only after its threads have ended`() = blocking {
        val before = listenerThreads()
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            repeat(FAILED_STARTS) {
                val config = ListenerConfig(LOOPBACK, occupied.localPort)
                val result = TdsListener.start(config, handler, ListenerEvents.NONE)
                assertIs<StartResult.Failed>(result)
                assertNoNewListenerThreads(before)
            }
        }
    }

    /**
     * Scheitert die Startprobe (hier: abgelaufene Kette), ist der Port nicht gebunden: ein anderer
     * Socket bindet ihn danach; die Diagnose nennt den Teil, und es läuft kein Thread des Listeners.
     *
     * @spec SPEC-043
     */
    @Test
    fun `failed probe leaves the port unbound and no thread`() = blocking {
        val before = listenerThreads()
        val port = freePort()
        val tls = TlsServerConfig(materialOf(KeyVariants.file(TestKeys.EXPIRED)), TlsParameters.SPEC_023)
        val result = TdsListener.start(ListenerConfig(LOOPBACK, port, tls = tls), handler, ListenerEvents.NONE)
        assertIs<StartResult.Failed>(result)
        val failure = result.failure
        assertIs<StartFailure.Tls>(failure)
        assertEquals(TlsPart.CERTIFICATE, failure.diagnosis.part)
        ServerSocket(port, 1, InetAddress.getByName(LOOPBACK)).use { assertEquals(port, it.localPort) }
        assertNoNewListenerThreads(before)
    }

    /**
     * Die Probe läuft vor dem Binden und der Start gelingt mit gültigem Material; der Listener bindet
     * den Port, und nach dem Beenden läuft kein Thread des Listeners.
     *
     * @spec SPEC-043 SPEC-005
     */
    @Test
    fun `valid material starts the listener and shutdown releases the threads`() = blocking {
        val before = listenerThreads()
        val tls = TlsServerConfig(materialOf(KeyVariants.file(TestKeys.VALID)), TlsParameters.SPEC_023)
        val result = track(TdsListener.start(ListenerConfig(LOOPBACK, 0, tls = tls), handler, ListenerEvents.NONE))
        assertIs<StartResult.Started>(result)
        val listener = result.listener
        assertTrue(listener.port > 0)
        assertTrue(listenerThreads().any { it !in before })
        listener.shutdown()
        assertNoNewListenerThreads(before)
        ServerSocket(listener.port, 1, InetAddress.getByName(LOOPBACK)).close()
    }
}
