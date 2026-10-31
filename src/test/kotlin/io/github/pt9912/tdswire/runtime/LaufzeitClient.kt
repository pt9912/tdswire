package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.handMessage
import kotlin.system.exitProcess

private const val PACKET_BYTES = 4000
private const val TDS_RESPONSE = 4

/**
 * Der Rohbytes-Client des Laufzeittests (`make laufzeittest`, Dockerfile-Stage `laufzeit-client`): verbindet
 * sich mit `TDSWIRE_ZIEL` (`host:port`), spricht PRELOGIN und LOGIN7 und schreibt bei LOGINACK die Zeile
 * `laufzeit-client: Login ok`. Jeder andere Ausgang endet mit Exit-Code 1. Er baut seine Bytes von Hand und
 * ist kein Referenzclient; belegt ist die Antwort des Servers auf diese Bytes.
 */
fun main() {
    val target = checkNotNull(System.getenv("TDSWIRE_ZIEL")) { "TDSWIRE_ZIEL fehlt" }
    val user = System.getenv("TDSWIRE_BENUTZER") ?: "alice"
    val password = System.getenv("TDSWIRE_PASSWORT") ?: "secret"
    val ok = RawClient(target.substringAfterLast(':').toInt(), target.substringBeforeLast(':')).use { client ->
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        val prelogin = client.readMessage()
        val login = SessionVectors.login7Payload {
            userName = user
            this.password = password.toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, login, PACKET_BYTES))
        val answer = client.readMessage()
        prelogin?.type == TDS_RESPONSE && answer?.type == TDS_RESPONSE &&
            TokenDecoder(answer.payload).decodeAll().any { it is LoginAck }
    }
    if (!ok) {
        println("laufzeit-client: Login fehlgeschlagen")
        exitProcess(1)
    }
    println("laufzeit-client: Login ok")
}
