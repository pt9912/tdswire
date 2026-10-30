package io.github.pt9912.tdswire.transport.channel

/** Fehlerkategorie eines Kanals; der Kanal kennt nur den internen Fehler. */
enum class ChannelErrorCategory {
    TDS_INTERNAL_ERROR,
}

/** Grund, aus dem ein Kanal einen Lese- oder Schreibauftrag nicht ausführt. */
enum class ChannelFailure {
    /** Der Kanal ist lokal geschlossen. */
    CLOSED,

    /** Das Betriebssystem oder die Gegenstelle hat den Vorgang mit einem E/A-Fehler beendet. */
    IO_FAILURE,
}

/**
 * Ergebnisfehler eines Kanals. [detail] nennt den Ausnahmetyp, nie dessen Text und nie Nutzdaten.
 * Festlegung: jeder Grund hat die Kategorie `TDS_INTERNAL_ERROR` (`SPEC-048`); `SPEC-011` ordnet
 * Kanalfehler keinem Code zu, die Zuordnung legt das Fehler-Mapping fest.
 */
data class ChannelError(val failure: ChannelFailure, val detail: String) {
    val category: ChannelErrorCategory get() = ChannelErrorCategory.TDS_INTERNAL_ERROR
}

/**
 * Ausgang einer Kanal-Operation: ein Wert oder ein [ChannelError]. E/A-Fehler und das Schließen
 * sind nie eine Ausnahme, sondern [Failed]; Programmierfehler des Aufrufers (etwa ein zweiter
 * gleichzeitiger Lesevorgang, ein nicht verbundener Kanal, ein schreibgeschützter Puffer) können
 * als Ausnahme austreten.
 */
sealed interface ChannelResult<out T> {
    data class Ok<out T>(val value: T) : ChannelResult<T>

    data class Failed(val error: ChannelError) : ChannelResult<Nothing>
}
