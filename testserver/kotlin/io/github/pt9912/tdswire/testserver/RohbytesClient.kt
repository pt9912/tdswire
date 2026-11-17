package io.github.pt9912.tdswire.testserver

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant

private const val CONNECT_MILLIS = 5_000
private const val DEFAULT_READ_MILLIS = 20_000
private const val HEADER_BYTES = 8
private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFF
private const val TYPE_RESPONSE = 4
private const val TYPE_BATCH = 1
private const val TYPE_LOGIN7 = 0x10
private const val TYPE_PRELOGIN = 0x12
private const val STATUS_EOM = 1
private const val LOGINACK_TOKEN = 0xAD
private const val FIXED_LOGIN_BYTES = 94
private const val NIBBLE_BITS = 4
private const val OBFUSCATION = 0xA5
private const val INT_BYTES = 4
private const val SHORT_BYTES = 2
private const val CLIENT_ID_BYTES = 6
private const val HEX_RADIX = 16
private const val HEADERS_BEFORE_DESCRIPTOR = "16 00 00 00  12 00 00 00  02 00"
private const val DESCRIPTOR_BYTES = 8
private const val HEADERS_AFTER_DESCRIPTOR = "01 00 00 00"
private const val MICROS_PER_SECOND = 1_000_000L
private const val NANOS_PER_MICRO = 1_000

/** Die Packet Size, die [RohbytesClient] im LOGIN7 nennt, wenn der Aufrufer keine wählt. */
internal const val ROH_PACKET_SIZE = 8000

/** Die Wanduhr in Mikrosekunden seit 1970; Server und Client im selben Kernel (Container eines Hosts) teilen sie. */
internal fun epochMicros(): Long {
    val now = Instant.now()
    return now.epochSecond * MICROS_PER_SECOND + now.nano / NANOS_PER_MICRO
}

/**
 * Ein Rohbytes-Client: PRELOGIN ohne Verschlüsselung, LOGIN7 mit Benutzer und Passwort (MS-TDS 2.2.6.4) und SQL_BATCH
 * (MS-TDS 2.2.6.7), ohne den Codec des Produkts und nur mit `java.net`. Er ist das Urteil darüber, ob der Server eine
 * Anmeldung annimmt (`LoginProbe` der Tests), und der Messclient der Abschluss-Messung ([messeAbschluss]); jedes Paket
 * einer Antwort meldet [batch] mit der Wanduhr des Empfangs (Mikrosekunden), gestempelt, wenn das Paket vollständig
 * gelesen ist.
 */
internal class RohbytesClient(
    host: String,
    port: Int,
    private val packetSize: Int = ROH_PACKET_SIZE,
    readMillis: Int = DEFAULT_READ_MILLIS,
) : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(host, port), CONNECT_MILLIS)
        soTimeout = readMillis
        tcpNoDelay = true
    }

    /** `true`, wenn der Server die Anmeldung mit LOGINACK annimmt; `false`, wenn er sie ablehnt. */
    fun login(user: String, password: String): Boolean {
        send(TYPE_PRELOGIN, hex("00 00 0B 00 06  01 00 11 00 01  FF  00 00 00 00 00 00  00"))
        check(readMessage().first == TYPE_RESPONSE) { "PRELOGIN ohne Antwort" }
        send(TYPE_LOGIN7, login7Payload(user, password, packetSize))
        val (type, payload) = readMessage()
        check(type == TYPE_RESPONSE) { "LOGIN7 ohne Antwort" }
        return payload.any { it.toInt() and BYTE_MASK == LOGINACK_TOKEN }
    }

    /**
     * Sendet [sql] als SQL_BATCH und liest die Antwort bis zum Ende der Nachricht; [onPacket] bekommt die Nutzdaten
     * jedes Pakets und die Wanduhr seines Empfangs. Liefert die Wanduhr des Sendens (Mikrosekunden).
     */
    fun batch(sql: String, onPacket: (ByteArray, Long) -> Unit = { _, _ -> }): Long {
        val sent = epochMicros()
        send(
            TYPE_BATCH,
            hex(HEADERS_BEFORE_DESCRIPTOR) + ByteArray(DESCRIPTOR_BYTES) + hex(HEADERS_AFTER_DESCRIPTOR) + utf16(sql),
        )
        check(readMessage(onPacket).first == TYPE_RESPONSE) { "SQL_BATCH ohne Antwort" }
        return sent
    }

    override fun close() = socket.close()

    private fun send(type: Int, payload: ByteArray) {
        val length = HEADER_BYTES + payload.size
        val header = byteArrayOf(
            type.toByte(),
            STATUS_EOM.toByte(),
            (length ushr BYTE_BITS).toByte(),
            length.toByte(),
            0,
            0,
            1,
            0,
        )
        socket.getOutputStream().write(header + payload)
        socket.getOutputStream().flush()
    }

    private fun readMessage(onPacket: (ByteArray, Long) -> Unit = { _, _ -> }): Pair<Int, ByteArray> {
        val payload = ByteArrayOutputStream()
        var type: Int
        var status: Int
        do {
            val header = socket.getInputStream().readNBytes(HEADER_BYTES)
            check(header.size == HEADER_BYTES) { "Paketkopf abgeschnitten" }
            type = header[0].toInt() and BYTE_MASK
            status = header[1].toInt() and BYTE_MASK
            val length = ((header[2].toInt() and BYTE_MASK) shl BYTE_BITS) or (header[3].toInt() and BYTE_MASK)
            val body = socket.getInputStream().readNBytes(length - HEADER_BYTES)
            check(body.size == length - HEADER_BYTES) { "Paket abgeschnitten" }
            onPacket(body, epochMicros())
            payload.write(body)
        } while (status and STATUS_EOM == 0)
        return type to payload.toByteArray()
    }
}

/** LOGIN7 nach MS-TDS 2.2.6.4: feste Länge, Offsettabelle, dann die Zeichenfolgen; das Passwort ist verschleiert. */
private fun login7Payload(user: String, password: String, packetSize: Int): ByteArray {
    val data = ByteArrayOutputStream()

    fun place(bytes: ByteArray, count: Int): ByteArray {
        val slot = le((FIXED_LOGIN_BYTES + data.size()).toLong(), SHORT_BYTES) + le(count.toLong(), SHORT_BYTES)
        data.write(bytes)
        return slot
    }

    fun text(value: String) = place(utf16(value), value.length)
    val host = text("H")
    val userSlot = text(user)
    val passwordSlot = place(obfuscate(utf16(password)), password.length)
    val app = text("A")
    val server = text("S")
    val client = text("C")
    val language = text("")
    val database = text("d")
    val sspi = place(ByteArray(0), 0)
    val change = place(ByteArray(0), 0)
    val slots = listOf(host, userSlot, passwordSlot, app, server, ByteArray(INT_BYTES), client, language, database)
    val out = ByteArrayOutputStream()
    out.write(le((FIXED_LOGIN_BYTES + data.size()).toLong(), INT_BYTES))
    out.write(hex("04 00 00 74"))
    out.write(le(packetSize.toLong(), INT_BYTES))
    out.write(hex("00 00 00 07  34 12 00 00  00 00 00 00"))
    out.write(hex("E0 03 00 00"))
    out.write(hex("00 00 00 00  09 04 00 00"))
    slots.forEach { out.write(it) }
    out.write(ByteArray(CLIENT_ID_BYTES))
    out.write(sspi)
    out.write(sspi)
    out.write(change)
    out.write(le(0, INT_BYTES))
    out.write(data.toByteArray())
    return out.toByteArray()
}

private fun le(value: Long, count: Int): ByteArray =
    ByteArray(count) { index -> ((value ushr (BYTE_BITS * index)) and BYTE_MASK.toLong()).toByte() }

private fun utf16(text: String): ByteArray = text.toByteArray(Charsets.UTF_16LE)

private fun obfuscate(plain: ByteArray): ByteArray = ByteArray(plain.size) { index ->
    val b = plain[index].toInt() and BYTE_MASK
    ((((b shl NIBBLE_BITS) or (b ushr NIBBLE_BITS)) and BYTE_MASK) xor OBFUSCATION).toByte()
}

private fun hex(text: String): ByteArray =
    text.split(' ').filter { it.isNotEmpty() }.map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
