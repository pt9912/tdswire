package io.github.pt9912.tdswire.testsupport

import io.github.pt9912.tdswire.transport.channel.ChannelError
import io.github.pt9912.tdswire.transport.channel.ChannelFailure
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException
import javax.net.ssl.SSLParameters
import javax.net.ssl.TrustManagerFactory

/**
 * Die PKCS#12-Speicher der TLS-Tests. Die Dockerfile-Stage `testkeys` erzeugt sie per `keytool`
 * unter dem Verzeichnis der Systemeigenschaft `tdswire.testkeys`; im Repo liegt kein Schlüssel.
 */
object TestKeys {
    const val VALID = "gueltig.p12"
    const val OTHER_NAME = "falscher-name.p12"
    const val EXPIRED = "abgelaufen.p12"
    private const val PASSWORD = "tdswire-test"
    private const val ALIAS = "server"

    private fun load(name: String): KeyStore {
        val directory = checkNotNull(System.getProperty("tdswire.testkeys")) { "Systemeigenschaft fehlt" }
        val store = KeyStore.getInstance("PKCS12")
        FileInputStream(File(directory, name)).use { store.load(it, PASSWORD.toCharArray()) }
        return store
    }

    /** Ein Server-Kontext mit Schlüssel und Kette aus [name]. */
    fun serverContext(name: String = VALID): SSLContext {
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(load(name), PASSWORD.toCharArray())
        return SSLContext.getInstance("TLS").apply { init(factory.keyManagers, null, null) }
    }

    /** Ein Client-Kontext, der dem obersten Zertifikat der Kette aus [name] vertraut. */
    fun clientContext(name: String = VALID): SSLContext {
        val trust = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry(ALIAS, load(name).getCertificateChain(ALIAS).last())
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        return SSLContext.getInstance("TLS").apply { init(null, factory.trustManagers, null) }
    }
}

/** Ein Fehler des Drahtes in der Gegenstelle des Tests (Ende des Stroms, Kanalfehler, falscher Pakettyp). */
class PeerWireException(message: String) : IOException(message)

/**
 * Hüllt [inner] ein und begrenzt jeden Lese- und Schreibvorgang auf die nächste Länge aus
 * [readSizes] bzw. [writeSizes]; eine fehlende Folge lässt die Richtung unverändert.
 */
class FragmentingChannel(
    private val inner: SuspendingChannel,
    private val readSizes: Iterator<Int>? = null,
    private val writeSizes: Iterator<Int>? = null,
) : SuspendingChannel {
    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
        val limit = buffer.limit()
        if (readSizes != null) buffer.limit(minOf(limit, buffer.position() + readSizes.next()))
        try {
            return inner.read(buffer)
        } finally {
            buffer.limit(limit)
        }
    }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> {
        var result: ChannelResult<Unit> = ChannelResult.Ok(Unit)
        if (writeSizes == null) return inner.writeFully(buffer)
        while (buffer.hasRemaining() && result is ChannelResult.Ok) {
            val limit = buffer.limit()
            buffer.limit(minOf(limit, buffer.position() + writeSizes.next()))
            result = inner.writeFully(buffer)
            buffer.limit(limit)
        }
        return result
    }

    override fun close() = inner.close()
}

/** Reicht jeden Aufruf an [inner] weiter und hält fest, was gelesen und geschrieben wurde. */
class RecordingChannel(private val inner: SuspendingChannel) : SuspendingChannel {
    private val writtenBytes = ByteArrayOutputStream()
    val readChunks = mutableListOf<ByteArray>()

    /** `true`, sobald [close] auf diesem Ende aufgerufen wurde (nicht, wenn nur der innere Kanal zu ist). */
    var closed = false
        private set

    /** Alle bisher geschriebenen Bytes. */
    val written: ByteArray get() = writtenBytes.toByteArray()

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
        val start = buffer.position()
        val result = inner.read(buffer)
        if (result is ChannelResult.Ok && result.value > 0) {
            readChunks += ByteArray(result.value).also { buffer.duplicate().position(start).get(it) }
        }
        return result
    }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> {
        val copy = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }
        val result = inner.writeFully(buffer)
        if (result is ChannelResult.Ok) writtenBytes.write(copy)
        return result
    }

    override fun close() {
        closed = true
        inner.close()
    }
}

/**
 * Die Gegenstelle der TLS-Tests: ein TLS-Client auf einer JDK-`SSLEngine`, unabhängig von der
 * Schleife im Produktionscode geschrieben (eigene Puffer, eigenes Auspacken der Pakete, Pakete von
 * Hand gebaut). [encapsulated] verpackt den Handshake in Pakete vom Typ `0x12` und schaltet mit
 * `FINISHED` auf rohes TLS um. [trailingJunk] hängt so viele Nullbytes an die letzte Handshake-Folge.
 * Mit [holdLastFlight] schreibt die Gegenstelle die letzte Handshake-Folge nicht sofort, sondern
 * zusammen mit den ersten verschlüsselten Nutzdaten in einem Schreibvorgang; [denseWriteBytes] nennt
 * dessen Größe.
 */
class JdkTlsPeer(
    private val wire: SuspendingChannel,
    private val engine: SSLEngine,
    private val encapsulated: Boolean,
    private val holdLastFlight: Boolean = false,
    private val payloadPerPacket: Int = PAYLOAD_PER_PACKET,
    private val trailingJunk: Int = 0,
) : SuspendingChannel {
    private var raw = ByteArray(0)
    private var tls = ByteArray(0)
    private var held = ByteArray(0)
    private var inHandshake = true
    private var plain = ByteBuffer.allocate(0)
    private var ended = false

    /** Größe des Schreibvorgangs, der die gehaltene Handshake-Folge und Nutzdaten zusammen trug; sonst 0. */
    var denseWriteBytes = 0
        private set

    /** `null`, wenn der Handshake gelang; sonst die Ausnahme, mit der er endete. */
    suspend fun handshake(): Throwable? = try {
        runHandshake()
        null
    } catch (broken: SSLException) {
        sendPendingAlert()
        broken
    } catch (broken: PeerWireException) {
        broken
    }

    /** Schreibt den Alert, den die Engine nach einem Handshake-Fehler bereithält; Fehler dabei zählen nicht. */
    private suspend fun sendPendingAlert() {
        val flight = ByteArrayOutputStream()
        try {
            var rounds = 0
            while (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP && rounds++ < ALERT_ROUNDS) {
                wrapInto(flight)
            }
            sendFlight(flight)
        } catch (ignored: SSLException) {
            // Der Alert ist nur ein Hinweis an die Gegenstelle.
        } catch (ignored: PeerWireException) {
            // Dito: die Gegenstelle hat den Draht schon geschlossen.
        }
    }

    private suspend fun runHandshake() {
        engine.beginHandshake()
        val flight = ByteArrayOutputStream()
        val scratch = ByteBuffer.allocate(engine.session.applicationBufferSize)
        var finished = false
        while (!finished) {
            finished = when (engine.handshakeStatus) {
                SSLEngineResult.HandshakeStatus.NEED_TASK -> runTasks()

                SSLEngineResult.HandshakeStatus.NEED_WRAP -> wrapInto(flight).handshakeStatus == FINISHED

                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    sendFlight(flight)
                    unwrapOne(scratch)
                }

                else -> throw PeerWireException("Status ${engine.handshakeStatus}")
            }
        }
        val last = framed(flight.toByteArray() + ByteArray(if (flight.size() > 0) trailingJunk else 0))
        if (holdLastFlight) {
            held = last
        } else if (last.isNotEmpty()) {
            send(last)
        }
        tls += raw
        raw = ByteArray(0)
        inHandshake = false
    }

    private fun runTasks(): Boolean {
        var task = engine.delegatedTask
        while (task != null) {
            task.run()
            task = engine.delegatedTask
        }
        return false
    }

    private fun wrapInto(flight: ByteArrayOutputStream): SSLEngineResult {
        val out = ByteBuffer.allocate(engine.session.packetBufferSize)
        val result = engine.wrap(ByteBuffer.allocate(0), out)
        out.flip()
        flight.write(ByteArray(out.remaining()).also { out.get(it) })
        return result
    }

    private suspend fun sendFlight(flight: ByteArrayOutputStream) {
        if (flight.size() == 0) return
        val bytes = framed(flight.toByteArray())
        flight.reset()
        send(bytes)
    }

    private fun framed(bytes: ByteArray): ByteArray =
        if (encapsulated && bytes.isNotEmpty()) handMessage(TYPE_PRELOGIN, bytes, payloadPerPacket) else bytes

    private suspend fun unwrapOne(scratch: ByteBuffer): Boolean {
        scratch.clear()
        val source = ByteBuffer.wrap(tls)
        val result = engine.unwrap(source, scratch)
        tls = tls.copyOfRange(source.position(), tls.size)
        if (result.status == SSLEngineResult.Status.BUFFER_UNDERFLOW) fetchForHandshake()
        if (result.status == SSLEngineResult.Status.CLOSED) throw PeerWireException("close_notify im Handshake")
        return result.handshakeStatus == FINISHED
    }

    private suspend fun fetchForHandshake() {
        if (!encapsulated) {
            tls += receive()
            return
        }
        while (!packetComplete()) raw += receive()
        val length = packetLength()
        if (raw[0].toInt() != TYPE_PRELOGIN) throw PeerWireException("Pakettyp ${raw[0]}")
        tls += raw.copyOfRange(HEADER, length)
        raw = raw.copyOfRange(length, raw.size)
    }

    private fun packetLength(): Int = ((raw[2].toInt() and BYTE) shl BYTE_BITS) or (raw[3].toInt() and BYTE)

    private fun packetComplete(): Boolean = raw.size >= HEADER && raw.size >= packetLength()

    private suspend fun receive(): ByteArray {
        val buffer = ByteBuffer.allocate(READ_SIZE)
        return when (val read = wire.read(buffer)) {
            is ChannelResult.Failed -> throw PeerWireException("Kanal ${read.error.failure}")

            is ChannelResult.Ok -> {
                if (read.value < 0) throw PeerWireException("Strom endet")
                buffer.flip()
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
    }

    private suspend fun send(bytes: ByteArray) {
        val sent = wire.writeFully(ByteBuffer.wrap(bytes))
        if (sent is ChannelResult.Failed) throw PeerWireException("Kanal ${sent.error.failure}")
    }

    override suspend fun read(buffer: ByteBuffer): ChannelResult<Int> {
        var failure: ChannelError? = null
        while (!plain.hasRemaining() && !ended && failure == null) failure = decryptMore()
        return when {
            failure != null -> ChannelResult.Failed(failure)
            !plain.hasRemaining() -> ChannelResult.Ok(-1)
            else -> ChannelResult.Ok(takePlain(buffer))
        }
    }

    private fun takePlain(buffer: ByteBuffer): Int {
        val count = minOf(plain.remaining(), buffer.remaining())
        val limit = plain.limit()
        plain.limit(plain.position() + count)
        buffer.put(plain)
        plain.limit(limit)
        return count
    }

    private suspend fun decryptMore(): ChannelError? = try {
        val scratch = ByteBuffer.allocate(engine.session.applicationBufferSize)
        val source = ByteBuffer.wrap(tls)
        val result = engine.unwrap(source, scratch)
        tls = tls.copyOfRange(source.position(), tls.size)
        scratch.flip()
        plain = scratch
        if (result.status == SSLEngineResult.Status.CLOSED) ended = true
        if (result.status == SSLEngineResult.Status.BUFFER_UNDERFLOW) tls += receiveOrEnd()
        null
    } catch (broken: SSLException) {
        ChannelError(ChannelFailure.IO_FAILURE, broken.javaClass.simpleName)
    } catch (broken: PeerWireException) {
        ChannelError(ChannelFailure.IO_FAILURE, broken.message.orEmpty())
    }

    private suspend fun receiveOrEnd(): ByteArray {
        val buffer = ByteBuffer.allocate(READ_SIZE)
        val read = wire.read(buffer)
        if (read is ChannelResult.Ok && read.value < 0) {
            engine.closeInbound()
            ended = true
            return ByteArray(0)
        }
        if (read is ChannelResult.Failed) throw PeerWireException("Kanal ${read.error.failure}")
        buffer.flip()
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    override suspend fun writeFully(buffer: ByteBuffer): ChannelResult<Unit> {
        val out = ByteArrayOutputStream()
        out.write(held)
        val prefix = held.size
        held = ByteArray(0)
        while (buffer.hasRemaining()) {
            val encrypted = ByteBuffer.allocate(engine.session.packetBufferSize)
            engine.wrap(buffer, encrypted)
            encrypted.flip()
            out.write(ByteArray(encrypted.remaining()).also { encrypted.get(it) })
        }
        if (prefix > 0) denseWriteBytes = out.size()
        return wire.writeFully(ByteBuffer.wrap(out.toByteArray()))
    }

    /** Löst in TLS 1.3 ein KeyUpdate aus (JDK: `beginHandshake` nach dem Handshake) und schreibt es roh. */
    suspend fun requestKeyUpdate() {
        engine.beginHandshake()
        val flight = ByteArrayOutputStream()
        var rounds = 0
        while (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP && rounds++ < ALERT_ROUNDS) {
            wrapInto(flight)
        }
        send(flight.toByteArray())
    }

    /** Sendet `close_notify` auf den Draht. */
    suspend fun sendCloseNotify() {
        engine.closeOutbound()
        val encrypted = ByteBuffer.allocate(engine.session.packetBufferSize)
        engine.wrap(ByteBuffer.allocate(0), encrypted)
        encrypted.flip()
        send(ByteArray(encrypted.remaining()).also { encrypted.get(it) })
    }

    override fun close() = wire.close()

    companion object {
        const val TYPE_PRELOGIN = 0x12
        private const val PAYLOAD_PER_PACKET = 4000
        private const val HEADER = 8
        private const val BYTE = 0xFF
        private const val BYTE_BITS = 8
        private const val READ_SIZE = 4096
        private const val ALERT_ROUNDS = 4
        private val FINISHED = SSLEngineResult.HandshakeStatus.FINISHED

        /** Eine Client-Engine für `localhost`; Hostnamenprüfung (HTTPS) ist an. */
        fun clientEngine(
            context: SSLContext,
            protocols: List<String>,
            suites: List<String>,
            alpn: List<String> = emptyList(),
        ): SSLEngine {
            val engine = context.createSSLEngine("localhost", 1)
            engine.useClientMode = true
            val parameters: SSLParameters = engine.sslParameters
            parameters.protocols = protocols.toTypedArray()
            parameters.cipherSuites = suites.toTypedArray()
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            if (alpn.isNotEmpty()) parameters.applicationProtocols = alpn.toTypedArray()
            engine.sslParameters = parameters
            return engine
        }
    }
}

/**
 * Ein ClientHello nach TLS 1.1, von Hand gebaut: Record-Version `03 01`, Hello-Version `03 02`,
 * eine Suite (`TLS_RSA_WITH_AES_128_CBC_SHA`), keine Erweiterungen. Der Server mit TLS 1.2 als
 * Mindestversion muss es ablehnen.
 */
fun tls11ClientHello(): ByteArray = hex(
    "16 03 01 00 2D  01 00 00 29  03 02  " + "00 ".repeat(32) + " 00  00 02 00 2F  01 00",
)
