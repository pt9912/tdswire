package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.domain.telemetry.EventLevel

/**
 * `LOG_LEVEL`, ein Eingang des Testservers und nicht der Bibliothek (`ADR-0020`, `ADR-0025`; `SPEC-052`): `ERROR`,
 * `WARN`, `INFO` oder `DEBUG` in genau dieser Schreibweise, ohne Schlüssel `INFO`. Jeder andere Wert, auch ein leerer,
 * ist `INVALID_VALUE` und bricht den Start des Testservers ab (Setzung für den leeren Wert). Ein Ereignis wird
 * geschrieben, wenn seine Ebene nicht tiefer liegt als die gewählte: `ERROR` lässt nur `ERROR` durch, `DEBUG` alle.
 */
internal object LogLevelSetting {
    const val KEY = "LOG_LEVEL"

    /** Die Ebene ohne Schlüssel. */
    val DEFAULT = EventLevel.INFO

    /** Die gewählte Ebene; bei einem ungültigen Wert [DEFAULT], den Fehler meldet [issue]. */
    fun levelOf(environment: Map<String, String>): EventLevel =
        environment[KEY]?.let { value -> EventLevel.entries.firstOrNull { it.name == value } } ?: DEFAULT

    /** Der Fehler des Wertes, oder `null`, wenn der Schlüssel fehlt oder eine Ebene nennt. */
    fun issue(environment: Map<String, String>): ConfigIssue? =
        environment[KEY]?.takeIf { value -> EventLevel.entries.none { it.name == value } }
            ?.let { ConfigIssue(KEY, IssueKind.INVALID_VALUE) }

    /** Ob ein Ereignis der Ebene [event] bei der gewählten Ebene [chosen] geschrieben wird. */
    fun passes(event: EventLevel, chosen: EventLevel): Boolean = event.ordinal <= chosen.ordinal
}
