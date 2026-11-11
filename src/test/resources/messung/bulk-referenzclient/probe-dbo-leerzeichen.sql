SELECT @@TRANCOUNT;

DECLARE @Object_ID INT = OBJECT_ID('[dbo].[bulk x]');
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
EXEC(N'SELECT ' + @Column_Names + N' FROM [dbo].[bulk x]');
SET FMTONLY OFF;

EXEC ..sp_tablecollations_100 N'[dbo].[bulk x]';

SELECT [Canonical_Column_Name], [Aliased_Column_Name]
FROM #Column_Aliases
ORDER BY [Canonical_Column_Id] ASC

DROP TABLE #Column_Aliases