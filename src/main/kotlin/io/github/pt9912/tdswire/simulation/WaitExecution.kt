package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.QueryExecution
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.selects.select
import kotlin.time.Duration

/**
 * Form Z (`SPEC-036`, `ADR-0058` Entscheidung 5): wartet [delay] mit [pause] ab, suspendierend und ohne einen Thread zu
 * blockieren, und endet dann mit dem Änderungszähler 0 ohne Art (wie die Antwort auf `SET`; Setzung, am Server nicht
 * gemessen). Vor dem Ende der Wartezeit sendet die Ausführung kein Ereignis: der Befehl ist ein wartender Befehl im
 * Sinn von `SPEC-029`. [stop] (der Cancel) beendet die Wartezeit sofort und ohne Ereignis; ebenso der Abbruch des
 * Sammlers. Die Ausführung ruft weder Zeilenquelle noch Callback. Eine Wartezeit von null ruft [pause] nicht.
 */
internal class WaitExecution(
    private val delay: Duration,
    private val pause: suspend (Duration) -> Unit,
    private val onEnd: (WaitExecution) -> Unit,
) : QueryExecution,
    Stoppable {
    private val stopped = CompletableDeferred<Unit>()

    override val events: Flow<QueryEvent> =
        flow { if (waited()) emit(QueryEvent.UpdateCount(0)) }.onCompletion { onEnd(this@WaitExecution) }

    override suspend fun close() {
        stop()
        onEnd(this)
    }

    override fun stop() {
        stopped.complete(Unit)
    }

    /** Wahr, wenn die Wartezeit ablief; falsch, wenn [stop] sie vorher beendete. */
    private suspend fun waited(): Boolean = when {
        stopped.isCompleted -> false

        delay <= Duration.ZERO -> true

        else -> coroutineScope {
            val waiting = async { pause(delay) }
            val ran = select {
                waiting.onAwait { true }
                stopped.onAwait { false }
            }
            waiting.cancel()
            ran
        }
    }
}
