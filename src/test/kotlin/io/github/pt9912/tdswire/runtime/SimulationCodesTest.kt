package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.simulation.SimCodes
import kotlin.test.Test
import kotlin.test.assertEquals

/** `runtime` kennt `simulation` und `protocol`; hier hält ein Test die Namen der Fehlercodes gegeneinander. */
internal class SimulationCodesTest {
    /**
     * Die Namen in `SimCodes` sind Namen von `ErrorCode`: `simulation` darf `protocol` nicht importieren und
     * nennt die Codes als Zeichenketten.
     * @spec SPEC-037
     * @spec SPEC-038
     */
    @Test
    fun `die Fehlercodes der Simulation sind Codes des Protokolls`() {
        assertEquals(ErrorCode.TDS_UNSUPPORTED_SQL, ErrorCode.valueOf(SimCodes.UNSUPPORTED_SQL))
        assertEquals(ErrorCode.TDS_SIM_UNKNOWN_OBJECT, ErrorCode.valueOf(SimCodes.UNKNOWN_OBJECT))
    }
}
