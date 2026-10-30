package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolErrorCategory
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Hilfen der Abbildungstests: Spalten abbilden, Zeilen abbilden, durch den Encoder schreiben. */
internal object MappingSupport {
    fun defs(vararg columns: ResultColumn): List<ColumnDef> =
        assertIs<ProtocolResult.Ok<List<ColumnDef>>>(ColumnMapping.map(columns.toList())).value

    fun error(result: ProtocolResult<*>): ProtocolError {
        val error = assertIs<ProtocolResult.Failed>(result).error
        assertEquals(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE, error.category)
        return error
    }

    /** Die Bytes von COLMETADATA, wie sie der Encoder aus der abgebildeten Spaltenliste schreibt. */
    fun encodedMetadata(columns: List<ResultColumn>): String {
        val defs = assertIs<ProtocolResult.Ok<List<ColumnDef>>>(ColumnMapping.map(columns)).value
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(defs)))
        return encoder.toByteArray().toHex()
    }

    /** Die Bytes eines ROW-Tokens: Zeile abbilden, durch den Encoder schreiben, ohne das COLMETADATA davor. */
    fun encodedRow(columns: List<ResultColumn>, row: List<Any?>): String {
        val defs = assertIs<ProtocolResult.Ok<List<ColumnDef>>>(ColumnMapping.map(columns)).value
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(defs)))
        val before = encoder.toByteArray().size
        val values = assertIs<ProtocolResult.Ok<List<TdsValue>>>(ValueMapping.row(defs, row)).value
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(RowToken(values)))
        return encoder.toByteArray().copyOfRange(before, encoder.toByteArray().size).toHex()
    }
}
