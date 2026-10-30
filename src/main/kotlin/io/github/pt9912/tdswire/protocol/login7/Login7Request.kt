package io.github.pt9912.tdswire.protocol.login7

/**
 * Ein Geheimnis aus dem LOGIN7 (Passwort, neues Passwort) als UTF-16-Einheiten. Die Textdarstellung
 * nennt weder Inhalt noch Länge (`TDS-QA-03`); [reveal] gibt eine Kopie, [wipe] überschreibt die
 * gehaltenen Einheiten. Festlegung: Der Codec schützt nur dieses Feld, nicht den Paket- oder
 * Lesepuffer des Aufrufers (`ADR-0009`, Geheimnisschutz).
 */
class SecretChars(chars: CharArray) {
    private val units: CharArray = chars.copyOf()

    val length: Int get() = units.size

    fun reveal(): CharArray = units.copyOf()

    fun wipe() = units.fill(WIPED)

    override fun toString(): String = "SecretChars(verborgen)"

    private companion object {
        const val WIPED = '\u0000'
    }
}

/** Kopf des LOGIN7: Version, Paketgröße, Kennungen des Clients und die vier Flag-Bytes (MS-TDS 2.2.6.4). */
data class Login7Header(
    val tdsVersion: Int,
    val packetSize: Int,
    val clientProgVer: Int,
    val clientPid: Int,
    val connectionId: Int,
    val optionFlags1: Int,
    val optionFlags2: Int,
    val typeFlags: Int,
    val optionFlags3: Int,
    val clientTimeZone: Int,
    val clientLcid: Int,
)

/** Die Textfelder des LOGIN7 außer Passwörtern; die Textdarstellung nennt sie nicht, sie tragen die Kennung. */
data class Login7Fields(
    val hostName: String,
    val userName: String,
    val appName: String,
    val serverName: String,
    val cltIntName: String,
    val language: String,
    val database: String,
    val attachDbFile: String,
) {
    override fun toString(): String = "Login7Fields(verborgen)"
}

/**
 * Das Ergebnis des LOGIN7-Parsers: Felder und Merkmale, keine Entscheidung (`ADR-0009`). Die
 * Passwörter sind [SecretChars]. [sspiLength] ist die Länge des SSPI-Blocks, sein Inhalt wird nicht
 * gelesen. [featureIds] sind die Kennungen der FeatureExt-Optionen in der Reihenfolge der
 * Nachricht, ohne ihre Daten.
 */
class Login7Request(
    val header: Login7Header,
    val fields: Login7Fields,
    val password: SecretChars,
    val newPassword: SecretChars,
    val sspiLength: Long,
    val featureIds: List<Int>,
) {
    /** `fIntSecurity` (OptionFlags2 Bit 7): SSPI/Kerberos, im Entwurf von `ADR-0009` abgelehnt. */
    val integratedSecurity: Boolean get() = header.optionFlags2 and FLAG_INT_SECURITY != 0

    /** `fChangePassword` (OptionFlags3 Bit 0). */
    val changePassword: Boolean get() = header.optionFlags3 and FLAG_CHANGE_PASSWORD != 0

    /** `fExtension` (OptionFlags3 Bit 4): die Nachricht trägt einen FeatureExt-Block. */
    val hasFeatureExtension: Boolean get() = header.optionFlags3 and FLAG_EXTENSION != 0

    /** `fReadOnlyIntent` (TypeFlags Bit 5). */
    val readOnlyIntent: Boolean get() = header.typeFlags and FLAG_READ_ONLY_INTENT != 0

    /** FeatureExt-Option `0x02` (föderierte Authentifizierung, Entra-ID). */
    val federatedAuthentication: Boolean get() = FEATURE_FEDAUTH in featureIds

    override fun toString(): String = "Login7Request(tdsVersion=0x${Integer.toHexString(header.tdsVersion)}, " +
        "packetSize=${header.packetSize}, integratedSecurity=$integratedSecurity, " +
        "changePassword=$changePassword, federatedAuthentication=$federatedAuthentication)"

    companion object {
        const val FLAG_INT_SECURITY = 0x80
        const val FLAG_CHANGE_PASSWORD = 0x01
        const val FLAG_EXTENSION = 0x10
        const val FLAG_READ_ONLY_INTENT = 0x20
        const val FEATURE_FEDAUTH = 0x02
    }
}
