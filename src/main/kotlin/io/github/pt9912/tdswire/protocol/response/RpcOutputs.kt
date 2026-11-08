package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.protocol.result.ColumnMapping
import io.github.pt9912.tdswire.protocol.result.ValueMapping
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/** Statusbyte des RETURNVALUE eines OUTPUT-Parameters (Bit 0; Messprotokoll W-5, Status 1 an `@id`). */
private const val RETURN_VALUE_OUTPUT = 0x01

/** Breite des Handles von `sp_prepexec` in Byte (`INTN` 4). */
private const val HANDLE_BYTES = 4

/** Der Name, unter dem der Dispatch das Handle von `sp_prepexec` als `OutputParameter` einspeist: leer wie am Draht. */
internal const val HANDLE_OUTPUT_NAME = ""

/**
 * Wo die Wertparameter eines RPC in der Aufrufliste stehen (`TDS-FA-10.a` Schritt 3, Messprotokoll W-5):
 * [firstValuePosition] ist die Position des ersten Wertparameters (`sp_executesql` Text und Deklarationsliste davor: 2,
 * `sp_prepexec` Handle, Deklarationsliste und Text: 3, `sp_execute` das Handle: 1, Prozedurname: 0). [hasHandle] sagt,
 * dass der Aufruf ein Handle als OUTPUT-Parameter an Position 0 führt, das der Dispatch einspeist (`sp_prepexec`).
 */
internal class CallLayout(val firstValuePosition: Int, val hasHandle: Boolean = false) {
    companion object {
        val EXECUTE_SQL = CallLayout(2)
        val PREPARE_EXECUTE = CallLayout(3, hasHandle = true)
        val EXECUTE_PREPARED = CallLayout(1)
        val PROCEDURE = CallLayout(0)
    }
}

/**
 * Sammelt `ReturnStatus` und `OutputParameter` einer RPC-Antwort, bis die Antwort endet: höchstens ein RETURNSTATUS,
 * und die RETURNVALUE tragen als Ordinal die Position ihres Parameters in der Aufrufliste und stehen in dieser
 * Reihenfolge (gemessen, W-5). Der Name des Ereignisses findet den Parameter in [parameters] (ohne Beachtung der
 * Schreibweise); ein Name außerhalb der Liste bekommt die Position hinter dem letzten Parameter (Setzung ohne
 * Messung). Das Handle von `sp_prepexec` hat Position 0 (Messprotokoll A-19); das Flag-Feld jedes RETURNVALUE ist
 * `0x0000` (`TokenWriters`). Ein Lauf des [ResponseSequencer] hält eine Instanz; sie ist nicht threadsicher.
 */
internal class RpcOutputs(private val layout: CallLayout, private val parameters: List<QueryParameter>) {
    private class Placed(val position: Int, val token: ReturnValueToken)

    private val placed = mutableListOf<Placed>()
    private var unlisted = 0
    private var handleSeen = false

    /** Der `ReturnStatus` des Backends; `null`, solange keiner kam. */
    var status: Int? = null
        private set

    /** Merkt den `ReturnStatus`; `false`, wenn schon einer kam (Reihenfolgeverstoß). */
    fun status(event: QueryEvent.ReturnStatus): Boolean {
        if (status != null) return false
        status = event.value
        return true
    }

    /**
     * Merkt das RETURNVALUE zum Ereignis; [ProtocolResult.Failed], wenn Typ oder Wert die Abbildung nicht schreibt.
     */
    fun value(event: QueryEvent.OutputParameter): ProtocolResult<Unit> {
        val isHandle = layout.hasHandle && !handleSeen && event.name == HANDLE_OUTPUT_NAME
        val column = if (isHandle) {
            handleColumn()
        } else {
            when (val mapped = ColumnMapping.map(listOf(ResultColumn(event.name, event.type, true)))) {
                is ProtocolResult.Failed -> return mapped
                is ProtocolResult.Ok -> mapped.value.single()
            }
        }
        return when (val mapped = ValueMapping.value(column, event.value)) {
            is ProtocolResult.Failed -> mapped

            is ProtocolResult.Ok -> {
                handleSeen = handleSeen || isHandle
                val position = if (isHandle) HANDLE_POSITION else positionOf(event.name)
                val token = ReturnValueToken(position, column.name, RETURN_VALUE_OUTPUT, column, mapped.value)
                placed += Placed(position, token)
                ProtocolResult.Ok(Unit)
            }
        }
    }

    /** Die RETURNVALUE in der Reihenfolge der Aufrufliste; gleiche Positionen behalten die Reihenfolge des Stroms. */
    fun returnValues(): List<ReturnValueToken> = placed.sortedBy { it.position }.map { it.token }

    private fun handleColumn() = ColumnDef(HANDLE_OUTPUT_NAME, TdsType.IntNType(HANDLE_BYTES), nullable = false)

    private fun positionOf(name: String): Int {
        val index = parameters.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        return layout.firstValuePosition + if (index >= 0) index else parameters.size + unlisted++
    }

    private companion object {
        const val HANDLE_POSITION = 0
    }
}
