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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/** Von Hand gebaute Eingaben (Tabellenlayout nach MS-TDS), unabhängig vom Codec. */
object SessionVectors {
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
class RecordingAdapter(private val answer: (AuthRequest) -> AuthResult) : AuthAdapter {
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

/** Ein [HandshakeChannel] mit festem Drehbuch; ohne weitere Einträge schweigt die Gegenstelle. */
class FakeHandshakeChannel(
    inbound: List<Inbound>,
    private val tlsResult: Boolean = true,
    private val sendResult: Boolean = true,
    var sendDelayMillis: Long = 0,
) : HandshakeChannel {
    private val script = ArrayDeque(inbound)
    val sent = mutableListOf<TdsMessage>()
    val trace = mutableListOf<String>()
    var tlsCalls = 0
        private set
    var limitSwitches = 0
        private set
    var closed = false
        private set

    override suspend fun receive(): Inbound = script.removeFirstOrNull() ?: awaitCancellation()

    override suspend fun send(message: TdsMessage): Boolean {
        trace += "send-start"
        sent += message
        if (sendDelayMillis > 0) delay(sendDelayMillis)
        trace += "send-end"
        return sendResult
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
class CollectingLog : HandshakeLog {
    val entries = mutableListOf<LogEntry>()

    override fun record(entry: LogEntry) {
        entries += entry
    }
}

fun HandshakeAction.label(): String = when (this) {
    is HandshakeAction.Send -> "Send"
    HandshakeAction.BeginTls -> "BeginTls"
    is HandshakeAction.Authenticate -> "Authenticate"
    HandshakeAction.SwitchToSessionLimits -> "SwitchToSessionLimits"
    is HandshakeAction.Log -> "Log"
    HandshakeAction.Forward -> "Forward"
    is HandshakeAction.Close -> "Close:${reason.name}"
}
