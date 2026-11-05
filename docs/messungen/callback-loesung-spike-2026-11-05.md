# Spike der Callback-Lösung mit d-migrate gegen den Testserver: Messprotokoll vom 2026-11-05

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (`AGENTS.md` §5 Regel 7), Grundlage von [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md). Es hält fest, was ein Wegwerf-Prototyp gemessen hat, nicht, ob eine Anforderung belegt ist. Es setzt die Sonde [d-migrate-sonde-2026-11-05](d-migrate-sonde-2026-11-05.md) fort: dort liefen die Katalogantworten aus einem aufzeichnenden Backend, hier kommen sie aus den Tabellen des Integrators.

**Vorbehalt, der für jede Zeile gilt.** Gemessen ist das Verhalten von d-migrate 1.12.0 und mssql-jdbc 13.4.0 gegen einen **Prototyp**, nicht gegen eine Datenbank und nicht gegen das Produkt. Was am echten SQL Server anders liefe, ist nicht gemessen (kein Lauf gegen ihn; die EULA-Entscheidung liegt beim Nutzer). Die Zeiten sind Einzelmessungen auf einem Rechner unter wechselnder Last; sie vergleichen Varianten desselben Laufs, sie sind keine Leistungszusage.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-05 |
| Host | Linux 6.8, 20 Kerne, 31 GiB; Load Average (1 Minute) zwischen 3,8 und 23,4 durch fremde Läufe (gelesen mit `uptime` um 17:23, 18:09 und 18:16) |
| Client | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` |
| Treiber | `mssql-jdbc` 13.4.0 (JDBC-Log: „Microsoft JDBC Driver 13.4.0.0 for SQL Server“) |
| Testserver | Stand `f12f360` des Repos plus Prototyp; Image lokal gebaut (Stufe `testserver` des Dockerfiles, Basis `gradle:8.14-jdk21@sha256:b3453e88792ea90b526f5d04fa2961bba183ae57ded4ad3437a49fb4e8381c06`), letztes Abbild `sha256:aae719f1f380d2b5d3bcfb1fa3a182f4127e5fb7812d6825529e7fc1af8763d2`; Docker 29.9.2, `--network host`, Verschlüsselung `TEST_UNENCRYPTED` (`encrypt=false`) |
| Prototyp | nur im Scratchpad der Sitzung, nie im Repo, nicht reproduzierbar abgelegt. Umfang: drei neue Dateien (Backend mit Katalog-Anbieter, Strukturänderung, MERGE-Zerlegung und Weitergabe des Massenladens 466 Zeilen; Decoder der Nutzlast von Paket 0x07 104 Zeilen; Bestand im Arbeitsspeicher als Integrator 161 Zeilen) und Eingriffe in neun Dateien der Bibliothek (Dispatch, Automat, RPC-Parameter, Handle-Grenze, PRELOGIN-Wert, Paketprotokoll) |
| Tabellen | `kunden` (`Id` INT, `Name`, `Eintritt` DATE) und `bestellungen` aus dem Beispiel des Testservers; für die Schreibläufe `g64` (`Id` BIGINT, `Name` NVARCHAR(4000), `Preis` DECIMAL(10,2), `Aktiv` BIT, `Eintritt` DATE, `Zeit` TIME, `Stempel` DATETIME2), angelegt von `schema migrate --execute` |

Die Abkürzungen bedeuten: **Anbieter** = der Prototyp fragt bei jeder Anfrage die aktuelle Tabellenliste des Integrators; **Bulk** = Standardpfad von `data import` und `data transfer` (SQLServerBulkCopy, Pakettyp 0x07); **MERGE** = Pfad `--on-conflict skip|update`.

## (a) MERGE-Zerlegung und Zeilenquelle nach Schlüssel

| Nr. | Lauf | Ergebnis |
|---|---|---|
| a1 | `data import --on-conflict skip`, `g64`, 2 Zeilen, eine davon vorhanden | Exit 0, „1 inserted, 1 skipped“; der Export zeigt 3 Zeilen. Das Backend las über die Zeilenquelle mit dem Schlüssel als Vorfilter und rief danach `onInsert` bzw. nichts |
| a2 | `--on-conflict update`, dieselben 2 Zeilen mit neuen Werten | Exit 0, „2 updated“; der Export zeigt die neuen Werte (`Name`, `Preis`, `Eintritt`, `Zeit`, `Stempel`) |
| a3 | `--on-conflict skip` in `kunden` (`Id` INT), Parameter kommt als Int64 | Ohne Verengung auf Int32 endet der Lauf mit Exit 5, „Invalid value.“ (die strenge Überführung weist Int64 in einer Int32-Spalte ab); mit Verengung im Wertebereich: Exit 0, „3 inserted“, der Export enthält 3 Zeilen einschließlich `Cäcilie ünï` |
| a4 | 20 000 Zeilen mit `--on-conflict skip`, Bestand mit Schlüsselsuche (Zeilenquelle beachtet den Vorfilter) | 8,8 s (alle eingefügt), 5,9 s (alle übersprungen); Handle-Grenze dafür auf 100 000 gesetzt (a6) |
| a5 | dasselbe, Zeilenquelle beachtet den Vorfilter nicht (liefert alle Zeilen, das Backend prüft den Schlüssel selbst) | 29,7 s (alle eingefügt), 40,5 s (alle übersprungen); das Ergebnis ist gleich, die Laufzeit wächst quadratisch mit dem Bestand |
| a6 | 20 000 Zeilen `--on-conflict skip` mit der Handle-Grenze 1000 aus [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) | Exit 5, „Prepared handle limit reached“ nach 1001 `MERGE` je Sitzung (d-migrate nutzte zwei Sitzungen: 2002 `MERGE`); in diesen 2002 Anweisungen kam kein `sp_unprepare` an |
| a7 | RPC-Parameter des `MERGE` | `DECIMALN` mit Typlänge 17 (Precision 38, Scale 2) trägt den Wert in 2 bis 4 Byte (in 2004 Parametern gezählt: 8 mit 2, 652 mit 3, 1344 mit 4 Byte); der RPC-Parser des Repos verlangt genau 17 und weist ab (`INVALID_LENGTH`), d-migrate meldet „Protocol error.“ und die Verbindung schließt. Spalten `TIME` und `DATETIME2` kommen als `DATETIMN` (8 Byte, ausgelesen als `Time` mit Datum 1970-01-01 bzw. als Zeitpunkt), das der Katalog der Typen nicht kennt („Unsupported RPC.“). Mit angepasstem Parser (Prototyp) laufen a1 bis a5 |

## (b) Massenladen, Weg A

Ablauf (gemessen): `INSERT BULK [dbo].[g64] ([Id] BIGINT , …) with (CHECK_CONSTRAINTS, FIRE_TRIGGERS, ROWS_PER_BATCH = <n>)` als SQL-Text, danach eine Nachricht vom Typ 0x07 mit COLMETADATA (Token 0x81), je Zeile ein ROW (0xD1) und DONE (0xFD); davor `set fmtonly on SELECT [Id], … FROM [dbo].[g64] WHERE 1 = 0 set fmtonly off`, `sp_executesql N'SET FMTONLY ON SELECT * FROM [dbo].[g64] '` (als **SQL-Text**, nicht als RPC) und `select collation_name, is_computed from sys.columns where object_id=OBJECT_ID('[dbo].[g64]') order by column_id ASC` auf einer zweiten Verbindung. Nach dem letzten Teil folgt eine Nachricht 0x07 mit COLMETADATA und ohne Zeile (beobachtet in jedem Import). Der Prototyp dekodiert die ganze Nachricht im Speicher und ruft je Zeile den Einfüge-Callback.

### (i) Zeilenzahl und Inhalt

| Nr. | Lauf | Ergebnis |
|---|---|---|
| b1 | `data import` (JSON, 2 Zeilen) in `g64`, danach `data export` | Exit 0, „2 inserted“; der Export enthält beide Zeilen mit Wert und NULL, `Preis` als `"12.50"`, `Zeit` als `13:14:15`, `Stempel` als `2026-01-02T13:14:15` |
| b2 | `data transfer --tables g64` von einem Testserver zu einem zweiten | Exit 0; die beiden Exporte sind byte-gleich (`cmp`). Ohne Anpassung scheitert er mit „Backend error.“, weil der Treiber den Zeitpunkt als `2026-01-02 13:14:15.0` (Leerzeichen, Nachkommastelle) sendet, die Überführung der Bibliothek aber das `T` verlangt; im Prototyp gelockert |
| b3 | `data import` in `kunden` (`Id` INT) und in jede Tabelle mit INT-Spalte | **scheitert im Treiber vor dem Server**: `An error occurred while converting the Long value to JDBC data type INTEGER` (`ClassCastException: Long cannot be cast to Integer` in `SQLServerBulkCopy.writeColumnToTdsWriter`). Der Server erhält die Nachricht 0x07 abgeschnitten mit gesetztem IGNORE-Bit (b8). Gleich mit CSV statt JSON und mit `--schema`. Dasselbe für ein ganzzahlig geschriebenes `DECIMAL` im JSON (`"Preis": 1.00`): `…the Long value to JDBC data type DECIMAL`. Mit gebrochenem Wert (`12.50`) und mit BIGINT läuft der Import. Die Ursache liegt in der Wertliste, die d-migrate dem Treiber gibt; ob ein echter SQL Server hier anders antwortet, ist nicht gemessen |
| b4 | Bulk abschalten | Aus dem Bytecode von d-migrate gelesen (`MssqlBulkCopyFastPath.isEligible`): Bulk nur bei `--on-conflict abort` (Voreinstellung) und wenn alle Spaltentypen in einer festen Menge liegen; `--on-conflict skip` oder `update` nimmt den Pfad MERGE (a1 bis a5, gemessen). In der vollständigen Ausgabe von `data import --help` (65 Zeilen) steht kein Schalter für Bulk (kein Treffer für „bulk“, „insert“, „batch“); `data transfer --help` ist nicht gelesen |

### (ii) Fehler mitten im Strom

| Nr. | Lauf | Ergebnis |
|---|---|---|
| b5 | Integrator wirft bei der 3. von 5 Einfügungen | d-migrate: `SQLServerException: Backend error. Msg 50004, Level 16, State 1`, „Import failed: Backend error.“, Exit 5. Der Bestand enthält danach die Zeilen mit `Id` 11 und 12 (keine Transaktion); 13 bis 15 fehlen |
| b6 | zweiter Import derselben 5 Zeilen (Schlüsselverletzung, der Integrator wirft) | Exit 5, `Msg 50004`, 5 Zeilen im Bestand. Die Fehlernummer 2627 (Schlüsselverletzung des echten Servers) wird **nicht** gesendet; d-migrate sieht 50004 |
| b7 | wie b6 mit `--on-error skip` und `--on-error log` | „5 rows processed, 5 failed“, „Failed to import table 'g64': Backend error.“, Exit 5 in beiden Fällen |
| b8 | Treiberfehler in Zeile 2 von 3 (b3-Ursache): der Client bricht die Nachricht 0x07 mit IGNORE-Bit ab (Status 3: EOM und IGNORE), Zeile 1 steht vollständig davor | Prototyp **ohne** Beachtung des IGNORE-Bits: Zeile 1 steht im Bestand (der Client wollte sie verwerfen); **mit** Beachtung: Bestand leer, Exit 5 |

### (iii) Typen, die d-migrate sendet

Aus dem `INSERT BULK`-Text und dem COLMETADATA der Nachricht 0x07 (gemessen, `g64` und `typen`):

| Quelle (Spalte der Tabelle) | `INSERT BULK`-Text | COLMETADATA | Wert in der Zeile |
|---|---|---|---|
| BIGINT | `BIGINT` | `INTN`, 8 | Long (läuft) |
| INT | `INT` | `INTN`, 4 | Long (scheitert im Treiber, b3) |
| NVARCHAR | `NVARCHAR(4000)` | `NVARCHAR`, 8000 Byte, Collation | String |
| DECIMAL(10,2) | `DECIMAL(10, 2)` | `DECIMALN`, Länge 17, Precision 10, Scale 2 | BigDecimal (läuft mit Bruchteil; ganzzahlig scheitert, b3) |
| BIT | `BIT` | `BITN`, 1 | Boolean |
| DATE | `VARCHAR(10)` | `BIGVARCHAR`, 10 | String `2026-01-02` |
| TIME | `VARCHAR(16)` | `BIGVARCHAR`, 16 | String `13:14:15` |
| DATETIME2 | `VARCHAR(27)` | `BIGVARCHAR`, 27 | aus JSON `2026-01-02T13:14:15`, aus `data transfer` `2026-01-02 13:14:15.0` |

Nicht gemessen: SMALLINT, TINYINT, FLOAT, UUID, Binärtypen, `NVARCHAR(MAX)`, Dezimal mit Precision über 10, Zeit mit Nachkommastellen, NULL in jedem Typ außer den in b1 gezeigten. FLOAT kam in keinem Lauf vor: die Beschreibung der Tabellen weist `float` ab ([SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)), es gab also keine Spalte, die ihn hätte erzwingen können.

### (iv) Beschränkter Speicher und Gegendruck

Testserver mit `-Xmx` wie angegeben, Bestand zählt nur (kein Speichern), JSON mit 500 000 Zeilen (rund 92 MB), je Zeile rund 159 Byte in der Nachricht, Stichprobe des Heaps alle 2 s (wenige Stichproben je Lauf: das Maximum ist eine Untergrenze).

| Nr. | `--chunk-size` | Heap des Servers | Nachricht 0x07 | Ergebnis |
|---|---|---|---|---|
| b9 | 10 000 (Voreinstellung) | 64 MiB | 100 Nachrichten zu 1,55 bis 1,59 MB | Exit 0, 500 000 Zeilen, 6,2 s gesamt (mit Start der JVM von d-migrate), Heap-Stichprobe höchstens 39 MiB |
| b10 | 100 000 | 64 MiB | 10 Nachrichten zu 15,7 bis 15,9 MB (knapp unter 16 MiB) | Exit 0, 6,8 s, Stichprobe höchstens 54 MiB |
| b11 | 120 000 | 64 MiB | über 16 MiB (Grenze [SPEC-007](../../spec/spezifikation.md#3-defaults-und-konstanten)) | Exit 5, „Connection reset by peer“, 0 Nachrichten verarbeitet: der Server schließt die Verbindung |
| b12 | 100 000 | 32 MiB | 15,7 bis 15,9 MB | Der Server endet mit `OutOfMemoryError` (`-XX:+ExitOnOutOfMemoryError`), d-migrate: „SQL Server returned an incomplete response“, Exit 5 |
| b13 | 10 000 | 64 MiB | 1,55 bis 1,57 MB, **500 µs Verzögerung je Einfügung** | Exit 0, 50 000 Zeilen in 31,4 s (rund 5,8 s je Nachricht zu 10 000 Zeilen); der Client wartet auf den Callback, Heap-Stichprobe höchstens 43 MiB |

Die Nachricht wird als Ganzes aus den Paketen zusammengesetzt, bevor die erste Zeile den Callback erreicht (`MessageAssembler`, Grenze 16 MiB). Gegendruck gibt es damit zwischen den Zeilen einer Nachricht und zwischen den Nachrichten, nicht innerhalb einer Nachricht; der Speicherbedarf ist mindestens die Größe der Nachricht und nach b12 mehr als das Doppelte davon.

### Variante: Zeile als Einfügeanweisung über `QueryBackend.execute`

| Nr. | Lauf | Ergebnis |
|---|---|---|
| b14 | b9 mit dem Pfad „jede Zeile wird zu `INSERT INTO [dbo].[g64] ([Id], …) VALUES (…)` mit Literalen und über `execute` an das Backend gegeben“ (kein neuer Port, `QueryBackend` unverändert), Katalog-Anbieter wird je Anweisung neu gefragt | Exit 0, 500 000 Zeilen, 14,8 s gesamt (gegen 6,2 s im direkten Pfad b9), Heap-Stichprobe höchstens 32 MiB. Der erste Versuch endete mit `OutOfMemoryError` durch ein Leck des Prototyps (Registrierung je Anfrage nicht entfernt); nach der Behebung lief er durch |

## (c) Katalog-Anbieter und Strukturänderung

| Nr. | Lauf | Ergebnis |
|---|---|---|
| c1 | `schema reverse` (2 Tabellen) | Exit 0, 26 Anweisungen, davon 18 `sys.*`; `--include-all` 28 Anweisungen, davon 20 `sys.*`. Die Tabellen und Spalten stimmen mit der Beschreibung; `Id` kommt als `integer`, `Name` als `text` mit `max_length: 4000`, `Eintritt` als `date`, `Betrag` als `decimal` mit 10 und 2 |
| c2 | `schema migrate --execute` mit einem Soll, das `produkte` neu anlegt und `kunden` um `Email` erweitert (Texte mit `max_length: 4000`) | Exit 0; d-migrate sendet `set implicit_transactions on`, `CREATE TABLE [produkte] (… CONSTRAINT [pk_produkte] PRIMARY KEY ([Id]))`, `ALTER TABLE [kunden] ADD [Email] NVARCHAR(4000)`, `IF @@TRANCOUNT > 0 COMMIT TRAN`, `set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN`, danach erneut Katalogabfragen der Nachprüfung; „Post-execute compare“ ohne Abweichung |
| c3 | danach `schema reverse` | Exit 0; die Ausgabe führt `bestellungen`, `kunden` (mit `Email`) und `produkte` mit allen fünf Spalten |
| c4 | Gegenprobe: fester Katalog (Schnappschuss beim Start) statt Anbieter | `schema migrate --execute` endet mit Exit 5, „Post-execute compare detected drift“; mit Anbieter Exit 0. `schema reverse` zeigt mit festem Katalog nur 2 Tabellen |
| c5 | dasselbe Soll mit `max_length: 100` bzw. 200 für Texte | Exit 5, „Unsupported SQL.“: d-migrate plant `ALTER COLUMN [Name] NVARCHAR(100)` und sendet davor einen T-SQL-Block (`DECLARE @df sysname …; SELECT @df = dc.name FROM sys.default_constraints …; EXEC sp_executesql @sql;`), weil der Katalog `nvarchar(4000)` meldet (`SqlType` kennt keine Länge, [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)) |
| c6 | `schema compare --source file:… --target db:…` mit dem Soll aus c2 | Exit 1 mit Unterschied `type: identifier -> integer` je Schlüsselspalte (`SimColumn` trägt keine Identität); sonst gleich |

## (d) Mehrere Callbacks einer Anweisung, Fehler in der Mitte

| Nr. | Lauf | Ergebnis |
|---|---|---|
| d1 | `schema migrate --execute` wie c2, die 2. Strukturänderung wirft | Exit 5, Meldung „Backend error.“. d-migrate sendet danach `IF @@TRANCOUNT > 0 ROLLBACK TRAN`, das das Backend mit dem Zähler 0 beantwortet (ohne Wirkung). Der Bericht von d-migrate nennt `transactionRolledBack: true` und `recoverability: FULL_ROLLBACK_CONFIRMED`; `schema reverse` zeigt `produkte` **weiterhin** im Bestand (die 1. Änderung wurde nicht zurückgenommen) |
| d2 | Bulk mit Fehler in der 3. Einfügung | siehe b5: die ersten beiden Zeilen bleiben |
| d3 | MERGE-Zerlegung | je Anweisung ein Lesen über die Zeilenquelle und höchstens ein Callback (aus dem Prototyp gelesen); ein Fehler des Callbacks mitten in einer MERGE-Anweisung ist nicht gemessen |

## Einschaltung

| Nr. | Lauf | Ergebnis |
|---|---|---|
| e1 | ohne Strukturänderungs-Callback (Anbieter gesetzt) | `schema reverse` Exit 0; `schema migrate --execute` Exit 5 (Anweisung abgewiesen) |
| e2 | Tabellen ohne `onInsert`, `onUpdate`, `onDelete` | Bulk: `Backend error.` (Prototyp-Fehler des fehlenden Callbacks), Exit 5; MERGE: „Internal error.“ mit geschlossener Verbindung, weil der Prototyp den fehlenden Callback nicht abfängt (kein Verhalten des Entwurfs); Export und Katalog laufen, der Bestand bleibt leer |

## PRELOGIN-Version

Der Prototyp setzt die Version der PRELOGIN-Antwort aus einer Umgebungsvariablen; `schema reverse --include-all` gegen jeden Wert:

| Version | Ergebnis |
|---|---|
| 0.0.0.0 | „SQL Server version 0 is not supported by this driver“ |
| 8.0.0.0 | „SQL Server version 8 is not supported by this driver“ |
| 9.0.0.0, 10.0.0.0, 11.0.0.0, 12.0.0.0, 13.0.0.0, 15.0.0.0, 16.0.1000.6, 17.0.0.0 | Exit 0, Schema geschrieben |
| 255.0.0.0 | „SQL Server version -1 is not supported by this driver“ (der Treiber liest das Byte vorzeichenbehaftet) |

Gemessen ist nur `schema reverse` bei diesen Werten; ob andere Funktionen von d-migrate oder der Treiber bei einer anderen Version anders verfahren, ist nicht gemessen.

## Weitere Beobachtungen

- Die Katalogabfragen von `schema reverse` (Tabellen, Spalten, Schlüssel, Fremdschlüssel, Indizes, Volltext, Partitionen, Prüfbedingungen) und die für Sequenzen, Routinen und Abhängigkeiten stehen als Text im Bytecode von `driver-mssql-1.12.0.jar` (Klassen `MssqlMetadataQueries`, `MssqlRoutineQueries`), ihre Spaltennamen sind dort ablesbar; zusätzlich sendet d-migrate `SELECT SCHEMA_NAME() AS schema_name`, `sys.identity_columns` und `sys.computed_columns` je Tabelle vor einem Import.
- Die Zeit eines Parameters kam als `DATETIMN` mit dem Datum 1970-01-01; ein Parameter `NULL` für Datum, Zeit und Zeitpunkt kam als `null` an.

## Grenzen der Messung

- Einzelmessungen auf einem Host unter Last, ein Digest des Clients, eine Treiberversion; die Zeiten haben keine Streuung.
- Der Prototyp löst die Funktionen mit Text- und Regelabgleich auf dem Wortlaut von d-migrate 1.12.0, nicht mit der Grammatik der Bibliothek; was für andere Formulierungen gälte, ist nicht gemessen.
- Nicht gemessen: ein Lauf von d-migrate gegen einen echten SQL Server (b3 kann dort anders enden), Parquet und CSV beim Export, `--filter`, `--since`, mehrere Schemata, Abbruch durch den Nutzer (ATTENTION) während des Massenladens, TLS (Teil von [ADR-0032](../plan/adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md)), mehr als zwei gleichzeitige Verbindungen.
- Aufgeräumt nach dem Spike: Testserver-Container und die lokalen Abbilder des Prototyps entfernt.
