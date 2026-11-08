package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Die Antwort auf die Probe der Zieltabelle von `SqlBulkCopy` ([BulkCopyProbe], `SPEC-036`, Form B) gegen die
 * Aufzeichnung des echten Servers (Messreihe M-8 bis M-12, Datei `bulk-verstoss-schluessel.messung`, Zeile 13, SQL
 * Server
 * 2022; die Antwort der Instanz 2025 hat dieselbe Folge). Die Hex-Datei `antwort-probe-verstoss-schluessel.hex` ist die
 * Antwort ohne die zwei Nullbyte hinter der Spaltenzahl jedes COLMETADATA (`src/test/resources/messung/`).
 */
class BulkCopyProbeTest {
    private val twoColumns = listOf(
        BulkProbeColumn(ResultColumn("Id", SqlType.Int64, false), 4000),
        BulkProbeColumn(ResultColumn("Text", SqlType.UnicodeText, true), 10),
    )

    private fun resource(name: String): ByteArray =
        hex(requireNotNull(javaClass.getResource("/messung/bulk-referenzclient/$name.hex")).readText())

    private fun answer(columns: List<BulkProbeColumn>): ByteArray =
        assertIs<ProtocolResult.Ok<ByteArray>>(BulkCopyProbe.answer(columns)).value

    private fun occurrences(bytes: ByteArray, pattern: ByteArray): Int =
        (0..bytes.size - pattern.size).count { at -> pattern.indices.all { bytes[at + it] == pattern[it] } }

    /**
     * Für die Tabelle der Aufzeichnung (`Id` BIGINT nicht nullbar, `Text` NVARCHAR(10) nullbar) ist die Antwort Byte
     * für
     * Byte die des echten Servers, bis auf die zwei Nullbyte. Wächter: eine andere Folge der DONE, ein anderer Status
     * oder
     * Befehl, ein falsches Flag der Spalten, eine falsche Zeile der Kollationen (ROW für den Text, NBCROW für die
     * Zahl),
     * eine Textlänge, die nicht die der Beschreibung ist.
     *
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `die Antwort gleicht der Aufzeichnung des echten Servers fuer die Tabelle bulk_schluessel`() {
        assertContentEquals(resource("antwort-probe-verstoss-schluessel"), answer(twoColumns))
    }

    /**
     * Die Zahl der Spalten steht in den Zeilenzahlen von DONEINPROC nach der Abfrage der Spaltennamen und nach den
     * Kollationen (DONEINPROC, Status `0x0011`, Befehl `0x00c1`, Zähler 3). Wächter: eine feste Zahl 2.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Zahl der Spalten steht in beiden Zeilenzahlen von DONEINPROC`() {
        val three = twoColumns + BulkProbeColumn(ResultColumn("Zahl", SqlType.Int32, true), 4000)
        val doneInProc = hex("ff 11 00 c1 00 03 00 00 00 00 00 00 00")
        assertEquals(2, occurrences(answer(three), doneInProc))
        assertEquals(0, occurrences(answer(twoColumns), doneInProc))
    }

    /**
     * Eine Dezimalspalte meldet in der Beschreibung der Tabelle die Größe 17 für jede Precision (Aufzeichnung:
     * `DECIMALN(10,2)` mit `6a 11 0a 02`), ein `INT` nullbar die N-Form (`26 04`). Wächter: die Größe 9 aus der
     * Precision, eine feste Form für eine nullbare Spalte.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Beschreibung der Tabelle meldet Dezimalzahlen mit der Groesse 17`() {
        val columns = twoColumns + listOf(
            BulkProbeColumn(ResultColumn("Preis", SqlType.Decimal(10, 2), true), 4000),
            BulkProbeColumn(ResultColumn("Zahl", SqlType.Int32, true), 4000),
        )
        val bytes = answer(columns)
        assertEquals(1, occurrences(bytes, hex("09 00 6a 11 0a 02 05 50 00 72 00 65 00 69 00 73 00")))
        assertEquals(1, occurrences(bytes, hex("09 00 26 04 04 5a 00 61 00 68 00 6c 00")))
    }
}
