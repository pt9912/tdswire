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
internal data class Collation(val info: Int, val sortId: Int) {
    companion object {
        val LATIN1_GENERAL_CI_AS = Collation(info = 0x00D00409, sortId = 0x34)
    }
}

/**
 * Datentypen des Mindestkatalogs (`SPEC-003`) mit ihrer TYPE_INFO. Festlegung: Nullbare Spalten
 * tragen die N-Form des Typs (`IntNType`, `BitNType`); die festen Typen (`Int1Type`, `Int2Type`,
 * `Int4Type`, `Int8Type`, `BitType`) tragen kein NULL. Die PLP-Typen (MAX, [PlpType]) sind nullbar oder nicht wie jede
 * Spalte und tragen ihren Wert als Stück-Folge ([PlpValues]); sie gehören nicht zum Typkatalog des Backend-Vertrags
 * (`SPEC-003`), der Encoder schreibt sie auf Anweisung eines Aufrufers.
 */
internal sealed interface TdsType {
    /** Ganzzahl fester Länge ohne Vorzeichen, 1 Byte (INT1, `0x30`). */
    data object Int1Type : TdsType

    /** Ganzzahl fester Länge, 2 Byte (INT2, `0x34`). */
    data object Int2Type : TdsType

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

    /**
     * Dezimalzahl (DECIMALN `0x6A`, mit `numeric` NUMERICN `0x6C`), Precision 1 bis 38, Scale bis Precision. [bytes]
     * ist die Größe der Wertebytes in der TYPE_INFO; ohne Angabe (0) schreibt der Encoder 17, mit Angabe gilt sie
     * unverändert (mindestens die Länge, die die Precision für den Wert braucht, höchstens 17). Gemessen ist die 17
     * für `decimal` und `numeric` der Precisionen 1, 9, 10, 19, 20, 28, 29 und 38 (Spalten einer Tabelle und
     * Ausdrücke, SQL Server 2022 und 2025, Messreihe `decimal-laenge-*` in `referenztest-dotnet/Mitschnitt/messung`);
     * die Wertbytes je Zeile schreibt der Encoder nach der Precision (5, 9, 13 oder 17); der echte Server schrieb in
     * derselben Messung für Werte bis 4 Byte Betrag 5 Byte, auch bei Precision 38.
     */
    data class DecimalType(val precision: Int, val scale: Int, val numeric: Boolean = false, val bytes: Int = 0) :
        TdsType

    /** Typen mit MAX-Länge: TYPE_INFO mit Länge `0xFFFF`, Wert als Stück-Folge mit Terminator (PLP). */
    sealed interface PlpType : TdsType

    /** `nvarchar(max)` (NVARCHAR, `0xE7`, Länge `0xFFFF`); Wert [TdsValue.TextValue]. */
    data class NVarcharMaxType(val collation: Collation) : PlpType

    /**
     * `varchar(max)` (BIGVARCHAR, `0xA7`, Länge `0xFFFF`); Wert [TdsValue.BytesValue] in der Kodierung der
     * Kollation, der Encoder übersetzt keinen Text.
     */
    data class VarcharMaxType(val collation: Collation) : PlpType

    /** `varbinary(max)` (BIGVARBINARY, `0xA5`, Länge `0xFFFF`); Wert [TdsValue.BytesValue]. */
    data object VarbinaryMaxType : PlpType

    /** `sql_variant` (SSVARIANT, `0x62`, größte Länge 8009); Wert [TdsValue.VariantIntValue], nur Basistyp `int`. */
    data object SqlVariantType : TdsType

    /** Datum (DATEN, `0x28`). */
    data object DateType : TdsType

    /** Uhrzeit mit Scale 0 bis 7 (TIMEN, `0x29`). */
    data class TimeType(val scale: Int) : TdsType

    /** Datum mit Uhrzeit, Scale 0 bis 7 (DATETIME2N, `0x2A`). */
    data class DateTime2Type(val scale: Int) : TdsType
}

/** Ein Wert einer Zeile oder eines Ausgabeparameters. */
internal sealed interface TdsValue {
    data object NullValue : TdsValue

    data class IntegerValue(val value: Long) : TdsValue

    data class BitValue(val value: Boolean) : TdsValue

    data class TextValue(val value: String) : TdsValue

    /** Ein `int` als Wert eines `sql_variant` (Basistyp `0x38`). */
    data class VariantIntValue(val value: Int) : TdsValue

    /** Bytes eines `varchar(max)` oder `varbinary(max)`; gleich sind zwei Werte mit gleichem Inhalt. */
    class BytesValue(val value: ByteArray) : TdsValue {
        override fun equals(other: Any?): Boolean = other is BytesValue && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()

        override fun toString(): String = "BytesValue(bytes=${value.size})"
    }

    data class DecimalValue(val value: BigDecimal) : TdsValue

    data class DateValue(val value: LocalDate) : TdsValue

    data class TimeValue(val value: LocalTime) : TdsValue

    data class DateTime2Value(val value: LocalDateTime) : TdsValue
}

/**
 * Eine Spalte von COLMETADATA. `nullable` setzt das Bit `fNullable` der Spaltenflags; `extraFlags`
 * sind weitere Bits (z. B. `0x0008`, `usUpdateable` = unbekannt) und werden unverändert übernommen.
 */
internal data class ColumnDef(
    val name: String,
    val type: TdsType,
    val nullable: Boolean = false,
    val userType: Int = 0,
    val extraFlags: Int = 0,
)
