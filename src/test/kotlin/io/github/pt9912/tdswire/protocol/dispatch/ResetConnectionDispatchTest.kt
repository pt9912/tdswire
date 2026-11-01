package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.execute
import io.github.pt9912.tdswire.protocol.dispatch.RpcWire.prepExec
import io.github.pt9912.tdswire.protocol.session.ConnectionState
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.WireSession
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handPacket
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RESETCONNECTION (`TDS-FA-07`, `TDS-QA-02`, `TDS-FA-04`, `SPEC-001`, `SPEC-030`, `SPEC-057`): Das Bit `0x08` im
 * ersten Paket einer Anfrage in `READY` leert die Handle-Tabelle der Sitzung vor der Verarbeitung dieser Anfrage.
 * Gegenstelle ist ein Fake-Backend (`ScriptedBackend`) und ein Rohbytes-Client; Handle-Werte, Zähler, Grenze und
 * Antwort ohne ENVCHANGE sind Festlegungen und weder am Referenzclient noch an einem SQL Server gemessen. Die Tests
 * belegen den Sitzungsrahmen, nicht den Pool des Clients.
 */
class ResetConnectionDispatchTest {
    private val oneRow = listOf(resultStart(), QueryEvent.Row(listOf(7)), QueryEvent.ResultEnd(1))

    private val unknownHandle = ServerMessage(50009, 1, 16, "Unknown prepared handle.")
    private val limitReached = ServerMessage(50010, 1, 16, "Prepared handle limit reached.")

    private fun rpc(payload: ByteArray) = BatchVectors.raw(PacketType.RPC, payload)

    private fun resetting(type: PacketType, payload: ByteArray): Inbound =
        Inbound.Message(TdsMessage(type, payload, resetConnection = true))

    private fun resettingBatch(text: String) = resetting(PacketType.SQL_BATCH, BatchVectors.payload(text))

    private fun resettingRpc(payload: ByteArray) = resetting(PacketType.RPC, payload)

    private fun isUnknown(session: Session, index: Int) = session.tokens(index).filterIsInstance<ErrorToken>()
        .singleOrNull()?.message == unknownHandle

    /**
     * Drei Handles sind vergeben; ein SQL_BATCH mit dem Bit wird wie jeder andere beantwortet (kein Fehler), und danach
     * ist jedes der drei Handles unbekannt: ERROR 50009 und DONE mit Fehlerstatus bei offener Verbindung, ohne das
     * Backend zu rufen. Der Folge-RPC wird beantwortet. Wächter: das Leeren entfernt, nach der Anfrage statt davor
     * ausgeführt oder auf einen Teil der Tabelle beschränkt.
     *
     * @spec SPEC-001
     * @spec SPEC-030
     * @spec SPEC-057
     */
    @Test
    fun `RESETCONNECTION macht jedes frueher vergebene Handle unbekannt, die Anfrage mit dem Bit wird beantwortet`() =
        blocking {
            val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
            val session = Session.login(
                rpc(prepExec(-1, "select 1", "")),
                rpc(prepExec(-1, "select 2", "")),
                rpc(prepExec(-1, "select 3", "")),
                resettingBatch("select 9"),
                rpc(execute(1)),
                rpc(execute(2)),
                rpc(execute(3)),
                rpc(prepExec(-1, "select 4", "")),
                Inbound.EndOfStream,
            )
            session.serve(backend)
            assertEquals(8, session.replies.size)
            assertTrue(session.tokens(3).none { it is ErrorToken }, "die Anfrage mit dem Bit bekommt ihre Antwort")
            (4..6).forEach {
                assertTrue(isUnknown(session, it), "Antwort $it")
                assertEquals(DoneStatus.ERROR, session.tokens(it).lastDone().status)
            }
            assertTrue(session.tokens(7).none { it is ErrorToken })
            assertEquals(
                listOf("select 1", "select 2", "select 3", "select 9", "select 4"),
                backend.requests.map { it.sql },
            )
            assertTrue(session.channel.exhausted)
        }

    /**
     * Ein `sp_prepexec` mit dem Bit liefert das neue Handle mit dem Wert 1 (der Zähler beginnt neu); es ist danach
     * gültig und führt den neuen Text aus, ein zweites `sp_prepexec` bekommt 2. Wächter: der Zähler läuft weiter
     * (Handle 4), das Leeren kommt nach der Anfrage (das neue Handle ist danach unbekannt).
     *
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Ein sp_prepexec mit dem Bit liefert das Handle 1, der Zaehler beginnt neu`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            rpc(prepExec(-1, "select 2", "")),
            resettingRpc(prepExec(-1, "select new", "")),
            rpc(execute(1)),
            rpc(prepExec(-1, "select next", "")),
            rpc(execute(2)),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(session.hex(0), session.hex(2), "Handle 1 vor und nach dem Zurücksetzen")
        assertEquals(session.hex(1), session.hex(4), "Handle 2 vor und nach dem Zurücksetzen")
        assertEquals(
            listOf("select 1", "select 2", "select new", "select new", "select next", "select next"),
            backend.requests.map { it.sql },
        )
        assertTrue(session.replies.indices.none { it > 2 && isUnknown(session, it) })
    }

    /**
     * Die Grenze von 1000 gilt neu: 1000 Handles sind gehalten, das `sp_prepexec` mit dem Bit und 999 weitere werden
     * angenommen (wieder 1000 gehalten), das nächste ergibt 50010. Wächter: die Tabelle behält ihre Einträge, die
     * Grenze zählt die alten mit.
     *
     * @spec SPEC-058
     * @spec SPEC-030
     */
    @Test
    fun `Nach RESETCONNECTION gelingen wieder 1000 Handles, das 1001 ergibt 50010`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val limit = PreparedHandles.MAX_HANDLES
        val before = (1..limit).map { rpc(prepExec(-1, "select $it", "")) }
        val after = (2..limit).map { rpc(prepExec(-1, "select again $it", "")) }
        val session = Session.login(
            *before.toTypedArray(),
            resettingRpc(prepExec(-1, "select again 1", "")),
            *after.toTypedArray(),
            rpc(prepExec(-1, "select over", "")),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(2 * limit + 1, session.replies.size)
        val accepted = session.replies.indices.take(2 * limit)
        assertTrue(accepted.none { index -> session.tokens(index).any { it is ErrorToken } })
        assertEquals(limitReached, session.tokens(2 * limit).filterIsInstance<ErrorToken>().single().message)
        assertEquals(2 * limit, backend.requests.size)
    }

    /**
     * Ohne das Bit bleiben die Handles gültig: ein SQL_BATCH und ein `sp_prepexec` ohne Bit lassen Handle 1 gelten
     * und den Zähler weiterlaufen (Handle 2). Wächter: ein Leeren bei jeder Anfrage.
     *
     * @spec SPEC-030
     * @spec SPEC-001
     */
    @Test
    fun `Ohne das Bit bleiben die Handles gueltig und der Zaehler laeuft weiter`() = blocking {
        val backend = ScriptedBackend.ofList(*oneRow.toTypedArray())
        val session = Session.login(
            rpc(prepExec(-1, "select 1", "")),
            BatchVectors.message("select batch"),
            rpc(prepExec(-1, "select 2", "")),
            rpc(execute(1)),
            rpc(execute(2)),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(5, session.replies.size)
        assertTrue(session.replies.indices.none { isUnknown(session, it) })
        assertEquals(
            listOf("select 1", "select batch", "select 2", "select 1", "select 2"),
            backend.requests.map { it.sql },
        )
    }

    /**
     * Das Bit kommt über den echten Transport an: ein Rohbytes-Client sendet `sp_prepexec` (Status `0x01`) und dann
     * `sp_execute` für Handle 1 im Status `0x09` (EOM und RESETCONNECTION); die zweite Anfrage trifft eine leere
     * Tabelle (ERROR 50009). Wächter: der Paket-Codec oder der Kanal gibt das Bit nicht weiter.
     *
     * @spec SPEC-001
     * @spec TDS-FA-07
     */
    @Test
    fun `Das Bit im Status des ersten Pakets erreicht den Sitzungsrahmen ueber den Transport`() = blocking {
        val session = WireSession.login(PACKET_SIZE, retain = true) { live = true }
        val serving = launch { session.serve(ScriptedBackend.ofList(*oneRow.toTypedArray())) }
        session.wire.feed(handPacket(PacketType.RPC.code, EOM, prepExec(-1, "select 1", "")))
        session.wire.feed(handPacket(PacketType.RPC.code, EOM or RESET_CONNECTION, execute(1)))
        session.wire.endInput()
        serving.join()
        val errors = TokenDecoder(session.wire.payload()).decodeAll().filterIsInstance<ErrorToken>()
        assertEquals(listOf(unknownHandle), errors.map { it.message })
    }

    /**
     * Das Bit löst nur an einer Anfrage in `READY` aus und läuft im Übergang unter der Sperre des Rahmens: der
     * Rahmen ruft das eingehängte Zurücksetzen genau einmal für die Anfrage mit dem Bit, nicht für eine Anfrage ohne,
     * nicht für ATTENTION mit dem Bit; eine zweite Anfrage in `RUNNING` ist unzulässig und schließt, ohne
     * zurückzusetzen.
     * Während das Zurücksetzen läuft, wartet ein zweiter Übergang auf die Sperre (gesteuert: die zweite Coroutine
     * startet im Zurücksetzen und kann nicht fertig werden, bevor es endet). Wächter: Zurücksetzen außerhalb der
     * Sperre, bei jeder Anfrage, an ATTENTION oder in `RUNNING`.
     *
     * @spec SPEC-001
     */
    @Test
    fun `Der Rahmen setzt nur an einer Anfrage in READY zurueck und haelt dabei seine Sperre`() = blocking {
        val session = Session.login()
        val live = session.ready.live
        var resets = 0
        var concurrentFinished = false
        var concurrent: Job? = null
        live.onSessionReset {
            resets++
            concurrent = launch(start = CoroutineStart.UNDISPATCHED) {
                live.dispatch(HandshakeEvent.LoginTimeout)
                concurrentFinished = true
            }
            assertFalse(concurrentFinished, "ein Übergang lief, während das Zurücksetzen die Sperre hielt")
        }
        live.dispatch(HandshakeEvent.Message(TdsMessage(PacketType.ATTENTION, ByteArray(0), resetConnection = true)))
        assertEquals(0, resets, "ATTENTION mit dem Bit setzt nicht zurück")
        live.dispatch(HandshakeEvent.Message(TdsMessage(PacketType.SQL_BATCH, ByteArray(0))))
        assertEquals(0, resets, "eine Anfrage ohne Bit setzt nicht zurück")
        live.dispatch(HandshakeEvent.RequestFinished(null, connectionOpen = true, replyWritten = true))
        live.dispatch(HandshakeEvent.Message(TdsMessage(PacketType.RPC, ByteArray(0), resetConnection = true)))
        assertEquals(1, resets)
        assertEquals(ConnectionState.RUNNING, live.context.state)
        live.dispatch(HandshakeEvent.Message(TdsMessage(PacketType.RPC, ByteArray(0), resetConnection = true)))
        assertEquals(1, resets, "eine Anfrage in RUNNING ist unzulässig, das Bit wirkt dort nicht")
        assertEquals(ConnectionState.CLOSED, live.context.state)
        concurrent?.join()
        assertTrue(concurrentFinished, "der wartende Übergang läuft nach dem Zurücksetzen")
    }

    private companion object {
        const val PACKET_SIZE = 4096
        const val EOM = 0x01
        const val RESET_CONNECTION = 0x08
    }
}
