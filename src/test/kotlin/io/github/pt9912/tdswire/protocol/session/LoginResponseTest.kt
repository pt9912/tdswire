package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.domain.Principal
import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.EnvChange
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketHeader
import io.github.pt9912.tdswire.transport.framing.PacketType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginResponseTest {
    private val loginContext = HandshakeContext(
        state = ConnectionState.AUTHENTICATING,
        pending = PendingLogin("d", 8000),
    )

    private fun accept(config: HandshakeConfig, pending: PendingLogin): HandshakeAction.Send {
        val context = loginContext.copy(pending = pending)
        val step = HandshakeAutomaton(config).step(context, HandshakeEvent.AuthAccepted(Principal("sa"), "s1"))
        return step.actions.filterIsInstance<HandshakeAction.Send>().single()
    }

    /**
     * Die Ablehnung als Handvektor (Tokenlayout nach MS-TDS, von Hand zusammengesetzt): ein ERROR 18456
     * mit State 1, Class 14 und dem festen Text "Login failed.", dann DONE mit Fehlerstatus `0x0002`.
     * State, Text und Statusbits sind Festlegung; gemessen ist nur "genau ein ERROR mit DONE".
     *
     * @spec SPEC-012
     */
    @Test
    fun `Die Ablehnung ist ein ERROR 18456 mit DONE als Handvektor`() {
        val expected = hex(
            "AA 28 00  18 48 00 00  01  0E  0D 00" +
                " 4C 00 6F 00 67 00 69 00 6E 00 20 00 66 00 61 00 69 00 6C 00 65 00 64 00 2E 00" +
                "  00  00  00 00 00 00" +
                "  FD 02 00 00 00 00 00 00 00 00 00 00 00",
        )
        val message = LoginResponses.rejection
        assertEquals(PacketType.TABULAR_RESULT, message.type)
        assertEquals(expected.toHex(), message.payload.toHex())
    }

    /**
     * Die Annahme als Token-Folge: ENVCHANGE Datenbank, Packet Size und Collation, LOGINACK, DONE ohne
     * Fehlerbit; die Tokens stammen aus dem Test-Decoder, nicht aus dem Encoder. Die Reihenfolge und die
     * Werte außer "LOGINACK mit DONE" und der Collation sind Festlegung.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Die Annahme sendet ENVCHANGE, LOGINACK und DONE`() {
        val send = accept(HandshakeConfig(), PendingLogin("meine_db", 8000))
        assertEquals(PacketType.TABULAR_RESULT, send.message.type)
        val tokens = TokenDecoder(send.message.payload).decodeAll()
        assertEquals(
            listOf(
                EnvChange.Database("meine_db", ""),
                EnvChange.PacketSize(8000, LoginResponses.DEFAULT_PACKET_SIZE),
                EnvChange.SqlCollation(Collation.LATIN1_GENERAL_CI_AS, null),
                LoginAck(1, 0x74000004, "tdswire", 0x01000000),
                DoneToken(DoneKind.DONE, DoneStatus.FINAL, 0, 0),
            ),
            tokens,
        )
    }

    /**
     * LOGINACK und DONE am Ende der Antwort als Handvektor (Layout nach MS-TDS): Interface 1, TDS-Version
     * `74 00 00 04`, Programmname "tdswire", Version `01 00 00 00`, dann DONE ohne Bits.
     *
     * @spec SPEC-028
     */
    @Test
    fun `LOGINACK und DONE als Handvektor am Ende der Antwort`() {
        val tail = hex(
            "AD 18 00  01  74 00 00 04  07 74 00 64 00 73 00 77 00 69 00 72 00 65 00  01 00 00 00" +
                "  FD 00 00 00 00 00 00 00 00 00 00 00 00",
        )
        val payload = accept(HandshakeConfig(), PendingLogin("d", 8000)).message.payload
        assertContentEquals(tail, payload.copyOfRange(payload.size - tail.size, payload.size))
    }

    /**
     * Die gesamte Annahme als Handvektor (Layout nach MS-TDS, von Hand abgeleitet, nicht aus dem Encoder)
     * für Datenbank `d` und Packet Size 8000: ENVCHANGE Typ 1 (Datenbank), Typ 4 (Packet Size, als
     * Zeichenfolge "8000" nach "4096") und Typ 7 (SQL-Collation `09 04 D0 00 34`), dann LOGINACK und DONE.
     * Reihenfolge und Werte sind Festlegung ohne Messung.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Die Annahme als vollstaendiger Handvektor mit den ENVCHANGE-Bytes`() {
        val expected = hex(
            "E3 05 00  01  01 64 00  00" +
                "  E3 13 00  04  04 38 00 30 00 30 00 30 00  04 34 00 30 00 39 00 36 00" +
                "  E3 08 00  07  05 09 04 D0 00 34  00" +
                "  AD 18 00  01  74 00 00 04  07 74 00 64 00 73 00 77 00 69 00 72 00 65 00  01 00 00 00" +
                "  FD 00 00 00 00 00 00 00 00 00 00 00 00",
        )
        val payload = accept(HandshakeConfig(), PendingLogin("d", 8000)).message.payload
        assertEquals(expected.toHex(), payload.toHex())
    }

    /**
     * Die Grenzen der angenommenen Packet Size: größer als der Paketkopf (8 Byte) und höchstens
     * `MAX_TDS_PACKET_BYTES` wird übernommen, jeder Wert außerhalb ergibt 4096. Festlegung ohne Messung.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Die Packet Size wird an beiden Grenzen entschieden`() {
        val max = FramingLimits.MAX_TDS_PACKET_BYTES
        val default = LoginResponses.DEFAULT_PACKET_SIZE
        assertEquals(default, LoginResponses.negotiatedPacketSize(PacketHeader.SIZE))
        assertEquals(PacketHeader.SIZE + 1, LoginResponses.negotiatedPacketSize(PacketHeader.SIZE + 1))
        assertEquals(max, LoginResponses.negotiatedPacketSize(max))
        assertEquals(default, LoginResponses.negotiatedPacketSize(max + 1))
    }

    /**
     * Ohne Datenbank im LOGIN7 gilt die Voreinstellung der Konfiguration; eine unbrauchbare verlangte
     * Packet Size wird durch 4096 ersetzt; im TDS-8.0-Pfad trägt LOGINACK die Version `0x08000000`.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Voreinstellungen fuer Datenbank, Packet Size und TDS-Version`() {
        val tokens = TokenDecoder(accept(HandshakeConfig(), PendingLogin("", 4)).message.payload).decodeAll()
        assertEquals(EnvChange.Database(HandshakeConfig.DEFAULT_DATABASE, ""), tokens[0])
        assertEquals(EnvChange.PacketSize(4096, 4096), tokens[1])
        val strict = HandshakeConfig(order = HandshakeOrder.TLS_FIRST, defaultDatabase = "x")
        val strictTokens = TokenDecoder(accept(strict, PendingLogin("", 70_000)).message.payload).decodeAll()
        assertEquals(EnvChange.Database("x", ""), strictTokens[0])
        assertEquals(EnvChange.PacketSize(4096, 4096), strictTokens[1])
        assertEquals(LoginAck(1, 0x08000000, "tdswire", 0x01000000), strictTokens[3])
    }

    /**
     * Die Annahme erzeugt eine Sitzung mit ID, Principal, Datenbankkontext und Packet Size und wechselt
     * die Rahmen-Grenze.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Die Annahme erzeugt die Sitzung und wechselt die Grenze`() {
        val step = HandshakeAutomaton().step(
            loginContext,
            HandshakeEvent.AuthAccepted(Principal("sa"), "s1"),
        )
        val session = step.context.session
        assertEquals("s1", session?.id)
        assertEquals(Principal("sa"), session?.principal)
        assertEquals("d", session?.database)
        assertEquals(8000, session?.packetSize)
        assertEquals(ConnectionState.READY, step.context.state)
        assertTrue(HandshakeAction.SwitchToSessionLimits in step.actions)
    }
}
