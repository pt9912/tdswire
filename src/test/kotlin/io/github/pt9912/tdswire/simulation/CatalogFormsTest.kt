package io.github.pt9912.tdswire.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Erkennung der Katalogabfragen und der Transaktionstexte (`SPEC-036`, Formen K und C) am Wortlaut von d-migrate
 * 1.12.0. Die Handvektoren sind der Mitschnitt vom 2026-11-06 ([dMigrateCatalogTexts]); die Formen ohne Mitschnitt
 * (Abfragen des Imports) stehen mit dem Wortlaut aus dem Bytecode des Treibers in [CatalogForm.template].
 */
class CatalogFormsTest {
    /** Die Formen, die der Mitschnitt von `schema reverse --include-all` berührt (17 von 20). */
    private val recorded = setOf(
        CatalogForm.SCHEMA_NAME, CatalogForm.TABLES, CatalogForm.COLUMNS, CatalogForm.PARTITIONING,
        CatalogForm.PRIMARY_KEY, CatalogForm.FOREIGN_KEYS, CatalogForm.INDEXES, CatalogForm.CHECKS,
        CatalogForm.UNIQUE_KEYS, CatalogForm.FULLTEXT_COLUMNS, CatalogForm.SEQUENCES, CatalogForm.ROUTINES,
        CatalogForm.ROUTINE_PARAMETERS, CatalogForm.ROUTINE_DEPENDENCIES, CatalogForm.OTHER_OBJECTS,
        CatalogForm.VIEW_COLUMNS, CatalogForm.VIEWS,
    )

    /**
     * Happy: jeder gesendete Text des Mitschnitts ist eine Katalogform, und der Mitschnitt berührt genau 17 der 20
     * Formen (es fehlen die Formen des Imports: `IDENTITY_FIRST`, `COMPUTED_COLUMNS`, `IDENTITY_COLUMNS`).
     * Wächter: eine Normalisierung, die den Leerraum nicht zusammenfasst (die Texte tragen Einrückung und doppelte
     * Leerzeichen), oder ein Parameter `@P0` mit Leerzeichen, der nicht zu `?` wird.
     * @spec SPEC-036
     */
    @Test
    fun `jeder gesendete Katalogtext ist eine Form`() {
        val forms = dMigrateCatalogTexts.map { assertNotNull(CatalogForms.recognize(it), it) }
        assertEquals(recorded, forms.toSet())
        assertEquals(forms.size, forms.toSet().size, "ein Text je Form")
    }

    /**
     * Happy: auch die Texte des Imports (Wortlaut aus dem Bytecode, Parameter als `@P0`) sind erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `die Texte des Imports sind Formen`() {
        for (form in setOf(CatalogForm.IDENTITY_FIRST, CatalogForm.COMPUTED_COLUMNS, CatalogForm.IDENTITY_COLUMNS)) {
            val sent = form.template.replace("?", " @P0 ")
            assertEquals(form, CatalogForms.recognize(sent), form.name)
        }
    }

    /**
     * Happy: Leerraum am Rand, ein Tab statt eines Leerzeichens, CR LF statt LF und ein abschließendes `;` ändern die
     * Form nicht.
     * Wächter: ein Vergleich ohne Normalisierung des Leerraums.
     * @spec SPEC-036
     */
    @Test
    fun `Leerraum und abschliessendes Semikolon aendern die Form nicht`() {
        val text = dMigrateCatalogTexts[1]
        assertEquals(CatalogForm.TABLES, CatalogForms.recognize("\n  $text ;"))
        assertEquals(CatalogForm.TABLES, CatalogForms.recognize(text.replace("\n", "\r\n").replace("FROM", "FROM\t")))
    }

    /**
     * Negativ: ein anderer Wortlaut ist keine Katalogform — ein umbenannter Alias, ein ergänzter Kommentar,
     * kleingeschriebene Schlüsselwörter, ein zweiter Text und ein fehlender Parameter. Der Wortlaut ist das Maß,
     * keine Auswertung von SQL.
     * Wächter: eine Erkennung nach Teilstrings oder ohne Beachtung der Groß-/Kleinschreibung.
     * @spec SPEC-036
     */
    @Test
    fun `anderer Wortlaut ist keine Katalogform`() {
        val text = dMigrateCatalogTexts[1]
        val variants = listOf(
            text.replace("table_name", "tname"),
            "$text -- Kommentar",
            text.replace("SELECT", "select"),
            "$text; $text",
            text.replace(" @P0 ", " 'dbo' "),
            "SELECT SCHEMA_NAME()",
            "",
        )
        for (variant in variants) assertNull(CatalogForms.recognize(variant), variant)
    }

    /**
     * Happy: der Parser ordnet einen Katalogtext der Form K zu (vor dem Scanner, der `sys.tables t` nicht liest);
     * ein Text außerhalb bleibt `TDS_UNSUPPORTED_SQL`.
     * @spec SPEC-036
     */
    @Test
    fun `der Parser liefert Form K vor dem Scanner`() {
        val recognition = StatementParser.recognize(dMigrateCatalogTexts[2])
        assertEquals(CatalogForm.COLUMNS, assertIs<Recognition.Catalog>(recognition).form)
        assertIs<Recognition.NotRecognized>(StatementParser.recognize("SELECT * FROM sys.tables t"))
    }

    /**
     * Happy: der Mitschnitt von `schema migrate --execute` enthält die Transaktionstexte (`IF @@TRANCOUNT > 0 COMMIT
     * TRAN`, `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN `), und beide gelten als Form C mit
     * Änderungszähler 0; `ROLLBACK TRAN` und `set implicit_transactions on|off` davor ebenso (Setzung für die
     * ungesendeten Kombinationen).
     * Negativ: `TRANSACTION`, ein anderer Vergleich, eine andere Option und ein Zusatztext sind nicht erkannt (alles
     * oder nichts).
     * Wächter: ein Regex ohne Anker (`matches` statt `containsMatchIn`) oder mit offener Option.
     * @spec SPEC-036
     */
    @Test
    fun `Transaktionstexte sind Form C und nur sie`() {
        val sent = dMigrateStructureTexts.filter { it.contains("@@TRANCOUNT") }
        assertEquals(2, sent.size)
        val unsent = listOf(
            "IF @@TRANCOUNT > 0 ROLLBACK TRAN",
            "set implicit_transactions on IF @@TRANCOUNT > 0 COMMIT TRAN",
        )
        for (text in sent + unsent) {
            assertEquals(Recognition.Fixed(FixedForm.Transaction), StatementParser.recognize(text), text)
        }
        val others = listOf(
            "IF @@TRANCOUNT > 0 COMMIT TRANSACTION",
            "IF @@TRANCOUNT > 1 COMMIT TRAN",
            "set ansi_nulls on IF @@TRANCOUNT > 0 COMMIT TRAN",
            "IF @@TRANCOUNT > 0 COMMIT TRAN; SELECT 1",
            "IF @@TRANCOUNT > 0 COMMIT TRAN DROP TABLE x",
        )
        for (text in others) assertIs<Recognition.NotRecognized>(StatementParser.recognize(text), text)
    }

    /**
     * Die Normalisierung ist die Grundlage der Erkennung: Leerraumläufe werden ein Leerzeichen, die Parameterstelle
     * mit ihrem Leerraum wird `?`.
     * @spec SPEC-036
     */
    @Test
    fun `die Normalisierung macht aus Parameterstellen ein Fragezeichen`() {
        assertEquals("A =?B", CatalogForms.normalize(" A =  @P0 B ;"))
        assertEquals("F(?)", CatalogForms.normalize("F( @P12 )"))
        assertTrue(CatalogForm.entries.all { CatalogForms.recognize(it.template) == it })
    }
}
