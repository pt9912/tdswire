package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.config.auth.NegotiatedEncryption
import io.github.pt9912.tdswire.protocol.session.SessionVectors.ENCRYPT_NOT_SUP
import io.github.pt9912.tdswire.protocol.session.SessionVectors.ENCRYPT_OFF
import io.github.pt9912.tdswire.protocol.session.SessionVectors.ENCRYPT_ON
import io.github.pt9912.tdswire.protocol.session.SessionVectors.ENCRYPT_REQ
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PreloginNegotiationTest {
    private fun prelogin(config: HandshakeConfig, payload: ByteArray): Transition {
        val event = HandshakeEvent.Message(TdsMessage(PacketType.PRELOGIN, payload))
        return HandshakeAutomaton(config).step(HandshakeContext(ConnectionState.PRELOGIN), event)
    }

    private fun closedWith(step: Transition, reason: CloseReason) {
        assertEquals(ConnectionState.CLOSED, step.context.state)
        assertEquals(listOf(HandshakeAction.Close(reason)), step.actions)
    }

    private fun responseEncryption(step: Transition): Int {
        val send = step.actions.filterIsInstance<HandshakeAction.Send>().single()
        assertEquals(PacketType.TABULAR_RESULT, send.message.type)
        return send.message.payload[ENCRYPTION_BYTE_INDEX].toInt()
    }

    /**
     * Die Antwort bei `REQUIRED` als Handvektor: VERSION `0.0.0.0`, ENCRYPT_REQ, leerer INSTOPT, MARS
     * aus; Tabelle mit Offsets ab Nachrichtenanfang (Festlegung, nicht am Referenzclient gemessen).
     *
     * @spec SPEC-028
     * @spec SPEC-010
     */
    @Test
    fun `REQUIRED antwortet ENCRYPT_REQ und beginnt TLS nach der Antwort`() {
        val step = prelogin(HandshakeConfig(), SessionVectors.preloginPayload(ENCRYPT_ON))
        assertEquals(ConnectionState.TLS_NEGOTIATION, step.context.state)
        assertEquals(NegotiatedEncryption.REQUIRED, step.context.encryption)
        assertEquals(listOf("Send", "BeginTls"), step.actions.map { it.label() })
        val send = step.actions.first() as HandshakeAction.Send
        assertContentEquals(
            hex("00 00 15 00 06  01 00 1B 00 01  02 00 1C 00 01  04 00 1D 00 01  FF  00 00 00 00 00 00  03  00  00"),
            send.message.payload,
        )
    }

    /**
     * `REQUIRED` nimmt Clients mit ENCRYPT_OFF, ENCRYPT_ON und ENCRYPT_REQ an und antwortet immer ENCRYPT_REQ.
     *
     * @spec SPEC-010
     */
    @Test
    fun `REQUIRED nimmt OFF, ON und REQ des Clients an`() {
        listOf(ENCRYPT_OFF, ENCRYPT_ON, ENCRYPT_REQ).forEach { client ->
            val step = prelogin(HandshakeConfig(), SessionVectors.preloginPayload(client))
            assertEquals(ConnectionState.TLS_NEGOTIATION, step.context.state, "Client $client")
            assertEquals(ENCRYPT_REQ, responseEncryption(step), "Client $client")
        }
    }

    /**
     * Kein stilles Downgrade: ein Client ohne Verschlüsselung (NOT_SUP), ohne ENCRYPTION-Option oder mit
     * unbekanntem Wert ist unvereinbar mit `REQUIRED`; der Server bricht vor LOGIN7 ab und antwortet nicht.
     *
     * @spec SPEC-010
     * @spec SPEC-028
     */
    @Test
    fun `REQUIRED bricht bei unvereinbarer Client-Anforderung vor LOGIN7 ab`() {
        listOf(ENCRYPT_NOT_SUP, 0x09, null).forEach { client ->
            val step = prelogin(HandshakeConfig(), SessionVectors.preloginPayload(client))
            closedWith(step, CloseReason.ENCRYPTION_INCOMPATIBLE)
        }
    }

    /**
     * Der Testmodus antwortet ENCRYPT_NOT_SUP, geht ohne TLS nach LOGIN7 und meldet die Abschwächung im Log.
     *
     * @spec SPEC-010
     */
    @Test
    fun `Testmodus antwortet ENCRYPT_NOT_SUP ohne TLS und meldet die Abschwaechung`() {
        val config = HandshakeConfig(encryptionPolicy = EncryptionPolicy.TEST_UNENCRYPTED)
        listOf(ENCRYPT_OFF, ENCRYPT_NOT_SUP).forEach { client ->
            val step = prelogin(config, SessionVectors.preloginPayload(client))
            assertEquals(ConnectionState.LOGIN7, step.context.state, "Client $client")
            assertEquals(NegotiatedEncryption.NOT_SUPPORTED, step.context.encryption)
            assertEquals(listOf("Send", "Log"), step.actions.map { it.label() })
            assertEquals(ENCRYPT_NOT_SUP, responseEncryption(step))
            assertEquals(HandshakeAction.Log(LogEntry.EncryptionNotOffered), step.actions.last())
        }
    }

    /**
     * Im Testmodus ist ein Client, der Verschlüsselung anfordert (ON, REQ), unvereinbar; es folgt kein LOGIN7.
     *
     * @spec SPEC-010
     */
    @Test
    fun `Testmodus bricht bei Client mit ON oder REQ ab`() {
        val config = HandshakeConfig(encryptionPolicy = EncryptionPolicy.TEST_UNENCRYPTED)
        listOf(ENCRYPT_ON, ENCRYPT_REQ, null).forEach { client ->
            closedWith(prelogin(config, SessionVectors.preloginPayload(client)), CloseReason.ENCRYPTION_INCOMPATIBLE)
        }
    }

    /**
     * Der Testmodus gilt nur in der Reihenfolge PRELOGIN vor TLS (`SPEC-028`: nur TDS 7.x).
     *
     * @spec SPEC-028
     */
    @Test
    fun `Testmodus mit TLS vor PRELOGIN ist keine gueltige Konfiguration`() {
        val failure = runCatching {
            HandshakeConfig(order = HandshakeOrder.TLS_FIRST, encryptionPolicy = EncryptionPolicy.TEST_UNENCRYPTED)
        }.exceptionOrNull()
        assertIs<IllegalArgumentException>(failure)
    }

    /**
     * TDS-8.0-Reihenfolge: TLS besteht schon, PRELOGIN geht ohne weiteren Handschlag nach LOGIN7; die
     * ENCRYPTION-Option des Clients wird nicht ausgewertet (Festlegung ohne Messung).
     *
     * @spec SPEC-028
     */
    @Test
    fun `TLS zuerst antwortet ohne weiteren Handschlag`() {
        val config = HandshakeConfig(order = HandshakeOrder.TLS_FIRST)
        val step = prelogin(config, SessionVectors.preloginPayload(ENCRYPT_NOT_SUP))
        assertEquals(ConnectionState.LOGIN7, step.context.state)
        assertEquals(listOf("Send"), step.actions.map { it.label() })
        assertEquals(ENCRYPT_REQ, responseEncryption(step))
    }

    /**
     * Eine PRELOGIN-Nachricht über `MAX_PRELOGIN_MESSAGE_BYTES` wird abgewiesen, ohne Antwort; genau an
     * der Grenze wird sie noch gelesen (und scheitert dann nur am Inhalt).
     *
     * @spec SPEC-046
     */
    @Test
    fun `PRELOGIN ueber der Vor-Login-Grenze wird abgewiesen`() {
        val limit = FramingLimits.MAX_PRELOGIN_MESSAGE_BYTES
        closedWith(prelogin(HandshakeConfig(), ByteArray(limit + 1)), CloseReason.MESSAGE_TOO_LARGE)
        closedWith(prelogin(HandshakeConfig(), ByteArray(limit)), CloseReason.PROTOCOL_VIOLATION)
    }

    /**
     * Eine ungültige PRELOGIN-Tabelle (kein Terminator) schließt ohne Antwort.
     *
     * @spec SPEC-028
     */
    @Test
    fun `Ungueltiges PRELOGIN schliesst ohne Antwort`() {
        closedWith(prelogin(HandshakeConfig(), hex("00 00 0B 00 06")), CloseReason.PROTOCOL_VIOLATION)
        closedWith(prelogin(HandshakeConfig(), ByteArray(0)), CloseReason.PROTOCOL_VIOLATION)
    }

    private companion object {
        const val ENCRYPTION_BYTE_INDEX = 21 + 6
    }
}
