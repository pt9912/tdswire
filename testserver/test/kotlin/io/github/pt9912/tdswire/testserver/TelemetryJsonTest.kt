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
        val recorder = RealEvents.collect(environment("$name\t{plain}secret\n")) { port ->
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
