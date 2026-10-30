package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.protocol.session.LogEntry

/**
 * Die Zeilen, die der Serverprozess über Start, Stop und Fehler schreibt (`TDS-FA-09.a`). Eine Zeile nennt
 * Schlüssel, Art eines Fehlers und feste Wörter, nie einen Konfigurationswert, einen Pfad, einen Benutzernamen,
 * einen Prüfwert oder ein Passwort. Strukturierte Logs, Metriken und Korrelations-IDs gehören `observability`.
 */
internal fun interface ServerLog {
    fun line(text: String)

    companion object {
        val NONE = ServerLog { }

        /** Schreibt jede Zeile mit dem Präfix `tdswire:` auf die Standardausgabe. */
        val STDOUT = ServerLog { println("tdswire: $it") }
    }
}

/** Die Zeile zu einem Eintrag des Handshake-Logs; sie trägt Art und Grund, nie Kennung oder Passwort. */
internal fun describe(entry: LogEntry): String = when (entry) {
    is LogEntry.LoginRejected -> "abgelehnt (${entry.rejection})"
    is LogEntry.AdapterFailed -> "Adapterfehler (${entry.exceptionType})"
    LogEntry.EncryptionNotOffered -> "Client bietet keine Verschluesselung an"
}
