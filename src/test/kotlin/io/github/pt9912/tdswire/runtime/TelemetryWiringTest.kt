package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.telemetry.ClientProfile
import io.github.pt9912.tdswire.domain.telemetry.ConnectionFailed
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.EventLevel
import io.github.pt9912.tdswire.domain.telemetry.LoginAccepted
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TelemetrySink
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.eventually
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4

/**
 * Gibt jedes Ereignis an [inner] und wirft danach [error], sobald die Anmeldung angenommen ist. Der Aufruf der Senke
 * steht im Handler der Verbindung; ein `Error` verlässt die Meldestelle (sie fängt nur `Exception`) und beendet den
 * Handler. Das ist der Auslöser, kein Gegenstand der Zusage: was die Bibliothek mit einem `Error` aus der Senke tut,
 * ist weder gemessen noch zugesagt (`TelemetrySink`).
 */
internal class ThrowsAfterLogin(private val inner: CapturedEvents, private val error: Error) : TelemetrySink {
    override fun accept(event: TelemetryEvent) {
        inner.accept(event)
        if (event is LoginAccepted) throw error
    }
}

/**
 * Die Verdrahtung der Ereignisse des Listeners am Server (`TDS-FA-09`, `TDS-FA-09.a`): `CONNECTION_FAILED` entsteht,
 * wenn der Handler einer Verbindung mit einer Ausnahme endet, und nur dann. Der Test fährt den Server im Prozess und
 * den Rohbytes-Client; eine Senke, die nach der Anmeldung einen `Error` wirft, ist der Auslöser
 * (siehe [ThrowsAfterLogin]).
 */
internal class TelemetryWiringTest : RuntimeTestBase() {
    private fun environment() = loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"))

    private fun login(client: RawClient, readReply: Boolean = true) {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        if (readReply) assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    /**
     * Ein Handler, der nach der Anmeldung mit einer Ausnahme endet, ergibt genau ein `CONNECTION_FAILED` mit dem
     * Typnamen der Ausnahme, der Ebene `ERROR` und der Nummer der Verbindung, ohne `session_id` und mit dem Profil
     * `unknown`, obwohl `LOGIN_ACCEPTED` derselben Verbindung beides trägt (Grenze aus Abschnitt 4 der Spezifikation).
     * Der Typname folgt der Ausnahme, nicht einer festen Marke: zwei verschiedene Ausnahmen, zwei verschiedene Namen.
     *
     * @spec SPEC-048
     * @spec TDS-FA-09
     */
    @Test
    fun `Ein Handler mit Ausnahme ergibt CONNECTION_FAILED ohne Sitzung und mit unbekanntem Profil`() = blocking {
        val errors = listOf<Error>(LinkageError("wertprobe-aq7"), AssertionError("wertprobe-zk2"))
        errors.forEach { error ->
            sink.events.clear()
            val server = running(environment(), Wiring(telemetry = ThrowsAfterLogin(sink, error)))
            RawClient(server.port).use { client ->
                login(client, readReply = false)
                eventually("CONNECTION_FAILED") { sink.of<ConnectionFailed>().isNotEmpty() }
            }
            server.stop()
            val failed = sink.of<ConnectionFailed>().single()
            assertEquals(EventKind.CONNECTION_FAILED, failed.kind)
            assertEquals(EventLevel.ERROR, failed.level)
            assertEquals(error.javaClass.name, failed.exceptionType)
            assertEquals(1L, failed.connectionId)
            assertNull(failed.sessionId)
            assertEquals(ClientProfile.UNKNOWN, failed.clientProfile)
            val accepted = sink.of<LoginAccepted>().single()
            assertEquals(failed.connectionId, accepted.connectionId)
            assertNotNull(accepted.sessionId)
            assertTrue(accepted.clientProfile != ClientProfile.UNKNOWN, accepted.clientProfile)
        }
    }

    /**
     * Endet die Verbindung ohne Ausnahme des Handlers (der Client schließt oder bricht nach der Anmeldung ab, der
     * Server wird beendet), entsteht kein `CONNECTION_FAILED`.
     *
     * @spec TDS-FA-09
     */
    @Test
    fun `Eine Verbindung ohne Ausnahme des Handlers ergibt kein CONNECTION_FAILED`() = blocking {
        val server = running(environment(), Wiring(telemetry = sink))
        RawClient(server.port).use { login(it) }
        RawClient(server.port).use { client ->
            login(client)
            client.reset()
        }
        eventually("beide Verbindungen angemeldet") { sink.of<LoginAccepted>().size == 2 }
        server.stop()
        assertEquals(2, sink.of<LoginAccepted>().size, sink.events.toString())
        assertTrue(sink.of<ConnectionFailed>().isEmpty(), sink.events.toString())
    }

    /**
     * Scheitert das Annehmen am Betriebssystem (die Kind-JVM hat 128 offene Dateien und wird mit gehaltenen
     * Verbindungen erschöpft), entsteht `ACCEPT_FAILED` mit dem Typnamen der Ausnahme, der Ebene `ERROR`, ohne
     * Verbindung und Sitzung und mit dem Profil `unknown`. Der Server nimmt nach dem Schließen der Verbindungen wieder
     * an und beantwortet PRELOGIN. Beides gilt unter der Bedingung, dass [AcceptChild] die Klassen der Kind-JVM
     * vorlädt: ohne Vorladen kann die JVM unter erschöpftem Limit die Klasse des Fehlerpfads nicht laden, die
     * Meldestelle des Listeners endet mit `NoClassDefFoundError`, und weder ein Ereignis noch ein Wiederannehmen ist
     * belegt (gemessen, siehe `preloadClasses` in [AcceptChild]). Die Grenze von 128 Dateien ist eine Setzung des
     * Tests.
     *
     * @spec SPEC-048
     * @spec TDS-FA-09
     */
    @Test
    fun `Ein gescheitertes Annehmen ergibt ACCEPT_FAILED und der Server nimmt danach wieder an`() {
        val environment = loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"))
        AcceptChild(environment, DESCRIPTORS).use { child ->
            val port = child.port()
            val lines = child.provokeAcceptFailure(port)
            val failed = lines.filter { "Kind=ACCEPT_FAILED" in it }
            assertTrue(failed.isNotEmpty(), "kein ACCEPT_FAILED: ${child.output.map { it.take(DIAGNOSTIC_CHARS) }}")
            failed.forEach { line ->
                val fields = line.removePrefix(EVENT_PREFIX).split(" ")
                assertEquals("AcceptFailed", fields.first(), line)
                assertTrue("Level=ERROR" in fields, line)
                assertTrue("ConnectionId=null" in fields && "SessionId=null" in fields, line)
                assertTrue("ClientProfile=${ClientProfile.UNKNOWN}" in fields, line)
                assertTrue("ExceptionType=java.io.IOException" in fields, line)
            }
            assertTrue(lines.none { "Kind=CONNECTION_FAILED" in it }, "gehaltene Verbindungen sind kein Fehler: $lines")
            assertTrue(serves(port), "der Server nimmt nach dem Schließen nicht mehr an: ${child.output}")
        }
    }

    /** `true`, sobald der Server auf ein PRELOGIN antwortet; wartet bis zu zehn Sekunden auf freigegebene Dateien. */
    private fun serves(port: Int): Boolean {
        repeat(RETRIES) {
            val answered = runCatching {
                RawClient(port).use { client ->
                    client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
                    client.readMessage() != null
                }
            }.getOrDefault(false)
            if (answered) return true
            Thread.sleep(RETRY_MILLIS)
        }
        return false
    }

    private companion object {
        const val DESCRIPTORS = 128
        const val DIAGNOSTIC_CHARS = 90
        const val RETRIES = 20
        const val RETRY_MILLIS = 500L
    }
}
