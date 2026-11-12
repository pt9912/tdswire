package io.github.pt9912.tdswire.simulation

/**
 * Der Text, mit dem `schema migrate --execute` von d-migrate 1.12.0 den Standardwert einer vorhandenen Spalte entfernt,
 * bevor es den neuen anlegt (`SPEC-036`, Form D7): `DECLARE @df sysname, @sql nvarchar(max);`, eine `SELECT`-Zuweisung
 * des Namens aus `sys.default_constraints` und `sys.columns`, dann `IF @df IS NOT NULL BEGIN … EXEC sp_executesql @sql;
 * END;`.
 * Der Wortlaut ist der Mitschnitt (Lauf `schema` des Vergleichslaufs, Schritt s12) mit der Tabelle in
 * `OBJECT_ID('<Tabelle>')` und `[<Tabelle>]` und der Spalte in `c.name = '<Spalte>'`. Erkannt ist der Text, wenn er bis
 * auf Leerraum dem Wortlaut gleicht und die Tabelle an beiden Stellen gleich heißt. Der Leerraum ist Setzung, nicht
 * aufgezeichnet: eine beliebige Folge aus Leerzeichen, Tabulator und Zeilenumbruch gilt an jeder Lücke des Wortlauts,
 * und ein `;` sowie Leerraum am Ende sind erlaubt. Die Tabelle besteht aus Buchstaben, Ziffern, `_`, `#` und `$` (die
 * Quotierung anderer Namen im Text ist nicht aufgezeichnet, der Text mit einem solchen Namen ist nicht erkannt); die
 * Spalte ist ein Textliteral, `''` steht für `'`. Der Text nennt kein Schema.
 */
internal object DefaultDropForms {
    private const val TABLE_FIRST = "@TABLE1@"
    private const val COLUMN = "@COLUMN@"
    private const val TABLE_SECOND = "@TABLE2@"

    private const val TEMPLATE = """
        DECLARE @df sysname, @sql nvarchar(max);
        SELECT @df = dc.name FROM sys.default_constraints dc
            JOIN sys.columns c ON c.object_id = dc.parent_object_id
                AND c.column_id = dc.parent_column_id
            WHERE dc.parent_object_id = OBJECT_ID('@TABLE1@')
                AND c.name = '@COLUMN@';
        IF @df IS NOT NULL
        BEGIN
            SET @sql = 'ALTER TABLE [@TABLE2@] DROP CONSTRAINT ' + QUOTENAME(@df);
            EXEC sp_executesql @sql;
        END;
    """

    /** Die Wiederholungen sind besitzend (`++`): sie probieren keine Rückschritte und rekursieren nicht je Zeichen. */
    private const val TABLE_NAME = """([\p{L}\p{Nd}_#$]++)"""
    private const val COLUMN_NAME = """((?:[^']++|'')++)"""
    private const val BLANK = "[ \t\r\n]+"

    private val pattern: Regex = run {
        val template = CatalogForms.normalize(TEMPLATE)
        val pieces = template.split(TABLE_FIRST, COLUMN, TABLE_SECOND).map { piece ->
            piece.split(" ").joinToString(BLANK) { Regex.escape(it) }
        }
        Regex(pieces[0] + TABLE_NAME + pieces[1] + COLUMN_NAME + pieces[2] + "\\1" + pieces[3])
    }

    /** Die Anweisung, wenn [sql] der Text ist, sonst `null`; die Namen stehen in der Schreibung des Textes. */
    fun recognize(sql: String): Statement.DropDefault? {
        val text = sql.trim(' ', '\t', '\r', '\n').removeSuffix(";").trimEnd(' ', '\t', '\r', '\n')
        val found = pattern.matchEntire(text) ?: return null
        return Statement.DropDefault(TableRef(null, found.groupValues[1]), found.groupValues[2].replace("''", "'"))
    }
}
