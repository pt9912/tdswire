package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthFeatures
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.ConnectionContext
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.RejectReason
import io.github.pt9912.tdswire.protocol.session.FakeHandshakeChannel.Companion.message
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Die Drosselung im Handshake-Treiber (`ADR-0016`, `SPEC-053`): wann gewartet wird (vor dem Adapteraufruf), was
 * zählt (nur eine Ablehnung des Adapters), was nicht eingeht (Kennung, Passwort), und was das Warten nicht belegt
 * (Plätze des [AuthPool]). Die Uhr ist gesteuert, nur der Test der Frist läuft in Echtzeit.
 */
class LoginThrottleDriverTest {
    private val source = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1))
    private val now = 0L
    private val pauses = mutableListOf<Duration>()
    private val rejecting = RecordingAdapter { AuthResult.Rejected(RejectReason.WRONG_PASSWORD) }

    private fun throttle(pause: suspend (Duration) -> Unit = { pauses += it }) =
        LoginThrottle(ThrottlePolicy(), { now }, pause)

    /** Eine Drosselung, die jeden Versuch warten lässt und das Warten nie beendet; [waiting] meldet den Beginn. */
    private fun hanging(waiting: CompletableDeferred<Unit>) = LoginThrottle(ThrottlePolicy(freeFailures = 0), { now }) {
        waiting.complete(Unit)
        awaitCancellation()
    }

    private fun driver(
        adapter: AuthAdapter,
        throttle: LoginThrottle,
        timeout: Duration = 10.seconds,
        pool: AuthPool = AuthPool(throttle = throttle),
    ) = HandshakeDriver(HandshakeConfig(), adapter, HandshakeLog.NONE, timeout, pool, { "s1" })

    private fun script(user: String = "sa", password: String = "pw") = FakeHandshakeChannel(
        listOf(
            message(SessionVectors.prelogin()),
            message(
                SessionVectors.login7 {
                    userName = user
                    this.password = password.toCharArray()
                },
            ),
        ),
    )

    private fun request(password: String = "pw") = AuthRequest(
        userName = "sa",
        password = PasswordChars(password.toCharArray()),
        context = ConnectionContext("master", "app", "host", false),
        encryption = NegotiatedEncryption.ON,
        features = AuthFeatures(integratedSecurity = false, federatedAuthentication = false, changePassword = false),
    )

    /**
     * Orakel: nach fünf abgelehnten Anmeldungen einer Adresse hält die sechste für 250 ms an, und zwar bevor der
     * Adapter zum sechsten Mal gerufen wird (die Wartezeit liegt vor der Prüfung der Zugangsdaten). Die Antwort bleibt
     * die feste Ablehnung, die Verbindung endet wie ohne Drosselung.
     *
     * @spec SPEC-053
     * @spec SPEC-012
     */
    @Test
    fun `Die sechste Ablehnung wartet vor dem Adapteraufruf`() = blocking {
        val callsAtPause = mutableListOf<Int>()
        val throttle = throttle {
            pauses += it
            callsAtPause += rejecting.calls
        }
        repeat(5) { driver(rejecting, throttle).run(script(), source) }
        assertEquals(emptyList<Duration>(), pauses, "die ersten fuenf Versuche warten nicht")
        val channel = script()
        val outcome = driver(rejecting, throttle).run(channel, source)
        assertEquals(listOf(250.milliseconds), pauses)
        assertEquals(listOf(5), callsAtPause, "Adapteraufrufe zum Zeitpunkt des Wartens")
        assertEquals(6, rejecting.calls)
        assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_REJECTED), outcome)
        assertContentEquals(LoginResponses.rejection.payload, channel.sent.last().payload)
        assertTrue(channel.closed)
    }

    /**
     * Orakel: die Wartezeit hängt nicht von Kennung oder Passwort ab. Zwei Folgen von je acht Versuchen, die eine mit
     * einer bekannten, die andere mit einer unbekannten Kennung gegen den Adapter der Benutzerliste, ergeben dieselben
     * Wartezeiten und in jedem Schritt dieselben Antwortbytes.
     *
     * @spec SPEC-053
     * @spec SPEC-012
     */
    @Test
    fun `Die Wartezeit und die Antwort sind fuer bekannte und unbekannte Kennung gleich`() = blocking {
        val adapter = RecordingAdapter { request ->
            val known = request.userName == "alice"
            AuthResult.Rejected(if (known) RejectReason.WRONG_PASSWORD else RejectReason.UNKNOWN_USER)
        }
        suspend fun sequence(user: String): Pair<List<Duration>, List<List<Byte>>> {
            val recorded = mutableListOf<Duration>()
            val throttle = throttle { recorded += it }
            val answers = List(8) {
                val channel = script(user = user)
                driver(adapter, throttle).run(channel, source)
                channel.sent.last().payload.toList()
            }
            return recorded to answers
        }

        val known = sequence("alice")
        val unknown = sequence("mallory")
        assertEquals(listOf(250.milliseconds, 500.milliseconds, 1.seconds), known.first)
        assertEquals(known.first, unknown.first)
        assertEquals(known.second, unknown.second)
    }

    /**
     * Orakel: gezählt wird eine Ablehnung des Adapters. Sechs Annahmen, sechs Adapterfehler und sechs Anmeldungen
     * ohne Adresse lassen die nächste Anmeldung unverzögert.
     *
     * @spec SPEC-053
     */
    @Test
    fun `Annahme, Adapterfehler und fehlende Adresse zaehlen nicht`() = blocking {
        val throttle = throttle()
        val accepting = RecordingAdapter.accepting()
        val failing = object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult = error("Adapterfehler")
        }
        repeat(6) { driver(accepting, throttle).run(script(), source) }
        repeat(6) { driver(failing, throttle).run(script(), source) }
        repeat(6) { driver(rejecting, throttle).run(script()) }
        assertEquals(emptyList<Duration>(), pauses)
        val outcome = driver(rejecting, throttle).run(script(), source)
        assertIs<HandshakeOutcome.Closed>(outcome)
        assertEquals(emptyList<Duration>(), pauses, "der erste Fehlversuch der Adresse")
    }

    /**
     * Orakel: bricht der Aufrufer das Warten ab, ruft der Treiber den Adapter nicht, das Passwort ist überschrieben,
     * und der Versuch ist beendet (die Adresse bleibt nicht als laufend in der Tabelle). Während des Wartens ist das
     * Passwort noch lesbar und der Versuch zählt als laufend.
     *
     * @spec SPEC-053
     * @spec SPEC-051
     */
    @Test
    fun `Ein abgebrochenes Warten ruft den Adapter nicht und ueberschreibt das Passwort`() = blocking {
        val waiting = CompletableDeferred<Unit>()
        val throttle = hanging(waiting)
        val secret = request("geheim")
        val job = launch { driver(rejecting, throttle).authenticate(secret, source) }
        withTimeout(WAIT_MILLIS) { waiting.await() }
        assertEquals("geheim", String(secret.password.reveal()), "Passwort waehrend des Wartens")
        assertEquals(1, throttle.trackedAddresses)
        job.cancelAndJoin()
        assertEquals(0, rejecting.calls)
        assertTrue(secret.password.reveal().all { it == '\u0000' }, "Passwort nach dem Abbruch")
        assertEquals(0, throttle.trackedAddresses)
    }

    /**
     * Orakel: ein Warten, das noch läuft, hält keinen Platz des [AuthPool]. Hängt eine Anmeldung in der Wartezeit
     * und hat der Pool genau einen Platz, läuft ein zweiter Aufruf auf diesem Pool trotzdem durch.
     *
     * @spec SPEC-053
     * @spec SPEC-008
     */
    @Test
    fun `Das Warten belegt keinen Platz des AuthPool`() = blocking {
        val waiting = CompletableDeferred<Unit>()
        val throttle = hanging(waiting)
        val pool = AuthPool(parallelism = 1, throttle = throttle)
        val job = launch { driver(rejecting, throttle, pool = pool).run(script(), source) }
        withTimeout(WAIT_MILLIS) { waiting.await() }
        val other = AuthExecutor(pool).start(RecordingAdapter.accepting(), request())
        assertIs<AuthOutcome.Decided>(withTimeout(WAIT_MILLIS) { other.await() })
        assertEquals(0, rejecting.calls, "der wartende Versuch hat den Adapter nicht gerufen")
        job.cancelAndJoin()
    }

    /**
     * Orakel: die Wartezeit gehört zu `LOGIN_TIMEOUT` und wird nicht an sie gebunden. Eine Frist von 300 ms bei einer
     * Wartezeit, die nicht endet, schließt die Verbindung ohne Antwort auf das LOGIN7 (nur die Antwort auf PRELOGIN
     * wurde gesendet) und ohne Adapteraufruf; der Treiber meldet `LOGIN_TIMEOUT`. Echtzeit: die Frist ist weit unter
     * dem Wartezeitlimit des Tests, die Wartezeit endet nie.
     *
     * @spec SPEC-053
     * @spec SPEC-008
     */
    @Test
    fun `Eine Wartezeit ueber LOGIN_TIMEOUT endet ohne Antwort`() = blocking {
        val throttle = hanging(CompletableDeferred())
        val channel = script()
        val outcome = driver(rejecting, throttle, timeout = 300.milliseconds).run(channel, source)
        assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_TIMEOUT), outcome)
        assertEquals(1, channel.sent.size, "nur die Antwort auf PRELOGIN")
        assertEquals(0, rejecting.calls)
        assertTrue(channel.closed)
        assertNull(rejecting.userName)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
