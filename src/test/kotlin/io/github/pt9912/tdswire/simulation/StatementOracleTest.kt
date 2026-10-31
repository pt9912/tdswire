package io.github.pt9912.tdswire.simulation

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Variation der Eingabe für die Anweisungserkennung (`SPEC-036`): eine Tabelle von Grenzfällen mit Erwartung
 * von Hand und ein Erzeuger, der Anweisungen aus einer Beschreibung rendert. Beide Orakel benutzen den Scanner
 * nicht: die Erwartung steht vor dem Text, nicht danach. Selbstkonsistenz: Autor und Erwartender sind derselbe,
 * nichts ist an einem Server gemessen.
 */
class StatementOracleTest {
    private val t = tableRef("t")

    private sealed interface Expect {
        data class Is(val statement: Statement) : Expect

        data class No(val reason: Unrecognized) : Expect
    }

    private fun yes(statement: Statement) = Expect.Is(statement)

    private fun no(reason: Unrecognized) = Expect.No(reason)

    private val positiveCases: List<Pair<String, Expect>> = listOf(
        "SELECT a FROM t WHERE a = ';'" to yes(select(t, listOf("a"), eq("a", txt(";")))),
        "SELECT a FROM t WHERE a = '--'" to yes(select(t, listOf("a"), eq("a", txt("--")))),
        "SELECT a FROM t WHERE a = '/* x */'" to yes(select(t, listOf("a"), eq("a", txt("/* x */")))),
        "SELECT a FROM t WHERE a = 'a''b'" to yes(select(t, listOf("a"), eq("a", txt("a'b")))),
        "SELECT a FROM t WHERE a = N'a;b'" to yes(select(t, listOf("a"), eq("a", ntxt("a;b")))),
        "SELECT a FROM t WHERE a = N'--'" to yes(select(t, listOf("a"), eq("a", ntxt("--")))),
        "SELECT a FROM t WHERE a IN ('a', ';', '--', N'x')" to
            yes(select(t, listOf("a"), inList("a", txt("a"), txt(";"), txt("--"), ntxt("x")))),
        "SELECT a FROM t WHERE a = 'x' -- AND b = 1" to yes(select(t, listOf("a"), eq("a", txt("x")))),
        "SELECT a FROM t WHERE a = 'x' /* AND b = 1 */" to yes(select(t, listOf("a"), eq("a", txt("x")))),
        "SELECT a FROM t WHERE a = 'x' /* ' */ AND b = 'y'" to
            yes(select(t, listOf("a"), eq("a", txt("x")), eq("b", txt("y")))),
        "SELECT [a;b] FROM t" to yes(select(t, listOf("a;b"))),
        "SELECT [a--b] FROM t" to yes(select(t, listOf("a--b"))),
        "SELECT [a]]b] FROM t" to yes(select(t, listOf("a]b"))),
        "SELECT \"a;b\", \"c\"\"d\" FROM t" to yes(select(t, listOf("a;b", "c\"d"))),
        "SELECT a FROM [t;x]" to yes(select(tableRef("t;x"), listOf("a"))),
        "SELECT a FROM \"s--x\".\"t\"" to yes(select(tableRef("t", "s--x"), listOf("a"))),
        "SELECT a /* , b */ FROM t" to yes(select(t, listOf("a"))),
        "SELECT a -- , b\n FROM t" to yes(select(t, listOf("a"))),
        "SELECT a, /* b, */ c FROM t" to yes(select(t, listOf("a", "c"))),
        "SELECT * FROM t WHERE a IS NULL -- AND b IS NULL" to yes(select(t, null, isNull("a"))),
        "SELECT * FROM t WHERE a IS /* NOT */ NULL" to yes(select(t, null, isNull("a"))),
        "SELECT * FROM t WHERE a IS NOT /* NOT */ NULL" to yes(select(t, null, isNotNull("a"))),
        "SELECT * FROM t WHERE a = @p -- ;\n AND b = @q" to
            yes(select(t, null, eq("a", param("p")), eq("b", param("q")))),
        "SELECT * FROM t WHERE a = NULL" to yes(select(t, null, eq("a", SimValue.Null))),
        "SELECT * FROM t WHERE a = 'NULL'" to yes(select(t, null, eq("a", txt("NULL")))),
        "SELECT * FROM t WHERE a = '@p'" to yes(select(t, null, eq("a", txt("@p")))),
        "SELECT * FROM t WHERE a = '' AND b = ''''" to yes(select(t, null, eq("a", txt("")), eq("b", txt("'")))),
        "SELECT * FROM t WHERE a = '''' ; " to yes(select(t, null, eq("a", txt("'")))),
        "SELECT * FROM t WHERE a = 'x;'" to yes(select(t, null, eq("a", txt("x;")))),
        "SELECT * FROM t WHERE a = 'x;';" to yes(select(t, null, eq("a", txt("x;")))),
        "SELECT * FROM t WHERE a = 'x' ; -- end" to yes(select(t, null, eq("a", txt("x")))),
        "INSERT INTO t (a) VALUES (';')" to yes(insert(t, listOf("a"), txt(";"))),
        "INSERT INTO t (a) VALUES ('--')" to yes(insert(t, listOf("a"), txt("--"))),
        "INSERT INTO t (a) VALUES ('/*')" to yes(insert(t, listOf("a"), txt("/*"))),
        "INSERT INTO t (a, b) VALUES (N'x', 'y') /* tail */" to
            yes(insert(t, listOf("a", "b"), ntxt("x"), txt("y"))),
        "INSERT INTO t ([a;b], \"c--d\") VALUES (1, 2)" to
            yes(insert(t, listOf("a;b", "c--d"), num("1"), num("2"))),
        "INSERT INTO t (a) VALUES (-1)" to yes(insert(t, listOf("a"), num("-1"))),
        "INSERT INTO t (a) VALUES (1.5)" to yes(insert(t, listOf("a"), num("1.5"))),
        "INSERT /* c */ INTO t (a) VALUES (1)" to yes(insert(t, listOf("a"), num("1"))),
        "UPDATE t SET a = ';' WHERE b = '--'" to yes(update(t, listOf(set("a", txt(";"))), eq("b", txt("--")))),
        "UPDATE t SET a = 'x''y', b = N'z'" to yes(update(t, listOf(set("a", txt("x'y")), set("b", ntxt("z"))))),
        "UPDATE t SET a = 1 -- , b = 2\n WHERE c = 3" to
            yes(update(t, listOf(set("a", num("1"))), eq("c", num("3")))),
        "UPDATE t SET [a b] = @p" to yes(update(t, listOf(set("a b", param("p"))))),
        "DELETE FROM t WHERE a = ';'" to yes(delete(t, eq("a", txt(";")))),
        "DELETE FROM t WHERE a = '--' AND b IS NULL" to yes(delete(t, eq("a", txt("--")), isNull("b"))),
        "DELETE /* c */ FROM -- d\n t" to yes(delete(t)),
    )

    private val negativeCases: List<Pair<String, Expect>> = listOf(
        "SELECT 'a;b' FROM t" to no(Unrecognized.UNEXPECTED_TOKEN),
        "SELECT a FROM t WHERE a = 'x" to no(Unrecognized.UNTERMINATED_LITERAL),
        "SELECT a FROM t WHERE a = 'x''" to no(Unrecognized.UNTERMINATED_LITERAL),
        "SELECT [a FROM t" to no(Unrecognized.UNTERMINATED_IDENTIFIER),
        "SELECT \"a FROM t" to no(Unrecognized.UNTERMINATED_IDENTIFIER),
        "SELECT a FROM t /* x" to no(Unrecognized.UNTERMINATED_COMMENT),
        "SELECT a FROM t WHERE a = 'x'; SELECT 1" to no(Unrecognized.UNEXPECTED_TOKEN),
        "SELECT a FROM t; /* x */ ;" to no(Unrecognized.UNEXPECTED_TOKEN),
        "SELECT a FROM t WHERE a = '-- x' OR b = 1" to no(Unrecognized.UNEXPECTED_TOKEN),
        "SELECT a FROM t WHERE a = 1; DROP TABLE t" to no(Unrecognized.UNEXPECTED_TOKEN),
        "SELECT a FROM t WHERE a = '1'; DROP TABLE t" to no(Unrecognized.UNEXPECTED_TOKEN),
        "SELECT a FROM t ORDER BY ';'" to no(Unrecognized.UNEXPECTED_TOKEN),
        "SELECT a FROM t WHERE a = N 'x'" to no(Unrecognized.UNEXPECTED_TOKEN),
        "SELECT a FROM t WHERE a = 5x" to no(Unrecognized.MALFORMED_NUMBER),
        "SELECT a FROM t WHERE a = 'x' + 'y'" to no(Unrecognized.UNSUPPORTED_CHARACTER),
        "INSERT INTO t (a, b) VALUES ('x;y')" to no(Unrecognized.COUNT_MISMATCH),
        "INSERT INTO t (a) VALUES ('x', 'y')" to no(Unrecognized.COUNT_MISMATCH),
        "SELECT a FROM t WHERE a = @" to no(Unrecognized.UNSUPPORTED_CHARACTER),
    )

    /**
     * 64 Grenzfälle mit `;`, `--`, Blockkommentar, `[…]`, `"…"` und `N'…'` an jeder Stelle, mit der
     * Erwartung des ganzen Ergebnisses.
     * @spec SPEC-036
     */
    @Test
    fun `ordnet 64 Grenzfälle wie erwartet zu`() {
        val cases = positiveCases + negativeCases
        assertTrue(cases.size >= 60, "Untergrenze der Fälle: ${cases.size}")
        assertEquals(64, cases.size)
        for ((sql, expected) in cases) {
            when (expected) {
                is Expect.Is -> assertRecognized(expected.statement, sql)
                is Expect.No -> assertNotRecognized(sql, expected.reason)
            }
        }
        assertEquals(46, cases.count { it.second is Expect.Is })
    }

    // Erzeuger -------------------------------------------------------------------------------------------

    private val simpleNames = listOf("a", "b2", "Col_1", "x#", "y\$", "_z", "Ärger", "tbl")
    private val nastyNames = listOf("a b", "a;b", "a--b", "a/*b*/", "a]b", "a\"b", "SELECT", "a.b", "[x]", "N'x'", "--")
    private val separators = listOf(
        " ", "\t", "\r\n", "  ", " /* c */ ", " /* a /* nested ; */ -- */ ", " -- x ;'\n", "/**/", "\n", " -- [\r",
    )
    private val textPayloads = listOf(
        "", "x", "it's", "a;b", "--c", "/* d */", "[e]", "\"f\"", "N'g'", "line\nbreak", "; DROP TABLE t", "'", "''",
        "ä€", "*/", "\\", "@p", "NULL", "-- ' /* [",
    )
    private val tailSeparators = listOf("", " ", "\t", "\r\n", " /* c */ ")
    private val numbers = listOf("0", "-7", "3.50", "-0.25", "12345678901234567890")

    private class Gen(seed: Long) {
        val random = Random(seed)
        var nastyNamesUsed = 0
        val payloadsUsed = HashSet<String>()
        val kinds = IntArray(4)

        fun <T> pick(list: List<T>): T = list[random.nextInt(list.size)]
    }

    private fun renderName(gen: Gen, name: String): String {
        val nasty = name in nastyNames
        if (nasty) gen.nastyNamesUsed++
        return when {
            nasty && gen.random.nextBoolean() -> "[" + name.replace("]", "]]") + "]"

            nasty -> "\"" + name.replace("\"", "\"\"") + "\""

            else -> when (gen.random.nextInt(3)) {
                0 -> name
                1 -> "[$name]"
                else -> "\"$name\""
            }
        }
    }

    private fun renderValue(gen: Gen, value: SimValue): String = when (value) {
        is SimValue.Number -> value.text
        is SimValue.Text -> (if (value.national) "N" else "") + "'" + value.value.replace("'", "''") + "'"
        SimValue.Null -> gen.pick(listOf("NULL", "null", "Null"))
        is SimValue.Parameter -> "@" + value.name
    }

    private fun randomValue(gen: Gen): SimValue = when (gen.random.nextInt(5)) {
        0 -> num(gen.pick(numbers))

        1, 2 -> {
            val payload = gen.pick(textPayloads)
            gen.payloadsUsed += payload
            if (gen.random.nextBoolean()) txt(payload) else ntxt(payload)
        }

        3 -> SimValue.Null

        else -> param(gen.pick(listOf("p", "P_2", "x1")))
    }

    private fun randomName(gen: Gen): String =
        if (gen.random.nextInt(3) == 0) gen.pick(nastyNames) else gen.pick(simpleNames)

    /** Ein Wort oder eine Klammer zwischen zwei Token: Wörter brauchen Leerraum, Satzzeichen nicht. */
    private fun glue(gen: Gen, left: String, right: String): String {
        val punct = "(),=;."
        val bothWords = left.last() !in punct && right.first() !in punct
        val sep = gen.pick(separators)
        return if (!bothWords && gen.random.nextInt(3) == 0) "" else sep
    }

    private fun join(gen: Gen, tokens: List<String>): String {
        val out = StringBuilder(tokens.first())
        for (i in 1 until tokens.size) out.append(glue(gen, tokens[i - 1], tokens[i])).append(tokens[i])
        return out.toString()
    }

    private fun keyword(gen: Gen, word: String): String = when (gen.random.nextInt(3)) {
        0 -> word
        1 -> word.lowercase()
        else -> word.lowercase().replaceFirstChar { it.uppercase() }
    }

    private class Built(val sql: String, val core: String, val expected: Statement)

    private fun table(gen: Gen): Pair<TableRef, List<String>> {
        val schema = if (gen.random.nextBoolean()) randomName(gen) else null
        val name = randomName(gen)
        val tokens = if (schema == null) {
            listOf(renderName(gen, name))
        } else {
            listOf(renderName(gen, schema), ".", renderName(gen, name))
        }
        return TableRef(schema, name) to tokens
    }

    private fun where(gen: Gen): Pair<List<Term>, List<String>> {
        val terms = ArrayList<Term>()
        val tokens = ArrayList<String>()
        repeat(gen.random.nextInt(4)) { index ->
            tokens += keyword(gen, if (index == 0) "WHERE" else "AND")
            val column = randomName(gen)
            tokens += renderName(gen, column)
            when (gen.random.nextInt(3)) {
                0 -> {
                    val value = randomValue(gen)
                    terms += eq(column, value)
                    tokens += listOf("=", renderValue(gen, value))
                }

                1 -> {
                    val values = List(1 + gen.random.nextInt(3)) { randomValue(gen) }
                    terms += Term.In(column, values)
                    tokens += listOf(keyword(gen, "IN"), "(")
                    values.forEachIndexed { i, v ->
                        if (i > 0) tokens += ","
                        tokens += renderValue(gen, v)
                    }
                    tokens += ")"
                }

                else -> {
                    val negated = gen.random.nextBoolean()
                    terms += Term.IsNull(column, negated)
                    tokens += listOf(keyword(gen, "IS")) + (if (negated) listOf(keyword(gen, "NOT")) else emptyList()) +
                        keyword(gen, "NULL")
                }
            }
        }
        return terms to tokens
    }

    /** Die Spalten einer Auswahlliste; eine Spalte darf dort mehrfach stehen. */
    private fun columns(gen: Gen): List<String> = List(1 + gen.random.nextInt(3)) { randomName(gen) }

    /** Die Spalten von INSERT und SET: paarweise verschieden ohne Beachtung der Schreibweise. */
    private fun distinctColumns(gen: Gen): List<String> {
        val chosen = ArrayList<String>()
        val wanted = 1 + gen.random.nextInt(3)
        while (chosen.size < wanted) {
            val candidate = randomName(gen)
            if (chosen.none { SimNames.fold(it) == SimNames.fold(candidate) }) chosen += candidate
        }
        return chosen
    }

    private fun commaList(items: List<String>): List<String> =
        items.flatMapIndexed { i, item -> if (i == 0) listOf(item) else listOf(",", item) }

    private fun build(gen: Gen): Built {
        val kind = gen.random.nextInt(4)
        gen.kinds[kind]++
        val (table, tableTokens) = table(gen)
        val (terms, whereTokens) = where(gen)
        val tokens = ArrayList<String>()
        val statement: Statement
        when (kind) {
            0 -> {
                val star = gen.random.nextInt(4) == 0
                val cols = columns(gen)
                tokens += keyword(gen, "SELECT")
                tokens += if (star) listOf("*") else commaList(cols.map { renderName(gen, it) })
                tokens += keyword(gen, "FROM")
                tokens += tableTokens + whereTokens
                statement = Statement.Select(table, if (star) null else cols, terms)
            }

            1 -> {
                val cols = distinctColumns(gen)
                val values = cols.map { randomValue(gen) }
                tokens += listOf(keyword(gen, "INSERT"), keyword(gen, "INTO")) + tableTokens + "("
                tokens += commaList(cols.map { renderName(gen, it) })
                tokens += listOf(")", keyword(gen, "VALUES"), "(")
                tokens += commaList(values.map { renderValue(gen, it) })
                tokens += ")"
                statement = Statement.Insert(table, cols, values)
            }

            2 -> {
                val cols = distinctColumns(gen)
                val values = cols.map { randomValue(gen) }
                tokens += listOf(keyword(gen, "UPDATE")) + tableTokens + keyword(gen, "SET")
                cols.forEachIndexed { i, c ->
                    if (i > 0) tokens += ","
                    tokens += listOf(renderName(gen, c), "=", renderValue(gen, values[i]))
                }
                tokens += whereTokens
                statement = Statement.Update(table, cols.zip(values) { c, v -> Assignment(c, v) }, terms)
            }

            else -> {
                tokens += listOf(keyword(gen, "DELETE"), keyword(gen, "FROM")) + tableTokens + whereTokens
                statement = Statement.Delete(table, terms)
            }
        }
        val core = join(gen, tokens)
        val semicolon = if (gen.random.nextBoolean()) gen.pick(tailSeparators) + ";" else ""
        return Built(core + semicolon, core, statement)
    }

    /**
     * 600 erzeugte Anweisungen aller vier Formen mit Bezeichnern und Literalen voller Sonderzeichen,
     * Kommentaren und Leerraum an jeder Stelle: Das Ergebnis ist die Beschreibung, aus der der Text entstand.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt erzeugte Anweisungen wieder`() {
        val gen = Gen(20_261_031L)
        repeat(600) {
            val built = build(gen)
            assertRecognized(built.expected, built.sql)
        }
        assertTrue(gen.kinds.all { it >= 100 }, "jede Form mindestens 100 Mal: ${gen.kinds.toList()}")
        assertTrue(gen.nastyNamesUsed >= 200, "Bezeichner mit Sonderzeichen: ${gen.nastyNamesUsed}")
        assertEquals(textPayloads.toSet(), gen.payloadsUsed, "jedes Literal kam vor")
    }

    /**
     * Zwei erzeugte Anweisungen hintereinander sind nie eine Anweisung; ein Anhang aus der Liste „Nicht erkannt“
     * an eine erzeugte Anweisung macht sie nicht erkannt, während die Anweisung ohne Anhang erkannt wird.
     * @spec SPEC-036
     */
    @Test
    fun `lehnt erzeugte Anhänge und Verdopplungen ab`() {
        val gen = Gen(31_102_026L)
        val suffixes = "ORDER BY a|OR a = 1|GROUP BY a|JOIN u ON a = b|x|AS x|OFFSET 1 ROWS".split("|").map { " $it" }
        val connectors = listOf(" ", "; ", ";\n", " /* x */ ", " -- y\n")
        var cases = 0
        repeat(300) {
            val first = build(gen)
            val second = build(gen)
            assertNotRecognized(first.sql + connectors[it % connectors.size] + second.sql)
            assertRecognized(first.expected, first.core)
            assertNotRecognized(first.core + suffixes[it % suffixes.size])
            assertNotRecognized(first.core + "/**/" + suffixes[(it + 1) % suffixes.size])
            cases++
        }
        assertEquals(300, cases)
    }
}
