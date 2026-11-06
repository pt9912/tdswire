package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType

private fun text(name: String, nullable: Boolean = false) = ResultColumn(name, SqlType.UnicodeText, nullable)

private fun int(name: String, nullable: Boolean = false) = ResultColumn(name, SqlType.Int32, nullable)

private fun long(name: String, nullable: Boolean = false) = ResultColumn(name, SqlType.Int64, nullable)

private fun bool(name: String, nullable: Boolean = false) = ResultColumn(name, SqlType.Bool, nullable)

/** Woher die Zeilen einer Katalogantwort kommen: aus den Tabellen des Katalogs oder gar nicht (leer). */
internal enum class CatalogSource { TABLES, COLUMNS, PRIMARY_KEY, SCHEMA_NAME, EMPTY }

/**
 * Die Katalogabfragen, die d-migrate 1.12.0 (Treiber `driver-mssql-1.12.0`, mssql-jdbc 13.4.0) für `schema reverse`
 * (auch `--include-all`), `schema compare` und `schema migrate` stellt (`SPEC-036`, Form K). [template] ist der
 * Wortlaut aus der Klasse `MssqlMetadataQueries` beziehungsweise `MssqlRoutineQueries` des Treibers, gelesen aus dem
 * Bytecode (`javap`), mit `?` für einen Parameter; [CatalogForms] vergleicht den Text einer Anfrage nach
 * [CatalogForms.normalize] mit ihm. [columns] sind die Spalten der Antwort in der Reihenfolge der Abfrage, [source]
 * sagt, woher die Zeilen kommen.
 * Die Typen der Spalten sind die nächsten von [SqlType] (`smallint`, `tinyint` und `char` des Servers heißen hier
 * `Int32` und `UnicodeText`, Setzung).
 */
internal enum class CatalogForm(val template: String, val source: CatalogSource, val columns: List<ResultColumn>) {
    SCHEMA_NAME(
        "SELECT SCHEMA_NAME() AS schema_name",
        CatalogSource.SCHEMA_NAME,
        listOf(text("schema_name")),
    ),
    TABLES(
        """
        SELECT t.name AS table_name, s.name AS schema_name
        FROM sys.tables t
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        WHERE s.name = ? AND t.is_ms_shipped = 0
        ORDER BY t.name
        """,
        CatalogSource.TABLES,
        listOf(text("table_name"), text("schema_name")),
    ),
    COLUMNS(
        """
        SELECT c.name AS column_name, ty.name AS type_name, c.max_length, c.precision, c.scale,
               c.is_nullable, c.is_identity, c.is_computed, c.column_id,
               dc.definition AS default_definition,
               ic.seed_value, ic.increment_value,
               cc.definition AS computed_definition, cc.is_persisted AS computed_persisted
        FROM sys.columns c
        JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        LEFT JOIN sys.default_constraints dc ON dc.object_id = c.default_object_id
        LEFT JOIN sys.identity_columns ic
            ON ic.object_id = c.object_id AND ic.column_id = c.column_id
        LEFT JOIN sys.computed_columns cc
            ON cc.object_id = c.object_id AND cc.column_id = c.column_id
        WHERE c.object_id = OBJECT_ID(?)
        ORDER BY c.column_id
        """,
        CatalogSource.COLUMNS,
        listOf(
            text("column_name"), text("type_name"), int("max_length"), int("precision"), int("scale"),
            bool("is_nullable"), bool("is_identity"), bool("is_computed"), int("column_id"),
            text("default_definition", nullable = true), long("seed_value", nullable = true),
            long("increment_value", nullable = true), text("computed_definition", nullable = true),
            bool("computed_persisted", nullable = true),
        ),
    ),
    PRIMARY_KEY(
        """
        SELECT col.name AS column_name
        FROM sys.key_constraints kc
        JOIN sys.index_columns ic
            ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
        JOIN sys.columns col
            ON col.object_id = ic.object_id AND col.column_id = ic.column_id
        WHERE kc.parent_object_id = OBJECT_ID(?) AND kc.type = 'PK'
        ORDER BY ic.key_ordinal
        """,
        CatalogSource.PRIMARY_KEY,
        listOf(text("column_name")),
    ),
    UNIQUE_KEYS(
        """
        SELECT kc.name AS constraint_name, col.name AS column_name
        FROM sys.key_constraints kc
        JOIN sys.index_columns ic
            ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
        JOIN sys.columns col
            ON col.object_id = ic.object_id AND col.column_id = ic.column_id
        WHERE kc.parent_object_id = OBJECT_ID(?) AND kc.type = 'UQ'
        ORDER BY kc.name, ic.key_ordinal
        """,
        CatalogSource.EMPTY,
        listOf(text("constraint_name"), text("column_name")),
    ),
    FOREIGN_KEYS(
        """
        SELECT fk.name AS constraint_name, pc.name AS column_name,
               rt.name AS referenced_table, rc.name AS referenced_column,
               fk.delete_referential_action_desc, fk.update_referential_action_desc,
               fkc.constraint_column_id
        FROM sys.foreign_keys fk
        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
        JOIN sys.columns pc
            ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
        JOIN sys.columns rc
            ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
        JOIN sys.tables rt ON rt.object_id = fkc.referenced_object_id
        WHERE fk.parent_object_id = OBJECT_ID(?)
        ORDER BY fk.name, fkc.constraint_column_id
        """,
        CatalogSource.EMPTY,
        listOf(
            text("constraint_name"),
            text("column_name"),
            text("referenced_table"),
            text("referenced_column"),
            text("delete_referential_action_desc"),
            text("update_referential_action_desc"),
            int("constraint_column_id"),
        ),
    ),
    INDEXES(
        """
        SELECT i.name AS index_name, i.is_unique, i.has_filter, i.filter_definition, i.type,
               col.name AS column_name, ic.key_ordinal, ic.is_descending_key, ic.is_included_column
        FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns col ON col.object_id = ic.object_id AND col.column_id = ic.column_id
        WHERE i.object_id = OBJECT_ID(?) AND i.type > 0
          AND i.is_primary_key = 0 AND i.is_hypothetical = 0
        ORDER BY i.name, ic.key_ordinal, ic.index_column_id
        """,
        CatalogSource.EMPTY,
        listOf(
            text("index_name", nullable = true), bool("is_unique"), bool("has_filter"),
            text("filter_definition", nullable = true), int("type"), text("column_name"), int("key_ordinal"),
            bool("is_descending_key"), bool("is_included_column"),
        ),
    ),
    FULLTEXT_COLUMNS(
        """
        SELECT col.name AS column_name
        FROM sys.fulltext_index_columns ftc
        JOIN sys.columns col ON col.object_id = ftc.object_id AND col.column_id = ftc.column_id
        WHERE ftc.object_id = OBJECT_ID(?)
        ORDER BY ftc.column_id
        """,
        CatalogSource.EMPTY,
        listOf(text("column_name")),
    ),
    PARTITIONING(
        """
        SELECT pf.name AS function_name, ps.name AS scheme_name, pf.function_id,
               pf.boundary_value_on_right, col.name AS column_name
        FROM sys.tables t
        JOIN sys.indexes i ON i.object_id = t.object_id AND i.index_id IN (0, 1)
        JOIN sys.partition_schemes ps ON ps.data_space_id = i.data_space_id
        JOIN sys.partition_functions pf ON pf.function_id = ps.function_id
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            AND ic.partition_ordinal = 1
        JOIN sys.columns col ON col.object_id = ic.object_id AND col.column_id = ic.column_id
        WHERE t.object_id = OBJECT_ID(?)
        """,
        CatalogSource.EMPTY,
        listOf(
            text("function_name"),
            text("scheme_name"),
            int("function_id"),
            bool("boundary_value_on_right"),
            text("column_name"),
        ),
    ),
    CHECKS(
        """
        SELECT cc.name AS constraint_name, cc.definition
        FROM sys.check_constraints cc
        WHERE cc.parent_object_id = OBJECT_ID(?) AND cc.is_ms_shipped = 0
        ORDER BY cc.name
        """,
        CatalogSource.EMPTY,
        listOf(text("constraint_name"), text("definition", nullable = true)),
    ),
    SEQUENCES(
        """
        SELECT seq.name AS sequence_name, ty.name AS type_name,
               CAST(seq.start_value AS bigint) AS start_value,
               CAST(seq.increment AS bigint) AS increment_value,
               CAST(seq.minimum_value AS bigint) AS minimum_value,
               CAST(seq.maximum_value AS bigint) AS maximum_value,
               seq.is_cycling,
               seq.is_cached,
               seq.cache_size
        FROM sys.sequences seq
        JOIN sys.schemas s ON s.schema_id = seq.schema_id
        JOIN sys.types ty ON ty.user_type_id = seq.user_type_id
        WHERE s.name = ?
        ORDER BY seq.name
        """,
        CatalogSource.EMPTY,
        listOf(
            text("sequence_name"), text("type_name"), long("start_value"), long("increment_value"),
            long("minimum_value"), long("maximum_value"), bool("is_cycling"), bool("is_cached"),
            int("cache_size", nullable = true),
        ),
    ),
    VIEWS(
        """
        SELECT v.name AS view_name, m.definition
        FROM sys.views v
        JOIN sys.sql_modules m ON m.object_id = v.object_id
        WHERE v.schema_id = SCHEMA_ID(?) AND v.is_ms_shipped = 0
        ORDER BY v.name
        """,
        CatalogSource.EMPTY,
        listOf(text("view_name"), text("definition", nullable = true)),
    ),
    VIEW_COLUMNS(
        """
        SELECT v.name AS view_name, c.name AS column_name, t.name AS type_name
        FROM sys.views v
        JOIN sys.columns c ON c.object_id = v.object_id
        JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE v.schema_id = SCHEMA_ID(?) AND v.is_ms_shipped = 0
        ORDER BY v.name, c.column_id
        """,
        CatalogSource.EMPTY,
        listOf(text("view_name"), text("column_name"), text("type_name")),
    ),
    IDENTITY_FIRST(
        """
        SELECT TOP 1 c.name AS column_name,
               CAST(c.seed_value AS bigint) AS seed_value,
               CAST(c.increment_value AS bigint) AS increment_value,
               CAST(c.last_value AS bigint) AS last_value
        FROM sys.identity_columns c
        WHERE c.object_id = OBJECT_ID(?)
        """,
        CatalogSource.EMPTY,
        listOf(text("column_name"), long("seed_value"), long("increment_value"), long("last_value", nullable = true)),
    ),
    COMPUTED_COLUMNS(
        """
        SELECT c.name AS column_name
        FROM sys.computed_columns c
        WHERE c.object_id = OBJECT_ID(?)
        """,
        CatalogSource.EMPTY,
        listOf(text("column_name")),
    ),
    IDENTITY_COLUMNS(
        """
        SELECT c.name AS column_name
        FROM sys.identity_columns c
        WHERE c.object_id = OBJECT_ID(?)
        """,
        CatalogSource.EMPTY,
        listOf(text("column_name")),
    ),
    OTHER_OBJECTS(
        """
        SELECT o.type AS object_type, o.name AS object_name
        FROM sys.objects o
        LEFT JOIN sys.sql_modules m ON m.object_id = o.object_id
        WHERE o.schema_id = SCHEMA_ID(?) AND o.is_ms_shipped = 0
          AND o.type IN ('P', 'PC', 'FN', 'FS', 'FT', 'IF', 'TF', 'TR', 'TA')
          AND m.definition IS NULL
        ORDER BY o.type, o.name
        """,
        CatalogSource.EMPTY,
        listOf(text("object_type"), text("object_name")),
    ),
    ROUTINE_PARAMETERS(
        """
        SELECT o.name AS routine_name, p.name AS param_name, t.name AS type_name,
               p.is_output, p.is_readonly, p.parameter_id, p.precision, p.scale
        FROM sys.objects o
        JOIN sys.parameters p ON p.object_id = o.object_id
        JOIN sys.types t ON t.user_type_id = p.user_type_id
        WHERE o.schema_id = SCHEMA_ID(?) AND o.is_ms_shipped = 0
          AND o.type IN ('P', 'FN', 'IF', 'TF')
        ORDER BY o.name, p.parameter_id
        """,
        CatalogSource.EMPTY,
        listOf(
            text("routine_name"),
            text("param_name", nullable = true),
            text("type_name"),
            bool("is_output"),
            bool("is_readonly"),
            int("parameter_id"),
            int("precision"),
            int("scale"),
        ),
    ),
    ROUTINE_DEPENDENCIES(
        """
        SELECT o.name AS referencing_name,
               COALESCE(ro.name, d.referenced_entity_name) AS referenced_name,
               COALESCE(rs.name, d.referenced_schema_name) AS referenced_schema,
               ro.type AS referenced_type
        FROM sys.sql_expression_dependencies d
        JOIN sys.objects o ON o.object_id = d.referencing_id
        JOIN sys.schemas s ON s.schema_id = o.schema_id
        LEFT JOIN sys.objects ro ON ro.object_id = d.referenced_id
        LEFT JOIN sys.schemas rs ON rs.schema_id = ro.schema_id
        WHERE s.name = ? AND o.is_ms_shipped = 0
          AND o.type IN ('P', 'FN', 'IF', 'TF', 'TR')
        ORDER BY o.name, referenced_name
        """,
        CatalogSource.EMPTY,
        listOf(
            text("referencing_name"),
            text("referenced_name", nullable = true),
            text("referenced_schema", nullable = true),
            text("referenced_type", nullable = true),
        ),
    ),
    ROUTINES(
        """
        SELECT o.type AS object_type, o.name AS object_name, m.definition,
               OBJECT_NAME(o.parent_object_id) AS parent_name,
               OBJECTPROPERTY(o.object_id, 'ExecIsInsertTrigger') AS is_insert,
               OBJECTPROPERTY(o.object_id, 'ExecIsUpdateTrigger') AS is_update,
               OBJECTPROPERTY(o.object_id, 'ExecIsDeleteTrigger') AS is_delete,
               OBJECTPROPERTY(o.object_id, 'ExecIsInsteadOfTrigger') AS is_instead_of
        FROM sys.objects o
        JOIN sys.sql_modules m ON m.object_id = o.object_id
        WHERE o.schema_id = SCHEMA_ID(?) AND o.is_ms_shipped = 0
          AND o.type IN ('P', 'FN', 'IF', 'TF', 'TR')
        ORDER BY o.type, o.name
        """,
        CatalogSource.EMPTY,
        listOf(
            text("object_type"),
            text("object_name"),
            text("definition", nullable = true),
            text("parent_name", nullable = true),
            int("is_insert", nullable = true),
            int("is_update", nullable = true),
            int("is_delete", nullable = true),
            int("is_instead_of", nullable = true),
        ),
    ),
}

/**
 * Erkennung der Katalogabfragen (`SPEC-036`, Form K): der Text gilt als erkannt, wenn er nach [normalize] gleich dem
 * Wortlaut genau einer [CatalogForm] ist. Das ist ein Textvergleich, keine Auswertung von SQL; ein Text mit anderem
 * Wortlaut, Kommentar oder anderer Groß-/Kleinschreibung ist nicht erkannt.
 */
internal object CatalogForms {
    private val PARAMETER = Regex(" ?(?:@P[0-9]+|\\?) ?")
    private val SPACE = Regex("[ \t\r\n]+")

    private val byText: Map<String, CatalogForm> = CatalogForm.entries.associateBy { normalize(it.template) }

    /**
     * Leerraum (Leerzeichen, Tab, CR, LF) in einem Lauf zu einem Leerzeichen, Anfang und Ende ohne Leerraum, ein
     * abschließendes `;` entfernt und ein Parameter `@P<n>` oder `?` samt dem Leerzeichen davor und dahinter zu `?`.
     * mssql-jdbc setzt `@P<n>` mit je einem Leerzeichen davor und dahinter für das `?` ein (gemessen:
     * `OBJECT_ID( @P0 )`).
     */
    fun normalize(sql: String): String =
        sql.trim(' ', '\t', '\r', '\n').removeSuffix(";").replace(SPACE, " ").trimEnd().replace(PARAMETER, "?")

    fun recognize(sql: String): CatalogForm? = byText[normalize(sql)]
}
