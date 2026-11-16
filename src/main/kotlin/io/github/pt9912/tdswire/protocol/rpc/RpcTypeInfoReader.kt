package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Liest die TYPE_INFO eines RPC-Parameters (`SPEC-003`): Typkennung, dann die Angaben des Typs.
 *
 * Festlegungen (ohne Orakel): Die Größe der Typen mit Größenbyte (`INTN` 1/2/4/8, `BITN` 1,
 * `FLTN`/`MONEYN`/`DATETIMN` 4/8, `GUID` 16, Dezimal 5/9/13/17), Precision 1 bis 38, Scale bis
 * Precision (Dezimal) bzw. bis 7 (Zeittypen) sind Grenzen dieses Parsers; die Kombination von
 * Precision und Größe bei `DECIMALN`/`NUMERICN` wird nicht geprüft. Eine Typkennung außerhalb von
 * [RpcTypeKind] macht die Länge des Wertes unbestimmt und ist `INVALID_TYPE` (Strukturfehler). Die Länge `0xFFFF`
 * ist bei `NVARCHAR`, `BIGVARCHAR` und `BIGVARBINARY` PLP (`plp` im [RpcType], Wert als Stück-Folge, siehe
 * [PlpReader]); bei `NCHAR`, `BIGCHAR` und `BIGBINARY` ist sie gültig kodiert, aber kein Katalogtyp und
 * `UNSUPPORTED_PLP` (Festlegung ohne Messung: nur die drei Typen mit veränderlicher Länge nehmen die MAX-Form an,
 * in `SPEC-003` stehen nur sie).
 */
internal object RpcTypeInfoReader {
    const val PLP_MARKER = 0xFFFF
    private const val MAX_PRECISION = 38
    private const val MAX_SCALE = 7
    private const val BIT_SIZE = 1
    private const val GUID_SIZE = 16
    private val characterKinds = setOf(
        RpcTypeKind.NVARCHAR,
        RpcTypeKind.NCHAR,
        RpcTypeKind.BIGVARCHAR,
        RpcTypeKind.BIGCHAR,
    )
    private val plpKinds = setOf(RpcTypeKind.NVARCHAR, RpcTypeKind.BIGVARCHAR, RpcTypeKind.BIGVARBINARY)
    private val integerSizes = setOf(1, 2, 4, 8)
    private val floatSizes = setOf(4, 8)
    private val decimalSizes = setOf(5, 9, 13, 17)

    fun read(reader: ByteReader): RpcType {
        val code = reader.u8()
        val kind = RpcTypeKind.entries.firstOrNull { it.code == code }
            ?: violation(ProtocolFailure.INVALID_TYPE, "Typkennung 0x${Integer.toHexString(code)} nicht im Katalog")
        return when (kind) {
            RpcTypeKind.INTN -> sized(reader, kind, integerSizes)
            RpcTypeKind.BITN -> sized(reader, kind, setOf(BIT_SIZE))
            RpcTypeKind.FLTN, RpcTypeKind.MONEYN, RpcTypeKind.DATETIMN -> sized(reader, kind, floatSizes)
            RpcTypeKind.GUID -> sized(reader, kind, setOf(GUID_SIZE))
            RpcTypeKind.DECIMALN, RpcTypeKind.NUMERICN -> decimal(reader, kind)
            RpcTypeKind.DATEN -> RpcType(kind)
            RpcTypeKind.TIMEN, RpcTypeKind.DATETIME2N, RpcTypeKind.DATETIMEOFFSETN -> scaled(reader, kind)
            in characterKinds -> character(reader, kind)
            else -> binary(reader, kind)
        }
    }

    private fun character(reader: ByteReader, kind: RpcTypeKind): RpcType {
        val length = maxLength(reader, kind)
        return RpcType(kind, length = length, collation = collation(reader), plp = length == PLP_MARKER)
    }

    private fun binary(reader: ByteReader, kind: RpcTypeKind): RpcType {
        val length = maxLength(reader, kind)
        return RpcType(kind, length = length, plp = length == PLP_MARKER)
    }

    private fun sized(reader: ByteReader, kind: RpcTypeKind, allowed: Set<Int>): RpcType {
        val size = reader.u8()
        if (size !in allowed) {
            violation(ProtocolFailure.INVALID_TYPE_INFO, "${kind.name} mit Groesse $size")
        }
        return RpcType(kind, length = size)
    }

    private fun decimal(reader: ByteReader, kind: RpcTypeKind): RpcType {
        val size = reader.u8()
        val precision = reader.u8()
        val scale = reader.u8()
        if (size !in decimalSizes || precision !in 1..MAX_PRECISION || scale > precision) {
            violation(ProtocolFailure.INVALID_TYPE_INFO, "${kind.name}: Groesse $size, Precision $precision/$scale")
        }
        return RpcType(kind, length = size, precision = precision, scale = scale)
    }

    private fun scaled(reader: ByteReader, kind: RpcTypeKind): RpcType {
        val scale = reader.u8()
        if (scale > MAX_SCALE) {
            violation(ProtocolFailure.INVALID_TYPE_INFO, "${kind.name} mit Scale $scale")
        }
        return RpcType(kind, scale = scale)
    }

    private fun maxLength(reader: ByteReader, kind: RpcTypeKind): Int {
        val length = reader.u16Le()
        if (length == PLP_MARKER && kind !in plpKinds) {
            violation(ProtocolFailure.UNSUPPORTED_PLP, "${kind.name} mit PLP (MAX) nicht im Katalog dieses Parsers")
        }
        return length
    }

    private fun collation(reader: ByteReader): Collation = Collation(info = reader.i32Le(), sortId = reader.u8())
}
