package io.github.pt9912.tdswire.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests der Form C und des Konstantenterms von `SPEC-036`: `SELECT <Ganzzahl>`, `SET`-Anweisungen und
 * `WHERE <Ganzzahl> = <Ganzzahl>`.
 * Die Texte stammen aus der Aufzeichnung von d-migrate 1.12.0 (mssql-jdbc 13.4.0) vom 2026-11-05 gegen den
 * Testserver: `SELECT 1`, `SET LOCK_TIMEOUT 30000`, der Text mit sieben `SET`-Anweisungen von `data import` und
 * `SELECT * FROM [kunden] WHERE 1 = 0`. Die Abwandlungen sind von Hand geschrieben. Welche Texte die Form umfasst,
 * ist Setzung; nichts ist an einem SQL Server gemessen.
 */
class FixedFormsTest {
    private val t = tableRef("t")

    private fun assertFixed(expected: FixedForm, sql: String) {
        val result = recognize(sql)
        assertIs<Recognition.Fixed>(result, "erwartet Form C: $sql")
        assertEquals(expected, result.form, sql)
    }

    /** Der Zwilling ist Form C, jede Abwandlung ist weder Form C noch eine andere Form. */
    private fun assertOnlyFixedTwin(twin: String, vararg variants: String) {
        assertIs<Recognition.Fixed>(recognize(twin), "Zwilling muss Form C sein: $twin")
        for (variant in variants) assertNotRecognized(variant)
    }

    private fun settings(vararg options: Pair<String, String>) =
        FixedForm.Settings(options.map { SetOption(it.first, it.second) })

    /**
     * Happy: der Verbindungstest, mit abschließendem `;`, in jeder Schreibweise der Schlüsselwörter.
     * Wächter: eine Erkennung, die `SELECT 1` nicht von `SELECT 2` unterscheidet.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt SELECT 1`() {
        assertFixed(FixedForm.Probe, "SELECT 1")
        assertFixed(FixedForm.Probe, "select 1;")
        assertFixed(FixedForm.Probe, "  SeLeCt\r\n1 /* c */ ")
    }

    /**
     * Negativ zu `SELECT 1`: jede Abwandlung, die kein Ganzzahlliteral im Bereich von `Int32` ist, ist weder Form C
     * noch eine andere Form (die Ganzzahlen stehen in `erkennt SELECT <Ganzzahl>`).
     * @spec SPEC-036
     */
    @Test
    fun `SELECT 1 hat keine Abwandlung`() {
        assertOnlyFixedTwin(
            "SELECT 1",
            "SELECT 1.0",
            "SELECT 1, 1",
            "SELECT 1 FROM t",
            "SELECT 1; SELECT 1",
            "SELECT '1'",
            "SELECT @p",
            "SELECT 1 AS x",
            "SELECT",
        )
    }

    /**
     * Happy und Boundary zu `ADR-0058` Entscheidung 4: `SELECT <Ganzzahl>` mit einem Ganzzahlliteral im Bereich von
     * `Int32` (Ziffern, optional führendes `-`; auch `0`, `-0`, führende Nullen und beide Grenzen), mit abschließendem
     * `;`, in jeder Schreibweise des Schlüsselworts. `SELECT 1` bleibt der Verbindungstest. Wächter: eine Erkennung,
     * die den Wert verwirft, das Vorzeichen verliert oder die Grenze von `Int32` nicht prüft.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt SELECT mit einer Ganzzahl im Bereich Int32`() {
        assertFixed(FixedForm.IntegerConstant(2), "SELECT 2")
        assertFixed(FixedForm.IntegerConstant(-2), "select -2;")
        assertFixed(FixedForm.IntegerConstant(0), "SELECT 0")
        assertFixed(FixedForm.IntegerConstant(0), "SELECT -0")
        assertFixed(FixedForm.IntegerConstant(1), "SELECT 01")
        assertFixed(FixedForm.IntegerConstant(-1), "SeLeCt\r\n-1 /* c */ ")
        assertFixed(FixedForm.IntegerConstant(Int.MAX_VALUE), "SELECT 2147483647")
        assertFixed(FixedForm.IntegerConstant(Int.MIN_VALUE), "SELECT -2147483648")
        assertFixed(FixedForm.Probe, "SELECT 1")
    }

    /**
     * Negativ zu `SELECT <Ganzzahl>` (`ADR-0058` Entscheidung 4): außerhalb von `Int32`, Rechnen, mehrere Spalten,
     * Alias, Tabelle, andere Konstanten, Parameter und Systemvariablen, Klammer und jede Folge, auch
     * `SELECT <Ganzzahl>` als Teil der Form S2.
     * @spec SPEC-036
     */
    @Test
    fun `SELECT mit einer Ganzzahl hat keine Abwandlung`() {
        assertOnlyFixedTwin(
            "SELECT 2",
            "SELECT 2147483648",
            "SELECT -2147483649",
            "SELECT 99999999999999999999",
            "SELECT 1 + 1",
            "SELECT 1 / 0",
            "SELECT 2 * 3",
            "SELECT 1-1",
            "SELECT 1, 2",
            "SELECT 2 AS x",
            "SELECT 2 x",
            "SELECT 2 FROM t",
            "SELECT 2 WHERE 1 = 1",
            "SELECT 'a'",
            "SELECT N'2'",
            "SELECT NULL",
            "SELECT 1.5",
            "SELECT -2.0",
            "SELECT @p",
            "SELECT @@VERSION",
            "SELECT (2)",
            "SELECT 2 2",
            "SELECT 0x10",
            "SELECT 2; SELECT 2",
            "SELECT 2; SELECT 1",
            "SELECT a FROM t; SELECT 2",
            "SELECT 2; SELECT a FROM t",
            "SELECT 2;; ",
            "SELECT TOP 2",
            "SELECT",
        )
    }

    /**
     * Happy: `SET LOCK_TIMEOUT <Zahl>` und jede Option mit `ON` und `OFF`, in beliebiger Schreibweise; die Option
     * steht in Großschreibung, das Argument von `ON`/`OFF` ebenso, die Zahl wie geschrieben.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt SET LOCK_TIMEOUT und die Optionen mit ON und OFF`() {
        assertFixed(settings("LOCK_TIMEOUT" to "30000"), "SET LOCK_TIMEOUT 30000")
        assertFixed(settings("LOCK_TIMEOUT" to "-1"), "set lock_timeout -1;")
        val options = "ANSI_NULLS ANSI_PADDING ANSI_WARNINGS ARITHABORT CONCAT_NULL_YIELDS_NULL " +
            "QUOTED_IDENTIFIER NUMERIC_ROUNDABORT IMPLICIT_TRANSACTIONS"
        options.split(' ').forEach { option ->
            assertFixed(settings(option to "ON"), "SET $option ON")
            assertFixed(settings(option to "OFF"), "set ${option.lowercase()} off;")
        }
    }

    /**
     * Happy: mehrere `SET`-Anweisungen in einem Text, durch `;` getrennt, mit Zeilenumbruch; der Text ist der
     * aufgezeichnete von `data import`. Die Reihenfolge bleibt.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt mehrere SET durch Semikolon getrennt`() {
        val recorded = "SET ANSI_NULLS ON;\nSET ANSI_PADDING ON;\nSET ANSI_WARNINGS ON;\nSET ARITHABORT ON;\n" +
            "SET CONCAT_NULL_YIELDS_NULL ON;\nSET NUMERIC_ROUNDABORT OFF;\nSET QUOTED_IDENTIFIER ON"
        assertFixed(
            settings(
                "ANSI_NULLS" to "ON",
                "ANSI_PADDING" to "ON",
                "ANSI_WARNINGS" to "ON",
                "ARITHABORT" to "ON",
                "CONCAT_NULL_YIELDS_NULL" to "ON",
                "NUMERIC_ROUNDABORT" to "OFF",
                "QUOTED_IDENTIFIER" to "ON",
            ),
            recorded,
        )
        assertFixed(settings("ANSI_NULLS" to "ON", "LOCK_TIMEOUT" to "5"), "SET ANSI_NULLS ON; SET LOCK_TIMEOUT 5;")
    }

    /**
     * Negativ: Abwandlungen von `SET` sind nicht erkannt — unbekannte Option, fehlendes oder falsches Argument,
     * Zahl bei einer `ON`/`OFF`-Option, `ON` bei `LOCK_TIMEOUT`, Dezimalzahl, Variablenzuweisung, leere Anweisung
     * zwischen den `;`, fehlendes Trennzeichen, eine fremde Anweisung oder eine unbekannte Option neben gültigen
     * (alles oder nichts). Wächter: eine Erkennung, die nur das erste `SET` des Textes prüft.
     * @spec SPEC-036
     */
    @Test
    fun `SET hat keine unerkannte Abwandlung`() {
        assertOnlyFixedTwin(
            "SET ANSI_NULLS ON; SET LOCK_TIMEOUT 5",
            "SET FOO ON",
            "SET ANSI_NULLS",
            "SET ANSI_NULLS MAYBE",
            "SET ANSI_NULLS 1",
            "SET LOCK_TIMEOUT ON",
            "SET LOCK_TIMEOUT 1.5",
            "SET LOCK_TIMEOUT",
            "SET LOCK_TIMEOUT 1 2",
            "SET @x = 1",
            "SET ANSI_NULLS ON; ; SET ANSI_PADDING ON",
            "SET ANSI_NULLS ON SET ANSI_PADDING ON",
            "SET ANSI_NULLS ON; SELECT 1",
            "SET ANSI_NULLS ON; SET FOO ON",
            "SET FOO ON; SET ANSI_NULLS ON",
            "SET ANSI_NULLS ON OFF",
            "SET FMTONLY ON",
            "SET TRANSACTION ISOLATION LEVEL SNAPSHOT",
            "SET ANSI_NULLS ON; SET ANSI_NULLS",
        )
    }

    /**
     * Happy: `WHERE 1 = 0` und `WHERE 1 = 1` sind Konstantenterme; der Wert ist der Vergleich der Ganzzahlen, nicht
     * des Textes (`2 = 2`, `-3 = -3`, `007 = 7`). Der Term darf neben Spaltentermen stehen.
     * Wächter: ein Vergleich der Schreibweise statt der Zahl (`007 = 7` wäre falsch).
     * @spec SPEC-036
     */
    @Test
    fun `erkennt Konstantenterme in S1`() {
        assertRecognized(select(t, null, Term.Constant(false)), "SELECT * FROM t WHERE 1 = 0")
        assertRecognized(select(t, null, Term.Constant(true)), "SELECT * FROM t WHERE 1 = 1")
        assertRecognized(select(t, null, Term.Constant(true)), "SELECT * FROM t WHERE 2 = 2")
        assertRecognized(select(t, null, Term.Constant(true)), "SELECT * FROM t WHERE -3 = -3")
        assertRecognized(select(t, null, Term.Constant(true)), "SELECT * FROM t WHERE 007 = 7")
        assertRecognized(select(t, null, Term.Constant(false)), "SELECT * FROM t WHERE 7 = -7")
        assertRecognized(
            select(t, listOf("a"), eq("a", num("1")), Term.Constant(false), isNull("b")),
            "SELECT a FROM [t] WHERE a = 1 AND 1 = 0 AND b IS NULL;",
        )
        assertRecognized(
            select(tableRef("kunden"), null, Term.Constant(false)),
            "SELECT * FROM [kunden] WHERE 1 = 0",
        )
    }

    /**
     * Negativ: kein Konstantenterm sind Dezimalzahl, Text, Bezeichner, Parameter, andere Operatoren, `OR`, `NOT` und
     * eine Zahl allein; ein Ausdruck bleibt ausgeschlossen. Wächter: ein Parser, der jede Zahl links vom `=` annimmt.
     * @spec SPEC-036
     */
    @Test
    fun `Konstantenterm hat keine unerkannte Abwandlung`() {
        assertOnlyExtensionFails(
            "SELECT * FROM t WHERE 1 = 0",
            "SELECT * FROM t WHERE 1 = 0.5",
            "SELECT * FROM t WHERE 1.0 = 1",
            "SELECT * FROM t WHERE 1 = '0'",
            "SELECT * FROM t WHERE '1' = '1'",
            "SELECT * FROM t WHERE 1 = a",
            "SELECT * FROM t WHERE 1 = @p",
            "SELECT * FROM t WHERE 1 = NULL",
            "SELECT * FROM t WHERE 1 <> 0",
            "SELECT * FROM t WHERE 1 < 0",
            "SELECT * FROM t WHERE 1",
            "SELECT * FROM t WHERE 1 =",
            "SELECT * FROM t WHERE 1 = 0 = 0",
            "SELECT * FROM t WHERE 1 = 0 OR a = 1",
            "SELECT * FROM t WHERE NOT 1 = 0",
            "SELECT * FROM t WHERE 1 IN (1)",
            "SELECT * FROM t WHERE 1 + 1 = 2",
            "SELECT * FROM t WHERE a = 1 AND",
            "SELECT * FROM t WHERE 1 = 0 AND",
        )
    }

    /**
     * Negativ: der Konstantenterm gilt nur in S1. In `UPDATE` und `DELETE` wäre `WHERE 1 = 0` ohne Spaltenterm ein
     * Filter ohne Vorfilter, den der Callback als „alle Zeilen“ liest; deshalb ist er dort nicht erkannt.
     * Wächter: eine Zulassung des Terms in `whereClause()` für alle Formen.
     * @spec SPEC-036
     */
    @Test
    fun `Konstantenterm gilt nicht in UPDATE und DELETE`() {
        assertOnlyExtensionFails(
            "DELETE FROM t WHERE a = 1",
            "DELETE FROM t WHERE 1 = 0",
            "DELETE FROM t WHERE 1 = 1",
            "DELETE FROM t WHERE a = 1 AND 1 = 0",
        )
        assertOnlyExtensionFails(
            "UPDATE t SET a = 1 WHERE a = 2",
            "UPDATE t SET a = 1 WHERE 1 = 0",
            "UPDATE t SET a = 1 WHERE a = 2 AND 1 = 1",
        )
    }
}
