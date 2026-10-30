package io.github.pt9912.tdswire.testsupport

import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.EnvChange
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.InfoToken
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Der Gegenspieler des Encoders, nur für Tests: liest einen Tokenstrom zurück. Er gehört nicht zum
 * Produktionscode und belegt Selbstkonsistenz, keine Protokolltreue (das tun die Handvektoren).
 * Bei einem Fehler im Strom wirft er `IllegalStateException`.
 */
class TokenDecoder(private val bytes: ByteArray) {
    private var pos = 0
    private var columns: List<ColumnDef> = emptyList()

    fun decodeAll(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < bytes.size) {
            tokens += decodeOne()
        }
        return tokens
    }

    private fun u8(): Int = bytes[pos++].toInt() and 0xFF

    private fun u16(): Int = u8() or (u8() shl 8)

    private fun i32(): Int = u16() or (u16() shl 16)

    private fun i64(): Long = (i32().toLong() and 0xFFFFFFFFL) or (i32().toLong() shl 32)

    private fun i32Be(): Int = ((u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8())

    private fun text(chars: Int): String = String(CharArray(chars) { u16().toChar() })

    private fun bVarchar(): String = text(u8())

    private fun usVarchar(): String = text(u16())

    private fun le(count: Int): Long = (0 until count).fold(0L) { acc, i -> acc or (u8().toLong() shl (8 * i)) }

    private fun decodeOne(): Token = when (val code = u8()) {
        0xAD -> loginAck()
        0xE3 -> envChange()
        0xAB -> InfoToken(message())
        0xAA -> ErrorToken(message())
        0xFD, 0xFE, 0xFF -> done(code)
        0x81 -> colMetadata()
        0xD1 -> RowToken(columns.map { value(it) })
        0x79 -> ReturnStatusToken(i32())
        0xAC -> returnValue()
        else -> error("Tokenkennung $code")
    }

    private fun loginAck(): Token {
        u16()
        val interfaceType = u8()
        val version = i32Be()
        val name = bVarchar()
        return LoginAck(interfaceType, version, name, i32Be())
    }

    private fun collation(): Collation {
        val info = i32()
        return Collation(info, u8())
    }

    private fun envChange(): Token {
        u16()
        return when (val type = u8()) {
            1 -> EnvChange.Database(bVarchar(), bVarchar())
            4 -> EnvChange.PacketSize(bVarchar().toInt(), bVarchar().toInt())
            7 -> sqlCollation()
            else -> error("ENVCHANGE-Typ $type")
        }
    }

    private fun sqlCollation(): EnvChange {
        check(u8() == 5)
        val newValue = collation()
        val oldLength = u8()
        return EnvChange.SqlCollation(newValue, if (oldLength == 0) null else collation())
    }

    private fun decimalType(code: Int): TdsType {
        u8()
        return TdsType.DecimalType(u8(), u8(), code == 0x6C)
    }

    private fun textValue(): TdsValue {
        val length = u16()
        return if (length == 0xFFFF) TdsValue.NullValue else TdsValue.TextValue(text(length / 2))
    }

    private fun message(): ServerMessage {
        u16()
        val number = i32()
        val state = u8()
        val severity = u8()
        return ServerMessage(number, state, severity, usVarchar(), bVarchar(), bVarchar(), i32())
    }

    private fun done(code: Int): Token = DoneToken(DoneKind.entries.single { it.code == code }, u16(), u16(), i64())

    private fun colMetadata(): Token {
        val count = u16()
        columns = List(count) { column() }
        return ColMetadataToken(columns)
    }

    private fun column(named: Boolean = true): ColumnDef {
        val userType = i32()
        val flags = u16()
        val type = typeInfo()
        val name = if (named) bVarchar() else ""
        return ColumnDef(name, type, flags and 1 != 0, userType, flags and 1.inv())
    }

    private fun typeInfo(): TdsType = when (val code = u8()) {
        0x38 -> TdsType.Int4Type
        0x7F -> TdsType.Int8Type
        0x32 -> TdsType.BitType
        0x26 -> TdsType.IntNType(u8())
        0x68 -> TdsType.BitNType.also { check(u8() == 1) }
        0xE7 -> TdsType.NVarcharType(u16() / 2, collation())
        0x6A, 0x6C -> decimalType(code)
        0x28 -> TdsType.DateType
        0x29 -> TdsType.TimeType(u8())
        0x2A -> TdsType.DateTime2Type(u8())
        else -> error("Typkennung $code")
    }

    private fun returnValue(): Token {
        val ordinal = u16()
        val name = bVarchar()
        val status = u8()
        val column = column(named = false)
        return ReturnValueToken(ordinal, name, status, column, value(column))
    }

    private fun value(column: ColumnDef): TdsValue = when (val type = column.type) {
        TdsType.Int4Type -> TdsValue.IntegerValue(i32().toLong())
        TdsType.Int8Type -> TdsValue.IntegerValue(i64())
        TdsType.BitType -> TdsValue.BitValue(u8() != 0)
        is TdsType.IntNType -> nullable { length -> TdsValue.IntegerValue(signed(length, type.size == 1)) }
        TdsType.BitNType -> nullable { TdsValue.BitValue(u8() != 0) }
        is TdsType.NVarcharType -> textValue()
        is TdsType.DecimalType -> nullable { length -> decimal(length, type.scale) }
        TdsType.DateType -> nullable { TdsValue.DateValue(date()) }
        is TdsType.TimeType -> nullable { length -> TdsValue.TimeValue(time(length, type.scale)) }
        is TdsType.DateTime2Type -> nullable { length -> TdsValue.DateTime2Value(decodeDateTime2(length, type.scale)) }
    }

    private fun nullable(read: (Int) -> TdsValue): TdsValue {
        val length = u8()
        return if (length == 0) TdsValue.NullValue else read(length)
    }

    private fun signed(length: Int, unsigned: Boolean): Long {
        val raw = le(length)
        if (unsigned || length == 8) return raw
        val shift = 64 - 8 * length
        return (raw shl shift) shr shift
    }

    private fun decimal(length: Int, scale: Int): TdsValue {
        val positive = u8() == 1
        val littleEndian = ByteArray(length - 1) { u8().toByte() }
        val magnitude = BigInteger(1, littleEndian.reversedArray())
        return TdsValue.DecimalValue(BigDecimal(if (positive) magnitude else magnitude.negate(), scale))
    }

    private fun date(): LocalDate = LocalDate.ofEpochDay(le(3) - DAYS_TO_UNIX_EPOCH)

    private fun time(length: Int, scale: Int): LocalTime {
        var unit = 1L
        repeat(9 - scale) { unit *= 10 }
        return LocalTime.ofNanoOfDay(le(length) * unit)
    }

    private fun decodeDateTime2(length: Int, scale: Int): LocalDateTime {
        val time = time(length - 3, scale)
        return LocalDateTime.of(date(), time)
    }

    private companion object {
        const val DAYS_TO_UNIX_EPOCH = 719_162L
    }
}
