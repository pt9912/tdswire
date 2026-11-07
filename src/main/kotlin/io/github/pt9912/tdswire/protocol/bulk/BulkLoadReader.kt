package io.github.pt9912.tdswire.protocol.bulk

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkRows
import io.github.pt9912.tdswire.protocol.rpc.RpcType
import io.github.pt9912.tdswire.protocol.rpc.RpcTypeInfoReader
import io.github.pt9912.tdswire.protocol.rpc.RpcTypeKind
import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.protocol.wire.guarded
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Liest die Nutzlast einer Nachricht vom Typ BULK_LOAD (0x07, `MS-TDS` 2.2.6.1; `SPEC-036`, Pakettypen):
 * COLMETADATA, danach ROW je Zeile, danach DONE. Die Nachricht liegt als Ganzes im Speicher (`SPEC-007`); der Leser
 * liest sie einmal von vorn nach hinten, die Werte liest [BulkValueReader].
 *
 * Gedeckt ist, was d-migrate 1.12.0 mit mssql-jdbc 13.4.0 sendet (Messprotokoll des Spikes, Tabelle (iii)): die
 * Spaltentypen `INTN` (1, 2, 4, 8 Byte), `BITN`, `DECIMALN` und `NUMERICN`, `NVARCHAR`, `NCHAR`, `BIGVARCHAR`,
 * `BIGCHAR`, `DATEN`, `TIMEN`, `DATETIME2N`, abgebildet auf [SqlType] (`INTN` mit 8 Byte ist `Int64`, jede andere Größe
 * `Int32`). Jede andere Typkennung, ein PLP-Typ (MAX) und ein anderes Token als ROW und DONE (auch NBCROW) weist der
 * Leser mit einem [ProtocolFailure] ab, ebenso eine Nachricht ohne COLMETADATA, mit Zeilen, aber ohne DONE, und eine,
 * die hinter dem DONE weitergeht.
 *
 * Festlegung ohne Messung am Server: höchstens [MAX_COLUMNS] Spalten und [MAX_ROWS] Zeilen je Nachricht; die Grenze
 * der Zeilen schützt den Speicher vor einer Nachricht an der Grenze von 16 MiB mit Zeilen von zwei Byte.
 */
internal object BulkLoadReader {
    const val MAX_COLUMNS = 1024
    const val MAX_ROWS = 262_144
    private const val TOKEN_COLMETADATA = 0x81
    private const val TOKEN_ROW = 0xD1
    private const val TOKEN_DONE = 0xFD
    private const val NO_METADATA = 0xFFFF
    private const val FLAG_NULLABLE = 0x0001
    private const val DONE_BYTES = 12
    private const val LONG_SIZE = 8

    private class Column(val name: String, val type: RpcType, val nullable: Boolean)

    private val supported = setOf(
        RpcTypeKind.INTN,
        RpcTypeKind.BITN,
        RpcTypeKind.DECIMALN,
        RpcTypeKind.NUMERICN,
        RpcTypeKind.NVARCHAR,
        RpcTypeKind.NCHAR,
        RpcTypeKind.BIGVARCHAR,
        RpcTypeKind.BIGCHAR,
        RpcTypeKind.DATEN,
        RpcTypeKind.TIMEN,
        RpcTypeKind.DATETIME2N,
    )

    fun read(payload: ByteArray): ProtocolResult<BulkRows> = guarded {
        val reader = ByteReader(payload)
        val columns = metadata(reader)
        val rows = rows(reader, columns.map { it.type })
        BulkRows(columns.map { ResultColumn(it.name, sqlType(it.type), it.nullable) }, rows)
    }

    private fun metadata(reader: ByteReader): List<Column> {
        val token = reader.u8()
        if (token != TOKEN_COLMETADATA) {
            violation(ProtocolFailure.NO_COLUMN_METADATA, "Nachricht beginnt mit Token 0x${Integer.toHexString(token)}")
        }
        val count = reader.u16Le()
        if (count == NO_METADATA || count == 0) violation(ProtocolFailure.NO_COLUMN_METADATA, "keine Spalten")
        if (count > MAX_COLUMNS) violation(ProtocolFailure.INVALID_LENGTH, "mehr als $MAX_COLUMNS Spalten")
        return List(count) { column(reader) }
    }

    private fun column(reader: ByteReader): Column {
        reader.skip(Int.SIZE_BYTES)
        val nullable = reader.u16Le() and FLAG_NULLABLE != 0
        val type = RpcTypeInfoReader.read(reader)
        if (type.kind !in supported) {
            violation(ProtocolFailure.INVALID_TYPE, "Typ ${type.kind.name} nicht im Katalog des Massenladens")
        }
        return Column(String(reader.utf16(reader.u8())), type, nullable)
    }

    private fun rows(reader: ByteReader, types: List<RpcType>): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        while (true) {
            when (val token = reader.u8()) {
                TOKEN_ROW -> {
                    if (rows.size == MAX_ROWS) violation(ProtocolFailure.INVALID_LENGTH, "mehr als $MAX_ROWS Zeilen")
                    rows.add(types.map { BulkValueReader.read(reader, it) })
                }

                TOKEN_DONE -> {
                    reader.skip(DONE_BYTES)
                    if (reader.remaining != 0) {
                        violation(ProtocolFailure.INVALID_LENGTH, "${reader.remaining} Byte hinter DONE")
                    }
                    return rows
                }

                else -> violation(
                    ProtocolFailure.INVALID_TYPE,
                    "Token 0x${Integer.toHexString(token)} nicht im Katalog des Massenladens",
                )
            }
        }
    }

    private fun sqlType(type: RpcType): SqlType = when (type.kind) {
        RpcTypeKind.INTN -> if (type.length == LONG_SIZE) SqlType.Int64 else SqlType.Int32
        RpcTypeKind.BITN -> SqlType.Bool
        RpcTypeKind.DECIMALN, RpcTypeKind.NUMERICN -> SqlType.Decimal(type.precision, type.scale)
        RpcTypeKind.DATEN -> SqlType.Date
        RpcTypeKind.TIMEN -> SqlType.Time
        RpcTypeKind.DATETIME2N -> SqlType.DateTime
        else -> SqlType.UnicodeText
    }
}
