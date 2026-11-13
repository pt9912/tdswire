package io.github.pt9912.tdswire.integrator

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.EncryptionSetting
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ObservationSettings
import io.github.pt9912.tdswire.config.RequestLimits
import io.github.pt9912.tdswire.config.RequestSettings
import io.github.pt9912.tdswire.config.RequestTimeouts
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.runtime.StartOutcome
import io.github.pt9912.tdswire.runtime.TdsServer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Einstellungen der Anfragen von außen (`TDS-FA-09.a`, `TDS-QA-05`, `SPEC-059`, `SPEC-062`): drei benannte
 * Gruppen mit Voreinstellungen, aus Kotlin mit Namen und aus Java über die Konstruktoren mit weniger Argumenten
 * (`@JvmOverloads`). Der Test nutzt nur öffentliche Namen. Der Java-Aufruf ist über Reflection auf die öffentlichen
 * Konstruktoren belegt (kein Java-Quelltext im Source-Set `integrator`), derselbe Weg wie bei den Konstruktoren von
 * `UpdateCount` und `ResultColumn`.
 */
class EinstellungenEmbeddingTest {
    /**
     * Aus Kotlin nennt ein Aufruf nur die Felder, die er ändert; jede Gruppe und jedes Feld ohne Angabe hat die
     * Voreinstellung. Wächter: eine verlorene Voreinstellung, zwei vertauschte Felder, eine Gruppe, die ein Feld einer
     * anderen trägt.
     *
     * @spec SPEC-059
     * @spec SPEC-062
     */
    @Test
    fun dieGruppenNehmenNurGenannteFelderUndBehaltenDieVoreinstellung() {
        val settings = RequestSettings(
            timeouts = RequestTimeouts(cancelTimeoutSeconds = CANCEL),
            limits = RequestLimits(maxMessageBytes = BYTES),
        )
        assertEquals(DEFAULT_QUERY, settings.timeouts.queryTimeoutSeconds)
        assertEquals(CANCEL, settings.timeouts.cancelTimeoutSeconds)
        assertEquals(DEFAULT_GRACE, settings.timeouts.shutdownGraceSeconds)
        assertEquals(DEFAULT_HANDLES, settings.limits.maxPreparedHandles)
        assertEquals(BYTES, settings.limits.maxMessageBytes)
        assertEquals(DEFAULT_METRICS, settings.observation.metricsIntervalSeconds)
        assertEquals(RequestSettings(), RequestSettings(RequestTimeouts(), RequestLimits(), ObservationSettings()))
    }

    /**
     * Aus Java sind die Konstruktoren mit weniger Argumenten aufrufbar, die fehlenden Werte sind die
     * Voreinstellungen; den Konstruktor mit vier Ganzzahlen für `RequestSettings` gibt es nicht mehr (die Zahl der
     * Parameter ist durch die Gruppen ersetzt). Wächter: eine Gruppe ohne Überladung für Java, ein anderer
     * Standardwert, der alte Konstruktor mit Ganzzahlen.
     *
     * @spec SPEC-059
     * @spec SPEC-062
     */
    @Test
    fun ausJavaSindDieKonstruktorenMitWenigerArgumentenAufrufbar() {
        val int = Int::class.javaPrimitiveType
        val timeouts = RequestTimeouts::class.java
        assertEquals(RequestTimeouts(), timeouts.getConstructor().newInstance())
        assertEquals(RequestTimeouts(queryTimeoutSeconds = QUERY), timeouts.getConstructor(int).newInstance(QUERY))
        assertEquals(
            RequestTimeouts(queryTimeoutSeconds = QUERY, cancelTimeoutSeconds = CANCEL),
            timeouts.getConstructor(int, int).newInstance(QUERY, CANCEL),
        )
        assertEquals(
            RequestTimeouts(queryTimeoutSeconds = QUERY, cancelTimeoutSeconds = CANCEL, shutdownGraceSeconds = GRACE),
            timeouts.getConstructor(int, int, int).newInstance(QUERY, CANCEL, GRACE),
        )
        val limits = RequestLimits::class.java
        assertEquals(RequestLimits(), limits.getConstructor().newInstance())
        assertEquals(RequestLimits(maxPreparedHandles = HANDLES), limits.getConstructor(int).newInstance(HANDLES))
        assertEquals(
            RequestLimits(maxPreparedHandles = HANDLES, maxMessageBytes = BYTES),
            limits.getConstructor(int, int).newInstance(HANDLES, BYTES),
        )
        val observation = ObservationSettings::class.java
        assertEquals(ObservationSettings(), observation.getConstructor().newInstance())
        assertEquals(
            ObservationSettings(metricsIntervalSeconds = METRICS),
            observation.getConstructor(int).newInstance(METRICS),
        )
        val requests = RequestSettings::class.java
        assertEquals(RequestSettings(), requests.getConstructor().newInstance())
        assertEquals(
            RequestSettings(limits = RequestLimits(maxPreparedHandles = HANDLES)),
            requests
                .getConstructor(RequestTimeouts::class.java, RequestLimits::class.java)
                .newInstance(RequestTimeouts(), RequestLimits(maxPreparedHandles = HANDLES)),
        )
        assertEquals(
            RequestSettings(),
            requests
                .getConstructor(RequestTimeouts::class.java, RequestLimits::class.java, ObservationSettings::class.java)
                .newInstance(RequestTimeouts(), RequestLimits(), ObservationSettings()),
        )
        val declared = requests.constructors.filterNot { it.isSynthetic }
        assertTrue(declared.none { constructor -> constructor.parameterTypes.any { it == int } })
    }

    /**
     * Gruppen aus dem Konstruktor mit weniger Argumenten gehen in die Prüfung des Starts wie die aus Kotlin: eine
     * Frist von 20 s ist gültig, die Grenze 0 der Prepared-Handles ergibt genau einen Befund, der Start bricht ab.
     * Wächter: eine Gruppe, die der Start nicht liest, ein Befund für die gültige Gruppe.
     *
     * @spec SPEC-059
     * @spec SPEC-062
     */
    @Test
    fun eineAusJavaGebauteGruppeWirdBeimStartGeprueft() {
        val int = Int::class.javaPrimitiveType
        val timeouts = RequestTimeouts::class.java.getConstructor(int).newInstance(VALID_QUERY)
        val limits = RequestLimits::class.java.getConstructor(int).newInstance(0)
        val parsed = assertIs<UserListResult.Valid>(UserList.parse("alice\t{plain}secret\n", true))
        val config = ServerConfig(
            ListenSettings(host = LOOPBACK, port = 0),
            encryption = EncryptionSetting.TEST_UNENCRYPTED,
            auth = AuthSettings(parsed.list, plainTestMode = true),
            requests = RequestSettings(timeouts = timeouts, limits = limits),
        )
        val outcome = runBlocking { TdsServer.start(config, BackendSource.None) }
        val aborted = assertIs<StartOutcome.Aborted>(outcome, "maxPreparedHandles 0 liegt außerhalb des Bereichs")
        assertEquals(1, aborted.issues.size, aborted.issues.toString())
    }

    private companion object {
        const val QUERY = 7
        const val CANCEL = 8
        const val GRACE = 9
        const val HANDLES = 5
        const val BYTES = 70_000
        const val METRICS = 3
        const val VALID_QUERY = 20
        const val DEFAULT_QUERY = 30
        const val DEFAULT_GRACE = 10
        const val DEFAULT_HANDLES = 1000
        const val DEFAULT_METRICS = 60
    }
}
