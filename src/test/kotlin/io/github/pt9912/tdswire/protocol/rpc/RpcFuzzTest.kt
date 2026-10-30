package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class RpcFuzzTest {
    private val vectors = listOf(
        hex(RpcVectors.EXECUTE_SQL),
        hex(RpcVectors.PREP_EXEC),
        hex(RpcVectors.EXECUTE),
        hex("${RpcVectors.NAME_HEAD} ${RpcVectors.NAME_PARAMS}"),
    )

    /** TYPE_INFO-Anfänge aller Typen des Katalogs, danach folgen Zufallsbytes als Wert. */
    private val typeInfos = listOf(
        "26 01", "26 02", "26 04", "26 08", "68 01", "6D 04", "6D 08", "6E 04", "6E 08", "6F 04", "6F 08", "24 10",
        "6C 11 12 02", "6A 05 09 00", "28", "29 03", "2A 07", "2B 07",
        "E7 08 00 ${RpcVectors.COLLATION}", "EF 08 00 ${RpcVectors.COLLATION}",
        "A7 08 00 ${RpcVectors.COLLATION}", "AF 08 00 ${RpcVectors.COLLATION}", "A5 08 00", "AD 08 00",
    )

    /** Ein Fehler ist ein Ergebniswert; ein Ok hat Wertarten, die zu ihrer TYPE_INFO passen. */
    private fun assertConsistent(result: ProtocolResult<RpcRequest>) {
        if (result is ProtocolResult.Ok) {
            val call = result.value.call
            if (call is RpcCall.ByName) {
                assertTrue(call.name.isNotEmpty())
            }
            if (call is RpcCall.UnknownProcedure) {
                assertTrue(call.procId !in KNOWN_IDS)
            }
            parametersOf(call).forEach(::assertParameter)
        }
    }

    private fun parametersOf(call: RpcCall): List<RpcParameter> = when (call) {
        is RpcCall.ExecuteSql -> call.parameters
        is RpcCall.PrepExec -> call.parameters
        is RpcCall.Execute -> call.parameters
        is RpcCall.ByName -> call.parameters
        is RpcCall.UnknownProcedure -> emptyList()
    }

    private fun assertParameter(parameter: RpcParameter) {
        val kind = parameter.type.kind
        when (val value = parameter.value) {
            is RpcValue.IntegerValue -> assertTrue(kind == RpcTypeKind.INTN)
            is RpcValue.BitValue -> assertTrue(kind == RpcTypeKind.BITN)
            is RpcValue.TextValue -> assertTrue(kind == RpcTypeKind.NVARCHAR || kind == RpcTypeKind.NCHAR)
            is RpcValue.RawValue -> assertTrue(value.bytes.size <= MAX_VALUE_BYTES)
            RpcValue.NullValue -> Unit
        }
    }

    private fun randomMessage(random: Random): ByteArray {
        val head = when (random.nextInt(PROC_CHOICES)) {
            0 -> "FF FF 0A 00 00 00"
            1 -> "FF FF 0D 00 00 00"
            2 -> "FF FF 0C 00 00 00"
            3 -> "01 00 70 00 00 00"
            else -> "FF FF ${RpcVectors.hexByte(random.nextInt(0, MAX_BYTE))} 00 00 00"
        }
        val body = StringBuilder(head)
        repeat(random.nextInt(0, MAX_PARAMETERS)) {
            val nameChars = random.nextInt(0, MAX_NAME_CHARS)
            val status = RpcVectors.hexByte(random.nextInt(0, MAX_STATUS))
            val typeInfo = typeInfos[random.nextInt(typeInfos.size)]
            body.append(" ${RpcVectors.hexByte(nameChars)} ${random.nextBytes(nameChars * 2).toHex()}")
            body.append(" $status $typeInfo ${random.nextBytes(random.nextInt(0, MAX_VALUE_BYTES)).toHex()}")
        }
        return hex("${RpcVectors.HEADERS} $body")
    }

    /**
     * Fuzz mit festem Startwert (`SPEC-027`, `TDS-QA-02`): zufällige Bytes, Handvektoren mit
     * veränderten Bytes, jede Verkürzung der Vektoren und Nachrichten aus zufälligen Parametern
     * mit Katalog-TYPE_INFO enden in einem Ergebniswert, nie in einer Ausnahme. Ein Ergebniswert
     * hat Wertarten, die zu ihrer TYPE_INFO passen.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zufaellige und veraenderte Eingaben enden in einem Ergebniswert`() {
        val random = Random(SEED)
        repeat(RUNS) {
            assertConsistent(RpcParser.parse(random.nextBytes(random.nextInt(0, MAX_NOISE))))
            assertConsistent(RpcParser.parse(randomMessage(random)))
            vectors.forEach { base ->
                val mutated = base.copyOf()
                repeat(random.nextInt(1, MAX_MUTATIONS)) {
                    mutated[random.nextInt(mutated.size)] = random.nextInt().toByte()
                }
                assertConsistent(RpcParser.parse(mutated))
                assertConsistent(RpcParser.parse(base.copyOf(random.nextInt(0, base.size))))
            }
        }
        vectors.forEach { base ->
            for (length in 0..base.size) {
                assertConsistent(RpcParser.parse(base.copyOf(length)))
            }
        }
    }

    private companion object {
        const val SEED = 20261030
        const val RUNS = 6000
        const val MAX_NOISE = 120
        const val MAX_MUTATIONS = 4
        const val MAX_PARAMETERS = 5
        const val MAX_NAME_CHARS = 3
        const val MAX_STATUS = 4
        const val MAX_BYTE = 256
        const val MAX_VALUE_BYTES = 24
        const val PROC_CHOICES = 5
        val KNOWN_IDS = setOf(10, 12, 13)
    }
}
