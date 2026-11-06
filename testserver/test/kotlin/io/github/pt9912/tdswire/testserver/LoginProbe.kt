package io.github.pt9912.tdswire.testserver

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val LOOPBACK = "127.0.0.1"
private const val CONNECT_MILLIS = 5_000
private const val READ_MILLIS = 20_000
private const val HEADER_BYTES = 8
private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFF
private const val TYPE_RESPONSE = 4
private const val TYPE_LOGIN7 = 0x10
private const val TYPE_PRELOGIN = 0x12
private const val STATUS_EOM = 1
private const val LOGINACK_TOKEN = 0xAD
private const val FIXED_LOGIN_BYTES = 94
private const val PACKET_SIZE = 8000
private const val NIBBLE_BITS = 4
private const val OBFUSCATION = 0xA5
private const val INT_BYTES = 4
private const val SHORT_BYTES = 2
private const val CLIENT_ID_BYTES = 6
private const val HEX_RADIX = 16

/**
 * Ein Rohbytes-Client, der nur anmeldet: PRELOGIN ohne Verschlüsselung und LOGIN7 mit Benutzer und Passwort (MS-TDS
 * 2.2.6.4), ohne den Codec des Produkts und nur mit `java.net`. Er ist das Urteil darüber, ob der Server eine Anmeldung
 * annimmt, und braucht dafür keinen Typ der Bibliothek außerhalb ihrer öffentlichen Fläche.
 */
internal class LoginProbe(port: Int) : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(LOOPBACK, port), CONNECT_MILLIS)
        soTimeout = READ_MILLIS
        tcpNoDelay = true
    }

    /** `true`, wenn der Server die Anmeldung mit LOGINACK annimmt; `false`, wenn er sie ablehnt. */
    fun login(user: String, password: String): Boolean {
        send(TYPE_PRELOGIN, hex("00 00 0B 00 06  01 00 11 00 01  FF  00 00 00 00 00 00  00"))
        check(readMessage().first == TYPE_RESPONSE) { "PRELOGIN ohne Antwort" }
        send(TYPE_LOGIN7, login7Payload(user, password))
        val (type, payload) = readMessage()
        check(type == TYPE_RESPONSE) { "LOGIN7 ohne Antwort" }
        return payload.any { it.toInt() and BYTE_MASK == LOGINACK_TOKEN }
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

    private fun readMessage(): Pair<Int, ByteArray> {
        val payload = ByteArrayOutputStream()
        var type: Int
        var status: Int
        do {
            val header = socket.getInputStream().readNBytes(HEADER_BYTES)
            check(header.size == HEADER_BYTES) { "Paketkopf abgeschnitten" }
            type = header[0].toInt() and BYTE_MASK
            status = header[1].toInt() and BYTE_MASK
            val length = ((header[2].toInt() and BYTE_MASK) shl BYTE_BITS) or (header[3].toInt() and BYTE_MASK)
            payload.write(socket.getInputStream().readNBytes(length - HEADER_BYTES))
        } while (status and STATUS_EOM == 0)
        return type to payload.toByteArray()
    }
}

/** LOGIN7 nach MS-TDS 2.2.6.4: feste Länge, Offsettabelle, dann die Zeichenfolgen; das Passwort ist verschleiert. */
private fun login7Payload(user: String, password: String): ByteArray {
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
    out.write(le(PACKET_SIZE.toLong(), INT_BYTES))
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
