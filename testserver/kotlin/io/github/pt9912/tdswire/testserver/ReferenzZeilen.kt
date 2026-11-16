package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.simulation.SimColumn
import java.math.BigDecimal
import java.time.LocalDateTime

/** Name der Tabelle des Referenz-Workloads (`docs/plan/referenz-workload.md` S1). */
internal const val REF_TABLE = "ref_zeilen"

/** Der feste Seed des Lasttests (Setzung, `docs/plan/referenz-workload.md` Abschnitt 5); `LASTTEST_SEED` setzt ihn. */
internal const val REF_DEFAULT_SEED = 42L

private const val CODE_CHARS = 12
private const val NAME_CHARS = 40
private const val NOTE_CHARS = 100
private const val NOTE_NULL_EVERY = 10
private const val AMOUNT_PRECISION = 12
private const val AMOUNT_SCALE = 2
private const val AMOUNT_RANGE = 1_000_000_000_000L
private const val BASE_YEAR = 2020
private const val SECONDS_PER_YEAR = 31_536_000L
private const val MILLIS_PER_SECOND = 1000
private const val NANOS_PER_MILLI = 1_000_000
private const val GOLDEN = -0x61c8864680b583ebL
private const val MIX_1 = -0x40a7b892e31b1a47L
private const val MIX_2 = -0x6b2fb644ecceee15L
private const val SHIFT_A = 30
private const val SHIFT_B = 27
private const val SHIFT_C = 31
private const val SHIFT_AMOUNT = 8
private const val SHIFT_QUANTITY = 33
private const val SHIFT_TIME = 12
private const val SHIFT_TEXT = 16
private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

/**
 * Die Zeilen des Referenz-Workloads (S1 in `docs/plan/referenz-workload.md`): Tabelle `ref_zeilen` mit acht Spalten,
 * Zeile `n` allein aus Seed und `n` erzeugt (kein Zustand, gleiche Zeile bei jedem Lauf und in jedem Prozess). Der
 * Erzeuger rechnet mit SplitMix64. Kopplung: `referenztest-dotnet/Lasttest/ReferenzZeilen.cs` rechnet dasselbe (Konstanten,
 * Reihenfolge der Mischungen, Alphabet); `make lasttest` vergleicht im Export die erste und die letzte Zeile beider
 * Seiten, im Import vergleicht nichts.
 */
internal object ReferenzZeilen {
    val columns: List<SimColumn> = listOf(
        SimColumn("Id", SqlType.Int64, nullable = false),
        SimColumn("Menge", SqlType.Int32),
        SimColumn("Betrag", SqlType.Decimal(AMOUNT_PRECISION, AMOUNT_SCALE)),
        SimColumn("Aktiv", SqlType.Bool),
        SimColumn("Angelegt", SqlType.DateTime),
        SimColumn("Code", SqlType.UnicodeText, maxLength = CODE_CHARS),
        SimColumn("Name", SqlType.UnicodeText, maxLength = NAME_CHARS),
        SimColumn("Notiz", SqlType.UnicodeText, maxLength = NOTE_CHARS),
    )

    /** Dieselben Spalten für ein eigenes Backend. */
    val resultColumns: List<ResultColumn> = columns.map { ResultColumn(it.name, it.type, it.nullable, it.maxLength) }

    /** Die Werte von Zeile [n] (ab 1) in der Reihenfolge von [columns]. */
    fun row(seed: Long, n: Long): List<Any?> {
        val h = mix(seed + n * GOLDEN)
        return listOf(
            n,
            (h ushr SHIFT_QUANTITY).toInt(),
            BigDecimal.valueOf(Math.floorMod(h ushr SHIFT_AMOUNT, AMOUNT_RANGE), AMOUNT_SCALE),
            (h and 1L) == 1L,
            LocalDateTime.of(BASE_YEAR, 1, 1, 0, 0)
                .plusSeconds(Math.floorMod(h ushr SHIFT_TIME, SECONDS_PER_YEAR))
                .plusNanos(Math.floorMod(h ushr SHIFT_TEXT, MILLIS_PER_SECOND.toLong()) * NANOS_PER_MILLI),
            text(h, CODE_CHARS),
            text(mix(h), NAME_CHARS),
            if (n % NOTE_NULL_EVERY == 0L) null else text(mix(mix(h)), NOTE_CHARS),
        )
    }

    private fun mix(value: Long): Long {
        var z = value
        z = (z xor (z ushr SHIFT_A)) * MIX_1
        z = (z xor (z ushr SHIFT_B)) * MIX_2
        return z xor (z ushr SHIFT_C)
    }

    private fun text(seed: Long, chars: Int): String {
        var state = seed
        return String(
            CharArray(chars) {
                state = mix(state)
                ALPHABET[Math.floorMod(state, ALPHABET.length.toLong()).toInt()]
            },
        )
    }
}
