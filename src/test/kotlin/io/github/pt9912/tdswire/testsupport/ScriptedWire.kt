package io.github.pt9912.tdswire.testsupport

import io.github.pt9912.tdswire.transport.channel.ChannelError
import io.github.pt9912.tdswire.transport.channel.ChannelFailure
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Kopf eines Pakets, das auf den Draht ging: Typ, Status, Länge samt Header und Packet-ID. */
data class WirePacket(val type: Int, val status: Int, val length: Int, val packetId: Int) {
    val endOfMessage: Boolean get() = status and EOM != 0

    private companion object {
        const val EOM = 1
    }
}

/**
 * Ein Draht für Tests ohne Uhr und ohne Netz: liest die [inbound] Bytes (danach Ende der Gegenstelle)
 * und nimmt Pakete an, wobei jeder Aufruf von `writeFully` genau ein Paket ist. Nach [stallAfterBytes]
 * Bytes nimmt er nichts mehr an: der Schreiber suspendiert, [stalled] ist gesetzt, und [release]
 * öffnet ihn wieder. Nach [failAfterPackets] angenommenen Paketen scheitert das Schreiben. Hängt
 * [retain] an, hält er alle Bytes und alle Köpfe; sonst nur Zähler, die ersten Nutzdatenbytes und das
 * letzte Paket, damit ein Lauf mit Millionen Zeilen keinen Speicher an den Draht bindet.
 */
class ScriptedWire(private val inbound: ByteArray, private val retain: Boolean = false) : SuspendingChannel {
    /** Gesetzt, sobald ein Schreibvorgang wegen [stallAfterBytes] wartet. */
    val stalled = CompletableDeferred<Unit>()

    private val gate = CompletableDeferred<Unit>()

    var stallAfterBytes: Long = Long.MAX_VALUE
    var failAfterPackets: Int = Int.MAX_VALUE

    /**
     * Nach so vielen angenommenen Paketen wirft der nächste Schreibvorgang einmal einen
     * `OutOfMemoryError`, nachdem der Schreiber das Paket aus seinem Puffer entnommen hat.
     */
    var outOfMemoryAfterPackets: Int = Int.MAX_VALUE
    private var outOfMemoryThrown = false

    /** Zähler seit Erzeugung oder dem letzten [startCounting]. */
    var packetCount = 0
        private set
    var wireBytes = 0L
        private set
    var payloadBytes = 0L
        private set
    var closed = false
        private set

    /** Die Köpfe der Pakete seit [startCounting]: alle mit `retain`, sonst die ersten [KEPT]. */
    val packets = mutableListOf<WirePacket>()
    private val head = ByteArrayOutputStream()
    private val everything = ByteArrayOutputStream()

    /** Alle angenommenen Bytes samt Headern seit [startCounting]; nur mit `retain`, sonst leer. */
    val wireContent: ByteArray get() = everything.toByteArray()
    private var lastPacket = ByteArray(0)

    /** Die ersten Nutzdatenbytes (mindestens [KEPT], falls so viele ankamen) seit [startCounting]. */
    val headBytes: ByteArray get() = head.toByteArray()

    /** Das letzte angenommene Paket samt Header. */
    val tail: ByteArray get() = lastPacket

    var readPosition = 0
        private set

    /** Die noch nicht gelesenen Bytes der Gegenstelle. */
    val unread: Int get() = inbound.size - readPosition

    /** Die Nutzdaten aller angenommenen Pakete hintereinander, ohne Header; nur mit `retain`. */
    fun payload(): ByteArray {
        val all = everything.toByteArray()
        val out = ByteArrayOutputStream()
        var at = 0
        while (at < all.size) {
            val length = ((all[at + 2].toInt() and BYTE) shl BYTE_BITS) or (all[at + 3].toInt() and BYTE)
            out.write(all, at + HEADER, length - HEADER)
            at += length
        }
        return out.toByteArray()
    }

    /** Setzt die Zähler zurück; was vorher (Login) geschrieben wurde, zählt nicht mehr. */
    fun startCounting() {
        packetCount = 0
        wireBytes = 0
        payloadBytes = 0
        packets.clear()
        head.reset()
        everything.reset()
    }

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
        if (readPosition >= inbound.size) return ChannelResult.Ok(-1)
        val count = minOf(buffer.remaining(), inbound.size - readPosition)
        buffer.put(inbound, readPosition, count)
        readPosition += count
        return ChannelResult.Ok(count)
    }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> {
        val packet = ByteArray(buffer.remaining())
        buffer.get(packet)
        if (packetCount >= outOfMemoryAfterPackets && !outOfMemoryThrown) {
            outOfMemoryThrown = true
            throw OutOfMemoryError("Text darf nicht zum Client")
        }
        if (packetCount >= failAfterPackets) {
            return ChannelResult.Failed(ChannelError(ChannelFailure.IO_FAILURE, "ScriptedWire"))
        }
        if (wireBytes + packet.size > stallAfterBytes) {
            stalled.complete(Unit)
            gate.await()
        }
        accept(packet)
        return ChannelResult.Ok(Unit)
    }

    private fun accept(packet: ByteArray) {
        val header = WirePacket(
            type = packet[0].toInt() and BYTE,
            status = packet[1].toInt() and BYTE,
            length = ((packet[2].toInt() and BYTE) shl BYTE_BITS) or (packet[3].toInt() and BYTE),
            packetId = packet[ID_OFFSET].toInt() and BYTE,
        )
        if (retain || packets.size < KEPT) packets += header
        if (retain) everything.write(packet)
        if (head.size() < KEPT) head.write(packet, HEADER, packet.size - HEADER)
        packetCount++
        wireBytes += packet.size
        payloadBytes += packet.size - HEADER
        lastPacket = packet
    }

    override fun close() {
        closed = true
    }

    companion object {
        const val KEPT = 64
        private const val HEADER = 8
        private const val BYTE = 0xFF
        private const val BYTE_BITS = 8
        private const val ID_OFFSET = 6
    }
}
