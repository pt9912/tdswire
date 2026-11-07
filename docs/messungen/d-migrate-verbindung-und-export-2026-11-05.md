# d-migrate meldet sich an und exportiert: Messprotokoll vom 2026-11-05

Messprotokoll zu [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) und [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (`AGENTS.md` §5 Regel 7), Träger: [slice-d-migrate-verbindung-und-export](../plan/planning/done/slice-d-migrate-verbindung-und-export.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Gegenstand sind die Hürden 2 bis 5 und 8 der [Sonde](d-migrate-sonde-2026-11-05.md); anders als dort läuft der Testserver ohne Eingriffe, ohne aufzeichnendes Backend und ohne Protokoll-Hacks.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Host | 2026-11-05, Linux 6.8, 20 Kerne, 31 GiB; Last (`uptime`, Lastmittel 1/5/15 min) während der Läufe 13,90 bis 21,10 durch fremde Läufe (Spanne aus den `uptime`-Zeilen der Läufe, nicht je Befehl wiedergegeben) |
| Testserver | Quelltext des Commits `39c8872` (Zweig `slice-d-migrate-verbindung-und-export`); der spätere Commit `75ca5c7` änderte danach nur die Bildung des Antwortstroms der Form C (`asFlow()` statt Spread-Operator in `SimulationBackend.kt`), die Läufe sind nicht auf dem Stand von `75ca5c7` wiederholt, `make testserver-up` mit `TESTSERVER_NAME=tdswire-vexp`, `TESTSERVER_PORT=24871`, `TESTSERVER_IMAGE=tdswire-testserver:vexp`; Image-ID `sha256:e592424832726ff99115373fbbaf227b1b81fa82b9c45207476ffa7af8047d79`; Beschreibung `src/testserver/resources/manuell/sim.yaml` (Tabellen `kunden` und `bestellungen`, ohne Zeilen); Benutzer `alice`, `TEST_UNENCRYPTED` |
| Client | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, Treiber `mssql-jdbc-13.4.0.jre11` (Pfad im JDBC-Log) |
| URL | `mssql://alice:secret@127.0.0.1:24871/manuell?encrypt=false`, `docker run --rm --network host` |
| Messgrenzen | Einzelläufe, ein Host, ein Digest des Clients, eine Treiberversion. Kein Server-Log (der Testserver schreibt keines für Anweisungen); was der Server empfing, steht nur im Ausgang des Clients. Leere Tabellen: Zeilen im Export sind nicht belegt. |

## Aufzeichnung der Vektoren (Quelle der Tests)

Die zwei Nutzlasten in `RpcPositionalNamesTest` und die Texte in `FixedFormsTest` stammen aus einem Lauf mit einer **vorübergehenden Instrumentierung** des Testservers (Ausgabe der RPC-Nutzlast als Hex vor dem Parser und des SQL-Textes samt Parametern vor dem Planer auf der Standardfehlerausgabe, `docker logs`). Die Instrumentierung ist nicht im Repo und nicht committet; aufgezeichnet sind Befehle `data export --tables kunden` (mit und ohne `--filter "Id = 7"`) und `data import`. Reproduzierbar ist der Weg über dieselbe Instrumentierung, nicht über ein Make-Target.

Aufgezeichnete SQL-Texte der Läufe (Batch, soweit nicht `sp_executesql` genannt): `SELECT 1`; `SET LOCK_TIMEOUT 30000` (nach jedem Verbindungsaufbau, auch bei parallelen Verbindungen); `sp_executesql` mit einem Parameter (`SELECT * FROM [kunden] WHERE 1 = 0`, danach `SELECT * FROM [kunden]`); `sp_executesql` mit Text, Deklaration `@P0 bigint` und einem Wert ohne Namen (`SELECT * FROM [kunden] WHERE [Id] =  @P0 `, Wert 7 als `INTN` 8); bei `data import` ein Batch mit sieben `SET`-Anweisungen (`ANSI_NULLS ON`, `ANSI_PADDING ON`, `ANSI_WARNINGS ON`, `ARITHABORT ON`, `CONCAT_NULL_YIELDS_NULL ON`, `NUMERIC_ROUNDABORT OFF`, `QUOTED_IDENTIFIER ON`, durch `;` und Zeilenumbruch getrennt) und danach `SELECT SCHEMA_NAME() AS schema_name`.

## Ergebnis je Funktion

Alle Befehle: `docker run --rm --network host -v <Arbeitsverzeichnis>:/work <Image> <Befehl>` mit der URL oben als `--source` beziehungsweise `--target`.

| Funktion | Befehl (gekürzt) | Exit | Ergebnis |
|---|---|---|---|
| Anmeldung, Pool, Verbindungstest | in jedem Befehl | – | Anmeldung, `SELECT 1` und `SET LOCK_TIMEOUT 30000` werden angenommen (JDBC-Log: `Executing (not server cursor) SELECT 1`, `SET LOCK_TIMEOUT 30000`, kein Fehler); ein Pool mit mehreren Verbindungen sendet `SET LOCK_TIMEOUT 30000` je Verbindung |
| `data export` JSON | `data export --source <url> --format json --tables kunden -o /work/kunden.json` | 0 | „Exported 1 table(s) (0 rows, 0.00 MB)“; Datei `[]` |
| `data export` JSON | `... --tables bestellungen -o /work/bestellungen.json` | 0 | 0 Zeilen; Datei `[]` (Spalte `Betrag`: Dezimal als Metadaten) |
| `data export` CSV | `... --format csv --tables kunden -o /work/kunden.csv` | 0 | Datei mit Kopfzeile `Id,Name,Eintritt` |
| `data export` YAML | `... --format yaml --tables bestellungen` | 0 | 0 Zeilen |
| `data export` mit Textfilter | `... --tables kunden --filter "Name = 'Ada'"` | 0 | 0 Zeilen; der Wert kommt als Parameter ohne Namen an und wird als `@P0` gebunden |
| `data export` mit `IS NULL` | `... --filter "Name IS NULL"` | 0 | 0 Zeilen |
| `data export` mit Ganzzahlfilter | `... --filter "Id = 7"` | 5 | „Failed to export table 'kunden': Invalid value.“ (`TDS_SIM_INVALID_VALUE`); Ursache aus dem Code gelesen, nicht gemessen: der Wert kommt als `bigint` (`Int64`), die Spalte `Id` ist `Int32`, und die Überführung weitet nur `Int32` auf `Int64`; so auch bei `Eintritt = '2020-01-02'` neben einem Textfilter: „Invalid value.“ (Ursache dort nicht untersucht) |
| `data export` ohne `--tables` | `data export --source <url> --format json -o /work/all.json` | 4 | „Failed to list tables: Unsupported SQL.“ (Katalog `sys.*`) |
| `schema reverse` | `schema reverse --source <url> --output /work/rev.yaml` | 4 | „[ERROR] Connection or metadata error: Unsupported SQL.“ (Katalog `sys.*`) |
| `data import` | `data import --target <url> --source /work/kunden.json --table kunden` | 5 | „Import failed: Unsupported SQL.“; der Batch mit den sieben `SET` wurde angenommen, danach scheitert `SELECT SCHEMA_NAME() AS schema_name` (aus dem Mitschnitt der Instrumentierung, nicht aus dem Ausgang des Clients) |
| `data profile` | `data profile --source <url> --tables kunden` | 5 | „Failed to list tables: Unsupported SQL.“ (Katalog `sys.*`) |

Aufgezeichneter Auszug (`data export --tables kunden`, Exit 0, 13:02:37 Ortszeit, Last 17,20 21,10 17,31):

```text
Starting run 4d6d8d0e-aa56-4bb0-b067-cd5a55c5bf7d: Exporting 1 table(s)
Exporting table 'kunden' (1/1)
Exporting table 'kunden' | chunk 1 | 0 rows | 0.00 MB
Exported table 'kunden' | 0 rows | 1 chunks | 0.00 MB
Exported 1 table(s) (0 rows, 0.00 MB) in 0.11 s
```

## Vorher und nachher

Vorher (gleicher Client, `data export`/`schema reverse` mit der Version `16.0.1000.6`, Stand `fbd2a44`): `Failed to initialize pool: Unsupported SQL.` (Lauf B in [prelogin-serverversion-2026-11-05](prelogin-serverversion-2026-11-05.md), dort für `schema reverse`; hier nicht erneut gemessen). Nachher: Exit 0 für `data export` einer Tabelle des Simulations-Backends. Ein erster Lauf der Instrumentierung scheiterte an der Messanordnung (das Arbeitsverzeichnis stammte aus der Sonde, die Ausgabedatei war nicht überschreibbar: Exit 5, „Export failed“, ohne Anweisung nach `SET LOCK_TIMEOUT`); Wiederholung in einem neuen Verzeichnis: Exit 0. Die Zwischenstufen (nur `SELECT 1` und `SET`, ohne die RPC-Änderung) sind nicht einzeln gemessen.

`SET IMPLICIT_TRANSACTIONS` ist in diesem Protokoll nicht gemessen: die Option steht in der Liste der Form C nach der Beobachtung der [Sonde](d-migrate-sonde-2026-11-05.md) (`schema migrate --execute`), in keinem Lauf hier.

## Was danach scheitert (nur gemeldet, nicht behoben)

- Katalogabfragen `sys.*` und `INFORMATION_SCHEMA`, `SELECT SCHEMA_NAME()` (Hürde 6 der Sonde): `data export` ohne `--tables`, `schema reverse`, `data profile`, `data import`; Träger: [slice-katalog-und-ddl-zustand](../plan/planning/done/slice-katalog-und-ddl-zustand.md).
- Schreiben (`data import`, Hürden 9 und 10): [slice-schreibpfad-bulk-load](../plan/planning/done/slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](../plan/planning/in-progress/slice-schreibpfad-merge-output.md).
- Parameter, die nicht verlustfrei in den Spaltentyp passen (`bigint` gegen `Int32`): kein Träger benannt, Register-Eintrag [parametertyp-des-clients-passt-nicht-zur-spalte](../plan/planning/observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/observation.md); die Grenze liegt in der strengen Überführung von [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), `--filter` steht nicht im Soll von [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
