package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.result.ColumnMapping
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.NullBitmapRowToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.HexFormat
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Wahl zwischen ROW und NBCROW im Sequenzierer ([RowForm]), mit den Bytes der Messung am echten SQL Server als
 * Orakel: Einzelmessung je Instanz 2022 (16.0.4305.5) und 2025 (17.0.5035.5), Messreihe `zeilenform-null`
 * (`referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/zeilenform-null.messung`,
 * `Microsoft.Data.SqlClient` 7.1.1, Messprotokoll `docs/messungen/zeilenform-nbcrow-2026-11-17.md`). Die Bytes beider
 * Instanzen sind je Zeile gleich; `MessungBulkBelegTests` in `referenztest-dotnet` hält dieselben Hexfolgen gegen beide
 * Ablagen. Die Folgen unten sind die Bytes je Zeile (Kennbyte `d1` oder `d2` bis zum Ende der Zeile) so, wie der Server
 * sie sendet; verglichen werden sie mit den Bytes, die der Encoder für die Tokens des Sequenzierers schreibt. Nicht
 * verglichen sind die Bytes von COLMETADATA (Längenwahl des Dezimaltyps, `docs/messungen/referenzabgleich.md` A-11) und
 * die Typen außerhalb des Typmodells (`uniqueidentifier`, `varbinary`, `nvarchar(max)`): der Sequenzierer erreicht sie
 * nicht.
 */
class ResponseRowFormTest {
    private companion object {
        const val SEED = 0x4E4243L
        const val RUNS = 400
        const val MAX_RANDOM_COLUMNS = 40
        const val MAX_RANDOM_ROWS = 30
        const val PERCENT = 100
        const val WIDE_COLUMNS = 3000
        const val BITS_PER_BYTE = 8
        const val MANY_ROWS = 20000
        const val EPOCH_DAYS = 20000
        const val TEXT_LENGTH = 20
    }

    private val text = SqlType.UnicodeText

    private fun column(name: String, type: SqlType, nullable: Boolean = true, maxLength: Int = 4000) =
        ResultColumn(name, type, nullable, maxLength)

    /** Führt ein Ergebnis aus [columns] und [rows] in der [form] aus; liefert Ausgang und die Bytes je Zeilentoken. */
    private fun answer(
        columns: List<ResultColumn>,
        rows: List<List<Any?>>,
        form: ResponseForm = ResponseForm.BATCH,
    ): Pair<SequenceOutcome, List<String>> {
        val events = listOf<QueryEvent>(QueryEvent.ResultStart(columns)) + rows.map { QueryEvent.Row(it) } +
            QueryEvent.ResultEnd(rows.size.toLong())
        val sink = RecordingSink()
        val request = QueryRequest("s", "r", "SELECT 1", emptyList(), null)
        val outcome = runBlocking {
            ResponseSequencer(FIXED_MAPPING).run(ScriptedBackend.ofList(*events.toTypedArray()), request, sink, form)
        }
        return outcome to encodedRows(sink.tokens)
    }

    private fun encodedRows(tokens: List<Token>): List<String> {
        val encoder = TokenEncoder()
        val result = mutableListOf<String>()
        tokens.forEach { token ->
            assertIs<ProtocolResult.Ok<Unit>>(encoder.write(token))
            val bytes = encoder.drain()
            if (token is RowToken || token is NullBitmapRowToken) result += HexFormat.of().formatHex(bytes)
        }
        return result
    }

    private fun assertRows(expected: List<String>, columns: List<ResultColumn>, rows: List<List<Any?>>) {
        val (outcome, actual) = answer(columns, rows)
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals(expected, actual)
    }

    private val measuredZwei = listOf(
        "d101000000040a00000002007800",
        "d1020000000002007900",
        "d204030000000414000000",
        "d20604000000",
        "d105000000041e00000002007a00",
    )

    private val measuredNurNull = listOf(
        "d203",
    )

    private val measuredBreit8 = listOf(
        "d104010000000402000000040300000004040000000405000000040600000004070000000408000000",
        "d1000402000000040300000004040000000405000000040600000004070000000408000000",
        "d203040300000004040000000405000000040600000004070000000408000000",
        "d20704040000000405000000040600000004070000000408000000",
        "d20f0405000000040600000004070000000408000000",
        "d2ff",
        "d104010000000402000000040300000004040000000405000000040600000004070000000408000000",
        "d104010000000402000000040300000004040000000405000000040600000004070000000408000000",
    )

    private val measuredBreit9 = listOf(
        "d1040100000004020000000403000000040400000004050000000406000000040700000004080000000409000000",
        "d10004020000000403000000040400000004050000000406000000040700000004080000000409000000",
        "d100000403000000040400000004050000000406000000040700000004080000000409000000",
        "d20700040400000004050000000406000000040700000004080000000409000000",
        "d20f0004050000000406000000040700000004080000000409000000",
        "d2ff01",
        "d1040100000004020000000403000000040400000004050000000406000000040700000004080000000409000000",
        "d10401000000040200000004030000000404000000040500000004060000000407000000040800000000",
    )

    private val measuredBreit10 = listOf(
        "d1040100000004020000000403000000040400000004050000000406000000040700000004080000000409000000040a" +
            "000000",
        "d10004020000000403000000040400000004050000000406000000040700000004080000000409000000040a000000",
        "d100000403000000040400000004050000000406000000040700000004080000000409000000040a000000",
        "d20700040400000004050000000406000000040700000004080000000409000000040a000000",
        "d20f0004050000000406000000040700000004080000000409000000040a000000",
        "d2ff03",
        "d104010000000402000000040300000004040000000405000000040600000004070000000408000000040900000000",
        "d1040100000004020000000403000000040400000004050000000406000000040700000004080000000000",
    )

    private val measuredBreit16 = listOf(
        "d1040100000004020000000403000000040400000004050000000406000000040700000004080000000409000000040a" +
            "000000040b000000040c000000040d000000040e000000040f0000000410000000",
        "d10004020000000403000000040400000004050000000406000000040700000004080000000409000000040a00000004" +
            "0b000000040c000000040d000000040e000000040f0000000410000000",
        "d100000403000000040400000004050000000406000000040700000004080000000409000000040a000000040b000000" +
            "040c000000040d000000040e000000040f0000000410000000",
        "d20700040400000004050000000406000000040700000004080000000409000000040a000000040b000000040c000000" +
            "040d000000040e000000040f0000000410000000",
        "d20f0004050000000406000000040700000004080000000409000000040a000000040b000000040c000000040d000000" +
            "040e000000040f0000000410000000",
        "d2ffff",
        "d10401000000040200000004030000000404000000040500000004060000000407000000040800000004090000000004" +
            "0b000000040c000000040d000000040e000000040f0000000410000000",
        "d1040100000004020000000403000000040400000004050000000406000000040700000004080000000000040b000000" +
            "040c000000040d000000040e000000040f0000000410000000",
    )

    private val measuredBreit17 = listOf(
        "d1040100000004020000000403000000040400000004050000000406000000040700000004080000000409000000040a" +
            "000000040b000000040c000000040d000000040e000000040f00000004100000000411000000",
        "d10004020000000403000000040400000004050000000406000000040700000004080000000409000000040a00000004" +
            "0b000000040c000000040d000000040e000000040f00000004100000000411000000",
        "d100000403000000040400000004050000000406000000040700000004080000000409000000040a000000040b000000" +
            "040c000000040d000000040e000000040f00000004100000000411000000",
        "d1000000040400000004050000000406000000040700000004080000000409000000040a000000040b000000040c0000" +
            "00040d000000040e000000040f00000004100000000411000000",
        "d20f000004050000000406000000040700000004080000000409000000040a000000040b000000040c000000040d0000" +
            "00040e000000040f00000004100000000411000000",
        "d2ffff01",
        "d10401000000040200000004030000000404000000040500000004060000000407000000040800000004090000000004" +
            "0b000000040c000000040d000000040e000000040f00000004100000000411000000",
        "d1040100000004020000000403000000040400000004050000000406000000040700000004080000000000040b000000" +
            "040c000000040d000000040e000000040f00000004100000000411000000",
    )

    private val measuredGemischt = listOf(
        "d10200610002006200040100000004020000000403000000040400000004050000000406000000040700000004080000" +
            "00",
        "d1ffff0200620004010000000402000000040300000004040000000405000000040600000004070000000408000000",
        "d2030004010000000402000000040300000004040000000405000000040600000004070000000408000000",
        "d20500020062000402000000040300000004040000000405000000040600000004070000000408000000",
        "d21c00020061000200620004040000000405000000040600000004070000000408000000",
        "d102006100020062000000040300000004040000000405000000040600000004070000000408000000",
    )

    private val measuredFest = listOf(
        "d10100000064000000000000000800650069006e007300",
        "d102000000c80000000000000008007a00770065006900",
    )

    private val measuredTypen = listOf(
        "d10101020200040300000008040000000000000001010321490b0580ed97f46e0880ed97f46e21490b",
        "d100020200040300000008040000000000000001010321490b0580ed97f46e0880ed97f46e21490b",
        "d1010100040300000008040000000000000001010321490b0580ed97f46e0880ed97f46e21490b",
        "d101010202000008040000000000000001010321490b0580ed97f46e0880ed97f46e21490b",
        "d1010102020004030000000001010321490b0580ed97f46e0880ed97f46e21490b",
        "d101010202000403000000080400000000000000000321490b0580ed97f46e0880ed97f46e21490b",
        "d1010102020004030000000804000000000000000101000580ed97f46e0880ed97f46e21490b",
        "d10101020200040300000008040000000000000001010321490b000880ed97f46e21490b",
        "d10101020200040300000008040000000000000001010321490b0580ed97f46e00",
        "d230010102020004030000000804000000000000000580ed97f46e0880ed97f46e21490b",
        "d2ff",
    )

    private val measuredLiteral = listOf(
        "d10100000000",
    )

    // ---- Zeilen der Messung (Tabellen z_zwei, z_breit, z_gemischt, z_fest, z_typ2) --------------------------------

    private val wide = (1..17).map { column("C$it", SqlType.Int32) }

    /**
     * Die acht Zeilen von `z_breit` (NULL in den ersten ein bis vier Spalten, in allen, nur in `C10`, in `C9` und
     * `C10`), erste [n] Spalten.
     */
    private fun wideRows(n: Int): List<List<Any?>> {
        val nullAt = listOf<(Int) -> Boolean>(
            { false },
            { it <= 1 },
            { it <= 2 },
            { it <= 3 },
            { it <= 4 },
            { true },
            { it == 10 },
            { it == 9 || it == 10 },
        )
        return nullAt.map { isNull -> (1..n).map { if (isNull(it)) null else it } }
    }

    // ---- Zeilen ohne, mit einer und mit nur NULL ------------------------------------------------------------------

    /**
     * `SELECT Id, A, B FROM z_zwei ORDER BY Id` (Messung, Austausch 01, beide Instanzen): `Id` `int` ohne NULL, `A`
     * `int` und `B` `nvarchar(20)` nullbar; fünf Zeilen. Gemessen: ROW ohne NULL, ROW mit NULL in `A` (Bitmap ein Byte,
     * ein NULL-Byte gespart: gleich lang), NBCROW mit NULL in `B` (zwei Byte gespart, ein Byte Bitmap) und NBCROW mit
     * NULL in beiden. Wächter: ein Sequenzierer, der immer ROW oder bei jeder NULL NBCROW schreibt (zweite Zeile),
     * einer, der bei Gleichstand NBCROW wählt (zweite Zeile), einer, der `NVARCHAR` wie `INTN` mit einem Byte rechnet
     * (dritte Zeile).
     *
     * @spec SPEC-003
     */
    @Test
    fun `Zeilen ohne NULL, mit NULL in INTN, in NVARCHAR und in beiden schreiben ROW ROW NBCROW NBCROW`() {
        val columns = listOf(
            column("Id", SqlType.Int32, nullable = false),
            column("A", SqlType.Int32),
            column("B", text, maxLength = 20),
        )
        val rows = listOf(
            listOf(1, 10, "x"),
            listOf(2, null, "y"),
            listOf(3, 20, null),
            listOf(4, null, null),
            listOf(5, 30, "z"),
        )
        assertRows(measuredZwei, columns, rows)
    }

    /**
     * `SELECT A, B FROM z_zwei WHERE Id = 4` (Austausch 02) und `SELECT CAST(NULL AS int), CAST(NULL AS nvarchar(20))`
     * (Austausch 12): eine Zeile aus zwei NULL ist `d2 03` (Bitmap, kein Wert), im Batch. Wächter: ROW mit NULL-Werten
     * (`d1 00 ff ff`).
     *
     * @spec SPEC-003
     */
    @Test
    fun `eine Zeile nur aus NULL ist eine NBCROW aus der Bitmap allein`() {
        val columns = listOf(column("A", SqlType.Int32), column("B", text, maxLength = 20))
        assertRows(measuredNurNull, columns, listOf(listOf(null, null)))
    }

    /**
     * Die Antworten im RPC (`sp_executesql`, Austausch 15 bis 17, `WHERE Id = 2`, `1`, `4`): dieselbe Wahl wie im
     * Batch; Id 2 ROW mit NULL in `A`, Id 1 ROW ohne NULL, Id 4 NBCROW `d2 03`. Wächter: eine Wahl, die nur in der Form
     * BATCH gilt.
     *
     * @spec SPEC-003
     */
    @Test
    fun `die Wahl gilt im RPC wie im Batch`() {
        val columns = listOf(column("A", SqlType.Int32), column("B", text, maxLength = 20))
        val rows = listOf(listOf(null, "y"), listOf(10, "x"), listOf(null, null))
        val (outcome, actual) = answer(columns, rows, ResponseForm.RPC)
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals(listOf("d10002007900", "d1040a00000002007800", "d203"), actual)
    }

    /**
     * `SELECT 1 AS a, CAST(NULL AS int) AS b` (Austausch 13): eine Spalte ohne NULL und eine nullbare mit NULL ist ein
     * ROW (`d1 01000000 00`): ein NULL-Byte und ein Byte Bitmap sind gleich lang.
     *
     * @spec SPEC-003
     */
    @Test
    fun `eine NULL in einer INTN-Spalte neben einer Spalte ohne NULL bleibt ROW`() {
        val columns = listOf(column("a", SqlType.Int32, nullable = false), column("b", SqlType.Int32))
        assertRows(measuredLiteral, columns, listOf(listOf(1, null)))
    }

    // ---- Die Bitmap wächst mit der Spaltenzahl ----------------------------------------------------------------------

    /**
     * `z_breit` mit den ersten 8 Spalten (Austausch 03): die Bitmap hat ein Byte, zwei NULL-Werte von je einem Byte
     * sparen mehr als sie kostet. Gemessen: eine NULL ROW, zwei bis vier und alle NBCROW, keine NULL in den ersten acht
     * Spalten ROW. Wächter: ein Vergleich `>=` statt `>` (Zeile mit einer NULL), eine Bitmap von festen zwei Byte.
     *
     * @spec SPEC-003
     */
    @Test
    fun `bei 8 Spalten wird ab zwei NULL aus ROW eine NBCROW mit einem Byte Bitmap`() =
        assertRows(measuredBreit8, wide.take(8), wideRows(8))

    /**
     * `z_breit` mit den ersten 9 Spalten (Austausch 04): die Bitmap hat zwei Byte; zwei NULL-Werte sind gleich lang wie
     * die Bitmap und bleiben ROW, drei sind kürzer. Wächter: eine Bitmap, die bei 9 Spalten ein Byte rechnet.
     *
     * @spec SPEC-003
     */
    @Test
    fun `bei 9 Spalten bleiben zwei NULL ROW und erst drei werden NBCROW mit zwei Byte Bitmap`() =
        assertRows(measuredBreit9, wide.take(9), wideRows(9))

    /**
     * `z_breit` mit zehn Spalten (Austausch 05): die NULL steht erst in `C10` (zehnte Spalte, zweites Bitmap-Byte) oder
     * in `C9` und `C10`; beide Zeilen sind ROW, drei NULL in den ersten Spalten NBCROW (`d2 07 00`), alle `d2 ff 03`.
     * Wächter: eine Bitmap, deren zweites Byte nicht geschrieben wird (`d2 ff`), ein Bit in falscher Lage.
     *
     * @spec SPEC-003
     */
    @Test
    fun `bei 10 Spalten steht die NULL hinter der achten in einem zweiten Bitmap-Byte`() =
        assertRows(measuredBreit10, wide.take(10), wideRows(10))

    /**
     * `z_breit` mit 16 Spalten (Austausch 06): zwei Byte Bitmap, alle NULL `d2 ff ff`. Wächter: eine Bitmap, die bei
     * genau 16 Spalten ein drittes Byte schreibt.
     *
     * @spec SPEC-003
     */
    @Test
    fun `bei 16 Spalten hat die Bitmap zwei Byte`() = assertRows(measuredBreit16, wide.take(16), wideRows(16))

    /**
     * `z_breit` mit 17 Spalten (Austausch 07): drei Byte Bitmap; drei NULL-Werte sind gleich lang wie die Bitmap und
     * bleiben ROW, vier sind kürzer; alle NULL `d2 ff ff 01`. Wächter: eine Bitmap, die bei 17 Spalten zwei Byte
     * rechnet.
     *
     * @spec SPEC-003
     */
    @Test
    fun `bei 17 Spalten hat die Bitmap drei Byte und vier NULL werden NBCROW`() =
        assertRows(measuredBreit17, wide.take(17), wideRows(17))

    // ---- Größe der NULL im ROW hängt vom Typ ab
    // -----------------------------------------------------------------------

    /**
     * `SELECT V1, V2, I1 … I8 FROM z_gemischt ORDER BY Id` (Austausch 08; zehn Spalten, zwei Byte Bitmap): NULL in
     * einer `NVARCHAR`-Spalte allein (zwei Byte im ROW) ist gleich lang wie die Bitmap und bleibt ROW (`d1 ffff …`);
     * zwei `NVARCHAR`-NULL, `NVARCHAR` und `INTN` und drei `INTN`-NULL sind NBCROW, zwei `INTN`-NULL ROW. Wächter: eine
     * Regel „NBCROW bei jeder NULL in einer Textspalte“ (zweite Zeile), eine „NBCROW ab der zweiten NULL“ (letzte
     * Zeile).
     *
     * @spec SPEC-003
     */
    @Test
    fun `die Größe einer NULL im ROW zählt, nicht ihre Zahl oder die Art der Spalte`() {
        val columns = listOf(column("V1", text, maxLength = 20), column("V2", text, maxLength = 20)) +
            (1..8).map { column("I$it", SqlType.Int32) }
        val ints = (1..8).toList()
        val rows = listOf(
            listOf<Any?>("a", "b") + ints,
            listOf<Any?>(null, "b") + ints,
            listOf<Any?>(null, null) + ints,
            listOf<Any?>(null, "b", null) + ints.drop(1),
            listOf<Any?>("a", "b", null, null, null) + ints.drop(3),
            listOf<Any?>("a", "b", null, null) + ints.drop(2),
        )
        assertRows(measuredGemischt, columns, rows)
    }

    /**
     * `SELECT A, B, C FROM z_fest ORDER BY A` (Austausch 09): keine nullbare Spalte, also keine NULL und immer ROW, die
     * festen Typen ohne Längenbyte. Wächter: eine Wahl, die ohne NULL NBCROW schreibt.
     *
     * @spec SPEC-003
     */
    @Test
    fun `ohne nullbare Spalte ist jede Zeile ein ROW`() {
        val columns = listOf(
            column("A", SqlType.Int32, nullable = false),
            column("B", SqlType.Int64, nullable = false),
            column("C", text, nullable = false, maxLength = 10),
        )
        assertRows(measuredFest, columns, listOf(listOf(1, 100L, "eins"), listOf(2, 200L, "zwei")))
    }

    /**
     * `SELECT T, S, I, G, F, D, Tm, Z FROM z_typ2 ORDER BY Id` (Austausch 11): die acht Typen des Typmodells in einer
     * nullbaren Spalte (`tinyint`, `smallint`, `int`, `bigint`, `bit`, `date`, `time`, `datetime2`), acht Spalten (ein
     * Byte Bitmap). Gemessen: eine NULL in jeder der acht Spalten ist ROW (ein NULL-Byte, ein Byte Bitmap), zwei NULL
     * (`F`, `D`) und alle NULL sind NBCROW. Wächter: ein Typ, dessen NULL im ROW mit mehr als einem Byte gerechnet wird
     * (`d2 …` statt `d1 …`), eine Zählung nach Spalten statt nach Bytes.
     *
     * @spec SPEC-003
     */
    @Test
    fun `eine NULL in jedem der acht Typen des Typmodells bleibt ROW, zwei und alle werden NBCROW`() {
        val columns = listOf(
            column("T", SqlType.UInt8),
            column("S", SqlType.Int16),
            column("I", SqlType.Int32),
            column("G", SqlType.Int64),
            column("F", SqlType.Bool),
            column("D", SqlType.Date),
            column("Tm", SqlType.Time),
            column("Z", SqlType.DateTime),
        )
        val base = listOf<Any?>(
            1,
            2,
            3,
            4L,
            true,
            LocalDate.of(2026, 1, 2),
            LocalTime.of(13, 14, 15),
            LocalDateTime.of(2026, 1, 2, 13, 14, 15),
        )

        fun withNull(at: Set<Int>) = base.mapIndexed { index, value -> if (index in at) null else value }
        val rows = listOf(base) + (0..7).map { withNull(setOf(it)) } +
            listOf(withNull(setOf(4, 5)), withNull((0..7).toSet()))
        assertRows(measuredTypen, columns, rows)
    }

    /**
     * `z_typ` (Austausch 10) misst `decimal(10,2)` und `datetime2` mit einer NULL: ROW (ein NULL-Byte, ein Byte
     * Bitmap). Hier ist die Zeilenform der Spalten `Flag`, `Preis`, `Zeitpunkt` ohne die Bytes des Dezimalwerts geprüft
     * (die Länge der Dezimalzahl ist A-11); eine NULL ist ROW, zwei sind NBCROW. Anwendung der gemessenen Regel auf den
     * Typ `decimal` des Typmodells, ohne Messung der Tabelle mit nur diesen drei Spalten (Festlegung).
     *
     * @spec SPEC-003
     */
    @Test
    fun `eine NULL in einer Dezimalspalte ist ROW, zwei NULL sind NBCROW`() {
        val columns = listOf(
            column("Flag", SqlType.Bool),
            column("Preis", SqlType.Decimal(10, 2)),
            column("Zeitpunkt", SqlType.DateTime),
        )
        val price = BigDecimal("12.34")
        val moment = LocalDateTime.of(2026, 1, 2, 13, 14, 15)
        val (outcome, actual) = answer(
            columns,
            listOf(listOf(true, price, moment), listOf(true, null, moment), listOf(null, null, moment)),
        )
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals(listOf("d1", "d1", "d2"), actual.map { it.take(2) })
    }

    // ---- Zufällige, überlange und zerstückelte Eingaben (SPEC-027)
    // -------------------------------------------------------

    private fun randomColumns(random: Random, count: Int) = (0 until count).map {
        when (random.nextInt(5)) {
            0 -> column("c$it", text, random.nextBoolean(), TEXT_LENGTH)
            1 -> column("c$it", SqlType.Int64, random.nextBoolean())
            2 -> column("c$it", SqlType.Bool, random.nextBoolean())
            3 -> column("c$it", SqlType.Date, random.nextBoolean())
            else -> column("c$it", SqlType.Int32, random.nextBoolean())
        }
    }

    private fun randomRow(random: Random, columns: List<ResultColumn>, nullShare: Int): List<Any?> = columns.map {
        when {
            it.nullable && random.nextInt(PERCENT) < nullShare -> null
            it.type == SqlType.Int64 -> random.nextLong()
            it.type == SqlType.Bool -> random.nextBoolean()
            it.type == SqlType.Date -> LocalDate.ofEpochDay(random.nextInt(EPOCH_DAYS).toLong())
            it.type == SqlType.Int32 -> random.nextInt()
            else -> "t" + random.nextInt(PERCENT)
        }
    }

    /**
     * Zufällige Ergebnisse mit 0 bis 40 Spalten, 0 bis 100 Prozent NULL und 0 bis 30 Zeilen (fester Startwert, 400
     * Läufe): jeder Lauf endet mit `COMPLETED`; jede Zeile ist höchstens so lang wie die andere Form und NBCROW nur,
     * wenn sie kürzer ist; der Strom aus COLMETADATA und Zeilen lässt sich mit dem Decoder der Tests auf dieselben
     * Tokens zurücklesen. Selbstkonsistenz von Wahl und Encoder, keine Messung: die Längen bildet der Test selbst mit
     * dem Encoder.
     *
     * @spec SPEC-003
     * @spec SPEC-027
     */
    @Test
    fun `zufällige Zeilen enden in einem Ergebnis, die Wahl ist die kürzere Form und der Strom ist lesbar`() {
        val random = Random(SEED)
        repeat(RUNS) { run ->
            val columns = randomColumns(random, random.nextInt(MAX_RANDOM_COLUMNS + 1))
            val share = random.nextInt(PERCENT + 1)
            val rows = List(random.nextInt(MAX_RANDOM_ROWS + 1)) { randomRow(random, columns, share) }
            val (outcome, tokens) = answerTokens(columns, rows)
            assertEquals(SequenceOutcome.COMPLETED, outcome, "Lauf $run")
            assertEquals(rows.size, tokens.size, "Lauf $run")
            tokens.forEachIndexed { index, token -> assertChosenIsShorter(columns, token, "Lauf $run Zeile $index") }
            assertEquals(tokens, decoded(columns, tokens), "Lauf $run")
        }
    }

    private fun assertChosenIsShorter(columns: List<ResultColumn>, token: Token, where: String) {
        val values = if (token is RowToken) token.values else (token as NullBitmapRowToken).values
        val other = if (token is RowToken) NullBitmapRowToken(values) else RowToken(values)
        val chosen = size(columns, token)
        val alternative = size(columns, other)
        assertTrue(chosen <= alternative, "$where: gewählt $chosen, andere Form $alternative")
        assertTrue(token is RowToken || chosen < alternative, "$where: NBCROW bei Gleichstand")
    }

    private fun decoded(columns: List<ResultColumn>, tokens: List<Token>): List<Token> {
        val encoder = TokenEncoder()
        val defs = assertIs<ProtocolResult.Ok<List<ColumnDef>>>(ColumnMapping.map(columns)).value
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(defs)))
        tokens.forEach { assertIs<ProtocolResult.Ok<Unit>>(encoder.write(it)) }
        return TokenDecoder(encoder.toByteArray()).decodeAll().filter { it is RowToken || it is NullBitmapRowToken }
    }

    /**
     * Überlange Eingaben: 3000 Spalten (375 Byte Bitmap) und 20000 Zeilen mit einer Spalte; beide enden mit
     * `COMPLETED`, und die Zeile aus 3000 NULL-Werten ist eine NBCROW aus 375 Bytes `ff`. Eine Zeile mit zu wenigen
     * oder zu vielen Werten ergibt den Fehler der Typabbildung (`FAILED`), kein Zeilentoken und keine Ausnahme.
     *
     * @spec SPEC-003
     * @spec SPEC-027
     */
    @Test
    fun `überlange Zeilen und Ergebnisse enden in einem Ergebnis, eine Zeile falscher Länge in einem Fehler`() {
        val columns = (0 until WIDE_COLUMNS).map { column("c$it", SqlType.Int32) }
        val (outcome, rows) = answer(columns, listOf(List(WIDE_COLUMNS) { null }))
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals("d2" + "ff".repeat(WIDE_COLUMNS / BITS_PER_BYTE), rows.single())

        val (manyOutcome, many) = answerTokens(listOf(column("n", SqlType.Int32)), List(MANY_ROWS) { listOf(it) })
        assertEquals(SequenceOutcome.COMPLETED, manyOutcome)
        assertEquals(MANY_ROWS, many.size)

        listOf(listOf(), listOf(1, 2)).forEach { bad ->
            val (failed, tokens) = answerTokens(listOf(column("n", SqlType.Int32)), listOf(bad))
            assertEquals(SequenceOutcome.FAILED, failed)
            assertTrue(tokens.isEmpty())
        }
    }

    private fun answerTokens(columns: List<ResultColumn>, rows: List<List<Any?>>): Pair<SequenceOutcome, List<Token>> {
        val events = listOf<QueryEvent>(QueryEvent.ResultStart(columns)) + rows.map { QueryEvent.Row(it) } +
            QueryEvent.ResultEnd(rows.size.toLong())
        val sink = RecordingSink()
        val request = QueryRequest("s", "r", "SELECT 1", emptyList(), null)
        val outcome = runBlocking {
            ResponseSequencer(FIXED_MAPPING).run(ScriptedBackend.ofList(*events.toTypedArray()), request, sink)
        }
        return outcome to sink.tokens.filter { it is RowToken || it is NullBitmapRowToken }
    }

    /** Die Länge der Bytes von [token] mit dem Encoder, der die Spalten kennt. */
    private fun size(columns: List<ResultColumn>, token: Token): Int {
        val defs = assertIs<ProtocolResult.Ok<List<ColumnDef>>>(ColumnMapping.map(columns)).value
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(defs)))
        encoder.drain()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(token))
        return encoder.drain().size
    }
}
