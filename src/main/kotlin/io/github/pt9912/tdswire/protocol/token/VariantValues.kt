package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ByteWriter

/**
 * TYPE_INFO und Wert von `sql_variant` (SSVARIANT, `0x62`). Belegt ist die Antwort eines echten SQL Servers auf die
 * Katalogabfrage von d-migrate (Spalten `seed_value` und `increment_value`, Mitschnitt `katalog-abfragen`, Zeile 64
 * und die Zeile mit den Werten, Einzelmessung je Instanz 2022 und 2025): TYPE_INFO `62` mit der größten Länge 8009
 * in 4 Byte (`49 1F 00 00`); ein Wert ist die Länge in 4 Byte (hier 6), die Kennung des Basistyps (`0x38`, `int`), die
 * Zahl der Eigenschaftsbytes (0) und der Wert (4 Byte, Little-Endian).
 *
 * Festlegung: Der Encoder schreibt nur den Basistyp `int`; jeder andere Basistyp und jeder andere Wert ist
 * `TYPE_MISMATCH` (`TDS_UNSUPPORTED_TYPE`). NULL als Wert in ROW ist die Länge 0 (Lesart von MS-TDS, nicht gemessen:
 * der Mitschnitt lässt NULL im NBCROW aus).
 */
internal object VariantValues {
    const val MAX_LENGTH = 8009
    private const val TYPE_CODE = 0x62
    private const val BASE_INT = 0x38
    private const val INT_BYTES = 4
    private const val HEADER_BYTES = 2

    fun writeTypeInfo(out: ByteWriter) {
        out.u8(TYPE_CODE)
        out.i32Le(MAX_LENGTH)
    }

    fun write(out: ByteWriter, value: TdsValue) {
        val number = (value as? TdsValue.VariantIntValue ?: ValueSupport.mismatch(value)).value
        out.i32Le(HEADER_BYTES + INT_BYTES)
        out.u8(BASE_INT)
        out.u8(0)
        out.i32Le(number)
    }

    fun writeNull(out: ByteWriter) = out.i32Le(0)
}
