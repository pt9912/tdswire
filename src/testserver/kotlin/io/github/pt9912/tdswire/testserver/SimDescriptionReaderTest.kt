package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests des Lesers der Tabellenbeschreibung (`SPEC-035`). Die Bäume sind von Hand gebaut; die Erwartungen
 * (Typabbildung, Ignorierliste, Pflichtfelder) stammen aus dem Text von `SPEC-035` und sind hier einzeln
 * ausgeschrieben, nicht aus dem Leser abgeleitet. Alles ist Selbstkonsistenz und Festlegung: nichts davon
 * ist an einem Server oder an einer Datei von d-migrate gemessen.
 */
class SimDescriptionReaderTest {
    private val b = DocBuilder()

    private fun load(root: SimNode): List<SimTable> = SimDescriptionReader.read(root)

    private fun failure(root: SimNode): SimLoadException = assertFailsWith<SimLoadException> { load(root) }

    /** Ein Dokument mit der einen Tabelle `t`. */
    private fun tableDoc(columns: SimNode, key: SimNode): SimMap = b.doc(b.m("t" to b.table(columns, key)))

    /** Die Stellen aller Schlüssel namens [key] im Baum [node]; ein Test vergleicht damit die Stelle eines Fehlers. */
    private fun keyPositions(node: SimNode, key: String): List<SimPosition> = when (node) {
        is SimScalar -> emptyList()

        is SimList -> node.items.flatMap { keyPositions(it, key) }

        is SimMap -> node.entries.flatMap {
            (if (it.key == key) listOf(it.keyPosition) else emptyList()) + keyPositions(it.value, key)
        }
    }

    private fun SimTable.shape() = listOf(schema, name, columns, primaryKey, onInsert, onUpdate, onDelete)

    private fun one(cols: Map<String, String>, key: List<String>): SimTable = load(b.simple(cols, key)).single()

    /**
     * Eine Datei mit drei Tabellen und allen zehn abbildbaren Typen wird gelesen: Namen, Typen, Nullbarkeit.
     * @spec SPEC-035
     */
    @Test
    fun `liest eine Datei mit allen abbildbaren Typen`() {
        val person = b.table(
            b.m(
                "id" to b.col("identifier"),
                "name" to b.col("text", "required" to b.s("true"), "max_length" to b.s("100")),
                "age" to b.col("integer"),
                "big" to b.col("biginteger"),
                "flag" to b.col("boolean"),
                "price" to b.col("decimal", "precision" to b.s("10"), "scale" to b.s("2")),
                "born" to b.col("date"),
                "at" to b.col("time"),
                "created" to b.col("datetime"),
                "mail" to b.col("email"),
            ),
            b.keys("id"),
        )
        val other = b.table(b.m("k" to b.col("integer", "required" to b.s("true"))), b.keys("k"))
        val third = b.table(b.m("a" to b.col("text"), "b" to b.col("text")), b.keys("a", "b"))
        val tables = load(b.doc(b.m("person" to person, "other" to other, "third" to third)))
        assertEquals(listOf("person", "other", "third"), tables.map { it.name })
        assertTrue(tables.all { it.schema == "dbo" })
        val columns = tables[0].columns
        assertEquals(
            listOf(
                SimColumn("id", SqlType.Int32, nullable = false),
                SimColumn("name", SqlType.UnicodeText, nullable = false),
                SimColumn("age", SqlType.Int32),
                SimColumn("big", SqlType.Int64),
                SimColumn("flag", SqlType.Bool),
                SimColumn("price", SqlType.Decimal(10, 2)),
                SimColumn("born", SqlType.Date),
                SimColumn("at", SqlType.Time),
                SimColumn("created", SqlType.DateTime),
                SimColumn("mail", SqlType.UnicodeText),
            ),
            columns,
        )
        assertEquals(listOf("id"), tables[0].primaryKey)
        assertEquals(listOf("a", "b"), tables[2].primaryKey)
        assertEquals(listOf(false, false), tables[2].columns.map { it.nullable })
    }

    /**
     * Die Typabbildung, einzeln je neutralem Typ.
     * @spec SPEC-035
     */
    @Test
    fun `bildet jeden der zehn Typen auf den Katalogtyp ab`() {
        val expected = mapOf(
            "identifier" to SqlType.Int32,
            "integer" to SqlType.Int32,
            "biginteger" to SqlType.Int64,
            "boolean" to SqlType.Bool,
            "text" to SqlType.UnicodeText,
            "email" to SqlType.UnicodeText,
            "date" to SqlType.Date,
            "time" to SqlType.Time,
            "datetime" to SqlType.DateTime,
        )
        for ((neutral, sql) in expected) {
            assertEquals(sql, one(mapOf("c" to neutral, "k" to "integer"), listOf("k")).column("c")?.type, neutral)
        }
        assertEquals(9, expected.size)
        assertEquals(
            SqlType.Decimal(38, 0),
            load(
                b.doc(
                    b.m(
                        "t" to b.table(
                            b.m(
                                "k" to b.col("integer"),
                                "d" to b.col("decimal", "precision" to b.s("38"), "scale" to b.s("0")),
                            ),
                            b.keys("k"),
                        ),
                    ),
                ),
            ).single().column("d")?.type,
        )
    }

    /**
     * Jeder Typ ohne Entsprechung weist die Datei ab, mit Pfad, Stelle des Typs und Typname.
     * @spec SPEC-035
     */
    @Test
    fun `weist jeden Typ ohne Entsprechung ab`() {
        val unmapped = listOf(
            "smallint", "char", "float", "uuid", "binary", "xml", "json", "array", "enum", "geometry", "fulltext",
        )
        for (name in unmapped) {
            val type = b.s(name)
            val doc = b.doc(b.m("t" to b.table(b.m("c" to b.m("type" to type)), b.keys("c"))))
            val error = failure(doc)
            assertEquals("tables.t.columns.c.type", error.path, name)
            assertEquals(type.position, error.position, name)
            assertTrue(error.message.orEmpty().contains(name), name)
        }
        assertEquals(11, unmapped.size)
    }

    /**
     * `datetime` mit `timezone: true` hat keine Entsprechung, mit `timezone: false` schon.
     * @spec SPEC-035
     */
    @Test
    fun `weist datetime mit Zeitzone ab`() {
        val type = b.s("datetime")
        val column = b.m("type" to type, "timezone" to b.s("true"))
        val error = failure(b.doc(b.m("t" to b.table(b.m("c" to column), b.keys("c")))))
        assertEquals("tables.t.columns.c.type", error.path)
        assertEquals(type.position, error.position)
        val good = b.doc(b.m("t" to b.table(b.m("c" to b.col("datetime", "timezone" to b.s("false"))), b.keys("c"))))
        assertEquals(SqlType.DateTime, load(good).single().columns.single().type)
    }

    /**
     * Ein Typname außerhalb der 21 bekannten wird abgewiesen, ohne ihn in die Meldung zu schreiben.
     * @spec SPEC-035
     */
    @Test
    fun `weist einen unbekannten Typ ab und nennt ihn nicht`() {
        for (name in listOf("weirdtype", "Integer", "INTEGER", "int", "money")) {
            val error = failure(b.simple(mapOf("c" to name), listOf("c")))
            assertEquals("Typ unbekannt", error.reason, name)
            assertFalse(error.message.orEmpty().contains(name), name)
        }
    }

    /**
     * Die Ignorierliste: jedes im Schema bekannte Feld ohne Wirkung wird übergangen, auf jeder Ebene einzeln.
     * Die Listen sind aus `SPEC-035` abgeschrieben.
     * @spec SPEC-035
     */
    @Test
    fun `liest die Ignorierliste ohne Wirkung`() {
        val document = listOf(
            "description", "encoding", "locale", "custom_types", "procedures", "functions", "views", "triggers",
            "sequences",
        )
        val table = listOf("description", "indices", "constraints", "partitioning", "metadata")
        val column = listOf(
            "unique", "default", "auto_increment", "generation", "references",
            "length", "float_precision", "values", "ref_type", "element_type", "geometry_type", "srid",
        )
        assertEquals(26, document.size + table.size + column.size)
        val base = one(mapOf("c" to "integer"), listOf("c"))
        for (key in document) {
            val tree = b.doc(b.m("t" to b.table(b.m("c" to b.col("integer")), b.keys("c"))), key to b.s("x"))
            assertEquals(listOf(base.shape()), load(tree).map { it.shape() }, key)
        }
        for (key in table) {
            val tree = b.doc(b.m("t" to b.table(b.m("c" to b.col("integer")), b.keys("c"), key to b.s("x"))))
            assertEquals(base.columns, load(tree).single().columns, key)
        }
        for (key in column) {
            val tree = b.doc(b.m("t" to b.table(b.m("c" to b.col("integer", key to b.s("x"))), b.keys("c"))))
            assertEquals(base.columns, load(tree).single().columns, key)
        }
    }

    /**
     * Jedes Feld, das weder gelesen noch ignoriert wird, ist ein Ladefehler; ein Feld der falschen Ebene ebenso.
     * @spec SPEC-035
     */
    @Test
    fun `weist unbekannte Felder und Felder der falschen Ebene ab`() {
        val cols = { b.m("c" to b.col("integer")) }
        val cases = listOf(
            "bogus" to b.doc(b.m("t" to b.table(cols(), b.keys("c"))), "bogus" to b.s("x")),
            "indices" to b.doc(b.m("t" to b.table(cols(), b.keys("c"))), "indices" to b.s("x")),
            "bogus" to b.doc(b.m("t" to b.table(cols(), b.keys("c"), "bogus" to b.s("x")))),
            "unique" to b.doc(b.m("t" to b.table(cols(), b.keys("c"), "unique" to b.s("x")))),
            "bogus" to b.doc(b.m("t" to b.table(b.m("c" to b.col("integer", "bogus" to b.s("x"))), b.keys("c")))),
            "metadata" to b.doc(b.m("t" to b.table(b.m("c" to b.col("integer", "metadata" to b.s("x"))), b.keys("c")))),
            "Required" to tableDoc(b.m("c" to b.col("integer", "Required" to b.s("true"))), b.keys("c")),
        )
        for ((key, tree) in cases) {
            val error = failure(tree)
            assertEquals("Feld unbekannt", error.reason, key)
            assertTrue(error.path.endsWith(key), error.path)
            assertEquals(listOf(error.position), keyPositions(tree, key), "Stelle des Schlüssels $key")
        }
        assertEquals(7, cases.size)
    }

    /**
     * Ein doppelter Schlüssel in einer Abbildung ist ein Ladefehler an der Stelle des zweiten, auch bei einem
     * ignorierten Feld.
     * @spec SPEC-035
     */
    @Test
    fun `weist ein doppeltes Feld ab`() {
        val second = b.s("integer")
        val dupType = b.m("type" to b.s("integer"), "type" to second)
        val tree = b.doc(b.m("t" to b.table(b.m("c" to dupType), b.keys("c"))))
        val error = failure(tree)
        assertEquals("Feld doppelt", error.reason)
        assertEquals("tables.t.columns.c.type", error.path)
        assertEquals(dupType.entries[1].keyPosition, error.position)
        val dupIgnored = b.m("type" to b.s("integer"), "unique" to b.s("true"), "unique" to b.s("false"))
        val tree2 = b.doc(b.m("t" to b.table(b.m("c" to dupIgnored), b.keys("c"))))
        assertEquals(dupIgnored.entries[2].keyPosition, failure(tree2).position)
        val dupDoc = b.doc(b.m(), "name" to b.s("again"))
        assertEquals("Feld doppelt", failure(dupDoc).reason)
    }

    /**
     * Pflichtfelder auf jeder Ebene: fehlt eines, nennt der Fehler das Feld und die Stelle der Abbildung.
     * @spec SPEC-035
     */
    @Test
    fun `verlangt die Pflichtfelder`() {
        fun without(drop: String): SimMap {
            val head = listOf("schema_format" to b.s("1.0"), "name" to b.s("n"), "version" to b.s("1"))
            val tables = "tables" to b.m("t" to b.table(b.m("c" to b.col("integer")), b.keys("c")))
            return b.m(*(head + tables).filter { it.first != drop }.toTypedArray())
        }
        for (field in listOf("schema_format", "name", "version", "tables")) {
            val tree = without(field)
            val error = failure(tree)
            assertEquals("Pflichtfeld $field fehlt", error.reason, field)
            assertEquals(tree.position, error.position, field)
        }
        val noColumns = b.table(null, b.keys("c"))
        assertEquals("Pflichtfeld columns fehlt", failure(b.doc(b.m("t" to noColumns))).reason)
        val noKey = b.table(b.m("c" to b.col("integer")), null)
        assertEquals("Pflichtfeld primary_key fehlt", failure(b.doc(b.m("t" to noKey))).reason)
        val noType = b.m()
        val error = failure(b.doc(b.m("t" to b.table(b.m("c" to noType), b.keys("c")))))
        assertEquals("Pflichtfeld type fehlt", error.reason)
        assertEquals(noType.position, error.position)
        assertEquals("Pflichtfeld tables fehlt", failure(b.doc(null)).reason)
    }

    /**
     * `schema_format` muss `1.0` sein; die Schreibweise mit Anführungszeichen gilt gleich.
     * @spec SPEC-035
     */
    @Test
    fun `verlangt schema_format 1_0`() {
        for (bad in listOf("2.0", "1.1", "1", "1.00", "", "v1.0")) {
            val format = b.s(bad)
            val tree = b.m(
                "schema_format" to format,
                "name" to b.s("n"),
                "version" to b.s("1"),
                "tables" to b.m(),
            )
            val error = failure(tree)
            assertEquals("schema_format", error.path, bad)
            assertEquals(format.position, error.position, bad)
        }
        val quoted = b.m(
            "schema_format" to b.s("1.0", plain = false),
            "name" to b.s("n"),
            "version" to b.s("1"),
            "tables" to b.m(),
        )
        assertEquals(emptyList(), load(quoted))
    }

    /**
     * `tables` muss eine Abbildung sein: eine Liste, ein Skalar und eine Wurzel, die keine Abbildung ist, werden
     * abgewiesen (d-migrate liest eine Liste ohne Befund).
     * @spec SPEC-035
     */
    @Test
    fun `weist eine Liste statt einer Abbildung ab`() {
        val list = b.l(b.s("a"))
        assertEquals("Abbildung erwartet", failure(b.doc(list)).reason)
        assertEquals(list.position, failure(b.doc(list)).position)
        assertEquals("Abbildung erwartet", failure(b.doc(b.s("x"))).reason)
        assertEquals("Abbildung erwartet", failure(b.l()).reason)
        assertEquals("Abbildung erwartet", failure(b.s("x")).reason)
        assertEquals("Abbildung erwartet", failure(b.doc(b.m("t" to b.l()))).reason)
        assertEquals("Abbildung erwartet", failure(b.doc(b.m("t" to b.table(b.l(), b.keys("c"))))).reason)
        assertEquals("Liste erwartet", failure(tableDoc(b.m("c" to b.col("integer")), b.s("c"))).reason)
    }

    /**
     * Skalare nur kanonisch: Wahrheitswerte nur `true` und `false` schlicht, Ganzzahlen nur `0|[1-9][0-9]*`.
     * @spec SPEC-035
     */
    @Test
    fun `weist nicht kanonische Skalare ab`() {
        val badBooleans = listOf("yes", "no", "True", "TRUE", "1", "0", "on", "", "truee")
        for (text in badBooleans) {
            val node = b.s(text)
            val tree = b.doc(b.m("t" to b.table(b.m("c" to b.col("integer", "required" to node)), b.keys("c"))))
            val error = failure(tree)
            assertEquals("tables.t.columns.c.required", error.path, text)
            assertEquals(node.position, error.position, text)
        }
        val quoted = b.s("true", plain = false)
        val qTree = b.doc(b.m("t" to b.table(b.m("c" to b.col("integer", "required" to quoted)), b.keys("c"))))
        assertEquals(quoted.position, failure(qTree).position)
        val badInts = listOf("010", "0x10", "+5", "-1", "1e3", "1.0", "", " 1", "1 ", "٣", "00")
        for (text in badInts) {
            val tree = b.doc(b.m("t" to b.table(b.m("c" to b.col("text", "max_length" to b.s(text))), b.keys("c"))))
            assertEquals("tables.t.columns.c.max_length", failure(tree).path, "[$text]")
        }
        val quotedInt = b.s("5", plain = false)
        val iTree = b.doc(b.m("t" to b.table(b.m("c" to b.col("text", "max_length" to quotedInt)), b.keys("c"))))
        assertEquals(quotedInt.position, failure(iTree).position)
        assertEquals(9 + 11, badBooleans.size + badInts.size)
    }

    /**
     * Gute Skalare werden gelesen: `true` und `false`, `max_length` ab 1, `scale` 0.
     * @spec SPEC-035
     */
    @Test
    fun `liest kanonische Skalare`() {
        val tree = b.doc(
            b.m(
                "t" to b.table(
                    b.m(
                        "a" to b.col("text", "max_length" to b.s("1"), "required" to b.s("false")),
                        "b" to b.col("text", "max_length" to b.s("2147483647"), "required" to b.s("true")),
                        "d" to b.col("decimal", "precision" to b.s("5"), "scale" to b.s("0")),
                    ),
                    b.keys("b"),
                ),
            ),
        )
        val columns = load(tree).single().columns
        assertEquals(listOf(true, false, true), columns.map { it.nullable })
        assertEquals(SqlType.Decimal(5, 0), columns[2].type)
    }

    /**
     * `max_length` wird geprüft (positiv, passt in Int), aber nicht durchgesetzt und nicht gemeldet: Typ und
     * Nullbarkeit hängen nicht an ihm.
     * @spec SPEC-035
     */
    @Test
    fun `liest max_length ohne Wirkung`() {
        val lengths = listOf("1", "5", "4000", "100000")
        for (length in lengths) {
            val columns = b.m("k" to b.col("integer"), "c" to b.col("text", "max_length" to b.s(length)))
            val tree = tableDoc(columns, b.keys("k"))
            assertEquals(SimColumn("c", SqlType.UnicodeText, nullable = true), load(tree).single().column("c"), length)
        }
        for (bad in listOf("0", "2147483648", "99999999999")) {
            val node = b.s(bad)
            val tree = b.doc(b.m("t" to b.table(b.m("c" to b.col("text", "max_length" to node)), b.keys("c"))))
            assertEquals(node.position, failure(tree).position, bad)
        }
    }

    /**
     * `decimal` braucht `precision` und `scale` (Festlegung, ADR-0012 Offen 7), `precision` höchstens 38,
     * `scale` höchstens `precision`.
     * @spec SPEC-035
     */
    @Test
    fun `prüft precision und scale bei decimal`() {
        fun decimal(vararg extra: Pair<String, SimNode>): SimMap =
            b.doc(b.m("t" to b.table(b.m("c" to b.col("decimal", *extra)), b.keys("c"))))
        fun missing(vararg extra: Pair<String, SimNode>): SimLoadException {
            val column = b.col("decimal", *extra)
            val error = failure(b.doc(b.m("t" to b.table(b.m("c" to column), b.keys("c")))))
            assertEquals("tables.t.columns.c", error.path)
            assertEquals(column.position, error.position, "Stelle der Spalte")
            return error
        }
        assertEquals("precision fehlt bei decimal", missing("scale" to b.s("2")).reason)
        assertEquals("scale fehlt bei decimal", missing("precision" to b.s("5")).reason)
        assertEquals("precision fehlt bei decimal", missing().reason)
        val tooBig = b.s("39")
        val big = failure(decimal("precision" to tooBig, "scale" to b.s("2")))
        assertEquals("tables.t.columns.c.precision", big.path)
        assertEquals(tooBig.position, big.position)
        val scale = b.s("6")
        val over = failure(decimal("precision" to b.s("5"), "scale" to scale))
        assertEquals("tables.t.columns.c.scale", over.path)
        assertEquals(scale.position, over.position)
        val zero = failure(decimal("precision" to b.s("0"), "scale" to b.s("0")))
        assertEquals("tables.t.columns.c.precision", zero.path)
        for ((p, s) in listOf("38" to "38", "1" to "0", "1" to "1", "38" to "0", "18" to "9")) {
            val type = load(decimal("precision" to b.s(p), "scale" to b.s(s))).single().columns.single().type
            assertEquals(SqlType.Decimal(p.toInt(), s.toInt()), type, "$p,$s")
        }
    }

    /**
     * `ordinal` bei allen Spalten ordnet danach, bei keiner gilt die Reihenfolge des Dokuments, bei einem Teil
     * (und bei einer Wiederholung) ist die Datei abzuweisen.
     * @spec SPEC-035
     */
    @Test
    fun `ordnet Spalten nach ordinal`() {
        fun cols(ordinals: List<Int?>): SimMap = b.m(
            *ordinals.mapIndexed { i, o ->
                "c$i" to (if (o == null) b.col("integer") else b.col("integer", "ordinal" to b.s("$o")))
            }.toTypedArray(),
        )

        fun names(ordinals: List<Int?>) =
            load(b.doc(b.m("t" to b.table(cols(ordinals), b.keys("c0"))))).single().columns.map { it.name }
        assertEquals(listOf("c0", "c1", "c2"), names(listOf(null, null, null)))
        assertEquals(listOf("c2", "c0", "c1"), names(listOf(2, 3, 1)))
        assertEquals(listOf("c1", "c2", "c0"), names(listOf(9, 1, 5)))
        val partial = cols(listOf(1, null, 3))
        val error = failure(b.doc(b.m("t" to b.table(partial, b.keys("c0")))))
        assertEquals("ordinal bei allen Spalten oder bei keiner", error.reason)
        assertEquals(partial.position, error.position)
        val dup = cols(listOf(1, 1))
        val dupError = failure(b.doc(b.m("t" to b.table(dup, b.keys("c0")))))
        assertEquals("ordinal doppelt", dupError.reason)
        assertEquals("tables.t.columns.c1.ordinal", dupError.path)
        val secondOrdinal = (dup.entries[1].value as SimMap).entries.single { it.key == "ordinal" }.value
        assertEquals(secondOrdinal.position, dupError.position)
        val zeroOrdinal = failure(tableDoc(b.m("c0" to b.col("integer", "ordinal" to b.s("0"))), b.keys("c0")))
        assertEquals("tables.t.columns.c0.ordinal", zeroOrdinal.path)
    }

    /**
     * Nullbar ist eine Spalte, die weder `required: true` trägt noch im Schlüssel steht noch `identifier` ist.
     * @spec SPEC-035
     */
    @Test
    fun `leitet die Nullbarkeit ab`() {
        val tree = b.doc(
            b.m(
                "t" to b.table(
                    b.m(
                        "plain" to b.col("integer"),
                        "req" to b.col("integer", "required" to b.s("true")),
                        "notreq" to b.col("integer", "required" to b.s("false")),
                        "ident" to b.col("identifier"),
                        "pk" to b.col("integer"),
                    ),
                    b.keys("pk"),
                ),
            ),
        )
        val byName = load(tree).single().columns.associate { it.name to it.nullable }
        assertEquals(
            mapOf("plain" to true, "req" to false, "notreq" to true, "ident" to false, "pk" to false),
            byName,
        )
    }

    /**
     * Der Schlüssel: Einträge lösen ohne Beachtung der Schreibweise auf den Namen der Spalte auf; eine
     * unbekannte Spalte, eine Wiederholung, eine leere Liste und ein Nicht-Skalar sind Ladefehler an der Stelle.
     * @spec SPEC-035
     */
    @Test
    fun `prüft den Schlüssel`() {
        val ok = load(b.simple(mapOf("Id" to "integer", "Name" to "text"), listOf("ID", "name"))).single()
        assertEquals(listOf("Id", "Name"), ok.primaryKey)
        val unknown = b.s("nope")
        val unknownError = failure(b.doc(b.m("t" to b.table(b.m("c" to b.col("integer")), b.l(unknown)))))
        assertEquals("Schlüsselspalte nicht vorhanden", unknownError.reason)
        assertEquals("tables.t.primary_key[0]", unknownError.path)
        assertEquals(unknown.position, unknownError.position)
        val twice = b.s("C")
        val twiceError = failure(b.doc(b.m("t" to b.table(b.m("c" to b.col("integer")), b.l(b.s("c"), twice)))))
        assertEquals("Schlüsselspalte doppelt", twiceError.reason)
        assertEquals(twice.position, twiceError.position)
        val empty = b.l()
        val emptyError = failure(b.doc(b.m("t" to b.table(b.m("c" to b.col("integer")), empty))))
        assertEquals(empty.position, emptyError.position)
        val nested = b.l()
        assertEquals("Skalar erwartet", failure(tableDoc(b.m("c" to b.col("integer")), b.l(nested))).reason)
    }

    /**
     * Doppelte Namen ohne Beachtung der Schreibweise: Tabellen (auch bei gleicher Schreibweise), Spalten;
     * leere Namen. Der Fehler zeigt auf den zweiten Eintrag.
     * @spec SPEC-035
     */
    @Test
    fun `weist doppelte und leere Namen ab`() {
        fun tbl() = b.table(b.m("c" to b.col("integer")), b.keys("c"))
        val tables = b.m("person" to tbl(), "Person" to tbl())
        val tableError = failure(b.doc(tables))
        assertEquals("tables.Person", tableError.path)
        assertEquals(tables.entries[1].keyPosition, tableError.position)
        assertEquals("tables.PERSON", failure(b.doc(b.m("person" to tbl(), "other" to tbl(), "PERSON" to tbl()))).path)
        val sameCase = failure(b.doc(b.m("t" to tbl(), "t" to tbl())))
        assertEquals("Name doppelt (ohne Beachtung der Schreibweise)", sameCase.reason)
        val columns = b.m("id" to b.col("integer"), "ID" to b.col("integer"))
        val columnError = failure(b.doc(b.m("t" to b.table(columns, b.keys("id")))))
        assertEquals("tables.t.columns.ID", columnError.path)
        assertEquals(columns.entries[1].keyPosition, columnError.position)
        val dotted = failure(tableDoc(b.m("i" to b.col("integer"), "İ" to b.col("integer")), b.keys("i")))
        assertEquals("tables.t.columns.İ", dotted.path)
        val emptyTable = b.m("" to tbl())
        val emptyTableError = failure(b.doc(emptyTable))
        assertEquals("Name leer", emptyTableError.reason)
        assertEquals("tables.", emptyTableError.path)
        assertEquals(emptyTable.entries.single().keyPosition, emptyTableError.position)
        val emptyColumn = b.m("" to b.col("integer"))
        val emptyColumnError = failure(b.doc(b.m("t" to b.table(emptyColumn, b.keys("x")))))
        assertEquals("Name leer", emptyColumnError.reason)
        assertEquals("tables.t.columns.", emptyColumnError.path)
        assertEquals(emptyColumn.entries.single().keyPosition, emptyColumnError.position)
        val sharp = load(b.doc(b.m("a" to tbl(), "ß" to tbl(), "SS" to tbl())))
        assertEquals(3, sharp.size)
    }

    /**
     * Eine Tabelle ohne Spalten ist ein Ladefehler an der Stelle der leeren Abbildung.
     * @spec SPEC-035
     */
    @Test
    fun `verlangt mindestens eine Spalte`() {
        val empty = b.m()
        val error = failure(b.doc(b.m("t" to b.table(empty, b.keys("c")))))
        assertEquals("tables.t.columns", error.path)
        assertEquals(empty.position, error.position)
    }

    /**
     * Die Meldung eines Ladefehlers enthält nie den Inhalt: Werte stehen weder im Grund noch im Pfad, die
     * Stelle steht darin.
     * @spec SPEC-035
     */
    @Test
    fun `nennt in der Meldung nie den Inhalt`() {
        val secret = "GEHEIMWERT"
        val cases = listOf(
            b.doc(b.m(), "name" to b.m()),
            b.m("schema_format" to b.s(secret), "name" to b.s("n"), "version" to b.s("1"), "tables" to b.m()),
            b.simple(mapOf("c" to secret), listOf("c")),
            b.doc(b.m("t" to b.table(b.m("c" to b.col("integer", "required" to b.s(secret))), b.keys("c")))),
            b.doc(b.m("t" to b.table(b.m("c" to b.col("text", "max_length" to b.s(secret))), b.keys("c")))),
            b.doc(b.m("t" to b.table(b.m("c" to b.col("decimal", "precision" to b.s(secret))), b.keys("c")))),
            b.doc(b.m("t" to b.table(b.m("c" to b.col("integer")), b.keys(secret)))),
            b.doc(b.m("t" to b.table(b.m("c" to b.col("integer", "ordinal" to b.s(secret))), b.keys("c")))),
            b.doc(b.m("t" to b.table(b.m("c" to b.col("integer")), b.keys("c")), "version" to b.s(secret))),
            b.doc(b.l(b.s(secret))),
        )
        for ((index, tree) in cases.withIndex()) {
            val error = failure(tree)
            assertFalse(error.message.orEmpty().contains(secret), "Fall $index: ${error.message}")
            val where = "Zeile ${error.position.line}, Spalte ${error.position.column}"
            assertTrue(error.message.orEmpty().contains(where))
        }
        assertEquals(10, cases.size)
    }

    /**
     * Ein Pfadabschnitt wird auf 64 Zeichen gekürzt und von Steuerzeichen befreit.
     * @spec SPEC-035
     */
    @Test
    fun `kürzt Pfadabschnitte und entfernt Steuerzeichen`() {
        val long = "x".repeat(200)
        val error = failure(b.doc(b.m(long to b.l())))
        assertEquals("tables." + "x".repeat(64) + "…", error.path)
        val exact = "y".repeat(64)
        assertEquals("tables.$exact", failure(b.doc(b.m(exact to b.l()))).path)
        val control = failure(b.doc(b.m("a\nb\u0000c" to b.l())))
        assertEquals("tables.a?b?c", control.path)
        assertFalse(control.message.orEmpty().contains('\n'))
    }

    /**
     * Zeilen- und Absatztrenner (U+2028, U+2029), Formatzeichen der Schreibrichtung (U+202E, U+200B) und ein
     * einzelnes Ersatzzeichen werden im Pfadabschnitt zu `?`; ein Paar außerhalb der Grundebene bleibt ganz, auch
     * an der Kürzungsgrenze.
     * @spec SPEC-035
     */
    @Test
    fun `ersetzt Format- und Trennzeichen im Pfadabschnitt`() {
        val cases = mapOf(
            "a\u2028b" to "a?b",
            "a\u2029b" to "a?b",
            "a\u202Eb" to "a?b",
            "a\u200Bb" to "a?b",
            "a\u0085b" to "a?b",
            "a\uD800b" to "a?b",
        )
        for ((key, expected) in cases) assertEquals("tables.$expected", failure(b.doc(b.m(key to b.l()))).path, key)
        assertEquals(6, cases.size)
        val astral = "\uD840\uDC00"
        assertEquals("tables.$astral", failure(b.doc(b.m(astral to b.l()))).path)
        val atLimit = "x".repeat(63) + astral + "y"
        assertEquals("tables." + "x".repeat(63) + astral + "…", failure(b.doc(b.m(atLimit to b.l()))).path)
    }

    /**
     * Eine Tabelle aus der Datei hat eine leere Zeilenquelle und keine Callbacks; ihr Name ist wörtlich, ein
     * Punkt bleibt Teil des Namens, das Schema ist `dbo`.
     * @spec SPEC-035
     */
    @Test
    fun `lädt Tabellen mit leerer Zeilenquelle ohne Callbacks`() {
        val tree = b.doc(b.m("dbo.person" to b.table(b.m("c" to b.col("integer")), b.keys("c"))))
        val table = load(tree).single()
        assertEquals("dbo.person", table.name)
        assertEquals("dbo", table.schema)
        assertNull(table.onInsert)
        assertNull(table.onUpdate)
        assertNull(table.onDelete)
        val source = runBlocking { table.rows(SimQuery(table.columns, null)) }
        assertNull(runBlocking { source.next() })
        source.close()
    }

    /**
     * `tables` als leere Abbildung beschreibt nichts und lädt (Festlegung nach dem Schema).
     * @spec SPEC-035
     */
    @Test
    fun `lädt eine leere Abbildung unter tables`() {
        assertEquals(emptyList(), load(b.doc(b.m())))
        assertEquals("Pflichtfeld tables fehlt", failure(b.doc(null)).reason)
    }

    /**
     * `name` und `version` sind Pflicht und nicht leer, werden aber nicht ausgewertet.
     * @spec SPEC-035
     */
    @Test
    fun `verlangt name und version ohne sie auszuwerten`() {
        for (field in listOf("name", "version")) {
            val empty = b.s("")
            val tree = b.m(
                "schema_format" to b.s("1.0"),
                "name" to (if (field == "name") empty else b.s("n")),
                "version" to (if (field == "version") empty else b.s("1")),
                "tables" to b.m(),
            )
            val error = failure(tree)
            assertEquals(empty.position, error.position, field)
            assertEquals("Text darf nicht leer sein", error.reason, field)
        }
        val tree = b.m("schema_format" to b.s("1.0"), "name" to b.s("!?"), "version" to b.s("x.y"), "tables" to b.m())
        assertEquals(emptyList(), load(tree))
    }
}
