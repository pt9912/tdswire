package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.protocol.result.ColumnMapping
import io.github.pt9912.tdswire.protocol.result.ValueMapping
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/** Statusbyte des RETURNVALUE eines OUTPUT-Parameters (Bit 0, Setzung ohne Messung am echten Server). */
private const val RETURN_VALUE_OUTPUT = 0x01

/**
 * Bildet `ReturnStatus` und `OutputParameter` einer RPC-Antwort auf RETURNSTATUS und RETURNVALUE ab und
 * zählt dabei: höchstens ein RETURNSTATUS, und die Ordinale der RETURNVALUE laufen ab 0 in der
 * Reihenfolge der Ereignisse (Festlegungen ohne Messung, `SPEC-030`). Ein Lauf des [ResponseSequencer]
 * hält eine Instanz; sie ist nicht threadsicher.
 */
internal class RpcOutputs {
    private var statusSeen = false
    private var ordinal = 0

    /**
     * RETURNSTATUS zum Ereignis; `null`, wenn schon ein `ReturnStatus` kam (Reihenfolgeverstoß).
     */
    fun status(event: QueryEvent.ReturnStatus): ProtocolResult<Token>? {
        if (statusSeen) return null
        statusSeen = true
        return ProtocolResult.Ok(ReturnStatusToken(event.value))
    }

    /**
     * RETURNVALUE zum Ereignis; [ProtocolResult.Failed], wenn Typ oder Wert die Abbildung nicht schreibt.
     * Das Ordinal ist die Zahl der bisher abgebildeten Ausgabewerte, nicht die Position des Parameters
     * in der Aufrufliste (Setzung ohne Messung).
     */
    fun value(event: QueryEvent.OutputParameter): ProtocolResult<Token> {
        val column = when (val mapped = ColumnMapping.map(listOf(ResultColumn(event.name, event.type, true)))) {
            is ProtocolResult.Failed -> return mapped
            is ProtocolResult.Ok -> mapped.value.single()
        }
        return when (val mapped = ValueMapping.value(column, event.value)) {
            is ProtocolResult.Failed -> mapped

            is ProtocolResult.Ok ->
                ProtocolResult.Ok(ReturnValueToken(ordinal++, column.name, RETURN_VALUE_OUTPUT, column, mapped.value))
        }
    }
}
