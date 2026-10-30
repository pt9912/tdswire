package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.drain
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.transport.framing.FramingFailure
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.messaging.TransportError
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Abbruch der Gegenstelle an jeder Byteposition eines Stroms aus drei Nachrichten (`TDS-QA-02`): die
 * Nachrichten, die vor dem Abbruch vollständig ankamen, werden geliefert; endet der Strom zwischen
 * zwei Nachrichten, ist es ein sauberes Ende, mitten in einer Nachricht ein Protokollfehler, und ein
 * Fehler des Kanals bleibt ein Kanalfehler.
 */
class StreamCutoffTest {
    private class Sample(val type: Int, val payload: ByteArray, val wire: ByteArray)

    private fun sample(type: Int, payload: ByteArray, perPacket: Int) =
        Sample(type, payload, handMessage(type, payload, perPacket))

    /** Zwei, null und drei Pakete; unterschiedliche Typen und Inhalte. */
    private val samples = listOf(
        sample(0x01, ByteArray(5) { (it + 1).toByte() }, 3),
        sample(0x03, ByteArray(0), 10),
        sample(0x12, ByteArray(20) { (it * 7).toByte() }, 7),
    )

    private val wire = samples.map { it.wire }.reduce(ByteArray::plus)

    /** Stromlängen, an denen eine Nachricht endet; der Strom kann dort sauber enden. */
    private val boundaries = samples.runningFold(0) { sum, sample -> sum + sample.wire.size }

    private fun outcomeAt(cut: Int, end: ScriptEnd, readBuffer: Int): Pair<List<ReadOutcome.Message>, ReadOutcome> {
        val channel = ScriptedChannel(listOf(wire.copyOf(cut)), end)
        val (messages, last) = runBlocking { MessageReader(channel, FramingLimits(), readBuffer).drain() }
        return messages.map { ReadOutcome.Message(it) } to last
    }

    /**
     * Für jede Länge von 0 bis zum ganzen Strom, drei Lesepuffergrößen (1, 5, 8192 Byte) und beide
     * Enden (sauber geschlossen, E/A-Fehler): die vor dem Abbruch vollendeten Nachrichten kommen mit
     * Typ und Nutzdaten an. Ein sauberes Ende zwischen zwei Nachrichten ist `EndOfStream`, mitten in
     * Header oder Nutzdaten `MESSAGE_TRUNCATED`; ein E/A-Fehler ist an jeder Stelle ein Kanalfehler.
     * Die Grenzen sind aus den von Hand gebauten Paketlängen gezählt, nicht aus dem Codec.
     *
     * @spec SPEC-027 SPEC-028
     */
    @Test
    fun `Abbruch an jeder Position liefert die vollendeten Nachrichten und den richtigen Fehler`() {
        var cases = 0
        for (readBuffer in listOf(1, 5, 8192)) {
            for (end in ScriptEnd.entries) {
                for (cut in 0..wire.size) {
                    val (messages, last) = outcomeAt(cut, end, readBuffer)
                    val context = "Abbruch bei $cut, Puffer $readBuffer, $end"
                    val complete = boundaries.drop(1).count { it <= cut }
                    assertEquals(complete, messages.size, "vollendete Nachrichten, $context")
                    messages.zip(samples).forEach { (have, want) ->
                        assertEquals(want.type, have.message.type.code, "Typ, $context")
                        assertContentEquals(want.payload, have.message.payload, "Nutzdaten, $context")
                    }
                    assertTail(last, cut in boundaries, end, context)
                    cases++
                }
            }
        }
        assertEquals(3 * 2 * (wire.size + 1), cases)
    }

    private fun assertTail(last: ReadOutcome, onBoundary: Boolean, end: ScriptEnd, context: String) {
        when {
            end == ScriptEnd.IO_FAILURE ->
                assertIs<TransportError.Channel>(assertIs<ReadOutcome.Failed>(last).error, context)

            onBoundary -> assertEquals(ReadOutcome.EndOfStream, last, context)

            else -> {
                val error = assertIs<TransportError.Framing>(assertIs<ReadOutcome.Failed>(last).error, context)
                assertEquals(FramingFailure.MESSAGE_TRUNCATED, error.error.failure, context)
            }
        }
    }

    private val typeChange = handPacket(0x01, 0, ByteArray(3)) + byteArrayOf(0x03, 1, 0, 8, 0, 0, 2, 0)

    /** Je ein Strom-Ende, das der Codec ablehnt, mit dem Fehler, den die Spezifikation dafür verlangt. */
    private val corruptTails = mapOf(
        "unbekannter Pakettyp" to (byteArrayOf(0x05, 1, 0, 8, 0, 0, 1, 0) to FramingFailure.UNKNOWN_PACKET_TYPE),
        "Länge unter 8" to (byteArrayOf(0x01, 1, 0, 7, 0, 0, 1, 0) to FramingFailure.LENGTH_BELOW_HEADER),
        "Länge über der Paketgrenze" to
            (byteArrayOf(0x01, 1, 0, 101, 0, 0, 1, 0) to FramingFailure.LENGTH_ABOVE_PACKET_LIMIT),
        "Typwechsel in der Nachricht" to
            (typeChange to FramingFailure.PACKET_TYPE_CHANGED),
    )

    /**
     * Ein Fehler hinter vollendeten Nachrichten kostet diese nicht: zwei gültige Nachrichten und ein
     * Strom-Ende, das der Codec ablehnt, kommen in einem einzigen Lesevorgang (Puffer 8192) und in
     * Stücken zu 7 und 1 Byte an; der Leser liefert beide Nachrichten und danach den Fehler der
     * Spezifikation. Die Nachrichten stammen von Hand gebauten Paketen, der Fehler je Tabellenzeile.
     *
     * @spec SPEC-027 SPEC-006 SPEC-028
     */
    @Test
    fun `Fehler hinter vollendeten Nachrichten liefert zuerst die Nachrichten`() = blocking {
        val limits = FramingLimits(maxPacketBytes = 100, maxMessageBytes = 256)
        corruptTails.forEach { (name, tail) ->
            listOf(8192, 7, 1).forEach { readBuffer ->
                val stream = samples[0].wire + samples[2].wire + tail.first
                val reader = MessageReader(ScriptedChannel(listOf(stream)), limits, readBuffer)
                val context = "$name, Puffer $readBuffer"
                val (messages, last) = reader.drain()
                assertEquals(listOf(samples[0].type, samples[2].type), messages.map { it.type.code }, context)
                val error = assertIs<TransportError.Framing>(assertIs<ReadOutcome.Failed>(last).error, context)
                assertEquals(tail.second, error.error.failure, context)
            }
        }
    }

    /**
     * Nach dem Fehler liefert der Leser immer wieder dasselbe Ergebnis und die Pakettypen bleiben
     * unverändert: ein abgeschnittener Strom führt nicht zu einer erfundenen Nachricht.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Nach dem Abbruch liefert der Leser stabil denselben Fehler`() = blocking {
        val channel = ScriptedChannel(listOf(wire.copyOf(boundaries[1] + 4)))
        val reader = MessageReader(channel)
        assertEquals(PacketType.SQL_BATCH, assertIs<ReadOutcome.Message>(reader.next()).message.type)
        val first = reader.next()
        assertIs<ReadOutcome.Failed>(first)
        repeat(3) { assertEquals(first, reader.next()) }
    }
}
