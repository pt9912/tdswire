package io.github.pt9912.tdswire.transport.framing

import io.github.pt9912.tdswire.testsupport.chunked
import io.github.pt9912.tdswire.testsupport.hex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageAssemblerTest {
    private val twoPackets = hex(
        "01 00 00 0C 00 00 01 00 11 22 33 44 " +
            "01 01 00 0A 00 00 02 00 55 66",
    )

    private fun feedAll(assembler: MessageAssembler, chunks: List<ByteArray>): AssemblyStep {
        val messages = mutableListOf<TdsMessage>()
        var error: FramingError? = null
        chunks.forEach { chunk ->
            val step = assembler.feed(chunk)
            messages += step.messages
            error = error ?: step.error
        }
        return AssemblyStep(messages, error)
    }

    /**
     * Orakel: zwei Pakete (Status 00 und 01, Längen 12 und 10) ergeben eine SQL_BATCH-Nachricht
     * aus den Nutzdaten beider (`11 22 33 44 55 66`).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Pakete bis EOM ergeben eine Nachricht`() {
        val step = MessageAssembler().feed(twoPackets)
        assertNull(step.error)
        assertEquals(1, step.messages.size)
        assertEquals(PacketType.SQL_BATCH, step.messages[0].type)
        assertContentEquals(hex("11 22 33 44 55 66"), step.messages[0].payload)
    }

    /**
     * Festlegung ohne Messung: Der Leser prüft die Packet-ID nicht; Pakete mit nicht fortlaufenden
     * IDs (7, dann 2, dann 255) ergeben dieselbe Nachricht wie fortlaufende.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Nicht fortlaufende Packet-IDs werden angenommen`() {
        val wire = hex("01 00 00 09 00 00 07 00 11 01 00 00 09 00 00 02 00 22 01 01 00 09 00 00 FF 00 33")
        val step = MessageAssembler().feed(wire)
        assertNull(step.error)
        assertContentEquals(hex("11 22 33"), step.messages.single().payload)
    }

    /**
     * Die Zerstückelung ändert das Ergebnis nicht: byteweise und mit zufälligen Stücklängen
     * (fester Startwert) kommt dieselbe Nachricht heraus wie am Stück.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Zerstueckelte Eingabe ergibt dieselbe Nachricht`() {
        val expected = MessageAssembler().feed(twoPackets).messages
        val bytewise = feedAll(MessageAssembler(), twoPackets.map { byteArrayOf(it) })
        assertEquals(expected, bytewise.messages)
        val random = Random(SEED)
        repeat(RUNS) {
            val sizes = generateSequence { random.nextInt(1, 8) }
            val step = feedAll(MessageAssembler(), chunked(twoPackets, sizes))
            assertNull(step.error)
            assertEquals(expected, step.messages)
        }
    }

    /**
     * Ein Aufruf darf mehrere Nachrichten vollenden; die Nachricht danach beginnt neu (Typ und
     * Summe der Längen), auch wenn sie einen anderen Typ trägt.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Mehrere Nachrichten in einem Aufruf`() {
        val wire = hex("01 01 00 09 00 00 01 00 AA 03 01 00 0A 00 00 01 00 BB CC")
        val step = MessageAssembler().feed(wire)
        assertNull(step.error)
        assertEquals(listOf(PacketType.SQL_BATCH, PacketType.RPC), step.messages.map { it.type })
        assertContentEquals(hex("BB CC"), step.messages[1].payload)
    }

    /**
     * Grenze: ein Header-Paket der Länge 8 (ATTENTION) ist eine Nachricht ohne Nutzdaten.
     *
     * @spec SPEC-029
     */
    @Test
    fun `Header-Paket ohne Nutzdaten ist eine leere Nachricht`() {
        val step = MessageAssembler().feed(hex("06 01 00 08 00 00 01 00"))
        assertEquals(listOf(PacketType.ATTENTION), step.messages.map { it.type })
        assertEquals(0, step.messages[0].payload.size)
    }

    /**
     * Orakel: RESETCONNECTION (0x08) im ersten Paket und IGNORE (0x02) im letzten werden an der
     * Nachricht sichtbar (MS-TDS 2.2.3.1.2).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Reset-Connection und Ignore werden gemeldet`() {
        val wire = hex("01 08 00 09 00 00 01 00 AA 01 03 00 09 00 00 02 00 BB")
        val message = MessageAssembler().feed(wire).messages.single()
        assertTrue(message.resetConnection)
        assertTrue(message.ignore)
        val plain = MessageAssembler().feed(twoPackets).messages.single()
        assertFalse(plain.resetConnection)
        assertFalse(plain.ignore)
    }

    /**
     * Grenze: die Summe der Paketlängen darf die Nachrichtengrenze erreichen (100) und nicht
     * überschreiten (101).
     *
     * @spec SPEC-007
     */
    @Test
    fun `Nachrichtengrenze gilt einschliesslich`() {
        val limits = FramingLimits(maxPacketBytes = 60, maxMessageBytes = 100)
        val first = hex("01 00 00 3C 00 00 01 00") + ByteArray(52)
        val atLimit = hex("01 01 00 28 00 00 02 00") + ByteArray(32)
        val overLimit = hex("01 01 00 29 00 00 02 00") + ByteArray(33)
        assertNull(MessageAssembler(limits).feed(first + atLimit).error)
        val step = MessageAssembler(limits).feed(first + overLimit)
        assertEquals(FramingFailure.MESSAGE_ABOVE_LIMIT, assertNotNull(step.error).failure)
    }

    /**
     * Die Nachrichtengrenze greift am Header, bevor die Nutzdaten des überschreitenden Pakets
     * und damit die Nachricht gepuffert sind: nach dem Fehler stehen nur die 52 Nutzdatenbytes des
     * ersten Pakets im Puffer, und weitere Eingabe ändert daran nichts.
     *
     * @spec SPEC-007
     */
    @Test
    fun `Abbruch bevor die Nachricht gepuffert ist`() {
        val limits = FramingLimits(maxPacketBytes = 60, maxMessageBytes = 100)
        val assembler = MessageAssembler(limits)
        assertNull(assembler.feed(hex("01 00 00 3C 00 00 01 00") + ByteArray(52)).error)
        assertEquals(52, assembler.bufferedBytes)
        val step = assembler.feed(hex("01 01 00 29 00 00 02 00"))
        assertEquals(FramingFailure.MESSAGE_ABOVE_LIMIT, assertNotNull(step.error).failure)
        assertEquals(52, assembler.bufferedBytes)
        assembler.feed(ByteArray(33))
        assertEquals(52, assembler.bufferedBytes)
    }

    /**
     * Vor abgeschlossenem Login gilt 65536 (`SPEC-046`): 65536 Bytes Summe sind erlaubt, 65537 nicht.
     *
     * @spec SPEC-046
     */
    @Test
    fun `Grenze vor dem Login ist 65536`() {
        val limits = FramingLimits.preLogin()
        assertEquals(65536, limits.maxMessageBytes)
        val last = hex("12 01 00 08 00 00 02 00")
        val atLimit = hex("12 00 FF F8 00 00 01 00") + ByteArray(65520) + last
        assertNull(MessageAssembler(limits).feed(atLimit).error)
        val overLimit = hex("12 00 FF F9 00 00 01 00") + ByteArray(65521) + last
        val step = MessageAssembler(limits).feed(overLimit)
        assertEquals(FramingFailure.MESSAGE_ABOVE_LIMIT, assertNotNull(step.error).failure)
    }

    /**
     * Negativfall: ein Paket mit anderem Pakettyp mitten in der Nachricht ist ein Protokollfehler.
     *
     * @spec SPEC-011
     */
    @Test
    fun `Typwechsel innerhalb einer Nachricht ist ein Fehler`() {
        val step = MessageAssembler().feed(hex("01 00 00 09 00 00 01 00 AA 03 01 00 09 00 00 02 00 BB"))
        assertEquals(FramingFailure.PACKET_TYPE_CHANGED, assertNotNull(step.error).failure)
        assertTrue(step.messages.isEmpty())
    }

    /**
     * Negativfall: ein Fehler in der Mitte der Eingabe lässt die zuvor vollendeten Nachrichten im
     * Ergebnis, der Assembler ist danach verbraucht und meldet denselben Fehler.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Fehler ist dauerhaft und bewahrt fertige Nachrichten`() {
        val assembler = MessageAssembler()
        val step = assembler.feed(hex("01 01 00 09 00 00 01 00 AA 05 01 00 09 00 00 01 00 BB"))
        assertEquals(1, step.messages.size)
        val error = assertNotNull(step.error)
        assertEquals(FramingFailure.UNKNOWN_PACKET_TYPE, error.failure)
        val later = assembler.feed(hex("01 01 00 09 00 00 01 00 AA"))
        assertEquals(error, later.error)
        assertTrue(later.messages.isEmpty())
        assertEquals(error, assembler.endOfInput().error)
    }

    /**
     * Negativfall: endet die Eingabe im Header oder in den Nutzdaten, meldet `endOfInput` einen
     * Fehler; am Nachrichtenende und im Leerlauf meldet es keinen.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Abgeschnittene Eingabe wird am Ende gemeldet`() {
        for (cut in 1 until twoPackets.size) {
            val assembler = MessageAssembler()
            assembler.feed(twoPackets.copyOf(cut))
            val end = assembler.endOfInput()
            assertEquals(FramingFailure.MESSAGE_TRUNCATED, assertNotNull(end.error).failure, "Schnitt bei $cut")
        }
        val whole = MessageAssembler()
        whole.feed(twoPackets)
        assertNull(whole.endOfInput().error)
        assertNull(MessageAssembler().endOfInput().error)
    }

    /**
     * Die Grenzen sind zur Laufzeit änderbar: der Wechsel von der Grenze vor dem Login auf die
     * große Grenze gilt für die folgenden Header.
     *
     * @spec SPEC-046
     */
    @Test
    fun `Grenzen koennen nach dem Login gewechselt werden`() {
        val assembler = MessageAssembler(FramingLimits(maxPacketBytes = 60, maxMessageBytes = 60))
        assertNotNull(assembler.feed(hex("01 01 00 3D 00 00 01 00")).error)
        val fresh = MessageAssembler(FramingLimits(maxPacketBytes = 60, maxMessageBytes = 60))
        fresh.limits = FramingLimits(maxPacketBytes = 61, maxMessageBytes = 61)
        assertNull(fresh.feed(hex("01 01 00 3D 00 00 01 00")).error)
    }

    /**
     * Fuzz mit festem Startwert (`SPEC-027`, `TDS-QA-02`): zufällige Bytes, zufällig zerstückelt,
     * und gültige Nachrichten mit zufällig verändertem Byte enden in einem Ergebniswert, nie in
     * einer ungefangenen Ausnahme; die Obergrenze des Puffers bleibt eingehalten.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zufaellige und veraenderte Eingaben enden in einem Ergebniswert`() {
        val random = Random(SEED)
        val limits = FramingLimits(maxPacketBytes = 200, maxMessageBytes = 1000)
        repeat(FUZZ_RUNS) {
            val noise = random.nextBytes(random.nextInt(0, 300))
            val mutated = twoPackets.copyOf()
            mutated[random.nextInt(mutated.size)] = random.nextInt().toByte()
            listOf(noise, mutated).forEach { input ->
                val assembler = MessageAssembler(limits)
                feedAll(assembler, chunked(input, generateSequence { random.nextInt(1, 20) }))
                assembler.endOfInput()
                assertTrue(assembler.bufferedBytes <= limits.maxMessageBytes)
            }
        }
    }

    private companion object {
        const val SEED = 20261030
        const val RUNS = 200
        const val FUZZ_RUNS = 2000
    }
}
