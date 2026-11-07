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
 * Liest die Nutzlast einer Nachricht vom Typ BULK_LOAD (0x07, `MS-TDS`, Paket „Bulk Load“; `SPEC-036`, Pakettypen):
 * COLMETADATA, danach ROW je Zeile, danach DONE. Die Nachricht liegt als Ganzes im Speicher (`SPEC-007`); der Leser
 * liest sie einmal von vorn nach hinten, die Werte liest [BulkValueReader].
 *
 * Gedeckt durch einen Mitschnitt (d-migrate 1.12.0 mit mssql-jdbc 13.4.0, Messprotokoll des Massenladens) sind die
 * Spaltentypen `INTN` mit 8 Byte, `NVARCHAR` und `BIGVARCHAR`; Datum, Zeit und Zeitpunkt sendet d-migrate als
 * `VARCHAR`. `INTN` mit 1, 2 und 4 Byte, `BITN`, `DECIMALN`, `NUMERICN`, `NCHAR`, `BIGCHAR`, `DATEN`, `TIMEN` und
 * `DATETIME2N` liest der Leser nach Handvektoren aus der Beschreibung von `MS-TDS`, ohne Mitschnitt eines Clients.
 * Die Typen sind abgebildet auf [SqlType] (`INTN` mit 8 Byte ist `Int64`, jede andere Größe `Int32`). Jede andere
 * Typkennung, ein PLP-Typ (MAX) und ein anderes Token als ROW und DONE (auch NBCROW) weist der Leser mit einem
 * [ProtocolFailure] ab, ebenso eine Nachricht ohne COLMETADATA, mit Zeilen, aber ohne DONE, und eine, die hinter dem
 * DONE weitergeht.
 *
 * Grenze: die Nutzlast des Referenzclients (`SqlBulkCopy`) liest dieser Leser nicht. Der Referenzclient sendet nicht
 * nullbare Spalten als feste Typen (die erste Spalte der Messreihe als BIGINT fest, Typkennung `0x7f`), die der Katalog
 * nicht führt; alle 10 aufgezeichneten Nachrichten 0x07 (`referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/`)
 * enden in [ProtocolFailure]. Belegt sind die Typen, die mssql-jdbc sendet.
 *
 * Festlegung ohne Messung am Server: höchstens [MAX_COLUMNS] Spalten und [MAX_CELLS] Zellen (Spalten mal Zeilen) je
 * Nachricht. Die Zellenzahl begrenzt die Zahl der Werte, die der Leser und die Überführung anlegen, nicht die Größe der
 * Nachricht: die ist durch `SPEC-007` auf 16 MiB begrenzt. Eine NULL-Zelle kostet auf der Leitung ein Byte oder
 * keines, im Heap dagegen einen Verweis in der Zeile und die Abbildung der Überführung: gemessen an der Grenze (einmal,
 * Messprotokoll des Massenladens, Abschnitt Heap je Zelle) 12 bis 64 MiB für 262 144 Zellen, 4 bis 120 Byte je Zelle
 * im Leser und 48 bis 256 Byte mit der Überführung, je nach Typ; ohne die Grenze der Zellen endete eine gültige
 * Nachricht von 15,7 MiB mit 1024 NULL-Spalten und 16 000 Zeilen bei 128 MiB Heap mit `OutOfMemoryError`.
 */
internal object BulkLoadReader {
    const val MAX_COLUMNS = 1024
    const val MAX_CELLS = 262_144
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
                    if ((rows.size + 1L) * types.size > MAX_CELLS) {
                        violation(ProtocolFailure.INVALID_LENGTH, "mehr als $MAX_CELLS Zellen")
                    }
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
