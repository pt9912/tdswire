package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
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
    }

    private fun run(
        backend: QueryBackend,
        sink: RecordingSink = RecordingSink(),
        mapping: ErrorMapper = mapper,
    ): Result {
        val outcome = runBlocking { ResponseSequencer(mapping.sequencerMapping()).run(backend, REQUEST, sink) }
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
            assertContentEquals(expected(code), result.bytes, code.name)
            assertEquals(code.rule.fate, mapper.fateOf(result.outcome), code.name)
            val single = mapper.failure(QueryEvent.Failure(code.name, "m"))
            assertEquals(single.fate, mapper.fateOf(result.outcome), code.name)
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
            assertEquals(SequenceOutcome.FAILED_CLOSE_CONNECTION, result.outcome, name)
            assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, mapper.fateOf(result.outcome), name)
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
        assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, mapper.fateOf(result.outcome))
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
        assertEquals(ConnectionFate.CLOSE_AFTER_REPLY, response.fate)
        assertEquals(response.fate, mapper.fateOf(result.outcome))
    }

    /**
     * Die Ausnahme des Backends erreicht das Log mit ihrem Typnamen, nie mit ihrem Text; ein
     * Reihenfolgeverstoß trägt die Marke `sequence-violation`; Fehler des Backends und Erfolg rufen
     * den Fall nicht.
     *
     * @spec SPEC-048
     */
    @Test
    fun `Ausnahme und Reihenfolgeverstoss erreichen das Log`() {
        val entries = mutableListOf<ErrorLogEntry>()
        val logged = ErrorMapper { entries += it }
        val result = run(FlowBackend { flow { throw IllegalStateException("geheim") } }, mapping = logged)
        val internal = ErrorCode.TDS_INTERNAL_ERROR
        assertEquals(listOf(ErrorLogEntry(internal, IllegalStateException::class.java.name)), entries)
        assertEquals(false, result.tokens.toString().contains("geheim"))
        entries.clear()
        run(ScriptedBackend.ofList(row(1)), mapping = logged)
        assertEquals(listOf(ErrorLogEntry(internal, "sequence-violation")), entries)
        entries.clear()
        run(ScriptedBackend.ofList(QueryEvent.UpdateCount(1)), mapping = logged)
        assertEquals(emptyList(), entries)
    }

    /**
     * Der Ausgang „ohne Abschluss schließen“ des Sequenzierers und `streamSynchronous = false` des
     * Mappers sind dieselbe Folge: `CLOSE_WITHOUT_REPLY`; ein Erfolg lässt die Verbindung offen.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ausgang ohne Abschluss entspricht der Folge ohne Antwort des Mappers`() {
        val result = run(ScriptedBackend.ofList(start()), sink = RecordingSink(rejectAt = 1))
        assertEquals(SequenceOutcome.CLOSE_WITHOUT_COMPLETION, result.outcome)
        val fate = mapper.fateOf(result.outcome)
        assertEquals(ConnectionFate.CLOSE_WITHOUT_REPLY, fate)
        assertEquals(mapper.code(ErrorCode.TDS_INTERNAL_ERROR, streamSynchronous = false).fate, fate)
        assertEquals(ConnectionFate.OPEN, mapper.fateOf(run(ScriptedBackend.ofList(QueryEvent.UpdateCount(1))).outcome))
    }
}
