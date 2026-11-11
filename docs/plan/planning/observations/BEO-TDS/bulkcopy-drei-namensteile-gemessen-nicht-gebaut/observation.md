# Ein Name mit drei Teilen (Datenbank, Schema, Tabelle) im Massenladen ist am echten SQL Server gemessen, aber der Server dieses Repos baut ihn nicht

**Sub-Area:** `*` (Simulations-Backend, Massenladen)

`SqlBulkCopy` (`Microsoft.Data.SqlClient` 7.1.1) sendet für `DestinationTableName` mit drei Teilen eine Probe der Zieltabelle mit `EXEC [<Datenbank>]..sp_tablecollations_100 N'[<Schema>].[<Tabelle>]'` und `insert bulk [<Datenbank>].[<Schema>].[<Tabelle>]` (Messreihe M-33, Instanz 2022 und 2025, Ablage `bulk-name-schema`, Probe 3). Die Erkennung ([SPEC-036](../../../../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), Form B) nimmt ein oder zwei Teile an; der Text mit drei Teilen geht den üblichen Weg und endet mit `TDS_UNSUPPORTED_SQL`. Am Zielbild gemessen ist das eine Abweichung des Servers, kein Ziel; der Katalog des Backends führt keine Datenbank, die ein dritter Teil benennen könnte.

## Benannt, nicht gezählt

Review `2026-11-11-d4331dc4` des Slice [slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen](../../../done/slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen.md), Finding F-6 (LOW): die Grenze steht in der Spezifikation, im Messprotokoll und in der Nutzer-Doku, ein Träger fehlte.
