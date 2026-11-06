package io.github.pt9912.tdswire.transport.messaging

import io.github.pt9912.tdswire.stability.ScriptedChannel
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.handMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Meldung „Nachricht unvollständig“ des Lesers (`SPEC-044` Nr. 2): sie steht, solange Bytes einer nicht vollendeten
 * Nachricht gepuffert sind, und fällt mit dem Warten auf die nächste Nachricht oder dem Ende des Stroms.
 */
class MessageReaderMidMessageTest {
    private val first = handMessage(0x01, ByteArray(20) { it.toByte() }, 8)
    private val second = handMessage(0x01, ByteArray(20) { (it + 1).toByte() }, 8)

    /**
     * Vollendet ein Lesevorgang eine Nachricht und hinterlässt den Anfang der nächsten, meldet der Leser `true`; das
     * Ende des Stroms mitten in der Nachricht meldet `false`.
     *
     * @spec SPEC-044
     */
    @Test
    fun `ein Rest einer unvollstaendigen Nachricht wird gemeldet`() = blocking {
        val reported = mutableListOf<Boolean>()
        val channel = ScriptedChannel(listOf(first + second.copyOf(10)))
        val reader = MessageReader(channel, onMidMessage = { reported += it })
        assertIs<ReadOutcome.Message>(reader.next())
        assertTrue(reported.last(), "Rest gepuffert: $reported")
        assertIs<ReadOutcome.Failed>(reader.next())
        assertFalse(reported.last(), "Ende des Stroms: $reported")
    }

    /**
     * Kommt jede Nachricht in einem Stück, wird nie `true` gemeldet; der Leser ist zwischen den Nachrichten ruhend.
     *
     * @spec SPEC-044
     */
    @Test
    fun `vollstaendig eintreffende Nachrichten melden keinen Rest`() = blocking {
        val reported = mutableListOf<Boolean>()
        val channel = ScriptedChannel(listOf(first, second))
        val reader = MessageReader(channel, onMidMessage = { reported += it })
        assertIs<ReadOutcome.Message>(reader.next())
        assertIs<ReadOutcome.Message>(reader.next())
        assertEquals(setOf(false), reported.toSet())
    }

    /**
     * Ein Rest in zwei Lesevorgängen: erst `true`; die Meldung `false` kommt erst, wenn der Leser auf die nächste
     * Nachricht wartet, nicht schon mit dem Vollenden der vorigen.
     *
     * @spec SPEC-044
     */
    @Test
    fun `die Meldung false kommt mit dem Warten auf die naechste Nachricht`() = blocking {
        val reported = mutableListOf<Boolean>()
        val reader = MessageReader(
            ScriptedChannel(listOf(first.copyOf(12), first.copyOfRange(12, first.size))),
            onMidMessage = { reported += it },
        )
        assertTrue(reported.isEmpty() || reported.all { !it })
        assertIs<ReadOutcome.Message>(reader.next())
        assertTrue(reported.last(), "bei der Rückgabe der vollendeten Nachricht steht der Rest-Zustand noch: $reported")
        reader.next()
        assertFalse(reported.last())
    }
}
