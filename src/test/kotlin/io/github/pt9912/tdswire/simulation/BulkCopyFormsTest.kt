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
 * sie liefert. Die Texte sind die aufgezeichneten Anfragen (Datei `probe-bulk_typen.sql`: Messreihe M-8 bis M-12,
 * `bulk-typen-null.messung`, Zeile 12; die Dateien `probe-*.sql`: Messreihe M-33 bis M-35, `bulk-name-schema.messung`
 * und `bulk-name-sonderzeichen.messung`, Instanz 2022; alle mit Zeilenumbruch CR LF, unter
 * `src/test/resources/messung/bulk-referenzclient/`); die Varianten sind von Hand aus ihnen gebildet.
 */
class BulkCopyFormsTest {
    private fun text(name: String): String =
        requireNotNull(javaClass.getResource("/messung/bulk-referenzclient/$name.sql")).readText()

    private val recorded: String = text("probe-bulk_typen")

    /**
     * Der aufgezeichnete Text ist erkannt und nennt `bulk_typen`; derselbe Text mit LF statt CR LF und mit einem
     * anderen Namen an allen drei Stellen ebenso. Wächter: eine Erkennung, die am Zeilenumbruch oder am Namen
     * `bulk_typen` hängt.
     *
     * @spec SPEC-036
     */
    @Test
    fun `der aufgezeichnete Text und seine Varianten mit anderem Namen und Zeilenumbruch sind erkannt`() {
        assertEquals(TableRef(null, "bulk_typen"), BulkCopyForms.recognize(recorded))
        assertEquals(TableRef(null, "bulk_typen"), BulkCopyForms.recognize(recorded.replace("\r\n", "\n")))
        assertEquals(TableRef(null, "kunden"), BulkCopyForms.recognize(recorded.replace("bulk_typen", "kunden")))
    }

    /**
     * Die aufgezeichneten Texte für Namen mit Schema (`dbo.bulk_ns`, `s1.bulk_s1`, `dbo.[bulk x]`) nennen Schema und
     * Tabelle; die für `]` (`[bulk]]x]`), `'` (`[bulk''x]`), `.` und eine Folge zweier Leerzeichen im Namen nennen den
     * Namen unausgezeichnet und ohne Schema, die Folge der Leerzeichen bleibt erhalten. Wächter: eine Erkennung, die
     * das Schema verwirft, `]]` oder `''` nicht auflöst oder den Namen am Leerraum zusammenzieht.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die aufgezeichneten Texte mit Schema und Sonderzeichen nennen Schema und unausgezeichneten Namen`() {
        assertEquals(TableRef("dbo", "bulk_ns"), BulkCopyForms.recognize(text("probe-dbo-bulk_ns")))
        assertEquals(TableRef("s1", "bulk_s1"), BulkCopyForms.recognize(text("probe-s1-bulk_s1")))
        assertEquals(TableRef("dbo", "bulk x"), BulkCopyForms.recognize(text("probe-dbo-leerzeichen")))
        assertEquals(TableRef(null, "bulk]x"), BulkCopyForms.recognize(text("probe-klammer")))
        assertEquals(TableRef(null, "bulk'x"), BulkCopyForms.recognize(text("probe-apostroph")))
        assertEquals(TableRef(null, "bulk.x"), BulkCopyForms.recognize(text("probe-punkt")))
        assertEquals(TableRef(null, "bulk  y"), BulkCopyForms.recognize(text("probe-leerzeichen2")))
        assertEquals(TableRef("dbo", "gibtesnicht"), BulkCopyForms.recognize(text("probe-dbo-gibtesnicht")))
    }

    /**
     * Nicht erkannt sind: ein Name, der an einer der drei Stellen abweicht (auch die Kollationsform ohne Schema bei
     * einem Namen mit Schema und umgekehrt); der aufgezeichnete Text mit drei Teilen (Datenbank, Schema, Tabelle); ein
     * Name mit einem einzelnen `'` oder `]` (nicht verdoppelt); ein Name mit Schema als ein Teil; ein Text mit
     * angehängter Anweisung; ein anderer Text. Wächter: eine Erkennung, die nur den Anfang vergleicht, die Namen nicht
     * aneinander bindet oder die Schreibung der Auszeichnung nicht prüft.
     *
     * @spec SPEC-036
     */
    @Test
    fun `ein abweichender Name, drei Teile, eine unverdoppelte Auszeichnung, ein Zusatz oder ein anderer Text sind nicht erkannt`() {
        assertNull(BulkCopyForms.recognize(recorded.replaceFirst("OBJECT_ID('[bulk_typen]')", "OBJECT_ID('[andere]')")))
        assertNull(BulkCopyForms.recognize(recorded.replaceFirst("FROM [bulk_typen]", "FROM [andere]")))
        assertNull(BulkCopyForms.recognize(recorded.replaceFirst("N'.[bulk_typen]'", "N'.[andere]'")))
        val schema = text("probe-dbo-bulk_ns")
        assertNull(BulkCopyForms.recognize(schema.replaceFirst("OBJECT_ID('[dbo].[bulk_ns]')", "OBJECT_ID('[dbo].[andere]')")))
        assertNull(BulkCopyForms.recognize(schema.replaceFirst("FROM [dbo].[bulk_ns]", "FROM [dbo].[andere]")))
        assertNull(BulkCopyForms.recognize(schema.replaceFirst("FROM [dbo].[bulk_ns]", "FROM [s1].[bulk_ns]")))
        assertNull(BulkCopyForms.recognize(schema.replaceFirst("N'[dbo].[bulk_ns]'", "N'[dbo].[andere]'")))
        assertNull(BulkCopyForms.recognize(schema.replaceFirst("N'[dbo].[bulk_ns]'", "N'.[bulk_ns]'")))
        assertNull(BulkCopyForms.recognize(schema.replaceFirst("N'[dbo].[bulk_ns]'", "N'[s1].[bulk_ns]'")))
        assertNull(BulkCopyForms.recognize(recorded.replaceFirst("N'.[bulk_typen]'", "N'[dbo].[bulk_typen]'")))
        assertNull(BulkCopyForms.recognize(text("probe-dreiteilig")))
        assertNull(BulkCopyForms.recognize(recorded.replace("bulk_typen", "bulk'typen")))
        assertNull(BulkCopyForms.recognize(recorded.replace("bulk_typen", "bulk]typen")))
        assertNull(BulkCopyForms.recognize(recorded.replace("[bulk_typen]", "[a].[b]")))
        assertNull(BulkCopyForms.recognize("$recorded; SELECT 1"))
        assertNull(BulkCopyForms.recognize("SELECT 1"))
    }

    private fun backend(vararg tables: SimTable) = SimCatalog(tables.toList())

    private fun table(schema: String, name: String, vararg columns: SimColumn) = SimTable(
        schema = schema,
        name = name,
        columns = columns.toList(),
        primaryKey = listOf("Id"),
        rows = { RowSource { null } },
    )

    private val columns = arrayOf(
        SimColumn("Id", SqlType.Int64, nullable = false),
        SimColumn("Text", SqlType.UnicodeText, maxLength = 10),
        SimColumn("Lang", SqlType.UnicodeText, maxLength = UNBOUNDED_TEXT),
    )

    /**
     * Die Spalten der Tabelle in der Reihenfolge der Beschreibung mit Typ, NULL-Fähigkeit und Länge des Textes;
     * `NVARCHAR(MAX)` meldet 4000. Eine nicht beschriebene Tabelle ist `TDS_SIM_UNKNOWN_OBJECT`, ein Katalog, der
     * wirft, `TDS_BACKEND_ERROR`, jeder andere Text `null`. Wächter: eine Länge, die nicht aus der Beschreibung
     * kommt; die Länge -1; ein Katalog, der die Ausnahme nach außen gibt.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Probe liefert die Spalten der Tabelle, sonst die Ablehnung`() {
        val catalog = backend(table("dbo", "bulk_typen", *columns))
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

    /**
     * Die Probe sucht die Tabelle im genannten Schema: `dbo.bulk_ns` findet nur die Tabelle in `dbo`, `s1.bulk_s1` nur
     * die in `s1`, und der Name ohne Schema gilt für `dbo`; ein Name mit `]`, `'` oder zwei Leerzeichen findet die
     * Tabelle mit dem unausgezeichneten Namen. Wächter: eine Suche, die das Schema ignoriert oder den Namen
     * ausgezeichnet sucht.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Probe sucht im genannten Schema und mit dem unausgezeichneten Namen`() {
        val catalog = backend(
            table("dbo", "bulk_ns", *columns),
            table("s1", "bulk_s1", *columns),
            table("s1", "bulk_typen", SimColumn("Id", SqlType.Int64, nullable = false)),
            table("dbo", "bulk]x", *columns),
            table("dbo", "bulk'x", *columns),
            table("dbo", "bulk  y", *columns),
        )
        assertIs<BulkProbeResult.Ready>(BulkCopyForms.probe(text("probe-dbo-bulk_ns")) { catalog })
        assertIs<BulkProbeResult.Ready>(BulkCopyForms.probe(text("probe-s1-bulk_s1")) { catalog })
        val wrongSchema = text("probe-s1-bulk_s1").replace("[s1].", "[dbo].")
        val rejected = assertIs<BulkProbeResult.Rejected>(BulkCopyForms.probe(wrongSchema) { catalog })
        assertEquals(SimCodes.UNKNOWN_OBJECT, rejected.code)
        val noSchema = assertIs<BulkProbeResult.Rejected>(BulkCopyForms.probe(recorded) { catalog })
        assertEquals(SimCodes.UNKNOWN_OBJECT, noSchema.code)
        for (name in listOf("probe-klammer", "probe-apostroph", "probe-leerzeichen2")) {
            assertIs<BulkProbeResult.Ready>(BulkCopyForms.probe(text(name)) { catalog })
        }
        val unknown = assertIs<BulkProbeResult.Rejected>(BulkCopyForms.probe(text("probe-dbo-gibtesnicht")) { catalog })
        assertEquals(SimCodes.UNKNOWN_OBJECT, unknown.code)
    }
}
