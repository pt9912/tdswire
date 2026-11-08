package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CONNECT_MILLIS = 5_000
private const val READ_MILLIS = 20_000
private const val CLEANUP_MILLIS = 5_000L
private const val HEADER_BYTES = 8
private const val BYTE = 0xFF
private const val BYTE_BITS = 8
private const val EOM = 1
private const val SOME_BYTES = 512
private const val TAIL_BYTES = 32
const val PRELOGIN_TYPE = 0x12
const val LOGIN7_TYPE = 0x10
const val BATCH_TYPE = 0x01

/**
 * Prüfwort für "kein Wert im Log": nicht numerisch und mit Präfix, damit es weder in Port, Zeitstempel noch Zähler
 * der Logzeilen zufällig vorkommt (`TDS-QA-03`).
 */
internal const val WERTPROBE = "wertprobe-xq9"

/** Der Sammler: hält die Ereignisse, die der Server an die Senke gibt, in der Reihenfolge des Eingangs. */
internal class CapturedEvents : TelemetrySink {
    val events = CopyOnWriteArrayList<TelemetryEvent>()

    override fun accept(event: TelemetryEvent) {
        events += event
    }

    /** Die Ereignisse der Art [T]. */
    inline fun <reified T : TelemetryEvent> of(): List<T> = events.filterIsInstance<T>()
}

/**
 * Alles, was ein Adapter am Ereignis lesen kann, als Text: der Name der Art und der Wert jedes öffentlichen Feldes.
 * Die Redaktionstests suchen darin; ein neues Feld liest dieser Weg ohne Änderung mit.
 */
internal fun TelemetryEvent.visibleText(): String = (
    listOf(javaClass.simpleName) +
        javaClass.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") && it.name != "getClass" }
            .map { "${it.name.removePrefix("get")}=${it.invoke(this)}" }
    ).joinToString(" ")

private val SETTING_SHAPE = Regex("[A-Za-z0-9_]{1,40}")

/**
 * Jede Einstellung eines Ereignisses `CONFIG_EFFECTIVE` in [events] hat Schlüssel und Wert von der Form einer Zahl,
 * eines Namens oder eines Schalters (Buchstaben, Ziffern, Unterstrich, höchstens 40 Zeichen): ein Pfad, der Inhalt
 * eines Schlüsselspeichers, ein Passwort oder ein Eintrag der Benutzerliste hat diese Form nicht. Mindestens ein
 * solches Ereignis muss da sein, sonst bewiese die Prüfung nichts.
 */
internal fun assertSettingsAreShapedAsNames(events: Iterable<TelemetryEvent>) {
    val all = events.filter { it.kind == EventKind.CONFIG_EFFECTIVE }.map { assertNotNull(it.settings) }
    assertTrue(all.isNotEmpty(), "kein CONFIG_EFFECTIVE, die Prüfung bewiese nichts")
    all.forEach { settings ->
        settings.forEach { (key, value) ->
            val shaped = SETTING_SHAPE.matches(key) && SETTING_SHAPE.matches(value)
            assertTrue(shaped, "Einstellung $key hat den Wert '$value'")
        }
    }
}

/** Eine gelesene und verworfene Nachricht ([RawClient.drainMessage]): Typ, Zahl der Nutzdatenbytes, deren Ende. */
internal class DrainedMessage(val type: Int, val payloadBytes: Long, val tail: ByteArray)

/** Eine vollständige Nachricht des Servers: Pakettyp, Nutzdaten und alle Bytes samt Paketköpfen. */
internal class RawMessage(val type: Int, val payload: ByteArray, val bytes: ByteArray)

/**
 * Ein Rohbytes-Client mit blockierendem Socket auf `host` (Voreinstellung `127.0.0.1`): sendet Bytes, wie der
 * Test sie baut, und liest Pakete des Servers, ohne den Codec des Produkts zu benutzen. Er ist kein Referenzclient.
 */
internal class RawClient(port: Int, host: String = LOOPBACK) : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(host, port), CONNECT_MILLIS)
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

    /**
     * Liest die nächste Nachricht und wirft ihre Nutzdaten weg: der Typ, die Zahl der Nutzdatenbytes und die letzten
     * [TAIL_BYTES] davon; `null`, wenn die Gegenstelle vor dem ersten Byte schließt. Für Antworten, die der Test nicht
     * im Speicher halten kann.
     */
    fun drainMessage(): DrainedMessage? {
        var type = -1
        var total = 0L
        var tail = ByteArray(0)
        var first = true
        do {
            val header = readHeader() ?: return if (first) null else throw EOFException("Paket abgeschnitten")
            first = false
            type = header[0].toInt() and BYTE
            val length = ((header[2].toInt() and BYTE) shl BYTE_BITS) or (header[3].toInt() and BYTE)
            val body = input.readNBytes(length - HEADER_BYTES)
            if (body.size != length - HEADER_BYTES) throw EOFException("Paket abgeschnitten")
            total += body.size
            tail = (tail + body).takeLast(TAIL_BYTES).toByteArray()
        } while (header[1].toInt() and EOM == 0)
        return DrainedMessage(type, total, tail)
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

    /** Die nächsten Bytes, sobald welche da sind (bis zu [max]); leer beim Ende der Gegenstelle. */
    fun readSome(max: Int = SOME_BYTES): ByteArray {
        val buffer = ByteArray(max)
        val count = input.read(buffer)
        return if (count <= 0) ByteArray(0) else buffer.copyOf(count)
    }

    /** Alle Bytes bis zum Ende der Gegenstelle; ein Lesefehler bricht den Test ab. */
    fun readToEnd(): ByteArray = input.readAllBytes()

    /** Bricht die Verbindung mit RST ab (`SO_LINGER` 0), wie ein Client, der abstürzt. */
    fun reset() {
        socket.setSoLinger(true, 0)
        socket.close()
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

/** Basis der Tests, die einen [TdsServer] im Prozess starten: nach jedem Test werden die Server beendet. */
internal open class RuntimeTestBase : TempFilesTest() {
    private val started = ConcurrentLinkedQueue<TdsServer>()
    val sink = CapturedEvents()

    suspend fun start(environment: Map<String, String>, wiring: Wiring = Wiring(telemetry = sink)): StartOutcome {
        val result = Bootstrap.launch(configOf(environment), wiring)
        if (result is StartOutcome.Running) started += result.server
        return result
    }

    suspend fun running(environment: Map<String, String>, wiring: Wiring = Wiring(telemetry = sink)): TdsServer =
        assertIs<StartOutcome.Running>(start(environment, wiring)).server

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
