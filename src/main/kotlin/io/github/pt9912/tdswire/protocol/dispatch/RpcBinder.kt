package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.protocol.rpc.RpcCall
import io.github.pt9912.tdswire.protocol.rpc.RpcParser
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/** Das Ergebnis der Bindung einer RPC-Nachricht: eine Anfrage an das Backend oder die Ablehnung. */
internal sealed interface RpcBinding {
    /**
     * Der Text und die Parameter für den `QueryRequest`. Die Textdarstellung nennt weder Text noch Werte.
     */
    class Query(val sql: String, val parameters: List<QueryParameter>) : RpcBinding {
        override fun toString(): String = "Query(sqlChars=${sql.length}, parameters=$parameters)"
    }

    /** Der RPC erreicht das Backend nicht; der Client bekommt `TDS_UNSUPPORTED_RPC`. */
    data class Rejected(val reason: RpcRejection) : RpcBinding
}

/**
 * Bindet die Nutzlast einer RPC-Nachricht an den Backend-Vertrag (`TDS-FA-04.a`, `SPEC-030`,
 * `SPEC-002`). Das Backend erhält immer Text und Parameterliste und nie TDS-Bytes:
 * - `sp_executesql`: der Text der Nachricht unverändert als `sql`, die Wertparameter typisiert in der
 *   Liste ([RpcParameters]); die Deklarationsliste fließt nicht in die Anfrage ein.
 * - RPC per Prozedurname: ein Aufruftext `EXEC <name> @a = @a, ...` ([RpcProcedureName]) und dieselben
 *   Wertparameter. Der Name wird in den Text übernommen, die Werte nie.
 * - Alles andere ist abgelehnt: die Katalogformen `sp_prepexec` und `sp_execute` (Handle-Verwaltung nicht
 *   gebaut), jede andere ProcID, ein Name, den der Katalog führt, und jede Nachricht, die der Parser
 *   abweist.
 *
 * Festlegungen (nicht gemessen): Die Optionsflags und die Kennungen der ALL_HEADERS werden nicht
 * ausgewertet. Eine Nachricht, die der Parser abweist, ist [RpcRejection.ENCODING] und wird wie ein
 * unbekannter RPC beantwortet (die Nachricht ist vollständig gerahmt, der Strom bleibt synchron).
 */
internal object RpcBinder {
    fun bind(payload: ByteArray): RpcBinding = try {
        when (val parsed = RpcParser.parse(payload)) {
            is ProtocolResult.Failed -> RpcBinding.Rejected(RpcRejection.ENCODING)
            is ProtocolResult.Ok -> bind(parsed.value.call)
        }
    } catch (rejected: RpcRejected) {
        RpcBinding.Rejected(rejected.reason)
    }

    private fun bind(call: RpcCall): RpcBinding = when (call) {
        is RpcCall.ExecuteSql -> RpcBinding.Query(call.statement, RpcParameters.map(call.parameters))
        is RpcCall.ByName -> byName(call)
        is RpcCall.PrepExec, is RpcCall.Execute -> RpcBinding.Rejected(RpcRejection.NOT_BUILT)
        is RpcCall.UnknownProcedure -> RpcBinding.Rejected(RpcRejection.UNKNOWN_PROCEDURE)
    }

    private fun byName(call: RpcCall.ByName): RpcBinding = when {
        !RpcProcedureName.isValid(call.name) -> RpcBinding.Rejected(RpcRejection.PROCEDURE_NAME)

        RpcProcedureName.isCatalogProcedure(call.name) -> RpcBinding.Rejected(RpcRejection.UNKNOWN_PROCEDURE)

        else -> {
            val parameters = RpcParameters.map(call.parameters)
            RpcBinding.Query(RpcProcedureName.execText(call.name, parameters), parameters)
        }
    }
}
