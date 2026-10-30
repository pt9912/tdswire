package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.protocol.session.CloseReason
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.HandshakeDriver
import io.github.pt9912.tdswire.protocol.session.HandshakeLog
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.session.RecordingAdapter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.TransportHandshakeChannel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Fuzz des Anmeldepfads (`TDS-QA-02`): beschädigte PRELOGIN-/LOGIN7-Ströme gehen durch Paket-Codec,
 * Leser, Treiber und Automat. Das Orakel ist [FramingOracle]; es kennt weder Automat noch Parser.
 */
class HandshakeFuzzTest {
    private val limits = FramingLimits.preLogin()
    private val tally = mutableMapOf<String, Int>()

    private fun preloginWire(perPacket: Int): ByteArray =
        handMessage(0x12, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON), perPacket)

    private fun baseWire(perPacket: Int): ByteArray =
        preloginWire(perPacket) + handMessage(0x10, SessionVectors.login7Payload(), perPacket)

    private fun overwrite(random: Random, bytes: ByteArray, count: Int): ByteArray = bytes.also {
        repeat(count) { _ -> it[random.nextInt(it.size)] = random.nextInt().toByte() }
    }

    private fun insert(random: Random, bytes: ByteArray): ByteArray {
        val at = random.nextInt(bytes.size + 1)
        return bytes.copyOf(at) + random.nextBytes(random.nextInt(1, MAX_INSERT)) + bytes.copyOfRange(at, bytes.size)
    }

    private fun cutOut(random: Random, bytes: ByteArray): ByteArray {
        val at = random.nextInt(bytes.size)
        return bytes.copyOf(at) + bytes.copyOfRange(minOf(bytes.size, at + random.nextInt(1, MAX_CUT)), bytes.size)
    }

    private fun lengthByte(random: Random, base: ByteArray): ByteArray = base.copyOf().also {
        it[random.nextInt(LENGTH_FIELD_FIRST, LENGTH_FIELD_FIRST + 2)] = random.nextInt().toByte()
    }

    /**
     * Eine von elf Veränderungen des gültigen Stroms: Bytes überschreiben, einfügen, herausschneiden,
     * Zufallsbytes, ein Byte des Längenfelds, Ende an der Nachrichtengrenze, leer, ein unbekannter
     * Pakettyp hinter dem LOGIN7; Nr. 7 unverändert.
     */
    private fun damage(random: Random, base: ByteArray, preloginSize: Int): ByteArray = when (random.nextInt(KINDS)) {
        0 -> overwrite(random, base.copyOf(), random.nextInt(1, MAX_OVERWRITE))
        1 -> overwrite(random, base.copyOf(), 1)
        2 -> base.copyOf(random.nextInt(0, base.size))
        3 -> insert(random, base)
        4 -> cutOut(random, base)
        5 -> random.nextBytes(random.nextInt(0, GARBAGE_MAX))
        6 -> lengthByte(random, base)
        8 -> base.copyOf(preloginSize)
        9 -> ByteArray(0)
        10 -> base + CORRUPT_TAIL
        else -> base
    }

    private fun assertOutcome(outcome: HandshakeOutcome, expected: FramingOracle.Expected, end: ScriptEnd, calls: Int) {
        val types = expected.messages.map { it.type }
        assertTrue(calls <= 1, "Adapter $calls-mal gerufen")
        when (outcome) {
            is HandshakeOutcome.Ready -> {
                assertEquals(listOf(0x12, 0x10), types.take(2), "Ready ohne PRELOGIN und LOGIN7 im Strom")
                assertEquals(1, calls)
            }

            is HandshakeOutcome.Closed -> assertClosed(outcome.reason, expected, end)
        }
        tally.merge(name(outcome), 1, Int::plus)
    }

    private fun name(outcome: HandshakeOutcome): String = when (outcome) {
        is HandshakeOutcome.Ready -> "Ready"
        is HandshakeOutcome.Closed -> "Closed:${outcome.reason}"
    }

    private fun assertClosed(reason: CloseReason, expected: FramingOracle.Expected, end: ScriptEnd) {
        when (reason) {
            CloseReason.PEER_CLOSED -> {
                assertEquals(ScriptEnd.CLEAN_EOF, end)
                assertTrue(expected.defects.isEmpty(), "sauberes Ende trotz ${expected.defects}")
                assertTrue(expected.messages.size < 2, "Ende gelesen, obwohl PRELOGIN und LOGIN7 vollständig waren")
            }

            CloseReason.READ_FAILED -> {
                assertTrue(expected.defects.isNotEmpty() || end == ScriptEnd.IO_FAILURE, "Lesefehler ohne Ursache")
                assertTrue(expected.messages.size < 2, "Lesefehler hinter vollständigem PRELOGIN und LOGIN7")
            }

            CloseReason.WRITE_FAILED, CloseReason.LOGIN_TIMEOUT ->
                throw AssertionError("$reason ohne Schreibfehler oder Wanduhr nicht möglich")

            else -> Unit
        }
    }

    /**
     * 4000 beschädigte Ströme (Bytes überschrieben, abgeschnitten, eingefügt, herausgeschnitten, Längenfeld
     * verändert, Zufallsbytes, unverändert) mit festem Startwert, in Stücke zufälliger Länge geliefert
     * und mit sauberem oder fehlerhaftem Ende: der Treiber endet immer mit einem Ausgang, ohne
     * Ausnahme und ohne den Adapter öfter als einmal zu rufen. Ready gibt es nur, wenn die Referenz
     * PRELOGIN und LOGIN7 als erste Nachrichten sieht; ein sauberes Ende gibt es nur, wenn die Referenz keinen
     * Rahmenfehler findet; ein Lesefehler nur bei Rahmenfehler oder Kanalfehler. Der Zähler weist
     * nach, dass Annahme, Rahmenfehler, Protokollverstoß und sauberes Ende vorkamen.
     *
     * @spec SPEC-027 SPEC-028 SPEC-046 SPEC-006
     */
    @Test
    fun `Beschaedigte Anmeldestraeme enden immer in einem Ausgang des Treibers`() = blocking {
        val random = Random(SEED)
        val generator = WireGenerator(random, limits.maxPacketBytes, limits.maxMessageBytes)
        repeat(RUNS) {
            val perPacket = listOf(20, 100, 4000)[random.nextInt(3)]
            val wire = damage(random, baseWire(perPacket), preloginWire(perPacket).size)
            val end = if (random.nextInt(3) == 0) ScriptEnd.IO_FAILURE else ScriptEnd.CLEAN_EOF
            val channel = ScriptedChannel(generator.chunks(wire), end)
            val reader = MessageReader(channel, limits)
            val adapter = RecordingAdapter.accepting()
            val handshake = TransportHandshakeChannel(reader, MessageWriter(channel, PACKET), channel) { true }
            val outcome = HandshakeDriver(HandshakeConfig(), adapter, HandshakeLog.NONE, 10.seconds).run(handshake)
            val expected = FramingOracle(wire, limits.maxPacketBytes, limits.maxMessageBytes).expected()
            assertOutcome(outcome, expected, end, adapter.calls)
        }
        listOf("Ready", "Closed:READ_FAILED", "Closed:PROTOCOL_VIOLATION", "Closed:PEER_CLOSED").forEach {
            assertTrue((tally[it] ?: 0) >= MIN_PER_KIND, "$it nur ${tally[it]}x in $tally")
        }
    }

    private companion object {
        const val SEED = 20261031L
        const val RUNS = 4000
        const val KINDS = 11
        const val GARBAGE_MAX = 200
        val CORRUPT_TAIL = byteArrayOf(0x05, 1, 0, 8, 0, 0, 1, 0)
        const val MAX_INSERT = 21
        const val MAX_CUT = 30
        const val MAX_OVERWRITE = 5
        const val LENGTH_FIELD_FIRST = 2
        const val PACKET = 4096
        const val MIN_PER_KIND = 30
    }
}
