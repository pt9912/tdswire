package io.github.pt9912.tdswire.config.auth

/** Die Art eines Fehlers in der Benutzerliste; je Abweichung genau eine (`ADR-0011`). */
enum class UserListErrorKind {
    EMPTY_LIST,
    EMPTY_LINE,
    CARRIAGE_RETURN,
    MISSING_TAB,
    EMPTY_USER_NAME,
    INVALID_USER_NAME,
    DUPLICATE_USER_NAME,
    MISSING_SCHEME,
    UNKNOWN_SCHEME,
    PLAIN_NOT_ALLOWED,
    FIELD_COUNT,
    SALT_INVALID,
    SALT_TOO_SHORT,
    ITERATIONS_INVALID,
    ITERATIONS_BELOW_FLOOR,
    ITERATIONS_ABOVE_CEILING,
    HASH_INVALID,
}

/**
 * Ein Fehler der Benutzerliste: Art und Zeilennummer der Datei (1-basiert), `null` bei einer Liste
 * ohne Zeile. Er trägt weder Prüfwert noch Benutzername noch Passwort (`TDS-QA-03`).
 */
data class UserListError(val kind: UserListErrorKind, val line: Int?)

/** Ergebnis der Validierung: eine gültige Liste oder der erste Fehler in Dateireihenfolge. */
sealed interface UserListResult {
    class Valid(val list: UserList) : UserListResult {
        override fun toString(): String = "Valid(verborgen)"
    }

    data class Invalid(val error: UserListError) : UserListResult
}

/** Ein Eintrag der Liste; die Textdarstellung nennt Benutzername und Prüfwert nicht. */
internal class UserEntry(val name: String, val secret: StoredSecret) {
    override fun toString(): String = "UserEntry(verborgen)"
}

/**
 * Die validierte feste Benutzerliste (`SPEC-051`, `ADR-0011`). Sie entsteht nur über [parse]; eine
 * leere Liste gibt es nicht. Die Textdarstellung nennt keinen Inhalt.
 *
 * Festlegungen (Setzungen, nicht gemessen):
 * - Der erste Fehler in Dateireihenfolge bricht die Prüfung ab; weitere Fehler werden nicht gesucht.
 * - Kommentare gibt es nicht, eine Zeile mit `#` ist ein gewöhnlicher Eintrag.
 * - Eine leere Zeile ist ein Fehler; nur das Zeilenende am Dateiende ist erlaubt.
 * - Zeilenende ist LF; ein CR irgendwo in einer Zeile (also auch CRLF) ist ein Fehler.
 * - Der Iterationsboden gilt auch im Testmodus.
 * - Der Hash hat genau [Pbkdf2Limits.HASH_BYTES] Byte, das Salz mindestens [Pbkdf2Limits.MIN_SALT_BYTES].
 * - Die Iterationen sind eine Ziffernfolge von 1 bis 8 Stellen ohne führende Null.
 * - Benutzernamen werden zeichengenau verglichen, ohne Groß-/Kleinschreibung und ohne Normalisierung.
 * - Dateirechte und Übergabe der Datei prüft diese Klasse nicht; das Lesen der Datei gehört der Konfiguration.
 *
 * Entschieden (Nutzer, 2026-10-30): Testmodus-Boden (derselbe Boden wie sonst), keine Kommentare und keine
 * Leerzeilen, kein CR (CRLF nicht toleriert), Hash genau 32 Byte und Salz mindestens 16 Byte ohne
 * Obergrenze, Ziffernfolge 1 bis 8 Stellen ohne führende Null. Die Werte sind Setzungen, nicht gemessen.
 *
 * Scheinvergleich: Die Attrappe für einen unbekannten Benutzernamen ist der `pbkdf2`-Eintrag mit der höchsten
 * Iterationszahl (bei Gleichstand der erste in Dateireihenfolge), mit Salz- und Hashlänge dieses Eintrags;
 * gibt es nur `plain`-Einträge, ist es der erste Eintrag (keine Ableitung). Verbleibende Grenze bei gemischten
 * Listen: Ein bekannter Benutzer mit niedrigerer Iterationszahl oder mit `plain`-Prüfwert durchläuft einen
 * anderen Rechenschritt als die Attrappe; die Struktur ist dort nicht gleich. Gemessen ist nichts; über Zeit
 * sagt diese Bauart nichts.
 */
class UserList private constructor(private val byName: Map<String, UserEntry>, private val decoyEntry: UserEntry) {
    internal fun find(name: String): UserEntry? = byName[name]

    /** Eintrag für den Scheinvergleich bei unbekanntem Benutzernamen (Festlegung ohne Messung). */
    internal val decoy: UserEntry get() = decoyEntry

    override fun toString(): String = "UserList(verborgen)"

    companion object {
        /** Prüft den Dateiinhalt [text]; [plainTestMode] erlaubt das Verfahren `plain`. */
        fun parse(text: String, plainTestMode: Boolean): UserListResult = UserListParser(plainTestMode).parse(text)

        /** Der Testmodus gilt nur bei genau `true` (`ADR-0011`, `AUTH_PLAIN_TESTMODE`). */
        fun plainTestModeFrom(environmentValue: String?): Boolean = environmentValue == "true"

        internal fun of(entries: List<UserEntry>): UserList =
            UserList(entries.associateBy { it.name }, decoyOf(entries))

        private fun decoyOf(entries: List<UserEntry>): UserEntry = entries.filter { it.secret is StoredSecret.Pbkdf2 }
            .maxByOrNull { (it.secret as StoredSecret.Pbkdf2).iterations }
            ?: entries.first()
    }
}
