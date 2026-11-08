package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.port.BulkProbeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Die Erkennung der Probe der Zieltabelle von `SqlBulkCopy` ([BulkCopyForms], `SPEC-036` Form B) und die Spalten, die
 * sie
 * liefert. Der Text ist die aufgezeichnete Anfrage (Messreihe M-8 bis M-12, `bulk-typen-null.messung`, Zeile 12, mit
 * Zeilenumbruch CR LF; Datei `probe-bulk_typen.sql` unter `src/test/resources/messung/bulk-referenzclient/`); die
 * Varianten
 * sind von Hand aus ihm gebildet.
 */
class BulkCopyFormsTest {
    private val recorded: String =
        requireNotNull(javaClass.getResource("/messung/bulk-referenzclient/probe-bulk_typen.sql")).readText()

    /**
     * Der aufgezeichnete Text ist erkannt und nennt `bulk_typen`; derselbe Text mit LF statt CR LF und mit einem
     * anderen
     * Namen an allen drei Stellen ebenso. Wächter: eine Erkennung, die am Zeilenumbruch oder am Namen `bulk_typen`
     * hängt.
     *
     * @spec SPEC-036
     */
    @Test
    fun `der aufgezeichnete Text und seine Varianten mit anderem Namen und Zeilenumbruch sind erkannt`() {
        assertEquals("bulk_typen", BulkCopyForms.recognize(recorded))
        assertEquals("bulk_typen", BulkCopyForms.recognize(recorded.replace("\r\n", "\n")))
        assertEquals("kunden", BulkCopyForms.recognize(recorded.replace("bulk_typen", "kunden")))
    }

    /**
     * Nicht erkannt sind: ein Name, der an einer der drei Stellen abweicht; ein Name mit Schema; ein Name mit `'`; ein
     * Text mit angehängter Anweisung; ein anderer Text. Wächter: eine Erkennung, die nur den Anfang vergleicht oder die
     * drei Namen nicht aneinander bindet.
     *
     * @spec SPEC-036
     */
    @Test
    fun `ein abweichender Name, ein Schema, ein Zusatz oder ein anderer Text sind nicht erkannt`() {
        assertNull(BulkCopyForms.recognize(recorded.replaceFirst("OBJECT_ID('[bulk_typen]')", "OBJECT_ID('[andere]')")))
        assertNull(BulkCopyForms.recognize(recorded.replaceFirst("FROM [bulk_typen]", "FROM [andere]")))
        assertNull(BulkCopyForms.recognize(recorded.replaceFirst("N'.[bulk_typen]'", "N'.[andere]'")))
        assertNull(BulkCopyForms.recognize(recorded.replace("[bulk_typen]", "[dbo].[bulk_typen]")))
        assertNull(BulkCopyForms.recognize(recorded.replace("bulk_typen", "bulk'typen")))
        assertNull(BulkCopyForms.recognize("$recorded; SELECT 1"))
        assertNull(BulkCopyForms.recognize("SELECT 1"))
    }

    private fun backend(vararg columns: SimColumn) = SimCatalog(
        listOf(
            SimTable(
                name = "bulk_typen",
                columns = columns.toList(),
                primaryKey = listOf("Id"),
                rows = { RowSource { null } },
            ),
        ),
    )

    /**
     * Die Spalten der Tabelle in der Reihenfolge der Beschreibung mit Typ, NULL-Fähigkeit und Länge des Textes;
     * `NVARCHAR(MAX)` meldet 4000. Eine nicht beschriebene Tabelle ist `TDS_SIM_UNKNOWN_OBJECT`, ein Katalog, der
     * wirft,
     * `TDS_BACKEND_ERROR`, jeder andere Text `null`. Wächter: eine Länge, die nicht aus der Beschreibung kommt; die
     * Länge
     * -1; ein Katalog, der die Ausnahme nach außen gibt.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Probe liefert die Spalten der Tabelle, sonst die Ablehnung`() {
        val catalog = backend(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Text", SqlType.UnicodeText, maxLength = 10),
            SimColumn("Lang", SqlType.UnicodeText, maxLength = UNBOUNDED_TEXT),
        )
        val ready = assertIs<BulkProbeResult.Ready>(BulkCopyForms.probe(recorded) { catalog })
        assertEquals(
            listOf(
                BulkProbeColumn(ResultColumn("Id", SqlType.Int64, false), 4000),
                BulkProbeColumn(ResultColumn("Text", SqlType.UnicodeText, true), 10),
                BulkProbeColumn(ResultColumn("Lang", SqlType.UnicodeText, true), 4000),
            ),
            ready.columns,
        )
        val missing = recorded.replace("bulk_typen", "fehlt")
        val unknown = assertIs<BulkProbeResult.Rejected>(BulkCopyForms.probe(missing) { catalog })
        assertEquals(SimCodes.UNKNOWN_OBJECT, unknown.code)
        val broken = assertIs<BulkProbeResult.Rejected>(BulkCopyForms.probe(recorded) { error("Katalog") })
        assertEquals(SimCodes.BACKEND_ERROR, broken.code)
        assertNull(BulkCopyForms.probe("SELECT 1") { catalog })
    }
}
