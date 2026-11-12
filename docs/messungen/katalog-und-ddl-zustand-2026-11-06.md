# Katalog und Strukturänderung: d-migrate gegen den Testserver, Messprotokoll vom 2026-11-06

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (`AGENTS.md` §5 Regel 7), Träger: [slice-katalog-und-ddl-zustand](../plan/planning/done/slice-katalog-und-ddl-zustand.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Es setzt den [Spike der Callback-Lösung](callback-loesung-spike-2026-11-05.md) fort: dort lief ein Prototyp, hier läuft die gebaute Bibliothek mit dem Katalog-Anbieter aus [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Fassung F).

**Vorbehalt, der für jede Zeile gilt.** Gemessen ist das Verhalten von d-migrate 1.12.0 und mssql-jdbc 13.4.0 gegen den **Testserver dieses Repos**, nicht gegen einen echten SQL Server. Die Katalogantworten sind aus den Tabellen des Integrators abgeleitet; ihre Typnamen und Maße für `bigint`, `bit`, `time` und `datetime2` sind Setzung nach dem Handbuch des Servers, gemessen ist nur, dass d-migrate sie liest (Lauf `reverse-nach` unten). Die Gegenmessung der Abfragen von d-migrate am echten Server liegt vor: Zeilen M-16 bis M-21 im [Messprotokoll zum Referenzabgleich](referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07); die Sichten für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel, die hier leer antworten, trägt [slice-katalog-indizes-defaults-fremdschluessel](../plan/planning/done/slice-katalog-indizes-defaults-fremdschluessel.md). Die Zeiten sind Einzelmessungen unter wechselnder Last.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-06, Läufe zwischen 14:10 und 15:10 |
| Host | Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.2; Load Average (1 Minute) laut `uptime`: 38,5 um 14:21 (Entwicklungsläufe anderer), 12,2 um 15:04, 10,7 um 15:08 (Beginn des Messlaufs unten) |
| Client | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` |
| Treiber | `mssql-jdbc-13.4.0.jre11.jar` (im Image, Verzeichnis `/opt/d-migrate/lib`), Treiber des Dialekts `driver-mssql-1.12.0.jar` |
| Testserver | Stand `fd6d5286` des Repos plus die Änderungen des Slice (zum Zeitpunkt der Messung noch nicht committet); Image lokal gebaut (`make testserver-up` mit `TESTSERVER_IMAGE=tdswire-testserver:kd`), Abbild `sha256:bc17e237c3c09e32a609217e9e7cf95fbac2091d30db244ddf6a4970f9216a3c`, `TEST_UNENCRYPTED`, Benutzer `alice`, Port 24881 auf dem Host-Netz; Beschreibung `testserver/resources/manuell/sim.yaml` (Tabellen `kunden`, `bestellungen`, `BIGINT`-Schlüssel) |
| Mitschnitt | Ein Wegwerf-Wrapper um das Backend schrieb den Anfragetext und die Parameter jeder Anfrage auf die Standardausgabe und gab sie unverändert weiter; er steht nicht im Repo. Die Zahl der Anfragen und die Texte unten stammen aus ihm. **Grenze:** Die Spalten „Anfragen“ und „davon `sys.`“ sind Einzelmessungen an einem nicht committeten Stand mit einem Erzeuger, den niemand wiederholen kann; sie tragen nur als Größenordnung (zwei Dutzend Anfragen für `schema reverse`, mehr für `schema migrate --execute`). Wiederholt am Commit `5fe078e7` (Review vom selben Tag) sind die Exit-Codes und die Berichtsfelder der Läufe, nicht die Zahlen. Was im Repo trägt, sind die Handvektoren in `DMigrateWire.kt` und die Tests, die sie lesen. Die gesendeten Texte sind als Handvektoren in `src/test/kotlin/io/github/pt9912/tdswire/simulation/DMigrateWire.kt` abgelegt |
| URL | `mssql://alice:secret@127.0.0.1:24881/manuell?encrypt=false`, `docker run --rm --network host --user 1000:1000 -v <Arbeitsverzeichnis>:/work <Image> <Befehl>` |

## Läufe

Alle Läufe nacheinander gegen **einen** frisch gestarteten Server (der Bestand wächst durch `schema migrate`); „Anfragen“ ist die Zahl der Anfragetexte, die der Server im Lauf erhielt (einschließlich der Verbindungsprüfung `SELECT 1` und `SET LOCK_TIMEOUT 30000` des Pools), „davon `sys.`“ die mit Katalogabfragen.

| Nr | Befehl (gekürzt) | Exit | Anfragen | davon `sys.` | Ausgabe |
|---|---|---|---|---|---|
| k1 | `schema reverse --source <url> --output rev.yaml` | 0 | 26 | 22 | Schema geschrieben: beide Tabellen, `Id` als `biginteger`, `Name` als `text` mit `max_length: 100`, `Betrag` als `decimal` 10, 2, Schlüssel `Id` |
| k2 | `schema reverse --source <url> --include-all` | 0 | 27 | 24 | zusätzlich Sichten, Sequenzen, Routinen (leer) |
| k3 | `schema compare --source file:rev.yaml --target db:<url>` | 0 | 27 | 24 | `Status: IDENTICAL`, „No differences found.“ |
| k4 | `schema compare --source file:sim.yaml --target db:<url>` (die Beschreibung des Testservers als Soll) | 0 | 27 | 24 | `Status: IDENTICAL` |
| k5 | `schema migrate --source soll2.yaml --target db:<url> --execute --report …` (neue Tabelle `produkte` mit sieben Spalten aller Typen, neue Spalte `Email` an `kunden`) | 0 | 69 | 56 | Bericht: `status: ok`, `operationsTotal: 2`, `statementsAttempted: 2`, `transactionRolledBack: false`; die Nachprüfung („Post-execute compare“) fand keine Abweichung |
| k6 | `schema compare --source file:soll2.yaml --target db:<url>` | 0 | 35 | 32 | `Status: IDENTICAL` |
| k7 | `schema reverse` nach der Änderung | 0 | 34 | 30 | `produkte` (`biginteger`, `text` 200, `decimal` 10, 2, `boolean`, `datetime`, `time`, `integer`) und `kunden.Email` (`text`, `max_length: 200`) stehen im Schema |
| k8 | `schema compare --source file:soll.yaml …` mit `Id` vom Typ `identifier` und einer Spalte `Email` ohne `max_length` | **1** | 35 | 32 | `Status: DIFFERENT`: `type: identifier -> biginteger`, `type: text -> text(200)`, dazu eine Tabelle, die der Bestand nicht führt |
| k9 | derselbe `schema migrate` wie k5 noch einmal | 0 | 70 | 64 | Bericht: `status: no_op`, `operationsTotal: 0` |
| k10 | `schema migrate --source soll3.yaml …` (zusätzlich Tabelle `notizen` mit `Text` vom Typ `text` ohne `max_length`) | 0 | 85 | 72 | gesendet: `CREATE TABLE [notizen] (… [Text] NVARCHAR(MAX) …)`; Nachprüfung ohne Abweichung |
| k11 | `schema compare --source file:soll3.yaml …` | 0 | 43 | 40 | `Status: IDENTICAL` |

Zur Gegenprobe: in einem früheren Lauf desselben Tages, als die Bibliothek `NVARCHAR(MAX)` noch als 4000 Zeichen meldete, endete derselbe `schema migrate` mit Exit 5 („Post-execute compare detected drift“), und `schema compare` zeigte `text -> text(4000)` (Einzelmessung, ein Lauf; der Stand ist nicht mehr im Repo). Mit `maxLength` -1 für `MAX` bestehen k10 und k11.

## Gesendete Texte

Die Läufe k1 bis k11 sandten 24 verschiedene Texte (Einzelheiten in `DMigrateWire.kt`), davon:

| Gruppe | Text | Form |
|---|---|---|
| Verbindung | `SELECT 1`, `SET LOCK_TIMEOUT 30000` | Form C |
| Katalog | `SELECT SCHEMA_NAME() AS schema_name` | Form K |
| Katalog, aus den Tabellen | Tabellenliste (`sys.tables`, `sys.schemas`; Parameter `@P0` = Schema `dbo`), Spalten (`sys.columns` mit vier `LEFT JOIN`; Parameter `OBJECT_ID( @P0 )` = `[dbo].[kunden]`), Schlüssel (`sys.key_constraints`, `kc.type = 'PK'`) | Form K |
| Katalog, leer beantwortet | Eindeutigkeiten, Fremdschlüssel, Indizes, Volltextspalten, Partitionierung, Prüfbedingungen, Sequenzen, Sichten und Sichtspalten, Routinen, Routinenparameter, Abhängigkeiten, Objekte ohne Text | Form K |
| Struktur | `CREATE TABLE [produkte] (…, CONSTRAINT [pk_produkte] PRIMARY KEY ([Id]));`, `ALTER TABLE [kunden] ADD [Email] NVARCHAR(200);` | Form D |
| Transaktion | `set implicit_transactions on `, `IF @@TRANCOUNT > 0 COMMIT TRAN`, `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN ` | Form C |

Von den 20 Katalogformen berühren die Läufe 17; `sys.identity_columns`, `sys.computed_columns` und die Abfrage des größten Wertes stellt d-migrate nur beim Import (`data import`, nicht gemessen); ihr Wortlaut stammt aus dem Bytecode des Treibers. Der Parameter einer Katalogabfrage kam als `@P0` vom Typ `UnicodeText`; mssql-jdbc setzt `@P0` mit Leerzeichen davor und dahinter ein (`OBJECT_ID( @P0 )`). Der Wortlaut der Anfrage `SELECT t.name AS table_name …` enthielt `s.name =  @P0` mit zwei Leerzeichen; die Erkennung vergleicht deshalb nach der Normalisierung des Leerraums.

## Abweichungen vom Spike und von der Referenz

- Der Spike zählte 26 Anweisungen für `schema reverse` und 28 mit `--include-all` (c1); hier sind es 26 und 27 Anfragen, davon 22 und 24 mit `sys.`. Die Zahlen hängen an der Zahl der Tabellen (zwei) und daran, wie oft der Pool die Verbindung prüft; sie sind keine Zusage.
- Die Typen der Spalten der Katalogantworten sind die nächsten von `SqlType`: `smallint` und `tinyint` des Servers (`max_length`, `precision`, `scale`) heißen hier `int`. Am Referenzclient gemessen ist für die Katalogsicht `sys.columns` (M-1 in [referenzabgleich](referenzabgleich.md)): `name` `nvarchar`, `max_length` `smallint`, `precision` und `scale` `tinyint`, `is_nullable` und `is_identity` `bit`. Der Bau folgt dem Server, soweit `SqlType` es erlaubt; d-migrate liest die Antworten (Exit 0, `IDENTICAL`). Ob ein Client mit strenger Typprüfung die `int`-Werte anders liest, ist nicht gemessen.
- Eine Identität kennt `SimColumn` nicht: `is_identity` ist 0, ein Soll mit `identifier` zeigt `identifier -> biginteger` (k8, Exit 1). Das ist die Grenze aus [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), Konsequenzen.
- Eine Spalte aus der Beschreibung ohne `max_length` meldet der Katalog mit 4000 Zeichen (die Voreinstellung von `SimColumn.maxLength`); ein Soll mit `text` ohne `max_length` zeigt gegen sie `text -> text(4000)`. Eine Spalte, die ein Client mit `NVARCHAR(MAX)` anlegt, meldet `max_length` -1 und besteht (k10, k11).
- A-8 in [referenzabgleich](referenzabgleich.md) (`SchemaOnly`, `KeyInfo`, `SET FMTONLY`) ist nicht Gegenstand dieser Läufe: d-migrate stellt diese Texte bei `schema` nicht; `FMTONLY` stellt der Import (Massenladen).

## Grenzen der Messung

- Einzelmessungen auf einem Host unter wechselnder Last, ein Digest des Clients, eine Treiberversion, der Bestand eines Servers über alle Läufe.
- Nicht gemessen: ein echter SQL Server mit denselben Befehlen, `data export`, `data import` und `data transfer` gegen den Stand dieses Slice, mehrere Schemata, Rücknahme bei Fehler (der Spike misst den Fall, d1 in [callback-loesung-spike-2026-11-05](callback-loesung-spike-2026-11-05.md)), `--filter`, TLS, mehr als zwei Verbindungen, Soll-Dateien mit Fremdschlüsseln, Indizes oder Standardwerten (Träger: [slice-katalog-indizes-defaults-fremdschluessel](../plan/planning/done/slice-katalog-indizes-defaults-fremdschluessel.md)).
- Die Läufe stehen nicht in `make gates` und nicht in `make e2e`; es gibt dafür kein `make`-Ziel (die Entscheidung dazu liegt bei [slice-abnahme-d-migrate](../plan/planning/done/slice-abnahme-d-migrate.md)).
