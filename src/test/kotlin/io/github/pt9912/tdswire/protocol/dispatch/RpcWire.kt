package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex

/**
 * Baut RPC-Nachrichten aus Hex-Teilen (Layout nach MS-TDS, unabhängig vom Parser). Text und Namen sind
 * UTF-16LE aus dem Kotlin-String; jeder Wert eines Typs steht in den Tests als Literal.
 */
object RpcWire {
    private const val MAX_BYTES_8000 = "40 1F"

    fun utf16(text: String): String = text.toByteArray(Charsets.UTF_16LE).toHex()

    /** TYPE_INFO und Wert eines `NVARCHAR(4000)` mit [text]. */
    fun nvarchar(text: String): String {
        val length = text.length * 2
        return "E7 $MAX_BYTES_8000  ${RpcVectors.COLLATION}  ${u16(length)}  ${utf16(text)}"
    }

    /** Ein Parameter: Namenslänge in Zeichen, Name, Statusbyte, TYPE_INFO und Wert. */
    fun param(name: String, typeAndValue: String, status: Int = 0): String =
        "${RpcVectors.hexByte(name.length)}  ${utf16(name)}  ${RpcVectors.hexByte(status)}  $typeAndValue"

    /** `sp_executesql` (ProcID 10) mit [statement], [declarations] und den fertigen [params]. */
    fun executeSql(statement: String, declarations: String, vararg params: String): ByteArray = hex(
        "${RpcVectors.HEADERS}  FF FF 0A 00  02 00  00 00 ${nvarchar(statement)}  00 00 ${nvarchar(declarations)}  " +
            params.joinToString("  "),
    )

    /** Ein RPC per Prozedurname [name] (Zeichenzahl als Länge) mit den fertigen [params]. */
    fun byName(name: String, vararg params: String): ByteArray = hex(
        "${RpcVectors.HEADERS}  ${u16(name.length)}  ${utf16(name)}  00 00  " + params.joinToString("  "),
    )

    /** Ein RPC mit einer ProcID außerhalb des Katalogs, ohne weitere Bytes (der Parser liest sie nicht). */
    fun procId(id: Int): ByteArray = hex("${RpcVectors.HEADERS}  FF FF ${u16(id)}  00 00")

    /** `INTN` 4 Byte mit Wert. */
    fun int4(value: Int): String = "26 04 04 " + le(value.toLong(), 4)

    /** `INTN` 8 Byte mit Wert. */
    fun int8(value: Long): String = "26 08 08 " + le(value, 8)

    /** Little-Endian-Bytes der unteren [count] Byte von [value] als Hex. */
    fun le(value: Long, count: Int): String = ByteArray(count) { (value shr (8 * it)).toByte() }.toHex()

    private fun u16(value: Int): String = le(value.toLong(), 2)
}
