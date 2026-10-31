package io.github.pt9912.tdswire.testsupport

import java.io.ByteArrayOutputStream

/**
 * Baut LOGIN7-Nachrichten für Testfälle nach MS-TDS 2.2.6.4. Die Verschleierung ist hier unabhängig
 * vom Produktionscode ausgeschrieben (Nibble-Tausch, dann XOR `0xA5`).
 */
class Login7Builder {
    var optionFlags1 = 0xE0
    var optionFlags2 = 0x03
    var typeFlags = 0
    var optionFlags3 = 0
    var hostName = "H"
    var userName = "sa"
    var password: CharArray = "a".toCharArray()
    var passwordWire: ByteArray? = null
    var appName = "A"
    var serverName = "S"
    var cltIntName = "C"
    var language = ""
    var database = "d"
    var changePassword: CharArray = CharArray(0)
    var sspi: ByteArray = ByteArray(0)
    var sspiShortMarker = false
    var features: List<Pair<Int, ByteArray>>? = null

    /** Die verlangte Packet Size im Header (Voreinstellung 8000, der Wert der Handvektoren). */
    var packetSize = 8000

    private class Slot(val offset: Int, val count: Int)

    fun build(): ByteArray {
        val data = ByteArrayOutputStream()
        val base = FIXED
        fun place(bytes: ByteArray, count: Int): Slot {
            val slot = Slot(base + data.size(), count)
            data.write(bytes)
            return slot
        }
        fun text(value: String) = place(utf16(value.toCharArray()), value.length)
        val host = text(hostName)
        val user = text(userName)
        val wire = passwordWire ?: obfuscate(utf16(password))
        val pass = place(wire, wire.size / 2)
        val app = text(appName)
        val server = text(serverName)
        val clt = text(cltIntName)
        val lang = text(language)
        val db = text(database)
        val sspiSlot = place(sspi, sspi.size)
        val change = place(obfuscate(utf16(changePassword)), changePassword.size)
        val extension = features?.let { placeFeatures(data, base, it) } ?: Slot(0, 0)
        val out = ByteArrayOutputStream()
        val total = base + data.size()
        out.write(le(total.toLong(), 4))
        out.write(hex("04 00 00 74"))
        out.write(le(packetSize.toLong(), 4))
        out.write(hex("00 00 00 07  34 12 00 00  00 00 00 00"))
        out.write(byteArrayOf(optionFlags1.toByte(), optionFlags2.toByte(), typeFlags.toByte(), optionFlags3.toByte()))
        out.write(hex("00 00 00 00  09 04 00 00"))
        listOf(host, user, pass, app, server, extension, clt, lang, db).forEach { out.write(pair(it)) }
        out.write(ByteArray(CLIENT_ID))
        val sspiCount = if (sspiShortMarker) 0xFFFF else sspi.size
        out.write(pair(Slot(sspiSlot.offset, sspiCount)))
        out.write(pair(Slot(sspiSlot.offset + sspi.size, 0)))
        out.write(pair(change))
        out.write(le(if (sspiShortMarker) sspi.size.toLong() else 0L, 4))
        out.write(data.toByteArray())
        return out.toByteArray()
    }

    private fun placeFeatures(data: ByteArrayOutputStream, base: Int, list: List<Pair<Int, ByteArray>>): Slot {
        val pointerAt = base + data.size()
        val tableAt = pointerAt + 4
        data.write(le(tableAt.toLong(), 4))
        list.forEach { (id, bytes) ->
            data.write(id)
            data.write(le(bytes.size.toLong(), 4))
            data.write(bytes)
        }
        data.write(FEATURE_END)
        return Slot(pointerAt, 4)
    }

    private fun pair(slot: Slot): ByteArray = le(slot.offset.toLong(), 2) + le(slot.count.toLong(), 2)

    companion object {
        const val FIXED = 94
        private const val CLIENT_ID = 6
        private const val FEATURE_END = 0xFF

        fun le(value: Long, count: Int): ByteArray =
            ByteArray(count) { index -> ((value ushr (8 * index)) and 0xFF).toByte() }

        fun utf16(chars: CharArray): ByteArray = ByteArray(chars.size * 2) { index ->
            val unit = chars[index / 2].code
            (if (index % 2 == 0) unit and 0xFF else unit ushr 8).toByte()
        }

        fun obfuscate(plain: ByteArray): ByteArray = ByteArray(plain.size) { index ->
            val b = plain[index].toInt() and 0xFF
            ((((b shl 4) or (b ushr 4)) and 0xFF) xor 0xA5).toByte()
        }
    }
}
