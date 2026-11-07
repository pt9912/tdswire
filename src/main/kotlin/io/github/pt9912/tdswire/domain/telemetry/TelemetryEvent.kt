package io.github.pt9912.tdswire.domain.telemetry

import java.time.Instant
import java.util.Collections

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
 * Die Art eines Ereignisses (`TDS-FA-09.a`, `SPEC-059`). Die Menge ist geschlossen; die Ebene ([TelemetryEvent.level])
 * ist je Art fest und steht an der Konstante. Welche Felder eine Art füllt, steht ebenfalls an der Konstante; ein
 * Feld, das die Art nicht trägt, ist `null`.
 *
 * Ein `when` über die Art ist vollständig, solange die Menge dieselbe bleibt; jede neue Art bricht ein `when` des
 * Einbettenden bei der Übersetzung.
 */
public enum class EventKind {
    /**
     * Der Server läuft; [TelemetryEvent.address] ist die konfigurierte Bind-Adresse, [TelemetryEvent.port] der
     * gebundene Port. Ebene `INFO`.
     */
    SERVER_STARTED,

    /**
     * Der Server ist beendet; höchstens ein Ereignis je Server, nach dem Ende jeder Verbindung. Bricht der Aufruf von
     * `TdsServer.stop` ab, bevor das Beenden fertig ist, kommt es erst mit dem nächsten Aufruf. Ebene `INFO`.
     */
    SERVER_STOPPED,

    /**
     * Der Start bricht vor dem Binden ab, ohne dass Konfiguration oder TLS-Material die Ursache nennen;
     * [TelemetryEvent.reason] ist `BACKEND_AND_SIMULATION` oder `TABLE_NAME_DUPLICATE`. Ebene `ERROR`.
     */
    START_REFUSED,

    /**
     * Ein Konfigurationsfehler beim Start; [TelemetryEvent.key] nennt das Konfigurationsfeld,
     * [TelemetryEvent.issueKind] die Art des Fehlers, nie der Wert. Ein Start mit mehreren Fehlern erzeugt je Fehler
     * ein Ereignis. Ebene `ERROR`.
     */
    CONFIG_INVALID,

    /**
     * Die Startprobe oder das Laden der TLS-Konfiguration ist gescheitert (`SPEC-043`); [TelemetryEvent.part] nennt
     * den fehlerhaften Teil, [TelemetryEvent.form] die gefundene Form als feste Beschreibung oder Ausnahmetyp, nie
     * Pfad, Inhalt oder Ausnahmetext. Ebene `ERROR`.
     */
    TLS_START_REJECTED,

    /**
     * Das Binden ist gescheitert (`SPEC-043`); [TelemetryEvent.address] und [TelemetryEvent.port] nennen das Ziel,
     * [TelemetryEvent.exceptionType] den Typnamen der Ausnahme oder `null`, wenn der Port belegt ist. Ebene `ERROR`.
     */
    BIND_FAILED,

    /**
     * Eine Abschwächung der Sicherheit ist aktiv; [TelemetryEvent.reason] ist `ENCRYPTION_TEST_UNENCRYPTED`
     * (`ENCRYPTION_POLICY` ist `TEST_UNENCRYPTED`, `SPEC-010`) oder `PLAIN_TEST_MODE` (`AUTH_PLAIN_TESTMODE` ist
     * aktiv, das Verfahren `plain` ist zugelassen). Ebene `WARN`.
     */
    SECURITY_WEAKENED,

    /** Das Simulations-Backend ist verdrahtet; [TelemetryEvent.tables] ist die Zahl der Tabellen. Ebene `DEBUG`. */
    SIMULATION_BACKEND_READY,

    /** Annehmen scheiterte, der Listener läuft weiter; [TelemetryEvent.exceptionType]. Ebene `ERROR`. */
    ACCEPT_FAILED,

    /** Der Handler einer Verbindung hat eine Ausnahme geworfen; [TelemetryEvent.exceptionType]. Ebene `ERROR`. */
    CONNECTION_FAILED,

    /**
     * Die Anmeldung ist angenommen; [TelemetryEvent.principal] ist die Kennung aus dem Authentifizierungsadapter und
     * steht in keiner anderen Art. Für die mitgelieferte Benutzerliste stammt sie aus der Liste des Betreibers; dass
     * die Kennung eines eigenen Adapters frei von Geheimnissen ist, sagt die Bibliothek nicht zu. Ebene `INFO`.
     */
    LOGIN_ACCEPTED,

    /**
     * Die Anmeldung ist abgelehnt; [TelemetryEvent.reason] ist eine feste Marke (`UNKNOWN_USER`, `WRONG_PASSWORD`,
     * `INPUT_TOO_LONG`, `UNSUPPORTED_INTEGRATED_SECURITY`, `UNSUPPORTED_FEDERATED_AUTHENTICATION`,
     * `UNSUPPORTED_PASSWORD_CHANGE`, `ADAPTER_FAILURE`), ein Feld für die Kennung fehlt. Ebene `WARN`.
     */
    LOGIN_REJECTED,

    /** Der Authentifizierungsadapter hat eine Ausnahme geworfen; [TelemetryEvent.exceptionType]. Ebene `ERROR`. */
    ADAPTER_FAILED,

    /** Der Client bietet im PRELOGIN keine Verschlüsselung an, die Richtlinie verlangt sie. Ebene `WARN`. */
    ENCRYPTION_NOT_OFFERED,

    /**
     * Der Client beginnt mit TLS, der Server bietet TLS ab dem ersten Byte nicht an; [TelemetryEvent.reason] ist
     * `NO_TLS_MATERIAL` oder `POLICY_NOT_REQUIRED`. Ebene `WARN`.
     */
    TLS_FIRST_NOT_OFFERED,

    /** Der TLS-Handschlag ist gescheitert; [TelemetryEvent.failure] ist die Art, nie der Text. Ebene `WARN`. */
    TLS_HANDSHAKE_FAILED,

    /**
     * Eine Anfrage ist mit dem Fehler [TelemetryEvent.errorCode] (`TDS_*`) beantwortet worden;
     * [TelemetryEvent.cause] ist der Typname der Ausnahme oder eine feste Marke des Anlasses, sonst `null`, nie der
     * Text einer Ausnahme und nie ein Wert. Ebene `WARN`.
     */
    REQUEST_FAILED,

    /**
     * Die Konfiguration, mit der der Server läuft; [TelemetryEvent.settings] nennt Schlüssel und Wert, nie Pfad,
     * Prüfwert, Passwort oder Benutzerliste. Genau ein Ereignis je Start, vor [SERVER_STARTED];
     * beide kommen nach dem Binden, ein Client auf einem festen Port kann davor [CONNECTION_OPENED] auslösen
     * (Reihenfolge dort nicht gemessen). Ebene `INFO`.
     */
    CONFIG_EFFECTIVE,

    /**
     * Das geordnete Beenden beginnt (`TdsServer.stop`); wird `stop` gerufen, genau ein Ereignis je Server, vor dem Ende
     * jeder Verbindung; ein nie beendeter Server meldet keines.
     * Ebene `INFO`.
     */
    SERVER_STOPPING,

    /**
     * Der Listener hat eine Verbindung angenommen; auch eine, die er sofort abweist ([EventKind.CONNECTION_CLOSED]
     * mit `REFUSED_AT_LIMIT` oder `REFUSED_AT_SHUTDOWN`). [TelemetryEvent.connectionId] zählt je Server. Ebene `INFO`.
     */
    CONNECTION_OPENED,

    /**
     * Eine Verbindung ist beendet, genau ein Ereignis je Verbindung; [TelemetryEvent.reason] ist die Art des Endes
     * (`HANDLER_RETURNED`, `HANDLER_FAILED`, `LOGIN_TIMEOUT`, `SHUTDOWN`, `SHUTDOWN_GRACE_EXPIRED`,
     * `REFUSED_AT_SHUTDOWN`, `REFUSED_AT_LIMIT`). Das Ereignis trägt keine Sitzung und das Profil `unknown`: der
     * Listener kennt beides nicht. Ebene `INFO`.
     */
    CONNECTION_CLOSED,
}

/** Warum ein Start vor dem Binden abbricht, ohne dass Konfiguration oder TLS-Material die Ursache nennen. */
internal enum class StartRefusal {
    /** Ein Backend und Tabellen des Simulations-Backends sind zugleich konfiguriert. */
    BACKEND_AND_SIMULATION,

    /** Ein Tabellenname kommt doppelt vor. */
    TABLE_NAME_DUPLICATE,
}

/** Wie eine Verbindung endete; die Namen sind die Marken in [TelemetryEvent.reason] von `CONNECTION_CLOSED`. */
internal enum class ConnectionCloseReason {
    /** Der Handler ist zurückgekehrt, auch nach dem Trennen durch die Gegenstelle. */
    HANDLER_RETURNED,

    /** Der Handler hat eine Ausnahme geworfen (die Art steht in `CONNECTION_FAILED`). */
    HANDLER_FAILED,

    /** `LOGIN_TIMEOUT` ist abgelaufen, bevor die Anmeldung abgeschlossen war. */
    LOGIN_TIMEOUT,

    /** Das geordnete Beenden hat die Verbindung beendet. */
    SHUTDOWN,

    /** `SHUTDOWN_GRACE` ist abgelaufen und die Verbindung wurde getrennt. */
    SHUTDOWN_GRACE_EXPIRED,

    /** Die Verbindung wurde im Moment des Beginns des Beendens angenommen und sofort geschlossen. */
    REFUSED_AT_SHUTDOWN,

    /** Die Verbindung wurde über `MAX_CONNECTIONS` hinaus angenommen und sofort geschlossen. */
    REFUSED_AT_LIMIT,
}

/** Welche Abschwächung der Sicherheit aktiv ist. */
internal enum class WeakeningReason {
    /** `ENCRYPTION_POLICY` ist `TEST_UNENCRYPTED` (`SPEC-010`). */
    ENCRYPTION_TEST_UNENCRYPTED,

    /** `AUTH_PLAIN_TESTMODE` ist aktiv: das Verfahren `plain` ist zugelassen. */
    PLAIN_TEST_MODE,
}

/** Die Grund-Marke einer abgelehnten Anmeldung; sie nennt nie Kennung oder Eingabe (`SPEC-012`). */
internal enum class LoginRejectionReason {
    UNKNOWN_USER,
    WRONG_PASSWORD,
    INPUT_TOO_LONG,
    UNSUPPORTED_INTEGRATED_SECURITY,
    UNSUPPORTED_FEDERATED_AUTHENTICATION,
    UNSUPPORTED_PASSWORD_CHANGE,
    ADAPTER_FAILURE,
}

/** Warum der Server TLS ab dem ersten Byte nicht anbietet. */
internal enum class TlsFirstRefusal {
    /** Der Server hat kein TLS-Material. */
    NO_TLS_MATERIAL,

    /** Die Richtlinie der Verschlüsselung ist nicht `REQUIRED`. */
    POLICY_NOT_REQUIRED,
}

/**
 * Ein Ereignis des Servers (`TDS-FA-09.a`, `SPEC-059`) und die einzige öffentliche Ereignisklasse. Die Art steht in
 * [kind]; die Menge der Arten ist geschlossen und trägt keinen Freitext. Die Felder jeder Art stehen an den Konstanten
 * von [EventKind]; ein Feld, das die Art nicht trägt, ist `null`. Für Passwort, Prüfwert, Parameterwert, SQL-Text,
 * Pfad und Ausnahmetext hat keine Art ein Feld; eine Ausnahme erscheint nur als Typname. Jedes Zeichenketten-Feld ist
 * auf 256 UTF-16-Einheiten gekürzt. Die Bibliothek erzeugt Ereignisse; für Kotlin-Aufrufer sind die Unterklassen und
 * ihre Konstruktoren `internal`. Aus Java sind sie im Bytecode öffentlich und aufrufbar (die Grenze von `internal`);
 * die Längengrenze gilt dort weiter, ein Java-Aufrufer kann nur Ereignisse für die eigene Senke bauen.
 * `TelemetrySink.NONE` ist aus Java nur über `TelemetrySink.Companion.getNONE()` erreichbar.
 *
 * Gemeinsame Felder: [at] ist der Zeitpunkt aus dem Zeitgeber der Bibliothek, [level] ist je Art fest. [connectionId]
 * ist der Zähler des Listeners (ab 1 je Server) und fehlt bei Ereignissen ohne Verbindung. [sessionId] ist die
 * zufällige UUID der Sitzung und fehlt vor der Annahme der Anmeldung. [clientProfile] ist `unknown`,
 * `tds-8.0:strict` oder `tds-7.4:<mode>` mit `<mode>` aus `off`, `on`, `not_supported`, `required`; bei einer
 * Verbindung im Pfad TDS 7.4 vor der Annahme der Anmeldung ist es `unknown`. Eine Anfrage-Kennung trägt noch kein
 * Ereignis.
 *
 * Die Felder der Arten sind in dieser Klasse Zugriffe, die `null` liefern; die internen Unterklassen überschreiben
 * die Zugriffe ihrer Art.
 */
public sealed class TelemetryEvent protected constructor(
    public val kind: EventKind,
    public val at: Instant,
    public val level: EventLevel,
    context: EventContext = EventContext.NONE,
) {
    public val connectionId: Long? = context.connectionId
    public val sessionId: String? = context.sessionId
    public val clientProfile: String = context.clientProfile

    /** Der Port: gebunden ([EventKind.SERVER_STARTED]) oder verfehlt ([EventKind.BIND_FAILED]). */
    public open val port: Int? get() = null

    /** Die Zahl der Tabellen ([EventKind.SIMULATION_BACKEND_READY]). */
    public open val tables: Int? get() = null

    /** Die feste Marke des Grundes, wo [EventKind] sie nennt. */
    public open val reason: String? get() = null

    /** Das Konfigurationsfeld ([EventKind.CONFIG_INVALID]). */
    public open val key: String? get() = null

    /** Die Art des Konfigurationsfehlers ([EventKind.CONFIG_INVALID]). */
    public open val issueKind: String? get() = null

    /** Der fehlerhafte Teil der TLS-Konfiguration ([EventKind.TLS_START_REJECTED]). */
    public open val part: String? get() = null

    /** Die gefundene Form der TLS-Konfiguration ([EventKind.TLS_START_REJECTED]). */
    public open val form: String? get() = null

    /** Die Bind-Adresse ([EventKind.BIND_FAILED]). */
    public open val address: String? get() = null

    /** Der Typname einer Ausnahme, wo [EventKind] ihn nennt. */
    public open val exceptionType: String? get() = null

    /** Die Kennung der angenommenen Anmeldung ([EventKind.LOGIN_ACCEPTED]). */
    public open val principal: String? get() = null

    /** Die Art des Handschlag-Fehlers ([EventKind.TLS_HANDSHAKE_FAILED]). */
    public open val failure: String? get() = null

    /** Der `TDS_*`-Code der Antwort ([EventKind.REQUEST_FAILED]). */
    public open val errorCode: String? get() = null

    /** Typname oder feste Marke des Anlasses ([EventKind.REQUEST_FAILED]). */
    public open val cause: String? get() = null

    /**
     * Die wirksamen Einstellungen als Schlüssel und Wert ([EventKind.CONFIG_EFFECTIVE]). Die Werte sind Zahlen, Namen
     * von Aufzählungen, `true` oder `false`; für den Schlüsselspeicher steht nur `SET` oder `UNSET`, nie ein Pfad.
     */
    public open val settings: Map<String, String>? get() = null
}

internal class ServerStarted(at: Instant, address: String, override val port: Int) :
    TelemetryEvent(EventKind.SERVER_STARTED, at, EventLevel.INFO) {
    override val address: String = bounded(address)
}

internal class ServerStopped(at: Instant) : TelemetryEvent(EventKind.SERVER_STOPPED, at, EventLevel.INFO)

internal class ServerStopping(at: Instant) : TelemetryEvent(EventKind.SERVER_STOPPING, at, EventLevel.INFO)

internal class ConfigEffective(at: Instant, settings: EffectiveSettings) :
    TelemetryEvent(EventKind.CONFIG_EFFECTIVE, at, EventLevel.INFO) {
    override val settings: Map<String, String> = Collections.unmodifiableMap(settings.asMap())
}

internal class ConnectionOpened(at: Instant, context: EventContext) :
    TelemetryEvent(EventKind.CONNECTION_OPENED, at, EventLevel.INFO, context)

internal class ConnectionClosed(at: Instant, context: EventContext, close: ConnectionCloseReason) :
    TelemetryEvent(EventKind.CONNECTION_CLOSED, at, EventLevel.INFO, context) {
    override val reason: String = close.name
}

internal class StartRefused(at: Instant, refusal: StartRefusal) :
    TelemetryEvent(EventKind.START_REFUSED, at, EventLevel.ERROR) {
    override val reason: String = refusal.name
}

internal class ConfigInvalid(at: Instant, key: String, kind: String) :
    TelemetryEvent(EventKind.CONFIG_INVALID, at, EventLevel.ERROR) {
    override val key: String = bounded(key)
    override val issueKind: String = bounded(kind)
}

internal class TlsStartRejected(at: Instant, part: String, form: String) :
    TelemetryEvent(EventKind.TLS_START_REJECTED, at, EventLevel.ERROR) {
    override val part: String = bounded(part)
    override val form: String = bounded(form)
}

internal class BindFailed(at: Instant, address: String, override val port: Int, exceptionType: String?) :
    TelemetryEvent(EventKind.BIND_FAILED, at, EventLevel.ERROR) {
    override val address: String = bounded(address)
    override val exceptionType: String? = exceptionType?.let(::bounded)
}

internal class SecurityWeakened(at: Instant, weakening: WeakeningReason) :
    TelemetryEvent(EventKind.SECURITY_WEAKENED, at, EventLevel.WARN) {
    override val reason: String = weakening.name
}

internal class SimulationBackendReady(at: Instant, override val tables: Int) :
    TelemetryEvent(EventKind.SIMULATION_BACKEND_READY, at, EventLevel.DEBUG)

internal class AcceptFailed(at: Instant, exceptionType: String) :
    TelemetryEvent(EventKind.ACCEPT_FAILED, at, EventLevel.ERROR) {
    override val exceptionType: String = bounded(exceptionType)
}

internal class ConnectionFailed(at: Instant, context: EventContext, exceptionType: String) :
    TelemetryEvent(EventKind.CONNECTION_FAILED, at, EventLevel.ERROR, context) {
    override val exceptionType: String = bounded(exceptionType)
}

internal class LoginAccepted(at: Instant, context: EventContext, principal: String) :
    TelemetryEvent(EventKind.LOGIN_ACCEPTED, at, EventLevel.INFO, context) {
    override val principal: String = bounded(principal)
}

internal class LoginRejected(at: Instant, context: EventContext, rejection: LoginRejectionReason) :
    TelemetryEvent(EventKind.LOGIN_REJECTED, at, EventLevel.WARN, context) {
    override val reason: String = rejection.name
}

internal class AdapterFailed(at: Instant, context: EventContext, exceptionType: String) :
    TelemetryEvent(EventKind.ADAPTER_FAILED, at, EventLevel.ERROR, context) {
    override val exceptionType: String = bounded(exceptionType)
}

internal class EncryptionNotOffered(at: Instant, context: EventContext) :
    TelemetryEvent(EventKind.ENCRYPTION_NOT_OFFERED, at, EventLevel.WARN, context)

internal class TlsFirstNotOffered(at: Instant, context: EventContext, refusal: TlsFirstRefusal) :
    TelemetryEvent(EventKind.TLS_FIRST_NOT_OFFERED, at, EventLevel.WARN, context) {
    override val reason: String = refusal.name
}

internal class TlsHandshakeFailed(at: Instant, context: EventContext, failure: String) :
    TelemetryEvent(EventKind.TLS_HANDSHAKE_FAILED, at, EventLevel.WARN, context) {
    override val failure: String = bounded(failure)
}

internal class RequestFailed(at: Instant, context: EventContext, errorCode: String, cause: String?) :
    TelemetryEvent(EventKind.REQUEST_FAILED, at, EventLevel.WARN, context) {
    override val errorCode: String = bounded(errorCode)
    override val cause: String? = cause?.let(::bounded)
}
