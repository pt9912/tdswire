package io.github.pt9912.tdswire.protocol.bulk

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.rpc.RpcType

/**
 * Die Typen fester Länge in COLMETADATA der Nachricht BULK_LOAD (`MS-TDS`, Typen ohne Längenpräfix): [code] ist die
 * Typkennung, [size] die Zahl der Wertebytes, [sqlType] die Abbildung auf [SqlType]. Ein Wert fester Länge trägt kein
 * NULL. Gemessen mit dem Referenzclient (`SqlBulkCopy`, nicht nullbare Spalten, Messreihe M-8 bis M-12 und M-36) sind
 * alle fünf Typen: [INT8] in M-8 bis M-12, [INT4], [INT2], [INT1] und [BIT] in M-36.
 */
internal enum class BulkFixedType(val code: Int, val size: Int, val sqlType: SqlType) {
    INT1(code = 0x30, size = 1, sqlType = SqlType.Int32),
    BIT(code = 0x32, size = 1, sqlType = SqlType.Bool),
    INT2(code = 0x34, size = 2, sqlType = SqlType.Int32),
    INT4(code = 0x38, size = 4, sqlType = SqlType.Int32),
    INT8(code = 0x7F, size = 8, sqlType = SqlType.Int64),
    ;

    companion object {
        /** Der feste Typ mit der Typkennung [code] oder `null`. */
        fun of(code: Int): BulkFixedType? = entries.firstOrNull { it.code == code }
    }
}

/** Der Typ einer Spalte der Nachricht: fester Länge oder mit den Angaben der TYPE_INFO des RPC. */
internal sealed interface BulkColumnType {
    data class Fixed(val kind: BulkFixedType) : BulkColumnType

    data class Sized(val type: RpcType) : BulkColumnType
}
