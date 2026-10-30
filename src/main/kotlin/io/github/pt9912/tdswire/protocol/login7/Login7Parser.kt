package io.github.pt9912.tdswire.protocol.login7

import io.github.pt9912.tdswire.protocol.wire.ByteReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.protocol.wire.guarded
import io.github.pt9912.tdswire.protocol.wire.violation

/**
 * Liest die LOGIN7-Nachricht (MS-TDS 2.2.6.4, `TDS-FA-02.a`, `SPEC-028`): den festen Kopf von 94
 * Byte mit der Offset-/Längen-Tabelle, die UTF-16-Felder, das entschleierte Passwort und die
 * Merkmale `fIntSecurity`, `fChangePassword` und FeatureExt-Option `0x02`. Der Parser liefert
 * Felder und entscheidet nichts (`ADR-0009`). Die Grenze von 128 UTF-16-Einheiten für Kennung und
 * Passwort (`ADR-0009`, `spec/spezifikation.md` `SPEC-051`) gilt nicht im Parser, sondern im Anmeldepfad;
 * der Parser liest bis 65535 Einheiten je Feld.
 *
 * Festlegungen: Das Längenfeld stimmt mit der Nachrichtenlänge überein. Ein Feld mit Länge 0 wird
 * nicht an seinem Offset geprüft; jedes andere liegt hinter dem festen Kopf und endet in der
 * Nachricht. Die FeatureExt-Tabelle wird nur gelesen, wenn `fExtension` gesetzt ist; ihr Zeiger
 * (`cbExtension`) ist dann 4 Byte lang. Der SSPI-Block wird nur gemessen, nicht gelesen.
 */
object Login7Parser {
    const val FIXED_BYTES = 94
    private const val EXTENSION_POINTER_BYTES = 4
    private const val SSPI_LONG_MARKER = 0xFFFF
    private const val CLIENT_ID_BYTES = 6
    private const val FEATURE_TERMINATOR = 0xFF
    private const val BYTE_MASK = 0xFF
    private const val BYTE_BITS = 8

    /** Liest die Nachricht; jede Abweichung vom Format ist ein [ProtocolResult.Failed]. */
    fun parse(payload: ByteArray): ProtocolResult<Login7Request> = guarded { read(payload) }

    private class Slot(val name: String, val offset: Int, val count: Int)

    /** Die Felder der Offset-/Längen-Tabelle in der Reihenfolge der Nachricht. */
    private enum class Field(val label: String, val skipBefore: Int = 0) {
        HOST_NAME("HostName"),
        USER_NAME("UserName"),
        PASSWORD("Password"),
        APP_NAME("AppName"),
        SERVER_NAME("ServerName"),
        EXTENSION("Extension"),
        CLT_INT_NAME("CltIntName"),
        LANGUAGE("Language"),
        DATABASE("Database"),
        SSPI("SSPI", CLIENT_ID_BYTES),
        ATTACH_DB_FILE("AtchDBFile"),
        CHANGE_PASSWORD("ChangePassword"),
    }

    private class Slots(private val bySlot: Map<Field, Slot>, val sspiLong: Long) {
        operator fun get(field: Field): Slot = bySlot.getValue(field)
    }

    private fun read(payload: ByteArray): Login7Request {
        if (payload.size < FIXED_BYTES) {
            violation(ProtocolFailure.TRUNCATED, "LOGIN7 mit ${payload.size} Byte, mindestens $FIXED_BYTES")
        }
        val reader = ByteReader(payload)
        val declared = reader.u32Le()
        if (declared != payload.size.toLong()) {
            violation(ProtocolFailure.INVALID_LENGTH, "LOGIN7 deklariert $declared Byte, Nachricht hat ${payload.size}")
        }
        val header = readHeader(reader)
        val slots = readSlots(reader)
        val total = payload.size
        val fields = Login7Fields(
            hostName = text(reader, slots[Field.HOST_NAME], total),
            userName = text(reader, slots[Field.USER_NAME], total),
            appName = text(reader, slots[Field.APP_NAME], total),
            serverName = text(reader, slots[Field.SERVER_NAME], total),
            cltIntName = text(reader, slots[Field.CLT_INT_NAME], total),
            language = text(reader, slots[Field.LANGUAGE], total),
            database = text(reader, slots[Field.DATABASE], total),
            attachDbFile = text(reader, slots[Field.ATTACH_DB_FILE], total),
        )
        val features = if (header.optionFlags3 and Login7Request.FLAG_EXTENSION != 0) {
            readFeatures(reader, slots[Field.EXTENSION], total)
        } else {
            emptyList()
        }
        return Login7Request(
            header = header,
            fields = fields,
            password = secret(reader, slots[Field.PASSWORD], total),
            newPassword = secret(reader, slots[Field.CHANGE_PASSWORD], total),
            sspiLength = sspiLength(slots, total),
            featureIds = features,
        )
    }

    private fun readHeader(reader: ByteReader): Login7Header = Login7Header(
        tdsVersion = reader.i32Le(),
        packetSize = reader.i32Le(),
        clientProgVer = reader.i32Le(),
        clientPid = reader.i32Le(),
        connectionId = reader.i32Le(),
        optionFlags1 = reader.u8(),
        optionFlags2 = reader.u8(),
        typeFlags = reader.u8(),
        optionFlags3 = reader.u8(),
        clientTimeZone = reader.i32Le(),
        clientLcid = reader.i32Le(),
    )

    private fun readSlots(reader: ByteReader): Slots {
        val bySlot = Field.entries.associateWith { field ->
            reader.skip(field.skipBefore)
            Slot(field.label, reader.u16Le(), reader.u16Le())
        }
        return Slots(bySlot, reader.u32Le())
    }

    private fun locate(reader: ByteReader, slot: Slot, byteCount: Long, total: Int) {
        if (slot.offset < FIXED_BYTES || slot.offset + byteCount > total) {
            violation(
                ProtocolFailure.INVALID_OFFSET,
                "Feld ${slot.name}: Offset ${slot.offset}, $byteCount Byte, Nachricht $total Byte",
            )
        }
        reader.seek(slot.offset)
    }

    private fun text(reader: ByteReader, slot: Slot, total: Int): String {
        if (slot.count == 0) {
            return ""
        }
        locate(reader, slot, slot.count * 2L, total)
        return String(reader.utf16(slot.count))
    }

    private fun secret(reader: ByteReader, slot: Slot, total: Int): SecretChars {
        if (slot.count == 0) {
            return SecretChars(CharArray(0))
        }
        locate(reader, slot, slot.count * 2L, total)
        val plain = PasswordObfuscation.deobfuscate(reader.bytes(slot.count * 2))
        val chars = CharArray(slot.count) { index ->
            (plain[2 * index].toInt() and BYTE_MASK or ((plain[2 * index + 1].toInt() and BYTE_MASK) shl BYTE_BITS))
                .toChar()
        }
        plain.fill(0)
        return SecretChars(chars).also { chars.fill('\u0000') }
    }

    private fun sspiLength(slots: Slots, total: Int): Long {
        val sspi = slots[Field.SSPI]
        val length = if (sspi.count == SSPI_LONG_MARKER) slots.sspiLong else sspi.count.toLong()
        if (length > 0 && (sspi.offset < FIXED_BYTES || sspi.offset + length > total)) {
            violation(
                ProtocolFailure.INVALID_OFFSET,
                "SSPI-Block: Offset ${sspi.offset}, $length Byte, Nachricht $total Byte",
            )
        }
        return length
    }

    private fun readFeatures(reader: ByteReader, extension: Slot, total: Int): List<Int> {
        if (extension.count != EXTENSION_POINTER_BYTES) {
            violation(ProtocolFailure.INVALID_LENGTH, "FeatureExt-Zeiger mit ${extension.count} Byte statt 4")
        }
        locate(reader, extension, EXTENSION_POINTER_BYTES.toLong(), total)
        val tableOffset = reader.u32Le()
        if (tableOffset < FIXED_BYTES || tableOffset >= total) {
            violation(ProtocolFailure.INVALID_OFFSET, "FeatureExt-Tabelle bei $tableOffset, Nachricht $total Byte")
        }
        reader.seek(tableOffset.toInt())
        val ids = mutableListOf<Int>()
        var id = reader.u8()
        while (id != FEATURE_TERMINATOR) {
            val length = reader.u32Le()
            if (length > reader.remaining) {
                violation(ProtocolFailure.INVALID_LENGTH, "FeatureExt $id: $length Byte, ${reader.remaining} vorhanden")
            }
            reader.skip(length.toInt())
            ids += id
            id = reader.u8()
        }
        return ids
    }
}
