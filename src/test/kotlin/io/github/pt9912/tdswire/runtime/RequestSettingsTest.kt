package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.ObservationSettings
import io.github.pt9912.tdswire.config.RequestLimits
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.RequestTimeouts
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Die Einstellungen der Anfragen in ihren drei Gruppen (`TDS-FA-09.a`, `TDS-QA-05`): jedes Feld hat einen eigenen
 * Wert und einen eigenen Platz, eine Voreinstellung gilt für jedes Feld, das ein Aufruf nicht nennt.
 */
class RequestSettingsTest {
    private fun users(): AuthSettings =
        AuthSettings(assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}pw\n", true)).list, true)

    private fun timeouts(query: Int, cancel: Int, grace: Int) =
        RequestTimeouts(queryTimeoutSeconds = query, cancelTimeoutSeconds = cancel, shutdownGraceSeconds = grace)

    private fun limits(handles: Int, bytes: Int) = RequestLimits(maxPreparedHandles = handles, maxMessageBytes = bytes)

    private val distinct = RequestSettings(
        timeouts = RequestTimeouts(queryTimeoutSeconds = 11, cancelTimeoutSeconds = 12, shutdownGraceSeconds = 13),
        limits = RequestLimits(maxPreparedHandles = 21, maxMessageBytes = 70_022),
        observation = ObservationSettings(metricsIntervalSeconds = 31),
    )

    /**
     * Sechs verschiedene Werte kommen in der wirksamen Konfiguration unter dem Schlüssel an, dessen Namen die Gruppe
     * trägt (`ADR-0040`): zwei vertauschte Werte an Konstruktor, Prüfung oder Abbildung ergeben eine andere Zahl unter
     * mindestens einem Schlüssel.
     *
     * @spec SPEC-062
     * @spec SPEC-059
     */
    @Test
    fun `Jeder Wert der Gruppen steht unter seinem Schluessel`() {
        val settings = ServerConfig(auth = users(), requests = distinct).effectiveSettings().asMap()
        assertEquals("11", settings[ConfigKeys.QUERY_TIMEOUT])
        assertEquals("12", settings[ConfigKeys.CANCEL_TIMEOUT])
        assertEquals("13", settings[ConfigKeys.SHUTDOWN_GRACE])
        assertEquals("21", settings[ConfigKeys.MAX_PREPARED_HANDLES])
        assertEquals("70022", settings[ConfigKeys.MAX_MESSAGE_BYTES])
        assertEquals("31", settings[ConfigKeys.METRICS_INTERVAL_SECONDS])
    }

    /**
     * Ein Aufruf, der nur ein Feld nennt, behält für jedes andere Feld und jede andere Gruppe die Voreinstellung der
     * Spezifikation; die Gruppe ohne Angabe ist die Voreinstellung der Gruppe.
     *
     * @spec SPEC-007
     * @spec SPEC-009
     * @spec SPEC-033
     * @spec SPEC-047
     * @spec SPEC-058
     * @spec SPEC-062
     */
    @Test
    fun `Ein genanntes Feld laesst alle anderen auf der Voreinstellung`() {
        assertEquals(timeouts(30, 5, 10), RequestTimeouts())
        assertEquals(limits(1000, 16_777_216), RequestLimits())
        assertEquals(ObservationSettings(metricsIntervalSeconds = 60), ObservationSettings())
        assertEquals(RequestSettings(RequestTimeouts(), RequestLimits(), ObservationSettings()), RequestSettings())
        assertEquals(timeouts(30, 7, 10), RequestTimeouts(cancelTimeoutSeconds = 7))
        assertEquals(timeouts(30, 5, 0), RequestTimeouts(shutdownGraceSeconds = 0))
        assertEquals(limits(1000, 70_000), RequestLimits(maxMessageBytes = 70_000))
        assertEquals(limits(2, 16_777_216), RequestLimits(maxPreparedHandles = 2))
        val onlyLimits = RequestSettings(limits = RequestLimits(maxPreparedHandles = 2))
        assertEquals(RequestTimeouts(), onlyLimits.timeouts)
        assertEquals(ObservationSettings(), onlyLimits.observation)
    }

    /**
     * Die Gruppen sind eigene Typen: `copy` ersetzt eine Gruppe als Ganzes und lässt die anderen gleich.
     *
     * @spec SPEC-062
     */
    @Test
    fun `copy ersetzt eine Gruppe und laesst die anderen`() {
        val changed = distinct.copy(observation = ObservationSettings(metricsIntervalSeconds = 0))
        assertEquals(distinct.timeouts, changed.timeouts)
        assertEquals(distinct.limits, changed.limits)
        assertEquals(0, changed.observation.metricsIntervalSeconds)
    }
}
