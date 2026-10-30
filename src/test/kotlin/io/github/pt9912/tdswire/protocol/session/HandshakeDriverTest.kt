package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListAuthAdapter
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.protocol.session.FakeHandshakeChannel.Companion.message
import io.github.pt9912.tdswire.testsupport.Login7Builder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HandshakeDriverTest {
    private val log = CollectingLog()

    private fun driver(
        adapter: RecordingAdapter,
        config: HandshakeConfig = HandshakeConfig(),
        timeout: Duration = 10.seconds,
    ) = HandshakeDriver(config, adapter, log, timeout, sessionIds = { "s1" })

    private fun happyScript(login: () -> TdsMessage = { SessionVectors.login7() }) =
        listOf(message(SessionVectors.prelogin()), message(login()))

    private fun userListAdapter(): UserListAuthAdapter {
        val parsed = UserList.parse("alice\t{plain}secret\n", plainTestMode = true)
        return UserListAuthAdapter(assertIs<UserListResult.Valid>(parsed).list)
    }

    /**
     * TDS 7.x: PRELOGIN, Antwort, TLS-Handschlag, LOGIN7, Adapter, Annahme. Die Sitzung trägt ID,
     * Principal, Datenbankkontext und Packet Size; die Rahmen-Grenze wechselt genau einmal; der Adapter
     * bekommt die Werte aus LOGIN7 und den ausgehandelten Modus; das Passwort ist danach überschrieben.
     *
     * @spec SPEC-001
     * @spec SPEC-028
     */
    @Test
    fun `Annahme in der Reihenfolge PRELOGIN vor TLS`() = blocking {
        val adapter = RecordingAdapter.accepting("sa")
        val channel = FakeHandshakeChannel(happyScript { SessionVectors.login7 { password = "geheim".toCharArray() } })
        val outcome = driver(adapter).run(channel)
        val ready = assertIs<HandshakeOutcome.Ready>(outcome)
        assertEquals("s1", ready.session.id)
        assertEquals(Principal("sa"), ready.session.principal)
        assertEquals("d", ready.session.database)
        assertEquals(8000, ready.session.packetSize)
        assertEquals(1, channel.tlsCalls)
        assertEquals(1, channel.limitSwitches)
        assertEquals(listOf("send-start", "send-end", "tls", "send-start", "send-end"), channel.trace)
        assertEquals(listOf(PacketType.TABULAR_RESULT, PacketType.TABULAR_RESULT), channel.sent.map { it.type })
        assertEquals(1, adapter.calls)
        assertEquals("sa", adapter.userName)
        assertEquals("geheim", adapter.passwordDuringCall)
        val request: AuthRequest = checkNotNull(adapter.lastRequest)
        assertEquals(NegotiatedEncryption.REQUIRED, request.encryption)
        assertEquals("d", request.context.database)
        assertFalse(request.features.integratedSecurity)
        assertFalse(channel.closed)
        assertTrue(checkNotNull(adapter.passwordChars).reveal().all { it == '\u0000' }, "Passwort nicht ueberschrieben")
    }

    /**
     * TDS 8.0: TLS vor jedem PRELOGIN. Der Handschlag ist die erste Handlung, PRELOGIN und LOGIN7 folgen
     * ohne weiteren Handschlag.
     *
     * @spec SPEC-001
     * @spec SPEC-028
     */
    @Test
    fun `Annahme in der Reihenfolge TLS vor PRELOGIN`() = blocking {
        val adapter = RecordingAdapter.accepting()
        val channel = FakeHandshakeChannel(happyScript())
        val config = HandshakeConfig(order = HandshakeOrder.TLS_FIRST)
        assertIs<HandshakeOutcome.Ready>(driver(adapter, config).run(channel))
        assertEquals(1, channel.tlsCalls)
        assertEquals(listOf("tls", "send-start", "send-end", "send-start", "send-end"), channel.trace)
    }

    /**
     * Unbekannte Kennung und falsches Passwort ergeben dieselben Antwortbytes (mit der echten
     * Benutzerliste aus der Konfiguration, am Produkt mit Rohbytes gemessen, nicht am Referenzclient); die
     * Verbindung endet, die Grenze wechselt nicht, weder Kennung noch Passwort stehen in Antwort oder Log.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Unbekannte Kennung und falsches Passwort ergeben dieselben Antwortbytes`() = blocking {
        val adapter = userListAdapter()
        suspend fun attempt(user: String, password: String): FakeHandshakeChannel {
            val login = SessionVectors.login7 {
                userName = user
                this.password = password.toCharArray()
            }
            val channel = FakeHandshakeChannel(listOf(message(SessionVectors.prelogin()), message(login)))
            val outcome = HandshakeDriver(HandshakeConfig(), adapter, log, 10.seconds).run(channel)
            assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_REJECTED), outcome)
            return channel
        }

        val unknown = attempt("mallory", "wrongpw")
        val wrong = attempt("alice", "wrongpw")
        assertContentEquals(unknown.sent.last().payload, wrong.sent.last().payload)
        assertContentEquals(LoginResponses.rejection.payload, wrong.sent.last().payload)
        assertEquals(PacketType.TABULAR_RESULT, wrong.sent.last().type)
        assertTrue(wrong.closed && unknown.closed)
        assertEquals(0, wrong.limitSwitches)
        val expectedLog: List<LogEntry> = listOf(
            LogEntry.LoginRejected(LoginRejection.UNKNOWN_USER),
            LogEntry.LoginRejected(LoginRejection.WRONG_PASSWORD),
        )
        assertEquals(expectedLog, log.entries.toList())
        val payloads = (unknown.sent + wrong.sent).map { it.payload }
        val wire = payloads.joinToString { String(it, Charsets.UTF_16LE) + String(it) }
        listOf("mallory", "alice", "wrongpw", "secret").forEach { secret ->
            assertFalse(secret in wire, "$secret in der Antwort")
            assertFalse(log.entries.any { secret in it.toString() }, "$secret im Log")
        }
    }

    /**
     * Eine Ausnahme im Adapter wird wie eine Ablehnung beantwortet (dieselben Bytes); ins Log geht nur der
     * Typ der Ausnahme, nicht ihr Text.
     *
     * @spec SPEC-012
     * @spec SPEC-048
     */
    @Test
    fun `Eine Ausnahme im Adapter wird wie eine Ablehnung beantwortet`() = blocking {
        val adapter = RecordingAdapter { error("Text mit Geheimnis") }
        val channel = FakeHandshakeChannel(happyScript())
        val outcome = driver(adapter).run(channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_REJECTED), outcome)
        assertContentEquals(LoginResponses.rejection.payload, channel.sent.last().payload)
        val expectedLog: List<LogEntry> = listOf(
            LogEntry.LoginRejected(LoginRejection.ADAPTER_FAILURE),
            LogEntry.AdapterFailed("java.lang.IllegalStateException"),
        )
        assertEquals(expectedLog, log.entries.toList())
        assertFalse(log.entries.any { "Geheimnis" in it.toString() })
        assertTrue(checkNotNull(adapter.passwordChars).reveal().all { it == '\u0000' })
    }

    /**
     * Die drei nicht unterstützten Modi ergeben dasselbe Ablehnungsmuster, ohne dass der Adapter gerufen wird.
     *
     * @spec SPEC-012
     */
    @Test
    fun `Nicht unterstuetzte Modi werden ohne Adapteraufruf abgelehnt`() = blocking {
        val variants = mapOf<LoginRejection, Login7Builder.() -> Unit>(
            LoginRejection.UNSUPPORTED_INTEGRATED_SECURITY to {
                optionFlags2 = 0x83
                sspi = ByteArray(8)
            },
            LoginRejection.UNSUPPORTED_FEDERATED_AUTHENTICATION to {
                optionFlags3 = 0x10
                features = listOf(0x02 to byteArrayOf(1, 0, 0, 0, 0))
            },
            LoginRejection.UNSUPPORTED_PASSWORD_CHANGE to {
                optionFlags3 = 0x01
                changePassword = "neu".toCharArray()
            },
        )
        variants.forEach { (reason, configure) ->
            val adapter = RecordingAdapter.accepting()
            val channel = FakeHandshakeChannel(happyScript { SessionVectors.login7(configure) })
            val outcome = driver(adapter).run(channel)
            assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_REJECTED), outcome, "$reason")
            assertEquals(0, adapter.calls, "Adapteraufrufe bei $reason")
            assertContentEquals(LoginResponses.rejection.payload, channel.sent.last().payload)
        }
        val expectedLog: List<LogEntry> = variants.keys.map { LogEntry.LoginRejected(it) }
        assertEquals(expectedLog, log.entries.toList())
    }

    /**
     * Ein LOGIN7 mit ungültigen Offsets beendet die Verbindung ohne weitere Antwort und ohne Adapteraufruf.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Ungueltige Felder schliessen kontrolliert ohne Antwort`() = blocking {
        val adapter = RecordingAdapter.accepting()
        val broken = SessionVectors.login7Payload().also { it[44] = 0xF0.toByte() }
        val bad = TdsMessage(PacketType.LOGIN7, broken)
        val channel = FakeHandshakeChannel(listOf(message(SessionVectors.prelogin()), message(bad)))
        val outcome = driver(adapter).run(channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.PROTOCOL_VIOLATION), outcome)
        assertEquals(1, channel.sent.size, "nur die PRELOGIN-Antwort")
        assertEquals(0, adapter.calls)
        assertTrue(channel.closed)
    }

    /**
     * Eine unvereinbare Verschlüsselungs-Anforderung bricht vor LOGIN7 ab: keine Antwort, kein TLS,
     * kein Adapter (kein stilles Downgrade).
     *
     * @spec SPEC-010
     */
    @Test
    fun `Unvereinbare Verschluesselung bricht vor LOGIN7 ab`() = blocking {
        val adapter = RecordingAdapter.accepting()
        val channel = FakeHandshakeChannel(listOf(message(SessionVectors.prelogin(SessionVectors.ENCRYPT_NOT_SUP))))
        val outcome = driver(adapter).run(channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.ENCRYPTION_INCOMPATIBLE), outcome)
        assertTrue(channel.sent.isEmpty())
        assertEquals(0, channel.tlsCalls)
        assertEquals(0, adapter.calls)
    }

    /**
     * Ein unzulässiger Übergang beendet nur diese Verbindung: eine zweite, gleichzeitig laufende Verbindung
     * desselben Treibers meldet sich an (kein gemeinsamer Zustand).
     *
     * @spec SPEC-001
     */
    @Test
    fun `Ein Verstoss auf einer Verbindung beruehrt die andere nicht`() = blocking {
        val shared = driver(RecordingAdapter.accepting())
        val violating = FakeHandshakeChannel(listOf(message(SessionVectors.login7())))
        val honest = FakeHandshakeChannel(happyScript())
        val outcomes = listOf(async { shared.run(violating) }, async { shared.run(honest) }).awaitAll()
        assertEquals(HandshakeOutcome.Closed(CloseReason.PROTOCOL_VIOLATION), outcomes[0])
        assertIs<HandshakeOutcome.Ready>(outcomes[1])
        assertTrue(violating.sent.isEmpty())
        assertFalse(honest.closed)
    }

    /**
     * Ende der Gegenstelle, Lesefehler, gescheiterter TLS-Handschlag und gescheitertes Schreiben beenden die
     * Verbindung ohne weitere Antwort.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Ende, Lesefehler, TLS-Fehler und Schreibfehler beenden die Verbindung`() = blocking {
        assertEquals(
            HandshakeOutcome.Closed(CloseReason.PEER_CLOSED),
            runWith(FakeHandshakeChannel(listOf(Inbound.EndOfStream))),
        )
        assertEquals(
            HandshakeOutcome.Closed(CloseReason.READ_FAILED),
            runWith(FakeHandshakeChannel(listOf(Inbound.Failed))),
        )
        assertEquals(
            HandshakeOutcome.Closed(CloseReason.READ_FAILED),
            runWith(FakeHandshakeChannel(happyScript(), tlsResult = false)),
        )
        assertEquals(
            HandshakeOutcome.Closed(CloseReason.WRITE_FAILED),
            runWith(FakeHandshakeChannel(happyScript(), sendResult = false)),
        )
    }

    private suspend fun runWith(channel: FakeHandshakeChannel) = driver(RecordingAdapter.accepting()).run(channel)

    /**
     * `LOGIN_TIMEOUT` (Ereignis nach Ablauf der Frist) endet im Schließen ohne Antwort: der Client sendet
     * nach PRELOGIN nichts mehr.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Fristablauf schliesst ohne Antwort`() = blocking {
        val channel = FakeHandshakeChannel(listOf(message(SessionVectors.prelogin())))
        val outcome = driver(RecordingAdapter.accepting(), timeout = 200.milliseconds).run(channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_TIMEOUT), outcome)
        assertEquals(1, channel.sent.size, "nur die PRELOGIN-Antwort, keine Antwort auf den Fristablauf")
        assertTrue(channel.closed)
    }

    /**
     * Ein langsamer Adapter hält `LOGIN_TIMEOUT` nicht auf: die Frist endet das Warten, die Verbindung
     * schließt ohne Login-Antwort, lange bevor der Adapter zurückkehrt. Keine Last-Aussage.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Ein langsamer Adapter haelt LOGIN_TIMEOUT ein`() = blocking {
        val slow = RecordingAdapter.blocking(SLOW_ADAPTER_MILLIS)
        val channel = FakeHandshakeChannel(happyScript())
        val started = System.nanoTime()
        val outcome = driver(slow, timeout = 300.milliseconds).run(channel)
        val elapsedMillis = (System.nanoTime() - started) / NANOS_PER_MILLI
        assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_TIMEOUT), outcome)
        assertTrue(elapsedMillis < SLOW_ADAPTER_MILLIS / 2, "Warten dauerte $elapsedMillis ms")
        assertEquals(1, channel.sent.size, "keine Login-Antwort")
        assertTrue(channel.closed)
    }

    /**
     * Der Adapter läuft nicht auf dem Transport-Thread: während er 300 ms blockiert, laufen die anderen
     * Coroutinen des Aufrufer-Threads weiter, und der Adapter sieht einen anderen Thread.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Der Adapter blockiert den Transport-Thread nicht`() = blocking {
        val slow = RecordingAdapter.blocking(BLOCKING_MILLIS)
        var ticks = 0
        val ticker = launch {
            while (isActive) {
                delay(TICK_MILLIS)
                ticks++
            }
        }
        val outcome = driver(slow).run(FakeHandshakeChannel(happyScript()))
        ticker.cancel()
        assertIs<HandshakeOutcome.Ready>(outcome)
        assertTrue(ticks >= MIN_TICKS, "nur $ticks Ticks waehrend des blockierenden Aufrufs")
        assertNotSame(Thread.currentThread(), slow.callingThread)
    }

    /**
     * Übergang und Schreiben laufen unter einem `Mutex` je Sitzung: ein gleichzeitiges Ereignis schließt den
     * Kanal erst, wenn das laufende Schreiben beendet ist.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Das Schliessen wartet auf das laufende Schreiben`() = blocking {
        val channel = FakeHandshakeChannel(emptyList(), sendDelayMillis = TICK_MILLIS * 5)
        val session = HandshakeSession(HandshakeAutomaton(), channel, log)
        session.dispatch(HandshakeEvent.Start)
        session.dispatch(HandshakeEvent.Message(SessionVectors.prelogin()))
        session.dispatch(HandshakeEvent.TlsEstablished)
        session.dispatch(HandshakeEvent.Message(SessionVectors.login7()))
        channel.trace.clear()
        val accepted = async { session.dispatch(HandshakeEvent.AuthAccepted(Principal("sa"), "s1")) }
        val closing = async { session.dispatch(HandshakeEvent.PeerClosed) }
        awaitAll(accepted, closing)
        assertEquals(listOf("send-start", "send-end", "close"), channel.trace)
        assertEquals(CloseReason.PEER_CLOSED, session.closeReason)
    }

    /**
     * Wirft der Adapter eine `CancellationException`, ohne dass der Aufruf abgebrochen ist, ist das ein
     * Adapterfehler und wird wie eine Ablehnung beantwortet (dieselben Bytes, `ADAPTER_FAILURE`, Typ der
     * Ausnahme im Log). Festlegung, nicht gemessen.
     *
     * @spec SPEC-008
     * @spec SPEC-012
     * @spec SPEC-048
     */
    @Test
    fun `Eine Abbruchausnahme des Adapters ohne Abbruch des Aufrufs ist ein Adapterfehler`() = blocking {
        val adapter = RecordingAdapter { throw CancellationException("Abbruch im Adapter") }
        val channel = FakeHandshakeChannel(happyScript())
        val outcome = driver(adapter).run(channel)
        assertEquals(HandshakeOutcome.Closed(CloseReason.LOGIN_REJECTED), outcome)
        assertContentEquals(LoginResponses.rejection.payload, channel.sent.last().payload)
        val expectedLog: List<LogEntry> = listOf(
            LogEntry.LoginRejected(LoginRejection.ADAPTER_FAILURE),
            LogEntry.AdapterFailed(CancellationException::class.java.name),
        )
        assertEquals(expectedLog, log.entries.toList())
        assertTrue(channel.closed)
        assertTrue(checkNotNull(adapter.passwordChars).reveal().all { it == '\u0000' })
    }

    /**
     * Stammt die `CancellationException` vom Abbruch des Aufrufers (Zustand des eigenen Jobs), wird sie
     * weitergereicht: keine Login-Antwort, keine Ablehnung im Log, der Kanal ist geschlossen. Der Adapter
     * wirft erst, nachdem der Aufrufer abgebrochen hat.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Eine Abbruchausnahme nach dem Abbruch des Aufrufers ist keine Ablehnung`() = blocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter = RecordingAdapter {
            started.countDown()
            release.await()
            throw CancellationException("Abbruch")
        }
        val channel = FakeHandshakeChannel(happyScript())
        val running = launch { driver(adapter).run(channel) }
        while (started.count > 0) delay(TICK_MILLIS)
        running.cancelAndJoin()
        release.countDown()
        assertTrue(channel.closed, "Kanal nach dem Abbruch des Aufrufers offen")
        assertEquals(1, channel.sent.size, "keine Login-Antwort, nur die PRELOGIN-Antwort")
        assertTrue(log.entries.isEmpty(), "keine Ablehnung im Log")
    }

    /**
     * Bricht der Aufrufer `run` ab, während der Treiber auf den Client wartet, ist der Kanal danach
     * geschlossen; nur `Ready` lässt ihn offen.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Bricht der Aufrufer ab, ist der Kanal geschlossen`() = blocking {
        val channel = FakeHandshakeChannel(listOf(message(SessionVectors.prelogin())))
        val running = launch { driver(RecordingAdapter.accepting()).run(channel) }
        while (channel.sent.isEmpty()) yield()
        running.cancelAndJoin()
        assertTrue(channel.closed, "Kanal nach dem Abbruch des Aufrufers offen")
    }

    /**
     * Läuft die Frist ab, während die Annahme gesendet wird (Zustand `READY`, Schreiben unterbrochen),
     * schließt `expire()` den Kanal trotzdem: `LOGIN_TIMEOUT` ist in `READY` ohne Wirkung, der Zweig
     * danach setzt den Zustand auf `CLOSED` und räumt die Sitzung ab. Festlegung ohne Messung: das
     * unterbrochene Schreiben kann ein Paket halb schreiben.
     *
     * @spec SPEC-008
     * @spec SPEC-001
     */
    @Test
    fun `Fristablauf waehrend des Schreibens der Annahme schliesst den Kanal`() = blocking {
        val channel = FakeHandshakeChannel(emptyList())
        val session = HandshakeSession(HandshakeAutomaton(), channel, log)
        session.dispatch(HandshakeEvent.Start)
        session.dispatch(HandshakeEvent.Message(SessionVectors.prelogin()))
        session.dispatch(HandshakeEvent.TlsEstablished)
        session.dispatch(HandshakeEvent.Message(SessionVectors.login7()))
        channel.trace.clear()
        channel.sendDelayMillis = SLOW_ADAPTER_MILLIS
        val accepting = launch { session.dispatch(HandshakeEvent.AuthAccepted(Principal("sa"), "s1")) }
        while ("send-start" !in channel.trace) yield()
        accepting.cancelAndJoin()
        assertEquals(ConnectionState.READY, session.context.state)
        assertFalse(channel.closed)
        session.expire()
        assertTrue(channel.closed, "Kanal nach Fristablauf offen")
        assertEquals(ConnectionState.CLOSED, session.context.state)
        assertEquals(null, session.context.session)
        assertEquals(CloseReason.LOGIN_TIMEOUT, session.closeReason)
        assertFalse("send-end" in channel.trace, "das Schreiben endete nicht")
    }

    /**
     * Verwaiste Adapteraufrufe sind begrenzt: bei ein Platz im `AuthPool` laufen nie zwei Aufrufe zugleich,
     * und ein Aufruf, der beim Abbruch noch auf seinen Platz wartet, beginnt nie. Der Test wartet darauf,
     * dass der erste Aufruf begonnen hat und alle drei Anmeldungen eingereiht sind, bevor er abbricht;
     * der laufende Aufruf endet folgenlos (keine Antwort, Passwort überschrieben).
     *
     * @spec SPEC-008
     */
    @Test
    fun `Verwaiste Adapteraufrufe sind begrenzt und beginnen nach dem Abbruch nicht mehr`() = blocking {
        val calls = AtomicInteger()
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        val release = CountDownLatch(1)
        val requests = ConcurrentLinkedQueue<AuthRequest>()
        val adapter = object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult {
                calls.incrementAndGet()
                requests += request
                maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                release.await()
                running.decrementAndGet()
                return AuthResult.Accepted(Principal("sa"))
            }
        }
        val pool = AuthPool(parallelism = 1)
        val limited = HandshakeDriver(HandshakeConfig(), adapter, log, authPool = pool)
        val channels = List(3) { FakeHandshakeChannel(happyScript()) }
        val runs = channels.map { channel ->
            launch { limited.run(channel) }.also {
                while (!channel.exhausted) yield()
                repeat(SETTLE_YIELDS) { yield() }
            }
        }
        while (calls.get() == 0) delay(TICK_MILLIS)
        runs.forEach { it.cancelAndJoin() }
        release.countDown()
        awaitPassword(requests.first())
        assertEquals(1, calls.get(), "Aufrufe, die beim Abbruch noch warteten, begannen")
        assertEquals(1, maxRunning.get(), "gleichzeitige Adapteraufrufe")
        channels.forEach { assertEquals(1, it.sent.size, "Antwort nach dem Abbruch") }
        assertTrue(requests.all { it.password.reveal().all { c -> c == '\u0000' } }, "Passwort nicht ueberschrieben")
    }

    /**
     * Die Obergrenze gilt je `AuthPool`: zwei Treiber auf einem Pool mit einem Platz lassen nie zwei
     * Adapteraufrufe zugleich laufen.
     *
     * @spec SPEC-008
     */
    @Test
    fun `Die Obergrenze der Adapteraufrufe gilt je Pool ueber Treiber hinweg`() = blocking {
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        val adapter = object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult {
                maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                Thread.sleep(TICK_MILLIS * 3)
                running.decrementAndGet()
                return AuthResult.Accepted(Principal("sa"))
            }
        }
        val pool = AuthPool(parallelism = 1)
        val first = HandshakeDriver(HandshakeConfig(), adapter, log, authPool = pool)
        val second = HandshakeDriver(HandshakeConfig(), adapter, log, authPool = pool)
        val outcomes = listOf(first, second).map { driver ->
            async { driver.run(FakeHandshakeChannel(happyScript())) }
        }.awaitAll()
        outcomes.forEach { assertIs<HandshakeOutcome.Ready>(it) }
        assertEquals(1, maxRunning.get(), "gleichzeitige Adapteraufrufe ueber zwei Treiber")
    }

    private suspend fun awaitPassword(request: AuthRequest) {
        while (request.password.reveal().any { it != '\u0000' }) delay(TICK_MILLIS)
    }

    private companion object {
        const val SLOW_ADAPTER_MILLIS = 3_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val BLOCKING_MILLIS = 400L
        const val TICK_MILLIS = 10L
        const val MIN_TICKS = 10
        const val SETTLE_YIELDS = 5
    }
}
