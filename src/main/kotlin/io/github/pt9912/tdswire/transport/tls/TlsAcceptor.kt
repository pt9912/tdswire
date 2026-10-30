package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine

/**
 * Baut die Server-Engine, führt den Handshake aus und prüft, was danach gelten muss: das ALPN im
 * TDS-8.0-Pfad und die saubere Umschaltung auf rohes TLS im TDS-7.4-Pfad. Eingang ist
 * [TlsChannel.accept].
 */
internal object TlsAcceptor {
    suspend fun accept(
        channel: SuspendingChannel,
        context: SSLContext,
        path: TlsPath,
        parameters: TlsParameters,
        options: TlsOptions,
    ): TlsResult<TlsChannel> {
        val io = TlsRecordIo(
            serverEngine(context, path, parameters),
            NetInbound(channel, options.readBufferSize),
            NetOutbound(channel, options.packetSize),
            options.taskDispatcher,
        )
        if (path == TlsPath.ENCAPSULATED_IN_TDS) {
            io.inbound.startPackets()
            io.outbound.startPackets()
        }
        val handshake = TlsHandshake(io).run()
        val verdict = if (handshake is TlsResult.Failed) handshake else switchToStream(io, path)
        return when (verdict) {
            is TlsResult.Failed -> verdict

            is TlsResult.Ok -> {
                io.adoptSession()
                TlsResult.Ok(TlsChannel(channel, io))
            }
        }
    }

    private fun serverEngine(context: SSLContext, path: TlsPath, parameters: TlsParameters): SSLEngine {
        val engine = context.createSSLEngine()
        engine.useClientMode = false
        val settings = engine.sslParameters
        settings.protocols = parameters.protocols.toTypedArray()
        settings.cipherSuites = parameters.cipherSuites.toTypedArray()
        if (path == TlsPath.FROM_FIRST_BYTE) settings.applicationProtocols = arrayOf(TlsChannel.ALPN_TDS_8_0)
        engine.sslParameters = settings
        return engine
    }

    private fun switchToStream(io: TlsRecordIo, path: TlsPath): TlsResult<Unit> = when {
        path == TlsPath.FROM_FIRST_BYTE && io.engine.applicationProtocol != TlsChannel.ALPN_TDS_8_0 ->
            failed(TlsFailure.ALPN_MISMATCH, "ALPN ${TlsChannel.ALPN_TDS_8_0} nicht ausgehandelt")

        path == TlsPath.ENCAPSULATED_IN_TDS && !(io.inbound.endPackets() && io.outbound.endPackets()) ->
            failed(TlsFailure.PROTOCOL_VIOLATION, "Handshake-Paket reicht ueber FINISHED hinaus")

        else -> TlsResult.Ok(Unit)
    }
}
