package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/** Tokenkennungen der Antwortseite (MS-TDS 2.2.7). */
internal object TokenCode {
    const val COLMETADATA = 0x81
    const val ERROR = 0xAA
    const val INFO = 0xAB
    const val RETURNVALUE = 0xAC
    const val LOGINACK = 0xAD
    const val ENVCHANGE = 0xE3
    const val ROW = 0xD1
    const val RETURNSTATUS = 0x79
}

/** Die Schreibfunktionen je Token; eine Verletzung der Grenzen eines Felds meldet [violation]. */
internal object TokenWriters {
    private const val MAX_SHORT = 0xFFFF
    private const val MAX_COLUMNS = 0xFFFE

    fun write(out: ByteWriter, token: Token, columns: List<ColumnDef>?) {
        when (token) {
            is LoginAck -> LoginTokenWriters.writeLoginAck(out, token)
            is EnvChange -> LoginTokenWriters.writeEnvChange(out, token)
            is InfoToken -> LoginTokenWriters.writeMessage(out, TokenCode.INFO, token.message)
            is ErrorToken -> LoginTokenWriters.writeMessage(out, TokenCode.ERROR, token.message)
            is DoneToken -> writeDone(out, token)
            is ColMetadataToken -> writeColMetadata(out, token)
            is RowToken -> writeRow(out, token, columns)
            is ReturnStatusToken -> writeReturnStatus(out, token)
            is ReturnValueToken -> writeReturnValue(out, token)
        }
    }

    private fun writeReturnStatus(out: ByteWriter, token: ReturnStatusToken) {
        out.u8(TokenCode.RETURNSTATUS)
        out.i32Le(token.value)
    }

    private fun writeDone(out: ByteWriter, token: DoneToken) {
        ValueSupport.inRange("Status", token.status.toLong(), MAX_SHORT.toLong())
        ValueSupport.inRange("CurCmd", token.curCmd.toLong(), MAX_SHORT.toLong())
        ValueSupport.inRange("RowCount", token.rowCount, Long.MAX_VALUE)
        out.u8(token.kind.code)
        out.u16Le(token.status)
        out.u16Le(token.curCmd)
        out.i64Le(token.rowCount)
    }

    private fun writeColMetadata(out: ByteWriter, token: ColMetadataToken) {
        if (token.columns.size > MAX_COLUMNS) {
            violation(ProtocolFailure.TOO_LONG, "COLMETADATA mit ${token.columns.size} Spalten")
        }
        out.u8(TokenCode.COLMETADATA)
        out.u16Le(token.columns.size)
        token.columns.forEach { writeColumn(out, it) }
    }

    private fun writeColumn(out: ByteWriter, column: ColumnDef) {
        out.i32Le(column.userType)
        out.u16Le(TypeInfoWriter.flags(column))
        TypeInfoWriter.writeTypeInfo(out, column)
        out.bVarchar(column.name, "Spaltenname")
    }

    private fun writeRow(out: ByteWriter, token: RowToken, columns: List<ColumnDef>?) {
        if (columns == null) {
            violation(ProtocolFailure.NO_COLUMN_METADATA, "ROW ohne vorheriges COLMETADATA")
        }
        if (columns.size != token.values.size) {
            violation(ProtocolFailure.TYPE_MISMATCH, "ROW mit ${token.values.size} Werten, ${columns.size} Spalten")
        }
        out.u8(TokenCode.ROW)
        columns.zip(token.values).forEach { (column, value) -> ValueWriter.write(out, column, value) }
    }

    private fun writeReturnValue(out: ByteWriter, token: ReturnValueToken) {
        ValueSupport.inRange("Ordinal", token.ordinal.toLong(), MAX_SHORT.toLong())
        ValueSupport.inRange("Status", token.status.toLong(), ValueSupport.MAX_BYTE)
        out.u8(TokenCode.RETURNVALUE)
        out.u16Le(token.ordinal)
        out.bVarchar(token.name, "Parametername")
        out.u8(token.status)
        out.i32Le(token.column.userType)
        out.u16Le(TypeInfoWriter.flags(token.column))
        TypeInfoWriter.writeTypeInfo(out, token.column)
        ValueWriter.write(out, token.column, token.value)
    }
}
