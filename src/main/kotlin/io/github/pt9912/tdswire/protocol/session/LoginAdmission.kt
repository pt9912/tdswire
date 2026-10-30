package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.AuthFeatures
import io.github.pt9912.tdswire.config.auth.AuthRequest
import io.github.pt9912.tdswire.config.auth.ConnectionContext
import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.protocol.login7.Login7Parser
import io.github.pt9912.tdswire.protocol.login7.Login7Request
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/** Das Urteil über ein LOGIN7, bevor der Adapter gerufen wird. */
internal sealed interface Admission {
    /** Das LOGIN7 ist gültig und trägt einen unterstützten Modus; der Adapter ist zu rufen. */
    class Admitted(val request: AuthRequest, val pending: PendingLogin) : Admission

    /** Abgelehnt, ohne den Adapter zu rufen. */
    data class Refused(val rejection: LoginRejection) : Admission

    /** Das LOGIN7 folgt nicht dem Format (ungültige Offsets oder Längen). */
    data object Malformed : Admission
}

/**
 * Prüft ein LOGIN7 vor dem Adapteraufruf (`ADR-0009`, `TDS-FA-02.a`): nicht unterstützte Modi
 * (`fIntSecurity`, FeatureExt-Option `0x02`, `fChangePassword`) und Eingaben über 128 UTF-16-Einheiten
 * werden abgelehnt, der Adapter wird dafür nicht gerufen. Festlegung ohne Messung: Die Grenze von
 * 128 Einheiten gilt auch für den Datenbanknamen (die Antwort trägt ihn in einem ENVCHANGE mit
 * höchstens 255 Einheiten); ein LOGIN7 mit ungültigen Offsets oder Längen ist [Admission.Malformed].
 * Die Passwörter des geparsten LOGIN7 sind nach dem Urteil überschrieben.
 */
internal object LoginAdmission {
    private const val ENVCHANGE_VALUE_UNITS = 128

    fun admit(payload: ByteArray, encryption: NegotiatedEncryption): Admission =
        when (val parsed = Login7Parser.parse(payload)) {
            is ProtocolResult.Failed -> Admission.Malformed
            is ProtocolResult.Ok -> decide(parsed.value, encryption)
        }

    /** Das Urteil über ein geparstes LOGIN7; danach sind Passwort und neues Passwort überschrieben. */
    fun decide(request: Login7Request, encryption: NegotiatedEncryption): Admission {
        val refusal = refusal(request)
        request.newPassword.wipe()
        if (refusal != null) {
            request.password.wipe()
            return Admission.Refused(refusal)
        }
        val fields = request.fields
        val authRequest = AuthRequest(
            userName = fields.userName,
            password = PasswordMapping.fromSecret(request.password),
            context = ConnectionContext(
                database = fields.database,
                applicationName = fields.appName,
                hostName = fields.hostName,
                readOnlyIntent = request.readOnlyIntent,
            ),
            encryption = encryption,
            features = AuthFeatures(
                integratedSecurity = request.integratedSecurity,
                federatedAuthentication = request.federatedAuthentication,
                changePassword = request.changePassword,
            ),
        )
        return Admission.Admitted(authRequest, PendingLogin(fields.database, request.header.packetSize))
    }

    private fun refusal(request: Login7Request): LoginRejection? = when {
        request.integratedSecurity -> LoginRejection.UNSUPPORTED_INTEGRATED_SECURITY
        request.federatedAuthentication -> LoginRejection.UNSUPPORTED_FEDERATED_AUTHENTICATION
        request.changePassword -> LoginRejection.UNSUPPORTED_PASSWORD_CHANGE
        tooLong(request) -> LoginRejection.INPUT_TOO_LONG
        else -> null
    }

    private fun tooLong(request: Login7Request): Boolean =
        request.fields.userName.length > AuthRequest.MAX_INPUT_UNITS ||
            request.password.length > AuthRequest.MAX_INPUT_UNITS ||
            request.fields.database.length > ENVCHANGE_VALUE_UNITS
}
