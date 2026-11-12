# Standardwert an einer vorhandenen Spalte ersetzen (`DECLARE @df …`): Messprotokoll vom 2026-11-12

Messprotokoll zu [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (`AGENTS.md` §5 Regel 7), Träger: [slice-declare-df-standardwert-vorhandene-spalte](../plan/planning/in-progress/slice-declare-df-standardwert-vorhandene-spalte.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Es setzt das [Messprotokoll zu `DROP CONSTRAINT`](drop-constraint-d-migrate-2026-11-11.md) fort (dort Schritt s12: d-migrate sendet für den Standardwert einer vorhandenen Spalte einen Text mit `DECLARE @df`, der Testserver erkannte ihn nicht) und das [Messprotokoll zu Standardwert, Eindeutigkeit, Fremdschlüssel, Index und Prüfbedingung](katalog-indizes-defaults-fremdschluessel-d-migrate-2026-11-09.md) (dort Zeile c1). Der Text wird hier mit dem Referenzclient am **echten SQL Server** gemessen, dazu fährt d-migrate die Folge gegen den Testserver.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-12 |
| Host | Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Load Average (1 Minute) laut `uptime` 2,9 bis 3,6 beim Start der Läufe, 8,6 während der Mutationsläufe danach (Entwicklungsläufe anderer) |
| Instanzen | SQL Server 2022 (RTM-CU27-GDR) 16.0.4305.5, Image `mcr.microsoft.com/mssql/server:2022-latest@sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`; SQL Server 2025 (RTM-CU10) 17.0.5035.5, Image `mcr.microsoft.com/mssql/server:2025-latest@sha256:245f6b1c8903edae52cb20fa31419cefcde1a49938bb8c3447099011fc871ca3`; `MSSQL_PID=Developer`, nur lokal ([EULA-Entscheidung des Nutzers](../plan/adr/0008-dotnet-kompatibilitaetstests.md), Nutzer pt9912, für dieses Image bestätigt) |
| Clients | Referenzclient `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10.0.14 über den TLS-beendenden Proxy; d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, mssql-jdbc 13.4.0, `encrypt=false` (Tabelle B; der Text von d-migrate gegen den echten Server liegt in der Ablage des Laufs `schema`, Schritt s12) |
| Testserver | Arbeitsbaum des Slice mit Form D7 vor dem Commit der Quellen, Image lokal gebaut (`TESTSERVER_IMAGE=tdswire-testserver:df`), `TEST_UNENCRYPTED`, Benutzer `alice`, Port 24971, Beschreibung `testserver/resources/manuell/sim.yaml`; zum Vergleich der Stand mit Form D5 und D6 vor diesem Slice (Image `tdswire-testserver:drop`, Port 24972) |

**Erzeuger der Zahlen.** Im Repo liegen: das Szenario `struktur-standardwert-ersetzen` von `make vergleichslauf-d LAUF=bulk-2022|bulk-2025` (`referenztest-dotnet/Vergleich/MessungBulkTests.cs`; Ablage `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/<stufe>/struktur-standardwert-ersetzen.messung`, 41018 und 41038 Byte; die Aussagen der Tabelle A prüft `MessungBulkBelegTests`) und der Lauf `schema` (Ablage `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/<stufe>.schema.aufnahme.txt`, Schritt s12; aufgenommen am 2026-11-11, die Zeilen des Textes und seiner Antwort prüft ebenfalls `MessungBulkBelegTests`). Die Läufe gegen den **Testserver** (Tabelle B) sind von Hand mit `docker run` gefahren; die Skripte, die sie nacheinander ausführten und die Soll-Dateien schrieben, liegen nicht im Repo. Zeiten sind Einzelmessungen und tragen nur als Größenordnung: Lauf `bulk-2022` 46 s, `bulk-2025` 42 s (mit Bau des Client-Images aus dem Cache), die zwölf Schritte der Tabelle B 2 bis 4 s je Befehl. Die Antworten der Tabelle A sind beide Instanzen in jeder Zeile gleich (Tokenfolge, `DONE`-Felder, Fehler und Zeilen der Kontrollabfragen).

## A. Antworten des echten Servers auf den Text mit `DECLARE @df`

Der Wortlaut ist der von d-migrate 1.12.0 (Länge 419 Zeichen für Tabelle `auftraege` und Spalte `Menge`; Tabelle und Spalte stehen in `OBJECT_ID('<Tabelle>')`, `c.name = '<Spalte>'` und `ALTER TABLE [<Tabelle>]`):

```text
DECLARE @df sysname, @sql nvarchar(max);
SELECT @df = dc.name FROM sys.default_constraints dc
    JOIN sys.columns c ON c.object_id = dc.parent_object_id
        AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('auftraege')
        AND c.name = 'Menge';
IF @df IS NOT NULL
BEGIN
    SET @sql = 'ALTER TABLE [auftraege] DROP CONSTRAINT ' + QUOTENAME(@df);
    EXEC sp_executesql @sql;
END;
```

Szenario `struktur-standardwert-ersetzen`, Referenzclient, Batch, ohne `IMPLICIT_TRANSACTIONS` bis Zeile 19 (Zeile = Nummer der Anfrage in der Ablage). Aufbau in den Zeilen 01 und 02: `df_kind` mit den Spalten `Menge` (Standardwert `df_df_menge`, 1), `Status` (eigener Name `eigener_name`, `N'neu'`), `Ohne` (ohne Standardwert), `Zahl` (`df_zahl`, 7) und `Impl` (`df_impl`, 9); `df_zwei` mit `Menge` (`df_zwei_menge`, 5). „Treffer“ heißt: die Spalte hat einen Standardwert. Die Kontrollabfragen lesen `sys.default_constraints` der Tabelle.

| Zeile | Text | Antwort |
|---|---|---|
| 03 | Kontrolle vor dem Entfernen (`df_kind`) | vier Standardwerte: `Menge`, `Status`, `Zahl`, `Impl` |
| 04 | `ALTER TABLE [df_kind] ADD CONSTRAINT [df_df_menge2] DEFAULT 2 FOR [Menge];` (die Spalte hat schon einen Standardwert) | `ERROR ERROR DONE`: 1781 (Class 16, State 1, „Column already has a DEFAULT bound to it.“), 1750 (Class 16, State 0, „Could not create constraint or index. See previous errors.“), `DONE` Status `ERROR`, Befehl `0x00fd` |
| 05 | Text für `df_kind` und `Menge` (Treffer) | `DONE DONE DONE DONEINPROC RETURNSTATUS DONEPROC`: `DONE` `MORE\|COUNT` `0x00c1` mit 1, `DONE` `MORE` `0x00c0`, `DONE` `MORE\|COUNT` `0x00c1` mit 1, `DONEINPROC` `MORE` `0x00d8`, `DONEPROC` ohne Bits `0x00e0`; ohne Fehler |
| 06 | Kontrolle | drei Standardwerte: `Menge` ist weg, die übrigen bleiben |
| 07 | `ALTER TABLE [df_kind] ADD CONSTRAINT [df_df_menge] DEFAULT 2 FOR [Menge];` | Erfolg: `DONE` ohne Bits, ohne `COUNT`, Befehl `0x00d8` |
| 08 | Kontrolle | vier Standardwerte, `Menge` mit `((2))` unter dem Namen `df_df_menge` |
| 09 | Text für `Menge` (Treffer, der neue Standardwert) | wie Zeile 05 |
| 10 | Text für `Ohne` (kein Standardwert) | `DONE DONE`: `DONE` `MORE\|COUNT` `0x00c1` mit 0, `DONE` ohne Bits, Befehl `0x00c0`; ohne Fehler |
| 11 | Text für `Menge` (nach dem Entfernen, kein Standardwert) | wie Zeile 10 |
| 12 | Text für `STATUS` (Spalte in Großbuchstaben, eigener Name des Standardwerts, Treffer) | wie Zeile 05; der Standardwert fällt |
| 13 | Text für `DF_KIND` und `Zahl` (Tabelle in Großbuchstaben, Treffer) | wie Zeile 05; der Standardwert fällt |
| 14 | Text für `Gibtesnicht` (unbekannte Spalte) | wie Zeile 10 |
| 15 | Text für `df_gibtesnicht` und `Menge` (unbekannte Tabelle) | wie Zeile 10 |
| 16 | Kontrolle (`df_kind`) | nur `Impl` ist geblieben |
| 17 | Kontrolle (`df_zwei`) | `Menge` mit `((5))` |
| 18 | Text für `df_zwei` und `Menge` (Treffer) | wie Zeile 05 |
| 19 | Kontrolle (`df_zwei`) | kein Standardwert; die Tabelle `df_kind` blieb unberührt (Zeile 16) |
| 20 | `SET IMPLICIT_TRANSACTIONS ON` | `DONE` `0x00b9` |
| 21 | Text für `Ohne` (kein Standardwert) | `ENVCHANGE DONE DONE`: `ENVCHANGE` Typ 8 (Beginn der Transaktion) vor den zwei `DONE` wie Zeile 10 |
| 22 | Text für `Impl` (Treffer) | wie Zeile 05, ohne weiteres `ENVCHANGE` (die Transaktion ist offen) |
| 23 | Text für `df_gibtesnicht` (unbekannte Tabelle) | wie Zeile 10 |
| 24 | `IF @@TRANCOUNT > 0 COMMIT TRAN` | `DONE` `MORE` `0x00c0`, `ENVCHANGE` Typ 9, `DONE` `0x00d5` |
| 25, 26 | `SET IMPLICIT_TRANSACTIONS OFF`; Kontrolle (`df_kind`) | `DONE` `0x00ba`; kein Standardwert mehr (`Impl` fiel in Zeile 22 und blieb nach dem `COMMIT` weg) |
| 27 | Folgeabfrage `SELECT 1 AS eins` | Ergebnis mit einer Zeile; die Verbindung bleibt offen |

## B. d-migrate 1.12.0 gegen den Testserver

Die Tabellen `kunden` und `bestellungen` von `sim.yaml`, dazu `lieferanten` (`Id`, `Name`) und `auftraege` (`Id`, `LieferantId` mit Fremdschlüssel auf `lieferanten.Id`, `Menge` und `Status`); der Server ist frisch gestartet. Die Soll-Dateien unterscheiden sich in den Standardwerten: `a` ohne Standardwerte, `b` mit `Menge` 1 und `Status` `neu`, `c` mit `Menge` 2 und `Status` `neu`, `d` mit `Menge` 2 und ohne `Status`, `e` ohne beide. Befehle: `schema migrate --source /work/<Soll> --target db:mssql://alice:secret@127.0.0.1:<Port>/manuell?encrypt=false --execute --report /work/bericht.yaml`, `schema compare` und `schema reverse`, je als `docker run --rm --network host --user "$(id -u):$(id -g)"`.

| Schritt | Soll, Befehl | Exit Form D7 | Exit Stand vorher | Ergebnis mit Form D7 |
|---|---|---|---|---|
| s01 | `a`, `migrate` | 0 | 0 | `CREATE TABLE` für `lieferanten` und `auftraege`, `statementsAttempted` 2; s02 `IDENTICAL` |
| s03 | `b`, `migrate`: die Spalten haben keinen Standardwert, das Soll gibt beiden einen | **0** | **5** | `statementsAttempted` 4: je Spalte der Text mit `DECLARE @df` (kein Treffer, am Testserver ohne Änderung) und `ALTER TABLE [auftraege] ADD CONSTRAINT [df_auftraege_Menge] DEFAULT 1 FOR [Menge];` bzw. `[df_auftraege_Status] DEFAULT N'neu' FOR [Status]`; s04 `IDENTICAL` (vorher `DIFFERENT`) |
| s05 | `b`, zweiter Lauf | 0 | 5 | `status: no_op`, `statementsAttempted` 0 |
| s06 | `c`, `Menge` von 1 auf 2 | **0** | **5** | `statementsAttempted` 2: der Text mit `DECLARE @df` (Treffer) und `ADD CONSTRAINT [df_auftraege_Menge] DEFAULT 2 FOR [Menge];`; s07 `IDENTICAL` |
| s08 | `d`, `Status` ohne Standardwert | **0** | **5** | `statementsAttempted` 1: der Text mit `DECLARE @df` für `Status`; s09 `IDENTICAL` |
| s10 | `e`, `Menge` ohne Standardwert | 0 | 0 | `statementsAttempted` 1: der Text für `Menge`; s11 `IDENTICAL` |
| s12 | `schema reverse` | 0 | 0 | die Datei führt weder an `Menge` noch an `Status` ein `default` |

Die Spalte „Stand vorher“ ist derselbe Lauf gegen das Image `tdswire-testserver:drop` (ohne Form D7): ab s03 endet jeder Schritt, der den Text mit `DECLARE @df` sendet, mit Exit 5 (`Unsupported SQL.`) und der Vergleich mit Exit 1 (`DIFFERENT`); s10 und s11 gelingen dort, weil der Standardwert von `Menge` nie gesetzt wurde und das Soll keinen verlangt.

## Befunde

- Der Text mit `DECLARE @df` entfernt am echten Server (2022 und 2025) den Standardwert der Spalte, auch einen mit eigenem Namen (`eigener_name`, Zeile 12); er braucht den Namen nicht. Danach gelingt `ADD CONSTRAINT … DEFAULT … FOR` (Zeilen 06 bis 08). Ohne ihn endet das `ADD CONSTRAINT` an einer Spalte mit Standardwert mit 1781 und 1750 (Zeile 04).
- Ohne Standardwert an der Spalte, an einer unbekannten Spalte und an einer unbekannten Tabelle gelingt der Text ohne Fehler und ohne Änderung (Zeilen 10, 11, 14, 15, 21, 23); die Antwort ist dann kürzer (zwei `DONE`, das zweite mit Befehl `0x00c0`) als bei einem Treffer (vier `DONE`-Token, `RETURNSTATUS`, `DONEPROC`).
- Tabelle und Spalte gelten ohne Beachtung der Schreibweise (Zeilen 12 und 13; Kollation der Datenbank `tdswire_struktur` ist die Voreinstellung der Instanz, nicht eigens erhoben). Der Text wirkt nur auf die genannte Tabelle (Zeilen 16 bis 19).
- Unter `IMPLICIT_TRANSACTIONS` beginnt der erste Text eine Transaktion: `ENVCHANGE` Typ 8 vor der Antwort, ohne Treffer in Zeile 21, mit Treffer in Schritt s12 des Laufs `schema`; die folgenden Texte schreiben kein weiteres Typ 8 (Zeilen 22 und 23, die Transaktion ist offen).
- Der Testserver mit Form D7: `schema migrate --execute` mit einem Standardwert an einer vorhandenen Spalte (neu gesetzt, geändert, weggenommen) endet mit Exit 0 und `IDENTICAL` (Tabelle B, s03, s06, s08).
- **Abweichungen des Testservers vom echten Server (nicht gebaut, Entscheid beim Nutzer):** (1) Der Testserver antwortet auf den Text mit dem einen Abschluss `DONE` ohne `COUNT`, Befehl `0x00d8`; der Server sendet die Folge der Anweisungen des Textes (Tabelle A). Die öffentliche Fläche kann das nicht ausdrücken, ohne zu wachsen. (2) Ein zweiter Standardwert an derselben Spalte (`ADD CONSTRAINT … DEFAULT … FOR` ohne vorherigen Text) endet am echten Server mit 1781 und 1750; der Testserver antwortet `TDS_SIM_OBJECT_EXISTS` (2714). (3) Unter `IMPLICIT_TRANSACTIONS` schreibt der Server `ENVCHANGE` Typ 8 bei dem Text, der Testserver nur bei `CREATE TABLE` (bekannte Grenze der Transaktion).
- **Nicht gemessen:** d-migrate gegen den echten Server mit einem Soll, das einer Spalte ohne Standardwert einen gibt oder einen wegnimmt (nur s12 des Laufs `schema`, ein Standardwert ändert sich); der Text mit einer Tabelle in einem anderen Schema als `dbo`, mit Sonderzeichen im Namen (`'`, `[`, `]`, `.`, Leerzeichen) oder über 128 Zeichen: wie d-migrate sie schreibt, ist nicht aufgezeichnet, der Testserver erkennt solche Namen nicht; die Wirkung auf eine Tabelle mit Zeilen und in einer Transaktion des Referenzclients; ein Standardwert, der einen Index oder eine Prüfbedingung trägt; der Text in einem RPC statt als Batch.
