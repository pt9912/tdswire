package io.github.pt9912.tdswire.transport.framing

import java.io.ByteArrayOutputStream

/** Stand eines [MessageAssembler] nach einer Eingabe: fertige Nachrichten und der erste Fehler. */
data class AssemblyStep(val messages: List<TdsMessage>, val error: FramingError?)

/**
 * Setzt Nachrichten aus beliebig zerstückelten Bytes zusammen (`TDS-FA-01.a`, `SPEC-028`).
 * Paketgrenzen sind keine Grenzen der Eingabe: ein Aufruf von [feed] darf mitten in einem Header
 * oder in den Nutzdaten enden, und ein Aufruf darf mehrere Nachrichten vollenden.
 *
 * Die Nachrichtengrenze ([FramingLimits.maxMessageBytes]) wird am Header geprüft, sobald er
 * vollständig ist: überschreitet die Summe der Paketlängen die Grenze, bricht der Assembler ab,
 * bevor die Nutzdaten dieses Pakets gepuffert sind. Nach dem ersten Fehler ist der Assembler
 * verbraucht: jeder weitere Aufruf meldet denselben Fehler und puffert nichts.
 *
 * Festlegung: Alle Pakete einer Nachricht tragen denselben Pakettyp, sonst ist es ein
 * Protokollfehler. Nicht thread-sicher; ein Assembler gehört einer Verbindung.
 */
class MessageAssembler(var limits: FramingLimits = FramingLimits()) {
    private val headerBuffer = ByteArray(PacketHeader.SIZE)
    private var headerFill = 0
    private var packet: PacketHeader? = null
    private var packetPayloadLeft = 0
    private val payload = ByteArrayOutputStream()
    private var messageType: PacketType? = null
    private var messageLength = 0L
    private var firstStatus = PacketStatus.NORMAL
    private var failure: FramingError? = null

    private val insideMessage: Boolean get() = headerFill > 0 || packet != null || messageType != null

    /** Anzahl gepufferter Nutzdatenbytes der laufenden Nachricht. */
    val bufferedBytes: Int get() = payload.size()

    /** Verarbeitet [chunk] (auch leer) und liefert die damit vollendeten Nachrichten. */
    fun feed(chunk: ByteArray): AssemblyStep {
        val done = mutableListOf<TdsMessage>()
        var position = 0
        while (position < chunk.size && failure == null) {
            position += if (packet == null) readHeader(chunk, position, done) else readPayload(chunk, position, done)
        }
        return AssemblyStep(done, failure)
    }

    /** Meldet das Ende der Eingabe: ein unvollständiger Header oder eine halbe Nachricht ist ein Fehler. */
    fun endOfInput(): AssemblyStep {
        if (failure == null && insideMessage) {
            failure = FramingError(FramingFailure.MESSAGE_TRUNCATED, "Eingabe endet innerhalb einer Nachricht")
        }
        return AssemblyStep(emptyList(), failure)
    }

    private fun readHeader(chunk: ByteArray, from: Int, done: MutableList<TdsMessage>): Int {
        val take = minOf(PacketHeader.SIZE - headerFill, chunk.size - from)
        chunk.copyInto(headerBuffer, headerFill, from, from + take)
        headerFill += take
        if (headerFill == PacketHeader.SIZE) {
            headerFill = 0
            acceptHeader(done)
        }
        return take
    }

    private fun acceptHeader(done: MutableList<TdsMessage>) {
        when (val parsed = PacketHeader.parse(headerBuffer, 0, limits)) {
            is FramingResult.Failed -> failure = parsed.error
            is FramingResult.Ok -> admit(parsed.value, done)
        }
    }

    private fun admit(header: PacketHeader, done: MutableList<TdsMessage>) {
        val total = messageLength + header.length
        val started = messageType
        failure = violation(header, total, started)
        if (failure == null) {
            if (started == null) {
                messageType = header.type
                firstStatus = header.status
            }
            messageLength = total
            packet = header
            packetPayloadLeft = header.payloadLength
            finishPacketIfComplete(done)
        }
    }

    private fun violation(header: PacketHeader, total: Long, started: PacketType?): FramingError? = when {
        total > limits.maxMessageBytes -> FramingError(
            FramingFailure.MESSAGE_ABOVE_LIMIT,
            "Summe der Paketlaengen ueber ${limits.maxMessageBytes}",
        )

        started != null && started != header.type -> FramingError(
            FramingFailure.PACKET_TYPE_CHANGED,
            "Pakettyp $started, dann ${header.type}",
        )

        else -> null
    }

    private fun readPayload(chunk: ByteArray, from: Int, done: MutableList<TdsMessage>): Int {
        val take = minOf(packetPayloadLeft, chunk.size - from)
        payload.write(chunk, from, take)
        packetPayloadLeft -= take
        finishPacketIfComplete(done)
        return take
    }

    private fun finishPacketIfComplete(done: MutableList<TdsMessage>) {
        val header = packet
        if (header != null && packetPayloadLeft == 0) {
            packet = null
            if (header.endOfMessage) {
                done += completeMessage(header)
            }
        }
    }

    private fun completeMessage(last: PacketHeader): TdsMessage {
        val message = TdsMessage(
            type = last.type,
            payload = payload.toByteArray(),
            resetConnection = firstStatus and PacketStatus.RESET_CONNECTION != 0,
            ignore = last.status and PacketStatus.IGNORE != 0,
        )
        payload.reset()
        messageType = null
        messageLength = 0L
        firstStatus = PacketStatus.NORMAL
        return message
    }
}
