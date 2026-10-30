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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Fuzz des Anmeldepfads (`TDS-QA-02`): beschädigte PRELOGIN-/LOGIN7-Ströme gehen durch Paket-Codec,
 * Leser, Treiber und Automat. Das Orakel ist [FramingOracle]; es kennt weder Automat noch Parser.
 */
class HandshakeFuzzTest {
    private val limits = FramingLimits.preLogin()
    private val tally = mutableMapOf<String, Int>()

    private val prelogin = SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON)
    private val login7 = SessionVectors.login7Payload()

    private fun wireOf(preloginPayload: ByteArray, login7Payload: ByteArray, perPacket: Int): ByteArray =
        handMessage(0x12, preloginPayload, perPacket) + handMessage(0x10, login7Payload, perPacket)

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

    /** Ein Strom und die Art der Veränderung, aus der er stammt. */
    private class Draw(val kind: Int, val wire: ByteArray)

    /**
     * Eine von zwölf Veränderungen des gültigen Stroms: Bytes überschreiben, einfügen, herausschneiden,
     * Zufallsbytes, ein Byte des Längenfelds, Ende an der Nachrichtengrenze, leer, ein unbekannter
     * Pakettyp hinter dem LOGIN7, ein Offset- oder Längenfeld in PRELOGIN oder LOGIN7; Nr. 7 unverändert.
     */
    private fun damage(random: Random, perPacket: Int): Draw {
        val base = wireOf(prelogin, login7, perPacket)
        val kind = random.nextInt(KINDS)
        val wire = when (kind) {
            0 -> overwrite(random, base.copyOf(), random.nextInt(1, MAX_OVERWRITE))
            1 -> overwrite(random, base.copyOf(), 1)
            2 -> base.copyOf(random.nextInt(0, base.size))
            3 -> insert(random, base)
            4 -> cutOut(random, base)
            5 -> random.nextBytes(random.nextInt(0, GARBAGE_MAX))
            6 -> lengthByte(random, base)
            8 -> base.copyOf(handMessage(0x12, prelogin, perPacket).size)
            9 -> ByteArray(0)
            10 -> base + CORRUPT_TAIL
            OFFSET_KIND -> offsetField(random, perPacket)
            else -> base
        }
        return Draw(kind, wire)
    }

    /**
     * Setzt genau ein Offset- oder Längenfeld einer Nachricht auf einen Randwert (0, 1, Rand des festen
     * Teils, Nachrichtenlänge -1, 0 und +1, 65535) oder einen Zufallswert. In PRELOGIN ist es Offset
     * oder Länge (Big-Endian) eines Tabelleneintrags, in LOGIN7 eines der Wörter der Slot-Tabelle ab
     * Byte 36 oder das Längenfeld am Anfang (Little-Endian); beide Lagen stehen im Parser des Repos.
     */
    private fun offsetField(random: Random, perPacket: Int): ByteArray {
        val inLogin = random.nextBoolean()
        val payload = (if (inLogin) login7 else prelogin).copyOf()
        val at = if (inLogin) loginFieldAt(random) else preloginFieldAt(random, payload)
        val value = (edges(payload.size) + random.nextInt(U16)).random(random)
        val low = (value and BYTE_MASK).toByte()
        val high = (value ushr BYTE_BITS and BYTE_MASK).toByte()
        payload[at] = if (inLogin) low else high
        payload[at + 1] = if (inLogin) high else low
        return if (inLogin) wireOf(prelogin, payload, perPacket) else wireOf(payload, login7, perPacket)
    }

    private fun edges(size: Int) =
        listOf(0, 1, LOGIN_FIXED - 1, LOGIN_FIXED, LOGIN_FIXED + 1, size - 1, size, size + 1, U16 - 1)

    private fun loginFieldAt(random: Random): Int =
        if (random.nextInt(LENGTH_ONE_IN) == 0) 0 else SLOTS_FROM + 2 * random.nextInt((LOGIN_FIXED - SLOTS_FROM) / 2)

    private fun preloginFieldAt(random: Random, payload: ByteArray): Int {
        val entries = generateSequence(0) { it + ENTRY }.takeWhile { payload[it] != TERMINATOR }.count()
        return ENTRY * random.nextInt(entries) + if (random.nextBoolean()) 1 else 3
    }

    private fun assertOutcome(
        outcome: HandshakeOutcome,
        expected: FramingOracle.Expected,
        script: ScriptedChannel,
        end: ScriptEnd,
        calls: Int,
    ): String {
        val types = expected.messages.map { it.type }
        assertTrue(calls <= 1, "Adapter $calls-mal gerufen")
        when (outcome) {
            is HandshakeOutcome.Ready -> {
                assertEquals(listOf(0x12, 0x10), types.take(2), "Ready ohne PRELOGIN und LOGIN7 im Strom")
                assertEquals(1, calls)
                assertFalse(script.closed, "Kanal nach Ready geschlossen")
                assertTrue(script.bytesWritten > 0, "Ready ohne Antwort an die Gegenstelle")
            }

            is HandshakeOutcome.Closed -> {
                assertClosed(outcome.reason, expected, end)
                assertTrue(script.closed, "Kanal nach ${outcome.reason} offen")
            }
        }
        return name(outcome, expected, end)
    }

    /**
     * Der Name des Ausgangs für den Zähler; der Lesefehler trägt seine Ursache: Rahmenfehler im Strom
     * oder Fehler des Kanals (ein Strom, der nur abgeschnitten ist und mit E/A-Fehler endet, ist einer).
     */
    private fun name(outcome: HandshakeOutcome, expected: FramingOracle.Expected, end: ScriptEnd): String {
        val reason = (outcome as? HandshakeOutcome.Closed)?.reason
        return when {
            reason == null -> "Ready"

            reason == CloseReason.READ_FAILED ->
                "Closed:READ_FAILED:" + if (framingError(expected, end)) FRAMING else CHANNEL

            else -> "Closed:$reason"
        }
    }

    private fun framingError(expected: FramingOracle.Expected, end: ScriptEnd): Boolean =
        expected.defects.any { it != FramingOracle.Defect.MESSAGE_TRUNCATED } ||
            (expected.defects.isNotEmpty() && end == ScriptEnd.CLEAN_EOF)

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
     * verändert, Offset- und Längenfelder in PRELOGIN und LOGIN7 auf Randwerte gesetzt, Zufallsbytes,
     * unverändert) mit festem Startwert, in Stücke zufälliger Länge geliefert
     * und mit sauberem oder fehlerhaftem Ende: der Treiber endet immer mit einem Ausgang, ohne
     * Ausnahme und ohne den Adapter öfter als einmal zu rufen. Ready gibt es nur, wenn die Referenz
     * PRELOGIN und LOGIN7 als erste Nachrichten sieht; ein sauberes Ende gibt es nur, wenn die Referenz keinen
     * Rahmenfehler findet; ein Lesefehler nur bei Rahmenfehler oder Kanalfehler. Nach Ready ist der Kanal
     * offen und hat Bytes empfangen, nach jedem anderen Ausgang ist er geschlossen. Der Zähler weist nach,
     * dass Annahme, Rahmenfehler und Kanalfehler als getrennte Lesefehler, Protokollverstoß, sauberes
     * Ende, Protokollverstoß durch ein verändertes Offset- oder Längenfeld und mindestens ein Drittel
     * zerstückelter Läufe vorkamen.
     *
     * @spec SPEC-027 SPEC-028 SPEC-046 SPEC-006
     */
    @Test
    fun `Beschaedigte Anmeldestraeme enden immer in einem Ausgang des Treibers`() = blocking {
        val random = Random(SEED)
        val generator = WireGenerator(random, limits.maxPacketBytes, limits.maxMessageBytes)
        repeat(RUNS) {
            val perPacket = listOf(20, 100, 4000)[random.nextInt(3)]
            val draw = damage(random, perPacket)
            val wire = draw.wire
            val end = if (random.nextInt(3) == 0) ScriptEnd.IO_FAILURE else ScriptEnd.CLEAN_EOF
            val chunks = generator.chunks(wire)
            if (chunks.size >= 2) tally.merge(CHUNKED, 1, Int::plus)
            val channel = ScriptedChannel(chunks, end)
            val reader = MessageReader(channel, limits)
            val adapter = RecordingAdapter.accepting()
            val handshake = TransportHandshakeChannel(reader, MessageWriter(channel, PACKET), channel) { true }
            val outcome = HandshakeDriver(HandshakeConfig(), adapter, HandshakeLog.NONE, 10.seconds).run(handshake)
            val expected = FramingOracle(wire, limits.maxPacketBytes, limits.maxMessageBytes).expected()
            val name = assertOutcome(outcome, expected, channel, end, adapter.calls)
            tally.merge(name, 1, Int::plus)
            if (draw.kind == OFFSET_KIND) tally.merge("Offset:$name", 1, Int::plus)
        }
        listOf("Ready", "Closed:READ_FAILED:$FRAMING", "Closed:READ_FAILED:$CHANNEL").forEach {
            assertTrue((tally[it] ?: 0) >= MIN_PER_KIND, "$it nur ${tally[it]}x in $tally")
        }
        listOf("Closed:PROTOCOL_VIOLATION", "Closed:PEER_CLOSED", "Offset:Closed:PROTOCOL_VIOLATION").forEach {
            assertTrue((tally[it] ?: 0) >= MIN_PER_KIND, "$it nur ${tally[it]}x in $tally")
        }
        assertTrue((tally[CHUNKED] ?: 0) >= RUNS / 3, "zerstückelt nur ${tally[CHUNKED]}x in $tally")
    }

    private companion object {
        const val SEED = 20261031L
        const val RUNS = 4000
        const val KINDS = 12
        const val OFFSET_KIND = 11
        const val FRAMING = "RAHMEN"
        const val CHANNEL = "KANAL"
        const val CHUNKED = "ZERSTUECKELT"
        const val LENGTH_ONE_IN = 8
        const val SLOTS_FROM = 36
        const val LOGIN_FIXED = 94
        const val ENTRY = 5
        const val U16 = 0x10000
        const val BYTE_BITS = 8
        const val BYTE_MASK = 0xFF
        val TERMINATOR = 0xFF.toByte()
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
