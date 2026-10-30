package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Sequenzierer und echter [ErrorMapper] zusammen, ohne Fake für den Mapper: für jeden Code liefern
 * die Bytes in der laufenden Nachricht und die Verbindungsfolge dieselben Werte wie die Einzelantwort
 * des Mappers (eine Quelle der Wahrheit, `TDS-FA-06.a`).
 */
class ResponseErrorMapperTest {
    private val mapper = ErrorMapper()

    private class Result(val outcome: SequenceOutcome, val tokens: List<Token>) {
        /** Die Tokens ab dem ERROR als Bytes: ERROR und DONE mit Fehlerstatus. */
        val tail: List<Token> get() = tokens.dropWhile { it !is ErrorToken }
        val bytes: ByteArray
            get() {
                val encoder = TokenEncoder()
                tail.forEach { encoder.write(it) }
                return encoder.toByteArray()
            }
        val error: ErrorToken get() = assertIs<ErrorToken>(tail.first())
    }

    private fun run(backend: QueryBackend): Result {
        val sink = RecordingSink()
        val outcome = runBlocking { ResponseSequencer(mapper.sequencerMapping()).run(backend, REQUEST, sink) }
        return Result(outcome, sink.tokens)
    }

    private fun expected(code: ErrorCode) = assertNotNull(mapper.code(code).message).payload

    /**
     * Jeder Backend-Code: dieselben Bytes wie die Einzelantwort, dieselbe Folge.
     *
     * @spec SPEC-015 SPEC-031
     */
    @Test
    fun `Backend-Fehler liefern fuer jeden Code Bytes und Folge des Mappers`() {
        for (code in ErrorMapper.BACKEND_CODES) {
            val result = run(ScriptedBackend.ofList(QueryEvent.Failure(code.name, "geheim")))
            assertEquals(SequenceOutcome.FAILED, result.outcome, code.name)
            assertContentEquals(expected(code), result.bytes, code.name)
            assertEquals(code.rule.fate, mapper.fateOf(result.error.message), code.name)
            assertEquals(mapper.failure(QueryEvent.Failure(code.name, "m")).fate, mapper.fateOf(result.error.message))
        }
    }

    /**
     * Zeilen, dann Fehler in einer Nachricht (Messung R03): die Zeilen stehen davor, ERROR und DONE
     * sind die des Mappers.
     *
     * @spec SPEC-015 SPEC-031
     */
    @Test
    fun `Fehler nach Zeilen haengt ERROR und DONE des Mappers an`() {
        val backend = ScriptedBackend.ofList(start(), row(1), QueryEvent.Failure("TDS_SIM_INVALID_VALUE", "m"))
        val result = run(backend)
        assertIs<RowToken>(result.tokens[1])
        assertContentEquals(expected(ErrorCode.TDS_SIM_INVALID_VALUE), result.bytes)
    }

    /**
     * Ein Code, den nur der Server erzeugt, und ein fremder Code kommen als `TDS_INTERNAL_ERROR` an, und
     * die Folge ist das Schließen, obwohl der Sequenzierer nur `FAILED` meldet.
     *
     * @spec SPEC-048
     */
    @Test
    fun `Code ausserhalb der Positivliste kommt als interner Fehler mit Verbindungsende an`() {
        val serverOnly = ErrorCode.entries.map { it.name } - ErrorMapper.BACKEND_CODES.map { it.name }.toSet()
        for (name in serverOnly + "ORA-00001") {
            val result = run(ScriptedBackend.ofList(QueryEvent.Failure(name, "m")))
            assertContentEquals(expected(ErrorCode.TDS_INTERNAL_ERROR), result.bytes, name)
            assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, mapper.fateOf(result.error.message), name)
        }
    }

    /**
     * Eine Ausnahme des Backends: Bytes und Folge von `TDS_INTERNAL_ERROR`; der Sequenzierer meldet
     * `FAILED_CLOSE_CONNECTION` und stimmt mit der Folge des Mappers überein.
     *
     * @spec SPEC-048
     */
    @Test
    fun `Ausnahme liefert Bytes und Folge von TDS_INTERNAL_ERROR`() {
        val result = run(FlowBackend { flow { error("geheim") } })
        assertEquals(SequenceOutcome.FAILED_CLOSE_CONNECTION, result.outcome)
        assertContentEquals(expected(ErrorCode.TDS_INTERNAL_ERROR), result.bytes)
        assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, mapper.fateOf(result.error.message))
    }

    /**
     * Ein nicht abbildbares Ereignis geht durch `typeMapping`: dieselben Bytes und dieselbe Folge wie
     * `protocol` des Mappers für denselben Fehler.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Typabbildung liefert Bytes und Folge von protocol`() {
        val result = run(ScriptedBackend.ofList(QueryEvent.ReturnStatus(0)))
        val error = ProtocolError(ProtocolFailure.INVALID_TYPE, "Ereignis ReturnStatus wird nicht abgebildet")
        val response = mapper.protocol(error)
        assertContentEquals(assertNotNull(response.message).payload, result.bytes)
        assertEquals(response.fate, mapper.fateOf(result.error.message))
    }
}
