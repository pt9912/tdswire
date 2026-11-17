package io.github.pt9912.tdswire.testserver

private const val LOOPBACK = "127.0.0.1"

/**
 * Ein Rohbytes-Client, der nur anmeldet: PRELOGIN ohne Verschlüsselung und LOGIN7 mit Benutzer und Passwort (MS-TDS
 * 2.2.6.4), ohne den Codec des Produkts und nur mit `java.net`. Er ist das Urteil darüber, ob der Server eine Anmeldung
 * annimmt, und braucht dafür keinen Typ der Bibliothek außerhalb ihrer öffentlichen Fläche. Die Bytes schreibt
 * [RohbytesClient] (Hauptquellen des Testservers), derselbe Client wie in der Abschluss-Messung.
 */
internal class LoginProbe(port: Int) : AutoCloseable {
    private val client = RohbytesClient(LOOPBACK, port)

    /** `true`, wenn der Server die Anmeldung mit LOGINACK annimmt; `false`, wenn er sie ablehnt. */
    fun login(user: String, password: String): Boolean = client.login(user, password)

    /** Sendet [sql] als SQL_BATCH und liest die Antwort bis zum Ende der Nachricht. */
    fun batch(sql: String) {
        client.batch(sql)
    }

    override fun close() = client.close()
}
