package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.blocking
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Das Aufräumen von [ListenerTestBase] beendet die Listener, die ein Test gestartet hat. */
class ListenerTestBaseTest : ListenerTestBase() {
    /**
     * Nach dem Aufräumen nimmt keiner der gestarteten Listener mehr Verbindungen an, und es läuft kein
     * Thread des Listeners. Ob ein fehlschlagender Test ohne das Aufräumen Folgefehler auslöste, zeigt
     * der Test nicht.
     *
     * @spec SPEC-044
     */
    @Test
    fun `the cleanup shuts down every listener the test started`() {
        val before = listenerThreads()
        val ports = mutableListOf<Int>()
        blocking {
            repeat(2) { ports += startOnLoopback(ScriptedHandler(), ListenerEvents.NONE).port }
            connectTo(ports.first()).close()
        }
        shutDownStartedListeners()
        blocking { ports.forEach { port -> assertFailsWith<ExecutionException> { connectTo(port) } } }
        assertNoNewListenerThreads(before)
    }
}
