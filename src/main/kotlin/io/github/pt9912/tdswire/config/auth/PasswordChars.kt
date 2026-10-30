package io.github.pt9912.tdswire.config.auth

/**
 * Das Passwort eines Anmeldeversuchs als UTF-16-Einheiten. Die Textdarstellung nennt weder Inhalt
 * noch Länge (`TDS-QA-03`); [reveal] gibt eine Kopie, [wipe] überschreibt die gehaltenen Einheiten.
 * Festlegung ohne Messung: Das Überschreiben schützt nur dieses Feld, nicht den Paket-, Lese- oder
 * TLS-Puffer des Aufrufers und nicht jede Kopie der JVM (`ADR-0009`, Geheimnisschutz).
 * Der Konstruktor kopiert: Wer ein Passwort aus einem anderen Typ abbildet (der Anmeldepfad aus `SecretChars`),
 * überschreibt das Zwischenfeld und das Feld des Quelltyps selbst.
 */
class PasswordChars(chars: CharArray) {
    private val units: CharArray = chars.copyOf()

    val length: Int get() = units.size

    fun reveal(): CharArray = units.copyOf()

    fun wipe() = units.fill(WIPED)

    override fun toString(): String = "PasswordChars(verborgen)"

    private companion object {
        const val WIPED = '\u0000'
    }
}
