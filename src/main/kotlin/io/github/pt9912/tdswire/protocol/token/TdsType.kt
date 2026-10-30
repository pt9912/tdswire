package io.github.pt9912.tdswire.protocol.token

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Kollation einer Zeichenspalte (MS-TDS 2.2.5.1.2): `info` ist das 4-Byte-Feld aus LCID und
 * Vergleichsflags (Little-Endian als Zahl), `sortId` das fünfte Byte. `SQL_Latin1_General_CP1_CI_AS`
 * ist `info = 0x00D00409`, `sortId = 0x34`.
 */
data class Collation(val info: Int, val sortId: Int) {
    companion object {
        val LATIN1_GENERAL_CI_AS = Collation(info = 0x00D00409, sortId = 0x34)
    }
}

/**
 * Datentypen des Mindestkatalogs (`SPEC-003`) mit ihrer TYPE_INFO. Festlegung: Nullbare Spalten
 * tragen die N-Form des Typs (`IntNType`, `BitNType`); die festen Typen (`Int4Type`, `Int8Type`,
 * `BitType`) tragen kein NULL. PLP-Typen (MAX) gehören nicht zum Katalog.
 */
sealed interface TdsType {
    /** Ganzzahl fester Länge, 4 Byte (INT4, `0x38`). */
    data object Int4Type : TdsType

    /** Ganzzahl fester Länge, 8 Byte (INT8, `0x7F`). */
    data object Int8Type : TdsType

    /** Bit fester Länge (BIT, `0x32`). */
    data object BitType : TdsType

    /** Nullbare Ganzzahl mit `size` in 1, 2, 4 oder 8 Byte (INTN, `0x26`). */
    data class IntNType(val size: Int) : TdsType

    /** Nullbares Bit (BITN, `0x68`). */
    data object BitNType : TdsType

    /** Unicode-Text bis `maxChars` UTF-16-Einheiten, 1 bis 4000 (NVARCHAR, `0xE7`, nicht PLP). */
    data class NVarcharType(val maxChars: Int, val collation: Collation) : TdsType

    /** Dezimalzahl (DECIMALN `0x6A`, mit `numeric` NUMERICN `0x6C`), Precision 1 bis 38, Scale bis Precision. */
    data class DecimalType(val precision: Int, val scale: Int, val numeric: Boolean = false) : TdsType

    /** Datum (DATEN, `0x28`). */
    data object DateType : TdsType

    /** Uhrzeit mit Scale 0 bis 7 (TIMEN, `0x29`). */
    data class TimeType(val scale: Int) : TdsType

    /** Datum mit Uhrzeit, Scale 0 bis 7 (DATETIME2N, `0x2A`). */
    data class DateTime2Type(val scale: Int) : TdsType
}

/** Ein Wert einer Zeile oder eines Ausgabeparameters. */
sealed interface TdsValue {
    data object NullValue : TdsValue

    data class IntegerValue(val value: Long) : TdsValue

    data class BitValue(val value: Boolean) : TdsValue

    data class TextValue(val value: String) : TdsValue

    data class DecimalValue(val value: BigDecimal) : TdsValue

    data class DateValue(val value: LocalDate) : TdsValue

    data class TimeValue(val value: LocalTime) : TdsValue

    data class DateTime2Value(val value: LocalDateTime) : TdsValue
}

/**
 * Eine Spalte von COLMETADATA. `nullable` setzt das Bit `fNullable` der Spaltenflags; `extraFlags`
 * sind weitere Bits (z. B. `0x0008`, `usUpdateable` = unbekannt) und werden unverändert übernommen.
 */
data class ColumnDef(
    val name: String,
    val type: TdsType,
    val nullable: Boolean = false,
    val userType: Int = 0,
    val extraFlags: Int = 0,
)
