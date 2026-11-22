package io.github.pt9912.tdswire.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Form F von `SPEC-036` (`ExecuteReader(SchemaOnly)`, `ADR-0059` Entscheidung 2) in der Erkennung: die Folge `SET
 * FMTONLY OFF; SET FMTONLY ON; <Auswahl> [SET FMTONLY OFF] [;]`. Gemessen ist die Folge mit Spaltenlisten, `*` und
 * `WHERE Id = @i` (Messprotokoll W-26, W-28; die Texte stammen aus `schemaonly-spalten` und `schemaonly-sonderfaelle`);
 * die Grenzen der Erkennung sind Setzung.
 */
class SchemaFormsTest {
    private val t = tableRef("t")
    private val head = "SET FMTONLY OFF; SET FMTONLY ON;"

    private fun schemaOnly(select: Statement, closed: Boolean) =
        Recognition.SchemaOnly(select as Statement.Select, closed)

    /** Der Zwilling ist Form F, jede Abwandlung nicht erkannt: ein Negativfall ist nicht aus fremdem Grund rot. */
    private fun assertOnlyVariantsFail(twin: String, vararg variants: String) {
        assertIs<Recognition.SchemaOnly>(recognize(twin), "Zwilling muss Form F sein: $twin")
        for (variant in variants) assertNotRecognized(variant)
    }

    /**
     * Die Auswahl der Form F ist die der Probe ohne Zeilen: der Konstantenterm, der nie gilt, hängt an der Bedingung;
     * `closed` sagt, ob das schließende `SET` im Text steht. Wächter: eine Auswahl ohne den Konstantenterm (das Backend
     * öffnete die Zeilenquelle), ein vertauschtes `closed`.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt die Folge mit und ohne schliessendes SET`() {
        val never = Term.Constant(false)
        assertEquals(
            schemaOnly(select(t, listOf("a", "b"), never), true),
            recognize(" $head SELECT a, b FROM t SET FMTONLY OFF;"),
        )
        assertEquals(schemaOnly(select(t, null, never), false), recognize("$head SELECT * FROM t"))
        assertEquals(schemaOnly(select(t, null, never), false), recognize("$head SELECT * FROM t;"))
        assertEquals(
            schemaOnly(select(t, listOf("a"), eq("a", SimValue.Parameter("i")), never), false),
            recognize("$head SELECT a FROM t WHERE a = @i"),
        )
    }

    /**
     * Setzung: jede erkannte Bedingung von S1 gilt, eine `OR`-Folge wird wie bei der Probe zur UND-Folge mit dem
     * Konstantenterm. Wächter: eine `OR`-Folge, die `disjunction` behält (das Backend läse Zeilen).
     * @spec SPEC-036
     */
    @Test
    fun `nimmt die Bedingungen von S1 wie die Probe und macht eine OR-Folge zur UND-Folge`() {
        assertEquals(
            schemaOnly(select(t, listOf("a"), eq("a", num("1")), eq("b", num("2")), Term.Constant(false)), false),
            recognize("$head SELECT a FROM t WHERE a = 1 OR b = 2"),
        )
        assertEquals(
            schemaOnly(select(t, listOf("a"), Term.Constant(true), Term.Constant(false)), false),
            recognize("$head SELECT a FROM t WHERE 1 = 1"),
        )
    }

    /**
     * Die Probe der Zieltabelle (`SET FMTONLY ON …`, Form B) bleibt `Recognized` und ist nicht Form F; Form F beginnt
     * mit dem führenden `SET FMTONLY OFF;`. Wächter: eine Form F, die die Probe annimmt, oder eine Probe, die Form F
     * annimmt.
     * @spec SPEC-036
     */
    @Test
    fun `die Probe ist nicht Form F und Form F nicht die Probe`() {
        assertIs<Recognition.Recognized>(recognize("SET FMTONLY ON SELECT a FROM t"))
        assertIs<Recognition.Recognized>(recognize("SET FMTONLY ON SELECT a FROM t SET FMTONLY OFF"))
        assertIs<Recognition.SchemaOnly>(recognize("$head SELECT a FROM t"))
        assertNotRecognized("SET FMTONLY OFF SELECT a FROM t")
    }

    /**
     * Die Probe der Zieltabelle liest den Rest hinter `SET FMTONLY ON` nicht als Form F (`ADR-0059` Entscheidung 2
     * und 6): `SET FMTONLY ON` vor der Form, mit und ohne schließendes `SET`, im Text und als `sp_executesql`-Text, ist
     * nicht erkannt; der Zwilling ohne das erste `SET FMTONLY ON` ist Form F. Wächter: eine Probe, die ihren Rest
     * über die Erkennung mit Form F liest (das schließende `SET` fiele still weg, der Text würde beantwortet).
     * @spec SPEC-036
     */
    @Test
    fun `die Probe liest ihren Rest nicht als Form F`() {
        assertOnlyVariantsFail(
            "$head SELECT a FROM t SET FMTONLY OFF",
            "SET FMTONLY ON $head SELECT a FROM t",
            "SET FMTONLY ON $head SELECT a FROM t SET FMTONLY OFF",
            "SET FMTONLY ON $head SELECT a FROM t;",
            "EXEC sp_executesql N'SET FMTONLY ON $head SELECT a FROM t'",
            "sp_executesql N'SET FMTONLY ON $head SELECT a FROM t SET FMTONLY OFF'",
            "SET FMTONLY ON SET FMTONLY ON $head SELECT a FROM t",
        )
    }

    /**
     * Negativ: jede andere Folge ist nicht erkannt (`ADR-0059` Entscheidung 2), auch die Texte von `KeyInfo`
     * (`NO_BROWSETABLE`); der Zwilling ist erkannt, jede Abwandlung nicht. Wächter: eine Erkennung, die ein `;` vor dem
     * schließenden `SET`, eine fehlende `;`-Klammer der führenden `SET`, eine weitere Option oder eine andere
     * Reihenfolge annimmt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt jede andere Folge ab`() {
        assertOnlyVariantsFail(
            "$head SELECT a FROM t SET FMTONLY OFF",
            "$head SELECT a FROM t; SET FMTONLY OFF",
            "$head SELECT a FROM t SET FMTONLY OFF SET FMTONLY OFF",
            "SET FMTONLY OFF SET FMTONLY ON; SELECT a FROM t",
            "SET FMTONLY OFF x SET FMTONLY ON; SELECT a FROM t",
            "SET FMTONLY OFF; SET FMTONLY ON x SELECT a FROM t",
            "SET FMTONLY OFF; SET FMTONLY ON SELECT a FROM t",
            "SET FMTONLY ON; SET FMTONLY OFF; SELECT a FROM t",
            "SET FMTONLY OFF; SET FMTONLY OFF; SELECT a FROM t",
            "SET FMTONLY OFF; SET NO_BROWSETABLE ON; SET FMTONLY ON; SELECT a FROM t",
            "SET FMTONLY OFF; SET NO_BROWSETABLE ON; SELECT a FROM t SET NO_BROWSETABLE OFF;",
            "SET FMTONLY OFF; SET FMTONLY ON; SET NOCOUNT ON; SELECT a FROM t",
            "$head SELECT a FROM t;;",
            head,
            "SET FMTONLY OFF; SET FMTONLY ON; SET FMTONLY OFF",
        )
    }

    /**
     * Negativ (Setzung): hinter den `SET` ist nur die Form S1 aus einer Tabelle erkannt; `SELECT <Ganzzahl>`, S2,
     * Änderungen, `WAITFOR`, ein Alias und ein Ausdruck sind nicht erkannt. Wächter: eine Erkennung, die jede Form der
     * Liste hinter den `SET` zulässt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt alles ausser einer Auswahl der Form S1 aus einer Tabelle ab`() {
        assertOnlyVariantsFail(
            "$head SELECT a FROM t",
            "$head SELECT 2",
            "$head SELECT 1",
            "$head SELECT a FROM t; SELECT b FROM t",
            "$head INSERT INTO t (a) VALUES (1)",
            "$head UPDATE t SET a = 1",
            "$head DELETE FROM t",
            "$head MERGE INTO t USING (VALUES (1)) AS s (a) ON t.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (s.a)",
            "$head CREATE TABLE u (a int)",
            "$head WAITFOR DELAY '00:00:01'",
            "$head SELECT a AS b FROM t",
            "$head SELECT a + 1 FROM t",
        )
    }
}
