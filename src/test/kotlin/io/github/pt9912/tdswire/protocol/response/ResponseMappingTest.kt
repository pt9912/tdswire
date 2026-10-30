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
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.testsupport.toHex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ereignis → Token und die Vorschau für `DONE_MORE` (Festlegungen: KDoc von [ResponseSequencer]). */
class ResponseMappingTest {
    private fun sequence(vararg events: QueryEvent): Triple<SequenceOutcome, List<Token>, ScriptedBackend> {
        val backend = ScriptedBackend.ofList(*events)
        val sink = RecordingSink()
        val outcome = runBlocking { ResponseSequencer(FIXED_MAPPING).run(backend, REQUEST, sink) }
        return Triple(outcome, sink.tokens, backend)
    }

    private fun bytes(tokens: List<Token>): String {
        val encoder = TokenEncoder()
        tokens.forEach { encoder.write(it) }
        return encoder.toByteArray().toHex()
    }

    /**
     * Szenario aus ADR-0006: Zeilen, Update-Count, Zeilen, leeres Ergebnis. Die Bits folgen der
     * Vorschau: `DONE_MORE` an jedem Abschluss außer dem letzten.
     *
     * @spec SPEC-031 SPEC-002
     */
    @Test
    fun `Szenario Zeilen, Update-Count, Zeilen, leeres Ergebnis setzt DONE_MORE nur vor dem letzten Abschluss`() {
        val (outcome, tokens, backend) = sequence(
            start(), row(1), row(2), QueryEvent.ResultEnd(2),
            QueryEvent.UpdateCount(5),
            start(), row(3), QueryEvent.ResultEnd(1),
            start(), QueryEvent.ResultEnd(0),
        )
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        val doneStatuses = tokens.filterIsInstance<DoneToken>()
        assertEquals(
            listOf(COUNT_MORE, COUNT_MORE, COUNT_MORE, DoneStatus.COUNT),
            doneStatuses.map { it.status },
        )
        assertEquals(listOf(2L, 5L, 1L, 0L), doneStatuses.map { it.rowCount })
        assertEquals(3, tokens.filterIsInstance<ColMetadataToken>().size)
        assertEquals(3, tokens.filterIsInstance<RowToken>().size)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Handvektor, von Hand aus MS-TDS 2.2.7.6, 2.2.7.4 und 2.2.7.17 abgeleitet (nicht gemessen):
     * COLMETADATA mit einer Spalte `n` (INT4, nicht nullbar), eine ROW mit dem Wert 1, ein DONE mit
     * `DONE_COUNT` und Zeilenzahl 1, `curCmd` 0.
     *
     * @spec SPEC-031 SPEC-002
     */
    @Test
    fun `Handvektor einer Antwort mit einer Zeile`() {
        val (_, tokens, _) = sequence(start(), row(1), QueryEvent.ResultEnd(1))
        assertEquals(
            "81 01 00 00 00 00 00 00 00 38 01 6E 00 " +
                "D1 01 00 00 00 " +
                "FD 10 00 00 00 01 00 00 00 00 00 00 00",
            bytes(tokens),
        )
    }

    /**
     * Handvektor: ein zurückgehaltenes DONE bekommt `DONE_MORE` (`0x0001`) zu `DONE_COUNT` (`0x0010`),
     * also Status `0x0011`.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Handvektor DONE mit COUNT und MORE`() {
        val (_, tokens, _) = sequence(QueryEvent.UpdateCount(3), QueryEvent.UpdateCount(4))
        assertEquals(
            "FD 11 00 00 00 03 00 00 00 00 00 00 00 FD 10 00 00 00 04 00 00 00 00 00 00 00",
            bytes(tokens),
        )
    }

    /**
     * Leeres Ergebnis mit Metadaten (`TDS-FA-08` Boundary): COLMETADATA, kein ROW, DONE ohne MORE.
     *
     * @spec SPEC-031
     */
    @Test
    fun `leeres Ergebnis mit Metadaten`() {
        val (outcome, tokens, _) = sequence(start(), QueryEvent.ResultEnd(0))
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals(listOf<Token>(tokens[0], done(DoneStatus.COUNT, 0)), tokens)
        assertIs<ColMetadataToken>(tokens[0])
    }

    /**
     * Festlegung: `ResultEnd(null)` trägt keine Zahl; das Bit `COUNT` fehlt und die Zeilenzahl ist 0.
     *
     * @spec SPEC-031
     */
    @Test
    fun `ResultEnd ohne Zahl setzt kein COUNT`() {
        val (_, tokens, _) = sequence(start(), QueryEvent.ResultEnd(null))
        assertEquals(done(DoneStatus.FINAL, 0), tokens.last())
    }

    /**
     * Festlegung: ein Strom ohne Ereignis endet in einem DONE ohne Bits.
     *
     * @spec SPEC-031
     */
    @Test
    fun `leerer Strom endet in einem DONE ohne Bits`() {
        val (outcome, tokens, backend) = sequence()
        assertEquals(SequenceOutcome.COMPLETED, outcome)
        assertEquals(listOf<Token>(done(DoneStatus.FINAL)), tokens)
        assertEquals(1, backend.closeCalls)
    }

    /**
     * Die Metadaten werden vor der ersten Zeile geschrieben und ein nicht abbildbarer Typ scheitert vor
     * COLMETADATA: kein COLMETADATA, ERROR und DONE mit Fehlerstatus (`SPEC-031`).
     *
     * @spec SPEC-031 SPEC-014
     */
    @Test
    fun `nicht abbildbare Metadaten schreiben kein COLMETADATA`() {
        val decimal = SqlType.Decimal(39, 0)
        val column = ResultColumn("d", decimal, nullable = false)
        val (outcome, tokens, backend) = sequence(QueryEvent.ResultStart(listOf(column)), row(1))
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(
            listOf<Token>(
                ErrorToken(TYPE_MESSAGE),
                done(DoneStatus.ERROR),
            ),
            tokens,
        )
        assertEquals(1, backend.closeCalls)
        assertEquals(1, backend.delivered)
    }

    /**
     * Eine Zeile wird als Ganzes abgebildet: ein Typkonflikt in der dritten Zeile schreibt die Zeilen
     * davor, dann ERROR und DONE mit Fehlerstatus, nie die halbe Zeile (`SPEC-031`, `SPEC-014`).
     *
     * @spec SPEC-031 SPEC-014
     */
    @Test
    fun `nicht abbildbarer Wert nach Zeilen endet in ERROR und DONE`() {
        val (outcome, tokens, _) = sequence(start(), row(1), row(2), QueryEvent.Row(listOf("text")), row(4))
        assertEquals(SequenceOutcome.FAILED, outcome)
        assertEquals(2, tokens.filterIsInstance<RowToken>().size)
        assertEquals(
            listOf<Token>(
                ErrorToken(TYPE_MESSAGE),
                done(DoneStatus.ERROR),
            ),
            tokens.takeLast(2),
        )
        assertEquals(RowToken(listOf(TdsValue.IntegerValue(2))), tokens[2])
    }

    /**
     * Die Vorschau hält ein Ereignis lang zurück: nach `ResultEnd` ist das DONE noch nicht
     * geschrieben, mit dem nächsten Ereignis folgt es mit `DONE_MORE`. Die Steuerung ist ein
     * `CompletableDeferred`, keine Wanduhr.
     *
     * @spec SPEC-031
     */
    @Test
    fun `ResultEnd wird bis zum naechsten Ereignis zurueckgehalten`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val backend = FlowBackend {
            flow {
                emit(start())
                emit(QueryEvent.ResultEnd(0))
                gate.await()
                emit(QueryEvent.UpdateCount(1))
            }
        }
        val sink = RecordingSink()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            ResponseSequencer(FIXED_MAPPING).run(backend, REQUEST, sink)
        }
        assertEquals(1, sink.tokens.size)
        assertIs<ColMetadataToken>(sink.tokens.single())
        gate.complete(Unit)
        job.join()
        assertEquals(done(COUNT_MORE, 0), sink.tokens[1])
        assertEquals(done(DoneStatus.COUNT, 1), sink.tokens[2])
    }

    /**
     * Festlegung (Nutzer): ein zurückgehaltener Abschluss wird bei Cancel oder Disconnect verworfen
     * und nicht geschrieben; `close()` läuft trotzdem.
     *
     * @spec SPEC-031 SPEC-029
     */
    @Test
    fun `Abbruch verwirft den zurueckgehaltenen Abschluss und schliesst die Ausfuehrung`() = runBlocking {
        val backend = FlowBackend {
            flow {
                emit(start())
                emit(QueryEvent.ResultEnd(0))
                awaitCancellation()
            }
        }
        val sink = RecordingSink()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            ResponseSequencer(FIXED_MAPPING).run(backend, REQUEST, sink)
        }
        job.cancel()
        job.join()
        assertEquals(1, sink.tokens.size)
        assertTrue(sink.tokens.single() is ColMetadataToken)
        assertEquals(1, backend.closeCalls)
    }
}
