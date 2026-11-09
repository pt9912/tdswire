package io.github.pt9912.tdswire.simulation

/** Ein Eintrag der Tabellenliste von `CREATE TABLE`, der keine Spalte ist. */
internal sealed interface TableLevel {
    data class PrimaryKey(val columns: List<String>) : TableLevel

    data class Element(val constraint: NewConstraint) : TableLevel
}

/**
 * Die Bedingungen der Formen D1 und D3 (`SPEC-036`): in `CREATE TABLE` als Eintrag der Tabellenliste, in `ALTER TABLE`
 * hinter `ADD CONSTRAINT`.
 * - `[CONSTRAINT <Name>] PRIMARY KEY (<Spalten>)` (nur in `CREATE TABLE`, der Name darf fehlen),
 * - `CONSTRAINT <Name> UNIQUE (<Spalten>)`,
 * - `CONSTRAINT <Name> FOREIGN KEY (<Spalten>) REFERENCES <Tabelle> (<Spalten>)` mit `[ON DELETE <Aktion>]` und
 *   `[ON UPDATE <Aktion>]`
 *   mit einer Zieltabelle ohne Schema (sie liegt im Schema der Tabelle) und der Aktion `NO ACTION`, `CASCADE`,
 *   `SET NULL` oder `SET DEFAULT`,
 * - `CONSTRAINT <Name> CHECK (<Ausdruck>)` mit dem Ausdruck nach [CheckExpressions],
 * - `CONSTRAINT <Name> DEFAULT <Literal> FOR <Spalte>` (nur in `ALTER TABLE`).
 * Eine Bedingung ohne Namen außer dem Schlüssel macht den Text nicht erkannt (der Server vergäbe einen Namen; welchen,
 * ist nicht gemessen). Gemessen ist der Wortlaut von d-migrate 1.12.0 (`schema generate` und `schema migrate
 * --dry-run`).
 */
internal class ConstraintParts(private val stream: TokenStream, private val parts: Parts) {
    fun isStart(): Boolean {
        val next = stream.peek() ?: return false
        return KEYWORDS.any { next.isKeyword(it) }
    }

    /** Der Eintrag der Tabellenliste von `CREATE TABLE`. */
    fun tableLevel(): TableLevel {
        val name = if (stream.acceptKeyword("CONSTRAINT")) parts.identifier() else null
        if (stream.acceptKeyword("PRIMARY")) {
            stream.keyword("KEY")
            return TableLevel.PrimaryKey(columnList())
        }
        return TableLevel.Element(body(name ?: throw unexpected(), forAlter = false))
    }

    /** Die Bedingung hinter `ALTER TABLE … ADD`; das Wort `CONSTRAINT` steht davor. */
    fun added(): NewConstraint {
        stream.keyword("CONSTRAINT")
        return body(parts.identifier(), forAlter = true)
    }

    private fun body(name: String, forAlter: Boolean): NewConstraint = when {
        stream.acceptKeyword("UNIQUE") -> NewConstraint.Unique(SimUniqueKey(name, columnList()))
        stream.acceptKeyword("FOREIGN") -> NewConstraint.Foreign(foreignKey(name))
        stream.acceptKeyword("CHECK") -> NewConstraint.Check(SimCheck(name, CheckExpressions(stream, parts).check()))
        forAlter && stream.acceptKeyword("DEFAULT") -> defaultFor(name)
        else -> throw unexpected()
    }

    private fun defaultFor(name: String): NewConstraint {
        val definition = DefaultDefinitions.of(stream.next()) ?: throw unexpected()
        stream.keyword("FOR")
        return NewConstraint.Default(name, parts.identifier(), definition)
    }

    private fun foreignKey(name: String): SimForeignKey {
        stream.keyword("KEY")
        val columns = columnList()
        stream.keyword("REFERENCES")
        val target = parts.identifier()
        val targetColumns = columnList()
        if (columns.size != targetColumns.size) throw Reject(Unrecognized.COUNT_MISMATCH)
        val actions = referentialActions()
        return SimForeignKey(name, columns, target, targetColumns, actions.first, actions.second)
    }

    /** `[ON DELETE <Aktion>] [ON UPDATE <Aktion>]` in beliebiger Reihenfolge, jedes höchstens einmal. */
    private fun referentialActions(): Pair<String, String> {
        val found = HashMap<String, String>()
        while (stream.acceptKeyword("ON")) {
            val event = if (stream.acceptKeyword("DELETE")) "DELETE" else updateEvent()
            if (found.put(event, action()) != null) throw unexpected()
        }
        return (found["DELETE"] ?: NO_ACTION) to (found["UPDATE"] ?: NO_ACTION)
    }

    private fun updateEvent(): String {
        stream.keyword("UPDATE")
        return "UPDATE"
    }

    private fun action(): String = when {
        stream.acceptKeyword("CASCADE") -> "CASCADE"

        stream.acceptKeyword("NO") -> {
            stream.keyword("ACTION")
            NO_ACTION
        }

        stream.acceptKeyword("SET") -> when {
            stream.acceptKeyword("NULL") -> "SET_NULL"
            stream.acceptKeyword("DEFAULT") -> "SET_DEFAULT"
            else -> throw unexpected()
        }

        else -> throw unexpected()
    }

    /** `( <Spalte> {, <Spalte>} )` ohne doppelte Spalte. */
    fun columnList(): List<String> {
        stream.symbol('(')
        val names = parts.list { parts.identifier() }
        stream.symbol(')')
        if (SimNames.firstDuplicate(names) != null) throw Reject(Unrecognized.DUPLICATE_COLUMN)
        return names
    }

    private companion object {
        val KEYWORDS = listOf("CONSTRAINT", "PRIMARY", "UNIQUE", "FOREIGN", "CHECK")
    }
}
