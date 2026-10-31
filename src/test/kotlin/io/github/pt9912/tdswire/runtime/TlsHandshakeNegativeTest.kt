package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.JdkTlsPeer
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.tls11ClientHello
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.connectTo
import io.github.pt9912.tdswire.transport.listener.eventually
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Handschlag-Negativfälle am Serverprozess mit `ENCRYPTION_POLICY=REQUIRED` (`SPEC-010`, `SPEC-043`, `TDS-QA-03`):
 * Der echte Handler läuft über einen Socket; die Gegenstelle ist ein Rohbytes-Client oder die JDK-`SSLEngine`, kein
 * Referenzclient. Jeder Fall endet ohne ein Byte Antwort und ohne `LoginAck`, das Log nennt nur die Art
 * (`TLS-Handschlag gescheitert: <Grund>`), und danach nimmt derselbe Server eine gültige Anmeldung über TLS an.
 */
internal class TlsHandshakeNegativeTest : RuntimeTestBase() {
    private val user = "Zk9-alice"
    private val password = "Zk9-geheim"

    private fun environment(): Map<String, String> {
        val directory = checkNotNull(System.getProperty("tdswire.testkeys"))
        return mapOf(
            ConfigKeys.LISTEN_HOST to LOOPBACK,
            ConfigKeys.LISTEN_PORT to "0",
            ConfigKeys.TLS_KEYSTORE_FILE to "$directory/gueltig.p12",
            ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to file("kennwort.txt", "tdswire-test\n"),
            ConfigKeys.AUTH_USERS_FILE to file("users.txt", "$user\t${pbkdf2Value(password)}\n"),
        )
    }

    private fun preloginRequiringTls(client: RawClient) {
        client.send(preloginWire(SessionVectors.ENCRYPT_ON))
        assertEquals(SessionVectors.ENCRYPT_REQ, encryptionOf(assertNotNull(client.readMessage()).payload))
    }

    private fun plaintextLogin() = handMessage(
        LOGIN7_TYPE,
        SessionVectors.login7Payload {
            userName = user
            this.password = this@TlsHandshakeNegativeTest.password.toCharArray()
        },
        4000,
    )

    private suspend fun assertHandshakeLogged(expected: String) {
        eventually("Handschlag-Fehler im Log") { log.lines.any { it.startsWith("TLS-Handschlag gescheitert") } }
        val lines = log.lines.toList()
        assertTrue(lines.contains("TLS-Handschlag gescheitert: $expected"), lines.toString())
        lines.forEach { line ->
            listOf("Zk9", "CN=", ".p12", "tdswire-test", "-----", "kennwort", "users").forEach {
                assertTrue(!line.contains(it), "Logzeile nennt $it: $line")
            }
        }
    }

    private suspend fun assertStillServes(port: Int) {
        val wire = connectTo(port)
        MessageWriter(wire, PACKET_SIZE)
            .write(TdsMessage(PacketType.PRELOGIN, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON)))
        assertIs<ReadOutcome.Message>(MessageReader(wire).next())
        val engine = JdkTlsPeer.clientEngine(
            TestKeys.clientContext(),
            listOf("TLSv1.2"),
            TlsParameters.SPEC_023.cipherSuites,
        )
        val peer = JdkTlsPeer(wire, engine, encapsulated = true)
        assertNull(peer.handshake())
        val login = SessionVectors.login7 {
            userName = user
            this.password = this@TlsHandshakeNegativeTest.password.toCharArray()
        }
        MessageWriter(peer, PACKET_SIZE).write(login)
        val answer = assertIs<ReadOutcome.Message>(MessageReader(peer).next()).message
        val tokens = TokenDecoder(answer.payload).decodeAll()
        assertTrue(tokens.any { it is LoginAck }, "gueltige Anmeldung nach dem Fehler")
        wire.close()
    }

    /**
     * Ein Client, der auf `ENCRYPT_REQ` hin kein TLS beginnt und sein LOGIN7 im Klartext schickt (Herabstufung),
     * wird ohne Antwort getrennt: Der Server nimmt die Anmeldung nicht an und liest sie nicht als Anmeldung, das Log
     * nennt `PROTOCOL_VIOLATION` und weder Kennung noch Passwort.
     *
     * @spec SPEC-010
     * @spec SPEC-023
     */
    @Test
    fun `Ein LOGIN7 im Klartext nach ENCRYPT_REQ wird nicht angenommen`() = blocking {
        val server = running(environment())
        RawClient(server.port).use {
            preloginRequiringTls(it)
            it.send(plaintextLogin())
            assertTrue(it.closedByServerWithoutBytes(), "Antwort auf ein Klartext-LOGIN7")
        }
        assertHandshakeLogged("PROTOCOL_VIOLATION")
        assertStillServes(server.port)
    }

    /**
     * Ein ClientHello nach TLS 1.1 im Pakettyp `0x12` wird ohne Antwort getrennt; der Server bietet keine Version
     * unter TLS 1.2 an (`SPEC-023`). Die Zeile nennt den Grund `HANDSHAKE_FAILED`.
     *
     * @spec SPEC-023
     */
    @Test
    fun `Ein ClientHello nach TLS 1_1 beendet den Handschlag`() = blocking {
        val server = running(environment())
        RawClient(server.port).use {
            preloginRequiringTls(it)
            it.send(handMessage(PRELOGIN_TYPE, tls11ClientHello(), 4000))
            assertTrue(it.closedByServerWithoutBytes(), "Antwort auf TLS 1.1")
        }
        assertHandshakeLogged("HANDSHAKE_FAILED")
        assertStillServes(server.port)
    }

    /**
     * Bytes, die kein TLS sind, im Pakettyp `0x12` beenden den Handschlag ohne Antwort. Der Fall belegt, dass der
     * Handler nach `ENCRYPT_REQ` nie auf Klartext-Auswertung zurückfällt.
     *
     * @spec SPEC-010
     */
    @Test
    fun `Bytes ohne TLS im Handschlagpaket beenden den Handschlag`() = blocking {
        val server = running(environment())
        RawClient(server.port).use {
            preloginRequiringTls(it)
            it.send(handMessage(PRELOGIN_TYPE, ByteArray(64) { 0x41 }, 4000))
            assertTrue(it.closedByServerWithoutBytes(), "Antwort auf Nicht-TLS")
        }
        assertHandshakeLogged("HANDSHAKE_FAILED")
        assertStillServes(server.port)
    }

    /**
     * Bricht die Gegenstelle mitten im ClientHello ab, endet die Verbindung als abgebrochen; der Server läuft weiter
     * und nimmt die nächste Anmeldung an (kein Zustand der Verbindung bleibt hängen).
     *
     * @spec SPEC-010
     */
    @Test
    fun `Ein Abbruch mitten im Handschlag laesst den Server weiterlaufen`() = blocking {
        val server = running(environment())
        RawClient(server.port).use {
            preloginRequiringTls(it)
            val partial = tls11ClientHello().copyOf(TRUNCATED_HELLO_BYTES)
            val message = handMessage(PRELOGIN_TYPE, partial, 4000)
            it.send(message)
        }
        assertHandshakeLogged("CONNECTION_ABORTED")
        assertStillServes(server.port)
    }

    /**
     * Ein Client, der dem Zertifikat des Servers nicht vertraut, bricht den Handschlag ab; der Server nimmt keine
     * Anmeldung an, das Log nennt die Art, keinen Namen aus dem Zertifikat.
     *
     * @spec SPEC-010
     * @spec SPEC-043
     */
    @Test
    fun `Ein Client ohne Vertrauen in das Zertifikat kommt nicht zur Anmeldung`() = blocking {
        val server = running(environment())
        val wire = connectTo(server.port)
        MessageWriter(wire, PACKET_SIZE)
            .write(TdsMessage(PacketType.PRELOGIN, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON)))
        assertNotNull(MessageReader(wire).next())
        val engine = JdkTlsPeer.clientEngine(
            TestKeys.clientContext(TestKeys.OTHER_NAME),
            listOf("TLSv1.3"),
            TlsParameters.SPEC_023.cipherSuites,
        )
        val failure = JdkTlsPeer(wire, engine, encapsulated = true).handshake()
        assertNotNull(failure, "der Client hat dem fremden Zertifikat vertraut")
        wire.close()
        assertHandshakeLogged("HANDSHAKE_FAILED")
        assertStillServes(server.port)
    }

    private companion object {
        const val PACKET_SIZE = 4096
        const val TRUNCATED_HELLO_BYTES = 20
    }
}
