package io.github.pt9912.tdswire.observability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private const val MILLI = 1_000_000L

/**
 * Die Klassen der Latenz-Verteilung (`SPEC-018`, Abschnitt 5 der Spezifikation): die Grenzen sind Obergrenzen
 * einschließlich, ein Perzentil ist die Obergrenze der Klasse des Rangs (Nearest-Rank).
 */
internal class LatencyHistogramTest {
    private val histogram = LatencyHistogram(longArrayOf(1, 10, 100))

    private fun record(vararg milliseconds: Long) = milliseconds.forEach { histogram.record(it * MILLI) }

    /**
     * Eine Dauer auf der Grenze zählt in die Klasse dieser Grenze, ein Nanosekunden-Rest darüber in die nächste, eine
     * Dauer über der größten Grenze in die Klasse ohne Obergrenze; eine negative Dauer zählt als null.
     *
     * @spec SPEC-018
     */
    @Test
    fun `Die Grenzen sind Obergrenzen einschliesslich`() {
        record(1)
        assertEquals(1L, histogram.percentile(100))
        histogram.record(MILLI + 1)
        assertEquals(10L, histogram.percentile(100))
        histogram.record(100 * MILLI)
        assertEquals(100L, histogram.percentile(100))
        histogram.record(100 * MILLI + 1)
        assertEquals(LatencyHistogram.OVERFLOW, histogram.percentile(100))
        histogram.record(-5)
        assertEquals(5L, histogram.total())
        assertEquals(1L, histogram.percentile(20))
    }

    /**
     * Der Rang ist `ceil(total * p / 100)`: bei zehn Dauern ist p50 die fünfte, p95 und p99 die zehnte; ohne Dauer gibt
     * es kein Perzentil, und ein Perzentil außerhalb von 1 bis 100 wird abgewiesen.
     *
     * @spec SPEC-018
     */
    @Test
    fun `Der Rang folgt Nearest-Rank`() {
        assertNull(histogram.percentile(50))
        record(1, 1, 1, 1, 1, 10, 10, 10, 10, 100)
        assertEquals(1L, histogram.percentile(50))
        assertEquals(10L, histogram.percentile(51))
        assertEquals(10L, histogram.percentile(90))
        assertEquals(100L, histogram.percentile(95))
        assertEquals(100L, histogram.percentile(99))
        assertFailsWith<IllegalArgumentException> { histogram.percentile(0) }
        assertFailsWith<IllegalArgumentException> { histogram.percentile(101) }
    }

    /**
     * Grenzen, die nicht aufsteigen oder fehlen, werden abgewiesen: eine Verteilung mit gleichen oder verdrehten
     * Grenzen hätte Klassen, die nie gefüllt werden.
     *
     * @spec SPEC-018
     */
    @Test
    fun `Unsortierte Grenzen werden abgewiesen`() {
        assertFailsWith<IllegalArgumentException> { LatencyHistogram(longArrayOf(10, 1)) }
        assertFailsWith<IllegalArgumentException> { LatencyHistogram(longArrayOf(5, 5)) }
        assertFailsWith<IllegalArgumentException> { LatencyHistogram(longArrayOf()) }
    }
}
