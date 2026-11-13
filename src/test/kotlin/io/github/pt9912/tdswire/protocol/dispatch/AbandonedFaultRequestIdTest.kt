package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorLog
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Der späte Fehler eines Backends, das den Abbruch ignoriert (`SPEC-033`, `TDS-FA-09.a`): die Schleife gibt die Anfrage
 * nach `CANCEL_TIMEOUT` auf (`abandoned`) und schließt die Verbindung; kehrt das Backend danach mit einer Ausnahme
 * zurück, gehört das Ereignis `REQUEST_FAILED` weiter zu dieser Anfrage. Die Zeit steuert der Test (`SteeredPause`),
 * das Zurückkehren des Backends ein Latch.
 */
internal class AbandonedFaultRequestIdTest {
    /**
     * Das Backend ignoriert ATTENTION; nach der gesteuerten Frist ist die Anfrage aufgegeben und die Schleife zu
     * Ende. Erst danach gibt der Test das Backend frei, das mit einer Ausnahme zurückkehrt. Der Log-Eintrag des späten
     * Fehlers ist `TDS_INTERNAL_ERROR` und trägt die Kennung der Anfrage (`r1`). Wächter: eine Kennung, die mit dem
     * Ende der Anfrage gelöscht wird (Eintrag mit `null`); ein zweiter Eintrag.
     *
     * @spec SPEC-033
     * @spec SPEC-048
     * @spec SPEC-018
     */
    @Test
    fun `Der spaete Fehler nach abandoned traegt die Kennung der Anfrage`() = blocking {
        val pause = SteeredPause()
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend({ self ->
            flow {
                self.begin()
                withContext(NonCancellable) { gate.await() }
                error("spaet")
            }
        })
        val entries = CopyOnWriteArrayList<ErrorLogEntry>()
        val times = RequestTimes(30.seconds, 5.seconds, now = { FIXED_NOW }, pause = pause::pause)
        withGate(gate) {
            val session = Session.login(BatchVectors.message("stuck"))
            val errors = ErrorMapper(ErrorLog { entries += it })
            val serving = launch { session.loop(backend, errors, times = times).run() }
            backend.collecting.await()
            session.channel.feed(BatchVectors.attention())
            pause.awaitWaiting(5.seconds)
            pause.fire(5.seconds)
            serving.join()
            assertEquals(emptyList(), entries.toList(), "vor der Freigabe des Backends kein Fehler")
            gate.complete(Unit)
            awaitUntil { entries.isNotEmpty() }
            val expected = ErrorLogEntry(ErrorCode.TDS_INTERNAL_ERROR, "java.lang.IllegalStateException", "r1")
            assertEquals(listOf(expected), entries.toList())
        }
    }
}
