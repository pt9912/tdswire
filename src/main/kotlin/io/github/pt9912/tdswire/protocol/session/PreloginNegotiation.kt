package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.protocol.prelogin.EncryptionMode
import io.github.pt9912.tdswire.protocol.prelogin.PreloginCodec
import io.github.pt9912.tdswire.protocol.prelogin.PreloginMessage
import io.github.pt9912.tdswire.protocol.prelogin.PreloginOption
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/** Das Ergebnis der PRELOGIN-Verhandlung. */
internal sealed interface Negotiation {
    /** Die Antwort [response] steht fest; [tlsFollows] sagt, ob danach der TLS-Handschlag beginnt. */
    class Agreed(val response: TdsMessage, val encryption: NegotiatedEncryption, val tlsFollows: Boolean) :
        Negotiation

    /** Die Anforderung des Clients und die Richtlinie sind unvereinbar, oder die Nachricht ist ungültig. */
    class Failed(val reason: CloseReason) : Negotiation
}

/**
 * Die PRELOGIN-Antwort aus der Verschlüsselungs-Richtlinie (`SPEC-010`, `SPEC-028`, `ADR-0002`).
 *
 * Gemessen am Spike-Testserver mit dem Referenzclient (`ADR-0002`): Der Handschlag gelingt bei ENCRYPT_REQ
 * mit `Encrypt=true` wie mit `Encrypt=false`; ENCRYPT_ON gegen einen Client mit `Encrypt=false` scheiterte
 * serverseitig im Testserver. Festlegung ohne Messung: [EncryptionPolicy.REQUIRED] antwortet ENCRYPT_REQ; ein Client
 * mit ENCRYPT_NOT_SUP, ohne ENCRYPTION-Option oder mit unbekanntem Wert ist unvereinbar; im Testmodus
 * antwortet der Server ENCRYPT_NOT_SUP und nimmt nur Clients mit ENCRYPT_OFF oder ENCRYPT_NOT_SUP an;
 * bei Unvereinbarkeit schließt der Server vor LOGIN7 ohne Antwort (kein stilles Downgrade). In der
 * Reihenfolge [HandshakeOrder.TLS_FIRST] besteht TLS schon, die ENCRYPTION-Option des Clients wird
 * nicht ausgewertet, die Antwort ist ENCRYPT_REQ ohne folgenden Handschlag. Die übrigen Optionen der
 * Antwort (VERSION `0.0.0.0`, leerer INSTOPT, MARS aus) sind Festlegung; FEDAUTHREQUIRED sendet der
 * Server nicht, weil er föderierte Authentifizierung nicht unterstützt. Die Antwort trägt den
 * Pakettyp TABULAR_RESULT (Festlegung, MS-TDS-Lesart).
 */
internal class PreloginNegotiation(private val config: HandshakeConfig) {
    fun negotiate(payload: ByteArray): Negotiation {
        if (payload.size > FramingLimits.MAX_PRELOGIN_MESSAGE_BYTES) {
            return Negotiation.Failed(CloseReason.MESSAGE_TOO_LARGE)
        }
        return when (val parsed = PreloginCodec.parse(payload)) {
            is ProtocolResult.Failed -> Negotiation.Failed(CloseReason.PROTOCOL_VIOLATION)
            is ProtocolResult.Ok -> decide(parsed.value)
        }
    }

    private fun decide(request: PreloginMessage): Negotiation = when {
        config.order == HandshakeOrder.TLS_FIRST -> agreed(EncryptionMode.REQUIRED, tlsFollows = false)

        config.encryptionPolicy == EncryptionPolicy.REQUIRED ->
            if (request.encryption in REQUIRED_ACCEPTS) {
                agreed(EncryptionMode.REQUIRED, tlsFollows = true)
            } else {
                Negotiation.Failed(CloseReason.ENCRYPTION_INCOMPATIBLE)
            }

        request.encryption in TEST_ACCEPTS -> agreed(EncryptionMode.NOT_SUPPORTED, tlsFollows = false)

        else -> Negotiation.Failed(CloseReason.ENCRYPTION_INCOMPATIBLE)
    }

    private fun agreed(mode: Int, tlsFollows: Boolean): Negotiation {
        val message = PreloginMessage(
            listOf(
                PreloginOption.version(0, 0, 0, 0),
                PreloginOption.encryption(mode),
                PreloginOption.instance(""),
                PreloginOption.mars(false),
            ),
        )
        val encoded = PreloginCodec.encode(message)
        check(encoded is ProtocolResult.Ok) { "PRELOGIN-Antwort nicht schreibbar" }
        val encryption = if (mode == EncryptionMode.REQUIRED) {
            NegotiatedEncryption.REQUIRED
        } else {
            NegotiatedEncryption.NOT_SUPPORTED
        }
        return Negotiation.Agreed(TdsMessage(PacketType.TABULAR_RESULT, encoded.value), encryption, tlsFollows)
    }

    private companion object {
        val REQUIRED_ACCEPTS = setOf(EncryptionMode.OFF, EncryptionMode.ON, EncryptionMode.REQUIRED)
        val TEST_ACCEPTS = setOf(EncryptionMode.OFF, EncryptionMode.NOT_SUPPORTED)
    }
}
