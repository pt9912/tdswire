package io.github.pt9912.tdswire.simulation

/**
 * Die Formen D1 bis D4 von `SPEC-036`:
 * - D1 `CREATE TABLE [<Schema>.]<Tabelle> (<Eintrag> {, <Eintrag>})`: ein Eintrag ist eine Spalte ([ColumnParts]) oder
 *   eine Bedingung der Tabelle ([ConstraintParts]), höchstens ein Schlüssel,
 * - D2 `ALTER TABLE [<Schema>.]<Tabelle> ADD <Spalte> {, <Spalte>}`,
 * - D3 `ALTER TABLE [<Schema>.]<Tabelle> [WITH CHECK] ADD CONSTRAINT <Name> …` ([ConstraintParts]),
 * - D4 `CREATE [UNIQUE] INDEX <Name> ON [<Schema>.]<Tabelle> (<Spalten>) [INCLUDE (<Spalten>)]`.
 * `NVARCHAR(MAX)` ist `maxLength` -1 (der Katalog meldet `max_length` -1). Gemessen ist der Wortlaut von d-migrate
 * 1.12.0 (`CREATE TABLE` mit `CONSTRAINT [pk_…] PRIMARY KEY ([Id])`, `ALTER TABLE … ADD [Email] NVARCHAR(4000)`,
 * `ALTER TABLE … WITH CHECK ADD CONSTRAINT …`, `CREATE INDEX …`); mehrere Spalten in `ADD` sind Setzung. Eine weitere
 * Klausel (`IDENTITY`, `WITH NOCHECK`, `CLUSTERED`) macht den Text nicht erkannt.
 */
internal class StructureParts(private val stream: TokenStream, private val parts: Parts) {
    private val columns = ColumnParts(stream, parts)
    private val constraints = ConstraintParts(stream, parts)

    /** Die Einträge einer Tabellenliste von `CREATE TABLE`, nach Art gesammelt. */
    private class Collected {
        val columns = ArrayList<SimColumn>()
        var key: List<String>? = null
        val unique = ArrayList<SimUniqueKey>()
        val foreign = ArrayList<SimForeignKey>()
        val checks = ArrayList<SimCheck>()
    }

    /** Hinter `CREATE`: `TABLE …` oder `[UNIQUE] INDEX …`. */
    fun create(): Statement {
        if (stream.acceptKeyword("TABLE")) return createTable()
        val unique = stream.acceptKeyword("UNIQUE")
        stream.keyword("INDEX")
        return createIndex(unique)
    }

    private fun createTable(): Statement {
        val table = parts.tableRef()
        stream.symbol('(')
        val collected = Collected()
        do {
            entry(collected)
        } while (stream.acceptSymbol(','))
        stream.symbol(')')
        if (collected.columns.isEmpty() || SimNames.firstDuplicate(collected.columns.map { it.name }) != null) {
            throw Reject(Unrecognized.DUPLICATE_COLUMN)
        }
        val elements = TableElements(collected.unique, collected.foreign, collected.checks)
        return Statement.CreateTable(table, collected.columns, collected.key, elements)
    }

    private fun entry(collected: Collected) {
        if (!constraints.isStart()) {
            val parsed = columns.column()
            collected.columns += parsed.column
            parsed.unique?.let { collected.unique += it }
            return
        }
        when (val level = constraints.tableLevel()) {
            is TableLevel.PrimaryKey -> {
                if (collected.key != null) throw unexpected()
                collected.key = level.columns
            }

            is TableLevel.Element -> when (val element = level.constraint) {
                is NewConstraint.Unique -> collected.unique += element.key
                is NewConstraint.Foreign -> collected.foreign += element.key
                is NewConstraint.Check -> collected.checks += element.check
                is NewConstraint.Default -> throw unexpected()
            }
        }
    }

    /** Hinter `ALTER`: `TABLE <Tabelle> [WITH CHECK] ADD …`. */
    fun alter(): Statement {
        stream.keyword("TABLE")
        val table = parts.tableRef()
        val withCheck = stream.acceptKeyword("WITH")
        if (withCheck) stream.keyword("CHECK")
        stream.keyword("ADD")
        if (stream.peek()?.isKeyword("CONSTRAINT") == true) {
            return Statement.AddConstraint(table, constraints.added())
        }
        if (withCheck) throw unexpected()
        return Statement.AddColumns(table, addedColumns())
    }

    /** Die Spalten hinter `ADD`; eine Eindeutigkeit an der Spalte gibt es dort nicht. */
    private fun addedColumns(): List<SimColumn> {
        val added = parts.list { columns.column() }
        val list = added.map { it.column }
        if (added.any { it.unique != null }) throw unexpected()
        if (SimNames.firstDuplicate(list.map { it.name }) != null) throw Reject(Unrecognized.DUPLICATE_COLUMN)
        return list
    }

    private fun createIndex(unique: Boolean): Statement {
        val name = parts.identifier()
        stream.keyword("ON")
        val table = parts.tableRef()
        val keys = constraints.columnList()
        val included = if (stream.acceptKeyword("INCLUDE")) constraints.columnList() else emptyList()
        if (SimNames.firstDuplicate(keys + included) != null) throw Reject(Unrecognized.DUPLICATE_COLUMN)
        return Statement.CreateIndex(table, SimIndex(name, keys, unique, included))
    }
}
