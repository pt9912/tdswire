package io.github.pt9912.tdswire.simulation

import java.time.LocalDateTime

/** Die Zeitpunkt-Schreibweisen, die die Überführung eines Textes kennt (`SPEC-035`). */
internal object SimTemporal {
    private const val DATE_TIME_SEPARATOR_AT = 10

    /**
     * Der Zeitpunkt aus Text: ISO mit `T` (`yyyy-MM-dd'T'HH:mm:ss[.f]`) oder mit einem Leerzeichen an der Stelle des
     * `T` (`yyyy-MM-dd HH:mm:ss[.f]`, so sendet mssql-jdbc ihn im Massenladen; Messprotokoll des Spikes, b2). Das
     * Leerzeichen gilt hinter einem vierstelligen Jahr; Setzung, am Server nicht gemessen.
     */
    fun dateTime(value: String): LocalDateTime {
        val at = DATE_TIME_SEPARATOR_AT
        val iso = if (value.length > at && value[at] == ' ') value.replaceRange(at, at + 1, "T") else value
        return LocalDateTime.parse(iso)
    }
}
