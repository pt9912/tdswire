package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.protocol.session.EncryptionPolicy
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.HandshakeDriver
import io.github.pt9912.tdswire.protocol.session.HandshakeLog
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.listener.Connection
import io.github.pt9912.tdswire.transport.listener.ConnectionHandler
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.messaging.TransportError
import io.github.pt9912.tdswire.transport.messaging.TransportHandshakeChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * Handler der Stabilitätstests: führt den Handshake mit [HandshakeDriver] über Paket-Codec, Leser und
 * Schreiber auf dem rohen Kanal der Verbindung, meldet nach der Annahme „Login abgeschlossen“ und
 * liest danach Nachrichten, bis der Strom endet oder ein Fehler kommt. Es gibt keinen SQL_BATCH-Pfad:
 * eingehende Nachrichten nach dem Login werden verworfen. Der unverschlüsselte Testmodus ersetzt TLS.
 */
class DriverHandler(private val adapter: AuthAdapter) : ConnectionHandler {
    /** Der Ausgang des Handshakes je Verbindung (Nummer der Verbindung im Listener). */
    val outcomes = ConcurrentHashMap<Long, String>()
    val afterLogin = ConcurrentLinkedQueue<String>()

    /** Ist sie gesetzt, hält sie jeden Handler am Eingang, bis alle Teilnehmer gleichzeitig darin sind. */
    @Volatile
    var gate: Rendezvous? = null

    override suspend fun handle(connection: Connection) {
        val entry = gate
        entry?.enter()
        try {
            serve(connection)
        } finally {
            entry?.leave()
        }
    }

    private suspend fun serve(connection: Connection) {
        val reader = MessageReader(connection.channel, FramingLimits.preLogin())
        val writer = MessageWriter(connection.channel, PACKET_SIZE)
        val channel = TransportHandshakeChannel(reader, writer, connection.channel) { false }
        val config = HandshakeConfig(encryptionPolicy = EncryptionPolicy.TEST_UNENCRYPTED)
        val driver = HandshakeDriver(config, adapter, HandshakeLog.NONE, HANDSHAKE_TIMEOUT, sessionIds = { "s" })
        val outcome = driver.run(channel)
        outcomes[connection.id] = describe(outcome)
        if (outcome is HandshakeOutcome.Ready) {
            connection.loginCompleted()
            afterLogin += describe(readUntilEnd(reader))
        }
    }

    private suspend fun readUntilEnd(reader: MessageReader): ReadOutcome {
        while (true) {
            val next = reader.next()
            if (next !is ReadOutcome.Message) return next
        }
    }

    private fun describe(outcome: HandshakeOutcome): String = when (outcome) {
        is HandshakeOutcome.Ready -> "Ready"
        is HandshakeOutcome.Closed -> "Closed:${outcome.reason}"
    }

    private fun describe(outcome: ReadOutcome): String = when (outcome) {
        is ReadOutcome.Message -> "Message"
        ReadOutcome.EndOfStream -> "EndOfStream"
        is ReadOutcome.Failed -> describe(outcome.error)
    }

    private fun describe(error: TransportError): String = when (error) {
        is TransportError.Framing -> "Framing:${error.error.failure}"
        is TransportError.Channel -> "Channel"
    }

    private companion object {
        const val PACKET_SIZE = 4096
        val HANDSHAKE_TIMEOUT = 60.seconds
    }
}

/**
 * Eintrittssperre für [parties] Handler: [enter] kehrt erst zurück, wenn [parties] Handler zugleich
 * eingetreten und keiner ausgetreten ist. [peak] ist die größte gemessene Zahl gleichzeitig eingetretener
 * Handler. Kommen nicht alle innerhalb der Frist, wirft [enter] und der Handler endet mit einer Ausnahme.
 */
class Rendezvous(private val parties: Int) {
    private val inside = AtomicInteger()
    private val peakInside = AtomicInteger()
    private val opened = CompletableDeferred<Unit>()

    val peak: Int get() = peakInside.get()

    suspend fun enter() {
        val now = inside.incrementAndGet()
        peakInside.accumulateAndGet(now, ::maxOf)
        if (now >= parties) opened.complete(Unit)
        withTimeout(WAIT) { opened.await() }
    }

    fun leave() {
        inside.decrementAndGet()
    }

    private companion object {
        val WAIT = 60.seconds
    }
}
