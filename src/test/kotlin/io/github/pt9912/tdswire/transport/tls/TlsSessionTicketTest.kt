package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.testsupport.TlsSetup
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.channel
import io.github.pt9912.tdswire.testsupport.exchangeMessages
import io.github.pt9912.tdswire.testsupport.runHandshake
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Das NewSessionTicket des Servers im TDS-8.0-Pfad mit TLS 1.3. Die Test-JVM setzt keine Option für die Zahl der
 * Tickets; das JDK sendet sein Voreinstellungs-Ticket nach dem Handschlag. Die Gegenstelle ist die JDK-`SSLEngine`
 * im selben Prozess, nicht der Referenzclient.
 */
class TlsSessionTicketTest {
    /** Die Typ-Bytes der TLS-Records in [wire], in der Reihenfolge des Drahts. */
    private fun recordTypes(wire: ByteArray): List<Int> {
        val types = mutableListOf<Int>()
        var at = 0
        while (at < wire.size) {
            types += wire[at].toInt() and 0xFF
            at += RECORD_HEADER + (((wire[at + 3].toInt() and 0xFF) shl 8) or (wire[at + 4].toInt() and 0xFF))
        }
        assertEquals(wire.size, at, "die Bytes enden auf einer Recordgrenze")
        return types
    }

    /**
     * Nach dem Handschlag des 8.0-Pfads (beide Seiten fertig) steht auf dem Draht des Servers hinter seinem
     * Handschlag-Flug (ServerHello, ChangeCipherSpec, verschlüsselter Flug) ein vierter Record; das ist das
     * NewSessionTicket (der Inhalt ist verschlüsselt und nicht gelesen). Wann in der Engine der Record entsteht, ist
     * nicht beobachtet. Die Gegenstelle nimmt es an, und der Austausch von Nachrichten in beide Richtungen gelingt
     * danach. Die Zahl der Records ist die des JDK 21 der Testumgebung.
     *
     * @spec SPEC-023 SPEC-028
     */
    @Test
    fun `the 8_0 path sends a session ticket after the handshake and the exchange works`() = blocking {
        val outcome = runHandshake(TlsSetup(TlsPath.FROM_FIRST_BYTE, listOf("TLSv1.3")))
        val server = outcome.channel()
        assertEquals(
            listOf(TLS_HANDSHAKE, TLS_CHANGE_CIPHER_SPEC, TLS_APPLICATION_DATA, TLS_APPLICATION_DATA),
            recordTypes(outcome.serverWire.written),
        )
        assertEquals("TLSv1.3", server.protocol)
        exchangeMessages(server, outcome.peer)
    }

    private companion object {
        const val RECORD_HEADER = 5
        const val TLS_HANDSHAKE = 0x16
        const val TLS_CHANGE_CIPHER_SPEC = 0x14
        const val TLS_APPLICATION_DATA = 0x17
    }
}
