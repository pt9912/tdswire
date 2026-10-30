package io.github.pt9912.tdswire.testsupport

/**
 * Handvektoren für RPC-Nachrichten, von Hand aus MS-TDS gerechnet (Aufbau: ALL_HEADERS, Prozedur,
 * Optionsflags, Parameter). Die Teile sind Hex-Texte für [hex].
 */
object RpcVectors {
    /** ALL_HEADERS: Gesamtlänge 22, ein Header von 18 Byte, Typ 2 (Transaction Descriptor), 8 + 4 Byte. */
    const val HEADERS = "16 00 00 00  12 00 00 00  02 00  00 00 00 00 00 00 00 00  01 00 00 00"

    /** Kollation `SQL_Latin1_General_CP1_CI_AS`: Info `0x00D00409` Little-Endian, Sortierung `0x34`. */
    const val COLLATION = "09 04 D0 00 34"

    /** TYPE_INFO und Wert eines `NVARCHAR` mit dem Text "select @a" (9 Zeichen, 18 Byte = `0x12`). */
    const val STATEMENT = "E7 12 00  $COLLATION  12 00  73 00 65 00 6C 00 65 00 63 00 74 00 20 00 40 00 61 00"

    /** TYPE_INFO und Wert eines `NVARCHAR` mit dem Text "@a int" (6 Zeichen, 12 Byte = `0x0C`). */
    const val DECLARATION = "E7 0C 00  $COLLATION  0C 00  40 00 61 00 20 00 69 00 6E 00 74 00"

    /** Parameter `@a`, `INTN` 4 Byte, Wert 42, kein OUTPUT. */
    const val PARAM_A_42 = "02 40 00 61 00  00  26 04  04  2A 00 00 00"

    /** Parameter `@o`, `INTN` 4 Byte, OUTPUT (Status `0x01`), NULL. */
    const val PARAM_O_OUTPUT_NULL = "02 40 00 6F 00  01  26 04  00"

    /** `sp_executesql` (ProcID 10), `OptionFlags=0x0002` wie in der Messmatrix von `ADR-0005`. */
    const val EXECUTE_SQL =
        "$HEADERS  FF FF 0A 00  02 00  00 00 $STATEMENT  00 00 $DECLARATION  $PARAM_A_42  $PARAM_O_OUTPUT_NULL"

    /** `sp_prepexec` (ProcID 13): Handle −1 als OUTPUT, Deklaration, Text, Parameter `@a` = 7. */
    const val PREP_EXEC =
        "$HEADERS  FF FF 0D 00  00 00  00 01 26 04 04 FF FF FF FF  00 00 $DECLARATION  00 00 $STATEMENT  " +
            "02 40 00 61 00  00  26 04  04  07 00 00 00"

    /** `sp_execute` (ProcID 12): Handle 5, `@a` = BITN wahr, `@b` = `NVARCHAR(3)` NULL. */
    const val EXECUTE =
        "$HEADERS  FF FF 0C 00  00 00  00 00 26 04 04 05 00 00 00  02 40 00 61 00  00  68 01  01  01  " +
            "02 40 00 62 00  00  E7 06 00  $COLLATION  FF FF"

    /** Prozedurname "[dbo].[p]" (9 Zeichen = `0x09`, 18 Byte UTF-16LE), `OptionFlags=0x0000`. */
    const val NAME_HEAD =
        "$HEADERS  09 00  5B 00 64 00 62 00 6F 00 5D 00 2E 00 5B 00 70 00 5D 00  00 00"

    /** Zwei Parameter nach `NAME_HEAD`: `@x` leerer String (Länge 0, nicht NULL) und `@y` NULL. */
    const val NAME_PARAMS =
        "02 40 00 78 00  00  E7 08 00  $COLLATION  00 00  02 40 00 79 00  00  E7 08 00  $COLLATION  FF FF"

    /** Ein Byte als zwei Hex-Ziffern. */
    fun hexByte(value: Int): String = byteArrayOf(value.toByte()).toHex()

    /** Eine RPC per Name "p" (1 Zeichen) mit einem Parameter ohne Namen aus [typeAndValue]. */
    fun byName(typeAndValue: String): ByteArray = hex("$HEADERS  01 00 70 00  00 00  00 00 $typeAndValue")
}
