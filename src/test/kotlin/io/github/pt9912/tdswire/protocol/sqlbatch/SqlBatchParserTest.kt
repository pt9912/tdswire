package io.github.pt9912.tdswire.protocol.sqlbatch

import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.hex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SqlBatchParserTest {
    /**
     * ALL_HEADERS nach MS-TDS 2.2.5.3: Gesamtlänge 22 (`16 00 00 00`), ein Header von 18 Byte
     * (`12 00 00 00`) vom Typ 2 (Transaction Descriptor, `02 00`) mit 8 Byte Descriptor und 4 Byte
     * Request Count, danach der Text "select 1" als UTF-16LE.
     */
    private val allHeaders = "16 00 00 00  12 00 00 00  02 00  00 00 00 00 00 00 00 00  01 00 00 00 "
    private val selectOne = hex(allHeaders + "73 00 65 00 6C 00 65 00 63 00 74 00 20 00 31 00")

    private fun parse(bytes: ByteArray): ProtocolResult<SqlBatchRequest> = SqlBatchParser.parse(bytes)

    private fun failureOf(bytes: ByteArray): ProtocolFailure =
        assertIs<ProtocolResult.Failed>(parse(bytes)).error.failure

    /**
     * Orakel: der Beispielvektor liefert den Text und die Kennung des Headers.
     *
     * @spec SPEC-028
     */
    @Test
    fun `SQL_BATCH nach MS-TDS liefert Text und Headerkennungen`() {
        val request = assertIs<ProtocolResult.Ok<SqlBatchRequest>>(parse(selectOne)).value
        assertEquals("select 1", request.text)
        assertEquals(listOf(2), request.headerTypes)
    }

    /**
     * Grenze: ein ALL_HEADERS-Block ohne Header (Länge 4) und ein leerer Text sind zulässig;
     * mehrere Header werden der Reihe nach gelesen; Unicode bleibt erhalten.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Leerer Block, leerer Text und mehrere Header`() {
        val empty = assertIs<ProtocolResult.Ok<SqlBatchRequest>>(parse(hex("04 00 00 00"))).value
        assertEquals("", empty.text)
        assertEquals(emptyList(), empty.headerTypes)
        val two = hex("10 00 00 00  06 00 00 00 01 00  06 00 00 00 03 00  AC 20 3D D8 00 DE")
        val request = assertIs<ProtocolResult.Ok<SqlBatchRequest>>(parse(two)).value
        assertEquals(listOf(1, 3), request.headerTypes)
        assertEquals("€😀", request.text)
    }

    /**
     * Negativfall: Gesamtlänge unter 4 oder über der Nachricht, Header unter 6 Byte, Header über die
     * Gesamtlänge hinaus und abgeschnittene Nachrichten sind Ergebniswerte.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Fehlerhafter ALL_HEADERS-Block ist ein Ergebniswert`() {
        assertEquals(ProtocolFailure.TRUNCATED, failureOf(ByteArray(0)))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf(hex("16 00 00")))
        assertEquals(ProtocolFailure.INVALID_HEADER_BLOCK, failureOf(hex("03 00 00 00")))
        assertEquals(ProtocolFailure.INVALID_HEADER_BLOCK, failureOf(hex("05 00 00 00")))
        assertEquals(ProtocolFailure.INVALID_HEADER_BLOCK, failureOf(hex("0A 00 00 00  05 00 00 00 01 00")))
        assertEquals(ProtocolFailure.INVALID_HEADER_BLOCK, failureOf(hex("0A 00 00 00  07 00 00 00 01 00")))
        assertEquals(ProtocolFailure.INVALID_HEADER_BLOCK, failureOf(selectOne.copyOf(21)))
        assertEquals(ProtocolFailure.INVALID_HEADER_BLOCK, failureOf(hex("FF FF FF FF")))
    }

    /**
     * Negativfall: ein Text mit ungerader Byte-Zahl ist kein UTF-16.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Text mit ungerader Laenge ist ein Fehler`() {
        assertEquals(ProtocolFailure.ODD_TEXT_LENGTH, failureOf(hex("04 00 00 00 73 00 65")))
    }

    /**
     * Die Textdarstellung nennt Längen, nie den Text (er kann Literale und Kennwörter tragen).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Textdarstellung enthaelt den Text nicht`() {
        val request = assertIs<ProtocolResult.Ok<SqlBatchRequest>>(parse(selectOne)).value
        assertFalse(request.toString().contains("select"))
    }

    /**
     * Fuzz mit festem Startwert (`SPEC-027`): zufällige und veränderte Eingaben enden in einem Ergebniswert.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zufaellige und veraenderte Eingaben enden in einem Ergebniswert`() {
        val random = Random(SEED)
        repeat(RUNS) {
            parse(random.nextBytes(random.nextInt(0, 80)))
            val mutated = selectOne.copyOf()
            repeat(random.nextInt(1, 4)) { mutated[random.nextInt(mutated.size)] = random.nextInt().toByte() }
            parse(mutated)
            parse(selectOne.copyOf(random.nextInt(0, selectOne.size)))
        }
    }

    private companion object {
        const val SEED = 20261030
        const val RUNS = 4000
    }
}
