package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.testsupport.blocking
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Die Zählerlogik der Drosselung (`ADR-0016`, `SPEC-053`) mit gesteuerter Uhr: Staffel, Fenster, Erfolg, laufende
 * Versuche, Tabellengrenze. Die Werte der Staffel stehen hier ausgeschrieben, nicht aus der Implementierung gelesen
 * (Handrechnung der ADR: 250 ms, verdoppelt, Deckel 5 s). Gemessen wird keine Zeit; die Wirkung auf eine echte
 * Wartezeit zeigt `LoginThrottleTimingTest`.
 */
class LoginThrottleTest {
    private var now = 0L
    private val pauses = mutableListOf<Duration>()

    private fun throttle(policy: ThrottlePolicy = ThrottlePolicy()) = LoginThrottle(policy, { now }, { pauses += it })

    private fun address(last: Int, second: Int = 0): InetAddress =
        InetAddress.getByAddress(byteArrayOf(10, second.toByte(), (last shr 8).toByte(), last.toByte()))

    private fun advance(duration: Duration) {
        now += duration.inWholeNanoseconds
    }

    private fun LoginThrottle.fail(source: InetAddress?): Duration {
        val attempt = enter(source)
        attempt.settle(failed = true)
        return attempt.wait
    }

    private fun LoginThrottle.waitNow(source: InetAddress?): Duration {
        val attempt = enter(source)
        attempt.settle(failed = false)
        return attempt.wait
    }

    /**
     * Orakel (Handrechnung): die ersten fünf Fehlversuche einer Adresse bleiben unverzögert, danach wartet der nächste
     * Versuch 250 ms, dann 500 ms, 1 s, 2 s, 4 s, und der Deckel 5 s gilt für alle weiteren.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Die Staffel beginnt nach fuenf Fehlversuchen und endet im Deckel`() {
        val throttle = throttle()
        val waits = List(12) { throttle.fail(address(1)) }
        val free = List(5) { Duration.ZERO }
        val staffel = listOf(250.milliseconds, 500.milliseconds, 1.seconds, 2.seconds, 4.seconds, 5.seconds, 5.seconds)
        assertEquals(free + staffel, waits)
    }

    /**
     * Orakel: ein Zähler fällt 60 s nach dem letzten Fehlversuch auf null. Bis 1 ns davor zählt er weiter, bei genau
     * 60 s ist die Adresse unverzögert.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Das Fenster endet sechzig Sekunden nach dem letzten Fehlversuch`() {
        val throttle = throttle()
        repeat(6) { throttle.fail(address(1)) }
        advance(60.seconds - 1.milliseconds)
        assertEquals(500.milliseconds, throttle.waitNow(address(1)), "kurz vor Ablauf des Fensters")
        advance(1.milliseconds)
        assertEquals(Duration.ZERO, throttle.waitNow(address(1)), "nach Ablauf des Fensters")
        assertEquals(Duration.ZERO, throttle.fail(address(1)), "der Zähler beginnt neu")
    }

    /**
     * Orakel: das Fenster rechnet ab dem letzten Fehlversuch, nicht ab dem ersten. Fünf Fehlversuche im Abstand von
     * 20 s liegen insgesamt über 60 s auseinander, der Zähler steht 20 s nach dem letzten trotzdem bei fünf.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Jeder Fehlversuch verschiebt das Fenster`() {
        val throttle = throttle()
        repeat(5) {
            throttle.fail(address(1))
            advance(20.seconds)
        }
        assertEquals(250.milliseconds, throttle.waitNow(address(1)))
    }

    /**
     * Orakel: ein Erfolg setzt den Zähler der Fehlversuche nicht zurück (ein gültiges Konto löscht den Zähler
     * fremder Versuche nicht). Nach fünf Fehlversuchen wartet auch der erfolgreiche Versuch, und der nächste wartet
     * wieder.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Ein Erfolg setzt die Fehlversuche nicht zurueck`() {
        val throttle = throttle()
        repeat(5) { throttle.fail(address(1)) }
        assertEquals(250.milliseconds, throttle.waitNow(address(1)), "erfolgreicher Versuch hinter fuenf Fehlversuchen")
        assertEquals(250.milliseconds, throttle.waitNow(address(1)), "der Erfolg hat den Zaehler nicht geleert")
    }

    /**
     * Orakel: ein Schwall gleichzeitiger Versuche sieht wachsende Zählerstände. Fünf laufende Versuche bleiben
     * unverzögert, der sechste wartet 250 ms, der siebte 500 ms. Enden alle ohne Fehlversuch, ist die Adresse
     * wieder frei und nicht mehr in der Tabelle.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Laufende Versuche zaehlen ab ihrem Beginn und werden bei Erfolg abgezogen`() {
        val throttle = throttle()
        val running = List(7) { throttle.enter(address(1)) }
        assertEquals(List(5) { Duration.ZERO } + listOf(250.milliseconds, 500.milliseconds), running.map { it.wait })
        running.forEach { it.settle(failed = false) }
        assertEquals(0, throttle.trackedAddresses)
        assertEquals(Duration.ZERO, throttle.waitNow(address(1)))
    }

    /**
     * Orakel: ein laufender Versuch, den der Adapter ablehnt, bleibt als Fehlversuch zurück. Vier abgelehnte Versuche
     * und ein laufender (ohne Fehlversuch bisher) lassen den sechsten Versuch warten; nach dem Ende des laufenden mit
     * Erfolg zählen die vier Fehlversuche allein, und der fünfte Versuch ist noch frei.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Fehlversuche und laufende Versuche addieren sich`() {
        val throttle = throttle()
        repeat(4) { throttle.fail(address(1)) }
        val open = throttle.enter(address(1))
        assertEquals(Duration.ZERO, open.wait, "fuenfter Versuch")
        val sixth = throttle.enter(address(1))
        assertEquals(250.milliseconds, sixth.wait, "vier Fehlversuche plus ein laufender")
        sixth.settle(failed = false)
        open.settle(failed = false)
        assertEquals(Duration.ZERO, throttle.waitNow(address(1)), "vier Fehlversuche allein")
    }

    /**
     * Orakel: ein Versuch, der endet, ohne dass der Adapter ablehnte (Abbruch, Adapterfehler, Fristablauf), zählt
     * keinen Fehlversuch; zweimal beendet zählt ein Versuch einmal.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Nur ein Fehlversuch zaehlt und ein Versuch endet genau einmal`() {
        val throttle = throttle()
        repeat(5) { throttle.enter(address(1)).settle(failed = false) }
        assertEquals(Duration.ZERO, throttle.waitNow(address(1)), "abgebrochene Versuche zaehlen nicht")
        val once = throttle.enter(address(2))
        repeat(2) { once.settle(failed = true) }
        repeat(3) { throttle.fail(address(2)) }
        assertEquals(Duration.ZERO, throttle.waitNow(address(2)), "ein Versuch zaehlt einmal: vier Fehlversuche")
        throttle.fail(address(2))
        assertEquals(250.milliseconds, throttle.waitNow(address(2)), "fuenf Fehlversuche")
    }

    /**
     * Orakel: der Schlüssel ist die Adresse. Eine andere Adresse hat ihren eigenen Zähler; ein Versuch ohne Adresse
     * wird nicht gedrosselt und legt keinen Tabelleneintrag an; die Zonenkennung einer IPv6-Adresse gehört nicht zum
     * Schlüssel.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Der Schluessel ist die Adresse`() {
        val throttle = throttle()
        repeat(8) { throttle.fail(address(1)) }
        assertEquals(Duration.ZERO, throttle.waitNow(address(2)), "andere Adresse")
        assertEquals(Duration.ZERO, throttle.waitNow(address(1, second = 1)), "andere Adresse im selben Netz")
        repeat(9) { assertEquals(Duration.ZERO, throttle.fail(null)) }
        assertEquals(1, throttle.trackedAddresses, "nur die Adresse mit Fehlversuchen bleibt in der Tabelle")
        val bytes = ByteArray(16) { if (it == 15) 1 else 0 }
        val plain = InetAddress.getByAddress(bytes)
        val scoped = Inet6Address.getByAddress(null, bytes, 1)
        repeat(5) { throttle.fail(scoped) }
        assertEquals(250.milliseconds, throttle.waitNow(plain), "dieselbe Adresse mit und ohne Zonenkennung")
    }

    /**
     * Orakel: die Tabelle überschreitet ihre Grenze nie. Mit der Voreinstellung (10 000 Adressen) bleibt sie bei
     * 10 000, auch nach 10 001 Adressen mit je einem Fehlversuch.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Die Tabelle haelt hoechstens zehntausend Adressen`() {
        val throttle = throttle()
        repeat(10_001) { throttle.fail(address(it % 65_536, it / 65_536)) }
        assertEquals(10_000, throttle.trackedAddresses)
    }

    /**
     * Orakel: bei voller Tabelle wird die Adresse mit dem ältesten Fehlversuch verdrängt, samt Zähler. Adresse 1
     * fehlt erneut, nachdem 2 und 3 gefehlt haben, und ist damit jünger als diese; Adresse 4 verdrängt 2, nicht 1.
     * Die verdrängte Adresse beginnt bei null.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Bei voller Tabelle wird die Adresse mit dem aeltesten Fehlversuch verdraengt`() {
        val throttle = throttle(ThrottlePolicy(freeFailures = 1, maxAddresses = 3))
        throttle.fail(address(1))
        throttle.fail(address(2))
        throttle.fail(address(2))
        throttle.fail(address(3))
        throttle.fail(address(1))
        throttle.fail(address(4))
        assertEquals(3, throttle.trackedAddresses)
        assertEquals(Duration.ZERO, throttle.waitNow(address(2)), "verdraengt: Zaehler bei null")
        assertEquals(500.milliseconds, throttle.waitNow(address(1)), "zwei Fehlversuche, nicht verdraengt")
    }

    /**
     * Orakel: die Verdrängung rechnet nach Anlage und letztem Fehlversuch, nicht nach dem Zustand der Versuche: ein
     * Eintrag, der nur laufende Versuche trägt, wird als ältester verdrängt, seine Zähler gehen verloren, und die
     * Einträge mit Fehlversuch bleiben.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Ein Eintrag mit nur laufenden Versuchen ist bei voller Tabelle verdraengbar`() {
        val throttle = throttle(ThrottlePolicy(freeFailures = 1, maxAddresses = 2))
        repeat(2) { throttle.enter(address(1)) }
        throttle.fail(address(2))
        throttle.fail(address(3))
        assertEquals(2, throttle.trackedAddresses)
        assertEquals(250.milliseconds, throttle.waitNow(address(2)), "Fehlversuch bleibt")
        assertEquals(250.milliseconds, throttle.waitNow(address(3)), "Fehlversuch bleibt")
    }

    /**
     * Orakel: eine Adresse, die während eines laufenden Versuchs verdrängt wird, bekommt kein zweites Mal einen
     * Eintrag durch das Ende dieses Versuchs; die Tabelle bleibt an der Grenze.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Das Ende eines Versuchs legt keinen verdraengten Eintrag neu an`() {
        val throttle = throttle(ThrottlePolicy(maxAddresses = 1))
        val open = throttle.enter(address(1))
        throttle.fail(address(2))
        open.settle(failed = true)
        assertEquals(1, throttle.trackedAddresses)
    }

    /**
     * Orakel: gleichzeitige Aufrufe aus mehreren Threads verlieren keinen Zähler. Nach acht Threads mit je 1000
     * Versuchen ohne Fehlversuch steht kein laufender Versuch mehr in der Tabelle; mit je 50 Fehlversuchen sind es
     * genau 400, und die Wartezeit steht im Deckel.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Gleichzeitige Aufrufe verlieren keinen Zaehler`() {
        val throttle = throttle()
        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            val done = CountDownLatch(THREADS)
            repeat(THREADS) {
                pool.execute {
                    repeat(1_000) { throttle.enter(address(1)).settle(failed = false) }
                    done.countDown()
                }
            }
            assertTrue(done.await(30, TimeUnit.SECONDS))
            assertEquals(0, throttle.trackedAddresses)
            val failures = CountDownLatch(THREADS)
            repeat(THREADS) {
                pool.execute {
                    repeat(50) { throttle.fail(address(2)) }
                    failures.countDown()
                }
            }
            assertTrue(failures.await(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(5.seconds, throttle.waitNow(address(2)))
        assertEquals(1, throttle.trackedAddresses)
    }

    /**
     * Orakel: [LoginThrottle.Attempt.waitTurn] hält für die Wartezeit an und überspringt eine Wartezeit von null.
     *
     * @spec SPEC-053
     */
    @Test
    fun `waitTurn wartet nur bei positiver Wartezeit`() = blocking {
        val throttle = throttle()
        repeat(5) { throttle.fail(address(1)) }
        val free = throttle.enter(address(2))
        free.waitTurn()
        assertEquals(emptyList<Duration>(), pauses)
        val slow = throttle.enter(address(1))
        slow.waitTurn()
        assertEquals(listOf(250.milliseconds), pauses)
    }

    @Test
    fun `Ungueltige Grenzwerte werden abgewiesen`() {
        assertFailsWith<IllegalArgumentException> { ThrottlePolicy(maxAddresses = 0) }
        assertFailsWith<IllegalArgumentException> { ThrottlePolicy(initialWait = 10.seconds) }
        assertFailsWith<IllegalArgumentException> { ThrottlePolicy(window = Duration.ZERO) }
    }

    private companion object {
        const val THREADS = 8
    }
}
