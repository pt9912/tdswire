package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.auth.AuthAdapter
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.AuthResult
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.telemetry.BindFailed
import io.github.pt9912.tdswire.domain.telemetry.EventLevel
import io.github.pt9912.tdswire.domain.telemetry.LoginAccepted
import io.github.pt9912.tdswire.domain.telemetry.RequestFailed
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import io.github.pt9912.tdswire.domain.telemetry.TlsStartRejected
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val RPC_TYPE = 0x03
private const val LIMIT = 256
private const val LONG = 300
private val BASE_STRINGS = setOf("sessionId", "clientProfile")
private val STRINGS_BY_KIND = mapOf(
    "ConfigInvalid" to setOf("key", "kind"),
    "TlsStartRejected" to setOf("part", "form"),
    "BindFailed" to setOf("address", "exceptionType"),
    "AcceptFailed" to setOf("exceptionType"),
    "ConnectionFailed" to setOf("exceptionType"),
    "LoginAccepted" to setOf("principal"),
    "AdapterFailed" to setOf("exceptionType"),
    "TlsHandshakeFailed" to setOf("failure"),
    "RequestFailed" to setOf("errorCode", "cause"),
)
private val ALLOWED_TYPES = setOf(
    Instant::class.java,
    EventLevel::class.java,
    Long::class.javaObjectType,
    Long::class.javaPrimitiveType,
    Int::class.javaPrimitiveType,
    String::class.java,
)

/**
 * Redaktion durch Bauart (`TDS-QA-03`, `SPEC-043`, `SPEC-048`): Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad,
 * Inhalt von Zertifikat und Schlüssel sowie Ausnahmetext erreichen kein Ereignis, bei je verschiedenen Eingabewerten.
 * Das Modell hat dafür kein Feld; die Tests prüfen es an der Bauart und an Eingaben, die durch den echten Server
 * laufen.
 */
internal class TelemetryRedactionTest : RuntimeTestBase() {
    private val markers = listOf("wertprobe-aq7", "wertprobe-zk2")

    private fun assertClean(marker: String, label: String) {
        assertTrue(sink.events.isNotEmpty(), "$label: keine Ereignisse, die Prüfung bewiese nichts")
        sink.events.forEach { event ->
            val text = event.visibleText().let { if (event is LoginAccepted) it.replace(event.principal, "") else it }
            assertTrue(!text.contains(marker), "$label: Ereignis nennt die Marke: $text")
            assertTrue(!text.contains(directory), "$label: Ereignis nennt einen Pfad: $text")
        }
    }

    private fun loginAndSend(port: Int, name: String, password: String, followUp: ByteArray?) {
        RawClient(port).use { client ->
            client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
            assertNotNull(client.readMessage())
            val payload = SessionVectors.login7Payload {
                userName = name
                this.password = password.toCharArray()
            }
            client.send(handMessage(LOGIN7_TYPE, payload, 4000))
            assertNotNull(client.readMessage())
            if (followUp != null) {
                client.send(followUp)
                assertNotNull(client.readMessage())
            }
        }
    }

    /**
     * Die Bauart: jede Art trägt nur Zeitpunkt, Ebene, Zahlen, Aufzählungen und Zeichenketten aus der Liste ihrer
     * eigenen Felder (Kennung nur in der Annahme, Fehlercode nur im Anfragefehler und so fort).
     * Ein Feld vom Typ einer Ausnahme, einer Bytefolge oder einer Liste, oder eine Zeichenkette unter einem
     * unbekannten Namen, macht den Test rot.
     *
     * @spec SPEC-048
     */
    @Test
    fun `Kein Ereignis hat ein Feld fuer Passwort, Pruefwert, Parameter, SQL, Pfad oder Ausnahmetext`() {
        val kinds: List<Class<*>> = TelemetryEvent::class.java.permittedSubclasses.orEmpty().toList()
        assertTrue(kinds.isNotEmpty(), "die Unterklassen sind nicht lesbar")
        (kinds + listOf(TelemetryEvent::class.java)).forEach { kind ->
            val getters = kind.methods.filter { it.parameterCount == 0 && it.name.startsWith("get") }
            getters.filter { it.name != "getClass" }.forEach { getter ->
                val name = getter.name.removePrefix("get").replaceFirstChar { it.lowercase() }
                val type = getter.returnType
                assertTrue(
                    type in ALLOWED_TYPES || type.isEnum,
                    "${kind.simpleName}.$name hat den Typ ${type.name}",
                )
                if (type == String::class.java) {
                    val allowed = BASE_STRINGS + STRINGS_BY_KIND[kind.simpleName].orEmpty()
                    assertTrue(name in allowed, "${kind.simpleName}.$name ist ein unbekanntes Textfeld der Art")
                }
            }
        }
    }

    /**
     * Anfrage: SQL-Text, Parameterwert und der Text der Ausnahme oder des Backend-Fehlers tragen je Lauf eine andere
     * Marke; kein Ereignis nennt sie. Das Ereignis der Antwort nennt Code und Typname der Ausnahme.
     *
     * @spec SPEC-048
     */
    @Test
    fun `SQL-Text, Parameterwert und Ausnahmetext erreichen kein Ereignis`() = blocking {
        markers.forEach { marker ->
            sink.events.clear()
            val failing = object : QueryBackend {
                var calls = 0

                override suspend fun execute(request: QueryRequest): QueryExecution {
                    calls++
                    check(calls != 2) { "Ausnahmetext $marker" }
                    return ScriptedBackend.ofList(QueryEvent.Failure("TDS_BACKEND_ERROR", "Fehlertext $marker"))
                        .execute(request)
                }

                override suspend fun cancel(sessionId: String, requestId: String) = Unit
            }
            val server = running(
                loopbackEnvironment(file("users-$marker.txt", "alice-$marker\t{plain}pw-$marker\n")),
                Wiring(telemetry = sink, requests = RequestSource(failing)),
            )
            val rpc = RpcWire.executeSql(
                "SELECT '$marker'",
                "@p nvarchar(40)",
                RpcWire.param("@p", RpcWire.nvarchar("wert-$marker")),
            )
            repeat(2) { loginAndSend(server.port, "alice-$marker", "pw-$marker", handMessage(RPC_TYPE, rpc, 4000)) }
            server.stop()
            val failed = sink.of<RequestFailed>()
            assertEquals(2, failed.size, sink.events.toString())
            assertEquals(setOf("TDS_BACKEND_ERROR", "TDS_INTERNAL_ERROR"), failed.map { it.errorCode }.toSet())
            assertEquals("java.lang.IllegalStateException", failed.single { it.cause != null }.cause)
            assertClean(marker, "Anfrage $marker")
        }
    }

    /**
     * Start: Pfad des Speichers, Inhalt der Datei, Kennwort, Tabellennamen und ein Backend zugleich mit Tabellen
     * tragen je Lauf eine andere Marke; weder das Ereignis noch der Grund des Abbruchs nennt sie. Der Speicher ist
     * kein PKCS#12; sein Inhalt und sein Pfad bleiben außen.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Pfad, Schluesselinhalt, Kennwort und Tabellenname erreichen weder Ereignis noch Grund`() = blocking {
        markers.forEach { marker ->
            sink.events.clear()
            val broken = file("kaputt-$marker.p12", "kein Schluesselspeicher $marker")
            val environment = mapOf(
                ConfigKeys.LISTEN_HOST to LOOPBACK,
                ConfigKeys.LISTEN_PORT to "0",
                ConfigKeys.TLS_KEYSTORE_FILE to broken,
                ConfigKeys.TLS_KEYSTORE_PASSWORD_FILE to file("kennwort-$marker.txt", "kennwort-$marker\n"),
                ConfigKeys.AUTH_USERS_FILE to file("users-$marker.txt", "alice\t${pbkdf2Value("pw-$marker")}\n"),
            )
            val aborted = assertIs<StartOutcome.Aborted>(start(environment), marker)
            assertIs<TlsStartRejected>(sink.events.single())
            assertTrue(!aborted.reason.contains(marker) && !aborted.reason.contains(directory), aborted.reason)
            assertClean(marker, "TLS $marker")
            sink.events.clear()
            val table = SimTable(
                name = "t-$marker",
                columns = mutableListOf(SimColumn("Id", SqlType.Int32, nullable = false)),
                primaryKey = mutableListOf("Id"),
                rows = { RowSource { null } },
            )
            val twice = Wiring(
                telemetry = sink,
                requests = RequestSource(simulationTables = listOf(table, table.copy(name = "T-$marker".uppercase()))),
            )
            val duplicate = assertIs<StartOutcome.Aborted>(
                start(loopbackEnvironment(file("users2-$marker.txt", "alice\t{plain}pw\n")), twice),
            )
            assertTrue(!duplicate.reason.contains(marker, ignoreCase = true), duplicate.reason)
            assertClean(marker, "Tabelle $marker")
        }
    }

    /**
     * Bindefehler: die Adresse steht im Ereignis und im Grund (Betriebsangabe, nicht Geheimnis, `SPEC-043`), mit
     * Port; die Ausnahme erscheint nur als Typname, nie mit Text.
     *
     * @spec SPEC-043
     */
    @Test
    fun `Der Bindefehler nennt Adresse und Port und den Typ der Ausnahme`() = blocking {
        val host = "nicht-aufloesbar-wertprobe-aq7.invalid"
        val users = file("users.txt", "alice\t{plain}pw\n")
        val environment = loopbackEnvironment(users) + (ConfigKeys.LISTEN_HOST to host)
        val aborted = assertIs<StartOutcome.Aborted>(start(environment))
        val failed = sink.events.filterIsInstance<BindFailed>().single()
        assertEquals(host, failed.address)
        assertEquals("java.nio.channels.UnresolvedAddressException", failed.exceptionType)
        assertEquals("Start gescheitert: Binden an $host:0 gescheitert: ${failed.exceptionType}", aborted.reason)
    }

    /**
     * `principal`: ein Authentifizierungsadapter, der die Eingabe als Kennung zurückgibt (Steuerzeichen,
     * Anführungszeichen, länger als 256 Zeichen), bringt sie nur gekürzt und nur im Ereignis „Anmeldung angenommen“
     * ins Modell. Das belegt die Längengrenze und den Ort des Feldes, nicht die Abwesenheit eines Geheimnisses in der
     * Kennung eines eigenen Adapters (Grenze der Zusage).
     *
     * @spec SPEC-012
     */
    @Test
    fun `Die Kennung steht gekuerzt und nur im Ereignis der Annahme`() = blocking {
        val echo = object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult =
                AuthResult.Accepted(Principal(request.userName + "k".repeat(LONG)))
        }
        val server = running(
            loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n")),
            Wiring(telemetry = sink, adapter = { echo }),
        )
        loginAndSend(server.port, "a\"b\u0001\nkk", "pw", null)
        server.stop()
        val accepted = sink.of<LoginAccepted>().single()
        assertEquals(LIMIT, accepted.principal.length)
        assertTrue(accepted.principal.startsWith("a\"b\u0001\nkk"))
        sink.events.filter { it !is LoginAccepted }.forEach {
            assertTrue(!it.visibleText().contains("kkkk"), "Kennung außerhalb der Annahme: ${it.visibleText()}")
        }
        assertEquals(1L, accepted.connectionId)
        assertNotNull(accepted.sessionId)
    }

    private val directory: String get() = File(file("anker.txt", "x")).parent
}
