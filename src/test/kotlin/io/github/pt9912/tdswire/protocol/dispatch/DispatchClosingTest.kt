package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die Ausgänge der Anfrageschleife, die nicht aus einem Fehler des Backends folgen (`TDS-FA-03`,
 * `SPEC-031`, `SPEC-048`): eine Senke, die ein Token nicht annimmt (`CLOSE_WITHOUT_COMPLETION`), und
 * eine Ausnahme aus `execute()` oder aus dem Strom. Die Senke ist ein Test-Fixture über der Schleife;
 * die Zuordnung der Ausgänge zur Verbindungsfolge ist Festlegung (`ErrorMapper`), nicht am echten
 * SQL Server gemessen.
 */
class DispatchClosingTest {
    private val internal = ServerMessage(50007, 1, 20, "Internal error.")

    /** Eine Senke, die das [rejectAt]-te Token (ab 1) nicht annimmt und alle früheren an [inner] gibt. */
    private class RejectingSink(private val inner: TokenSink, private val rejectAt: Int) : TokenSink {
        private var seen = 0

        override suspend fun accept(token: Token): Boolean = if (++seen == rejectAt) false else inner.accept(token)
    }

    private class FailingBackend(private val failInExecute: Boolean) : QueryBackend {
        var closeCalls = 0

        override suspend fun execute(request: QueryRequest): QueryExecution {
            check(!failInExecute) { "execute" }
            return object : QueryExecution {
                override val events: Flow<QueryEvent> = flow {
                    emit(resultStart())
                    error("Strom")
                }

                override suspend fun close() {
                    closeCalls++
                }
            }
        }

        override suspend fun cancel(sessionId: String, requestId: String) = Unit
    }

    /**
     * Nimmt die Senke ein Token nicht an (das erste COLMETADATA, danach die erste ROW), geht keine
     * Antwort hinaus, auch kein ERROR und kein DONE: die Sitzung ist `CLOSED`, der Kanal geschlossen,
     * die nächste Nachricht ungelesen, das Backend geschlossen.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Eine Senke, die ein Token ablehnt, schliesst ohne Antwort`() = blocking {
        listOf(1, 2).forEach { rejectAt ->
            val backend = ScriptedBackend.ofList(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))
            val session = Session.login(BatchVectors.message("q"), BatchVectors.message("never read"))
            session.serve(backend, sinkAround = { RejectingSink(it, rejectAt) })
            assertEquals(0, session.replies.size, "Token $rejectAt: Antwort gesendet")
            assertTrue(session.channel.closed, "Token $rejectAt")
            assertFalse(session.channel.exhausted, "Token $rejectAt: die naechste Nachricht wurde gelesen")
            assertEquals(1, backend.closeCalls, "Token $rejectAt")
            assertEquals(ConnectionState.CLOSED, session.ready.live.context.state, "Token $rejectAt")
        }
    }

    /**
     * Eine Ausnahme aus `execute()` und eine aus dem Strom enden mit ERROR (`TDS_INTERNAL_ERROR`,
     * Class 20) und DONE mit Fehlerstatus; die Verbindung schließt danach, die nächste Nachricht
     * bleibt ungelesen, das Backend ist geschlossen (soweit `execute()` eine Ausführung lieferte).
     *
     * @spec SPEC-048
     * @spec SPEC-031
     */
    @Test
    fun `Eine Ausnahme aus execute oder aus dem Strom schliesst nach der Fehlerantwort`() = blocking {
        listOf(true, false).forEach { inExecute ->
            val backend = FailingBackend(failInExecute = inExecute)
            val session = Session.login(BatchVectors.message("q"), BatchVectors.message("never read"))
            session.serve(backend)
            val name = if (inExecute) "execute" else "Strom"
            assertEquals(1, session.replies.size, name)
            assertEquals(internal, session.tokens(0).errorMessage(), name)
            assertEquals(DoneStatus.ERROR, session.tokens(0).lastDone().status, name)
            assertTrue(session.channel.closed, name)
            assertFalse(session.channel.exhausted, "$name: die naechste Nachricht wurde gelesen")
            assertEquals(ConnectionState.CLOSED, session.ready.live.context.state, name)
            assertEquals(if (inExecute) 0 else 1, backend.closeCalls, name)
        }
    }

    /**
     * Die Senke der Antwort nimmt ein Token an, das der Encoder schreibt, und lehnt eines ab, das er
     * nicht schreiben kann (ROW ohne vorheriges COLMETADATA); ein abgelehntes Token verändert die
     * Antwort nicht und löst den Beginn des Ergebnisses nicht aus.
     *
     * @spec SPEC-031
     */
    @Test
    fun `StreamedResponse lehnt ein Token ab, das der Encoder nicht schreibt`() = blocking {
        var started = 0
        val reply = CollectingReply()
        val response = StreamedResponse(reply) { started++ }
        assertFalse(response.accept(RowToken(emptyList())))
        assertEquals(0, reply.bytes.size)
        assertEquals(0, started)
        assertTrue(response.accept(ColMetadataToken(emptyList())))
        assertEquals(1, started)
        assertTrue(response.accept(DoneToken(DoneKind.DONE, DoneStatus.FINAL, 0, 0)))
        assertEquals(1, started)
    }
}
