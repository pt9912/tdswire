# Referenz-Workload für die Performance-Messung

Referenz-Workload zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), Träger [slice-qa-performance](planning/in-progress/slice-qa-performance.md). **Stand: die Antworten des Nutzers auf O1 bis O7 (2026-11-15, Wortlaut „01-07 Deine Empfehlungen“, Entscheider: Nutzer, `pt9912`) sind in die Abschnitte 3 bis 6 eingearbeitet; Zuschnitt freigegeben vom Nutzer am 2026-11-15 („referenz-workload.md freigabe“); die Freigabe dieses eingearbeiteten Stands steht aus (Review, dann Nutzer). Gemessen sind nur Probezeilen des Werkzeugs ([Messprotokoll](../messungen/lasttest-probe-2026-11-15.md)), kein Grenzwert.** Dieses Dokument legt keinen Grenzwert fest; die Grenzwerte stehen nach der Messung und der Entscheidung des Nutzers im Lastenheft (Tabelle der Abnahmeunterlagen: „Grenzwerte für Durchsatz, p95-Latenz, Sitzungen und Lastprofil“, Betreiber der .NET-Anwendung, vor Abnahme). Jede Zahl unten ist eine **Setzung** und trägt das Wort, oder sie ist als Zitat mit Quelle gekennzeichnet (`AGENTS.md` §3.8).

## 1. Herkunft der Form (d-migrate als Verbraucher)

Entscheidung des Nutzers vom 2026-11-15: Die Form des Workloads stammt aus d-migrate, dem Verbraucher des Servers ([TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)). Die Zahlen von d-migrate sind Ziele von d-migrate gegen echte Datenbanken, **keine Werte für tdswire**; hier gelten sie nur als Herkunft der **Form** (Größenordnung der Zeilenzahl, Lese- und Schreibmuster, Form der Metrik). Belege am Stand `a33984910` des Repos d-migrate (nur gelesen):

| Was d-migrate zeigt | Beleg | Was tdswire daraus nimmt |
|---|---|---|
| Export mindestens 10.000 Datensätze/s, Import mindestens 5.000/s | spec/lastenheft-d-migrate.md:294, :296 | Metrik „Zeilen/s“ je Richtung; **nicht** die Schwelle |
| Export von 1 Mio Datensätzen, Import von 1 Mio Datensätzen als Basis-Performance | spec/lastenheft-d-migrate.md:645-648 | Größenordnung 1 Mio Zeilen für den Hauptlauf |
| Export von 10 Mio Datensätzen ohne Out-of-Memory-Fehler | spec/lastenheft-d-migrate.md:650-652 | Größenordnung 10 Mio Zeilen für den Strom-Heap-Lauf (nur Lesen) |
| Parallele Verarbeitung: mindestens 5x Speedup bei 8 CPU-Kernen; mindestens 10 Tabellen gleichzeitig | spec/lastenheft-d-migrate.md:654, :302 | Staffel gleichzeitiger Sitzungen mit Stufen bei 8 und 16 (aus 8 Kernen und 10 Tabellen, Setzung) |
| Lesen und Schreiben über Dateien: `data export` dann `data import` derselben Daten, Mess-Kern unter Caps `2cpu/4g` | make/sample-db.mk:139-144, Hilfetext Makefile:131 | Muster „erst alles lesen, dann alles laden“ und feste Ressourcengrenzen des Containers |
| Zeilen/s als Zeilenzahl durch ganze Sekunden Wanduhr | examples/sample-db/scripts/smoke-tpch-perf.sh:123-125, :35-36 | Zeilen/s = Zeilen durch Wanduhr des Laufs; tdswire misst feiner (Millisekunden), die Form bleibt |
| Zwei Budgets je Messpunkt (Runaway-Wächter, Baseline-Erwartung); die Baseline ist nur auf einem benannten Runner hart (`PERF_GATE`) | Makefile:59-66, :378-386; hexagon/profiling/src/main/kotlin/dev/dmigrate/profiling/perf/PerfMeasure.kt:12-23 | Form der Grenzwerte: erst ein grober Wächter, die engere Zahl nur mit benannter Umgebung (Entscheidung O5) |
| Median und p95 über 5 Aufwärm- und 20 Messläufe als Voreinstellung | PerfMeasure.kt:22 | Wiederholungen mit Streuung (Abschnitt 5); die Zahlen 5 und 20 sind d-migrates Voreinstellung, für tdswire eine Setzung |
| Heap-Budget je Messpunkt (`maxHeapMb`) | test/perf-large-schema/src/test/kotlin/dev/dmigrate/test/perf/LargeSchemaScaleSpec.kt:138-139 (DDL-Erzeugung, kein Datenstrom) | Form „Heap-Spitze gegen ein Budget“; nur die Form, der Gegenstand ist ein anderer |
| Kalibrierung des Hosts gegen eine Referenzoperation, bei Abweichung Rückfall auf „diagnostisch“ | smoke-tpch-perf.sh:41-45, :165-178; docs/operations/performance-benchmarks.md:137-141 | Idee einer Vor-Messung des Hosts (Entscheidung O6); nicht übernommen, solange der Host nicht festliegt |

Was d-migrate **nicht** zeigt und tdswire nicht ableitet: eine p95-Latenz je Anfrage (d-migrate misst Gesamtzeiten je Lauf und den p95 über Wiederholungen derselben Operation, PerfMeasure.kt:18), eine Zahl gleichzeitiger Sitzungen als Ziel, und irgendeine Zahl gegen den Server dieses Repos.

## 2. Was der Server heute kann (Grundlage der Setzungen)

Gelesen in `spec/spezifikation.md`, nicht behauptet:

- **Lesen:** `SELECT <Spalten | *> FROM [<Schema>.]<Tabelle> [WHERE …]` der Form S1 gegen das Simulations-Backend (Zeile 1150); die Zeilenquelle liest nie mehr voraus, als der Transport abnimmt (Zeilen 210-213). Ein eigenes Backend liefert beliebige Ströme über `QueryBackend` (Stufe 8 von `make e2e`, `tools/mk/eigenes-backend.mk`).
- **Schreiben:** Massenladen mit `SqlBulkCopy` als `INSERT BULK` (Form B) mit je einer Nachricht `0x07` je Batch; das Backend muss `onInsertBatch` tragen (Zeilen 237, 314-351, 1163). Ein eigenes Backend (`QueryBackend`) trägt das Massenladen nicht: die Fähigkeit ist nicht öffentlich (`BulkLoading.kt`, [ADR-0033](adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5), `INSERT BULK` ist dort ein Fehler beim Zugriff auf die Zieltabelle (gemessen, [Messprotokoll](../messungen/lasttest-probe-2026-11-15.md)). Ein serverseitiges `BULK INSERT` aus einer Datei ist in der Spezifikation **nicht** als Anweisungsform genannt (Suche nach dem Wort ohne Treffer); es gehört nicht in den Workload. d-migrate 1.12.0 sendet mit mssql-jdbc 13.4.0 am Testserver ebenfalls `INSERT BULK` mit je einer Nachricht `0x07` (Messprotokoll [Massenladen](../messungen/bulk-load-2026-11-07.md), Nr. 7 und 8; `MERGE` bei `--on-conflict update`, [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)); der Referenzclient mit `SqlBulkCopy` ist ein anderer Client desselben Musters, die Messung mit d-migrate selbst ist nach O3 eine einmalige Gegenprobe.
- **Grenzen, die den Zuschnitt bestimmen:** höchstens 262 144 Zellen je Nachricht `0x07` (Zeile 349); der Bestand des Testservers nimmt höchstens 2 097 152 Zellen auf (Zeile 1080). Eine Tabelle mit 8 Spalten fasst dort also rund 262 000 Zeilen, **1 Mio Zeilen laden geht nur in ein Backend, das die Zeilen zählt oder verwirft**, nicht in den Bestand des Testservers (Setzung S6).
- **Telemetrie:** `SPAN_ENDED` mit `durationMs` je Verbindung, Anfrage und Backend-Aufruf, ganze Millisekunden abgerundet (Zeilen 1792-1816); `METRICS` mit Perzentilen als **Obergrenze der Klasse** (Klassen 1, 2, 5, 10 … ms), nicht als gemessene Dauer (Zeilen 1822-1834). Ein p95 für die Grenzwerte darf deshalb nicht aus `request_latency_p95_ms` stammen, sondern aus der Messung des Clients je Anfrage; die Metrik dient als Gegenprobe (Setzung S9).

## 3. Festlegung

Jede Zeile trägt das Wort „Setzung“ oder eine Quelle; Setzung des Nutzers heißt: Antwort auf O1 bis O7 (Abschnitt 6).

### 3.1 Daten

**S1 (Setzung): Tabelle `ref_zeilen`**, 8 Spalten, deterministisch erzeugt aus einem festen Seed (Vorgabe 42; Zeile n gleich bei jedem Lauf und in jedem Prozess), die Werte kommen aus einem Erzeuger im Repo, nicht aus einer Datei: `ReferenzZeilen.kt` im Testserver-Modul (SplitMix64) und dieselbe Rechnung in `referenztest-dotnet/Lasttest/ReferenzZeilen.cs`; der Export-Test vergleicht die erste und die letzte gelesene Zeile mit der Fassung des Clients.

| Spalte | Typ | Hinweis |
|---|---|---|
| `Id` | `BIGINT NOT NULL` | Schlüssel, aufsteigend ab 1 |
| `Menge` | `INT` | |
| `Betrag` | `DECIMAL(12,2)` | |
| `Aktiv` | `BIT` | |
| `Angelegt` | `DATETIME2(3)` | |
| `Code` | `NVARCHAR(12)` | 12 Zeichen |
| `Name` | `NVARCHAR(40)` | 40 Zeichen |
| `Notiz` | `NVARCHAR(100)` | 100 Zeichen; jede zehnte Zeile `NULL` |

Alle Typen sind für das Massenladen gedeckt (Zeilen 338-342). **Zeilengröße auf dem Draht (gemessen, Einzelmessung 2026-11-15):** 328 bis 330 Byte je Zeile im Export und 337 Byte je Zeile im Import, gemessen als Zähler der Netzwerkschnittstelle des Client-Containers geteilt durch die Zeilenzahl, also **mit** IP- und TCP-Kopf und nicht die Länge der TDS-Pakete ([Messprotokoll](../messungen/lasttest-probe-2026-11-15.md)). Form: TPC-H-ähnlich in Spaltenmix (Zahl, Dezimal, kurzer und langer Text), ohne TPC-H nachzubilden; d-migrate nutzt TPC-H (make/sample-db.mk:125-127, 8 Tabellen), tdswire misst eine Tabelle, weil die Messung den Strom prüft, nicht den Katalog.

### 3.2 Zeilenzahlen

**S2 (Setzung, O2 des Nutzers):**

| Stufe | Zeilen | Zweck |
|---|---|---|
| Probe | 20.000 | Probengröße der E2E-Stufe 9: das Werkzeug läuft über die Prozessgrenze, die Zahl ist keine Messreihe |
| klein | 100.000 | Vorgabe des Werkzeugs (`LASTTEST_ZEILEN`), Grundlast, Warmlauf; alle Stufen der Staffel außer den zwei folgenden |
| Haupt | 1.000.000 | Durchsatz je Richtung, **nur für die Stufen 1 und 8** der Staffel (Größenordnung aus spec/lastenheft-d-migrate.md:647-648) |
| groß | 10.000.000 | nur Lesen, Heap beim Strom (Größenordnung aus spec/lastenheft-d-migrate.md:652); der Heap des Serverprozesses ist fest und kleiner als das Ergebnis (der Lauf `gross` der Integration nutzt 48 MiB, `SPEC-040`); Gegenstand von `slice-qa-performance-heap-abschluss` |

**Laufzeit (gemessen, Einzelmessungen, [Messprotokoll](../messungen/lasttest-probe-2026-11-15.md)):** `make lasttest` mit 100.000 Zeilen, 5 Läufen und allen Aufbauten, je mit und ohne Senke: 92 s, davon 36 s Bau und 56 s Messung (2026-11-16, Last 8,2 bis 7,1, mit Import in Aufbau A); der Lauf vom 2026-11-15 ohne den Import in A dauerte 63 s (Bau 3 s, Messung 60 s; Last 5,8 bis 6,5). Mit 1.000.000 Zeilen und 5 Läufen (2026-11-15, Last 4,3 bis 5,7): Aufbau C mit Senke rund 91 s (Export und Import), Aufbau B mit Senke rund 64 s (nur Export). Ein Lauf der Stufen 1 und 8 der Staffel mit 1.000.000 Zeilen liegt also in dieser Größenordnung je Aufbau und Senke; die Staffel der Folge-Slices plant damit ([Messprotokoll](../messungen/lasttest-probe-2026-11-15.md)).

### 3.3 Muster

**S3 (Setzung):** vier Muster, je eines pro Richtung und Last; das Werkzeug dieses Slice liefert die ersten zwei mit einer Sitzung:

1. **Export:** `SELECT * FROM ref_zeilen`, vom Client bis zum Ende gelesen (ein Strom je Sitzung). Referenzclient: .NET `SqlDataReader` ([ADR-0008](adr/0008-dotnet-kompatibilitaetstests.md)). *Im Werkzeug: Aufbau B und C.*
2. **Import:** `SqlBulkCopy` mit `BatchSize` 1000 (Setzung, wie die Messreihe des Massenladens, Zeile 322) in `ref_zeilen`; Ziel ist das Simulations-Backend, dessen `onInsertBatch` die Zeilen zählt und nicht speichert (S6). *Im Werkzeug: Aufbau C und A-Import.*
3. **Punktabfrage unter Last:** parametrisiertes `SELECT … WHERE Id = @id` (RPC, `sp_executesql`, [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)) auf einer eigenen Sitzung, während die übrigen Sitzungen streamen oder laden; **daraus kommt der p95 je Anfrage**. *Folge-Slice `slice-qa-performance-durchsatz-sitzungen`.*
4. **Langsamer Leser:** Sitzungen, die nach jeder Zeile oder jedem Block pausieren, gegen schnelle; Gegenstand der Rückstau-Messungen ([ADR-0006](adr/0006-backend-vertrag-und-streaming.md), Folge-Slice `slice-qa-performance-rueckstau`).

### 3.4 Sitzungen

**S4 (Setzung):** Staffel 1, 8, 16, 64 gleichzeitige Sitzungen je Muster 1 und 2 (8 und 10 aus d-migrates Parallelitätszielen, lastenheft-d-migrate.md:302 und :654, auf 8 und 16 gerundet; Voreinstellung dort ist die Kernzahl, :316); eine zusätzliche Stufe 256 nur für die Rückstau-Messung mit mehreren Verbindungen. Die Obergrenze des Servers ist `MAX_CONNECTIONS` 1000 (`SPEC-034`, Setzung der Spezifikation, nicht gemessen); die Staffel erreicht sie nicht. „Gleichzeitige Sitzungen“ als Grenzwert ist die höchste Stufe, bei der die übrigen Messgrößen ihre Grenzwerte halten; das ist erst nach der Messung eine Zahl. Das Werkzeug dieses Slice läuft mit **einer** Sitzung; die Staffel gehört `slice-qa-performance-durchsatz-sitzungen`.

### 3.5 Metriken

**S5 (Setzung):** je Lauf, je Stufe der Staffel; die Spalte „Werkzeug“ nennt, was `make lasttest` schon schreibt:

| Metrik | Quelle | Werkzeug | Anmerkung |
|---|---|---|---|
| Zeilen/s je Richtung | Client: Zeilen durch Wanduhr, Millisekunden | ja (aus dem Median) | gleiche Form wie smoke-tpch-perf.sh:125, :137 |
| Median, Minimum, Maximum je Stufe | Client, 5 Läufe nach einem Warmlauf | ja | Abschnitt 5 |
| Bytes je Zeile | Zähler der Netzwerkschnittstelle des Client-Containers | ja | mit IP- und TCP-Kopf; Abschnitt 3.1 |
| erste Zeile in ms | Client | je Lauf in der Ausgabe des Clients, nicht in der Messzeile | wie die Messzeile des Spike in [ADR-0006](adr/0006-backend-vertrag-und-streaming.md) |
| p95 (und p50, p99) der Punktabfrage in ms | Client: Zeit je Anfrage, mindestens 1000 Anfragen je Stufe (Setzung) | nein, Folge-Slice | nicht aus `METRICS` (Klassenobergrenze) |
| gleichzeitige Sitzungen | Staffelstufe; Gegenprobe `sessions_active` aus `METRICS` | nein, Folge-Slice | |
| Heap des Serverprozesses | Stichprobe der Live-Größe nach GC im Serverprozess (Mechanik wählt der Folge-Slice) | nein; nur die cgroup-Spitze des Containers (`memory.peak`) steht in der Ausgabe | Stichprobe, keine Spitze (Aussage wie im Spike, [ADR-0006](adr/0006-backend-vertrag-und-streaming.md)) |
| Rückstau: Zeilen, die das Backend vorausläuft | Zähler am messenden Backend | nein, Folge-Slice | gegen den Zähler-Beleg am Test-Backend (`spec/spezifikation.md:1957`) |
| Dauer je Schicht | `SPAN_ENDED`: `tds.query` und `backend.execute` | nein (Senke „mit“ schreibt sie ins Log, das Werkzeug liest sie nicht) | Auflösung 1 ms abgerundet: Anfragen unter 1 ms zeigen 0 |

### 3.6 Getrennte Messung Protokollschicht und Backend

**S6 und S7 (Setzung, nach dem ersten Lauf des Werkzeugs berichtigt).** Die Telemetrie liefert Dauer je Schicht (Abschnitt 4), aber `backend.execute` enthält beim Strom auch das Warten auf den Transport (der Strom wird gezogen, Zeile 213), und `tds.query` enthält das Backend. Eine Differenz der beiden Spans ist darum **nicht** der Anteil der Protokollschicht. Das Werkzeug trennt deshalb über drei Aufbauten mit demselben Workload:

| Aufbau | Backend | Misst | Richtung |
|---|---|---|---|
| A: Backend allein | Backend ohne Server und Draht, im selben Prozess. Export: Strom bis zu einem Zähler gezogen, beide Backends (das eigene `ZeilenBackend` und das Simulations-Backend über `ref_zeilen`). Import: der Stapel-Callback `onInsertBatch` von `ref_zeilen` direkt in Stapeln zu 1000 Zeilen gerufen | Eigenzeit des Backends | Export und Import |
| B: Protokollschicht | Server mit eigenem Backend (`ZeilenBackend`), das Zeilen aus dem Erzeuger ohne Arbeit liefert (Stufe 8 von `make e2e` als Vorbild) | Kosten von Codec, Sitzung, Transport und Client für diesen Workload | Export; **Import entfällt** |
| C: Gesamt | Simulations-Backend mit Zeilenquelle (`SimTable.rows`) und `onInsertBatch`, das zählt | Summe, wie ein Betreiber sie sieht | Export und Import |

Der Anteil der Protokollschicht am Export ist B gegen A; der des Backends C gegen B. **Befund gegenüber dem Entwurf (Entscheidung des Nutzers 2026-11-16):** ein eigenes Backend nimmt kein Massenladen an (Abschnitt 2), für den Import gibt es darum keinen Aufbau B. Der Import wird stattdessen über den Callback getrennt: A-Import ruft denselben Stapel-Callback wie C ohne Draht und ohne Client; C gegen A-Import ist damit der Aufwand von Codec, Sitzung, Transport und Client für den Import **zuzüglich** des Unterschieds im Bau der Zeilen für den Callback: A-Import baut die Maps selbst, der Server baut sie nach dem Dekodieren und mit Typüberführung; ob beide gleich teuer sind, ist ungemessen, und der Client ist nicht abgetrennt (der Callback ist in beiden derselbe). Eine öffentliche Fläche für das Massenladen eines eigenen Backends entsteht nicht. Der Zähler im Backend (S6: **zählt oder verwirft, speichert nicht**) hält die Grenze des Bestands (2 097 152 Zellen) aus dem Weg. Beide Backends laufen im selben Serverprozess-Image; der Erzeuger der Zeilen ist im Repo. Aufbau A ist keine Prozessgrenze und damit kein E2E; er ist eine Referenz im Lasttest-Werkzeug, die E2E-Stufe selbst (B und C) geht über den Draht. Der Client liest und schreibt in der gemessenen Zeit mit: B gegen A und C gegen A-Import enthalten auch seinen Aufwand (ungemessen, wie viel). A-Import baut je Zeile eine Map für den Callback; der Server baut sie nach dem Dekodieren und überführt dabei die Typen. Ob beide Schritte gleich teuer sind, ist ungemessen; die Differenz C gegen A-Import enthält diesen Unterschied.

### 3.7 Ressourcen und Umgebung

**S8 (Setzung, O4 des Nutzers):** Server und Client laufen in Containern eines internen Docker-Netzes (kein Zugang nach außen, wie `tools/eigenes-backend-test.sh`), Basis-Images per Digest (`Dockerfile`, `referenztest-dotnet/Dockerfile`). Der Serverprozess läuft mit `-Xmx256m` (Setzung nach dem ersten Probelauf: die cgroup-Spitze des Containers lag bei 101 bis 178 MB; der Wert ist eine Setzung, kein Nachweis, dass 256 MiB genügen); der Server-Container mit `--cpus 2`, `--memory 4g` und `--memory-swap 4g` (nach d-migrate, make/sample-db.mk:140), der Client-Container mit `--cpus 2`, `--memory 2g` und `--memory-swap 2g` (Setzung). Alle Werte sind im Werkzeug über `LASTTEST_*` einstellbar und stehen im Kopf jedes Laufs. Die Limits aus `make builder-create` (`BUILDER_CPUS=8`, `BUILDER_MEMORY=24g`, `tools/mk/builder.mk`) gelten nur für den Builder-Container und damit für Bau und `RUN`-Schritte, **nicht** für `docker run`; sie sind kein Mittel für den Lasttest. Das Werkzeug bricht mit Exit 3 ab, wenn der verfügbare Speicher des Hosts vor einem Container unter 4096 MiB liegt (Vorgabe, `LASTTEST_MIN_FREI_MB`). Die Grenze gilt je Container; die Limits von Server und Client summieren sich auf 6 GiB, und die Summe prüft der Wächter nicht. Gemessen sind nur Spitzen des Serverprozesses (101 bis 178 MB); eine Spitze des Clients ist ungemessen.

## 4. Telemetrie: liefert sie Dauer je Schicht? (Ist-Stand, Start-Trigger von `slice-qa-performance`)

Ja, mit zwei Grenzen. Gebaut sind die Spans `tds.connection`, `tds.query` und `backend.execute` mit `durationMs` (`SPEC-017` bis `SPEC-019`, `spec/spezifikation.md` Zeilen 1760-1766, Zeilen 1792-1816), Tests `SpanOutcomesDispatchTest`, `MetricsTracingWiringTest`, `ObserverTest`; der Slice [slice-betrieb-metriken-tracing](planning/done/slice-betrieb-metriken-tracing.md) nennt als Ziel ausdrücklich, dass der Performance-Slice die Dauer je Schicht lesen kann (§1 dort). Das Ende eines Spans liegt auf der Ebene `DEBUG` (`docs/user/referenz-ereignisse.md:44`); der Testserver zeigt es nur mit `LOG_LEVEL=DEBUG` (`docs/user/einbettung-und-betrieb.md:631`). Grenzen: (1) Auflösung ganze Millisekunden, abgerundet (`spec/spezifikation.md:1794`); (2) die Kosten der Telemetrie selbst und einer blockierenden Senke sind **ungemessen** (`spec/spezifikation.md`, Abschnitt 5, „Kosten des Ereignisbaus und der Aggregation sind nicht gemessen“). **Entscheidung O7 des Nutzers (S10):** der Lasttest misst jede Stufe der Aufbauten B und C einmal **mit** und einmal **ohne** Senke, sonst ist „Protokollschicht“ von der Beobachtung nicht zu trennen. „Mit Senke“ ist das Log des Testservers auf `LOG_LEVEL=DEBUG` (der Adapter schreibt die JSON-Zeilen auf die Standardausgabe des Containers); „ohne Senke“ startet den Server mit `TelemetrySink.NONE`. Der Einfluss der Senke ist in der Probe nicht eindeutig ([Messprotokoll](../messungen/lasttest-probe-2026-11-15.md)).

## 5. Reproduzierbarkeit auf diesem Host

**Gemeint ist:** [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) verlangt einen „reproduzierbaren Lasttest“; reproduzierbar ist der **Lauf**, nicht die Zahl. Gleich bleiben: Befehl (`make lasttest`), Images (Basis per Digest), Seed, Workload-Parameter, Container-Limits, Reihenfolge der Stufen, Messmethode. Verschieden sein darf und wird die Zahl, weil der Host unter fremder Last läuft.

- **Host-Verhalten:** Der Host swappt und läuft unter Last 5 bis 9 (Messprotokoll [Maven-Paket](../messungen/maven-paket-2026-11-14.md), Abschnitt Bedingungen; Last 3 bis 5 mit 10 von 16 GiB Swap im [Messprotokoll der Abnahme](../messungen/abnahme-d-migrate-2026-11-12.md); am 2026-11-15 Last 4,3 bis 6,5 und 14 bis 15 GiB von 16 GiB Swap belegt, [Messprotokoll](../messungen/lasttest-probe-2026-11-15.md)). Es entsteht **keine künstliche Last**; die einzige Last ist die des gemessenen Workloads.
- **Je Messzeile festgehalten:** Datum, Aufbau, Senke, Richtung, Zeilenzahl, Wiederholungen, Median, Minimum, Maximum, Zeilen/s, Bytes je Zeile, Last (1/5/15 Minuten) und Swap und verfügbarer Speicher des Hosts vor und nach der Stufe; im Kopf des Laufs Host (Kerne, RAM, Kernel), Docker-Version, Image-IDs, Commit (mit `+geaendert` bei geändertem Arbeitsbaum), Limits (`--cpus`, `--memory`, `--memory-swap`, `-Xmx`), Zeilen, Seed.
- **Wiederholungen mit Streuung (Setzung S11):** je Stufe ein Warmlauf (nicht gezählt) und danach 5 Läufe (`LASTTEST_WDH`); berichtet werden Median, Minimum und Maximum, nie ein Einzelwert als Ergebnis (`tools/lasttest-statistik.sh`, gehalten von `make skript-selbsttest`). Bei starker Streuung (Maximum mehr als das Doppelte des Minimums, Setzung) trägt die Zeile `streuung zu-gross`: die Stufe ist „nicht belegt“ statt gemittelt. **Abweichung vom Entwurf, nach dem Bau berichtigt:** das Werkzeug **verwirft und wiederholt keinen Lauf selbst**; es vermerkt `last-hoch` (Last 1 Minute über 12, Setzung, `LASTTEST_LAST_MAX`) und `swap-gewachsen` (Swap des Hosts um mehr als 16 MiB größer als vor der Stufe, Setzung) in der Zeile, und die Entscheidung über Verwerfen und Wiederholen liegt beim Menschen (Setzung, vom Nutzer bestätigt 2026-11-16), der die Zeile in ein Messprotokoll übernimmt. Der Swap des Hosts gehört fremden Prozessen; dass er wächst, sagt nichts über den Container.
- **Container gegen Swap:** `--memory-swap` gleich `--memory`. Docker nimmt die Angabe an, und im Container steht `memory.swap.max` 0 (gelesen in jedem Serverlauf des Werkzeugs, Einzelmessung 2026-11-15). Ungemessen bleibt, ob der Container unter Speicherdruck des Hosts dennoch ausgelagert würde; die Heap-Aussagen der Folge-Slices setzen auf die Angabe, nicht auf eine Messung.
- **Ein Messprotokoll sagt Datum, Host, Last und gilt als Einzelmessung** (`AGENTS.md` §5 Regel 7), auch mit fünf Wiederholungen: es ist eine Messung eines Tages auf einem Host. Grenzwerte im Lastenheft tragen deshalb die **Umgebung** mit (Limits, Image, Methode) und sind Grenzwerte für diese Referenzumgebung (O5).
- **Ungemessen bleibt:** ein echter SQL Server als Vergleich (Vergleichslauf D misst Wire-Verhalten, nicht Durchsatz); andere Clients als der .NET-Referenzclient (und, bei der einmaligen Gegenprobe nach O3, d-migrate selbst); TLS (die Staffel läuft in TDS 7.4 ohne Verschlüsselung, Setzung); Netz zwischen Hosts; Langzeitlauf; andere Garbage Collectors und JVM-Versionen; Hosts mit anderer Kernzahl; die Kosten einer blockierenden oder langsamen Senke; Zeilen mit LOB oder PLP (nicht im Massenladen gedeckt, Zeilen 345-349).

## 6. Entscheidungen des Nutzers (O1 bis O7)

Am 2026-11-15 antwortete der Nutzer auf O1 bis O7 mit dem Wortlaut „01-07 Deine Empfehlungen“. Damit gilt für jede Frage die Empfehlung als **Setzung des Nutzers**. „Spec ist Zielbild“: wo eine Antwort die Spezifikation oder das Lastenheft ändern würde, steht neben „Code angleichen“ auch „Zielbild anpassen“.

| Nr. | Frage | Entscheidung | Wo sie wirkt |
|---|---|---|---|
| O1 | Zielwerte (Zeilen/s je Richtung, p95, Sitzungen) vorab setzen oder aus der Messung ableiten? | Aus der Messung ableiten, vom Nutzer bestätigt, danach Eintrag im Lastenheft; die Zahlen von d-migrate nicht übernehmen. Verfehlt eine Messung ein von außen gesetztes Ziel: Weg „Code angleichen“ (Optimierungs-Slice); passt das Ziel nicht zum Zweck eines Testservers: „Zielbild anpassen“. | Kopf (kein Grenzwert hier), Abschnitt 1; `slice-qa-performance-grenzwerte` |
| O2 | Zeilenzahl des Hauptlaufs | 1 Mio nur für die Stufen 1 und 8 der Staffel; die übrigen Stufen mit 100.000 Zeilen. Die Laufzeit steht jetzt in 3.2. | 3.2 |
| O3 | Wer misst | Referenzclient für alle Grenzwerte; d-migrate nur als **einmalige Gegenprobe** im Messprotokoll (andere Messung, kein Grenzwert); Folge-Slice nur auf Verlangen. | 3.3, 5; das Werkzeug dieses Slice misst nur den Referenzclient |
| O4 | Ressourcen der Referenzumgebung | `--cpus 2`, `--memory 4g` (Server); Zahlen sind Setzung, kein Nachweis. Alternative „keine Limits“ nicht gewählt. | 3.7 |
| O5 | Form der Grenzwerte; Lasttest im `make e2e`-Lauf? | Zwei Zahlen je Messpunkt (grober Wächter, enge Zahl nur mit Umgebung). Der Lasttest bleibt außerhalb von `make gates` ([ADR-0022](adr/0022-e2e-laeufe-nicht-in-den-gates.md)). **Auslegung des Slice (Setzung, vom Nutzer bestätigt 2026-11-16):** die E2E-Stufe 9 führt den Lasttest **aus**, in Probengröße (20.000 Zeilen, 3 Läufe), und prüft keine Schwelle; die Hauptgrößen laufen nur über `make lasttest` von Hand. | 5; `tools/e2e.sh` Stufe 9; `slice-qa-performance-grenzwerte` |
| O6 | Kalibrierung des Hosts | Nicht jetzt: kein designierter Runner. Wiederaufnahme, wenn ein fester Runner benannt ist. | nicht umgesetzt |
| O7 | Telemetrie im Lasttest | Jede Stufe von B und C mit und ohne Senke (S10). | 4; `LASTTEST_SENKEN` |

## 7. Was nach diesem Stand passiert

Die Antworten zu O1 bis O7 sind eingearbeitet. Die **Freigabe dieses Stands** folgt nach Review durch den Nutzer; der Kopf nennt dann Datum und Entscheider. Danach folgt die Reihenfolge der Slices ([Welle](planning/welle-qualitaet-grundlage.md)): Werkzeug (dieser Slice), Messungen, Grenzwerte im Lastenheft. Ein Eintrag im Lastenheft entsteht erst nach der Messung.
