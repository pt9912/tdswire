# Slice slice-qa-performance: Performance: Referenz-Workload festlegen und Lasttest-Werkzeug

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** `SPEC-017` · `SPEC-018` · `SPEC-019` (Dauer je Schicht als Quelle der getrennten Messung)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-29; zerlegt am 2026-11-15 (Zerlegung siehe §1).

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein Referenz-Workload ist festgelegt und freigegeben ([Referenz-Workload](../../referenz-workload.md), Form aus d-migrate als Verbraucher), und ein reproduzierbares Werkzeug im Repo misst Protokollschicht und Backend getrennt; die Messungen und die Grenzwerte für Durchsatz, p95-Latenz und gleichzeitige Sitzungen liefern die Folge-Slices.

**Zerlegung (Planner, 2026-11-15).** Die Fassung vom 2026-10-29 trug in einem Liefer-Punkt Workload, Werkzeug, fünf Messungen (Socket mit mehreren Verbindungen, blockierendes Backend, Simulations-Backend, Heap, Latenz des Abschlusses), die E2E-Stufe und in einem weiteren die Grenzwerte: mehr als drei Liefer-Punkte, wie §4 vorab als Rückführung „zu groß“ benennt. Der Schnitt nach Lieferwert und Eingang (Modul 5; dieselbe Form wie bei [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md), das einen Teil behielt und die Messreihe in einen Folge-Slice gab): dieser Slice behält Workload, Werkzeug und E2E-Stufe und bleibt unter seiner Kennung in `open/`, weil [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), die Welle und zwei Beobachtungen auf diese Kennung zeigen und er einen Teil des Gegenstands selbst liefert (die Regel „Gegenstand übernimmt ein anderer“ führte ihn nur nach `done/`, wenn er nichts mehr lieferte).

| Reihenfolge | Slice | Liefer-Punkte |
|---|---|---|
| 1 | `slice-qa-performance` (dieser) | 3: Workload-Dokument, Werkzeug mit Aufbauten A bis C, E2E-Stufe |
| 2 | [slice-qa-performance-durchsatz-sitzungen](slice-qa-performance-durchsatz-sitzungen.md) | 3: Export, Import, Sitzungen mit p95 |
| 3 | [slice-qa-performance-rueckstau](slice-qa-performance-rueckstau.md) | 3: Socket, blockierendes Backend, Simulations-Backend |
| 3 | [slice-qa-performance-heap-abschluss](slice-qa-performance-heap-abschluss.md) | 2: Heap, Latenz des Abschlusses |
| 4 | [slice-qa-performance-grenzwerte](slice-qa-performance-grenzwerte.md) | 2: Grenzwerte im Lastenheft, Spec-Nachzug |

Die Zeilen mit Reihenfolge 3 dürfen nacheinander laufen, jede ist nach Slice 2 startbar (Setzung: Slice 2 zuerst, damit die Streuung des Hosts an der einfacheren Messung bekannt wird).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Optimierung — der Slice misst und legt Grenzwerte fest; eine Verbesserung ist ein eigener Slice, sobald ein Grenzwert verfehlt wird.
- Die Messungen selbst (Durchsatz, Rückstau, Heap, zurückgehaltener Abschluss) — sie liegen bei den Folge-Slices der Tabelle oben; dieser Slice liefert höchstens eine Probe-Messzeile je Aufbau, damit das Werkzeug seine Trennung belegt.
- Die Grenzwerte im Lastenheft — [slice-qa-performance-grenzwerte](slice-qa-performance-grenzwerte.md); sie brauchen die Messungen und die Bestätigung des Nutzers.
- Die Entscheidung über die offenen Fragen des Workloads (Zielwerte, Zeilenzahl, wer misst) — sie ist Eingang (Start-Trigger), nicht Lieferung dieses Slice.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Workload-Dokument:** `docs/plan/referenz-workload.md` ist vom Entwurf zum freigegebenen Stand überführt: die Antworten des Nutzers auf die offenen Fragen stehen im Dokument, jede Setzung, die eine Antwort ändert, ist angepasst, der Kopf nennt Datum und Entscheider.
- [ ] **Lasttest-Werkzeug im Repo:** ein `make`-Target außerhalb von `make gates` ruft den Lasttest in Docker (Images per Digest, `--cpus`, `--memory`, fester Seed, Wiederholungen mit Median, Minimum und Maximum, Messzeile mit Datum, Host, Last und Swap); es misst Protokollschicht und Backend getrennt über die Aufbauten A (Backend allein), B (eigenes Backend ohne Arbeit) und C (Simulations-Backend) und nennt je Aufbau eine Probe-Messzeile mit der Zeilengröße auf dem Draht und der Laufzeit der Stufen; der Erzeuger der Zahlen liegt im Repo. Die Zeile des Targets steht in `harness/README.md` §Sensors.
- [ ] **E2E-Stufe:** Der Lasttest ist als Stufe im Produkt-E2E ([slice-e2e-geruest](../done/slice-e2e-geruest.md)) mit der Kennung [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) deklariert und ruft den Lauf über die Prozessgrenze (Referenzclient oder Rohbytes-Client gegen den Serverprozess), nicht über `internal`-Zugriff im Modul; `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`. Ob die Stufe den Lasttest im `make e2e`-Lauf ausführt oder nur deklariert, folgt der Antwort zur Frage O5 im Workload-Dokument und [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Sensors für das neue Target; `docs/user/` nur bei geändertem Verhalten, nicht zu erwarten).
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
| `docs/plan/referenz-workload.md` | update | Entwurf vom 2026-11-15 auf den freigegebenen Stand bringen (Antworten des Nutzers) |
| Lasttest-Werkzeug (Skript und Make-Fragment unter `tools/`, Erzeuger der Zeilen und messende Backends im Testserver-Modul) | neu | Messung nach [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance); Aufbauten A bis C, Erzeuger im Repo |
| `tools/e2e.sh` und `docs/plan/e2e-abdeckung.md` | update | Stufe mit der Kennung [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) deklarieren, `make e2e-abdeckung` erzeugt die Sicht |
| `harness/README.md` §Sensors | update | Zeile des neuen Targets (Werkzeug, kein Gate) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) in `done/` (erfüllt) und die Telemetrie (`SPEC-017`..`SPEC-019`) liefert Dauer je Schicht (erfüllt, Beleg in [Referenz-Workload](../../referenz-workload.md) Abschnitt 4: Spans mit `durationMs` gebaut, auf `DEBUG`, ganze Millisekunden) **und** der Nutzer hat die offenen Fragen O1 bis O7 des Workload-Dokuments beantwortet (erfüllt am 2026-11-15, Wortlaut „01-07 Deine Empfehlungen“, festgehalten im Workload-Dokument; die Freigabe des fertigen Dokuments ist nicht erteilt, sie folgt dem ersten Liefer-Punkt) (Entscheidung und Lieferung getrennt: die Antworten sind Eingang, ihre Einarbeitung ist der erste Liefer-Punkt).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Workload liegt nicht fest (eine der Fragen O1 bis O7 ist unbeantwortet, oder eine Antwort widerspricht einer anderen).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Ohne freigegebenen Referenz-Workload sind die Grenzwerte nicht begründbar. — **Ausgang:** offen bis zur Closure.
- Rückstau im Produktivbetrieb, Heap und zurückgehaltener Abschluss ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)) sind bisher nur am Fake oder auf einer Maschine belegt (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`); die Messung braucht dieses Werkzeug. — **Ausgang:** weiter offen → [slice-qa-performance-rueckstau](slice-qa-performance-rueckstau.md) und [slice-qa-performance-heap-abschluss](slice-qa-performance-heap-abschluss.md) (Träger der Messung, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` und `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig` im Register).
- Der Host läuft unter Last 5 bis 9 und swappt; ob `--memory-swap` und die Limits den Container von ihm trennen, ist ungemessen, und die Streuung kann Stufen unbrauchbar machen. — **Ausgang:** offen bis zur Closure.
- Die Laufzeit einer Stufe mit 1 Mio Zeilen und 5 Wiederholungen auf diesem Host ist ungemessen; sie bestimmt die Staffel der Folge-Slices. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` gilt für das ganze Repo (`*`, Greenfield); der Slice berührt `*`, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Stand 2026-11-15): Treffer sind `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11 Dateien unter `evidence/`, die Schwelle von 3× ist überschritten; Träger der Messung sind die Folge-Slices aus §1), `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig` (1 Datei, Träger ebenso), `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (4 Dateien, verkörpert: der Erzeuger der Zahlen liegt im Repo, siehe DoD) und `BEO-TDS/annahme-ueber-puffer-der-umgebung` (2 Dateien, für die Folge-Slices).

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
