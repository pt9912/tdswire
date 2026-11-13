package io.github.pt9912.tdswire.runtime

import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SPAN_ENDED = "span_ended"
private const val REQUEST_FAILED = "request_failed"
private const val LOGIN_ACCEPTED = "login_accepted"
private const val TDS_CONNECTION = "tds.connection"
private const val TDS_QUERY = "tds.query"
private const val BACKEND_EXECUTE = "backend.execute"

/**
 * Die Prüfung aller Zeilen der Standardausgabe eines Laufs gegen die Felder von `spezifikation.md` Abschnitt 4 und 5
 * (`TDS-QA-03`): die Pflichtfelder je Art sind eine Festlegung aus der Spezifikation, nicht gemessen. Die Menge der
 * erlaubten Felder und ihre Arten sind die Felder von `TelemetryJson` (`SPEC-052`; sie enthält auch `issue_kind` und
 * `exception_type`, die die Spezifikation nicht führt) und damit eine zweite Fundstelle derselben Zahl: `TelemetryJson`
 * liegt im Modul `testserver`, das `src/test` nicht sieht; `TelemetryJsonTest` hält jene gleich dem Modell der
 * Bibliothek, diese hält nichts. Grenze: ein Feld dieser Menge, das der Adapter nie schreibt, und eine Art, die der
 * Lauf nicht erzeugt (etwa `bind_failed`), bleiben unbemerkt; der Lauf prüft nur, was er auslöst.
 */
internal object LogInspektion {
    private val HEAD = setOf("ts", "level", "event")

    /** Die Felder der Ereignisse (`TelemetryEvent`) in der Schreibweise der Zeile. */
    private val EVENT_FIELDS = setOf(
        "connection_id", "session_id", "client_profile", "port", "tables", "reason", "key", "issue_kind", "part",
        "form", "address", "exception_type", "principal", "failure", "error_code", "cause", "span", "outcome",
        "request_id", "duration_ms", "phase", "request_kind",
    )

    /** Die Schlüssel von `CONFIG_EFFECTIVE` in Kleinbuchstaben (`EffectiveSettings.asMap`). */
    private val SETTINGS = setOf(
        "login_timeout", "max_prelogin_message_bytes", "encryption_policy", "tls_keystore_file",
        "auth_plain_testmode", "query_timeout", "cancel_timeout", "shutdown_grace", "max_connections",
        "max_prepared_handles", "max_message_bytes", "metrics_interval_seconds",
    )

    /** Die Messwerte von `METRICS`, mit dem Präfix `metrics.` der Zeile. */
    private val METRIC_KEYS = setOf(
        "sessions_active", "logins_succeeded_total", "logins_failed_total", "requests_total",
        "request_latency_p50_ms", "request_latency_p95_ms", "request_latency_p99_ms", "backend_active",
        "aborts_total", "protocol_errors_total", "spans_open",
    ).map { "metrics.$it" }.toSet()

    private val KNOWN = HEAD + EVENT_FIELDS + SETTINGS + METRIC_KEYS + "dropped"

    /** Die Arten, die `TelemetryJson` mit `connection_id` schreibt (Unterklassen von `TelemetryEvent` mit Kontext). */
    private val WITH_CONNECTION = setOf(
        "connection_opened",
        "connection_closed",
        "encryption_not_offered",
        LOGIN_ACCEPTED,
        "login_rejected",
        "adapter_failed",
        "connection_failed",
        "tls_first_not_offered",
        "tls_handshake_failed",
        REQUEST_FAILED,
        SPAN_ENDED,
    )

    /**
     * Das Feld und die Arten, in denen es stehen darf: die Zuordnung, mit der `TelemetryJson` es aus `TelemetryEvent`
     * schreibt (`spezifikation.md` Abschnitt 4, Herkunft der IDs). Ein Feld in einer anderen Art ist ein Befund.
     */
    private val ONLY_IN = mapOf(
        "request_id" to setOf(SPAN_ENDED, REQUEST_FAILED),
        "principal" to setOf(LOGIN_ACCEPTED),
        "duration_ms" to setOf(SPAN_ENDED),
        "error_code" to setOf(REQUEST_FAILED),
        "session_id" to setOf(LOGIN_ACCEPTED, REQUEST_FAILED, SPAN_ENDED),
        "connection_id" to WITH_CONNECTION,
        "cause" to setOf(REQUEST_FAILED),
        "reason" to setOf(
            "security_weakened",
            "login_rejected",
            "connection_closed",
            "start_refused",
            "tls_first_not_offered",
        ),
        "failure" to setOf("tls_handshake_failed"),
        "exception_type" to setOf("bind_failed", "adapter_failed", "connection_failed", "accept_failed"),
        "issue_kind" to setOf("config_invalid"),
        "key" to setOf("config_invalid"),
        "part" to setOf("tls_start_rejected"),
        "form" to setOf("tls_start_rejected"),
        "address" to setOf("server_started", "bind_failed"),
        "port" to setOf("server_started", "bind_failed"),
        "tables" to setOf("simulation_backend_ready"),
        "span" to setOf(SPAN_ENDED),
        "outcome" to setOf(SPAN_ENDED),
        "phase" to setOf(SPAN_ENDED),
        "request_kind" to setOf(SPAN_ENDED),
    )

    /** Die Zeilen, die keine Ereignisse sind: die zwei Zeilen des Testservers. */
    fun plainLines(output: List<String>): List<String> = output.filterNot { it.startsWith("{") }

    /** Die Ereignisse der Ausgabe; jede Zeile mit `{` muss gültiges JSON sein. */
    fun events(output: List<String>): List<Map<String, Any>> =
        output.filter { it.startsWith("{") }.map { assertNotNull(FlatJson.parse(it), "keine gültige JSON-Zeile: $it") }

    /** Die Felder, die [event] nach Art und Span tragen muss. */
    private fun required(event: Map<String, Any>): Set<String> {
        val name = event["event"]
        val common = HEAD + "client_profile" + if (name in WITH_CONNECTION) setOf("connection_id") else emptySet()
        return common + when (name) {
            SPAN_ENDED -> spanFields(event)
            REQUEST_FAILED -> setOf("session_id", "request_id", "error_code")
            LOGIN_ACCEPTED -> setOf("principal")
            "login_rejected", "connection_closed", "security_weakened" -> setOf("reason")
            "server_started" -> setOf("address", "port")
            else -> emptySet()
        }
    }

    private fun spanFields(event: Map<String, Any>): Set<String> {
        val base = setOf("span", "outcome", "duration_ms")
        return base + when (event["span"]) {
            TDS_CONNECTION -> setOf("phase") + if (event["phase"] == "session") setOf("session_id") else emptySet()
            TDS_QUERY -> setOf("session_id", "request_id", "request_kind")
            BACKEND_EXECUTE -> setOf("session_id", "request_id")
            else -> emptySet()
        }
    }

    /**
     * (a) und (b): jede Zeile trägt die Pflichtfelder ihrer Art, jedes Feld gehört zur Menge der Felder der
     * Spezifikation, und ein Feld mit begrenzter Herkunft steht nur in den Arten, die es tragen dürfen.
     */
    fun assertFields(events: List<Map<String, Any>>) {
        events.forEach { event ->
            val missing = required(event) - event.keys
            assertTrue(missing.isEmpty(), "Pflichtfelder $missing fehlen in $event")
            val unknown = event.keys - KNOWN
            assertTrue(unknown.isEmpty(), "unbekannte Felder $unknown in $event")
            ONLY_IN.forEach { (field, kinds) ->
                if (field in event) assertTrue(event["event"] in kinds, "$field steht nicht in dieser Art: $event")
            }
            if (event["event"] == "metrics") {
                assertTrue(event.keys.any { it in METRIC_KEYS }, "metrics ohne Messwerte: $event")
            }
        }
    }

    /**
     * (c): keines der Wörter in [forbidden] steht in einer Zeile der Ausgabe von [server]; die Kennung der angenommenen
     * Anmeldung im Feld `principal` darf stehen. Die Suche ist unabhängig von der Schreibweise.
     */
    fun assertNoneOf(server: ServerProcess, forbidden: Collection<String>) {
        val text = server.outputWithoutPrincipal().joinToString("\n").lowercase()
        val all = server.output.joinToString("\n")
        forbidden.forEach { word -> assertFalse(text.contains(word.lowercase()), "die Ausgabe nennt '$word':\n$all") }
    }
}
