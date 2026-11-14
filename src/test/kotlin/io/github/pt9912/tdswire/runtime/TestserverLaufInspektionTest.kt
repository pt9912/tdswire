package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.config.auth.pbkdf2Value
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import java.util.Base64
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val HEAP = "-Xmx512m"
private const val RECEIVE_BUFFER = 4_096

/**
 * Die Datenmenge der großen Auswahl: 40 MiB, eine Setzung (`TDS-QA-06`). Kein Wert des Hosts geht in sie ein; `HEAP`
 * begrenzt sie nach oben, die Tabelle liegt im Speicher des Kindes. Grenze: die Anfrage bleibt offen, solange die
 * Puffer die Menge nicht fassen; gemessen am lokalen Host (Obergrenzen 4 und 6 MiB) und in einem Container mit
 * Obergrenzen von 144 MiB für beide Puffer, an keinem weiteren Host. Mit und ohne `RECEIVE_BUFFER` blieb sie in beiden
 * Umgebungen offen: eine Wirkung des Parameters auf diese Hosts ist nicht gemessen.
 */
private const val VOLUME = 40L * 1024 * 1024
private const val CHARS = 4_000
private const val BYTES_PER_CHAR = 2L
private const val ROWS_PER_MESSAGE = 1_000
private const val ALICE_PASSWORD = "geheim-pw-a1"
private const val BOB_PASSWORD = "geheim-pw-b2"
private const val WRONG_PASSWORD = "falsch-pw-z3"
private const val UNKNOWN_USER = "bob-zz"
private const val PARAMETER_VALUE = "wertprobe-param-z8"
private const val PERSON = "zz_person"
private const val STORE = "zz_lager"
private const val MISSING = "zz_fehlt"
private const val OPEN_AFTER_LOGIN = 1L
private const val OPEN_WHILE_STUCK = 3L
private const val PERSON_ROWS = 3
private const val UNKNOWN_OBJECT = "TDS_SIM_UNKNOWN_OBJECT"

private val DESCRIPTION = """
    schema_format: "1.0"
    name: demo
    version: "1"
    tables:
      $PERSON:
        columns:
          zz_nr:
            type: identifier
          zz_name:
            type: text
            max_length: 50
        primary_key: [zz_nr]
      $STORE:
        columns:
          zz_nr:
            type: identifier
          zz_daten:
            type: text
            max_length: $CHARS
        primary_key: [zz_nr]
""".trimIndent() + "\n"

/**
 * Ein Lauf eines Rohbytes-Clients über den Testserver-Prozess und die Inspektion aller Zeilen seiner Standardausgabe
 * (`TDS-FA-09`, `TDS-QA-03`, `ADR-0020`; Prozesstests liegen nach `ADR-0022` Entscheidung 4 in der Stage `test`).
 */
internal class TestserverLaufInspektionTest : TempFilesTest() {
    private fun environment() = mapOf(
        ConfigKeys.LISTEN_HOST to LOOPBACK,
        ConfigKeys.LISTEN_PORT to "0",
        ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
        ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
        ConfigKeys.AUTH_USERS_FILE to file(
            "users.txt",
            "alice\t{plain}$ALICE_PASSWORD\n$UNKNOWN_USER\t${pbkdf2Value(BOB_PASSWORD)}\n",
        ),
        ConfigKeys.METRICS_INTERVAL_SECONDS to "1",
        ConfigKeys.QUERY_TIMEOUT to QUERY_TIMEOUT_SECONDS,
        "LOG_LEVEL" to "DEBUG",
        "SIMULATION_DESCRIPTION_FILE" to file("sim.yaml", DESCRIPTION),
    )

    private fun metric(line: String): Map<String, Any>? =
        if (line.startsWith("{") && line.contains("\"event\":\"metrics\"")) FlatJson.parse(line) else null

    /** Eine Zeile `metrics` ohne Anfrage im Flug: [requests] beendete Anfragen und [open] offene Spans. */
    private fun quiet(line: String, requests: Int, open: Long): Boolean = metric(line)?.let {
        it["metrics.requests_total"] == requests.toLong() && it["metrics.spans_open"] == open &&
            it["metrics.backend_active"] == 0L
    } == true

    private fun rows(tokens: List<Token>) = tokens.count { it is RowToken }

    /**
     * Ein Lauf: Anmeldung abgelehnt (ein Fehlversuch), Anmeldung angenommen, Massenladen zweier Tabellen, ein SQL_BATCH
     * mit Ergebnis, eine parametrisierte Anfrage, eine Anfrage auf eine unbekannte Tabelle, eine große Auswahl, deren
     * Antwort der Client nicht liest und die ATTENTION beendet, dann eine weitere Auswahl auf derselben Verbindung und
     * SIGTERM. Jede Zeile der Standardausgabe ist eine der zwei Zeilen des Testservers oder gültiges JSON mit den
     * Pflichtfeldern ihrer Art; kein Feld liegt außerhalb der Felder von `TelemetryJson` und keines steht in einer
     * Art, die es nicht trägt; weder Passwort noch Prüfwert (ganz und in seinen Teilen) noch Kennung der Ablehnung
     * noch Parameterwert noch SQL-Text noch Text der Fehlermeldung steht in einer Zeile; die
     * drei Spans enden mit ihren Pflicht-Attributen, und die Metriken nennen die Anfragen, den Abbruch und keinen
     * offenen Span.
     *
     * @spec SPEC-017
     * @spec SPEC-018
     * @spec SPEC-019
     * @spec SPEC-052
     */
    @Test
    fun `Ein Lauf ueber den Prozess ist vollstaendig erfasst und nennt nichts Verbotenes`() {
        val bigRows = (VOLUME / (CHARS * BYTES_PER_CHAR) + 1).toInt()
        ServerProcess(environment(), jvmOptions = listOf(HEAP)).use { server ->
            val port = server.port()
            LaufClient(port).use { assertFalse(it.login(UNKNOWN_USER, WRONG_PASSWORD)) }
            val (requests, errorText) = LaufClient(port, RECEIVE_BUFFER).use { client ->
                assertTrue(client.login("alice", ALICE_PASSWORD))
                val loadSteps = load(client, bigRows)
                val text = ask(server, client, VOLUME)
                assertEquals(PERSON_ROWS, rows(client.batch("SELECT zz_nr, zz_name FROM $PERSON")), "offen nach Cancel")
                assertEquals(loadSteps + QUERIES_AFTER_LOAD, client.requests)
                client.requests to text
            }
            settle(server, requests)
            assertNotNull(server.terminate(), "der Prozess endet nach SIGTERM")
            assertNotNull(server.awaitLine("${ServerProcess.PREFIX} gestoppt"), server.output.toString())
            inspect(server, port, errorText, requests, requests - QUERIES_AFTER_LOAD)
        }
    }

    /** Lädt beide Tabellen per Massenladen; die Zahl der Anfragen dafür (`INSERT BULK` und je Nachricht eine). */
    private fun load(client: LaufClient, bigRows: Int): Int {
        val person = BulkWire.message("zz_nr", "zz_name", 50, 1, listOf("anna", "bert", PARAMETER_VALUE))
        assertEquals(PERSON_ROWS.toLong(), client.load(BulkWire.insertBulk(PERSON, "zz_nr", "zz_name", 50), person))
        val insert = BulkWire.insertBulk(STORE, "zz_nr", "zz_daten", CHARS)
        val filler = "x".repeat(CHARS)
        var first = 1L
        while (first <= bigRows) {
            val count = minOf(ROWS_PER_MESSAGE.toLong(), bigRows - first + 1).toInt()
            val message = BulkWire.message("zz_nr", "zz_daten", CHARS, first, List(count) { filler })
            assertEquals(count.toLong(), client.load(insert, message))
            first += count
        }
        return client.requests
    }

    /**
     * Die Anfragen mit Ergebnis, mit Parameter und mit Fehler, dann die große Auswahl mit ATTENTION. Der Cancel folgt
     * erst, wenn eine Zeile `metrics` die offene Anfrage zeigt, nachdem eine Zeile die Ruhe gezeigt hat. Liefert den
     * Text der Fehlermeldung, den kein Ereignis tragen darf.
     */
    private fun ask(server: ServerProcess, client: LaufClient, volume: Long): String {
        assertEquals(PERSON_ROWS, rows(client.batch("SELECT zz_nr, zz_name FROM $PERSON")))
        val param = client.parameterized("SELECT zz_nr, zz_name FROM $PERSON WHERE zz_name = @p", PARAMETER_VALUE)
        assertEquals(1, rows(param), "Ergebnis der parametrisierten Anfrage")
        val failed = client.batch("SELECT zz_nr FROM $MISSING").filterIsInstance<ErrorToken>().single()
        val quiet = assertNotNull(
            server.awaitIndex { quiet(it, client.requests, OPEN_AFTER_LOGIN) },
            "keine Ruhe vor dem Cancel: ${server.output}",
        )
        client.sendBatch("SELECT zz_nr, zz_daten FROM $STORE")
        val stuck = assertNotNull(
            server.awaitIndex(quiet + 1) { metric(it)?.get("metrics.backend_active") == 1L },
            "keine offene Anfrage (die Puffer der Umgebung fassen die Menge $volume Byte?): ${server.output}",
        )
        assertEquals(OPEN_WHILE_STUCK, metric(server.output[stuck])?.get("metrics.spans_open"))
        client.sendAttention()
        val (bytes, attention) = client.drainAnswer()
        assertTrue(attention, "die Antwort endet mit DONE und ATTENTION")
        assertTrue(bytes < volume, "die Antwort ist abgebrochen (Puffer größer als die Menge?): $bytes von $volume")
        return failed.message.text
    }

    /**
     * Wartet auf die Ruhe nach dem Schließen: das Ende beider Verbindungen als Zeilen, danach eine Zeile `metrics`
     * mit allen [requests] Anfragen und ohne offenen Span.
     */
    private fun settle(server: ServerProcess, requests: Int) {
        var spanAt = 0
        repeat(CONNECTIONS) {
            val line = server.awaitIndex(spanAt, ::ended)
            spanAt = assertNotNull(line, "Ende der Verbindung: ${server.output}") + 1
        }
        assertNotNull(server.awaitIndex(spanAt) { quiet(it, requests, 0) }, "keine Ruhe: ${server.output}")
        var closedAt = 0
        repeat(CONNECTIONS) {
            val line = server.awaitIndex(closedAt) { text -> text.contains("\"event\":\"connection_closed\"") }
            closedAt = assertNotNull(line, "connection_closed: ${server.output}") + 1
        }
    }

    private fun ended(line: String): Boolean =
        line.contains("\"event\":\"span_ended\"") && line.contains("\"span\":\"tds.connection\"")

    private fun inspect(server: ServerProcess, port: Int, errorText: String, requests: Int, loadSteps: Int) {
        val output = server.output.toList()
        val ready = "${ServerProcess.PREFIX} gestartet, Port $port"
        assertEquals(listOf(ready, "${ServerProcess.PREFIX} gestoppt"), LogInspektion.plainLines(output))
        assertEquals("${ServerProcess.PREFIX} gestoppt", output.last())
        val events = LogInspektion.events(output)
        assertEquals("server_stopped", events.last()["event"])
        LogInspektion.assertFields(events)
        val forbidden = listOf(
            ALICE_PASSWORD, BOB_PASSWORD, WRONG_PASSWORD, UNKNOWN_USER, PARAMETER_VALUE, "zz_", "select",
            "insert", "sp_executesql", "nvarchar", "@p", errorText,
        ) + checkValueParts()
        LogInspektion.assertNoneOf(server, forbidden)
        inspectEvents(events, port)
        inspectSpans(events, loadSteps)
        inspectMetrics(events, requests)
    }

    /**
     * Der Prüfwert aus der Benutzerdatei des Laufs ohne Passwort und seine Teile: die Marke, das Salz und der Hash in
     * der Form der Datei (Base64 mit Padding), als Base64URL ohne Padding und als Hex.
     * Grenze: ein Teilstück oder ein auf mehrere Felder verteilter Wert wird nicht gefunden.
     */
    private fun checkValueParts(): List<String> {
        val value = pbkdf2Value(BOB_PASSWORD)
        val parts = value.substringAfter('}').split('$')
        val bytes = listOf(parts.first(), parts.last()).map { Base64.getDecoder().decode(it) }
        val hex = bytes.map { HexFormat.of().formatHex(it) }
        val urlForm = listOf(parts.first(), parts.last()).map { it.trimEnd('=').replace('+', '-').replace('/', '_') }
        return listOf(value, value.substringBefore('}') + "}", "pbkdf2") + parts.first() + parts.last() + hex + urlForm
    }

    private fun inspectEvents(events: List<Map<String, Any>>, port: Int) {
        fun count(name: String) = events.count { it["event"] == name }
        assertEquals(CONNECTIONS, count("connection_opened"))
        assertEquals(CONNECTIONS, count("connection_closed"))
        assertEquals(CONNECTIONS, count("encryption_not_offered"))
        assertEquals("alice", events.single { it["event"] == "login_accepted" }["principal"])
        assertEquals("WRONG_PASSWORD", events.single { it["event"] == "login_rejected" }["reason"])
        assertEquals(port.toLong(), events.single { it["event"] == "server_started" }["port"])
        assertEquals(1, count("request_failed"))
    }

    private fun inspectSpans(events: List<Map<String, Any>>, loadSteps: Int) {
        val spans = events.filter { it["event"] == "span_ended" }
        val connections = spans.filter { it["span"] == "tds.connection" }
        assertEquals<Map<Any?, Any?>>(
            mapOf("login_rejected" to "handshake", "ok" to "session"),
            connections.associate { it["outcome"] to it["phase"] },
        )
        val queries = spans.filter { it["span"] == "tds.query" }
        val outcomes = List(loadSteps) { "ok" } + listOf("ok", "ok", "error", "cancelled", "ok")
        val kinds = List(loadSteps) { "sql_batch" } + listOf("sql_batch", "rpc", "sql_batch", "sql_batch", "sql_batch")
        assertEquals(outcomes, queries.map { it["outcome"] }, spans.toString())
        assertEquals(kinds, queries.map { it["request_kind"] })
        assertEquals(1, queries.map { it["session_id"] }.toSet().size)
        val backends = spans.filter { it["span"] == "backend.execute" }
        val byRequest = queries.associateBy { it["request_id"] }
        backends.forEach { backend ->
            val query = assertNotNull(byRequest[backend["request_id"]], "backend.execute ohne tds.query: $backend")
            assertEquals(query["session_id"], backend["session_id"])
        }
        val last = queries.takeLast(QUERIES_AFTER_LOAD)
        last.forEach { query ->
            val backend = backends.singleOrNull { it["request_id"] == query["request_id"] }
            assertEquals(query["outcome"], assertNotNull(backend, "backend.execute zu $query")["outcome"])
        }
        val failed = events.single { it["event"] == "request_failed" }
        assertEquals(last[ERROR_AT]["request_id"], failed["request_id"])
        assertEquals(UNKNOWN_OBJECT, failed["error_code"])
    }

    private fun inspectMetrics(events: List<Map<String, Any>>, requests: Int) {
        val last = events.last { it["event"] == "metrics" }
        val expected = mapOf(
            "metrics.requests_total" to requests.toLong(),
            "metrics.logins_succeeded_total" to 1L,
            "metrics.logins_failed_total" to 1L,
            "metrics.aborts_total" to 1L,
            "metrics.protocol_errors_total" to 0L,
            "metrics.spans_open" to 0L,
            "metrics.backend_active" to 0L,
            "metrics.sessions_active" to 0L,
        )
        assertEquals(expected, expected.keys.associateWith { last[it] }, last.toString())
    }

    private companion object {
        const val CONNECTIONS = 2

        /** Das Maximum von `QUERY_TIMEOUT`: die Frist des Servers liegt weit über jeder Schranke einer Wartestelle. */
        const val QUERY_TIMEOUT_SECONDS = "3600"
        const val QUERIES_AFTER_LOAD = 5
        const val ERROR_AT = 2
    }
}
