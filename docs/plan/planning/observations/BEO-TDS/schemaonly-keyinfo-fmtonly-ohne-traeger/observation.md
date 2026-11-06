# `SchemaOnly`, `KeyInfo` und `SET FMTONLY` werden vom Server nicht beantwortet, und kein Slice nennt sie

**Sub-Area:** `*` (Simulations-Backend, Katalog, Schemaabfrage)

`ExecuteReader(SchemaOnly)` und `(KeyInfo)` mit `GetSchemaTable()` senden `SET FMTONLY` und `SET NO_BROWSETABLE`; der echte SQL Server beantwortet sie mit Spalten, Typ, Größe, Nullbarkeit, Schlüssel und Tabelle, der Server dieses Repos mit `Number 50001`, „Unsupported SQL.“ ([SPEC-036](../../../../../../spec/spezifikation.md), [ADR-0005](../../../../adr/0005-rpc-und-prepared-statements.md): die `SET`-Behandlung ist offen). Folge: die drei Schritte `schema-*` des Live-Laufs sind alle Lücke und können das Schema des Servers nicht gegen das Skript der Referenzinstanz halten (Schema-Gleichlauf ohne Sensor).
