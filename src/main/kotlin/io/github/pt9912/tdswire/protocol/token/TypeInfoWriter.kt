package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/** Typkennungen der TYPE_INFO (Layout nach MS-TDS). */
internal object TypeCode {
    const val INT1 = 0x30
    const val INT2 = 0x34
    const val INT4 = 0x38
    const val INT8 = 0x7F
    const val BIT = 0x32
    const val INTN = 0x26
    const val BITN = 0x68
    const val NVARCHAR = 0xE7
    const val DECIMALN = 0x6A
    const val NUMERICN = 0x6C
    const val DATEN = 0x28
    const val TIMEN = 0x29
    const val DATETIME2N = 0x2A
}

/** Schreibt TYPE_INFO und die Spaltenflags; ein Typ außerhalb seiner Grenzen ist ein Verstoß. */
internal object TypeInfoWriter {
    const val MAX_NVARCHAR_CHARS = 4000
    const val MAX_PRECISION = 38
    const val MAX_SCALE = 7
    const val FLAG_NULLABLE = 0x0001
    private val intNSizes = setOf(SIZE_1, SIZE_2, SIZE_4, SIZE_8)
    private const val BYTES_PER_CHAR = 2

    fun writeTypeInfo(out: ByteWriter, column: ColumnDef) {
        val type = column.type
        val fixed = fixedCode(type)
        if (fixed != null) {
            if (column.nullable) {
                violation(ProtocolFailure.INVALID_TYPE, "Spalte ${column.name}: fester Typ ohne NULL, aber nullable")
            }
            out.u8(fixed)
            return
        }
        when (type) {
            TdsType.Int1Type, TdsType.Int2Type, TdsType.Int4Type, TdsType.Int8Type, TdsType.BitType -> Unit
            is TdsType.IntNType -> writeIntN(out, type)
            TdsType.BitNType -> writeFixedSize(out, TypeCode.BITN, 1)
            is TdsType.NVarcharType -> writeNVarchar(out, type)
            is TdsType.DecimalType -> writeDecimal(out, type)
            TdsType.DateType -> out.u8(TypeCode.DATEN)
            is TdsType.TimeType -> writeScaled(out, TypeCode.TIMEN, type.scale)
            is TdsType.DateTime2Type -> writeScaled(out, TypeCode.DATETIME2N, type.scale)
        }
    }

    /** Die Typkennung der festen Typen (ohne NULL), sonst `null`. */
    private fun fixedCode(type: TdsType): Int? = when (type) {
        TdsType.Int1Type -> TypeCode.INT1
        TdsType.Int2Type -> TypeCode.INT2
        TdsType.Int4Type -> TypeCode.INT4
        TdsType.Int8Type -> TypeCode.INT8
        TdsType.BitType -> TypeCode.BIT
        else -> null
    }

    /** Die Spaltenflags: `fNullable` und die Bits, die der Aufrufer in `extraFlags` mitgibt. */
    fun flags(column: ColumnDef): Int = column.extraFlags or (if (column.nullable) FLAG_NULLABLE else 0)

    /** Länge der Dezimalwerte in Byte einschließlich Vorzeichen, abhängig von der Precision. */
    fun decimalBytes(precision: Int): Int = when {
        precision <= PRECISION_5 -> LENGTH_5
        precision <= PRECISION_9 -> LENGTH_9
        precision <= PRECISION_13 -> LENGTH_13
        else -> LENGTH_17
    }

    /** Länge der Zeit-Ticks in Byte, abhängig vom Scale. */
    fun timeBytes(scale: Int): Int = when {
        scale <= SCALE_SHORT -> TIME_3
        scale <= SCALE_MEDIUM -> TIME_4
        else -> TIME_5
    }

    private fun writeIntN(out: ByteWriter, type: TdsType.IntNType) {
        if (type.size !in intNSizes) {
            violation(ProtocolFailure.INVALID_TYPE, "INTN mit Groesse ${type.size}")
        }
        writeFixedSize(out, TypeCode.INTN, type.size)
    }

    private fun writeFixedSize(out: ByteWriter, code: Int, size: Int) {
        out.u8(code)
        out.u8(size)
    }

    private fun writeNVarchar(out: ByteWriter, type: TdsType.NVarcharType) {
        if (type.maxChars !in 1..MAX_NVARCHAR_CHARS) {
            violation(ProtocolFailure.INVALID_TYPE, "NVARCHAR mit ${type.maxChars} Zeichen")
        }
        out.u8(TypeCode.NVARCHAR)
        out.u16Le(type.maxChars * BYTES_PER_CHAR)
        writeCollation(out, type.collation)
    }

    private fun writeDecimal(out: ByteWriter, type: TdsType.DecimalType) {
        if (type.precision !in 1..MAX_PRECISION || type.scale !in 0..type.precision) {
            violation(ProtocolFailure.INVALID_TYPE, "DECIMAL(${type.precision}, ${type.scale})")
        }
        val bytes = if (type.bytes == 0) decimalBytes(type.precision) else type.bytes
        if (bytes < decimalBytes(type.precision) || bytes > LENGTH_17) {
            violation(ProtocolFailure.INVALID_TYPE, "DECIMAL(${type.precision}, ${type.scale}) mit $bytes Byte")
        }
        out.u8(if (type.numeric) TypeCode.NUMERICN else TypeCode.DECIMALN)
        out.u8(bytes)
        out.u8(type.precision)
        out.u8(type.scale)
    }

    private fun writeScaled(out: ByteWriter, code: Int, scale: Int) {
        if (scale !in 0..MAX_SCALE) {
            violation(ProtocolFailure.INVALID_TYPE, "Scale $scale")
        }
        writeFixedSize(out, code, scale)
    }

    fun writeCollation(out: ByteWriter, collation: Collation) {
        out.i32Le(collation.info)
        out.u8(collation.sortId)
    }

    private const val SIZE_1 = 1
    private const val SIZE_2 = 2
    private const val SIZE_4 = 4
    private const val SIZE_8 = 8
    private const val PRECISION_5 = 9
    private const val PRECISION_9 = 19
    private const val PRECISION_13 = 28
    private const val LENGTH_5 = 5
    private const val LENGTH_9 = 9
    private const val LENGTH_13 = 13
    private const val LENGTH_17 = 17
    private const val SCALE_SHORT = 2
    private const val SCALE_MEDIUM = 4
    private const val TIME_3 = 3
    private const val TIME_4 = 4
    private const val TIME_5 = 5
}
