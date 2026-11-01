package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.Token
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration

/**
 * Eine gesteuerte Uhr: [pause] wartet, bis der Test die Frist mit [fire] auslöst. Es läuft keine echte Zeit; welche
 * Frist wann abläuft, bestimmt allein der Test (`SPEC-009`, `SPEC-033`). [started] nennt jede angeforderte Wartezeit.
 */
internal class SteeredPause {
    private class Waiter(val duration: Duration, val gate: CompletableDeferred<Unit>)

    private val waiters = mutableListOf<Waiter>()

    /** Jede Wartezeit, die angefordert wurde, in der Reihenfolge der Anforderung. */
    val started = mutableListOf<Duration>()

    suspend fun pause(duration: Duration) {
        val waiter = Waiter(duration, CompletableDeferred())
        started += duration
        waiters += waiter
        try {
            waiter.gate.await()
        } finally {
            waiters -= waiter
        }
    }

    fun isWaiting(duration: Duration): Boolean = waiters.any { it.duration == duration }

    /** Wartet (ohne Zeit, durch Abgeben der Ausführung), bis die Wartezeit [duration] angefordert ist. */
    suspend fun awaitWaiting(duration: Duration) {
        while (!isWaiting(duration)) yield()
    }

    /** Löst die Wartezeit [duration] aus; sie muss angefordert sein. */
    fun fire(duration: Duration) {
        waiters.first { it.duration == duration }.gate.complete(Unit)
    }
}

/**
 * Führt [body] aus und öffnet [gate] in jedem Ausgang. Ein Fake-Backend, das den Abbruch ignoriert, hält sonst die
 * Schleife fest, wenn ein Test vorzeitig endet; so bleibt ein roter Test rot und hängt nicht.
 */
internal suspend fun withGate(gate: CompletableDeferred<Unit>, body: suspend () -> Unit) {
    try {
        body()
    } finally {
        gate.complete(Unit)
    }
}

/** Gibt die Ausführung ab, bis [done] gilt. */
internal suspend fun awaitUntil(done: () -> Boolean) {
    while (!done()) yield()
}

/**
 * Ein Backend, dessen n-ter [execute]-Aufruf den Strom der n-ten Fabrik von [flows] liefert (nach der letzten bleibt
 * es bei ihr). Es zählt die Anfragen, die Aufrufe von [cancel] und von [QueryExecution.close]. [collecting] ist
 * gesetzt, sobald ein Strom [begin] gerufen hat.
 */
internal class FlowBackend(private vararg val flows: (FlowBackend) -> Flow<QueryEvent>) : QueryBackend {
    val requests = mutableListOf<QueryRequest>()
    val cancelled = mutableListOf<Pair<String, String>>()
    var closeCalls = 0
        private set

    /** Gesetzt, sobald ein Strom gesammelt wird. */
    val collecting = CompletableDeferred<Unit>()

    /** Gesetzt, sobald die Ausführung geschlossen wurde. */
    val closed = CompletableDeferred<Unit>()

    /** Der Anfang eines Stroms: meldet [collecting]. */
    fun begin() {
        collecting.complete(Unit)
    }

    override suspend fun execute(request: QueryRequest): QueryExecution {
        requests += request
        val flow = flows[minOf(requests.size, flows.size) - 1](this)
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = flow

            override suspend fun close() {
                closeCalls++
                closed.complete(Unit)
            }
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) {
        cancelled += sessionId to requestId
    }
}

/** Ein Strom, der erst die [first] Ereignisse liefert, dann [FlowBackend.begin] meldet und bis [gate] suspendiert. */
internal fun suspendedAfter(
    gate: CompletableDeferred<Unit>,
    vararg first: QueryEvent,
): (FlowBackend) -> Flow<QueryEvent> = { backend ->
    flow {
        first.forEach { emit(it) }
        backend.begin()
        gate.await()
    }
}

/**
 * Ein Strom, der den Abbruch nicht beachtet: nach den [first] Ereignissen wartet er in einem nicht abbrechbaren
 * Bereich auf [gate]. Er steht für ein Backend, das `cancel()` ignoriert.
 */
internal fun ignoringCancel(
    gate: CompletableDeferred<Unit>,
    vararg first: QueryEvent,
): (FlowBackend) -> Flow<QueryEvent> = { backend ->
    flow {
        first.forEach { emit(it) }
        backend.begin()
        withContext(NonCancellable) { gate.await() }
    }
}

/**
 * Ein Strom, der einen blockierenden Aufruf auf einem Thread des IO-Pools wartet (`ADR-0013`: ein blockierender
 * Treiber liegt hinter einem Pool und wird unterbrechbar gerufen); der Abbruch unterbricht den Thread.
 */
internal fun blockingAfter(
    latch: CountDownLatch,
    vararg first: QueryEvent,
    pool: CoroutineDispatcher = Dispatchers.IO,
): (FlowBackend) -> Flow<QueryEvent> = { backend ->
    flow {
        first.forEach { emit(it) }
        backend.begin()
        runInterruptible(pool) { latch.await() }
    }
}

/** Ein Strom, der die [events] liefert (für die Anfrage, die nach dem Abbruch folgt). */
internal fun delivering(vararg events: QueryEvent): (FlowBackend) -> Flow<QueryEvent> = { flowOf(*events) }

/**
 * Eine Senke, die nach dem Token, auf das [after] zutrifft, [onAccepted] ausführt, bevor sie zurückkehrt: so
 * geschieht der Eingriff zwischen zwei Schritten des Sequencers, ohne Uhr.
 */
internal fun interposing(after: (Int, Token) -> Boolean, onAccepted: suspend () -> Unit): (TokenSink) -> TokenSink =
    { inner ->
        var index = 0
        TokenSink { token ->
            val accepted = inner.accept(token)
            if (after(++index, token)) onAccepted()
            accepted
        }
    }

/** Trifft auf das abschließende DONE ohne Fehlerstatus und ohne `MORE`. */
internal fun isFinalDone(token: Token): Boolean = token is DoneToken && token.status and DoneStatus.MORE == 0

/** Gibt die Ausführung ab, bis die Sitzung [state] hat. */
internal suspend fun HandshakeOutcome.Ready.awaitState(state: ConnectionState) {
    awaitUntil { live.context.state == state }
}
