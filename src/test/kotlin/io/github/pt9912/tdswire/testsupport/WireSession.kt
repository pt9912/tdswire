package io.github.pt9912.tdswire.testsupport

import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.RequestLoop
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.HandshakeDriver
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.session.RecordingAdapter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.FramingResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.PacketWriter
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.TransportHandshakeChannel
import kotlin.test.assertIs

/** Die Packet Size des Schreibers vor dem Login (wie `HandshakeConnectionHandler.PACKET_SIZE`). */
const val WIRE_DEFAULT_PACKET_SIZE = 4096

/**
 * Eine Sitzung über dem echten Transport (Leser, Schreiber, Paket-Codec) und einem [ScriptedWire]:
 * der Login ist gelaufen, die Zähler des Drahts stehen auf null, und die SQL_BATCH-Nachrichten der
 * Gegenstelle liegen zum Lesen bereit. [serve] startet die Anfrageschleife.
 */
class WireSession(
    val wire: ScriptedWire,
    val ready: HandshakeOutcome.Ready,
    private val channel: TransportHandshakeChannel,
) {
    suspend fun serve(
        backend: QueryBackend,
        sinkAround: (TokenSink) -> TokenSink = { it },
        errors: ErrorMapper = ErrorMapper(),
    ) = RequestLoop(ready, channel, backend, errors, sinkAround = sinkAround).run()

    companion object {
        /**
         * Meldet an (PRELOGIN und LOGIN7 mit der verlangten [packetSize]) und stellt [batches] bereit;
         * danach schweigt die Gegenstelle mit Ende der Verbindung. Mit [packetBuilder] baut der Schreiber seine
         * Pakete anders (Tests lösen so eine Ausnahme im Paketbau aus). [configure] stellt Haltepunkte des
         * Drahts ein, bevor irgendetwas geschrieben wird.
         */
        suspend fun login(
            packetSize: Int,
            vararg batches: String,
            retain: Boolean = false,
            packetBuilder: ((PacketType, ByteArray, Int, Boolean, Int) -> ByteArray)? = null,
            configure: ScriptedWire.() -> Unit = {},
        ): WireSession {
            val login = SessionVectors.login7 { this.packetSize = packetSize }
            val messages = listOf(SessionVectors.prelogin(), login) +
                batches.map { TdsMessage(PacketType.SQL_BATCH, BatchVectors.payload(it)) }
            val inbound = messages.fold(ByteArray(0)) { all, message ->
                val split = PacketWriter.split(message.type, message.payload, WIRE_DEFAULT_PACKET_SIZE)
                all + assertIs<FramingResult.Ok<List<ByteArray>>>(split).value.reduce(ByteArray::plus)
            }
            val wire = ScriptedWire(inbound, retain)
            val reader = MessageReader(wire, FramingLimits.preLogin())
            val writer = MessageWriter(wire, WIRE_DEFAULT_PACKET_SIZE)
            if (packetBuilder != null) writer.packetBuilder = packetBuilder
            val channel = TransportHandshakeChannel(reader, writer, wire) { true }
            val driver = HandshakeDriver(HandshakeConfig(), RecordingAdapter.accepting(), sessionIds = { "s1" })
            val ready = assertIs<HandshakeOutcome.Ready>(driver.run(channel))
            wire.startCounting()
            wire.configure()
            return WireSession(wire, ready, channel)
        }
    }
}
