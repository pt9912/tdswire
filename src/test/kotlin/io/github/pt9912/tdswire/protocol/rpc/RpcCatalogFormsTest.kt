package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TypeInfoWriter
import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Handvektoren der vier Katalogformen. Die Orakel sind von Hand nach MS-TDS gerechnet und nicht
 * an einem Server oder Client gemessen; die Form der Parameter folgt der Messmatrix in `ADR-0005`
 * (Testserver, TDS 7.4 unverschlüsselt, kein Microsoft-SQL-Server). Die ProcIDs 11 und 15 im Test
 * der unbekannten ProcID sind frei gewählte Werte außerhalb des Katalogs, keine benannten
 * Prozeduren.
 */
class RpcCatalogFormsTest {
    private val latin1 = Collation(info = 0x00D00409, sortId = 0x34)

    private fun request(bytes: ByteArray): RpcRequest =
        assertIs<ProtocolResult.Ok<RpcRequest>>(RpcParser.parse(bytes)).value

    private fun integerOf(parameter: RpcParameter): Long = assertIs<RpcValue.IntegerValue>(parameter.value).value

    /**
     * Orakel: `sp_executesql` (ProcID 10, `FF FF 0A 00`, Optionsflags `02 00`) liefert Text und
     * Deklarationsliste getrennt von den Wertparametern; `@a` ist `INTN` 4 mit 42, `@o` ein
     * OUTPUT-Parameter mit NULL.
     *
     * @spec SPEC-030
     */
    @Test
    fun `sp_executesql trennt Text, Deklaration und Wertparameter`() {
        val request = request(hex(RpcVectors.EXECUTE_SQL))
        val call = assertIs<RpcCall.ExecuteSql>(request.call)
        assertEquals("select @a", call.statement)
        assertEquals("@a int", call.declarations)
        assertEquals(listOf(2), request.headerTypes)
        assertEquals(0x0002, request.optionFlags)
        assertEquals(listOf("@a", "@o"), call.parameters.map { it.name })
        assertEquals(RpcType(RpcTypeKind.INTN, length = 4), call.parameters[0].type)
        assertEquals(42L, integerOf(call.parameters[0]))
        assertFalse(call.parameters[0].output)
        assertTrue(call.parameters[1].output)
        assertEquals(RpcValue.NullValue, call.parameters[1].value)
    }

    /**
     * Orakel: `sp_prepexec` (ProcID 13) trägt das Handle −1 als OUTPUT-Parameter 0, dann
     * Deklaration und Text; `@a` = 7 bleibt Wertparameter.
     *
     * @spec SPEC-030
     */
    @Test
    fun `sp_prepexec liefert Handle, Deklaration, Text und Wertparameter`() {
        val call = assertIs<RpcCall.PrepExec>(request(hex(RpcVectors.PREP_EXEC)).call)
        assertEquals(-1, call.handle)
        assertEquals("@a int", call.declarations)
        assertEquals("select @a", call.statement)
        assertEquals(listOf("@a"), call.parameters.map { it.name })
        assertEquals(7L, integerOf(call.parameters[0]))
    }

    /**
     * Orakel: `sp_execute` (ProcID 12) trägt das Handle 5 im Parameter 0; `@a` ist ein `BITN` mit
     * wahr, `@b` ein `NVARCHAR` mit NULL-Sentinel `FF FF`.
     *
     * @spec SPEC-030
     */
    @Test
    fun `sp_execute liefert das Handle und die Wertparameter`() {
        val call = assertIs<RpcCall.Execute>(request(hex(RpcVectors.EXECUTE)).call)
        assertEquals(5, call.handle)
        assertEquals(listOf("@a", "@b"), call.parameters.map { it.name })
        assertEquals(true, assertIs<RpcValue.BitValue>(call.parameters[0].value).value)
        assertEquals(RpcType(RpcTypeKind.NVARCHAR, length = 6, collation = latin1), call.parameters[1].type)
        assertEquals(RpcValue.NullValue, call.parameters[1].value)
    }

    /**
     * Orakel: `NameLenProcID` `09 00` ist die Zeichenzahl von "[dbo].[p]" (18 Byte UTF-16LE);
     * Optionsflags `00 00`. Der leere String (Länge 0) ist nicht NULL, `FF FF` ist NULL.
     *
     * @spec SPEC-030
     */
    @Test
    fun `RPC per Prozedurname zaehlt Zeichen und unterscheidet leeren String von NULL`() {
        val request = request(hex("${RpcVectors.NAME_HEAD}  ${RpcVectors.NAME_PARAMS}"))
        val call = assertIs<RpcCall.ByName>(request.call)
        assertEquals("[dbo].[p]", call.name)
        assertEquals(0, request.optionFlags)
        assertEquals("", assertIs<RpcValue.TextValue>(call.parameters[0].value).value)
        assertEquals(RpcValue.NullValue, call.parameters[1].value)
    }

    /**
     * Grenze: ein Name ohne Parameter ist zulässig (der Spike beobachtete `StoredProcedure` ohne
     * Parameter); die Optionsflags `05 00` (`fWithRecomp`, `fReuseMetaData`) kommen unverändert.
     *
     * @spec SPEC-030
     */
    @Test
    fun `RPC per Name ohne Parameter und mit Optionsflags`() {
        val bytes = hex("${RpcVectors.HEADERS}  01 00 70 00  05 00")
        val request = request(bytes)
        assertEquals(0x0005, request.optionFlags)
        assertEquals(emptyList(), assertIs<RpcCall.ByName>(request.call).parameters)
    }

    /**
     * Festlegung: Eine ProcID außerhalb von 10, 12 und 13 (hier 11 und 15) ist ein eigener
     * Ergebniswert; die Parameter hinter den Optionsflags werden nicht gelesen, auch wenn sie
     * unlesbar sind.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Unbekannte ProcID ist ein Ergebniswert und liest keine Parameter`() {
        listOf(11, 15, 0).forEach { procId ->
            val bytes = hex("${RpcVectors.HEADERS}  FF FF ${RpcVectors.hexByte(procId)} 00  00 00  FF EE DD")
            assertEquals(RpcCall.UnknownProcedure(procId), request(bytes).call)
        }
    }

    /**
     * Gegenrechnung der Hex-Vektoren: UTF-16LE des Textes, TYPE_INFO der Typen und ALL_HEADERS
     * entstehen ein zweites Mal im Code (Selbstkonsistenz mit `TypeInfoWriter` des Token-Encoders,
     * nicht unabhängig von diesem).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Hex-Vektor stimmt mit dem im Code gebauten Vektor ueberein`() {
        val out = ByteWriter()
        out.bytes(hex(RpcVectors.HEADERS))
        out.u16Le(0xFFFF)
        out.u16Le(10)
        out.u16Le(2)
        listOf("select @a", "@a int").forEach { text ->
            out.u8(0)
            out.u8(0)
            TypeInfoWriter.writeTypeInfo(
                out,
                ColumnDef("", TdsType.NVarcharType(text.length, latin1)),
            )
            out.u16Le(text.length * 2)
            out.bytes(text.toByteArray(Charsets.UTF_16LE))
        }
        out.bytes(hex(RpcVectors.PARAM_A_42))
        out.bytes(hex(RpcVectors.PARAM_O_OUTPUT_NULL))
        val expected = hex(RpcVectors.EXECUTE_SQL)
        assertEquals(expected.toHex(), out.toByteArray().toHex())
        val intInfo = ByteWriter()
        TypeInfoWriter.writeTypeInfo(intInfo, ColumnDef("", TdsType.IntNType(4)))
        assertContentEquals(hex("26 04"), intInfo.toByteArray())
    }
}
