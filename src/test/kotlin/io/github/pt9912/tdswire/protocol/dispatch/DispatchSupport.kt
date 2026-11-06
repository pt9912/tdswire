package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.FakeHandshakeChannel
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.HandshakeDriver
import io.github.pt9912.tdswire.protocol.session.HandshakeOutcome
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.session.RecordingAdapter
import io.github.pt9912.tdswire.protocol.session.ReplyStream
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import java.io.ByteArrayOutputStream
import kotlin.test.assertIs

/** Von Hand gebaute Eingaben und erwartete Antworten (Tabellenlayout nach MS-TDS), unabhängig vom Codec. */
internal object BatchVectors {
    /**
     * ALL_HEADERS nach MS-TDS: Gesamtlänge 22, ein Header von 18 Byte vom Typ 2 (Transaction
     * Descriptor) mit 8 Byte Descriptor und 4 Byte Request Count.
     */
    const val ALL_HEADERS = "16 00 00 00  12 00 00 00  02 00  00 00 00 00 00 00 00 00  01 00 00 00 "

    fun payload(text: String): ByteArray = hex(ALL_HEADERS) + text.toByteArray(Charsets.UTF_16LE)

    fun message(text: String): Inbound = Inbound.Message(TdsMessage(PacketType.SQL_BATCH, payload(text)))

    fun raw(type: PacketType, payload: ByteArray): Inbound = Inbound.Message(TdsMessage(type, payload))

    /** COLMETADATA einer Spalte `n`, Int4, nicht nullbar (Handvektor wie in der Token-Tabelle der Codec-Tests). */
    const val COLMETADATA_N = "81 01 00 00 00 00 00 00 00 38 01 6E 00"

    /** DONE, Status COUNT (`0x0010`), curCmd 0, Zeilenzahl 1. */
    const val DONE_COUNT_1 = "FD 10 00 00 00 01 00 00 00 00 00 00 00"

    /** DONE, Status COUNT, Zeilenzahl 0. */
    const val DONE_COUNT_0 = "FD 10 00 00 00 00 00 00 00 00 00 00 00"

    /** DONE ohne Bits, ohne Zeilenzahl. */
    const val DONE_FINAL = "FD 00 00 00 00 00 00 00 00 00 00 00 00"

    /** DONE mit Fehlerstatus (`0x0002`), curCmd 0, Zeilenzahl 0. */
    const val DONE_ERROR = "FD 02 00 00 00 00 00 00 00 00 00 00 00"

    /** DONE mit dem Statusbit ATTENTION (`0x0020`), curCmd 0, Zeilenzahl 0: die Bestätigung eines Cancel. */
    const val DONE_ATTN = "FD 20 00 00 00 00 00 00 00 00 00 00 00"

    /** Ein ATTENTION-Paket: leere Nutzlast. */
    fun attention(): Inbound = raw(PacketType.ATTENTION, ByteArray(0))

    /** ROW mit einem Int4-Wert, little endian. */
    fun row(value: Int): String = "D1 " + ByteArray(4) { (value shr (8 * it)).toByte() }.toHex()
}

internal val INT_COLUMN = ResultColumn("n", SqlType.Int32, nullable = false)

internal fun resultStart() = QueryEvent.ResultStart(listOf(INT_COLUMN))

private val requestStates = setOf(ConnectionState.RUNNING, ConnectionState.STREAMING, ConnectionState.CANCELLING)

/** Der Rahmen der Login-Antworten: zwei Nachrichten (PRELOGIN, LOGIN7) stehen vor jeder Antwort einer Anfrage. */
internal const val LOGIN_REPLIES = 2

/** Eine angemeldete Sitzung `s1` über einem [FakeHandshakeChannel]; sein Drehbuch geht nach dem Login weiter. */
internal class Session(val channel: FakeHandshakeChannel, val ready: HandshakeOutcome.Ready) {
    init {
        // Die Gegenstelle der meisten Tests schließt erst nach der Antwort; ein Ende während der Anfrage ist ein
        // Disconnect (`DisconnectTest` setzt die Bedingung zurück).
        channel.holdEndWhile = { ready.live.context.state in requestStates }
    }

    /** Die Antworten auf Anfragen, ohne die beiden Login-Antworten. */
    val replies: List<TdsMessage> get() = channel.sent.drop(LOGIN_REPLIES)

    fun tokens(index: Int): List<Token> = TokenDecoder(replies[index].payload).decodeAll()

    fun hex(index: Int): String = replies[index].payload.toHex()

    /** Die Schleife dieser Sitzung; ihre [RequestLoop.handles] bleiben nach dem Lauf lesbar. */
    fun loop(
        backend: QueryBackend,
        errors: ErrorMapper = ErrorMapper(),
        sinkAround: (TokenSink) -> TokenSink = { it },
        times: RequestTimes = fixedTimes(),
        requestIds: () -> String = Counter()::next,
    ) = RequestLoop(ready, channel, backend, errors, times, RequestHooks(requestIds, sinkAround))

    suspend fun serve(
        backend: QueryBackend,
        errors: ErrorMapper = ErrorMapper(),
        sinkAround: (TokenSink) -> TokenSink = { it },
        times: RequestTimes = fixedTimes(),
        requestIds: () -> String = Counter()::next,
    ) = loop(backend, errors, sinkAround, times, requestIds).run()

    companion object {
        suspend fun login(vararg later: Inbound): Session {
            val script = listOf(
                FakeHandshakeChannel.message(SessionVectors.prelogin()),
                FakeHandshakeChannel.message(SessionVectors.login7()),
            )
            val channel = FakeHandshakeChannel(script + later.toList())
            val driver = HandshakeDriver(HandshakeConfig(), RecordingAdapter.accepting(), sessionIds = { "s1" })
            return Session(channel, assertIs<HandshakeOutcome.Ready>(driver.run(channel)))
        }
    }
}

/** Die Wanduhr der Dispatch-Tests: fest, damit die Frist einer Anfrage (Beginn plus `QUERY_TIMEOUT`) genau ist. */
internal const val FIXED_NOW = 1_700_000_000_000L

/** Die Fristen der Dispatch-Tests: Voreinstellungen von `SPEC-009` und `SPEC-033`, feste Uhr. */
internal fun fixedTimes() = RequestTimes(now = { FIXED_NOW })

/** Die Frist einer Anfrage mit der Voreinstellung `QUERY_TIMEOUT`. */
internal val DEFAULT_DEADLINE = FIXED_NOW + RequestTimes.DEFAULT_QUERY_TIMEOUT.inWholeMilliseconds

/** Zählt Anfrage-Kennungen: `r1`, `r2`, ... */
internal class Counter {
    private var count = 0

    fun next(): String = "r${++count}"
}

/** Die Meldung des ERROR-Tokens einer Antwort. */
internal fun List<Token>.errorMessage(): ServerMessage = filterIsInstance<ErrorToken>().single().message

/** Das letzte Token einer Antwort. */
internal fun List<Token>.lastDone(): DoneToken = assertIs<DoneToken>(last())

/** Eine Antwort im Speicher: sammelt die Bytes, die ihr zugeschrieben werden. */
internal class CollectingReply : ReplyStream {
    private val collected = ByteArrayOutputStream()
    val bytes: ByteArray get() = collected.toByteArray()

    override suspend fun write(bytes: ByteArray): Boolean {
        collected.write(bytes)
        return true
    }

    override suspend fun finish(): Boolean = true
}
