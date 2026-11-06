package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.FlowBackend
import io.github.pt9912.tdswire.protocol.dispatch.delivering
import io.github.pt9912.tdswire.protocol.dispatch.ignoringCancel
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.dispatch.suspendedAfter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.listener.ConnectionEnd
import io.github.pt9912.tdswire.transport.listener.ListenerEvent
import io.github.pt9912.tdswire.transport.listener.ListenerEvents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4
private const val STOP_MILLIS = 15_000L
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Das geordnete Beenden über einen Socket auf `127.0.0.1` mit dem Rohbytes-Client und einem Fake-Backend
 * (`TDS-FA-07`, `SPEC-044`, `SPEC-047`, `SPEC-049`, `ADR-0027` Entscheidung 4 und 2c): `TdsServer.stop` ruft den Ablauf
 * mit `SHUTDOWN_GRACE` aus der Konfiguration. Gemessen wird der Server dieses Repos in echter Zeit (Fristen von einer
 * bis zwei Sekunden, dem kleinsten Wert der Einstellung), nicht der Referenzclient und nicht ein echter SQL Server.
 */
internal class ShutdownWiringTest : RuntimeTestBase() {
    private val rowAnswer =
        listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1).joinToString(" ")

    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
    private val shutdown = ServerMessage(50008, 1, 20, "Server is shutting down.")
    private val ends = CopyOnWriteArrayList<ListenerEvent.Closed>()
    private val events = ListenerEvents { if (it is ListenerEvent.Closed) ends += it }

    private fun environment(vararg more: Pair<String, String>) =
        loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"), *more)

    private fun login(client: RawClient) {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    private fun batch(text: String) = handMessage(BATCH_TYPE, BatchVectors.payload(text), 4000)

    private fun RawClient.reply(text: String): RawMessage {
        val message = assertNotNull(readMessage(), text)
        assertEquals(TABULAR_TYPE, message.type, text)
        return message
    }

    /** Beendet den Server in einem eigenen Thread, damit der Test auf dem Socket blockieren darf. */
    private fun stopInBackground(server: TdsServer): Thread =
        thread(name = "tdswire-test-stop") { runBlocking { server.stop() } }

    private suspend fun serverOf(backend: FlowBackend, vararg more: Pair<String, String>) =
        running(environment(*more), Wiring(telemetry = sink, events = events, requests = RequestSource(backend)))

    /**
     * Eine laufende Anfrage schließt innerhalb der Frist regulär ab: die Antwort ist vollständig, danach endet die
     * Verbindung (Ende `SHUTDOWN`), eine ruhende zweite Verbindung endet sofort, und der Stop kehrt erst nach der
     * laufenden Anfrage zurück. Der Abbruch am Backend bleibt aus.
     *
     * @spec SPEC-044
     * @spec SPEC-047
     * @spec TDS-FA-07
     */
    @Test
    fun `der Stop wartet auf die laufende Anfrage und trennt danach`() = blocking {
        val release = CompletableDeferred<Unit>()
        val backend = FlowBackend(suspendedAfter(release, *oneRow))
        val server = serverOf(backend, ConfigKeys.SHUTDOWN_GRACE to "10")
        RawClient(server.port).use { busy ->
            RawClient(server.port).use { idle ->
                login(busy)
                login(idle)
                busy.send(batch("laeuft"))
                backend.collecting.await()
                val stopping = stopInBackground(server)
                assertTrue(idle.closedByServerWithoutBytes(), "die ruhende Verbindung endet sofort")
                delay(SETTLE_MILLIS)
                assertTrue(stopping.isAlive, "der Stop wartet auf die laufende Anfrage")
                release.complete(Unit)
                assertEquals(rowAnswer, busy.reply("reguläre Antwort").payload.toHex())
                assertTrue(busy.closedByServerWithoutBytes(), "danach endet die Verbindung")
                stopping.join(STOP_MILLIS)
                assertFalse(stopping.isAlive, "der Stop kehrt zurück")
            }
        }
        assertEquals(0, backend.cancelled.size)
        assertEquals(setOf(ConnectionEnd.SHUTDOWN), ends.map { it.end }.toSet())
        assertEquals(2, ends.size)
    }

    /**
     * Eine Anfrage, die nach dem Beginn eintrifft, erhält ERROR `TDS_SHUTDOWN` und DONE mit Fehlerstatus; der Test
     * liest beide vollständig, bevor das Schließen sichtbar wird. Das Backend sieht sie nicht.
     *
     * @spec SPEC-044
     * @spec SPEC-049
     * @spec TDS-FA-07
     */
    @Test
    fun `eine Anfrage nach dem Beginn erhaelt TDS_SHUTDOWN und danach endet die Verbindung`() = blocking {
        val release = CompletableDeferred<Unit>()
        val backend = FlowBackend(suspendedAfter(release, *oneRow), delivering(*oneRow))
        val server = serverOf(backend, ConfigKeys.SHUTDOWN_GRACE to "10")
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("laeuft") + batch("spaet"))
            backend.collecting.await()
            val stopping = stopInBackground(server)
            delay(SETTLE_MILLIS)
            release.complete(Unit)
            assertEquals(rowAnswer, client.reply("reguläre Antwort").payload.toHex())
            val tokens = TokenDecoder(client.reply("TDS_SHUTDOWN").payload).decodeAll()
            assertEquals(shutdown, tokens.filterIsInstance<ErrorToken>().single().message)
            assertEquals(DoneStatus.ERROR, tokens.filterIsInstance<DoneToken>().single().status)
            assertTrue(client.closedByServerWithoutBytes(), "erst nach ERROR und DONE endet die Verbindung")
            stopping.join(STOP_MILLIS)
            assertFalse(stopping.isAlive, "der Stop kehrt zurück")
        }
        assertEquals(1, backend.requests.size, "die zweite Anfrage erreicht das Backend nicht")
        assertEquals(0, backend.cancelled.size)
    }

    /**
     * Nach Ablauf von `SHUTDOWN_GRACE` (eine Sekunde) wird die laufende Anfrage wie bei ATTENTION abgebrochen: ein
     * `cancel()`, die Antwort endet mit genau einem `DONE[ATTN]` ohne ERROR, danach endet die Verbindung; der Stop
     * kehrt nach der Frist zurück.
     *
     * @spec SPEC-044
     * @spec SPEC-029
     * @spec TDS-FA-07
     */
    @Test
    fun `nach Ablauf der Frist wird die Anfrage abgebrochen und die Verbindung endet`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val server = serverOf(backend, ConfigKeys.SHUTDOWN_GRACE to "1")
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("haengt"))
            backend.collecting.await()
            val before = System.nanoTime()
            withTimeout(STOP_MILLIS) { server.stop() }
            val waited = (System.nanoTime() - before) / NANOS_PER_MILLI
            assertTrue(waited >= MILLIS_PER_SECOND, "der Stop wartet die Frist ab: $waited ms")
            assertEquals(BatchVectors.DONE_ATTN, client.reply("Bestätigung").payload.toHex())
            assertTrue(client.closedByServerWithoutBytes())
        }
        assertEquals(1, backend.cancelled.size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * `SHUTDOWN_GRACE` 0 heißt sofort: der Stop wartet nicht auf die laufende Anfrage, bricht sie ab (`DONE[ATTN]`) und
     * beendet die Verbindung.
     *
     * @spec SPEC-044
     * @spec SPEC-047
     */
    @Test
    fun `SHUTDOWN_GRACE 0 bricht die laufende Anfrage sofort ab`() = blocking {
        val backend = FlowBackend(suspendedAfter(CompletableDeferred()))
        val server = serverOf(backend, ConfigKeys.SHUTDOWN_GRACE to "0")
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("haengt"))
            backend.collecting.await()
            withTimeout(STOP_MILLIS) { server.stop() }
            assertEquals(BatchVectors.DONE_ATTN, client.reply("Bestätigung").payload.toHex())
            assertTrue(client.closedByServerWithoutBytes())
        }
        assertEquals(1, backend.cancelled.size)
    }

    /**
     * Obergrenze: ein Backend, das den Abbruch ignoriert, hält den Stop höchstens `SHUTDOWN_GRACE` plus
     * `CANCEL_TIMEOUT`
     * (hier 1 s und 1 s) und eine kurze Wartezeit; die Verbindung ist danach ohne weitere Bytes getrennt, und der Stop
     * kehrt zurück, obwohl das Backend noch hängt. Die Wanduhr-Grenze 3 s trägt die Zeit des Laufs, nicht eine Messung
     * unter Last.
     *
     * @spec SPEC-044
     * @spec SPEC-033
     */
    @Test
    fun `ein Backend, das den Abbruch ignoriert, haelt den Stop hoechstens bis Frist plus CANCEL_TIMEOUT`() = blocking {
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend(ignoringCancel(gate))
        val server = serverOf(
            backend,
            ConfigKeys.SHUTDOWN_GRACE to "1",
            ConfigKeys.CANCEL_TIMEOUT to "1",
        )
        try {
            RawClient(server.port).use { client ->
                login(client)
                client.send(batch("haengt"))
                backend.collecting.await()
                val before = System.nanoTime()
                withTimeout(STOP_MILLIS) { server.stop() }
                val waited = (System.nanoTime() - before) / NANOS_PER_MILLI
                assertTrue(waited >= 2 * MILLIS_PER_SECOND, "Frist plus CANCEL_TIMEOUT: $waited ms")
                assertTrue(waited < LIMIT_MILLIS, "Obergrenze: $waited ms")
                assertTrue(client.closedByServerWithoutBytes())
            }
            gate.complete(Unit)
            backend.closed.await()
            assertEquals(1, backend.cancelled.size)
        } finally {
            gate.complete(Unit)
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
        const val NANOS_PER_MILLI = 1_000_000L
        const val LIMIT_MILLIS = 4_000L
    }
}
