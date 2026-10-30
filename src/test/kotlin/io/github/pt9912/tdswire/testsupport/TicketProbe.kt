package io.github.pt9912.tdswire.testsupport

import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.messaging.TransportError
import io.github.pt9912.tdswire.transport.tls.TlsPath
import io.github.pt9912.tdswire.transport.tls.TlsResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Hauptprogramm der Kind-JVM des Tests zum JVM-Flag `jdk.tls.server.newSessionTicketCount`: das JDK
 * liest das Flag einmal je Prozess, ein Test in derselben JVM kann es nicht umschalten. Die Kind-JVM
 * führt im TDS-7.4-Pfad mit TLS 1.3 einen Handshake und eine Nachricht des Servers aus und schreibt
 * eine Zeile `TICKET_PROBE <Ausgang>` auf die Standardausgabe; ob das Flag gesetzt ist, bestimmt der
 * Aufrufer über den Prozessstart.
 */
object TicketProbe {
    const val MARKER = "TICKET_PROBE"
    private const val TIMEOUT_MILLIS = 20_000L

    @JvmStatic
    fun main(args: Array<String>) {
        println("$MARKER ${runBlocking { withTimeout(TIMEOUT_MILLIS) { probe() } }}")
    }

    private suspend fun probe(): String {
        val outcome = runHandshake(TlsSetup(TlsPath.ENCAPSULATED_IN_TDS, listOf("TLSv1.3")))
        val server = outcome.server
        return if (outcome.peerFailure != null || server !is TlsResult.Ok) {
            "handshake_failed"
        } else {
            firstMessage(server.value, outcome.peer)
        }
    }

    private suspend fun firstMessage(server: SuspendingChannel, peer: SuspendingChannel): String {
        val message = TdsMessage(PacketType.TABULAR_RESULT, ByteArray(64) { it.toByte() })
        MessageWriter(server, PACKET_SIZE).write(message)
        return when (val read = MessageReader(peer).next()) {
            is ReadOutcome.Message -> if (read.message == message) "exchange_ok" else "exchange_wrong"
            is ReadOutcome.Failed -> "exchange_failed ${(read.error as? TransportError.Channel)?.error?.detail}"
            ReadOutcome.EndOfStream -> "exchange_failed end"
        }
    }

    private const val PACKET_SIZE = 4096
}
