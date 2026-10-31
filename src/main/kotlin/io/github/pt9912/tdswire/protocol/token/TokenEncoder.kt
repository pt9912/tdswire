package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.protocol.wire.guarded

/**
 * Schreibt Antwort-Tokens in einen Tokenstrom (`TDS-FA-05`, `TDS-FA-06`, `TDS-FA-10`, `SPEC-003`,
 * `SPEC-028`). Der Encoder hält das zuletzt geschriebene COLMETADATA, gegen das er ROW-Werte
 * schreibt; die TDS-Version ist 7.2 und höher (8-Byte-Zeilenzahl im DONE, 4-Byte-Zeilennummer in
 * INFO und ERROR, 4-Byte-UserType in den Spalten).
 *
 * Ein Token, das der Encoder nicht schreiben kann, ist ein [ProtocolResult.Failed] und ändert den
 * Strom nicht; der Aufrufer entscheidet über ERROR und DONE mit Fehlerstatus (`ADR-0007`). Der
 * Encoder prüft nicht die Reihenfolge der Tokens, nur dass ROW ein COLMETADATA vorfindet.
 * Nicht thread-sicher.
 */
class TokenEncoder {
    private var stream = ByteWriter()
    private var columns: List<ColumnDef>? = null

    /** Schreibt [token] an das Ende des Stroms. */
    fun write(token: Token): ProtocolResult<Unit> {
        val piece = ByteWriter()
        val result = guarded { TokenWriters.write(piece, token, columns) }
        if (result is ProtocolResult.Ok) {
            stream.bytes(piece.toByteArray())
            if (token is ColMetadataToken) {
                columns = token.columns
            }
        }
        return result
    }

    /** Die bisher geschriebenen Bytes des Tokenstroms. */
    fun toByteArray(): ByteArray = stream.toByteArray()

    /**
     * Die seit dem letzten Aufruf geschriebenen Bytes; der Strom ist danach leer, das zuletzt
     * geschriebene COLMETADATA gilt für folgende ROW-Tokens weiter. Der Encoder hält dann nur noch
     * Bytes, die noch nicht abgeholt sind.
     */
    fun drain(): ByteArray {
        val bytes = stream.toByteArray()
        stream = ByteWriter()
        return bytes
    }
}
