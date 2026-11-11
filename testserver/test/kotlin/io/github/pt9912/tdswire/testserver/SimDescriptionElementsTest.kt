package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.simulation.SimCheck
import io.github.pt9912.tdswire.simulation.SimForeignKey
import io.github.pt9912.tdswire.simulation.SimIndex
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimUniqueKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Der Leser der Tabellenbeschreibung für Standardwert, Eindeutigkeit, Fremdschlüssel, Index und Prüfbedingung
 * (`SPEC-035`). Die Bäume sind von Hand gebaut. Gemessen ist nur, dass `d-migrate schema generate` (1.12.0) die
 * Formen `unique`, `default`, `references` (an der Spalte), `indices` und `constraints` mit den hier benutzten Feldern
 * annimmt und die Namen `uq_<Tabelle>_<Spalte>` und `fk_<Tabelle>_<Spalte>` vergibt; alle Fehler des Lesers sind
 * Festlegung.
 */
class SimDescriptionElementsTest {
    private val b = DocBuilder()

    private fun load(root: SimNode): List<SimTable> = SimDescriptionReader.read(root)

    private fun failure(root: SimNode): SimLoadException = assertFailsWith<SimLoadException> { load(root) }

    private fun id() = b.col("biginteger", "required" to b.s("true"))

    private fun customers() = b.table(b.m("Id" to id(), "Mail" to b.col("text")), b.keys("Id"))

    /** Die Tabellen `kunden` (Ziel) und `t` mit den Spalten [columns] und den Feldern [extra] der Tabelle. */
    private fun doc(columns: SimMap, vararg extra: Pair<String, SimNode>): SimMap =
        b.doc(b.m("kunden" to customers(), "t" to b.table(columns, b.keys("Id"), *extra)))

    private fun reference(vararg fields: Pair<String, SimNode>) =
        b.col("biginteger", "references" to b.m("table" to b.s("kunden"), "column" to b.s("Id"), *fields))

    /**
     * Happy: `unique: true` an einer Spalte ergibt die Eindeutigkeit `uq_<Tabelle>_<Spalte>` (der Name, den d-migrate
     * beim Anlegen vergibt), `unique: false` keine. `unique: "true"` (in Anführungszeichen) ist ein Ladefehler.
     * Wächter: ein Leser, der `unique` wieder ignoriert.
     * @spec SPEC-035
     */
    @Test
    fun `liest unique an der Spalte`() {
        val columns = b.m(
            "Id" to id(),
            "Mail" to b.col("text", "unique" to b.s("true")),
            "N" to b.col("text", "unique" to b.s("false")),
        )
        val read = load(doc(columns))
        assertEquals(listOf(SimUniqueKey("uq_t_Mail", listOf("Mail"))), read.single { it.name == "t" }.uniqueKeys)
        val quoted = failure(doc(b.m("Id" to id(), "Mail" to b.col("text", "unique" to b.s("true", plain = false)))))
        assertEquals("nur true oder false erlaubt", quoted.reason)
        assertEquals("tables.t.columns.Mail.unique", quoted.path)
    }

    /**
     * Happy: `unique_constraint` benennt die Eindeutigkeit von `unique: true`, so wie `schema reverse` von d-migrate
     * 1.12.0 sie schreibt (Messprotokoll Zeile a5: `unique: true` mit `unique_constraint: uq_lieferanten_Name`); ohne
     * `unique_constraint` gilt `uq_<Tabelle>_<Spalte>`. `unique_constraint` ohne `unique: true` und ein leerer Name
     * sind Ladefehler (Festlegung).
     * Wächter: ein Leser, der `unique_constraint` als unbekanntes Feld abweist (der Start bräche an der Datei, die
     * `schema reverse` schreibt), oder den Namen ignoriert.
     * @spec SPEC-035
     */
    @Test
    fun `liest unique_constraint als Namen der Eindeutigkeit`() {
        val named = b.col("text", "unique" to b.s("true"), "unique_constraint" to b.s("uq_lieferanten_Name"))
        val read = load(doc(b.m("Id" to id(), "Name" to named)))
        assertEquals(
            listOf(SimUniqueKey("uq_lieferanten_Name", listOf("Name"))),
            read.single { it.name == "t" }.uniqueKeys,
        )
        val alone = failure(doc(b.m("Id" to id(), "Name" to b.col("text", "unique_constraint" to b.s("uq_x")))))
        assertEquals("unique_constraint ohne unique: true", alone.reason)
        assertEquals("tables.t.columns.Name.unique_constraint", alone.path)
        val empty = b.col("text", "unique" to b.s("true"), "unique_constraint" to b.s(""))
        assertEquals("tables.t.columns.Name.unique_constraint", failure(doc(b.m("Id" to id(), "Name" to empty))).path)
    }

    /**
     * Happy: `default` ergibt die Form des Katalogs: ein Wahrheitswert `((1))` oder `((0))`, eine Zahl `((n))`, ein
     * anderer Skalar `(N'…')` mit verdoppeltem Hochkomma; ein Skalar in Anführungszeichen ist Text, auch wenn er wie
     * eine Zahl aussieht. Gemessen am Server sind `((1))` und `(N'neu')`. Eine Abbildung (Funktionsaufruf) ist ein
     * Ladefehler.
     * Wächter: eine Zahl als Text, ein nicht verdoppeltes Hochkomma, ein Funktionsaufruf, der still entfällt.
     * @spec SPEC-035
     */
    @Test
    fun `liest den Standardwert in der Form des Katalogs`() {
        val expected = listOf(
            b.s("1") to "((1))",
            b.s("true") to "((1))",
            b.s("false") to "((0))",
            b.s("-3") to "((-3))",
            b.s("1.5") to "((1.5))",
            b.s("neu") to "(N'neu')",
            b.s("7", plain = false) to "(N'7')",
            b.s("o'k", plain = false) to "(N'o''k')",
            b.s("1e3") to "(N'1e3')",
        )
        for ((node, definition) in expected) {
            val read = load(doc(b.m("Id" to id(), "C" to b.col("text", "default" to node))))
            val column = read.single { it.name == "t" }.columns.single { it.name == "C" }
            assertEquals(definition, column.defaultDefinition, node.text)
        }
        val function = b.m("type" to b.s("function_call"))
        val call = failure(doc(b.m("Id" to id(), "C" to b.col("text", "default" to function))))
        assertEquals("Skalar erwartet", call.reason)
        assertEquals("tables.t.columns.C.default", call.path)
    }

    /**
     * Happy: `references` an einer Spalte ergibt den Fremdschlüssel `fk_<Tabelle>_<Spalte>` auf die Zieltabelle und
     * Zielspalte; die Aktionen `cascade`, `set_null`, `set_default` und `no_action` entsprechen den Namen des
     * Katalogs, `restrict` gilt als `NO_ACTION` (so rendert d-migrate es für SQL Server); ohne Angabe gilt `NO_ACTION`.
     * Negativ: eine unbekannte Aktion, eine fehlende Zieltabelle und eine fehlende Zielspalte sind Ladefehler, die
     * letzten beiden erst nach allen Tabellen (die Zieltabelle darf hinter der Tabelle stehen).
     * Wächter: eine vertauschte Aktion, ein Ziel, das nicht geprüft wird.
     * @spec SPEC-035
     */
    @Test
    fun `liest references an der Spalte`() {
        val actions = mapOf(
            "cascade" to "CASCADE",
            "set_null" to "SET_NULL",
            "set_default" to "SET_DEFAULT",
            "no_action" to "NO_ACTION",
            "restrict" to "NO_ACTION",
        )
        for ((name, catalog) in actions) {
            val both = reference("on_delete" to b.s(name), "on_update" to b.s("cascade"))
            val read = load(doc(b.m("Id" to id(), "K" to both)))
            val key = read.single { it.name == "t" }.foreignKeys.single()
            assertEquals(SimForeignKey("fk_t_K", listOf("K"), "kunden", listOf("Id"), catalog, "CASCADE"), key, name)
        }
        val plain = load(doc(b.m("Id" to id(), "K" to reference()))).single { it.name == "t" }.foreignKeys.single()
        assertEquals(SimForeignKey("fk_t_K", listOf("K"), "kunden", listOf("Id")), plain)

        val action = failure(doc(b.m("Id" to id(), "K" to reference("on_delete" to b.s("drop")))))
        assertEquals("Aktion unbekannt", action.reason)
        assertEquals("tables.t.columns.K.references.on_delete", action.path)
        val missingTable = b.col("biginteger", "references" to b.m("table" to b.s("gibtes"), "column" to b.s("Id")))
        val noTable = failure(doc(b.m("Id" to id(), "K" to missingTable)))
        assertEquals("Zieltabelle nicht vorhanden", noTable.reason)
        assertEquals("tables.t.columns.K.references", noTable.path)
        val missingColumn = b.col("biginteger", "references" to b.m("table" to b.s("kunden"), "column" to b.s("Nix")))
        assertEquals("Zielspalte nicht vorhanden", failure(doc(b.m("Id" to id(), "K" to missingColumn))).reason)
        val later = b.doc(
            b.m(
                "t" to b.table(b.m("Id" to id(), "K" to reference()), b.keys("Id")),
                "kunden" to customers(),
            ),
        )
        assertEquals(1, load(later).first().foreignKeys.size)
    }

    /**
     * Happy: `indices` einer Tabelle (Name, Spalten, `unique`; das Feld `type` ist ohne Wirkung) und `constraints` der
     * Arten `unique`, `foreign_key` (mit `columns` und `references` mit `table`, `columns` und Aktionen) und `check`
     * (der Ausdruck steht im Katalog wie geschrieben).
     * Negativ: eine unbekannte Art, ein fehlendes Pflichtfeld, eine unbekannte Spalte, eine leere oder doppelte Liste,
     * ungleich viele Zielspalten und ein unbekanntes Feld sind Ladefehler; zwei Elemente mit demselben Namen ebenso.
     * Wächter: ein Leser, der `indices` oder `constraints` wieder ignoriert, oder ein `check`, der den Ausdruck
     * umschreibt.
     * @spec SPEC-035
     */
    @Test
    fun `liest indices und constraints der Tabelle`() {
        val indices = b.l(
            b.m(
                "name" to b.s("ix_a"),
                "columns" to b.keys("Mail", "Id"),
                "unique" to b.s("true"),
                "type" to b.s("btree"),
            ),
            b.m("name" to b.s("ix_b"), "columns" to b.keys("Mail")),
        )
        val constraints = b.l(
            b.m("name" to b.s("uq_m"), "type" to b.s("unique"), "columns" to b.keys("Mail")),
            b.m(
                "name" to b.s("fk_x"),
                "type" to b.s("foreign_key"),
                "columns" to b.keys("Id"),
                "references" to b.m(
                    "table" to b.s("kunden"),
                    "columns" to b.keys("Id"),
                    "on_delete" to b.s("set_null"),
                ),
            ),
            b.m("name" to b.s("ck_i"), "type" to b.s("check"), "expression" to b.s("Id > 0 AND Id < 9")),
        )
        val columns = b.m("Id" to id(), "Mail" to b.col("text"))
        val read = load(doc(columns, "indices" to indices, "constraints" to constraints))
        val t = read.single { it.name == "t" }
        val expectedIndexes = listOf(
            SimIndex("ix_a", listOf("Mail", "Id"), unique = true),
            SimIndex("ix_b", listOf("Mail")),
        )
        assertEquals(expectedIndexes, t.indexes)
        assertEquals(listOf(SimUniqueKey("uq_m", listOf("Mail"))), t.uniqueKeys)
        assertEquals(listOf(SimForeignKey("fk_x", listOf("Id"), "kunden", listOf("Id"), "SET_NULL")), t.foreignKeys)
        assertEquals(listOf(SimCheck("ck_i", "Id > 0 AND Id < 9")), t.checks)
    }

    /**
     * Negativ: die Ladefehler der Elemente von Tabelle (`indices`, `constraints`), jeder mit festem Grund und dem Pfad
     * des Eintrags; keiner nennt einen Wert aus dem Dokument.
     * Wächter: ein Fehler, der als Ausnahme der Tabelle (`IllegalArgumentException`) statt als Ladefehler entweicht.
     * @spec SPEC-035
     */
    @Test
    fun `weist fehlerhafte indices und constraints ab`() {
        fun index(vararg f: Pair<String, SimNode>) =
            doc(b.m("Id" to id(), "Mail" to b.col("text")), "indices" to b.l(b.m(*f)))
        fun constraint(vararg f: Pair<String, SimNode>) =
            doc(b.m("Id" to id(), "Mail" to b.col("text")), "constraints" to b.l(b.m(*f)))
        val name = "name" to b.s("n")
        val refs = "references" to b.m("table" to b.s("kunden"), "columns" to b.keys("Id", "Mail"))
        val wrongRefs = "references" to b.m("table" to b.s("kunden"), "column" to b.s("Id"))
        val cases = listOf(
            index("columns" to b.keys("Id")) to "Pflichtfeld name fehlt",
            index(name) to "Pflichtfeld columns fehlt",
            index(name, "columns" to b.keys("Nix")) to "Spalte nicht vorhanden",
            index(name, "columns" to b.keys()) to "mindestens eine Spalte erforderlich",
            index(name, "columns" to b.keys("Id", "id")) to "Spalte doppelt",
            index(name, "columns" to b.keys("Id"), "include" to b.keys("Mail")) to "Feld unbekannt",
            constraint(name, "columns" to b.keys("Id")) to "Pflichtfeld type fehlt",
            constraint(name, "type" to b.s("primary_key")) to "Typ der Bedingung ohne Entsprechung",
            constraint(name, "type" to b.s("check")) to "Pflichtfeld expression fehlt",
            constraint(name, "type" to b.s("unique")) to "Pflichtfeld columns fehlt",
            constraint(name, "type" to b.s("foreign_key"), "columns" to b.keys("Id")) to "Pflichtfeld references fehlt",
            constraint(name, "type" to b.s("foreign_key"), "columns" to b.keys("Id"), refs) to
                "Zielspalten ungleich viele wie Spalten",
            constraint(name, "type" to b.s("foreign_key"), "columns" to b.keys("Id"), wrongRefs) to "Feld unbekannt",
        )
        for ((tree, reason) in cases) assertEquals(reason, failure(tree).reason, reason)
        val clash = b.m("name" to b.s("uq_t_Mail"), "type" to b.s("unique"), "columns" to b.keys("Id"))
        val twice = doc(
            b.m("Id" to id(), "Mail" to b.col("text", "unique" to b.s("true"))),
            "constraints" to b.l(clash),
        )
        val error = failure(twice)
        assertEquals("Name einer Bedingung oder eines Index doppelt", error.reason)
        assertEquals("tables.t", error.path)
    }
}
