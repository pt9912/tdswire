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

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Fragen 2 und 6: Schnittstelle in `domain`, Aggregation in `observability`, Schnappschuss und Span-Ende als Ereignisse an die Senke)

**Berührte Spec-Stellen:** SPEC-017 · SPEC-018 · SPEC-019 · SPEC-029 · ARC-009 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

**Verantwortlich:** — bis zur Priorisierung.

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
- Das Log — [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md); Zustandsereignisse — [slice-betrieb-zustandsereignisse](../open/slice-betrieb-zustandsereignisse.md); der JSON-Adapter, die Warteschlange und das Prozessende des Testservers — [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md) (dieser Slice ergänzt dort nur die Serialisierung der neuen Ereignisarten).
- Adapter für JMX, einen Text-Endpunkt oder eine Telemetrie-Bibliothek — jeweils eigene Entscheidung mit eigener Angriffsflächen-Prüfung ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 6, Re-Evaluierungs-Trigger); der Zugang ist die Senke.
- Die Definition, ob Cancel und Timeout als Fehler zählen — [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md) und [slice-fehler-mapping](../done/slice-fehler-mapping.md); die Zähler „Abbrüche“ und „Protokollfehler“ folgen deren Entscheidung (§6).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Spans:** `tds.connection` (`connection.id`, `tds.phase`, `outcome`), `tds.query` (`session.id`, `request.id`, `tds.request_kind`, `outcome`) und `backend.execute` (`request.id`, `duration_ms`, `outcome`) an den Stellen von Transport und Handshake, Anfrageverarbeitung und Backend-Adapter; ein Rohbytes-Lauf (Login, Anfrage, Fehler, Cancel) erzeugt mit dem Sammler die erwarteten Spans mit den Pflicht-Attributen; die Menge der `outcome`-Werte ist eine Festlegung und steht so in der Closure.
- [ ] **Metriken und Schnappschuss:** aktive Sessions, Login-Erfolgs- und -Fehlerzähler, Anfragen, Anfrage-Latenz als Verteilung (p50, p95 und p99 als Voreinstellung der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), Offen (d): Perzentile oder Zähler je Klasse entscheidet der Bau mit Begründung), aktive Backendoperationen, Abbrüche, Protokollfehler; Aggregation in `observability` (importiert nur `domain`), Ausgabe als Ereignis `metrics`; der Abstand in Sekunden ist ein Feld der `ServerConfig` (Voreinstellung 60, 0 schaltet ihn aus; Setzung der ADR, nicht gemessen) mit Prüfung in `config`; der Testserver liest ihn aus `METRICS_INTERVAL_SECONDS` (Eingang des Testservers, nicht der Bibliothek); kein SQL-Text als Label; ein Test mit vielen verschiedenen SQL-Texten ergibt dieselbe Zahl an Zeitreihen (Kardinalität).
- [ ] **Zugang über die Senke und Ausfallsicherheit:** Span-Ende und Schnappschuss erreichen die Senke aus [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) (der Zugang ist die Senke; JMX, Text-Endpunkt und Telemetrie-Bibliothek sind nicht Teil); eine Senke, die wirft, bricht keine Anfrage und keine Verbindung (Test); eine Senke, die blockiert, hält den Anfragepfad an, und das belegt kein Test, sondern steht als akzeptiertes Negativ der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) in der Closure-Notiz; ein Span wird auch bei Cancel, Disconnect und Stromende genau einmal beendet (kein offener Span nach dem Lauf); der JSON-Adapter des Testservers serialisiert die neuen Ereignisarten (ein Rohbytes-Lauf gegen den Testserver zeigt `metrics` und Span-Ende als Zeilen).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/observability/` <!-- d-check:ignore (geplant: neue Datei) --> | update | Adapter für Spans und Metriken nach der ADR |
| Instrumentierung in `transport`, `protocol` und im Backend-Adapter | update | Aufrufe der Schnittstelle an den Stellen der drei Spans; keine fachliche Entscheidung im Adapter (ARC-009) |
| `src/main/kotlin/io/github/pt9912/tdswire/config/` (`ServerConfig`, `ConfigValidation`) | update | Feld und Prüfung für den Abstand der Schnappschüsse |
| `testserver/` | update | Lader liest `METRICS_INTERVAL_SECONDS`; Adapter serialisiert `metrics` und Span-Ende |
| `src/test/kotlin/io/github/pt9912/tdswire/observability/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Rohbytes-Lauf mit Sammler, Kardinalität, Ausfall des Adapters; `@spec`-Tag SPEC-017, SPEC-018, SPEC-019 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) und [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md) liegen in `done/` ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) ist `Accepted`, erfüllt; die Senke und der Adapter, der die neuen Ereignisarten serialisiert, stehen), und die Wellen [welle-anfrageverarbeitung](../done/welle-anfrageverarbeitung.md), [welle-backend-streaming](../done/welle-backend-streaming/welle-backend-streaming.md) und [welle-fehler-abbruch](../done/welle-fehler-abbruch.md) sind abgeschlossen (Anfrage, Backend-Aufruf und Abbruch existieren als Code; erfüllt: alle drei Wellen sind geschlossen, die Closure-Notiz von `welle-anfrageverarbeitung` ist `welle-anfrageverarbeitung-results.md` in `done/`), und ein Verantwortlicher ist im Kopf eingetragen (offen: der Kopf trägt „bis zur Priorisierung“).

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

- „Abbrüche“ und „Protokollfehler“ hängen an Begriffen, die [slice-fehler-mapping](../done/slice-fehler-mapping.md) und [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md) umsetzen; [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) entscheidet seit dem 2026-11-01, dass Cancel und Timeout als Fehler zählen (Wortlaut der Folgepflichten dort; die Abbruch-Slices legt ein anderer Planner an). Sind sie beim Start dieses Slice nicht in `done/`, sind die Zähler eine Festlegung dieses Slice und tragen den Vermerk. — **Ausgang:** offen bis zur Closure.
- Die Senke eines Embedders kann blockieren (Vertrag „blockiert nicht“, akzeptiertes Negativ der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), Frage 7); die Kosten des Ereignisbaus und der Aggregation sind ungemessen. — **Ausgang:** offen bis zur Closure (erwartet: weiter offen → `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, geplant bei [slice-qa-performance](../open/slice-qa-performance.md)).
- Der Adapter trifft keine fachliche Entscheidung (ARC-009); wer in `protocol` oder `transport` instrumentiert, ruft nur die Schnittstelle. Die Importregeln belegen das nur über `import`-Zeilen (SPEC-041). — **Ausgang:** offen bis zur Closure.
- Overhead der Telemetrie unter Last ist ungemessen; dieser Slice sagt dazu nichts (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`), die Messung folgt in [slice-qa-performance](../open/slice-qa-performance.md). — **Ausgang:** offen bis zur Closure.
- Die Grenzen der Latenz-Verteilung (Histogramm) sind eine Setzung; ein Perzentil, das aus ihr abgeleitet wird, trägt nur so weit wie diese Grenzen. — **Ausgang:** offen bis zur Closure.
- Ein Test mit einheitlichen SQL-Texten würde die Kardinalitätsgrenze nicht prüfen; der Test variiert die Texte (`BEO-TDS/strukturtest-ohne-variation-der-eingabe`). — **Ausgang:** offen bis zur Closure.
- `request_id` und `duration_ms` sind Pflicht-Logfelder der Spezifikation (Abschnitt 4, „Zielzustand, nicht gebaut“); das Ereignismodell aus [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) trägt `request_id` nicht, auch nicht im Anfragefehler (`BEO-TDS/dod-nennt-pflichtfeld-das-die-spezifikation-als-zielzustand-fuehrt`). Dieser Slice bildet die Kennung für die Spans (`request.id`); ob sie auch als Feld in `REQUEST_FAILED` und in ein Ereignis „Anfrage beendet“ geht, entscheidet er mit dem Nutzer, weil ein Feld die öffentliche Fläche erweitert (`BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung`). — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

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
