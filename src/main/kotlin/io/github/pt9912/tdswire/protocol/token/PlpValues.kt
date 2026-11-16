package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * TYPE_INFO und Wert der MAX-Typen ([TdsType.PlpType]): die TYPE_INFO trägt die Länge `0xFFFF`, der Wert ist ein
 * PLP-Körper aus der Gesamtlänge in 8 Byte (alle Bytes `0xFF` ist NULL), einem Stück mit 4 Byte Länge und Daten
 * (bei Länge 0 entfällt das Stück) und dem Terminator aus 4 Nullbyte. Der Encoder schreibt immer die bekannte
 * Gesamtlänge und höchstens ein Stück; ein Leser nimmt auch mehrere Stücke (`RpcParameterReader`). Das Layout ist
 * eine Lesart von MS-TDS, gestützt auf Mitschnitte eines echten SQL Servers (Test `TokenPlpNbcRowTest`).
 *
 * Grenze (Festlegung): Ein Wert, dessen Bytes samt Rahmen nicht mehr in den Puffer des Tokens passen
 * ([MAX_BUFFERED_BYTES]), ist `TOO_LONG`. Die Prüfung läuft aus der Länge des Werts, bevor ein Byte geschrieben ist:
 * der Puffer des Tokens bleibt unverändert und wächst nicht auf ein Ergebnis zu, das er nicht halten kann.
 */
internal object PlpValues {
    private const val ARRAY_RESERVE = 8
    const val MARKER = 0xFFFF
    const val MAX_BUFFERED_BYTES = Int.MAX_VALUE - ARRAY_RESERVE
    private const val OVERHEAD_BYTES = 16L
    private const val BYTES_PER_CHAR = 2L
    private const val NULL_TOTAL = -1L
    private const val TERMINATOR = 0

    /** TYPE_INFO: Typkennung, Länge `0xFFFF`, bei den Zeichentypen die Kollation. */
    fun writeTypeInfo(out: ByteWriter, type: TdsType.PlpType) {
        when (type) {
            is TdsType.NVarcharMaxType -> withCollation(out, TypeCode.NVARCHAR, type.collation)
            is TdsType.VarcharMaxType -> withCollation(out, TypeCode.BIGVARCHAR, type.collation)
            TdsType.VarbinaryMaxType -> head(out, TypeCode.BIGVARBINARY)
        }
    }

    private fun withCollation(out: ByteWriter, code: Int, collation: Collation) {
        head(out, code)
        TypeInfoWriter.writeCollation(out, collation)
    }

    private fun head(out: ByteWriter, code: Int) {
        out.u8(code)
        out.u16Le(MARKER)
    }

    /** NULL: die Gesamtlänge mit allen Bytes `0xFF`, ohne Stück und Terminator. */
    fun writeNull(out: ByteWriter) = out.i64Le(NULL_TOTAL)

    /** Schreibt den Wert; ein Wert, der nicht zum Typ passt oder die Grenze [limit] sprengt, ist ein Verstoß. */
    fun write(out: ByteWriter, type: TdsType.PlpType, value: TdsValue, limit: Int = MAX_BUFFERED_BYTES) {
        when (type) {
            is TdsType.NVarcharMaxType ->
                writeText(out, (value as? TdsValue.TextValue ?: ValueSupport.mismatch(value)).value, limit)

            is TdsType.VarcharMaxType, TdsType.VarbinaryMaxType ->
                writeBytes(out, (value as? TdsValue.BytesValue ?: ValueSupport.mismatch(value)).value, limit)
        }
    }

    private fun writeText(out: ByteWriter, text: String, limit: Int) {
        val total = text.length * BYTES_PER_CHAR
        checkLength(total, out.size, limit)
        body(out, total) { out.utf16(text) }
    }

    private fun writeBytes(out: ByteWriter, bytes: ByteArray, limit: Int) {
        checkLength(bytes.size.toLong(), out.size, limit)
        body(out, bytes.size.toLong()) { out.bytes(bytes) }
    }

    /** Prüft [valueBytes] gegen die Grenze [limit], bevor der Wert in den Puffer mit [buffered] Byte kommt. */
    fun checkLength(valueBytes: Long, buffered: Int, limit: Int = MAX_BUFFERED_BYTES) {
        if (valueBytes + OVERHEAD_BYTES + buffered > limit) {
            violation(ProtocolFailure.TOO_LONG, "MAX-Wert mit $valueBytes Byte passt nicht in den Puffer")
        }
    }

    private inline fun body(out: ByteWriter, total: Long, data: () -> Unit) {
        out.i64Le(total)
        if (total > 0) {
            out.i32Le(total.toInt())
            data()
        }
        out.i32Le(TERMINATOR)
    }
}
