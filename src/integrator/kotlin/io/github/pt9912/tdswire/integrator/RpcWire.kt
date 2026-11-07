package io.github.pt9912.tdswire.integrator

private const val COLLATION = "09 04 D0 00 34"
private const val MAX_BYTES_8000 = "40 1F"
private const val STATUS_INPUT = 0
private const val STATUS_OUTPUT = 1
private const val DATE_BYTES = 3
private const val LONG_BYTES_RPC = 8

/**
 * Der Rumpf eines `sp_executesql` (RPC, ProcID 10, MS-TDS 2.2.6.6): ALL_HEADERS, die Anweisung [statement], die
 * Deklaration [declarations] und die fertigen [params]. Die Sortierung der Texte ist eine Setzung.
 */
internal fun executeSqlPayload(statement: String, declarations: String, vararg params: ByteArray): ByteArray {
    val head = allHeaders() + hex("FF FF 0A 00 02 00")
    val texts = hex("00 00") + nvarchar(statement) + hex("00 00") + nvarchar(declarations)
    return head + texts + params.fold(ByteArray(0), ByteArray::plus)
}

/**
 * Der Rumpf eines `sp_prepexec` (RPC, ProcID 13): ALL_HEADERS, der Ausgabeparameter für das Handle mit dem Wert 0 (so
 * sendet mssql-jdbc 13.4.0 ihn, Messzeile h6 des Messprotokolls `handle-grenze-optionen-2026-11-06`), die Deklaration
 * [declarations] und die Anweisung [statement].
 */
internal fun prepExecPayload(statement: String, declarations: String = ""): ByteArray {
    val head = allHeaders() + hex("FF FF 0D 00 02 00")
    return head + rpcParam("", STATUS_OUTPUT, intn(0)) + hex("00 00") + nvarchar(declarations) + hex("00 00") +
        nvarchar(statement)
}

/** Ein Eingabeparameter `INTN` mit 4 Byte (`@name`); [value] `null` ist NULL. */
internal fun inputInt(name: String, value: Int?): ByteArray = rpcParam(name, STATUS_INPUT, intn(value))

/** Ein Eingabeparameter `INTN` mit 8 Byte (`bigint`; so sendet mssql-jdbc 13.4.0 Ganzzahlen); `null` ist NULL. */
internal fun inputBigint(name: String, value: Long?): ByteArray = rpcParam(name, STATUS_INPUT, bigint(value))

/** Ein Eingabeparameter `NVARCHAR(4000)`; [value] `null` ist NULL (Länge `FFFF`). */
internal fun inputText(name: String, value: String?): ByteArray =
    rpcParam(name, STATUS_INPUT, if (value == null) hex("E7 $MAX_BYTES_8000 $COLLATION FF FF") else nvarchar(value))

/** Ein Eingabeparameter `DATEN`: drei Byte Tage seit 0001-01-01 (MS-TDS, Typ DATEN); [days] `null` ist NULL. */
internal fun inputDate(name: String, days: Int?): ByteArray =
    rpcParam(name, STATUS_INPUT, if (days == null) hex("28 00") else hex("28 03") + le(days.toLong(), DATE_BYTES))

/** Ein Ausgabeparameter `INTN` mit 4 Byte ohne Eingabewert (NULL). */
internal fun outputInt(name: String): ByteArray = rpcParam(name, STATUS_OUTPUT, intn(null))

/** TYPE_INFO und Wert eines `NVARCHAR(4000)`. */
private fun nvarchar(text: String): ByteArray =
    hex("E7 $MAX_BYTES_8000 $COLLATION") + le((text.length * SHORT_BYTES).toLong(), SHORT_BYTES) + utf16(text)

private fun bigint(value: Long?): ByteArray =
    if (value == null) hex("26 08 00") else hex("26 08 08") + le(value, LONG_BYTES_RPC)

private fun intn(value: Int?): ByteArray =
    if (value == null) hex("26 04 00") else hex("26 04 04") + le(value.toLong(), INT_BYTES)

private fun rpcParam(name: String, status: Int, typeAndValue: ByteArray): ByteArray =
    byteArrayOf(name.length.toByte()) + utf16(name) + byteArrayOf(status.toByte()) + typeAndValue
