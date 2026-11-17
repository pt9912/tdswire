package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.dispatch.RpcWire
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.byName
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int4
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.protocol.wire.ProtocolErrorCategory
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Mehrere RPCs in einer Nachricht (`SPEC-030`): Trennbyte, Reihenfolge, Obergrenzen und Fehler des Parsers. Der
 * Aufbau der Nachricht (Trennbyte `0xFF`, danach Prozedur, Optionsflags und Parameter ohne zweites ALL_HEADERS) ist an
 * der Messung am echten SQL Server gelesen (`rpc-batch-dml`, 2022 und 2025); die Handvektoren sind davon abgeleitet,
 * nicht aufgezeichnet.
 */
class RpcCallSequenceTest {
    private val headerBytes = hex(RpcVectors.HEADERS).size

    /** Die Aufrufe [messages] als eine Nachricht: ALL_HEADERS des ersten, danach Trennbyte und Rest je weiterem. */
    private fun joined(separator: Int, vararg messages: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(messages.first())
        messages.drop(1).forEach {
            out.write(separator)
            out.write(it, headerBytes, it.size - headerBytes)
        }
        return out.toByteArray()
    }

    private fun parsed(bytes: ByteArray): RpcRequest =
        assertIs<ProtocolResult.Ok<RpcRequest>>(RpcParser.parse(bytes)).value

    private fun failureOf(bytes: ByteArray): ProtocolFailure =
        assertIs<ProtocolResult.Failed>(RpcParser.parse(bytes)).error.failure

    private fun insert(id: Int) = executeSql("insert", "@Id int", param("@Id", int4(id)))

    /**
     * Drei Aufrufe mit dem Trennbyte `0xFF` (gemessen): jeder Aufruf behält Text und Parameter, die Reihenfolge ist die
     * der Nachricht, `call` ist der erste. Rot, wenn der Parser das Trennbyte als Namenslänge liest (das Bytemuster der
     * Messung ergäbe dann `TRUNCATED` oder falsche Parameter).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Mehrere Aufrufe einer Nachricht werden in der Reihenfolge der Nachricht gelesen`() {
        val request = parsed(joined(0xFF, insert(1), insert(2), insert(3)))
        val ids = request.invocations.map { invocation ->
            val call = assertIs<RpcCall.ExecuteSql>(invocation.call)
            assertEquals("insert", call.statement)
            assertEquals("@Id int", call.declarations)
            val value = assertIs<RpcValue.IntegerValue>(call.parameters.single().value)
            value.value
        }
        assertEquals(listOf(1L, 2L, 3L), ids)
        assertEquals(2, request.following.size)
        assertEquals(request.invocations.first().call, request.call)
    }

    /**
     * Das Byte `0x80` ist kein Trennbyte (ungemessen) und bleibt eine Namenslänge: ein Parameter mit dem Namen von 128
     * Zeichen (`@` und 127 `x`, gültig, `SPEC-030`) hinter einem anderen gehört zum Aufruf; die Nachricht hat nur
     * einen.
     * Rot, wenn der Parser `0x80` als Trennbyte liest.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Eine Namenslaenge von 128 trennt keine Aufrufe`() {
        val name = "@" + "x".repeat(127)
        val request = parsed(executeSql("insert", "@Id int", param("@Id", int4(1)), param(name, int4(2))))
        assertTrue(request.following.isEmpty())
        val call = assertIs<RpcCall.ExecuteSql>(request.call)
        assertEquals(listOf("@Id", name), call.parameters.map { it.name })
    }

    /**
     * Die Aufrufe müssen nicht dieselbe Form haben: `sp_executesql` und ein RPC per Name, jeder mit eigenen
     * Optionsflags (der zweite Aufruf trägt `0x0002`).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Aufrufe verschiedener Form und eigene Optionsflags je Aufruf`() {
        val request = parsed(joined(0xFF, insert(1), byName("p", param("@x", int4(5)))))
        assertIs<RpcCall.ExecuteSql>(request.call)
        val second = request.following.single()
        assertEquals("p", assertIs<RpcCall.ByName>(second.call).name)
        assertEquals(0, second.optionFlags)
        assertEquals(2, request.optionFlags)
    }

    /**
     * Ein Trennbyte am Ende der Nachricht hat keinen Aufruf hinter sich: `INVALID_RPC_FORM` (Kategorie
     * `TDS_PROTOCOL_ERROR`). Ein einzelner Aufruf ohne Trennbyte hat keine weiteren.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein Trennbyte ohne folgenden Aufruf ist INVALID_RPC_FORM`() {
        val failure = RpcParser.parse(insert(1) + byteArrayOf(0xFF.toByte()))
        assertEquals(ProtocolFailure.INVALID_RPC_FORM, assertIs<ProtocolResult.Failed>(failure).error.failure)
        assertEquals(ProtocolErrorCategory.TDS_PROTOCOL_ERROR, failure.error.failure.category)
        assertTrue(parsed(insert(1)).following.isEmpty())
    }

    /**
     * Ein Fehler im zweiten Aufruf (abgeschnittener Wert) macht die ganze Nachricht zum Fehler des Parsers; kein
     * Aufruf wird teilweise geliefert.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein Fehler im zweiten Aufruf ist ein Fehler der Nachricht`() {
        val second = insert(2)
        val broken = joined(0xFF, insert(1), second.copyOf(second.size - 2))
        assertEquals(ProtocolFailure.TRUNCATED, failureOf(broken))
    }

    /**
     * Hinter einer ProcID außerhalb des Katalogs endet das Lesen: ihre Parameter sind nicht gelesen, ein folgendes Byte
     * wäre nicht als Trennbyte erkennbar. Der Aufruf bleibt in der Liste (der Dispatch lehnt die Nachricht ab).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Hinter einer unbekannten ProcID endet das Lesen`() {
        val request = parsed(joined(0xFF, insert(1), RpcWire.procId(11)) + hex("FF FF 0A 00 00 00"))
        assertEquals(RpcCall.UnknownProcedure(11), request.following.single().call)
    }

    /**
     * Die Obergrenze von [RpcParameterReader.MAX_PARAMETERS] gilt für die ganze Nachricht: zählt man die Parameter
     * aller Aufrufe, ist der Parameter über der Grenze `INVALID_RPC_FORM`. Gegenprobe: genau an der Grenze gelingt es.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Die Parametergrenze gilt ueber alle Aufrufe der Nachricht`() {
        val limit = RpcParameterReader.MAX_PARAMETERS
        val prefix = hex("${RpcVectors.HEADERS} 01 00 70 00 00 00")
        val parameter = hex("00 00 26 04 00")
        fun message(first: Int, second: Int): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(prefix)
            repeat(first) { out.write(parameter) }
            out.write(0xFF)
            out.write(hex("01 00 70 00 00 00"))
            repeat(second) { out.write(parameter) }
            return out.toByteArray()
        }
        val ok = parsed(message(limit - 1, 1))
        assertEquals(limit, ok.invocations.sumOf { (it.call as RpcCall.ByName).parameters.size })
        assertEquals(ProtocolFailure.INVALID_RPC_FORM, failureOf(message(limit, 1)))
        assertEquals(ProtocolFailure.INVALID_RPC_FORM, failureOf(message(1, limit)))
    }

    /**
     * Höchstens [RpcParser.MAX_CALLS] Aufrufe je Nachricht (Festlegung ohne Messung): der Aufruf darüber ist
     * `INVALID_RPC_FORM`; genau an der Grenze gelingt es.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Mehr Aufrufe als die Obergrenze sind INVALID_RPC_FORM`() {
        val call = byName("p")
        fun message(count: Int): ByteArray = joined(0xFF, *Array(count) { call })
        assertEquals(RpcParser.MAX_CALLS, parsed(message(RpcParser.MAX_CALLS)).invocations.size)
        assertEquals(ProtocolFailure.INVALID_RPC_FORM, failureOf(message(RpcParser.MAX_CALLS + 1)))
    }
}
