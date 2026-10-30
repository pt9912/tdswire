package io.github.pt9912.tdswire.transport.listener

import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val YIELDS = 20
private const val AWAIT_MILLIS = 3_000L

/**
 * Warten von `IoGroup.stop` auf das Ende der Threads (`SPEC-043`): ein Thread der Liste, der nicht zur
 * Gruppe gehört und durch den Test gehalten wird, ist der einzige Grund, weshalb `stop` noch wartet.
 * Die Tests nutzen keine Wanduhr für die Aussage: sie lassen die Coroutinen des Tests nur reihum laufen.
 */
class IoGroupTest {
    private class Held {
        val release = CountDownLatch(1)
        val worker = Thread { release.await() }.also {
            it.isDaemon = true
            it.start()
        }
        val group: AsynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(1) { task ->
            Thread(task).also { it.isDaemon = true }
        }
        val io = TdsListener.IoGroup(group, listOf(worker))

        fun letGo() {
            release.countDown()
            worker.join()
        }
    }

    /**
     * Ist die Gruppe beendet, wartet `stop` weiter, solange ein Thread der Liste lebt, und kehrt erst
     * zurück, wenn er endet: `isTerminated` allein sagt nicht, dass der Thread beendet ist.
     *
     * @spec SPEC-043
     */
    @Test
    fun `stop waits for a living thread although the group is terminated`() = blocking {
        val held = Held()
        held.group.shutdownNow()
        withTimeout(AWAIT_MILLIS) { eventually("Gruppe beendet") { held.group.isTerminated } }
        val stop = async { held.io.stop() }
        repeat(YIELDS) { yield() }
        assertTrue(stop.isActive, "stop kehrte zurück, obwohl ein Thread lebt")
        held.letGo()
        withTimeout(AWAIT_MILLIS) { stop.await() }
    }

    /**
     * Wird der Aufruf von `stop` abgebrochen, läuft das Warten zu Ende: der abgebrochene Aufruf ist
     * erst abgeschlossen, wenn der letzte Thread beendet ist.
     *
     * @spec SPEC-043
     */
    @Test
    fun `a cancelled stop still waits until the thread has ended`() = blocking {
        val held = Held()
        val stop = async { held.io.stop() }
        withTimeout(AWAIT_MILLIS) { eventually("stop hat begonnen") { held.group.isShutdown } }
        stop.cancel()
        repeat(YIELDS) { yield() }
        assertFalse(stop.isCompleted, "der abgebrochene Aufruf kehrte zurück, obwohl ein Thread lebt")
        held.letGo()
        withTimeout(AWAIT_MILLIS) { stop.join() }
        assertTrue(stop.isCancelled)
    }
}
