package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Prüfung der Ereignisreihenfolge (Festlegung: Verstoß ist ein Fall von `SPEC-048`) und Zufallsfolgen. */
class ResponseOrderTest {
    private val internalError = ErrorToken(INTERNAL_MESSAGE)
    private val errorDone = done(DoneStatus.ERROR)

    private fun sequence(sink: RecordingSink, backend: ScriptedBackend): SequenceOutcome =
        runBlocking { ResponseSequencer(FIXED_MAPPING).run(backend, REQUEST, sink) }

    /**
     * Jeder Verstoß gegen die Reihenfolge endet in ERROR mit der festen Meldung, DONE mit
     * Fehlerstatus und dem Ausgang „Verbindung schließen“; `close()` läuft einmal.
     *
     * @spec SPEC-048 SPEC-031
     */
    @Test
    fun `Reihenfolgeverstoss endet in TDS_INTERNAL_ERROR`() {
        val violations = mapOf(
            "Row vor ResultStart" to listOf(row(1)),
            "doppeltes ResultEnd" to listOf(start(), QueryEvent.ResultEnd(0), QueryEvent.ResultEnd(0)),
            "ResultEnd ohne ResultStart" to listOf(QueryEvent.ResultEnd(0)),
            "ResultStart im offenen Ergebnis" to listOf(start(), row(1), start()),
            "UpdateCount im offenen Ergebnis" to listOf(start(), QueryEvent.UpdateCount(1)),
            "Row nach ResultEnd" to listOf(start(), QueryEvent.ResultEnd(0), row(1)),
            "Strom endet im offenen Ergebnis" to listOf(start(), row(1)),
        )
        for ((name, events) in violations) {
            val backend = ScriptedBackend.ofList(*events.toTypedArray())
            val sink = RecordingSink()
            assertEquals(SequenceOutcome.FAILED_CLOSE_CONNECTION, sequence(sink, backend), name)
            assertEquals(listOf<Token>(internalError, errorDone), sink.tokens.takeLast(2), name)
            assertEquals(1, backend.closeCalls, name)
        }
    }

    /**
     * Kein zweiter Resultset-Anfang nach einer Teilantwort (`SPEC-031`): ein zweites `ResultStart` im
     * offenen Ergebnis schreibt kein zweites COLMETADATA.
     *
     * @spec SPEC-031
     */
    @Test
    fun `kein zweites COLMETADATA nach einer Teilantwort`() {
        val backend = ScriptedBackend.ofList(start(), row(1), start(), row(2))
        val sink = RecordingSink()
        sequence(sink, backend)
        assertEquals(1, sink.tokens.count { it is ColMetadataToken })
        assertEquals(1, sink.tokens.count { it is RowToken })
    }

    /**
     * Ein Verstoß, der auf ein zurückgehaltenes `ResultEnd` folgt, zählt als nächstes Ereignis:
     * `DONE_MORE`, ERROR, DONE mit Fehlerstatus.
     *
     * @spec SPEC-048 SPEC-031
     */
    @Test
    fun `Verstoss nach ResultEnd gibt dem Abschluss DONE_MORE`() {
        val backend = ScriptedBackend.ofList(start(), QueryEvent.ResultEnd(0), row(1))
        val sink = RecordingSink()
        sequence(sink, backend)
        assertEquals(listOf<Token>(done(COUNT_MORE, 0), internalError, errorDone), sink.tokens.drop(1))
    }

    private fun randomEvents(random: Random): List<QueryEvent> {
        val wide = ResultColumn("t", SqlType.UnicodeText, nullable = true)
        val pool = listOf(
            start(), row(1), row(2), QueryEvent.Row(listOf("x")), QueryEvent.Row(emptyList()),
            QueryEvent.ResultStart(listOf(wide)), QueryEvent.ResultStart(emptyList()),
            QueryEvent.ResultEnd(1), QueryEvent.ResultEnd(null), QueryEvent.UpdateCount(2),
            QueryEvent.Failure("x", "m"), QueryEvent.ReturnStatus(1),
            QueryEvent.OutputParameter("p", SqlType.Int32, 1),
        )
        return List(random.nextInt(0, MAX_EVENTS)) { pool[random.nextInt(pool.size)] }
    }

    /**
     * Zufällige Ereignisfolgen mit festem Startwert enden stets in einem definierten Ausgang und nie
     * in einer Ausnahme. Unabhängig vom Ausgang gilt für die Tokenfolge: Sie lässt sich kodieren, ein
     * ROW steht nur nach COLMETADATA, ein DONE mit `DONE_MORE` ist nie das letzte Token, ein DONE
     * ohne `DONE_MORE` ist stets das letzte, und `close()` läuft genau einmal. Selbstkonsistenz, kein
     * Handvektor.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Zufallsfolgen enden stets in einem definierten Ausgang`() {
        val random = Random(SEED)
        val outcomes = mutableSetOf<SequenceOutcome>()
        repeat(RUNS) { index ->
            val events = randomEvents(random)
            val backend = ScriptedBackend.ofList(*events.toTypedArray())
            val sink = RecordingSink()
            val outcome = sequence(sink, backend)
            outcomes += outcome
            assertNotEquals(SequenceOutcome.CLOSE_WITHOUT_COMPLETION, outcome, "Lauf $index")
            assertEquals(1, backend.closeCalls, "Lauf $index")
            assertWellFormed(sink.tokens, "Lauf $index: $events")
        }
        assertEquals(
            setOf(SequenceOutcome.COMPLETED, SequenceOutcome.FAILED, SequenceOutcome.FAILED_CLOSE_CONNECTION),
            outcomes,
        )
    }

    private fun assertWellFormed(tokens: List<Token>, message: String) {
        val encoder = TokenEncoder()
        tokens.forEach { assertIs<ProtocolResult.Ok<Unit>>(encoder.write(it), message) }
        assertTrue(tokens.last() is DoneToken, message)
        var meta = false
        tokens.forEachIndexed { index, token ->
            when (token) {
                is ColMetadataToken -> meta = true

                is RowToken -> assertTrue(meta, message)

                is DoneToken -> assertEquals(
                    index != tokens.lastIndex,
                    token.status and DoneStatus.MORE != 0,
                    message,
                )

                else -> Unit
            }
        }
    }

    private companion object {
        const val SEED = 20261030L
        const val RUNS = 2000
        const val MAX_EVENTS = 8
    }
}
