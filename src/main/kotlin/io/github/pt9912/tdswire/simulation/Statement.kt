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
}

/** Eine `SET`-Anweisung der Form C: Option in ASCII-Großschreibung und ihr Argument (`ON`, `OFF` oder die Zahl). */
internal data class SetOption(val option: String, val argument: String)

/** Die Texte der Form C (`SPEC-036`), die keine Tabelle nennen. */
internal sealed interface FixedForm {
    /** `SELECT 1`: der Verbindungstest. */
    data object Probe : FixedForm

    /** Eine oder mehrere `SET`-Anweisungen, durch `;` getrennt. */
    data class Settings(val options: List<SetOption>) : FixedForm
}

/** Das Ergebnis der Erkennung. */
internal sealed interface Recognition {
    data class Recognized(val statement: Statement) : Recognition

    data class Fixed(val form: FixedForm) : Recognition

    data class NotRecognized(val reason: Unrecognized) : Recognition
}
