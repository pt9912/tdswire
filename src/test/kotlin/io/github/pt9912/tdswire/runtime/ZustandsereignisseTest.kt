package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.AuthSettings
import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.ListenSettings
import io.github.pt9912.tdswire.config.ServerConfig
import io.github.pt9912.tdswire.config.TlsKeyMaterial
import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.telemetry.ClientProfile
import io.github.pt9912.tdswire.domain.telemetry.ConfigEffective
import io.github.pt9912.tdswire.domain.telemetry.ConnectionClosed
import io.github.pt9912.tdswire.domain.telemetry.ConnectionOpened
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.EventLevel
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.transport.listener.ConnectionEnd
import io.github.pt9912.tdswire.transport.listener.ListenerEvent
import io.github.pt9912.tdswire.transport.listener.eventually
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4
private const val GATE_SECONDS = 20L
private const val SETTLE_MILLIS = 500L
private const val PARTIAL_BYTES = 100
private val STOP_KINDS =
    setOf(EventKind.SERVER_STOPPING, EventKind.CONNECTION_CLOSED, EventKind.SERVER_STOPPED)
private const val UNKNOWN_HANDLE_CODE = "TDS_SIM_UNKNOWN_PREPARED_HANDLE"

/**
 * Die Zustandsereignisse des Servers (`TDS-FA-09`, `TDS-FA-09.a`, `ADR-0020`): Start mit wirksamer Konfiguration und
 * Adresse, Verbindung geöffnet und geschlossen mit Grund, Anmeldung, Anfragefehler, Beginn und Ende des Beendens, in
 * der Reihenfolge, in der der Server sie an die Senke gibt. Der Test fährt den Server im Prozess und den
 * Rohbytes-Client (kein Referenzclient); die Zeilen des Testserver-Prozesses hält `ZustandsereignisseProzessTest`.
 */
internal class ZustandsereignisseTest : RuntimeTestBase() {
    private val usersFile get() = file("users.txt", "alice\t{plain}secret\n")

    private fun environment(vararg more: Pair<String, String>) = loopbackEnvironment(usersFile, *more)

    private fun login(client: RawClient) {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    private fun unknownHandle(client: RawClient) {
        client.send(handMessage(BATCH_TYPE, BatchVectors.payload("EXEC sp_unprepare 99"), 4000))
        assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    private fun kinds(): List<EventKind> = sink.events.map { it.kind }

    private fun withBackend(): Wiring {
        val backend = ScriptedBackend.ofList(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
        return Wiring(telemetry = sink, requests = RequestSource(backend))
    }

    /**
     * Ein Lauf mit Anmeldung, einem Anfragefehler, dem Ende der Verbindung durch den Client und dem Beenden ergibt die
     * Folge: wirksame Konfiguration, Start, die zwei Abschwächungen, Verbindung geöffnet, Anmeldung, Anfragefehler
     * mit `error_code`, Verbindung geschlossen, Beginn und Ende des Beendens. Jedes Ereignis trägt die Ebene seiner
     * Art; die Ereignisse der Verbindung tragen dieselbe `connection_id`, die der Sitzung dieselbe `session_id`.
     *
     * @spec SPEC-005
     * @spec SPEC-010
     * @spec SPEC-044
     * @spec SPEC-059
     */
    @Test
    fun `Ein Lauf meldet Start, Verbindung, Anmeldung, Anfragefehler und Beenden der Reihe nach`() = blocking {
        val server = running(environment(), withBackend())
        RawClient(server.port).use { client ->
            login(client)
            unknownHandle(client)
        }
        eventually("Verbindung geschlossen") { sink.of<ConnectionClosed>().isNotEmpty() }
        server.stop()

        assertEquals(
            listOf(
                EventKind.CONFIG_EFFECTIVE,
                EventKind.SERVER_STARTED,
                EventKind.SECURITY_WEAKENED,
                EventKind.SECURITY_WEAKENED,
                EventKind.CONNECTION_OPENED,
                EventKind.ENCRYPTION_NOT_OFFERED,
                EventKind.LOGIN_ACCEPTED,
                EventKind.REQUEST_FAILED,
                EventKind.CONNECTION_CLOSED,
                EventKind.SERVER_STOPPING,
                EventKind.SERVER_STOPPED,
            ),
            kinds(),
        )
        val levels = sink.events.associate { it.kind to it.level }
        assertEquals(EventLevel.INFO, levels[EventKind.CONFIG_EFFECTIVE])
        assertEquals(EventLevel.INFO, levels[EventKind.CONNECTION_OPENED])
        assertEquals(EventLevel.INFO, levels[EventKind.CONNECTION_CLOSED])
        assertEquals(EventLevel.INFO, levels[EventKind.SERVER_STOPPING])

        val started = sink.events.single { it.kind == EventKind.SERVER_STARTED }
        assertEquals("127.0.0.1", started.address)
        assertEquals(server.port, started.port)

        val opened = sink.of<ConnectionOpened>().single()
        val accepted = sink.events.single { it.kind == EventKind.LOGIN_ACCEPTED }
        val failed = sink.events.single { it.kind == EventKind.REQUEST_FAILED }
        val closed = sink.of<ConnectionClosed>().single()
        assertEquals(listOf(1L), listOf(opened, accepted, failed, closed).map { it.connectionId }.distinct())
        assertEquals(accepted.sessionId, failed.sessionId)
        assertNotNull(failed.sessionId)
        assertEquals(UNKNOWN_HANDLE_CODE, failed.errorCode)
        assertEquals("alice", accepted.principal)
        assertNull(opened.sessionId)
        assertEquals(ClientProfile.UNKNOWN, opened.clientProfile)
        assertEquals("HANDLER_RETURNED", closed.reason)
        assertNull(closed.sessionId)
    }

    /**
     * Die wirksame Konfiguration nennt jede Einstellung mit ihrem Wert, der Schlüsselspeicher steht als `UNSET`
     * (ohne Material) bzw. `SET` (mit Material), ein Pfad und das Passwort stehen nirgends. Mit der Voreinstellung und
     * dem Testmodus sind die Werte die der Spezifikation.
     *
     * @spec SPEC-005
     * @spec SPEC-007
     * @spec SPEC-008
     * @spec SPEC-009
     * @spec SPEC-010
     * @spec SPEC-033
     * @spec SPEC-034
     * @spec SPEC-046
     * @spec SPEC-047
     * @spec SPEC-061
     */
    @Test
    fun `Die wirksame Konfiguration nennt jede Einstellung und keinen Pfad`() = blocking {
        running(environment())
        val settings = assertNotNull(sink.of<ConfigEffective>().single().settings)
        assertEquals(
            mapOf(
                ConfigKeys.LOGIN_TIMEOUT to "15",
                ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES to "65536",
                ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
                ConfigKeys.TLS_KEYSTORE_FILE to "UNSET",
                ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
                ConfigKeys.QUERY_TIMEOUT to "30",
                ConfigKeys.CANCEL_TIMEOUT to "5",
                ConfigKeys.SHUTDOWN_GRACE to "10",
                ConfigKeys.MAX_CONNECTIONS to "1000",
                ConfigKeys.MAX_PREPARED_HANDLES to "1000",
                ConfigKeys.MAX_MESSAGE_BYTES to "16777216",
            ),
            settings,
        )
        val shown = sink.events.joinToString("\n") { it.visibleText() }
        assertFalse(shown.contains("users.txt"), shown)
        assertFalse(shown.contains("secret"), shown)
    }

    /**
     * Die Einstellungen aus einer Konfiguration mit Schlüsselmaterial, Passwort und Benutzerliste voller Prüfwörter:
     * der Schlüsselspeicher steht als `SET`, kein Wert enthält ein Prüfwort, und die Schlüssel sind genau die der
     * Einstellungen (kein Pfad, kein Passwort, keine Benutzerliste).
     *
     * @spec SPEC-010
     * @spec SPEC-012
     */
    @Test
    fun `Die Einstellungen enthalten weder Schluesselinhalt noch Passwort noch Benutzerliste`() {
        val users = assertIs<UserListResult.Valid>(UserList.parse("kennung-$WERTPROBE\t{plain}pw-$WERTPROBE\n", true))
        val tls = TlsKeyMaterial("schluessel-$WERTPROBE".toByteArray(), PasswordChars("kw-$WERTPROBE".toCharArray()))
        val listen = ListenSettings("host-$WERTPROBE", 0)
        val config = ServerConfig(listen, tls = tls, auth = AuthSettings(users.list, true))
        val settings = config.effectiveSettings().asMap()
        assertEquals("SET", settings[ConfigKeys.TLS_KEYSTORE_FILE])
        assertEquals("true", settings[ConfigKeys.AUTH_PLAIN_TESTMODE])
        assertEquals(11, settings.size, settings.toString())
        assertTrue(settings.none { (key, value) -> key.contains(WERTPROBE) || value.contains(WERTPROBE) }, "$settings")
    }

    /**
     * Die Schlüssel der Abbildung, die `EffectiveSettings` ableitet, sind die Namen der Konfiguration (`ConfigKeys`):
     * der Typ der Bibliothek im Package `domain` kennt `ConfigKeys` nicht und führt die Namen selbst.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Die Schluessel der wirksamen Konfiguration sind die Namen der Konfiguration`() {
        val users = assertIs<UserListResult.Valid>(UserList.parse("", true)).list
        val config = ServerConfig(auth = AuthSettings(users, true))
        assertEquals(
            listOf(
                ConfigKeys.LOGIN_TIMEOUT,
                ConfigKeys.MAX_PRELOGIN_MESSAGE_BYTES,
                ConfigKeys.ENCRYPTION_POLICY,
                ConfigKeys.TLS_KEYSTORE_FILE,
                ConfigKeys.AUTH_PLAIN_TESTMODE,
                ConfigKeys.QUERY_TIMEOUT,
                ConfigKeys.CANCEL_TIMEOUT,
                ConfigKeys.SHUTDOWN_GRACE,
                ConfigKeys.MAX_CONNECTIONS,
                ConfigKeys.MAX_PREPARED_HANDLES,
                ConfigKeys.MAX_MESSAGE_BYTES,
            ),
            config.effectiveSettings().asMap().keys.toList(),
        )
    }

    /**
     * Beim Beenden mit einer angemeldeten, ruhenden Verbindung folgt auf den Beginn des Beendens das Ende der
     * Verbindung mit dem Grund `SHUTDOWN`, danach das Ende des Servers. `stop` kehrt erst zurück, nachdem das Ereignis
     * zum Ende an die Senke gegeben wurde (`ADR-0020`, Frage 7): die Senke hat es gesehen, wenn der Aufruf zurückkehrt,
     * und ein zweiter Aufruf fügt nichts hinzu.
     *
     * @spec SPEC-044
     * @spec SPEC-059
     */
    @Test
    fun `Beenden meldet Beginn, Ende der Verbindung und Ende, und stop kehrt nach dem Ereignis zurueck`() = blocking {
        val seen = CopyOnWriteArrayList<TelemetryEvent>()
        val recording = TelemetrySink { event ->
            seen += event
            sink.accept(event)
        }
        val server = running(environment(), Wiring(telemetry = recording))
        RawClient(server.port).use { client ->
            login(client)
            eventually("Anmeldung gemeldet") { sink.events.any { it.kind == EventKind.LOGIN_ACCEPTED } }
            val before = seen.size
            server.stop()
            val atReturn = seen.drop(before).map { it.kind }
            assertEquals(
                listOf(EventKind.SERVER_STOPPING, EventKind.CONNECTION_CLOSED, EventKind.SERVER_STOPPED),
                atReturn,
            )
            assertEquals("SHUTDOWN", seen.filterIsInstance<ConnectionClosed>().single().reason)
            server.stop()
            assertEquals(atReturn.size, seen.drop(before).size)
        }
    }

    /**
     * Die Verbindung über `MAX_CONNECTIONS` hinaus wird als geöffnet und sofort als geschlossen mit dem Grund
     * `REFUSED_AT_LIMIT` gemeldet, mit eigener `connection_id`; die erste Verbindung bleibt davon unberührt.
     *
     * @spec SPEC-034
     */
    @Test
    fun `Eine Verbindung ueber dem Limit wird geoeffnet und mit Grund abgewiesen gemeldet`() = blocking {
        val server = running(environment(ConfigKeys.MAX_CONNECTIONS to "1"))
        RawClient(server.port).use { first ->
            login(first)
            RawClient(server.port).use { second ->
                assertTrue(second.closedByServerWithoutBytes())
            }
            eventually("Abweisung gemeldet") { sink.of<ConnectionClosed>().isNotEmpty() }
            val closed = sink.of<ConnectionClosed>().single()
            assertEquals("REFUSED_AT_LIMIT", closed.reason)
            assertEquals(2L, closed.connectionId)
            assertEquals(listOf(1L, 2L), sink.of<ConnectionOpened>().map { it.connectionId })
        }
    }

    /**
     * Jede Art des Endes der Verbindung hat einen Grund gleichen Namens, und jedes Ereignis des Listeners ergibt das
     * Ereignis seiner Art: die Annahme „geöffnet“, das Ende „geschlossen“ mit dem Grund, der Fehler des Handlers
     * zusätzlich `CONNECTION_FAILED`, der Annahmefehler nur seinen Fehler.
     *
     * @spec SPEC-044
     * @spec SPEC-059
     */
    @Test
    fun `Jedes Ende einer Verbindung hat einen Grund gleichen Namens`() {
        val at = Instant.parse("2026-11-07T10:00:00Z")
        ConnectionEnd.entries.forEach { end ->
            val closed = ListenerEvent.Closed(3L, end, null)
            assertEquals(end.name, closed.toStateEvent(at)?.reason, "$end")
            assertEquals(EventKind.CONNECTION_CLOSED, closed.toStateEvent(at)?.kind)
            assertNull(closed.toEvent(at), "$end ohne Ausnahme ergibt kein Fehlerereignis")
        }
        val opened = ListenerEvent.Accepted(5L).toStateEvent(at)
        assertEquals(EventKind.CONNECTION_OPENED, opened?.kind)
        assertEquals(5L, opened?.connectionId)
        val crashed = ListenerEvent.Closed(3L, ConnectionEnd.HANDLER_FAILED, "java.lang.Error")
        assertEquals(EventKind.CONNECTION_FAILED, crashed.toEvent(at)?.kind)
        assertEquals("HANDLER_FAILED", crashed.toStateEvent(at)?.reason)
        val accept = ListenerEvent.AcceptFailed("java.io.IOException")
        assertNull(accept.toStateEvent(at))
        assertEquals(EventKind.ACCEPT_FAILED, accept.toEvent(at)?.kind)
    }

    /**
     * Zwei nebenläufige `stop` bei angehaltener Senke (sie hält den ersten Aufruf im Ereignis zum Beginn an, eine
     * Verbindung ist offen): der zweite Aufruf kehrt nicht zurück, solange der erste nicht fertig ist, und beide kehren
     * erst zurück, nachdem das Ereignis zum Ende abgegeben ist; die Folge ist Beginn, Ende der Verbindung, Ende, je
     * einmal. Die Wartezeit von 500 ms gilt nur für den Fehlerfall: ein zweiter Aufruf, der nicht wartet, kehrt in
     * ihr zurück.
     *
     * @spec SPEC-044
     * @spec SPEC-059
     */
    @Test
    fun `Zwei nebenlaeufige stop warten aufeinander und kehren nach dem Ende zurueck`() = blocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gate = TelemetrySink { event ->
            sink.accept(event)
            if (event.kind == EventKind.SERVER_STOPPING) {
                entered.countDown()
                release.await(GATE_SECONDS, TimeUnit.SECONDS)
            }
        }
        val server = running(environment(), Wiring(telemetry = gate))
        RawClient(server.port).use { client ->
            login(client)
            eventually("Anmeldung gemeldet") { sink.events.any { it.kind == EventKind.LOGIN_ACCEPTED } }
            val first = launch(Dispatchers.Default) { server.stop() }
            assertTrue(entered.await(GATE_SECONDS, TimeUnit.SECONDS), "Beginn nicht gemeldet")
            val second = launch(Dispatchers.Default) { server.stop() }
            delay(SETTLE_MILLIS)
            assertTrue(second.isActive, "der zweite stop ist zurückgekehrt, bevor das Beenden fertig ist")
            assertFalse(sink.events.any { it.kind == EventKind.SERVER_STOPPED })
            release.countDown()
            first.join()
            second.join()
        }
        val order = kinds().filter { it in STOP_KINDS }
        assertEquals(listOf(EventKind.SERVER_STOPPING, EventKind.CONNECTION_CLOSED, EventKind.SERVER_STOPPED), order)
    }

    /**
     * Ein abgebrochener `stop` (eine Verbindung mit halber Nachricht hält das Beenden bis zum Ablauf von
     * `SHUTDOWN_GRACE` an) gibt das Ereignis zum Ende nicht ab; der nächste `stop` gibt es ab, ohne ein zweites zum
     * Beginn. Setzung des Verhaltens, beschrieben im KDoc von `stop`.
     *
     * @spec SPEC-044
     * @spec SPEC-059
     */
    @Test
    fun `Ein abgebrochener stop meldet das Ende erst mit dem naechsten Aufruf`() = blocking {
        val server = running(environment())
        RawClient(server.port).use { client ->
            login(client)
            client.send(handPacket(BATCH_TYPE, 0, ByteArray(PARTIAL_BYTES)))
            delay(SETTLE_MILLIS)
            val first = launch(Dispatchers.Default) { server.stop() }
            eventually("Beginn gemeldet") { sink.events.any { it.kind == EventKind.SERVER_STOPPING } }
            first.cancelAndJoin()
            assertTrue(first.isCancelled, "der Aufruf war schon fertig, der Test belegt nichts")
            assertFalse(sink.events.any { it.kind == EventKind.SERVER_STOPPED })
            server.stop()
            assertEquals(1, kinds().count { it == EventKind.SERVER_STOPPING })
            assertEquals(1, kinds().count { it == EventKind.SERVER_STOPPED })
        }
    }
}
