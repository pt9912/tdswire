package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DecimalValuesTest {
    private val type = TdsType.DecimalType(5, 2)

    private fun write(value: BigDecimal): ProtocolResult<Unit> {
        val encoder = TokenEncoder()
        assertIs<ProtocolResult.Ok<Unit>>(encoder.write(ColMetadataToken(listOf(ColumnDef("c", type, true)))))
        return encoder.write(RowToken(listOf(TdsValue.DecimalValue(value))))
    }

    /** Das Ergebnis des Schreibens in einem eigenen Thread; `null`, wenn es nach der Frist nicht vorliegt. */
    private fun withinDeadline(value: BigDecimal): Result<ProtocolResult<Unit>>? {
        var outcome: Result<ProtocolResult<Unit>>? = null
        val worker = Thread { outcome = runCatching { write(value) } }
        worker.isDaemon = true
        worker.start()
        worker.join(DEADLINE_MILLIS)
        return if (worker.isAlive) null else outcome
    }

    /**
     * Ein Wert mit extremem Exponent, der nicht in die Spalte passt, endet sofort in `VALUE_OUT_OF_RANGE`:
     * keine Ausnahme und keine Rechenzeit, die mit dem Exponenten wächst. Die Bereichsprüfung läuft vor der
     * Skalierung auf den Scale der Spalte.
     *
     * @spec SPEC-014
     */
    @Test
    fun `Dezimalzahl mit extremem Exponent endet sofort in VALUE_OUT_OF_RANGE`() {
        for (text in listOf("1E+1000000", "1E+30000000", "1E+999999999", "-1E+2147483647", "0E-999999999")) {
            val outcome = assertNotNull(withinDeadline(BigDecimal(text)), "$text nicht innerhalb der Frist")
            val result = assertIs<ProtocolResult.Failed>(outcome.getOrThrow(), text)
            assertEquals(ProtocolFailure.VALUE_OUT_OF_RANGE, result.error.failure, text)
        }
    }

    /**
     * Null mit großem positivem Exponent ist 0,00 und nicht überlaufen: die Prüfung weist nur Werte ab,
     * die mehr Stellen vor dem Komma haben als die Precision erlaubt. (Null mit Scale über dem der Spalte
     * wird wie jeder Wert mit zu vielen Nachkommastellen abgewiesen; der Encoder kürzt nicht.)
     *
     * @spec SPEC-003
     */
    @Test
    fun `Null mit extremem Exponent wird geschrieben`() {
        for (text in listOf("0E+999999999", "0E+2147483647")) {
            val outcome = assertNotNull(withinDeadline(BigDecimal(text)), text)
            assertTrue(outcome.getOrThrow() is ProtocolResult.Ok, text)
        }
    }

    private companion object {
        const val DEADLINE_MILLIS = 5_000L
    }
}
