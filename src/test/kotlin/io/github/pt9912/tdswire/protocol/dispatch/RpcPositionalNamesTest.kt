package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.byName
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int4
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int8
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.protocol.rpc.RpcParser
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `sp_executesql` mit nur dem Text und mit Wertparametern ohne Namen, so wie mssql-jdbc 13.4.0 sie sendet
 * (`SPEC-030`). Die beiden Vektoren [TEXT_ONLY] und [POSITIONAL] sind Aufzeichnungen: die Nutzlast des RPC, wie
 * sie d-migrate 1.12.0 (`ghcr.io/pt9912/d-migrate@sha256:308f2775…`, Treiber mssql-jdbc 13.4.0.jre11) am
 * 2026-11-05 an einen Testserver sendete (`data export --tables kunden` und
 * `data export --tables kunden --filter "Id = 7"`), als Hex aus einem Mitschnitt der Nutzlast vor dem Parser.
 * Der Name `@P<i>` ist eine Setzung; gemessen ist, dass die Werte ohne Namen ankommen und die Deklaration
 * `@P0 bigint` sie so nennt.
 */
class RpcPositionalNamesTest {
    private fun query(payload: ByteArray): RpcBinding.Query = assertIs<RpcBinding.Query>(RpcBinder.bind(payload))

    private fun rejection(payload: ByteArray): RpcRejection =
        assertIs<RpcBinding.Rejected>(RpcBinder.bind(payload)).reason

    /**
     * Aufzeichnung: ein Parameter, der Text `SELECT * FROM [kunden] WHERE 1 = 0`, keine Deklaration.
     *
     * @spec SPEC-030
     */
    @Test
    fun `sp_executesql nur mit dem Text wird gebunden`() {
        val bound = query(hex(TEXT_ONLY))
        assertEquals("SELECT * FROM [kunden] WHERE 1 = 0", bound.sql)
        assertEquals(emptyList(), bound.parameters)
    }

    /**
     * Aufzeichnung: Text mit `@P0`, Deklaration `@P0 bigint` und ein Wertparameter `INTN` 8 ohne Namen mit dem Wert 7;
     * er heißt `@P0`, Typ `Int64`, Richtung `INPUT`; der Text kommt unverändert an.
     *
     * @spec SPEC-030
     */
    @Test
    fun `ein Wertparameter ohne Namen heisst P0`() {
        val bound = query(hex(POSITIONAL))
        assertEquals("SELECT * FROM [kunden] WHERE [Id] =  @P0 ", bound.sql)
        assertEquals(listOf(QueryParameter("@P0", SqlType.Int64, 7L, ParameterDirection.INPUT)), bound.parameters)
    }

    /**
     * Grenze: der Name folgt der Stelle in der Reihenfolge der Wertparameter, nicht der Stelle in der Nachricht
     * (Text und Deklaration zählen nicht): drei Werte heißen `@P0`, `@P1`, `@P2`.
     * Wächter: ein Zähler, der bei 1 beginnt oder die zwei festen Parameter mitzählt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `die Stelle bestimmt den Namen`() {
        val bound = query(executeSql("s", "d", param("", int4(1)), param("", int8(2)), param("", int4(3))))
        assertEquals(listOf("@P0", "@P1", "@P2"), bound.parameters.map { it.name })
        assertEquals(listOf<Any?>(1, 2L, 3), bound.parameters.map { it.value })
    }

    /**
     * Ein benannter Wertparameter behält seinen Namen, ein unbenannter erhält den der eigenen Stelle (hier `@P1`).
     *
     * @spec SPEC-030
     */
    @Test
    fun `benannte und unbenannte Parameter mischen`() {
        val bound = query(executeSql("s", "d", param("@x", int4(1)), param("", int4(2))))
        assertEquals(listOf("@x", "@P1"), bound.parameters.map { it.name })
    }

    /**
     * Negativ: der erzeugte Name darf keinen vorhandenen verdecken, auch nicht in anderer Schreibweise; ein
     * ausdrücklich gesendetes `@p0` an Stelle 1 neben dem unbenannten Wert an Stelle 0 ist ein doppelter Name.
     * Wächter: die Eindeutigkeitsprüfung vor der Ersetzung des Namens statt danach.
     *
     * @spec SPEC-030
     */
    @Test
    fun `ein erzeugter Name darf keinen gesendeten doppeln`() {
        assertEquals(
            RpcRejection.PARAMETER_NAME,
            rejection(executeSql("s", "d", param("", int4(1)), param("@p0", int4(2)))),
        )
        assertEquals(
            RpcRejection.PARAMETER_NAME,
            rejection(executeSql("s", "d", param("@P1", int4(1)), param("", int4(2)))),
        )
    }

    /**
     * Negativ: ohne Parameter ist `sp_executesql` keine Form des Katalogs (`INVALID_RPC_FORM` im Parser), und der
     * einzige Parameter muss Text sein, nicht NULL und kein Integer.
     *
     * @spec SPEC-030
     */
    @Test
    fun `der einzige Parameter muss ein Text sein`() {
        val head = "${RpcVectors.HEADERS}  FF FF 0A 00  00 00"
        val failure = { tail: String ->
            assertIs<ProtocolResult.Failed>(RpcParser.parse(hex("$head $tail"))).error.failure
        }
        assertEquals(ProtocolFailure.INVALID_RPC_FORM, failure(""))
        assertEquals(ProtocolFailure.INVALID_RPC_FORM, failure("00 00 ${int4(1)}"))
        assertEquals(ProtocolFailure.INVALID_RPC_FORM, failure("00 00 E7 12 00 ${RpcVectors.COLLATION} FF FF"))
    }

    /**
     * Negativ: beim RPC per Prozedurname bleibt ein Wert ohne Namen abgelehnt, auch wenn ein Name an dieser Stelle
     * denkbar wäre. Wächter: eine Ersetzung, die nicht an der Form der Nachricht hängt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `per Prozedurname erhaelt kein Wert einen Namen`() {
        assertEquals(RpcRejection.PARAMETER_NAME, rejection(byName("p", param("", int4(1)))))
    }

    /**
     * `sp_prepexec` und `sp_execute` benennen einen Wert ohne Namen ebenso nach der Stelle. Die Nachrichten sind
     * aus den Bausteinen von `RpcWire` konstruiert (Layout nach MS-TDS), nicht aufgezeichnet: mit d-migrate wurde
     * weder `sp_prepexec` noch `sp_execute` gemessen.
     * Wächter: `nameInText = true` in `RpcBinder` für `PrepExec` oder `Execute`.
     *
     * @spec SPEC-030
     */
    @Test
    fun `sp_prepexec und sp_execute benennen Werte ohne Namen`() {
        val prepared = assertIs<RpcBinding.Prepare>(
            RpcBinder.bind(RpcWire.prepExec(-1, "select @a", "@a int", param("", int4(7)))),
        )
        assertEquals(listOf(QueryParameter("@P0", SqlType.Int32, 7)), prepared.parameters)

        val executed = assertIs<RpcBinding.ExecutePrepared>(
            RpcBinder.bind(RpcWire.execute(5, param("", int4(1)), param("", int8(2)))),
        )
        assertEquals(listOf("@P0", "@P1"), executed.parameters.map { it.name })
        assertEquals(listOf<Any?>(1, 2L), executed.parameters.map { it.value })
    }

    /**
     * Ein Parameter ohne Namen mit OUTPUT-Bit bekommt denselben Namen; mit Wert ist er `INPUT_OUTPUT`, mit NULL
     * `OUTPUT`. Konstruiert, nicht aufgezeichnet.
     * Wächter: `nameInText = true` an `ExecuteSql`, oder eine Namensbildung, die das Statusbyte ausnimmt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `ein Parameter ohne Namen mit OUTPUT-Bit heisst P0`() {
        val withValue = query(executeSql("s", "d", param("", int4(1), status = 1))).parameters.single()
        assertEquals(QueryParameter("@P0", SqlType.Int32, 1, ParameterDirection.INPUT_OUTPUT), withValue)
        val withNull = query(executeSql("s", "d", param("", "26 04 00", status = 1))).parameters.single()
        assertEquals(QueryParameter("@P0", SqlType.Int32, null, ParameterDirection.OUTPUT), withNull)
    }

    private companion object {
        const val HEAD = "16 00 00 00 12 00 00 00 02 00 00 00 00 00 00 00 00 00 01 00 00 00 FF FF 0A 00 00 00 "
        const val TEXT_HEAD = "00 00 E7 40 1F 09 04 D0 00 34 "

        const val TEXT_ONLY = HEAD + TEXT_HEAD +
            "44 00 53 00 45 00 4C 00 45 00 43 00 54 00 20 00 2A 00 20 00 46 00 52 00 4F 00 4D 00 20 00 5B 00 " +
            "6B 00 75 00 6E 00 64 00 65 00 6E 00 5D 00 20 00 57 00 48 00 45 00 52 00 45 00 20 00 31 00 20 00 " +
            "3D 00 20 00 30 00"

        const val POSITIONAL = HEAD + TEXT_HEAD +
            "52 00 53 00 45 00 4C 00 45 00 43 00 54 00 20 00 2A 00 20 00 46 00 52 00 4F 00 4D 00 20 00 5B 00 " +
            "6B 00 75 00 6E 00 64 00 65 00 6E 00 5D 00 20 00 57 00 48 00 45 00 52 00 45 00 20 00 5B 00 49 00 " +
            "64 00 5D 00 20 00 3D 00 20 00 20 00 40 00 50 00 30 00 20 00 " +
            "00 00 E7 40 1F 09 04 D0 00 34 14 00 40 00 50 00 30 00 20 00 62 00 69 00 67 00 69 00 6E 00 74 00 " +
            "00 00 26 08 08 07 00 00 00 00 00 00 00"
    }
}
