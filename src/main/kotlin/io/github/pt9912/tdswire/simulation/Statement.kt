package io.github.pt9912.tdswire.simulation

/**
 * Ein Wert aus dem Anweisungstext, so wie er dort steht (`SPEC-036` Wert). Die Überführung in den
 * Spaltentyp (`SPEC-035`) ist nicht geschehen: [Number] trägt die Schreibweise, [Text] den entschachtelten Text.
 */
internal sealed interface SimValue {
    /** Eine Zahl wie geschrieben, etwa `-12` oder `3.50`. */
    data class Number(val text: String) : SimValue

    /** Ein Textliteral `'…'` oder `N'…'`. */
    data class Text(val value: String, val national: Boolean) : SimValue

    /** Das Wort `NULL` als Wert. */
    data object Null : SimValue

    /** Ein Verweis auf den Parameter [name] (ohne `@`) der Anfrage. */
    data class Parameter(val name: String) : SimValue
}

/** Ein Term einer Bedingung (`SPEC-036` Term). */
internal sealed interface Term {
    /** Ein Term über eine Spalte; [column] ist der Name, wie er im Text steht. */
    sealed interface Named : Term {
        val column: String
    }

    /** `<Spalte> = <Wert>` (ein Wert) oder `<Spalte> IN (<Wert> {, <Wert>})`. */
    data class In(override val column: String, val values: List<SimValue>) : Named

    /** `<Spalte> IS [NOT] NULL`. */
    data class IsNull(override val column: String, val negated: Boolean) : Named

    /** `<Ganzzahl> = <Ganzzahl>` (`SPEC-036` Konstantenterm); [holds] ist, ob beide Zahlen gleich sind. Nur in S1. */
    data class Constant(val holds: Boolean) : Term
}

/** Eine Tabelle, wie der Text sie nennt; [schema] ist `null`, wenn der Text keines nennt. */
internal data class TableRef(val schema: String?, val name: String)

/** Eine Zuweisung `<Spalte> = <Wert>` von `UPDATE … SET`. */
internal data class Assignment(val column: String, val value: SimValue)

/** Die erkannte Form einer Anweisung (`SPEC-036`), Namen und Werte noch unbeprüft gegen die Beschreibung. */
internal sealed interface Statement {
    val table: TableRef

    /** S1: `columns` ist `null` bei `*`. */
    data class Select(override val table: TableRef, val columns: List<String>?, val where: List<Term>) : Statement

    /** W1: Spalten und Werte gleich viele, in der Reihenfolge des Textes. */
    data class Insert(override val table: TableRef, val columns: List<String>, val values: List<SimValue>) : Statement

    /** W2. */
    data class Update(override val table: TableRef, val set: List<Assignment>, val where: List<Term>) : Statement

    /** W3. */
    data class Delete(override val table: TableRef, val where: List<Term>) : Statement

    /**
     * D1: `CREATE TABLE` mit den Spalten in der Reihenfolge des Textes; [key] ist `null`, wenn der Text keinen
     * Schlüssel nennt. [elements] sind die Eindeutigkeiten, Fremdschlüssel und Prüfbedingungen des Textes.
     */
    data class CreateTable(
        override val table: TableRef,
        val cols: List<SimColumn>,
        val key: List<String>?,
        val elements: TableElements = TableElements(),
    ) : Statement

    /** D2: `ALTER TABLE … ADD` mit den neuen Spalten. */
    data class AddColumns(override val table: TableRef, val columns: List<SimColumn>) : Statement

    /** D3: `ALTER TABLE … ADD CONSTRAINT` mit Eindeutigkeit, Fremdschlüssel, Prüfbedingung oder Standardwert. */
    data class AddConstraint(override val table: TableRef, val constraint: NewConstraint) : Statement

    /** D4: `CREATE [UNIQUE] INDEX … ON … (…) [INCLUDE (…)]`. */
    data class CreateIndex(override val table: TableRef, val index: SimIndex) : Statement

    /** D5: `ALTER TABLE … DROP CONSTRAINT [IF EXISTS] <Name>`; [ifExists] ist wahr mit `IF EXISTS`. */
    data class DropConstraint(override val table: TableRef, val name: String, val ifExists: Boolean) : Statement

    /** D6: `DROP INDEX [IF EXISTS] <Name> ON <Tabelle>`; [ifExists] ist wahr mit `IF EXISTS`. */
    data class DropIndex(override val table: TableRef, val name: String, val ifExists: Boolean) : Statement

    /**
     * D7: der Text von d-migrate mit `DECLARE @df …` ([DefaultDropForms]); er entfernt den Standardwert der Spalte
     * [column] (Name, wie der Text ihn nennt), wenn sie einen hat, und ändert sonst nichts.
     */
    data class DropDefault(override val table: TableRef, val column: String) : Statement

    /**
     * M: `MERGE … OUTPUT $action` (`SPEC-036` Form M). [sourceColumns] benennt die Werte der einen `VALUES`-Zeile
     * [values]; [on] vergleicht Spalten der Tabelle mit Quellspalten, [update] ist `null` ohne Zweig `WHEN MATCHED`,
     * [insert] bildet die Spalten der Tabelle aus Quellspalten.
     */
    data class Merge(
        override val table: TableRef,
        val sourceColumns: List<String>,
        val values: List<SimValue>,
        val on: List<MergeLink>,
        val update: List<MergeLink>?,
        val insert: List<MergeLink>,
    ) : Statement
}

/** Die Elemente eines `CREATE TABLE` außer Spalten und Schlüssel (Form D1). */
internal data class TableElements(
    val uniqueKeys: List<SimUniqueKey> = emptyList(),
    val foreignKeys: List<SimForeignKey> = emptyList(),
    val checks: List<SimCheck> = emptyList(),
)

/** Das Element, das `ALTER TABLE … ADD CONSTRAINT` anlegt (Form D3). */
internal sealed interface NewConstraint {
    data class Unique(val key: SimUniqueKey) : NewConstraint

    data class Foreign(val key: SimForeignKey) : NewConstraint

    data class Check(val check: SimCheck) : NewConstraint

    /** `DEFAULT <Literal> FOR <Spalte>`: [definition] ist die Form des Katalogs, [name] der Name der Bedingung. */
    data class Default(val name: String, val column: String, val definition: String) : NewConstraint
}

/** Eine Zuordnung `<Alias der Tabelle>.<Spalte> = <Alias der Quelle>.<Spalte>` oder die Paarung von `INSERT`. */
internal data class MergeLink(val target: String, val source: String)

/** Eine `SET`-Anweisung der Form C: Option in ASCII-Großschreibung und ihr Argument (`ON`, `OFF` oder die Zahl). */
internal data class SetOption(val option: String, val argument: String)

/** Die Texte der Form C (`SPEC-036`), die keine Tabelle nennen. */
internal sealed interface FixedForm {
    /** `SELECT 1`: der Verbindungstest. */
    data object Probe : FixedForm

    /** Eine oder mehrere `SET`-Anweisungen, durch `;` getrennt. */
    data class Settings(val options: List<SetOption>) : FixedForm

    /**
     * Ein Text der Transaktionssteuerung nach [TransactionForms]: `IF @@TRANCOUNT > 0 COMMIT|ROLLBACK TRAN`, davor
     * optional `set implicit_transactions on|off`. [implicit] ist `null` ohne `set`-Teil, sonst der Wert (`true` für
     * `on`); [commit] ist wahr für `COMMIT`, falsch für `ROLLBACK`.
     */
    data class Transaction(val implicit: Boolean?, val commit: Boolean) : FixedForm
}

/** Das Ergebnis der Erkennung. */
internal sealed interface Recognition {
    data class Recognized(val statement: Statement) : Recognition

    /**
     * Eine Katalogabfrage (`SPEC-036`, Form K); ihre Parameter stehen in der Anfrage, nicht im Text. Nur wenn der Text
     * das Argument als Literal trägt (`OBJECT_ID('…')`), steht es in [literal].
     */
    data class Catalog(val form: CatalogForm, val literal: String? = null) : Recognition

    /** `INSERT BULK` (`SPEC-036`, Form B): das Ziel und die Spalten, wie der Text sie nennt. */
    data class Bulk(val table: TableRef, val columns: List<String>) : Recognition

    data class Fixed(val form: FixedForm) : Recognition

    data class NotRecognized(val reason: Unrecognized) : Recognition
}
