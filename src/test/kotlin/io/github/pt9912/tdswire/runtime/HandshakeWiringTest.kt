package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.protocol.session.AuthPool
import io.github.pt9912.tdswire.protocol.session.LoginResponses
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.ConnectionEnd
import io.github.pt9912.tdswire.transport.listener.ManualDelayer
import io.github.pt9912.tdswire.transport.listener.RecordingEvents
import io.github.pt9912.tdswire.transport.listener.eventually
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Listener, Handshake-Treiber und Adapter über den echten Handler des Prozesses (`ARC-008`, `SPEC-008`). */
internal class HandshakeWiringTest : RuntimeTestBase() {
    private val events = RecordingEvents()
    private val threads = Executors.newFixedThreadPool(6).asCoroutineDispatcher()

    @AfterTest
    fun closeThreads() = threads.close()

    private fun environment(vararg more: Pair<String, String>) =
        loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"), *more)

    private fun login(client: RawClient, user: String = "alice", password: String = "secret"): RawMessage {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = user
            this.password = password.toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        return assertNotNull(client.readMessage())
    }

    /** Zählt gleichzeitige Aufrufe; jeder wartet höchstens eine Sekunde auf einen zweiten. */
    private class OverlapProbe : AuthAdapter {
        val maxConcurrent = AtomicInteger()
        private val current = AtomicInteger()
        private val second = CountDownLatch(2)

        override fun authenticate(request: AuthRequest): AuthResult {
            maxConcurrent.accumulateAndGet(current.incrementAndGet()) { a, b -> maxOf(a, b) }
            second.countDown()
            second.await(1, TimeUnit.SECONDS)
            current.decrementAndGet()
            return AuthResult.Accepted(Principal("alice"))
        }
    }

    private suspend fun overlapWith(parallelism: Int): Int {
        val probe = OverlapProbe()
        val wiring = Wiring(log = log, authPool = AuthPool(threads, parallelism), adapter = { probe })
        val server = running(environment(), wiring)
        coroutineScope {
            List(2) { async(threads) { RawClient(server.port).use { login(it) } } }.awaitAll()
        }
        return probe.maxConcurrent.get()
    }

    /**
     * Zwei Verbindungen teilen einen `AuthPool`: bei einem Platz laufen nie zwei Adapteraufrufe zugleich, bei
     * zwei Plätzen überlappen sie (Gegenprobe: die Messanordnung kann Überlappung sehen). Die Zahl der Plätze
     * ist Festlegung, nicht gemessen; die Wartezeit des Adapters im Test ist eine Obergrenze, keine Aussage.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Zwei Verbindungen teilen sich einen AuthPool`() = blocking {
        assertEquals(1, overlapWith(parallelism = 1))
        assertEquals(2, overlapWith(parallelism = 2))
    }

    /**
     * Eine `CancellationException` des Adapters ohne Abbruch der Verbindung wird am echten Handler wie eine
     * Ablehnung beantwortet (feste Antwort), die Verbindung endet als `HANDLER_RETURNED`, nicht als
     * `HANDLER_FAILED` und nicht als `SHUTDOWN`. Das belegt die Setzung des Listeners gegen den Handshake-Handler,
     * mit einem Adapter, der von selbst abbricht.
     *
     * @spec SPEC-008
     * @spec SPEC-048
     */
    @Test
    fun `Eine Abbruchausnahme des Adapters ist ein Adapterfehler und kein Handlerfehler`() = blocking {
        val cancelling = object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult = throw CancellationException("x")
        }
        val wiring = Wiring(log = log, events = events, adapter = { cancelling })
        val server = running(environment(), wiring)
        val answer = RawClient(server.port).use { login(it) }
        assertContentEquals(LoginResponses.rejection.payload, answer.payload)
        eventually("Verbindung beendet") { events.closed().isNotEmpty() }
        val closed = events.closed().single()
        assertEquals(ConnectionEnd.HANDLER_RETURNED, closed.end)
        assertNull(closed.failure)
        assertTrue(log.lines.any { it.startsWith("Login: Adapterfehler") }, log.lines.toString())
    }

    /**
     * Wird der Server mitten im Handshake beendet, ist es für den echten Handler das Ende durch den Abbruch
     * (`SHUTDOWN`), kein Handlerfehler; die Gegenstelle sieht das Ende ohne ein Byte.
     *
     * @spec SPEC-044
     */
    @Test
    fun `Stop im Handshake ist SHUTDOWN und kein Handlerfehler`() = blocking {
        val server = running(environment(), Wiring(log = log, events = events))
        RawClient(server.port).use { client ->
            eventually("Verbindung angenommen") { events.events.isNotEmpty() }
            server.stop()
            assertTrue(client.closedByServerWithoutBytes())
        }
        val closed = events.closed().single()
        assertEquals(ConnectionEnd.SHUTDOWN, closed.end)
        assertNull(closed.failure)
    }

    /**
     * Stop mit einer angemeldeten, ruhenden Verbindung: der Stop kehrt zurück, die Verbindung ist getrennt, ihr Ende
     * ist `SHUTDOWN` (kein Handlerfehler), und der Listener hält keine Verbindung mehr.
     *
     * @spec SPEC-044
     */
    @Test
    fun `Stop trennt eine angemeldete Verbindung`() = blocking {
        val server = running(environment(), Wiring(log = log, events = events))
        RawClient(server.port).use { client ->
            assertEquals(4, login(client).type)
            withTimeout(STOP_MILLIS) { server.stop() }
            assertTrue(client.closedByServerWithoutBytes())
        }
        val closed = events.closed().single()
        assertEquals(ConnectionEnd.SHUTDOWN, closed.end)
        assertNull(closed.failure)
    }

    /**
     * `LOGIN_TIMEOUT` beginnt mit der Annahme der Verbindung und trägt den konfigurierten Wert (7 s und 11 s,
     * nicht den Standard 15 s); die Frist läuft, ohne dass der Client ein Byte gesendet hat. Nach ihrem Ablauf
     * schließt der Server ohne Antwort (`SPEC-008`).
     *
     * @spec SPEC-008
     */
    @Test
    fun `LOGIN_TIMEOUT beginnt mit der Annahme und schliesst ohne Antwort`() = blocking {
        listOf(7, 11).forEach { seconds ->
            val delayer = ManualDelayer()
            val recorded = RecordingEvents()
            val wiring = Wiring(events = recorded, delayer = delayer)
            val server = running(environment(ConfigKeys.LOGIN_TIMEOUT to "$seconds"), wiring)
            RawClient(server.port).use { client ->
                eventually("Frist läuft") { delayer.pending == listOf(seconds.seconds) }
                delayer.releaseAll()
                assertTrue(client.closedByServerWithoutBytes())
            }
            eventually("Ende gemeldet") { recorded.closed().isNotEmpty() }
            assertEquals(ConnectionEnd.LOGIN_TIMEOUT, recorded.closed().single().end)
        }
    }

    /**
     * Nach der Annahme meldet der Handler „Login abgeschlossen“: die Frist ist beendet, die Verbindung bleibt
     * offen. Die erste Nachricht danach beendet sie (es gibt noch keinen Anfragepfad; Festlegung): der Handler
     * kehrt zurück, `HANDLER_RETURNED`.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Nach dem Login gilt LOGIN_TIMEOUT nicht mehr`() = blocking {
        val delayer = ManualDelayer()
        val server = running(environment(), Wiring(events = events, delayer = delayer))
        RawClient(server.port).use { client ->
            assertEquals(4, login(client).type)
            eventually("Frist beendet") { delayer.pending.isEmpty() }
            delayer.releaseAll()
            assertTrue(events.closed().isEmpty(), "Verbindung nach der Frist noch offen")
            client.send(handMessage(BATCH_TYPE, ByteArray(10), 4000))
            assertTrue(client.closedByServerWithoutBytes())
        }
        eventually("Ende gemeldet") { events.closed().isNotEmpty() }
        assertEquals(ConnectionEnd.HANDLER_RETURNED, events.closed().single().end)
    }

    private fun preloginWithInstance(bytes: Int): ByteArray {
        val versionEntry = byteArrayOf(0, 0, 16, 0, 6)
        val encryptionEntry = byteArrayOf(1, 0, 22, 0, 1)
        val instanceEntry = byteArrayOf(2, 0, 23, (bytes shr 8).toByte(), bytes.toByte())
        val table = versionEntry + encryptionEntry + instanceEntry + byteArrayOf(0xFF.toByte())
        val version = ByteArray(6)
        val encryption = byteArrayOf(SessionVectors.ENCRYPT_OFF.toByte())
        val instance = ByteArray(bytes - 1) { 'a'.code.toByte() } + byteArrayOf(0)
        return handMessage(PRELOGIN_TYPE, table + version + encryption + instance, 4000)
    }

    /**
     * `MAX_PRELOGIN_MESSAGE_BYTES` gilt am Draht: bei 512 wird eine Nachricht von genau 512 Byte samt Paketkopf
     * beantwortet, eine von 513 Byte nicht (Schließen ohne Antwort); mit dem Standard 65536 wird auch die von
     * 513 Byte beantwortet. Gezählt wird nach `FramingLimits` (deklarierte Paketlängen samt Kopf).
     *
     * @spec SPEC-046
     */
    @Test
    fun `MAX_PRELOGIN_MESSAGE_BYTES begrenzt die Nachricht vor dem Login`() = blocking {
        val limited = running(environment(ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES to "512"))
        val open = running(environment())
        val headerAndTable = 8 + 16 + 6 + 1
        RawClient(limited.port).use {
            it.send(preloginWithInstance(512 - headerAndTable))
            assertNotNull(it.readMessage())
        }
        RawClient(limited.port).use {
            it.send(preloginWithInstance(513 - headerAndTable))
            assertTrue(it.closedByServerWithoutBytes())
        }
        RawClient(open.port).use {
            it.send(preloginWithInstance(513 - headerAndTable))
            assertNotNull(it.readMessage())
        }
    }

    /**
     * Die Richtlinie der Konfiguration erreicht die Aushandlung: im unverschlüsselten Testmodus antwortet der
     * Server `ENCRYPT_NOT_SUP` (0x02), und ein Client mit `ENCRYPT_ON` wird nicht angenommen (kein stilles
     * Herabstufen in die andere Richtung).
     *
     * @spec SPEC-010
     */
    @Test
    fun `Der Testmodus antwortet ENCRYPT_NOT_SUP und weist ENCRYPT_ON ab`() = blocking {
        val server = running(environment())
        RawClient(server.port).use {
            it.send(preloginWire(SessionVectors.ENCRYPT_NOT_SUP))
            assertEquals(SessionVectors.ENCRYPT_NOT_SUP, encryptionOf(assertNotNull(it.readMessage()).payload))
        }
        RawClient(server.port).use {
            it.send(preloginWire(SessionVectors.ENCRYPT_ON))
            assertTrue(it.closedByServerWithoutBytes())
        }
    }

    private companion object {
        const val STOP_MILLIS = 10_000L
    }
}
