package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.telemetry.ServerStopped
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.toHex
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val TABULAR = 0x04

/**
 * Der Weg der Ereignisse zur Senke (`SPEC-059`, Vertrag von `TelemetrySink`): ohne Senke entsteht nichts, der
 * Zeitpunkt kommt aus dem Zeitgeber der Bibliothek, eine Senke, die wirft, bricht weder Anfrage noch Verbindung, und
 * `CancellationException` und `Error` laufen zum Aufrufer.
 */
internal class TelemetryTest : RuntimeTestBase() {
    private val fixed = Instant.parse("2026-11-01T12:30:00Z")
    private val clock = Clock.fixed(fixed, ZoneOffset.UTC)

    private fun stopped(): TelemetryEvent = ServerStopped(fixed)

    /**
     * Ohne Senke baut die Bibliothek kein Ereignis: die Funktion, die es baute, wird nicht gerufen.
     *
     * @spec SPEC-059
     */
    @Test
    fun `Ohne Senke wird kein Ereignis gebaut`() {
        val built = AtomicInteger()
        Telemetry(TelemetrySink.NONE, clock).emit {
            built.incrementAndGet()
            stopped()
        }
        assertEquals(0, built.get())
    }

    /**
     * Der Zeitpunkt des Ereignisses kommt aus dem Zeitgeber der Bibliothek; die Senke bekommt genau das gebaute
     * Ereignis.
     *
     * @spec SPEC-059
     */
    @Test
    fun `Der Zeitpunkt kommt aus dem Zeitgeber`() {
        val seen = CapturedEvents()
        var stamped: Instant? = null
        Telemetry(seen, clock).emit {
            stamped = it
            stopped()
        }
        assertEquals(fixed, stamped)
        assertEquals(fixed, seen.events.single().at)
    }

    /**
     * Eine Senke, die eine `Exception` wirft, verliert dieses Ereignis; das nächste kommt an.
     *
     * @spec SPEC-059
     */
    @Test
    fun `Eine Ausnahme der Senke wird verworfen`() {
        val calls = AtomicInteger()
        val sink = TelemetrySink {
            calls.incrementAndGet()
            if (calls.get() == 1) error("Senke defekt")
        }
        val telemetry = Telemetry(sink, clock)
        telemetry.emit { stopped() }
        telemetry.emit { stopped() }
        assertEquals(2, calls.get())
    }

    /**
     * Vertrag zu `CancellationException` und `Error` (ADR-0013, Entscheidung 7): beide werden nicht gefangen und
     * laufen zum Aufrufer, die Abbruchausnahme der Coroutine und ein Fehler der JVM bleiben sichtbar.
     *
     * @spec SPEC-059
     */
    @Test
    fun `Abbruchausnahme und Error laufen zum Aufrufer`() {
        val cancelled = CancellationException("abgebrochen")
        val thrown = assertFailsWith<CancellationException> {
            Telemetry({ throw cancelled }, clock).emit { stopped() }
        }
        assertSame(cancelled, thrown)
        val broken = NoClassDefFoundError("Senke")
        val error = assertFailsWith<NoClassDefFoundError> {
            Telemetry({ throw broken }, clock).emit { stopped() }
        }
        assertSame(broken, error)
    }

    /**
     * Eine Senke, die bei jedem Ereignis wirft, bricht weder Start noch Anmeldung, Anfrage oder Stop: der Server
     * läuft, die Antwort kommt, und die Senke wurde gerufen.
     *
     * @spec SPEC-059
     */
    @Test
    fun `Eine Senke, die wirft, bricht weder Anfrage noch Verbindung`() = blocking {
        val calls = AtomicInteger()
        val throwing = TelemetrySink {
            calls.incrementAndGet()
            error("Senke defekt")
        }
        val backend = ScriptedBackend.ofList(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
        val server = running(environment(), Wiring(telemetry = throwing, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
            assertNotNull(client.readMessage())
            val login = SessionVectors.login7Payload {
                userName = "alice"
                password = "secret".toCharArray()
            }
            client.send(handMessage(LOGIN7_TYPE, login, 4000))
            assertEquals(TABULAR, assertNotNull(client.readMessage()).type)
            client.send(handMessage(BATCH_TYPE, BatchVectors.payload("select 1"), 4000))
            val reply = assertNotNull(client.readMessage())
            val expected = listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1)
            assertEquals(expected.joinToString(" "), reply.payload.toHex())
        }
        server.stop()
        assertTrue(calls.get() >= 3, "die Senke wurde nicht gerufen: ${calls.get()}")
    }

    private fun environment() = loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"))
}
