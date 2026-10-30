package io.github.pt9912.tdswire.protocol.wire

/**
 * Liest den ALL_HEADERS-Block am Anfang einer SQL_BATCH- oder RPC-Nachricht (`SPEC-028`): die
 * Gesamtlänge, dann Header aus Länge, Kennung und Inhalt. Die Summe der Header-Längen ist genau die
 * Gesamtlänge des Blocks; der Inhalt der Header wird übersprungen, nicht gedeutet.
 */
internal object AllHeaders {
    private const val TOTAL_LENGTH_BYTES = 4
    private const val HEADER_FIXED_BYTES = 6

    /** Liest den Block ab der aktuellen Position und liefert die Kennungen der Header in Reihenfolge. */
    fun read(reader: ByteReader, messageSize: Int): List<Int> {
        val start = reader.position
        val total = reader.u32Le()
        if (total < TOTAL_LENGTH_BYTES || total > messageSize - start) {
            violation(ProtocolFailure.INVALID_HEADER_BLOCK, "ALL_HEADERS $total Byte, Nachricht $messageSize Byte")
        }
        val types = mutableListOf<Int>()
        while (reader.position < start + total) {
            val length = reader.u32Le()
            if (length < HEADER_FIXED_BYTES || reader.position - start - TOTAL_LENGTH_BYTES + length > total) {
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
