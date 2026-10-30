package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Zufalls- und Grenzeingaben mit festem Startwert. Das Urteil „abbildbar“ fällt ein Orakel, das die
 * Abbildung nicht benutzt (CharsetEncoder für Unicode, `setScale` und Vergleich für Decimal); wo die
 * Abbildung annimmt, muss der Encoder dieselbe Zeile schreiben und der Test-Decoder sie wiedergeben.
 */
class MappingFuzzTest {
    private val random = Random(SEED)
    private val cleanPool = listOf("a", "\u00E9", "\uD83D\uDE00", " ")
    private val dirtyPool = cleanPool + listOf("\u0000", "\uD83D", "\uDE00")

    private fun randomType(): SqlType = when (random.nextInt(8)) {
        0 -> SqlType.Int32
        1 -> SqlType.Int64
        2 -> SqlType.Bool
        3 -> SqlType.UnicodeText
        4 -> random.nextInt(1, 39).let { SqlType.Decimal(it, random.nextInt(it + 1)) }
        5 -> SqlType.Date
        6 -> SqlType.Time
        else -> SqlType.DateTime
    }

    private fun randomText(): String {
        val length = if (random.nextBoolean()) random.nextInt(6) else random.nextInt(3998, 4003)
        val pool = if (random.nextInt(4) == 0) dirtyPool else cleanPool
        val builder = StringBuilder()
        while (builder.length < length) builder.append(pool.random(random))
        return builder.toString().take(length)
    }

    private fun randomDecimal(): BigDecimal =
        BigDecimal(BigInteger(random.nextInt(1, 140), random.asJavaRandom()), random.nextInt(-5, 45))
            .let { if (random.nextBoolean()) it.negate() else it }

    private fun randomDate(): LocalDate = LocalDate.ofEpochDay(random.nextLong(-800_000L, 3_000_000L))

    private fun randomTime(): LocalTime =
        LocalTime.ofNanoOfDay(random.nextLong(86_400_000_000_000L) / random.nextInt(1, 3) / 50 * 50)

    private fun randomValue(): Any? = when (random.nextInt(14)) {
        0 -> null
        1 -> random.nextInt()
        2 -> random.nextLong()
        3 -> random.nextBoolean()
        in 4..5 -> randomText()
        in 6..7 -> randomDecimal()
        8 -> randomDate()
        9 -> randomTime()
        10 -> LocalDateTime.of(randomDate(), randomTime())
        11 -> listOf(Int.MIN_VALUE, Int.MAX_VALUE, 0).random(random)
        12 -> listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L).random(random)
        else -> Any()
    }

    private val utf16 = StandardCharsets.UTF_16LE.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    /** Das Urteil des Orakels, ohne die Abbildung zu benutzen. */
    private fun accepts(column: ResultColumn, value: Any?): Boolean =
        if (value == null) column.nullable else acceptsValue(column.type, value)

    private fun acceptsValue(type: SqlType, value: Any): Boolean = when (type) {
        SqlType.Int32 -> value is Int
        SqlType.Int64 -> value is Long
        SqlType.Bool -> value is Boolean
        SqlType.UnicodeText -> value is String && acceptsText(value)
        is SqlType.Decimal -> value is BigDecimal && fitsDecimal(type, value)
        SqlType.Date, SqlType.Time, SqlType.DateTime -> acceptsTemporal(type, value)
    }

    private fun acceptsTemporal(type: SqlType, value: Any): Boolean = when (type) {
        SqlType.Date -> value is LocalDate && value.year in 1..9999
        SqlType.Time -> value is LocalTime && onTick(value)
        else -> value is LocalDateTime && value.year in 1..9999 && onTick(value.toLocalTime())
    }

    private fun acceptsText(value: String): Boolean {
        val encodable = synchronized(utf16) { utf16.canEncode(value) }
        return value.length <= 4000 && encodable
    }

    private fun onTick(time: LocalTime): Boolean = time.toNanoOfDay() % 100 == 0L

    private fun fitsDecimal(type: SqlType.Decimal, value: BigDecimal): Boolean {
        val exact = value.setScale(type.scale, RoundingMode.DOWN).compareTo(value) == 0
        val limit = BigDecimal.TEN.pow(type.precision - type.scale)
        return exact && value.abs() < limit
    }

    private fun normalized(value: TdsValue): TdsValue =
        if (value is TdsValue.DecimalValue) TdsValue.DecimalValue(value.value.stripTrailingZeros()) else value

    /**
     * 30000 Zeilen aus zufälligen Spaltenarten und Werten, darunter Grenzwerte, unpaare Surrogate,
     * Decimal über der Precision, Zeit feiner als 100 ns, falsche Klassen und NULL: die Abbildung wirft
     * nie, endet in `Ok` oder dem Fehlerwert, stimmt mit dem Orakel überein, und was sie annimmt,
     * schreibt der Encoder und liest der Test-Decoder zurück.
     *
     * @spec SPEC-003
     * @spec SPEC-014
     */
    @Test
    fun `Zufallseingaben enden in Ok oder dem Fehlerwert und stimmen mit dem Orakel ueberein`() {
        var accepted = 0
        repeat(ITERATIONS) { round ->
            val column = ResultColumn("c", randomType(), random.nextInt(3) != 0)
            val value = randomValue()
            val defs = assertIs<ProtocolResult.Ok<List<ColumnDef>>>(ColumnMapping.map(listOf(column))).value
            val result = ValueMapping.row(defs, listOf(value))
            val expected = accepts(column, value)
            val message = "Runde $round, ${column.type}, nullable=${column.nullable}"
            if (expected) {
                val row = assertIs<ProtocolResult.Ok<List<TdsValue>>>(result, message).value
                assertRoundtrip(defs, row, message)
                accepted++
            } else {
                MappingSupport.error(result)
            }
        }
        assertTrue(accepted in ITERATIONS / 10..ITERATIONS * 9 / 10, "angenommen: $accepted")
    }

    private fun assertRoundtrip(defs: List<ColumnDef>, row: List<TdsValue>, message: String) {
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(defs)), message)
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(RowToken(row)), message)
        val decoded: List<Token> = TokenDecoder(encoder.toByteArray()).decodeAll()
        val back = assertIs<RowToken>(decoded.last(), message)
        assertEquals(row.map(::normalized), back.values.map(::normalized), message)
    }

    /**
     * Zufällige Spaltenlisten mit Precision bis 60: die Abbildung wirft nie; sie nimmt die Liste genau
     * dann an, wenn keine Precision über 38 vorkommt, und was sie annimmt, schreibt der Encoder.
     *
     * @spec SPEC-031
     * @spec SPEC-014
     */
    @Test
    fun `Zufaellige Spaltenlisten werden ganz angenommen oder ganz abgewiesen`() {
        repeat(ITERATIONS) { round ->
            val columns = List(random.nextInt(0, 6)) {
                val type = if (random.nextInt(3) == 0) {
                    val precision = random.nextInt(1, 61)
                    SqlType.Decimal(precision, random.nextInt(precision + 1))
                } else {
                    randomType()
                }
                ResultColumn("c$it", type, random.nextBoolean())
            }
            val over = columns.map { it.type }.filterIsInstance<SqlType.Decimal>().any { it.precision > 38 }
            val result = ColumnMapping.map(columns)
            if (over) {
                MappingSupport.error(result)
            } else {
                val defs = assertIs<ProtocolResult.Ok<List<ColumnDef>>>(result, "Runde $round").value
                assertIs<ProtocolResult.Ok<Unit>>(TokenEncoder().write(ColMetadataToken(defs)), "Runde $round")
            }
        }
    }

    private companion object {
        const val SEED = 20_261_030
        const val ITERATIONS = 30_000
    }
}
