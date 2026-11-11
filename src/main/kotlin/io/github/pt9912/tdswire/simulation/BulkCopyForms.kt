package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.port.BulkProbeResult

/**
 * Die Probe der Zieltabelle, die `SqlBulkCopy` (`Microsoft.Data.SqlClient` 7.1.1, der Referenzclient) vor `INSERT BULK`
 * als SQL_BATCH stellt (`SPEC-036`, Form B): ein Text von rund 2 KB, der `@@TRANCOUNT`, den Namen der Tabelle in
 * `OBJECT_ID('<Name>')`, `FROM <Name>` und `sp_tablecollations_100 N'<Kollationsname>'` und sonst feste Anweisungen
 * trägt. Gemessen an zwei Instanzen (SQL Server 2022 und 2025, Messreihe M-8 bis M-12, `bulk-merge-katalog`, und
 * M-33 bis M-35, `bulk-name-schema` und `bulk-name-sonderzeichen`): der Client zerlegt `DestinationTableName` und
 * setzt jeden Teil in eckige Klammern, `]` als `]]`; der Name `[<Schema>].[<Tabelle>]` oder `[<Tabelle>]` steht in
 * `OBJECT_ID` und `FROM` gleich, ein `'` ist an allen drei Stellen als `''` geschrieben (sie stehen in Literalen); der
 * Kollationsname ist `.[<Tabelle>]` ohne und `[<Schema>].[<Tabelle>]` mit Schema. Erkannt ist der Text, wenn er bis auf
 * Leerraum dem Wortlaut gleicht und die Namen an den drei Stellen zusammenpassen; die Namen innerhalb der Klammern
 * bleiben unverändert (auch Leerzeichenfolgen). Der Leerraum ist Setzung, nicht aufgezeichnet: eine beliebige Folge aus
 * Leerzeichen, Tabulator und Zeilenumbruch gilt an jeder Lücke des Wortlauts (auch innerhalb seiner Literale), und
 * Leerraum sowie ein `;` am Ende des Textes sind erlaubt. Die 128-Zeichen-Grenze des Servers für Namen ist nicht
 * gemessen; die Erkennung prüft die Länge nicht. Ein Name mit drei Teilen (Datenbank, Schema, Tabelle) ist gemessen,
 * aber nicht erkannt: der Text geht dann den üblichen Weg und ist `TDS_UNSUPPORTED_SQL`.
 */
internal object BulkCopyForms {
    private const val NAME = "@NAME@"
    private const val COLLATION = "@COLLATION@"

    private const val TEMPLATE = """
        SELECT @@TRANCOUNT;

        DECLARE @Object_ID INT = OBJECT_ID('@NAME@');
        DECLARE @Column_Name_Query_SELECT NVARCHAR(MAX);
        DECLARE @Column_Name_Query_FILTER NVARCHAR(MAX);
        DECLARE @Column_Name_Query_SORT NVARCHAR(MAX);
        DECLARE @Column_Name_Query NVARCHAR(MAX);
        DECLARE @Column_Names NVARCHAR(MAX) = NULL;
        DECLARE @Has_Sys_All_Columns_Permissions INT = HAS_PERMS_BY_NAME('.[sys].[all_columns]', 'OBJECT', 'SELECT');

        CREATE TABLE #Column_Aliases
        (
            [Canonical_Column_Name] SYSNAME,
            [Canonical_Column_Id] INT,
            [Aliased_Column_Name] SYSNAME
        )

        IF CAST(SERVERPROPERTY('EngineEdition') AS INT) = 6
        BEGIN
            SET @Column_Name_Query_SELECT = N'SELECT @Column_Names = STRING_AGG(CAST(QUOTENAME([name]) AS NVARCHAR(MAX)), '', '') WITHIN GROUP (ORDER BY [column_id] ASC)';
            SET @Column_Name_Query_SORT = N'';
        END
        ELSE
        BEGIN
            SET @Column_Name_Query_SELECT = 'SELECT @Column_Names = COALESCE(@Column_Names + '', '', '''') + QUOTENAME([name])';
            SET @Column_Name_Query_SORT = N'ORDER BY [column_id] ASC';
        END

        IF @Has_Sys_All_Columns_Permissions = 1
        BEGIN
            IF EXISTS (SELECT TOP 1 * FROM .[sys].[all_columns] WHERE [object_id] = OBJECT_ID('.[sys].[all_columns]') AND [name] = 'graph_type')
            BEGIN
                SET @Column_Name_Query_FILTER = N'WHERE [object_id] = @Object_ID AND COALESCE([graph_type], 0) NOT IN (1, 3, 4, 6, 7)';

            END
            ELSE
            BEGIN
                SET @Column_Name_Query_FILTER = N'WHERE [object_id] = @Object_ID';
            END
            SET @Column_Name_Query = @Column_Name_Query_SELECT + ' FROM .[sys].[all_columns] ' + @Column_Name_Query_FILTER + ' ' + @Column_Name_Query_SORT + ';'

            EXEC sp_executesql @Column_Name_Query, N'@Object_ID INT, @Column_Names NVARCHAR(MAX) OUTPUT', @Object_ID = @Object_ID, @Column_Names = @Column_Names OUTPUT;

        END

        SELECT @Column_Names = COALESCE(@Column_Names, '*');

        SET FMTONLY ON;
        EXEC(N'SELECT ' + @Column_Names + N' FROM @NAME@');
        SET FMTONLY OFF;

        EXEC ..sp_tablecollations_100 N'@COLLATION@';

        SELECT [Canonical_Column_Name], [Aliased_Column_Name]
        FROM #Column_Aliases
        ORDER BY [Canonical_Column_Id] ASC

        DROP TABLE #Column_Aliases
    """

    /**
     * Ein Teil des Namens in Klammern; `]]` steht für `]`, `''` für `'`; die Gruppe ist der Teil im Text. Die
     * Wiederholung ist besitzend (`++`): sie probiert keine Rückschritte und rekursiert nicht je Zeichen, ein Name von
     * einer Million Zeichen wird ohne `StackOverflowError` erkannt (`BulkCopyFormsTest`).
     */
    private const val PART = "\\[((?:[^\\]']++|\\]\\]|'')++)\\]"
    private const val BLANK = "[ \t\r\n]+"

    /** Der Wortlaut ohne Leerraum an den Enden, mit Leerraum als `[ \t\r\n]+`, um die Namen eingesetzt. */
    private fun pattern(name: String, from: String, collation: String): Regex {
        val template = CatalogForms.normalize(TEMPLATE)
        val pieces = template.split(NAME, COLLATION).map { piece ->
            piece.split(" ").joinToString(BLANK) { Regex.escape(it) }
        }
        return Regex(pieces.first() + name + pieces[1] + from + pieces[2] + collation + pieces.last())
    }

    private val withoutSchema: Regex = pattern(PART, "\\[\\1\\]", "\\.\\[\\1\\]")
    private val withSchema: Regex = pattern(PART + "\\." + PART, "\\[\\1\\]\\.\\[\\2\\]", "\\[\\1\\]\\.\\[\\2\\]")

    private fun unescape(part: String): String = part.replace("]]", "]").replace("''", "'")

    /**
     * Die Tabelle, wenn [sql] die Probe ist, sonst `null`; ohne Schema ist [TableRef.schema] `null`. Die Namen sind in
     * der Schreibung des Katalogs (ohne die Auszeichnung mit Klammern und `''`).
     */
    fun recognize(sql: String): TableRef? {
        val text = sql.trim(' ', '\t', '\r', '\n').removeSuffix(";").trimEnd(' ', '\t', '\r', '\n')
        withoutSchema.matchEntire(text)?.let { return TableRef(null, unescape(it.groupValues[1])) }
        return withSchema.matchEntire(text)?.let { TableRef(unescape(it.groupValues[1]), unescape(it.groupValues[2])) }
    }

    /**
     * Die Spalten der Tabelle, die [sql] nennt, in der Reihenfolge der Beschreibung; eine nicht beschriebene Tabelle
     * ist `TDS_SIM_UNKNOWN_OBJECT` (Setzung, die Antwort des Servers auf eine unbekannte Tabelle ist nicht
     * aufgezeichnet). Jeder andere Text ergibt `null`. `nvarchar(max)` meldet 4000 Zeichen, die Obergrenze von
     * `SPEC-003`.
     */
    fun probe(sql: String, catalog: () -> SimCatalog): BulkProbeResult? {
        val target = recognize(sql) ?: return null
        val found = guarded { catalog().find(target.schema, target.name) }
        val table = found.getOrNull()
        return when {
            found.isFailure -> BulkProbeResult.Rejected(SimCodes.BACKEND_ERROR, "Katalog nicht lieferbar")
            table == null -> BulkProbeResult.Rejected(SimCodes.UNKNOWN_OBJECT, "Tabelle nicht beschrieben")
            else -> BulkProbeResult.Ready(table.columns.map(::column))
        }
    }

    private fun column(column: SimColumn): BulkProbeColumn {
        val chars = if (column.maxLength == UNBOUNDED_TEXT) MAX_TEXT_LENGTH else column.maxLength
        return BulkProbeColumn(ResultColumn(column.name, column.type, column.nullable), chars)
    }
}
