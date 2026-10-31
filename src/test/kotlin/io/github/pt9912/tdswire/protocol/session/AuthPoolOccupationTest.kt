package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthFeatures
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.ConnectionContext
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Belegung der Plätze des [AuthPool] (`TDS-QA-03`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`): Der Adapter ist
 * ein Test-Adapter mit fester Haltezeit ([HOLD_MILLIS]); gemessen wird die Struktur der Warteschlange (Wellen von
 * [AuthPool.DEFAULT_PARALLELISM] Aufrufen), nicht die Rechenzeit einer Ableitung. Wie lange eine echte Ableitung
 * dauert, misst `AuthTimingTest` in `config.auth`; die Wirkung auf den Transport unter Last ist nicht gemessen.
 */
class AuthPoolOccupationTest {
    private val places = AuthPool.DEFAULT_PARALLELISM

    private fun request(password: String) = AuthRequest(
        userName = "u",
        password = PasswordChars(password.toCharArray()),
        context = ConnectionContext("master", "app", "host", false),
        encryption = NegotiatedEncryption.ON,
        features = AuthFeatures(integratedSecurity = false, federatedAuthentication = false, changePassword = false),
    )

    private val holding = object : AuthAdapter {
        override fun authenticate(request: AuthRequest): AuthResult {
            Thread.sleep(HOLD_MILLIS)
            return AuthResult.Accepted(Principal("u"))
        }
    }

    /** Die Wartezeit eines regulären Aufrufs, der unmittelbar nach [attackers] gleichzeitigen Aufrufen beginnt. */
    private suspend fun regularLatencyAfter(attackers: Int): Double {
        val executor = AuthExecutor(AuthPool(parallelism = places))
        val occupying: List<Deferred<AuthOutcome>> =
            List(attackers) { executor.start(holding, request("angriff-$it")) }
        delay(SETTLE_MILLIS)
        val start = System.nanoTime()
        executor.start(holding, request("regulaer")).await()
        val latency = (System.nanoTime() - start) / NANOS_PER_MILLI
        occupying.joinAll()
        return latency
    }

    /**
     * Orakel: Belegen weniger Aufrufe als Plätze den Pool, wartet ein regulärer Aufruf nicht (Dauer etwa
     * [HOLD_MILLIS]); belegen genau so viele Aufrufe wie Plätze den Pool, wartet er auf das Ende einer Welle (mehr
     * als [HOLD_MILLIS] plus die halbe Haltezeit); doppelt so viele verlängern es um eine weitere Welle. Das ist die
     * Belegung der Plätze durch einen nicht angemeldeten Client mit mehreren Verbindungen: Die Wartezeit der
     * regulären Anmeldung wächst mit der Zahl der Aufrufe, die ein Angreifer offen hält, in Stufen der Haltezeit.
     * Gegenstand ist die Grenze, nicht eine Abhilfe: Die Drosselung ist als Carveout geführt (`CO-003`).
     *
     * @spec SPEC-008
     * @spec SPEC-051
     */
    @Test
    fun `Eine Welle von Aufrufen verzoegert die regulaere Anmeldung um ihre Haltezeit`() = blocking {
        val free = regularLatencyAfter(places - 1)
        val oneWave = regularLatencyAfter(places)
        val twoWaves = regularLatencyAfter(places * 2)
        assertTrue(free < HOLD_MILLIS * 1.5, "freie Plaetze: $free ms")
        assertTrue(oneWave > HOLD_MILLIS * 1.5, "eine Welle: $oneWave ms")
        assertTrue(twoWaves > oneWave + HOLD_MILLIS / 2, "zwei Wellen: $twoWaves ms, eine: $oneWave ms")
    }

    /**
     * Orakel: Das Passwort eines wartenden Aufrufs, den der Aufrufer abbricht, bleibt im Speicher, solange die
     * Plätze belegt sind, und ist überschrieben, sobald die Warteschlange ihn erreicht. Das Zeitfenster ist die
     * Restdauer der vorausgehenden Aufrufe, nicht null und nicht von `LOGIN_TIMEOUT` begrenzt; hier
     * [HOLD_MILLIS] bei einem Platz. Das Fenster gilt für die [PasswordChars] des Aufrufs; Kopien, die der Adapter
     * oder die JVM anlegt, sind nicht gemessen (`BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`).
     *
     * @spec SPEC-008
     */
    @Test
    fun `Das Passwort eines abgebrochenen wartenden Aufrufs bleibt bis zum Platz sichtbar`() = blocking {
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        val started = AtomicInteger()
        val blocker = object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult {
                started.incrementAndGet()
                running.countDown()
                release.await()
                return AuthResult.Accepted(Principal("u"))
            }
        }
        val executor = AuthExecutor(AuthPool(parallelism = 1))
        val first = executor.start(blocker, request("erster"))
        running.await()
        val waiting = request("wartend")
        val second = executor.start(blocker, waiting)
        second.cancel()
        delay(SETTLE_MILLIS)
        assertEquals("wartend", String(waiting.password.reveal()), "Passwort schon vor dem Platz ueberschrieben")
        val openSince = System.nanoTime()
        release.countDown()
        first.await()
        second.join()
        val windowMillis = (System.nanoTime() - openSince) / NANOS_PER_MILLI
        assertTrue(waiting.password.reveal().all { it == '\u0000' }, "Passwort nach dem Platz nicht ueberschrieben")
        assertEquals(1, started.get(), "der abgebrochene Aufruf begann")
        assertTrue(windowMillis < WINDOW_LIMIT_MILLIS, "Fenster nach der Freigabe: $windowMillis ms")
    }

    private companion object {
        const val HOLD_MILLIS = 300L
        const val SETTLE_MILLIS = 50L
        const val WINDOW_LIMIT_MILLIS = 2_000L
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
