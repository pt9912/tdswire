package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.ConnectionEnd
import io.github.pt9912.tdswire.transport.listener.ListenerEvent
import io.github.pt9912.tdswire.transport.listener.ListenerEvents
import io.github.pt9912.tdswire.transport.listener.eventually
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4

/**
 * Lecktest mit vielen Verbindungen gegen den Serverprozess im Prozess (`TDS-FA-07`, `TDS-QA-02`, `SPEC-029`,
 * `SPEC-031`, offener Punkt aus `ADR-0004`): je 50 Verbindungen brechen mit RST und mit regulärem Schließen ab, während
 * eine Anfrage `RUNNING` (das Backend hat noch kein Ergebnis) oder `STREAMING` (Metadaten und eine Zeile sind
 * geschrieben) ist; 50 brechen im Leerlauf und 50 mitten im Handshake ab. Gezählt werden am Fake-Backend die geöffneten
 * Ströme, die Aufrufe von `close()` und die Cancel je Anfrage, am Listener die Verbindungen und ihre Enden, und
 * unbehandelte Ausnahmen in Threads der Laufzeit (der Standard-Handler der JVM zählt mit; Ausnahmen der Coroutinen
 * einer Verbindung fängt der Scope, ein Fehler des Handlers zählt als Ende `HANDLER_FAILED`). Der Server läuft danach
 * weiter. Das Backend ist ein Fake: der Test belegt die Freigabe von Strom und Sitzung, nicht die Ressourcen eines
 * echten Treibers; die 50 RST sind eine Stichprobe auf einem Host, nicht der Nachweis für jede Plattform.
 */
internal class ConnectionLeakTest : RuntimeTestBase() {
    private val uncaught = ConcurrentLinkedQueue<String>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val accepted = AtomicInteger()
    private val ends = CopyOnWriteArrayList<ListenerEvent.Closed>()
    private val events = ListenerEvents {
        if (it is ListenerEvent.Accepted) accepted.incrementAndGet()
        if (it is ListenerEvent.Closed) ends += it
    }

    init {
        Thread.setDefaultUncaughtExceptionHandler { thread, error -> uncaught += "${thread.name}: $error" }
    }

    @AfterTest
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    /** Zählt geöffnete Ströme, ihr `close()` und jedes `cancel()`; die Ströme laufen, bis sie abgebrochen werden. */
    private class LeakBackend : QueryBackend {
        val opened = AtomicInteger()
        val closed = AtomicInteger()
        val reached = AtomicInteger()
        val cancelled = ConcurrentLinkedQueue<String>()

        override suspend fun execute(request: QueryRequest): QueryExecution {
            opened.incrementAndGet()
            return object : QueryExecution {
                override val events: Flow<QueryEvent> = flow {
                    when (request.sql) {
                        "stream" -> {
                            emit(resultStart())
                            emit(QueryEvent.Row(listOf(7)))
                        }

                        "ok" -> {
                            emit(resultStart())
                            emit(QueryEvent.Row(listOf(7)))
                            emit(QueryEvent.ResultEnd(1))
                            return@flow
                        }
                    }
                    reached.incrementAndGet()
                    awaitCancellation()
                }

                override suspend fun close() {
                    closed.incrementAndGet()
                }
            }
        }

        override suspend fun cancel(sessionId: String, requestId: String) {
            cancelled += "$sessionId/$requestId"
        }
    }

    private fun environment() = loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"))

    private fun prelogin(client: RawClient) {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
    }

    private fun login(client: RawClient) {
        prelogin(client)
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    private fun batch(text: String) = handMessage(BATCH_TYPE, BatchVectors.payload(text), 4000)

    /** Eine Verbindung mit einer Anfrage [text], die das Backend erreicht hat, bevor sie abbricht. */
    private suspend fun abort(port: Int, backend: LeakBackend, text: String, hard: Boolean) {
        val client = RawClient(port)
        login(client)
        val target = backend.reached.get() + 1
        client.send(batch(text))
        eventually("Backend $target erreicht") { backend.reached.get() >= target }
        if (hard) client.reset() else client.close()
    }

    /**
     * 200 Abbrüche in `RUNNING` und `STREAMING` (je 50 mit RST und je 50 mit regulärem Schließen), 50 im Leerlauf
     * und 50 mitten im Handshake: jeder geöffnete Strom ist geschlossen, jede Anfrage hat genau ein `cancel()`,
     * jede Verbindung ist im Listener beendet und keine mit einem Handlerfehler, keine unbehandelte Ausnahme in
     * einem Thread, und der Server beantwortet danach eine neue Verbindung.
     *
     * @spec SPEC-029
     * @spec SPEC-031
     * @spec TDS-FA-07
     * @spec TDS-QA-02
     */
    @Test
    fun `viele Abbrueche in jedem Zustand lassen weder Strom noch Verbindung noch Thread zurueck`() = blocking {
        val backend = LeakBackend()
        val wiring = Wiring(telemetry = sink, events = events, requests = RequestSource(backend))
        val server = running(environment(), wiring)
        for (hard in listOf(true, false)) {
            repeat(ROUNDS) { abort(server.port, backend, "run", hard) }
            repeat(ROUNDS) { abort(server.port, backend, "stream", hard) }
        }
        repeat(ROUNDS) {
            val client = RawClient(server.port)
            login(client)
            client.reset()
        }
        repeat(ROUNDS) {
            val client = RawClient(server.port)
            prelogin(client)
            client.reset()
        }
        val requests = 4 * ROUNDS
        eventually("alle Ströme geschlossen") { backend.closed.get() == requests }
        eventually("alle Verbindungen beendet") { ends.size == accepted.get() && accepted.get() == 6 * ROUNDS }
        assertEquals(requests, backend.opened.get())
        assertEquals(requests, backend.cancelled.size, "ein cancel() je Anfrage")
        assertEquals(requests, backend.cancelled.toSet().size, "jeder Cancel gilt einer anderen Anfrage")
        val failed = ends.filter { it.end == ConnectionEnd.HANDLER_FAILED }
        assertTrue(failed.isEmpty(), "Handlerfehler: $failed")
        assertEquals(emptyList(), uncaught.toList(), "unbehandelte Ausnahmen in Threads")

        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("ok"))
            assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
        }
        eventually("der Server hat weiter bedient") { backend.closed.get() == requests + 1 }
        assertEquals(requests, backend.cancelled.size, "die beendete Anfrage wird nicht abgebrochen")
    }

    private companion object {
        const val ROUNDS = 50
    }
}
