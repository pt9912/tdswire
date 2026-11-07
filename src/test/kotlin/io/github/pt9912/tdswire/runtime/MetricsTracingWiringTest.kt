package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.observability.MetricKeys
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.FlowBackend
import io.github.pt9912.tdswire.protocol.dispatch.delivering
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.dispatch.suspendedAfter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.listener.eventually
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4
private const val RPC_TYPE = 0x03
private const val SHORT_WAIT_MILLIS = 2_300L

/** Eine Senke, die jedes Ereignis zuerst aufnimmt und bei Span-Ende und Schnappschuss danach wirft. */
internal class ThrowsOnMeasurements(private val inner: CapturedEvents) : TelemetrySink {
    override fun accept(event: TelemetryEvent) {
        inner.accept(event)
        if (event.kind == EventKind.SPAN_ENDED || event.kind == EventKind.METRICS) {
            error("senke gebrochen $WERTPROBE")
        }
    }
}

/** Eine Senke, die jedes Ereignis aufnimmt und beim ersten Schnappschuss einen `Error` wirft. */
internal class ErrorOnMetrics(private val inner: CapturedEvents) : TelemetrySink {
    override fun accept(event: TelemetryEvent) {
        inner.accept(event)
        if (event.kind == EventKind.METRICS) throw AssertionError("senke gebrochen $WERTPROBE")
    }
}

/**
 * Spans und Metriken am Socket (`SPEC-017` bis `SPEC-019`, `TDS-FA-09`): der Server läuft im Prozess, der
 * Rohbytes-Client spricht PRELOGIN, LOGIN7 und Anfragen, ein Fake-Backend antwortet, die Senke ist der Sammler.
 * Gemessen wird der Server dieses Repos, kein Referenzclient und kein echter SQL Server. Der Schnappschuss braucht
 * eine Wartezeit von einer Sekunde, dem kleinsten Abstand.
 */
internal class MetricsTracingWiringTest : RuntimeTestBase() {
    private val oneRow = arrayOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private fun environment(vararg more: Pair<String, String>) = loopbackEnvironment(
        file("users.txt", "alice\t{plain}secret\n"),
        ConfigKeys.METRICS_INTERVAL_SECONDS to "1",
        *more,
    )

    private fun login(client: RawClient, password: String = "secret"): Boolean {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            this.password = password.toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        val answer = assertNotNull(client.readMessage())
        return answer.type == TABULAR_TYPE && answer.payload.any { it.toInt() and 0xFF == 0xAD }
    }

    private fun batch(text: String) = handMessage(BATCH_TYPE, BatchVectors.payload(text), 4000)

    private fun RawClient.reply(what: String): RawMessage {
        val message = assertNotNull(readMessage(), what)
        assertEquals(TABULAR_TYPE, message.type, what)
        return message
    }

    private fun spans(name: String): List<TelemetryEvent> =
        sink.events.filter { it.kind == EventKind.SPAN_ENDED && it.span == name }

    private fun latestMetrics(): Map<String, Long>? = sink.events.lastOrNull { it.kind == EventKind.METRICS }?.metrics

    /** Wartet auf einen Schnappschuss, der [condition] erfüllt, und gibt ihn zurück. */
    private suspend fun awaitMetrics(what: String, condition: (Map<String, Long>) -> Boolean): Map<String, Long> {
        eventually(what) { latestMetrics()?.let(condition) == true }
        return checkNotNull(latestMetrics())
    }

    private fun Map<String, Long>.value(key: String): Long = getValue(key)

    /**
     * Ein Lauf mit Anmeldung, einer Anfrage mit Ergebnis, einer mit Backend-Fehler und einer, die ATTENTION abbricht:
     * `tds.query` und `backend.execute` enden je dreimal mit `ok`, `error` und `cancelled`, tragen dieselbe Kennung der
     * Anfrage wie das Backend sie sah, die Sitzung der Anmeldung, die Verbindung, die Art `sql_batch` und eine Dauer;
     * `tds.connection` endet danach einmal mit `ok` in der Phase `session`. Der Schnappschuss nennt danach drei
     * Anfragen, einen Abbruch, eine Anmeldung und null offene Spans.
     *
     * @spec SPEC-017
     * @spec SPEC-018
     * @spec SPEC-019
     * @spec TDS-FA-09
     */
    @Test
    fun `Login, Anfrage, Fehler und Cancel erzeugen die Spans mit ihren Pflicht-Attributen`() = blocking {
        val gate = CompletableDeferred<Unit>()
        val failure = QueryEvent.Failure("TDS_BACKEND_ERROR", "kein Text fuer den Client")
        val backend = FlowBackend(delivering(*oneRow), delivering(failure), suspendedAfter(gate))
        val server = running(environment(), Wiring(telemetry = sink, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(batch("ergebnis"))
            client.reply("ok")
            client.send(batch("fehler"))
            client.reply("fehler")
            client.send(batch("haengt"))
            backend.collecting.await()
            client.send(handMessage(PacketType.ATTENTION.code, ByteArray(0), 4000))
            client.reply("cancel")
        }
        eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        val accepted = sink.events.single { it.kind == EventKind.LOGIN_ACCEPTED }

        val queries = spans("tds.query")
        val backends = spans("backend.execute")
        assertEquals(listOf("ok", "error", "cancelled"), queries.map { it.outcome })
        assertEquals(listOf("ok", "error", "cancelled"), backends.map { it.outcome })
        val requestIds = backend.requests.map { it.requestId }
        assertEquals(3, requestIds.toSet().size)
        assertEquals(requestIds, queries.map { it.requestId })
        assertEquals(requestIds, backends.map { it.requestId })
        queries.forEach {
            assertEquals("sql_batch", it.requestKind)
            assertEquals(accepted.sessionId, it.sessionId)
            assertEquals(accepted.connectionId, it.connectionId)
            assertTrue(checkNotNull(it.durationMs) >= 0)
        }
        backends.forEach { assertTrue(checkNotNull(it.durationMs) >= 0) }
        val connection = spans("tds.connection").single()
        assertEquals("ok", connection.outcome)
        assertEquals("session", connection.phase)
        assertEquals(accepted.connectionId, connection.connectionId)
        assertEquals(accepted.sessionId, connection.sessionId)
        assertNull(connection.requestId)
        val order = sink.events.filter { it.kind == EventKind.SPAN_ENDED }.map { it.span }
        assertEquals(
            listOf("backend.execute", "tds.query", "backend.execute", "tds.query", "backend.execute", "tds.query"),
            order.dropLast(1),
        )
        assertEquals("tds.connection", order.last())

        val metrics = awaitMetrics("Ruhe") { it[MetricKeys.SPANS_OPEN] == 0L && it[MetricKeys.REQUESTS] == 3L }
        assertEquals(0L, metrics.value(MetricKeys.SESSIONS_ACTIVE))
        assertEquals(0L, metrics.value(MetricKeys.BACKEND_ACTIVE))
        assertEquals(1L, metrics.value(MetricKeys.LOGINS_SUCCEEDED))
        assertEquals(0L, metrics.value(MetricKeys.LOGINS_FAILED))
        assertEquals(1L, metrics.value(MetricKeys.ABORTS))
        assertEquals(0L, metrics.value(MetricKeys.PROTOCOL_ERRORS))
        val p50 = metrics.value(MetricKeys.LATENCY_P50)
        assertTrue(p50 > 0 && p50 <= metrics.value(MetricKeys.LATENCY_P99), metrics.toString())
        gate.complete(Unit)
    }

    /**
     * Ein RPC (`sp_executesql`) erzeugt `tds.query` mit der Art `rpc`, dieselbe Kennung wie im Backend-Span.
     *
     * @spec SPEC-018
     * @spec SPEC-019
     */
    @Test
    fun `Ein RPC erzeugt tds_query mit der Art rpc`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val server = running(environment(), Wiring(telemetry = sink, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(handMessage(RPC_TYPE, hex(RpcVectors.EXECUTE_SQL), 4000))
            client.reply("rpc")
        }
        eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        val query = spans("tds.query").single()
        assertEquals("rpc", query.requestKind)
        assertEquals("ok", query.outcome)
        assertEquals(backend.requests.single().requestId, query.requestId)
        assertEquals(backend.requests.single().requestId, spans("backend.execute").single().requestId)
    }

    /**
     * Eine abgelehnte Anmeldung endet `tds.connection` mit `login_rejected` in der Phase `handshake` und ohne Sitzung;
     * eine Verbindung, die nach PRELOGIN abbricht, mit `aborted`. Der Schnappschuss zählt einen Anmeldefehler, keinen
     * Erfolg und keine aktive Sitzung.
     *
     * @spec SPEC-017
     * @spec TDS-FA-09
     */
    @Test
    fun `Eine abgelehnte oder abgebrochene Anmeldung endet im Handschlag`() = blocking {
        val server = running(environment(), Wiring(telemetry = sink))
        RawClient(server.port).use { assertTrue(!login(it, "falsch")) }
        RawClient(server.port).use { client ->
            client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
            assertNotNull(client.readMessage())
        }
        eventually("beide Verbindungen beendet") { spans("tds.connection").size == 2 }
        val ends = spans("tds.connection")
        assertEquals(setOf("login_rejected", "aborted"), ends.map { it.outcome }.toSet())
        ends.forEach {
            assertEquals("handshake", it.phase)
            assertNull(it.sessionId)
            assertNotNull(it.connectionId)
        }
        val metrics = awaitMetrics("Ruhe") { it[MetricKeys.SPANS_OPEN] == 0L && it[MetricKeys.LOGINS_FAILED] == 1L }
        assertEquals(0L, metrics.value(MetricKeys.LOGINS_SUCCEEDED))
        assertEquals(0L, metrics.value(MetricKeys.SESSIONS_ACTIVE))
        assertEquals(0L, metrics.value(MetricKeys.REQUESTS))
    }

    /**
     * Bricht die Gegenstelle während einer Anfrage ab, enden `tds.query` mit `disconnected`, der Backend-Aufruf mit
     * `cancelled` und die Verbindung; danach ist kein Span offen und kein Backend-Aufruf aktiv.
     *
     * @spec SPEC-018
     * @spec SPEC-019
     * @spec TDS-FA-07
     */
    @Test
    fun `Ein Disconnect waehrend der Anfrage beendet jeden Span genau einmal`() = blocking {
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend(suspendedAfter(gate))
        val server = running(environment(), Wiring(telemetry = sink, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(batch("haengt"))
            backend.collecting.await()
            client.reset()
        }
        eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        assertEquals(listOf("disconnected"), spans("tds.query").map { it.outcome })
        assertEquals(listOf("cancelled"), spans("backend.execute").map { it.outcome })
        val metrics = awaitMetrics("Ruhe") { it[MetricKeys.SPANS_OPEN] == 0L }
        assertEquals(0L, metrics.value(MetricKeys.BACKEND_ACTIVE))
        assertEquals(0L, metrics.value(MetricKeys.SESSIONS_ACTIVE))
        assertEquals(1, sink.events.count { it.kind == EventKind.SPAN_ENDED && it.span == "tds.connection" })
        gate.complete(Unit)
    }

    /**
     * Läuft `QUERY_TIMEOUT` ab, endet `tds.query` mit `timeout` und das Backend mit `cancelled`; der Abbruch zählt, die
     * Verbindung bleibt offen.
     *
     * @spec SPEC-018
     * @spec SPEC-009
     */
    @Test
    fun `Der Ablauf der Frist endet tds_query mit timeout`() = blocking {
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend(suspendedAfter(gate))
        val server = running(
            environment(ConfigKeys.QUERY_TIMEOUT to "1"),
            Wiring(telemetry = sink, requests = RequestSource(backend)),
        )
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(batch("haengt"))
            client.reply("timeout")
            eventually("Anfrage beendet") { spans("tds.query").size == 1 }
        }
        eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        assertEquals(listOf("timeout"), spans("tds.query").map { it.outcome })
        assertEquals(listOf("cancelled"), spans("backend.execute").map { it.outcome })
        assertEquals("ok", spans("tds.connection").single().outcome)
        val metrics = awaitMetrics("Ruhe") { it[MetricKeys.SPANS_OPEN] == 0L && it[MetricKeys.REQUESTS] == 1L }
        assertEquals(1L, metrics.value(MetricKeys.ABORTS))
        gate.complete(Unit)
    }

    /**
     * Eine Nachricht, die der Parser abweist, endet `tds.query` mit `protocol_error`; der Schnappschuss zählt einen
     * Protokollfehler. Das Backend wird nicht gerufen und hat keinen Span.
     *
     * @spec SPEC-018
     * @spec SPEC-011
     */
    @Test
    fun `Eine abgewiesene Nachricht endet tds_query mit protocol_error`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val server = running(environment(), Wiring(telemetry = sink, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(handMessage(BATCH_TYPE, ByteArray(3), 4000))
            client.reply("protokollfehler")
        }
        eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        assertEquals(listOf("protocol_error"), spans("tds.query").map { it.outcome })
        assertTrue(spans("backend.execute").isEmpty())
        assertEquals(0, backend.requests.size)
        val metrics = awaitMetrics("Ruhe") { it[MetricKeys.SPANS_OPEN] == 0L && it[MetricKeys.REQUESTS] == 1L }
        assertEquals(1L, metrics.value(MetricKeys.PROTOCOL_ERRORS))
    }

    /**
     * Kardinalität: zweihundert verschiedene SQL-Texte ergeben dieselben Schlüssel des Schnappschusses wie einer, und
     * kein Text eines Ereignisses (Span-Ende, Schnappschuss) enthält einen davon.
     *
     * @spec SPEC-018
     * @spec TDS-QA-03
     */
    @Test
    fun `Viele verschiedene SQL-Texte aendern die Zahl der Schluessel nicht`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val server = running(environment(), Wiring(telemetry = sink, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(batch("select $WERTPROBE-0"))
            client.reply("erste")
            val few = awaitMetrics("erster Schnappschuss") { it[MetricKeys.REQUESTS] == 1L }.keys
            repeat(MANY) {
                client.send(batch("select $WERTPROBE-${it + 1} from t where x = '$WERTPROBE'"))
                client.reply("anfrage $it")
            }
            val many = awaitMetrics("Schnappschuss nach vielen") { it[MetricKeys.REQUESTS] == MANY + 1L }.keys
            assertEquals(few, many)
        }
        eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        assertEquals(MANY + 1, spans("tds.query").size)
        val text = sink.events.filter { it.kind == EventKind.SPAN_ENDED || it.kind == EventKind.METRICS }
            .joinToString("\n") { it.visibleText() }
        assertTrue(!text.contains(WERTPROBE), "SQL-Text im Ereignis")
        assertTrue(!text.contains("select"), "SQL-Text im Ereignis")
    }

    /**
     * Eine Senke, die bei Span-Ende und Schnappschuss wirft, bricht keine Anfrage und keine Verbindung: alle Anfragen
     * werden beantwortet, eine zweite Verbindung meldet sich danach an, und die Senke hat die Ereignisse gesehen.
     *
     * @spec SPEC-018
     * @spec TDS-FA-09
     */
    @Test
    fun `Eine Senke, die wirft, bricht weder Anfrage noch Verbindung`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val throwing = ThrowsOnMeasurements(sink)
        val server = running(environment(), Wiring(telemetry = throwing, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            repeat(3) {
                client.send(batch("anfrage $it"))
                client.reply("anfrage $it")
            }
            awaitMetrics("Schnappschuss trotz Wurf") { it[MetricKeys.REQUESTS] == 3L }
        }
        RawClient(server.port).use { assertTrue(login(it)) }
        eventually("beide Verbindungen beendet") { spans("tds.connection").size == 2 }
        assertEquals(3, spans("tds.query").size)
        assertEquals(listOf("ok", "ok"), spans("tds.connection").map { it.outcome })
    }

    /**
     * `stop` beendet die Spans der offenen Verbindungen vor dem Ereignis zum Ende des Servers und den Schnappschuss mit
     * sich: nach der Rückkehr entsteht kein Schnappschuss mehr, auch nicht nach zwei Abständen.
     *
     * @spec SPEC-017
     * @spec TDS-FA-09
     */
    @Test
    fun `stop beendet die offenen Spans und den Schnappschuss`() = blocking {
        val server = running(environment(), Wiring(telemetry = sink))
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            awaitMetrics("erster Schnappschuss") { it[MetricKeys.SESSIONS_ACTIVE] == 1L }
            server.stop()
            val stopped = sink.events.indexOfFirst { it.kind == EventKind.SERVER_STOPPED }
            val connectionEnd = sink.events.indexOfFirst {
                it.kind == EventKind.SPAN_ENDED && it.span == "tds.connection"
            }
            assertTrue(connectionEnd in 0 until stopped, "Span-Ende $connectionEnd, Ende des Servers $stopped")
            val before = sink.events.count { it.kind == EventKind.METRICS }
            delay(SHORT_WAIT_MILLIS)
            assertEquals(before, sink.events.count { it.kind == EventKind.METRICS })
            assertEquals(EventKind.SERVER_STOPPED, sink.events.last().kind)
        }
    }

    /**
     * Abstand 0 schaltet den Schnappschuss aus: mit Verbindung und Anfrage endet jeder Span, aber nach mehr als einer
     * Sekunde Wartezeit steht kein Ereignis `metrics` im Sammler. Gegenprobe zu den Tests oben, die mit Abstand 1 einen
     * sehen.
     *
     * @spec SPEC-017
     */
    @Test
    fun `Abstand 0 schaltet den Schnappschuss aus`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val server = running(
            environment(ConfigKeys.METRICS_INTERVAL_SECONDS to "0"),
            Wiring(telemetry = sink, requests = RequestSource(backend)),
        )
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(batch("ergebnis"))
            client.reply("ok")
        }
        eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        delay(SHORT_WAIT_MILLIS)
        assertTrue(sink.events.none { it.kind == EventKind.METRICS }, sink.events.toString())
        assertEquals(1, spans("tds.query").size)
    }

    /**
     * Im geordneten Beenden mit `SHUTDOWN_GRACE` 0 bricht der Stop die laufende Anfrage ab: `tds.query` endet mit
     * `shutdown`, der Backend-Aufruf mit `cancelled`, und die Verbindung endet vor dem Ereignis zum Ende des Servers;
     * jeder Span endet genau einmal.
     *
     * @spec SPEC-018
     * @spec SPEC-044
     */
    @Test
    fun `Der Abbruch im Beenden endet tds_query mit shutdown`() = blocking {
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend(suspendedAfter(gate))
        val server = running(
            environment(ConfigKeys.SHUTDOWN_GRACE to "0"),
            Wiring(telemetry = sink, requests = RequestSource(backend)),
        )
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(batch("haengt"))
            backend.collecting.await()
            withTimeout(STOP_MILLIS) { server.stop() }
            client.reply("Bestaetigung")
        }
        assertEquals(listOf("shutdown"), spans("tds.query").map { it.outcome })
        assertEquals(listOf("cancelled"), spans("backend.execute").map { it.outcome })
        assertEquals(1, spans("tds.connection").size)
        val stopped = sink.events.indexOfFirst { it.kind == EventKind.SERVER_STOPPED }
        val connectionEnd = sink.events.indexOfFirst {
            it.kind == EventKind.SPAN_ENDED && it.span == "tds.connection"
        }
        assertTrue(connectionEnd in 0 until stopped, "Span-Ende $connectionEnd, Ende des Servers $stopped")
        gate.complete(Unit)
    }

    /**
     * Wirft der Handler einer Verbindung eine Ausnahme (hier ein `Error` der Senke nach der Anmeldung), endet
     * `tds.connection` mit `error`, und der Span bleibt nicht offen. Die Ausnahme ist der Auslöser, kein Gegenstand der
     * Zusage (`TelemetrySink`).
     *
     * @spec SPEC-017
     * @spec SPEC-048
     */
    @Test
    fun `Eine Ausnahme des Handlers endet tds_connection mit error`() = blocking {
        val server = running(environment(), Wiring(telemetry = ThrowsAfterLogin(sink, AssertionError(WERTPROBE))))
        RawClient(server.port).use { client ->
            client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
            assertNotNull(client.readMessage())
            val payload = SessionVectors.login7Payload {
                userName = "alice"
                password = "secret".toCharArray()
            }
            client.send(handMessage(LOGIN7_TYPE, payload, 4000))
            eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        }
        assertEquals(listOf("error"), spans("tds.connection").map { it.outcome })
        val metrics = awaitMetrics("Ruhe") { it[MetricKeys.SPANS_OPEN] == 0L }
        assertEquals(0L, metrics.value(MetricKeys.SESSIONS_ACTIVE))
    }

    /**
     * Wirft die Senke beim Schnappschuss einen `Error`, endet der Zeitgeber (der `Error` läuft aus ihm heraus, wie am
     * Anfragepfad): nach zwei Abständen steht genau ein `METRICS` im Sammler. Der Server nimmt weiter an und
     * beantwortet Anfragen, und die Spans enden weiter.
     *
     * @spec SPEC-017
     * @spec TDS-FA-09
     */
    @Test
    fun `Ein Error der Senke beendet den Zeitgeber, nicht den Server`() = blocking {
        val backend = FlowBackend(delivering(*oneRow))
        val server = running(environment(), Wiring(telemetry = ErrorOnMetrics(sink), requests = RequestSource(backend)))
        eventually("erster Schnappschuss") { sink.events.any { it.kind == EventKind.METRICS } }
        delay(SHORT_WAIT_MILLIS)
        assertEquals(1, sink.events.count { it.kind == EventKind.METRICS })
        RawClient(server.port).use { client ->
            assertTrue(login(client))
            client.send(batch("ergebnis"))
            client.reply("ok")
        }
        eventually("Verbindung beendet") { spans("tds.connection").size == 1 }
        assertEquals(listOf("ok"), spans("tds.query").map { it.outcome })
        assertEquals(1, sink.events.count { it.kind == EventKind.METRICS })
    }

    private companion object {
        const val MANY = 200
        const val STOP_MILLIS = 15_000L
    }
}
