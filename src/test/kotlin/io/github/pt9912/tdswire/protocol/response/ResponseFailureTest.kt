package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** Fehler im Strom: `Failure`, Ausnahmen, Senke ohne Annahme (Festlegungen: KDoc von [ResponseSequencer]). */
class ResponseFailureTest {
    private val backendError = ErrorToken(BACKEND_MESSAGE)
    private val internalError = ErrorToken(INTERNAL_MESSAGE)
    private val errorDone = done(DoneStatus.ERROR)

    private fun run(backend: QueryBackend, sink: RecordingSink = RecordingSink()) =
        runBlocking { ResponseSequencer(FIXED_MAPPING).run(backend, REQUEST, sink) }

    /**
     * `Failure` vor `ResultStart` (auch der Fehler beim Start, `SPEC-002`): ERROR und DONE mit
     * Fehlerstatus, kein COLMETADATA; spätere Ereignisse werden nicht gelesen.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Failure vor ResultStart endet in ERROR und DONE und liest nichts weiter`() {
        val backend = ScriptedBackend.ofList(QueryEvent.Failure("x", "m"), start(), row(1))
        val sink = RecordingSink()
        assertEquals(SequenceOutcome.FAILED, run(backend, sink))
        assertEquals(listOf<Token>(backendError, errorDone), sink.tokens)
        assertEquals(1, backend.delivered)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * `Failure` nach teilweise gesendeten Zeilen: die Zeilen, dann ERROR und DONE mit Fehlerstatus;
     * kein zweites COLMETADATA (`SPEC-031`).
     *
     * @spec SPEC-031
     */
    @Test
    fun `Failure nach Zeilen schreibt die Zeilen, dann ERROR und DONE`() {
        val backend = ScriptedBackend.ofList(start(), row(1), row(2), QueryEvent.Failure("x", "m"))
        val sink = RecordingSink()
        assertEquals(SequenceOutcome.FAILED, run(backend, sink))
        assertEquals(
            listOf(ColMetadataToken::class, RowToken::class, RowToken::class, ErrorToken::class),
            sink.tokens.dropLast(1).map { it::class },
        )
        assertEquals(errorDone, sink.tokens.last())
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Festlegung (Nutzer): ein `Failure` zählt in der Vorschau als nächstes Ereignis; der
     * zurückgehaltene Abschluss bekommt `DONE_MORE`, danach ERROR und DONE mit Fehlerstatus.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Failure nach ResultEnd gibt dem zurueckgehaltenen Abschluss DONE_MORE`() {
        val backend = ScriptedBackend.ofList(start(), row(1), QueryEvent.ResultEnd(1), QueryEvent.Failure("x", "m"))
        val sink = RecordingSink()
        assertEquals(SequenceOutcome.FAILED, run(backend, sink))
        assertEquals(listOf<Token>(done(COUNT_MORE, 1), backendError, errorDone), sink.tokens.takeLast(3))
    }

    /**
     * Eine Ausnahme aus `execute()` ist ein Fall von `SPEC-048`: ERROR mit der festen Meldung, DONE mit
     * Fehlerstatus, Verbindung schließen; der Text der Ausnahme steht nirgends im Tokenstrom.
     *
     * @spec SPEC-048 SPEC-031
     */
    @Test
    fun `Ausnahme aus execute endet in TDS_INTERNAL_ERROR`() {
        val backend = FlowBackend(onExecute = { error("geheim") }) { flow { } }
        val sink = RecordingSink()
        assertEquals(SequenceOutcome.FAILED_CLOSE_CONNECTION, run(backend, sink))
        assertEquals(listOf<Token>(internalError, errorDone), sink.tokens)
        assertFalse(sink.tokens.toString().contains("geheim"))
    }

    /**
     * Eine Ausnahme aus dem Strom nach teilweise gesendeten Zeilen: die Zeilen, ERROR mit der festen
     * Meldung, DONE mit Fehlerstatus, `close()` der Ausführung genau einmal (`SPEC-048`, `SPEC-031`).
     *
     * @spec SPEC-048 SPEC-031
     */
    @Test
    fun `Ausnahme aus dem Strom nach Zeilen endet in TDS_INTERNAL_ERROR und schliesst`() {
        val backend = FlowBackend {
            flow {
                emit(start())
                emit(row(1))
                error("geheim")
            }
        }
        val sink = RecordingSink()
        assertEquals(SequenceOutcome.FAILED_CLOSE_CONNECTION, run(backend, sink))
        assertEquals(listOf<Token>(internalError, errorDone), sink.tokens.takeLast(2))
        assertIs<RowToken>(sink.tokens[1])
        assertFalse(sink.tokens.toString().contains("geheim"))
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Die Ausnahme des Stroms zählt in der Vorschau als nächstes Ereignis: der zurückgehaltene
     * Abschluss bekommt `DONE_MORE`.
     *
     * @spec SPEC-048 SPEC-031
     */
    @Test
    fun `Ausnahme nach ResultEnd gibt dem zurueckgehaltenen Abschluss DONE_MORE`() {
        val backend = FlowBackend {
            flow {
                emit(QueryEvent.UpdateCount(1))
                error("geheim")
            }
        }
        val sink = RecordingSink()
        run(backend, sink)
        assertEquals(listOf<Token>(done(COUNT_MORE, 1), internalError, errorDone), sink.tokens)
    }

    /**
     * Die Abbruchausnahme aus `execute()` wird nicht als Fehler des Backends abgebildet, sondern
     * läuft nach außen.
     *
     * @spec SPEC-029
     */
    @Test
    fun `Abbruchausnahme aus execute laeuft nach aussen`() {
        val backend = FlowBackend(onExecute = { throw CancellationException("weg") }) { flow { } }
        val sink = RecordingSink()
        assertFailsWith<CancellationException> { run(backend, sink) }
        assertEquals(emptyList(), sink.tokens)
    }

    /**
     * Festlegung „synchron“: nimmt die Senke COLMETADATA nicht an, wird nichts mehr geschrieben, kein
     * ERROR und kein DONE; die Ausführung wird geschlossen.
     *
     * @spec SPEC-031
     */
    @Test
    fun `abgelehntes COLMETADATA schliesst ohne Abschluss`() {
        val backend = ScriptedBackend.ofList(start(), row(1), QueryEvent.ResultEnd(1))
        val sink = RecordingSink(rejectAt = 1)
        assertEquals(SequenceOutcome.CLOSE_WITHOUT_COMPLETION, run(backend, sink))
        assertEquals(emptyList(), sink.tokens)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Festlegung „synchron“: nimmt die Senke eine ROW nicht an, folgt nichts mehr (auch kein ERROR).
     *
     * @spec SPEC-031
     */
    @Test
    fun `abgelehnte ROW schliesst ohne Abschluss`() {
        val backend = ScriptedBackend.ofList(start(), row(1), row(2), QueryEvent.ResultEnd(2))
        val sink = RecordingSink(rejectAt = 3)
        assertEquals(SequenceOutcome.CLOSE_WITHOUT_COMPLETION, run(backend, sink))
        assertEquals(2, sink.tokens.size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Festlegung „synchron“: nimmt die Senke das zurückgehaltene DONE nicht an, folgt das ERROR eines
     * `Failure` nicht mehr.
     *
     * @spec SPEC-031
     */
    @Test
    fun `abgelehntes DONE_MORE vor Failure schliesst ohne ERROR`() {
        val backend = ScriptedBackend.ofList(QueryEvent.UpdateCount(1), QueryEvent.Failure("x", "m"))
        val sink = RecordingSink(rejectAt = 1)
        assertEquals(SequenceOutcome.CLOSE_WITHOUT_COMPLETION, run(backend, sink))
        assertEquals(emptyList(), sink.tokens)
    }

    /**
     * Festlegung „synchron“: nimmt die Senke das ERROR nicht an, folgt das DONE mit Fehlerstatus nicht.
     *
     * @spec SPEC-031
     */
    @Test
    fun `abgelehntes ERROR schliesst ohne DONE`() {
        val backend = ScriptedBackend.ofList(start(), QueryEvent.Failure("x", "m"))
        val sink = RecordingSink(rejectAt = 2)
        assertEquals(SequenceOutcome.CLOSE_WITHOUT_COMPLETION, run(backend, sink))
        assertEquals(1, sink.tokens.size)
    }

    /**
     * Festlegung (Nutzer): `ReturnStatus` und `OutputParameter` werden mit einem Fehler der
     * Typabbildung abgelehnt: ERROR und DONE mit Fehlerstatus, kein RETURNSTATUS.
     *
     * @spec SPEC-031 SPEC-014
     */
    @Test
    fun `ReturnStatus und OutputParameter werden abgelehnt`() {
        val events = listOf(
            QueryEvent.ReturnStatus(0),
            QueryEvent.OutputParameter("p", SqlType.Int32, 1),
        )
        for (event in events) {
            val backend = ScriptedBackend.ofList(QueryEvent.UpdateCount(1), event)
            val sink = RecordingSink()
            assertEquals(SequenceOutcome.FAILED, run(backend, sink))
            assertEquals(
                listOf<Token>(done(COUNT_MORE, 1), ErrorToken(TYPE_MESSAGE), errorDone),
                sink.tokens,
            )
            assertEquals(1, backend.closeCalls)
        }
    }
}
