package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val STORE_PASSWORD = "tdswire-test"

/** Lebenszyklus des Einstiegs im Prozess des Tests: `stop` und der Verbrauch des Passworts der Konfiguration. */
internal class TdsServerLifecycleTest : RuntimeTestBase() {
    private val directory = checkNotNull(System.getProperty("tdswire.testkeys"))

    private fun config(port: Int, key: TlsKeyMaterial = freshKey()): ServerConfig {
        val users = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}pw\n", true)).list
        return ServerConfig(ListenSettings(LOOPBACK, port), tls = key, auth = AuthSettings(users, true))
    }

    private fun freshKey() =
        TlsKeyMaterial(File("$directory/gueltig.p12").readBytes(), PasswordChars(STORE_PASSWORD.toCharArray()))

    private suspend fun launch(config: ServerConfig, properties: (String) -> String? = flagSet): StartOutcome =
        Bootstrap.launch(config, Wiring(log = log), properties)

    /**
     * Ein zweiter `stop` hat keine Wirkung: das Log nennt „gestoppt“ genau einmal, der Port bleibt frei.
     *
     * @spec SPEC-059
     * @spec SPEC-044
     */
    @Test
    fun `Ein zweiter stop schreibt keine zweite Logzeile`() = blocking {
        val server = running(loopbackEnvironment(file("users.txt", "alice\t{plain}pw\n")))
        server.stop()
        server.stop()
        assertEquals(1, log.lines.count { it == "gestoppt" }, log.lines.toString())
    }

    /**
     * Das Passwort im Schlüsselmaterial bleibt lesbar, wenn der Start vor dem Laden des Materials abbricht (Flag,
     * Prüfung der Konfiguration); nach einem Bindefehler ist es gelöscht, und ein zweiter Start mit derselben
     * Konfiguration bricht ab. Setzung: eine Konfiguration gilt für einen Start, sobald das Material geladen wird.
     *
     * @spec SPEC-059
     * @spec SPEC-043
     */
    @Test
    fun `Das Passwort wird erst beim Laden des Materials geloescht`() = blocking {
        val expected = STORE_PASSWORD.toCharArray()
        val noFlag = config(0)
        assertIs<StartOutcome.Aborted>(launch(noFlag) { null })
        assertContentEquals(expected, noFlag.tls?.password?.reveal())
        val invalid = config(PORT_OUTSIDE)
        assertIs<StartOutcome.Aborted>(launch(invalid))
        assertContentEquals(expected, invalid.tls?.password?.reveal())

        val bound = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { occupied ->
            val config = config(occupied.localPort)
            assertIs<StartOutcome.Aborted>(launch(config))
            assertContentEquals(CharArray(expected.size), config.tls?.password?.reveal())
            config
        }
        val reused = assertIs<StartOutcome.Aborted>(launch(config(0, checkNotNull(bound.tls))))
        assertEquals(emptyList(), reused.issues)
    }

    private companion object {
        const val PORT_OUTSIDE = 70_000
    }
}
