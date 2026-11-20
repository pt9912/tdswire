package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.dispatch.BatchVectors
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.NullBitmapRowToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TABULAR_TYPE = 4
private const val RPC_TYPE = 0x03
private const val LENGTH = 10
private const val UNSUPPORTED_TYPE_NUMBER = 50003
private const val CLASS_CLOSING = 20

/**
 * Die Länge einer Textspalte im COLMETADATA einer Auswahl und der Text über der Länge auf dem Draht (`SPEC-035`,
 * `SPEC-003`, Rohbytes-Client): die Tabelle führt `Text` mit `maxLength` 10, `Frei` ohne Angabe und `Lang` mit -1.
 * Gemessen ist `nvarchar(10)` (Länge 20) am Referenzclient (Messprotokoll A-14); dass ein Text über der Länge in
 * einem Fehler der Antwort endet, ist Setzung, die Fehlernummer ist nicht gemessen.
 */
internal class SpaltenlaengeAuswahlWireTest : RuntimeTestBase() {
    private val content = listOf(
        listOf(1, "x".repeat(LENGTH), "frei", "lang"),
        listOf(2, "x".repeat(LENGTH + 1), "frei", "lang"),
        listOf(3, "x", "frei", "l".repeat(4000)),
    )

    private val table = SimTable(
        name = "kaputt",
        columns = listOf(
            SimColumn("Id", SqlType.Int32, nullable = false),
            SimColumn("Text", SqlType.UnicodeText, maxLength = LENGTH),
            SimColumn("Frei", SqlType.UnicodeText),
            SimColumn("Lang", SqlType.UnicodeText, maxLength = -1),
        ),
        primaryKey = listOf("Id"),
        rows = {
            var next = 0
            RowSource { content.getOrNull(next++) }
        },
    )

    private fun asked(sql: String, closes: ((Boolean) -> Unit)? = null): List<Token> =
        exchange(handMessage(BATCH_TYPE, BatchVectors.payload(sql), 4000), closes)

    /** Ein `sp_executesql` mit [statement] und dem Ganzzahl-Parameter `@P0` (der Weg des Referenzclients). */
    private fun askedWithParameter(statement: String, value: Int): List<Token> {
        val rpc = RpcWire.executeSql(statement, "@P0 int", RpcWire.param("@P0", RpcWire.int4(value)))
        return exchange(handMessage(RPC_TYPE, rpc, 4000), null)
    }

    private fun exchange(request: ByteArray, closes: ((Boolean) -> Unit)?): List<Token> {
        var answer: List<Token> = emptyList()
        blocking {
            val users = file("users.txt", "alice\t{plain}secret\n")
            val wiring = Wiring(telemetry = sink, requests = RequestSource(simulationTables = listOf(table)))
            val server = running(loopbackEnvironment(users), wiring)
            RawClient(server.port).use { client ->
                client.send(preloginWire(SessionVectors.ENCRYPT_OFF))
                assertNotNull(client.readMessage())
                val login = SessionVectors.login7Payload {
                    userName = "alice"
                    password = "secret".toCharArray()
                }
                client.send(handMessage(LOGIN7_TYPE, login, 4000))
                assertEquals(TABULAR_TYPE, assertNotNull(client.readMessage()).type)
                client.send(request)
                val reply = assertNotNull(client.readMessage())
                assertEquals(TABULAR_TYPE, reply.type)
                answer = TokenDecoder(reply.payload).decodeAll()
                closes?.invoke(client.closedByServerWithoutBytes())
            }
        }
        return answer
    }

    private fun lengths(tokens: List<Token>): List<List<Int>> =
        tokens.filterIsInstance<ColMetadataToken>().map { meta ->
            meta.columns.mapNotNull { (it.type as? TdsType.NVarcharType)?.maxChars }
        }

    private fun rowCount(tokens: List<Token>) = tokens.count { it is RowToken || it is NullBitmapRowToken }

    /**
     * Happy: das COLMETADATA einer Auswahl trägt die Länge der Beschreibung (10 Zeichen, auf dem Draht 20 Byte), die
     * Spalte ohne Angabe 4000 und die mit -1 ebenfalls 4000; beide Formen S1 und S2 und eine Bedingung, die nie wahr
     * ist, melden dieselben Längen. Wächter: ein `ResultStart` ohne Länge (alle drei 4000), eine Abbildung von -1 auf
     * 10 oder auf einen anderen Wert als 4000.
     * @spec SPEC-035
     * @spec SPEC-003
     */
    @Test
    fun `das COLMETADATA einer Auswahl traegt die Laenge der Beschreibung`() {
        val one = asked("SELECT text, frei, lang FROM kaputt WHERE id = 1")
        assertEquals(listOf(listOf(LENGTH, 4000, 4000)), lengths(one))
        assertEquals(1, rowCount(one))

        val two = asked("SELECT text FROM kaputt WHERE id = 1; SELECT frei FROM kaputt WHERE id = 1")
        assertEquals(listOf(listOf(LENGTH), listOf(4000)), lengths(two))

        val never = asked("SELECT text FROM kaputt WHERE 1 = 0")
        assertEquals(listOf(listOf(LENGTH)), lengths(never))
        assertEquals(0, rowCount(never))
    }

    /**
     * Happy: auch die Auswahl mit Parameter über `sp_executesql` (der Weg des Referenzclients) trägt die Länge der
     * Beschreibung im COLMETADATA (10, 4000, 4000 für `Text`, `Frei`, `Lang`) und liefert ihre Zeile. Wächter: ein
     * Weg für Auswahlen mit Parameter, der die Spalten ohne Länge baut.
     * @spec SPEC-035
     * @spec SPEC-003
     */
    @Test
    fun `die Auswahl mit Parameter ueber sp_executesql traegt die Laenge`() {
        val tokens = askedWithParameter("SELECT text, frei, lang FROM kaputt WHERE id = @P0", 1)

        assertEquals(listOf(listOf(LENGTH, 4000, 4000)), lengths(tokens))
        assertEquals(1, rowCount(tokens))
        assertTrue(tokens.none { it is ErrorToken }, tokens.toString())
    }

    /**
     * Boundary: ein Text von 4000 Zeichen in einer Spalte mit `maxLength` -1 ist ein Ergebnis (die Länge, die das
     * Ergebnis für `nvarchar(max)` meldet, ist 4000), ohne Fehler. Wächter: eine Abbildung von -1 auf weniger als 4000.
     * @spec SPEC-035
     * @spec SPEC-003
     */
    @Test
    fun `ein Text von 4000 Zeichen in einer Spalte mit minus eins ist ein Ergebnis`() {
        val tokens = asked("SELECT lang FROM kaputt WHERE id = 3")

        assertEquals(listOf(listOf(4000)), lengths(tokens))
        assertEquals(1, rowCount(tokens))
        assertTrue(tokens.none { it is ErrorToken }, tokens.toString())
    }

    /**
     * Boundary: ein Text genau in der Länge der Beschreibung (10 Zeichen) ist ein Ergebnis, ohne Fehler. Wächter: eine
     * Grenze, die einen Text der Länge schon abweist (Vergleich `>=` statt `>`).
     * @spec SPEC-003
     */
    @Test
    fun `ein Text genau in der Laenge ist ein Ergebnis`() {
        val tokens = asked("SELECT text FROM kaputt WHERE id = 1")

        assertEquals(1, rowCount(tokens))
        assertTrue(tokens.none { it is ErrorToken }, tokens.toString())
        assertEquals(0, tokens.filterIsInstance<DoneToken>().count { it.status and DoneStatus.ERROR != 0 })
    }

    /**
     * Negative: ein Text einer Zeile über der Länge der Beschreibung (11 Zeichen bei Länge 10) endet in einem Fehler
     * der Antwort statt im Ergebnis: das COLMETADATA kommt, ein ERROR `TDS_UNSUPPORTED_TYPE` (50003, Class 20,
     * `SPEC-014`), kein ROW für diese Zeile, der Abschluss trägt das Fehlerbit, und die Verbindung schließt. Dieselbe
     * Zeile in der Spalte `Frei` (Länge 4000) ist ein Ergebnis. Wächter: eine Länge, die das Backend nicht
     * durchreicht (die Zeile wäre ein Ergebnis in 4000), ein Fehler, der die Zeile still kürzt.
     * @spec SPEC-003
     * @spec SPEC-035
     */
    @Test
    fun `ein Text ueber der Laenge endet im Fehler der Antwort`() {
        var closed = false
        val tokens = asked("SELECT text FROM kaputt WHERE id = 2") { closed = it }

        assertEquals(listOf(listOf(LENGTH)), lengths(tokens))
        assertEquals(0, rowCount(tokens))
        val error = tokens.filterIsInstance<ErrorToken>().single().message
        assertEquals(UNSUPPORTED_TYPE_NUMBER to CLASS_CLOSING, error.number to error.severity, error.toString())
        assertTrue(closed, "die Verbindung schließt nach dem Fehler")
        assertTrue(tokens.filterIsInstance<DoneToken>().any { it.status and DoneStatus.ERROR != 0 }, tokens.toString())

        val free = asked("SELECT frei FROM kaputt WHERE id = 2")
        assertEquals(1, rowCount(free))
        assertTrue(free.none { it is ErrorToken }, free.toString())
    }
}
