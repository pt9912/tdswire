package io.github.pt9912.tdswire.integrator

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal const val LOOPBACK = "127.0.0.1"
internal const val BYTE_MASK = 0xFF
internal const val ERROR_TOKEN = 0xAA
internal const val DONE_ERROR = 0x02

private const val CONNECT_MILLIS = 5_000
private const val READ_MILLIS = 20_000
private const val HEADER_BYTES = 8
private const val BYTE_BITS = 8
private const val TYPE_SQL_BATCH = 0x01
private const val TYPE_RPC = 0x03
private const val TYPE_RESPONSE = 4
private const val TYPE_LOGIN7 = 0x10
private const val TYPE_PRELOGIN = 0x12
private const val STATUS_EOM = 1
private const val LOGINACK_TOKEN = 0xAD
private const val DONE_TOKEN = 0xFD
private const val DONE_BYTES = 13
private const val DONE_COUNT_OFFSET = 5
private const val LONG_BYTES = 8
private const val FIXED_LOGIN_BYTES = 94
private const val PACKET_SIZE = 8000
private const val NIBBLE_BITS = 4
private const val OBFUSCATION = 0xA5
private const val ALL_HEADERS_BYTES = 22
private const val ALL_HEADERS_INNER_BYTES = 18
private const val TRANSACTION_DESCRIPTOR = 2
internal const val INT_BYTES = 4
internal const val SHORT_BYTES = 2
private const val CLIENT_ID_BYTES = 6
private const val HEX_RADIX = 16

/** Die Antwort auf eine Anfrage: Nutzdaten aller Pakete; das letzte Token ist DONE. */
internal class Answer(val payload: ByteArray) {
    private val done = payload.copyOfRange(payload.size - DONE_BYTES, payload.size)

    init {
        assertEquals(DONE_TOKEN, done[0].toInt() and BYTE_MASK, "die Antwort endet mit DONE")
    }

    val doneStatus: Int get() = (done[1].toInt() and BYTE_MASK) or ((done[2].toInt() and BYTE_MASK) shl BYTE_BITS)

    val rowCount: Long
        get() = (0 until LONG_BYTES).fold(0L) { sum, index ->
            sum or ((done[DONE_COUNT_OFFSET + index].toLong() and BYTE_MASK.toLong()) shl (BYTE_BITS * index))
        }

    /** `true`, wenn [text] (ASCII) als UTF-16LE in den Nutzdaten steht. */
    fun contains(text: String): Boolean {
        val needle = utf16(text)
        return (0..payload.size - needle.size).any { start -> needle.indices.all { payload[start + it] == needle[it] } }
    }
}

/**
 * Ein Rohbytes-Client mit blockierendem Socket: baut PRELOGIN, LOGIN7 und SQL_BATCH von Hand nach MS-TDS und liest
 * die Antwort ohne den Codec des Produkts. Er braucht nur `java.net`.
 */
internal class WireClient(port: Int) : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(LOOPBACK, port), CONNECT_MILLIS)
        soTimeout = READ_MILLIS
        tcpNoDelay = true
    }

    /** PRELOGIN ohne Verschlüsselung, dann LOGIN7 des Benutzers `alice`; beides muss angenommen werden. */
    fun login() {
        send(TYPE_PRELOGIN, preloginPayload())
        assertEquals(TYPE_RESPONSE, readMessage().first)
        send(TYPE_LOGIN7, login7Payload("alice", "secret"))
        val (type, payload) = readMessage()
        assertEquals(TYPE_RESPONSE, type)
        assertTrue(payload.any { it.toInt() and BYTE_MASK == LOGINACK_TOKEN }, "LOGINACK fehlt")
    }

    /** Ein SQL_BATCH mit [sql] und die Antwort darauf. */
    fun batch(sql: String): Answer {
        send(TYPE_SQL_BATCH, allHeaders() + utf16(sql))
        return Answer(readMessage().second)
    }

    /**
     * Ein `sp_executesql` (RPC, ProcID 10) mit [statement], [declarations] und den fertigen [params]
     * (MS-TDS 2.2.6.6); die Antwort sind die rohen Nutzdaten, ohne Annahme über das letzte Token.
     */
    fun executeSql(statement: String, declarations: String, vararg params: ByteArray): ByteArray {
        send(TYPE_RPC, executeSqlPayload(statement, declarations, *params))
        return readMessage().second
    }

    /** `true`, wenn der Server schließt, ohne vorher ein weiteres Byte zu senden. */
    fun closedByServer(): Boolean = try {
        socket.getInputStream().read() < 0
    } catch (reset: IOException) {
        reset.message?.contains("reset", ignoreCase = true) == true
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
            assertEquals(HEADER_BYTES, header.size, "Paketkopf abgeschnitten")
            type = header[0].toInt() and BYTE_MASK
            status = header[1].toInt() and BYTE_MASK
            val length = ((header[2].toInt() and BYTE_MASK) shl BYTE_BITS) or (header[3].toInt() and BYTE_MASK)
            payload.write(socket.getInputStream().readNBytes(length - HEADER_BYTES))
        } while (status and STATUS_EOM == 0)
        return type to payload.toByteArray()
    }
}

/** PRELOGIN mit Version und der Option ENCRYPTION (`ENCRYPT_OFF`); der Server ohne TLS antwortet `ENCRYPT_NOT_SUP`. */
private fun preloginPayload(): ByteArray = hex("00 00 0B 00 06  01 00 11 00 01  FF  00 00 00 00 00 00  00")

/** ALL_HEADERS mit dem Header der Transaktionsbeschreibung (MS-TDS 2.2.5.3). */
internal fun allHeaders(): ByteArray = le(ALL_HEADERS_BYTES.toLong(), INT_BYTES) +
    le(ALL_HEADERS_INNER_BYTES.toLong(), INT_BYTES) + le(TRANSACTION_DESCRIPTOR.toLong(), SHORT_BYTES) +
    ByteArray(LONG_BYTES) + le(1, INT_BYTES)

/** LOGIN7 nach MS-TDS 2.2.6.4: feste Länge, Offsettabelle, dann die Zeichenfolgen; das Passwort ist verschleiert. */
private fun login7Payload(user: String, password: String): ByteArray {
    val data = ByteArrayOutputStream()
    val slots = mutableListOf<ByteArray>()

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
    slots += listOf(host, userSlot, passwordSlot, app, server, ByteArray(INT_BYTES), client, language, database)
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

internal fun le(value: Long, count: Int): ByteArray =
    ByteArray(count) { index -> ((value ushr (BYTE_BITS * index)) and BYTE_MASK.toLong()).toByte() }

internal fun utf16(text: String): ByteArray = text.toByteArray(Charsets.UTF_16LE)

private fun obfuscate(plain: ByteArray): ByteArray = ByteArray(plain.size) { index ->
    val b = plain[index].toInt() and BYTE_MASK
    ((((b shl NIBBLE_BITS) or (b ushr NIBBLE_BITS)) and BYTE_MASK) xor OBFUSCATION).toByte()
}

internal fun hex(text: String): ByteArray =
    text.split(' ').filter { it.isNotEmpty() }.map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
