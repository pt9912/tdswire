package io.github.pt9912.tdswire.simulation

/**
 * Die Texte, die d-migrate 1.12.0 (Image `ghcr.io/pt9912/d-migrate`, Digest `sha256:308f2775…6491`, mssql-jdbc 13.4.0)
 * bei `schema reverse` (auch `--include-all`), `schema compare` und `schema migrate --execute` an den Testserver
 * sendet: Mitschnitt vom 2026-11-06 aus dem Anfragetext des Backends (`QueryRequest.sql`), Zeilenumbrüche und
 * Leerzeichen wie gesendet (jede Zeile eines Textes ein String; ein Parameter steht als `@P0` mit Leerzeichen
 * dabei). Eindeutige Texte der Läufe; die Messzeilen stehen im Messprotokoll
 * `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md`. Nicht darunter sind die Abfragen, die nur `data import`
 * stellt (`sys.identity_columns`, `sys.computed_columns`, `SELECT MAX`): sie stammen aus dem Bytecode des Treibers.
 */
private fun wire(vararg lines: String): String = lines.joinToString("\n")

/** Die eindeutigen Katalogabfragen des Mitschnitts, in der Reihenfolge ihres ersten Auftretens. */
val dMigrateCatalogTexts: List<String> = listOf(
    wire(
        "SELECT SCHEMA_NAME() AS schema_name",
    ),
    wire(
        "SELECT t.name AS table_name, s.name AS schema_name",
        "FROM sys.tables t",
        "JOIN sys.schemas s ON s.schema_id = t.schema_id",
        "WHERE s.name =  @P0 AND t.is_ms_shipped = 0",
        "ORDER BY t.name",
    ),
    wire(
        "SELECT c.name AS column_name, ty.name AS type_name, c.max_length, c.precision, c.scale,",
        "       c.is_nullable, c.is_identity, c.is_computed, c.column_id,",
        "       dc.definition AS default_definition,",
        "       ic.seed_value, ic.increment_value,",
        "       cc.definition AS computed_definition, cc.is_persisted AS computed_persisted",
        "FROM sys.columns c",
        "JOIN sys.types ty ON ty.user_type_id = c.user_type_id",
        "LEFT JOIN sys.default_constraints dc ON dc.object_id = c.default_object_id",
        "LEFT JOIN sys.identity_columns ic",
        "    ON ic.object_id = c.object_id AND ic.column_id = c.column_id",
        "LEFT JOIN sys.computed_columns cc",
        "    ON cc.object_id = c.object_id AND cc.column_id = c.column_id",
        "WHERE c.object_id = OBJECT_ID( @P0 )",
        "ORDER BY c.column_id",
    ),
    wire(
        "SELECT pf.name AS function_name, ps.name AS scheme_name, pf.function_id,",
        "       pf.boundary_value_on_right, col.name AS column_name",
        "FROM sys.tables t",
        "JOIN sys.indexes i ON i.object_id = t.object_id AND i.index_id IN (0, 1)",
        "JOIN sys.partition_schemes ps ON ps.data_space_id = i.data_space_id",
        "JOIN sys.partition_functions pf ON pf.function_id = ps.function_id",
        "JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id",
        "    AND ic.partition_ordinal = 1",
        "JOIN sys.columns col ON col.object_id = ic.object_id AND col.column_id = ic.column_id",
        "WHERE t.object_id = OBJECT_ID( @P0 )",
    ),
    wire(
        "SELECT col.name AS column_name",
        "FROM sys.key_constraints kc",
        "JOIN sys.index_columns ic",
        "    ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id",
        "JOIN sys.columns col",
        "    ON col.object_id = ic.object_id AND col.column_id = ic.column_id",
        "WHERE kc.parent_object_id = OBJECT_ID( @P0 ) AND kc.type = 'PK'",
        "ORDER BY ic.key_ordinal",
    ),
    wire(
        "SELECT fk.name AS constraint_name, pc.name AS column_name,",
        "       rt.name AS referenced_table, rc.name AS referenced_column,",
        "       fk.delete_referential_action_desc, fk.update_referential_action_desc,",
        "       fkc.constraint_column_id",
        "FROM sys.foreign_keys fk",
        "JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id",
        "JOIN sys.columns pc",
        "    ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id",
        "JOIN sys.columns rc",
        "    ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id",
        "JOIN sys.tables rt ON rt.object_id = fkc.referenced_object_id",
        "WHERE fk.parent_object_id = OBJECT_ID( @P0 )",
        "ORDER BY fk.name, fkc.constraint_column_id",
    ),
    wire(
        "SELECT i.name AS index_name, i.is_unique, i.has_filter, i.filter_definition, i.type,",
        "       col.name AS column_name, ic.key_ordinal, ic.is_descending_key, ic.is_included_column",
        "FROM sys.indexes i",
        "JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id",
        "JOIN sys.columns col ON col.object_id = ic.object_id AND col.column_id = ic.column_id",
        "WHERE i.object_id = OBJECT_ID( @P0 ) AND i.type > 0",
        "  AND i.is_primary_key = 0 AND i.is_hypothetical = 0",
        "ORDER BY i.name, ic.key_ordinal, ic.index_column_id",
    ),
    wire(
        "SELECT cc.name AS constraint_name, cc.definition",
        "FROM sys.check_constraints cc",
        "WHERE cc.parent_object_id = OBJECT_ID( @P0 ) AND cc.is_ms_shipped = 0",
        "ORDER BY cc.name",
    ),
    wire(
        "SELECT kc.name AS constraint_name, col.name AS column_name",
        "FROM sys.key_constraints kc",
        "JOIN sys.index_columns ic",
        "    ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id",
        "JOIN sys.columns col",
        "    ON col.object_id = ic.object_id AND col.column_id = ic.column_id",
        "WHERE kc.parent_object_id = OBJECT_ID( @P0 ) AND kc.type = 'UQ'",
        "ORDER BY kc.name, ic.key_ordinal",
    ),
    wire(
        "SELECT col.name AS column_name",
        "FROM sys.fulltext_index_columns ftc",
        "JOIN sys.columns col ON col.object_id = ftc.object_id AND col.column_id = ftc.column_id",
        "WHERE ftc.object_id = OBJECT_ID( @P0 )",
        "ORDER BY ftc.column_id",
    ),
    wire(
        "SELECT seq.name AS sequence_name, ty.name AS type_name,",
        "       CAST(seq.start_value AS bigint) AS start_value,",
        "       CAST(seq.increment AS bigint) AS increment_value,",
        "       CAST(seq.minimum_value AS bigint) AS minimum_value,",
        "       CAST(seq.maximum_value AS bigint) AS maximum_value,",
        "       seq.is_cycling,",
        "       seq.is_cached,",
        "       seq.cache_size",
        "FROM sys.sequences seq",
        "JOIN sys.schemas s ON s.schema_id = seq.schema_id",
        "JOIN sys.types ty ON ty.user_type_id = seq.user_type_id",
        "WHERE s.name =  @P0 ",
        "ORDER BY seq.name",
    ),
    wire(
        "SELECT o.type AS object_type, o.name AS object_name, m.definition,",
        "       OBJECT_NAME(o.parent_object_id) AS parent_name,",
        "       OBJECTPROPERTY(o.object_id, 'ExecIsInsertTrigger') AS is_insert,",
        "       OBJECTPROPERTY(o.object_id, 'ExecIsUpdateTrigger') AS is_update,",
        "       OBJECTPROPERTY(o.object_id, 'ExecIsDeleteTrigger') AS is_delete,",
        "       OBJECTPROPERTY(o.object_id, 'ExecIsInsteadOfTrigger') AS is_instead_of",
        "FROM sys.objects o",
        "JOIN sys.sql_modules m ON m.object_id = o.object_id",
        "WHERE o.schema_id = SCHEMA_ID( @P0 ) AND o.is_ms_shipped = 0",
        "  AND o.type IN ('P', 'FN', 'IF', 'TF', 'TR')",
        "ORDER BY o.type, o.name",
    ),
    wire(
        "SELECT o.name AS routine_name, p.name AS param_name, t.name AS type_name,",
        "       p.is_output, p.is_readonly, p.parameter_id, p.precision, p.scale",
        "FROM sys.objects o",
        "JOIN sys.parameters p ON p.object_id = o.object_id",
        "JOIN sys.types t ON t.user_type_id = p.user_type_id",
        "WHERE o.schema_id = SCHEMA_ID( @P0 ) AND o.is_ms_shipped = 0",
        "  AND o.type IN ('P', 'FN', 'IF', 'TF')",
        "ORDER BY o.name, p.parameter_id",
    ),
    wire(
        "SELECT o.name AS referencing_name,",
        "       COALESCE(ro.name, d.referenced_entity_name) AS referenced_name,",
        "       COALESCE(rs.name, d.referenced_schema_name) AS referenced_schema,",
        "       ro.type AS referenced_type",
        "FROM sys.sql_expression_dependencies d",
        "JOIN sys.objects o ON o.object_id = d.referencing_id",
        "JOIN sys.schemas s ON s.schema_id = o.schema_id",
        "LEFT JOIN sys.objects ro ON ro.object_id = d.referenced_id",
        "LEFT JOIN sys.schemas rs ON rs.schema_id = ro.schema_id",
        "WHERE s.name =  @P0 AND o.is_ms_shipped = 0",
        "  AND o.type IN ('P', 'FN', 'IF', 'TF', 'TR')",
        "ORDER BY o.name, referenced_name",
    ),
    wire(
        "SELECT o.type AS object_type, o.name AS object_name",
        "FROM sys.objects o",
        "LEFT JOIN sys.sql_modules m ON m.object_id = o.object_id",
        "WHERE o.schema_id = SCHEMA_ID( @P0 ) AND o.is_ms_shipped = 0",
        "  AND o.type IN ('P', 'PC', 'FN', 'FS', 'FT', 'IF', 'TF', 'TR', 'TA')",
        "  AND m.definition IS NULL",
        "ORDER BY o.type, o.name",
    ),
    wire(
        "SELECT v.name AS view_name, c.name AS column_name, t.name AS type_name",
        "FROM sys.views v",
        "JOIN sys.columns c ON c.object_id = v.object_id",
        "JOIN sys.types t ON t.user_type_id = c.user_type_id",
        "WHERE v.schema_id = SCHEMA_ID( @P0 ) AND v.is_ms_shipped = 0",
        "ORDER BY v.name, c.column_id",
    ),
    wire(
        "SELECT v.name AS view_name, m.definition",
        "FROM sys.views v",
        "JOIN sys.sql_modules m ON m.object_id = v.object_id",
        "WHERE v.schema_id = SCHEMA_ID( @P0 ) AND v.is_ms_shipped = 0",
        "ORDER BY v.name",
    ),

)

/** Die Texte der Strukturänderung und der Transaktionssteuerung des Mitschnitts von `schema migrate --execute`. */
val dMigrateStructureTexts: List<String> = listOf(
    wire(
        "SELECT 1",
    ),
    wire(
        "SET LOCK_TIMEOUT 30000",
    ),
    wire(
        "set implicit_transactions on ",
    ),
    wire(
        "CREATE TABLE [produkte] (",
        "    [Id] BIGINT NOT NULL,",
        "    [Bezeichnung] NVARCHAR(200) NOT NULL,",
        "    [Preis] DECIMAL(10,2),",
        "    [Aktiv] BIT,",
        "    [Seit] DATETIME2,",
        "    [Uhr] TIME,",
        "    [Zahl] INT,",
        "    CONSTRAINT [pk_produkte] PRIMARY KEY ([Id])",
        ");",
    ),
    wire(
        "ALTER TABLE [kunden] ADD [Email] NVARCHAR(200);",
    ),
    wire(
        "IF @@TRANCOUNT > 0 COMMIT TRAN",
    ),
    wire(
        "set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN ",
    ),
)

/**
 * Die Texte, die d-migrate 1.12.0 (derselbe Digest; `schema generate --target mssql` und `schema migrate --target
 * file:<ist.yaml> --dialect mssql --dry-run`, Läufe vom 2026-11-09) für ein Soll mit Eindeutigkeit, Standardwert,
 * Fremdschlüssel, Prüfbedingung und Index rendert; Zeilenumbrüche und Leerzeichen wie ausgegeben. Dass
 * `schema migrate --execute` dieselben Anweisungen sendet, belegt der Lauf gegen den Testserver (Messprotokoll
 * `docs/messungen/katalog-indizes-defaults-fremdschluessel-d-migrate-2026-11-09.md`); die Texte hier sind die der
 * Ausgabe, nicht des Drahts. Die erste Tabelle des Solls.
 */
val dMigrateElementKunden: String = wire(
    "CREATE TABLE [kunden] (",
    "    [Id] BIGINT NOT NULL,",
    "    [Email] NVARCHAR(200) CONSTRAINT [uq_kunden_Email] UNIQUE,",
    "    CONSTRAINT [pk_kunden] PRIMARY KEY ([Id])",
    ");",
)

/** Die zweite Tabelle des Solls: Standardwerte, Fremdschlüssel mit `ON DELETE CASCADE`, Prüfbedingung. */
val dMigrateElementBestellung: String = wire(
    "CREATE TABLE [bestellung] (",
    "    [Id] BIGINT NOT NULL,",
    "    [KundeId] BIGINT NOT NULL,",
    "    [Menge] INT NOT NULL CONSTRAINT [df_bestellung_Menge] DEFAULT 1,",
    "    [Status] NVARCHAR(20) NOT NULL CONSTRAINT [df_bestellung_Status] DEFAULT N'neu',",
    "    CONSTRAINT [fk_bestellung_KundeId] FOREIGN KEY ([KundeId]) REFERENCES [kunden] ([Id]) ON DELETE CASCADE,",
    "    CONSTRAINT [ck_bestellung_menge] CHECK (Menge > 0),",
    "    CONSTRAINT [pk_bestellung] PRIMARY KEY ([Id])",
    ");",
)

/** Der Index des Solls. */
const val DMIGRATE_ELEMENT_INDEX: String = "CREATE INDEX [ix_bestellung_status] ON [bestellung] ([Status]);"

/** `schema migrate` gegen vorhandene Tabellen: die Bedingungen als `ALTER TABLE`, der Standardwert mit `FOR`. */
val dMigrateElementAlterTexts: List<String> = listOf(
    "ALTER TABLE [bestellung] ADD CONSTRAINT [df_bestellung_Menge] DEFAULT 1 FOR [Menge];",
    "ALTER TABLE [bestellung] ADD CONSTRAINT [df_bestellung_Status] DEFAULT N'neu' FOR [Status];",
    "ALTER TABLE [bestellung] WITH CHECK ADD CONSTRAINT [_fk_KundeId] FOREIGN KEY ([KundeId]) " +
        "REFERENCES [kunden]([Id]) ON DELETE CASCADE;",
    "ALTER TABLE [bestellung] WITH CHECK ADD CONSTRAINT [ck_bestellung_menge] CHECK (Menge > 0);",
    "ALTER TABLE [kunden] WITH CHECK ADD CONSTRAINT [_unique_Email] UNIQUE ([Email]);",
)

/**
 * `schema migrate --execute` gegen vorhandene Tabellen: die Texte mit `DROP CONSTRAINT IF EXISTS`, wie der Durchlauf
 * sie am Draht sah (`make vergleichslauf-d LAUF=schema-2022` und `schema-2025`, 2026-11-11, echter Server; Ablage
 * `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/<stufe>.schema.aufnahme.txt`): die Prüfbedingung bei jedem
 * Lauf (Schritte s03 und s06), der Fremdschlüssel und die Eindeutigkeit, wenn das Soll sie ändert oder wegnimmt
 * (s08 und s10). Die Namen sind die der Tabellen dieser Tests.
 */
val dMigrateDropTexts: List<String> = listOf(
    "ALTER TABLE [bestellung] DROP CONSTRAINT IF EXISTS [ck_bestellung_menge];",
    "ALTER TABLE [bestellung] DROP CONSTRAINT IF EXISTS [fk_bestellung_KundeId];",
    "ALTER TABLE [kunden] DROP CONSTRAINT IF EXISTS [uq_kunden_Email];",
)

/**
 * `schema migrate --execute` mit geändertem Index: der Text vor dem neuen `CREATE INDEX`, wie der Durchlauf ihn am
 * Draht sah (`make vergleichslauf-d LAUF=schema-2022` und `schema-2025`, 2026-11-11, echter Server, Schritt s14; am echten
 * Server mit DONE, Befehl `0x00c9`, beantwortet). Name und Tabelle sind die der Tabellen dieser Tests.
 */
val dMigrateDropIndexText: String = "DROP INDEX IF EXISTS [ix_kunden_email] ON [kunden];"
