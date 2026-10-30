package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TypeInfoWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/**
 * Metadatenabbildung (`TDS-FA-05.a` Schritt 1, `SPEC-003`): [SqlType] auf [TdsType], nullbare Spalten
 * in der N-Form. Die ganze Liste wird geprüft, bevor eine Spalte ausgegeben wird (`SPEC-031`): ein
 * einziger Fehler macht das Ergebnis zu [ProtocolResult.Failed], eine Teilliste gibt es nicht. Die
 * leere Liste ist gültig.
 *
 * Festlegungen ohne Messung am Referenzclient oder echten SQL Server: Unicode-Text ist
 * `NVARCHAR(4000)` mit der Kollation [COLLATION], weil [SqlType.UnicodeText] kein Längenlimit trägt;
 * [SqlType.Time] und [SqlType.DateTime] haben Scale 7, weil das Modell keinen Scale trägt;
 * [SqlType.Decimal] wird DECIMALN, nicht NUMERICN; doppelte und leere Spaltennamen sind gültig. Eine
 * Precision über 38 ist im Modell darstellbar und wird hier mit `TDS_UNSUPPORTED_TYPE` abgewiesen.
 */
object ColumnMapping {
    const val MAX_COLUMNS = 0xFFFE
    const val MAX_NAME_CHARS = 255
    const val TEXT_CHARS = TypeInfoWriter.MAX_NVARCHAR_CHARS
    const val TIME_SCALE = TypeInfoWriter.MAX_SCALE
    const val MAX_PRECISION = TypeInfoWriter.MAX_PRECISION
    val COLLATION: Collation = Collation.LATIN1_GENERAL_CI_AS
    private const val INT_BYTES = 4
    private const val LONG_BYTES = 8

    fun map(columns: List<ResultColumn>): ProtocolResult<List<ColumnDef>> {
        if (columns.size > MAX_COLUMNS) {
            return failed(ProtocolFailure.TOO_LONG, "COLMETADATA mit ${columns.size} Spalten")
        }
        val results = columns.mapIndexed { index, column -> column(index, column) }
        val defs = results.filterIsInstance<ProtocolResult.Ok<ColumnDef>>().map { it.value }
        return results.filterIsInstance<ProtocolResult.Failed>().firstOrNull() ?: ProtocolResult.Ok(defs)
    }

    private fun column(index: Int, column: ResultColumn): ProtocolResult<ColumnDef> {
        val name = column.name
        return when {
            name.length > MAX_NAME_CHARS ->
                failed(ProtocolFailure.TOO_LONG, "Spalte $index: Name mit ${name.length} Zeichen")

            !TextMapping.isWellFormed(name) ->
                failed(ProtocolFailure.VALUE_OUT_OF_RANGE, "Spalte $index: Name mit unpaarem Surrogat")

            else -> when (val type = tdsType(index, column.type, column.nullable)) {
                is ProtocolResult.Ok -> ProtocolResult.Ok(ColumnDef(name, type.value, column.nullable))
                is ProtocolResult.Failed -> type
            }
        }
    }

    private fun tdsType(index: Int, type: SqlType, nullable: Boolean): ProtocolResult<TdsType> = when (type) {
        SqlType.Int32 -> ProtocolResult.Ok(if (nullable) TdsType.IntNType(INT_BYTES) else TdsType.Int4Type)
        SqlType.Int64 -> ProtocolResult.Ok(if (nullable) TdsType.IntNType(LONG_BYTES) else TdsType.Int8Type)
        SqlType.Bool -> ProtocolResult.Ok(if (nullable) TdsType.BitNType else TdsType.BitType)
        SqlType.UnicodeText -> ProtocolResult.Ok(TdsType.NVarcharType(TEXT_CHARS, COLLATION))
        is SqlType.Decimal -> decimal(index, type)
        SqlType.Date -> ProtocolResult.Ok(TdsType.DateType)
        SqlType.Time -> ProtocolResult.Ok(TdsType.TimeType(TIME_SCALE))
        SqlType.DateTime -> ProtocolResult.Ok(TdsType.DateTime2Type(TIME_SCALE))
    }

    private fun decimal(index: Int, type: SqlType.Decimal): ProtocolResult<TdsType> {
        val detail = "Spalte $index: Decimal mit Precision ${type.precision} ueber $MAX_PRECISION"
        return if (type.precision > MAX_PRECISION) {
            failed(ProtocolFailure.INVALID_TYPE, detail)
        } else {
            ProtocolResult.Ok(TdsType.DecimalType(type.precision, type.scale))
        }
    }
}
