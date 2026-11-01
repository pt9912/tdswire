package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.resultStart
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.testsupport.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4

/**
 * Antwortgrenzen nach einem Fehler über einen Socket auf `127.0.0.1` mit dem Rohbytes-Client und dem Test-Backend
 * (`TDS-QA-07`, `SPEC-031`): mehrere Anfragen gehen in einem Schreibvorgang hinaus, noch bevor die erste Antwort
 * gelesen ist. Das ist die Pipeline, die `ADR-0007` als nicht gemessen führt; der Referenzclient schickt höchstens
 * eine Anfrage zurzeit. Gemessen wird der Server dieses Repos (Anfrageschleife hinter dem Listener), nicht ein
 * echter SQL Server und nicht der Referenzclient. Die Tests hängen an der Folge der Antwortnachrichten, nicht an der
 * Paketierung.
 */
internal class SynchronitaetWiringTest : RuntimeTestBase() {
    private fun rowAnswer(value: Int) =
        listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(value), BatchVectors.DONE_COUNT_1).joinToString(" ")

    private fun oneRow(value: Int) = listOf(resultStart(), QueryEvent.Row(listOf(value)), QueryEvent.ResultEnd(1))

    private val rowsThenFailure = { code: String ->
        listOf(resultStart(), QueryEvent.Row(listOf(1)), QueryEvent.Row(listOf(2)), QueryEvent.Failure(code, "x"))
    }

    private fun environment() = loopbackEnvironment(file("users.txt", "alice\t{plain}secret\n"))

    private fun login(client: RawClient) {
        client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
        assertNotNull(client.readMessage())
        val payload = SessionVectors.login7Payload {
            userName = "alice"
            password = "secret".toCharArray()
        }
        client.send(handMessage(LOGIN7_TYPE, payload, 4000))
        assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
    }

    private fun batch(text: String) = handMessage(BATCH_TYPE, BatchVectors.payload(text), 4000)

    private fun RawClient.message(text: String): RawMessage {
        val message = assertNotNull(readMessage(), text)
        assertEquals(TABULAR_TYPE, message.type, text)
        return message
    }

    private fun RawClient.reply(text: String): String = message(text).payload.toHex()

    private fun names(tokens: List<Token>) = tokens.map { it::class.simpleName!!.removeSuffix("Token") }

    /**
     * Fehler der Class 16 nach Teilzeilen (`TDS_BACKEND_ERROR`, die Verbindung bleibt offen), und zwei weitere
     * Anfragen stehen schon im selben Schreibvorgang: die Antworten kommen in der Reihenfolge der Anfragen, die
     * erste endet mit dem DONE mit Fehlerstatus, die zweite, dritte und vierte tragen je eine andere Zeile (7, 8, 9)
     * des Backends und sind genau die Antwort ihrer Anfrage. Eine überzählige oder wiederholte Nachricht aus dem
     * Fehler verschöbe die folgenden Antworten und ließe mindestens eine dieser Zeilen am falschen Platz stehen.
     *
     * @spec SPEC-031
     * @spec TDS-QA-07
     */
    @Test
    fun `Anfragen hinter einem Fehler der Class 16 erhalten je genau ihre eigene Antwort`() = blocking {
        val backend = ScriptedBackend.inSequence(rowsThenFailure("TDS_BACKEND_ERROR"), oneRow(7), oneRow(8), oneRow(9))
        val server = running(environment(), Wiring(log = log, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("erste") + batch("zweite") + batch("dritte"))

            val failedMessage = client.message("erste")
            val failed = failedMessage.payload.toHex()
            assertTrue(failed.endsWith(BatchVectors.DONE_ERROR), failed)
            val tokens = TokenDecoder(failedMessage.payload).decodeAll()
            assertEquals(listOf("ColMetadata", "Row", "Row", "Error", "Done"), names(tokens))
            assertEquals(rowAnswer(7), client.reply("zweite"))
            assertEquals(rowAnswer(8), client.reply("dritte"))

            client.send(batch("vierte"))
            assertEquals(rowAnswer(9), client.reply("vierte"))
        }
        assertEquals(listOf("erste", "zweite", "dritte", "vierte"), backend.requests.map { it.sql })
        assertEquals(4, backend.requests.map { it.requestId }.toSet().size)
    }

    /**
     * Fehler der Class 20 (ein fremder Code ist `TDS_INTERNAL_ERROR`, die Verbindung schließt nach der Antwort), und
     * eine zweite Anfrage steht schon im selben Schreibvorgang: sie bekommt keine Antwort, auch nicht den Rest der
     * ersten, und erreicht das Backend nicht. Nach der Fehlerantwort kommt kein weiteres Byte. Grenze: Schließt der
     * Server mit ungelesenen Bytes der zweiten Anfrage im Empfangspuffer, kann das Betriebssystem die Verbindung
     * zurücksetzen und die Fehlerantwort beim Client verwerfen; dann scheitert das Lesen der ersten Antwort mit
     * "Connection reset" (aus dem Code geschlossen, in den Läufen dieses Slice nicht aufgetreten).
     *
     * @spec SPEC-031
     * @spec SPEC-048
     * @spec TDS-QA-07
     */
    @Test
    fun `Eine Anfrage hinter einem Fehler der Class 20 bleibt unbeantwortet und ohne Backend`() = blocking {
        val backend = ScriptedBackend.inSequence(rowsThenFailure("NOPE"), oneRow(7))
        val server = running(environment(), Wiring(log = log, requests = RequestSource(backend)))
        RawClient(server.port).use { client ->
            login(client)
            client.send(batch("erste") + batch("zweite"))

            val failed = client.reply("erste")
            assertTrue(failed.endsWith(BatchVectors.DONE_ERROR), failed)
            assertTrue(client.closedByServerWithoutBytes())
        }
        assertEquals(listOf("erste"), backend.requests.map { it.sql })
    }
}
