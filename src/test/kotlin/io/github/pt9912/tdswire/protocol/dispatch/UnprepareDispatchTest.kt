package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.execute
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.prepExec
import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.protocol.error.ErrorLog
import io.github.pt9912.tdswire.protocol.error.ErrorLogEntry
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NOT_FOUND = "Could not find prepared statement with handle"

/**
 * Die Freigabe von Prepared-Handles als SQL-Batch `EXEC sp_unprepare <n>[;EXEC sp_unprepare <n>…]` (`TDS-FA-04`,
 * `SPEC-030`, `SPEC-060`, `SPEC-058`, `SPEC-001`) gegen das Test-Backend [ScriptedBackend]. Die Antwort-Bytes sind
 * Handvektoren. Gemessen sind Nummer 8179, Class 16, State 8, Text und die offene Verbindung für ein Handle, das die
 * Sitzung nicht hält (Zeile M-4 des Messprotokolls `docs/messungen/referenzabgleich.md`, am Client, 2022 und 2025),
 * und der Ablauf des gemischten Batches (M-7, am Client); Tokenfolge und DONE auf dem Draht, die Erfolgsantwort,
 * die Schreibweise der Form und das Verhalten nach RESETCONNECTION sind Festlegungen und nicht gemessen.
 */
class UnprepareDispatchTest {
    private val oneRow = listOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private fun rpc(payload: ByteArray) = BatchVectors.raw(PacketType.RPC, payload)

    private fun batch(text: String) = BatchVectors.message(text)

    private fun resettingBatch(text: String): Inbound =
        Inbound.Message(TdsMessage(PacketType.SQL_BATCH, BatchVectors.payload(text), resetConnection = true))

    /** Die Meldung des Servers für ein Handle, das die Sitzung nicht hält (gemessen: M-4). */
    private fun notFound(handle: Int) = ServerMessage(8179, 8, 16, "$NOT_FOUND $handle.")

    private fun Session.errors(index: Int) = tokens(index).filterIsInstance<ErrorToken>().map { it.message }

    private val unknownHandle = ServerMessage(50009, 1, 16, "Unknown prepared handle.")

    /** RETURNVALUE des Handles (Handvektor wie in `PreparedHandlesDispatchTest`): Ordinal 0, ohne Namen, Flag-Feld 0 */
    private fun handleValue(handle: Int) = "AC 00 00 00 01 00 00 00 00 00 00 26 04 04 ${RpcWire.le(handle.toLong(), 4)}"

    /**
     * Der Batch mit gehaltenen Handles gibt sie frei, ohne das Backend zu rufen: Antwort DONE ohne Bits (Erfolg ohne
     * Zeilen), danach sind beide Handles für `sp_execute` unbekannt; die Zählung steht bei null, und das nächste
     * `sp_prepexec` bekommt den nächsten Wert, nicht den freigegebenen. Wächter: ein Backend-Aufruf, ein Handle, das
     * gehalten bleibt, ein wiederverwendeter Wert, eine Fehlerantwort.
     *
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Der Batch gibt gehaltene Handles frei, ohne das Backend zu rufen`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(prepExec(-1, "select 2", "")),
            batch("EXEC sp_unprepare 1;EXEC sp_unprepare 2"),
            rpc(execute(1)),
            rpc(execute(2)),
            rpc(prepExec(-1, "select 3", "")),
            Inbound.EndOfStream,
        )
        val loop = session.loop(backend)
        loop.run()
        assertEquals(6, session.replies.size)
        assertEquals(BatchVectors.DONE_FINAL, session.hex(2))
        assertEquals(unknownHandle, session.errors(3).single())
        assertEquals(unknownHandle, session.errors(4).single())
        assertEquals(listOf("select 1", "select 2", "select 3"), backend.requests.map { it.sql })
        assertTrue(session.hex(5).contains(handleValue(3)), "Handle 3 vergeben, nicht ein freigegebener Wert")
        assertTrue(session.channel.exhausted)
    }

    /**
     * Ein Handle, das die Sitzung nicht hält, ergibt ERROR 8179 (Class 16, State 8, Text mit der Nummer) und DONE mit
     * Fehlerstatus, ohne das Backend zu rufen; die Verbindung bleibt offen, der Folge-RPC wird beantwortet, die
     * Zählung ändert sich nicht. Drei Fälle: nie vergeben (99), bereits freigegeben (1 ein zweites Mal), nach
     * RESETCONNECTION (das Handle 2 aus der Zeit davor). Das Log nennt je Fall die feste Marke
     * `unknown-prepared-handle`. Wächter: Erfolg statt Fehler, ein anderer State oder eine andere Nummer, ein
     * Text ohne die Nummer, ein Backend-Aufruf, ein Verbindungsende, ein Handle, das nach dem Fehler fehlt.
     *
     * @spec SPEC-060
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Ein unbekanntes Handle ergibt 8179 mit State 8 bei offener Verbindung`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(prepExec(-1, "select 2", "")),
            batch("EXEC sp_unprepare 99"),
            batch("EXEC sp_unprepare 1"),
            batch("EXEC sp_unprepare 1"),
            resettingBatch("EXEC sp_unprepare 2"),
            rpc(execute(2)),
            Inbound.EndOfStream,
        )
        val logged = mutableListOf<ErrorLogEntry>()
        val loop = session.loop(backend, ErrorMapper(ErrorLog { logged += it }))
        loop.run()
        assertEquals(7, session.replies.size)
        assertEquals(notFound(99), session.errors(2).single())
        assertEquals(BatchVectors.DONE_FINAL, session.hex(3))
        assertEquals(notFound(1), session.errors(4).single())
        assertEquals(notFound(2), session.errors(5).single())
        listOf(2, 4, 5).forEach {
            // Die Antwort auf die Anfrage mit RESETCONNECTION (5) beginnt mit ENVCHANGE Typ 18.
            assertEquals(if (it == 5) 3 else 2, session.tokens(it).size, "$it")
            assertEquals(DoneStatus.ERROR, session.tokens(it).lastDone().status, "$it")
        }
        assertEquals(unknownHandle, session.errors(6).single())
        assertEquals(listOf("select 1", "select 2"), backend.requests.map { it.sql })
        assertEquals(
            List(3) { ErrorLogEntry(ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE, "unknown-prepared-handle") },
            logged.filter { it.code == ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE },
        )
        assertTrue(session.channel.exhausted)
    }

    /**
     * Der Batch gehalten/unbekannt/gehalten (Setzung nach `ADR-0037` Entscheidung 3, am Client an der Referenzinstanz
     * bestätigt: M-7): beide gehaltenen Handles sind danach frei, genau ein ERROR 8179 für das unbekannte, ein DONE
     * mit Fehlerstatus; zwei unbekannte Nummern ergeben zwei ERROR in der Reihenfolge des Textes. Wächter: ein Abbruch
     * des Batches am ersten Fehler (das dritte Handle bleibt gehalten), ein Fehler je Anweisung statt je
     * unbekannter Nummer, eine andere Reihenfolge.
     *
     * @spec SPEC-060
     * @spec SPEC-030
     */
    @Test
    fun `Ein Batch mit gehalten, unbekannt, gehalten gibt beide frei und meldet einen Fehler`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(prepExec(-1, "select 2", "")),
            rpc(prepExec(-1, "select 3", "")),
            batch("EXEC sp_unprepare 1;EXEC sp_unprepare 99;EXEC sp_unprepare 2"),
            rpc(execute(1)),
            rpc(execute(2)),
            rpc(execute(3)),
            batch("EXEC sp_unprepare 98;EXEC sp_unprepare 3;EXEC sp_unprepare 97"),
            rpc(execute(3)),
            Inbound.EndOfStream,
        )
        val loop = session.loop(backend)
        loop.run()
        assertEquals(listOf(notFound(99)), session.errors(3))
        assertEquals(2, session.tokens(3).size)
        assertEquals(unknownHandle, session.errors(4).single())
        assertEquals(unknownHandle, session.errors(5).single())
        assertTrue(session.errors(6).isEmpty())
        assertEquals(listOf(notFound(98), notFound(97)), session.errors(7))
        assertEquals(3, session.tokens(7).size)
        assertEquals(unknownHandle, session.errors(8).single())
        assertEquals(4, backend.requests.size)
    }

    /**
     * Die Antwort auf eine Freigabe mit sehr vielen unbekannten Nummern ist begrenzt (`SPEC-060`, Setzung nach
     * `UnprepareBatch.MAX_REPORTED`): 50000 unbekannte Nummern vor einem gehaltenen Handle ergeben genau
     * `MAX_REPORTED` ERROR (die ersten, in der Reihenfolge des Textes), ein DONE mit Fehlerstatus und ebenso viele
     * Log-Einträge; das gehaltene Handle am Ende des Textes ist trotzdem frei. Wächter: eine Antwort mit einem ERROR je
     * Nummer (50000 Token), ein Log mit einem Eintrag je Nummer, eine Begrenzung, die die Bearbeitung der übrigen
     * Nummern abbricht (das Handle 1 bliebe gehalten).
     *
     * @spec SPEC-060
     * @spec SPEC-030
     */
    @Test
    fun `Sehr viele unbekannte Nummern ergeben eine begrenzte Antwort und ein begrenztes Log`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val count = 50_000
        val text = (1..count).joinToString(";") { "EXEC sp_unprepare ${it + 1}" } + ";EXEC sp_unprepare 1"
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            batch(text),
            rpc(execute(1)),
            Inbound.EndOfStream,
        )
        val logged = mutableListOf<ErrorLogEntry>()
        val loop = session.loop(backend, ErrorMapper(ErrorLog { logged += it }))
        loop.run()
        val limit = UnprepareBatch.MAX_REPORTED
        assertEquals((2..limit + 1).map { notFound(it) }, session.errors(1))
        assertEquals(limit + 1, session.tokens(1).size)
        assertEquals(DoneStatus.ERROR, session.tokens(1).lastDone().status)
        assertEquals(limit, logged.count { it.code == ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE })
        assertEquals(unknownHandle, session.errors(2).single())
    }

    /**
     * Ein Text, der nicht ganz die Form der Freigabe hat, geht unverändert an das Backend und gibt nichts frei:
     * gemischter Batch, andere Schreibweise, nichtnumerisches und negatives Argument, eine Zahl über `Int`. Das Handle
     * 1 bleibt danach gültig. Wächter: ein Leser, der Teile eines gemischten Textes freigibt oder die Schreibweise
     * lockert; ein Text, der verschluckt statt weitergereicht wird.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein Text anderer Form geht an das Backend und gibt nichts frei`() = blocking {
        val texts = listOf(
            "EXEC sp_unprepare 1; SELECT 1",
            "EXEC sp_unprepare 1;SELECT 1",
            "exec sp_unprepare 1",
            "EXECUTE sp_unprepare 1",
            "EXEC sp_unprepare x",
            "EXEC sp_unprepare -1",
            "EXEC sp_unprepare 99999999999",
            "EXEC sp_unprepare (1)",
            "EXEC sp_unprepare 1\n",
        )
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            *texts.map { batch(it) }.toTypedArray(),
            rpc(execute(1)),
            Inbound.EndOfStream,
        )
        val loop = session.loop(backend)
        loop.run()
        assertEquals(texts.size + 2, session.replies.size)
        assertEquals(listOf("select 1") + texts + "select 1", backend.requests.map { it.sql })
        assertTrue(session.errors(texts.size + 1).isEmpty())
    }

    /**
     * Der RPC `sp_unprepare` (ProcID 15) bleibt `TDS_UNSUPPORTED_RPC` und gibt nichts frei, auch wenn die Batch-Form
     * dasselbe Handle freigäbe. Wächter: ein RPC `sp_unprepare`, der Handles freigibt oder die Batch-Form im
     * RPC-Pfad erkennt.
     *
     * @spec SPEC-013
     * @spec SPEC-030
     */
    @Test
    fun `Der RPC sp_unprepare gibt nichts frei, die Batch-Form schon`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(RpcWire.procId(15)),
            rpc(execute(1)),
            batch("EXEC sp_unprepare 1"),
            rpc(execute(1)),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(ServerMessage(50002, 1, 16, "Unsupported RPC."), session.errors(1).single())
        assertTrue(session.errors(2).isEmpty())
        assertEquals(BatchVectors.DONE_FINAL, session.hex(3))
        assertEquals(unknownHandle, session.errors(4).single())
    }

    /**
     * Die Grenze ist einstellbar: bei 3 nimmt die Sitzung drei Handles, das vierte `sp_prepexec` ergibt 50010 ohne
     * Backend-Aufruf; gezählt werden die gleichzeitig gehaltenen Handles, also schafft die Batch-Freigabe eines Handles
     * Platz für genau ein weiteres (Wert 4), danach ist die Grenze wieder erreicht. Ein fehlgeschlagener Batch
     * (unbekanntes Handle) schafft keinen Platz. Wächter: eine Grenze, die auf 1000 steht oder die eingestellte
     * ignoriert; eine Zählung der je vergebenen Handles (die Freigabe schafft keinen Platz); eine Freigabe, die die
     * Zählung um mehr als eins senkt.
     *
     * @spec SPEC-058
     * @spec SPEC-030
     */
    @Test
    fun `Die eingestellte Grenze zaehlt gehaltene Handles und die Freigabe schafft Platz`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val limitReached = ServerMessage(50010, 1, 16, "Prepared handle limit reached.")
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(prepExec(-1, "select 2", "")),
            rpc(prepExec(-1, "select 3", "")),
            rpc(prepExec(-1, "select 4", "")),
            batch("EXEC sp_unprepare 99"),
            rpc(prepExec(-1, "select 4", "")),
            batch("EXEC sp_unprepare 2"),
            rpc(prepExec(-1, "select 5", "")),
            rpc(prepExec(-1, "select 6", "")),
            Inbound.EndOfStream,
        )
        val loop = session.loopWithLimit(backend, 3)
        loop.run()
        assertEquals(limitReached, session.errors(3).single())
        assertEquals(notFound(99), session.errors(4).single())
        assertEquals(limitReached, session.errors(5).single())
        assertEquals(BatchVectors.DONE_FINAL, session.hex(6))
        assertTrue(session.errors(7).isEmpty())
        assertEquals(limitReached, session.errors(8).single())
        assertEquals(listOf("select 1", "select 2", "select 3", "select 5"), backend.requests.map { it.sql })
        assertTrue(session.hex(7).contains(handleValue(4)), "Handle 4 vergeben, nicht ein freigegebener Wert")
    }

    /**
     * Zwei offene Sitzungen zählen und geben getrennt frei: die Freigabe von Handle 1 in Sitzung B (die es nicht
     * hält) ergibt 8179 und lässt Handle 1 in Sitzung A stehen. Wächter: eine Tabelle oder ein Zähler, die
     * Sitzungen teilen.
     *
     * @spec SPEC-030
     * @spec SPEC-060
     */
    @Test
    fun `Zwei Sitzungen zaehlen und geben getrennt frei`() = blocking {
        val backendA = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val backendB = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val sessionA = Session.login(rpc(prepExec(-1, "select a", "")))
        val loopA = sessionA.loop(backendA)
        val running = launch { loopA.run() }
        while (sessionA.replies.isEmpty()) yield()
        assertEquals(1, loopA.handles.size())

        val sessionB = Session.login(batch("EXEC sp_unprepare 1"), Inbound.EndOfStream)
        val loopB = sessionB.loop(backendB)
        loopB.run()
        assertEquals(notFound(1), sessionB.errors(0).single())
        assertEquals(1, loopA.handles.size())
        running.cancel()
        running.join()
    }

    /**
     * Die Freigabe von 1000 Handles in einem Batch (so viele, wie die Voreinstellung hält) gelingt in einer Antwort
     * ohne Fehler und leert die Tabelle; danach nimmt die Sitzung wieder 1000 Handles. Wächter: eine Obergrenze der
     * Anweisungen je Batch, eine Freigabe, die nur einen Teil erfasst.
     *
     * @spec SPEC-030
     * @spec SPEC-058
     */
    @Test
    fun `Ein Batch mit 1000 Handles leert die Tabelle`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val limit = PreparedHandles.DEFAULT_MAX_HANDLES
        val session = Session.login(
            *(1..limit).map { rpc(prepExec(-1, "select $it", "")) }.toTypedArray(),
            batch((1..limit).joinToString(";") { "EXEC sp_unprepare $it" }),
            rpc(prepExec(-1, "select again", "")),
            Inbound.EndOfStream,
        )
        val loop = session.loop(backend)
        loop.run()
        assertEquals(limit + 2, session.replies.size)
        assertEquals(BatchVectors.DONE_FINAL, session.hex(limit))
        assertTrue(session.errors(limit + 1).isEmpty())
    }
}
