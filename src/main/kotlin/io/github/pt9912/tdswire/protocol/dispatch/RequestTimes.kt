package io.github.pt9912.tdswire.protocol.dispatch

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Die Fristen und die Uhr der Anfrageschleife (`SPEC-009`, `SPEC-033`, `ADR-0013` Entscheidung 4).
 *
 * - [queryTimeout] (`QUERY_TIMEOUT`): die Frist einer Anfrage ab ihrem Beginn; der Protokollkern setzt
 *   `deadlineEpochMillis` auf Beginn plus diese Dauer und bricht bei Ablauf ab. `null` heißt keine Frist; eine
 *   Dauer, die beim Beginn schon abgelaufen ist (höchstens null), endet in `TDS_TIMEOUT`, ohne das Backend zu rufen.
 * - [cancelTimeout] (`CANCEL_TIMEOUT`): die Frist, die ein Backend für den Abbruch hat; danach schließt die Verbindung.
 * - [now] liefert die Wanduhr in Millisekunden seit der Epoche; sie wird einmal je Anfrage gelesen, um die Frist
 *   zu setzen, und ein zweites Mal, um sie in die Wartezeit umzurechnen.
 * - [pause] wartet eine Dauer ab; Tests ersetzen sie, um die Zeit zu steuern.
 */
class RequestTimes(
    val queryTimeout: Duration? = DEFAULT_QUERY_TIMEOUT,
    val cancelTimeout: Duration = DEFAULT_CANCEL_TIMEOUT,
    val now: () -> Long = System::currentTimeMillis,
    val pause: suspend (Duration) -> Unit = { delay(it) },
) {
    companion object {
        /** `QUERY_TIMEOUT` nach `SPEC-009` (vorläufig). */
        val DEFAULT_QUERY_TIMEOUT = 30.seconds

        /** `CANCEL_TIMEOUT` nach `SPEC-033` (vorläufig). */
        val DEFAULT_CANCEL_TIMEOUT = 5.seconds
    }
}
