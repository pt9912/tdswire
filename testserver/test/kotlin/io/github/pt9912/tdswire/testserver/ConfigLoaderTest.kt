package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LOOPBACK = "127.0.0.1"

/** Schlüssel, Voreinstellungen und Bereiche der Konfiguration (`TDS-FA-09.a`). */
internal class ConfigLoaderTest : TempFilesTest() {
    private fun base(vararg more: Pair<String, String>) =
        testModeEnvironment(file("users.txt", "alice\t{plain}pw\n"), *more)

    /**
     * Ohne optionale Schlüssel gelten die Voreinstellungen der Spezifikation: Port 1433, `LOGIN_TIMEOUT` 15 s,
     * `MAX_PRELOGIN_MESSAGE_BYTES` 65536, `QUERY_TIMEOUT` 30 s, `CANCEL_TIMEOUT` 5 s. Die Werte der Spezifikation sind
     * vorläufig; der Test pinnt die Festlegung.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     * @spec SPEC-046
     * @spec SPEC-009
     * @spec SPEC-033
     */
    @Test
    fun `Voreinstellungen entsprechen der Spezifikation`() {
        val config = ConfigLoader.load(base()).loaded()
        assertEquals(1433, config.listen.port)
        assertEquals("0.0.0.0", config.listen.host)
        assertEquals(15, config.login.timeoutSeconds)
        assertEquals(65536, config.login.maxPreloginMessageBytes)
        assertEquals(30, config.requests.queryTimeoutSeconds)
        assertEquals(5, config.requests.cancelTimeoutSeconds)
        assertEquals(1000, config.listen.maxConnections)
        assertEquals(10, config.requests.shutdownGraceSeconds)
        assertEquals(1000, config.requests.maxPreparedHandles)
    }

    /**
     * Gesetzte Werte kommen an; die Einzelwerte unterscheiden sich, damit eine Vertauschung auffällt.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     * @spec SPEC-046
     */
    @Test
    fun `Gesetzte Werte werden uebernommen`() {
        val config = ConfigLoader.load(
            base(
                EnvKeys.LISTEN_HOST to "127.0.0.1",
                EnvKeys.LISTEN_PORT to "14330",
                EnvKeys.LOGIN_TIMEOUT to "7",
                EnvKeys.MAX_PRELOGIN_MESSAGE_BYTES to "4096",
                EnvKeys.QUERY_TIMEOUT to "11",
                EnvKeys.CANCEL_TIMEOUT to "3",
                EnvKeys.MAX_CONNECTIONS to "17",
                EnvKeys.SHUTDOWN_GRACE to "2",
                EnvKeys.MAX_PREPARED_HANDLES to "250",
            ),
        ).loaded()
        assertEquals("127.0.0.1", config.listen.host)
        assertEquals(14330, config.listen.port)
        assertEquals(7, config.login.timeoutSeconds)
        assertEquals(4096, config.login.maxPreloginMessageBytes)
        assertEquals(11, config.requests.queryTimeoutSeconds)
        assertEquals(3, config.requests.cancelTimeoutSeconds)
        assertEquals(17, config.listen.maxConnections)
        assertEquals(2, config.requests.shutdownGraceSeconds)
        assertEquals(250, config.requests.maxPreparedHandles)
        assertEquals(EncryptionSetting.TEST_UNENCRYPTED, config.encryption)
        assertTrue(config.auth.plainTestMode)
        assertNull(config.tls)
    }

    /**
     * Grenzen der Zahlenschlüssel: der Lader reicht jede Ziffernfolge weiter; der kleinste und der größte zulässige
     * Wert besteht die Prüfung der Bibliothek (Start der Bibliothek), der Wert davor und danach ist außerhalb des
     * Bereichs.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     * @spec SPEC-046
     * @spec SPEC-009
     * @spec SPEC-033
     */
    @Test
    fun `Bereichsgrenzen der Zahlenschluessel`() {
        val bounds = listOf(
            Triple(EnvKeys.LISTEN_PORT, 0, 65535),
            Triple(EnvKeys.LOGIN_TIMEOUT, 1, 3600),
            Triple(EnvKeys.MAX_PRELOGIN_MESSAGE_BYTES, 512, 65536),
            Triple(EnvKeys.QUERY_TIMEOUT, 1, 3600),
            Triple(EnvKeys.CANCEL_TIMEOUT, 1, 3600),
            Triple(EnvKeys.MAX_CONNECTIONS, 1, 100_000),
            Triple(EnvKeys.SHUTDOWN_GRACE, 0, 3600),
            Triple(EnvKeys.MAX_PREPARED_HANDLES, 1, 100_000),
        )
        for ((key, low, high) in bounds) {
            assertEquals(emptyList(), rangeIssues(base(key to low.toString())))
            assertEquals(emptyList(), rangeIssues(base(key to high.toString())))
            val outside = listOf(ConfigIssue(key, IssueKind.OUT_OF_RANGE))
            if (low > 0) assertEquals(outside, rangeIssues(base(key to (low - 1).toString())))
            assertEquals(outside, rangeIssues(base(key to (high + 1).toString())))
        }
    }

    /**
     * Die Befunde der Prüfung der Bibliothek, über ihre öffentliche Fläche: der Start liefert sie als `Aborted.issues`.
     * Ein gültiger Wert startet den Server (Loopback, Port 0 wenn nicht gesetzt) und stoppt ihn wieder; ein Abbruch
     * ohne Befund bricht den Test, damit ein anderer Grund nicht als „gültig“ zählt.
     */
    private fun issuesOf(config: ServerConfig): List<ConfigIssue> =
        when (val outcome = runBlocking { TdsServer.start(config, BackendSource.None) }) {
            is StartOutcome.Aborted -> {
                check(outcome.issues.isNotEmpty()) { "Abbruch ohne Befund: ${outcome.reason}" }
                outcome.issues
            }

            is StartOutcome.Running -> {
                runBlocking { outcome.server.stop() }
                emptyList()
            }
        }

    private fun rangeIssues(environment: Map<String, String>): List<ConfigIssue> {
        val onLoopback = mapOf(EnvKeys.LISTEN_HOST to LOOPBACK, EnvKeys.LISTEN_PORT to "0") + environment
        return issuesOf(ConfigLoader.load(onLoopback).loaded())
    }

    /**
     * Ein Zahlenwert besteht nur aus ASCII-Ziffern: Vorzeichen, Leerzeichen, Einheit, Dezimalpunkt, andere
     * Ziffernschriften, die leere Zeichenfolge und eine überlange Ziffernfolge sind `INVALID_VALUE`.
     *
     * @spec SPEC-005
     */
    @Test
    fun `Zahlenwerte mit anderer Form sind ungueltig`() {
        listOf("-1", "+80", " 80", "80 ", "15s", "1.5", "", "٨٠", "８０", "0x50", "1234567890").forEach { value ->
            val issue = singleIssue(base(EnvKeys.LISTEN_PORT to value))
            assertEquals(ConfigIssue(EnvKeys.LISTEN_PORT, IssueKind.INVALID_VALUE), issue, "Wert '$value'")
        }
    }

    /**
     * Die Grenze der Prepared-Handles: ein Wert aus Ziffern kommt an; null ist außerhalb des Bereichs (Start der
     * Bibliothek), ein negativer oder nichtnumerischer Wert ist `INVALID_VALUE` des Laders, und weder Wert noch
     * Nachbarschlüssel ändern sich dadurch. Der Name des Schlüssels ist der der Diagnose. Wächter: ein Lader, der
     * `0` oder `-1` still annimmt oder durch die Voreinstellung ersetzt, ein vertauschter Schlüssel.
     *
     * @spec SPEC-058
     * @spec SPEC-061
     */
    @Test
    fun `MAX_PREPARED_HANDLES null ist ausserhalb, negativ und nichtnumerisch ungueltig`() {
        assertEquals("MAX_PREPARED_HANDLES", EnvKeys.MAX_PREPARED_HANDLES)
        val one = ConfigLoader.load(base(EnvKeys.MAX_PREPARED_HANDLES to "1")).loaded()
        assertEquals(1, one.requests.maxPreparedHandles)
        assertEquals(
            listOf(ConfigIssue(EnvKeys.MAX_PREPARED_HANDLES, IssueKind.OUT_OF_RANGE)),
            rangeIssues(base(EnvKeys.MAX_PREPARED_HANDLES to "0")),
        )
        listOf("-1", "abc", "", "1.5", " 5").forEach { value ->
            val issue = singleIssue(base(EnvKeys.MAX_PREPARED_HANDLES to value))
            assertEquals(ConfigIssue(EnvKeys.MAX_PREPARED_HANDLES, IssueKind.INVALID_VALUE), issue, "Wert '$value'")
        }
    }

    /**
     * Die Richtlinie ist genau `REQUIRED` oder `TEST_UNENCRYPTED`; Schreibweisen und Nachbarwörter sind ungültig,
     * die Abschwächung folgt nie aus einem unbekannten Wert (`SPEC-010`).
     *
     * @spec SPEC-010
     */
    @Test
    fun `ENCRYPTION_POLICY kennt nur die zwei Werte`() {
        listOf("required", "Test_Unencrypted", "NONE", "OFF", "", " REQUIRED").forEach { value ->
            val issue = singleIssue(base(EnvKeys.ENCRYPTION_POLICY to value))
            assertEquals(ConfigIssue(EnvKeys.ENCRYPTION_POLICY, IssueKind.INVALID_VALUE), issue, "Wert '$value'")
        }
    }

    /**
     * Ohne Angabe gilt `REQUIRED` (`SPEC-010`), und `REQUIRED` verlangt Speicher und Kennwortdatei: ohne beide
     * bricht der Start ab, statt auf unverschlüsselt zurückzufallen.
     *
     * @spec SPEC-010
     */
    @Test
    fun `REQUIRED ist die Voreinstellung und verlangt TLS-Dateien`() {
        val environment = base() - EnvKeys.ENCRYPTION_POLICY
        val issues = ConfigLoader.load(environment).issues()
        assertEquals(
            listOf(
                ConfigIssue(EnvKeys.TLS_KEYSTORE_FILE, IssueKind.MISSING),
                ConfigIssue(EnvKeys.TLS_KEYSTORE_PASSWORD_FILE, IssueKind.MISSING),
            ),
            issues,
        )
    }

    /**
     * Im unverschlüsselten Testmodus sind die TLS-Schlüssel ein Widerspruch (`CONFLICT`), einzeln und zusammen.
     *
     * @spec SPEC-010
     */
    @Test
    fun `TLS-Schluessel im Testmodus sind ein Widerspruch`() {
        val store = EnvKeys.TLS_KEYSTORE_FILE
        val password = EnvKeys.TLS_KEYSTORE_PASSWORD_FILE
        assertEquals(ConfigIssue(store, IssueKind.CONFLICT), singleIssue(base(store to "x")))
        assertEquals(ConfigIssue(password, IssueKind.CONFLICT), singleIssue(base(password to "x")))
    }

    /**
     * Unter `REQUIRED` meldet der Lader eine Kennwortdatei, die es nicht gibt, als `UNREADABLE` mit dem Schlüssel der
     * Datei, nie mit dem Pfad; der Speicher, der gelesen werden kann, ist kein Fehler.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Eine fehlende Kennwortdatei ist unlesbar`() {
        val environment = base() - EnvKeys.ENCRYPTION_POLICY + mapOf(
            EnvKeys.TLS_KEYSTORE_FILE to file("speicher.p12", "inhalt"),
            EnvKeys.TLS_KEYSTORE_PASSWORD_FILE to "/gibt-es-nicht-4711.txt",
        )
        val issue = singleIssue(environment)
        assertEquals(ConfigIssue(EnvKeys.TLS_KEYSTORE_PASSWORD_FILE, IssueKind.UNREADABLE), issue)
        assertFalse(issue.message.contains("4711"))
    }

    /**
     * Mehrere Fehler der Form und der Dateien werden zusammen gemeldet, in fester Reihenfolge; die Diagnose nennt
     * nur Schlüssel und Art. Bereichsfehler prüft erst der Start der Bibliothek.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     */
    @Test
    fun `Mehrere Fehler werden zusammen gemeldet`() {
        val issues = ConfigLoader.load(
            mapOf(
                EnvKeys.LISTEN_PORT to "99999x",
                EnvKeys.LOGIN_TIMEOUT to "abc",
                EnvKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
            ),
        ).issues()
        assertEquals(
            listOf(
                ConfigIssue(EnvKeys.LISTEN_PORT, IssueKind.INVALID_VALUE),
                ConfigIssue(EnvKeys.LOGIN_TIMEOUT, IssueKind.INVALID_VALUE),
                ConfigIssue(EnvKeys.AUTH_USERS_FILE, IssueKind.MISSING),
            ),
            issues,
        )
    }

    /**
     * Die Diagnose nennt weder den Wert des Schlüssels noch einen Pfad: gesetzt sind unterscheidbare
     * Werte, keiner steht in `message` (`TDS-QA-03`, Konfigurationswerte nie im Log).
     *
     * @spec SPEC-051
     */
    @Test
    fun `Die Diagnose nennt keinen Wert und keinen Pfad`() {
        val secrets = listOf("geheim-wert-4711", "/pfad/geheim-4711/users", "kennung-4711")
        val environment = mapOf(
            EnvKeys.LISTEN_PORT to secrets[0],
            EnvKeys.ENCRYPTION_POLICY to secrets[0],
            EnvKeys.AUTH_PLAIN_TESTMODE to secrets[0],
            EnvKeys.AUTH_USERS_FILE to secrets[1],
        )
        val messages = ConfigLoader.load(environment).issues().map { it.message }
        assertEquals(4, messages.size, messages.toString())
        secrets.forEach { secret -> messages.forEach { assertFalse(it.contains(secret), "$it nennt $secret") } }
    }

    /**
     * Ein leerer Wert für die Bind-Adresse geht unverändert an die Bibliothek und ist dort ungültig.
     *
     * @spec SPEC-005
     */
    @Test
    fun `Leere Bind-Adresse ist ungueltig`() {
        val issues = rangeIssues(base(EnvKeys.LISTEN_HOST to " "))
        assertEquals(listOf(ConfigIssue(EnvKeys.LISTEN_HOST, IssueKind.INVALID_VALUE)), issues)
    }

    /**
     * Die Schlüssel, die der Testserver liest, tragen denselben Text wie die Schlüssel, die die Bibliothek in
     * einem `ConfigIssue` nennt: sonst meldete der Start einen Schlüssel, den es in der Umgebung nicht gibt. Jeder
     * Schlüssel, den die Prüfung der Bibliothek nennen kann, wird durch einen Wert außerhalb seines Bereichs (oder
     * durch fehlendes TLS-Material) ausgelöst und erscheint unter dem Namen der Umgebung.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Die Schluessel der Umgebung sind die der Diagnose`() {
        val outside = listOf(
            EnvKeys.LISTEN_HOST to " ",
            EnvKeys.LISTEN_PORT to "65536",
            EnvKeys.LOGIN_TIMEOUT to "0",
            EnvKeys.MAX_PRELOGIN_MESSAGE_BYTES to "511",
            EnvKeys.QUERY_TIMEOUT to "0",
            EnvKeys.CANCEL_TIMEOUT to "0",
            EnvKeys.MAX_CONNECTIONS to "0",
            EnvKeys.SHUTDOWN_GRACE to "3601",
            EnvKeys.MAX_PREPARED_HANDLES to "0",
        )
        outside.forEach { (key, value) ->
            assertEquals(listOf(key), rangeIssues(base(key to value)).map { it.key }, key)
        }
        val loaded = ConfigLoader.load(base()).loaded()
        val withoutTls = ServerConfig(loaded.listen, loaded.login, EncryptionSetting.REQUIRED, null, loaded.auth)
        val missing = issuesOf(withoutTls)
        assertEquals(listOf(EnvKeys.TLS_KEYSTORE_FILE, EnvKeys.TLS_KEYSTORE_PASSWORD_FILE), missing.map { it.key })
    }
}
