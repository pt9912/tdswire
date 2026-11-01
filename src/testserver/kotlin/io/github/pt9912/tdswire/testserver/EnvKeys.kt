package io.github.pt9912.tdswire.testserver

/**
 * Die Namen der Umgebungsvariablen des Testservers (`SPEC-005`, `SPEC-010`, `SPEC-035`, `SPEC-051`). Die Schlüssel,
 * die ein `ConfigIssue` der Bibliothek nennt, tragen denselben Text; ein Test des Testservers hält beide gleich.
 */
internal object EnvKeys {
    const val LISTEN_HOST = "LISTEN_HOST"
    const val LISTEN_PORT = "LISTEN_PORT"
    const val LOGIN_TIMEOUT = "LOGIN_TIMEOUT"
    const val ENCRYPTION_POLICY = "ENCRYPTION_POLICY"
    const val MAX_PRELOGIN_MESSAGE_BYTES = "MAX_PRELOGIN_MESSAGE_BYTES"
    const val TLS_KEYSTORE_FILE = "TLS_KEYSTORE_FILE"
    const val TLS_KEYSTORE_PASSWORD_FILE = "TLS_KEYSTORE_PASSWORD_FILE"
    const val AUTH_USERS_FILE = "AUTH_USERS_FILE"
    const val AUTH_PLAIN_TESTMODE = "AUTH_PLAIN_TESTMODE"
    const val SIMULATION_DESCRIPTION_FILE = "SIMULATION_DESCRIPTION_FILE"
    const val QUERY_TIMEOUT = "QUERY_TIMEOUT"
    const val CANCEL_TIMEOUT = "CANCEL_TIMEOUT"
}
