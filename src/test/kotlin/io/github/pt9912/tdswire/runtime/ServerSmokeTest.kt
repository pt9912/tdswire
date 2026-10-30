package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.protocol.session.LoginResponses
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.Login7Builder
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Rohbytes-Smoke der Welle (`make smoke`, Teil von `make gates`): ein Serverprozess, gestartet über `main`
 * mit dem JVM-Flag und einer Testkonfiguration, beantwortet einen Rohbytes-Client vom Prozessstart bis zur
 * Login-Antwort. Der Client baut seine Bytes von Hand (kein Codec des Produkts, kein Referenzclient); der Smoke
 * belegt, was der Server auf diese Bytes antwortet, nicht dass `Microsoft.Data.SqlClient` die Antwort annimmt.
 */
internal class ServerSmokeTest : TempFilesTest() {
    private fun environment(vararg more: Pair<String, String>): Map<String, String> {
        val users = "alice\t{plain}secret\nbob\t${pbkdf2Value("geheim-bob")}\n"
        return mapOf(
            ConfigKeys.LISTEN_HOST to LOOPBACK,
            ConfigKeys.LISTEN_PORT to "0",
            ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
            ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
            ConfigKeys.AUTH_USERS_FILE to file("users.txt", users),
        ) + more
    }

    private fun connect(port: Int, encryption: Int = SessionVectors.ENCRYPT_OFF): RawClient {
        val client = RawClient(port)
        client.send(preloginWire(encryption))
        val answer = assertNotNull(client.readMessage())
        assertEquals(4, answer.type)
        assertEquals(SessionVectors.ENCRYPT_NOT_SUP, encryptionOf(answer.payload))
        return client
    }

    private fun loginPayload(user: String, password: String, more: Login7Builder.() -> Unit = {}): ByteArray =
        SessionVectors.login7Payload {
            userName = user
            this.password = password.toCharArray()
            more()
        }

    private fun loginWire(user: String, password: String): ByteArray =
        handMessage(LOGIN7_TYPE, loginPayload(user, password), 4000)

    /** Je ein LOGIN7 mit SSPI-Merkmal, mit FeatureExt `0x02` und mit Passwortwechsel. */
    private fun unsupportedLogins(): List<ByteArray> = listOf<Login7Builder.() -> Unit>(
        {
            optionFlags2 = 0x03 or 0x80
            sspi = ByteArray(8) { it.toByte() }
        },
        {
            optionFlags3 = 0x10
            features = listOf(0x02 to byteArrayOf(1, 0, 0, 0, 0))
        },
        {
            optionFlags3 = 0x01
            changePassword = "neu".toCharArray()
        },
    ).map { loginPayload("alice", "secret", it) }

    private fun answerTo(port: Int, login: ByteArray): RawMessage {
        connect(port).use { client ->
            client.send(handMessage(LOGIN7_TYPE, login, 4000))
            val answer = assertNotNull(client.readMessage())
            assertTrue(client.closedByServerWithoutBytes(), "nach der Antwort schließt der Server")
            return answer
        }
    }

    private fun assertLoginAck(message: RawMessage) {
        assertEquals(4, message.type)
        assertTrue(TokenDecoder(message.payload).decodeAll().any { it is LoginAck }, message.bytes.toHex())
    }

    private val rejection = handPacket(4, 1, LoginResponses.rejection.payload)

    /**
     * Vom Prozessstart bis zur Login-Antwort: Start über `main`, PRELOGIN, gültiges LOGIN7 (`plain` und
     * `pbkdf2`) führt zu LOGINACK; unbekannte Kennung und falsches Passwort ergeben dieselben Bytes wie die
     * feste Ablehnung; SSPI-Merkmal, FeatureExt `0x02` und Passwortwechsel werden mit denselben Bytes abgelehnt;
     * ein LOGIN7 in 20-Byte-Paketen, byteweise gesendet, führt zur Anmeldung. Danach beendet SIGTERM den
     * Prozess, eine angemeldete Verbindung wird getrennt, und die Standardausgabe nennt weder Benutzer noch Passwort.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     * @spec SPEC-010
     * @spec SPEC-012
     * @spec SPEC-044
     */
    @Test
    fun `Serverprozess beantwortet den Login-Pfad mit Rohbytes`() {
        ServerProcess(environment()).use { server ->
            val port = server.port()
            val open = connect(port)
            open.send(loginWire(user = "alice", password = "secret"))
            assertLoginAck(assertNotNull(open.readMessage()))
            connect(port, SessionVectors.ENCRYPT_NOT_SUP).use {
                it.send(loginWire(user = "bob", password = "geheim-bob"))
                assertLoginAck(assertNotNull(it.readMessage()))
            }

            val unknown = answerTo(port, loginPayload(user = "mallory", password = "secret"))
            val wrong = answerTo(port, loginPayload(user = "alice", password = "falsch"))
            assertContentEquals(rejection, unknown.bytes, unknown.bytes.toHex())
            assertContentEquals(unknown.bytes, wrong.bytes)
            unsupportedLogins().forEach { assertContentEquals(rejection, answerTo(port, it).bytes) }

            connect(port).use {
                it.sendBytewise(handMessage(LOGIN7_TYPE, loginPayload("alice", "secret"), 20))
                assertLoginAck(assertNotNull(it.readMessage()))
            }

            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
            assertTrue(open.closedByServerWithoutBytes(), "die angemeldete Verbindung ist getrennt")
            assertNotNull(server.awaitLine("${ServerProcess.PREFIX} gestoppt"), server.output.toString())
            listOf("alice", "bob", "secret", "geheim", "falsch", "mallory").forEach { word ->
                assertTrue(server.output.none { it.contains(word) }, "Ausgabe nennt $word: ${server.output}")
            }
            open.close()
        }
    }

    /**
     * Ein Prozess ohne `-Djdk.tls.server.newSessionTicketCount=0` startet nicht: Exit-Code ungleich null, die
     * Ausgabe nennt das Flag, es gibt weder eine Start- noch eine Port-Zeile.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Ein Prozess ohne das JVM-Flag startet nicht`() {
        ServerProcess(environment(), withFlag = false).use { server ->
            val exit = server.awaitExit()
            assertNotNull(exit)
            assertEquals(EXIT_START_FAILED, exit)
            val expected = "${ServerProcess.PREFIX} JVM-Flag -D${Bootstrap.TICKET_FLAG}=0 fehlt"
            assertEquals(listOf(expected), server.output.toList())
        }
    }

    /**
     * Ein Start mit fehlender Pflichtoption, ungültigem Wert oder belegtem Port endet mit Exit-Code 1 und einer
     * Zeile, die Schlüssel und Art nennt; die Ausgabe nennt keinen Wert (Konfigurationswerte nie im Log).
     *
     * @spec SPEC-043
     * @spec SPEC-005
     */
    @Test
    fun `Fehlerhafte Konfiguration und belegter Port beenden den Start mit Exit-Code 1`() {
        val missing = environment() - ConfigKeys.AUTH_USERS_FILE
        ServerProcess(missing).use { server ->
            assertEquals(EXIT_START_FAILED, server.awaitExit())
            val expected = "${ServerProcess.PREFIX} Konfiguration ungueltig: AUTH_USERS_FILE: MISSING"
            assertEquals(listOf(expected), server.output.toList())
        }
        ServerProcess(environment(ConfigKeys.LOGIN_TIMEOUT to "geheim-4711")).use { server ->
            assertEquals(EXIT_START_FAILED, server.awaitExit())
            assertTrue(server.output.none { it.contains("geheim") || it.contains("4711") }, server.output.toString())
            assertTrue(server.output.single().contains("LOGIN_TIMEOUT: INVALID_VALUE"), server.output.toString())
        }
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            val port = ConfigKeys.LISTEN_PORT to occupied.localPort.toString()
            ServerProcess(environment(port)).use { server ->
                assertEquals(EXIT_START_FAILED, server.awaitExit())
                assertTrue(server.output.single().contains("Port belegt"), server.output.toString())
                assertNull(server.output.firstOrNull { it.contains("gestartet") })
            }
        }
    }
}
