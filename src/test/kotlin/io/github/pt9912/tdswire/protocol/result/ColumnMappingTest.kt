package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ColumnMappingTest {
    private fun column(type: SqlType, nullable: Boolean, name: String = "a") = ResultColumn(name, type, nullable)

    /** Eine Spalte `a` als COLMETADATA: Spaltenzahl 1, UserType 0, Flags, TYPE_INFO, Name `a`. */
    private fun metadata(flags: String, typeInfo: String) = "81 01 00 00 00 00 00 $flags 00 $typeInfo 01 61 00"

    private val latinNVarchar = "E7 40 1F 09 04 D0 00 34"

    /**
     * Erwartete TYPE_INFO-Bytes, übereinstimmend mit den Orakeln des Encoder-Tests (Selbstkonsistenz mit
     * dem Codec; eine Messung oder eine Quelle im Repo gibt es dafür nicht): feste Typen `38` (INT4), `7F` (INT8) und
     * `32` (BIT) nicht nullbar, `26 04`, `26 08` und `68 01` (INTN, BITN) nullbar; Unicode-Text ist `E7` mit
     * 8000 Byte (`40 1F`) und der Kollation `09 04 D0 00 34`; `28` (DATEN), `29 07` (TIMEN), `2A 07`
     * (DATETIME2N); `6A 09 0A 02` ist DECIMALN, Länge 9 für Precision 10 bis 19. Das Bit `fNullable` ist
     * Bit 0 der Flags. Die Kollation und die Scale 7 sind Festlegungen ohne Messung (Quelltext).
     *
     * @spec SPEC-003
     */
    @Test
    fun `Mindestkatalog wird in fester und N-Form abgebildet`() {
        val cases = listOf(
            Triple(SqlType.Int32, false, "00" to "38"),
            Triple(SqlType.Int32, true, "01" to "26 04"),
            Triple(SqlType.Int64, false, "00" to "7F"),
            Triple(SqlType.Int64, true, "01" to "26 08"),
            Triple(SqlType.Bool, false, "00" to "32"),
            Triple(SqlType.Bool, true, "01" to "68 01"),
            Triple(SqlType.UnicodeText, false, "00" to latinNVarchar),
            Triple(SqlType.UnicodeText, true, "01" to latinNVarchar),
            Triple(SqlType.Decimal(10, 2), true, "01" to "6A 09 0A 02"),
            Triple(SqlType.Date, true, "01" to "28"),
            Triple(SqlType.Time, true, "01" to "29 07"),
            Triple(SqlType.DateTime, false, "00" to "2A 07"),
        )
        for ((type, nullable, expected) in cases) {
            assertEquals(
                metadata(expected.first, expected.second),
                MappingSupport.encodedMetadata(listOf(column(type, nullable))),
                "$type nullable=$nullable",
            )
        }
    }

    /**
     * Die Zuordnung auf `TdsType` selbst, ohne den Umweg über Bytes: N-Form genau bei nullbaren Spalten
     * der Typen Integer und Bit, Zeit- und Datumstypen mit Scale 7.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Spaltendefinition traegt TdsType, Name und Nullbarkeit`() {
        val defs = MappingSupport.defs(
            column(SqlType.Int32, false, "i"),
            column(SqlType.Int32, true, "j"),
            column(SqlType.Time, false, "t"),
            column(SqlType.DateTime, true, "d"),
        )
        assertEquals(
            listOf(
                ColumnDef("i", TdsType.Int4Type, nullable = false),
                ColumnDef("j", TdsType.IntNType(4), nullable = true),
                ColumnDef("t", TdsType.TimeType(7), nullable = false),
                ColumnDef("d", TdsType.DateTime2Type(7), nullable = true),
            ),
            defs,
        )
    }

    /**
     * Handvektor: `id INT NOT NULL, name NVARCHAR NULL` als COLMETADATA mit zwei Spalten; Namen sind
     * UTF-16LE mit Längenbyte.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Spaltenliste mit zwei Spalten ergibt das erwartete COLMETADATA`() {
        assertEquals(
            "81 02 00 00 00 00 00 00 00 38 02 69 00 64 00 " +
                "00 00 00 00 01 00 $latinNVarchar 04 6E 00 61 00 6D 00 65 00",
            MappingSupport.encodedMetadata(
                listOf(column(SqlType.Int32, false, "id"), column(SqlType.UnicodeText, true, "name")),
            ),
        )
    }

    /**
     * Precision 1 bis 38 sind abbildbar, 39 nicht: der Widerspruch zwischen dem Modell (`Decimal` kennt
     * keine Obergrenze) und dem Encoder (1 bis 38) endet in `TDS_UNSUPPORTED_TYPE`, bevor ein Byte
     * geschrieben wird. Handvektor `6A 11 26 26`: Länge 17 für Precision 20 bis 38.
     *
     * @spec SPEC-003
     * @spec SPEC-014
     */
    @Test
    fun `Decimal bis Precision 38 ist abbildbar, darueber TDS_UNSUPPORTED_TYPE`() {
        assertEquals(
            listOf(ColumnDef("a", TdsType.DecimalType(1, 0), nullable = true)),
            MappingSupport.defs(column(SqlType.Decimal(1, 0), true)),
        )
        assertEquals(
            metadata("00", "6A 11 26 26"),
            MappingSupport.encodedMetadata(listOf(column(SqlType.Decimal(38, 38), false))),
        )
        val error = MappingSupport.error(ColumnMapping.map(listOf(column(SqlType.Decimal(39, 0), false))))
        assertEquals(ProtocolFailure.INVALID_TYPE, error.failure)
    }

    /**
     * Die Liste wird als Ganzes geprüft: ein einziger nicht abbildbarer Typ an letzter Stelle macht das
     * Ergebnis zu einem Fehler ohne Teilliste, und der Fehler nennt die Position.
     *
     * @spec SPEC-031
     * @spec SPEC-014
     */
    @Test
    fun `ein Fehler in der letzten Spalte verwirft die ganze Liste`() {
        val result = ColumnMapping.map(
            listOf(
                column(SqlType.Int32, false, "a"),
                column(SqlType.Bool, true, "b"),
                column(SqlType.Decimal(50, 0), false, "c"),
            ),
        )
        val error = MappingSupport.error(result)
        assertTrue("Spalte 2" in error.detail, error.detail)
    }

    /**
     * Die leere Spaltenliste ist gültig und ergibt `81 00 00` (COLMETADATA mit null Spalten).
     *
     * @spec SPEC-003
     */
    @Test
    fun `leere Spaltenliste ist gueltig`() {
        assertEquals("81 00 00", MappingSupport.encodedMetadata(emptyList()))
    }

    /**
     * Festlegung ohne Messung (Quelltext): leere und doppelte Spaltennamen sind gültig, weil TDS sie
     * trägt; ein Name über 255 Zeichen (B_VARCHAR) und ein Name mit unpaarem Surrogat werden abgewiesen.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Spaltennamen werden nach Laenge und Unicode geprueft`() {
        val twins = MappingSupport.defs(column(SqlType.Int32, false, ""), column(SqlType.Int32, false, ""))
        assertEquals(listOf("", ""), twins.map { it.name })
        MappingSupport.defs(column(SqlType.Int32, false, "x".repeat(255)))
        val long = ColumnMapping.map(listOf(column(SqlType.Int32, false, "x".repeat(256))))
        assertEquals(ProtocolFailure.TOO_LONG, MappingSupport.error(long).failure)
        val broken = ColumnMapping.map(listOf(column(SqlType.Int32, false, "a\uD800")))
        assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, MappingSupport.error(broken).failure)
    }

    /**
     * Die Textdarstellung eines Metadatenfehlers enthält keinen Spaltennamen: weder bei einem zu langen
     * Namen noch bei einem Namen mit unpaarem Surrogat; die Position der Spalte steht im Fehler.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Fehlertext der Metadaten enthaelt keinen Spaltennamen`() {
        val names = listOf("geheimspalte" + "x".repeat(250), "geheimspalte\uD800")
        for (name in names) {
            val columns = listOf(column(SqlType.Int32, false, "ok"), column(SqlType.Int32, false, name))
            val text = MappingSupport.error(ColumnMapping.map(columns)).toString()
            assertTrue("Spalte 1" in text, text)
            assertFalse("geheimspalte" in text, text)
        }
    }

    /**
     * Die Grenze von 65534 Spalten ist die des Encoders (Festlegung des Codecs, ohne Messung): die
     * Abbildung nimmt 65534 an, der Encoder schreibt sie, 65535 weist die Abbildung ab.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Spaltenzahl endet bei 65534`() {
        val limit = List(ColumnMapping.MAX_COLUMNS) { column(SqlType.Bool, false, "c") }
        val defs = assertIs<ProtocolResult.Ok<List<ColumnDef>>>(ColumnMapping.map(limit)).value
        assertIs<ProtocolResult.Ok<Unit>>(TokenEncoder().write(ColMetadataToken(defs)))
        val error = MappingSupport.error(ColumnMapping.map(limit + column(SqlType.Bool, false, "c")))
        assertEquals(ProtocolFailure.TOO_LONG, error.failure)
    }
}
