package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * TYPE_INFO und Wert je Typ des Mindestkatalogs (`SPEC-003`), in einer RPC per Name "p" mit einem
 * Parameter ohne Namen. Die Wertbytes sind von Hand nach MS-TDS gerechnet, ohne dass der Text der
 * Spezifikation im Repo liegt; belegt sind sie damit nicht gegen eine Spezifikationsstelle. Die
 * Länge der Zeittypen übernimmt der Parser von `TypeInfoWriter.timeBytes` (Scale 0 bis 2: 3 Byte,
 * 3 bis 4: 4 Byte, 5 bis 7: 5 Byte) und der Test prüft sie dagegen (Selbstkonsistenz). Der Zuschlag
 * von 2 Byte für den Offset bei `DATETIMEOFFSETN` und die Dezimalgrößen sind Festlegungen dieses
 * Parsers.
 */
class RpcTypesTest {
    private val latin1 = Collation(info = 0x00D00409, sortId = 0x34)

    private fun parameter(typeAndValue: String): RpcParameter {
        val request = assertIs<ProtocolResult.Ok<RpcRequest>>(RpcParser.parse(RpcVectors.byName(typeAndValue))).value
        return assertIs<RpcCall.ByName>(request.call).parameters.single()
    }

    private fun integer(typeAndValue: String): Long =
        assertIs<RpcValue.IntegerValue>(parameter(typeAndValue).value).value

    private fun raw(typeAndValue: String): ByteArray = assertIs<RpcValue.RawValue>(parameter(typeAndValue).value).bytes

    /**
     * Orakel: `INTN` mit 1 Byte ist vorzeichenlos (`FF` = 255), mit 2, 4 und 8 Byte
     * vorzeichenbehaftet (`FF FF` = −1; `7FFFFFFF` = 2147483647; `0x8000000000000000` = Long.MIN).
     *
     * @spec SPEC-003
     */
    @Test
    fun `INTN liest 1 Byte vorzeichenlos und 2 4 8 Byte vorzeichenbehaftet`() {
        assertEquals(255L, integer("26 01 01 FF"))
        assertEquals(-1L, integer("26 02 02 FF FF"))
        assertEquals(2147483647L, integer("26 04 04 FF FF FF 7F"))
        assertEquals(-2147483648L, integer("26 04 04 00 00 00 80"))
        assertEquals(Long.MIN_VALUE, integer("26 08 08 00 00 00 00 00 00 00 80"))
        assertEquals(-1L, integer("26 08 08 FF FF FF FF FF FF FF FF"))
        assertEquals(RpcType(RpcTypeKind.INTN, length = 2), parameter("26 02 02 01 00").type)
    }

    /**
     * Orakel: Länge 0 ist der NULL-Sentinel der Typen mit Längenbyte; `BITN` liest `00` als falsch,
     * `01` als wahr.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Laenge 0 ist NULL und BITN liest wahr und falsch`() {
        listOf("26 04 00", "68 01 00", "6D 08 00", "6E 04 00", "6F 08 00", "24 10 00", "28 00", "29 03 00")
            .forEach { assertEquals(RpcValue.NullValue, parameter(it).value, it) }
        assertEquals(false, assertIs<RpcValue.BitValue>(parameter("68 01 01 00").value).value)
        assertEquals(true, assertIs<RpcValue.BitValue>(parameter("68 01 01 01").value).value)
    }

    /**
     * Orakel: `FLTN` 8 mit 1,5 (`0x3FF8000000000000`, Little-Endian `00 00 00 00 00 00 F8 3F`),
     * `MONEYN`, `DATETIMN` und `GUID` liefern ihre Wertbytes unverändert.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Fliesskomma, Geld, Datetime und GUID liefern Wertbytes`() {
        assertContentEquals(hex("00 00 00 00 00 00 F8 3F"), raw("6D 08 08 00 00 00 00 00 00 F8 3F"))
        assertContentEquals(hex("10 27 00 00"), raw("6E 04 04 10 27 00 00"))
        assertContentEquals(hex("01 00 00 00 02 00 00 00"), raw("6F 08 08 01 00 00 00 02 00 00 00"))
        val guid = "01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10"
        assertContentEquals(hex(guid), raw("24 10 10 $guid"))
        assertEquals(RpcType(RpcTypeKind.FLTN, length = 8), parameter("6D 08 08 00 00 00 00 00 00 F8 3F").type)
    }

    /**
     * Orakel: `NUMERICN` mit Größe 17 (Vorzeichenbyte plus 16 Byte, wie `SPEC-003` für den
     * Referenzclient beobachtet), Precision 18, Scale 2, Wert 12,34 = 1234 = `D2 04`; `DECIMALN` mit
     * Größe 5, Precision 9, Scale 0, Wert 7.
     *
     * @spec SPEC-003
     */
    @Test
    fun `NUMERICN und DECIMALN tragen Precision, Scale und Vorzeichen mit Betrag`() {
        val zeros = List(14) { "00" }.joinToString(" ")
        val numeric = parameter("6C 11 12 02  11 01 D2 04 $zeros")
        assertEquals(RpcType(RpcTypeKind.NUMERICN, length = 17, precision = 18, scale = 2), numeric.type)
        assertContentEquals(hex("01 D2 04 $zeros"), assertIs<RpcValue.RawValue>(numeric.value).bytes)
        val decimal = parameter("6A 05 09 00  05 01 07 00 00 00")
        assertEquals(RpcType(RpcTypeKind.DECIMALN, length = 5, precision = 9, scale = 0), decimal.type)
        assertContentEquals(hex("01 07 00 00 00"), assertIs<RpcValue.RawValue>(decimal.value).bytes)
    }

    /**
     * Orakel: `DATEN` 3 Byte; `TIMEN` je Scale 3, 4 und 5 Byte (Scale 2, 3, 5); `DATETIME2N` mit
     * Scale 7 acht Byte (3 + 5); `DATETIMEOFFSETN` mit Scale 7 zehn Byte (3 + 5 + 2).
     *
     * @spec SPEC-003
     */
    @Test
    fun `Datums- und Zeittypen lesen die Laenge aus dem Scale`() {
        assertContentEquals(hex("01 00 00"), raw("28 03 01 00 00"))
        assertContentEquals(hex("11 22 33"), raw("29 02 03 11 22 33"))
        assertContentEquals(hex("11 22 33 44"), raw("29 03 04 11 22 33 44"))
        assertContentEquals(hex("11 22 33 44 55"), raw("29 05 05 11 22 33 44 55"))
        assertContentEquals(hex("01 02 03 04 05 06 07 08"), raw("2A 07 08 01 02 03 04 05 06 07 08"))
        val offset = "01 02 03 04 05 06 07 08 09 0A"
        assertContentEquals(hex(offset), raw("2B 07 0A $offset"))
        assertEquals(RpcType(RpcTypeKind.DATETIME2N, scale = 7), parameter("2A 07 00").type)
    }

    /**
     * Orakel: `NVARCHAR` und `NCHAR` liefern Text aus UTF-16LE (hier "ab€" und ein unpaariges
     * Surrogat unverändert); `BIGVARCHAR`/`BIGCHAR` und die Binärtypen liefern Bytes, ohne
     * Kodierung gedeutet; `FF FF` ist NULL, Länge 0 der leere Wert.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Zeichen- und Binaertypen lesen Text, Bytes und NULL`() {
        val nvarchar = parameter("E7 08 00  ${RpcVectors.COLLATION}  06 00  61 00 62 00 AC 20")
        assertEquals("ab€", assertIs<RpcValue.TextValue>(nvarchar.value).value)
        assertEquals(RpcType(RpcTypeKind.NVARCHAR, length = 8, collation = latin1), nvarchar.type)
        val surrogate = parameter("EF 04 00 ${RpcVectors.COLLATION} 02 00 3D D8")
        assertEquals("\uD83D", assertIs<RpcValue.TextValue>(surrogate.value).value)
        assertEquals(RpcTypeKind.NCHAR, parameter("EF 04 00 ${RpcVectors.COLLATION} 00 00").type.kind)
        assertContentEquals(hex("61 62 63"), raw("A7 0A 00 ${RpcVectors.COLLATION} 03 00 61 62 63"))
        assertContentEquals(hex("61"), raw("AF 05 00 ${RpcVectors.COLLATION} 01 00 61"))
        assertContentEquals(hex("DE AD"), raw("A5 04 00 02 00 DE AD"))
        assertContentEquals(hex("DE"), raw("AD 04 00 01 00 DE"))
        assertEquals(RpcValue.NullValue, parameter("A5 04 00 FF FF").value)
        assertEquals(RpcValue.NullValue, parameter("E7 08 00 ${RpcVectors.COLLATION} FF FF").value)
        assertEquals(0, raw("A5 04 00 00 00").size)
    }
}
