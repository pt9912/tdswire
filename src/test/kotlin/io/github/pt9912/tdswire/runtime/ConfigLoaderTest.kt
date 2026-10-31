package io.github.pt9912.tdswire.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Schlüssel, Voreinstellungen und Bereiche der Konfiguration (`TDS-FA-09.a`). */
class ConfigLoaderTest : TempFilesTest() {
    private fun base(vararg more: Pair<String, String>) =
        testModeEnvironment(file("users.txt", "alice\t{plain}pw\n"), *more)

    /**
     * Ohne optionale Schlüssel gelten die Voreinstellungen der Spezifikation: Port 1433, `LOGIN_TIMEOUT` 15 s,
     * `MAX_PRELOGIN_MESSAGE_BYTES` 65536. Die Werte der Spezifikation sind vorläufig; der Test pinnt die Festlegung.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     * @spec SPEC-046
     */
    @Test
    fun `Voreinstellungen entsprechen der Spezifikation`() {
        val config = ConfigLoader.load(base()).loaded()
        assertEquals(1433, config.listen.port)
        assertEquals("0.0.0.0", config.listen.host)
        assertEquals(15, config.login.timeoutSeconds)
        assertEquals(65536, config.login.maxPreloginMessageBytes)
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
                ConfigKeys.LISTEN_HOST to "127.0.0.1",
                ConfigKeys.LISTEN_PORT to "14330",
                ConfigKeys.LOGIN_TIMEOUT to "7",
                ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES to "4096",
            ),
        ).loaded()
        assertEquals("127.0.0.1", config.listen.host)
        assertEquals(14330, config.listen.port)
        assertEquals(7, config.login.timeoutSeconds)
        assertEquals(4096, config.login.maxPreloginMessageBytes)
        assertEquals(EncryptionSetting.TEST_UNENCRYPTED, config.encryption)
        assertTrue(config.auth.plainTestMode)
        assertNull(config.tls)
    }

    /**
     * Grenzen der Zahlenschlüssel: der kleinste und der größte zulässige Wert werden angenommen, der Wert
     * davor und danach ist außerhalb des Bereichs.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     * @spec SPEC-046
     */
    @Test
    fun `Bereichsgrenzen der Zahlenschluessel`() {
        val bounds = listOf(
            Triple(ConfigKeys.LISTEN_PORT, 0, 65535),
            Triple(ConfigKeys.LOGIN_TIMEOUT, 1, 3600),
            Triple(ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES, 512, 65536),
        )
        for ((key, low, high) in bounds) {
            ConfigLoader.load(base(key to low.toString())).loaded()
            ConfigLoader.load(base(key to high.toString())).loaded()
            val outside = ConfigIssue(key, IssueKind.OUT_OF_RANGE)
            if (low > 0) assertEquals(outside, singleIssue(base(key to (low - 1).toString())))
            assertEquals(outside, singleIssue(base(key to (high + 1).toString())))
        }
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
            val issue = singleIssue(base(ConfigKeys.LISTEN_PORT to value))
            assertEquals(ConfigIssue(ConfigKeys.LISTEN_PORT, IssueKind.INVALID_VALUE), issue, "Wert '$value'")
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
            val issue = singleIssue(base(ConfigKeys.ENCRYPTION_POLICY to value))
            assertEquals(ConfigIssue(ConfigKeys.ENCRYPTION_POLICY, IssueKind.INVALID_VALUE), issue, "Wert '$value'")
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
        val environment = base() - ConfigKeys.ENCRYPTION_POLICY
        val issues = ConfigLoader.load(environment).issues()
        assertEquals(
            listOf(
                ConfigIssue(ConfigKeys.TLS_KEYSTORE_FILE, IssueKind.MISSING),
                ConfigIssue(ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE, IssueKind.MISSING),
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
        val store = ConfigKeys.TLS_KEYSTORE_FILE
        val password = ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE
        assertEquals(ConfigIssue(store, IssueKind.CONFLICT), singleIssue(base(store to "x")))
        assertEquals(ConfigIssue(password, IssueKind.CONFLICT), singleIssue(base(password to "x")))
    }

    /**
     * Mehrere Fehler werden zusammen gemeldet, in fester Reihenfolge; die Diagnose nennt nur Schlüssel und Art.
     *
     * @spec SPEC-005
     * @spec SPEC-008
     */
    @Test
    fun `Mehrere Fehler werden zusammen gemeldet`() {
        val issues = ConfigLoader.load(
            mapOf(
                ConfigKeys.LISTEN_PORT to "99999",
                ConfigKeys.LOGIN_TIMEOUT to "0",
                ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
            ),
        ).issues()
        assertEquals(
            listOf(
                ConfigIssue(ConfigKeys.LISTEN_PORT, IssueKind.OUT_OF_RANGE),
                ConfigIssue(ConfigKeys.LOGIN_TIMEOUT, IssueKind.OUT_OF_RANGE),
                ConfigIssue(ConfigKeys.AUTH_USERS_FILE, IssueKind.MISSING),
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
            ConfigKeys.LISTEN_PORT to secrets[0],
            ConfigKeys.ENCRYPTION_POLICY to secrets[0],
            ConfigKeys.AUTH_PLAIN_TESTMODE to secrets[0],
            ConfigKeys.AUTH_USERS_FILE to secrets[1],
        )
        val messages = ConfigLoader.load(environment).issues().map { it.message }
        assertEquals(4, messages.size, messages.toString())
        secrets.forEach { secret -> messages.forEach { assertFalse(it.contains(secret), "$it nennt $secret") } }
    }

    /**
     * Ein leerer Wert für die Bind-Adresse ist ungültig; eine gesetzte Adresse wird nicht verändert.
     *
     * @spec SPEC-005
     */
    @Test
    fun `Leere Bind-Adresse ist ungueltig`() {
        val issue = singleIssue(base(ConfigKeys.LISTEN_HOST to " "))
        assertEquals(ConfigIssue(ConfigKeys.LISTEN_HOST, IssueKind.INVALID_VALUE), issue)
    }
}
