package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/** Die Schreibfunktionen der Tokens mit 2-Byte-Länge: LOGINACK, ENVCHANGE, INFO und ERROR. */
internal object LoginTokenWriters {
    private const val ENV_DATABASE = 1
    private const val ENV_PACKET_SIZE = 4
    private const val ENV_SQL_COLLATION = 7
    private const val COLLATION_BYTES = 5
    private const val MAX_SHORT = 0xFFFF

    fun writeLoginAck(out: ByteWriter, token: LoginAck) {
        ValueSupport.inRange("Interface", token.interfaceType.toLong(), ValueSupport.MAX_BYTE)
        val body = ByteWriter()
        body.u8(token.interfaceType)
        body.i32Be(token.tdsVersion)
        body.bVarchar(token.progName, "ProgName")
        body.i32Be(token.progVersion)
        out.u8(TokenCode.LOGINACK)
        lengthPrefixed(out, body, "LOGINACK")
    }

    fun writeEnvChange(out: ByteWriter, token: EnvChange) {
        val body = ByteWriter()
        when (token) {
            is EnvChange.Database -> {
                body.u8(ENV_DATABASE)
                body.bVarchar(token.newValue, "Datenbank")
                body.bVarchar(token.oldValue, "Datenbank")
            }

            is EnvChange.PacketSize -> {
                ValueSupport.inRange("Packet Size", token.newValue.toLong(), Int.MAX_VALUE.toLong())
                ValueSupport.inRange("Packet Size", token.oldValue.toLong(), Int.MAX_VALUE.toLong())
                body.u8(ENV_PACKET_SIZE)
                body.bVarchar(token.newValue.toString(), "Packet Size")
                body.bVarchar(token.oldValue.toString(), "Packet Size")
            }

            is EnvChange.SqlCollation -> {
                body.u8(ENV_SQL_COLLATION)
                writeCollationValue(body, token.newValue)
                if (token.oldValue != null) {
                    writeCollationValue(body, token.oldValue)
                } else {
                    body.u8(0)
                }
            }
        }
        out.u8(TokenCode.ENVCHANGE)
        lengthPrefixed(out, body, "ENVCHANGE")
    }

    fun writeMessage(out: ByteWriter, code: Int, message: ServerMessage) {
        ValueSupport.inRange("State", message.state.toLong(), ValueSupport.MAX_BYTE)
        ValueSupport.inRange("Severity", message.severity.toLong(), ValueSupport.MAX_BYTE)
        val body = ByteWriter()
        body.i32Le(message.number)
        body.u8(message.state)
        body.u8(message.severity)
        body.usVarchar(message.text, "Meldungstext")
        body.bVarchar(message.serverName, "Servername")
        body.bVarchar(message.procName, "Prozedurname")
        body.i32Le(message.lineNumber)
        out.u8(code)
        lengthPrefixed(out, body, "Meldung")
    }

    private fun writeCollationValue(body: ByteWriter, collation: Collation) {
        body.u8(COLLATION_BYTES)
        TypeInfoWriter.writeCollation(body, collation)
    }

    private fun lengthPrefixed(out: ByteWriter, body: ByteWriter, what: String) {
        if (body.size > MAX_SHORT) {
            violation(ProtocolFailure.TOO_LONG, "$what ueber $MAX_SHORT Byte")
        }
        out.u16Le(body.size)
        out.bytes(body.toByteArray())
    }
}
