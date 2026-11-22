package io.github.pt9912.tdswire.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests der Zuordnung zu S1, W1, W2 und W3 (`SPEC-036`) und der Liste „Nicht erkannt“: je Zeile der Liste ein
 * Negativtest, jeweils mit einem Zwilling, der erkannt wird, damit die Ablehnung nicht aus einem fremden Grund
 * geschieht. Die Erwartungen sind von Hand geschrieben und benutzen den Scanner nicht. Was keine Form der Liste ist,
 * ist nicht erkannt (ADR-0033 Nr. 11, ADR-0058 Entscheidung 1); die Grenzen der Formen sind Festlegung, nichts ist an
 * einem Server gemessen außer den in den Tests genannten Fällen (ADR-0058 Kontext).
 */
class StatementFormsTest {
    private val t = tableRef("t")

    /**
     * S1 in seinen Ausprägungen: Spaltenliste, `*`, Schema, Bedingungen mit `=`, `IN`, `IS [NOT] NULL`.
     * @spec SPEC-036
     */
    @Test
    fun `ordnet S1 zu`() {
        assertRecognized(select(t, listOf("a", "b")), "SELECT a, b FROM t")
        assertRecognized(select(tableRef("T", "dbo"), null, eq("A", num("1"))), "select * from dbo.T where A = 1")
        assertRecognized(select(tableRef("my table"), listOf("a b", "c d")), "SELECT [a b], \"c d\" FROM [my table]")
        assertRecognized(select(tableRef("t", "sales"), listOf("a")), "SELECT a FROM [sales].[t]")
        assertRecognized(select(tableRef("t", "sales"), listOf("a")), "SELECT a FROM \"sales\".t")
        assertRecognized(
            select(
                t,
                listOf("a"),
                eq("a", num("1")),
                inList("b", num("1"), txt("x"), ntxt("y"), SimValue.Null, param("p")),
                isNull("c"),
                isNotNull("d"),
            ),
            "SELECT a FROM t WHERE a = 1 AND b IN (1, 'x', N'y', NULL, @p) AND c IS NULL AND d IS NOT NULL",
        )
        assertRecognized(
            select(t, listOf("a", "a"), eq("a", num("1")), eq("a", num("2"))),
            "SELECT a, a FROM t WHERE a = 1 AND a = 2",
        )
        assertRecognized(select(t, listOf("a")), "SELECT a FROM t;")
        assertRecognized(select(t, listOf("a")), "SELECT a FROM t ;  ")
    }

    /**
     * W1: Spaltenliste Pflicht, ebenso viele Werte, ein Wert ist Zahl, Text, `N`-Text, `NULL` oder Parameter.
     * @spec SPEC-036
     */
    @Test
    fun `ordnet W1 zu`() {
        assertRecognized(insert(t, listOf("a", "b"), num("1"), txt("x")), "INSERT INTO t (a, b) VALUES (1, 'x')")
        assertRecognized(
            insert(tableRef("t", "dbo"), listOf("a", "b c", "d"), param("x"), SimValue.Null, ntxt("")),
            "insert into dbo.t ([a], \"b c\", d) values (@x, null, N'');",
        )
        assertRecognized(insert(t, listOf("a"), num("-5")), "INSERT INTO t(a)VALUES(-5)")
    }

    /**
     * W2 mit und ohne `WHERE`, mehreren Zuweisungen.
     * @spec SPEC-036
     */
    @Test
    fun `ordnet W2 zu`() {
        assertRecognized(update(t, listOf(set("a", num("1")))), "UPDATE t SET a = 1")
        assertRecognized(
            update(t, listOf(set("a", num("1")), set("b", ntxt("x"))), eq("c", param("p")), isNull("d")),
            "update t set a = 1, b = N'x' where c = @p and d is null",
        )
        assertRecognized(update(tableRef("t", "s"), listOf(set("a", SimValue.Null))), "UPDATE s.t SET a = NULL;")
    }

    /**
     * W3 mit und ohne `WHERE`.
     * @spec SPEC-036
     */
    @Test
    fun `ordnet W3 zu`() {
        assertRecognized(delete(t), "DELETE FROM t")
        assertRecognized(delete(t, eq("a", num("1")), isNotNull("b")), "delete from t where a = 1 and b is not null;")
        assertRecognized(delete(tableRef("t", "s"), inList("a", num("1"))), "DELETE FROM s.t WHERE a IN (1)")
    }

    /**
     * Zeile „JOIN und weitere Relationen“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt JOIN und weitere Relationen ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a = 1",
            "SELECT a FROM t JOIN u ON t.a = u.a",
            "SELECT a FROM t INNER JOIN u ON a = b",
            "SELECT a FROM t CROSS JOIN u",
            "SELECT a FROM t LEFT OUTER JOIN u ON a = b WHERE a = 1",
            "SELECT a FROM t, u",
            "SELECT a FROM t, u WHERE a = 1",
            "SELECT t.a FROM t",
            "UPDATE t SET a = 1 FROM u",
            "DELETE FROM t FROM u",
        )
    }

    /**
     * Zeile „Unterabfragen“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt Unterabfragen ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a IN (1, 2)",
            "SELECT a FROM (SELECT a FROM t)",
            "SELECT a FROM t WHERE a IN (SELECT b FROM u)",
            "SELECT (SELECT 1) FROM t",
            "SELECT a FROM t WHERE a = (SELECT 1)",
            "INSERT INTO t (a) VALUES ((SELECT 1))",
            "INSERT INTO t (a) SELECT b FROM u",
            "UPDATE t SET a = (SELECT 1)",
            "DELETE FROM t WHERE a IN (SELECT b FROM u)",
        )
    }

    /**
     * Zeile „WITH“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt WITH ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t",
            "WITH x AS (SELECT a FROM t) SELECT a FROM x",
            ";WITH x AS (SELECT a FROM t) SELECT a FROM x",
            "WITH x AS (SELECT a FROM t) DELETE FROM t",
        )
    }

    /**
     * Zeile „Aggregate und GROUP BY“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt Aggregate und GROUP BY ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t",
            "SELECT COUNT(*) FROM t",
            "SELECT SUM(a) FROM t",
            "SELECT MAX(a), b FROM t",
            "SELECT a FROM t GROUP BY a",
            "SELECT a FROM t WHERE a = 1 GROUP BY a",
            "SELECT a FROM t GROUP BY a HAVING a = 1",
        )
    }

    /**
     * Zeile „DISTINCT“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt DISTINCT ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t",
            "SELECT DISTINCT a FROM t",
            "SELECT DISTINCT * FROM t",
            "SELECT ALL a FROM t",
            "select distinct a, b from t",
        )
    }

    /**
     * Zeile „TOP“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt TOP ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t",
            "SELECT TOP 5 a FROM t",
            "SELECT TOP (5) a FROM t",
            "SELECT TOP 5 PERCENT * FROM t",
            "UPDATE TOP (1) t SET a = 1",
            "DELETE TOP (1) FROM t",
        )
    }

    /**
     * Zeile „ORDER BY“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt ORDER BY ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a = 1",
            "SELECT a FROM t ORDER BY a",
            "SELECT a FROM t WHERE a = 1 ORDER BY a DESC",
            "SELECT a FROM t ORDER BY 1",
            "SELECT * FROM t ORDER BY a, b",
        )
    }

    /**
     * Zeile „OFFSET/FETCH“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt OFFSET und FETCH ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t",
            "SELECT a FROM t ORDER BY a OFFSET 5 ROWS FETCH NEXT 5 ROWS ONLY",
            "SELECT a FROM t OFFSET 5 ROWS",
            "SELECT a FROM t FETCH NEXT 5 ROWS ONLY",
        )
    }

    /**
     * Zeile „Aliase für Tabellen und Spalten“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt Aliase ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a = 1",
            "SELECT a FROM t x",
            "SELECT a FROM t AS x",
            "SELECT a x FROM t",
            "SELECT a AS x FROM t",
            "SELECT a AS [x] FROM t",
            "SELECT x.a FROM t x",
            "SELECT a FROM t x WHERE x.a = 1",
            "DELETE FROM t x",
            "UPDATE t x SET a = 1",
        )
    }

    /**
     * Zeile „Ausdrücke und Funktionen in Listen und Bedingungen“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt Ausdrücke und Funktionen ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a = 1",
            "SELECT a + 1 FROM t",
            "SELECT UPPER(a) FROM t",
            "SELECT 1 FROM t",
            "SELECT a FROM t WHERE a = 1 + 1",
            "SELECT a FROM t WHERE a = 'x' + 'y'",
            "SELECT a FROM t WHERE a = UPPER(b)",
            "SELECT a FROM t WHERE a = b",
            "SELECT a FROM t WHERE (a = 1)",
            "SELECT a FROM t WHERE a = (1)",
            "SELECT a FROM t WHERE a = -(1)",
            "UPDATE t SET a = a + 1",
            "INSERT INTO t (a) VALUES (1 + 1)",
            "INSERT INTO t (a) VALUES (GETDATE())",
            "SELECT CASE WHEN a = 1 THEN b END FROM t",
        )
    }

    /**
     * Zeile „OR“ nach `ADR-0058` Entscheidung 3: nur in S1 und nur als reine Folge; jede Mischung mit `AND`, jede
     * Klammer, der Konstantenterm in der Folge und `OR` in `UPDATE` und `DELETE` bleiben nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt OR ausserhalb der reinen Folge in S1 ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a = 1 OR b = 2",
            "SELECT a FROM t WHERE a = 1 AND b = 2 OR c = 3",
            "SELECT a FROM t WHERE a = 1 OR b = 2 AND c = 3",
            "SELECT a FROM t WHERE a = 1 AND b = 2 OR c = 3 AND d = 4",
            "SELECT a FROM t WHERE (a = 1 OR b = 2)",
            "SELECT a FROM t WHERE a = 1 OR (b = 2)",
            "SELECT a FROM t WHERE (a = 1) OR b = 2",
            "SELECT a FROM t WHERE 1 = 1 OR a = 1",
            "SELECT a FROM t WHERE a = 1 OR 1 = 1",
            "SELECT a FROM t WHERE a = 1 OR 1 = 0",
            "SELECT a FROM t WHERE a = 1 OR",
            "SELECT a FROM t WHERE OR a = 1",
            "SELECT a FROM t WHERE a = 1 OR OR b = 2",
            "SELECT a FROM t WHERE a = 1 OR b",
            "SELECT a FROM t WHERE a = 1 OR NOT b = 2",
            "SELECT a FROM t WHERE a = 1 OR b < 2",
            "SELECT a FROM t WHERE a = 1 OR b LIKE 'x'",
            "SELECT a FROM t WHERE a = 1 OR b BETWEEN 1 AND 2",
            "SELECT a FROM t WHERE a = 1 OR b <> 2",
            "SELECT a FROM t WHERE a = 1 OR b != 2",
            "SELECT a FROM t WHERE a = 1 OR b NOT IN (1, 2)",
            "UPDATE t SET a = 1 WHERE a = 1 OR a = 2",
            "UPDATE t SET a = 1 WHERE a = 1 AND b = 1 OR a = 2",
            "DELETE FROM t WHERE a = 1 OR b = 2",
            "DELETE FROM t WHERE a = 1 OR a = 2 OR a = 3",
            "INSERT INTO t (a) VALUES (1) OR a = 2",
        )
    }

    /**
     * Happy und Boundary zu `ADR-0058` Entscheidung 3: eine reine `OR`-Folge aus zwei oder mehr Termen (`=`, `IN`,
     * `IS [NOT] NULL`) in S1, in jeder Schreibweise der Schlüsselwörter; ein Term allein ist keine Folge, und `AND`
     * bleibt UND. Wächter: ein Parser, der `OR` wie `AND` liest (dann fehlt das Kennzeichen der Folge).
     * @spec SPEC-036
     */
    @Test
    fun `ordnet S1 mit reiner OR-Folge zu`() {
        assertRecognized(
            selectAny(t, listOf("a"), eq("a", num("1")), eq("b", num("2"))),
            "SELECT a FROM t WHERE a = 1 OR b = 2",
        )
        assertRecognized(
            selectAny(t, null, eq("Id", num("1")), eq("Id", num("2"))),
            "select * from t where Id = 1 or Id = 2",
        )
        assertRecognized(
            selectAny(
                t,
                listOf("a"),
                eq("a", num("1")),
                inList("b", num("1"), SimValue.Null),
                isNull("c"),
                isNotNull("d"),
            ),
            "SELECT a FROM t WHERE a = 1 OR b IN (1, NULL) OR c IS NULL OR d IS NOT NULL;",
        )
        assertRecognized(
            selectAny(t, listOf("a"), eq("a", param("p")), eq("a", txt("x"))),
            "SELECT a FROM t WHERE a = @p OR a = 'x' /* c */",
        )
        assertRecognized(select(t, listOf("a"), eq("a", num("1"))), "SELECT a FROM t WHERE a = 1")
        assertRecognized(
            select(t, listOf("a"), eq("a", num("1")), eq("b", num("2"))),
            "SELECT a FROM t WHERE a = 1 AND b = 2",
        )
    }

    /**
     * Form S2 (`ADR-0058` Entscheidung 3): jede der zwei Auswahlen darf eine `OR`-Folge tragen (Setzung), auch eine
     * mit `AND`, jede für sich.
     * @spec SPEC-036
     */
    @Test
    fun `S2 erlaubt je Auswahl eine OR-Folge`() {
        val first = select(t, listOf("a"), eq("a", num("1")), eq("b", num("2"))) as Statement.Select
        val second = selectAny(t, listOf("b"), eq("a", num("1")), eq("a", num("2"))) as Statement.Select
        assertEquals(
            Recognition.Series(listOf(first, second)),
            recognize("SELECT a FROM t WHERE a = 1 AND b = 2; SELECT b FROM t WHERE a = 1 OR a = 2"),
        )
        assertNotRecognized("SELECT a FROM t WHERE a = 1 OR b = 2; SELECT b FROM t WHERE a = 1 AND b = 2 OR c = 3")
    }

    /**
     * Hinter `SET FMTONLY ON` ist eine `OR`-Folge die UND-Folge mit dem Konstantenterm, der nie gilt: die Auswahl
     * liefert Spalten und nie eine Zeile.
     * @spec SPEC-036
     */
    @Test
    fun `OR-Folge hinter FMTONLY wird zur Auswahl ohne Zeile`() {
        assertRecognized(
            select(t, listOf("a"), eq("a", num("1")), eq("b", num("2")), Term.Constant(false)),
            "SET FMTONLY ON SELECT a FROM t WHERE a = 1 OR b = 2",
        )
    }

    /**
     * Zeile „NOT“; `IS NOT NULL` ist ein Term und bleibt erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt NOT ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a IS NOT NULL",
            "SELECT a FROM t WHERE NOT a = 1",
            "SELECT a FROM t WHERE a NOT IN (1, 2)",
            "SELECT a FROM t WHERE NOT (a = 1)",
            "SELECT a FROM t WHERE a NOT LIKE 'x'",
            "SELECT a FROM t WHERE a NOT BETWEEN 1 AND 2",
            "SELECT a FROM t WHERE a IS NOT 1",
            "SELECT a FROM t WHERE a IS NOT",
        )
    }

    /**
     * Zeile „<, >“ samt ihrer Verwandten `<>`, `!=`, `<=`, `>=`.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt Vergleiche außer Gleichheit ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a = 1",
            "SELECT a FROM t WHERE a < 1",
            "SELECT a FROM t WHERE a > 1",
            "SELECT a FROM t WHERE a <= 1",
            "SELECT a FROM t WHERE a >= 1",
            "SELECT a FROM t WHERE a <> 1",
            "SELECT a FROM t WHERE a != 1",
            "SELECT a FROM t WHERE a !< 1",
            "SELECT a FROM t WHERE a == 1",
            "SELECT a FROM t WHERE a =< 1",
        )
    }

    /**
     * Zeile „LIKE“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt LIKE ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a = 'x'",
            "SELECT a FROM t WHERE a LIKE 'x%'",
            "select a from t where a like N'x'",
            "DELETE FROM t WHERE a LIKE 'x'",
        )
    }

    /**
     * Zeile „BETWEEN“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt BETWEEN ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t WHERE a = 1 AND b = 2",
            "SELECT a FROM t WHERE a BETWEEN 1 AND 2",
            "SELECT a FROM t WHERE a BETWEEN 1 AND 2 AND b = 2",
            "UPDATE t SET a = 1 WHERE a BETWEEN 1 AND 2",
        )
    }

    /**
     * Zeile „OUTPUT-Klauseln“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt OUTPUT ab`() {
        assertOnlyExtensionFails(
            "INSERT INTO t (a) VALUES (1)",
            "INSERT INTO t (a) OUTPUT inserted.a VALUES (1)",
            "INSERT INTO t (a) VALUES (1) OUTPUT inserted.a",
            "DELETE FROM t OUTPUT deleted.a",
            "UPDATE t SET a = 1 OUTPUT inserted.a",
            "UPDATE t SET a = 1 OUTPUT inserted.a WHERE a = 2",
        )
    }

    /**
     * Zeile „mehrere Anweisungen in einem Text“: genau ein abschließendes `;` fällt weg; ein weiteres `;`, ein leeres
     * Stück und jede Folge, die nicht aus zwei Auswahlen der Form S1 besteht, macht den Text nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt andere Folgen als zwei Auswahlen ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t;",
            "SELECT a FROM t;;",
            "SELECT a FROM t; ;",
            "SELECT a FROM t ; /* c */ ;",
            ";SELECT a FROM t",
            "SELECT a FROM t SELECT b FROM t",
            "SELECT a FROM t; -- c\nSELECT 1",
            "INSERT INTO t (a) VALUES (1); INSERT INTO t (a) VALUES (2)",
            "DELETE FROM t; DELETE FROM u",
            "SELECT a FROM t; DELETE FROM u",
            "DELETE FROM u; SELECT a FROM t",
            "SELECT a FROM t; SELECT b FROM t; SELECT c FROM t",
            "SELECT a FROM t; SELECT b FROM t;;",
            "UPDATE t SET a = 1;; ",
        )
        assertNotRecognized(";", Unrecognized.EMPTY_TEXT)
        assertNotRecognized("SELECT a FROM t;;", Unrecognized.UNEXPECTED_TOKEN)
        assertNotRecognized(";SELECT a FROM t", Unrecognized.UNEXPECTED_TOKEN)
        assertNotRecognized("SELECT a FROM t; SELECT b FROM t; SELECT c FROM t", Unrecognized.TOO_MANY_STATEMENTS)
    }

    /**
     * Form S2: zwei Auswahlen der Form S1, durch `;` getrennt, mit oder ohne abschließendes `;`; ein `;` in Literal,
     * Bezeichner oder Kommentar trennt nicht.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt zwei Auswahlen in einem Text`() {
        val expected = Recognition.Series(
            listOf(
                Statement.Select(TableRef(null, "t"), listOf("a"), listOf(Term.In("a", listOf(SimValue.Number("1"))))),
                Statement.Select(TableRef(null, "t"), listOf("b"), listOf(Term.In("a", listOf(SimValue.Number("2"))))),
            ),
        )
        assertEquals(expected, recognize("SELECT a FROM t WHERE a = 1; SELECT b FROM t WHERE a = 2"))
        assertEquals(expected, recognize("SELECT a FROM t WHERE a = 1;SELECT b FROM t WHERE a = 2;"))
        assertEquals(expected, recognize("SELECT a FROM t WHERE a = 1 -- ;\n; /* ; */ SELECT b FROM t WHERE a = 2"))

        val literal = assertIs<Recognition.Series>(recognize("SELECT a FROM t WHERE a = 'x;y'; SELECT [b;c] FROM t"))
        assertEquals(listOf("b;c"), literal.selects.last().columns)
    }

    /**
     * Zeile „alles, was keine Form der Tabelle ist“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt alles ab, was keine Form ist`() {
        assertOnlyExtensionFails(
            "SELECT a FROM t",
            "EXEC sp_who",
            "EXEC [sp_who]",
            "CREATE CLUSTERED INDEX i ON t (a)",
            "DROP TABLE t",
            "TRUNCATE TABLE t",
            "MERGE t USING u ON a = b WHEN MATCHED THEN DELETE",
            "SET NOCOUNT ON",
            "USE db",
            "BEGIN TRAN",
            "SELECT @@VERSION",
            "DECLARE @x int",
            "INSERT INTO t DEFAULT VALUES",
            "TRUNCATE t",
            "t",
            "1",
            "'SELECT a FROM t'",
        )
    }

    /**
     * Unvollständige Formen sind nicht erkannt: endet der Text zu früh, ist der Grund „Ende“, steht ein
     * falsches Token da, „Token“.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt unvollständige Formen ab`() {
        val twin = "SELECT a FROM t WHERE a = 1"
        assertIs<Recognition.Recognized>(recognize(twin))
        val atEnd = listOf(
            "SELECT",
            "SELECT a",
            "SELECT a FROM",
            "SELECT a FROM t WHERE",
            "SELECT a FROM t WHERE a",
            "SELECT a FROM t WHERE a =",
        )
        for (sql in atEnd) assertNotRecognized(sql, Unrecognized.UNEXPECTED_END)
        val atToken = listOf(
            "SELECT FROM t",
            "SELECT a, FROM t",
            "SELECT *, a FROM t",
            "SELECT a FROM t WHERE = 1",
            "SELECT a FROM t WHERE a IN ()",
            "SELECT a FROM t WHERE a IN (1,)",
            "SELECT a FROM t WHERE a IN 1",
        )
        for (sql in atToken) assertNotRecognized(sql, Unrecognized.UNEXPECTED_TOKEN)
        assertEquals(6, atEnd.size)
        assertNotRecognized("UPDATE t SET", Unrecognized.UNEXPECTED_END)
        assertNotRecognized("UPDATE t SET a", Unrecognized.UNEXPECTED_END)
        assertNotRecognized("UPDATE t SET a = 1,", Unrecognized.UNEXPECTED_END)
        assertNotRecognized("DELETE FROM", Unrecognized.UNEXPECTED_END)
        assertNotRecognized("INSERT INTO t (a) VALUES (", Unrecognized.UNEXPECTED_END)
    }

    /**
     * `INSERT` ohne Spaltenliste, `DELETE` ohne `FROM`, `INSERT` ohne `INTO`, mehrere Wertezeilen, ungleich viele
     * Spalten und Werte und eine wiederholte Spalte sind nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt Sonderformen von INSERT, UPDATE und DELETE ab`() {
        assertOnlyExtensionFails(
            "INSERT INTO t (a, b) VALUES (1, 2)",
            "INSERT INTO t VALUES (1, 2)",
            "INSERT t (a, b) VALUES (1, 2)",
            "INSERT INTO t (a, b) VALUES (1, 2), (3, 4)",
            "INSERT INTO t () VALUES ()",
        )
        assertNotRecognized("INSERT INTO t (a, b) VALUES (1)", Unrecognized.COUNT_MISMATCH)
        assertNotRecognized("INSERT INTO t (a) VALUES (1, 2)", Unrecognized.COUNT_MISMATCH)
        assertNotRecognized("INSERT INTO t (a, A) VALUES (1, 2)", Unrecognized.DUPLICATE_COLUMN)
        assertNotRecognized("UPDATE t SET a = 1, [A] = 2", Unrecognized.DUPLICATE_COLUMN)
        assertOnlyExtensionFails("DELETE FROM t WHERE a = 1", "DELETE t WHERE a = 1", "DELETE t")
    }

    /**
     * Dreiteilige Namen und Namen mit leerem Teil sind nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt dreiteilige Namen ab`() {
        assertOnlyExtensionFails(
            "SELECT a FROM dbo.t",
            "SELECT a FROM db.dbo.t",
            "SELECT a FROM [db].[dbo].[t]",
            "SELECT a FROM .t",
            "SELECT a FROM dbo..t",
            "SELECT a FROM t.",
            "DELETE FROM db.dbo.t",
            "UPDATE db.dbo.t SET a = 1",
            "INSERT INTO db.dbo.t (a) VALUES (1)",
        )
    }
}
