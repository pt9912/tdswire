package io.github.pt9912.tdswire.domain.telemetry

/** Die Verschlüsselungs-Richtlinie in der wirksamen Konfiguration; die Namen sind die der Konfiguration. */
internal enum class EncryptionMode { REQUIRED, TEST_UNENCRYPTED }

/** Ob Schlüsselmaterial konfiguriert ist; Pfad und Inhalt kennt das Modell nicht. */
internal enum class KeystoreState { SET, UNSET }

/**
 * Die wirksame Konfiguration eines Starts (`TDS-FA-09.a`, `ADR-0040`). Die Felder sind ausschließlich Zahlen,
 * Aufzählungen und `Boolean`: ein Pfad, der Inhalt eines Schlüsselspeichers, ein Passwort oder ein Eintrag der
 * Benutzerliste hat hier keinen Typ, in dem er stehen könnte. [asMap] ist die einzige Ableitung der Abbildung von
 * Schlüssel auf Wert, die `TelemetryEvent.settings` öffentlich liest; die Schlüssel sind die Namen der Konfiguration
 * (`ConfigKeys`, ein Test hält beide gleich). Eine neue Einstellung ist ein Feld hier und ein Eintrag in [asMap].
 */
internal data class EffectiveSettings(
    val loginTimeoutSeconds: Int,
    val maxPreloginMessageBytes: Int,
    val encryption: EncryptionMode,
    val keystore: KeystoreState,
    val plainTestMode: Boolean,
    val queryTimeoutSeconds: Int,
    val cancelTimeoutSeconds: Int,
    val shutdownGraceSeconds: Int,
    val maxConnections: Int,
    val maxPreparedHandles: Int,
    val maxMessageBytes: Int,
) {
    /** Schlüssel und Wert in fester Reihenfolge; der Wert ist die Zahl, der Name der Aufzählung oder `true`/`false`. */
    fun asMap(): Map<String, String> = linkedMapOf(
        "LOGIN_TIMEOUT" to loginTimeoutSeconds.toString(),
        "MAX_PRELOGIN_MESSAGE_BYTES" to maxPreloginMessageBytes.toString(),
        "ENCRYPTION_POLICY" to encryption.name,
        "TLS_KEYSTORE_FILE" to keystore.name,
        "AUTH_PLAIN_TESTMODE" to plainTestMode.toString(),
        "QUERY_TIMEOUT" to queryTimeoutSeconds.toString(),
        "CANCEL_TIMEOUT" to cancelTimeoutSeconds.toString(),
        "SHUTDOWN_GRACE" to shutdownGraceSeconds.toString(),
        "MAX_CONNECTIONS" to maxConnections.toString(),
        "MAX_PREPARED_HANDLES" to maxPreparedHandles.toString(),
        "MAX_MESSAGE_BYTES" to maxMessageBytes.toString(),
    )
}
