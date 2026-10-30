package io.github.pt9912.tdswire.transport.framing

import io.github.pt9912.tdswire.protocol.rpc.RpcParser
import io.github.pt9912.tdswire.protocol.rpc.RpcRequest
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.chunked
import io.github.pt9912.tdswire.testsupport.hex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Ende-zu-Ende ohne Netzwerk: RPC-Nachrichten, in kleine Pakete zerlegt und in beliebig kleinen
 * Stücken eingespeist, kommen aus dem [MessageAssembler] als dieselbe Nachricht heraus, und der
 * [RpcParser] liest daraus dasselbe Ergebnis wie aus den unzerlegten Bytes. Der Test liegt im
 * Paket `transport`, weil `protocol` den Transport nicht importieren soll.
 */
class RpcAssemblyTest {
    private val vectors = listOf(
        RpcVectors.EXECUTE_SQL,
        RpcVectors.PREP_EXEC,
        RpcVectors.EXECUTE,
        "${RpcVectors.NAME_HEAD} ${RpcVectors.NAME_PARAMS}",
    ).map { hex(it) }

    private fun parsed(payload: ByteArray): String =
        assertIs<ProtocolResult.Ok<RpcRequest>>(RpcParser.parse(payload)).value.toString()

    private fun wireOf(payload: ByteArray, packetSize: Int): ByteArray {
        val packets = assertIs<FramingResult.Ok<List<ByteArray>>>(
            PacketWriter.split(PacketType.RPC, payload, packetSize),
        ).value
        return packets.fold(ByteArray(0)) { all, packet -> all + packet }
    }

    private fun assembled(wire: ByteArray, sizes: Sequence<Int>): TdsMessage {
        val assembler = MessageAssembler()
        val messages = mutableListOf<TdsMessage>()
        chunked(wire, sizes).forEach { chunk ->
            val step = assembler.feed(chunk)
            assertNull(step.error)
            messages += step.messages
        }
        return messages.single()
    }

    /**
     * Zusage: Pakete mit 3 Nutzdatenbytes, Byte für Byte und in Zufallsstücken (fester Startwert)
     * eingespeist, ergeben dieselbe Nachricht (Pakettyp RPC, gleiche Nutzdaten) und dasselbe
     * Parse-Ergebnis wie die unzerlegte Nachricht.
     *
     * @spec SPEC-027
     */
    @Test
    fun `Zerstueckelte RPC-Nachrichten ergeben dasselbe Parse-Ergebnis`() {
        val random = Random(20261030)
        vectors.forEach { payload ->
            val wire = wireOf(payload, PacketHeader.SIZE + 3)
            val sizeSources = listOf(
                generateSequence { 1 },
                generateSequence { 1 + random.nextInt(11) },
            )
            sizeSources.forEach { sizes ->
                val message = assembled(wire, sizes)
                assertEquals(PacketType.RPC, message.type)
                assertContentEquals(payload, message.payload)
                assertEquals(parsed(payload), parsed(message.payload))
            }
        }
    }
}
