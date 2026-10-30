package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.PasswordChars
import io.github.pt9912.tdswire.protocol.login7.SecretChars

/**
 * Bildet das Passwort aus LOGIN7 ([SecretChars], Package `protocol.login7`) auf [PasswordChars]
 * (Package `config.auth`) ab. Der Konstruktor von [PasswordChars] kopiert; diese Abbildung
 * überschreibt deshalb das Zwischenfeld und das Feld des Quelltyps (`contracts/auth-adapter.md`,
 * `TDS-QA-03`). Festlegung ohne Messung: Das schützt nur diese beiden Felder, nicht den Paket- oder
 * Lesepuffer und nicht jede Kopie der JVM.
 */
internal object PasswordMapping {
    private const val WIPED = '\u0000'

    /** Das Passwort in [secret] als [PasswordChars]; [secret] und das Zwischenfeld sind danach überschrieben. */
    fun fromSecret(secret: SecretChars): PasswordChars {
        val intermediate = secret.reveal()
        try {
            return adopt(intermediate)
        } finally {
            secret.wipe()
        }
    }

    /** Übernimmt [units] in ein [PasswordChars] und überschreibt [units]. */
    fun adopt(units: CharArray): PasswordChars {
        try {
            return PasswordChars(units)
        } finally {
            units.fill(WIPED)
        }
    }
}
