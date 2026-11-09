package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Die Strukturformen D1 bis D4 mit Standardwert, Eindeutigkeit, Fremdschlüssel, Prüfbedingung und Index (`SPEC-036`,
 * `SPEC-035`): der Wortlaut von d-migrate 1.12.0 ([dMigrateElementKunden] und folgende) ruft den Callback mit der
 * Tabelle nach der Anweisung, Elemente eingeschlossen. Gemessen am echten Server ist die Form des Katalogs für die
 * Prüfbedingung `Menge > 0` (`([Menge]>(0))`), `DEFAULT 1` (`((1))`) und `DEFAULT N'neu'` (`(N'neu')`); alles darüber
 * hinaus (andere Ausdrücke, Fehlernummern, die Kennung von `CREATE INDEX`) ist Setzung.
 */
class SimulationBackendElementsStructureTest {
    private val rowless: suspend (SimQuery) -> RowSource = { RowSource { null } }

    private class Store(var tables: List<SimTable>) {
        val shapes = mutableListOf<SimTable>()

        val backend = SimulationBackend({ SimCatalog(tables) }) { shape ->
            shapes += shape
            tables = tables.filterNot { it.schema == shape.schema && it.name == shape.name } + shape
        }
    }

    private val kunden = SimTable(
        name = "kunden",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("Email", SqlType.UnicodeText, maxLength = 200),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
    )

    private val bestellung = SimTable(
        name = "bestellung",
        columns = listOf(
            SimColumn("Id", SqlType.Int64, nullable = false),
            SimColumn("KundeId", SqlType.Int64, nullable = false),
            SimColumn("Menge", SqlType.Int32, nullable = false),
            SimColumn("Status", SqlType.UnicodeText, nullable = false, maxLength = 20),
        ),
        primaryKey = listOf("Id"),
        rows = rowless,
    )

    private fun run(store: Store, sql: String): List<QueryEvent> = collectEvents(store.backend, sql)

    private fun kind(events: List<QueryEvent>): StatementKind = assertIs<QueryEvent.UpdateCount>(events.single()).kind

    private fun failureCode(store: Store, sql: String): String =
        assertIs<QueryEvent.Failure>(run(store, sql).single(), sql).code

    /**
     * Happy (Wortlaut von d-migrate): die beiden `CREATE TABLE` des Solls und der Index. Der Callback bekommt für
     * `kunden` die Eindeutigkeit `uq_kunden_Email` an der Spalte, für `bestellung` die Standardwerte `((1))` und
     * `(N'neu')`, den Fremdschlüssel mit `CASCADE` und `NO_ACTION` und die Prüfbedingung in der Form des Servers;
     * `CREATE INDEX` meldet die Tabelle mit dem Index (Art `UNKNOWN`: die Kennung des Abschlusses ist nicht gemessen).
     * Wächter: ein Text, der die Klausel stumm überliest (die Elemente fehlten im Callback), eine Prüfbedingung im
     * Wortlaut des Autors statt in der Form des Servers, eine Art `CREATE_TABLE` für den Index.
     * @spec SPEC-036
     */
    @Test
    fun `die Texte von d-migrate legen Elemente an`() {
        val store = Store(emptyList())
        assertEquals(StatementKind.CREATE_TABLE, kind(run(store, dMigrateElementKunden)))
        assertEquals(StatementKind.CREATE_TABLE, kind(run(store, dMigrateElementBestellung)))
        assertEquals(StatementKind.UNKNOWN, kind(run(store, DMIGRATE_ELEMENT_INDEX)))

        val first = store.shapes[0]
        assertEquals(listOf(SimUniqueKey("uq_kunden_Email", listOf("Email"))), first.uniqueKeys)
        assertEquals(listOf("Id"), first.primaryKey)

        val second = store.shapes[1]
        assertEquals(listOf(null, null, "((1))", "(N'neu')"), second.columns.map { it.defaultDefinition })
        assertEquals(
            listOf(SimForeignKey("fk_bestellung_KundeId", listOf("KundeId"), "kunden", listOf("Id"), "CASCADE")),
            second.foreignKeys,
        )
        assertEquals(listOf(SimCheck("ck_bestellung_menge", "([Menge]>(0))")), second.checks)
        assertEquals(emptyList(), second.indexes)

        val indexed = store.shapes[2]
        assertEquals(listOf(SimIndex("ix_bestellung_status", listOf("Status"))), indexed.indexes)
        assertEquals(second.columns, indexed.columns)
        assertEquals(second.checks, indexed.checks)
        assertEquals(listOf("bestellung", "kunden"), store.tables.map { it.name }.sorted())
    }

    /**
     * Happy (Wortlaut von `schema migrate --dry-run`): gegen vorhandene Tabellen legen die `ALTER TABLE … ADD
     * CONSTRAINT` Standardwert (`DEFAULT … FOR`), Fremdschlüssel, Prüfbedingung und Eindeutigkeit an, jedes mit der
     * Art `ALTER_TABLE`; `WITH CHECK` ist erlaubt. Die Tabelle nach der Anweisung trägt das neue Element zu den
     * früheren.
     * Wächter: ein `DEFAULT` auf der falschen Spalte, ein Element, das die früheren verdrängt, die Art `UNKNOWN`.
     * @spec SPEC-036
     */
    @Test
    fun `ALTER TABLE ADD CONSTRAINT legt Elemente an`() {
        val store = Store(listOf(kunden, bestellung))
        for (text in dMigrateElementAlterTexts) assertEquals(StatementKind.ALTER_TABLE, kind(run(store, text)), text)

        val last = store.tables.single { it.name == "bestellung" }
        assertEquals(listOf(null, null, "((1))", "(N'neu')"), last.columns.map { it.defaultDefinition })
        assertEquals(listOf("_fk_KundeId"), last.foreignKeys.map { it.name })
        assertEquals(listOf(SimCheck("ck_bestellung_menge", "([Menge]>(0))")), last.checks)
        assertEquals(listOf("_unique_Email"), store.tables.single { it.name == "kunden" }.uniqueKeys.map { it.name })
        assertEquals(5, store.shapes.size)
    }

    /**
     * Happy: `CREATE UNIQUE INDEX` mit `INCLUDE` und zwei Schlüsselspalten; ein Fremdschlüssel mit allen Aktionen
     * (`NO ACTION`, `SET NULL`, `SET DEFAULT`, `CASCADE`) in beliebiger Reihenfolge von `ON DELETE` und `ON UPDATE`;
     * ein Fremdschlüssel auf die eigene Tabelle.
     * Wächter: eine vertauschte Aktion, ein Index ohne `unique`, ein verlorenes `INCLUDE`.
     * @spec SPEC-036
     */
    @Test
    fun `Indexformen und Aktionen werden gelesen`() {
        val store = Store(listOf(kunden, bestellung))
        run(store, "CREATE UNIQUE INDEX [ux] ON [bestellung] ([Menge], [Status]) INCLUDE ([KundeId])")
        assertEquals(
            listOf(SimIndex("ux", listOf("Menge", "Status"), unique = true, included = listOf("KundeId"))),
            store.shapes.last().indexes,
        )
        run(
            store,
            "ALTER TABLE bestellung ADD CONSTRAINT f1 FOREIGN KEY (KundeId) REFERENCES kunden (Id) " +
                "ON UPDATE SET NULL ON DELETE SET DEFAULT",
        )
        run(
            store,
            "ALTER TABLE bestellung ADD CONSTRAINT f2 FOREIGN KEY (KundeId) REFERENCES bestellung (Id) " +
                "ON DELETE NO ACTION ON UPDATE CASCADE",
        )
        run(store, "ALTER TABLE bestellung ADD CONSTRAINT f3 FOREIGN KEY (KundeId) REFERENCES kunden (Id)")
        val keys = store.tables.single { it.name == "bestellung" }.foreignKeys
        assertEquals(
            listOf(
                SimForeignKey("f1", listOf("KundeId"), "kunden", listOf("Id"), "SET_DEFAULT", "SET_NULL"),
                SimForeignKey("f2", listOf("KundeId"), "bestellung", listOf("Id"), "NO_ACTION", "CASCADE"),
                SimForeignKey("f3", listOf("KundeId"), "kunden", listOf("Id")),
            ),
            keys,
        )
    }

    /**
     * Die Form des Katalogs für den Ausdruck einer Prüfbedingung: `Menge > 0` ergibt `([Menge]>(0))` (gemessen, M-21);
     * die übrigen Fälle sind Setzung: Bezeichner in `[…]`, Zahlen in Klammern, Texte mit `N` wie geschrieben, alle
     * sechs Vergleiche, `IS [NOT] NULL`, `AND` und `OR` mit Leerzeichen, Klammern bleiben, ein Klammerpaar um das
     * Ganze, `]` im Bezeichner verdoppelt.
     * Wächter: Leerzeichen um das Vergleichszeichen, eine Zahl ohne Klammern, ein verlorenes `<>` oder `>=`.
     * @spec SPEC-036
     */
    @Test
    fun `die Pruefbedingung steht in der Form des Servers im Katalog`() {
        val expected = mapOf(
            "Menge > 0" to "([Menge]>(0))",
            "Menge>=1 AND Menge<=9" to "([Menge]>=(1) AND [Menge]<=(9))",
            "Menge <> 3 OR Status = 'a'" to "([Menge]<>(3) OR [Status]='a')",
            "(Menge < 1 OR Menge > 5) AND Status = N'x''y'" to "(([Menge]<(1) OR [Menge]>(5)) AND [Status]=N'x''y')",
            "Status IS NOT NULL" to "([Status] IS NOT NULL)",
            "[a]]b] IS NULL AND 0 < Menge" to "([a]]b] IS NULL AND (0)<[Menge])",
            "Menge = -2" to "([Menge]=(-2))",
        )
        for ((source, definition) in expected) {
            val store = Store(listOf(bestellung))
            run(store, "ALTER TABLE bestellung ADD CONSTRAINT c CHECK ($source)")
            assertEquals(definition, store.tables.single().checks.single().definition, source)
        }
    }

    /**
     * Die Form des Katalogs für den Standardwert: Zahl `((n))` (auch negativ und dezimal), Text `('…')`, nationaler
     * Text `(N'…')`, das Hochkomma verdoppelt. Gemessen sind `((1))` und `(N'neu')`; der Rest ist Setzung.
     * Wächter: ein Text ohne `N`, der als `N'…'` erscheint, ein nicht verdoppeltes Hochkomma.
     * @spec SPEC-036
     */
    @Test
    fun `der Standardwert steht in der Form des Servers im Katalog`() {
        val expected = mapOf(
            "1" to "((1))",
            "-3" to "((-3))",
            "1.50" to "((1.50))",
            "N'neu'" to "(N'neu')",
            "'alt'" to "('alt')",
            "N'o''k'" to "(N'o''k')",
        )
        for ((literal, definition) in expected) {
            val store = Store(listOf(bestellung))
            run(store, "ALTER TABLE bestellung ADD CONSTRAINT d DEFAULT $literal FOR Menge")
            assertEquals(definition, store.tables.single().column("Menge")?.defaultDefinition, literal)
            val created = Store(emptyList())
            run(created, "CREATE TABLE t (a INT CONSTRAINT d1 DEFAULT $literal)")
            assertEquals(definition, created.shapes.single().columns.single().defaultDefinition, literal)
        }
    }

    /**
     * Negativ: die Abweisung gegen den Katalog. Eine Zieltabelle oder Zielspalte, die es nicht gibt, eine Spalte der
     * Eindeutigkeit, die es nicht gibt, und ein Standardwert für eine unbekannte Spalte sind `TDS_SIM_UNKNOWN_OBJECT`;
     * ein doppelter Name einer Bedingung, ein zweiter Standardwert der Spalte und ein doppelter Name eines Index sind
     * `TDS_SIM_OBJECT_EXISTS`; ein `ALTER TABLE` und ein `CREATE INDEX` auf eine unbekannte Tabelle sind
     * `TDS_SIM_OBJECT_NOT_FOUND`. Die Codes für Bedingungen sind Setzung (nur 2714, 2705 und 4902 sind für Tabelle und
     * Spalte gemessen). Der Callback bleibt ungerufen.
     * Wächter: eine Abweisung als `TDS_UNSUPPORTED_SQL` (die Verbindung bliebe, aber der Code wäre falsch) oder eine
     * Anweisung, die stumm gelingt.
     * @spec SPEC-036
     */
    @Test
    fun `Elemente gegen den Katalog werden abgewiesen`() {
        val store = Store(listOf(kunden, bestellung.copy(checks = listOf(SimCheck("c1", "([Id]>(0))")))))
        val unknown = SimCodes.UNKNOWN_OBJECT
        val exists = SimCodes.OBJECT_EXISTS
        val missing = SimCodes.OBJECT_NOT_FOUND
        val cases = listOf(
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (KundeId) REFERENCES gibtes (Id)" to unknown,
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (KundeId) REFERENCES kunden (Nix)" to unknown,
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (Nix) REFERENCES kunden (Id)" to unknown,
            "ALTER TABLE bestellung ADD CONSTRAINT u UNIQUE (Nix)" to unknown,
            "ALTER TABLE bestellung ADD CONSTRAINT d DEFAULT 1 FOR Nix" to unknown,
            "ALTER TABLE bestellung ADD CONSTRAINT C1 CHECK (Id > 1)" to exists,
            "ALTER TABLE bestellung ADD CONSTRAINT c1 UNIQUE (Id)" to exists,
            "ALTER TABLE gibtes ADD CONSTRAINT u UNIQUE (Id)" to missing,
            "CREATE INDEX i ON gibtes (Id)" to missing,
            "CREATE INDEX i ON kunden (Nix)" to unknown,
            "CREATE INDEX i ON kunden (Id) INCLUDE (Nix)" to unknown,
            "CREATE TABLE t (a INT, CONSTRAINT f FOREIGN KEY (a) REFERENCES gibtes (a))" to unknown,
            "CREATE TABLE t (a INT, CONSTRAINT f FOREIGN KEY (b) REFERENCES t (a))" to unknown,
            "CREATE TABLE t (a INT, CONSTRAINT u UNIQUE (b))" to unknown,
            "CREATE TABLE t (a INT, CONSTRAINT u UNIQUE (a), CONSTRAINT U CHECK (a > 1))" to exists,
        )
        for ((sql, code) in cases) assertEquals(code, failureCode(store, sql), sql)
        run(store, "CREATE INDEX i ON kunden (Id)")
        assertEquals(exists, failureCode(store, "CREATE INDEX I ON kunden (Email)"))
        run(store, "ALTER TABLE bestellung ADD CONSTRAINT d DEFAULT 1 FOR Menge")
        assertEquals(exists, failureCode(store, "ALTER TABLE bestellung ADD CONSTRAINT d2 DEFAULT 2 FOR menge"))
        assertEquals(2, store.shapes.size)
    }

    /**
     * Negativ: Texte außerhalb der Formen sind nicht erkannt (`TDS_UNSUPPORTED_SQL`): Bedingungen ohne Namen (außer
     * dem Schlüssel), ein Fremdschlüssel mit Schema der Zieltabelle, ungleich viele Spalten, doppelte Spalten, eine
     * Aktion außerhalb der vier, `WITH NOCHECK`, `CLUSTERED`, `DESC`, eine Funktion, `IN`, `NOT` und ein Rechenzeichen
     * im Ausdruck, ein Standardwert, der kein Literal ist, eine verschachtelte Klammer über der Tiefe 16.
     * Wächter: ein Parser, der eine dieser Formen rät.
     * @spec SPEC-036
     */
    @Test
    fun `Formen ausserhalb der erkannten Teilmenge sind nicht erkannt`() {
        val store = Store(listOf(kunden, bestellung))
        val deep = "(".repeat(17) + "Menge > 1" + ")".repeat(17)
        val texts = listOf(
            "ALTER TABLE bestellung ADD UNIQUE (Id)",
            "ALTER TABLE bestellung ADD CONSTRAINT FOREIGN KEY (KundeId) REFERENCES kunden (Id)",
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (KundeId) REFERENCES dbo.kunden (Id)",
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (KundeId, Id) REFERENCES kunden (Id)",
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (Id, Id) REFERENCES kunden (Id, Id)",
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (KundeId) REFERENCES kunden (Id) ON DELETE RESTRICT",
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (KundeId) REFERENCES kunden (Id) ON DELETE CASCADE " +
                "ON DELETE CASCADE",
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (KundeId) REFERENCES kunden (Id) ON UPDATE CASCADE " +
                "ON UPDATE CASCADE",
            "ALTER TABLE bestellung ADD CONSTRAINT f FOREIGN KEY (KundeId) REFERENCES kunden (Id) ON DELETE SET",
            "ALTER TABLE bestellung WITH NOCHECK ADD CONSTRAINT u UNIQUE (Id)",
            "ALTER TABLE bestellung WITH CHECK ADD Neu INT",
            "ALTER TABLE bestellung ADD CONSTRAINT p PRIMARY KEY (Id)",
            "ALTER TABLE bestellung ADD CONSTRAINT d DEFAULT GETDATE() FOR Menge",
            "ALTER TABLE bestellung ADD CONSTRAINT d DEFAULT 1",
            "ALTER TABLE bestellung ADD CONSTRAINT c CHECK (Menge IN (1, 2))",
            "ALTER TABLE bestellung ADD CONSTRAINT c CHECK (NOT Menge = 1)",
            "ALTER TABLE bestellung ADD CONSTRAINT c CHECK (LEN(Status) > 1)",
            "ALTER TABLE bestellung ADD CONSTRAINT c CHECK (Menge > 1 + 2)",
            "ALTER TABLE bestellung ADD CONSTRAINT c CHECK (Menge ! 1)",
            "ALTER TABLE bestellung ADD CONSTRAINT c CHECK (Menge > )",
            "ALTER TABLE bestellung ADD CONSTRAINT c CHECK ($deep)",
            "ALTER TABLE bestellung ADD CONSTRAINT c CHECK (Menge > 1) trailing",
            "ALTER TABLE bestellung ADD Neu INT UNIQUE",
            "ALTER TABLE bestellung ADD Neu INT CONSTRAINT u UNIQUE",
            "CREATE TABLE t (a INT, UNIQUE (a))",
            "CREATE TABLE t (a INT CONSTRAINT d DEFAULT GETDATE())",
            "CREATE TABLE t (a INT CONSTRAINT d DEFAULT 1 CONSTRAINT e DEFAULT 2)",
            "CREATE TABLE t (a INT CONSTRAINT u UNIQUE CONSTRAINT v UNIQUE)",
            "CREATE TABLE t (a INT NOT NULL NULL)",
            "CREATE TABLE t (a INT CONSTRAINT d FOREIGN KEY)",
            "CREATE TABLE t (a INT, CONSTRAINT d DEFAULT 1 FOR a)",
            "CREATE TABLE t (a INT, CONSTRAINT u UNIQUE (a, a))",
            "CREATE TABLE t (a INT, CONSTRAINT p PRIMARY KEY (a), PRIMARY KEY (a))",
            "CREATE INDEX i ON kunden (Id, Id)",
            "CREATE INDEX i ON kunden (Id) INCLUDE (Id)",
            "CREATE INDEX i ON kunden",
            "CREATE UNIQUE TABLE t (a INT)",
        )
        for (sql in texts) assertEquals(SimCodes.UNSUPPORTED_SQL, failureCode(store, sql), sql)
        assertEquals(emptyList(), store.shapes)
    }

    /**
     * Happy: der Schlüssel in `CREATE TABLE` darf ohne Namen stehen, Eindeutigkeit, Fremdschlüssel und Prüfbedingung
     * stehen in beliebiger Reihenfolge vor und hinter den Spalten; Eindeutigkeit und Standardwert stehen an der Spalte
     * hinter `NOT NULL` oder davor; jede Klausel einer Spalte höchstens einmal.
     * Wächter: eine Reihenfolge, die der Parser nur für den Wortlaut von d-migrate kann.
     * @spec SPEC-036
     */
    @Test
    fun `die Reihenfolge der Eintraege ist frei`() {
        val store = Store(emptyList())
        run(
            store,
            "CREATE TABLE t (CONSTRAINT c CHECK (a > 0), a INT CONSTRAINT d DEFAULT 5 NOT NULL CONSTRAINT u UNIQUE, " +
                "b INT NOT NULL CONSTRAINT d2 DEFAULT 6, PRIMARY KEY (b), " +
                "CONSTRAINT f FOREIGN KEY (a) REFERENCES t (b))",
        )
        val shape = store.shapes.single()
        assertEquals(listOf("a", "b"), shape.columns.map { it.name })
        assertEquals(listOf("((5))", "((6))"), shape.columns.map { it.defaultDefinition })
        assertEquals(listOf(false, false), shape.columns.map { it.nullable })
        assertEquals(listOf("b"), shape.primaryKey)
        assertEquals(listOf("u"), shape.uniqueKeys.map { it.name })
        assertEquals(listOf("f"), shape.foreignKeys.map { it.name })
        assertEquals(listOf("([a]>(0))"), shape.checks.map { it.definition })
    }
}
