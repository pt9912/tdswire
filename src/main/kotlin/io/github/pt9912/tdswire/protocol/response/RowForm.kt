package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.NullBitmapRowToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.ValueWriter

/**
 * Die Wahl zwischen ROW und NBCROW für eine Zeile (`SPEC-003`, gemessen am echten SQL Server 2022 und 2025,
 * Messprotokoll `docs/messungen/zeilenform-nbcrow-2026-11-17.md`): NBCROW genau dann, wenn er **kürzer** ist als ROW.
 * Die Null-Bitmap kostet `ceil(n / 8)` Byte, und jeder NULL-Wert, den NBCROW auslässt, spart die Bytes, die ein NULL im
 * ROW belegt ([ValueWriter.nullSize]: `INTN`, `BITN`, `DECIMALN`, `DATE`, `TIME`, `DATETIME2` ein Byte, `NVARCHAR`
 * zwei). Bei Gleichstand steht ROW. Gemessen ist die Regel bis 17 Spalten (Bitmap von drei Byte); darüber gilt dieselbe
 * Rechnung als Setzung.
 */
internal object RowForm {
    private const val BITMAP_BITS = 8

    /** Die Größe eines NULL im ROW je Spalte, 0 für eine Spalte ohne NULL; wird einmal je Ergebnis berechnet. */
    fun nullSizes(columns: List<ColumnDef>): List<Int> =
        columns.map { if (it.nullable) ValueWriter.nullSize(it) else 0 }

    /**
     * Das Token der Zeile [values]: [NullBitmapRowToken], wenn die ausgelassenen NULL-Werte mehr Bytes sparen, als die
     * Bitmap kostet, sonst [RowToken]. [nullSizes] sind die Größen aus [nullSizes] für dieselben Spalten.
     */
    fun token(values: List<TdsValue>, nullSizes: List<Int>): Token {
        val saved = values.indices.sumOf { if (values[it] is TdsValue.NullValue) nullSizes[it] else 0 }
        val bitmap = (values.size + BITMAP_BITS - 1) / BITMAP_BITS
        return if (saved > bitmap) NullBitmapRowToken(values) else RowToken(values)
    }
}
