package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.testsupport.Login7Builder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger

/** Von Hand gebaute Eingaben (Tabellenlayout nach MS-TDS), unabhängig vom Codec. */
internal object SessionVectors {
    const val ENCRYPT_OFF = 0x00
    const val ENCRYPT_ON = 0x01
    const val ENCRYPT_NOT_SUP = 0x02
    const val ENCRYPT_REQ = 0x03

    /** PRELOGIN-Anfrage: VERSION (6 Byte) und ENCRYPTION (1 Byte); `null` lässt ENCRYPTION weg. */
    fun preloginPayload(encryption: Int?): ByteArray = if (encryption == null) {
        hex("00 00 06 00 06  FF  00 00 00 00 00 00")
    } else {
        hex("00 00 0B 00 06  01 00 11 00 01  FF  00 00 00 00 00 00") + byteArrayOf(encryption.toByte())
    }

    fun prelogin(encryption: Int? = ENCRYPT_ON): TdsMessage =
        TdsMessage(PacketType.PRELOGIN, preloginPayload(encryption))

    fun login7Payload(configure: Login7Builder.() -> Unit = {}): ByteArray = Login7Builder().apply(configure).build()

    fun login7(configure: Login7Builder.() -> Unit = {}): TdsMessage =
        TdsMessage(PacketType.LOGIN7, login7Payload(configure))

    fun message(type: PacketType, payload: ByteArray = ByteArray(0)): TdsMessage = TdsMessage(type, payload)
}

/** Ein Adapter, der Aufrufe zählt und die Eingabe während des Aufrufs festhält. */
internal class RecordingAdapter(private val answer: (AuthRequest) -> AuthResult) : AuthAdapter {
    private val callCount = AtomicInteger()
    val calls: Int get() = callCount.get()

    @Volatile
    var userName: String? = null

    @Volatile
    var passwordDuringCall: String? = null

    @Volatile
    var passwordChars: PasswordChars? = null

    @Volatile
    var lastRequest: AuthRequest? = null

    @Volatile
    var callingThread: Thread? = null

    override fun authenticate(request: AuthRequest): AuthResult {
        callCount.incrementAndGet()
        lastRequest = request
        callingThread = Thread.currentThread()
        userName = request.userName
        passwordChars = request.password
        passwordDuringCall = String(request.password.reveal())
        return answer(request)
    }

    companion object {
        fun accepting(name: String = "sa") = RecordingAdapter { AuthResult.Accepted(Principal(name)) }

        /** Ein Adapter, der [millis] blockiert (wie eine Ableitung mit vielen Iterationen) und dann annimmt. */
        fun blocking(millis: Long) = RecordingAdapter {
            Thread.sleep(millis)
            AuthResult.Accepted(Principal("sa"))
        }
    }
}

/**
 * Ein [HandshakeChannel] mit festem Drehbuch; ohne weitere Einträge schweigt die Gegenstelle, bis ein Test mit
 * [feed] eine Nachricht nachlegt. [failReplyAfterWrites] lässt das Schreiben einer Antwort nach so vielen
 * angenommenen Stücken scheitern; [writtenChunks] hält die Stücke aller Antworten in der Reihenfolge.
 */
internal class FakeHandshakeChannel(
    inbound: List<Inbound>,
    private val tlsResult: Boolean = true,
    private val sendResult: Boolean = true,
    var sendDelayMillis: Long = 0,
) : HandshakeChannel {
    /** Läuft in `send` nach dem Eintrag in [sent] und vor dessen Ende; darf suspendieren. */
    var onSend: suspend (TdsMessage) -> Unit = {}

    /** Läuft am Anfang jedes `receive`, bevor der nächste Eintrag des Drehbuchs gelesen wird. */
    var onReceive: () -> Unit = {}

    /** Zahl der Aufrufe von `receive`. */
    var receiveCalls = 0
        private set

    private val script = ArrayDeque(inbound)
    private val later = Channel<Inbound>(Channel.UNLIMITED)

    /** Die Stücke, die Antworten angenommen haben, in der Reihenfolge ihres Schreibens. */
    val writtenChunks = mutableListOf<ByteArray>()

    /** Nach so vielen angenommenen Stücken scheitert das Schreiben einer Antwort. */
    var failReplyAfterWrites: Int = Int.MAX_VALUE

    /** Legt der Gegenstelle eine Nachricht nach, die sie nach dem Drehbuch liefert. */
    fun feed(inbound: Inbound) {
        later.trySend(inbound)
    }
    val sent = mutableListOf<TdsMessage>()
    val trace = mutableListOf<String>()
    var tlsCalls = 0
        private set
    var limitSwitches = 0
        private set
    var closed = false
        private set

    /** Wahr, wenn die Gegenstelle alle Einträge des Drehbuchs geliefert hat. */
    val exhausted: Boolean get() = script.isEmpty()

    /**
     * Solange sie wahr ist, liefert [receive] das Ende der Gegenstelle und den Lesefehler nicht: die Gegenstelle des
     * Tests schließt erst, wenn die Antwort da ist. Ein Test, der den Disconnect während einer Anfrage prüft, setzt
     * sie auf `{ false }`.
     */
    var holdEndWhile: () -> Boolean = { false }

    override suspend fun receive(): Inbound {
        receiveCalls++
        onReceive()
        val next = script.removeFirstOrNull() ?: later.receive()
        if (next !is Inbound.Message) while (holdEndWhile()) yield()
        return next
    }

    override suspend fun send(message: TdsMessage): Boolean {
        trace += "send-start"
        sent += message
        onSend(message)
        if (sendDelayMillis > 0) delay(sendDelayMillis)
        trace += "send-end"
        return sendResult
    }

    /** Die Packet Size, die der letzte Aufruf von `usePacketSize` setzte; `null` ohne Aufruf. */
    var packetSize: Int? = null
        private set

    /** Läuft in jedem `write` einer Antwort nach dem Eintrag der Bytes; darf suspendieren. */
    var onReplyWrite: suspend () -> Unit = {}

    override fun usePacketSize(size: Int) {
        packetSize = size
    }

    override fun openReply(type: PacketType): ReplyStream = object : ReplyStream {
        private val collected = java.io.ByteArrayOutputStream()
        private var done = false

        override suspend fun write(bytes: ByteArray): Boolean {
            if (done || !sendResult || writtenChunks.size >= failReplyAfterWrites) return false
            collected.write(bytes)
            writtenChunks += bytes
            onReplyWrite()
            return true
        }

        /** Eine beendete Antwort liegt wie eine gesendete Nachricht in [sent]; eine nicht beendete nirgends. */
        override suspend fun finish(): Boolean {
            if (done || !sendResult || writtenChunks.size >= failReplyAfterWrites) return false
            done = true
            sent += TdsMessage(type, collected.toByteArray())
            return true
        }
    }

    override suspend fun startTls(): Boolean {
        tlsCalls++
        trace += "tls"
        return tlsResult
    }

    override fun useSessionLimits() {
        limitSwitches++
    }

    override fun close() {
        trace += "close"
        closed = true
    }

    companion object {
        fun message(message: TdsMessage) = Inbound.Message(message)
    }
}

/** Sammelt die Einträge des Logs. */
internal class CollectingLog : HandshakeLog {
    val entries = mutableListOf<LogEntry>()

    override fun record(entry: LogEntry) {
        entries += entry
    }
}

internal fun HandshakeAction.label(): String = when (this) {
    is HandshakeAction.Send -> "Send"
    HandshakeAction.BeginTls -> "BeginTls"
    is HandshakeAction.Authenticate -> "Authenticate"
    HandshakeAction.SwitchToSessionLimits -> "SwitchToSessionLimits"
    is HandshakeAction.Log -> "Log"
    HandshakeAction.Forward -> "Forward"
    HandshakeAction.ResetSession -> "ResetSession"
    is HandshakeAction.Close -> "Close:${reason.name}"
    is HandshakeAction.Cancel -> "Cancel:${cause.name}"
}
