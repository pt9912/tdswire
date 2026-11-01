package io.github.pt9912.tdswire.domain.telemetry

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LIMIT = 256
private const val LONG = 300

/**
 * Das Ereignismodell (`TDS-FA-09.a`, `SPEC-059`): Zeitpunkt, feste Ebene je Art, die Felder der Verbindung, die
 * Längengrenze der Zeichenketten. Die Menge der Arten ist geschlossen; ein Test hält die Liste der Muster gegen die
 * Unterklassen, damit eine neue Art ohne Muster auffällt.
 */
internal class TelemetryEventTest {
    private val at: Instant = Instant.parse("2026-11-01T10:00:00Z")
    private val context = EventContext(7L, "sitzung-1", ClientProfile.tds74("on"))

    private class Sample(val event: TelemetryEvent, val level: EventLevel, val scoped: Boolean)

    private fun samples(): List<Sample> = listOf(
        Sample(ServerStarted(at, 1433), EventLevel.INFO, false),
        Sample(ServerStopped(at), EventLevel.INFO, false),
        Sample(StartRefused(at, StartRefusal.JVM_FLAG_MISSING), EventLevel.ERROR, false),
        Sample(ConfigInvalid(at, "SCHLUESSEL", "MISSING"), EventLevel.ERROR, false),
        Sample(TlsStartRejected(at, "CERTIFICATE", "Kette unbrauchbar"), EventLevel.ERROR, false),
        Sample(BindFailed(at, "127.0.0.1", 1433, null), EventLevel.ERROR, false),
        Sample(EncryptionWeakened(at), EventLevel.WARN, false),
        Sample(PlainTestModeActive(at), EventLevel.WARN, false),
        Sample(SimulationBackendReady(at, 2), EventLevel.DEBUG, false),
        Sample(AcceptFailed(at, "java.io.IOException"), EventLevel.ERROR, false),
        Sample(ConnectionFailed(at, context, "java.lang.IllegalStateException"), EventLevel.ERROR, true),
        Sample(LoginAccepted(at, context, "alice"), EventLevel.INFO, true),
        Sample(LoginRejected(at, context, LoginRejectionReason.WRONG_PASSWORD), EventLevel.WARN, true),
        Sample(AdapterFailed(at, context, "java.lang.IllegalStateException"), EventLevel.ERROR, true),
        Sample(EncryptionNotOffered(at, context), EventLevel.WARN, true),
        Sample(TlsFirstNotOffered(at, context, TlsFirstRefusal.NO_TLS_MATERIAL), EventLevel.WARN, true),
        Sample(TlsHandshakeFailed(at, context, "HANDSHAKE_FAILED"), EventLevel.WARN, true),
        Sample(RequestFailed(at, context, "TDS_INTERNAL_ERROR", null), EventLevel.WARN, true),
    )

    /**
     * Jede Unterklasse der geschlossenen Menge hat ein Muster in dieser Datei. Eine neue Art ohne Muster macht den
     * Test rot; ohne ihn bliebe sie ungeprüft, auch von den Redaktionstests, die die Muster nutzen.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Jede Ereignisart hat ein Muster`() {
        val declared = TelemetryEvent::class.java.permittedSubclasses.orEmpty().map { it.name }.toSet()
        assertTrue(declared.size >= samples().size, "die Unterklassen sind nicht lesbar: $declared")
        assertEquals(declared, samples().map { it.event.javaClass.name }.toSet())
    }

    /**
     * Zeitpunkt und Ebene stehen je Art fest; die Felder der Verbindung kommen aus dem Kontext, bei einer Art ohne
     * Verbindung fehlen Verbindung und Sitzung, und das Profil ist `unknown`.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Zeitpunkt, Ebene und Felder der Verbindung gelten je Art`() {
        samples().forEach { sample ->
            val event = sample.event
            val name = event.javaClass.simpleName
            assertEquals(at, event.at, name)
            assertEquals(sample.level, event.level, name)
            if (sample.scoped) {
                assertEquals(7L, event.connectionId, name)
                assertEquals("sitzung-1", event.sessionId, name)
                assertEquals("tds-7.4:on", event.clientProfile, name)
            } else {
                assertNull(event.connectionId, name)
                assertNull(event.sessionId, name)
                assertEquals("unknown", event.clientProfile, name)
            }
        }
    }

    /**
     * Jedes Zeichenketten-Feld ist auf 256 Einheiten gekürzt, in jeder Art, die eines trägt; ein kürzerer Wert
     * bleibt unverändert, auch mit Steuerzeichen und Anführungszeichen (das Escaping gehört dem Adapter).
     *
     * @spec SPEC-012
     */
    @Test
    fun `Zeichenketten sind auf 256 Einheiten gekuerzt`() {
        val long = "x".repeat(LONG)
        val cut = "x".repeat(LIMIT)
        assertEquals(cut, ConfigInvalid(at, long, long).key)
        assertEquals(cut, ConfigInvalid(at, long, long).kind)
        assertEquals(cut, TlsStartRejected(at, long, long).part)
        assertEquals(cut, TlsStartRejected(at, long, long).form)
        assertEquals(cut, BindFailed(at, long, 1, long).address)
        assertEquals(cut, BindFailed(at, long, 1, long).exceptionType)
        assertEquals(cut, AcceptFailed(at, long).exceptionType)
        assertEquals(cut, ConnectionFailed(at, context, long).exceptionType)
        assertEquals(cut, LoginAccepted(at, context, long).principal)
        assertEquals(cut, AdapterFailed(at, context, long).exceptionType)
        assertEquals(cut, TlsHandshakeFailed(at, context, long).failure)
        assertEquals(cut, RequestFailed(at, context, long, long).errorCode)
        assertEquals(cut, RequestFailed(at, context, long, long).cause)
        val hostile = "a\"b\u0000\n\\c"
        assertEquals(hostile, LoginAccepted(at, context, hostile).principal)
    }

    /**
     * Die Grenze liegt bei 256 Einheiten: 255 und 256 bleiben, 257 wird gekürzt. Ein Paar aus Ersatzzeichen, das die
     * Grenze trennt, entfällt ganz; das Ereignis trägt kein einzelnes Ersatzzeichen.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Die Grenze trennt kein Paar aus Ersatzzeichen`() {
        listOf(LIMIT - 1, LIMIT).forEach { length ->
            val text = "p".repeat(length)
            assertEquals(text, LoginAccepted(at, context, text).principal, "Länge $length")
        }
        assertEquals(LIMIT, LoginAccepted(at, context, "p".repeat(LIMIT + 1)).principal.length)
        val pair = "😀"
        val straddling = "p".repeat(LIMIT - 1) + pair
        val principal = LoginAccepted(at, context, straddling).principal
        assertEquals("p".repeat(LIMIT - 1), principal)
        assertTrue(principal.none { it.isSurrogate() })
        val whole = "p".repeat(LIMIT - 2) + pair
        assertEquals(whole, LoginAccepted(at, context, whole).principal)
    }
}
