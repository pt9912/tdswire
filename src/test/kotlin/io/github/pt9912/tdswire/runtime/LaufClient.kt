package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.protocol.bulk.BulkLoadReader
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PACKET_PAYLOAD = 4_000
private const val RPC_TYPE = 0x03
private const val BULK_LOAD_TYPE = 0x07
private const val ATTENTION_TYPE = 0x06
private const val LOGIN_PACKET = 4_000
private const val TABULAR_TYPE = 4
private const val EOM_STATUS = 1
private const val DONE_BYTES = 13
private const val DONE_TOKEN = 0xFD
private const val STATUS_AT = 12
private const val BYTE = 0xFF
private const val BYTE_BITS = 8
private const val COLMETADATA = 0x81
private const val ROW = 0xD1
private const val INTN = 0x26
private const val NVARCHAR = 0xE7
private const val INT_BYTES = 8
private const val CHAR_BYTES = 2
private const val COLLATION = "09 04 D0 00 34"
private const val DONE_EMPTY = "FD 00 00 00 00 00 00 00 00 00 00 00 00"

/** Eine Nachricht 0x07 in ihren Spalten: Nachrichtentext und die Zeilen, die der Leser des Produkts liest. */
internal class BulkMessage(val payload: ByteArray, val rows: Int)

/**
 * Baut Nachrichten 0x07 und Pakete von Hand (Layout nach MS-TDS, unabhängig vom Leser des Produkts): eine Spalte
 * `INTN(8)` und eine Spalte `NVARCHAR`, nullbar nicht. Jede gebaute Nachricht liest der Leser des Produkts vor dem
 * Senden gegen und findet die Zahl der Zeilen wieder.
 */
internal object BulkWire {
    fun insertBulk(table: String, idColumn: String, textColumn: String, chars: Int): String =
        "INSERT BULK [dbo].[$table] ([$idColumn] BIGINT , [$textColumn] NVARCHAR($chars) ) with (CHECK_CONSTRAINTS)"

    /** Eine Nachricht 0x07, eine Zeile je Text in [texts], Schlüssel ab [firstId], Texte bis [maxChars] Zeichen. */
    fun message(idColumn: String, textColumn: String, maxChars: Int, firstId: Long, texts: List<String>): BulkMessage {
        val out = ByteArrayOutputStream()
        out.write(COLMETADATA)
        out.write(byteArrayOf(2, 0))
        column(out, idColumn) { it.write(byteArrayOf(INTN.toByte(), INT_BYTES.toByte())) }
        column(out, textColumn) {
            it.write(NVARCHAR)
            it.write(le(maxChars.toLong() * CHAR_BYTES, 2))
            it.write(hexBytes(COLLATION))
        }
        texts.forEachIndexed { index, text ->
            val bytes = text.toByteArray(Charsets.UTF_16LE)
            out.write(ROW)
            out.write(INT_BYTES)
            out.write(le(firstId + index, INT_BYTES))
            out.write(le(bytes.size.toLong(), 2))
            out.write(bytes)
        }
        out.write(hexBytes(DONE_EMPTY))
        val payload = out.toByteArray()
        val read = assertIs<ProtocolResult.Ok<BulkRows>>(BulkLoadReader.read(payload))
        assertEquals(texts.size, read.value.rows.size, "die Nachricht 0x07 trägt die gebauten Zeilen")
        return BulkMessage(payload, texts.size)
    }

    private fun column(out: ByteArrayOutputStream, name: String, typeInfo: (ByteArrayOutputStream) -> Unit) {
        out.write(ByteArray(4))
        out.write(ByteArray(2))
        typeInfo(out)
        out.write(name.length)
        out.write(name.toByteArray(Charsets.UTF_16LE))
    }

    private fun le(value: Long, bytes: Int): ByteArray {
        val buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes == 2) buffer.putShort(value.toShort()) else buffer.putLong(value)
        return buffer.array()
    }

    private fun hexBytes(text: String): ByteArray = text.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    /** Die Pakete der Nachricht: [PACKET_PAYLOAD] Nutzdatenbytes je Paket, `EOM` im letzten. */
    fun packets(type: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + (payload.size / PACKET_PAYLOAD + 1) * PACKET_HEADER)
        var from = 0
        var id = 1
        do {
            val to = minOf(payload.size, from + PACKET_PAYLOAD)
            val status = if (to == payload.size) EOM_STATUS else 0
            out.write(handPacket(type, status, payload.copyOfRange(from, to), id++))
            from = to
        } while (from < payload.size)
        return out.toByteArray()
    }

    private const val PACKET_HEADER = 8
}

/**
 * Ein angemeldeter Rohbytes-Client des Laufs: PRELOGIN ohne Verschlüsselung, LOGIN7, dann Anfragen, deren Antwort er
 * liest und als Token von Hand zerlegt. Er ist kein Referenzclient.
 */
internal class LaufClient(port: Int, receiveBufferBytes: Int = 0) : AutoCloseable {
    private val client = RawClient(port, receiveBufferBytes = receiveBufferBytes)

    /** Die Zahl der Anfragen (Nachrichten SQL_BATCH, RPC und 0x07), die dieser Client gesendet hat. */
    var requests = 0
        private set

    /** Meldet [user] an; `true`, wenn die Antwort ein LOGINACK trägt, sonst abgelehnt. */
    fun login(user: String, password: String): Boolean {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = user
            this.password = password.toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, LOGIN_PACKET))
        return assertNotNull(client.readMessage()).payload.any { it.toInt() and BYTE == LOGINACK }
    }

    /** Ein SQL_BATCH [sql]; die Token der Antwort. */
    fun batch(sql: String): List<Token> = answer(handMessage(BATCH_TYPE, BatchVectors.payload(sql), LOGIN_PACKET))

    /** Ein RPC `sp_executesql` mit [statement], einer Deklaration und einem NVARCHAR-Parameter `@p` mit [value]. */
    fun parameterized(statement: String, value: String): List<Token> {
        val rpc = RpcWire.executeSql(statement, "@p nvarchar(40)", RpcWire.param("@p", RpcWire.nvarchar(value)))
        return answer(handMessage(RPC_TYPE, rpc, LOGIN_PACKET))
    }

    /** `INSERT BULK` und die Nachricht 0x07, je mit ihrer Antwort; die Zahl der geladenen Zeilen. */
    fun load(insertBulk: String, message: BulkMessage): Long {
        assertTrue(batch(insertBulk).filterIsInstance<DoneToken>().isNotEmpty(), "Antwort auf INSERT BULK")
        val done = answer(BulkWire.packets(BULK_LOAD_TYPE, message.payload)).filterIsInstance<DoneToken>().single()
        return done.rowCount
    }

    /** Sendet ein SQL_BATCH und liest die Antwort nicht. */
    fun sendBatch(sql: String) {
        requests++
        client.send(handMessage(BATCH_TYPE, BatchVectors.payload(sql), LOGIN_PACKET))
    }

    /** Sendet ein ATTENTION-Paket. */
    fun sendAttention() = client.send(handPacket(ATTENTION_TYPE, EOM_STATUS, ByteArray(0)))

    /**
     * Liest und verwirft eine Antwort; die Zahl ihrer Nutzdatenbytes und `true`, wenn sie mit `DONE` endet, dessen
     * Status das Bit `ATTENTION` (0x0020) trägt.
     */
    fun drainAnswer(): Pair<Long, Boolean> {
        val drained = assertNotNull(client.drainMessage(), "Antwort")
        val tail = drained.tail
        val done = tail.size >= DONE_BYTES && tail[tail.size - DONE_BYTES].toInt() and BYTE == DONE_TOKEN
        val status = (tail[tail.size - STATUS_AT].toInt() and BYTE) or
            ((tail[tail.size - STATUS_AT + 1].toInt() and BYTE) shl BYTE_BITS)
        return drained.payloadBytes to (done && (status and ATTENTION_BIT) != 0)
    }

    private fun answer(wire: ByteArray): List<Token> {
        requests++
        client.send(wire)
        val reply = assertNotNull(client.readMessage(), "Antwort")
        assertEquals(TABULAR_TYPE, reply.type)
        return TokenDecoder(reply.payload).decodeAll()
    }

    override fun close() = client.close()

    private companion object {
        const val LOGINACK = 0xAD
        const val ATTENTION_BIT = 0x0020
    }
}
