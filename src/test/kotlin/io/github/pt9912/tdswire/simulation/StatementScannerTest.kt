package io.github.pt9912.tdswire.simulation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests der Grenzen des Zeichenscanners (`SPEC-036` Scanner): Literale, Bezeichner, Kommentare, Zahlen,
 * Leerraum, Parameter und reservierte Wörter, je Grenze mit eigener Eingabe. Alles sind Festlegungen der
 * Spezifikation, nicht am Server gemessen; die Erwartungen sind von Hand geschrieben.
 */
class StatementScannerTest {
    private val t = tableRef("t")

    private fun value(sql: String): SimValue {
        val result = recognize(sql)
        val statement = (result as? Recognition.Recognized)?.statement as? Statement.Insert
        return statement?.values?.single() ?: error("nicht erkannt: $sql ($result)")
    }

    private fun text(literal: String): SimValue = value("INSERT INTO t (a) VALUES ($literal)")

    private fun column(identifier: String): String {
        val result = recognize("SELECT $identifier FROM t")
        val statement = (result as? Recognition.Recognized)?.statement as? Statement.Select
        return statement?.columns?.single() ?: error("nicht erkannt: $identifier ($result)")
    }

    /**
     * Literale: `;`, `--`, Blockkommentar, Klammern und Anführungszeichen im Text bleiben Text; ein
     * verdoppeltes Hochkomma ist ein Hochkomma.
     * @spec SPEC-036
     */
    @Test
    fun `liest Literale mit Sonderzeichen als Text`() {
        val cases = mapOf(
            "''" to "",
            "'x'" to "x",
            "'it''s'" to "it's",
            "''''" to "'",
            "''''''" to "''",
            "';'" to ";",
            "'a;b'" to "a;b",
            "'--'" to "--",
            "'a -- b'" to "a -- b",
            "'/* c */'" to "/* c */",
            "'/*'" to "/*",
            "'*/'" to "*/",
            "'['" to "[",
            "']'" to "]",
            "'\"'" to "\"",
            "'a\nb'" to "a\nb",
            "'ä€'" to "ä€",
            "' '" to " ",
            "'N'" to "N",
        )
        for ((literal, expected) in cases) assertEquals(txt(expected), text(literal), literal)
        assertEquals(19, cases.size)
    }

    /**
     * `N'…'` gilt mit `N` oder `n` unmittelbar vor dem Hochkomma; sonst ist es kein Literal.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt N-Literale nur unmittelbar`() {
        assertEquals(ntxt("x"), text("N'x'"))
        assertEquals(ntxt("x"), text("n'x'"))
        assertEquals(ntxt("it's"), text("N'it''s'"))
        assertEquals(ntxt(""), text("N''"))
        for (bad in listOf("N 'x'", "NN'x'", "xN'x'", "N/**/'x'", "N-- c\n'x'", "[N]'x'")) {
            assertNotRecognized("INSERT INTO t (a) VALUES ($bad)")
        }
    }

    /**
     * Ein offenes Literal ist nicht erkannt, auch wenn das Ende wie ein verdoppeltes Hochkomma aussieht.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt offene Literale ab`() {
        for (open in listOf("'abc", "'", "'abc''", "N'abc", "'a'' ", "''' ")) {
            assertNotRecognized("INSERT INTO t (a) VALUES ($open", Unrecognized.UNTERMINATED_LITERAL)
        }
        assertRecognized(insert(t, listOf("a"), num("1")), "INSERT INTO t (a) VALUES (1) -- '")
    }

    /**
     * Bezeichner in `[…]` und `"…"`: Sonderzeichen bleiben Name, `]]` und `""` stehen für ein Zeichen.
     * @spec SPEC-036
     */
    @Test
    fun `liest quotierte Bezeichner mit Sonderzeichen`() {
        val cases = mapOf(
            "[a]" to "a",
            "[a b]" to "a b",
            "[a]]b]" to "a]b",
            "[]]]" to "]",
            "[a;b]" to "a;b",
            "[--]" to "--",
            "[/* x */]" to "/* x */",
            "[\"]" to "\"",
            "[']" to "'",
            "[[]" to "[",
            "\"a\"" to "a",
            "\"a b\"" to "a b",
            "\"a\"\"b\"" to "a\"b",
            "\"\"\"\"" to "\"",
            "\"[\"" to "[",
            "\"]\"" to "]",
            "\"'\"" to "'",
            "[select]" to "select",
            "\"FROM\"" to "FROM",
            "[null]" to "null",
        )
        for ((identifier, expected) in cases) assertEquals(expected, column(identifier), identifier)
        assertEquals(20, cases.size)
    }

    /**
     * Ein leerer oder offener Bezeichner ist nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt leere und offene Bezeichner ab`() {
        assertNotRecognized("SELECT [] FROM t", Unrecognized.EMPTY_IDENTIFIER)
        assertNotRecognized("SELECT \"\" FROM t", Unrecognized.EMPTY_IDENTIFIER)
        for (sql in listOf("SELECT [abc", "SELECT \"abc", "SELECT [", "SELECT \"", "SELECT [a]]", "SELECT a FROM [t")) {
            assertNotRecognized(sql, Unrecognized.UNTERMINATED_IDENTIFIER)
        }
    }

    /**
     * Unquotierte Bezeichner: Buchstaben (auch Unicode), Ziffern ab der zweiten Stelle, `_`, `#`, `$`.
     * @spec SPEC-036
     */
    @Test
    fun `liest unquotierte Bezeichner`() {
        for (name in listOf("a", "A1", "_x", "#tmp", "\$x", "a_b", "Ärger", "a#b\$c", "a1b2", "x\$")) {
            assertEquals(name, column(name), name)
        }
        for (bad in listOf("a-b", "a.b.c", "a!", "a b c", "a%", "a:b", "ü±")) {
            assertNotRecognized("SELECT $bad FROM t")
        }
        assertNotRecognized("SELECT 1a FROM t", Unrecognized.MALFORMED_NUMBER)
    }

    /**
     * Nur die fünfzehn Wörter sind unquotiert keine Bezeichner, ohne Beachtung der Schreibweise; quotiert sind sie
     * Namen. Andere Wörter, auch `TOP` und `ORDER`, sind gewöhnliche Namen (Festlegung nach der Liste der
     * Spezifikation).
     * @spec SPEC-036
     */
    @Test
    fun `reserviert genau fünfzehn Wörter`() {
        val reserved = listOf(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "IS", "NULL", "INSERT", "INTO", "VALUES", "UPDATE",
            "SET", "DELETE",
        )
        for (word in reserved) {
            for (spelling in listOf(word, word.lowercase(), word.lowercase().replaceFirstChar { it.uppercase() })) {
                assertNotRecognized("SELECT $spelling FROM t", Unrecognized.UNEXPECTED_TOKEN)
                assertNotRecognized("SELECT a FROM $spelling", Unrecognized.UNEXPECTED_TOKEN)
            }
            assertEquals(word, column("[$word]"))
            assertEquals(word.lowercase(), column("\"${word.lowercase()}\""))
        }
        assertEquals(15, reserved.size)
        for (word in listOf("TOP", "ORDER", "BY", "DISTINCT", "JOIN", "AS", "LIKE", "ınto", "selecting")) {
            assertEquals(word, column(word), word)
        }
    }

    /**
     * Kommentare: `--` bis CR, LF oder Textende, auch mit Zeichen darin, die sonst etwas bedeuten.
     * @spec SPEC-036
     */
    @Test
    fun `überliest Zeilenkommentare`() {
        val expected = select(t, listOf("a"))
        for (sql in listOf(
            "SELECT a -- c\nFROM t",
            "SELECT a -- c\rFROM t",
            "SELECT a -- c\r\nFROM t",
            "SELECT a FROM t -- c",
            "SELECT a FROM t --",
            "SELECT a FROM t -- ; SELECT b FROM t",
            "SELECT a FROM t -- ' [ \" /* \n",
            "-- first\nSELECT a FROM t",
            "SELECT a --\nFROM --\nt",
            "SELECT a FROM t;--c",
            "SELECT a FROM t -- c\n;",
            "SELECT a FROM t --- c",
        )) {
            assertRecognized(expected, sql)
        }
        assertNotRecognized("-- only", Unrecognized.EMPTY_TEXT)
        assertNotRecognized("--", Unrecognized.EMPTY_TEXT)
        assertNotRecognized("SELECT a FROM t -- c\nORDER BY a")
    }

    /**
     * Blockkommentare schachteln; ein offener Kommentar und ein einzelnes `*` mit `/` sind nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `überliest geschachtelte Blockkommentare`() {
        val expected = select(t, listOf("a"))
        for (sql in listOf(
            "SELECT/**/a/**/FROM/**/t",
            "SELECT /* c */ a FROM t",
            "SELECT /* c ; -- ' */ a FROM t",
            "SELECT /* a /* b */ c */ a FROM t",
            "SELECT /* a /* b /* c */ d */ e */ a FROM t",
            "SELECT /***/ a FROM t",
            "SELECT /*/ x /*/ x */ */ a FROM t",
            "SELECT /* -- */ a FROM t",
            "SELECT /* \n\n */ a FROM t",
            "/* lead */ SELECT a FROM t /* trail */",
            "SELECT a FROM t /* c */ ;",
            "SELECT /* [ */ a /* ] */ FROM t",
            "SELECT /* ' */ a FROM t",
        )) {
            assertRecognized(expected, sql)
        }
        assertNotRecognized("/* only */", Unrecognized.EMPTY_TEXT)
        assertNotRecognized("SELECT /* a FROM t", Unrecognized.UNTERMINATED_COMMENT)
        assertNotRecognized("SELECT /* a /* b */ FROM t", Unrecognized.UNTERMINATED_COMMENT)
        assertNotRecognized("SELECT /*/ a FROM t", Unrecognized.UNTERMINATED_COMMENT)
        assertNotRecognized("SELECT a FROM t /*", Unrecognized.UNTERMINATED_COMMENT)
        assertNotRecognized("SELECT a FROM t */", Unrecognized.UNSUPPORTED_CHARACTER)
        assertNotRecognized("SELECT a / b FROM t", Unrecognized.UNSUPPORTED_CHARACTER)
    }

    /**
     * Leerraum sind Leerzeichen, Tab, CR und LF; jedes andere Leerzeichen-Zeichen ist nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `kennt vier Zeichen als Leerraum`() {
        val expected = select(t, listOf("a"))
        for (space in listOf(" ", "\t", "\r", "\n", "\r\n", " \t \n  ")) {
            assertRecognized(expected, "SELECT${space}a${space}FROM${space}t")
        }
        for (other in listOf(" ", " ", "\u000B", "\u000C", "\u0085", "​", "　")) {
            assertNotRecognized("SELECT${other}a FROM t")
        }
    }

    /**
     * Zahlen: Ziffern mit optionalem Dezimalpunkt und führendem `-`; Exponent, Hexzahl, `+`, Punkt ohne Ziffern
     * und angehängte Buchstaben sind nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `liest Zahlen streng`() {
        for (number in listOf("0", "7", "007", "-5", "-0", "3.50", "-0.5", "123456789012345678901234567890", "1.000")) {
            assertEquals(num(number), value("INSERT INTO t (a) VALUES ($number)"), number)
        }
        for (bad in listOf("1e3", "1E3", "0x10", "5abc", "5_", "1.2.3", "1.", "1.e3", "5\$", "5#")) {
            assertNotRecognized("INSERT INTO t (a) VALUES ($bad)", Unrecognized.MALFORMED_NUMBER)
        }
        for (bad in listOf("+5", "- 5", ".5", "--5", "1-2", "1 2", "1,", "-", "- -5")) {
            assertNotRecognized("INSERT INTO t (a) VALUES ($bad)")
        }
        assertNotRecognized("DELETE FROM t WHERE a IN (1 -2)", Unrecognized.UNEXPECTED_TOKEN)
    }

    /**
     * Parameter: `@` und mindestens ein Wortzeichen; ohne Name oder mit zweitem `@` nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `liest Parameter`() {
        for (name in listOf("p", "P_1", "1", "x#", "Ä")) {
            assertEquals(param(name), value("INSERT INTO t (a) VALUES (@$name)"), name)
        }
        for (bad in listOf("@", "@@x", "@ p", "@-", "@'x'", "@[x]")) {
            assertNotRecognized("INSERT INTO t (a) VALUES ($bad)")
        }
        assertNotRecognized("INSERT INTO t (a) VALUES (@)", Unrecognized.UNSUPPORTED_CHARACTER)
    }

    /**
     * Zeichen außerhalb des Scanners sind nicht erkannt.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt fremde Zeichen ab`() {
        for (c in "<>!+/%&|^~:{}\\?`§€") {
            assertNotRecognized("SELECT a FROM t WHERE a = $c", Unrecognized.UNSUPPORTED_CHARACTER)
        }
    }

    /**
     * Schema und Name in jeder Quotierung, auch gemischt.
     * @spec SPEC-036
     */
    @Test
    fun `liest Schema und Tabelle in jeder Quotierung`() {
        val expected = select(tableRef("t", "dbo"), listOf("a"))
        val spellings = listOf(
            "dbo.t",
            "[dbo].[t]",
            "\"dbo\".\"t\"",
            "dbo.[t]",
            "[dbo].t",
            "\"dbo\".[t]",
            "dbo . t",
            "dbo/**/./**/t",
        )
        for (name in spellings) {
            assertRecognized(expected, "SELECT a FROM $name")
        }
        assertRecognized(select(tableRef("a.b"), listOf("a")), "SELECT a FROM [a.b]")
        assertRecognized(select(tableRef("a.b", "s.c"), listOf("a")), "SELECT a FROM [s.c].[a.b]")
    }

    /**
     * Tiefe Schachtelung von Kommentaren und lange Werte-Listen laufen ohne Rekursion und ohne Stapelüberlauf:
     * 200 000 geschachtelte Kommentare, 50 000 Werte in einer `IN`-Liste, ein Literal von einer Million Zeichen.
     * Eine Zeitgrenze wird nicht behauptet.
     * @spec SPEC-036
     */
    @Test
    fun `verträgt tiefe Schachtelung und lange Eingaben`() {
        val depth = 200_000
        val nested = "/*".repeat(depth) + "*/".repeat(depth)
        assertRecognized(select(t, listOf("a")), "SELECT $nested a FROM t")
        val open = "SELECT " + "/*".repeat(depth) + "*/".repeat(depth - 1) + " a FROM t"
        assertNotRecognized(open, Unrecognized.UNTERMINATED_COMMENT)
        val count = 50_000
        val values = (1..count).joinToString(", ") { "$it" }
        val result = recognize("SELECT a FROM t WHERE a IN ($values)")
        val statement = (result as Recognition.Recognized).statement as Statement.Select
        assertEquals(count, (statement.where.single() as Term.In).values.size)
        val payload = "x".repeat(1_000_000)
        assertEquals(txt(payload), text("'$payload'"))
    }
}
