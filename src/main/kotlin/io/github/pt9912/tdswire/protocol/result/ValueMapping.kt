package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/**
 * Wertabbildung (`TDS-FA-05.a` Schritt 3 und 5, `SPEC-003`): ein Backendwert auf einen [TdsValue] der
 * Spalte. Eine Zeile wird als Ganzes abgebildet; ein einziger Fehler macht sie zu
 * [ProtocolResult.Failed], bevor ein Wert der Zeile ausgegeben wird (`SPEC-014`, `SPEC-031`). Kein Pfad
 * wirft für einen Wert eine Ausnahme.
 *
 * Festlegung ohne Messung (die Zeilendarstellung ist in ADR-0006 offen): die JVM-Klassen je Spaltenart
 * sind `Int` (32 Bit), `Long` (64 Bit), `Boolean`, `String`, `java.math.BigDecimal`, `LocalDate`,
 * `LocalTime` und `LocalDateTime`; jede andere Klasse, auch ein `Long` in einer 32-Bit-Spalte, ist ein
 * Typkonflikt. `null` ist NULL und gilt nur in einer nullbaren Spalte.
 */
object ValueMapping {
    private const val INT_BYTES = 4
    private const val LONG_BYTES = 8

    fun row(columns: List<ColumnDef>, values: List<Any?>): ProtocolResult<List<TdsValue>> {
        if (columns.size != values.size) {
            return failed(ProtocolFailure.TYPE_MISMATCH, "Zeile mit ${values.size} Werten, ${columns.size} Spalten")
        }
        val results = columns.indices.map { index -> at(index, value(columns[index], values[index])) }
        val mapped = results.filterIsInstance<ProtocolResult.Ok<TdsValue>>().map { it.value }
        return results.filterIsInstance<ProtocolResult.Failed>().firstOrNull() ?: ProtocolResult.Ok(mapped)
    }

    fun value(column: ColumnDef, raw: Any?): ProtocolResult<TdsValue> = when {
        raw != null -> nonNull(column.type, raw)
        column.nullable -> ProtocolResult.Ok(TdsValue.NullValue)
        else -> failed(ProtocolFailure.TYPE_MISMATCH, "NULL in einer Spalte ohne NULL")
    }

    private fun at(index: Int, result: ProtocolResult<TdsValue>): ProtocolResult<TdsValue> = when (result) {
        is ProtocolResult.Ok -> result
        is ProtocolResult.Failed -> failed(result.error.failure, "Spalte $index: ${result.error.detail}")
    }

    private fun nonNull(type: TdsType, raw: Any): ProtocolResult<TdsValue> = when (type) {
        TdsType.Int4Type -> integer32(raw)
        TdsType.Int8Type -> integer64(raw)
        is TdsType.IntNType -> intN(type, raw)
        TdsType.BitType, TdsType.BitNType -> bit(raw)
        is TdsType.NVarcharType -> TextMapping.value(type, raw)
        is TdsType.DecimalType -> DecimalMapping.value(type, raw)
        TdsType.DateType -> TemporalMapping.date(raw)
        is TdsType.TimeType -> TemporalMapping.time(raw)
        is TdsType.DateTime2Type -> TemporalMapping.dateTime(raw)
    }

    private fun intN(type: TdsType.IntNType, raw: Any): ProtocolResult<TdsValue> = when (type.size) {
        INT_BYTES -> integer32(raw)
        LONG_BYTES -> integer64(raw)
        else -> failed(ProtocolFailure.INVALID_TYPE, "Ganzzahl der Groesse ${type.size} nicht im Mindestkatalog")
    }

    private fun integer32(raw: Any): ProtocolResult<TdsValue> =
        if (raw is Int) ProtocolResult.Ok(TdsValue.IntegerValue(raw.toLong())) else mismatch("Int", raw)

    private fun integer64(raw: Any): ProtocolResult<TdsValue> =
        if (raw is Long) ProtocolResult.Ok(TdsValue.IntegerValue(raw)) else mismatch("Long", raw)

    private fun bit(raw: Any): ProtocolResult<TdsValue> =
        if (raw is Boolean) ProtocolResult.Ok(TdsValue.BitValue(raw)) else mismatch("Boolean", raw)
}
