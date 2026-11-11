# Standardwert, Eindeutigkeit, Fremdschlüssel, Index und Prüfbedingung: d-migrate gegen den Testserver, Messprotokoll vom 2026-11-09

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) (`AGENTS.md` §5 Regel 7), Träger: [slice-katalog-indizes-defaults-fremdschluessel](../plan/planning/in-progress/slice-katalog-indizes-defaults-fremdschluessel.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Es setzt das [Messprotokoll zu Katalog und Strukturänderung](katalog-und-ddl-zustand-2026-11-06.md) fort: dort antworteten die Abfragen dieser Elemente leer, hier antworten sie aus der Beschreibung der Tabellen (Zeilen der Messung M-21 im [Messprotokoll zum Referenzabgleich](referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)).

**Vorbehalt, der für jede Zeile gilt.** Gemessen ist das Verhalten von d-migrate 1.12.0 und mssql-jdbc 13.4.0 gegen den **Testserver dieses Repos**, nicht gegen einen echten SQL Server. Die Zeilen der Katalogantworten für Standardwert, Fremdschlüssel, Index, Prüfbedingung und Eindeutigkeit sind die des echten Servers (M-21, je ein Fall je Instanz); die Form der Prüfbedingung für mehrere Vergleiche, Texte und Klammern, mehrspaltige Elemente, die Namen der Fehler und die Kennung von `CREATE INDEX` sind Setzung. d-migrate gegen einen echten SQL Server ist nicht gemessen.

**Erzeuger der Zahlen.** Die Läufe sind von Hand mit `docker run` gefahren (die Befehle stehen unten); das Skript, das sie nacheinander ausführte, liegt nicht im Repo. Die Zeiten sind Einzelmessungen und tragen nur als Größenordnung; Exit-Codes, Berichtsfelder und Ausgaben sind wiederholbar mit den hier abgedruckten Dateien.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-09, Läufe zwischen 08:00 und 08:05 |
| Host | Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.2; Load Average (1 Minute) laut `uptime`: 7,8 um 08:03 (Entwicklungsläufe anderer) |
| Client | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, Treiber mssql-jdbc 13.4.0 |
| Testserver | Stand `c285cf21` des Zweigs (Quelltext der Bibliothek und des Testservers dieses Standes), Image lokal gebaut (`TESTSERVER_IMAGE=tdswire-testserver:katalog`), Abbild `sha256:671e6ce5dd5584d20431acebbfef971f8c6ce76cfaeca1fcf0ea0f3548a28cd3`, `TEST_UNENCRYPTED`, Benutzer `alice`, Port 24911 auf dem Host-Netz; je Abschnitt ein frisch gestarteter Container |
| URL | `mssql://alice:secret@127.0.0.1:24911/manuell?encrypt=false`, `docker run --rm --network host --user 1000:1000 -v <Arbeitsverzeichnis>:/work <Image> <Befehl>` |
| Beschreibung | `testserver/resources/manuell/sim.yaml` (Abschnitte A, C, D, E) oder die Datei `soll-elemente2.yaml` (Abschnitt B) |

## Texte, die d-migrate rendert

`d-migrate schema generate --source soll.yaml --target mssql` und `d-migrate schema migrate --source soll.yaml --target file:ist.yaml --dialect mssql --dry-run` (derselbe Digest) geben für ein Soll mit Eindeutigkeit, Standardwert, Fremdschlüssel, Prüfbedingung und Index die Texte aus, die als Handvektoren in `DMigrateWire.kt` stehen (`dMigrateElementKunden` und folgende): `CREATE TABLE` mit `CONSTRAINT [uq_…] UNIQUE`, `CONSTRAINT [df_…] DEFAULT 1` und `DEFAULT N'neu'` an der Spalte und `CONSTRAINT [fk_…] FOREIGN KEY … ON DELETE CASCADE`, `CONSTRAINT [ck_…] CHECK (Menge > 0)` in der Tabellenliste; `CREATE INDEX [ix_…] ON [t] ([Status])`; für vorhandene Tabellen `ALTER TABLE [t] WITH CHECK ADD CONSTRAINT [_fk_KundeId] FOREIGN KEY … REFERENCES [kunden]([Id]) ON DELETE CASCADE`, `… CHECK …`, `… UNIQUE …` und `ALTER TABLE [t] ADD CONSTRAINT [df_…] DEFAULT 1 FOR [Menge]`, dem bei einer vorhandenen Spalte ein Text mit `DECLARE @df sysname, @sql nvarchar(max);` vorangeht (Suche nach dem vorhandenen Standardwert, `DROP CONSTRAINT` in `EXEC sp_executesql`). Die Aktionen `restrict` und `no_action` rendert d-migrate als `NO ACTION`, `set_default` als `SET DEFAULT`. Dass `schema migrate --execute` dieselben Texte sendet, folgt aus den Läufen unten: jede Anweisung, die der Testserver nicht erkannte, endete mit `Unsupported SQL.` im Bericht.

## Läufe

Soll-Dateien: `soll-elemente.yaml` ist die Beschreibung des Testservers (`sim.yaml`) mit den neuen Tabellen `lieferanten` (`Name` mit `unique: true`) und `auftraege` (`LieferantId` mit `references` auf `lieferanten.Id` und `on_delete: cascade`, `Menge` mit `default: 1`, `Status` mit `default: neu`, ein Index auf `Status`, eine Prüfbedingung `Menge > 0`); `soll-elemente2.yaml` ist `sim.yaml` mit `unique: true` an `kunden.Name`, `references` an `bestellungen.KundenId`, einem Index auf `bestellungen.KundenId` und einer Prüfbedingung `Betrag >= 0` an `bestellungen`. Beide Dateien stehen in [Aufgabe 7 der Anleitung](../user/testserver-manuell.md#7-fremdschlüssel-indizes-standardwerte-und-prüfbedingungen-anlegen-und-lesen).

| Nr | Befehl (gekürzt) | Exit | Zeit | Ergebnis |
|---|---|---|---|---|
| a1 | `schema reverse` (Beschreibung `sim.yaml`) | 0 | 3 s | die zwei Tabellen ohne Elemente |
| a2 | `schema compare --source file:soll-elemente.yaml` | 1 | 2 s | `DIFFERENT`: `lieferanten` und `auftraege` fehlen im Server |
| a3 | `schema migrate --source soll-elemente.yaml --execute --report` | 0 | 2 s | Bericht `status: ok`, `operationsTotal` 2, `statementsAttempted` 3 (zwei `CREATE TABLE`, ein `CREATE INDEX`), `transactionRolledBack` false; die Nachprüfung von d-migrate fand keine Abweichung |
| a4 | `schema compare --source file:soll-elemente.yaml` | 0 | 2 s | `IDENTICAL`, `No differences found.` |
| a5 | `schema reverse` | 0 | 2 s | `auftraege` mit `default: 1` und `default: neu`, `fk_auftraege_LieferantId` (`on_delete: cascade`), `ck_auftraege_menge` mit `expression: '"Menge">(0)'` (d-migrate liest `([Menge]>(0))` aus dem Katalog und schreibt es in seiner Quotierung), Index `ix_auftraege_status`; `lieferanten.Name` mit `unique: true` und `unique_constraint: uq_lieferanten_Name` |
| a6 | `schema reverse --include-all` | 0 | 2 s | dieselben Tabellen und Elemente wie a5 |
| e1 | Server neu, `schema compare --source file:soll-elemente2.yaml` | 1 | 2 s | `DIFFERENT`: Index, Fremdschlüssel und Prüfbedingung an `bestellungen`, Eindeutigkeit an `kunden` |
| e2 | `schema migrate --source soll-elemente2.yaml --execute` | 0 | 2 s | `status: ok`, `operationsTotal` 4, `statementsAttempted` 4 (`ALTER TABLE … WITH CHECK ADD CONSTRAINT` für Fremdschlüssel, Prüfbedingung und Eindeutigkeit, `CREATE INDEX`), Nachprüfung ohne Abweichung |
| e3 | `schema compare --source file:soll-elemente2.yaml` | 0 | 2 s | `IDENTICAL` |
| e5 | derselbe `schema migrate` noch einmal | **5** | 2 s | `status: failed`, `executionError` „Unsupported SQL.“, `statementsAttempted` 1, `transactionRolledBack` true (siehe Befunde) |
| b1 | Server mit der Beschreibung `soll-elemente2.yaml`, `schema compare --source file:soll-elemente2.yaml` | 0 | 2 s | `IDENTICAL` |
| b3 | `schema reverse` und `schema compare` mit der zurückgeschriebenen Datei | 0 | 2 s | `IDENTICAL` |
| c1 | `schema migrate` mit `default: 0` an der vorhandenen Spalte `bestellungen.Betrag` | **5** | 3 s | `Unsupported SQL.`, `statementsAttempted` 1, zurückgenommen |
| d1 | `schema migrate` mit einem Index `unique: true` auf `kunden.Name` | **5** | 3 s | `POST_EXECUTE_DRIFT`; `statementsAttempted` 1 (`CREATE UNIQUE INDEX`) |
| d2 | `schema compare` danach | 1 | 2 s | `DIFFERENT`: im Soll `index ux_kunden_name [btree,unique]`, im Server `constraint _unique_Name (unique on [Name])` |

### Lauf am End-Stand `1b824ba3` (Verifikation, 2026-11-11)

Die Läufe oben gelten für den Stand `c285cf21`. Der Verifier fuhr den Roundtrip am End-Stand `1b824ba3` (nach der Nachbesserung des Lesers: `unique_constraint`, Namensraum von Index und Eindeutigkeit) noch einmal; Quelle: [Verifikationsbericht](../reviews/2026-11-11-1b824ba3-verifikation-slice-katalog-indizes-defaults-fremdschluessel.md), Abschnitt „Roundtrip“. Bedingungen: dieselben d-migrate- und Treiber-Versionen (Digest wie oben), Testserver-Image frisch aus dem End-Stand (`tdswire-testserver:verif-katalog`), `make testserver-up` mit `TESTSERVER_NAME=verif-ts-a` und `-b`, Ports 24931 und 24932, `--network host --user 1000:1000`, Soll `soll-elemente.yaml` wie oben; Host Linux 6.8.0-147, Load Average (1 Minute) 4 bis 5 laut `uptime`. Zeiten nicht erhoben.

| Nr | Befehl (gekürzt) | Exit | Ergebnis |
|---|---|---|---|
| f1 | Server A (Beschreibung `sim.yaml`): `schema compare --source file:soll-elemente.yaml` | 1 | `DIFFERENT`, zwei Tabellen fehlen |
| f2 | `schema migrate --execute --report` | 0 | `status: ok`, `statementsAttempted` 3, `transactionRolledBack` false |
| f3 | `schema compare` | 0 | `IDENTICAL` |
| f4 | `schema reverse --output ist-elemente.yaml` | 0 | `default: 1`, `default: neu`, Index, `fk_auftraege_LieferantId` (`on_delete: cascade`), Prüfbedingung `'"Menge">(0)'`, `lieferanten.Name` mit `unique: true` und `unique_constraint: uq_lieferanten_Name` |
| f5 | Server B startet mit `TESTSERVER_BESCHREIBUNG=ist-elemente.yaml` | 0 | bereit |
| f6 | `schema compare` gegen B mit `soll-elemente.yaml` und mit `ist-elemente.yaml` | 0, 0 | `IDENTICAL` beide |
| f7 | `schema reverse` gegen B, `diff` mit der Datei aus f4 | 0 | byte-gleich |

Zeile f5 ist der Start des Testservers mit der von `schema reverse` geschriebenen Datei; er brach vor der Nachbesserung ab („Feld unbekannt“, Review F-7). Zeile b3 oben beschreibt dagegen `reverse` und `compare`, nicht diesen Start.

## Befunde

- **Zurücklesen und Vergleich bestehen** für Standardwert (`((1))`, `(N'neu')`), Eindeutigkeit, Fremdschlüssel mit Aktion, Index und Prüfbedingung, sowohl für Tabellen, die `schema migrate --execute` anlegt (a3, a4), als auch für solche aus der Beschreibungsdatei (b1, b3) und für Elemente, die `ALTER TABLE … ADD CONSTRAINT` und `CREATE INDEX` an vorhandenen Tabellen anlegen (e2, e3).
- **Prüfbedingung:** d-migrate vergleicht den Text des Autors (`Menge > 0`) nach seiner Normalisierung mit der Form des Katalogs (`([Menge]>(0))`) und meldet `IDENTICAL`. Sein `schema migrate` plant eine vorhandene Prüfbedingung aber bei jedem Lauf neu: `ALTER TABLE [t] DROP CONSTRAINT IF EXISTS [ck_…]` und danach `ADD CONSTRAINT` (e5). Der Text mit `DROP CONSTRAINT` ist **nicht erkannt**; ein zweites `schema migrate` mit einer Prüfbedingung im Soll endet mit Exit 5. Das Grenzverhalten ist nicht am echten Server gemessen; d-migrate nennt in seiner Hilfe (`--provenance-output`), dass Text des Autors und Form des Katalogs „nie gleich“ sind, die Neuplanung ist deshalb auch am echten Server zu erwarten (Lesart, nicht gemessen).
- **Standardwert an vorhandener Spalte:** der Text mit `DECLARE @df sysname …` ist nicht erkannt (c1); ein Standardwert in `CREATE TABLE` oder an einer neuen Spalte ist nicht betroffen (a3).
- **Eindeutiger Index ohne Bedingung:** d-migrate liest einen `CREATE UNIQUE INDEX` als Eindeutigkeit (`_unique_Name`) zurück; das Soll mit `unique: true` im Index endet mit `POST_EXECUTE_DRIFT` (d1, d2). Die Abfrage der Eindeutigkeiten antwortet für diesen Index leer (`kc.type = 'UQ'`), die der Indizes mit `is_unique` 1; am echten Server ist dieser Fall nicht gemessen.
- **Nicht gemessen:** d-migrate gegen einen echten SQL Server mit diesen Soll-Dateien, mehrspaltige Elemente im Lauf (die Handvektoren in `DMigrateWire.kt` und die Tests tragen sie), `data import` und `data export` mit Tabellen, die Elemente führen, Sequenzen, Routinen, Sichten und Partitionen, Identität, `ON UPDATE` im Lauf, mehr als ein Host.
