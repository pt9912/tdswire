package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ProtocolErrorCategory
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TokenEncoderTest {
    private fun encoded(vararg tokens: Token): String {
        val encoder = TokenEncoder()
        tokens.forEach { assertIs<ProtocolResult.Ok<Unit>>(encoder.write(it)) }
        return encoder.toByteArray().toHex()
    }

    private fun failureOf(vararg tokens: Token): ProtocolFailure {
        val encoder = TokenEncoder()
        tokens.dropLast(1).forEach { assertIs<ProtocolResult.Ok<Unit>>(encoder.write(it)) }
        return assertIs<ProtocolResult.Failed>(encoder.write(tokens.last())).error.failure
    }

    private fun meta(name: String, type: TdsType, nullable: Boolean = false) =
        ColMetadataToken(listOf(ColumnDef(name, type, nullable)))

    private fun done(kind: DoneKind, status: Int) = DoneToken(kind, status, 0, 0)

    private val nvarchar10 = TdsType.NVarcharType(10, Collation.LATIN1_GENERAL_CI_AS)

    /**
     * Orakel: DONE nach MS-TDS 2.2.7.6 (7.2 und höher): Kennung `FD`, Status (`DONE_COUNT` = `0x0010`)
     * und CurCmd (193 = SELECT) je 2 Byte, Zeilenzahl 8 Byte, alles Little-Endian.
     *
     * @spec SPEC-028
     */
    @Test
    fun `DONE mit Zeilenzahl`() {
        assertEquals(
            "FD 10 00 C1 00 01 00 00 00 00 00 00 00",
            encoded(DoneToken(DoneKind.DONE, DoneStatus.COUNT, 193, 1)),
        )
    }

    /**
     * Orakel: DONEPROC (`FE`) und DONEINPROC (`FF`) mit `DONE_MORE`; der Fehlerstatus aus `DONE_ERROR`
     * und `DONE_SRVERROR` ist `0x0102`; `DONE_ATTN` ist `0x0020` und wird gesetzt, wenn der Aufrufer es
     * verlangt (`ADR-0007` entscheidet, wann).
     *
     * @spec SPEC-028
     */
    @Test
    fun `DONE-Familie mit Statusbits`() {
        assertEquals("FE 01 00 00 00 00 00 00 00 00 00 00 00", encoded(done(DoneKind.DONE_PROC, DoneStatus.MORE)))
        assertEquals("FF 01 00 00 00 00 00 00 00 00 00 00 00", encoded(done(DoneKind.DONE_IN_PROC, DoneStatus.MORE)))
        val failed = DoneStatus.ERROR or DoneStatus.SERVER_ERROR
        assertEquals("FD 02 01 00 00 00 00 00 00 00 00 00 00", encoded(done(DoneKind.DONE, failed)))
        assertEquals("FD 20 00 00 00 00 00 00 00 00 00 00 00", encoded(done(DoneKind.DONE, DoneStatus.ATTENTION)))
        assertEquals(
            listOf(0x0000, 0x0001, 0x0002, 0x0004, 0x0010, 0x0020, 0x0080, 0x0100),
            listOf(
                DoneStatus.FINAL,
                DoneStatus.MORE,
                DoneStatus.ERROR,
                DoneStatus.IN_TRANSACTION,
                DoneStatus.COUNT,
                DoneStatus.ATTENTION,
                DoneStatus.RPC_IN_BATCH,
                DoneStatus.SERVER_ERROR,
            ),
        )
    }

    /**
     * Grenze: Zeilenzahl über 32 Bit (8 Byte) und der größte Status.
     *
     * @spec SPEC-028
     */
    @Test
    fun `DONE traegt 64-Bit-Zeilenzahl`() {
        assertEquals(
            "FD FF FF 00 00 00 00 00 00 01 00 00 00",
            encoded(DoneToken(DoneKind.DONE, 0xFFFF, 0, 0x1_0000_0000L)),
        )
    }

    /**
     * Orakel: RETURNSTATUS (`79`) trägt einen Int32 Little-Endian ohne Längenfeld.
     *
     * @spec SPEC-030
     */
    @Test
    fun `RETURNSTATUS`() {
        assertEquals("79 00 00 00 00", encoded(ReturnStatusToken(0)))
        assertEquals("79 FF FF FF FF", encoded(ReturnStatusToken(-1)))
        assertEquals("79 02 01 00 00", encoded(ReturnStatusToken(258)))
    }

    /**
     * Orakel: ERROR (`AA`) mit Länge 40 (`0x28`): Nummer 18456 = `18 48 00 00`, State 1, Class 14
     * (`0E`), Text "Login failed." (13 Zeichen, `0D 00`), Servername und Prozedurname leer, Zeile 1.
     *
     * @spec SPEC-012
     */
    @Test
    fun `ERROR`() {
        val message = ServerMessage(number = 18456, state = 1, severity = 14, text = "Login failed.", lineNumber = 1)
        assertEquals(
            "AA 28 00 18 48 00 00 01 0E 0D 00 4C 00 6F 00 67 00 69 00 6E 00 20 00 66 00 61 00 69 00 6C 00 " +
                "65 00 64 00 2E 00 00 00 01 00 00 00",
            encoded(ErrorToken(message)),
        )
    }

    /**
     * Orakel: INFO (`AB`) hat dieselbe Form wie ERROR; Länge 22 (`0x16`) mit Servername "S",
     * Prozedurname "P", Nummer 5701 = `45 16 00 00`, State 2, Class 0, Text "OK", Zeile 3.
     *
     * @spec SPEC-028
     */
    @Test
    fun `INFO mit Server und Prozedur`() {
        val message = ServerMessage(5701, 2, 0, "OK", serverName = "S", procName = "P", lineNumber = 3)
        assertEquals(
            "AB 16 00 45 16 00 00 02 00 02 00 4F 00 4B 00 01 53 00 01 50 00 03 00 00 00",
            encoded(InfoToken(message)),
        )
    }

    /**
     * Orakel: ENVCHANGE Typ 1 (Datenbank): neu "master", alt leer; Länge 15 (`0x0F`).
     *
     * @spec SPEC-028
     */
    @Test
    fun `ENVCHANGE Datenbank`() {
        assertEquals(
            "E3 0F 00 01 06 6D 00 61 00 73 00 74 00 65 00 72 00 00",
            encoded(EnvChange.Database("master", "")),
        )
    }

    /**
     * Orakel: ENVCHANGE Typ 4 (Packet Size): beide Werte als Dezimaltext, "8000" und "4096"; Länge 19.
     *
     * @spec SPEC-028
     */
    @Test
    fun `ENVCHANGE Packet Size`() {
        assertEquals(
            "E3 13 00 04 04 38 00 30 00 30 00 30 00 04 34 00 30 00 39 00 36 00",
            encoded(EnvChange.PacketSize(8000, 4096)),
        )
    }

    /**
     * Orakel: ENVCHANGE Typ 7 (SQL-Kollation): neu 5 Byte `09 04 D0 00 34` (LCID 0x0409, Flags
     * `0x00D0`, SortId 52), alt leer (Längenbyte 0); Länge 8. Mit alter Kollation folgen 5 weitere Byte.
     *
     * @spec SPEC-003
     */
    @Test
    fun `ENVCHANGE Kollation`() {
        val latin = Collation.LATIN1_GENERAL_CI_AS
        assertEquals("E3 08 00 07 05 09 04 D0 00 34 00", encoded(EnvChange.SqlCollation(latin, null)))
        assertEquals(
            "E3 0D 00 07 05 09 04 D0 00 34 05 09 04 D0 00 34",
            encoded(EnvChange.SqlCollation(latin, latin)),
        )
    }

    /**
     * Orakel: LOGINACK (`AD`), Länge 24: Interface 1, TDS-Version `74 00 00 04` (7.4, Big-Endian im
     * Token), ProgName "tdswire" (7 Zeichen), ProgVersion `01 00 00 00`. Die Byte-Reihenfolge der
     * Version ist die Lesart von MS-TDS, nicht am Referenzclient gemessen.
     *
     * @spec SPEC-028
     */
    @Test
    fun `LOGINACK`() {
        assertEquals(
            "AD 18 00 01 74 00 00 04 07 74 00 64 00 73 00 77 00 69 00 72 00 65 00 01 00 00 00",
            encoded(LoginAck(1, 0x74000004, "tdswire", 0x01000000)),
        )
    }

    /**
     * Orakel: COLMETADATA (`81`): Spaltenzahl, je Spalte UserType (4), Flags (2), TYPE_INFO, Name als
     * B_VARCHAR. INT4 "id" ohne NULL hat die TYPE_INFO `38`; die leere Liste schreibt 0 Spalten.
     *
     * @spec SPEC-003
     */
    @Test
    fun `COLMETADATA fester Typ und leere Liste`() {
        assertEquals("81 01 00 00 00 00 00 00 00 38 02 69 00 64 00", encoded(meta("id", TdsType.Int4Type)))
        assertEquals("81 00 00", encoded(ColMetadataToken(emptyList())))
        assertEquals("81 01 00 00 00 00 00 00 00 7F 01 6C 00", encoded(meta("l", TdsType.Int8Type)))
        assertEquals("81 01 00 00 00 00 00 00 00 32 01 62 00", encoded(meta("b", TdsType.BitType)))
    }

    /**
     * Orakel: nullbare Typen tragen `fNullable` (`0x0001`) in den Flags: INTN(4) `26 04`, BITN `68 01`,
     * NVARCHAR(10) `E7 14 00` und Kollation. `extraFlags` und `userType` werden übernommen.
     *
     * @spec SPEC-003
     */
    @Test
    fun `COLMETADATA nullbare Typen und Flags`() {
        assertEquals(
            "81 01 00 00 00 00 00 01 00 26 04 01 6E 00",
            encoded(ColMetadataToken(listOf(ColumnDef("n", TdsType.IntNType(4), nullable = true)))),
        )
        assertEquals(
            "81 01 00 00 00 00 00 01 00 68 01 01 62 00",
            encoded(ColMetadataToken(listOf(ColumnDef("b", TdsType.BitNType, nullable = true)))),
        )
        assertEquals(
            "81 01 00 00 00 00 00 01 00 E7 14 00 09 04 D0 00 34 01 73 00",
            encoded(ColMetadataToken(listOf(ColumnDef("s", nvarchar10, nullable = true)))),
        )
        val tagged = ColumnDef("x", TdsType.Int4Type, userType = 256, extraFlags = 0x0008)
        assertEquals("81 01 00 00 01 00 00 08 00 38 01 78 00", encoded(ColMetadataToken(listOf(tagged))))
    }

    /**
     * Orakel: DECIMALN `6A` / NUMERICN `6C` tragen Längenbyte, Precision und Scale; das Längenbyte folgt
     * der Precision (1 bis 9: 5, 10 bis 19: 9, 20 bis 28: 13, 29 bis 38: 17).
     *
     * @spec SPEC-003
     */
    @Test
    fun `COLMETADATA Dezimalzahlen`() {
        fun meta(type: TdsType) = encoded(ColMetadataToken(listOf(ColumnDef("d", type))))
        assertEquals("81 01 00 00 00 00 00 00 00 6A 09 0A 02 01 64 00", meta(TdsType.DecimalType(10, 2)))
        assertEquals("81 01 00 00 00 00 00 00 00 6C 05 05 00 01 64 00", meta(TdsType.DecimalType(5, 0, numeric = true)))
        assertEquals("6A 05 09", meta(TdsType.DecimalType(9, 0)).substring(27, 35))
        assertEquals("6A 09 13", meta(TdsType.DecimalType(19, 0)).substring(27, 35))
        assertEquals("6A 0D 1C", meta(TdsType.DecimalType(28, 0)).substring(27, 35))
        assertEquals("6A 11 26 00", meta(TdsType.DecimalType(38, 0)).substring(27, 38))
    }

    /**
     * Orakel: DATEN `28`, TIMEN `29` mit Scale, DATETIME2N `2A` mit Scale.
     *
     * @spec SPEC-003
     */
    @Test
    fun `COLMETADATA Datum und Zeit`() {
        fun meta(type: TdsType) = encoded(ColMetadataToken(listOf(ColumnDef("x", type)))).substring(27)
        assertEquals("28 01 78 00", meta(TdsType.DateType))
        assertEquals("29 03 01 78 00", meta(TdsType.TimeType(3)))
        assertEquals("2A 07 01 78 00", meta(TdsType.DateTime2Type(7)))
    }

    private fun rowOf(columns: List<ColumnDef>, vararg values: TdsValue): String {
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(columns)))
        val metaBytes = encoder.toByteArray().size
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(RowToken(values.toList())))
        return encoder.toByteArray().toHex().substring(metaBytes * 3)
    }

    private fun single(type: TdsType, value: TdsValue, nullable: Boolean = true): String =
        rowOf(listOf(ColumnDef("c", type, nullable)), value)

    /**
     * Orakel: ROW (`D1`) mit festen Typen ohne Längenbyte: INT4 42, INT4 Minimum und Maximum, INT8
     * Minimum, BIT.
     *
     * @spec SPEC-003
     */
    @Test
    fun `ROW feste Typen`() {
        assertEquals("D1 2A 00 00 00", single(TdsType.Int4Type, TdsValue.IntegerValue(42), nullable = false))
        assertEquals("D1 00 00 00 80", single(TdsType.Int4Type, TdsValue.IntegerValue(Int.MIN_VALUE.toLong()), false))
        assertEquals("D1 FF FF FF 7F", single(TdsType.Int4Type, TdsValue.IntegerValue(Int.MAX_VALUE.toLong()), false))
        val min = TdsValue.IntegerValue(Long.MIN_VALUE)
        assertEquals("D1 00 00 00 00 00 00 00 80", single(TdsType.Int8Type, min, nullable = false))
        assertEquals("D1 01", single(TdsType.BitType, TdsValue.BitValue(true), false))
        assertEquals("D1 00", single(TdsType.BitType, TdsValue.BitValue(false), false))
    }

    /**
     * Orakel: nullbare Ganzzahl mit Längenbyte: INTN(1) 255 (tinyint, ohne Vorzeichen), INTN(2) -2,
     * INTN(4) -1, INTN(8) 5; NULL ist das Längenbyte 0; BITN mit Längenbyte 1.
     *
     * @spec SPEC-003
     */
    @Test
    fun `ROW nullbare Ganzzahlen und Bit`() {
        assertEquals("D1 01 FF", single(TdsType.IntNType(1), TdsValue.IntegerValue(255)))
        assertEquals("D1 02 FE FF", single(TdsType.IntNType(2), TdsValue.IntegerValue(-2)))
        assertEquals("D1 04 FF FF FF FF", single(TdsType.IntNType(4), TdsValue.IntegerValue(-1)))
        assertEquals("D1 08 05 00 00 00 00 00 00 00", single(TdsType.IntNType(8), TdsValue.IntegerValue(5)))
        assertEquals("D1 00", single(TdsType.IntNType(4), TdsValue.NullValue))
        assertEquals("D1 01 01", single(TdsType.BitNType, TdsValue.BitValue(true)))
        assertEquals("D1 00", single(TdsType.BitNType, TdsValue.NullValue))
    }

    /**
     * Orakel: NVARCHAR mit 2-Byte-Länge in Byte: "Hi" `04 00 48 00 69 00`, leerer Text `00 00` (nicht
     * NULL), NULL `FF FF`, "€" `02 00 AC 20`; ein Text von 10 Zeichen füllt die Spalte.
     *
     * @spec SPEC-003
     */
    @Test
    fun `ROW Text`() {
        assertEquals("D1 04 00 48 00 69 00", single(nvarchar10, TdsValue.TextValue("Hi")))
        assertEquals("D1 00 00", single(nvarchar10, TdsValue.TextValue("")))
        assertEquals("D1 FF FF", single(nvarchar10, TdsValue.NullValue))
        assertEquals("D1 02 00 AC 20", single(nvarchar10, TdsValue.TextValue("€")))
        assertEquals(1 + 2 + 20, single(nvarchar10, TdsValue.TextValue("0123456789")).split(" ").size)
    }

    /**
     * Orakel: Dezimalzahl: Längenbyte 9 (Precision 10), Vorzeichen (1 positiv, 0 negativ), Betrag ohne
     * Komma als Little-Endian (12,34 = 1234 = `D2 04`); eine Zahl mit weniger Nachkommastellen wird auf
     * den Scale gebracht (12,3 = 1230 = `CE 04`); Precision 38 braucht 17 Byte.
     *
     * @spec SPEC-003
     */
    @Test
    fun `ROW Dezimalzahlen`() {
        val type = TdsType.DecimalType(10, 2)
        fun decimal(text: String) = TdsValue.DecimalValue(java.math.BigDecimal(text))
        assertEquals("D1 09 01 D2 04 00 00 00 00 00 00", single(type, decimal("12.34")))
        assertEquals("D1 09 00 D2 04 00 00 00 00 00 00", single(type, decimal("-12.34")))
        assertEquals("D1 09 01 CE 04 00 00 00 00 00 00", single(type, decimal("12.3")))
        assertEquals("D1 09 01 00 00 00 00 00 00 00 00", single(type, decimal("0")))
        assertEquals("D1 00", single(type, TdsValue.NullValue))
        val max = "99999999999999999999999999999999999999"
        assertEquals(
            "D1 11 01 FF FF FF FF 3F 22 8A 09 7A C4 86 5A A8 4C 3B 4B",
            single(TdsType.DecimalType(38, 0), decimal(max)),
        )
    }

    /**
     * Orakel: DATE als Tage seit 0001-01-01 in 3 Byte (2024-01-01 = 738885 = `45 46 0B`, 0001-01-01 =
     * 0, 9999-12-31 = 3652058 = `DA B9 37`).
     *
     * @spec SPEC-003
     */
    @Test
    fun `ROW Datum`() {
        fun date(y: Int, m: Int, d: Int) = TdsValue.DateValue(java.time.LocalDate.of(y, m, d))
        assertEquals("D1 03 45 46 0B", single(TdsType.DateType, date(2024, 1, 1)))
        assertEquals("D1 03 00 00 00", single(TdsType.DateType, date(1, 1, 1)))
        assertEquals("D1 03 DA B9 37", single(TdsType.DateType, date(9999, 12, 31)))
        assertEquals("D1 00", single(TdsType.DateType, TdsValue.NullValue))
    }

    /**
     * Orakel: TIME in Ticks der Einheit `10^-scale` s seit Mitternacht: Scale 7 und 00:00:01 =
     * 10 000 000 Ticks (5 Byte `80 96 98 00 00`), Scale 0 und 23:59:59 = 86399 (3 Byte `7F 51 01`),
     * Scale 3 und 12:00:00,123 = 43 200 123 (4 Byte `7B 2E 93 02`).
     *
     * @spec SPEC-003
     */
    @Test
    fun `ROW Uhrzeit`() {
        fun time(h: Int, m: Int, s: Int, nano: Int = 0) = TdsValue.TimeValue(java.time.LocalTime.of(h, m, s, nano))
        assertEquals("D1 05 80 96 98 00 00", single(TdsType.TimeType(7), time(0, 0, 1)))
        assertEquals("D1 03 7F 51 01", single(TdsType.TimeType(0), time(23, 59, 59)))
        assertEquals("D1 04 7B 2E 93 02", single(TdsType.TimeType(3), time(12, 0, 0, 123_000_000)))
        assertEquals("D1 05 FF BF 69 2A C9", single(TdsType.TimeType(7), time(23, 59, 59, 999_999_900)))
    }

    /**
     * Orakel: DATETIME2 ist die Zeit (Ticks) gefolgt vom Datum (3 Byte); Scale 7 hat 8 Byte, Scale 0 hat 6.
     *
     * @spec SPEC-003
     */
    @Test
    fun `ROW Datum mit Uhrzeit`() {
        fun stamp(s: Int) = TdsValue.DateTime2Value(java.time.LocalDateTime.of(2024, 1, 1, 0, 0, s))
        assertEquals("D1 08 80 96 98 00 00 45 46 0B", single(TdsType.DateTime2Type(7), stamp(1)))
        assertEquals("D1 06 01 00 00 45 46 0B", single(TdsType.DateTime2Type(0), stamp(1)))
    }

    /**
     * Orakel: mehrere Spalten stehen hintereinander in der Reihenfolge von COLMETADATA.
     *
     * @spec SPEC-003
     */
    @Test
    fun `ROW mehrere Spalten`() {
        val columns = listOf(ColumnDef("a", TdsType.Int4Type), ColumnDef("b", nvarchar10, nullable = true))
        val row = rowOf(columns, TdsValue.IntegerValue(1), TdsValue.NullValue)
        assertEquals("D1 01 00 00 00 FF FF", row)
    }

    /**
     * Orakel: RETURNVALUE (`AC`) ohne Längenfeld: Ordinal (2), Name "@p" als B_VARCHAR, Status 1
     * (OUTPUT), UserType (4), Flags (`fNullable`), TYPE_INFO `26 04`, Wert `04 07 00 00 00`.
     *
     * @spec SPEC-030
     */
    @Test
    fun `RETURNVALUE`() {
        val column = ColumnDef("", TdsType.IntNType(4), nullable = true)
        assertEquals(
            "AC 00 00 02 40 00 70 00 01 00 00 00 00 01 00 26 04 04 07 00 00 00",
            encoded(ReturnValueToken(0, "@p", 1, column, TdsValue.IntegerValue(7))),
        )
    }

    /**
     * Negativfall: ROW ohne COLMETADATA und mit falscher Wertezahl sind Ergebniswerte; ein Fehler
     * ändert weder den Strom noch das gemerkte COLMETADATA.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Fehlgeschlagenes Token aendert den Strom nicht`() {
        assertEquals(ProtocolFailure.NO_COLUMN_METADATA, failureOf(RowToken(listOf(TdsValue.IntegerValue(1)))))
        val meta = ColMetadataToken(listOf(ColumnDef("id", TdsType.Int4Type)))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(meta, RowToken(emptyList())))
        val encoder = TokenEncoder()
        encoder.write(meta)
        val before = encoder.toByteArray().toHex()
        val bad = ColMetadataToken(listOf(ColumnDef("x", TdsType.IntNType(3))))
        assertEquals(ProtocolFailure.INVALID_TYPE, assertIs<ProtocolResult.Failed>(encoder.write(bad)).error.failure)
        assertEquals(before, encoder.toByteArray().toHex())
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(RowToken(listOf(TdsValue.IntegerValue(7)))))
        assertEquals("$before D1 07 00 00 00", encoder.toByteArray().toHex())
    }

    /**
     * Negativfall: Wert und Typ passen nicht zusammen, Zahlen sprengen den Bereich, NULL in einer
     * Spalte ohne NULL, Text länger als die Spalte: Kategorie `TDS_UNSUPPORTED_TYPE`.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Nicht serialisierbare Werte sind Ergebniswerte`() {
        fun failure(type: TdsType, value: TdsValue, nullable: Boolean = true) =
            failureOf(ColMetadataToken(listOf(ColumnDef("c", type, nullable))), RowToken(listOf(value)))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failure(TdsType.Int4Type, TdsValue.TextValue("1"), false))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failure(TdsType.Int4Type, TdsValue.NullValue, false))
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failure(TdsType.IntNType(4), TdsValue.NullValue, false))
        val tooBig = TdsValue.IntegerValue(2147483648L)
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(TdsType.Int4Type, tooBig, false))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(TdsType.IntNType(1), TdsValue.IntegerValue(256)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(TdsType.IntNType(1), TdsValue.IntegerValue(-1)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(TdsType.IntNType(2), TdsValue.IntegerValue(32768)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(nvarchar10, TdsValue.TextValue("01234567890")))
        val category = ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE
        val encoder = TokenEncoder()
        encoder.write(ColMetadataToken(listOf(ColumnDef("c", TdsType.Int4Type))))
        val nullRow = assertIs<ProtocolResult.Failed>(encoder.write(RowToken(listOf(TdsValue.NullValue))))
        assertEquals(category, nullRow.error.category)
    }

    /**
     * Negativfall: Dezimalzahl mit mehr Nachkommastellen als der Scale oder mehr Stellen als die
     * Precision, Datum außerhalb 0001 bis 9999, Uhrzeit feiner als der Scale (Festlegung: abgewiesen,
     * nicht gerundet).
     *
     * @spec SPEC-003
     */
    @Test
    fun `Dezimalzahlen und Zeiten ausserhalb des Typs werden abgewiesen`() {
        fun failure(type: TdsType, value: TdsValue) =
            failureOf(ColMetadataToken(listOf(ColumnDef("c", type))), RowToken(listOf(value)))
        fun decimal(text: String) = TdsValue.DecimalValue(java.math.BigDecimal(text))
        val type = TdsType.DecimalType(10, 2)
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(type, decimal("1.234")))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(type, decimal("100000000.00")))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(type, decimal("-100000000.00")))
        assertEquals("D1 09 01 FF E3 0B 54 02 00 00 00", single(type, decimal("99999999.99"), nullable = false))
        val tooLate = TdsValue.DateValue(java.time.LocalDate.of(10000, 1, 1))
        val tooEarly = TdsValue.DateValue(java.time.LocalDate.of(0, 12, 31))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(TdsType.DateType, tooLate))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(TdsType.DateType, tooEarly))
        val fine = TdsValue.TimeValue(java.time.LocalTime.of(0, 0, 0, 1_000_000))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failure(TdsType.TimeType(2), fine))
        val coarse = TdsValue.TimeValue(java.time.LocalTime.of(0, 0, 0, 100_000_000))
        assertEquals("D1 03 0A 00 00", single(TdsType.TimeType(2), coarse, nullable = false))
    }

    /**
     * Negativfall: Typen außerhalb ihrer Grenzen sind `INVALID_TYPE` (Kategorie `TDS_UNSUPPORTED_TYPE`),
     * auch ein fester Typ in einer Spalte mit NULL.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Ungueltige Typen werden abgewiesen`() {
        fun failure(type: TdsType, nullable: Boolean = false) =
            failureOf(ColMetadataToken(listOf(ColumnDef("c", type, nullable))))
        listOf(
            TdsType.IntNType(3),
            TdsType.IntNType(0),
            TdsType.NVarcharType(0, Collation.LATIN1_GENERAL_CI_AS),
            TdsType.NVarcharType(4001, Collation.LATIN1_GENERAL_CI_AS),
            TdsType.DecimalType(39, 0),
            TdsType.DecimalType(0, 0),
            TdsType.DecimalType(5, 6),
            TdsType.TimeType(8),
            TdsType.TimeType(-1),
            TdsType.DateTime2Type(8),
        ).forEach { assertEquals(ProtocolFailure.INVALID_TYPE, failure(it), "$it") }
        assertEquals(ProtocolFailure.INVALID_TYPE, failure(TdsType.Int4Type, nullable = true))
        assertEquals(ProtocolFailure.INVALID_TYPE, failure(TdsType.BitType, nullable = true))
        assertEquals(ProtocolFailure.INVALID_TYPE, failure(TdsType.Int8Type, nullable = true))
        val widest = meta("c", TdsType.NVarcharType(4000, Collation.LATIN1_GENERAL_CI_AS))
        assertIs<ProtocolResult.Ok<Unit>>(TokenEncoder().write(widest))
    }

    /**
     * Negativfall: Längen- und Bereichsgrenzen der Felder: Namen und Texte über ihrer Breite
     * (`TOO_LONG`), State, Class, Status und Zeilenzahl außerhalb ihres Felds (`VALUE_OUT_OF_RANGE`).
     *
     * @spec SPEC-011
     */
    @Test
    fun `Feldgrenzen der Tokens`() {
        val long255 = "x".repeat(255)
        val long256 = "x".repeat(256)
        assertIs<ProtocolResult.Ok<Unit>>(TokenEncoder().write(EnvChange.Database(long255, long255)))
        assertEquals(ProtocolFailure.TOO_LONG, failureOf(EnvChange.Database(long256, "")))
        assertEquals(ProtocolFailure.TOO_LONG, failureOf(LoginAck(1, 0, long256, 0)))
        assertEquals(ProtocolFailure.TOO_LONG, failureOf(meta(long256, TdsType.Int4Type)))
        assertEquals(ProtocolFailure.TOO_LONG, failureOf(ErrorToken(ServerMessage(1, 1, 1, "x".repeat(65536)))))
        assertEquals(ProtocolFailure.TOO_LONG, failureOf(ErrorToken(ServerMessage(1, 1, 1, "x".repeat(40000)))))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(ErrorToken(ServerMessage(1, 256, 1, ""))))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(ErrorToken(ServerMessage(1, 1, 256, ""))))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(ErrorToken(ServerMessage(1, -1, 1, ""))))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(DoneToken(DoneKind.DONE, 0x10000, 0, 0)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(DoneToken(DoneKind.DONE, 0, 0x10000, 0)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(DoneToken(DoneKind.DONE, 0, 0, -1)))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, failureOf(EnvChange.PacketSize(-1, 0)))
        assertEquals(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE, ProtocolFailure.TOO_LONG.category)
        assertEquals(ProtocolErrorCategory.TDS_PROTOCOL_ERROR, ProtocolFailure.TRUNCATED.category)
    }
}
