package io.github.pt9912.tdswire.testsupport

import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer

private const val TEST_TIMEOUT_MILLIS = 20_000L
private const val HEADER_BYTES = 8
private const val BYTE_BITS = 8

/** Führt [body] als Coroutine aus und bricht nach 20 s ab, damit ein hängender Test nicht den Lauf hält. */
fun blocking(body: suspend CoroutineScope.() -> Unit) {
    runBlocking { withTimeout(TEST_TIMEOUT_MILLIS) { body() } }
}

/**
 * Ein Paket von Hand gebaut, unabhängig vom Paket-Codec: Typ, Status, Länge (Big-Endian, samt
 * Header), SPID 0, Packet-ID, Window 0.
 */
fun handPacket(type: Int, status: Int, payload: ByteArray, packetId: Int = 1): ByteArray {
    val length = HEADER_BYTES + payload.size
    val header = byteArrayOf(
        type.toByte(),
        status.toByte(),
        (length ushr BYTE_BITS).toByte(),
        length.toByte(),
        0,
        0,
        packetId.toByte(),
        0,
    )
    return header + payload
}

/** Eine Nachricht von Hand in Pakete mit höchstens [payloadPerPacket] Nutzdatenbytes zerlegt (Status 1 im letzten). */
fun handMessage(type: Int, payload: ByteArray, payloadPerPacket: Int): ByteArray {
    var wire = ByteArray(0)
    var from = 0
    var id = 1
    do {
        val to = minOf(payload.size, from + payloadPerPacket)
        val last = to == payload.size
        wire += handPacket(type, if (last) 1 else 0, payload.copyOfRange(from, to), id++)
        from = to
    } while (!last)
    return wire
}

/** Liest bis zum ersten Ausgang, der keine Nachricht ist; liefert die Nachrichten und diesen Ausgang. */
suspend fun MessageReader.drain(): Pair<List<TdsMessage>, ReadOutcome> {
    val messages = mutableListOf<TdsMessage>()
    while (true) {
        when (val outcome = next()) {
            is ReadOutcome.Message -> messages += outcome.message
            else -> return messages to outcome
        }
    }
}

/** Reicht jeden Aufruf an [inner] weiter und zählt die gelesenen Bytes. */
class CountingChannel(private val inner: SuspendingChannel) : SuspendingChannel {
    var bytesRead = 0L
        private set

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
        val result = inner.read(buffer)
        if (result is ChannelResult.Ok && result.value > 0) bytesRead += result.value
        return result
    }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> = inner.writeFully(buffer)

    override fun close() = inner.close()
}
