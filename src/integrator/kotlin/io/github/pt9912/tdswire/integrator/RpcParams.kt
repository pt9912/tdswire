package io.github.pt9912.tdswire.integrator

private const val DATE_BYTES = 3
private const val LONG_BYTES_RPC = 8

/** Ein Eingabeparameter `INTN` mit 8 Byte (`bigint`; so sendet mssql-jdbc 13.4.0 Ganzzahlen); `null` ist NULL. */
internal fun inputBigint(name: String, value: Long?): ByteArray =
    rpcParam(name, STATUS_INPUT, if (value == null) hex("26 08 00") else hex("26 08 08") + le(value, LONG_BYTES_RPC))

/** Ein Eingabeparameter `NVARCHAR(4000)`; [value] `null` ist NULL (Länge `FFFF`). */
internal fun inputText(name: String, value: String?): ByteArray =
    rpcParam(name, STATUS_INPUT, if (value == null) hex("E7 $MAX_BYTES_8000 $COLLATION FF FF") else nvarchar(value))

/** Ein Eingabeparameter `DATEN`: drei Byte Tage seit 0001-01-01 (MS-TDS, Typ DATEN); [days] `null` ist NULL. */
internal fun inputDate(name: String, days: Int?): ByteArray =
    rpcParam(name, STATUS_INPUT, if (days == null) hex("28 00") else hex("28 03") + le(days.toLong(), DATE_BYTES))

/** Ein Eingabeparameter ohne Namen aus fertiger TYPE_INFO und fertigem Wert ([typeAndValue] als Hex). */
internal fun inputRaw(typeAndValue: String): ByteArray = rpcParam("", STATUS_INPUT, hex(typeAndValue))

/** Wahr, wenn [text] als UTF-16LE in der Antwort steht. */
internal fun ByteArray.containsUtf16(text: String): Boolean {
    val needle = utf16(text)
    return (0..size - needle.size).any { at -> needle.indices.all { this[at + it] == needle[it] } }
}
