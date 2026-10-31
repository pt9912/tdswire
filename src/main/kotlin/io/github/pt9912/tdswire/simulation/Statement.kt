package io.github.pt9912.tdswire.simulation

/**
 * Ein Wert aus dem Anweisungstext, so wie er dort steht (`SPEC-036` Wert). Die Überführung in den
 * Spaltentyp (`SPEC-035`) ist nicht geschehen: [Number] trägt die Schreibweise, [Text] den entschachtelten Text.
 */
sealed interface SimValue {
    /** Eine Zahl wie geschrieben, etwa `-12` oder `3.50`. */
    data class Number(val text: String) : SimValue

    /** Ein Textliteral `'…'` oder `N'…'`. */
    data class Text(val value: String, val national: Boolean) : SimValue

    /** Das Wort `NULL` als Wert. */
    data object Null : SimValue

    /** Ein Verweis auf den Parameter [name] (ohne `@`) der Anfrage. */
    data class Parameter(val name: String) : SimValue
}

/** Ein Term einer Bedingung (`SPEC-036` Term); [column] ist der Name, wie er im Text steht. */
sealed interface Term {
    val column: String

    /** `<Spalte> = <Wert>` (ein Wert) oder `<Spalte> IN (<Wert> {, <Wert>})`. */
    data class In(override val column: String, val values: List<SimValue>) : Term

    /** `<Spalte> IS [NOT] NULL`. */
    data class IsNull(override val column: String, val negated: Boolean) : Term
}

/** Eine Tabelle, wie der Text sie nennt; [schema] ist `null`, wenn der Text keines nennt. */
data class TableRef(val schema: String?, val name: String)

/** Eine Zuweisung `<Spalte> = <Wert>` von `UPDATE … SET`. */
data class Assignment(val column: String, val value: SimValue)

/** Die erkannte Form einer Anweisung (`SPEC-036`), Namen und Werte noch unbeprüft gegen die Beschreibung. */
sealed interface Statement {
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

/** Das Ergebnis der Erkennung. */
sealed interface Recognition {
    data class Recognized(val statement: Statement) : Recognition

    data class NotRecognized(val reason: Unrecognized) : Recognition
}
