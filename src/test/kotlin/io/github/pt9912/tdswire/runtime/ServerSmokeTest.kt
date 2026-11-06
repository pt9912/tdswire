package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
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

/** Exit-Code des Testservers bei abgebrochenem Start. */
private const val EXIT_START_FAILED = 1

/**
 * Der Rohbytes-Smoke der Welle (`make smoke`, Teil von `make gates`): der Testserver als Prozess, gestartet über sein
 * `main` und einer Testkonfiguration, beantwortet einen Rohbytes-Client vom Prozessstart bis zur
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
     * Prozess, eine angemeldete Verbindung wird getrennt, und die Standardausgabe nennt weder Passwort noch Benutzer
     * (außer die Kennung im Feld `principal` des Ereignisses „Anmeldung angenommen“).
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
                val lines = server.outputWithoutPrincipal()
                assertTrue(lines.none { it.contains(word) }, "Ausgabe nennt $word: ${server.output}")
            }
            open.close()
        }
    }

    /**
     * Ein Prozess ohne jede JVM-Option außer dem Heap startet mit TLS (`REQUIRED`, Speicher `gueltig.p12`): die Zeile
     * `gestartet, Port <n>` kommt, der Prozess läuft weiter. Die Startprobe des Prozesses läuft im 8.0-Pfad mit TLS
     * 1.3 und dem NewSessionTicket des Servers, ohne dass der Start daran scheitert.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Ein Prozess ohne JVM-Option startet mit TLS`() {
        val directory = checkNotNull(System.getProperty("tdswire.testkeys"))
        val tls = environment(
            ConfigKeys.ENCRYPTION_POLICY to "REQUIRED",
            ConfigKeys.TLS_KEYSTORE_FILE to "$directory/gueltig.p12",
            ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to file("kennwort.txt", "tdswire-test\n"),
            ConfigKeys.AUTH_PLAIN_TESTMODE to "false",
            ConfigKeys.AUTH_USERS_FILE to file("users-tls.txt", "alice\t${pbkdf2Value("secret")}\n"),
        )
        ServerProcess(tls).use { server ->
            assertTrue(server.port() > 0, server.output.toString())
            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
            assertNotNull(server.awaitLine("${ServerProcess.PREFIX} gestoppt"), server.output.toString())
            assertNull(server.output.firstOrNull { it.contains("gescheitert") }, server.output.toString())
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
        ServerProcess(environment(ConfigKeys.LOGIN_TIMEOUT to "geheim-$WERTPROBE")).use { server ->
            assertEquals(EXIT_START_FAILED, server.awaitExit())
            assertTrue(server.output.none { it.contains("geheim") || it.contains(WERTPROBE) }, server.output.toString())
            assertTrue(server.output.single().contains("LOGIN_TIMEOUT: INVALID_VALUE"), server.output.toString())
        }
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            val port = ConfigKeys.LISTEN_PORT to occupied.localPort.toString()
            ServerProcess(environment(port)).use { server ->
                assertEquals(EXIT_START_FAILED, server.awaitExit())
                val plain = server.output.filter { it.startsWith(ServerProcess.PREFIX) }
                assertTrue(plain.single().contains("Port belegt"), server.output.toString())
                assertNull(server.output.firstOrNull { it.contains("gestartet") })
            }
        }
    }

    private fun loggedIn(port: Int): RawClient = connect(port).also {
        it.send(loginWire(user = "alice", password = "secret"))
        assertLoginAck(assertNotNull(it.readMessage()))
    }

    private fun batchWire(text: String): ByteArray = handMessage(BATCH_TYPE, BatchVectors.payload(text), 4000)

    /** Ein Prozess mit der Tabelle `laufend` (Zeilen ohne Ende); Fristen von einer Sekunde halten den Test kurz. */
    private fun runningProcess() = ServerProcess(
        environment(ConfigKeys.SHUTDOWN_GRACE to "1", ConfigKeys.CANCEL_TIMEOUT to "1"),
        ServerProcess.RUNNING_TABLE_MAIN,
    )

    /** Startet die Anfrage ohne Ende und wartet, bis die ersten Bytes der Antwort da sind. */
    private fun startRunningQuery(port: Int): RawClient = loggedIn(port).also {
        it.send(batchWire("SELECT * FROM laufend"))
        assertTrue(it.readSome().isNotEmpty(), "die Antwort beginnt")
    }

    /**
     * Prozess-Stop mit einer ruhenden Verbindung und einer laufenden Anfrage (Zeilen ohne Ende, `SHUTDOWN_GRACE` 1 s,
     * `CANCEL_TIMEOUT` 1 s): SIGTERM beendet den Prozess mit der Zeile `gestoppt`, die ruhende Verbindung ist
     * getrennt, die laufende Anfrage endet nach der Frist mit genau einem `DONE[ATTN]` am Ende der Antwort, ohne
     * weitere Bytes danach. Gemessen ist der Prozess mit Rohbytes-Client, nicht der Referenzclient.
     *
     * @spec SPEC-044
     * @spec SPEC-047
     * @spec TDS-FA-07
     */
    @Test
    fun `Der Prozess-Stop beendet eine ruhende Verbindung und bricht die laufende Anfrage nach der Frist ab`() {
        runningProcess().use { server ->
            val port = server.port()
            val idle = loggedIn(port)
            val busy = startRunningQuery(port)
            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
            assertNotNull(server.awaitLine("${ServerProcess.PREFIX} gestoppt"), server.output.toString())
            assertTrue(idle.closedByServerWithoutBytes(), "die ruhende Verbindung ist getrennt")
            val rest = busy.readToEnd()
            val attention = BatchVectors.DONE_ATTN.split(" ").map { it.toInt(HEX).toByte() }
            assertEquals(attention, rest.takeLast(attention.size), "die Antwort endet mit DONE[ATTN]: ${rest.toHex()}")
            assertNull(server.output.firstOrNull { it.contains("Exception") }, server.output.toString())
            idle.close()
            busy.close()
        }
    }

    /**
     * Abrupter TCP-Abbruch (RST) gegen den Serverprozess, während eine Anfrage läuft: der Prozess läuft weiter, eine
     * neue Verbindung meldet sich an und wird bedient, die Ausgabe nennt keine Ausnahme, und der Backend-Strom der
     * Anfrage ist geschlossen (die Quelle `laufend` endet mit dem Abbruch; das Zählen am Strom belegen
     * `ConnectionLeakTest` und `DisconnectTest`). Der Test belegt Erreichbarkeit und keine Ausnahme in der Ausgabe, er
     * diskriminiert die Disconnect-Erkennung nicht (unter ausgeschalteter Erkennung blieb er grün) und trägt deshalb
     * kein `@spec`.
     */
    @Test
    fun `Ein abrupter TCP-Abbruch waehrend einer Anfrage laesst den Prozess erreichbar`() {
        runningProcess().use { server ->
            val port = server.port()
            repeat(RESETS) { startRunningQuery(port).reset() }
            loggedIn(port).use { client ->
                client.send(batchWire("SELECT * FROM person WHERE Id = 1"))
                assertEquals(4, assertNotNull(client.readMessage()).type)
            }
            assertNull(server.output.firstOrNull { it.contains("Exception") }, server.output.toString())
            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
        }
    }

    /**
     * Beschädigtes PRELOGIN (Nutzdaten ohne gültige Optionstabelle, dann ein Paketkopf mit unbekanntem Typ): der Server
     * schließt ohne Antwortbytes, der Prozess bleibt erreichbar, und eine neue Verbindung meldet sich an.
     *
     * @spec SPEC-027
     * @spec TDS-FA-01
     * @spec TDS-QA-02
     */
    @Test
    fun `Ein beschaedigtes PRELOGIN schliesst ohne Antwort und der Prozess bleibt erreichbar`() {
        ServerProcess(environment()).use { server ->
            val port = server.port()
            RawClient(port).use {
                it.send(handMessage(PRELOGIN_TYPE, ByteArray(UNPARSABLE_BYTES) { b -> (b * 7 + 3).toByte() }, 4000))
                assertTrue(it.closedByServerWithoutBytes(), "ohne Antwort geschlossen")
            }
            RawClient(port).use {
                it.send(byteArrayOf(0x7F, 1, 0, 8, 0, 0, 1, 0))
                assertTrue(it.closedByServerWithoutBytes(), "unbekannter Pakettyp: ohne Antwort geschlossen")
            }
            loggedIn(port).close()
            assertNull(server.output.firstOrNull { it.contains("Exception") }, server.output.toString())
            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
        }
    }

    private companion object {
        const val RESETS = 5
        const val UNPARSABLE_BYTES = 40
        const val HEX = 16
    }
}
