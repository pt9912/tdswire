package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.JdkTlsPeer
import io.github.pt9912.tdswire.testsupport.TestKeys
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import io.github.pt9912.tdswire.transport.listener.connectTo
import io.github.pt9912.tdswire.transport.messaging.MessageReader
import io.github.pt9912.tdswire.transport.messaging.MessageWriter
import io.github.pt9912.tdswire.transport.messaging.ReadOutcome
import io.github.pt9912.tdswire.transport.tls.TlsParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Die Richtlinie `REQUIRED` über den Handler des Prozesses: TLS im TDS-7.4-Pfad, dann Login (`SPEC-010`). */
internal class TlsWiringTest : RuntimeTestBase() {
    private fun environment(): Map<String, String> {
        val directory = checkNotNull(System.getProperty("tdswire.testkeys"))
        return mapOf(
            ConfigKeys.LISTEN_HOST to LOOPBACK,
            ConfigKeys.LISTEN_PORT to "0",
            ConfigKeys.TLS_KEYSTORE_FILE to "$directory/gueltig.p12",
            ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to file("kennwort.txt", "tdswire-test\n"),
            ConfigKeys.AUTH_USERS_FILE to file("users.txt", "alice\t${pbkdf2Value("pw")}\n"),
        )
    }

    private suspend fun loginOverTls(port: Int, protocols: List<String>, password: String): ReadOutcome {
        val wire = connectTo(port)
        val prelogin = TdsMessage(PacketType.PRELOGIN, SessionVectors.preloginPayload(SessionVectors.ENCRYPT_ON))
        MessageWriter(wire, PACKET_SIZE).write(prelogin)
        val answer = assertIs<ReadOutcome.Message>(MessageReader(wire).next()).message
        assertEquals(SessionVectors.ENCRYPT_REQ, encryptionOf(answer.payload))
        val engine = JdkTlsPeer.clientEngine(TestKeys.clientContext(), protocols, TlsParameters.SPEC_023.cipherSuites)
        val peer = JdkTlsPeer(wire, engine, encapsulated = true)
        assertNull(peer.handshake())
        val login = SessionVectors.login7 {
            userName = "alice"
            this.password = password.toCharArray()
        }
        MessageWriter(peer, PACKET_SIZE).write(login)
        return MessageReader(peer).next().also { wire.close() }
    }

    /**
     * Mit `REQUIRED` antwortet der Server `ENCRYPT_REQ`, führt den Handschlag im 7.4-Pfad aus (TLS 1.3 und TLS
     * 1.2, je gegen eine JDK-Gegenstelle) und nimmt danach den Login über TLS an; ein falsches Passwort wird mit
     * der festen Ablehnung beantwortet. Die Gegenstelle ist kein Referenzclient; TDS 8.0 ist nicht verdrahtet.
     *
     * @spec SPEC-010
     * @spec SPEC-028
     */
    @Test
    fun `REQUIRED fuehrt den TLS-Handschlag aus und nimmt den Login ueber TLS an`() = blocking {
        val server = running(environment())
        listOf(listOf("TLSv1.3"), listOf("TLSv1.2")).forEach { protocols ->
            val ok = assertIs<ReadOutcome.Message>(loginOverTls(server.port, protocols, "pw")).message
            assertTrue(TokenDecoder(ok.payload).decodeAll().any { it is LoginAck }, "Annahme über $protocols")
            val refused = assertIs<ReadOutcome.Message>(loginOverTls(server.port, protocols, "falsch")).message
            assertTrue(TokenDecoder(refused.payload).decodeAll().none { it is LoginAck }, "Ablehnung über $protocols")
        }
    }

    /**
     * Mit `REQUIRED` wird ein Client, der keine Verschlüsselung anbietet (`ENCRYPT_NOT_SUP`), ohne Antwort
     * getrennt; es gibt keinen Rückfall auf unverschlüsselt (`SPEC-010`).
     *
     * @spec SPEC-010
     */
    @Test
    fun `REQUIRED faellt nicht auf unverschluesselt zurueck`() = blocking {
        val server = running(environment())
        RawClient(server.port).use {
            it.send(preloginWire(SessionVectors.ENCRYPT_NOT_SUP))
            assertTrue(it.closedByServerWithoutBytes())
        }
    }

    private companion object {
        const val PACKET_SIZE = 4096
    }
}
