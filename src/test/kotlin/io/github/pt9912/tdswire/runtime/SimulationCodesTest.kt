package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.protocol.error.ErrorCode
import io.github.pt9912.tdswire.simulation.SimCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals(ErrorCode.TDS_SIM_INVALID_VALUE, ErrorCode.valueOf(SimCodes.INVALID_VALUE))
        assertEquals(ErrorCode.TDS_BACKEND_ERROR, ErrorCode.valueOf(SimCodes.BACKEND_ERROR))
    }

    /**
     * Jede Konstante von `SimCodes` ist ein Code des Protokolls, nicht nur die vier bekannten: ein fünfter Code
     * in `SimCodes` ohne Entsprechung macht diesen Test rot. Die vier bekannten Codes müssen darunter sein, sonst
     * hätte die Aufzählung nichts gefunden.
     * @spec SPEC-037
     * @spec SPEC-038
     * @spec SPEC-039
     */
    @Test
    fun `jede Konstante von SimCodes ist ein Code des Protokolls`() {
        val constants = SimCodes::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
        for (name in constants) ErrorCode.valueOf(name)
        val known = listOf(
            SimCodes.UNSUPPORTED_SQL,
            SimCodes.UNKNOWN_OBJECT,
            SimCodes.INVALID_VALUE,
            SimCodes.BACKEND_ERROR,
        )
        assertTrue(constants.containsAll(known), "$constants")
    }
}
