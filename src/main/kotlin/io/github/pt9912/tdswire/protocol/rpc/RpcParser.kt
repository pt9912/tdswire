package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.wire.AllHeaders
import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.protocol.wire.guarded
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Liest die Nachricht vom Pakettyp RPC (`SPEC-028`, `SPEC-030`): ALL_HEADERS, Prozedur per ProcID
 * oder Name, Optionsflags und die Parameter. Der Parser liefert die Form des Katalogs und
 * entscheidet nichts: Prepare-Handles, Ausführung und Antwort gehören dem Ablauf.
 *
 * Belegt ist der Katalog nur am Testserver des Spikes (TDS 7.4 unverschlüsselt, `Pooling=false`,
 * kein Microsoft-SQL-Server); die Tests belegen Handvektoren nach MS-TDS, nicht die Annahme durch
 * einen Client.
 *
 * Festlegungen (ohne Orakel): Der Parser gilt für TDS 7.2 und höher, ALL_HEADERS ist Pflicht.
 * `NameLenProcID` ist die Zeichenzahl des Namens, `0xFFFF` leitet eine ProcID ein; die Länge 0 ist
 * ein Fehler. Eine ProcID außerhalb von 10, 12 und 13 ist [RpcCall.UnknownProcedure]; Optionsflags
 * werden gelesen, Parameter nicht. Die Parameter von `sp_executesql`, `sp_prepexec` und
 * `sp_execute` werden nach Position gedeutet; die Positionen belegt die Messmatrix von ADR-0005
 * (Testserver), dass der Parser die Namen dabei ignoriert, ist Festlegung.
 */
object RpcParser {
    private const val ID_MARKER = 0xFFFF
    private const val NO_PROC_ID = -1
    const val PROC_ID_EXECUTE_SQL = 10
    const val PROC_ID_EXECUTE = 12
    const val PROC_ID_PREP_EXEC = 13

    /** Liest die Nachricht; jede Abweichung vom Format ist ein [ProtocolResult.Failed]. */
    fun parse(payload: ByteArray): ProtocolResult<RpcRequest> = guarded { read(payload) }

    private fun read(payload: ByteArray): RpcRequest {
        val reader = ByteReader(payload)
        val headerTypes = AllHeaders.read(reader, payload.size)
        val lead = reader.u16Le()
        val procId = if (lead == ID_MARKER) reader.u16Le() else NO_PROC_ID
        val name = if (lead == ID_MARKER) null else readName(reader, lead)
        val optionFlags = reader.u16Le()
        val call = if (name == null) {
            RpcCatalog.bind(procId, reader)
        } else {
            RpcCall.ByName(name, RpcParameterReader.readAll(reader))
        }
        return RpcRequest(headerTypes, optionFlags, call)
    }

    private fun readName(reader: ByteReader, length: Int): String {
        if (length == 0) {
            violation(ProtocolFailure.INVALID_LENGTH, "Prozedurname mit Laenge 0")
        }
        return String(reader.utf16(length))
    }
}
