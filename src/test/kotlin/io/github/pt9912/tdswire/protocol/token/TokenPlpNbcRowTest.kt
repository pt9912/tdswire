package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.result.ValueMapping
import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolErrorCategory
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.protocol.wire.guarded
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * PLP (MAX-Typen) und NBCROW im Encoder. Herkunft der Belege je Test: *gemessen* heißt Bytes eines echten SQL Servers
 * aus dem Mitschnitt `katalog-abfragen.messung` (`referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/2022` und
 * `/2025`, Zeile 64: Antwort auf die Katalogabfrage von d-migrate, Einzelmessung je Instanz, beide Instanzen gleich);
 * *Handvektor* ist eine Lesart von MS-TDS ohne Messung; *Selbstkonsistenz* ist der Roundtrip mit dem Test-Decoder
 * (`TokenRoundtripTest`); *Festlegung* ist eine Setzung dieses Codecs.
 */
class TokenPlpNbcRowTest {
    private val latin = Collation.LATIN1_GENERAL_CI_AS
    private val maxText = ColumnDef("m", TdsType.NVarcharMaxType(latin), nullable = true)

    private fun write(encoder: TokenEncoder, token: Token): ProtocolResult<Unit> = encoder.write(token)

    private fun row(columns: List<ColumnDef>, token: Token): String {
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(columns)))
        encoder.drain()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(token))
        return encoder.toByteArray().toHex()
    }

    private fun failureOf(columns: List<ColumnDef>, token: Token): ProtocolFailure {
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(columns)))
        return assertIs<ProtocolResult.Failed>(encoder.write(token)).error.failure
    }

    /**
     * Gemessen (Spalte `default_definition`, `nvarchar(max)`, nullbar, Flags `0x0021`, Typ `E7 FF FF` mit Kollation
     * `09 04 D0 00 34`): die Spaltenbytes stehen so im COLMETADATA des echten Servers. Nicht gemessen sind die
     * Spaltenbytes von `varchar(max)` (`A7`) und `varbinary(max)` (`A5`): Handvektor mit demselben Aufbau.
     *
     * @spec SPEC-003
     */
    @Test
    fun `COLMETADATA der MAX-Typen traegt die Laenge FF FF`() {
        val name = "64 00 65 00 66 00"
        val measured = TokenEncoder()
        measured.write(
            ColMetadataToken(
                listOf(ColumnDef("def", TdsType.NVarcharMaxType(latin), nullable = true, extraFlags = 0x20)),
            ),
        )
        assertEquals("81 01 00 00 00 00 00 21 00 E7 FF FF 09 04 D0 00 34 03 $name", measured.toByteArray().toHex())
        val varchar = TokenEncoder()
        varchar.write(ColMetadataToken(listOf(ColumnDef("def", TdsType.VarcharMaxType(latin), nullable = true))))
        assertEquals("81 01 00 00 00 00 00 01 00 A7 FF FF 09 04 D0 00 34 03 $name", varchar.toByteArray().toHex())
        val binary = TokenEncoder()
        binary.write(ColMetadataToken(listOf(ColumnDef("def", TdsType.VarbinaryMaxType, nullable = true))))
        assertEquals("81 01 00 00 00 00 00 01 00 A5 FF FF 03 $name", binary.toByteArray().toHex())
    }

    /**
     * Gemessen: die Zeile aus Zeile 64 des Mitschnitts (Katalogabfrage `sys.columns`, 14 Spalten), als NBCROW: die
     * Bitmap `00 3C` setzt die Bits 10 bis 13, der Wert der Spalte 9 (`default_definition`, `nvarchar(max)`) ist ein
     * PLP-Körper mit bekannter Länge 10, einem Stück und dem Terminator. Die Spalten 10 und 11 sind beim echten Server
     * `sql_variant`; hier stehen nullbare Stellvertreter, weil ihr Wert NULL ist und nicht in der Zeile steht. Der
     * Encoder schreibt dieselben Bytes wie der Server.
     *
     * @spec SPEC-003
     */
    @Test
    fun `NBCROW mit PLP-Wert gibt die Bytes des echten Servers wieder`() {
        val name = ColumnDef("n", TdsType.NVarcharType(128, latin))
        val columns = listOf(
            name,
            name,
            ColumnDef("max_length", TdsType.Int2Type),
            ColumnDef("precision", TdsType.Int1Type),
            ColumnDef("scale", TdsType.Int1Type),
            ColumnDef("is_nullable", TdsType.BitNType, nullable = true),
            ColumnDef("is_identity", TdsType.BitType),
            ColumnDef("is_computed", TdsType.BitNType, nullable = true),
            ColumnDef("column_id", TdsType.Int4Type),
            maxText,
            ColumnDef("seed_value", TdsType.IntNType(8), nullable = true),
            ColumnDef("increment_value", TdsType.IntNType(8), nullable = true),
            maxText,
            ColumnDef("computed_persisted", TdsType.BitNType, nullable = true),
        )
        val values = listOf(
            TdsValue.TextValue("Menge"),
            TdsValue.TextValue("int"),
            TdsValue.IntegerValue(4),
            TdsValue.IntegerValue(10),
            TdsValue.IntegerValue(0),
            TdsValue.BitValue(false),
            TdsValue.BitValue(false),
            TdsValue.BitValue(false),
            TdsValue.IntegerValue(3),
            TdsValue.TextValue("((1))"),
            TdsValue.NullValue,
            TdsValue.NullValue,
            TdsValue.NullValue,
            TdsValue.NullValue,
        )
        assertEquals(
            "D2 00 3C 0A 00 4D 00 65 00 6E 00 67 00 65 00 06 00 69 00 6E 00 74 00 04 00 0A 00 01 00 00 01 00 " +
                "03 00 00 00 0A 00 00 00 00 00 00 00 0A 00 00 00 28 00 28 00 31 00 29 00 29 00 00 00 00 00",
            row(columns, NullBitmapRowToken(values)),
        )
    }

    /**
     * Handvektor (Lesart von MS-TDS): der PLP-Körper aus Gesamtlänge (8 Byte), einem Stück (4 Byte Länge, Daten) und
     * Terminator (4 Nullbyte); der leere Wert hat die Gesamtlänge 0 und kein Stück; NULL sind acht Bytes `FF`.
     * Festlegung: der Encoder schreibt höchstens ein Stück.
     *
     * @spec SPEC-003
     */
    @Test
    fun `PLP-Wert ist Gesamtlaenge, ein Stueck und Terminator`() {
        val zero8 = "00 00 00 00 00 00 00 00"
        assertEquals(
            "D1 04 00 00 00 00 00 00 00 04 00 00 00 61 00 62 00 00 00 00 00",
            row(listOf(maxText), RowToken(listOf(TdsValue.TextValue("ab")))),
        )
        assertEquals("D1 $zero8 00 00 00 00", row(listOf(maxText), RowToken(listOf(TdsValue.TextValue("")))))
        assertEquals("D1 FF FF FF FF FF FF FF FF", row(listOf(maxText), RowToken(listOf(TdsValue.NullValue))))
        val binary = ColumnDef("b", TdsType.VarbinaryMaxType, nullable = true)
        assertEquals(
            "D1 03 00 00 00 00 00 00 00 03 00 00 00 01 02 03 00 00 00 00",
            row(listOf(binary), RowToken(listOf(TdsValue.BytesValue(byteArrayOf(1, 2, 3))))),
        )
        val varchar = ColumnDef("v", TdsType.VarcharMaxType(latin), nullable = true)
        assertEquals(
            "D1 02 00 00 00 00 00 00 00 02 00 00 00 7F 80 00 00 00 00",
            row(listOf(varchar), RowToken(listOf(TdsValue.BytesValue(byteArrayOf(0x7F, 0x80.toByte()))))),
        )
        assertEquals("D1 $zero8 00 00 00 00", row(listOf(binary), RowToken(listOf(TdsValue.BytesValue(ByteArray(0))))))
    }

    /**
     * Handvektor: RETURNVALUE trägt TYPE_INFO und Wert eines MAX-Typs wie eine Spalte (Ordinal 1, Name `@o`, Status
     * OUTPUT, UserType 0, Flags `0000`).
     *
     * @spec SPEC-003
     */
    @Test
    fun `RETURNVALUE mit MAX-Typ`() {
        val encoder = TokenEncoder()
        val column = ColumnDef("", TdsType.NVarcharMaxType(latin), nullable = true)
        encoder.write(ReturnValueToken(1, "@o", 1, column, TdsValue.TextValue("x")))
        assertEquals(
            "AC 01 00 02 40 00 6F 00 01 00 00 00 00 00 00 E7 FF FF 09 04 D0 00 34 " +
                "02 00 00 00 00 00 00 00 02 00 00 00 78 00 00 00 00 00",
            encoder.toByteArray().toHex(),
        )
    }

    /**
     * Negativfall: ein Wert, der nicht zum MAX-Typ passt (Text in `varbinary(max)`, Bytes in `nvarchar(max)`, eine
     * Zahl), und NULL in einer Spalte ohne NULL sind Ergebniswerte der Kategorie `TDS_UNSUPPORTED_TYPE`.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Unpassende Werte und NULL ohne Nullbarkeit sind Ergebniswerte`() {
        val binary = ColumnDef("b", TdsType.VarbinaryMaxType, nullable = true)
        val strict = ColumnDef("s", TdsType.NVarcharMaxType(latin))
        assertEquals(
            ProtocolFailure.TYPE_MISMATCH,
            failureOf(listOf(binary), RowToken(listOf(TdsValue.TextValue("a")))),
        )
        assertEquals(
            ProtocolFailure.TYPE_MISMATCH,
            failureOf(listOf(maxText), RowToken(listOf(TdsValue.BytesValue(byteArrayOf(1))))),
        )
        assertEquals(
            ProtocolFailure.TYPE_MISMATCH,
            failureOf(listOf(maxText), RowToken(listOf(TdsValue.IntegerValue(1)))),
        )
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(listOf(strict), RowToken(listOf(TdsValue.NullValue))))
        assertEquals(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE, ProtocolFailure.TYPE_MISMATCH.category)
    }

    /**
     * Festlegung: der Backend-Vertrag kennt keinen MAX-Typ; die Wertabbildung weist einen Wert für eine MAX-Spalte mit
     * `INVALID_TYPE` ab (Kategorie `TDS_UNSUPPORTED_TYPE`), statt ihn zu raten.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Die Wertabbildung des Backend-Vertrags weist MAX-Spalten ab`() {
        val result = ValueMapping.value(maxText, "abc")
        assertEquals(ProtocolFailure.INVALID_TYPE, assertIs<ProtocolResult.Failed>(result).error.failure)
    }

    /**
     * Festlegung: ein Wert über der Grenze des Puffers ([PlpValues.MAX_BUFFERED_BYTES], samt Rahmen von 16 Byte und
     * dem schon gepufferten Teil) ist `TOO_LONG`; die Prüfung läuft vor dem ersten geschriebenen Byte. Mit der
     * Grenze 8 Byte wird der Text `abcd` (8 Byte) samt Rahmen abgewiesen, ohne dass der Puffer wächst.
     *
     * @spec SPEC-003
     * @spec SPEC-027
     */
    @Test
    fun `Die Laengenpruefung steht vor dem Puffern`() {
        val out = ByteWriter()
        val text = TdsValue.TextValue("abcd")
        val failed = guarded { PlpValues.write(out, TdsType.NVarcharMaxType(latin), text, limit = 8) }
        assertEquals(ProtocolFailure.TOO_LONG, assertIs<ProtocolResult.Failed>(failed).error.failure)
        assertEquals(0, out.size)
        val bytes = TdsValue.BytesValue(ByteArray(8))
        val failedBytes = guarded { PlpValues.write(out, TdsType.VarbinaryMaxType, bytes, limit = 8) }
        assertEquals(ProtocolFailure.TOO_LONG, assertIs<ProtocolResult.Failed>(failedBytes).error.failure)
        assertEquals(0, out.size)
        val fits = guarded { PlpValues.write(out, TdsType.VarbinaryMaxType, bytes, limit = 24) }
        assertIs<ProtocolResult.Ok<Unit>>(fits)
        assertEquals(8 + 4 + 8 + 4, out.size)
    }

    /**
     * Festlegung: die Grenze der Länge ist `MAX_BUFFERED_BYTES` minus 16 Byte Rahmen minus den schon gepufferten Byte,
     * auf das Byte genau (die Größe eines Werts über 2 GiB lässt sich nicht als Wert anlegen, die Prüfung nimmt daher
     * die Länge).
     *
     * @spec SPEC-003
     */
    @Test
    fun `Die Grenze der Laenge gilt auf das Byte genau`() {
        val limit = PlpValues.MAX_BUFFERED_BYTES.toLong() - 16
        PlpValues.checkLength(limit, 0)
        assertEquals(
            ProtocolFailure.TOO_LONG,
            assertIs<ProtocolResult.Failed>(guarded { PlpValues.checkLength(limit + 1, 0) }).error.failure,
        )
        PlpValues.checkLength(limit - 100, 100)
        assertEquals(
            ProtocolFailure.TOO_LONG,
            assertIs<ProtocolResult.Failed>(guarded { PlpValues.checkLength(limit - 99, 100) }).error.failure,
        )
    }

    /**
     * Handvektor (Lesart von MS-TDS). Gemessen ist die Lage der Bits für die Spalten 9 bis 13: die Bitmap `00 32` aus
     * `sql_variant mit Basistyp int …` unterscheidet die Bitreihenfolge (`00 3C` aus `NBCROW mit PLP-Wert …` ist als
     * Bitmuster symmetrisch und tut es nicht). Ein Bit je Spalte, `ceil(n / 8)` Byte, Spalte 0 im niedrigsten Bit des
     * ersten Bytes; ohne Spalten steht nur die Kennung. Die Werte NULL stehen nicht in der Zeile.
     *
     * @spec SPEC-003
     */
    @Test
    fun `NBCROW legt die Bitmap je Spalte an`() {
        val int4 = ColumnDef("i", TdsType.IntNType(4), nullable = true)
        val one = TdsValue.IntegerValue(1)
        val oneBytes = "04 01 00 00 00"
        assertEquals("D2", row(emptyList(), NullBitmapRowToken(emptyList())))
        assertEquals("D2 FF", row(List(8) { int4 }, NullBitmapRowToken(List(8) { TdsValue.NullValue })))
        assertEquals(
            "D2 01 $oneBytes",
            row(listOf(int4, int4), NullBitmapRowToken(listOf(TdsValue.NullValue, one))),
        )
        assertEquals(
            "D2 80 ${List(7) { oneBytes }.joinToString(" ")}",
            row(List(8) { int4 }, NullBitmapRowToken(List(7) { one } + TdsValue.NullValue)),
        )
        assertEquals(
            "D2 00 01 ${List(8) { oneBytes }.joinToString(" ")}",
            row(List(9) { int4 }, NullBitmapRowToken(List(8) { one } + TdsValue.NullValue)),
        )
    }

    /**
     * Negativfall: NBCROW ohne COLMETADATA, mit falscher Wertezahl, mit NULL in einer Spalte ohne NULL und mit einem
     * Wert, der nicht zum Typ passt, ist ein Ergebniswert und ändert den Strom nicht.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Fehlgeschlagene NBCROW aendern den Strom nicht`() {
        val int4 = ColumnDef("i", TdsType.Int4Type)
        assertEquals(
            ProtocolFailure.NO_COLUMN_METADATA,
            assertIs<ProtocolResult.Failed>(TokenEncoder().write(NullBitmapRowToken(emptyList()))).error.failure,
        )
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(listOf(int4), NullBitmapRowToken(emptyList())))
        assertEquals(
            ProtocolFailure.TYPE_MISMATCH,
            failureOf(listOf(int4), NullBitmapRowToken(listOf(TdsValue.NullValue))),
        )
        assertEquals(
            ProtocolFailure.TYPE_MISMATCH,
            failureOf(listOf(int4), NullBitmapRowToken(listOf(TdsValue.BitValue(true)))),
        )
        val encoder = TokenEncoder()
        encoder.write(ColMetadataToken(listOf(int4)))
        val before = encoder.toByteArray().toHex()
        assertIs<ProtocolResult.Failed>(encoder.write(NullBitmapRowToken(listOf(TdsValue.NullValue))))
        assertEquals(before, encoder.toByteArray().toHex())
        assertIs<ProtocolResult.Ok<Unit>>(write(encoder, NullBitmapRowToken(listOf(TdsValue.IntegerValue(7)))))
        assertEquals("$before D2 00 07 00 00 00", encoder.toByteArray().toHex())
    }

    /**
     * Gemessen (Mitschnitt `katalog-abfragen`, Zeile 64 und die Zeile der Katalogabfrage mit Werten, beide Instanzen):
     * `sql_variant` ist `62` mit der größten Länge 8009 (`49 1F 00 00`), Flags `0x0021`; der Wert ist die Länge 6, der
     * Basistyp `int` (`38`), kein Eigenschaftsbyte und 4 Byte Wert. Die Zeile ist der NBCROW des echten Servers mit
     * Bitmap `00 32` (die Spalten 9, 12 und 13 sind NULL und stehen nicht in der Zeile).
     *
     * @spec SPEC-003
     */
    @Test
    fun `sql_variant mit Basistyp int gibt die Bytes des echten Servers wieder`() {
        val encoder = TokenEncoder()
        val variant = ColumnDef("seed_value", TdsType.SqlVariantType, nullable = true, extraFlags = 0x20)
        encoder.write(ColMetadataToken(listOf(variant)))
        assertEquals(
            "81 01 00 00 00 00 00 21 00 62 49 1F 00 00 0A 73 00 65 00 65 00 64 00 5F 00 76 00 61 00 6C 00 75 00 65 00",
            encoder.toByteArray().toHex(),
        )
        val name = ColumnDef("n", TdsType.NVarcharType(128, latin))
        val bit = ColumnDef("b", TdsType.BitNType, nullable = true)
        val columns = listOf(
            name,
            name,
            ColumnDef("max_length", TdsType.Int2Type),
            ColumnDef("precision", TdsType.Int1Type),
            ColumnDef("scale", TdsType.Int1Type),
            bit,
            ColumnDef("is_identity", TdsType.BitType),
            bit,
            ColumnDef("column_id", TdsType.Int4Type),
            maxText,
            variant,
            variant,
            maxText,
            bit,
        )
        val one = TdsValue.VariantIntValue(1)
        val values = listOf(
            TdsValue.TextValue("Id"),
            TdsValue.TextValue("int"),
            TdsValue.IntegerValue(4),
            TdsValue.IntegerValue(10),
            TdsValue.IntegerValue(0),
            TdsValue.BitValue(false),
            TdsValue.BitValue(true),
            TdsValue.BitValue(false),
            TdsValue.IntegerValue(1),
            TdsValue.NullValue,
            one,
            one,
            TdsValue.NullValue,
            TdsValue.NullValue,
        )
        assertEquals(
            "D2 00 32 04 00 49 00 64 00 06 00 69 00 6E 00 74 00 04 00 0A 00 01 00 01 01 00 01 00 00 00 " +
                "06 00 00 00 38 00 01 00 00 00 06 00 00 00 38 00 01 00 00 00",
            row(columns, NullBitmapRowToken(values)),
        )
    }

    /**
     * Handvektor (nicht gemessen): `sql_variant` in ROW trägt denselben Wert; NULL ist die Länge 0. Festlegung: jeder
     * andere Wert (Text, Zahl, Bytes) und jeder andere Basistyp ist `TYPE_MISMATCH`, Kategorie
     * `TDS_UNSUPPORTED_TYPE`; `sql_variant` ohne Nullbarkeit lässt NULL nicht zu.
     *
     * @spec SPEC-003
     * @spec SPEC-014
     */
    @Test
    fun `sql_variant in ROW, NULL und andere Werte`() {
        val variant = ColumnDef("v", TdsType.SqlVariantType, nullable = true)
        assertEquals(
            "D1 06 00 00 00 38 00 FF FF FF FF",
            row(listOf(variant), RowToken(listOf(TdsValue.VariantIntValue(-1)))),
        )
        assertEquals("D1 00 00 00 00", row(listOf(variant), RowToken(listOf(TdsValue.NullValue))))
        listOf(TdsValue.TextValue("x"), TdsValue.IntegerValue(1), TdsValue.BytesValue(ByteArray(1))).forEach {
            assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(listOf(variant), RowToken(listOf(it))), "$it")
        }
        val strict = ColumnDef("s", TdsType.SqlVariantType)
        assertEquals(ProtocolFailure.TYPE_MISMATCH, failureOf(listOf(strict), RowToken(listOf(TdsValue.NullValue))))
        val result = ValueMapping.value(variant, 1)
        assertEquals(ProtocolFailure.INVALID_TYPE, assertIs<ProtocolResult.Failed>(result).error.failure)
    }
}
