package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.LoginSettings
import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.domain.telemetry.TlsFirstNotOffered
import io.github.pt9912.tdswire.domain.telemetry.TlsFirstRefusal
import io.github.pt9912.tdswire.domain.telemetry.TlsHandshakeFailed
import io.github.pt9912.tdswire.protocol.session.AuthPool
import io.github.pt9912.tdswire.protocol.session.EncryptionPolicy
import io.github.pt9912.tdswire.protocol.session.HandshakeConfig
import io.github.pt9912.tdswire.protocol.session.RecordingAdapter
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.JdkTlsPeer
import io.github.pt9912.tdswire.testsupport.KeyVariants
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.tls11ClientHello
import io.github.pt9912.tdswire.transport.channel.ChannelResult
import io.github.pt9912.tdswire.transport.channel.MemoryChannel
import io.github.pt9912.tdswire.transport.channel.SuspendingChannel
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.listener.Connection
import io.github.pt9912.tdswire.transport.listener.ConnectionEnd
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.RecordingEvents
import io.github.pt9912.tdswire.transport.listener.connectTo
import io.github.pt9912.tdswire.transport.listener.eventually
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.tls.MaterialResult
import io.github.pt9912.tdswire.transport.tls.TlsChannel
import io.github.pt9912.tdswire.transport.tls.TlsMaterial
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PACKET_SIZE = 4096
private const val TLS_RECORD_FIRST_BYTE = 0x16
private const val TRUNCATED_HELLO_BYTES = 20
private const val ANSWER_WAIT_MILLIS = 5_000L
private const val USER = "alice"
private const val PASSWORD = "pw"

private fun keyEnvironment(files: TempFilesTest): Map<String, String> {
    val directory = checkNotNull(System.getProperty("tdswire.testkeys"))
    return mapOf(
        ConfigKeys.LISTEN_HOST to LOOPBACK,
        ConfigKeys.LISTEN_PORT to "0",
        ConfigKeys.TLS_KEYSTORE_FILE to "$directory/gueltig.p12",
        ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to files.file("kennwort.txt", "tdswire-test\n"),
        ConfigKeys.AUTH_USERS_FILE to files.file("users.txt", "$USER\t${pbkdf2Value(PASSWORD)}\n"),
    )
}

/** Eine JDK-Client-Engine für den TDS-8.0-Pfad; [alpn] ist das angebotene ALPN. */
private fun strictEngine(protocol: String, alpn: List<String> = listOf(TlsChannel.ALPN_TDS_8_0)): SSLEngine =
    JdkTlsPeer.clientEngine(TestKeys.clientContext(), listOf(protocol), TlsParameters.SPEC_023.cipherSuites, alpn)

private fun preloginRequiringTls() =
    TdsMessage(PacketType.PRELOGIN, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON))

/** Ein vollständiger TLS-Record vom Typ Handshake (Version 1.2 im Kopf), dessen Rumpf [body] im Klartext ist. */
private fun plaintextInRecord(body: ByteArray): ByteArray =
    byteArrayOf(TLS_RECORD_FIRST_BYTE.toByte(), 3, 3, (body.size shr 8).toByte(), body.size.toByte()) + body

/** Ein Ausgang des Logins über den Kanal [peer]: PRELOGIN, dann LOGIN7; die Antwort auf LOGIN7. */
private suspend fun loginThrough(peer: JdkTlsPeer, password: String): ReadOutcome {
    MessageWriter(peer, PACKET_SIZE).write(preloginRequiringTls())
    val reader = MessageReader(peer)
    val prelogin = assertIs<ReadOutcome.Message>(reader.next()).message
    assertEquals(SessionVectors.ENCRYPT_REQ, encryptionOf(prelogin.payload))
    val login = SessionVectors.login7 {
        userName = USER
        this.password = password.toCharArray()
    }
    MessageWriter(peer, PACKET_SIZE).write(login)
    return reader.next()
}

private fun hasLoginAck(outcome: ReadOutcome): Boolean =
    TokenDecoder(assertIs<ReadOutcome.Message>(outcome).message.payload).decodeAll().any { it is LoginAck }

/**
 * Der Pfad ab dem ersten Byte (TDS 8.0) im Serverprozess mit `ENCRYPTION_POLICY=REQUIRED` (`SPEC-001`, `SPEC-020`,
 * `SPEC-028`): Der echte Handler läuft über einen Socket, die Wahl des Pfads trifft er am ersten Byte. Die
 * Gegenstellen sind die JDK-`SSLEngine` und ein Rohbytes-Client, kein Referenzclient.
 */
internal class Tds8WiringTest : RuntimeTestBase() {
    private val events = RecordingEvents()

    private suspend fun start() = running(keyEnvironment(this), Wiring(telemetry = sink, events = events))

    private suspend fun assertStillServesStrict(port: Int) {
        val wire = connectTo(port)
        val peer = JdkTlsPeer(wire, strictEngine("TLSv1.3"), encapsulated = false)
        assertNull(peer.handshake())
        assertTrue(hasLoginAck(loginThrough(peer, PASSWORD)), "gültige Anmeldung nach dem Fehler")
        wire.close()
    }

    private suspend fun assertHandshakeLogged(expected: String) {
        eventually("Handschlag-Fehler im Ereignis") { sink.of<TlsHandshakeFailed>().isNotEmpty() }
        val failed = sink.of<TlsHandshakeFailed>()
        assertTrue(failed.any { it.failure == expected }, sink.events.toString())
        assertTrue(failed.all { it.clientProfile == "tds-8.0:strict" }, sink.events.toString())
    }

    /**
     * Eine Verbindung, die mit TLS beginnt, durchläuft `CONNECTED → TLS_NEGOTIATION → PRELOGIN → LOGIN7 → READY`:
     * ALPN `tds/8.0`, TLS 1.3 und TLS 1.2 je gegen eine JDK-Gegenstelle, PRELOGIN im Tunnel mit `ENCRYPT_REQ`, bei
     * richtigem Passwort `LoginAck`, bei falschem keines. Nach dem Ende der Gegenstelle endet die Verbindung wie im
     * TDS-7.4-Pfad: der Handler kehrt zurück, kein Handlerfehler.
     *
     * @spec SPEC-001
     * @spec SPEC-020
     * @spec SPEC-028
     */
    @Test
    fun `Eine Verbindung mit TLS ab dem ersten Byte erreicht READY und endet wie im 7_4-Pfad`() = blocking {
        val server = start()
        listOf("TLSv1.3", "TLSv1.2").forEach { protocol ->
            val engine = strictEngine(protocol)
            val wire = connectTo(server.port)
            val peer = JdkTlsPeer(wire, engine, encapsulated = false)
            assertNull(peer.handshake(), "Handschlag über $protocol")
            assertEquals(TlsChannel.ALPN_TDS_8_0, engine.applicationProtocol)
            assertEquals(protocol, engine.session.protocol)
            assertTrue(hasLoginAck(loginThrough(peer, PASSWORD)), "Annahme über $protocol")
            wire.close()
            val refusedWire = connectTo(server.port)
            val refusedPeer = JdkTlsPeer(refusedWire, strictEngine(protocol), encapsulated = false)
            assertNull(refusedPeer.handshake())
            assertTrue(!hasLoginAck(loginThrough(refusedPeer, "falsch")), "Ablehnung über $protocol")
            refusedWire.close()
        }
        eventually("vier beendete Verbindungen") { events.closed().size == 4 }
        val ended = events.closed()
        assertTrue(ended.all { it.end == ConnectionEnd.HANDLER_RETURNED && it.failure == null }, "$ended")
    }

    /**
     * Rohbytes mit dem ersten Byte `0x16`, die kein gültiger TLS-Anfang sind, beenden die Verbindung: ein
     * ClientHello nach TLS 1.1 (`HANDSHAKE_FAILED`) und ein Klartext-PRELOGIN im Rumpf eines TLS-Records
     * (`HANDSHAKE_FAILED`) ohne ein Byte Antwort, ein abgebrochenes ClientHello (`CONNECTION_ABORTED`, der Client
     * schließt selbst) mit der Logzeile, ohne Prüfung auf Antwortbytes. Es gibt keinen Rückfall auf Klartext
     * (Festlegung des Slice, nicht gemessen). Danach nimmt derselbe Server eine Anmeldung im TDS-8.0-Pfad an.
     *
     * @spec SPEC-001
     * @spec SPEC-028
     */
    @Test
    fun `Rohbytes ab dem ersten Byte 0x16 ohne gueltiges TLS enden ohne Antwort und ohne Rueckfall auf Klartext`() =
        blocking {
            val server = start()
            val cases = listOf(
                "HANDSHAKE_FAILED" to tls11ClientHello(),
                "CONNECTION_ABORTED" to tls11ClientHello().copyOf(TRUNCATED_HELLO_BYTES),
                "HANDSHAKE_FAILED" to plaintextInRecord(preloginWire(SessionVectors.ENCRYPT_OFF)),
            )
            cases.forEach { (reason, bytes) ->
                sink.events.clear()
                RawClient(server.port).use {
                    it.send(bytes)
                    if (reason == "HANDSHAKE_FAILED") assertTrue(it.closedByServerWithoutBytes(), reason)
                }
                assertHandshakeLogged(reason)
                assertStillServesStrict(server.port)
            }
        }

    /**
     * Eine Gegenstelle, die TLS ab dem ersten Byte beginnt, aber kein ALPN `tds/8.0` anbietet (hier keines, dann
     * `h2`), kommt nicht zum PRELOGIN: der Handler trennt ohne TDS-Antwort (ein TLS-Alert ist nicht geprüft); die
     * Zeile nennt `ALPN_MISMATCH` (kein ALPN) oder `HANDSHAKE_FAILED` (`h2`, die Engine weist es selbst ab).
     *
     * @spec SPEC-020
     * @spec SPEC-028
     */
    @Test
    fun `Ohne ALPN tds 8_0 kommt keine Verbindung zum PRELOGIN`() = blocking {
        val server = start()
        listOf(emptyList<String>() to "ALPN_MISMATCH", listOf("h2") to "HANDSHAKE_FAILED").forEach { (alpn, reason) ->
            sink.events.clear()
            val wire = connectTo(server.port)
            val peer = JdkTlsPeer(wire, strictEngine("TLSv1.3", alpn), encapsulated = false)
            if (peer.handshake() == null) {
                val answer = withTimeoutOrNull(ANSWER_WAIT_MILLIS) {
                    MessageWriter(peer, PACKET_SIZE).write(preloginRequiringTls())
                    MessageReader(peer).next()
                }
                assertTrue(answer !is ReadOutcome.Message, "Antwort ohne ALPN $alpn")
            }
            wire.close()
            assertHandshakeLogged(reason)
        }
        assertStillServesStrict(server.port)
    }

    /**
     * Beide Pfade laufen am selben Port: das erste Byte `0x12` (PRELOGIN im Klartext) wählt den TDS-7.4-Pfad mit
     * `ENCRYPT_REQ` und dem Handschlag in `0x12`-Paketen, ein erstes Byte, das weder `0x16` noch ein gültiger
     * Pakettyp ist, endet ohne Antwort (Festlegung des Slice, nicht gemessen).
     *
     * @spec SPEC-001
     * @spec SPEC-028
     */
    @Test
    fun `Das erste Byte waehlt den Pfad am selben Port`() = blocking {
        val server = start()
        RawClient(server.port).use {
            it.send(preloginWire(SessionVectors.ENCRYPT_ON))
            assertEquals(SessionVectors.ENCRYPT_REQ, encryptionOf(assertNotNull(it.readMessage()).payload))
        }
        RawClient(server.port).use {
            it.send(ByteArray(PACKET_SIZE) { 0x41 })
            assertTrue(it.closedByServerWithoutBytes(), "Antwort auf ein erstes Byte 0x41")
        }
        assertStillServesStrict(server.port)
    }

    /**
     * Im unverschlüsselten Testmodus gibt es kein TLS-Material: ein erstes Byte `0x16` beendet die Verbindung ohne
     * Antwort und mit einer Logzeile; der TDS-7.4-Pfad bleibt unverändert (`SPEC-010`).
     *
     * @spec SPEC-010
     * @spec SPEC-028
     */
    @Test
    fun `Im Testmodus ohne TLS wird ein TLS-Anfang ohne Antwort getrennt`() = blocking {
        val server = running(loopbackEnvironment(file("users.txt", "alice\t${pbkdf2Value(PASSWORD)}\n")))
        RawClient(server.port).use {
            it.send(tls11ClientHello())
            assertTrue(it.closedByServerWithoutBytes(), "Antwort ohne TLS-Material")
        }
        eventually("Ereignis zum TLS-Anfang") { sink.of<TlsFirstNotOffered>().isNotEmpty() }
        assertEquals(TlsFirstRefusal.NO_TLS_MATERIAL, sink.of<TlsFirstNotOffered>().single().reason)
        assertEquals("tds-8.0:strict", sink.of<TlsFirstNotOffered>().single().clientProfile)
        RawClient(server.port).use {
            it.send(preloginWire(SessionVectors.ENCRYPT_OFF))
            assertEquals(SessionVectors.ENCRYPT_NOT_SUP, encryptionOf(assertNotNull(it.readMessage()).payload))
        }
    }
}

/** Ein Handler mit TLS-Material und der Richtlinie `TEST_UNENCRYPTED`, die der Betrieb nicht zusammenbaut. */
internal class Tds8DefenseTest {
    private class FakeConnection(override val channel: SuspendingChannel) : Connection {
        override val id = 1L

        override fun loginCompleted() = Unit

        override fun requestRunning(running: Boolean) = Unit
    }

    /**
     * Ein `HandlerSetup` mit Material und `TEST_UNENCRYPTED` (die Konfiguration lässt die Kombination nicht zu,
     * `HandshakeConfig` verweigert sie mit `TLS_FIRST`): ein erstes Byte `0x16` führt zur Rückkehr des Handlers ohne
     * Ausnahme, ohne Antwort und mit der Logzeile, die die Richtlinie nennt, nicht das fehlende Material.
     *
     * @spec SPEC-010
     * @spec SPEC-028
     */
    @Test
    fun `Material mit TEST_UNENCRYPTED bietet den Pfad ab dem ersten Byte nicht an`() = blocking {
        val store = KeyVariants.file(TestKeys.VALID)
        val loaded = assertIs<MaterialResult.Loaded>(TlsMaterial.load(store, KeyVariants.password()))
        val sink = CapturedEvents()
        val setup = HandlerSetup(
            handshake = HandshakeConfig(encryptionPolicy = EncryptionPolicy.TEST_UNENCRYPTED),
            adapter = RecordingAdapter { error("kein Adapteraufruf vor dem Handschlag") },
            authPool = AuthPool(),
            login = LoginSettings(timeoutSeconds = 5, maxPreloginMessageBytes = 65_536),
            tls = loaded.material,
            telemetry = Telemetry(sink),
        )
        val (local, remote) = MemoryChannel.pair(capacity = 64)
        remote.writeFully(ByteBuffer.wrap(byteArrayOf(TLS_RECORD_FIRST_BYTE.toByte())))
        HandshakeConnectionHandler(setup).handle(FakeConnection(local))
        val refused = sink.events.filterIsInstance<TlsFirstNotOffered>().single()
        assertEquals(TlsFirstRefusal.POLICY_NOT_REQUIRED, refused.reason)
        assertEquals(1, sink.events.size)
        local.close()
        assertEquals(ChannelResult.Ok(-1), remote.read(ByteBuffer.allocate(1)))
    }
}

/** Derselbe Pfad am Serverprozess (Kind-JVM mit `main`), nicht im Prozess des Tests. */
internal class Tds8ProcessTest : TempFilesTest() {
    /**
     * Ein Serverprozess nimmt eine Anmeldung ab dem ersten Byte (JDK-Gegenstelle, ALPN `tds/8.0`) an und trennt
     * ein erstes Byte `0x16` ohne gültigen TLS-Anfang ohne Antwort; die Ausgabe nennt weder Benutzer noch Passwort.
     *
     * @spec SPEC-001
     * @spec SPEC-020
     */
    @Test
    fun `Serverprozess nimmt TLS ab dem ersten Byte an`() = blocking {
        ServerProcess(keyEnvironment(this@Tds8ProcessTest)).use { server ->
            val port = server.port()
            val wire = connectTo(port)
            val peer = JdkTlsPeer(wire, strictEngine("TLSv1.3"), encapsulated = false)
            assertNull(peer.handshake())
            assertTrue(hasLoginAck(loginThrough(peer, PASSWORD)))
            wire.close()
            RawClient(port).use {
                it.send(tls11ClientHello())
                assertTrue(it.closedByServerWithoutBytes(), "Antwort auf ein ungültiges TLS")
            }
            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
            listOf(USER, PASSWORD).forEach { word ->
                assertTrue(server.output.none { it.contains(word) }, "Ausgabe nennt $word: ${server.output}")
            }
        }
    }
}
