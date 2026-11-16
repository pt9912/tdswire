package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation
import java.io.ByteArrayOutputStream

/**
 * Liest den Körper eines PLP-Werts (MAX-Typ): Gesamtlänge in 8 Byte, dann Stücke aus 4 Byte Länge und Daten, dann der
 * Terminator (ein Stück der Länge 0). Alle Bytes `0xFF` als Gesamtlänge sind NULL, `0xFE` mit sieben `0xFF` heißt
 * „Länge unbekannt“ (Lesart von MS-TDS, nicht gemessen); die Gesamtlänge ist sonst die Summe der Stücke.
 *
 * Festlegung: Der Leser legt kein Byte an, bevor die Länge gegen die Nachricht geprüft ist: der Puffer wächst nur um
 * Stücke, die `ByteReader` als vorhanden gelesen hat, und die Gesamtlänge ist kein Größenhinweis. Eine bekannte
 * Gesamtlänge über [Int.MAX_VALUE] ist `INVALID_LENGTH`, über dem Rest der Nachricht `TRUNCATED`; ein Stück über dem
 * Rest der Nachricht ist `TRUNCATED`; eine Summe der Stücke, die nicht zur bekannten Gesamtlänge passt, ist
 * `INVALID_LENGTH`; die Summe prüft der Leser erst am Terminator, die Stücke sind bis dahin gepuffert. Der Puffer ist
 * damit nie größer als die Nachricht (`SPEC-007` begrenzt sie).
 */
internal object PlpReader {
    private const val NULL_TOTAL = -1L
    private const val UNKNOWN_TOTAL = -2L
    private const val BYTES_PER_CHAR = 2
    private const val BYTE_MASK = 0xFF
    private const val BYTE_BITS = 8

    /** Der Wert oder `null` für NULL. */
    fun read(reader: ByteReader): ByteArray? {
        val total = reader.u64Le()
        if (total == NULL_TOTAL) {
            return null
        }
        val known = total != UNKNOWN_TOTAL
        if (known) {
            checkTotal(total, reader.remaining)
        }
        val value = ByteArrayOutputStream()
        var sum = 0L
        while (true) {
            val chunk = reader.u32Le()
            if (chunk == 0L) {
                break
            }
            value.write(reader.bytes(chunk.toInt()))
            sum += chunk
        }
        if (known && sum != total) {
            violation(ProtocolFailure.INVALID_LENGTH, "PLP-Stücke ergeben $sum Byte, Gesamtlänge $total")
        }
        return value.toByteArray()
    }

    /**
     * Der Wert eines MAX-Typs: `nvarchar(max)` ist Text (gerade Byte-Zahl, UTF-16-Einheiten unverändert, auch
     * unpaarige Surrogate), die anderen MAX-Typen liefern die Bytes der Stück-Folge.
     */
    fun value(reader: ByteReader, type: RpcType): RpcValue {
        val bytes = read(reader) ?: return RpcValue.NullValue
        val isText = type.kind == RpcTypeKind.NVARCHAR
        return if (isText) RpcValue.TextValue(utf16Units(bytes)) else RpcValue.RawValue(bytes)
    }

    private fun utf16Units(bytes: ByteArray): String {
        if (bytes.size % BYTES_PER_CHAR != 0) {
            violation(ProtocolFailure.ODD_TEXT_LENGTH, "PLP-Wert mit ${bytes.size} Byte")
        }
        val units = CharArray(bytes.size / BYTES_PER_CHAR) { index ->
            val low = bytes[index * BYTES_PER_CHAR].toInt() and BYTE_MASK
            val high = bytes[index * BYTES_PER_CHAR + 1].toInt() and BYTE_MASK
            ((high shl BYTE_BITS) or low).toChar()
        }
        return String(units)
    }

    private fun checkTotal(total: Long, remaining: Int) {
        if (total < 0 || total > Int.MAX_VALUE) {
            violation(ProtocolFailure.INVALID_LENGTH, "PLP-Gesamtlänge $total ausserhalb 0..${Int.MAX_VALUE}")
        }
        if (total > remaining) {
            violation(ProtocolFailure.TRUNCATED, "PLP-Gesamtlänge $total, $remaining Byte vorhanden")
        }
    }
}
