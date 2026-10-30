package io.github.pt9912.tdswire.protocol.prelogin

import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.protocol.wire.guarded
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Liest und schreibt die PRELOGIN-Nachricht (MS-TDS 2.2.6.5, `SPEC-028`): eine Tabelle aus Kennung
 * (1 Byte), Offset und Länge (je 2 Byte Big-Endian), beendet durch `0xFF`, dann die Daten.
 * Offsets zählen ab dem Anfang der Nachricht.
 *
 * Festlegungen: Ein Offset liegt hinter dem Terminator, und Offset plus Länge liegen in der
 * Nachricht. Bekannte Optionen haben ihre feste Länge (VERSION 6, ENCRYPTION 1, THREADID 4,
 * MARS 1, TRACEID 36, FEDAUTHREQUIRED 1, NONCEOPT 32); INSTOPT hat beliebige Länge, unbekannte
 * Kennungen bleiben ungeprüft erhalten.
 */
object PreloginCodec {
    private const val ENTRY_BYTES = 5
    private const val TERMINATOR_BYTES = 1
    private const val MAX_OFFSET = 0xFFFF

    private val fixedLengths = mapOf(
        PreloginToken.VERSION to 6,
        PreloginToken.ENCRYPTION to 1,
        PreloginToken.THREADID to 4,
        PreloginToken.MARS to 1,
        PreloginToken.TRACEID to 36,
        PreloginToken.FEDAUTHREQUIRED to 1,
        PreloginToken.NONCEOPT to 32,
    )

    /** Liest die Nachricht; jede Abweichung vom Format ist ein [ProtocolResult.Failed]. */
    fun parse(payload: ByteArray): ProtocolResult<PreloginMessage> = guarded {
        val reader = ByteReader(payload)
        val entries = readTable(reader)
        val tableEnd = reader.position
        PreloginMessage(entries.map { entry -> readOption(reader, entry, tableEnd, payload.size) })
    }

    /** Schreibt die Nachricht: Tabelle, Terminator, Daten in der Reihenfolge der Optionen. */
    fun encode(message: PreloginMessage): ProtocolResult<ByteArray> = guarded {
        val tableBytes = message.options.size * ENTRY_BYTES + TERMINATOR_BYTES
        val writer = ByteWriter()
        var offset = tableBytes
        message.options.forEach { option ->
            checkFixedLength(option.token, option.data.size)
            if (offset + option.data.size > MAX_OFFSET) {
                violation(ProtocolFailure.TOO_LONG, "PRELOGIN-Nachricht ueber $MAX_OFFSET Byte")
            }
            writer.u8(option.token)
            writer.u16Be(offset)
            writer.u16Be(option.data.size)
            offset += option.data.size
        }
        writer.u8(PreloginToken.TERMINATOR)
        message.options.forEach { writer.bytes(it.data) }
        writer.toByteArray()
    }

    private class Entry(val token: Int, val offset: Int, val length: Int)

    private fun readTable(reader: ByteReader): List<Entry> {
        val entries = mutableListOf<Entry>()
        while (true) {
            if (reader.remaining == 0) {
                violation(ProtocolFailure.MISSING_TERMINATOR, "Tabelle ohne Terminator 0xFF")
            }
            val token = reader.u8()
            if (token == PreloginToken.TERMINATOR) {
                return entries
            }
            entries += Entry(token, reader.u16Be(), reader.u16Be())
        }
    }

    private fun readOption(reader: ByteReader, entry: Entry, tableEnd: Int, size: Int): PreloginOption {
        checkFixedLength(entry.token, entry.length)
        if (entry.offset < tableEnd || entry.offset + entry.length > size) {
            violation(
                ProtocolFailure.INVALID_OFFSET,
                "Option ${entry.token}: Offset ${entry.offset}, Laenge ${entry.length}, Nachricht $size Byte",
            )
        }
        reader.seek(entry.offset)
        return PreloginOption(entry.token, reader.bytes(entry.length))
    }

    private fun checkFixedLength(token: Int, length: Int) {
        val expected = fixedLengths[token]
        if (expected != null && expected != length) {
            violation(ProtocolFailure.INVALID_OPTION_LENGTH, "Option $token: Laenge $length statt $expected")
        }
    }
}
