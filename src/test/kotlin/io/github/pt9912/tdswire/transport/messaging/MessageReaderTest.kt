package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.testsupport.CountingChannel
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.chunked
import io.github.pt9912.tdswire.testsupport.drain
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.framing.FramingFailure
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageReaderTest {
    private val payloadA = hex("11 22 33 44 55 66")
    private val payloadB = ByteArray(3000) { (it * 7 + 3).toByte() }

    /** Von Hand gebaut: SQL_BATCH in zwei Paketen, PRELOGIN in drei, ATTENTION als reines Header-Paket. */
    private val wire = handMessage(0x01, payloadA, 4) +
        handMessage(0x12, payloadB, 1024) +
        handMessage(0x06, ByteArray(0), 1024)
    private val expected = listOf(
        TdsMessage(PacketType.SQL_BATCH, payloadA),
        TdsMessage(PacketType.PRELOGIN, payloadB),
        TdsMessage(PacketType.ATTENTION, ByteArray(0)),
    )

    /** Schreibt [chunks] in einen Speicher-Kanal, schließt ihn und liest bis zum ersten Ausgang ohne Nachricht. */
    private suspend fun readFrom(
        chunks: List<ByteArray>,
        limits: FramingLimits = FramingLimits(),
        readBufferSize: Int = 64,
    ) = coroutineScope {
        val (sender, receiver) = MemoryChannel.pair(capacity = 16)
        val feeder = launch {
            chunks.forEach { sender.writeFully(ByteBuffer.wrap(it)) }
            sender.close()
        }
        val result = MessageReader(receiver, limits, readBufferSize).drain()
        feeder.cancel()
        result
    }

    /**
     * Orakel: die handgebaute Wire-Folge ergibt die drei Nachrichten, am Stück, byteweise und mit
     * zufälligen Stücklängen (fester Startwert); danach meldet der Leser das saubere Ende.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Zerstueckelte Eingabe ergibt dieselben Nachrichten`() = blocking {
        val whole = readFrom(listOf(wire))
        assertEquals(expected, whole.first)
        assertEquals(ReadOutcome.EndOfStream, whole.second)
        assertEquals(whole, readFrom(wire.map { byteArrayOf(it) }))
        val random = Random(SEED)
        repeat(RUNS) {
            val sizes = generateSequence { random.nextInt(1, 40) }
            assertEquals(whole, readFrom(chunked(wire, sizes)))
        }
    }

    /**
     * Der Leser reicht die Grenze an den Paket-Codec weiter: eine Nachricht von 4 Paketen zu je
     * 1024 Bytes (Summe 4096) liegt auf der Grenze 4096 und kommt an, ein fünftes Paket
     * überschreitet sie und ergibt `MESSAGE_ABOVE_LIMIT`.
     *
     * @spec SPEC-007
     */
    @Test
    fun `Nachrichtengrenze ist ein Parameter des Lesers`() = blocking {
        val limits = FramingLimits(maxPacketBytes = 1024, maxMessageBytes = 4096)
        val atLimit = handMessage(0x01, ByteArray(4 * 1016), 1016)
        val overLimit = handMessage(0x01, ByteArray(5 * 1016), 1016)
        assertEquals(1, readFrom(listOf(atLimit), limits).first.size)
        val (messages, outcome) = readFrom(listOf(overLimit), limits)
        assertTrue(messages.isEmpty())
        val failed = assertIs<ReadOutcome.Failed>(outcome)
        val framing = assertIs<TransportError.Framing>(failed.error)
        assertEquals(FramingFailure.MESSAGE_ABOVE_LIMIT, framing.error.failure)
    }

    /**
     * Die Grenze löst aus, bevor die Nachricht gepuffert ist: 200 Pakete zu je 1024 Bytes (204800
     * Bytes) liegen bereit, die Grenze ist 4096. Gelesen werden höchstens die Bytes bis zum Header
     * des fünften Pakets (4104) plus ein Lesevorgang von 512 Bytes, und danach liest der Leser
     * nicht weiter.
     *
     * @spec SPEC-046
     */
    @Test
    fun `Ueberschreitung bricht ab bevor die Nachricht gepuffert ist`() = blocking {
        val limits = FramingLimits(maxPacketBytes = 1024, maxMessageBytes = 4096)
        val huge = handMessage(0x12, ByteArray(200 * 1016), 1016)
        val (sender, receiver) = MemoryChannel.pair(capacity = 1024)
        val feeder = launch { sender.writeFully(ByteBuffer.wrap(huge)) }
        val counting = CountingChannel(receiver)
        val reader = MessageReader(counting, limits, readBufferSize = 512)
        val failed = assertIs<ReadOutcome.Failed>(reader.next())
        val framing = assertIs<TransportError.Framing>(failed.error)
        assertEquals(FramingFailure.MESSAGE_ABOVE_LIMIT, framing.error.failure)
        val readAtFailure = counting.bytesRead
        assertTrue(readAtFailure <= 4096 + 8 + 512, "gelesen: $readAtFailure")
        assertTrue(readAtFailure < huge.size / 10)
        assertEquals(failed, reader.next())
        assertEquals(readAtFailure, counting.bytesRead)
        feeder.cancel()
    }

    /**
     * Die Grenze vor dem Login ist `FramingLimits.preLogin()` (65536, `SPEC-046`): zwei Pakete zu
     * 65535 Bytes (Summe 131070) werden abgewiesen, ein Paket der Länge 65535 nicht.
     *
     * @spec SPEC-046
     */
    @Test
    fun `Grenze vor dem Login weist grosse Nachrichten ab`() = blocking {
        val single = handMessage(0x10, ByteArray(65535 - 8), 65535 - 8)
        val double = handMessage(0x10, ByteArray(2 * (65535 - 8)), 65535 - 8)
        assertEquals(1, readFrom(listOf(single), FramingLimits.preLogin(), 4096).first.size)
        val outcome = readFrom(listOf(double), FramingLimits.preLogin(), 4096).second
        val failed = assertIs<ReadOutcome.Failed>(outcome)
        assertEquals(
            FramingFailure.MESSAGE_ABOVE_LIMIT,
            assertIs<TransportError.Framing>(failed.error).error.failure,
        )
    }

    /**
     * Orakel: bricht die Eingabe an jeder Stelle der Wire-Folge ab, ist das Ergebnis ein
     * Ergebniswert. Liegt der Abbruch auf einer Nachrichtengrenze
     * (0, 22, 3046 und das Ende; Nutzdaten plus 8 Byte Header je Hand-Paket), ist es das saubere
     * Ende nach den vollständigen Nachrichten, sonst `MESSAGE_TRUNCATED`.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Abgeschnittene Eingabe endet in einem Ergebniswert`() = blocking {
        val endOfA = 6 + 2 * 8
        val endOfB = endOfA + 3000 + 3 * 8
        val boundaries = mapOf(0 to 0, endOfA to 1, endOfB to 2, wire.size to 3)
        for (cut in 0..wire.size) {
            val (messages, outcome) = readFrom(listOf(wire.copyOf(cut)))
            val complete = boundaries[cut]
            if (complete != null) {
                assertEquals(expected.take(complete), messages, "cut $cut")
                assertEquals(ReadOutcome.EndOfStream, outcome, "cut $cut")
            } else {
                val failed = assertIs<ReadOutcome.Failed>(outcome, "cut $cut")
                val framing = assertIs<TransportError.Framing>(failed.error)
                assertEquals(FramingFailure.MESSAGE_TRUNCATED, framing.error.failure, "cut $cut")
            }
        }
    }

    /**
     * Vollendete Nachrichten gehen dem Fehler voran: eine gültige Nachricht, dann ein Header mit
     * unbekanntem Pakettyp (0x77). Der Fehler bleibt bei jedem weiteren Aufruf derselbe.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Nachricht vor dem Fehler wird geliefert`() = blocking {
        val input = handPacket(0x01, 1, payloadA) + handPacket(0x77, 1, ByteArray(0))
        val (messages, outcome) = readFrom(listOf(input))
        assertEquals(listOf(TdsMessage(PacketType.SQL_BATCH, payloadA)), messages)
        val failed = assertIs<ReadOutcome.Failed>(outcome)
        assertEquals(
            FramingFailure.UNKNOWN_PACKET_TYPE,
            assertIs<TransportError.Framing>(failed.error).error.failure,
        )
    }

    /**
     * Zufällige Eingaben (fester Startwert, Längen 0 bis 300, teils mit gültigem Header-Anfang)
     * in zufälligen Stücken enden immer in `EndOfStream` oder `Failed`; es fliegt keine Ausnahme,
     * und der Ausgang wiederholt sich beim nächsten Aufruf.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zufaellige Eingaben enden in Ergebniswerten`() = blocking {
        val random = Random(SEED)
        var failures = 0
        repeat(FUZZ_RUNS) {
            val noise = random.nextBytes(random.nextInt(0, 300))
            val validStart = handPacket(0x01, 0, ByteArray(random.nextInt(0, 40)))
            val input = if (random.nextBoolean()) validStart + noise else noise
            val (_, outcome) = readFrom(chunked(input, generateSequence { random.nextInt(1, 50) }))
            if (outcome is ReadOutcome.Failed) failures++
            assertTrue(outcome is ReadOutcome.Failed || outcome == ReadOutcome.EndOfStream)
        }
        assertTrue(failures > 0, "kein Lauf endete in einem Fehler: der Fuzz erreicht die Fehlerpfade nicht")
    }

    private companion object {
        const val SEED = 20_261_030
        const val RUNS = 50
        const val FUZZ_RUNS = 300
    }
}
