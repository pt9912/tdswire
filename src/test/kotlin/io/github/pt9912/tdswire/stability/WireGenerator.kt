package io.github.pt9912.tdswire.stability

import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Erzeugt Byteströme aus Nachrichten mit gezielt beschädigten Paketköpfen und zerlegt sie in Stücke.
 * Der Erzeuger kennt die Grenzen [maxPacket] und [maxMessage], damit er Längen auf die Grenze und
 * knapp darüber legt; was daraus folgt, sagt allein das [FramingOracle].
 */
class WireGenerator(private val random: Random, private val maxPacket: Int, private val maxMessage: Int) {
    private val kinds = FramingOracle.KNOWN_TYPES.toList()

    /** Ein bis vier Nachrichten, mit Wahrscheinlichkeit 1/4 am Ende an beliebiger Stelle abgeschnitten. */
    fun wire(): ByteArray {
        val out = ByteArrayOutputStream()
        repeat(random.nextInt(1, MAX_MESSAGES + 1)) { message(out) }
        val whole = out.toByteArray()
        return if (random.nextInt(CUT_ONE_IN) == 0) whole.copyOf(random.nextInt(0, whole.size + 1)) else whole
    }

    /** Zerlegung in ganze Ströme, einzelne Bytes oder Stücke zufälliger Länge. */
    fun chunks(wire: ByteArray): List<ByteArray> {
        val limit = when (random.nextInt(CHUNK_STRATEGIES)) {
            0 -> wire.size.coerceAtLeast(1)
            1 -> 1
            else -> random.nextInt(1, MAX_CHUNK + 1)
        }
        val result = mutableListOf<ByteArray>()
        var from = 0
        while (from < wire.size) {
            val to = minOf(wire.size, from + random.nextInt(1, limit + 1))
            result += wire.copyOfRange(from, to)
            from = to
        }
        return result
    }

    private fun message(out: ByteArrayOutputStream) {
        val type = kinds[random.nextInt(kinds.size)]
        val lengths = if (random.nextInt(BOUNDARY_ONE_IN) == 0) boundaryLengths() else randomLengths()
        lengths.forEachIndexed { index, length ->
            val header = header(type, if (index == lengths.lastIndex) END_OF_MESSAGE else 0, length, index + 1)
            if (random.nextInt(MUTATE_ONE_IN) == 0) mutate(header, length)
            out.write(header)
            out.write(random.nextBytes(length - FramingOracle.HEADER))
        }
    }

    private fun randomLengths(): List<Int> {
        val room = minOf(maxPacket - FramingOracle.HEADER, MAX_PAYLOAD)
        return List(random.nextInt(1, MAX_PACKETS + 1)) { FramingOracle.HEADER + random.nextInt(0, room + 1) }
    }

    /** Paketlängen, deren Summe `maxMessage` um -1, 0 oder +1 verfehlt. */
    private fun boundaryLengths(): List<Int> {
        val target = maxMessage + random.nextInt(-1, 2)
        val count = (target + maxPacket - 1) / maxPacket
        return List(count) { target / count + if (it < target % count) 1 else 0 }
    }

    private fun header(type: Int, status: Int, length: Int, id: Int) = byteArrayOf(
        type.toByte(),
        status.toByte(),
        (length ushr BYTE_BITS).toByte(),
        length.toByte(),
        0,
        0,
        id.toByte(),
        0,
    )

    private fun mutate(header: ByteArray, length: Int) {
        when (random.nextInt(MUTATIONS)) {
            0 -> header[0] = (if (random.nextBoolean()) random.nextInt(BYTE_RANGE) else kinds.random(random)).toByte()
            1 -> setLength(header, pickLength())
            2 -> header[1] = random.nextInt(BYTE_RANGE).toByte()
            3 -> setLength(header, (length + random.nextInt(-NEAR, NEAR + 1)).coerceIn(0, U16_MAX))
            else -> setLength(header, random.nextInt(0, FramingOracle.HEADER))
        }
    }

    private fun pickLength(): Int = when (random.nextInt(LENGTH_CHOICES)) {
        0 -> (maxPacket + 1).coerceAtMost(U16_MAX)
        1 -> U16_MAX
        else -> random.nextInt(0, U16_MAX + 1)
    }

    private fun setLength(header: ByteArray, length: Int) {
        header[2] = (length ushr BYTE_BITS).toByte()
        header[3] = length.toByte()
    }

    private companion object {
        const val MAX_MESSAGES = 4
        const val MAX_PACKETS = 4
        const val MAX_PAYLOAD = 300
        const val MAX_CHUNK = 50
        const val CHUNK_STRATEGIES = 3
        const val CUT_ONE_IN = 4
        const val BOUNDARY_ONE_IN = 8
        const val MUTATE_ONE_IN = 4
        const val MUTATIONS = 5
        const val LENGTH_CHOICES = 3
        const val NEAR = 3
        const val END_OF_MESSAGE = 1
        const val BYTE_BITS = 8
        const val BYTE_RANGE = 256
        const val U16_MAX = 0xFFFF
    }
}
