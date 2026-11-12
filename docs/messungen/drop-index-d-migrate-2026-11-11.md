# `DROP INDEX [IF EXISTS] <Index> ON <Tabelle>`: Messprotokoll vom 2026-11-11

Messprotokoll zu [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (`AGENTS.md` §5 Regel 7), Träger: [slice-drop-index-if-exists](../plan/planning/done/slice-drop-index-if-exists.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Es setzt das [Messprotokoll zu `DROP CONSTRAINT`](drop-constraint-d-migrate-2026-11-11.md) fort (dort Schritt s14: d-migrate sendet vor einem geänderten Index `DROP INDEX IF EXISTS … ON …`): die Form wird hier mit dem Referenzclient am **echten SQL Server** gemessen, dazu fährt d-migrate die Folge gegen den Testserver.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-11 |
| Host | Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Load Average (1 Minute) laut `uptime` 1,8 bis 2,7 während der Läufe (Entwicklungsläufe anderer) |
| Instanzen | SQL Server 2022 (RTM-CU27-GDR) 16.0.4305.5, Image `mcr.microsoft.com/mssql/server:2022-latest@sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`; SQL Server 2025 (RTM-CU10) 17.0.5035.5, Image `mcr.microsoft.com/mssql/server:2025-latest@sha256:245f6b1c8903edae52cb20fa31419cefcde1a49938bb8c3447099011fc871ca3`; `MSSQL_PID=Developer`, nur lokal ([EULA-Entscheidung des Nutzers](../plan/adr/0008-dotnet-kompatibilitaetstests.md), Nutzer pt9912, für dieses Image bestätigt) |
| Clients | Referenzclient `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10.0.14 über den TLS-beendenden Proxy; d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` (nur Tabelle C, gegen den Testserver; gegen den echten Server liegt der Lauf `schema` im Messprotokoll zu `DROP CONSTRAINT`) |
| Testserver | Arbeitsbaum des Slice `slice-drop-index-if-exists` mit Form D6 vor dem Commit der Quellen, Image lokal gebaut (`TESTSERVER_IMAGE=tdswire-testserver:i6`), `TEST_UNENCRYPTED`, Benutzer `alice`, Port 24961, Beschreibung `testserver/resources/manuell/sim.yaml` |

**Erzeuger der Zahlen.** Im Repo liegen: das Szenario `struktur-drop-index` von `make vergleichslauf-d LAUF=bulk-2022|bulk-2025` (`referenztest-dotnet/Vergleich/MessungBulkTests.cs`; Ablage `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/<stufe>/struktur-drop-index.messung`, 16319 und 16339 Byte; die Aussagen der Tabelle A prüft `MessungBulkBelegTests`) und der Lauf `schema` (Ablage `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/<stufe>.schema.aufnahme.txt`, Schritt s14; die Zeile des Befehls `0x00c9` prüft ebenfalls `MessungBulkBelegTests`). Die Läufe gegen den **Testserver** (Tabelle C) sind von Hand mit `docker run` gefahren; das Skript, das sie nacheinander ausführte, liegt nicht im Repo (Soll-Dateien: dieselben Tabellen wie in `schemalauf`, ohne Fremdschlüssel, Standardwert und Prüfbedingung, dazu die zwei Tabellen von `sim.yaml`; Befehle wie in [Aufgabe 7 der Anleitung](../user/testserver-manuell.md#7-fremdschlüssel-indizes-standardwerte-und-prüfbedingungen-anlegen-und-lesen)). Zeiten sind Einzelmessungen und tragen nur als Größenordnung: Lauf `bulk-2022` 34 s, `bulk-2025` 39 s (mit Bau des Client-Images aus dem Cache). Die Antworten der Tabelle A sind zusätzlich unabhängig von der Auswertung des Repos aus den Rohbytes der Ablage von 2022 gelesen worden (ERROR-Token: Nummer, State, Class; DONE-Token: Status, Befehl); beide Instanzen stimmen in jeder Zeile der Auswertung überein.

## A. Antworten des echten Servers auf `DROP INDEX`

Szenario `struktur-drop-index`, Referenzclient, beide Instanzen mit demselben Ergebnis in jeder Zeile (Zeile = Nummer der Anfrage in der Ablage). Aufbau in den Zeilen 01 bis 06: `idx_eltern` (`Id`, `Name`, Schlüssel `pk_idx_eltern`) mit den Indizes `ix_idx_name` und `ix_idx_nur_eltern`; `idx_kind` (`Id`, `Name`, `Menge`, `Code` mit Eindeutigkeit `uq_idx_code`, Schlüssel `pk_idx_kind`) mit dem Index `ix_idx_name` (gleicher Name wie an den Eltern) und dem eindeutigen Index `ux_idx_menge`; jeder Aufbau antwortet mit `DONE` ohne `COUNT`, Befehl `0x00c6` (Tabelle) bzw. `0x00c8` (Index). „Erfolg“ ist ein `DONE` ohne Fehlerbit, ohne `COUNT`, Befehl `0x00c9`, ohne weitere Token; „3701“ ist `ERROR DONE` mit `DONE` Status `ERROR`, Befehl `0x00c9`; „3723“ ist `ERROR DONE` mit `DONE` Status `ERROR`, Befehl `0x00fd`. Es folgt nie ein zweites ERROR.

| Zeile | Text | Antwort |
|---|---|---|
| 07 | `DROP INDEX IF EXISTS [ix_idx_name] ON [idx_kind];` (Treffer, der Wortlaut von d-migrate) | Erfolg |
| 08 | derselbe Text (kein Treffer mehr, der Name steht noch an den Eltern) | Erfolg |
| 09 | `DROP INDEX [ix_idx_name] ON [idx_kind];` (kein Treffer, ohne `IF EXISTS`) | 3701 (Class 11, State 7, „Cannot drop the index 'idx_kind.ix_idx_name', because it does not exist or you do not have permission.“) |
| 10 | `DROP INDEX [ix_idx_nur_eltern] ON [idx_kind];` (Index der anderen Tabelle) | 3701 (Class 11, State 7) |
| 11 | `DROP INDEX IF EXISTS [ix_idx_nur_eltern] ON [idx_kind];` | Erfolg (kein Lesen danach: ob der Index der Eltern bleibt, ist nicht gelesen) |
| 12 | `DROP INDEX [UX_IDX_MENGE] ON [idx_kind];` (eindeutiger Index, Name in anderer Schreibweise, ohne `IF EXISTS`) | Erfolg |
| 13 | `DROP INDEX [ux_idx_menge] ON [idx_kind];` (kein Treffer mehr) | 3701 (Class 11, State 7) |
| 14 | `DROP INDEX IF EXISTS [uq_idx_code] ON [idx_kind];` (Index der Eindeutigkeit) | 3723 (Class 16, State 5, „An explicit DROP INDEX is not allowed on index 'idx_kind.uq_idx_code'. It is being used for UNIQUE KEY constraint enforcement.“) |
| 15 | `DROP INDEX [uq_idx_code] ON [idx_kind];` | wie Zeile 14 |
| 16 | `DROP INDEX [pk_idx_kind] ON [idx_kind];` (Index des Schlüssels) | 3723 (Class 16, State 4, „… It is being used for PRIMARY KEY constraint enforcement.“) |
| 17 | `DROP INDEX IF EXISTS [pk_idx_kind] ON [idx_kind];` | wie Zeile 16 |
| 18 | `DROP INDEX IF EXISTS [x] ON [idx_gibtesnicht];` (unbekannte Tabelle) | **Erfolg** |
| 19 | `DROP INDEX [x] ON [idx_gibtesnicht];` | 3701 (Class 11, **State 6**, „Cannot drop the index 'idx_gibtesnicht.x', because it does not exist or you do not have permission.“) |
| 20 | `DROP INDEX [ix_idx_name] ON [idx_eltern];` (gewöhnlicher Index, Treffer, ohne `IF EXISTS`; der gleichnamige Index am Kind ist da schon entfernt) | Erfolg |
| 21 | `DROP INDEX IF EXISTS [ix_idx_nur_eltern] ON [dbo].[idx_eltern];` (Tabelle mit Schema `dbo`) | Erfolg (kein Lesen danach: ob der Index fiel, ist nicht gelesen) |
| 22 | `DROP INDEX IF EXISTS [zwei_a] ON [idx_kind]; DROP INDEX IF EXISTS [zwei_b] ON [idx_kind];` (zwei Anweisungen in einem Text, beide ohne Treffer) | zwei `DONE`: das erste mit Status `MORE`, beide Befehl `0x00c9` |
| 23 | `DROP INDEX [zwei_a] ON [idx_kind]; DROP INDEX IF EXISTS [zwei_b] ON [idx_kind];` (die erste ohne `IF EXISTS` und ohne Treffer) | `ERROR DONE DONE`: 3701 (State 7), das erste `DONE` `MORE\|ERROR`, das zweite ohne Bits; die zweite Anweisung läuft |
| 24 | `SELECT 1 AS eins` (Folgeabfrage) | Ergebnis mit einer Zeile; die Verbindung bleibt offen |

## B. d-migrate 1.12.0 gegen den echten Server

Ändert das Soll einen vorhandenen Index, sendet d-migrate vor dem neuen `CREATE UNIQUE INDEX` den Text `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege];`; der echte Server (2022 und 2025) antwortet mit `DONE`, Status 0, Befehl `0x00c9`, Zähler 0 (Schritt s14 des Laufs `schema`, [Messprotokoll zu `DROP CONSTRAINT`](drop-constraint-d-migrate-2026-11-11.md); Ablage `<stufe>.schema.aufnahme.txt`). Der Text trägt das Schema nicht, keine Klausel hinter dem Tabellennamen und genau einen Namen.

## C. d-migrate gegen den Testserver

Die Tabellen `kunden` und `bestellungen` von `sim.yaml`, dazu `lieferanten` und `auftraege` (je mit Schlüssel, ohne Fremdschlüssel, Standardwert und Prüfbedingung); der Server ist frisch gestartet. Soll `a`: Index `ix_auftraege_status` auf `Status`; `h`: Index auf `Status, Menge`; `i`: derselbe Index unter dem Namen `ix_auftraege_neu`; `j`: wie `i` mit `unique: true`; `k`: ohne Index.

| Schritt | Soll, Befehl | Exit | Ergebnis |
|---|---|---|---|
| t01 | `a`, `schema migrate --execute` | 0 | `CREATE TABLE` für `auftraege` und `lieferanten`, `CREATE INDEX [ix_auftraege_status] …`; t02 `IDENTICAL` |
| t03 | `a`, zweiter Lauf | 0 | `statementsAttempted` 0; t04 `IDENTICAL` |
| t05 | `h`, Index mit anderen Spalten | **0** | `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege];` und `CREATE INDEX [ix_auftraege_status] ON [auftraege] ([Status], [Menge]);`, `statementsAttempted` 2; t06 `IDENTICAL` |
| t07 | `i`, Index umbenannt | **0** | `CREATE INDEX [ix_auftraege_neu] …`, dann `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege];`, `statementsAttempted` 2; t08 `IDENTICAL` |
| t09 | `schema reverse` | 0 | |
| t10 | `j`, Index mit `unique: true` | 5 | `DROP INDEX IF EXISTS [ix_auftraege_neu] …` und `CREATE UNIQUE INDEX [ix_auftraege_neu] …`, dann `POST_EXECUTE_DRIFT`; t11 `DIFFERENT` (`- index ix_auftraege_neu [btree,unique] …` und `+ constraint ix_auftraege_neu (unique on [Status,Menge])`): das Verhalten von d-migrate, am echten Server ebenso (s14 und s15 im Messprotokoll zu `DROP CONSTRAINT`) |
| t12 | `k`, Index aus dem Soll entfernt, der Index ist eindeutig | 5 | d-migrate liest den eindeutigen Index als Eindeutigkeit und sendet `ALTER TABLE [auftraege] DROP CONSTRAINT IF EXISTS [ix_auftraege_neu];`; die Bibliothek führt den Namen als Index und nicht als Bedingung, `IF EXISTS` ohne Treffer ist ein Erfolg ohne Änderung, der Index bleibt; `POST_EXECUTE_DRIFT`; t13 `DIFFERENT`, t14 wie t12 |

Die Schritte t12 bis t14 sind **nicht am echten Server gemessen**: ob `ALTER TABLE … DROP CONSTRAINT IF EXISTS` den Namen eines eindeutigen Index entfernt, ist offen (der Befund zu `DROP CONSTRAINT` misst nur Bedingungen).

## Befunde

- `CREATE INDEX` antwortet am echten Server mit `DONE`, Status 0, Befehl `0x00c8`, ohne `COUNT` (Zeilen 03 bis 06, beide Instanzen; vier Anweisungen: gewöhnlich, eindeutig, mit zwei Spalten; `INCLUDE`, Filter und absteigende Spalte nicht gemessen).
- `DROP INDEX [IF EXISTS] <Name> ON <Tabelle>` antwortet am echten Server bei Treffer und bei `IF EXISTS` ohne Treffer mit `DONE`, Befehl `0x00c9`, ohne `COUNT`, ohne weitere Token; ohne Treffer und ohne `IF EXISTS` mit 3701 (Class 11, State 7), nicht mit 3728 oder 3733 wie `DROP CONSTRAINT` (Tabelle A).
- Treffer sind gelesen in den Zeilen 07 (danach 3701 in Zeile 09), 12 (danach 3701 in Zeile 13) und 20 (ohne `IF EXISTS`, sonst 3701); die Zeilen 08, 11, 18 und 21 antworten wie ein Treffer, ob der Index fiel, ist nicht gelesen. Der Name des Index einer anderen Tabelle trifft nicht (Zeile 10, 3701 ohne `IF EXISTS`); Indexnamen gelten je Tabelle: derselbe Name an zwei Tabellen, `DROP INDEX … ON` der einen lässt die andere unberührt (Zeilen 07, 09, 20).
- Eine unbekannte Tabelle ist mit `IF EXISTS` ein Erfolg (anders als bei `DROP CONSTRAINT`, dort 4902), ohne `IF EXISTS` 3701 mit State 6 (Zeilen 18, 19).
- Der Index einer Eindeutigkeit und der eines Schlüssels sind mit und ohne `IF EXISTS` abgewiesen, 3723, State 5 bzw. 4, Class 16, Befehl des `DONE` `0x00fd` (Zeilen 14 bis 17); ein eindeutiger Index aus `CREATE UNIQUE INDEX` ist entfernbar (Zeile 12).
- Der Name des eindeutigen Index gilt ohne Beachtung der Schreibweise (Zeile 12; Kollation der Datenbank `tdswire_struktur` ist die Voreinstellung der Instanz, nicht eigens erhoben).
- Zwei Anweisungen in einem Text antworten mit zwei `DONE`; ein 3701 der ersten hindert die zweite nicht (Zeilen 22, 23).
- Der Testserver mit Form D6: ein zweites `schema migrate` mit geändertem oder umbenanntem Index endet mit Exit 0 und `IDENTICAL` (Tabelle C, t05 und t07).
- **Nicht gemessen:** ein gruppierter Index (außer dem Schlüssel), ein Index mit `INCLUDE`, mit Filter oder mit absteigender Spalte; `DROP INDEX` in einem anderen Schema als `dbo` und für Tabellen eines anderen Schemas, dazu ob die Zeilen 11 und 21 den Index entfernten oder ließen (nach beiden folgt kein Lesen; die Zeile 21 trägt als einzige das Schema `dbo` im Text); die Schreibweise für den gewöhnlichen Index und für `IF EXISTS` mit anderer Schreibweise; die Wirkung auf eine Tabelle mit Zeilen und innerhalb einer Transaktion; `DROP INDEX` mehrerer Namen (`DROP INDEX a ON t, b ON t`), mit `WITH (…)`, die alte Schreibweise `DROP INDEX <Tabelle>.<Name>`; Namen mit Sonderzeichen oder über 128 Zeichen; der Name eines Index, der eine Eindeutigkeit oder einen Schlüssel trägt, an einer Tabelle ohne dieses Element; d-migrate gegen den echten Server mit den Schritten t05, t07 und t12 (nur s14 ist am echten Server gefahren).
