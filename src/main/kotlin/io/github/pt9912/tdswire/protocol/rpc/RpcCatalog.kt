package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Bindet die Parameter einer Katalog-ProcID an ihre Form (`SPEC-030`): `sp_executesql` mit Text
 * und Deklarationsliste an Position 0 und 1, `sp_prepexec` mit OUTPUT-Handle, Deklarationsliste
 * und Text an Position 0 bis 2, `sp_execute` mit Handle an Position 0. Der Rest sind Wertparameter.
 *
 * Festlegungen (ohne Orakel): Text und Deklarationsliste sind `NVARCHAR`/`NCHAR` und nicht NULL;
 * das Handle ist `INTN` mit 4 Byte und nicht NULL, bei `sp_prepexec` zusätzlich OUTPUT. Eine
 * Abweichung ist `INVALID_RPC_FORM`.
 */
internal object RpcCatalog {
    private const val HANDLE_BYTES = 4
    private const val EXECUTESQL_FIXED = 2
    private const val PREPEXEC_FIXED = 3

    fun bind(procId: Int, reader: ByteReader): RpcCall = when (procId) {
        RpcParser.PROC_ID_EXECUTE_SQL -> executeSql(RpcParameterReader.readAll(reader))
        RpcParser.PROC_ID_PREP_EXEC -> prepExec(RpcParameterReader.readAll(reader))
        RpcParser.PROC_ID_EXECUTE -> execute(RpcParameterReader.readAll(reader))
        else -> RpcCall.UnknownProcedure(procId)
    }

    private fun executeSql(all: List<RpcParameter>): RpcCall {
        requireCount(all, EXECUTESQL_FIXED)
        return RpcCall.ExecuteSql(text(all[0]), text(all[1]), all.drop(EXECUTESQL_FIXED))
    }

    private fun prepExec(all: List<RpcParameter>): RpcCall {
        requireCount(all, PREPEXEC_FIXED)
        return RpcCall.PrepExec(handle(all[0], output = true), text(all[1]), text(all[2]), all.drop(PREPEXEC_FIXED))
    }

    private fun execute(all: List<RpcParameter>): RpcCall {
        requireCount(all, 1)
        return RpcCall.Execute(handle(all[0], output = false), all.drop(1))
    }

    private fun requireCount(parameters: List<RpcParameter>, count: Int) {
        if (parameters.size < count) {
            violation(ProtocolFailure.INVALID_RPC_FORM, "${parameters.size} Parameter, mindestens $count")
        }
    }

    private fun text(parameter: RpcParameter): String {
        val value = parameter.value
        if (value !is RpcValue.TextValue) {
            violation(ProtocolFailure.INVALID_RPC_FORM, "Textparameter ist ${parameter.type.kind}, kein Text")
        }
        return value.value
    }

    private fun handle(parameter: RpcParameter, output: Boolean): Int {
        val value = parameter.value
        val isInt4 = parameter.type.kind == RpcTypeKind.INTN && parameter.type.length == HANDLE_BYTES
        if (!isInt4 || value !is RpcValue.IntegerValue) {
            violation(ProtocolFailure.INVALID_RPC_FORM, "Handle ist ${parameter.type.kind}, kein INTN mit 4 Byte")
        }
        if (output && !parameter.output) {
            violation(ProtocolFailure.INVALID_RPC_FORM, "Handle von sp_prepexec ist nicht OUTPUT")
        }
        return value.value.toInt()
    }
}
