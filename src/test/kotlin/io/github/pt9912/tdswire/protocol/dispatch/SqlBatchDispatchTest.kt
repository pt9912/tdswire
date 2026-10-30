package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Annahme, Ergebnis und leeres Ergebnis eines SQL_BATCH in `READY` (`TDS-FA-03.a`, `SPEC-001`, `SPEC-031`)
 * gegen das Test-Backend [ScriptedBackend]. Aussagen über einen echten SQL Server oder den Referenzclient
 * tragen diese Tests nicht; die Bytes der Antworten sind Handvektoren (Tabellenlayout nach MS-TDS), die
 * Tokenfolge und die DONE-Bits der Antwort auf einen Batch sind am echten Server nicht gemessen.
 */
class SqlBatchDispatchTest {
    private val oneRow = listOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private val oneRowReply = listOf(
        BatchVectors.COLMETADATA_N,
        BatchVectors.row(7),
        BatchVectors.DONE_COUNT_1,
    ).joinToString(" ")

    /**
     * Das Backend bekommt Sitzungs-ID, eine neue Anfrage-ID, den Text unverändert (auch leer, mit `SET`,
     * Unicode, Steuerzeichen und Zeilenumbruch), eine leere Parameterliste und keine Frist; je Text genau
     * ein Aufruf.
     *
     * @spec SPEC-001
     * @spec SPEC-002
     */
    @Test
    fun `Das Backend empfaengt Sitzung, neue Anfrage-ID und den Text unveraendert`() = blocking {
        val texts = listOf("select 1", "", "   ", "SET FMTONLY ON; select 1", "€😀\u0000 x\r\n\tü")
        texts.forEach { text ->
            val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
            val session = Session.login(BatchVectors.message(text), Inbound.EndOfStream)
            session.serve(backend)
            val request = backend.requests.single()
            assertEquals("s1", request.sessionId, text)
            assertEquals("r1", request.requestId, text)
            assertEquals(text, request.sql, text)
            assertEquals(emptyList(), request.parameters, text)
            assertEquals(null, request.deadlineEpochMillis, text)
            assertEquals(1, session.replies.size, text)
        }
    }

    /**
     * Ohne eigene Kennungsquelle sind die Anfrage-IDs zweier Anfragen derselben Sitzung nicht leer und
     * verschieden.
     *
     * @spec SPEC-002
     */
    @Test
    fun `Die Anfrage-ID ist je Anfrage neu`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(BatchVectors.message("a"), BatchVectors.message("b"), Inbound.EndOfStream)
        RequestLoop(session.ready, session.channel, backend).run()
        val ids = backend.requests.map { it.requestId }
        assertEquals(2, ids.size)
        assertTrue(ids.all { it.isNotBlank() })
        assertNotEquals(ids[0], ids[1])
    }

    /**
     * Ein Ergebnis mit einer Zeile ergibt genau eine Antwort-Nachricht vom Typ TABULAR_RESULT mit
     * COLMETADATA, ROW und DONE (COUNT 1); die Bytes sind ein Handvektor. Das Backend wird einmal
     * geschlossen.
     *
     * @spec SPEC-031
     * @spec SPEC-001
     */
    @Test
    fun `Ein Ergebnis wird als COLMETADATA, ROW und DONE beantwortet`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(BatchVectors.message("select 7"), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(1, session.replies.size)
        assertEquals(PacketType.TABULAR_RESULT, session.replies[0].type)
        assertEquals(oneRowReply, session.hex(0))
        assertEquals(1, backend.closeCalls)
        assertEquals(oneRow.size, backend.delivered)
    }

    /**
     * Ein Ergebnis ohne Zeilen ergibt Spaltenmetadaten und einen ordnungsgemäßen Abschluss, mit Zeilenzahl 0
     * (Bit COUNT) oder ohne Zahl (kein Bit); eine ROW steht nicht in der Antwort.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Ein leeres Ergebnis hat Metadaten und einen Abschluss ohne ROW`() = blocking {
        val variants = mapOf(
            QueryEvent.ResultEnd(0) to BatchVectors.DONE_COUNT_0,
            QueryEvent.ResultEnd(null) to BatchVectors.DONE_FINAL,
        )
        variants.forEach { (end, done) ->
            val backend = ScriptedBackend.ofList(resultStart(), end)
            val session = Session.login(BatchVectors.message("select 1 where 1=0"), Inbound.EndOfStream)
            session.serve(backend)
            assertEquals(BatchVectors.COLMETADATA_N + " " + done, session.hex(0), "$end")
            assertEquals(1, backend.closeCalls, "$end")
        }
    }

    /**
     * Zwei Anfragen in Folge auf einer Sitzung werden beide beantwortet, die zweite mit eigener
     * Anfrage-ID und ihrem eigenen Text; vor jedem Lesen ist die Sitzung `READY`, und der Kanal wird
     * erst nach dem Ende der Gegenstelle geschlossen (drei Leseaufrufe nach dem Login: zwei Batches und
     * das Ende).
     *
     * @spec SPEC-001
     * @spec SPEC-031
     */
    @Test
    fun `Zwei Anfragen in Folge werden beantwortet und die Sitzung ist dazwischen READY`() = blocking {
        val backend = ScriptedBackend.inSequence(
            oneRow,
            listOf(resultStart(), QueryEvent.ResultEnd(0)),
        )
        val session = Session.login(BatchVectors.message("one"), BatchVectors.message("two"), Inbound.EndOfStream)
        val states = mutableListOf<ConnectionState>()
        session.channel.onReceive = { states += session.ready.live.context.state }
        session.serve(backend)
        assertEquals(listOf("one", "two"), backend.requests.map { it.sql })
        assertEquals(listOf("r1", "r2"), backend.requests.map { it.requestId })
        assertEquals(oneRowReply, session.hex(0))
        assertEquals(BatchVectors.COLMETADATA_N + " " + BatchVectors.DONE_COUNT_0, session.hex(1))
        assertEquals(3, session.channel.receiveCalls - 2)
        assertEquals(List(3) { ConnectionState.READY }, states.takeLast(3))
        assertEquals(ConnectionState.CLOSED, session.ready.live.context.state)
    }

    /**
     * Der Anfragezustand läuft `READY → RUNNING → STREAMING → READY`: `RUNNING` steht zwischen der
     * Annahme und dem Backend-Aufruf (beobachtet in der Kennungsquelle, die der Dispatch dort ruft),
     * `STREAMING` ab dem ersten COLMETADATA (beobachtet von einer zweiten Coroutine bei jedem
     * Abnahmeschritt des Backends, ein Thread, keine Uhr).
     *
     * @spec SPEC-001
     */
    @Test
    fun `Der Anfragezustand laeuft von READY ueber RUNNING nach STREAMING`() = blocking {
        val backend = ScriptedBackend.generating(rows = 3)
        val session = Session.login(BatchVectors.message("select n"), Inbound.EndOfStream)
        val atRequestId = mutableListOf<ConnectionState>()
        val seen = mutableListOf<ConnectionState>()
        val probe = launch {
            while (true) {
                session.ready.live.context.state.let { if (seen.lastOrNull() != it) seen += it }
                yield()
            }
        }
        session.serve(backend) {
            atRequestId += session.ready.live.context.state
            "r1"
        }
        probe.cancel()
        assertEquals(listOf(ConnectionState.RUNNING), atRequestId)
        assertEquals(ConnectionState.STREAMING, seen.first())
        assertTrue(ConnectionState.RUNNING !in seen.drop(1), "RUNNING nach STREAMING: $seen")
        assertEquals(3, backend.rowsPulled)
    }
}
