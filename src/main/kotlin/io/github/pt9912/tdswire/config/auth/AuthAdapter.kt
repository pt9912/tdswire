package io.github.pt9912.tdswire.config.auth

import io.github.pt9912.tdswire.domain.Principal

/**
 * Die Schnittstelle zur Benutzerverwaltung nach `spec/spezifikation.md` (`SPEC-051`). Der
 * Adapter entscheidet allein über Annahme oder Ablehnung; Text und Nummer für den Client liefert er
 * nicht (`SPEC-012`).
 *
 * Festlegung ohne Messung: Der Aufruf blockiert und ist nicht `suspend`. Die Schnittstelle hängt so
 * an keiner Laufzeitbibliothek; der Aufrufer sorgt dafür, dass er den Transport-Thread nicht
 * blockiert und `LOGIN_TIMEOUT` (`SPEC-008`) einhält. Eine Ausnahme des Adapters behandelt der
 * Anmeldepfad wie [AuthResult.Rejected] (`SPEC-048`).
 */
interface AuthAdapter {
    fun authenticate(request: AuthRequest): AuthResult
}

/** Der ausgehandelte Verschlüsselungsmodus aus PRELOGIN. Festlegung ohne Messung (Entwurf des Vertrags). */
enum class NegotiatedEncryption { OFF, ON, NOT_SUPPORTED, REQUIRED }

/**
 * Werte aus LOGIN7; der Rechnername ist vom Client frei gesetzt und kein Beleg einer Herkunft. Die
 * Textdarstellung nennt sie nicht.
 */
class ConnectionContext(
    val database: String,
    val applicationName: String,
    val hostName: String,
    val readOnlyIntent: Boolean,
) {
    override fun toString(): String = "ConnectionContext(verborgen)"
}

/**
 * Die am LOGIN7 erkennbaren Authentifizierungsmerkmale. Die Einstiegsimplementierung wertet sie nicht
 * aus; der Anmeldepfad lehnt die nicht unterstützten Modi vor dem Adapter ab.
 */
data class AuthFeatures(
    val integratedSecurity: Boolean,
    val federatedAuthentication: Boolean,
    val changePassword: Boolean,
)

/**
 * Eingabe des Adapters. Benutzername und Passwort tragen je 0 bis [MAX_INPUT_UNITS] UTF-16-Einheiten
 * (auch leer, gemessen mit ASCII); der Anmeldepfad überschreibt das Passwort nach dem Aufruf. Die
 * Textdarstellung nennt keines der Felder.
 */
class AuthRequest(
    val userName: String,
    val password: PasswordChars,
    val context: ConnectionContext,
    val encryption: NegotiatedEncryption,
    val features: AuthFeatures,
) {
    override fun toString(): String = "AuthRequest(verborgen)"

    companion object {
        const val MAX_INPUT_UNITS = 128
    }
}

/** Gründe einer Ablehnung; sie dienen nur dem Server-Log und nennen weder Benutzername noch Passwort. */
enum class RejectReason { UNKNOWN_USER, WRONG_PASSWORD, INPUT_TOO_LONG }

/** Ergebnis des Adapters: Annahme mit [Principal] oder Ablehnung mit einem Grund nur für das Log. */
sealed interface AuthResult {
    class Accepted(val principal: Principal) : AuthResult {
        override fun toString(): String = "Accepted(verborgen)"
    }

    data class Rejected(val reason: RejectReason) : AuthResult
}
