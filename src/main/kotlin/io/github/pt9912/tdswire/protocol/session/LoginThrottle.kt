package io.github.pt9912.tdswire.protocol.session

import kotlinx.coroutines.delay
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Die Grenzwerte der Drosselung wiederholter Anmeldeversuche (`ADR-0016`, `SPEC-053`): [freeFailures] Versuche je
 * Adresse bleiben unverzögert, ein Zähler fällt [window] nach dem letzten Fehlversuch auf null, danach wartet die
 * Anmeldung [initialWait], verdoppelt je weiterem Versuch bis [maxWait]; die Tabelle hält höchstens [maxAddresses]
 * Adressen. Die Voreinstellungen sind Setzung, nicht gemessen und nicht konfigurierbar; die Parameter gibt es,
 * damit Tests kleine Werte setzen.
 */
internal data class ThrottlePolicy(
    val freeFailures: Int = 5,
    val window: Duration = 60.seconds,
    val initialWait: Duration = 250.milliseconds,
    val maxWait: Duration = 5.seconds,
    val maxAddresses: Int = 10_000,
) {
    init {
        require(freeFailures >= 0 && maxAddresses >= 1) { "freeFailures >= 0 und maxAddresses >= 1" }
        require(window.isPositive() && initialWait.isPositive() && maxWait >= initialWait) {
            "window und initialWait positiv, maxWait nicht kleiner als initialWait"
        }
    }
}

/**
 * Verzögert wiederholte Anmeldeversuche je Quelladresse (`ADR-0016`, Option B), ohne Sperre. Der Schlüssel ist die
 * Adresse der Gegenstelle; Kennung und Passwort gehen nicht ein, die Wartezeit hängt nur von der Adresse ab.
 *
 * Je Adresse zählen zwei Zähler: die Fehlversuche (ein Versuch, den der Adapter ablehnt; ein Erfolg setzt sie nicht
 * zurück, sie fallen [ThrottlePolicy.window] nach dem letzten Fehlversuch auf null) und die laufenden Versuche (ein
 * Versuch zählt ab [enter] und wird bei [Attempt.settle] abgezogen). Beginnt ein Versuch, wartet er so lange, wie
 * die Summe beider Zähler es nach der Staffel der [ThrottlePolicy] verlangt; ein Schwall gleichzeitiger Versuche
 * sieht damit wachsende Zählerstände. Die Wartezeit ist ein Anhalten der Coroutine über [Attempt.waitTurn].
 *
 * Die Tabelle hält höchstens [ThrottlePolicy.maxAddresses] Adressen; für eine neue Adresse bei voller Tabelle wird
 * die am längsten nicht berührte verdrängt (Anlage oder letzter Fehlversuch zählt), samt ihren Zählern. Ein Versuch
 * ohne Adresse ([enter] mit `null`) wird nicht gedrosselt: der Kanal kennt keine Gegenstelle. Eine verteilte Suche
 * über viele Adressen bleibt ungebremst (Grenze der Wahl, `ADR-0016`).
 *
 * [nanos] ist eine monotone Uhr, [pause] wartet; beide sind austauschbar, damit Tests die Zeit steuern.
 */
internal class LoginThrottle(
    private val policy: ThrottlePolicy = ThrottlePolicy(),
    private val nanos: () -> Long = System::nanoTime,
    private val pause: suspend (Duration) -> Unit = { delay(it) },
) {
    internal class Entry(var failures: Int = 0, var lastFailure: Long = 0, var running: Int = 0)

    private val lock = Any()
    private val table = LinkedHashMap<String, Entry>()

    /** Zahl der Adressen in der Tabelle; nie über [ThrottlePolicy.maxAddresses]. */
    val trackedAddresses: Int get() = synchronized(lock) { table.size }

    /** Beginnt einen Versuch von [source] und legt seine Wartezeit fest; [Attempt.settle] beendet ihn. */
    fun enter(source: InetAddress?): Attempt {
        if (source == null) return Attempt(null, "", null, Duration.ZERO)
        val key = source.hostAddress.substringBefore('%')
        return synchronized(lock) {
            val entry = table[key]?.also { expire(it, nanos()) } ?: admit(key)
            val wait = waitFor(entry.failures + entry.running)
            entry.running++
            Attempt(this, key, entry, wait)
        }
    }

    private fun admit(key: String): Entry {
        if (table.size >= policy.maxAddresses) table.remove(table.keys.first())
        return Entry().also { table[key] = it }
    }

    private fun expire(entry: Entry, now: Long) {
        if (entry.failures > 0 && (now - entry.lastFailure).nanoseconds >= policy.window) entry.failures = 0
    }

    private fun waitFor(level: Int): Duration {
        val over = level - policy.freeFailures
        if (over < 0) return Duration.ZERO
        var wait = policy.initialWait
        var doublings = 0
        while (doublings < over && wait < policy.maxWait) {
            wait *= 2
            doublings++
        }
        return minOf(wait, policy.maxWait)
    }

    private fun settle(key: String, entry: Entry, failed: Boolean) = synchronized(lock) {
        entry.running--
        val tracked = table[key] === entry
        if (failed) {
            val now = nanos()
            expire(entry, now)
            entry.failures++
            entry.lastFailure = now
            if (tracked) {
                table.remove(key)
                table[key] = entry
            }
        } else if (tracked && entry.running == 0 && entry.failures == 0) {
            table.remove(key)
        }
    }

    /**
     * Ein laufender Versuch. [wait] ist die Wartezeit vor der Prüfung der Zugangsdaten. [settle] beendet den
     * Versuch genau einmal: `failed` zählt einen Fehlversuch (der Adapter hat abgelehnt); Erfolg, Adapterfehler
     * und Abbruch zählen keinen. Ein Aufrufer ruft [settle] in jedem Fall, auch beim Abbruch.
     */
    class Attempt internal constructor(
        private val owner: LoginThrottle?,
        private val key: String,
        private val entry: Entry?,
        val wait: Duration,
    ) {
        private val open = AtomicBoolean(true)

        /** Hält die Coroutine für [wait] an; ohne Wartezeit kehrt es sofort zurück. */
        suspend fun waitTurn() {
            if (owner != null && wait.isPositive()) owner.pause(wait)
        }

        fun settle(failed: Boolean) {
            if (owner != null && entry != null && open.getAndSet(false)) owner.settle(key, entry, failed)
        }
    }
}
