package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Selbstkonsistenz von Encoder und Test-Decoder. Die Protokolltreue belegen die Handvektoren in
 * [TokenEncoderTest]; ein Roundtrip allein würde einen Fehler in beiden Richtungen nicht sehen.
 */
class TokenRoundtripTest {
    private val latin = Collation.LATIN1_GENERAL_CI_AS

    private fun normalized(token: Token): Token = when (token) {
        is RowToken -> RowToken(token.values.map(::normalized))
        is ReturnValueToken -> token.copy(value = normalized(token.value))
        else -> token
    }

    private fun normalized(value: TdsValue): TdsValue =
        if (value is TdsValue.DecimalValue) TdsValue.DecimalValue(value.value.stripTrailingZeros()) else value

    private fun roundtrip(tokens: List<Token>) {
        val encoder = TokenEncoder()
        tokens.forEach { assertIs<ProtocolResult.Ok<Unit>>(encoder.write(it), "$it") }
        val decoded = TokenDecoder(encoder.toByteArray()).decodeAll()
        assertEquals(tokens.map(::normalized), decoded.map(::normalized))
    }

    /**
     * Die Login-Antwort aus LOGINACK, ENVCHANGE (Datenbank, Packet Size, Kollation), INFO und DONE
     * läuft durch Encoder und Decoder.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Login-Antwort laeuft hin und zurueck`() {
        roundtrip(
            listOf(
                EnvChange.Database("master", ""),
                InfoToken(ServerMessage(5701, 2, 0, "Changed database context to 'master'.", "srv", "", 1)),
                EnvChange.SqlCollation(latin, null),
                EnvChange.PacketSize(8000, 4096),
                LoginAck(1, 0x74000004, "tdswire", 0x01000000),
                DoneToken(DoneKind.DONE, DoneStatus.FINAL, 0, 0),
            ),
        )
    }

    /**
     * Ein Resultset mit allen Typen des Mindestkatalogs, NULL, Grenzwerten und Unicode, danach
     * RETURNVALUE, RETURNSTATUS und die DONE-Familie.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Resultset aus allen Typen laeuft hin und zurueck`() {
        val columns = listOf(
            ColumnDef("i4", TdsType.Int4Type),
            ColumnDef("i8", TdsType.Int8Type),
            ColumnDef("b", TdsType.BitType),
            ColumnDef("n1", TdsType.IntNType(1), nullable = true),
            ColumnDef("n2", TdsType.IntNType(2), nullable = true),
            ColumnDef("n4", TdsType.IntNType(4), nullable = true),
            ColumnDef("n8", TdsType.IntNType(8), nullable = true),
            ColumnDef("bn", TdsType.BitNType, nullable = true),
            ColumnDef("s", TdsType.NVarcharType(5, latin), nullable = true),
            ColumnDef("d", TdsType.DecimalType(38, 10), nullable = true),
            ColumnDef("dt", TdsType.DateType, nullable = true),
            ColumnDef("t", TdsType.TimeType(7), nullable = true),
            ColumnDef("dt2", TdsType.DateTime2Type(3), nullable = true),
        )
        val nulls = List(columns.size - FIXED_COLUMNS) { TdsValue.NullValue }
        val fixed = listOf(
            TdsValue.IntegerValue(Int.MIN_VALUE.toLong()),
            TdsValue.IntegerValue(Long.MAX_VALUE),
            TdsValue.BitValue(true),
        )
        val full = fixed + listOf(
            TdsValue.IntegerValue(255),
            TdsValue.IntegerValue(-32768),
            TdsValue.IntegerValue(Int.MAX_VALUE.toLong()),
            TdsValue.IntegerValue(Long.MIN_VALUE),
            TdsValue.BitValue(false),
            TdsValue.TextValue("€a😀"),
            TdsValue.DecimalValue(BigDecimal("-1234567890123456789012345678.1234567890")),
            TdsValue.DateValue(LocalDate.of(9999, 12, 31)),
            TdsValue.TimeValue(LocalTime.of(23, 59, 59, 999_999_900)),
            TdsValue.DateTime2Value(LocalDateTime.of(1, 1, 1, 0, 0, 0, 1_000_000)),
        )
        val output = ColumnDef("", TdsType.IntNType(4), nullable = true)
        roundtrip(
            listOf(
                ColMetadataToken(columns),
                RowToken(full),
                RowToken(fixed + nulls),
                DoneToken(DoneKind.DONE_IN_PROC, DoneStatus.MORE or DoneStatus.COUNT, 193, 2),
                ReturnValueToken(0, "@o", 1, output, TdsValue.IntegerValue(7)),
                ReturnStatusToken(0),
                DoneToken(DoneKind.DONE_PROC, DoneStatus.FINAL, 0, 0),
            ),
        )
    }

    /**
     * Fuzz mit festem Startwert (`SPEC-027`): zufällige Typen (auch ungültige), Werte (auch
     * unpassende) und Zeilen; jeder Auftrag endet in einem Ergebniswert, und was der Encoder
     * schreibt, liest der Decoder gleich zurück.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zufaellige Tokens enden in Ergebniswerten und lesen sich zurueck`() {
        val random = Random(SEED)
        val encoder = TokenEncoder()
        val written = mutableListOf<Token>()
        var columns: List<ColumnDef>? = null
        repeat(RUNS) {
            val current = columns
            val token: Token = if (current == null || random.nextInt(ROW_ONE_IN) == 0) {
                ColMetadataToken(List(random.nextInt(0, 5)) { randomColumn(random) })
            } else {
                RowToken(randomRow(random, current))
            }
            if (encoder.write(token) is ProtocolResult.Ok) {
                written += token
                if (token is ColMetadataToken) columns = token.columns
            }
        }
        assertEquals(written.map(::normalized), TokenDecoder(encoder.toByteArray()).decodeAll().map(::normalized))
        val rows = written.count { it is RowToken }
        check(rows > MIN_ROWS) { "nur $rows gueltige Zeilen, der Erzeuger deckt zu wenig ab" }
    }

    private fun randomColumn(random: Random): ColumnDef {
        val type = randomType(random)
        return ColumnDef("c${random.nextInt(0, 9)}", type, nullable = random.nextInt(4) != 0)
    }

    private fun randomType(random: Random): TdsType = when (random.nextInt(0, 10)) {
        0 -> TdsType.Int4Type
        1 -> TdsType.Int8Type
        2 -> TdsType.BitType
        3 -> TdsType.IntNType(random.nextInt(0, 10))
        4 -> TdsType.BitNType
        5 -> TdsType.NVarcharType(random.nextInt(-1, 4100), latin)
        6 -> TdsType.DecimalType(random.nextInt(-1, 41), random.nextInt(-1, 41), random.nextBoolean())
        7 -> TdsType.DateType
        8 -> TdsType.TimeType(random.nextInt(-1, 10))
        else -> TdsType.DateTime2Type(random.nextInt(-1, 10))
    }

    private fun randomRow(random: Random, columns: List<ColumnDef>): List<TdsValue> {
        val count = if (random.nextInt(ROW_ONE_IN) == 0) random.nextInt(0, columns.size + 2) else columns.size
        return List(count) { index -> randomValue(random, columns.getOrNull(index)?.type) }
    }

    private fun randomValue(random: Random, type: TdsType?): TdsValue = when {
        random.nextInt(ROW_ONE_IN) == 0 -> TdsValue.NullValue
        random.nextInt(WRONG_ONE_IN) == 0 -> TdsValue.BitValue(random.nextBoolean())
        else -> matchingValue(random, type)
    }

    private fun matchingValue(random: Random, type: TdsType?): TdsValue = when (type) {
        TdsType.Int4Type, TdsType.Int8Type, is TdsType.IntNType -> TdsValue.IntegerValue(randomInteger(random))
        TdsType.BitType, TdsType.BitNType -> TdsValue.BitValue(random.nextBoolean())
        is TdsType.NVarcharType -> TdsValue.TextValue(randomText(random, type.maxChars))
        is TdsType.DecimalType -> TdsValue.DecimalValue(randomDecimal(random, type))
        TdsType.DateType -> TdsValue.DateValue(randomDate(random))
        is TdsType.TimeType -> TdsValue.TimeValue(randomTime(random))
        is TdsType.DateTime2Type -> TdsValue.DateTime2Value(LocalDateTime.of(randomDate(random), randomTime(random)))
        null -> TdsValue.NullValue
    }

    private fun randomInteger(random: Random): Long = when (random.nextInt(3)) {
        0 -> random.nextLong()
        1 -> random.nextInt(-300, 300).toLong()
        else -> random.nextInt().toLong()
    }

    private fun randomText(random: Random, maxChars: Int): String {
        val length = random.nextInt(0, maxOf(1, minOf(maxChars, 12)) + 2)
        return String(CharArray(length) { random.nextInt(0x20, 0xFFFF).toChar() })
    }

    private fun randomDecimal(random: Random, type: TdsType.DecimalType): BigDecimal {
        val unscaled = BigInteger(random.nextInt(1, 130), java.util.Random(random.nextLong()))
        return BigDecimal(unscaled, random.nextInt(0, type.scale + 2))
    }

    private fun randomDate(random: Random): LocalDate = LocalDate.ofEpochDay(random.nextLong(-730_000L, 2_950_000L))

    private fun randomTime(random: Random): LocalTime {
        val nanos = random.nextLong(0, 86_400_000_000_000L)
        return LocalTime.ofNanoOfDay(if (random.nextBoolean()) nanos - nanos % 1_000_000_000L else nanos)
    }

    private companion object {
        const val SEED = 20261030
        const val RUNS = 6000
        const val ROW_ONE_IN = 8
        const val WRONG_ONE_IN = 12
        const val MIN_ROWS = 500
        const val FIXED_COLUMNS = 3
    }
}
