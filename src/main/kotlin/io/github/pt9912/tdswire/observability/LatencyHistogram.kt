package io.github.pt9912.tdswire.observability

import java.util.concurrent.atomic.AtomicLongArray

/**
 * Die Verteilung der Anfrage-Latenz (`SPEC-017` bis `SPEC-019`, Abschnitt 5 der Spezifikation) als Zähler je Klasse.
 * Die Klassengrenzen [boundsMs] (Obergrenzen in Millisekunden, aufsteigend, Setzung, nicht gemessen) bestimmen, wie
 * weit ein Perzentil trägt: [percentile] liefert die Obergrenze der Klasse, in der der Rang liegt, nicht die gemessene
 * Dauer. Eine Dauer über der größten Grenze zählt in die letzte Klasse ohne Obergrenze.
 */
internal class LatencyHistogram(private val boundsMs: LongArray = DEFAULT_BOUNDS_MS) {
    private val counts = AtomicLongArray(boundsMs.size + 1)

    init {
        val ascending = boundsMs.isNotEmpty() && boundsMs.toList().zipWithNext().all { (a, b) -> a < b }
        require(ascending) { "Grenzen nicht aufsteigend" }
    }

    /** Zählt eine Dauer von [nanos] Nanosekunden; eine negative Dauer zählt als null. */
    fun record(nanos: Long) {
        val index = boundsMs.indexOfFirst { nanos <= it * NANOS_PER_MILLI }
        counts.incrementAndGet(if (index < 0) boundsMs.size else index)
    }

    /** Die Zahl der gezählten Dauern. */
    fun total(): Long = (0 until counts.length()).sumOf { counts.get(it) }

    /**
     * Das Perzentil [percent] (1 bis 100) als Obergrenze der Klasse, in der der Rang `ceil(total * percent / 100)`
     * liegt (Nearest-Rank über die Klassen); [OVERFLOW] bei einem Rang in der Klasse über der größten Grenze; `null`,
     * solange nichts gezählt ist.
     */
    fun percentile(percent: Int): Long? {
        require(percent in 1..PERCENT) { "Perzentil ausserhalb von 1 bis 100" }
        val snapshot = LongArray(counts.length()) { counts.get(it) }
        val total = snapshot.sum()
        val rank = maxOf(1L, (total * percent + PERCENT - 1) / PERCENT)
        var cumulative = 0L
        val index = snapshot.indexOfFirst {
            cumulative += it
            cumulative >= rank
        }
        return when {
            total == 0L -> null
            index in boundsMs.indices -> boundsMs[index]
            else -> OVERFLOW
        }
    }

    companion object {
        /** Der Wert eines Perzentils über der größten Klassengrenze: größer als jede Grenze. */
        const val OVERFLOW = Long.MAX_VALUE

        /** Die Obergrenzen der Klassen in Millisekunden (Setzung, nicht gemessen). */
        val DEFAULT_BOUNDS_MS =
            longArrayOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 30_000, 60_000)

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val PERCENT = 100
    }
}
