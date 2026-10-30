package io.github.pt9912.tdswire.protocol.sqlbatch

import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.protocol.wire.guarded
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Der Inhalt einer SQL_BATCH-Nachricht: der Anweisungstext und die Kennungen der Header im
 * ALL_HEADERS-Block (1 Query Notifications, 2 Transaction Descriptor, 3 Trace Activity ID).
 * Die Textdarstellung nennt nur die Längen: der Text kann Literale und Kennwörter tragen.
 */
class SqlBatchRequest(val text: String, val headerTypes: List<Int>) {
    override fun toString(): String = "SqlBatchRequest(textChars=${text.length}, headerTypes=$headerTypes)"
}

/**
 * Liest die Nachricht vom Pakettyp SQL_BATCH (MS-TDS 2.2.6.7, `SPEC-028`): ALL_HEADERS, dann der
 * Anweisungstext als UTF-16LE bis zum Ende.
 *
 * Festlegungen: Der Parser gilt für TDS 7.2 und höher (das Clientprofil nennt 7.4 und 8.0), ein
 * ALL_HEADERS-Block ist deshalb Pflicht. Die Summe der Header-Längen ist genau die Gesamtlänge des
 * Blocks; der Inhalt der Header wird übersprungen, nicht gedeutet.
 */
object SqlBatchParser {
    private const val TOTAL_LENGTH_BYTES = 4
    private const val HEADER_FIXED_BYTES = 6

    /** Liest die Nachricht; jede Abweichung vom Format ist ein [ProtocolResult.Failed]. */
    fun parse(payload: ByteArray): ProtocolResult<SqlBatchRequest> = guarded { read(payload) }

    private fun read(payload: ByteArray): SqlBatchRequest {
        val reader = ByteReader(payload)
        val total = reader.u32Le()
        if (total < TOTAL_LENGTH_BYTES || total > payload.size) {
            violation(ProtocolFailure.INVALID_HEADER_BLOCK, "ALL_HEADERS $total Byte, Nachricht ${payload.size} Byte")
        }
        val types = readHeaders(reader, total.toInt())
        if (reader.remaining % 2 != 0) {
            violation(ProtocolFailure.ODD_TEXT_LENGTH, "Text mit ${reader.remaining} Byte")
        }
        return SqlBatchRequest(String(reader.utf16(reader.remaining / 2)), types)
    }

    private fun readHeaders(reader: ByteReader, total: Int): List<Int> {
        val types = mutableListOf<Int>()
        while (reader.position < total) {
            val length = reader.u32Le()
            if (length < HEADER_FIXED_BYTES || reader.position - TOTAL_LENGTH_BYTES + length > total) {
                violation(
                    ProtocolFailure.INVALID_HEADER_BLOCK,
                    "Header mit $length Byte bei Position ${reader.position}",
                )
            }
            types += reader.u16Le()
            reader.skip(length.toInt() - HEADER_FIXED_BYTES)
        }
        return types
    }
}
