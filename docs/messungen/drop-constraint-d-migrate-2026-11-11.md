# `ALTER TABLE … DROP CONSTRAINT [IF EXISTS]` und der eindeutige Index: Messprotokoll vom 2026-11-11

Messprotokoll zu [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (`AGENTS.md` §5 Regel 7), Träger: [slice-drop-constraint-form-d5](../plan/planning/in-progress/slice-drop-constraint-form-d5.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Es setzt das [Messprotokoll zu Indizes, Standardwerten und Fremdschlüsseln](katalog-indizes-defaults-fremdschluessel-d-migrate-2026-11-09.md) fort (dort Zeilen e5 und d1/d2): d-migrate wird hier gegen einen **echten SQL Server** gefahren, und die Form `DROP CONSTRAINT` wird mit dem Referenzclient gemessen.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-11 |
| Host | Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Load Average (1 Minute) laut `uptime` 4,2 bis 4,7 während der Läufe (Entwicklungsläufe anderer) |
| Instanzen | SQL Server 2022 (RTM-CU27-GDR) 16.0.4305.5, Image `mcr.microsoft.com/mssql/server:2022-latest@sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`; SQL Server 2025 (RTM-CU10) 17.0.5035.5, Image `mcr.microsoft.com/mssql/server:2025-latest@sha256:245f6b1c8903edae52cb20fa31419cefcde1a49938bb8c3447099011fc871ca3`; `MSSQL_PID=Developer`, nur lokal ([EULA-Entscheidung des Nutzers](../plan/adr/0008-dotnet-kompatibilitaetstests.md), Nutzer pt9912, für dieses Image bestätigt) |
| Clients | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, mssql-jdbc 13.4.0, `encrypt=false` über den mitschneidenden Durchlauf `tools/jdbc-messung/Aufnahme.java`; Referenzclient `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10.0.14 über den TLS-beendenden Proxy |
| Testserver | Stand des Zweigs `slice-drop-constraint-form-d5` mit Form D5, Image lokal gebaut (`TESTSERVER_IMAGE=tdswire-testserver:drop`), `TEST_UNENCRYPTED`, Benutzer `alice`, Port 24951, Beschreibung `testserver/resources/manuell/sim.yaml` |

**Erzeuger der Zahlen.** Im Repo liegen: der Lauf `make vergleichslauf-d LAUF=schema-2022|schema-2025` (Funktion `schemalauf` in `tools/vergleichslauf-d.sh`; Ablage `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/<stufe>.schema.aufnahme.txt`, 566233 und 568301 Byte) und das Szenario `struktur-drop-constraint` von `make vergleichslauf-d LAUF=bulk-2022|bulk-2025` (`referenztest-dotnet/Vergleich/MessungBulkTests.cs`; Ablage `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/<stufe>/struktur-drop-constraint.messung`, 17306 und 17326 Byte; die Aussagen der Tabelle A prüft `MessungBulkBelegTests`). Die Läufe gegen den **Testserver** (Tabelle C) sind von Hand mit `docker run` gefahren; das Skript, das sie nacheinander ausführte, liegt nicht im Repo (Soll-Dateien: dieselben wie in `schemalauf`, zusätzlich die zwei Tabellen von `sim.yaml`; Befehle wie in [Aufgabe 7 der Anleitung](../user/testserver-manuell.md#7-fremdschlüssel-indizes-standardwerte-und-prüfbedingungen-anlegen-und-lesen)). Zeiten sind Einzelmessungen und tragen nur als Größenordnung: Lauf `schema-2022` 71 s, `schema-2025` 85 s, `bulk-2022` 26 s, `bulk-2025` 28 s (mit Bau des Client-Images aus dem Cache). Die Antworten der Tabelle A sind zusätzlich unabhängig von der Auswertung des Repos aus den Rohbytes der Ablage von 2022 gelesen worden (ERROR-Token: Nummer, State, Class, Text; DONE-Token: Status, Befehl).

## A. Antworten des echten Servers auf `DROP CONSTRAINT`

Szenario `struktur-drop-constraint`, Referenzclient, beide Instanzen mit demselben Ergebnis in jeder Zeile (Zeile = Nummer der Anfrage in der Ablage; Aufbau in den Zeilen 01 und 02: `drop_eltern` mit Schlüssel `pk_drop_eltern`; `drop_kind` mit Eindeutigkeit `uq_drop_name`, Standardwert `df_drop_menge`, Schlüssel `pk_drop_kind`, Prüfbedingung `ck_drop_menge`, Fremdschlüssel `fk_drop_eltern` auf `drop_eltern`). „Erfolg“ ist ein `DONE` ohne Fehlerbit, ohne `COUNT`, Befehl `0x00d8`, ohne weitere Token; „Fehler“ ist `ERROR ERROR DONE` mit `DONE` Status `ERROR`, Befehl `0x00fd` (bei 4902 `ERROR DONE`).

| Zeile | Text | Antwort |
|---|---|---|
| 03 | `ALTER TABLE [drop_eltern] DROP CONSTRAINT [pk_drop_eltern];` (Schlüssel, ein Fremdschlüssel verweist darauf) | Fehler 3725 (Class 16, State 0, „The constraint 'pk_drop_eltern' is being referenced by table 'drop_kind', foreign key constraint 'fk_drop_eltern'.“), dann 3727 (Class 16, State 0, „Could not drop constraint. See previous errors.“) |
| 04 | `… DROP CONSTRAINT [CK_DROP_MENGE];` (Prüfbedingung, Name in anderer Schreibweise, ohne `IF EXISTS`) | Erfolg |
| 05 | `… DROP CONSTRAINT [ck_drop_menge];` (kein Treffer mehr) | Fehler 3728 (Class 16, State 1, „'ck_drop_menge' is not a constraint.“), dann 3727 |
| 06 | `… DROP CONSTRAINT IF EXISTS [ck_drop_menge];` (kein Treffer) | Erfolg |
| 07 | `ALTER TABLE [drop_eltern] DROP CONSTRAINT IF EXISTS [uq_drop_name];` (Name gehört zu `drop_kind`) | Erfolg; die Eindeutigkeit bleibt (Zeile 09 entfernt sie) |
| 08 | `ALTER TABLE [drop_eltern] DROP CONSTRAINT [uq_drop_name];` | Fehler 3733 (Class 16, State 2, „Constraint 'uq_drop_name' does not belong to table 'drop_eltern'.“), dann 3727 |
| 09 | `ALTER TABLE [drop_kind] DROP CONSTRAINT IF EXISTS [uq_drop_name];` (Eindeutigkeit, Treffer) | Erfolg |
| 10 | `… DROP CONSTRAINT [uq_drop_name];` (kein Treffer mehr) | Fehler 3728, dann 3727 |
| 11 | `… DROP CONSTRAINT IF EXISTS [uq_drop_name];` (kein Treffer) | Erfolg |
| 12 | `… DROP CONSTRAINT IF EXISTS [fk_drop_eltern];` (Fremdschlüssel, Treffer) | Erfolg |
| 13 | `… DROP CONSTRAINT [fk_drop_eltern];` (kein Treffer mehr) | Fehler 3728, dann 3727 |
| 14 | `… DROP CONSTRAINT IF EXISTS [fk_drop_eltern];` (kein Treffer) | Erfolg |
| 15 | `… DROP CONSTRAINT [df_drop_menge];` (Standardwert, Treffer, ohne `IF EXISTS`) | Erfolg |
| 16 | `… DROP CONSTRAINT [df_drop_menge];` (kein Treffer mehr) | Fehler 3728, dann 3727 |
| 17 | `… DROP CONSTRAINT IF EXISTS [df_drop_menge];` (kein Treffer) | Erfolg |
| 18 | `… DROP CONSTRAINT IF EXISTS [gibtesnicht];` (nie vorhandener Name) | Erfolg |
| 19 | `… DROP CONSTRAINT [gibtesnicht];` | Fehler 3728, dann 3727 |
| 20 | `ALTER TABLE [drop_gibtesnicht] DROP CONSTRAINT IF EXISTS [x];` (unbekannte Tabelle) | Fehler 4902 (Class 16, State 1, „Cannot find the object "drop_gibtesnicht" because it does not exist or you do not have permissions.“), `ERROR DONE` |
| 21 | `ALTER TABLE [drop_gibtesnicht] DROP CONSTRAINT [x];` | wie Zeile 20 |
| 22 | `ALTER TABLE [drop_kind] DROP CONSTRAINT [pk_drop_kind];` (Schlüssel, nichts verweist darauf) | Erfolg |

Die Zeilen 12, 14 und 17 sind Treffer und Nicht-Treffer mit `IF EXISTS`; ein Treffer ohne `IF EXISTS` ist für Prüfbedingung (04), Standardwert (15) und Schlüssel (22) gemessen, für Eindeutigkeit und Fremdschlüssel nur mit `IF EXISTS` (09, 12).

## B. d-migrate 1.12.0 gegen den echten Server

Lauf `schema`, Soll-Dateien aus `schemalauf` (`lieferanten` mit `Name`, `auftraege` mit Fremdschlüssel auf `lieferanten.Id`, `Menge` mit Standardwert, `Status` mit Standardwert, Index `ix_auftraege_status` auf `Status`, Prüfbedingung `ck_auftraege_menge`); die Datenbank `schemadb` ist beim Start leer. Die Soll-Dateien unterscheiden sich in je einer Angabe: `a` (Eindeutigkeit an `lieferanten.Name`, Fremdschlüssel mit `on_delete: cascade`, `default: 1`, `Menge > 0`), `b-check` (`Menge > 1`), `c-fk` (Fremdschlüssel ohne `on_delete`), `d-unique` (ohne `unique` an `Name`), `e-default` (`default: 2`), `f-index` (Index mit `unique: true`). Beide Instanzen gleich in Exit-Codes, gesendeten Texten und Antworten.

| Schritt | Soll, Befehl | Exit | Gesendet (außer den Katalogabfragen) und Antwort der Instanz |
|---|---|---|---|
| s01 | `a`, `schema migrate --execute` | 0 | `CREATE TABLE [lieferanten] (…)` mit `CONSTRAINT [uq_lieferanten_Name] UNIQUE`, `CREATE TABLE [auftraege] (…)` mit `DEFAULT 1`, `DEFAULT N'neu'`, `CHECK (Menge > 0)` und `FOREIGN KEY … ON DELETE CASCADE`, `CREATE INDEX [ix_auftraege_status] …`; Antworten `DONE` mit Befehl `0x00c6`, `0x00c6`, `0x00c8` |
| s02 | `a`, `schema compare` | 0 | `IDENTICAL` |
| s03 | `a`, `schema migrate --execute` (zweiter Lauf) | **0** | `ALTER TABLE [auftraege] DROP CONSTRAINT IF EXISTS [ck_auftraege_menge];` mit Antwort `ENVCHANGE` Typ 8, `DONE` Befehl `0x00d8`, ohne weitere Token; danach `ALTER TABLE [auftraege] WITH CHECK ADD CONSTRAINT [ck_auftraege_menge] CHECK (Menge > 0);` mit `DONE` `0x00d8`; weder Eindeutigkeit, Fremdschlüssel noch Standardwert werden neu geplant |
| s04 | `a`, `schema compare` | 0 | `IDENTICAL` |
| s05 | `schema reverse` | 0 | `lieferanten.Name` mit `unique: true` und `unique_constraint: uq_lieferanten_Name`, Fremdschlüssel `fk_auftraege_LieferantId` (`on_delete: cascade`), Prüfbedingung `'"Menge">(0)'`, Standardwerte `1` und `neu`, Index `ix_auftraege_status` |
| s06 | `b-check`, `migrate` | 0 | derselbe Text `DROP CONSTRAINT IF EXISTS [ck_auftraege_menge]`, dann `ADD CONSTRAINT … CHECK (Menge > 1)`; s07 `IDENTICAL` |
| s08 | `c-fk`, `migrate` | 0 | zusätzlich `ALTER TABLE [auftraege] DROP CONSTRAINT IF EXISTS [fk_auftraege_LieferantId];` und `ALTER TABLE [auftraege] WITH CHECK ADD CONSTRAINT [_fk_LieferantId] FOREIGN KEY ([LieferantId]) REFERENCES [lieferanten]([Id]);`, je `DONE` `0x00d8`; s09 `IDENTICAL` |
| s10 | `d-unique`, `migrate` | 0 | zusätzlich `ALTER TABLE [lieferanten] DROP CONSTRAINT IF EXISTS [uq_lieferanten_Name];` mit `DONE` `0x00d8`; s11 `IDENTICAL` |
| s12 | `e-default`, `migrate` | 0 | ein Text `DECLARE @df sysname, @sql nvarchar(max); SELECT @df = dc.name FROM sys.default_constraints dc … EXEC sp_executesql @sql;` (er sucht den Namen des vorhandenen Standardwerts im Katalog und führt `DROP CONSTRAINT` im dynamischen SQL aus; Antwort: `ENVCHANGE` Typ 8, `DONE` mit `COUNT` und Befehl `0x00c1`, `DONE` `0x00c0`, `DONE` mit `COUNT`, `DONEINPROC` Befehl `0x00d8`, `RETURNSTATUS` 0, `DONEPROC`), dann `ALTER TABLE [auftraege] ADD CONSTRAINT [df_auftraege_Menge] DEFAULT 2 FOR [Menge];`, dann die Neuplanung der Prüfbedingung; s13 `IDENTICAL` |
| s14 | `f-index`, `migrate` | **5** | die Neuplanung der Prüfbedingung, dann `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege];` (Antwort `DONE` Befehl `0x00c9`) und `CREATE UNIQUE INDEX [ix_auftraege_status] ON [auftraege] ([Status]);` (`DONE` `0x00c8`); d-migrate meldet `POST_EXECUTE_DRIFT` |
| s15 | `f-index`, `schema compare` | 1 | `DIFFERENT`: `- index ix_auftraege_status [btree,unique] on (Status)` und `+ constraint _unique_Status (unique on [Status])` |
| s16 | `schema reverse` | 0 | `auftraege.Status` mit `unique: true`, kein Eintrag unter `indices` |

**Antwort auf die Frage zu Punkt 3 des Registers.** d-migrate liest einen `CREATE UNIQUE INDEX` ohne Bedingung auch am echten Server (2022 und 2025) als Eindeutigkeit der Spalte zurück; ein Soll mit `unique: true` im Index endet dort mit `POST_EXECUTE_DRIFT`, Exit 5, und `schema compare` meldet danach `DIFFERENT` mit den beiden Zeilen aus s15. Die Abweichung liegt bei d-migrate, nicht beim Testserver (Tabelle C, s17 und s18: gleicher Exit und gleicher Unterschied).

## C. d-migrate gegen den Testserver

Dieselben Soll-Dateien, zusätzlich mit den Tabellen `kunden` und `bestellungen` von `sim.yaml`; der Server ist frisch gestartet. Schritt s17 ist eine Soll-Datei `g-index` (wie `d-unique` mit `default: 1`, dazu ein zweiter Index `ux_auftraege_status` mit `unique: true` neben `ix_auftraege_status`), die den eindeutigen Index ohne die Wirkung von s12 und ohne `DROP INDEX` prüft.

| Schritt | Exit | Ergebnis |
|---|---|---|
| s01 bis s04 | 0 | wie am echten Server; der zweite `schema migrate` (s03) hat `statementsAttempted` 2 (`DROP CONSTRAINT IF EXISTS` und `ADD CONSTRAINT`), `transactionRolledBack` false; s04 `IDENTICAL` |
| s05 | 0 | `schema reverse` |
| s06 bis s11 | 0 | wie am echten Server; s07, s09 und s11 `IDENTICAL` (Prüfbedingung, Fremdschlüssel, Eindeutigkeit geändert oder weggenommen) |
| s12 | 5 | `Unsupported SQL.`, `statementsAttempted` 1, zurückgenommen: der Text mit `DECLARE @df` ist nicht erkannt (Punkt 1 des Registers); s13 `DIFFERENT` (`default: 2 -> 1`) |
| s14 | 5 | `Unsupported SQL.`, `statementsAttempted` 1: die Anweisung davor (s12) scheiterte, der Standardwert steht noch bei 1, deshalb beginnt der Plan wieder mit dem Text mit `DECLARE @df`; s15 `DIFFERENT` |
| s17 | 5 | `POST_EXECUTE_DRIFT`, `statementsAttempted` 3 (Neuplanung der Prüfbedingung, `CREATE UNIQUE INDEX`), `transactionRolledBack` false |
| s18 | 1 | `DIFFERENT`: `- index ux_auftraege_status [btree,unique] on (Status)` und `+ constraint _unique_Status (unique on [Status])`, wie am echten Server |
| s19 | 0 | `schema reverse` |

## Befunde

- `ALTER TABLE … DROP CONSTRAINT IF EXISTS` trifft eine vorhandene Prüfbedingung, einen Fremdschlüssel und eine Eindeutigkeit und antwortet jedes Mal mit `DONE`, Befehl `0x00d8`, ohne `COUNT`; ohne Treffer, auch bei dem Namen einer Bedingung einer anderen Tabelle, ist es ein Erfolg derselben Gestalt (Tabelle A).
- Ohne `IF EXISTS` endet ein Name ohne Treffer mit 3728 (State 1) und 3727, der Name einer Bedingung einer anderen Tabelle mit 3733 (State 2) und 3727; eine unbekannte Tabelle ist mit und ohne `IF EXISTS` 4902; der Name gilt ohne Beachtung der Schreibweise (Zeile 04, Collation der Datenbank `tdswire_struktur` ist die Voreinstellung der Instanz, nicht eigens erhoben).
- d-migrate sendet für eine Prüfbedingung, einen Fremdschlüssel und eine Eindeutigkeit `DROP CONSTRAINT IF EXISTS` mit dem Namen aus dem Katalog; für einen Standardwert sendet es den Text mit `DECLARE @df`, nie `DROP CONSTRAINT <Name>` (s12). Eine unveränderte Eindeutigkeit, ein unveränderter Fremdschlüssel und ein unveränderter Standardwert werden im zweiten Lauf nicht neu geplant (s03).
- Der Testserver bis Form D5: der zweite `schema migrate` endet mit Exit 0 und `IDENTICAL` (Tabelle C, s03 und s04).
- Neu: Ändert das Soll einen vorhandenen Index, sendet d-migrate zuerst `DROP INDEX IF EXISTS … ON …` (s14); der echte Server beantwortet es mit `DONE` `0x00c9`, der Testserver erkennt es nicht.
- **Nicht gemessen:** d-migrate gegen den echten Server mit den Beispiel-Tabellen `kunden` und `bestellungen` (das Soll des Laufs `schema` führt sie nicht); `DROP CONSTRAINT` mit mehreren Namen, Schemanamen, Namen mit Sonderzeichen, auf einer Tabelle mit Zeilen und innerhalb einer Transaktion des Referenzclients; der zweite ERROR (3727) im RPC (die Texte laufen als Batch); die Fehler bei Eindeutigkeit und Fremdschlüssel ohne `IF EXISTS` mit Treffer (Zeilen 09 und 12 sind nur mit `IF EXISTS` gemessen).
