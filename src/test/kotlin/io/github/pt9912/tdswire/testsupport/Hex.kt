package io.github.pt9912.tdswire.testsupport

import java.util.Locale

/** Bytes aus einer Hex-Schreibweise mit Leerzeichen und Zeilenumbrüchen als Trenner. */
fun hex(text: String): ByteArray {
    val digits = text.filter { !it.isWhitespace() }
    require(digits.length % 2 == 0) { "ungerade Zahl Hex-Ziffern" }
    return ByteArray(digits.length / 2) { index ->
        digits.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

/** Die Bytes in der Schreibweise von [hex], für lesbare Fehlermeldungen. */
fun ByteArray.toHex(): String = joinToString(" ") { String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF) }

/** Zerlegt [bytes] in Stücke der Längen aus [sizes]; der Rest bleibt ein letztes Stück. */
fun chunked(bytes: ByteArray, sizes: Sequence<Int>): List<ByteArray> {
    val result = mutableListOf<ByteArray>()
    var position = 0
    val iterator = sizes.iterator()
    while (position < bytes.size) {
        val size = minOf(iterator.next(), bytes.size - position)
        result += bytes.copyOfRange(position, position + size)
        position += size
    }
    return result
}
