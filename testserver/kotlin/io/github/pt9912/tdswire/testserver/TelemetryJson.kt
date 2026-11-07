package io.github.pt9912.tdswire.testserver

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.SerializableString
import com.fasterxml.jackson.core.io.CharacterEscapes
import com.fasterxml.jackson.core.json.JsonWriteFeature
import io.github.pt9912.tdswire.domain.telemetry.EventKind
import io.github.pt9912.tdswire.domain.telemetry.TelemetryEvent
import java.io.StringWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Ein Feld der JSON-Zeile; [value] ist eine Zeichenkette, eine Zahl oder `null` (dann steht das Feld nicht da). */
internal class JsonField(val name: String, val value: Any?)

private const val DELETE = 0x7F

/** Die Escape-Tabelle des Standards von JSON, dazu DEL (0x7F) als Steuerzeichen. */
private object ControlEscapes : CharacterEscapes() {
    private val codes: IntArray = standardAsciiEscapesForJSON().also { it[DELETE] = ESCAPE_STANDARD }

    override fun getEscapeCodesForAscii(): IntArray = codes

    override fun getEscapeSequence(ch: Int): SerializableString? = null
}

/** Ein Feld des Ereignisses und der Name, unter dem es in der Zeile steht. */
private class EventProperty(val property: String, val json: String, val read: (TelemetryEvent) -> Any?)

/**
 * Die JSON-Zeile je Ereignis (`ADR-0020`, `SPEC-052`): `ts` (UTC, ISO-8601, aus `at`), `level`, `event`, dann die
 * Felder des Ereignisses, die nicht `null` sind, dann `dropped`, wenn Zeilen verworfen wurden. Die Messwerte von
 * `metrics` stehen als Felder `metrics.<Schlüssel>` in der flachen Zeile. Geschrieben wird mit dem
 * Streaming-Generator von `jackson-core`; das Escaping der Zeichenketten ist das der Bibliothek, mit
 * `ESCAPE_NON_ASCII` und DEL als Steuerzeichen: die Zeile besteht nur aus druckbaren ASCII-Zeichen, damit die
 * Zeichenkodierung der Standardausgabe (die Locale des Prozesses) kein Zeichen verändert. Eine Zeile enthält keinen
 * Zeilenumbruch.
 *
 * Die Felder des Ereignisses liest [fieldsOf] aus der Tabelle [PROPERTIES]; `TelemetryJsonTest` hält sie gleich
 * den öffentlichen Zugriffen von `TelemetryEvent`. Der Name jeder Art steht in [eventName]; das `when` hat kein
 * `else`, eine neue Art bricht die Übersetzung.
 */
internal object TelemetryJson {
    private val factory: JsonFactory = JsonFactory.builder()
        .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
        .build()
        .also { it.characterEscapes = ControlEscapes }

    private val PROPERTIES = listOf(
        EventProperty("connectionId", "connection_id") { it.connectionId },
        EventProperty("sessionId", "session_id") { it.sessionId },
        EventProperty("clientProfile", "client_profile") { it.clientProfile },
        EventProperty("port", "port") { it.port },
        EventProperty("tables", "tables") { it.tables },
        EventProperty("reason", "reason") { it.reason },
        EventProperty("key", "key") { it.key },
        EventProperty("issueKind", "issue_kind") { it.issueKind },
        EventProperty("part", "part") { it.part },
        EventProperty("form", "form") { it.form },
        EventProperty("address", "address") { it.address },
        EventProperty("exceptionType", "exception_type") { it.exceptionType },
        EventProperty("principal", "principal") { it.principal },
        EventProperty("failure", "failure") { it.failure },
        EventProperty("errorCode", "error_code") { it.errorCode },
        EventProperty("cause", "cause") { it.cause },
        EventProperty("span", "span") { it.span },
        EventProperty("outcome", "outcome") { it.outcome },
        EventProperty("requestId", "request_id") { it.requestId },
        EventProperty("durationMs", "duration_ms") { it.durationMs },
        EventProperty("phase", "phase") { it.phase },
        EventProperty("requestKind", "request_kind") { it.requestKind },
        EventProperty("metrics", "metrics") { it.metrics },
    )

    /** Die Namen der Zugriffe von `TelemetryEvent`, die die Zeile abdeckt: Kopf (`kind`, `at`, `level`) und Tabelle. */
    val covered: Set<String> = setOf("kind", "at", "level") + PROPERTIES.map { it.property }

    /**
     * Der Wert des Feldes `event` je Art: der Name der Konstante in Kleinbuchstaben. Das `when` nennt jede Art und
     * hat kein `else`: eine neue Art bricht die Übersetzung, bis sie hier steht.
     */
    fun eventName(kind: EventKind): String = when (kind) {
        EventKind.SERVER_STARTED,
        EventKind.SERVER_STOPPED,
        EventKind.START_REFUSED,
        EventKind.CONFIG_INVALID,
        EventKind.TLS_START_REJECTED,
        EventKind.BIND_FAILED,
        EventKind.SECURITY_WEAKENED,
        EventKind.SIMULATION_BACKEND_READY,
        EventKind.ACCEPT_FAILED,
        EventKind.CONNECTION_FAILED,
        EventKind.LOGIN_ACCEPTED,
        EventKind.LOGIN_REJECTED,
        EventKind.ADAPTER_FAILED,
        EventKind.ENCRYPTION_NOT_OFFERED,
        EventKind.TLS_FIRST_NOT_OFFERED,
        EventKind.TLS_HANDSHAKE_FAILED,
        EventKind.REQUEST_FAILED,
        EventKind.METRICS,
        EventKind.SPAN_ENDED,
        -> kind.name.lowercase()
    }

    /** Die Felder des Ereignisses ohne Kopf, die nicht `null` sind, in der Reihenfolge der Tabelle. */
    fun fieldsOf(event: TelemetryEvent): List<JsonField> = PROPERTIES.flatMap { property ->
        when (val value = property.read(event)) {
            null -> emptyList()
            is Map<*, *> -> value.map { (key, number) -> JsonField("${property.json}.$key", number) }
            else -> listOf(JsonField(property.json, value))
        }
    }

    /** Die Zeile zu [event]; [dropped] größer null steht als letztes Feld `dropped`. */
    fun line(event: TelemetryEvent, dropped: Long = 0): String {
        val fields = fieldsOf(event) + if (dropped > 0) listOf(JsonField(DROPPED, dropped)) else emptyList()
        return line(event.at, event.level.name, eventName(event.kind), fields)
    }

    /** Die Zeile des Adapters für verlorene Zeilen: [dropped] Zeilen sind nicht geschrieben worden. */
    fun droppedLine(at: Instant, dropped: Long): String =
        line(at, LEVEL_WARN, DROPPED_EVENT, listOf(JsonField(DROPPED, dropped)))

    /** Die Zeile aus Kopf und Feldern; ein Feld mit dem Wert `null` entfällt. */
    fun line(at: Instant, level: String, event: String, fields: List<JsonField>): String {
        val out = StringWriter()
        factory.createGenerator(out).use { generator ->
            generator.writeStartObject()
            generator.writeStringField("ts", DateTimeFormatter.ISO_INSTANT.format(at))
            generator.writeStringField("level", level)
            generator.writeStringField("event", event)
            fields.forEach { write(generator, it) }
            generator.writeEndObject()
        }
        return out.toString()
    }

    private fun write(generator: JsonGenerator, field: JsonField) {
        when (val value = field.value) {
            null -> Unit
            is String -> generator.writeStringField(field.name, value)
            is Int -> generator.writeNumberField(field.name, value)
            is Long -> generator.writeNumberField(field.name, value)
            else -> error("Typ des Feldes ${field.name} nicht vorgesehen: ${value.javaClass.name}")
        }
    }

    private const val DROPPED = "dropped"
    private const val DROPPED_EVENT = "log_dropped"
    private const val LEVEL_WARN = "WARN"
}
