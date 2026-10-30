package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.wire.ProtocolErrorCategory
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Negativfälle des RPC-Parsers. Jede Abweichung ist ein Ergebniswert mit Fehlergrund und Kategorie
 * nach `SPEC-011`, nie eine Ausnahme. Die Zuordnung der Fehlergründe ist eine Festlegung dieses
 * Parsers; gemessen ist keine Fehlerantwort.
 */
class RpcFailureTest {
    private val head = RpcVectors.HEADERS
    private val coll = RpcVectors.COLLATION

    private fun failureOf(bytes: ByteArray): ProtocolFailure =
        assertIs<ProtocolResult.Failed>(RpcParser.parse(bytes)).error.failure

    private fun byName(typeAndValue: String): ProtocolFailure = failureOf(RpcVectors.byName(typeAndValue))

    private fun assertAll(expected: ProtocolFailure, vararg messages: String) {
        messages.forEach { assertEquals(expected, failureOf(hex(it)), it) }
    }

    /**
     * Negativfall: abgeschnittener oder fehlerhafter Kopf — ALL_HEADERS, Name oder ProcID,
     * Optionsflags — und ein Prozedurname der Länge 0.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Fehlerhafter Kopf ist ein Ergebniswert`() {
        assertAll(ProtocolFailure.TRUNCATED, "", head, "$head FF FF 0A", "$head FF FF 0A 00", "$head 01 00 70 00")
        assertAll(ProtocolFailure.TRUNCATED, "$head FF 7F 70 00 00 00", "$head 03 00 70 00 71 00")
        assertAll(ProtocolFailure.INVALID_HEADER_BLOCK, "03 00 00 00", "FF FF FF FF", "05 00 00 00")
        assertAll(ProtocolFailure.INVALID_LENGTH, "$head 00 00 00 00")
    }

    /**
     * Negativfall: TYPE_INFO mit Größe, Precision oder Scale außerhalb der Grenzen des Parsers ist
     * `INVALID_TYPE_INFO` (Kategorie `TDS_PROTOCOL_ERROR`).
     *
     * @spec SPEC-003
     */
    @Test
    fun `TYPE_INFO ausserhalb der Grenzen ist ein Protokollfehler`() {
        listOf(
            "26 03", "26 00", "68 02", "6D 05", "6E 02", "6F 03", "24 08",
            "6C 04 09 00", "6C 11 00 00", "6C 11 27 00", "6C 11 05 06", "6A 11 FF 00", "29 08", "2A 09", "2B 08",
        ).forEach {
            val failure = byName(it)
            assertEquals(ProtocolFailure.INVALID_TYPE_INFO, failure, it)
            assertEquals(ProtocolErrorCategory.TDS_PROTOCOL_ERROR, failure.category)
        }
    }

    /**
     * Festlegung: PLP (MAX, höchste Länge `FF FF`), `XML` (`F1`) und Typkennungen außerhalb des
     * Katalogs (`62`, `E5`, `00`) sind `INVALID_TYPE` mit Kategorie `TDS_UNSUPPORTED_TYPE`.
     *
     * @spec SPEC-003
     */
    @Test
    fun `PLP, XML und unbekannte Typen sind ein Ergebnisfehler der Kategorie unsupported`() {
        listOf("E7 FF FF $coll", "A7 FF FF $coll", "A5 FF FF", "AD FF FF", "EF FF FF $coll", "F1 00", "62", "E5", "00")
            .forEach {
                val failure = byName(it)
                assertEquals(ProtocolFailure.INVALID_TYPE, failure, it)
                assertEquals(ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE, failure.category)
            }
    }

    /**
     * Negativfall: Wertlänge, die nicht zur TYPE_INFO passt, Zeichenwert über der höchsten Länge,
     * Unicode-Text mit ungerader Byte-Zahl und ein abgeschnittener Wert.
     *
     * @spec SPEC-003
     */
    @Test
    fun `Wertlaenge gegen TYPE_INFO ist ein Ergebniswert`() {
        listOf(
            "26 04 02 01 00",
            "26 04 08 00 00 00 00 00 00 00 00",
            "6D 08 04 00 00 00 00",
            "28 02 01 00",
            "29 03 03 01 02 03",
        )
            .forEach { assertEquals(ProtocolFailure.INVALID_LENGTH, byName(it), it) }
        assertEquals(ProtocolFailure.INVALID_LENGTH, byName("E7 02 00 $coll 04 00 61 00 62 00"))
        assertEquals(ProtocolFailure.INVALID_LENGTH, byName("A5 01 00 02 00 DE AD"))
        assertEquals(ProtocolFailure.ODD_TEXT_LENGTH, byName("E7 08 00 $coll 03 00 61 00 62"))
        listOf("26 04 04 01 00", "E7 08 00 $coll 04 00 61 00", "A5 08 00 05 00 DE AD", "26 04", "E7 08 00 $coll 04")
            .forEach { assertEquals(ProtocolFailure.TRUNCATED, byName(it), it) }
    }

    /**
     * Festlegung: Die Katalogform wird nach Position geprüft. `sp_executesql` braucht zwei
     * Textparameter ohne NULL, `sp_prepexec` ein OUTPUT-Handle `INTN` 4 und zwei Textparameter,
     * `sp_execute` ein Handle `INTN` 4 ohne NULL; sonst `INVALID_RPC_FORM`.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Verletzte Katalogform ist INVALID_RPC_FORM`() {
        val execSql = "$head FF FF 0A 00 00 00"
        val prepExec = "$head FF FF 0D 00 00 00"
        val execute = "$head FF FF 0C 00 00 00"
        val stmt = "00 00 ${RpcVectors.STATEMENT}"
        val decl = "00 00 ${RpcVectors.DECLARATION}"
        assertAll(
            ProtocolFailure.INVALID_RPC_FORM,
            execSql,
            "$execSql $stmt",
            "$execSql 00 00 26 04 04 01 00 00 00  $decl",
            "$execSql 00 00 E7 12 00 $coll FF FF  $decl",
            "$execSql $stmt  00 00 28 03 01 00 00",
            "$prepExec 00 00 26 04 04 FF FF FF FF  $decl  $stmt",
            "$prepExec 00 01 26 08 08 FF FF FF FF FF FF FF FF  $decl  $stmt",
            "$prepExec 00 01 26 04 00  $decl  $stmt",
            "$prepExec 00 01 26 04 04 FF FF FF FF  $decl",
            execute,
            "$execute 00 00 26 04 00",
            "$execute 00 00 26 02 02 05 00",
            "$execute 00 00 E7 08 00 $coll 00 00",
        )
    }

    /**
     * Negativfall (überlange Eingabe): Bytes hinter dem letzten Parameter sind ein Parameter, der
     * nicht endet; sie werden nie still verworfen.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ueberzaehlige Bytes hinter den Parametern sind ein Fehler`() {
        val byName = "${RpcVectors.NAME_HEAD} ${RpcVectors.NAME_PARAMS}"
        listOf(RpcVectors.EXECUTE_SQL, RpcVectors.PREP_EXEC, RpcVectors.EXECUTE, byName)
            .forEach { vector ->
                listOf("FF", "00", "01 00", "00 00 00").forEach { tail ->
                    assertIs<ProtocolResult.Failed>(RpcParser.parse(hex("$vector $tail")), "$tail nach $vector")
                }
            }
    }

    /**
     * Negativfall (abgeschnittene Eingabe): wird der letzte Parameter jedes Vektors an irgendeiner
     * Stelle in seinem Inneren abgeschnitten, ist das ein Ergebniswert; am Parametergrenze
     * (vollständige Parameter) bleibt der Rest eine gültige Nachricht.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Abgeschnittener letzter Parameter ist ein Ergebniswert`() {
        val lastParams = mapOf(
            RpcVectors.EXECUTE_SQL to RpcVectors.PARAM_O_OUTPUT_NULL,
            RpcVectors.PREP_EXEC to "02 40 00 61 00  00  26 04  04  07 00 00 00",
            RpcVectors.EXECUTE to "02 40 00 62 00  00  E7 06 00  $coll  FF FF",
            "${RpcVectors.NAME_HEAD} ${RpcVectors.NAME_PARAMS}" to "02 40 00 79 00  00  E7 08 00  $coll  FF FF",
        )
        lastParams.forEach { (vector, last) ->
            val bytes = hex(vector)
            val start = bytes.size - hex(last).size
            assertIs<ProtocolResult.Ok<RpcRequest>>(RpcParser.parse(bytes.copyOf(start)), "Grenze $vector")
            for (length in start + 1 until bytes.size) {
                assertIs<ProtocolResult.Failed>(RpcParser.parse(bytes.copyOf(length)), "$length Byte von $vector")
            }
        }
    }

    /**
     * Zusage: die Textdarstellung nennt nie Parameterwerte, SQL-Text oder Deklarationsliste, nur
     * Längen, Namen und Typen.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Textdarstellung enthaelt weder Werte noch SQL-Text`() {
        val secret = "geheim"
        val secretHex = secret.toByteArray(Charsets.UTF_16LE).toHex()
        val value = "E7 0C 00 $coll 0C 00 $secretHex"
        val vectors = listOf(
            hex(RpcVectors.EXECUTE_SQL),
            hex(RpcVectors.PREP_EXEC),
            RpcVectors.byName(value),
            RpcVectors.byName("26 04 04 15 CD 5B 07"),
            RpcVectors.byName("A5 08 00 06 00 DE AD BE EF 00 11"),
        )
        vectors.forEach { bytes ->
            val text = assertIs<ProtocolResult.Ok<RpcRequest>>(RpcParser.parse(bytes)).value.toString()
            listOf("select", "int", secret, "123456789", "DE AD").forEach { forbidden ->
                assertFalse(text.contains(forbidden), "$forbidden in $text")
            }
        }
    }
}
