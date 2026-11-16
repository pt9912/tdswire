package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * PLP (MAX-Typen) als RPC-Parameter. Herkunft der Belege je Test: *Handvektor* ist eine Lesart von MS-TDS ohne
 * Messung eines Clients; *gemessen (Layout)* heißt, dass die Bytes des PLP-Körpers ein echter SQL Server in der
 * Antwortrichtung geschrieben hat (`referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/2022/
 * katalog-abfragen.messung`, Zeile 64; ein Client sendet in dieser Richtung nicht gemessen); *Festlegung* ist eine
 * Setzung dieses Parsers. Welche Typen ein Referenzclient in den Szenarien als MAX sendet, ist nicht gemessen
 * (`SPEC-003`: `nvarchar(max)`, `varchar(max)`, `varbinary(max)` und `xml` tragen Länge `0xFFFF`).
 */
class RpcPlpTest {
    private val coll = RpcVectors.COLLATION
    private val head = RpcVectors.HEADERS
    private val latin = Collation(info = 0x00D00409, sortId = 0x34)

    private fun le(value: Long, count: Int): String = ByteArray(count) { (value shr (8 * it)).toByte() }.toHex()

    private fun utf16(text: String): String = text.toByteArray(Charsets.UTF_16LE).toHex()

    /** PLP-Körper: Gesamtlänge (ohne Angabe die Summe der Stücke), je Stück 4 Byte Länge und Daten, Terminator. */
    private fun plp(vararg chunks: String, total: Long? = null): String {
        val sizes = chunks.map { it.filter { c -> !c.isWhitespace() }.length / 2 }
        val chunkText = chunks.indices.joinToString(" ") { "${le(sizes[it].toLong(), 4)} ${chunks[it]}" }
        return "${le(total ?: sizes.sum().toLong(), 8)} $chunkText 00 00 00 00"
    }

    private fun nvarcharMax(text: String, chunkChars: Int = 0): String {
        val size = if (chunkChars == 0) maxOf(1, text.length) else chunkChars
        return "E7 FF FF $coll " + plp(*text.chunked(size).map(::utf16).toTypedArray())
    }

    private fun byName(typeAndValue: String): ProtocolResult<RpcRequest> =
        RpcParser.parse(RpcVectors.byName(typeAndValue))

    private fun parameterOf(typeAndValue: String): RpcParameter {
        val call = assertIs<RpcCall.ByName>(assertIs<ProtocolResult.Ok<RpcRequest>>(byName(typeAndValue)).value.call)
        return call.parameters.single()
    }

    private fun failureOf(typeAndValue: String): ProtocolFailure =
        assertIs<ProtocolResult.Failed>(byName(typeAndValue)).error.failure

    /**
     * Handvektor: `NVARCHAR` mit Länge `FF FF` ist `plp`, Länge `0xFFFF`, mit Kollation; `BIGVARCHAR` und
     * `BIGVARBINARY` ebenso. Festlegung: `NCHAR`, `BIGCHAR` und `BIGBINARY` mit `FF FF` sind kein MAX-Typ von
     * `SPEC-003`; sie bleiben `UNSUPPORTED_PLP` (Kategorie `TDS_UNSUPPORTED_TYPE`).
     *
     * @spec SPEC-003
     */
    @Test
    fun `TYPE_INFO der MAX-Typen ist PLP`() {
        val nvarchar = parameterOf("E7 FF FF $coll ${plp()}")
        assertEquals(RpcType(RpcTypeKind.NVARCHAR, length = 0xFFFF, collation = latin, plp = true), nvarchar.type)
        val varchar = parameterOf("A7 FF FF $coll ${plp()}")
        assertEquals(RpcType(RpcTypeKind.BIGVARCHAR, length = 0xFFFF, collation = latin, plp = true), varchar.type)
        val binary = parameterOf("A5 FF FF ${plp()}")
        assertEquals(RpcType(RpcTypeKind.BIGVARBINARY, length = 0xFFFF, plp = true), binary.type)
        listOf("EF FF FF $coll", "AF FF FF $coll", "AD FF FF").forEach {
            assertEquals(ProtocolFailure.UNSUPPORTED_PLP, failureOf(it), it)
        }
        assertEquals(false, parameterOf("E7 FF FE $coll 00 00").type.plp)
    }

    /**
     * Handvektor: SQL-Text und Deklarationsliste von `sp_executesql` als `nvarchar(max)` werden gelesen, auch über
     * mehrere Stücke; die Wertparameter dahinter bleiben an ihrer Stelle.
     *
     * @spec SPEC-003
     * @spec SPEC-030
     */
    @Test
    fun `sp_executesql liest SQL-Text und Deklaration als nvarchar(max)`() {
        val message = "$head FF FF 0A 00 00 00  00 00 ${nvarcharMax("select @a, 'x'", chunkChars = 5)}  " +
            "00 00 ${nvarcharMax("@a int")}  ${RpcVectors.PARAM_A_42}"
        val call = assertIs<RpcCall.ExecuteSql>(
            assertIs<ProtocolResult.Ok<RpcRequest>>(RpcParser.parse(hex(message))).value.call,
        )
        assertEquals("select @a, 'x'", call.statement)
        assertEquals("@a int", call.declarations)
        assertEquals(listOf("@a"), call.parameters.map { it.name })
    }

    /**
     * Handvektor: `sp_prepexec` mit Deklaration und Text als MAX; der Text über mehrere Stücke.
     *
     * @spec SPEC-030
     */
    @Test
    fun `sp_prepexec liest Deklaration und Text als nvarchar(max)`() {
        val message = "$head FF FF 0D 00 00 00  00 01 26 04 04 FF FF FF FF  00 00 ${nvarcharMax("@a int")}  " +
            "00 00 ${nvarcharMax("select @a", chunkChars = 3)}"
        val call = assertIs<RpcCall.PrepExec>(
            assertIs<ProtocolResult.Ok<RpcRequest>>(RpcParser.parse(hex(message))).value.call,
        )
        assertEquals("select @a", call.statement)
        assertEquals("@a int", call.declarations)
    }

    /**
     * Gemessen (Layout): der PLP-Körper des Servers für `((1))` (Gesamtlänge 10, ein Stück von 10 Byte, Terminator)
     * ist als `nvarchar(max)`-Wert lesbar. Handvektor: ein Stück, mehrere Stücke, unbekannte Gesamtlänge
     * (`FF FF FF FF FF FF FF FE`), der leere Wert, NULL (alle Bytes `FF`) und `varbinary(max)`/`varchar(max)` als
     * Bytes.
     *
     * @spec SPEC-003
     */
    @Test
    fun `PLP-Werte aus einem Stueck, mehreren Stuecken, unbekannter Laenge, leer und NULL`() {
        val measured = "0A 00 00 00 00 00 00 00 0A 00 00 00 28 00 28 00 31 00 29 00 29 00 00 00 00 00"
        assertEquals(
            "((1))",
            assertIs<RpcValue.TextValue>(parameterOf("E7 FF FF $coll $measured").value).value,
        )
        assertEquals("abc", assertIs<RpcValue.TextValue>(parameterOf(nvarcharMax("abc", 1)).value).value)
        val unknown = "FE FF FF FF FF FF FF FF 02 00 00 00 41 00 02 00 00 00 42 00 00 00 00 00"
        assertEquals("AB", assertIs<RpcValue.TextValue>(parameterOf("E7 FF FF $coll $unknown").value).value)
        assertEquals("", assertIs<RpcValue.TextValue>(parameterOf(nvarcharMax("")).value).value)
        assertEquals(RpcValue.NullValue, parameterOf("E7 FF FF $coll FF FF FF FF FF FF FF FF").value)
        val binary = assertIs<RpcValue.RawValue>(parameterOf("A5 FF FF ${plp("01 02", "03")}").value)
        assertContentEquals(byteArrayOf(1, 2, 3), binary.bytes)
        val varchar = assertIs<RpcValue.RawValue>(parameterOf("A7 FF FF $coll ${plp("80 41")}").value)
        assertContentEquals(byteArrayOf(0x80.toByte(), 0x41), varchar.bytes)
        assertEquals(0, assertIs<RpcValue.RawValue>(parameterOf("A5 FF FF ${plp()}").value).bytes.size)
        assertEquals(RpcValue.NullValue, parameterOf("A5 FF FF FF FF FF FF FF FF FF FF").value)
    }

    /**
     * Festlegung: `nvarchar(max)` trägt UTF-16-Einheiten unverändert, auch ein unpaariges Surrogat (wie die Typen
     * mit fester Höchstlänge); eine ungerade Byte-Zahl ist `ODD_TEXT_LENGTH`.
     *
     * @spec SPEC-003
     */
    @Test
    fun `nvarchar(max) behaelt Surrogate und weist ungerade Laengen ab`() {
        val lone = parameterOf("E7 FF FF $coll ${plp("3D D8 41 00")}").value
        assertEquals("\uD83D" + "A", assertIs<RpcValue.TextValue>(lone).value)
        assertEquals(ProtocolFailure.ODD_TEXT_LENGTH, failureOf("E7 FF FF $coll ${plp("41 00 42")}"))
    }

    /**
     * Negativfall (Festlegung): ein Körper, der nicht zu seiner Gesamtlänge passt, ist ein Ergebniswert: Stücke über
     * der Gesamtlänge und darunter `INVALID_LENGTH`; ein fehlender Terminator und ein Stück über dem Rest
     * `TRUNCATED`. Die Gesamtlänge `0xFFFFFFFFFFFFFFFE` (unbekannt) erlaubt jede Summe.
     *
     * @spec SPEC-027
     */
    @Test
    fun `PLP-Koerper gegen Gesamtlaenge und Nachrichtenende`() {
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf("A5 FF FF ${plp("01 02", "03", total = 2)}"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf("A5 FF FF ${plp("01 02", "03", total = 4)}"))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf("A5 FF FF 01 00 00 00 00 00 00 00 01 00 00 00 01"))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf("A5 FF FF 00 00 00 00 00 00 00 00"))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf("A5 FF FF 00 00 00 00 00 00 00"))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf("A5 FF FF FE FF FF FF FF FF FF FF 01 00 00 00 01"))
    }

    /**
     * Negativfall (Festlegung): überlange Längenangaben enden in einem Ergebniswert, bevor ein Puffer entsteht. Eine
     * Gesamtlänge über `Int.MAX_VALUE` und eine negative (nicht `-1`, `-2`) ist `INVALID_LENGTH`; eine Gesamtlänge
     * bis `Int.MAX_VALUE` und ein Stück bis 4 GiB, die die Nachricht nicht hält, sind `TRUNCATED`. Wächter: eine
     * Prüfung, die erst nach dem Anlegen des Puffers greift, endet in `OutOfMemoryError`.
     *
     * @spec SPEC-027
     * @spec SPEC-007
     */
    @Test
    fun `Ueberlange PLP-Laengen enden vor dem Puffern in einem Ergebniswert`() {
        assertEquals(ProtocolFailure.TRUNCATED, failureOf("A5 FF FF FF FF FF 7F 00 00 00 00 01 00 00 00 01"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf("A5 FF FF 00 00 00 80 00 00 00 00"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf("A5 FF FF FF FF FF FF FF FF FF 7F"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf("A5 FF FF 00 00 00 00 00 00 00 80"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, failureOf("A5 FF FF FD FF FF FF FF FF FF FF"))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf("A5 FF FF FE FF FF FF FF FF FF FF FF FF FF FF 01"))
    }

    /**
     * Zerstückelt (`SPEC-027`, `TDS-QA-02`): jede Verkürzung einer Nachricht mit PLP-Parametern ist ein Ergebniswert,
     * nie eine Ausnahme. Gelesen ist eine Verkürzung nur an einer Parametergrenze (die Parameter reichen bis zum Ende
     * der Nachricht, ab dem SQL-Text); jede Verkürzung mitten in einem Parameter, auch mitten in einem Stück oder vor
     * dem Terminator, ist `Failed`.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Jede Verkuerzung einer Nachricht mit PLP endet in einem Ergebniswert`() {
        val pieces = listOf(
            "$head FF FF 0A 00 00 00",
            "00 00 ${nvarcharMax("select @a, 'x'", 5)}",
            "00 00 ${nvarcharMax("@a int", 2)}",
            "02 40 00 62 00 00 A5 FF FF ${plp("01 02", "03")}",
            "02 40 00 63 00 00 A5 FF FF FF FF FF FF FF FF FF FF",
        ).map { hex(it) }
        val message = pieces.reduce { all, next -> all + next }
        val boundaries = pieces.drop(1).runningFold(pieces.first().size) { end, piece -> end + piece.size }.drop(1)
        for (length in 0..message.size) {
            val result = RpcParser.parse(message.copyOf(length))
            if (length in boundaries) {
                assertIs<ProtocolResult.Ok<RpcRequest>>(result, "Länge $length")
            } else {
                assertIs<ProtocolResult.Failed>(result, "Länge $length")
            }
        }
    }

    /**
     * Selbstkonsistenz mit festem Startwert: beliebige Nutzdaten, zufällig in Stücke zerlegt (bekannte und unbekannte
     * Gesamtlänge), kommen als `varbinary(max)` unverändert zurück; mit zufällig veränderten Bytes und zufälligen
     * Längenfeldern endet jede Nachricht in einem Ergebniswert (`SPEC-027`).
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zufaellige Zerlegungen kommen unveraendert zurueck, zufaellige Koerper enden in Ergebniswerten`() {
        val random = Random(SEED)
        val prefix = hex("$head 01 00 70 00 00 00 00 00")
        repeat(RUNS) {
            val payload = random.nextBytes(random.nextInt(0, MAX_PAYLOAD))
            val chunks = mutableListOf<String>()
            var position = 0
            while (position < payload.size) {
                val size = minOf(random.nextInt(1, MAX_CHUNK), payload.size - position)
                chunks += payload.copyOfRange(position, position + size).toHex()
                position += size
            }
            val total = if (random.nextBoolean()) null else -2L
            val body = plp(*chunks.toTypedArray(), total = total)
            val value = assertIs<RpcValue.RawValue>(parameterOf("A5 FF FF $body").value)
            assertContentEquals(payload, value.bytes)
            val mutated = hex("A5 FF FF $body")
            repeat(random.nextInt(1, MAX_MUTATIONS)) {
                mutated[random.nextInt(mutated.size)] = random.nextInt().toByte()
            }
            RpcParser.parse(prefix + mutated)
            RpcParser.parse(prefix + hex("A5 FF FF") + random.nextBytes(random.nextInt(0, MAX_NOISE)))
        }
    }

    private companion object {
        const val SEED = 20261116
        const val RUNS = 3000
        const val MAX_PAYLOAD = 64
        const val MAX_CHUNK = 9
        const val MAX_MUTATIONS = 4
        const val MAX_NOISE = 40
    }
}
