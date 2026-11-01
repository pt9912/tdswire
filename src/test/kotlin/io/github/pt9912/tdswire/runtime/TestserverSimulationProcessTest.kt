package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.config.ConfigKeys
import io.github.pt9912.tdswire.config.TempFilesTest
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.handMessage
import io.github.pt9912.tdswire.transport.listener.LOOPBACK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4
private const val DESCRIPTION_KEY = "SIMULATION_DESCRIPTION_FILE"

private val PERSON_FILE = """
    schema_format: "1.0"
    name: demo
    version: "1"
    tables:
      person:
        columns:
          id:
            type: identifier
          name:
            type: text
            max_length: 50
        primary_key: [id]
""".trimIndent() + "\n"

/**
 * Die Datei der Tabellenbeschreibung über die Prozessgrenze (`SPEC-035`, `SPEC-052`): der Testserver liest die Datei
 * aus der Umgebung, die Bibliothek beantwortet eine Auswahl der beschriebenen Tabelle über den Socket. Der Client
 * baut seine Bytes von Hand (kein Referenzclient).
 */
internal class TestserverSimulationProcessTest : TempFilesTest() {
    private fun environment(description: String = PERSON_FILE) = mapOf(
        ConfigKeys.LISTEN_HOST to LOOPBACK,
        ConfigKeys.LISTEN_PORT to "0",
        ConfigKeys.ENCRYPTION_POLICY to "TEST_UNENCRYPTED",
        ConfigKeys.AUTH_PLAIN_TESTMODE to "true",
        ConfigKeys.AUTH_USERS_FILE to file("users.txt", "alice\t{plain}secret\n"),
        DESCRIPTION_KEY to file("sim.yaml", description),
    )

    private fun ask(client: RawClient, sql: String): List<Token> {
        client.send(handMessage(BATCH_TYPE, BatchVectors.payload(sql), 4000))
        val reply = assertNotNull(client.readMessage(), sql)
        assertEquals(TABULAR_TYPE, reply.type, sql)
        return TokenDecoder(reply.payload).decodeAll()
    }

    /**
     * Eine Tabelle aus der Datei liefert Metadaten und ein leeres Ergebnis (eine geladene Tabelle hat eine leere
     * Zeilenquelle); ein Einfügen endet mit einem Fehler (keine Callbacks).
     *
     * @spec SPEC-035
     */
    @Test
    fun `eine Tabelle aus der Datei liefert Metadaten und ein leeres Ergebnis`() {
        ServerProcess(environment()).use { server ->
            RawClient(server.port()).use { client ->
                client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
                assertNotNull(client.readMessage())
                val payload = SessionVectors.login7Payload {
                    userName = "alice"
                    password = "secret".toCharArray()
                }
                client.send(handMessage(LOGIN7_TYPE, payload, 4000))
                assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)

                val select = ask(client, "SELECT id, name FROM person")
                val meta = assertIs<ColMetadataToken>(select.first())
                assertEquals(listOf("id", "name"), meta.columns.map { it.name })
                assertTrue(select.none { it is RowToken })
                assertEquals(0L, assertIs<DoneToken>(select.last()).rowCount)

                val insert = ask(client, "INSERT INTO person (id) VALUES (1)")
                assertTrue(insert.any { it is ErrorToken }, insert.toString())
            }
        }
    }

    /**
     * Eine Datei ohne Tabellen (`tables: {}`) ergibt ein Backend mit leerem Katalog: eine Anfrage endet mit einem
     * Fehler der Antwort und die Verbindung bleibt offen, statt ohne Bytes zu schließen (so verhielte sich ein Server
     * ohne Backend).
     *
     * @spec SPEC-035
     */
    @Test
    fun `eine Datei ohne Tabellen ergibt einen leeren Katalog`() {
        val empty = "schema_format: \"1.0\"\nname: leer\nversion: \"1\"\ntables: {}\n"
        ServerProcess(environment(empty)).use { server ->
            RawClient(server.port()).use { client ->
                client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
                assertNotNull(client.readMessage())
                val payload = SessionVectors.login7Payload {
                    userName = "alice"
                    password = "secret".toCharArray()
                }
                client.send(handMessage(LOGIN7_TYPE, payload, 4000))
                assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)

                val answer = ask(client, "SELECT id FROM person")
                assertTrue(answer.any { it is ErrorToken }, answer.toString())
            }
        }
    }
}
