# Slice slice-betrieb-metriken-tracing: Metriken und Spans: tds.connection, tds.query und backend.execute

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Fragen 2 und 6: Schnittstelle in `domain`, Aggregation in `observability`, Schnappschuss und Span-Ende als Ereignisse an die Senke), [ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) (`EffectiveSettings` führt das Intervall, Merge-Stand)

**Berührte Spec-Stellen:** SPEC-017 · SPEC-018 · SPEC-019 · SPEC-062 · SPEC-052 · SPEC-059 · SPEC-044 · SPEC-048 · SPEC-009 · SPEC-011 · SPEC-030 · SPEC-031 · SPEC-033 · SPEC-036 · SPEC-007 · ARC-009 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-07.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Verbindungen, Anfragen und Backend-Aufrufe erzeugen die Spans und Metriken von Abschnitt 5 der Spezifikation mit den Pflicht-Attributen, ohne SQL-Text, Anmeldekennung und Parameterwerte; der Schnappschuss der Metriken und das Ende eines Spans gehen als Ereignisse an die Senke aus [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) (kein zweiter Parameter, kein Telemetrie-Port, keine Fremdbibliothek, [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 6 A), sodass [slice-qa-performance](../open/slice-qa-performance.md) die Dauer je Schicht lesen kann.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Lastmessung, Referenz-Workload und Grenzwerte — [slice-qa-performance](../open/slice-qa-performance.md); dieser Slice liefert die Messpunkte, keine Zahl über Durchsatz oder Overhead.
- Export-Backend, Collector, Dashboards und Alarmierung — Betreiber (ARC-014); der Server liefert nach der ADR den Zugang, betreibt aber keine Auswertung.
- Das Log — [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md); Zustandsereignisse — [slice-betrieb-zustandsereignisse](../done/slice-betrieb-zustandsereignisse.md); der JSON-Adapter, die Warteschlange und das Prozessende des Testservers — [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md) (dieser Slice ergänzt dort nur die Serialisierung der neuen Ereignisarten).
- Adapter für JMX, einen Text-Endpunkt oder eine Telemetrie-Bibliothek — jeweils eigene Entscheidung mit eigener Angriffsflächen-Prüfung ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 6, Re-Evaluierungs-Trigger); der Zugang ist die Senke.
- Die Definition, ob Cancel und Timeout als Fehler zählen — [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md) und [slice-fehler-mapping](../done/slice-fehler-mapping.md); die Zähler „Abbrüche“ und „Protokollfehler“ folgen deren Entscheidung (§6).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Spans:** `tds.connection` (`connection.id`, `tds.phase`, `outcome`), `tds.query` (`session.id`, `request.id`, `tds.request_kind`, `outcome`) und `backend.execute` (`request.id`, `duration_ms`, `outcome`) an den Stellen von Transport und Handshake, Anfrageverarbeitung und Backend-Adapter; ein Rohbytes-Lauf (Login, Anfrage, Fehler, Cancel) erzeugt mit dem Sammler die erwarteten Spans mit den Pflicht-Attributen; die Menge der `outcome`-Werte ist eine Festlegung und steht so in der Closure.
- [x] **Metriken und Schnappschuss:** aktive Sessions, Login-Erfolgs- und -Fehlerzähler, Anfragen, Anfrage-Latenz als Verteilung (p50, p95 und p99 als Voreinstellung der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), Offen (d): Perzentile oder Zähler je Klasse entscheidet der Bau mit Begründung), aktive Backendoperationen, Abbrüche, Protokollfehler; Aggregation in `observability` (importiert nur `domain`), Ausgabe als Ereignis `metrics`; der Abstand in Sekunden ist ein Feld der `ServerConfig` (Voreinstellung 60, 0 schaltet ihn aus; Setzung der ADR, nicht gemessen) mit Prüfung in `config`; der Testserver liest ihn aus `METRICS_INTERVAL_SECONDS` (Eingang des Testservers, nicht der Bibliothek); kein SQL-Text als Label; ein Test mit vielen verschiedenen SQL-Texten ergibt dieselbe Zahl an Zeitreihen (Kardinalität).
- [x] **Zugang über die Senke und Ausfallsicherheit:** Span-Ende und Schnappschuss erreichen die Senke aus [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) (der Zugang ist die Senke; JMX, Text-Endpunkt und Telemetrie-Bibliothek sind nicht Teil); eine Senke, die wirft, bricht keine Anfrage und keine Verbindung (Test); eine Senke, die blockiert, hält den Anfragepfad an, und das belegt kein Test, sondern steht als akzeptiertes Negativ der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) in der Closure-Notiz; ein Span wird auch bei Cancel, Disconnect und Stromende genau einmal beendet (kein offener Span nach dem Lauf); der JSON-Adapter des Testservers serialisiert die neuen Ereignisarten (ein Rohbytes-Lauf gegen den Testserver zeigt `metrics` und Span-Ende als Zeilen).

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [ ] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor — **Nachbesserung ohne zweiten Review** (`220e5c32`, `d6c4913b`, `ee78d515`; die Nachbesserung bis `85a7c00b` und die Merge-Auflösung hat die Verifikation wie einen Review bewertet, kein Re-Review in eigenem Kontext).
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/TelemetryEvent.kt` | update | `EventKind` um `METRICS` und `SPAN_ENDED`; `TelemetryEvent` um die Felder `span`, `outcome`, `requestId`, `durationMs`, `phase`, `requestKind`, `metrics` (keine neuen Typnamen, `make flaeche` bleibt bei 61); Konfliktfläche mit dem Zweig der Zustandsereignisse |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/MeasurementEvents.kt` <!-- d-check:ignore (geplant: neue Datei) --> | neu | interne Ereignisklassen `MetricsSnapshot` und `SpanEnded`, interne Aufzählungen der `outcome`-, `phase`- und `request_kind`-Werte |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/Instruments.kt` <!-- d-check:ignore (geplant: neue Datei) --> | neu | interne Schnittstelle der Spans (Verbindung, Anfrage, Backend-Aufruf) mit `NONE`; `protocol` und `runtime` rufen nur sie (ARC-009: keine fachliche Entscheidung im Adapter) |
| `src/main/kotlin/io/github/pt9912/tdswire/observability/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | `Observer` (Spans, Zähler, Verteilung, Schnappschuss), `LatencyHistogram`; importiert nur `domain` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/InstrumentedBackend.kt` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Hülle um das `QueryBackend`: Span `backend.execute` von `execute` bis `close` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` | update | Span `tds.query` je Anfrage, Ausgang an den Stellen der Antwort; Anfrage-Kennung je Anfrage in `step`; kleine Änderung, Konfliktfläche mit dem Zweig des Schreibpfads |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` (`ConnectionWiring.kt`, `Bootstrap.kt`, `TdsServer.kt`) | update | Span `tds.connection` im Handler, Verdrahtung des `Observer`, Zeitgeber des Schnappschusses (Aufgabe von `TdsServer`, endet mit `stop`) |
| `src/main/kotlin/io/github/pt9912/tdswire/config/` (`ServerConfig`, `ConfigValidation`) | update | Feld `metricsIntervalSeconds` (Voreinstellung 60, 0 = aus) und Prüfung |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/` (`ConfigLoader`, `EnvKeys`, `TelemetryJson`) | update | Lader liest `METRICS_INTERVAL_SECONDS`; Zeile für `metrics` und Span-Ende |
| `src/test/kotlin/io/github/pt9912/tdswire/observability/` und `runtime/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Unit-Tests des `Observer`; Rohbytes-Lauf mit Sammler (Login, Anfrage, Fehler, Cancel, Disconnect), Kardinalität, Ausfall der Senke; `@spec`-Tag SPEC-017, SPEC-018, SPEC-019 |
| `testserver/test/` und `src/test/.../TestserverLogProcessTest.kt` | update | Lader, Zeilen `metrics` und `span_ended` am Prozess `main` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestOutcomes.kt` | neu | Abbildung des Laufendes und des Ausgangs des Sequenzierers auf das `outcome` von `tds.query`; bei der Zerlegung von `RequestLoop` entstanden (Grenze `TooManyFunctions`) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/SpanOutcomesDispatchTest.kt` | neu | `outcome` an den Stellen der Schleife und der Hülle um das Backend, ohne Netz |
| `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/` (`TelemetryEmbeddingTest`, `TelemetryFelderEmbeddingTest`) | update | Ereignisliste mit den Enden der Spans, Felder und Schnappschuss über die öffentliche Fläche |
| `src/test/kotlin/` (`ConfigValidationTest`, `EnvironmentConfig`, `TelemetryEventTest`, `TelemetryRedactionTest`, `TestserverLogProcessTest`) | update | Bereich und Voreinstellung des Abstands, Ereignisse der neuen Arten, Typliste der Redaktion (`Map` nur für `metrics`), Zeilen am Prozess |
| `docs/plan/spec-abdeckung.md` | update | mit `make spec-abdeckung` erzeugt, `SPEC-017` bis `SPEC-019` mit bestandenen Tests |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/EffectiveSettings.kt`, `src/main/kotlin/io/github/pt9912/tdswire/runtime/TelemetryEvents.kt` | update | `METRICS_INTERVAL_SECONDS` in der wirksamen Konfiguration (Merge mit den Zustandsereignissen, [ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md)) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/ZustandsereignisseTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/SpanOutcomesBulkTest.kt` | update, neu | Ereignisfolgen mit Span-Enden; Spans auf dem Weg des Massenladens (`RequestLoop.answer`, Bulk-Zweig des Schreibpfads) |
| `spec/spezifikation.md` (Abschnitt 5, Abschnitt 4, Tabelle der Eingänge des Testservers), `docs/user/einbettung-und-betrieb.md` | update | Zielzustand wird gebaut: Stand der Zusage, Werte von `outcome`, Schlüssel von `metrics`, `METRICS_INTERVAL_SECONDS`; Konfliktfläche mit dem Zweig der Zustandsereignisse |

**Zuschnitt der Fläche (vorab).** Neue öffentliche Typnamen entstehen nicht: Das Ereignis `metrics` und das Span-Ende sind zwei Arten und sieben Zugriffe an `EventKind` und `TelemetryEvent`, ein Konstruktorparameter an `ServerConfig`. Die Ereignisklassen, die Schnittstelle der Spans und der `Observer` sind `internal`.

**Abweichung vom Plan-Wortlaut.** Der Abstand der Schnappschüsse ist kein Konstruktorparameter von `ServerConfig`, sondern `RequestSettings.metricsIntervalSeconds` (in `ServerConfig.requests`): ein siebter Parameter von `ServerConfig` bricht die Grenze `LongParameterList` (6) von detekt, und ein eigener Typ wäre ein neuer öffentlicher Name. Der Nutzer hat am 2026-11-07 entschieden, die Einstellungen in Typen zu bündeln ([slice-einstellungen-in-typen-buendeln](../open/slice-einstellungen-in-typen-buendeln.md)). Die Spezifikation führt den Ort (Abschnitt 5, `SPEC-062`). Dazu sind im Plan nicht vorhergesehen: `SPEC-062` als neue Zeile der Tabelle der Defaults (zweite Fundstelle des Abstands), der Zusatzschlüssel `spans_open` und die Hülle `InstrumentedBackend` statt einer Instrumentierung in `transport`.

Die Anfrage-Kennung in `REQUEST_FAILED` ist am 2026-11-07 entschieden und liegt bei [slice-request-failed-mit-request-id](../open/slice-request-failed-mit-request-id.md); dieser Lauf erweitert `REQUEST_FAILED` nicht.

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) und [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md) liegen in `done/` ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) ist `Accepted`, erfüllt; die Senke und der Adapter, der die neuen Ereignisarten serialisiert, stehen), und die Wellen [welle-anfrageverarbeitung](../done/welle-anfrageverarbeitung.md), [welle-backend-streaming](../done/welle-backend-streaming/welle-backend-streaming.md) und [welle-fehler-abbruch](../done/welle-fehler-abbruch.md) sind abgeschlossen (Anfrage, Backend-Aufruf und Abbruch existieren als Code; erfüllt: alle drei Wellen sind geschlossen, die Closure-Notiz von `welle-anfrageverarbeitung` ist `welle-anfrageverarbeitung-results.md` in `done/`), und ein Verantwortlicher ist im Kopf eingetragen (erfüllt: Implementer-Agent, Auftrag des Nutzers 2026-11-07).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Spans und Metriken als zwei Slices.
- `in-progress` → `open` (blockiert — Carveout?): die Umsetzung der Entscheidung zu Abbruch und Fehler (Cancel und Timeout als Fehler, [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) `Accepted`) steht noch nicht im Code, sodass die Zähler „Abbrüche“ und „Protokollfehler“ nicht festgelegt werden können.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung*, die Liste der `outcome`-Werte und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.


- „Abbrüche“ und „Protokollfehler“ hängen an Begriffen, die [slice-fehler-mapping](../done/slice-fehler-mapping.md) und [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md) umsetzen; [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) entscheidet seit dem 2026-11-01, dass Cancel und Timeout als Fehler zählen. — **Ausgang:** entfallen als Lücke: beide Slices liegen in `done/`, `aborts_total` zählt `cancelled` und `timeout`, `protocol_errors_total` zählt `protocol_error` (Festlegung in Abschnitt 5 der Spezifikation; Tests `ObserverTest`, `MetricsTracingWiringTest`).
- Die Senke eines Embedders kann blockieren (Vertrag „blockiert nicht“, akzeptiertes Negativ der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), Frage 7); die Kosten des Ereignisbaus und der Aggregation sind ungemessen. — **Ausgang:** eingetreten und getragen als akzeptiertes Negativ (siehe §7); die Kosten bleiben weiter offen → `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, Träger [slice-qa-performance](../open/slice-qa-performance.md). Folge am Nebenpfad (Verifikation V-7): `stop` wartet auf einen laufenden Schnappschuss und kehrt erst nach der Senke zurück; benannt in KDoc, Spezifikation und Nutzer-Doku, getestet mit einer Zeitgrenze (`MetricsTracingWiringTest`).
- Der Adapter trifft keine fachliche Entscheidung (ARC-009); wer in `protocol` oder `transport` instrumentiert, ruft nur die Schnittstelle. Die Importregeln belegen das nur über `import`-Zeilen (SPEC-041). — **Ausgang:** entfallen, soweit `import` es zeigt (Schichtgrenze: `observability` importiert nur `domain` und `java.*`, Verifikation); dass `RequestLoop` nichts anhand der Beobachtung entscheidet, ist Lesart des Reviews, kein Sensor (Grenze, so benannt).
- Overhead der Telemetrie unter Last ist ungemessen; dieser Slice sagt dazu nichts. — **Ausgang:** weiter offen → `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (Evidence dieses Slice, 11 Dateien), Messung in [slice-qa-performance](../open/slice-qa-performance.md).
- Die Grenzen der Latenz-Verteilung (Histogramm) sind eine Setzung; ein Perzentil, das aus ihr abgeleitet wird, trägt nur so weit wie diese Grenzen. — **Ausgang:** eingetreten und getragen: Setzung, nicht gemessen; ein Perzentil ist die Obergrenze seiner Klasse (Nearest-Rank), Test `LatencyHistogramTest`; Spezifikation und Nutzer-Doku sagen es.
- Ein Test mit einheitlichen SQL-Texten würde die Kardinalitätsgrenze nicht prüfen. — **Ausgang:** entfallen: der Test variiert die Texte (200 verschiedene, `MetricsTracingWiringTest`); die Mutation „SQL-Text als Kennung“ macht fünf Tests rot (Verifikation M5). Nicht gefahren: Schlüssel der Metriken aus Eingabe.
- `request_id` und `duration_ms` sind Pflicht-Logfelder der Spezifikation (Abschnitt 4); das Ereignismodell trug `request_id` nicht. — **Ausgang:** Nutzerentscheid 2026-11-07 (Empfehlung (b)): `requestId` kommt in `REQUEST_FAILED` → [slice-request-failed-mit-request-id](../open/slice-request-failed-mit-request-id.md); das Ereignis „Anfrage beendet“ bleibt Zielzustand der Spezifikation ohne Slice → weiter offen als Entscheid `BEO-TDS/request-failed-ohne-request-id` (Re-Evaluierungs-Trigger dort).

**Risiken, die im Lauf neu entstanden** (jeweils mit Ausgang):

- Der Abstand der Schnappschüsse ist der sechste Parameter von `RequestSettings` (Abweichung vom Plan-Wortlaut „Feld der `ServerConfig`“; aus Java entfallen die Konstruktoren mit weniger Parametern). — **Ausgang:** Nutzerentscheid 2026-11-07: die Einstellungen werden in benannten Typen gebündelt → [slice-einstellungen-in-typen-buendeln](../open/slice-einstellungen-in-typen-buendeln.md), Register `BEO-TDS/sechster-requestsettings-parameter-ohne-entscheidung` (entschieden); bis dahin bleibt der sechste Parameter mit benannten Aufrufen (Verifikation: kein positionaler Aufruf im Repo).
- Ein Bulk-Load-Paket (Typ 0x07) zählt im Span `tds.query` als `sql_batch`. — **Ausgang:** Nutzerentscheid 2026-11-07 (Empfehlung (a)): Setzung der kleinsten Menge von Werten, nicht gemessen; Register `BEO-TDS/bulk-load-als-sql-batch-beobachtet` (entschieden); Re-Evaluierungs-Trigger: ein Embedder verlangt eine eigene Marke. Der Weg ist seit `220e5c32` getestet (`SpanOutcomesBulkTest`).
- Der Stresstest der Atomizität des Span-Endes (`ObserverTest`, acht Threads, 5000 Runden) tötet die Mutation „Prüfen-dann-Setzen“ nicht (Verifikation V-8, M11). — **Ausgang:** weiter offen als benannte Grenze: die Atomizität ist durch `compareAndSet` gebaut und nur probabilistisch getestet; KDoc sagt „Stresstest, kein Beweis“; Träger: Register `BEO-TDS/atomizitaet-nur-probabilistisch-getestet`, kein Slice.
- Die Nachbesserung (`220e5c32`, `d6c4913b`) und der Übertrag in die Nutzer-Doku (`ee78d515`) sind weder gereviewt noch erneut verifiziert. — **Ausgang:** weiter offen als Vermerk → `BEO-TDS/nachbesserung-ohne-zweiten-review` (7×, Entscheid beim Nutzer).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

Closure am 2026-11-07 (Planner). Zuerst die Belege der Rollen, danach die Entscheide, die Zuordnung der Aussagen, das akzeptierte Negativ, die Grenzen und die Closure-Teile.

**Belege der Rollen.** Umsetzung `10afe022`, `dfa5146a`, `33f67126`; Review: [2026-11-07-33f67126-review-slice-betrieb-metriken-tracing](../../../reviews/2026-11-07-33f67126-review-slice-betrieb-metriken-tracing.md) (0 HIGH, 4 MEDIUM, 3 LOW, 4 INFO; F-1 bis F-4 merge-blockierend). Nachbesserung F-1 bis F-7, F-11: `85a7c00b`. Verifikation: [2026-11-07-4ed6a526-verifikation-slice-betrieb-metriken-tracing](../../../reviews/2026-11-07-4ed6a526-verifikation-slice-betrieb-metriken-tracing.md) (HEAD `4ed6a526`; 0 HIGH, 4 MEDIUM, 3 LOW, 4 INFO; zehn Bedingungen; die drei Liefer-Punkte sind in der Sache bestätigt; `make clean-build` Exit 0 mit 1183 Testfällen, Matrix frisch byte-gleich, neun von elf Mutationen rot). Behebung der Verifikation V-1 bis V-9: `220e5c32`, `d6c4913b`; Übertrag in die neue Nutzer-Doku: `ee78d515`. **Nachbesserung ohne zweiten Review:** die Nachbesserung `33f67126..85a7c00b` und die Merge-Auflösung hat die Verifikation wie einen Review bewertet (Abschnitte „Nachbesserung“ und „Merge-Auflösung“), das ersetzt keinen Re-Review in eigenem Kontext; `220e5c32`, `d6c4913b` und `ee78d515` sind **weder gereviewt noch erneut verifiziert** (Register `BEO-TDS/nachbesserung-ohne-zweiten-review`, Evidence dieses Slice, damit 7×; Entscheid beim Nutzer). Der Haken der Review-Zeile steht mit diesem Vermerk; die Closure-Commits (Planung, Register, Matrix) ändern kein Verhalten.

**Nutzerentscheide vom 2026-11-07.** „Deine Empfehlungen, Regelwerk konform“ gilt; Grundsatz „Spec ist Zielbild“.

| Frage (Verifikation) | Entscheid | Träger / Ausgang |
|---|---|---|
| V-5 (1) sechster Parameter von `RequestSettings` | **Typ statt Parameterliste:** die Einstellungen werden in benannten, gruppierten Typen gebündelt (nicht „so lassen“); bis dahin bleibt `metricsIntervalSeconds` der sechste Parameter, Aufrufe benannt | [slice-einstellungen-in-typen-buendeln](../open/slice-einstellungen-in-typen-buendeln.md); Register `BEO-TDS/sechster-requestsettings-parameter-ohne-entscheidung` (entschieden) |
| V-5 (2) `REQUEST_FAILED` ohne `requestId` | `requestId` in `REQUEST_FAILED` (Option (b)); das Ereignis „Anfrage beendet“ bleibt Zielzustand ohne Slice | [slice-request-failed-mit-request-id](../open/slice-request-failed-mit-request-id.md); Register `BEO-TDS/request-failed-ohne-request-id` (entschieden, Re-Evaluierungs-Trigger dort) |
| V-4 / V-5 (3) Bulk-Load als `sql_batch` | Setzung der kleinsten Menge von Werten (nicht gemessen); Re-Evaluierungs-Trigger: ein Embedder verlangt eine eigene Marke | Register `BEO-TDS/bulk-load-als-sql-batch-beobachtet` (entschieden); Spezifikation Abschnitt 5 |

**Entscheide des Planners zu den Bedingungen der Verifikation.**

| Bedingung / Finding | Entscheid | Ausgang |
|---|---|---|
| 1 Closure-Notiz | diese Notiz: Zuordnung, `outcome`-Werte, Negativ, Abweichung, Lerneintrag, sieben und vier neue Risiken | §6, unten |
| 2 / V-1 Duplikat in `open/` | behoben mit `220e5c32` (Datei entfernt, Verweise in `done/`); Prüfpunkt im Register `BEO-TDS/merge-bringt-verschobene-datei-zurueck` | Register |
| 3 / V-2 Satz der Spezifikation | behoben mit `220e5c32` (Abschnitt 4 führt Schnappschuss und Span-Ende als gebaut) | `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/zielbild-aussage-durch-merge-ueberholt` |
| 4 / V-3 Bulk-Load-Zweig | behoben: `SpanOutcomesBulkTest` (`ok`, `protocol_error`, `error`, `cancelled` auf dem Weg des Massenladens) | `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` |
| 5 / V-4, V-5 Plan, Träger | Plan-Tabelle, Kopf und Bezug auf Merge-Stand (`220e5c32`); die drei Entscheidungen haben Träger (oben) | `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` |
| 6 Register | fortgeschrieben (unten); `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` neu bewertet: bleibt gestrichen, die Mitglieder führt `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` (jetzt 2×) | Register |
| 7 Re-Review | **Nachbesserung ohne zweiten Review** (siehe Belege); die Verifikation gilt für `33f67126..85a7c00b` und die Merge-Auflösung, nicht für die späteren Commits | `BEO-TDS/nachbesserung-ohne-zweiten-review` |
| 8 Haken | gesetzt nach Deckung durch die Verifikation für die drei Liefer-Punkte; für die Nachbesserung nur mit dem Vermerk | §2 |
| 9 Nutzer-Doku | übertragen mit `ee78d515`; bei der Closure komplett gegen den Slice gelesen (unten) | unten |
| 10 V-6, V-7, V-8 | V-6: Wortlaut auf Kopie begrenzt (`MeasurementEvents`); V-7: Test mit Zeitgrenze und Satz in KDoc, Spezifikation, Nutzer-Doku; V-8: benannte Grenze (unten) | §6 |
| V-9 Cache-Hinweise | Belege der Tests sind `make clean-build` und der Frischlauf der Stage `smoke`, nicht `make gates FORCE=1` allein | `BEO-TDS/test-stage-cache-verdeckt-wiederholung` |

**Die Werte der Festlegungen (Zuordnung Test | Festlegung, `BEO-TDS/aussagen-ueber-messung-hinaus`).** Keine Zahl und keine Menge dieses Slice ist am echten SQL Server oder unter Last gemessen; gemessen sind nur die Läufe der Verifikation (Einzelmessungen unter Last 8 bis 26).

| Aussage | Art | Stelle |
|---|---|---|
| Menge der `outcome`-Werte: `tds.connection` `ok`, `login_rejected`, `aborted`, `error`, `cancelled`; `tds.query` `ok`, `error`, `protocol_error`, `cancelled`, `timeout`, `shutdown`, `disconnected`, `abandoned`, `closed`; `backend.execute` `ok`, `error`, `cancelled` | **Festlegung** (Menge und Bedeutung); die Abbildung jedes Werts auf seine Stelle ist **Test** | Spezifikation Abschnitt 5; `SpanOutcomesDispatchTest`, `SpanOutcomesBulkTest`, `MetricsTracingWiringTest` |
| `tds.phase` (`handshake`, `session`) und `tds.request_kind` (`sql_batch`, `rpc`; Bulk-Load als `sql_batch`) | **Festlegung** (Werte); Zuordnung **Test** | `MetricsTracingWiringTest`, `SpanOutcomesDispatchTest`, `SpanOutcomesBulkTest` |
| Klassengrenzen der Latenz 1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 30000, 60000 ms; Perzentil = Obergrenze der Klasse (Nearest-Rank); über 60000 ms der Wert 9223372036854775807 | **Festlegung** (Setzung, nicht gemessen); der Rang ist **Test** | `LatencyHistogramTest`, `ObserverTest` |
| Bereich des Abstands 0 bis 86400 s, Voreinstellung 60, 0 = aus | **Festlegung** ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), nicht gemessen); Prüfung **Test** (Mutation M3 rot) | `ConfigValidationTest`, `ConfigLoaderTest` |
| Schlüssel der Metriken (elf) sind fest; 200 verschiedene SQL-Texte ergeben dieselben Schlüssel; kein SQL-Text, keine Anmeldekennung, kein Parameterwert in einem Ereignis | **Test** (Mutation M5 rot, fünf Tests) | `MetricsTracingWiringTest`, `TelemetryRedactionTest` |
| Ein Span endet genau einmal (Cancel, Disconnect, Frist, Beenden, Ausnahme des Handlers); Ausnahme `abandoned`: `backend.execute` endet erst mit der Rückkehr des Backends | **Test** (Mutationen M1, M4, M6, M8, M10 rot) mit der Grenze der Atomizität unten | `ObserverTest`, `SpanOutcomesDispatchTest`, `MetricsTracingWiringTest` |
| `aborts_total` zählt `cancelled` und `timeout`, `protocol_errors_total` zählt `protocol_error` | **Festlegung** nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md); Zählung **Test** | `ObserverTest`, `MetricsTracingWiringTest` |
| Eine Senke, die eine `Exception` wirft, bricht keine Anfrage und keine Verbindung | **Test** | `MetricsTracingWiringTest`, `TelemetryTest` |
| Ein `Error` der Senke beendet den Zeitgeber, nicht den Server | **Festlegung** (der Senke wird ein `Error` nicht abgenommen); Folge **Test** (Wartezeit 2,3 s bei Abstand 1 s: unter extremer Last könnte der Test zu früh grün sein, nie falsch rot) | `MetricsTracingWiringTest` |
| `stop` beendet den Zeitgeber und wartet auf einen laufenden Schnappschuss | **Test** (Mutation M4 rot) | `MetricsTracingWiringTest` |
| Schnappschuss liest die Werte einzeln, `requests_total` und Verteilung können um einen Eintrag auseinanderliegen | **Festlegung**, benannt | Spezifikation Abschnitt 5, KDoc von `Metrics` |
| `metrics` und `settings`: Zeilen des Testservers `metrics.<Schlüssel>`; `METRICS_INTERVAL_SECONDS` als Eingang des Testservers | **Test** am Adapter und am Prozess | `TelemetryJsonTest`, `ConfigLoaderTest`, `TestserverLogProcessTest` |
| Ende der Spans und Schnappschuss über die öffentliche Fläche | **Test** | `TelemetryEmbeddingTest`, `TelemetryFelderEmbeddingTest` |
| Zwölfter Schlüssel `METRICS_INTERVAL_SECONDS` der wirksamen Konfiguration | **Test** (Mutation M13 rot) nach [ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) | `ZustandsereignisseTest` |
| Ereignis „Anfrage beendet“, eigenes Ereignis zum Abbruch durch ATTENTION | **Zielzustand ohne Slice** (Spezifikation) | Abschnitt 4 der Spezifikation |
| Overhead und Kosten der Aggregation | **nicht gemessen** | `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` |

**Akzeptiertes Negativ der blockierenden Senke ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 7, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)).** Das Ende eines Spans läuft auf dem Anfragepfad; eine Senke, die blockiert, hält das Ende von `tds.query` und `backend.execute` und damit den Anfragepfad an und belegt Threads von `Dispatchers.Default`; der Schnappschuss läuft in einer eigenen Aufgabe, aber `stop` wartet auf sie. Davor schützt die Bibliothek nicht. Das belegt **kein Test**, wie die DoD es verlangt; die Grenze steht in Spezifikation Abschnitt 5, in der KDoc und in der Nutzer-Doku (Aufgabe 10, Grenzen). Der Fall ist nicht gefahren; die Kosten des Ereignisbaus sind ungemessen.

**Grenzen (benannt, mit Träger).**

- *Atomizität des Span-Endes nicht deterministisch getestet (M11).* Gebaut ist `compareAndSet`; der Stresstest (acht Threads, Barriere, 5000 Runden) tötet „Setzen ohne Prüfung“ (M1), nicht „Prüfen-dann-Setzen“ (M11, Exit 0 unter Last 10 bis 26). Eine deterministische Prüfung mit einem Hook zwischen Lesen und Setzen ist nicht gebaut. Träger: Register `BEO-TDS/atomizitaet-nur-probabilistisch-getestet`, kein Slice.
- *Overhead ungemessen.* Träger [slice-qa-performance](../open/slice-qa-performance.md) und Register `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`.
- *Die Schichtgrenze belegt nur `import`* (SPEC-041): ob `RequestLoop` anhand der Beobachtung entscheidet, sieht kein Sensor.
- *Der Zeitgeber läuft auf `Dispatchers.Default`* (daemon-Threads) und endet mit `stop`; ohne `stop` bleibt die Aufgabe bis zum Prozessende.
- *`stop` kehrt nicht zurück, solange die Senke im Schnappschuss blockiert* (V-7): getestet mit Zeitgrenze, benannt.

**Nutzer-Doku (Schritt 6e) komplett gegen den Slice gelesen.** Alle Dateien unter `docs/user/` sind gegen den Stand nach dem Slice gelesen: Aufgabe 10 „Metriken und Spans lesen“ (`einbettung-und-betrieb.md`) mit Schritten, erwartetem Ergebnis, Bedeutung der Werte, Grenzen und typischen Fehlern; `referenz-konfiguration.md` (Zeile `requests.metricsIntervalSeconds`, `SPEC-062`); `referenz-ereignisse.md` (Arten `METRICS` und `SPAN_ENDED`, Werte von `outcome`, Klassengrenzen, Zeilen mit `LOG_LEVEL=DEBUG`); `testserver-manuell.md` (`span_ended` bei `DEBUG`, `METRICS_INTERVAL_SECONDS`). Gefunden und behoben bei der Closure: der Einstieg `docs/user/README.md` führte Aufgabe 10 nicht; die Zeile steht jetzt in der Liste „Ich möchte …“. Kein „nicht gebaut“-Satz zu Metriken und Spans, keine Chronik.

**Gelernt (Lerneintrag).** Fünf Klassen von Befunden, alle am Rand des Slice entstanden:

1. *Ein Merge bringt eine verschobene Datei zurück* (`open/slice-betrieb-zustandsereignisse.md` neben `done/`; „geändert gegen gelöscht“ zugunsten der Änderung aufgelöst); `make docs-check` meldet nichts. Prüfpunkt: nach jedem Merge `git diff --name-status <main> HEAD -- docs/plan/planning` auf hinzugefügte Dateien in Lifecycle-Verzeichnissen lesen. Register `BEO-TDS/merge-bringt-verschobene-datei-zurueck` (1×).
2. *Ein Test für einen Pfad fehlt* (Bulk-Load-Zweig, `abandoned`, `closed`: Mutationen M6, M10, M12 grün). Register `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (jetzt 23×, bereits verkörpert im Reviewer-Skill).
3. *Eine Aussage trägt nicht weit* (KDoc „wirft nicht und blockiert nicht“; „unveränderliche Kopie“; die Begründung zu `sql_batch` breiter als der Beleg). Register `BEO-TDS/aussagen-ueber-messung-hinaus` (bereits verkörpert in `AGENTS.md` §3.8).
4. *Ein Zielzustand-Satz der Spezifikation wird nach dem Bau falsch* (zweite Fundstelle, durch den Merge mit dem Text von `main` entstanden). Register `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (bereits verkörpert) und `BEO-TDS/zielbild-aussage-durch-merge-ueberholt` (jetzt 2×, Prüfpunkt: Aussagen über Verhalten am Prozess und Zielzustand-Sätze beim Merge gegen die Änderungen von `main` lesen).
5. *Der Übertrag in die Nutzer-Doku nach ihrer Neustrukturierung* gelingt nur über eine Übergabeliste und ist nicht gereviewt; ein Einstieg (`README.md`) wurde dabei vergessen. Register `BEO-TDS/nutzerdoku-uebertrag-nach-neustrukturierung` (1×).

- **Steering-Loop-Eintrag:** keine Regel verkörpert; die fünf Klassen sind gezählt, die Regeln zu Klasse 2 bis 4 gelten bereits (Reviewer-Skill, `AGENTS.md` §3.8). Der Lese-Schritt des Registers nennt dazu: *kein Eintrag über der Schwelle*, der mit diesem Slice neu die Schwelle erreicht, außer `BEO-TDS/nachbesserung-ohne-zweiten-review` (7×, ohne Ausgang, Entscheid beim Nutzer; der Lese-Schritt gehört in die Closure der Welle).
- **Beobachtungs-Register (`../observations/`):** neu angelegt `BEO-TDS/merge-bringt-verschobene-datei-zurueck`, `BEO-TDS/atomizitaet-nur-probabilistisch-getestet`, `BEO-TDS/nutzerdoku-uebertrag-nach-neustrukturierung` (je 1×), und die drei Einträge der Verifikation (`BEO-TDS/sechster-requestsettings-parameter-ohne-entscheidung`, `BEO-TDS/request-failed-ohne-request-id`, `BEO-TDS/bulk-load-als-sql-batch-beobachtet`, jetzt entschieden). `evidence/slice-betrieb-metriken-tracing.md` ergänzt in `BEO-TDS/nachbesserung-ohne-zweiten-review` (7×), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`, `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/zielbild-aussage-durch-merge-ueberholt` (2×), `BEO-TDS/test-stage-cache-verdeckt-wiederholung`, `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` (2×), `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`.
- **Folge-Slices:** [slice-request-failed-mit-request-id](../open/slice-request-failed-mit-request-id.md), [slice-einstellungen-in-typen-buendeln](../open/slice-einstellungen-in-typen-buendeln.md), [slice-qa-performance](../open/slice-qa-performance.md) (Start-Trigger: Telemetrie liefert Dauer je Schicht, jetzt erfüllt).
- **Risiken aus §6:** alle sieben und die vier neuen tragen einen Ausgang (§6).
- **Gates:** siehe unten (Läufe der Closure).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die Observability-Schicht (ARC-009) ist ein Pfad-Cluster (Achse 3) mit eigener Importregel (Achse 1 plausibel); eine eigenständig abgleichbare Inventur-Linie (Achse 2) besteht erst mit dem Code. Der Modus Greenfield bliebe derselbe, eine eigene Zeile in `harness/conventions.md` löste keine andere Regel aus; `*` bleibt, eine Ausdifferenzierung ist Sache des Nutzers und hier nicht vorgenommen.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`); nachgesehen am 2026-11-01 nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (zusätzlich berührt: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` *geplant* bei `slice-qa-performance`, Risiko in §6; `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung`, 3×, offen: die Start-Kanten in §4 nennen Rückführungen; `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, 10×, offen: die Reihenfolge Adapter-Slice vor diesem Slice ist Lesart des Planners):

- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 1×, offen. Betrifft jede Aussage über Telemetrie unter Last: keine (§6).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft den Kardinalitätstest: Eingaben variieren (§6).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über Spans und Metriken nennt den Lauf, an dem sie gemessen ist.
- `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` — 1×, offen. Betrifft „kein offener Span nach dem Lauf“: Zähler mit Untergrenze.
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft Tests in der Docker-Stage: gecachte grüne Läufe belegen Wiederholung nicht.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
