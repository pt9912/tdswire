package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire
import io.github.pt9912.tdswire.protocol.session.SessionVectors
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.simulation.CatalogForm
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val TABULAR_TYPE = 4
private const val RPC_TYPE = 0x03

/**
 * Die Antwort auf die Spaltenabfrage von d-migrate 1.12.0 (`SPEC-035` „Katalog der Spalten“, `SPEC-036` Form K) auf
 * den Rohbytes: Typ, Länge und Nullbarkeit je Antwortspalte im COLMETADATA und die Werte der Zeilen. Die Erwartung
 * der Typen ist die Messung M-16 des Referenzabgleichs (`smallint`, `tinyint`, `bit` und `BITN`, `int`); die Antwort
 * kommt als `sp_executesql`-RPC wie dort.
 */
internal class KatalogSpaltenWireTest : RuntimeTestBase() {
    private val table = SimTable(
        name = "kunde",
        columns = listOf(
            SimColumn("Id", SqlType.Int32, nullable = false),
            SimColumn("Klein", SqlType.Int16),
            SimColumn("Winzig", SqlType.UInt8),
            SimColumn("Lang", SqlType.UnicodeText, maxLength = -1),
        ),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
    )

    private fun asked(): List<Token> {
        var tokens: List<Token> = emptyList()
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
                val statement = CatalogForm.COLUMNS.template.replace("?", " @P0 ")
                val parameter = RpcWire.param("@P0", RpcWire.nvarchar("dbo.kunde"))
                val rpc = RpcWire.executeSql(statement, "@P0 nvarchar(4000)", parameter)
                client.send(handMessage(RPC_TYPE, rpc, 4000))
                val reply = assertNotNull(client.readMessage())
                assertEquals(TABULAR_TYPE, reply.type)
                tokens = TokenDecoder(reply.payload).decodeAll()
            }
        }
        return tokens
    }

    /**
     * Happy: im COLMETADATA ist `max_length` der feste Typ `smallint`, `precision` und `scale` sind `tinyint` (alle
     * nicht nullbar), `is_nullable` und `is_computed` `BITN`, `is_identity` `bit` nicht nullbar und `column_id` `int`;
     * die Zeilen tragen die Maße (`int` 4/10/0, `smallint` 2/5/0, `tinyint` 1/3/0, `nvarchar(max)` −1/0/0) und die
     * Nullbarkeit der Spalte im Wert von `is_nullable`.
     * Wächter: `Int32` für `max_length` (Typkennung `38` statt `34`), `is_nullable` ohne NULL-Flag, ein Maß von
     * `Klein`, das nicht `smallint` entspricht.
     *
     * @spec SPEC-035
     * @spec SPEC-036
     */
    @Test
    fun `die Spaltenabfrage antwortet mit den gemessenen Drahttypen und den Massen`() {
        val tokens = asked()

        val meta = tokens.filterIsInstance<ColMetadataToken>().single().columns.associateBy { it.name }
        fun typeOf(name: String) = meta.getValue(name).let { it.type to it.nullable }
        assertEquals(TdsType.Int2Type to false, typeOf("max_length"))
        assertEquals(TdsType.Int1Type to false, typeOf("precision"))
        assertEquals(TdsType.Int1Type to false, typeOf("scale"))
        assertEquals(TdsType.BitNType to true, typeOf("is_nullable"))
        assertEquals(TdsType.BitType to false, typeOf("is_identity"))
        assertEquals(TdsType.BitNType to true, typeOf("is_computed"))
        assertEquals(TdsType.Int4Type to false, typeOf("column_id"))

        val rows = tokens.filterIsInstance<RowToken>().map { it.values }
        fun text(value: String) = TdsValue.TextValue(value)
        fun number(value: Long) = TdsValue.IntegerValue(value)
        assertEquals(
            listOf(
                listOf(text("int"), number(4), number(10), number(0)),
                listOf(text("smallint"), number(2), number(5), number(0)),
                listOf(text("tinyint"), number(1), number(3), number(0)),
                listOf(text("nvarchar"), number(-1), number(0), number(0)),
            ),
            rows.map { it.subList(1, 5) },
        )
        val no = TdsValue.BitValue(false)
        val yes = TdsValue.BitValue(true)
        assertEquals(listOf(no, no, no), rows[0].subList(5, 8))
        assertEquals(listOf(yes, no, no), rows[1].subList(5, 8))
    }
}
