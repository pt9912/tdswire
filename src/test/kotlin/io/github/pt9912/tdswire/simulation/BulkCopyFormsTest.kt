package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.port.BulkProbeResult
import java.security.MessageDigest
import java.util.HexFormat
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

    private fun assertNotRecognized(sql: String) = assertNull(BulkCopyForms.recognize(sql))

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
    fun `abweichende Namen, drei Teile, unverdoppelte Zeichen, Zusätze und andere Texte sind nicht erkannt`() {
        assertNotRecognized(recorded.replaceFirst("OBJECT_ID('[bulk_typen]')", "OBJECT_ID('[andere]')"))
        assertNotRecognized(recorded.replaceFirst("FROM [bulk_typen]", "FROM [andere]"))
        assertNotRecognized(recorded.replaceFirst("N'.[bulk_typen]'", "N'.[andere]'"))
        val schema = text("probe-dbo-bulk_ns")
        assertNotRecognized(schema.replaceFirst("OBJECT_ID('[dbo].[bulk_ns]')", "OBJECT_ID('[dbo].[andere]')"))
        assertNotRecognized(schema.replaceFirst("FROM [dbo].[bulk_ns]", "FROM [dbo].[andere]"))
        assertNotRecognized(schema.replaceFirst("FROM [dbo].[bulk_ns]", "FROM [s1].[bulk_ns]"))
        assertNotRecognized(schema.replaceFirst("N'[dbo].[bulk_ns]'", "N'[dbo].[andere]'"))
        assertNotRecognized(schema.replaceFirst("N'[dbo].[bulk_ns]'", "N'.[bulk_ns]'"))
        assertNotRecognized(schema.replaceFirst("N'[dbo].[bulk_ns]'", "N'[s1].[bulk_ns]'"))
        assertNotRecognized(recorded.replaceFirst("N'.[bulk_typen]'", "N'[dbo].[bulk_typen]'"))
        assertNotRecognized(text("probe-dreiteilig"))
        assertNotRecognized(recorded.replace("bulk_typen", "bulk'typen"))
        assertNotRecognized(recorded.replace("bulk_typen", "bulk]typen"))
        assertNotRecognized(recorded.replace("[bulk_typen]", "[a].[b]"))
        assertNotRecognized("$recorded; SELECT 1")
        assertNotRecognized("SELECT 1")
    }

    /**
     * Setzung, nicht aufgezeichnet: Leerraum und ein `;` am Ende des Textes sowie mehrere Leerzeichen an einer Lücke
     * des Wortlauts (auch in einem Literal) ändern die Erkennung nicht. Wächter: eine Erkennung, die den Text an den
     * Enden nicht beschneidet.
     *
     * @spec SPEC-036
     */
    @Test
    fun `Leerraum und Semikolon am Ende und mehrere Leerzeichen im Wortlaut sind erkannt`() {
        val expected = TableRef(null, "bulk_typen")
        assertEquals(expected, BulkCopyForms.recognize("$recorded\r\n"))
        assertEquals(expected, BulkCopyForms.recognize("  \t$recorded;"))
        assertEquals(expected, BulkCopyForms.recognize("$recorded ;\n"))
        assertEquals(expected, BulkCopyForms.recognize(recorded.replace("'', ''", "'',  ''")))
    }

    private fun escaped(raw: String): String = raw.replace("]", "]]").replace("'", "''")

    /**
     * Lange Namen (10 000 und 100 000 Zeichen, mit `]`, `'`, `.` und Leerzeichen, ohne und mit Schema, auch das Schema
     * lang) sind erkannt und liefern den unausgezeichneten Namen; ein langer Name mit einem einzelnen `]` in der Mitte
     * ist nicht erkannt. Beides ohne Fehler. Die Länge, die der Server annimmt (128 Zeichen), ist nicht gemessen und
     * hier nicht geprüft. Wächter: eine Erkennung, die je Zeichen des Namens rekursiert (`StackOverflowError` ab etwa
     * 3000 Zeichen).
     *
     * @spec SPEC-036
     */
    @Test
    fun `lange Namen mit Sonderzeichen sind erkannt, ohne und mit Schema, ohne Fehler`() {
        for (length in listOf(10_000, 100_000)) {
            val raw = "a]b'c. ".repeat(length / 7)
            val plain = recorded.replace("bulk_typen", escaped(raw))
            assertEquals(TableRef(null, raw), BulkCopyForms.recognize(plain))
            val both = text("probe-dbo-bulk_ns").replace("bulk_ns", escaped(raw)).replace("dbo", escaped(raw))
            assertEquals(TableRef(raw, raw), BulkCopyForms.recognize(both))
            val onlyChars = recorded.replace("bulk_typen", "x".repeat(length))
            assertEquals(TableRef(null, "x".repeat(length)), BulkCopyForms.recognize(onlyChars))
            assertNotRecognized(recorded.replace("bulk_typen", escaped(raw) + "]" + escaped(raw)))
        }
    }

    /**
     * Die Summe SHA-256 jeder Datei `probe-*.sql` gleicht der Summe der Probe in der Ablage (`MessungBulkBelegTests` in
     * `referenztest-dotnet`, gleiche Tabelle dort, beide Instanzen). Wächter: eine Änderung an einer Datei (Zeichen,
     * Zeilenende), die der Aussage „aufgezeichnete Texte“ nicht mehr entspricht.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Dateien der Probetexte tragen die Summen der aufgezeichneten Proben`() {
        val expected = mapOf(
            "probe-bulk_typen" to "3aa31cbadb2b4761aa36ed4bab799823e155314f2839aa43460c86ce4cc166bc",
            "probe-dbo-bulk_ns" to "9ba79d968258edb6de9cefa14613803748b55e88a46682064afc4656554345f7",
            "probe-dreiteilig" to "04d9c887d2d31fd977b341eb649c0a67b2c0f318b5439159cf36a6b99f6975b2",
            "probe-s1-bulk_s1" to "44ea3ebb26471704148fc3549594992c14a8f3dd378518df4e076862cdead150",
            "probe-dbo-gibtesnicht" to "7d0ee1e651bb7a00e1119424f4e8a07dd009652708ac21dc062743d8f0c00e1e",
            "probe-klammer" to "7570d7a806599788e4947449fff7e322fae58bb53f13539a9aab3f0f672827a8",
            "probe-apostroph" to "4cba397a365cbffdba869a410d0b82d6b4f0e60db030ca8bd6d0a692dc541cd9",
            "probe-punkt" to "a2521e614e79357673f2e0f3bc61374de3bb8fec3f63db63365883c036b5864b",
            "probe-dbo-leerzeichen" to "c61c8fb6e61dc8d2b832e9fb74aad02ad75135c97903d47ad9bb40c6df23f877",
            "probe-leerzeichen2" to "15ee9740f6f94e1e087f668b1f173997840ab352d6bf2034792e6833ee078aab",
        )
        for ((name, sum) in expected) {
            val bytes = requireNotNull(javaClass.getResource("/messung/bulk-referenzclient/$name.sql")).readBytes()
            val found = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            assertEquals(sum, found, name)
        }
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

    /**
     * Setzung, am echten Server nicht gemessen: Schema und Name der Probe gelten ohne Beachtung der Schreibweise, wie
     * im Katalog (`SimCatalog.find`). Wächter: eine Suche, die das Schema oder den Namen exakt vergleicht.
     *
     * @spec SPEC-036
     */
    @Test
    fun `die Probe sucht Schema und Namen ohne Beachtung der Schreibweise`() {
        val catalog = backend(table("dbo", "bulk_ns", *columns))
        val upper = text("probe-dbo-bulk_ns").replace("[dbo]", "[DBO]")
        assertIs<BulkProbeResult.Ready>(BulkCopyForms.probe(upper) { catalog })
        val upperName = text("probe-dbo-bulk_ns").replace("bulk_ns", "BULK_NS")
        assertIs<BulkProbeResult.Ready>(BulkCopyForms.probe(upperName) { catalog })
    }
}
