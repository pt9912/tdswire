# Referenz-Workload für die Performance-Messung (Entwurf)

Entwurf zu [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), Träger [slice-qa-performance](planning/open/slice-qa-performance.md). **Stand: Entwurf vom 2026-11-15, nicht freigegeben, nichts gemessen.** Dieses Dokument legt keinen Grenzwert fest; die Grenzwerte stehen nach der Messung und der Entscheidung des Nutzers im Lastenheft (Tabelle der Abnahmeunterlagen: „Grenzwerte für Durchsatz, p95-Latenz, Sitzungen und Lastprofil“, Betreiber der .NET-Anwendung, vor Abnahme). Jede Zahl unten ist eine **Setzung** und trägt das Wort, oder sie ist als Zitat mit Quelle gekennzeichnet (`AGENTS.md` §3.8).

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
| Zwei Budgets je Messpunkt (Runaway-Wächter, Baseline-Erwartung); die Baseline ist nur auf einem benannten Runner hart (`PERF_GATE`) | Makefile:59-66, :378-386; hexagon/profiling/src/main/kotlin/dev/dmigrate/profiling/perf/PerfMeasure.kt:12-23 | Form der Grenzwerte: erst ein grober Wächter, die engere Zahl nur mit benannter Umgebung (offene Frage O5) |
| Median und p95 über 5 Aufwärm- und 20 Messläufe als Voreinstellung | PerfMeasure.kt:22 | Wiederholungen mit Streuung (Abschnitt 5); die Zahlen 5 und 20 sind d-migrates Voreinstellung, für tdswire eine Setzung |
| Heap-Budget je Messpunkt (`maxHeapMb`) | test/perf-large-schema/src/test/kotlin/dev/dmigrate/test/perf/LargeSchemaScaleSpec.kt:138-139 (DDL-Erzeugung, kein Datenstrom) | Form „Heap-Spitze gegen ein Budget“; nur die Form, der Gegenstand ist ein anderer |
| Kalibrierung des Hosts gegen eine Referenzoperation, bei Abweichung Rückfall auf „diagnostisch“ | smoke-tpch-perf.sh:41-45, :165-178; docs/operations/performance-benchmarks.md:137-141 | Idee einer Vor-Messung des Hosts (offene Frage O6); nicht übernommen, solange der Host nicht festliegt |

Was d-migrate **nicht** zeigt und tdswire nicht ableitet: eine p95-Latenz je Anfrage (d-migrate misst Gesamtzeiten je Lauf und den p95 über Wiederholungen derselben Operation, PerfMeasure.kt:18), eine Zahl gleichzeitiger Sitzungen als Ziel, und irgendeine Zahl gegen den Server dieses Repos.

## 2. Was der Server heute kann (Grundlage der Setzungen)

Gelesen in `spec/spezifikation.md`, nicht behauptet:

- **Lesen:** `SELECT <Spalten | *> FROM [<Schema>.]<Tabelle> [WHERE …]` der Form S1 gegen das Simulations-Backend (Zeile 1150); die Zeilenquelle liest nie mehr voraus, als der Transport abnimmt (Zeilen 210-213). Ein eigenes Backend liefert beliebige Ströme über `QueryBackend` (Stufe 8 von `make e2e`, `tools/mk/eigenes-backend.mk`).
- **Schreiben:** Massenladen mit `SqlBulkCopy` als `INSERT BULK` (Form B) mit je einer Nachricht `0x07` je Batch; das Backend muss `onInsertBatch` tragen (Zeilen 237, 314-351, 1163). Ein serverseitiges `BULK INSERT` aus einer Datei ist in der Spezifikation **nicht** als Anweisungsform genannt (Suche nach dem Wort ohne Treffer); es gehört nicht in den Workload. d-migrate 1.12.0 sendet mit mssql-jdbc 13.4.0 am Testserver ebenfalls `INSERT BULK` mit je einer Nachricht `0x07` (Messprotokoll [Massenladen](../messungen/bulk-load-2026-11-07.md), Nr. 7 und 8; `MERGE` bei `--on-conflict update`, [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)); der Referenzclient mit `SqlBulkCopy` ist ein anderer Client desselben Musters, die Messung mit d-migrate selbst ist offene Frage O3.
- **Grenzen, die den Zuschnitt bestimmen:** höchstens 262 144 Zellen je Nachricht `0x07` (Zeile 349); der Bestand des Testservers nimmt höchstens 2 097 152 Zellen auf (Zeile 1080). Eine Tabelle mit 8 Spalten fasst dort also rund 262 000 Zeilen, **1 Mio Zeilen laden geht nur in ein Backend, das die Zeilen zählt oder verwirft**, nicht in den Bestand des Testservers (Setzung S6).
- **Telemetrie:** `SPAN_ENDED` mit `durationMs` je Verbindung, Anfrage und Backend-Aufruf, ganze Millisekunden abgerundet (Zeilen 1792-1816); `METRICS` mit Perzentilen als **Obergrenze der Klasse** (Klassen 1, 2, 5, 10 … ms), nicht als gemessene Dauer (Zeilen 1822-1834). Ein p95 für die Grenzwerte darf deshalb nicht aus `request_latency_p95_ms` stammen, sondern aus der Messung des Clients je Anfrage; die Metrik dient als Gegenprobe (Setzung S9).

## 3. Entwurf

### 3.1 Daten

**S1 (Setzung): Tabelle `ref_zeilen`**, 8 Spalten, deterministisch erzeugt aus einem festen Seed (gleiche Zeile n bei jedem Lauf), die Werte kommen aus einem Erzeuger, nicht aus einer Datei:

| Spalte | Typ | Hinweis |
|---|---|---|
| `Id` | `BIGINT NOT NULL` | Schlüssel, aufsteigend |
| `Menge` | `INT` | |
| `Betrag` | `DECIMAL(12,2)` | |
| `Aktiv` | `BIT` | |
| `Angelegt` | `DATETIME2(3)` | |
| `Code` | `NVARCHAR(12)` | |
| `Name` | `NVARCHAR(40)` | |
| `Notiz` | `NVARCHAR(100)` | etwa jede zehnte Zeile `NULL` |

Alle Typen sind für das Massenladen gedeckt (Zeilen 338-342). Die Zeilengröße auf dem Draht ist **ungemessen**; sie ist Teil der ersten Messzeile (Slice [slice-qa-performance](planning/open/slice-qa-performance.md)), damit „MiB/s“ neben „Zeilen/s“ stehen kann. Form: TPC-H-ähnlich in Spaltenmix (Zahl, Dezimal, kurzer und langer Text), ohne TPC-H nachzubilden; d-migrate nutzt TPC-H (make/sample-db.mk:125-127, 8 Tabellen), tdswire misst eine Tabelle, weil die Messung den Strom prüft, nicht den Katalog.

### 3.2 Zeilenzahlen

**S2 (Setzung):**

| Stufe | Zeilen | Zweck |
|---|---|---|
| klein | 100.000 | Probe des Werkzeugs, Grundlast, Warmlauf |
| Haupt | 1.000.000 | Durchsatz je Richtung (Größenordnung aus spec/lastenheft-d-migrate.md:647-648) |
| groß | 10.000.000 | nur Lesen, Heap beim Strom (Größenordnung aus spec/lastenheft-d-migrate.md:652); der Heap des Serverprozesses ist fest und kleiner als das Ergebnis (der Lauf `gross` der Integration nutzt 48 MiB, `SPEC-040`) |

### 3.3 Muster

**S3 (Setzung):** vier Muster, je eines pro Richtung und Last:

1. **Export:** `SELECT * FROM ref_zeilen` gegen das Simulations-Backend mit Zeilenquelle, vom Client bis zum Ende gelesen (ein Strom je Sitzung). Referenzclient: .NET `SqlDataReader` ([ADR-0008](adr/0008-dotnet-kompatibilitaetstests.md)).
2. **Import:** `SqlBulkCopy` mit `BatchSize` 1000 (Setzung, wie die Messreihe des Massenladens, Zeile 322) in `ref_zeilen`; Ziel ist ein Backend mit `onInsertBatch`, das die Zeilen zählt (S6).
3. **Punktabfrage unter Last:** parametrisiertes `SELECT … WHERE Id = @id` (RPC, `sp_executesql`, [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)) auf einer eigenen Sitzung, während die übrigen Sitzungen streamen oder laden; **daraus kommt der p95 je Anfrage**.
4. **Langsamer Leser:** Sitzungen, die nach jeder Zeile oder jedem Block pausieren, gegen schnelle; Gegenstand der Rückstau-Messungen ([ADR-0006](adr/0006-backend-vertrag-und-streaming.md), Folge-Slice `slice-qa-performance-rueckstau`).

### 3.4 Sitzungen

**S4 (Setzung):** Staffel 1, 8, 16, 64 gleichzeitige Sitzungen je Muster 1 und 2 (8 und 10 aus d-migrates Parallelitätszielen, lastenheft-d-migrate.md:302 und :654, auf 8 und 16 gerundet; Voreinstellung dort ist die Kernzahl, :316); eine zusätzliche Stufe 256 nur für die Rückstau-Messung mit mehreren Verbindungen. Die Obergrenze des Servers ist `MAX_CONNECTIONS` 1000 (`SPEC-034`, Setzung der Spezifikation, nicht gemessen); die Staffel erreicht sie nicht. „Gleichzeitige Sitzungen“ als Grenzwert ist die höchste Stufe, bei der die übrigen Messgrößen ihre Grenzwerte halten; das ist erst nach der Messung eine Zahl.

### 3.5 Metriken

**S5 (Setzung):** je Lauf, je Stufe der Staffel:

| Metrik | Quelle | Anmerkung |
|---|---|---|
| Zeilen/s je Richtung | Client: Zeilen durch Wanduhr, Millisekunden | gleiche Form wie smoke-tpch-perf.sh:125, :137 |
| p95 (und p50, p99) der Punktabfrage in ms | Client: Zeit je Anfrage, mindestens 1000 Anfragen je Stufe (Setzung) | nicht aus `METRICS` (Klassenobergrenze) |
| erste Zeile in ms | Client | wie die Messzeile des Spike in [ADR-0006](adr/0006-backend-vertrag-und-streaming.md) |
| gleichzeitige Sitzungen | Staffelstufe; Gegenprobe `sessions_active` aus `METRICS` | |
| Heap des Serverprozesses | Stichprobe der Live-Größe nach GC im Serverprozess (Mechanik wählt Slice 1) | Stichprobe, keine Spitze (Aussage wie im Spike, [ADR-0006](adr/0006-backend-vertrag-und-streaming.md)) |
| Rückstau: Zeilen, die das Backend vorausläuft | Zähler am messenden Backend | gegen den Zähler-Beleg am Test-Backend (`spec/spezifikation.md:1957`) |
| Dauer je Schicht | `SPAN_ENDED`: `tds.query` und `backend.execute` | Auflösung 1 ms abgerundet: Anfragen unter 1 ms zeigen 0 |

### 3.6 Getrennte Messung Protokollschicht und Backend

**S6 und S7 (Setzung).** Die Telemetrie liefert Dauer je Schicht (siehe unten), aber `backend.execute` enthält beim Strom auch das Warten auf den Transport (der Strom wird gezogen, Zeile 213), und `tds.query` enthält das Backend. Eine Differenz der beiden Spans ist darum **nicht** der Anteil der Protokollschicht. Der Entwurf trennt deshalb über drei Aufbauten mit demselben Workload:

| Aufbau | Backend | Misst |
|---|---|---|
| A: Backend allein | Backend ohne Server, Strom im selben Prozess bis zur Senke gezogen (kein Draht) | Eigenzeit und Heap des Backends |
| B: Protokollschicht | Server mit eigenem Backend, das Zeilen aus einem Erzeuger ohne Arbeit liefert und beim Import nur zählt (Stufe 8 von `make e2e` als Vorbild) | Kosten von Codec, Sitzung und Transport für diesen Workload |
| C: Gesamt | Simulations-Backend mit Zeilenquelle (`SimTable.rows`) und `onInsertBatch`, das zählt | Summe, wie ein Betreiber sie sieht |

Der Anteil der Protokollschicht ist B gegen A; der des Backends C gegen B. Der Zähler im Backend (S6: **zählt oder verwirft, speichert nicht**) hält die Grenze des Bestands (2 097 152 Zellen) aus dem Weg. Beide Backends laufen im selben Serverprozess-Image; der Erzeuger der Zeilen ist im Repo (ein Wegwerf-Wrapper außerhalb des Repos machte die Zahlen nicht wiederholbar; sie trügen nur als Größenordnung). Aufbau A ist keine Prozessgrenze und damit kein E2E; er ist eine Referenz im Lasttest-Werkzeug, die E2E-Stufe selbst (B und C) geht über den Draht.

### 3.7 Ressourcen und Umgebung

**S8 (Setzung):** Server und Client laufen in Containern eines internen Docker-Netzes (kein Zugang nach außen, wie `tools/eigenes-backend-test.sh`), Image-Basis per Digest. Der Serverprozess läuft mit festem `-Xmx` (Wert wählt der Slice nach dem ersten Probelauf); der Container mit `--cpus` und `--memory` (Startwerte `2` und `4g` als Setzung nach d-migrate, make/sample-db.mk:140, offene Frage O4). Die Limits aus `make builder-create` (`BUILDER_CPUS=8`, `BUILDER_MEMORY=24g`, `tools/mk/builder.mk`) gelten nur für den Builder-Container und damit für Bau und `RUN`-Schritte, **nicht** für `docker run`; sie sind kein Mittel für den Lasttest.

## 4. Telemetrie: liefert sie Dauer je Schicht? (Ist-Stand, Start-Trigger von `slice-qa-performance`)

Ja, mit zwei Grenzen. Gebaut sind die Spans `tds.connection`, `tds.query` und `backend.execute` mit `durationMs` (`SPEC-017` bis `SPEC-019`, `spec/spezifikation.md` Zeilen 1760-1766, Zeilen 1792-1816), Tests `SpanOutcomesDispatchTest`, `MetricsTracingWiringTest`, `ObserverTest`; der Slice [slice-betrieb-metriken-tracing](planning/done/slice-betrieb-metriken-tracing.md) nennt als Ziel ausdrücklich, dass der Performance-Slice die Dauer je Schicht lesen kann (§1 dort). Das Ende eines Spans liegt auf der Ebene `DEBUG` (`docs/user/referenz-ereignisse.md:44`); der Testserver zeigt es nur mit `LOG_LEVEL=DEBUG` (`docs/user/einbettung-und-betrieb.md:631`). Grenzen: (1) Auflösung ganze Millisekunden, abgerundet (`spec/spezifikation.md:1794`); (2) die Kosten der Telemetrie selbst und einer blockierenden Senke sind **ungemessen** (`spec/spezifikation.md`, Abschnitt 5, „Kosten des Ereignisbaus und der Aggregation sind nicht gemessen“); der Lasttest misst darum jede Stufe einmal **mit** und einmal **ohne** Senke (Setzung S10), sonst ist „Protokollschicht“ von der Beobachtung nicht zu trennen.

## 5. Reproduzierbarkeit auf diesem Host

**Gemeint ist:** [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance) verlangt einen „reproduzierbaren Lasttest“; reproduzierbar ist der **Lauf**, nicht die Zahl. Gleich bleiben: Befehl (`make`-Target), Images per Digest, Seed, Workload-Parameter, Container-Limits, Reihenfolge der Stufen, Messmethode. Verschieden sein darf und wird die Zahl, weil der Host unter fremder Last läuft.

- **Host-Verhalten:** Der Host swappt und läuft unter Last 5 bis 9 (Messprotokoll [Maven-Paket](../messungen/maven-paket-2026-11-14.md), Abschnitt Bedingungen; Last 3 bis 5 mit 10 von 16 GiB Swap im [Messprotokoll der Abnahme](../messungen/abnahme-d-migrate-2026-11-12.md)). Es entsteht **keine künstliche Last**; die einzige Last ist die des gemessenen Workloads.
- **Je Messzeile festgehalten:** Datum, Host (Kerne, RAM), Last (`uptime`, 1/5/15 Minuten vor und nach jedem Lauf), Swap-Belegung vor und nach dem Lauf (`free`), Docker-Version, Image-Digest, Commit, Limits (`--cpus`, `--memory`, `-Xmx`), Wiederholungszahl.
- **Wiederholungen mit Streuung (Setzung S11):** je Stufe mindestens 5 Läufe; berichtet werden Median, Minimum und Maximum, nie ein Einzelwert als Ergebnis; ein Lauf mit Swap-Anstieg des Containers oder mit Last über einer vorab genannten Schwelle (Wert wählt der Slice) wird verworfen und wiederholt, **mit** Nennung in der Messzeile. Die Zahl 5 ist eine Setzung; bei starker Streuung (Maximum mehr als das Doppelte des Minimums, Setzung) ist die Stufe „nicht belegt“ statt gemittelt.
- **Container gegen Swap (Setzung, ungemessen):** `--memory-swap` gleich `--memory`, damit der Container nicht in den Swap des Hosts ausweicht und die Heap-Messung verfälscht. Ob der Host die Angabe annimmt, ist nicht gemessen; der Slice prüft es mit dem ersten Lauf.
- **Ein Messprotokoll sagt Datum, Host, Last und gilt als Einzelmessung** (`AGENTS.md` §5 Regel 7), auch mit fünf Wiederholungen: es ist eine Messung eines Tages auf einem Host. Grenzwerte im Lastenheft tragen deshalb die **Umgebung** mit (Limits, Image, Methode) und sind Grenzwerte für diese Referenzumgebung (offene Frage O5).
- **Ungemessen bleibt:** ein echter SQL Server als Vergleich (Vergleichslauf D misst Wire-Verhalten, nicht Durchsatz); andere Clients als der .NET-Referenzclient (und, falls O3 so ausgeht, d-migrate selbst); TLS (die Staffel läuft in TDS 7.4 ohne Verschlüsselung, Setzung); Netz zwischen Hosts; Langzeitlauf; andere Garbage Collectors und JVM-Versionen; Hosts mit anderer Kernzahl; die Kosten einer blockierenden oder langsamen Senke; Zeilen mit LOB oder PLP (nicht im Massenladen gedeckt, Zeilen 345-349).

## 6. Offene Fragen an den Nutzer, mit Empfehlung

„Spec ist Zielbild“: wo eine Antwort die Spezifikation oder das Lastenheft ändern würde, steht neben „Code angleichen“ auch „Zielbild anpassen“.

| Nr. | Frage | Empfehlung |
|---|---|---|
| O1 | **Zielwerte** (Zeilen/s je Richtung, p95, Sitzungen): vorab setzen oder aus der Messung ableiten? Das Lastenheft nennt sie ausdrücklich „anhand eines Referenz-Workloads festgelegt“. | Aus der Messung ableiten, vom Nutzer bestätigt, danach Eintrag im Lastenheft. Die Zahlen von d-migrate nicht übernehmen: sie gelten dem Verbraucher gegen echte Datenbanken, der Server ist nur ein Teil davon. Verfehlt eine Messung ein von außen gesetztes Ziel, ist der Weg „Code angleichen“ (Optimierungs-Slice, ausgeschlossen aus diesem Plan); passt das Ziel nicht zum Zweck eines Testservers, ist der Weg „Zielbild anpassen“. |
| O2 | **Zeilenzahl** des Hauptlaufs: 1 Mio (Größenordnung d-migrate) oder kleiner? Auf diesem Host mit Last 5 bis 9 dauert jede Stufe mal 5 Wiederholungen mal Staffel. | Haupt 1 Mio nur für die Stufen 1 und 8; die übrigen Stufen mit 100.000 Zeilen. Die Laufzeit ist **nicht gemessen**; der erste Probelauf (Slice 1) nennt sie, bevor der Plan der Staffel steht. |
| O3 | **Wer misst:** nur der .NET-Referenzclient, oder auch d-migrate selbst (JDBC, `data export`, `data import`)? | Referenzclient für alle Grenzwerte (Rohbytes- und Referenzclient sind die Prozessgrenze des E2E, [ADR-0022](adr/0022-e2e-laeufe-nicht-in-den-gates.md)); d-migrate als **einmalige Gegenprobe** im Messprotokoll (d-migrate lädt über mssql-jdbc, nicht über `SqlBulkCopy`; Batchgröße, Typen und Treiber unterscheiden sich, die Zahl ist also eine andere Messung und kein Grenzwert). Folge-Slice nur auf Verlangen. |
| O4 | **Ressourcen der Referenzumgebung:** `--cpus 2`, `--memory 4g` (nach d-migrate) oder die 8 Kerne und 24 GiB von `make builder-create`? | 2 Kerne und 4 GiB: Der Host hat 20 Kerne und läuft unter fremder Last; ein kleines festes Budget streut weniger als ein großes. Die Zahlen sind eine Setzung, kein Nachweis. Alternative „Zielbild anpassen“: keine Container-Limits setzen und ehrlich „Host, Last x“ festhalten; das macht die Grenzwerte unübertragbar. |
| O5 | **Form der Grenzwerte:** zwei Zahlen je Messpunkt (grober Wächter gate-fähig, enge Zahl nur mit benannter Umgebung, wie `PERF_GATE` in d-migrate, Makefile:59-66) oder eine? Und: Läuft der Lasttest je im `make e2e`-Lauf oder nur als Deklaration (Entscheidung steht an die ADR zum Voll-E2E, [ADR-0022](adr/0022-e2e-laeufe-nicht-in-den-gates.md))? | Zwei Zahlen; der Wächter in der Größenordnung „Lauf bricht nicht ab“, die enge Zahl nur mit Umgebung. Der Lasttest bleibt außerhalb von `make gates` (Laufzeit, Last des Hosts, [ADR-0022](adr/0022-e2e-laeufe-nicht-in-den-gates.md)). |
| O6 | **Kalibrierung des Hosts** (Vor-Messung gegen eine Referenzoperation, bei Abweichung Rückfall auf diagnostisch wie bei d-migrate)? | Nicht jetzt: es gibt keinen designierten Runner; ohne ihn ist ein Referenzwert eine Zahl von einem Tag. Wiederaufnahme, wenn ein fester Runner benannt ist. |
| O7 | **Telemetrie im Lasttest:** jede Stufe mit und ohne Senke (S10), oder nur mit Senke? | Mit und ohne: sonst ist der Anteil der Protokollschicht von der Beobachtung nicht zu trennen (Kosten der Telemetrie sind ungemessen, Abschnitt 4). Kostet die doppelte Laufzeit; wenn O2 klein bleibt, tragbar. |

## 7. Was nach der Freigabe passiert

Freigabe heißt: die Fragen O1 bis O7 sind beantwortet, die Antworten stehen in diesem Dokument, und der Kopf nennt Datum und Entscheider. Danach folgt die Reihenfolge der Slices ([Welle](planning/welle-qualitaet-grundlage.md)): Werkzeug, Messungen, Grenzwerte im Lastenheft. Ein Eintrag im Lastenheft entsteht erst nach der Messung.
