package io.github.pt9912.tdswire.domain.telemetry

import java.time.Instant

/** Obergrenze jedes Zeichenketten-Feldes eines Ereignisses in UTF-16-Einheiten (Setzung, nicht gemessen). */
internal const val MAX_TEXT_LENGTH = 256

/** Kürzt [text] auf [MAX_TEXT_LENGTH] Einheiten; ein abgeschnittenes Paar aus Ersatzzeichen entfällt ganz. */
internal fun bounded(text: String): String {
    if (text.length <= MAX_TEXT_LENGTH) return text
    val cut = text.substring(0, MAX_TEXT_LENGTH)
    return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}

/**
 * Die Werte von [TelemetryEvent.clientProfile]. Die Menge ist geschlossen und stammt nur aus dem ausgehandelten
 * Protokoll, nie aus Angaben des Clients in LOGIN7 (`TDS-FA-09.a`).
 */
internal object ClientProfile {
    /** Vor der Aushandlung, oder wenn der Aufrufer sie nicht kennt. */
    const val UNKNOWN = "unknown"

    /** TLS ab dem ersten Byte (TDS 8.0). */
    const val TDS_8_0 = "tds-8.0:strict"

    /** TDS 7.4 mit dem ausgehandelten Verschlüsselungsmodus [mode] in Kleinbuchstaben. */
    fun tds74(mode: String): String = "tds-7.4:$mode"
}

/** Was ein Ereignis zu einer Verbindung weiß; ein Ereignis ohne Verbindung trägt [NONE]. */
internal data class EventContext(
    val connectionId: Long? = null,
    val sessionId: String? = null,
    val clientProfile: String = ClientProfile.UNKNOWN,
) {
    companion object {
        val NONE = EventContext()
    }
}

/**
 * Ein Ereignis des Servers (`TDS-FA-09.a`, `SPEC-059`). Die Menge der Ereignisarten ist geschlossen; jede Art trägt
 * typisierte Felder und keinen Freitext. Für Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext hat
 * keine Art ein Feld; eine Ausnahme erscheint nur als Typname. Jedes Zeichenketten-Feld ist auf 256 UTF-16-Einheiten
 * gekürzt. Die Bibliothek erzeugt Ereignisse; für Kotlin-Aufrufer sind die Konstruktoren `internal`. Aus Java sind
 * sie im Bytecode öffentlich und aufrufbar (die Grenze von `internal`); die Längengrenze gilt dort weiter, ein Java-
 * Aufrufer kann nur Ereignisse für die eigene Senke bauen. `TelemetrySink.NONE` ist aus Java nur über
 * `TelemetrySink.Companion.getNONE()` erreichbar.
 *
 * Gemeinsame Felder: [at] ist der Zeitpunkt aus dem Zeitgeber der Bibliothek, [level] ist je Art fest. [connectionId]
 * ist der Zähler des Listeners (ab 1 je Server) und fehlt bei Ereignissen ohne Verbindung. [sessionId] ist die
 * zufällige UUID der Sitzung und fehlt vor der Annahme der Anmeldung. [clientProfile] ist `unknown`,
 * `tds-8.0:strict` oder `tds-7.4:<mode>` mit `<mode>` aus `off`, `on`, `not_supported`, `required`; bei einer
 * Verbindung im Pfad TDS 7.4 vor der Annahme der Anmeldung ist es `unknown`. Eine Anfrage-Kennung trägt noch kein
 * Ereignis.
 */
public sealed class TelemetryEvent protected constructor(
    public val at: Instant,
    public val level: EventLevel,
    context: EventContext = EventContext.NONE,
) {
    public val connectionId: Long? = context.connectionId
    public val sessionId: String? = context.sessionId
    public val clientProfile: String = context.clientProfile
}

/** Warum ein Start vor dem Binden abbricht, ohne dass Konfiguration oder TLS-Material die Ursache nennen. */
public enum class StartRefusal {
    /** Ein Backend und Tabellen des Simulations-Backends sind zugleich konfiguriert. */
    BACKEND_AND_SIMULATION,

    /** Ein Tabellenname kommt doppelt vor. */
    TABLE_NAME_DUPLICATE,
}

/** Die Grund-Marke einer abgelehnten Anmeldung; sie nennt nie Kennung oder Eingabe (`SPEC-012`). */
public enum class LoginRejectionReason {
    UNKNOWN_USER,
    WRONG_PASSWORD,
    INPUT_TOO_LONG,
    UNSUPPORTED_INTEGRATED_SECURITY,
    UNSUPPORTED_FEDERATED_AUTHENTICATION,
    UNSUPPORTED_PASSWORD_CHANGE,
    ADAPTER_FAILURE,
}

/** Warum der Server TLS ab dem ersten Byte nicht anbietet. */
public enum class TlsFirstRefusal {
    /** Der Server hat kein TLS-Material. */
    NO_TLS_MATERIAL,

    /** Die Richtlinie der Verschlüsselung ist nicht `REQUIRED`. */
    POLICY_NOT_REQUIRED,
}

/** Der Server läuft und ist an [port] gebunden. Ebene `INFO`. */
public class ServerStarted internal constructor(at: Instant, public val port: Int) :
    TelemetryEvent(at, EventLevel.INFO)

/** Der Server ist beendet; genau ein Ereignis je Server. Ebene `INFO`. */
public class ServerStopped internal constructor(at: Instant) : TelemetryEvent(at, EventLevel.INFO)

/** Der Start bricht aus dem Grund [reason] ab. Ebene `ERROR`. */
public class StartRefused internal constructor(at: Instant, public val reason: StartRefusal) :
    TelemetryEvent(at, EventLevel.ERROR)

/**
 * Ein Konfigurationsfehler beim Start: [key] nennt das Konfigurationsfeld, [kind] die Art des Fehlers; nie der Wert.
 * Ein Start mit mehreren Fehlern erzeugt je Fehler ein Ereignis. Ebene `ERROR`.
 */
public class ConfigInvalid internal constructor(at: Instant, key: String, kind: String) :
    TelemetryEvent(at, EventLevel.ERROR) {
    public val key: String = bounded(key)
    public val kind: String = bounded(kind)
}

/**
 * Die Startprobe oder das Laden der TLS-Konfiguration ist gescheitert (`SPEC-043`): [part] nennt den fehlerhaften
 * Teil, [form] die gefundene Form als feste Beschreibung oder Ausnahmetyp; nie Pfad, Inhalt oder Ausnahmetext.
 * Ebene `ERROR`.
 */
public class TlsStartRejected internal constructor(at: Instant, part: String, form: String) :
    TelemetryEvent(at, EventLevel.ERROR) {
    public val part: String = bounded(part)
    public val form: String = bounded(form)
}

/**
 * Das Binden an [address] und [port] ist gescheitert (`SPEC-043`). [exceptionType] ist der Typname der Ausnahme
 * oder `null`, wenn der Port belegt ist. Ebene `ERROR`.
 */
public class BindFailed internal constructor(
    at: Instant,
    address: String,
    public val port: Int,
    exceptionType: String?,
) : TelemetryEvent(at, EventLevel.ERROR) {
    public val address: String = bounded(address)
    public val exceptionType: String? = exceptionType?.let(::bounded)
}

/** Die Verschlüsselung ist abgeschwächt: `ENCRYPTION_POLICY` ist `TEST_UNENCRYPTED` (`SPEC-010`). Ebene `WARN`. */
public class EncryptionWeakened internal constructor(at: Instant) : TelemetryEvent(at, EventLevel.WARN)

/** Das Verfahren `plain` ist zugelassen: `AUTH_PLAIN_TESTMODE` ist aktiv. Ebene `WARN`. */
public class PlainTestModeActive internal constructor(at: Instant) : TelemetryEvent(at, EventLevel.WARN)

/** Das Simulations-Backend ist mit [tables] Tabellen verdrahtet. Ebene `DEBUG`. */
public class SimulationBackendReady internal constructor(at: Instant, public val tables: Int) :
    TelemetryEvent(at, EventLevel.DEBUG)

/** Annehmen einer Verbindung scheiterte, der Listener läuft weiter; [exceptionType] ist der Typname. Ebene `ERROR`. */
public class AcceptFailed internal constructor(at: Instant, exceptionType: String) :
    TelemetryEvent(at, EventLevel.ERROR) {
    public val exceptionType: String = bounded(exceptionType)
}

/** Der Handler einer Verbindung hat eine Ausnahme geworfen; [exceptionType] ist ihr Typname. Ebene `ERROR`. */
public class ConnectionFailed internal constructor(at: Instant, context: EventContext, exceptionType: String) :
    TelemetryEvent(at, EventLevel.ERROR, context) {
    public val exceptionType: String = bounded(exceptionType)
}

/**
 * Die Anmeldung ist angenommen. [principal] ist die Kennung aus dem Authentifizierungsadapter und steht in keinem
 * anderen Ereignis. Für die mitgelieferte Benutzerliste stammt sie aus der Liste des Betreibers; dass die Kennung
 * eines eigenen Adapters frei von Geheimnissen ist, sagt die Bibliothek nicht zu. Ebene `INFO`.
 */
public class LoginAccepted internal constructor(at: Instant, context: EventContext, principal: String) :
    TelemetryEvent(at, EventLevel.INFO, context) {
    public val principal: String = bounded(principal)
}

/** Die Anmeldung ist abgelehnt; [reason] ist eine feste Marke, ein Feld für die Kennung fehlt. Ebene `WARN`. */
public class LoginRejected internal constructor(
    at: Instant,
    context: EventContext,
    public val reason: LoginRejectionReason,
) : TelemetryEvent(at, EventLevel.WARN, context)

/** Der Authentifizierungsadapter hat eine Ausnahme geworfen; [exceptionType] ist ihr Typname. Ebene `ERROR`. */
public class AdapterFailed internal constructor(at: Instant, context: EventContext, exceptionType: String) :
    TelemetryEvent(at, EventLevel.ERROR, context) {
    public val exceptionType: String = bounded(exceptionType)
}

/** Der Client bietet im PRELOGIN keine Verschlüsselung an, die Richtlinie verlangt sie. Ebene `WARN`. */
public class EncryptionNotOffered internal constructor(at: Instant, context: EventContext) :
    TelemetryEvent(at, EventLevel.WARN, context)

/** Der Client beginnt mit TLS, der Server bietet TLS ab dem ersten Byte aus [reason] nicht an. Ebene `WARN`. */
public class TlsFirstNotOffered internal constructor(
    at: Instant,
    context: EventContext,
    public val reason: TlsFirstRefusal,
) : TelemetryEvent(at, EventLevel.WARN, context)

/** Der TLS-Handschlag einer Verbindung ist gescheitert; [failure] ist die Art, nie der Text. Ebene `WARN`. */
public class TlsHandshakeFailed internal constructor(at: Instant, context: EventContext, failure: String) :
    TelemetryEvent(at, EventLevel.WARN, context) {
    public val failure: String = bounded(failure)
}

/**
 * Eine Anfrage ist mit dem Fehler [errorCode] (`TDS_*`) beantwortet worden. [cause] ist der Typname der Ausnahme oder
 * eine feste Marke des Anlasses, sonst `null`; nie der Text einer Ausnahme und nie ein Wert. Ebene `WARN`.
 */
public class RequestFailed internal constructor(at: Instant, context: EventContext, errorCode: String, cause: String?) :
    TelemetryEvent(at, EventLevel.WARN, context) {
    public val errorCode: String = bounded(errorCode)
    public val cause: String? = cause?.let(::bounded)
}
