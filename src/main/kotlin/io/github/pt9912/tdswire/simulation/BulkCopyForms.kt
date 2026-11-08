package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.port.BulkProbeResult

/**
 * Die Probe der Zieltabelle, die `SqlBulkCopy` (`Microsoft.Data.SqlClient` 7.1.1, der Referenzclient) vor `INSERT BULK`
 * als SQL_BATCH stellt (`SPEC-036`, Form B): ein Text von rund 2 KB, der `@@TRANCOUNT`, den Namen der Tabelle in
 * `OBJECT_ID('[<Name>]')`, `FROM [<Name>]` und `sp_tablecollations_100 N'.[<Name>]'` und sonst feste Anweisungen trägt.
 * Gemessen an zwei Instanzen (SQL Server 2022 und 2025, Messreihe M-8 bis M-12, `bulk-merge-katalog`) für sechs Tabellen
 * ohne Schema; der Wortlaut war in allen Aufzeichnungen bis auf den Namen gleich. Erkannt ist der Text, wenn er nach
 * [CatalogForms.normalize] dem Wortlaut gleicht und der Name an allen drei Stellen derselbe ist; ein Name mit `]`
 * oder `'` und ein Name mit Schema sind nicht aufgezeichnet und nicht erkannt (der Text geht dann den üblichen Weg und
 * ist `TDS_UNSUPPORTED_SQL`).
 */
internal object BulkCopyForms {
    private const val NAME = "@NAME@"

    private const val TEMPLATE = """
        SELECT @@TRANCOUNT;

        DECLARE @Object_ID INT = OBJECT_ID('[@NAME@]');
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
        EXEC(N'SELECT ' + @Column_Names + N' FROM [@NAME@]');
        SET FMTONLY OFF;

        EXEC ..sp_tablecollations_100 N'.[@NAME@]';

        SELECT [Canonical_Column_Name], [Aliased_Column_Name]
        FROM #Column_Aliases
        ORDER BY [Canonical_Column_Id] ASC

        DROP TABLE #Column_Aliases
    """

    private val pattern: Regex = run {
        val parts = CatalogForms.normalize(TEMPLATE).split("[$NAME]").map { Regex.escape(it) }
        Regex(parts[0] + "\\[([^\\]']+)\\]" + parts[1] + "\\[\\1\\]" + parts[2] + "\\[\\1\\]" + parts[3])
    }

    /** Der Name der Tabelle, wenn [sql] die Probe ist, sonst `null`. */
    fun recognize(sql: String): String? = pattern.matchEntire(CatalogForms.normalize(sql))?.groupValues?.get(1)

    /**
     * Die Spalten der Tabelle, die [sql] nennt, in der Reihenfolge der Beschreibung; eine nicht beschriebene Tabelle
     * ist `TDS_SIM_UNKNOWN_OBJECT` (Setzung, die Antwort des Servers auf eine unbekannte Tabelle ist nicht
     * aufgezeichnet). Jeder andere Text ergibt `null`. `nvarchar(max)` meldet 4000 Zeichen, die Obergrenze von
     * `SPEC-003`.
     */
    fun probe(sql: String, catalog: () -> SimCatalog): BulkProbeResult? {
        val name = recognize(sql) ?: return null
        val found = guarded { catalog().find(null, name) }
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
