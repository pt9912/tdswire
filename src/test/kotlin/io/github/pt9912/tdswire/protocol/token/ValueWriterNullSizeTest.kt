package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [ValueWriter.nullSize] gegen die Bytes, die ein NULL der Spalte in einem ROW belegt. Die Größen für `INTN`, `BITN`,
 * `DECIMALN`, `DATE`, `TIME`, `DATETIME2` (ein Byte) und `NVARCHAR` (zwei Byte) stehen an den Zeilen der Messung
 * `zeilenform-null` (`docs/messungen/zeilenform-nbcrow-2026-11-17.md`, Austausch 01, 08, 10 und 11, beide Instanzen).
 * Die Größen der PLP-Typen (acht Byte) und von `sql_variant` (vier Byte) schreibt der Encoder; am Server gemessen ist
 * nur, dass eine Zeile mit NULL in `nvarchar(max)` oder `varbinary` ein NBCROW ist (Austausch 10), und der Sequenzierer
 * erreicht diese Typen nicht. Die Gleichheit mit dem Encoder ist Selbstkonsistenz.
 */
class ValueWriterNullSizeTest {
    private val latin = Collation.LATIN1_GENERAL_CI_AS

    private fun nullable(type: TdsType) = ColumnDef("c", type, nullable = true)

    private fun written(column: ColumnDef): Int {
        val out = ByteWriter()
        ValueWriter.write(out, column, TdsValue.NullValue)
        return out.toByteArray().size
    }

    /**
     * Wächter: eine Größe, die von dem abweicht, was `write` für NULL schreibt (die Wahl zwischen ROW und NBCROW
     * rechnete dann mit falschen Bytes); die erwarteten Zahlen stehen einzeln da, damit auch eine gemeinsame Änderung
     * beider Seiten auffällt.
     *
     * @spec SPEC-003
     */
    @Test
    fun `die Größe eines NULL im ROW ist die Zahl der geschriebenen Bytes`() {
        val expected = listOf(
            nullable(TdsType.IntNType(4)) to 1,
            nullable(TdsType.IntNType(8)) to 1,
            nullable(TdsType.BitNType) to 1,
            nullable(TdsType.DecimalType(10, 2)) to 1,
            nullable(TdsType.DateType) to 1,
            nullable(TdsType.TimeType(7)) to 1,
            nullable(TdsType.DateTime2Type(7)) to 1,
            nullable(TdsType.NVarcharType(20, latin)) to 2,
            nullable(TdsType.NVarcharMaxType(latin)) to 8,
            nullable(TdsType.VarcharMaxType(latin)) to 8,
            nullable(TdsType.VarbinaryMaxType) to 8,
            nullable(TdsType.SqlVariantType) to 4,
        )
        expected.forEach { (column, size) ->
            assertEquals(size, ValueWriter.nullSize(column), column.type.toString())
            assertEquals(size, written(column), column.type.toString())
        }
    }

    /**
     * Eine Spalte ohne NULL hat keine Größe: `nullSize` ist wie `write` ein Verstoß (`ProtocolViolation`), der Aufrufer
     * ([RowForm]) fragt nur nullbare Spalten.
     *
     * @spec SPEC-003
     */
    @Test
    fun `für eine Spalte ohne NULL gibt es keine Größe`() {
        assertFailsWith<ProtocolViolation> { ValueWriter.nullSize(ColumnDef("c", TdsType.Int4Type, nullable = false)) }
    }
}
