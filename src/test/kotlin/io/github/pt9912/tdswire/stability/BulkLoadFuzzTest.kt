package io.github.pt9912.tdswire.stability

import io.github.pt9912.tdswire.protocol.bulk.BulkLoadReader
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.hex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fuzz des Eingabepfads der Nachricht BULK_LOAD (`TDS-QA-02`, `SPEC-036`): beschädigte Nutzlasten gehen durch den
 * Leser. Das Orakel ist schwach und unabhängig vom Leser: jede Eingabe endet mit einem Ergebnis ([ProtocolResult.Ok]
 * oder [ProtocolResult.Failed]), nie mit einer Ausnahme, und die Zeilenzahl eines Ergebnisses überschreitet die Grenze
 * nicht. Die Ausgangsnutzlast ist der Mitschnitt von mssql-jdbc aus `BulkLoadReaderTest`; die Veränderungen sind
 * überschriebene Bytes, eingefügte Bytes, herausgeschnittene Bytes, eine veränderte Längenangabe, ein abgeschnittenes
 * Ende und Zufallsbytes. Der Zufall ist fest gesät, der Lauf wiederholbar.
 */
class BulkLoadFuzzTest {
    private val valid = hex(
        "81030000000000000026080249006400000000000100e7401f0904d00034044e0061006d006500000000000100" +
            "a70a000904d0003408450069006e00740072006900740074" +
            "00d10801000000000000000e004100640061002000dc006e00ef000a00323032362d30312d3032" +
            "d1080200000000000000ffffffffd10803000000000000000e004300e400630069006c00690065000a" +
            "00323032352d31322d3331fd000000000000000000000000",
    )

    private fun overwritten(random: Random): ByteArray = valid.copyOf().also {
        repeat(random.nextInt(1, MAX_BYTES)) { _ -> it[random.nextInt(it.size)] = random.nextInt().toByte() }
    }

    private fun inserted(random: Random): ByteArray {
        val at = random.nextInt(valid.size)
        return valid.copyOf(at) + random.nextBytes(random.nextInt(1, MAX_BYTES)) + valid.copyOfRange(at, valid.size)
    }

    private fun cutOut(random: Random): ByteArray {
        val at = random.nextInt(valid.size)
        return valid.copyOf(at) + valid.copyOfRange(minOf(valid.size, at + random.nextInt(1, MAX_BYTES)), valid.size)
    }

    private fun damaged(random: Random): ByteArray = when (random.nextInt(KINDS)) {
        0 -> overwritten(random)
        1 -> valid.copyOf(random.nextInt(0, valid.size))
        2 -> inserted(random)
        3 -> cutOut(random)
        4 -> random.nextBytes(random.nextInt(0, GARBAGE))
        else -> valid.copyOf().also { it[LENGTH_OFFSETS.random(random)] = random.nextInt().toByte() }
    }

    /**
     * 20 000 beschädigte Nutzlasten: keine Ausnahme, die Zeilenzahl bleibt unter der Grenze, und beide Ausgänge kommen
     * vor (sonst wäre der Lauf leer). Wächter: ein Leser, der eine Länge ungeprüft zum Lesen oder Anlegen nutzt (es
     * entstünde eine `IndexOutOfBounds`-, `NegativeArraySize`- oder `OutOfMemoryError`-Ausnahme).
     *
     * @spec SPEC-036
     * @spec TDS-QA-02
     */
    @Test
    fun `beschaedigte Nutzlasten enden mit einem Ergebnis des Lesers`() {
        val random = Random(SEED)
        var accepted = 0
        var refused = 0
        repeat(RUNS) {
            when (val result = BulkLoadReader.read(damaged(random))) {
                is ProtocolResult.Ok -> {
                    accepted++
                    assertTrue(result.value.rows.size <= BulkLoadReader.MAX_ROWS)
                }

                is ProtocolResult.Failed -> refused++
            }
        }
        assertTrue(accepted > 0, "kein gültiges Ergebnis in $RUNS Läufen")
        assertTrue(refused > 0, "kein Fehler in $RUNS Läufen")
    }

    private companion object {
        const val SEED = 20261107L
        const val RUNS = 20_000
        const val KINDS = 6
        const val MAX_BYTES = 8
        const val GARBAGE = 300

        /** Die Längenfelder im Anfang: Spaltenzahl (1 bis 2), Länge des NVARCHAR (47 bis 48), Länge des Namens. */
        val LENGTH_OFFSETS = listOf(1, 2, 12, 13, 14, 45, 46, 47)
    }
}
