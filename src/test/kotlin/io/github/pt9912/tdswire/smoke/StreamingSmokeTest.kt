package io.github.pt9912.tdswire.smoke

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.WireSession
import io.github.pt9912.tdswire.testsupport.blocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Ende-zu-Ende über Test-Backend, Port, Typabbildung, Sequenzierer, `TokenEncoder`, Paket-Schreiber und
 * einen Draht ohne Netz: die Ausgabe wird mit dem Test-Decoder des Codecs zurückgelesen. Der Decoder
 * belegt Selbstkonsistenz mit dem Encoder, keine Protokolltreue; die Bytes der großen Antwort sind
 * Handvektor-Arithmetik. Der Nachweis gilt für das Test-Backend und den Draht ohne Netz, nicht für das
 * Simulations-Backend, einen Socket oder ein blockierendes Backend.
 */
class StreamingSmokeTest {
    private val packetSize = 512

    private fun WireSession.tokens(): List<Token> = TokenDecoder(wire.payload()).decodeAll()

    /**
     * Zwei Ergebnisse (600 Zeilen, dann eine): die Ausgabe in mehreren Paketen liefert COLMETADATA,
     * 600 ROW und ein DONE mit `COUNT` und `MORE` (Zeilenzahl 600), dann COLMETADATA, eine ROW und ein
     * DONE mit `COUNT` ohne `MORE`; das letzte Paket trägt EOM, und die Sitzung schließt mit dem Ende der
     * Gegenstelle.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Zwei Ergebnisse ergeben Metadaten, Zeilen und die DONE_MORE-Folge`() = blocking {
        val events = listOf(resultStart()) + List(600) { QueryEvent.Row(listOf(it)) } +
            QueryEvent.ResultEnd(600) + resultStart() + QueryEvent.Row(listOf(7)) + QueryEvent.ResultEnd(1)
        val session = WireSession.login(packetSize, "select", retain = true)
        val backend = ScriptedBackend.ofList(*events.toTypedArray())
        session.serve(backend)
        val tokens = session.tokens()
        assertTrue(session.wire.packetCount > 2)
        assertTrue(session.wire.packets.last().endOfMessage)
        assertIs<ColMetadataToken>(tokens.first())
        assertEquals(600, tokens.drop(1).takeWhile { it is RowToken }.size)
        val dones = tokens.filterIsInstance<DoneToken>()
        assertEquals(listOf(DoneStatus.COUNT or DoneStatus.MORE, DoneStatus.COUNT), dones.map { it.status })
        assertEquals(listOf(600L, 1L), dones.map { it.rowCount })
        assertEquals(2, tokens.filterIsInstance<ColMetadataToken>().size)
        assertEquals(601, tokens.filterIsInstance<RowToken>().size)
        assertIs<DoneToken>(tokens.last())
        assertEquals(1, backend.closeCalls)
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
    }

    /**
     * Ein Fehler nach 600 Teilzeilen: die Ausgabe trägt Metadaten, die 600 Zeilen, dann ERROR (Class 16,
     * Nummer von Hand) und ein DONE mit Fehlerstatus, genau ein Abschluss, in einer Nachricht mit EOM im
     * letzten Paket.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein Fehler nach Teilzeilen ergibt ERROR und DONE mit Fehlerstatus`() = blocking {
        val events = listOf(resultStart()) + List(600) { QueryEvent.Row(listOf(it)) } +
            QueryEvent.Failure("TDS_BACKEND_ERROR", "kein Text fuer den Client")
        val session = WireSession.login(packetSize, "select", retain = true)
        val backend = ScriptedBackend.ofList(*events.toTypedArray())
        session.serve(backend)
        val tokens = session.tokens()
        assertEquals(600, tokens.filterIsInstance<RowToken>().size)
        val error = tokens.filterIsInstance<ErrorToken>().single()
        assertEquals(ServerMessage(50004, 1, 16, "Backend error."), error.message)
        assertEquals(DoneStatus.ERROR, tokens.filterIsInstance<DoneToken>().single().status)
        assertIs<DoneToken>(tokens.last())
        assertTrue(session.wire.packets.last().endOfMessage)
        assertEquals(1, backend.closeCalls)
        assertEquals(emptyList(), backend.cancelled)
    }

    /**
     * Speichergrenze: 1 200 000 Zeilen zu je einem Text von 128 Zeichen haben allein als Text mehr Bytes,
     * als der Heap des Testlaufs fasst (Untergrenze: ein Byte je Zeichen, das Mindeste, das ein Text im
     * Heap belegt); die Antwort wird vollständig übertragen. Belegt ist die Übertragung über die Bytezahl
     * (Zeilenteil: 1 Byte Token, 2 Byte Länge, 256 Byte UTF-16LE je Zeile, von Hand gerechnet; der Rest
     * ist die Länge von COLMETADATA und DONE aus einem Lauf mit drei Zeilen), die Zahl der Pakete, den
     * Anfang (COLMETADATA `0x81`) und das Ende (DONE mit `COUNT` und der Zeilenzahl). Heap-Einstellung des
     * Testlaufs: `maxHeapSize` in `gradle/test-jvm.init.gradle.kts`; der Beleg ist ein Zusatz zur
     * Zähler-Schranke, kein deterministischer Beleg für die Schranke selbst.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein Ergebnis ueber dem Heap des Testlaufs wird vollstaendig uebertragen`() = blocking {
        val rows = 1_200_000
        val chars = 128
        val heap = Runtime.getRuntime().maxMemory()
        assertTrue(rows.toLong() * chars > heap, "der Test ist zu klein für den Heap: $heap")

        val small = WireSession.login(8000, "select")
        small.serve(ScriptedBackend.generatingText(3, chars))
        val framing = small.wire.payloadBytes - 3L * (3 + 2 * chars)

        val session = WireSession.login(8000, "select")
        val backend = ScriptedBackend.generatingText(rows, chars)
        session.serve(backend)
        val wire = session.wire
        val total = framing + rows.toLong() * (3 + 2 * chars)
        assertEquals(total, wire.payloadBytes)
        assertEquals((total + 7991) / 7992, wire.packetCount.toLong())
        assertEquals(0x81, wire.headBytes[0].toInt() and 0xFF)
        val doneTail = byteArrayOf(0xFD.toByte(), 0x10, 0, 0, 0) + longLe(rows.toLong())
        assertContentEquals(doneTail, wire.tail.copyOfRange(wire.tail.size - doneTail.size, wire.tail.size))
        assertTrue(wire.tail[1].toInt() and 1 == 1)
        assertEquals(rows + 2, backend.delivered)
        assertEquals(1, backend.closeCalls)
        assertEquals(emptyList(), backend.cancelled)
    }

    private fun longLe(value: Long) = ByteArray(8) { (value shr (8 * it)).toByte() }
}
