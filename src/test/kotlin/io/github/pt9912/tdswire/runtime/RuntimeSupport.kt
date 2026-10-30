package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.config.testModeEnvironment
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.assertIs

private const val CONNECT_MILLIS = 5_000
private const val READ_MILLIS = 20_000
private const val CLEANUP_MILLIS = 5_000L
private const val HEADER_BYTES = 8
private const val BYTE = 0xFF
private const val BYTE_BITS = 8
private const val EOM = 1
const val PRELOGIN_TYPE = 0x12
const val LOGIN7_TYPE = 0x10
const val BATCH_TYPE = 0x01

/** Die Eigenschaft des Prozesses, wie sie der Start setzt: das Flag gegen das NewSessionTicket ist `0`. */
internal val flagSet: (String) -> String? = { if (it == Bootstrap.TICKET_FLAG) "0" else null }

/** Sammelt die Zeilen des Server-Logs. */
internal class CapturedLog : ServerLog {
    val lines = CopyOnWriteArrayList<String>()

    override fun line(text: String) {
        lines += text
    }
}

/** Eine vollständige Nachricht des Servers: Pakettyp, Nutzdaten und alle Bytes samt Paketköpfen. */
internal class RawMessage(val type: Int, val payload: ByteArray, val bytes: ByteArray)

/**
 * Ein Rohbytes-Client mit blockierendem Socket auf `127.0.0.1`: sendet Bytes, wie der Test sie baut, und liest
 * Pakete des Servers, ohne den Codec des Produkts zu benutzen. Er ist kein Referenzclient.
 */
internal class RawClient(port: Int) : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(LOOPBACK, port), CONNECT_MILLIS)
        soTimeout = READ_MILLIS
        tcpNoDelay = true
    }
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()

    fun send(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    fun sendBytewise(bytes: ByteArray) = bytes.forEach {
        output.write(it.toInt())
        output.flush()
    }

    /** Die nächste Nachricht; `null`, wenn die Gegenstelle vor dem ersten Byte schließt. */
    fun readMessage(): RawMessage? {
        val all = java.io.ByteArrayOutputStream()
        val payload = java.io.ByteArrayOutputStream()
        var type = -1
        do {
            val header = readHeader() ?: return if (all.size() == 0) null else throw EOFException("Paket abgeschnitten")
            type = header[0].toInt() and BYTE
            val length = ((header[2].toInt() and BYTE) shl BYTE_BITS) or (header[3].toInt() and BYTE)
            val body = input.readNBytes(length - HEADER_BYTES)
            if (body.size != length - HEADER_BYTES) throw EOFException("Paket abgeschnitten")
            all.write(header)
            all.write(body)
            payload.write(body)
        } while (header[1].toInt() and EOM == 0)
        return RawMessage(type, payload.toByteArray(), all.toByteArray())
    }

    private fun readHeader(): ByteArray? {
        val header = input.readNBytes(HEADER_BYTES)
        if (header.size in 1 until HEADER_BYTES) throw EOFException("Kopf abgeschnitten")
        return header.takeIf { it.isNotEmpty() }
    }

    /** `true`, wenn der Server schließt, ohne vorher ein weiteres Byte zu senden. */
    fun closedByServerWithoutBytes(): Boolean = try {
        input.read() < 0
    } catch (reset: IOException) {
        reset.message?.contains("reset", ignoreCase = true) == true
    }

    override fun close() = socket.close()
}

/** Ein freier Port auf `127.0.0.1`; er ist nach der Rückgabe wieder frei. */
fun freePort(): Int = ServerSocket(0, 1, java.net.InetAddress.getByName(LOOPBACK)).use { it.localPort }

/** Der Wert der Option ENCRYPTION in einer PRELOGIN-Antwort, von Hand aus der Optionstabelle gelesen. */
fun encryptionOf(preloginPayload: ByteArray): Int {
    var entry = 0
    while (preloginPayload[entry].toInt() and BYTE != BYTE) {
        if (preloginPayload[entry].toInt() == 1) {
            val offset = ((preloginPayload[entry + 1].toInt() and BYTE) shl BYTE_BITS) or
                (preloginPayload[entry + 2].toInt() and BYTE)
            return preloginPayload[offset].toInt()
        }
        entry += 5
    }
    error("keine ENCRYPTION-Option")
}

/** Der PRELOGIN-Wire eines Clients, in Paketen zu 4000 Nutzdatenbytes. */
fun preloginWire(encryption: Int): ByteArray =
    handMessage(PRELOGIN_TYPE, SessionVectors.preloginPayload(encryption), 4000)

/** Die Umgebung eines Testmodus-Starts auf `127.0.0.1` mit freiem Port. */
fun loopbackEnvironment(usersFile: String, vararg more: Pair<String, String>): Map<String, String> =
    testModeEnvironment(
        usersFile,
        "LISTEN_HOST" to LOOPBACK,
        "LISTEN_PORT" to "0",
        *more,
    )

/** Basis der Tests, die einen [Server] im Prozess starten: nach jedem Test werden die Server beendet. */
internal open class RuntimeTestBase : TempFilesTest() {
    private val started = ConcurrentLinkedQueue<Server>()
    val log = CapturedLog()

    suspend fun start(environment: Map<String, String>, wiring: Wiring = Wiring(log = log)): BootResult {
        val result = Bootstrap.start(environment, flagSet, wiring)
        if (result is BootResult.Running) started += result.server
        return result
    }

    suspend fun running(environment: Map<String, String>, wiring: Wiring = Wiring(log = log)): Server =
        assertIs<BootResult.Running>(start(environment, wiring)).server

    @AfterTest
    fun stopStartedServers() {
        runBlocking {
            while (true) {
                val server = started.poll() ?: break
                withContext(NonCancellable) { withTimeoutOrNull(CLEANUP_MILLIS) { server.stop() } }
            }
        }
    }
}
