package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.token.TypeInfoWriter
import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Liest die Parameter einer RPC-Nachricht (`SPEC-030`, `SPEC-003`): je Parameter Namenslänge in
 * Zeichen und Name, Statusbyte, TYPE_INFO ([RpcTypeInfoReader]) und Wert mit Längenpräfix.
 *
 * Festlegungen (ohne Orakel): Die Parameter reichen bis zum Ende der Nachricht; ein Batch-Flag für
 * mehrere RPCs wird nicht erkannt (der Katalog kennt es nicht). Ein Wert mit Längenbyte ist NULL
 * (Länge 0) oder genau so lang, wie die TYPE_INFO vorgibt; ein Zeichen- oder Binärwert ist NULL
 * (`0xFFFF`) oder höchstens so lang wie die höchste Länge der TYPE_INFO, Unicode-Text mit gerader
 * Byte-Zahl. Das Statusbyte wird unverändert übernommen, reservierte Bits werden nicht geprüft.
 *
 * Obergrenze: höchstens [MAX_PARAMETERS] Parameter je Nachricht, sonst `INVALID_RPC_FORM`. Die
 * Zahl ist eine Festlegung ohne Messung (kein Client und kein Server wurde danach befragt); sie
 * begrenzt die Objekte, die eine Nachricht erzeugt: ohne sie ließe eine Nachricht an der
 * Nachrichtengrenze Parameter von 5 Byte (Name leer, NULL) zu Millionen von Objekten werden. Der
 * Parser fängt keinen `OutOfMemoryError`; die Grenze greift, bevor der Parameter gelesen wird.
 */
internal object RpcParameterReader {
    const val MAX_PARAMETERS = 4096
    private const val NULL_COUNTED = 0xFFFF
    private const val NULL_PREFIXED = 0
    private const val BYTE_MASK = 0xFFL
    private const val BYTE_BITS = 8
    private const val DATE_BYTES = 3
    private const val OFFSET_BYTES = 2
    private const val BYTES_PER_CHAR = 2
    private val bytesKinds = setOf(
        RpcTypeKind.BIGVARCHAR,
        RpcTypeKind.BIGCHAR,
        RpcTypeKind.BIGVARBINARY,
        RpcTypeKind.BIGBINARY,
    )

    /** Liest alle Parameter bis zum Ende der Nachricht. */
    fun readAll(reader: ByteReader): List<RpcParameter> {
        val parameters = mutableListOf<RpcParameter>()
        while (reader.remaining > 0) {
            if (parameters.size == MAX_PARAMETERS) {
                violation(ProtocolFailure.INVALID_RPC_FORM, "mehr als $MAX_PARAMETERS Parameter")
            }
            parameters += read(reader)
        }
        return parameters
    }

    private fun read(reader: ByteReader): RpcParameter {
        val name = String(reader.utf16(reader.u8()))
        val status = reader.u8()
        val type = RpcTypeInfoReader.read(reader)
        return RpcParameter(name, status, type, readValue(reader, type))
    }

    private fun readValue(reader: ByteReader, type: RpcType): RpcValue = when (type.kind) {
        RpcTypeKind.NVARCHAR, RpcTypeKind.NCHAR -> readText(reader, type)
        in bytesKinds -> readBytes(reader, type)
        else -> readPrefixed(reader, type)
    }

    private fun countedLength(reader: ByteReader, type: RpcType): Int? {
        val length = reader.u16Le()
        if (length != NULL_COUNTED && length > type.length) {
            violation(ProtocolFailure.INVALID_LENGTH, "${type.kind.name}: $length Byte, hoechstens ${type.length}")
        }
        return length.takeIf { it != NULL_COUNTED }
    }

    private fun readText(reader: ByteReader, type: RpcType): RpcValue {
        val length = countedLength(reader, type) ?: return RpcValue.NullValue
        if (length % BYTES_PER_CHAR != 0) {
            violation(ProtocolFailure.ODD_TEXT_LENGTH, "${type.kind.name}: Wert mit $length Byte")
        }
        return RpcValue.TextValue(String(reader.utf16(length / BYTES_PER_CHAR)))
    }

    private fun readBytes(reader: ByteReader, type: RpcType): RpcValue {
        val length = countedLength(reader, type) ?: return RpcValue.NullValue
        return RpcValue.RawValue(reader.bytes(length))
    }

    private fun readPrefixed(reader: ByteReader, type: RpcType): RpcValue {
        val length = reader.u8()
        if (length == NULL_PREFIXED) {
            return RpcValue.NullValue
        }
        val expected = valueBytes(type)
        if (length != expected) {
            violation(ProtocolFailure.INVALID_LENGTH, "${type.kind.name}: Wert mit $length Byte, erwartet $expected")
        }
        val bytes = reader.bytes(length)
        return when (type.kind) {
            RpcTypeKind.INTN -> RpcValue.IntegerValue(integer(bytes))
            RpcTypeKind.BITN -> RpcValue.BitValue(bytes[0].toInt() != 0)
            else -> RpcValue.RawValue(bytes)
        }
    }

    /** Länge des Werts in Byte, die die TYPE_INFO vorgibt. */
    private fun valueBytes(type: RpcType): Int = when (type.kind) {
        RpcTypeKind.DATEN -> DATE_BYTES
        RpcTypeKind.TIMEN -> TypeInfoWriter.timeBytes(type.scale)
        RpcTypeKind.DATETIME2N -> DATE_BYTES + TypeInfoWriter.timeBytes(type.scale)
        RpcTypeKind.DATETIMEOFFSETN -> DATE_BYTES + OFFSET_BYTES + TypeInfoWriter.timeBytes(type.scale)
        else -> type.length
    }

    /** Little-Endian; ein Byte ist vorzeichenlos (`tinyint`), 2, 4 und 8 Byte sind vorzeichenbehaftet. */
    private fun integer(bytes: ByteArray): Long {
        var value = 0L
        for (index in bytes.indices.reversed()) {
            value = (value shl BYTE_BITS) or (bytes[index].toLong() and BYTE_MASK)
        }
        if (bytes.size > 1) {
            val shift = Long.SIZE_BITS - bytes.size * BYTE_BITS
            value = (value shl shift) shr shift
        }
        return value
    }
}
