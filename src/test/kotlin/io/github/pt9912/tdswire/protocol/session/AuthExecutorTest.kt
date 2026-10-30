package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.delay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthExecutorTest {
    private fun request(password: String): AuthRequest {
        val payload = SessionVectors.login7Payload { this.password = password.toCharArray() }
        val admission = LoginAdmission.admit(payload, NegotiatedEncryption.REQUIRED)
        return assertIs<Admission.Admitted>(admission).request
    }

    /**
     * Ein Aufruf, der bei Abbruch noch auf einen Platz wartet, beginnt nie und überschreibt sein
     * Passwort trotzdem; der laufende Aufruf hält den einzigen Platz, bis er endet. Der abgebrochene
     * Aufruf endet erst, wenn die Warteschlange ihn erreicht.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Ein wartender Aufruf beginnt nach dem Abbruch nie und das Passwort ist ueberschrieben`() = blocking {
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val adapter = object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult {
                calls.incrementAndGet()
                release.await()
                return AuthResult.Accepted(Principal("sa"))
            }
        }
        val executor = AuthExecutor(AuthPool(parallelism = 1))
        val first = executor.start(adapter, request("eins"))
        while (calls.get() == 0) delay(POLL_MILLIS)
        val waiting = request("zwei")
        val second = executor.start(adapter, waiting)
        second.cancel()
        release.countDown()
        assertIs<AuthOutcome.Decided>(first.await())
        second.join()
        assertEquals(1, calls.get(), "der wartende Aufruf begann")
        val wiped = waiting.password.reveal().all { it == '\u0000' }
        assertTrue(wiped, "Passwort des wartenden Aufrufs nicht ueberschrieben")
    }

    private companion object {
        const val POLL_MILLIS = 5L
    }
}
