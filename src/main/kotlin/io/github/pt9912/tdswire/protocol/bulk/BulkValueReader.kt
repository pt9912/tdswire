package io.github.pt9912.tdswire.protocol.bulk

import io.github.pt9912.tdswire.protocol.dispatch.RpcRejected
import io.github.pt9912.tdswire.protocol.dispatch.RpcWireValues
import io.github.pt9912.tdswire.protocol.rpc.RpcType
import io.github.pt9912.tdswire.protocol.rpc.RpcTypeKind
import io.github.pt9912.tdswire.protocol.token.TypeInfoWriter
import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation
import java.nio.charset.Charset

/**
 * Liest einen Wert eines ROW-Tokens der Nachricht BULK_LOAD (`MS-TDS` 2.2.5.5, Wertebytes mit Längenpräfix) nach der
 * TYPE_INFO seiner Spalte; Aufrufer ist [BulkLoadReader]. Die Klassen der Werte sind die von `ValueMapping`: `Int`
 * und `Long` für `INTN` (8 Byte `Long`, sonst `Int`), `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`,
 * `LocalDateTime`; `null` ist NULL.
 *
 * Festlegungen ohne Messung am Server: Ein `BIGVARCHAR` und `BIGCHAR` wird als Windows-1252 gelesen, gleich welche
 * Kollation die Spalte trägt (für ASCII-Werte, die d-migrate so für Datum, Zeit und Zeitpunkt sendet, gleich jeder
 * Kollation). Ein Dezimalwert darf kürzer sein als die Typlänge (Vorzeichen und mindestens ein Byte Betrag): so
 * sendet der Treiber ihn im RPC (Messprotokoll des Spikes, a7). Ein Wert außerhalb seines Bereichs ist
 * `VALUE_OUT_OF_RANGE`, eine Länge, die die TYPE_INFO nicht zulässt, `INVALID_LENGTH`.
 */
internal object BulkValueReader {
    private const val NULL_COUNTED = 0xFFFF
    private const val BYTES_PER_CHAR = 2
    private const val TINY = 1
    private const val SMALL = 2
    private const val LONG_SIZE = 8
    private const val DATE_BYTES = 3
    private const val BYTE_MASK = 0xFFL
    private val windows1252: Charset = Charset.forName("windows-1252")

    fun read(reader: ByteReader, type: RpcType): Any? = when (type.kind) {
        RpcTypeKind.NVARCHAR, RpcTypeKind.NCHAR -> counted(reader, type)?.let { text(reader, it, Charsets.UTF_16LE) }
        RpcTypeKind.BIGVARCHAR, RpcTypeKind.BIGCHAR -> counted(reader, type)?.let { text(reader, it, windows1252) }
        else -> prefixed(reader, type)
    }

    /** Die Länge in Byte oder `null` für NULL (`0xFFFF`); eine Länge über der der TYPE_INFO ist ein Fehler. */
    private fun counted(reader: ByteReader, type: RpcType): Int? {
        val length = reader.u16Le()
        if (length == NULL_COUNTED) return null
        if (length > type.length) {
            violation(ProtocolFailure.INVALID_LENGTH, "${type.kind.name}: $length Byte, hoechstens ${type.length}")
        }
        return length
    }

    private fun text(reader: ByteReader, length: Int, charset: Charset): String {
        if (charset == Charsets.UTF_16LE && length % BYTES_PER_CHAR != 0) {
            violation(ProtocolFailure.ODD_TEXT_LENGTH, "Text mit $length Byte")
        }
        return String(reader.bytes(length), charset)
    }

    private fun prefixed(reader: ByteReader, type: RpcType): Any? {
        val length = reader.u8()
        if (length == 0) return null
        return when (type.kind) {
            RpcTypeKind.INTN -> {
                exactly(length, type.length, type)
                integer(reader.bytes(length), type.length)
            }

            RpcTypeKind.BITN -> {
                exactly(length, 1, type)
                reader.u8() != 0
            }

            RpcTypeKind.DECIMALN, RpcTypeKind.NUMERICN -> {
                if (length < SMALL || length > type.length) {
                    violation(ProtocolFailure.INVALID_LENGTH, "${type.kind.name}: Wert mit $length Byte")
                }
                temporal(type, reader.bytes(length))
            }

            RpcTypeKind.DATEN -> {
                exactly(length, DATE_BYTES, type)
                temporal(type, reader.bytes(length))
            }

            RpcTypeKind.TIMEN -> {
                exactly(length, TypeInfoWriter.timeBytes(type.scale), type)
                temporal(type, reader.bytes(length))
            }

            else -> {
                exactly(length, DATE_BYTES + TypeInfoWriter.timeBytes(type.scale), type)
                temporal(type, reader.bytes(length))
            }
        }
    }

    private fun exactly(length: Int, expected: Int, type: RpcType) {
        if (length != expected) {
            violation(ProtocolFailure.INVALID_LENGTH, "${type.kind.name}: Wert mit $length Byte, erwartet $expected")
        }
    }

    /** Dezimal-, Datums- und Zeitwerte decodiert [RpcWireValues]; sein Bereichsfehler wird ein Fehler des Lesers. */
    private fun temporal(type: RpcType, bytes: ByteArray): Any = try {
        RpcWireValues.decode(type, bytes)
    } catch (ignored: RpcRejected) {
        violation(ProtocolFailure.VALUE_OUT_OF_RANGE, "${type.kind.name}: Wert ausserhalb des Bereichs")
    }

    /** Little-Endian; ein Byte ist vorzeichenlos (`tinyint`), 2, 4 und 8 Byte sind vorzeichenbehaftet. */
    private fun integer(bytes: ByteArray, size: Int): Any {
        var value = 0L
        for (index in bytes.indices.reversed()) {
            value = (value shl Byte.SIZE_BITS) or (bytes[index].toLong() and BYTE_MASK)
        }
        if (size > TINY) {
            val shift = Long.SIZE_BITS - size * Byte.SIZE_BITS
            value = (value shl shift) shr shift
        }
        return if (size == LONG_SIZE) value else value.toInt()
    }
}
