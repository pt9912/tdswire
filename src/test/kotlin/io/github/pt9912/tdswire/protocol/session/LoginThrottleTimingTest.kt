package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.delay
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Wirkung der Drosselung auf die Wartezeit in Echtzeit (`ADR-0016`, `SPEC-053`), wie `AuthPoolOccupationTest` sie für
 * die Plätze misst: echte Uhr, echtes Warten, kleine Grenzwerte ([policy]). Gemessen wird die Wartezeit einer
 * Anmeldung vor dem Adapteraufruf, nicht die Rechenzeit einer Ableitung.
 *
 * Schranken (Zeitmessung unter Last ist fragil, deshalb weit gesetzt): die Untergrenze einer Wartezeit ist die
 * nominelle Wartezeit (ein Warten endet nicht früher; 10 % Spiel für die Rundung auf Millisekunden), die Obergrenze
 * der freien Versuche ist die kleinste Wartezeit der Staffel (ein Versuch ohne Wartezeit tut nichts außer einem
 * Tabellenzugriff; ein Stillstand der JVM von 200 ms wäre nötig, um sie zu reißen), die Obergrenze einer gedrosselten
 * Wartezeit ist die nominelle plus [SLACK].
 *
 * Messzeile (drei Läufe von `make test` auf dem Entwicklungsrechner, während zwei weitere Docker-Läufe liefen): freie
 * Versuche 1 µs bis 0,3 ms; Wartezeiten nominell 200 ms gemessen 200,18 bis 200,23 ms, nominell 400 ms gemessen
 * 400,15 bis 400,30 ms. Weitere Läufe unter anderer Last sind nicht gemessen.
 */
class LoginThrottleTimingTest {
    private val source = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 7))
    private val policy = ThrottlePolicy(
        freeFailures = 2,
        window = 1.seconds,
        initialWait = 200.milliseconds,
        maxWait = 400.milliseconds,
    )

    private suspend fun LoginThrottle.timedAttempt(failed: Boolean): Pair<Duration, Duration> {
        val attempt = enter(source)
        val elapsed = measureTime { attempt.waitTurn() }
        attempt.settle(failed)
        return attempt.wait to elapsed
    }

    /**
     * Orakel: zwei Fehlversuche bleiben ohne Wartezeit; jeder weitere hält für die Staffel 200, 400, 400 ms an (Deckel)
     * und nie kürzer. Nach Ablauf des Fensters (1 s nach dem letzten Fehlversuch) ist die nächste Anmeldung wieder
     * ohne Wartezeit: eine reguläre Anmeldung ist nicht dauerhaft blockiert.
     *
     * @spec SPEC-053
     * @spec SPEC-008
     */
    @Test
    fun `Wiederholte Fehlversuche warten gemessen die Staffel, danach ist die Adresse frei`() = blocking {
        val throttle = LoginThrottle(policy)
        val free = List(2) { throttle.timedAttempt(failed = true) }
        free.forEach { (wait, elapsed) ->
            assertEquals(Duration.ZERO, wait)
            assertTrue(elapsed < policy.initialWait, "freier Versuch: $elapsed")
        }
        val nominal = listOf(200.milliseconds, 400.milliseconds, 400.milliseconds)
        nominal.forEach { expected ->
            val (wait, elapsed) = throttle.timedAttempt(failed = true)
            assertEquals(expected, wait)
            assertTrue(elapsed >= expected * LOWER_FACTOR, "gewartet $elapsed, nominell $expected")
            assertTrue(elapsed < expected + SLACK, "gewartet $elapsed, nominell $expected")
        }
        delay(policy.window + 100.milliseconds)
        val (wait, elapsed) = throttle.timedAttempt(failed = false)
        assertEquals(Duration.ZERO, wait, "nach Ablauf des Fensters")
        assertTrue(elapsed < policy.initialWait, "freier Versuch nach dem Fenster: $elapsed")
    }

    private companion object {
        const val LOWER_FACTOR = 0.9
        val SLACK = 1.seconds
    }
}
