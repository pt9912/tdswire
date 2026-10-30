package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Schreibt den Wert einer Spalte in der Form des Typs (TYPE_VARBYTE, MS-TDS 2.2.5.5): feste Typen
 * ohne Längenbyte, nullbare Typen mit Längenbyte (0 = NULL), NVARCHAR mit 2-Byte-Länge in Byte
 * (`0xFFFF` = NULL). Ein Wert, der nicht zum Typ passt oder ihn sprengt, ist ein Verstoß.
 *
 * Festlegung: Eine Dezimalzahl mit mehr Nachkommastellen als der Scale und eine Uhrzeit mit
 * feinerer Auflösung als der Scale werden abgewiesen, nicht gerundet.
 */
internal object ValueWriter {
    private const val NULL_LENGTH_SHORT = 0xFFFF
    private const val BYTES_PER_CHAR = 2

    fun write(out: ByteWriter, column: ColumnDef, value: TdsValue) {
        if (value is TdsValue.NullValue) {
            writeNull(out, column)
        } else {
            writeNonNull(out, column.type, value)
        }
    }

    private fun writeNull(out: ByteWriter, column: ColumnDef) {
        if (!column.nullable) {
            violation(ProtocolFailure.TYPE_MISMATCH, "Spalte ${column.name}: NULL in einer Spalte ohne NULL")
        }
        if (column.type is TdsType.NVarcharType) {
            out.u16Le(NULL_LENGTH_SHORT)
        } else {
            out.u8(0)
        }
    }

    private fun writeNonNull(out: ByteWriter, type: TdsType, value: TdsValue) {
        when (type) {
            TdsType.Int4Type -> out.i32Le(ValueSupport.integer(value, ValueSupport.INT_RANGE).toInt())
            TdsType.Int8Type -> out.i64Le(ValueSupport.integer(value, ValueSupport.LONG_RANGE))
            TdsType.BitType -> out.u8(bit(value))
            is TdsType.IntNType -> ValueSupport.prefixed(out, intN(type.size, value))
            TdsType.BitNType -> ValueSupport.prefixed(out, byteArrayOf(bit(value).toByte()))
            is TdsType.NVarcharType -> writeText(out, type, value)
            is TdsType.DecimalType -> ValueSupport.prefixed(out, DecimalValues.bytes(type, value))
            TdsType.DateType -> ValueSupport.prefixed(out, TemporalValues.dateBytes(value))
            is TdsType.TimeType -> ValueSupport.prefixed(out, TemporalValues.timeBytes(type.scale, value))
            is TdsType.DateTime2Type -> ValueSupport.prefixed(out, TemporalValues.dateTime2Bytes(type.scale, value))
        }
    }

    private fun bit(value: TdsValue): Int {
        val flag = value as? TdsValue.BitValue ?: ValueSupport.mismatch(value)
        return if (flag.value) 1 else 0
    }

    private fun intN(size: Int, value: TdsValue): ByteArray =
        ValueSupport.littleEndian(ValueSupport.integer(value, ValueSupport.rangeOfSize(size)), size)

    private fun writeText(out: ByteWriter, type: TdsType.NVarcharType, value: TdsValue) {
        val text = (value as? TdsValue.TextValue ?: ValueSupport.mismatch(value)).value
        if (text.length > type.maxChars) {
            violation(
                ProtocolFailure.VALUE_OUT_OF_RANGE,
                "Text mit ${text.length} Zeichen, Spalte erlaubt ${type.maxChars}",
            )
        }
        out.u16Le(text.length * BYTES_PER_CHAR)
        out.utf16(text)
    }
}
