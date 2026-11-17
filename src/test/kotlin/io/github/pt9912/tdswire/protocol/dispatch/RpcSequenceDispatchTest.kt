package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.byName
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.executeSql
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.int4
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.param
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.testsupport.RpcVectors
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.PacketType
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mehrere RPCs in einer Nachricht (`SPEC-030`, `TDS-FA-04`) gegen das Test-Backend [ScriptedBackend]: je Aufruf ein
 * Aufruf des Backends, eine Antwort mit den Antworten der Aufrufe hintereinander. Die Form der Antwort (DONEPROC mit
 * `MORE|0x80` an jedem Aufruf außer dem letzten) ist am echten SQL Server gemessen (Messreihe am Draht `rpc-batch-*`,
 * 2022 und 2025, ein `SqlDataAdapter` mit `UpdateBatchSize`); die Handvektoren hier sind davon abgeleitet. Nicht
 * gemessen: der Fehler vor dem ersten Ergebnis eines Aufrufs vor dem Ende, `sp_prepexec` und `sp_execute` darin.
 */
class RpcSequenceDispatchTest {
    private val headerBytes = hex(RpcVectors.HEADERS).size
    private val update = listOf<QueryEvent>(QueryEvent.UpdateCount(1))

    private fun rpc(payload: ByteArray) = BatchVectors.raw(PacketType.RPC, payload)

    private fun joined(vararg messages: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(messages.first())
        messages.drop(1).forEach {
            out.write(0xFF)
            out.write(it, headerBytes, it.size - headerBytes)
        }
        return out.toByteArray()
    }

    private fun insert(id: Int) = executeSql("insert @Id", "@Id int", param("@Id", int4(id)))

    private fun doneProcs(session: Session): List<Int> =
        session.tokens(0).filterIsInstance<DoneToken>().filter { it.kind == DoneKind.DONE_PROC }.map { it.status }

    /**
     * Zwei `sp_executesql` in einer Nachricht rufen das Backend je einmal, der Reihe nach, mit derselben Kennung der
     * Anfrage und dem Text und den Parametern des jeweiligen Aufrufs. Die Antwort ist eine Nachricht: je Aufruf
     * DONEINPROC, RETURNSTATUS, DONEPROC; das DONEPROC des ersten trägt `MORE|RPC_IN_BATCH` (`0x0081`), das des
     * letzten keine Bits (gemessen). Die Folgeanfrage bekommt ihre eigene Antwort. Rot, wenn die Schleife nur den
     * ersten Aufruf ausführt oder das DONEPROC des ersten ohne die Bits schreibt.
     *
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Zwei Aufrufe rufen das Backend je einmal und beantworten in einer Nachricht`() = blocking {
        val backend = ScriptedBackend.ofList(*update.toTypedArray())
        val session = Session.login(rpc(joined(insert(1), insert(2))), rpc(insert(3)), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(listOf("r1", "r1", "r2"), backend.requests.map { it.requestId })
        assertEquals(List(3) { "insert @Id" }, backend.requests.map { it.sql })
        assertEquals(
            listOf(1, 2, 3),
            backend.requests.map { (it.parameters.single().value as Int) },
        )
        assertEquals(2, session.replies.size)
        assertEquals(listOf(0x0081, 0x0000), doneProcs(session))
        assertEquals(
            listOf(DoneKind.DONE_IN_PROC, DoneKind.DONE_PROC, DoneKind.DONE_IN_PROC, DoneKind.DONE_PROC),
            session.tokens(0).filterIsInstance<DoneToken>().map { it.kind },
        )
        assertEquals(listOf(0, 0), session.tokens(0).filterIsInstance<ReturnStatusToken>().map { it.value })
        assertEquals(DoneStatus.FINAL, (session.tokens(1).last() as DoneToken).status)
        assertEquals(3, backend.closeCalls)
        assertTrue(session.channel.closed)
    }

    /**
     * Der Wert eines Ausgabeparameters steht je Aufruf mit dem Ordinal seiner Stelle in der Aufrufliste, nicht
     * fortlaufend über die Aufrufe (gemessen: Ordinal 4 in beiden Aufrufen, `rpc-batch-ausgabewert`).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Das Ordinal der RETURNVALUE zaehlt je Aufruf`() = blocking {
        val output = listOf<QueryEvent>(QueryEvent.UpdateCount(1), QueryEvent.OutputParameter("@N", SqlType.Int32, 5))
        val call = executeSql(
            "insert @Id",
            "@Id int, @N int output",
            param("@Id", int4(1)),
            param("@Text", RpcWire.nvarchar("r1")),
            param("@N", "26 04 00", status = 1),
        )
        val backend = ScriptedBackend.ofList(*output.toTypedArray())
        val session = Session.login(rpc(joined(call, call)), Inbound.EndOfStream)
        session.serve(backend)
        val values = session.tokens(0).filterIsInstance<ReturnValueToken>()
        assertEquals(listOf(4, 4), values.map { it.ordinal })
        assertEquals(listOf("@N", "@N"), values.map { it.name })
    }

    /**
     * Ein Fehler, der die Verbindung offen lässt, beendet nur seinen Aufruf: der dritte Aufruf läuft (gemessen:
     * der Aufruf nach dem Schlüsselverstoß wird ausgeführt). Der Fehlerabschluss des zweiten Aufrufs trägt die Bits des
     * Aufrufs, der dritte schließt ohne Bits; die Verbindung bleibt offen und beantwortet die Folgeanfrage. Rot, wenn
     * die Schleife nach dem ersten Fehler abbricht (dann fehlt der dritte Aufruf des Backends).
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein Fehler beendet nur seinen Aufruf und die weiteren laufen`() = blocking {
        val failure = listOf<QueryEvent>(QueryEvent.Failure("TDS_UNSUPPORTED_SQL", "x"))
        val backend = ScriptedBackend.inSequence(update, failure, update, update)
        val session = Session.login(rpc(joined(insert(1), insert(2), insert(3))), rpc(insert(4)), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(listOf(1, 2, 3, 4), backend.requests.map { it.parameters.single().value })
        assertEquals(1, session.tokens(0).filterIsInstance<ErrorToken>().size)
        assertEquals(listOf(0x0081, 0x0083, 0x0000), doneProcs(session))
        assertEquals(2, session.replies.size)
    }

    /**
     * Ein Fehler, der die Verbindung schließt (hier ein Verstoß des Backends gegen die Reihenfolge der Ereignisse,
     * `SPEC-048`), beendet die Nachricht: der dritte Aufruf erreicht das Backend nicht, und das DONEPROC des Fehlers
     * trägt kein `MORE`, damit der Client nicht auf weitere Tokens wartet. Rot, wenn die Schleife den dritten Aufruf
     * ausführt oder `MORE` am letzten geschriebenen DONEPROC steht.
     *
     * @spec SPEC-030
     * @spec SPEC-048
     */
    @Test
    fun `Ein Fehler mit Verbindungsende beendet die Nachricht ohne MORE`() = blocking {
        val violation = listOf<QueryEvent>(QueryEvent.Row(listOf(1)))
        val backend = ScriptedBackend.inSequence(update, violation, update)
        val session = Session.login(rpc(joined(insert(1), insert(2), insert(3))), rpc(insert(4)))
        session.serve(backend)
        assertEquals(2, backend.requests.size)
        assertEquals(1, session.replies.size)
        val last = session.tokens(0).last() as DoneToken
        assertEquals(DoneKind.DONE_PROC, last.kind)
        assertEquals(DoneStatus.ERROR, last.status)
        assertEquals(listOf(0x0081, 0x0002), doneProcs(session))
        assertTrue(session.channel.closed)
    }

    /**
     * Eine Nachricht, deren einer Aufruf abgelehnt wird (hier `sp_prepexec`, nicht in der Nachricht gebaut; ebenso eine
     * ProcID außerhalb des Katalogs), erreicht das Backend mit keinem Aufruf: ERROR `TDS_UNSUPPORTED_RPC` und DONEPROC
     * mit Fehlerstatus, die Verbindung bleibt offen.
     *
     * @spec SPEC-013
     * @spec SPEC-030
     */
    @Test
    fun `Ein abgelehnter Aufruf lehnt die ganze Nachricht ab und das Backend wird nicht gerufen`() = blocking {
        val prepare = RpcWire.prepExec(0, "insert @Id", "@Id int", param("@Id", int4(2)))
        val rejected = mapOf(
            "sp_prepexec" to joined(insert(1), prepare),
            "ProcID ausserhalb" to joined(insert(1), RpcWire.procId(11)),
            "Name im Katalog" to joined(insert(1), byName("sp_cursoropen")),
        )
        rejected.forEach { (name, payload) ->
            val backend = ScriptedBackend.ofList(*update.toTypedArray())
            val session = Session.login(rpc(payload), rpc(insert(9)), Inbound.EndOfStream)
            session.serve(backend)
            assertEquals(2, session.replies.size, name)
            assertTrue(session.hex(0).endsWith(BatchVectors.DONEPROC_ERROR), name)
            assertEquals(1, backend.requests.size, name)
            assertEquals(9, backend.requests.single().parameters.single().value, name)
        }
    }

    /**
     * Ein Trennbyte ohne folgenden Aufruf ist ein Fehler des Parsers: ERROR und DONE (nicht DONEPROC), die Verbindung
     * schließt, das Backend wird nicht gerufen.
     *
     * @spec SPEC-011
     */
    @Test
    fun `Ein Trennbyte ohne Aufruf ergibt den Protokollfehler und schliesst`() = blocking {
        val backend = ScriptedBackend.ofList(*update.toTypedArray())
        val session = Session.login(rpc(insert(1) + byteArrayOf(0xFF.toByte())), rpc(insert(2)))
        session.serve(backend)
        assertEquals(0, backend.requests.size)
        assertEquals(1, session.replies.size)
        assertEquals(DoneKind.DONE, (session.tokens(0).last() as DoneToken).kind)
        assertFalse(session.channel.sent.isEmpty())
        assertTrue(session.channel.closed)
    }

    /**
     * Alle Aufrufe einer Nachricht tragen dieselbe Sitzung und dieselbe Frist (Beginn der Nachricht plus
     * `QUERY_TIMEOUT`), auch wenn die Uhr zwischen den Aufrufen weiterläuft (hier um 1000 ms je Lesen). Rot, wenn die Frist
     * je Aufruf neu berechnet wird.
     *
     * @spec SPEC-009
     * @spec SPEC-030
     */
    @Test
    fun `Alle Aufrufe einer Nachricht tragen dieselbe Frist und Sitzung`() = blocking {
        var tick = FIXED_NOW
        val times = RequestTimes(now = { tick.also { tick += 1000 } })
        val backend = ScriptedBackend.ofList(*update.toTypedArray())
        val session = Session.login(rpc(joined(insert(1), insert(2), insert(3))), Inbound.EndOfStream)
        session.serve(backend, times = times)
        assertEquals(3, backend.requests.size)
        assertEquals(listOf(DEFAULT_DEADLINE), backend.requests.map { it.deadlineEpochMillis }.distinct())
        assertEquals(listOf("s1"), backend.requests.map { it.sessionId }.distinct())
    }

    /**
     * Die gemessene Form eines Fehlers nach dem Beginn einer Anweisung (W-2 und W-13): ERROR, DONEINPROC `MORE|ERROR`,
     * RETURNSTATUS mit der SQL-Server-Nummer des Fehlers (hier 2627), DONEPROC `MORE|0x80` ohne Fehlerbit; der Aufruf
     * danach läuft. Rot, wenn der Fehlerpfad das Bit `0x80` oder `MORE` am DONEPROC nicht schreibt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein Fehler nach dem Beginn einer Anweisung traegt die gemessene Form mit MORE und 0x80`() = blocking {
        val key = QueryEvent.Failure("TDS_SIM_KEY_VIOLATION", "x")
        val failing = listOf(resultStart(), QueryEvent.Row(listOf(7)), key)
        val backend = ScriptedBackend.inSequence(update, failing, update)
        val session = Session.login(rpc(joined(insert(1), insert(2), insert(3))), Inbound.EndOfStream)
        session.serve(backend)
        assertEquals(3, backend.requests.size)
        val tokens = session.tokens(0)
        assertEquals(listOf(0, 2627, 0), tokens.filterIsInstance<ReturnStatusToken>().map { it.value })
        assertEquals(listOf(0x0081, 0x0081, 0x0000), doneProcs(session))
        val inProc = tokens.filterIsInstance<DoneToken>().filter { it.kind == DoneKind.DONE_IN_PROC }.map { it.status }
        val counted = DoneStatus.MORE or DoneStatus.COUNT
        assertEquals(listOf(counted, DoneStatus.MORE or DoneStatus.ERROR, counted), inProc)
    }
}
