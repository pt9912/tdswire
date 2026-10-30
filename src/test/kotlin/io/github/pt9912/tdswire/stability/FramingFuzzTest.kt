package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.stability.FramingOracle.Defect
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.transport.framing.FramingError
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.MessageAssembler
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fuzz des Paket-Codecs gegen die unabhängige Referenz [FramingOracle] (`TDS-QA-02`): defekte
 * Paketlängen, Pakettypen, Status und Abschnitte der Nachrichtengrenze, in beliebige Stücke zerlegt.
 * Alle Läufe haben feste Startwerte.
 */
class FramingFuzzTest {
    private val configs = listOf(
        FramingLimits(maxPacketBytes = 100, maxMessageBytes = 256),
        FramingLimits(maxPacketBytes = 1000, maxMessageBytes = 4000),
        FramingLimits.preLogin(),
    )

    private class Outcome(val messages: List<TdsMessage>, val error: FramingError?, val assembler: MessageAssembler)

    private fun feedAll(limits: FramingLimits, chunks: List<ByteArray>): Outcome {
        val assembler = MessageAssembler(limits)
        val messages = mutableListOf<TdsMessage>()
        var error: FramingError? = null
        for (chunk in chunks) {
            val step = assembler.feed(chunk)
            messages += step.messages
            if (error == null) error = step.error
        }
        if (error == null) error = assembler.endOfInput().error
        return Outcome(messages, error, assembler)
    }

    private fun assertAgrees(expected: FramingOracle.Expected, got: Outcome, context: String) {
        assertEquals(expected.messages.size, got.messages.size, "Zahl der Nachrichten, $context")
        expected.messages.zip(got.messages).forEach { (want, have) ->
            assertEquals(want.type, have.type.code, "Typ, $context")
            assertContentEquals(want.payload, have.payload, "Nutzdaten, $context")
        }
        val error = got.error
        if (expected.defects.isEmpty()) {
            assertNull(error, "kein Fehler erwartet, $context")
            assertEquals(0, got.assembler.bufferedBytes, "Puffer nach sauberem Ende, $context")
        } else {
            assertNotNull(error, "Fehler ${expected.defects} erwartet, $context")
            val allowed = expected.defects.map { it.name }
            assertTrue(error.failure.name in allowed, "${error.failure} nicht in $allowed, $context")
            assertEquals(expected.buffered, got.assembler.bufferedBytes, "gepufferte Bytes, $context")
        }
    }

    private fun assertConsumed(got: Outcome, context: String) {
        val error = got.error ?: return
        val before = got.assembler.bufferedBytes
        val again = got.assembler.feed(ByteArray(FOLLOW_UP) { it.toByte() })
        assertTrue(again.messages.isEmpty(), "Nachricht nach dem Fehler, $context")
        assertEquals(error.failure, again.error?.failure, "Fehler nach dem Fehler, $context")
        assertEquals(before, got.assembler.bufferedBytes, "Puffer wächst nach dem Fehler, $context")
    }

    private class Tally {
        val counts = mutableMapOf<String, Int>()

        fun add(expected: FramingOracle.Expected) {
            val key = when {
                expected.defects.isEmpty() -> if (expected.messages.size >= 2) "OK_MEHRERE" else "OK"
                expected.defects.size == 1 -> expected.defects.single().name
                else -> "MEHRERE_REGELN"
            }
            counts.merge(key, 1, Int::plus)
        }

        fun count(key: String) = counts[key] ?: 0
    }

    /**
     * Pro Grenzenpaar 3000 Ströme aus beschädigten und unbeschädigten Nachrichten (Längen auf, knapp
     * über und weit über der Grenze, falsche und fremde Typen, abgeschnittene Enden), in einzelne
     * Bytes, ganze Ströme oder Stücke zufälliger Länge zerlegt: Nachrichten, Fehlerart, gepufferte
     * Bytes und die Zusage „nach dem ersten Fehler verbraucht“ stimmen mit der Referenz überein. Der
     * Zähler weist nach, dass jede Fehlerart und der Erfolg in jeder Grenzenkonfiguration vorkamen.
     *
     * @spec SPEC-027 SPEC-006 SPEC-007 SPEC-028 SPEC-046
     */
    @Test
    fun `Beschaedigte und zerstueckelte Straeme stimmen mit der Referenz ueberein`() {
        configs.forEachIndexed { index, limits ->
            val random = Random(SEED + index)
            val generator = WireGenerator(random, limits.maxPacketBytes, limits.maxMessageBytes)
            val tally = Tally()
            repeat(RUNS) { run ->
                val wire = generator.wire()
                val expected = FramingOracle(wire, limits.maxPacketBytes, limits.maxMessageBytes).expected()
                val got = feedAll(limits, generator.chunks(wire))
                val context = "Grenzen $index, Lauf $run, ${wire.size} Byte"
                assertAgrees(expected, got, context)
                assertConsumed(got, context)
                tally.add(expected)
            }
            assertTally(tally, limits, index)
        }
    }

    private fun assertTally(tally: Tally, limits: FramingLimits, index: Int) {
        val needed = mutableListOf("OK_MEHRERE", "UNKNOWN_PACKET_TYPE", "LENGTH_BELOW_HEADER", "MESSAGE_ABOVE_LIMIT")
        needed += listOf("PACKET_TYPE_CHANGED", "MESSAGE_TRUNCATED", "OK")
        if (limits.maxPacketBytes < FramingLimits.MAX_TDS_PACKET_BYTES) needed += "LENGTH_ABOVE_PACKET_LIMIT"
        needed.forEach {
            val seen = tally.count(it)
            assertTrue(seen >= MIN_PER_KIND, "Grenzen $index: $it nur ${seen}x in ${tally.counts}")
        }
    }

    /**
     * Die Grenze der Nachricht gilt einschließlich: die Summe der Paketlängen genau auf der Grenze wird
     * angenommen, ein Byte darüber abgewiesen (Summen 255, 256 und 257 bei der Grenze 256).
     *
     * @spec SPEC-007 SPEC-046
     */
    @Test
    fun `Nachrichtengrenze nimmt Summe auf der Grenze an und weist ein Byte darueber ab`() {
        val limits = FramingLimits(maxPacketBytes = 100, maxMessageBytes = 256)
        listOf(255 to true, 256 to true, 257 to false).forEach { (total, accepted) ->
            val count = (total + limits.maxPacketBytes - 1) / limits.maxPacketBytes
            val lengths = List(count) { total / count + if (it < total % count) 1 else 0 }
            val wire = lengths.withIndex().fold(ByteArray(0)) { acc, (i, length) ->
                acc + handPacket(0x01, if (i == count - 1) 1 else 0, ByteArray(length - 8) { 7 }, i + 1)
            }
            val got = feedAll(limits, listOf(wire))
            assertEquals(accepted, got.error == null, "Summe $total")
            assertEquals(if (accepted) 1 else 0, got.messages.size, "Summe $total")
        }
    }

    /**
     * Prüfung der Referenz an Handvektoren, die nicht aus dem Codec stammen: jede Regel, die der
     * Fuzz-Test der Referenz überlässt, ergibt hier den Fehler, den MS-TDS 2.2.3.1 und die Grenzen
     * verlangen. Eine Referenz, die alles annähme, würde den Fuzz-Test wertlos machen.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Referenz entscheidet Handvektoren wie die Spezifikation`() {
        val ok = handPacket(0x12, 1, byteArrayOf(1, 2, 3))
        assertEquals(setOf<Defect>(), defectsOf(ok))
        assertEquals(1, oracleOf(ok).messages.size)
        assertEquals(setOf(Defect.UNKNOWN_PACKET_TYPE), defectsOf(handPacket(0x05, 1, byteArrayOf())))
        assertEquals(setOf(Defect.LENGTH_BELOW_HEADER), defectsOf(byteArrayOf(0x12, 1, 0, 7, 0, 0, 1, 0)))
        assertEquals(setOf(Defect.LENGTH_ABOVE_PACKET_LIMIT), defectsOf(byteArrayOf(0x12, 1, 0, 101, 0, 0, 1, 0)))
        val otherType = ok.copyOf().also { it[0] = 0x10 }
        assertEquals(setOf(Defect.PACKET_TYPE_CHANGED), defectsOf(handPacket(0x12, 0, byteArrayOf()), otherType))
        assertEquals(setOf(Defect.MESSAGE_TRUNCATED), defectsOf(ok.copyOf(ok.size - 1)))
        assertEquals(setOf(Defect.MESSAGE_TRUNCATED), defectsOf(ok.copyOf(5)))
        assertEquals(2, oracleOf(ok.copyOf(ok.size - 1)).buffered)
        val big = handPacket(0x01, 0, ByteArray(92))
        assertEquals(setOf<Defect>(), defectsOf(big, big, handPacket(0x01, 1, ByteArray(48))))
        assertEquals(setOf(Defect.MESSAGE_ABOVE_LIMIT), defectsOf(big, big, handPacket(0x01, 1, ByteArray(49))))
    }

    private fun oracleOf(vararg parts: ByteArray) =
        FramingOracle(parts.reduce(ByteArray::plus), HAND_MAX_PACKET, HAND_MAX_MESSAGE).expected()

    private fun defectsOf(vararg parts: ByteArray) = oracleOf(*parts).defects

    private companion object {
        const val SEED = 20261030L
        const val RUNS = 3000
        const val MIN_PER_KIND = 25
        const val FOLLOW_UP = 20
        const val HAND_MAX_PACKET = 100
        const val HAND_MAX_MESSAGE = 256
    }
}
