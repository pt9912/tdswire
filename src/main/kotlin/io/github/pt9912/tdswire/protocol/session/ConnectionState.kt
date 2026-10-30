package io.github.pt9912.tdswire.protocol.session

/**
 * Zustände des Verbindungsautomaten (`SPEC-001`, `SPEC-028`). Festlegung ohne Messung: eine flache
 * Aufzählung wie im Spike; `ADR-0004` führt den Zuschnitt (flach oder getrennt nach Verbindungs- und
 * Anfragezustand) als offen, die Zweiteilung nach `SPEC-001` ist nicht gemessen. [AUTHENTICATING] ist
 * der Zustand, in dem der Adapteraufruf läuft und kein weiteres LOGIN7 angenommen wird. [READY] ist
 * der login-seitige Endzustand; der Anfragezustand gehört nicht zu dieser Aufzählung.
 */
enum class ConnectionState {
    CONNECTED,
    PRELOGIN,
    TLS_NEGOTIATION,
    LOGIN7,
    AUTHENTICATING,
    READY,
    CLOSED,
}

/**
 * Die Art eines Ereignisses; die Übergangsmatrix ist über (Zustand, Art) definiert (`ADR-0004`:
 * Übergangsfunktion über Zustand und Ereignisart). Festlegung ohne Messung: Die Zuordnung der
 * Pakettypen zu den Arten steht in [HandshakeEvent.Message].
 */
enum class EventKind {
    START,
    TLS_ESTABLISHED,
    PRELOGIN_MESSAGE,
    LOGIN7_MESSAGE,
    REQUEST_MESSAGE,
    ATTENTION_MESSAGE,
    OTHER_MESSAGE,
    AUTH_ACCEPTED,
    AUTH_REJECTED,
    LOGIN_TIMEOUT,
    PEER_CLOSED,
    READ_FAILED,
}

/**
 * Reihenfolge von TLS und PRELOGIN (`SPEC-001`, `SPEC-028`). [PRELOGIN_FIRST] ist der TDS-7.x-Pfad
 * (PRELOGIN vor TLS, die Aushandlung folgt der PRELOGIN-Antwort); [TLS_FIRST] ist der TDS-8.0-Pfad
 * (TLS vor PRELOGIN). Die Reihenfolge von TDS 8.0 ist Festlegung (`ADR-0001`).
 */
enum class HandshakeOrder { PRELOGIN_FIRST, TLS_FIRST }

/**
 * Verschlüsselungs-Richtlinie (`SPEC-010`, `ADR-0002`). [REQUIRED] ist der Standard;
 * [TEST_UNENCRYPTED] ist der ausdrücklich konfigurierte unverschlüsselte Testmodus (nur
 * [HandshakeOrder.PRELOGIN_FIRST]).
 */
enum class EncryptionPolicy { REQUIRED, TEST_UNENCRYPTED }

/**
 * Parameter des Automaten. [defaultDatabase] ist der Datenbankkontext einer Sitzung, deren LOGIN7
 * keine Datenbank nennt; Festlegung ohne Messung.
 */
data class HandshakeConfig(
    val order: HandshakeOrder = HandshakeOrder.PRELOGIN_FIRST,
    val encryptionPolicy: EncryptionPolicy = EncryptionPolicy.REQUIRED,
    val defaultDatabase: String = DEFAULT_DATABASE,
) {
    init {
        require(order == HandshakeOrder.PRELOGIN_FIRST || encryptionPolicy == EncryptionPolicy.REQUIRED) {
            "der unverschluesselte Testmodus gilt nur in der Reihenfolge PRELOGIN_FIRST (SPEC-028)"
        }
    }

    companion object {
        const val DEFAULT_DATABASE = "master"
    }
}
