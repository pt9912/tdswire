package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests der Objektprüfung vor dem Lesen (`TDS-FA-11.a` Schritt 3) mit einer zählenden Attrappe für Zeilenquelle
 * und Callbacks. Die Attrappe zählt: ein positiver Kontrollfall ruft sie und erwartet die Zahl. Alles ist
 * Festlegung (nichts am Server gemessen).
 */
class StatementPlannerTest {
    /** Zählt jeden Aufruf der Zeilenquelle und der drei Callbacks einer Tabelle. */
    private class Counting(schema: String = DEFAULT_SCHEMA, name: String = "person") {
        var rows = 0
        var sources = 0
        var inserts = 0
        var updates = 0
        var deletes = 0

        val table = SimTable(
            schema = schema,
            name = name,
            columns = listOf(
                SimColumn("Id", SqlType.Int32, nullable = false),
                SimColumn("Name", SqlType.UnicodeText),
                SimColumn("Age", SqlType.Int32),
            ),
            primaryKey = listOf("Id"),
            rows = {
                rows++
                RowSource {
                    sources++
                    null
                }
            },
            onInsert = { inserts++.toLong() },
            onUpdate = { _, _ -> updates++.toLong() },
            onDelete = { deletes++.toLong() },
        )

        val total: Int get() = rows + sources + inserts + updates + deletes
    }

    private val person = Counting()
    private val planner = StatementPlanner(SimCatalog(listOf(person.table)))

    private fun plan(sql: String): PlanResult = planner.plan(sql)

    private fun planned(sql: String): Plan = assertIs<PlanResult.Planned>(plan(sql), sql).plan

    private fun rejected(sql: String): PlanResult.Rejected = assertIs<PlanResult.Rejected>(plan(sql), sql)

    /**
     * Eine Anweisung, die eine beschriebene Tabelle und Spalten nennt, wird geplant; ohne Schema gilt `dbo`;
     * Namen gelten ohne Beachtung der Schreibweise; das Ergebnis trägt die Tabelle der Beschreibung.
     * @spec SPEC-036
     */
    @Test
    fun `plant Anweisungen gegen die Beschreibung`() {
        for (sql in listOf(
            "SELECT id FROM person",
            "SELECT ID, NAME FROM PERSON WHERE AGE = 1",
            "SELECT * FROM dbo.person",
            "SELECT * FROM [DBO].[Person] WHERE name IS NULL AND age IN (1, 2)",
            "INSERT INTO person (id, name) VALUES (1, 'x')",
            "UPDATE person SET name = 'x' WHERE id = 1",
            "DELETE FROM person WHERE age IS NOT NULL",
            "DELETE FROM person",
        )) {
            assertSame(person.table, planned(sql).table, sql)
        }
    }

    /**
     * Die Auswahlliste des Plans: bei `*` alle Spalten in der Reihenfolge der Beschreibung, sonst die
     * genannten in der Reihenfolge des Textes (mit Wiederholung), mit den Namen der Beschreibung.
     * @spec SPEC-036
     */
    @Test
    fun `liefert die Spalten der Auswahlliste`() {
        val all = planned("SELECT * FROM person").resultColumns
        assertEquals(person.table.columns, all)
        val listed = planned("SELECT age, ID, age FROM person").resultColumns
        assertEquals(listOf("Age", "Id", "Age"), listed.map { it.name })
        assertEquals(listOf(SqlType.Int32, SqlType.Int32, SqlType.Int32), listed.map { it.type })
        assertEquals(emptyList(), planned("DELETE FROM person").resultColumns)
        assertEquals(emptyList(), planned("INSERT INTO person (id) VALUES (1)").resultColumns)
    }

    /**
     * Ohne Schema gilt `dbo`; eine Tabelle in einem anderen Schema ist nur mit diesem Schema erreichbar.
     * @spec SPEC-036
     */
    @Test
    fun `ohne Schema gilt dbo`() {
        val sales = Counting(schema = "sales", name = "orders")
        val dbo = Counting(name = "orders")
        val both = StatementPlanner(SimCatalog(listOf(sales.table, dbo.table)))
        assertSame(dbo.table, assertIs<PlanResult.Planned>(both.plan("SELECT * FROM orders")).plan.table)
        assertSame(dbo.table, assertIs<PlanResult.Planned>(both.plan("SELECT * FROM dbo.orders")).plan.table)
        assertSame(sales.table, assertIs<PlanResult.Planned>(both.plan("SELECT * FROM sales.orders")).plan.table)
        val onlySales = StatementPlanner(SimCatalog(listOf(sales.table)))
        val missing = assertIs<PlanResult.Rejected>(onlySales.plan("SELECT * FROM orders"))
        assertEquals(SimCodes.UNKNOWN_OBJECT, missing.code)
        assertIs<PlanResult.Planned>(onlySales.plan("SELECT * FROM [sales].[orders]"))
        val dotted = SimTable(
            name = "dbo.person",
            columns = listOf(SimColumn("a", SqlType.Int32, nullable = false)),
            primaryKey = listOf("a"),
            rows = { RowSource { null } },
        )
        val dottedPlanner = StatementPlanner(SimCatalog(listOf(dotted)))
        assertIs<PlanResult.Planned>(dottedPlanner.plan("SELECT a FROM [dbo.person]"))
        assertIs<PlanResult.Rejected>(dottedPlanner.plan("SELECT a FROM dbo.person"))
    }

    /**
     * Eine unbekannte Tabelle endet mit `TDS_SIM_UNKNOWN_OBJECT`, in jeder der vier Formen.
     * @spec SPEC-038
     */
    @Test
    fun `lehnt unbekannte Tabellen ab`() {
        for (sql in listOf(
            "SELECT * FROM nothing",
            "SELECT * FROM other.person",
            "INSERT INTO nothing (a) VALUES (1)",
            "UPDATE nothing SET a = 1",
            "DELETE FROM nothing",
            "SELECT * FROM [person ]",
        )) {
            val result = rejected(sql)
            assertEquals(SimCodes.UNKNOWN_OBJECT, result.code, sql)
            assertEquals("Tabelle nicht beschrieben", result.message, sql)
        }
    }

    /**
     * Die unbekannte Tabelle trägt ihren Namen, wie der Text ihn nennt (Messprotokoll, Szenario
     * `fehler-tabelle-unbekannt`): `<Schema>.<Name>` oder `<Name>`, ohne Klammern und Anführungszeichen, in der
     * Schreibung des Textes, Hochkomma und Zeilenumbruch unverändert; für jede der fünf Formen. Der Text der Meldung
     * bleibt „Tabelle nicht beschrieben“ (er erreicht den Client nie).
     * Wächter: ein Name, der die Schreibweise ändert (klein geschrieben, mit Klammern, mit Standardschema `dbo`).
     * @spec SPEC-038
     * @spec TDS-QA-01
     */
    @Test
    fun `die unbekannte Tabelle nennt ihren Namen wie der Text`() {
        val names = mapOf(
            "SELECT * FROM GibtEsNicht" to "GibtEsNicht",
            "SELECT * FROM [gibtes nicht]" to "gibtes nicht",
            "SELECT * FROM dbo.nothing" to "dbo.nothing",
            "SELECT * FROM [S1].[Nothing]" to "S1.Nothing",
            "SELECT * FROM \"x\".\"y\"" to "x.y",
            "SELECT * FROM [gibtes'nicht]" to "gibtes'nicht",
            "SELECT * FROM [gibtes''nicht]" to "gibtes''nicht",
            "SELECT * FROM [gibtes]]nicht]" to "gibtes]nicht",
            "SELECT * FROM [gibtes.nicht]" to "gibtes.nicht",
            "SELECT * FROM [gibtes\nnicht]" to "gibtes\nnicht",
            "INSERT INTO nothing (a) VALUES (1)" to "nothing",
            "UPDATE nothing SET a = 1" to "nothing",
            "DELETE FROM nothing" to "nothing",
            "MERGE INTO [dbo].[nothing] AS tgt USING (VALUES ( @P0 )) AS src ([a]) ON tgt.[a] = src.[a] " +
                "WHEN NOT MATCHED THEN INSERT ([a]) VALUES (src.[a]) OUTPUT \$action;" to "dbo.nothing",
        )
        for ((sql, name) in names) {
            val result = rejected(sql)
            assertEquals(SimCodes.UNKNOWN_OBJECT, result.code, sql)
            assertEquals("Tabelle nicht beschrieben", result.message, sql)
            assertEquals(name, result.objectName, sql)
        }
    }

    /**
     * Ein Bezeichner mit 128 Zeichen steht im Namen, einer mit 129 (die Grenze des echten Servers) und ein Name mit
     * einem Steuerzeichen außer dem Zeilenumbruch stehen nicht darin; die Ablehnung bleibt `TDS_SIM_UNKNOWN_OBJECT`.
     * Eine unbekannte Spalte nennt keinen Namen.
     * Wächter: eine Grenze bei 127 oder 130, ein Steuerzeichen im Namen, ein Name bei der Spalte.
     * @spec SPEC-038
     */
    @Test
    fun `der Name ausserhalb der Grenzen und bei einer Spalte fehlt`() {
        assertEquals("a".repeat(128), rejected("SELECT * FROM ${"a".repeat(128)}").objectName)
        assertEquals("s.${"a".repeat(128)}", rejected("SELECT * FROM s.${"a".repeat(128)}").objectName)
        assertEquals("${"b".repeat(128)}.t", rejected("SELECT * FROM [${"b".repeat(128)}].t").objectName)
        for (sql in listOf(
            "SELECT * FROM ${"a".repeat(129)}",
            "SELECT * FROM [${"a".repeat(129)}]",
            "SELECT * FROM [${"a".repeat(129)}].t",
            "SELECT * FROM s.[${"a".repeat(129)}]",
            "SELECT * FROM [gibtes\u0000nicht]",
            "SELECT * FROM [gibtes\tnicht]",
        )) {
            val result = rejected(sql)
            assertEquals(SimCodes.UNKNOWN_OBJECT, result.code, sql)
            assertNull(result.objectName, sql)
        }
        assertNull(rejected("SELECT nope FROM person").objectName)
        assertNull(rejected("INSERT INTO person (nope) VALUES (1)").objectName)
    }

    /**
     * Eine unbekannte Spalte endet mit `TDS_SIM_UNKNOWN_OBJECT`, an jeder Stelle, an der der Text eine Spalte nennt.
     * @spec SPEC-038
     */
    @Test
    fun `lehnt unbekannte Spalten an jeder Stelle ab`() {
        for (sql in listOf(
            "SELECT nope FROM person",
            "SELECT id, nope FROM person",
            "SELECT id FROM person WHERE nope = 1",
            "SELECT id FROM person WHERE id = 1 AND nope IN (1)",
            "SELECT id FROM person WHERE nope IS NULL",
            "SELECT id FROM person WHERE id = 1 AND name IS NULL AND nope IS NOT NULL",
            "INSERT INTO person (nope) VALUES (1)",
            "INSERT INTO person (id, nope) VALUES (1, 2)",
            "UPDATE person SET nope = 1",
            "UPDATE person SET id = 1, nope = 2",
            "UPDATE person SET id = 1 WHERE nope = 1",
            "DELETE FROM person WHERE nope = 1",
            "SELECT * FROM person WHERE nope = 1",
            "SELECT [name ] FROM person",
        )) {
            val result = rejected(sql)
            assertEquals(SimCodes.UNKNOWN_OBJECT, result.code, sql)
            assertEquals("Spalte nicht beschrieben", result.message, sql)
        }
    }

    /**
     * Form S2: zwei Auswahlen in einem Text sind zwei Pläne in der Reihenfolge des Textes; ist eine Tabelle oder
     * Spalte der zweiten nicht beschrieben, ist der ganze Text abgewiesen, bevor etwas gelesen wird (Festlegung).
     * @spec SPEC-036
     */
    @Test
    fun `plant zwei Auswahlen und weist den Text bei einer unbekannten zweiten ab`() {
        val planned = planner.plan("SELECT id FROM person WHERE id = 1; SELECT name FROM person")
        val series = assertIs<PlanResult.Series>(planned)
        assertEquals(listOf("Id", "Name"), series.plans.map { it.resultColumns.single().name })

        val unknownTable = rejected("SELECT id FROM person; SELECT id FROM nothing")
        assertEquals(SimCodes.UNKNOWN_OBJECT, unknownTable.code)
        assertEquals("nothing", unknownTable.objectName)
        val unknownColumn = rejected("SELECT id FROM person; SELECT nope FROM person")
        assertEquals("Spalte nicht beschrieben", unknownColumn.message)
    }

    /**
     * Eine nicht erkannte Anweisung endet mit `TDS_UNSUPPORTED_SQL`, auch wenn Tabelle und Spalten unbekannt wären:
     * die Zuordnung zur Form kommt vor der Objektprüfung.
     * @spec SPEC-037
     */
    @Test
    fun `lehnt nicht erkannte Anweisungen mit TDS_UNSUPPORTED_SQL ab`() {
        for (sql in listOf(
            "SELECT id FROM person ORDER BY id",
            "SELECT nope FROM nothing ORDER BY x",
            "SELECT COUNT(*) FROM person",
            "SELECT id FROM person; SELECT id FROM person; SELECT id FROM person",
            "SELECT id FROM person; DELETE FROM person",
            "SELECT id FROM person WHERE id = 1 AND id = 2 OR id = 3",
            "EXEC sp_who",
            "",
            "   ",
            "-- nur ein Kommentar",
            "SELECT id FROM person WHERE name = 'unterbrochen",
        )) {
            val result = rejected(sql)
            assertEquals(SimCodes.UNSUPPORTED_SQL, result.code, sql)
        }
    }

    /**
     * Die Meldung nennt nie Teile des Anweisungstextes.
     * @spec SPEC-037
     */
    @Test
    fun `nennt in Meldungen nie den Anweisungstext`() {
        val secret = "GEHEIMNAME"
        for (sql in listOf(
            "SELECT $secret FROM person",
            "SELECT id FROM $secret",
            "SELECT id FROM person WHERE name = '$secret' ORDER BY id",
            "SELECT id FROM person WHERE $secret LIKE 'x'",
            "SELECT id FROM person WHERE $secret = 1",
        )) {
            assertFalse(rejected(sql).message.contains(secret), sql)
        }
    }

    /**
     * Die zählende Attrappe: in allen abgewiesenen Fällen ruft nichts Zeilenquelle oder Callback. Der positive
     * Kontrollfall zeigt, dass die Zähler zählen und dass derselbe Plan sie erreicht; die Untergrenze der Fälle
     * ist geprüft.
     * @spec SPEC-036
     * @spec SPEC-037
     * @spec SPEC-038
     */
    @Test
    fun `ruft bei abgewiesenen Anweisungen weder Zeilenquelle noch Callback`() {
        val cases = listOf(
            "SELECT * FROM nothing",
            "SELECT nope FROM person",
            "SELECT * FROM person WHERE nope = 1",
            "INSERT INTO person (nope) VALUES (1)",
            "UPDATE person SET nope = 1",
            "UPDATE person SET id = 1 WHERE nope IS NULL",
            "DELETE FROM person WHERE nope IN (1, 2)",
            "DELETE FROM nothing",
            "SELECT * FROM person ORDER BY id",
            "SELECT * FROM person JOIN other ON a = b",
            "SELECT * FROM person; DELETE FROM person",
            "INSERT INTO person (id) VALUES (1); INSERT INTO person (id) VALUES (2)",
            "UPDATE person SET id = id + 1",
            "DELETE FROM person WHERE id = 1 OR id = 2",
            "SELECT TOP 1 * FROM person",
            "SELECT * FROM person WHERE name LIKE 'x%'",
            "INSERT INTO person VALUES (1, 'x', 2)",
            "DELETE person",
            "WITH x AS (SELECT 1) SELECT * FROM person",
            "SELECT * FROM person WHERE name = 'x",
        )
        assertTrue(cases.size >= 20, "Untergrenze der Fälle: ${cases.size}")
        for (sql in cases) assertIs<PlanResult.Rejected>(plan(sql), sql)
        assertEquals(0, person.total, "Zeilenquelle und Callbacks bleiben ungerufen")

        // Kontrollfall: derselbe Planer, dieselbe Attrappe; geplant wird, gezählt wird erst beim Aufruf.
        val select = planned("SELECT * FROM person")
        assertEquals(0, person.total, "Planen allein ruft nichts")
        val source = runBlocking { select.table.rows(SimQuery(select.resultColumns, null)) }
        assertEquals(1, person.rows)
        runBlocking { source.next() }
        assertEquals(1, person.sources)
        runBlocking {
            select.table.onInsert?.invoke(emptyMap())
            select.table.onUpdate?.invoke(emptyMap(), null)
            select.table.onDelete?.invoke(null)
        }
        assertEquals(listOf(1, 1, 1), listOf(person.inserts, person.updates, person.deletes))
        assertEquals(5, person.total)
    }
}
