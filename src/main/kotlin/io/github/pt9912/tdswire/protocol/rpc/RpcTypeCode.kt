package io.github.pt9912.tdswire.protocol.rpc

/** Typkennungen der TYPE_INFO, die [RpcTypeKind] trägt. */
internal object RpcTypeCode {
    const val GUID = 0x24
    const val INTN = 0x26
    const val DATEN = 0x28
    const val TIMEN = 0x29
    const val DATETIME2N = 0x2A
    const val DATETIMEOFFSETN = 0x2B
    const val BITN = 0x68
    const val DECIMALN = 0x6A
    const val NUMERICN = 0x6C
    const val FLTN = 0x6D
    const val MONEYN = 0x6E
    const val DATETIMN = 0x6F
    const val BIGVARBINARY = 0xA5
    const val BIGVARCHAR = 0xA7
    const val BIGBINARY = 0xAD
    const val BIGCHAR = 0xAF
    const val NVARCHAR = 0xE7
    const val NCHAR = 0xEF
}
