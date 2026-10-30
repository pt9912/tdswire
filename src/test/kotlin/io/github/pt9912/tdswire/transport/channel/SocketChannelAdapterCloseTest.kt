package io.github.pt9912.tdswire.transport.channel

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tests von `SocketChannelAdapter.close()` über einen Kanal, dessen Schließen fehlschlägt (`FailingCloseChannel`). */
class SocketChannelAdapterCloseTest {
    /** Eine `IOException` beim Schließen ist verschluckt: `close()` kehrt ohne Ausnahme zurück. */
    @Test
    fun `IOException beim Schliessen wird verschluckt`() {
        val failing = FailingCloseChannel(IOException("zu"))
        SocketChannelAdapter(failing).close()
        assertEquals(1, failing.closeCalls())
    }

    /** Andere Ausnahmen als `IOException` treten aus `close()` aus, sie werden nicht verschluckt. */
    @Test
    fun `Andere Ausnahmen beim Schliessen treten aus`() {
        val failing = FailingCloseChannel(IllegalStateException("kaputt"))
        assertFailsWith<IllegalStateException> { SocketChannelAdapter(failing).close() }
    }
}
