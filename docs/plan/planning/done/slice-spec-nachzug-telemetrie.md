# Slice slice-spec-nachzug-telemetrie: Spezifikation auf die Observability-Schnittstelle nachziehen (Pflicht-Logfelder, Zugang zu Metriken und Spans, Eingänge des Testservers)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance); [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Folgepflicht 1), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Accepted`, Entscheidungen 6 und 7: Eingänge des Testservers), [ADR-0009](../../adr/0009-authentifizierungsmodus.md) (überlässt das Feld der Anmeldekennung der Spezifikation).

**Berührte Spec-Stellen:** [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose) (Nr. 3) · SPEC-012 (Logfelder) · SPEC-017 · SPEC-018 · SPEC-019 · Abschnitt 4 (Pflicht-Logfelder) · Abschnitt 5 (Metriken und Tracing-Felder) · „Pflichten des Authentifizierungsadapters“ (Satz „bisher keines“) · die Festlegungen der Läufe gegen den Testserver · Änderungsprotokoll der Spezifikation.

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `spec/spezifikation.md` sagt, was [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) für Log und Telemetrie entscheidet: Die Pflicht-Logfelder tragen `ts`, `level` und `principal` (nur bei angenommener Anmeldung), `client_profile` hat eine Quelle, die Herkunft der IDs ist festgelegt; Abschnitt 5 nennt den Zugang zu Metriken und Spans (die Senke der Bibliothek); `LOG_LEVEL`, `METRICS_INTERVAL_SECONDS` und das Format der JSON-Zeile stehen als Aussagen über den Testserver da. Der Satz „bisher keines“ steht nirgends mehr.

**Befund, der den Slice begründet** (am 2026-11-01 in `spec/spezifikation.md` und in der Folgepflicht (1) der ADR gelesen, nichts ausgeführt): Abschnitt 4 führt die Pflicht-Logfelder `connection_id`, `session_id`, `request_id`, `event`, `duration_ms`, `error_code`, `client_profile` ohne `ts`, `level`, ohne Feld für die Anmeldekennung und ohne Quelle für `client_profile`; `TDS-FA-09.a` Nr. 3 und der Abschnitt „Pflichten des Authentifizierungsadapters“ sagen beide „bisher keines“ für die Kennung im Log (zwei Fundstellen derselben Aussage); Abschnitt 5 nennt Spans und Metriken, aber keinen Zugang; `LOG_LEVEL` und `METRICS_INTERVAL_SECONDS` kommen nicht vor.

**Zuschnitt (begründet).** Dieser Slice ist der zweite Spec-Nachzug neben `slice-spec-nachzug-produktschnitt`: Beide zusammen hätten sechs statt drei Liefer-Punkte. Die Schnittlinie ist die Aussage („Produktschnitt“ gegenüber „Telemetrie“) und der Abschnitt der Spezifikation (3, 6, 7 dort; 4, 5 und `TDS-FA-09.a` hier). [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) verlangt den Nachzug „in einem Zug“ mit dem von [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md); der Slice wartet deshalb unmittelbar auf den ersten (§4) und läuft danach ohne Zwischenschritt. Er übernimmt die Spec-Seite des Liefer-Punkts 3 von `slice-adr-observability` („Anschlussstellen nachgezogen“); die Closure jenes Slice nennt den Übergang (Setzung des Planners, zur Bestätigung durch den Nutzer).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Namen der Senke, des Ereignismodells und des Parameters von `start` in `SPEC-059`: Arbeitsnamen der ADR (Offen (g)); sie trägt `SPEC-059` mit der Closure von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) (`AGENTS.md` §3.8).
- Die Fläche und Läufe des Produktschnitts (`SPEC-052`, `SPEC-005`, `SPEC-010`, `SPEC-023`, `SPEC-035`, `SPEC-040`, `SPEC-059` ohne die Senke) — [slice-spec-nachzug-produktschnitt](../done/slice-spec-nachzug-produktschnitt.md), Voraussetzung dieses Slice.
- Code, Tests, Skripte und Gates — [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md), [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md), [slice-betrieb-metriken-tracing](../next/slice-betrieb-metriken-tracing.md); der Slice sagt, was gilt, und baut nichts.
- Das Lastenheft und angenommene ADRs (`AGENTS.md` §3.5). Ob der Satz zum Log in [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 1 eine Folge-ADR mit `Supersedes` braucht (Offen (b) der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md)), entscheidet der Nutzer; der Slice meldet es nicht neu.
- Die Entscheidung, ob das Ereignis `metrics` Perzentile oder Zähler je Klasse trägt (Offen (d)): sie trägt [slice-betrieb-metriken-tracing](../next/slice-betrieb-metriken-tracing.md); die Spezifikation behält p50, p95, p99 als Voreinstellung.
- Die Quelladresse der Gegenstelle im Ereignis (Offen (e), Datenschutz) — gehört zur Entscheidung zur Anmeldedrosselung ([ADR-0016](../../adr/0016-anmeldedrosselung.md)); die Spezifikation führt sie nicht als Pflichtfeld.
- Die Spec-Folgepflichten aus [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (`SPEC-016`, `SPEC-029`, `SPEC-001`, `SPEC-030` und weitere) — sie gehören in die Abbruch- und Reset-Slices, die ein anderer Planner anlegt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Pflicht-Logfelder und Ereignismodell** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): Abschnitt 4 nennt die Pflichtfelder um `ts` (UTC; das Format über UTC hinaus ist Voreinstellung ISO-8601 im Adapter, Offen (c)) und `level` ergänzt; `principal` steht nur im Ereignis „Anmeldung angenommen“ (Frage 3 B), für abgelehnte Anmeldungen gibt es kein Feld; `client_profile` ist aus dem ausgehandelten Protokoll abgeleitet (`tds-7.4` oder `tds-8.0` mit Verschlüsselungsmodus, vor der Aushandlung `unknown`, Frage 5 C); `connection_id` ist der Zähler des Listeners (ab 1 je Prozessstart, wiederholt sich nach einem Neustart), `session_id` und `request_id` sind zufällige UUID (Setzung, nicht gemessen); Parameterwerte erscheinen nie (Frage 4 A; der Satz „standardmäßig nicht“ wird durch „nie“ ersetzt, weil es keinen Schalter gibt); das Modell ist eine geschlossene Menge von Ereignisarten ohne Freitextfeld, `error_code` ein `TDS_*`-Code, Ausnahmen nur als Typname; die Zeichenketten-Felder sind auf 256 Zeichen begrenzt (Setzung). Die Redaktion „durch Bauart“ gilt für das Modell und für jeden Adapter; nicht zugesagt sind Escaping, Ziele und Aufbewahrung im Adapter eines Embedders und die Kennung eines eigenen Authentifizierungsadapters (Frage 2). `TDS-FA-09.a` Nr. 3 und der Abschnitt „Pflichten des Authentifizierungsadapters“ verweisen auf das Feld statt „bisher keines“ zu sagen. Die Ereignisarten der ADR stehen als Entwurf (Setzung), nicht als geschlossene Liste der Spezifikation.
- [x] **Zugang zu Metriken und Spans** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance)): Abschnitt 5 sagt: Schnappschuss der Metriken und Ende eines Spans gehen als Ereignisse an die Senke der Bibliothek, die ein Parameter von `TdsServer.start` ist (Voreinstellung keine Senke: dann schreibt die Bibliothek nichts); der Vertrag der Senke (blockiert nicht, wirft nicht, Besitz und Schließen beim Übergebenden; die Bibliothek fängt Ausnahmen ab) steht als Festlegung; der Abstand der Schnappschüsse ist ein Wert der `ServerConfig` (Voreinstellung 60 Sekunden, 0 schaltet ihn aus; Setzung, nicht gemessen) mit Prüfung in `config`; Namen und Pflicht-Attribute der Spans (`SPEC-017` bis `SPEC-019`) bleiben; kein SQL-Text als Label; JMX, Text-Endpunkt und Telemetrie-Bibliothek sind nicht Teil der Zusage (sie wären Adapter hinter der Senke).
- [x] **Aussagen über den Testserver** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidungen 6 und 7): Bei den Festlegungen der Läufe gegen den Testserver (angelegt von `slice-spec-nachzug-produktschnitt`) stehen: `LOG_LEVEL` (`ERROR`, `WARN`, `INFO`, `DEBUG`; Voreinstellung `INFO`; ein anderer Wert bricht den Start des Testservers ab) und `METRICS_INTERVAL_SECONDS` (Quelle des Werts der `ServerConfig`) als Eingänge des Testservers, nicht der Bibliothek; Format (eine JSON-Zeile je Ereignis, Felder `ts`, `level`, `event` und die des Ereignisses), begrenzte Warteschlange, Verlustzähler `dropped` und Frist beim Prozessende als Aussagen über den Testserver (Setzung, kein Vertrag der Bibliothek); die Bereitschaftszeile bleibt Sache des Testservers und ist kein Ereignis des Modells. Eine Suche über `spec/spezifikation.md` und `spec/architecture.md` nach `bisher keines`, `client_profile`, `Standardlogging`, `Serverprozess`, `LOG_LEVEL` und `METRICS_INTERVAL` findet nur Treffer, die die neue Lage sagen; die Trefferliste steht in der Closure-Notiz (gesucht wird die Aussage, nicht die genannte Zeile: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`).

Wortlaut und Zuschnitt folgen [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Folgepflicht 1 (beim Übergang `open` → `next` gegen die ADR geprüft). Jede Aussage trägt ihren Beleg als Test oder ist als Festlegung gekennzeichnet (`AGENTS.md` §3.8); die Spezifikation nennt keine ADR-Bezüge in Sicht-Dokumenten (`AGENTS.md` §3.4).

Nachweis-Pflichten für alle Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün. (Entfällt dem Inhalt nach: der Slice ändert nur `spec/spezifikation.md`, kein Test ist hinzugekommen; die Menge der `SPEC-*`-Kennungen ist gleich, `make spec-abdeckung` lief, die Matrix blieb unverändert.)
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md`, Abschnitt 4 (Pflicht-Logfelder) | update | `ts`, `level`, `principal`, `client_profile`, Herkunft der IDs, „nie“ für Parameterwerte, Grenze der Redaktion |
| `spec/spezifikation.md`, `TDS-FA-09.a` Nr. 3 und „Pflichten des Authentifizierungsadapters“ | update | „bisher keines“ ersetzt: dieselbe Aussage an zwei Stellen |
| `spec/spezifikation.md`, Abschnitt 5 (Metriken und Tracing-Felder) | update | Zugang über die Senke, Abstand der Schnappschüsse als Wert der `ServerConfig`, Vertrag der Senke |
| `spec/spezifikation.md`, Festlegungen der Läufe gegen den Testserver (Abschnitt 7) | update | `LOG_LEVEL`, `METRICS_INTERVAL_SECONDS`, JSON-Zeile, Warteschlange, Bereitschaftszeile als Aussagen über den Testserver |
| `spec/spezifikation.md`, Änderungsprotokoll | update | eine Zeile |
| `spec/architecture.md` (`ARC-009`, `ARC-014`) | prüfen | nur ändern, wenn eine Aussage der Entscheidung widerspricht; ohne ADR-, Wellen- und Slice-Bezug (`AGENTS.md` §3.4) |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung`, falls sich die Menge der Festlegungen ändert (erwartet: keine neue Kennung) |

Fundstellensuche (Teil der Arbeit, nicht des Ergebnisses): `bisher keines`, `client_profile`, `Standardlogging`, `Pflicht-Logfelder`, `Korrelations`, `LOG_LEVEL`, `METRICS_INTERVAL`, `Serverprozess`, `Observability` über `spec/`. Treffer außerhalb der genannten Stellen, die dieselbe Aussage tragen, gehören in diesen Slice.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`), alle Bedingungen beobachtbar:

1. [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) steht im ADR-Index auf `Accepted` (erfüllt: Annahme am 2026-11-01).
2. [slice-spec-nachzug-produktschnitt](../done/slice-spec-nachzug-produktschnitt.md) liegt in `done/`: dieselbe Datei, dieselben Festlegungen der Läufe gegen den Testserver; der Wortlaut „Eingänge des Testservers“ steht, bevor dieser Slice ihn ergänzt.
3. Der Start-Trigger von [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) ist eingetreten (erfüllt: die Wellen `welle-transport-tls` und `welle-handshake-login` liegen in `done/`).
4. Ein Verantwortlicher ist im Kopf eingetragen.

**Folge-Kante:** [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) wartet auf diesen Slice (die Spezifikation führt, der Code folgt).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Fundstellensuche (§3) findet Festlegungen außerhalb der drei Liefer-Punkte, die dieselbe Aussage tragen und mehr als einen weiteren Punkt ergäben; Zerlegung nach Abschnitt der Spezifikation (4 | 5 | Testserver).
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) wird durch eine Folge-ADR abgelöst; oder der Nutzer entscheidet die Lesart von [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) (Happy Path bei Voreinstellung „keine Senke“, Offen (a); Lastenheft 0.6.1 stützt die Lesart der Zusage) anders, als die Zusage in Liefer-Punkt 2 sie trägt; oder `slice-spec-nachzug-produktschnitt` fällt nach `open/` zurück.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make docs-check`), der Review-Report liegt vor, die Trefferliste der Fundstellensuche steht in der Closure-Notiz, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke). Gemessen ist die Gleichheit von Spezifikation und [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (Selbstkonsistenz), nicht ein Verhalten des Codes: Der Code kennt bis zur Closure von `slice-betrieb-log-schnittstelle` keine Senke.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Spezifikation sagt früher als der Code (Greenfield, Spec führt): zwischen diesem Slice und `slice-betrieb-log-schnittstelle` nennen Abschnitt 4 und 5 Felder und einen Zugang, den `src/main` nicht kennt (`ServerLog` schreibt Text). — **Ausgang:** weiter offen (kein Verschwinden vor dem Bau): die Spezifikation trägt den Vermerk „Zielzustand, nicht gebaut“ in 13 Zeilen (Zählung per Suche am Stand `8490fe7`, alle Themen, nicht nur dieser Slice); Träger [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md), [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md), [slice-betrieb-metriken-tracing](../next/slice-betrieb-metriken-tracing.md) entfernen die Vermerke (Nachweis-Pflicht dort, einzutragen von deren Planner). Eingetreten ist die Hälfte des Risikos in der Form „falscher Marker“ (Verifikation F-1), behoben in `ddb5bd4`.
- „Bisher keines“ steht an zwei Stellen (`TDS-FA-09.a` Nr. 3 und „Pflichten des Authentifizierungsadapters“), und eine dritte mit anderem Wortlaut kann existieren (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, 3×). — **Ausgang:** eingetreten und behoben: beide Stellen ersetzt, die dritte Fundstelle (Z. 896 „Log-Eintrag“, Marker) gefunden; die Verifikation fand zusätzlich F-1 (falscher Marker) und F-3 (SPEC-043 unbedingt), beide in `ddb5bd4` behoben; F-2 (SPEC-010) nach dem Umbau-Merge in `8490fe7`. Die Fundstellensuche nach dem Merge ist nicht unabhängig wiederholt (§7).
- Die Lesart von [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) Happy Path („relevante Zustandsereignisse werden protokolliert“) ist bei Voreinstellung „keine Senke“ „protokolliert, sobald der Integrator eine Senke hat“; der Nutzer hat sie mit der Annahme nicht ausdrücklich bestätigt (Offen (a)). — **Ausgang:** entfallen: Lastenheft 0.6.1 schreibt „ohne übergebene Diagnoseausgabe schreibt die Bibliothek kein Log“; die Lesart ist bestätigt, `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` bekommt keine weitere Evidenz.
- Zahlen der Spezifikation (60 Sekunden, 256 Zeichen, 1024 Zeilen, 2 Sekunden) sind Setzungen ohne Messung; die Spezifikation kennzeichnet sie als Setzung (`AGENTS.md` §3.8). — **Ausgang:** weiter offen: 60 s, 256 Zeichen, 1024 Zeilen, 2 s stehen in der Spezifikation als Setzung, nicht gemessen (Verifikation I-2: die Kennzeichnung der beiden letzten hängt am Blockkopf von `SPEC-052`); die Messung trägt [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md) und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`.
- Instanztrennung bei mehreren Servern in einer JVM (Offen (f)) und die Quelladresse (Offen (e)) bleiben offen; die Spezifikation führt sie weder als Pflichtfeld noch als Zusage. — **Ausgang:** weiter offen: (f) Träger [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md), (e) die Entscheidung zur Anmeldedrosselung ([ADR-0016](../../adr/0016-anmeldedrosselung.md)); die Spezifikation führt beide als „nicht festgelegt“.
- Zwei Träger für dieselbe Spec-Arbeit: `slice-adr-observability` nannte in seiner DoD den Nachzug der Abschnitte 4 und 5; dieser Slice übernimmt ihn. — **Ausgang:** eingetreten und beidseitig vermerkt: `slice-adr-observability` (Closure 2026-11-01) übergibt die Spec-Seite des Liefer-Punkts 3 hierher und hat sie nicht geliefert; die Bestätigung der Übergabe durch den Nutzer steht aus.
- `StartOutcome.Aborted.reason` gegen die Regel zum Prozessende (Review M-2 von `slice-adr-observability`): [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) setzt „der Grund des Abbruchs steht im `StartOutcome`, derselbe Inhalt wie das Abbruch-Ereignis“, und das Ereignis nennt bei Bindefehlern Adresse und Port; die Spezifikation legt `reason` als „Text ohne Konfigurationswert, Pfad und Ausnahmetext“ fest, und `SPEC-043` verlangt für den belegten Port eine Diagnose, die Adresse und Port nennt. Der Nachzug entscheidet beim Schreiben von Abschnitt 4 und der Zeile `StartOutcome.Aborted`, wo die Adresse steht (im Ereignis des Testservers, nicht im `reason`; oder `reason` mit Adresse als Ausnahme der Regel) und schreibt es ohne Widerspruch zu `SPEC-043`; [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) bleibt unverändert (`AGENTS.md` §3.5). — **Ausgang:** weiter offen, Nutzerentscheidung M-4 (§7): gebaut nennt `StartOutcome.Aborted.reason` Adresse und Port („Start gescheitert: Port belegt: <Adresse>:<Port>“, `Bootstrap.kt`, `ListenerConfig.kt`); die Spezifikation sagt „ohne Konfigurationswert“ und trägt den Zielzustand-Vermerk, `SPEC-043` die Bedingung „ohne Senke keine Diagnose“. Widerspruch zu `SPEC-043` ist beseitigt, die Entscheidung über den Inhalt von `reason` nicht; sie wird hier nicht getroffen.
- Offen (a) der ADR ist überholt: Lastenheft 0.6.1 (Commit `902ab72`, 2026-11-01) schreibt „Zustandsereignisse werden an die Diagnoseausgabe gegeben; ohne übergebene Diagnoseausgabe schreibt die Bibliothek kein Log“ und bestätigt damit die Lesart „keine Senke, kein Log“ (Review L-1). Das Risiko zur Lesart oben und die Rückführung nach `open` (§4) beziehen sich darauf; der Nachzug prüft den Wortlaut des Lastenhefts und schließt die Lesart, statt sie als unbestätigt zu führen (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` bekommt dann keine weitere Evidenz). — **Ausgang:** entfallen: der Wortlaut des Lastenhefts 0.6.1 ist geprüft, die Spezifikation folgt ihm („ohne Senke schreibt die Bibliothek nichts“); die Rückführung nach `open` (§4) ist nicht eingetreten.

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

**Umfang und was nicht unabhängig verifiziert ist.** Verifiziert hat der Verifier ([Bericht](../../../reviews/2026-11-01-9d86dc0-verifikation-slice-spec-nachzug-telemetrie.md)) den Stand `9d86dc0`: closure-fähig mit Bedingungen F-1, F-3, F-2. Danach kamen `ddb5bd4` (F-1 und F-3 behoben, Fehlertext-Befund des Startpfads) und der Merge `8490fe7` (Umbau, Abbruchpfad) mit Konfliktlösung im Absatz zu `SPEC-048` und der Senke-Bedingung in `SPEC-010` (F-2). **Dieses Delta wurde nicht unabhängig verifiziert.** `git diff 9d86dc0..HEAD -- spec/` (nur `spec/spezifikation.md`, +76/-64): der größere Teil kommt von `main` und gehört nicht zu diesem Slice (Abbruchpfad: Abschnitt „Fehler“, Zustandsautomat, `SPEC-029`, `SPEC-016`, Tabelle `TDS_TIMEOUT`; Umbau: Entfernen der Zielzustand-Vermerke zu `SPEC-035`, `SPEC-051`, `SPEC-059`, Eingang des Testservers in `SPEC-008`, `SPEC-009`, `SPEC-033`, `SPEC-046`, Änderungsprotokoll). Zu diesem Slice gehören: (1) `SPEC-010` „im Log sichtbar (wenn eine Senke übergeben ist)“; (2) `SPEC-043` Zusatz „ohne übergebene Senke gibt es sie nicht, Zielzustand, nicht gebaut“; (3) `SPEC-048` und der Absatz zur Fehlerzuordnung: der falsche Marker ist ersetzt durch „Gebaut ist im Anfragepfad die Textzeile mit Code und vollqualifiziertem Typnamen, nie der Text der Ausnahme; Zielzustand: Format und Felder“, der Startpfad (Meldung des Ladefehlers mit Pfad, Zeile, Spalte) ist als gebaut-abweichend genannt; (4) `Aborted.reason`: der gebaute Wortlaut „Start gescheitert: Port belegt: <Adresse>:<Port>“ steht als Zielzustand-Abweichung. Gelesen und durch `make docs-check` und `make gates` (Einzelmessung, siehe unten) geprüft, nicht von einem frischen Kontext gegen Code und [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) gegengelesen. Die Fundstellensuche (unten) stammt vom Verifier vor dem Merge und wurde danach nicht wiederholt; die Marker-Hälfte der Suche (Zeilen 911 bis 975 des Verifikationsstands) ist nach dem Merge verschoben.

**Zuordnung der Aussagen** (Slice ohne Code und ohne neuen Test):

- *Gemessen:* nichts in diesem Slice. Die Marker zum gebauten Stand sind gegen den Quelltext gelesen (nicht ausgeführt): `ServerLog.line(text)`, `ErrorMapper.kt:80-83`, `ErrorMapperTest.kt:264-272`, `Bootstrap.kt:130,178,181,187,192`, `SimulationSetup.kt:41`.
- *Handvektor:* keiner.
- *Selbstkonsistenz Spezifikation gegen [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md):* Frage 1 bis 8 deckungsgleich (Verifikation); bewusste Abweichung `Aborted.reason` (M-4 unten); gegenproben des Verifiers: ein ADR-Bezug in `spec/` macht `make docs-check` rot, das Skript für Aussagen ohne Marker fand auf der Testkopie genau die ungekennzeichneten Zeilen.
- *Festlegung / Setzung, nicht gemessen:* `ts` UTC mit Format ISO-8601 als Voreinstellung des Adapters; `connection_id` als Zähler ab 1 je Prozessstart; `session_id` und `request_id` als UUID; 256 Zeichen; 60 s und 0 schaltet aus; Senken-Vertrag; Eingänge `LOG_LEVEL` und `METRICS_INTERVAL_SECONDS`; 1024 Zeilen, `dropped`, 2 s; Bereitschaftszeile kein Ereignis; die Ereignisarten als Entwurf.
- *Nicht gemessen und nicht zugesagt:* Escaping, Ziele und Aufbewahrung im Adapter eines Embedders; JMX, Text-Endpunkt, Telemetrie-Bibliothek; Perzentile oder Zähler (Offen (d)); Quelladresse (e); Instanztrennung (f); Namen der Senke und des Parameters (g).
- *Die Spec führt dem Code voraus* (Greenfield): Marker „Zielzustand, nicht gebaut“ an den Aussagen zu `ts`, `level`, `principal`, Senke, `LOG_LEVEL`, `METRICS_INTERVAL_SECONDS`, Ereignisformat; die Ausnahme-Typnamen im Anfragepfad sind schon gebaut.

**Trefferliste der Fundstellensuche** (Verifier, Stand `9d86dc0`, Begriffe laut §3): `bisher keines` 0 Treffer; `Standardlogging` 0; `ins Log`: Zeilen 98 und 853 stimmen mit Abschnitt 4 überein, 835 (Drosselung, „heute schreibt sie nichts ins Log“) ohne Widerspruch, 1088 („damit sie ins Log geht“) unkritisch; `im Log`: 155, 905, 1111, 1197 konsistent, 832 (`SPEC-010`) war F-2 und trägt jetzt die Bedingung; `Standardausgabe` 1290 und `Serverprozess` 1287/1290 ok; `LOG_LEVEL`/`METRICS_INTERVAL` 1290/1331 ok; `Korrelation`: Spezifikation 150 neu formuliert, `spec/architecture.md` 208 verträglich (Datei unverändert, kein ADR-, Wellen- oder Slice-Wort); `Start-Log` 1213 konsistent; `Log-Eintrag` 896 wahr, Marker falsch (F-1, behoben); `geloggt` 153 und 1193 ok; `Server-Log` 34 und 1191 ohne Senken-Bedingung, unkritisch (Verifikation I-1). Aus der Suche hervorgegangen und im Slice behoben: F-1 (falscher Marker an `SPEC-048` und im Absatz zur Fehlerzuordnung), F-3 (`SPEC-043` unbedingt), F-2 (`SPEC-010`).

**Bewusst nicht in der Spezifikation** (Verifier, mit Grund; keine Zusage, weil Verhalten des Adapters oder Baus): `TdsServer.stop()` kehrt erst nach dem Ende-Ereignis zurück; der Schreiber-Thread startet erst mit dem laufenden Server; die Bereitschaftszeile läuft nie über die verwerfende Warteschlange; das Prozessende nach der Frist auch bei hängendem Kanal; die Escaping-Zusage des Testserver-Adapters. Sie gehören in [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md) beziehungsweise [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) und sind dort zu entscheiden, bevor sie als Aussage der Spezifikation stehen.

**Offene Nutzerentscheidung M-4 (nicht entschieden).** Gebaut nennt `StartOutcome.Aborted.reason` Adresse und Port (`ListenerConfig.kt`, `Bootstrap.kt`); die Spezifikation sagt `reason` sei „ein Text ohne Konfigurationswert“ und Abschnitt 4 führt die Adresse nur im Ereignis; [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) sagt „derselbe Inhalt wie das Abbruch-Ereignis“. Empfehlung des Koordinators (Setzung, keine Entscheidung): `reason` darf Adresse und Port nennen, die Regel der Spezifikation wird präzisiert. Die Wirkung: `TDS-FA-09` verlangt bei belegtem Port eine verständliche Diagnose, und ohne Senke wäre `reason` die einzige. Vermerkt in [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) §4 als Start-Bedingung.

**Offen (a)** der ADR ist überholt: Lastenheft 0.6.1 bestätigt die Lesart „keine Senke, kein Log“ (§6: entfallen).

**Closure (Planner, 2026-11-01):**

- **Was hat funktioniert:** Der Review ([Bericht](../../../reviews/2026-11-01-02db795-slice-spec-nachzug-telemetrie.md)) fand fünf MEDIUM vor der Verifikation; die Verifikation fand F-1 bis F-3 und die Folgepflichten (Testserver-Skript, Merge-Hunk). Die Gegenprobe „ADR-Bezug in `spec/`“ ist rot geworden, die Regel `AGENTS.md` §3.4 trägt das Gate.
- **Was ging anders als geplant:** Der Reviewer behauptete, der gebaute Code schreibe den Text der Ausnahme ins Log (M-2); das war falsch (`ErrorMapper.unexpected` übergibt den Typnamen). Die Fix-Runde ersetzte einen zuvor richtigen Satz des Vorbestands („Code und Typname, nicht ihren Text“) durch den falschen Marker; der Verifier korrigierte es gegen den Quelltext (F-1). Die zweite Fundstelle (`SPEC-043`, `SPEC-010`) blieb wie in den vier Slices davor stehen.
- **Steering-Loop-Eintrag (Lerneintrag):** Eine Behauptung über den gebauten Stand (Marker „Zielzustand, nicht gebaut“, „der gebaute Stand schreibt …“) wird vor dem Schreiben in die Spezifikation gegen den Quelltext belegt, mit Datei und Zeile im Slice; die Behauptung eines Reviewers ist kein Beleg. Gezählt, nicht verkörpert (kein `liegt in`): Zielort wäre das Reviewer-Skill und die Hard Rule 3.8; die Entscheidung über Verkörperung liegt beim Nutzer beziehungsweise bei der Welle-Closure.
- **Über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer:** `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` steht mit diesem Slice bei 6× (neu: F-1 und F-3, ein Vorgang zählt einmal). Den Lese-Schritt macht die Welle-Closure; hier wird nichts verkörpert. `BEO-TDS/aussagen-ueber-messung-hinaus` ist verkörpert; der falsche Marker (Aussage breiter als der Beleg im Quelltext) bekommt dort eine Evidenz-Datei.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-spec-nachzug-telemetrie.md` in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (6×) und in `BEO-TDS/aussagen-ueber-messung-hinaus`. `BEO-TDS/spec-nachzug-nach-messung-offen` nicht einschlägig (keine Messung offen, die der Nachzug nachträgt). `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`: keine weitere Evidenz (Lastenheft 0.6.1). Keine neue Beobachtung angelegt; der Kandidat „Behauptung über den gebauten Stand ungeprüft“ passt unter `aussagen-ueber-messung-hinaus` (1× in dieser Form, bei einem zweiten Auftreten als eigene Beobachtung zu prüfen).
- **Folge-Slices:** keine neuen. Folgepflichten der Verifikation: (1) `SPEC-010` „im Log sichtbar“: in diesem Slice erledigt (`8490fe7`); der Adapter-Slice entfernt den Vermerk (steht dort in §6). (2) `tools/laufzeittest.sh` (`alice|secret`): steht schon in [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md) §6 als Folgepflicht (a), unverändert. (3) Hunk `SPEC-048`: erledigt. (4) M-4: Start-Bedingung in [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) §4 (Punkt 6).
- **Risiken aus §6:** acht Risiken: zwei entfallen (Lesart des Happy Path und Offen (a), beide durch Lastenheft 0.6.1), eines eingetreten und behoben (zweite Fundstelle „bisher keines“), eines eingetreten und beidseitig vermerkt (zwei Träger; Bestätigung durch den Nutzer steht aus), vier weiter offen mit Träger (Spec vor Code, Zahlen als Setzung, (e) und (f), `reason`/M-4).
- **Gate-Läufe:** `make spec-abdeckung` am Stand `8490fe7`: 1 m 34 s (Einzelmessung), `docs/plan/spec-abdeckung.md` unverändert (12 Festlegungen ohne Nachweis, wie zuvor); `make docs-check` und `make gates` am Ende der Closure: grün (Einzelmessung, Zeit im Bericht des Planners).
- **Drei Paarungen:** Anker: kein Eintrag mit `liegt in`, nichts zu paaren. Folge-Slice: Folgepflichten stehen in den Zielslices. Register: die Dateien liegen vor.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt `spec/` ohne eigene Strukturregel oder Inventur-Linie. Keine feinere Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, am 2026-11-01). Treffer: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (3×, offen; Fundstellensuche in §3), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (10×, offen; Lesart (a) der ADR, Zuordnung zu zwei Spec-Slices), `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` (4×, offen; die Festlegungen tragen Lastenheft-Bezug in `Bezug`), `BEO-TDS/spec-nachzug-nach-messung-offen` (1×, offen; Lesart der Setzungen beim Nachzug), `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (3×, geplant; die Festlegung zu `principal` ist ein Teil davon), `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (geplant bei `slice-qa-performance`; die Warteschlange ist Setzung, nicht gemessen), `BEO-TDS/offene-punkte-ohne-traeger` (verkörpert; die Offen-Punkte (a) bis (h) der ADR haben je einen Träger), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, `AGENTS.md` §3.8). Sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
