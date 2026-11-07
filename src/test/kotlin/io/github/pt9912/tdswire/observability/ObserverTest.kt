package io.github.pt9912.tdswire.observability

import io.github.pt9912.tdswire.domain.telemetry.EventContext
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.EventLevel
import io.github.pt9912.tdswire.domain.telemetry.RequestKind
import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MILLI = 1_000_000L
private const val CONNECTION = 7L

/**
 * Der Beobachter von Spans und Metriken (`SPEC-017` bis `SPEC-019`, Abschnitt 5 der Spezifikation) ohne Netz und ohne
 * Server: eine feste Uhr für die Dauer, ein Sammler für die Ereignisse. Die Verdrahtung am Socket belegt
 * `MetricsTracingWiringTest`.
 */
internal class ObserverTest {
    private val emitted = mutableListOf<TelemetryEvent>()
    private var nanos = 0L
    private val observer = Observer(
        emit = { build -> build(Instant.EPOCH)?.let { emitted += it } },
        nanoTime = { nanos },
    )
    private val context = EventContext(CONNECTION, "sitzung-1", "unknown")

    private fun metrics(): Map<String, Long> {
        observer.snapshot()
        return checkNotNull(emitted.last { it.kind == EventKind.METRICS }.metrics)
    }

    /**
     * Ein Span endet genau einmal: ein zweiter Aufruf von `end`, auch mit anderem `outcome`, erzeugt kein zweites
     * Ereignis, zählt nichts doppelt und lässt `openSpans` bei null.
     *
     * @spec SPEC-018
     */
    @Test
    fun `Ein Span endet genau einmal`() {
        val session = observer.connection(CONNECTION).loginAccepted(context)
        val span = session.request("anfrage-1", RequestKind.SQL_BATCH)
        assertEquals(2, observer.openSpans())
        span.end(SpanOutcome.OK)
        span.end(SpanOutcome.ERROR)
        val ended = emitted.filter { it.kind == EventKind.SPAN_ENDED }
        assertEquals(1, ended.size)
        assertEquals("ok", ended.single().outcome)
        assertEquals(1, observer.openSpans())
        assertEquals(1L, metrics().getValue(MetricKeys.REQUESTS))
    }

    /**
     * Die Pflicht-Attribute der drei Spans: `tds.connection` mit Verbindung, Phase und Ausgang; `tds.query` mit
     * Sitzung, Anfrage-Kennung, Art und Ausgang; `backend.execute` mit Anfrage-Kennung, Dauer und Ausgang. Die Dauer
     * ist der Abstand der monotonen Uhr in ganzen Millisekunden.
     *
     * @spec SPEC-017
     * @spec SPEC-018
     * @spec SPEC-019
     */
    @Test
    fun `Die drei Spans tragen ihre Pflicht-Attribute`() {
        val connection = observer.connection(CONNECTION)
        val session = connection.loginAccepted(context)
        val query = session.request("anfrage-1", RequestKind.RPC)
        val backend = session.backend("anfrage-1")
        nanos += 12 * MILLI + 999_999
        backend.end(SpanOutcome.OK)
        nanos += 3 * MILLI
        query.end(SpanOutcome.ERROR)
        connection.end(SpanOutcome.OK)

        val (backendEnd, queryEnd, connectionEnd) = emitted.filter { it.kind == EventKind.SPAN_ENDED }
        val names = listOf(backendEnd, queryEnd, connectionEnd).map { it.span }
        assertEquals(listOf("backend.execute", "tds.query", "tds.connection"), names)
        assertEquals(12L, backendEnd.durationMs)
        assertEquals("anfrage-1", backendEnd.requestId)
        assertEquals("ok", backendEnd.outcome)
        assertEquals(15L, queryEnd.durationMs)
        assertEquals("anfrage-1", queryEnd.requestId)
        assertEquals("rpc", queryEnd.requestKind)
        assertEquals("error", queryEnd.outcome)
        assertEquals("sitzung-1", queryEnd.sessionId)
        assertEquals(CONNECTION, queryEnd.connectionId)
        assertEquals("session", connectionEnd.phase)
        assertEquals(CONNECTION, connectionEnd.connectionId)
        assertEquals("sitzung-1", connectionEnd.sessionId)
        assertNull(connectionEnd.requestId)
        assertNull(connectionEnd.requestKind)
        emitted.filter { it.kind == EventKind.SPAN_ENDED }.forEach { assertEquals(EventLevel.DEBUG, it.level) }
        assertEquals(0, observer.openSpans())
    }

    /**
     * Eine Verbindung, deren Anmeldung nicht angenommen wurde, endet in der Phase `handshake` ohne Sitzung; sie zählt
     * als Anmeldefehler nur bei `LOGIN_REJECTED`, und `sessions_active` bleibt null.
     *
     * @spec SPEC-017
     */
    @Test
    fun `Eine Verbindung ohne angenommene Anmeldung endet im Handschlag`() {
        observer.connection(CONNECTION).end(SpanOutcome.LOGIN_REJECTED)
        observer.connection(CONNECTION + 1).end(SpanOutcome.ABORTED)
        val ends = emitted.filter { it.kind == EventKind.SPAN_ENDED }
        assertEquals(listOf("login_rejected", "aborted"), ends.map { it.outcome })
        ends.forEach {
            assertEquals("handshake", it.phase)
            assertNull(it.sessionId)
        }
        val values = metrics()
        assertEquals(1L, values.getValue(MetricKeys.LOGINS_FAILED))
        assertEquals(0L, values.getValue(MetricKeys.LOGINS_SUCCEEDED))
        assertEquals(0L, values.getValue(MetricKeys.SESSIONS_ACTIVE))
    }

    /**
     * Aktive Sessions, Login-Erfolge, aktive Backendoperationen, Anfragen, Abbrüche (ATTENTION und Frist) und
     * Protokollfehler zählen nach den Ausgängen der Spans; Momentwerte fallen mit dem Ende zurück.
     *
     * @spec SPEC-017
     * @spec SPEC-018
     * @spec SPEC-019
     */
    @Test
    fun `Zaehler und Momentwerte folgen den Spans`() {
        val connection = observer.connection(CONNECTION)
        val session = connection.loginAccepted(context)
        val backend = session.backend("a-1")
        val during = metrics()
        assertEquals(1L, during.getValue(MetricKeys.SESSIONS_ACTIVE))
        assertEquals(1L, during.getValue(MetricKeys.BACKEND_ACTIVE))
        assertEquals(1L, during.getValue(MetricKeys.LOGINS_SUCCEEDED))
        assertEquals(2L, during.getValue(MetricKeys.SPANS_OPEN))
        backend.end(SpanOutcome.CANCELLED)
        listOf(
            SpanOutcome.OK,
            SpanOutcome.ERROR,
            SpanOutcome.CANCELLED,
            SpanOutcome.TIMEOUT,
            SpanOutcome.PROTOCOL_ERROR,
            SpanOutcome.SHUTDOWN,
            SpanOutcome.DISCONNECTED,
        ).forEachIndexed { index, outcome -> session.request("a-$index", RequestKind.SQL_BATCH).end(outcome) }
        connection.end(SpanOutcome.OK)
        val after = metrics()
        assertEquals(0L, after.getValue(MetricKeys.SESSIONS_ACTIVE))
        assertEquals(0L, after.getValue(MetricKeys.BACKEND_ACTIVE))
        assertEquals(0L, after.getValue(MetricKeys.SPANS_OPEN))
        assertEquals(7L, after.getValue(MetricKeys.REQUESTS))
        assertEquals(2L, after.getValue(MetricKeys.ABORTS), "ATTENTION und Frist zählen, das Beenden nicht")
        assertEquals(1L, after.getValue(MetricKeys.PROTOCOL_ERRORS))
        assertEquals(1L, after.getValue(MetricKeys.LOGINS_SUCCEEDED))
        assertEquals(0L, after.getValue(MetricKeys.LOGINS_FAILED))
    }

    /**
     * Die Perzentile p50, p95 und p99 fehlen, solange keine Anfrage endete; danach sind sie die Obergrenze der Klasse
     * des Rangs, nicht die gemessene Dauer. Hundert Anfragen: 90 zu 3 ms, 9 zu 40 ms, eine zu 70 s ergeben p50 = 5,
     * p95 = 50 und p99 = 50; mit einer zweiten Anfrage über der größten Grenze liegt p99 über allen Grenzen.
     *
     * @spec SPEC-018
     */
    @Test
    fun `Die Latenz-Perzentile sind die Obergrenzen der Klassen`() {
        assertNull(metrics()[MetricKeys.LATENCY_P50])
        val session = observer.connection(CONNECTION).loginAccepted(context)
        fun request(milliseconds: Long) {
            val span = session.request("a", RequestKind.SQL_BATCH)
            nanos += milliseconds * MILLI
            span.end(SpanOutcome.OK)
        }
        repeat(90) { request(3) }
        repeat(9) { request(40) }
        request(70_000)
        val values = metrics()
        assertEquals(5L, values[MetricKeys.LATENCY_P50])
        assertEquals(50L, values[MetricKeys.LATENCY_P95])
        assertEquals(50L, values[MetricKeys.LATENCY_P99])
        request(70_000)
        assertEquals(LatencyHistogram.OVERFLOW, metrics()[MetricKeys.LATENCY_P99])
    }

    /**
     * Kardinalität: die Schlüssel des Schnappschusses sind dieselben, egal wie viele verschiedene Anfrage-Kennungen
     * durchlaufen; keine Kennung wird zum Schlüssel. Die Schnittstelle der Spans nimmt keinen SQL-Text an.
     *
     * @spec SPEC-018
     * @spec TDS-QA-03
     */
    @Test
    fun `Die Zahl der Schluessel haengt nicht von den Anfragen ab`() {
        val session = observer.connection(CONNECTION).loginAccepted(context)
        session.request("a-0", RequestKind.SQL_BATCH).end(SpanOutcome.OK)
        val few = metrics().keys
        repeat(500) { session.request("a-${it + 1}", RequestKind.SQL_BATCH).end(SpanOutcome.OK) }
        val many = metrics().keys
        assertEquals(few, many)
        assertTrue(many.none { "a-" in it || "select" in it }, many.toString())
        assertEquals(501L, metrics().getValue(MetricKeys.REQUESTS))
    }
}
