package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LOOPBACK = "127.0.0.1"
private const val MAX_TEXT = 256
private const val SNAPSHOT_WAIT_MILLIS = 1_500L

/** Anführungszeichen, Rückstrich, Schrägstrich, alle Steuerzeichen, DEL, BMP, ein Paar aus Ersatzzeichen, U+2028. */
private val NASTY: String = buildString {
    append("a\"b\\c/d")
    (0..0x1F).forEach { append(it.toChar()) }
    append('\u007F')
    append("ü€😀")
    append(' ')
}

/** Ein einzelnes Ersatzzeichen ohne Partner, wie es eine fremde Kennung tragen könnte. */
private const val LONE_SURROGATE = "x\uD800y"

/**
 * Die JSON-Zeile des Adapters ([TelemetryJson]) gegen den Leser von `jackson-core`: Escaping, Form, Felder je
 * Ereignis. Ereignisse bauen nur der Server und die Bibliothek (die Klassen je Art sind `internal`); die Tests holen
 * sie aus einem Start im Prozess ([RealEvents]).
 */
internal class TelemetryJsonTest : TempFilesTest() {
    private fun environment(users: String, vararg more: Pair<String, String>) = testModeEnvironment(
        file("users.txt", users),
        EnvKeys.LISTEN_HOST to LOOPBACK,
        EnvKeys.LISTEN_PORT to "0",
        *more,
    )

    private fun assertWellFormed(line: String): Map<String, Any> {
        assertTrue(line.all { it.code in 0x20..0x7E }, "Zeile nur aus druckbarem ASCII: $line")
        return parseFlat(line)
    }

    /**
     * Anführungszeichen, Rückstrich, jedes Steuerzeichen, Unicode (BMP und außerhalb) und ein einzelnes Ersatzzeichen
     * in `principal`, Adresse, Typname und Schlüsselname kommen aus dem Leser unverändert zurück; die Zeile hat kein
     * Zeichen außerhalb des druckbaren ASCII und keinen Zeilenumbruch.
     *
     * @spec SPEC-052
     * @spec SPEC-012
     */
    @Test
    fun `Das Escaping aller Zeichenketten ist vollstaendig`() {
        for (value in listOf(NASTY, LONE_SURROGATE, "", " ", "ä")) {
            val fields = listOf("principal", "address", "exception_type", "key").map { JsonField(it, value) }
            val line = TelemetryJson.line(Instant.parse("2026-11-06T10:15:30Z"), "INFO", "x", fields)
            val parsed = assertWellFormed(line)
            fields.forEach { assertEquals(value, parsed[it.name], "Feld ${it.name} für ${value.length} Zeichen") }
        }
    }

    /**
     * `ts` ist der Zeitpunkt des Ereignisses in UTC als ISO-8601 und kommt aus `at`; `level` ist der Name der Ebene.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Die Zeile beginnt mit ts, level und event`() {
        val line = TelemetryJson.line(Instant.parse("2026-11-06T10:15:30.123Z"), "WARN", "login_rejected", emptyList())
        assertEquals("""{"ts":"2026-11-06T10:15:30.123Z","level":"WARN","event":"login_rejected"}""", line)
        val whole = TelemetryJson.line(Instant.parse("2026-11-06T10:15:30Z"), "INFO", "x", emptyList())
        assertTrue(whole.startsWith("""{"ts":"2026-11-06T10:15:30Z","""), whole)
    }

    /**
     * Ein Feld mit dem Wert `null` steht nicht in der Zeile; Zeichenketten und ganze Zahlen werden geschrieben, ein
     * anderer Typ bricht.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Felder ohne Wert entfallen`() {
        val fields = listOf(JsonField("a", null), JsonField("b", "text"), JsonField("c", 7), JsonField("d", 8L))
        val parsed = parseFlat(TelemetryJson.line(Instant.EPOCH, "INFO", "x", fields))
        assertNull(parsed["a"])
        assertEquals(mapOf<String, Any>("b" to "text", "c" to 7L, "d" to 8L), parsed.filterKeys { it in "bcd" })
    }

    /**
     * Jede Ereignisart hat einen eigenen Namen, und er ist der Name der Konstante in Kleinbuchstaben.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Jede Ereignisart hat einen eigenen Namen`() {
        val names = EventKind.entries.map { TelemetryJson.eventName(it) }
        assertEquals(EventKind.entries.map { it.name.lowercase() }, names)
        assertEquals(names.size, names.toSet().size)
    }

    /**
     * Die Tabelle des Adapters deckt jeden öffentlichen Zugriff von `TelemetryEvent`: wächst die Klasse um ein Feld,
     * wird dieser Test rot, statt dass die Zeile es still auslässt.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Die Zeile deckt jeden Zugriff des Ereignisses`() {
        val getters = TelemetryEvent::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.name != "getClass" }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }
            .toSet()
        assertEquals(getters, TelemetryJson.covered)
    }

    /**
     * Eine Anfrage, die der Server mit einem Fehlercode beantwortet, an einem Start im Prozess: die Zeile
     * `request_failed` trägt `error_code` und `request_id`, und `request_id` ist die Kennung im Ende von `tds.query`
     * derselben Anfrage; das Ereignis der Anmeldung trägt keines. Wächter: das Feld fehlt in der Zeile, oder die
     * Zeile nennt eine andere Kennung.
     *
     * @spec SPEC-052
     * @spec SPEC-018
     */
    @Test
    fun `Die Zeile request_failed traegt request_id`() {
        val recorder = RealEvents.collect(
            environment(
                "alice\t{plain}secret\n",
                EnvKeys.SIMULATION_DESCRIPTION_FILE to file("sim.yaml", PERSON_YAML),
            ),
            listOf(EventKind.REQUEST_FAILED),
        ) { port ->
            LoginProbe(port).use {
                assertTrue(it.login("alice", "secret"))
                it.batch("dieser Text ist kein SQL")
            }
        }
        val failed = recorder.ofKind(EventKind.REQUEST_FAILED).single()
        val query = recorder.events.single { it.kind == EventKind.SPAN_ENDED && it.span == "tds.query" }
        val parsed = assertWellFormed(TelemetryJson.line(failed))
        assertEquals("request_failed", parsed["event"])
        assertTrue(parsed["error_code"] is String, parsed.toString())
        assertEquals(query.requestId, parsed["request_id"])
        assertTrue(checkNotNull(query.requestId).isNotEmpty())
        val accepted = assertWellFormed(TelemetryJson.line(recorder.ofKind(EventKind.LOGIN_ACCEPTED).single()))
        assertFalse(accepted.containsKey("request_id"))
    }

    /**
     * Die Felder eines Ereignisses der Annahme, an einem Start im Prozess: Kennung mit Anführungszeichen, Rückstrich,
     * Steuerzeichen, Unicode und Paar aus Ersatzzeichen steht in `principal` unverändert, `connection_id` und
     * `session_id` stehen da, und die Zeile bleibt gültiges JSON.
     *
     * @spec SPEC-012
     * @spec SPEC-052
     */
    @Test
    fun `Die Kennung der Annahme steht escaped im Feld principal`() {
        val name = "a\"b\\c\u0001\u001F/ü€😀"
        val users = environment("$name\t{plain}secret\n")
        val recorder = RealEvents.collect(users, listOf(EventKind.LOGIN_ACCEPTED)) { port ->
            LoginProbe(port).use { assertTrue(it.login(name, "secret")) }
        }
        val accepted = recorder.await(EventKind.LOGIN_ACCEPTED)
        val parsed = assertWellFormed(TelemetryJson.line(accepted))
        assertEquals(name, parsed["principal"])
        assertEquals("login_accepted", parsed["event"])
        assertEquals("INFO", parsed["level"])
        assertTrue(parsed["connection_id"] is Long && parsed["session_id"] is String, parsed.toString())
        assertFalse(parsed.containsKey("dropped"))
    }

    /**
     * Das Ende eines Spans und der Schnappschuss der Metriken an einem Start im Prozess (`METRICS_INTERVAL_SECONDS` 1):
     * `span_ended` trägt Name, Ausgang, Phase, Dauer und die Kennungen der Verbindung und Sitzung, `metrics` die
     * Messwerte als flache Felder `metrics.<Schlüssel>` mit ganzen Zahlen; beide bleiben gültiges JSON aus druckbarem
     * ASCII, und die Felder einer anderen Art stehen nicht da.
     *
     * @spec SPEC-052
     * @spec SPEC-017
     */
    @Test
    fun `Span-Ende und Schnappschuss stehen als flache Zeilen`() {
        val recorder = RealEvents.collect(
            environment("alice\t{plain}secret\n", EnvKeys.METRICS_INTERVAL_SECONDS to "1"),
        ) { port ->
            LoginProbe(port).use { assertTrue(it.login("alice", "secret")) }
            Thread.sleep(SNAPSHOT_WAIT_MILLIS)
        }
        val span = assertWellFormed(TelemetryJson.line(recorder.ofKind(EventKind.SPAN_ENDED).single()))
        assertEquals("span_ended", span["event"])
        assertEquals("DEBUG", span["level"])
        assertEquals("tds.connection", span["span"])
        assertEquals("ok", span["outcome"])
        assertEquals("session", span["phase"])
        assertTrue(span["duration_ms"] is Long && span["connection_id"] is Long, span.toString())
        assertTrue(span["session_id"] is String, span.toString())
        assertFalse(span.containsKey("request_id") || span.containsKey("request_kind") || span.containsKey("metrics"))

        val metrics = assertWellFormed(TelemetryJson.line(recorder.ofKind(EventKind.METRICS).first()))
        assertEquals("metrics", metrics["event"])
        assertEquals("INFO", metrics["level"])
        assertEquals(1L, metrics["metrics.logins_succeeded_total"])
        assertTrue(metrics["metrics.sessions_active"] is Long && metrics["metrics.spans_open"] is Long, "$metrics")
        assertFalse(metrics.containsKey("metrics") || metrics.containsKey("span") || metrics.containsKey("outcome"))
    }

    /**
     * Ein Bindefehler nennt Adresse und Port, ein Konfigurationsfehler der Bibliothek Schlüssel und Art; die Felder
     * einer anderen Art stehen nicht da. Die Adresse `LISTEN_HOST` ist hier 255 Zeichen `a` und ein Paar aus
     * Ersatzzeichen: die Bibliothek kürzt auf 256 Einheiten und lässt das halbe Paar weg, die Zeile bleibt gültiges
     * JSON.
     *
     * @spec SPEC-043
     * @spec SPEC-052
     */
    @Test
    fun `Bindefehler und Konfigurationsfehler tragen ihre Felder`() {
        val busy = RealEvents.busyPort(environment("alice\t{plain}secret\n"))
        val bind = TelemetryJson.line(busy.ofKind(EventKind.BIND_FAILED).single())
        val bound = assertWellFormed(bind)
        assertEquals(LOOPBACK, bound["address"])
        assertTrue(bound["port"] is Long && bound["level"] == "ERROR" && bound["event"] == "bind_failed", bind)
        assertFalse(bound.containsKey("principal"))

        val invalid = RealEvents.busyPort(environment("alice\t{plain}secret\n", EnvKeys.LOGIN_TIMEOUT to "0"))
        val config = assertWellFormed(TelemetryJson.line(invalid.ofKind(EventKind.CONFIG_INVALID).single()))
        assertEquals("LOGIN_TIMEOUT", config["key"])
        assertEquals("OUT_OF_RANGE", config["issue_kind"])

        val long = "a".repeat(MAX_TEXT - 1) + "😀"
        val unresolved = RealEvents.busyPort(environment("alice\t{plain}secret\n", EnvKeys.LISTEN_HOST to long))
        val event = (unresolved.ofKind(EventKind.BIND_FAILED) + unresolved.ofKind(EventKind.CONFIG_INVALID)).first()
        val parsed = assertWellFormed(TelemetryJson.line(event))
        assertTrue(parsed.values.filterIsInstance<String>().all { it.length <= MAX_TEXT }, parsed.toString())
    }

    /**
     * Die Zeilen der Zustandsereignisse, an einem Start im Prozess mit einer Verbindung: `server_started` nennt
     * Adresse und Port, `config_effective` je Einstellung ein Feld unter dem Schlüssel in Kleinbuchstaben (Zahlen als
     * Zahl, `ENCRYPTION_POLICY` als Zeichenkette, kein Pfad), `connection_opened` und `connection_closed` die
     * `connection_id` und das Ende den `reason` (`SHUTDOWN`: die Verbindung ruht beim Beenden), `server_stopping` hat
     * keine Felder außer dem Kopf.
     *
     * @spec SPEC-052
     * @spec SPEC-005
     */
    @Test
    fun `Die Zustandsereignisse tragen ihre Felder in der Zeile`() {
        val held = mutableListOf<LoginProbe>()
        val recorder = RealEvents.collect(environment("alice\t{plain}secret\n")) { port ->
            held += LoginProbe(port).also { assertTrue(it.login("alice", "secret")) }
        }
        held.forEach { it.close() }
        val started = assertWellFormed(TelemetryJson.line(recorder.ofKind(EventKind.SERVER_STARTED).single()))
        assertEquals(LOOPBACK, started["address"])
        assertTrue(started["port"] is Long && started["event"] == "server_started", started.toString())

        val effective = assertWellFormed(TelemetryJson.line(recorder.ofKind(EventKind.CONFIG_EFFECTIVE).single()))
        assertEquals("config_effective", effective["event"])
        assertEquals(15L, effective["login_timeout"])
        assertEquals("TEST_UNENCRYPTED", effective["encryption_policy"])
        assertEquals("UNSET", effective["tls_keystore_file"])
        assertEquals("true", effective["auth_plain_testmode"])
        assertEquals(16_777_216L, effective["max_message_bytes"])
        assertFalse(effective.values.any { it is String && it.contains("users.txt") }, effective.toString())

        val opened = assertWellFormed(TelemetryJson.line(recorder.ofKind(EventKind.CONNECTION_OPENED).single()))
        assertTrue(opened["connection_id"] == 1L && opened["event"] == "connection_opened", opened.toString())
        val closed = assertWellFormed(TelemetryJson.line(recorder.ofKind(EventKind.CONNECTION_CLOSED).single()))
        assertEquals("SHUTDOWN", closed["reason"])
        assertEquals(1L, closed["connection_id"])
        val stopping = assertWellFormed(TelemetryJson.line(recorder.ofKind(EventKind.SERVER_STOPPING).single()))
        assertEquals(setOf("ts", "level", "event", "client_profile"), stopping.keys)
    }

    /**
     * Mit `dropped` größer null steht das Feld als letztes in der Zeile; die Zeile des Adapters für verlorene Zeilen
     * trägt Ebene `WARN`, den Namen `log_dropped` und die Zahl.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Verworfene Zeilen stehen im Feld dropped`() {
        val recorder = RealEvents.collect(environment("alice\t{plain}secret\n")) {}
        val started = recorder.ofKind(EventKind.SERVER_STARTED).single()
        assertFalse(parseFlat(TelemetryJson.line(started)).containsKey("dropped"))
        assertEquals(listOf("dropped"), parseFlat(TelemetryJson.line(started, 3)).keys.toList().takeLast(1))
        assertEquals(3L, parseFlat(TelemetryJson.line(started, 3))["dropped"])
        val last = parseFlat(TelemetryJson.droppedLine(Instant.parse("2026-11-06T10:15:30Z"), 42))
        assertEquals(
            mapOf<String, Any>(
                "ts" to "2026-11-06T10:15:30Z",
                "level" to "WARN",
                "event" to "log_dropped",
                "dropped" to 42L,
            ),
            last,
        )
    }
}
