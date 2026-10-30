package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.testsupport.blocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die Verbindungsfolge nach einem Fehler, über Rohbytes einer Sitzung mit dem Test-Backend
 * [ScriptedBackend] (`TDS-FA-06`, `TDS-QA-07`, `SPEC-001`, `SPEC-031`, `SPEC-048`): offen nach Class 16,
 * geschlossen nach der Antwort bei Class 20, geschlossen ohne Antwort bei nicht abschließbarem Strom.
 * Die Zuordnung der Codes zu Class und Folge ist Setzung des Nutzers, Festlegung oder Voreinstellung
 * (`ErrorCode`); die Nummern unten sind von Hand eingetragen. Gemessen am Referenzclient oder am echten Server ist hier
 * nichts. Als Kriterium für „nicht abschließbar“ gilt die Festlegung des Sequenzierers: die Senke nimmt
 * ein Token nicht an.
 */
class DispatchFateTest {
    private val oneRow = listOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private val oneRowReply = listOf(BatchVectors.COLMETADATA_N, BatchVectors.row(7), BatchVectors.DONE_COUNT_1)
        .joinToString(" ")

    private val twoRows = listOf(
        resultStart(),
        QueryEvent.Row(listOf(7)),
        QueryEvent.Row(listOf(8)),
        QueryEvent.ResultEnd(2),
    )

    private val rowsThenFailure = { code: String -> twoRows + QueryEvent.Failure(code, "kein Text fuer den Client") }

    /** Ereignisse, die ein Backend hinter einem Fehler noch liefert; sie gehören zu keiner Antwort. */
    private val behindError = listOf(resultStart(), QueryEvent.Row(listOf(99)), QueryEvent.ResultEnd(1))

    /** Die Tokens ab dem ERROR: genau ERROR und das DONE mit Fehlerstatus, nichts dahinter. */
    private fun List<Token>.fromError(): List<Token> = dropWhile { it !is ErrorToken }

    private fun Session.allSent(): List<Token> = replies.indices.flatMap { tokens(it) }

    /** Eine Senke, die jedes Token zählt, das ihr der Sequenzierer anbietet, und ab dem [rejectAt]-ten ablehnt. */
    private class CountingSink(private val inner: TokenSink, private val rejectAt: Int) : TokenSink {
        var offered = 0
            private set

        override suspend fun accept(token: Token): Boolean {
            offered++
            return if (offered >= rejectAt) false else inner.accept(token)
        }
    }

    /**
     * Nach einem Fehler der Class 16 (`OPEN`) erhält die unmittelbar folgende Anfrage nur ihre eigene
     * Antwort: genau die Bytes des Handvektors, ohne ERROR und ohne ein Token der Fehlerantwort. Die
     * Fehlerantwort davor hat genau einen ERROR und genau ein DONE mit Fehlerstatus als letztes Token.
     * Eingabe variiert: Fehler vor jeder Zeile, nach Teilzeilen, andere Codes der Class 16 (`TDS_SIM_INVALID_VALUE`
     * ist Voreinstellung, nicht entschieden); hinter dem Fehler liefert das Backend ein weiteres Ergebnis,
     * das nicht zur Antwort gehört (ab dem ERROR nur ERROR und DONE). Die Sitzung ist beim Lesen jeder
     * Nachricht `READY`.
     *
     * @spec SPEC-031
     * @spec SPEC-001
     */
    @Test
    fun `Nach einem Fehler der Class 16 erhaelt die naechste Anfrage nur ihre eigene Antwort`() = blocking {
        val failures = mapOf(
            "vor der ersten Zeile" to listOf<QueryEvent>(QueryEvent.Failure("TDS_UNSUPPORTED_SQL", "x")),
            "nach Teilzeilen" to rowsThenFailure("TDS_BACKEND_ERROR"),
            "anderer Code (Voreinstellung)" to listOf<QueryEvent>(QueryEvent.Failure("TDS_SIM_INVALID_VALUE", "x")),
        ).mapValues { it.value + behindError }
        failures.forEach { (name, failure) ->
            val backend = ScriptedBackend.inSequence(failure, oneRow)
            val session = Session.login(BatchVectors.message("bad"), BatchVectors.message("good"), Inbound.EndOfStream)
            val states = mutableListOf<ConnectionState>()
            session.channel.onReceive = { states += session.ready.live.context.state }
            session.serve(backend)
            assertEquals(
                listOf(ConnectionState.READY, ConnectionState.READY, ConnectionState.READY),
                states,
                "$name: Zustand beim Lesen jeder Nachricht, davon [1] vor der Folgeanfrage",
            )
            assertEquals(2, session.replies.size, name)
            assertEquals(1, session.tokens(0).count { it is ErrorToken }, name)
            assertEquals(2, session.tokens(0).fromError().size, "$name: hinter dem Fehler nur sein DONE")
            val failedDone = session.tokens(0).lastDone()
            assertEquals(DoneStatus.ERROR, failedDone.status and DoneStatus.ERROR, name)
            assertEquals(1, session.tokens(0).count { it is DoneToken && it.status and DoneStatus.ERROR != 0 }, name)
            assertEquals(oneRowReply, session.hex(1), name)
            assertEquals(0, session.tokens(1).count { it is ErrorToken }, name)
            assertEquals(listOf("bad", "good"), backend.requests.map { it.sql }, name)
            assertEquals(2, backend.closeCalls, name)
        }
    }

    /**
     * Fehler der Class 16 wiederholen sich, ohne dass die Verbindung schließt oder eine Antwort in die
     * nächste läuft: fünf Anfragen (Fehler, Erfolg, Fehler, Fehler, Erfolg) ergeben genau fünf Antworten
     * in der Reihenfolge, jede mit ihrer Form; der Zähler der Backend-Aufrufe ist 5. Hinter jedem Fehler
     * liefert das Backend weitere Ereignisse, die in keiner Antwort stehen.
     *
     * @spec SPEC-031
     * @spec TDS-QA-07
     */
    @Test
    fun `Eine Folge offener Fehler vermischt keine Antworten`() = blocking {
        val fail = listOf<QueryEvent>(QueryEvent.Failure("TDS_UNSUPPORTED_SQL", "x")) + behindError
        val partial = rowsThenFailure("TDS_BACKEND_ERROR") + behindError
        val backend = ScriptedBackend.inSequence(fail, oneRow, fail, partial, oneRow)
        val session = Session.login(
            *List(5) { BatchVectors.message("q$it") }.toTypedArray(),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(5, backend.requests.size)
        assertEquals(5, session.replies.size)
        val errorsPerReply = session.replies.indices.map { i -> session.tokens(i).count { it is ErrorToken } }
        assertEquals(listOf(1, 0, 1, 1, 0), errorsPerReply)
        val tailPerReply = session.replies.indices.map { i -> session.tokens(i).fromError().size }
        assertEquals(listOf(2, 0, 2, 2, 0), tailPerReply)
        assertEquals(oneRowReply, session.hex(1))
        assertEquals(oneRowReply, session.hex(4))
        assertEquals(5, session.allSent().count { it is DoneToken && it.status and DoneStatus.MORE == 0 })
    }

    /**
     * Ein Fehler der Class 20 nach Teilzeilen schließt nach der Antwort: die Antwort ist COLMETADATA, ROW,
     * ROW, DONE mit `MORE`, ERROR, DONE mit Fehlerstatus (genau ein ERROR, genau ein DONE mit Fehlerstatus
     * als letztes Token, kein zweites COLMETADATA), danach wird keine weitere Anfrage gelesen oder
     * angenommen und das Backend ist geschlossen. Eingabe variiert über zwei Wege in die Class 20: ein
     * `Failure` mit fremdem Code und ein Reihenfolgeverstoß des Stroms (`Row` ohne offenes Ergebnis).
     * Ein Code, den nur der Server selbst erzeugt (`TDS_LOGIN_FAILED`), läuft über denselben Weg wie der
     * fremde Code (`ErrorMapper.failure`) und ist deshalb kein eigener Fall. Hinter dem Fehler stehen im
     * Strom weitere Ereignisse (zweites Ergebnis), die nicht gelesen werden.
     *
     * @spec SPEC-031
     * @spec SPEC-048
     * @spec TDS-QA-07
     */
    @Test
    fun `Ein Fehler der Class 20 nach Teilzeilen schliesst nach genau einer Fehlerantwort`() = blocking {
        val paths = mapOf(
            "Failure mit fremdem Code" to rowsThenFailure("NOPE"),
            "Reihenfolgeverstoss" to twoRows + QueryEvent.Row(listOf(5)),
        )
        paths.forEach { (code, events) ->
            val backend = ScriptedBackend.ofList(*(events + behindError).toTypedArray())
            val never = BatchVectors.message("never")
            val session = Session.login(BatchVectors.message("q"), never, never)
            session.serve(backend)
            assertEquals(1, session.replies.size, code)
            val tokens = session.tokens(0)
            assertEquals(
                listOf("ColMetadata", "Row", "Row", "Done", "Error", "Done"),
                tokens.map { it::class.simpleName!!.removeSuffix("Token") },
                code,
            )
            assertEquals(1, tokens.count { it is ColMetadataToken }, code)
            assertEquals(2, tokens.count { it is RowToken }, code)
            val internal = ServerMessage(50007, 1, 20, "Internal error.")
            assertEquals(internal, tokens.filterIsInstance<ErrorToken>().single().message, code)
            assertEquals(DoneStatus.ERROR, tokens.lastDone().status, code)
            assertEquals(DoneStatus.COUNT or DoneStatus.MORE, (tokens[3] as DoneToken).status, code)
            assertEquals(1, backend.requests.size, code)
            assertEquals(1, backend.closeCalls, code)
            assertTrue(session.channel.closed, code)
            assertEquals(1, session.channel.receiveCalls - 2, "$code: Leseaufrufe nach dem Login")
            assertFalse(session.channel.exhausted, code)
            assertEquals(ConnectionState.CLOSED, session.ready.live.context.state, code)
        }
    }

    /**
     * Lässt sich der Strom nicht mehr abschließen (die Senke nimmt das k-te Token nicht an), geht keine
     * Antwort hinaus, kein ERROR und kein DONE; der Sequenzierer bietet der Senke nach der Ablehnung kein
     * weiteres Token an, also auch kein zweites COLMETADATA des zweiten Ergebnisses. Die Verbindung ist
     * geschlossen, die nächste Nachricht ungelesen, das Backend geschlossen. Eingabe variiert: k von 1 bis
     * 5 (COLMETADATA, ROW, ROW, DONE-Abschluss mit `MORE` durch das nächste Ereignis, zweites COLMETADATA).
     *
     * @spec SPEC-031
     * @spec TDS-QA-07
     */
    @Test
    fun `Ein nicht abschliessbarer Strom schliesst ohne Antwort und ohne weiteres Token`() = blocking {
        val twoResults = oneRow.dropLast(1) + QueryEvent.Row(listOf(8)) + QueryEvent.ResultEnd(2) + oneRow
        (1..5).forEach { k ->
            val backend = ScriptedBackend.ofList(*twoResults.toTypedArray())
            val session = Session.login(BatchVectors.message("q"), BatchVectors.message("never read"))
            var sink: CountingSink? = null
            session.serve(backend, sinkAround = { CountingSink(it, k).also { counting -> sink = counting } })
            assertEquals(k, checkNotNull(sink).offered, "k=$k: Tokens, die der Senke angeboten wurden")
            assertEquals(0, session.replies.size, "k=$k")
            assertTrue(session.channel.closed, "k=$k")
            assertFalse(session.channel.exhausted, "k=$k: die naechste Nachricht wurde gelesen")
            assertEquals(1, backend.closeCalls, "k=$k")
            assertEquals(ConnectionState.CLOSED, session.ready.live.context.state, "k=$k")
        }
    }
}
