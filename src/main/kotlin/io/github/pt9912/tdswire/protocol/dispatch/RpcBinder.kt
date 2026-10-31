package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.protocol.rpc.RpcCall
import io.github.pt9912.tdswire.protocol.rpc.RpcParser
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/** Das Ergebnis der Bindung einer RPC-Nachricht: eine Anfrage an das Backend oder die Ablehnung. */
internal sealed interface RpcBinding {
    /**
     * Der Text und die Parameter für den `QueryRequest`. Die Textdarstellung nennt weder Text noch Werte.
     */
    class Query(val sql: String, val parameters: List<QueryParameter>) : RpcBinding {
        override fun toString(): String = "Query(sqlChars=${sql.length}, parameters=$parameters)"
    }

    /**
     * Der RPC erreicht das Backend nicht. Weist der Parser die Nachricht als Struktur- oder Kodierfehler ab
     * ([RpcRejection.ENCODING]), trägt [error] dessen Fehler und der Client erhält den Fehler des Parsers, die
     * Verbindung schließt; sonst ist [error] `null` und der Client erhält `TDS_UNSUPPORTED_RPC` bei offener
     * Verbindung.
     */
    data class Rejected(val reason: RpcRejection, val error: ProtocolError? = null) : RpcBinding
}

/**
 * Bindet die Nutzlast einer RPC-Nachricht an den Backend-Vertrag (`TDS-FA-04.a`, `SPEC-030`,
 * `SPEC-002`). Das Backend erhält immer Text und Parameterliste und nie TDS-Bytes:
 * - `sp_executesql`: der Text der Nachricht unverändert als `sql`, die Wertparameter typisiert in der
 *   Liste ([RpcParameters]); die Deklarationsliste fließt nicht in die Anfrage ein.
 * - RPC per Prozedurname: ein Aufruftext `EXEC <name> @a = @a, ...` ([RpcProcedureName]) und dieselben
 *   Wertparameter. Der Name wird in den Text übernommen, die Werte nie.
 * - Alles andere ist abgelehnt: die Katalogformen `sp_prepexec` und `sp_execute` (Handle-Verwaltung nicht
 *   gebaut), jede andere ProcID und ein Name, den der Katalog führt; ebenso eine Nachricht, die der
 *   Parser abweist (andere Folge, siehe unten).
 *
 * Festlegungen (nicht gemessen): Die Optionsflags und die Kennungen der ALL_HEADERS werden nicht
 * ausgewertet. Ein Struktur- oder Kodierfehler des Parsers (Format, Länge, unbekannte Typkennung,
 * TYPE_INFO, ALL_HEADERS) ist [RpcRejection.ENCODING] und trägt dessen `ProtocolError`; er wird wie ein
 * SQL_BATCH mit Parserfehler beantwortet, die Verbindung schließt. Jede gültig kodierte, aber nicht
 * unterstützte Form ist ohne Fehler abgelehnt (`TDS_UNSUPPORTED_RPC`, die Verbindung bleibt offen), auch ein
 * PLP-Typ (`nvarchar(max)`, [ProtocolFailure.UNSUPPORTED_PLP], als [RpcRejection.TYPE]). Der Rest einer Nachricht
 * hinter einer PLP-Typangabe ist nicht gelesen. Parametername `@@x`: nur beim RPC per Prozedurname abgelehnt
 * ([RpcParameters]). Die Trennung nach Ursache ist eine Nutzerentscheidung vom 2026-10-31, nicht gemessen.
 */
internal object RpcBinder {
    fun bind(payload: ByteArray): RpcBinding = try {
        when (val parsed = RpcParser.parse(payload)) {
            is ProtocolResult.Failed -> rejectedByParser(parsed.error)
            is ProtocolResult.Ok -> bind(parsed.value.call)
        }
    } catch (rejected: RpcRejected) {
        RpcBinding.Rejected(rejected.reason)
    }

    /** Gültig kodiert, aber nicht unterstützt (PLP) ist [RpcRejection.TYPE] ohne Fehler; alles andere ist ENCODING. */
    private fun rejectedByParser(error: ProtocolError): RpcBinding = when (error.failure) {
        ProtocolFailure.UNSUPPORTED_PLP -> RpcBinding.Rejected(RpcRejection.TYPE)
        else -> RpcBinding.Rejected(RpcRejection.ENCODING, error)
    }

    private fun bind(call: RpcCall): RpcBinding = when (call) {
        is RpcCall.ExecuteSql ->
            RpcBinding.Query(call.statement, RpcParameters.map(call.parameters, nameInText = false))

        is RpcCall.ByName -> byName(call)

        is RpcCall.PrepExec, is RpcCall.Execute -> RpcBinding.Rejected(RpcRejection.NOT_BUILT)

        is RpcCall.UnknownProcedure -> RpcBinding.Rejected(RpcRejection.UNKNOWN_PROCEDURE)
    }

    private fun byName(call: RpcCall.ByName): RpcBinding = when {
        !RpcProcedureName.isValid(call.name) -> RpcBinding.Rejected(RpcRejection.PROCEDURE_NAME)

        RpcProcedureName.isCatalogProcedure(call.name) -> RpcBinding.Rejected(RpcRejection.UNKNOWN_PROCEDURE)

        else -> {
            val parameters = RpcParameters.map(call.parameters, nameInText = true)
            RpcBinding.Query(RpcProcedureName.execText(call.name, parameters), parameters)
        }
    }
}
