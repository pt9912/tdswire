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

**Verantwortlich:** pt9912.

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
| 2 | [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) | 3: Export, Import, Sitzungen mit p95 |
| 3 | [slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md) | 3: Socket, blockierendes Backend, Simulations-Backend |
| 3 | [slice-qa-performance-heap-abschluss](../done/slice-qa-performance-heap-abschluss.md) | 2: Heap, Latenz des Abschlusses |
| 4 | [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md) | 2: Grenzwerte im Lastenheft, Spec-Nachzug |

Die Zeilen mit Reihenfolge 3 dürfen nacheinander laufen, jede ist nach Slice 2 startbar (Setzung: Slice 2 zuerst, damit die Streuung des Hosts an der einfacheren Messung bekannt wird).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Optimierung — der Slice misst und legt Grenzwerte fest; eine Verbesserung ist ein eigener Slice, sobald ein Grenzwert verfehlt wird.
- Die Messungen selbst (Durchsatz, Rückstau, Heap, zurückgehaltener Abschluss) — sie liegen bei den Folge-Slices der Tabelle oben; dieser Slice liefert höchstens eine Probe-Messzeile je Aufbau, damit das Werkzeug seine Trennung belegt.
- Die Grenzwerte im Lastenheft — [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md); sie brauchen die Messungen und die Bestätigung des Nutzers.
- Die Entscheidung über die offenen Fragen des Workloads (Zielwerte, Zeilenzahl, wer misst) — sie ist Eingang (Start-Trigger), nicht Lieferung dieses Slice.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Workload-Dokument:** `docs/plan/referenz-workload.md` ist vom Entwurf zum freigegebenen Stand überführt: die Antworten des Nutzers auf die offenen Fragen stehen im Dokument, jede Setzung, die eine Antwort ändert, ist angepasst, der Kopf nennt Datum und Entscheider.

  **Freigabe (Entscheidung des Nutzers, 2026-11-16):** `docs/plan/referenz-workload.md` ist am 2026-11-16 von pt9912 freigegeben; der Kopf des Dokuments nennt Datum und Entscheider. Mit ihr trägt der Haken.
- [x] **Lasttest-Werkzeug im Repo:** ein `make`-Target außerhalb von `make gates` ruft den Lasttest in Docker (Images per Digest, `--cpus`, `--memory`, fester Seed, Wiederholungen mit Median, Minimum und Maximum, Messzeile mit Datum, Host, Last und Swap); es misst Protokollschicht und Backend getrennt über die Aufbauten A (Backend allein), B (eigenes Backend ohne Arbeit) und C (Simulations-Backend) und nennt je Aufbau eine Probe-Messzeile mit der Zeilengröße auf dem Draht und der Laufzeit der Stufen; der Erzeuger der Zahlen liegt im Repo. Die Zeile des Targets steht in `harness/README.md` §Sensors.

  **Lesart „Host“ (Entscheidung des Nutzers, 2026-11-16):** der Host steht je Lauf im Kopf der Ausgabe (`lasttest: Kopf …`: Kerne, RAM, Kernel, Docker, Commit, Image-IDs, Limits), nicht in jeder Messzeile; die Messzeile trägt Datum, Last und Swap. Das Werkzeug ist dafür nicht geändert.
- [x] **E2E-Stufe:** Der Lasttest ist als Stufe im Produkt-E2E ([slice-e2e-geruest](../done/slice-e2e-geruest.md)) mit der Kennung [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) deklariert und ruft den Lauf über die Prozessgrenze (Referenzclient oder Rohbytes-Client gegen den Serverprozess), nicht über `internal`-Zugriff im Modul; `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`. Ob die Stufe den Lasttest im `make e2e`-Lauf ausführt oder nur deklariert, folgt der Antwort zur Frage O5 im Workload-Dokument und [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Sensors für das neue Target; `docs/user/` nur bei geändertem Verhalten, nicht zu erwarten).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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
| `harness/README.md` §Sensors, `README.md` | update | Zeile des neuen Targets (Werkzeug, kein Gate); Stufenzahl von `make e2e` |
| `Dockerfile` (Stages `lasttestserver`, `skript-selbsttest`), `referenztest-dotnet/Dockerfile` (Stage `lasttest`), `referenztest-dotnet/Lasttest/*.cs` | neu / update | Serverprozess und Client des Lasttests; Statistik und Prüfung der Umgebungswerte laufen im Skript-Selbsttest |
| `tools/e2e-selbsttest.sh`, `tools/mk/e2e.mk`, `tools/mk/skript-selbsttest.mk`, `tools/mk/lasttest.mk` | update / neu | Stufe 9 im Selbsttest des E2E-Skripts, Hilfetexte und Kommentare der Fragmente, Make-Fragment des Werkzeugs |
| `docs/messungen/lasttest-probe-2026-11-15.md` | neu | Probe-Messzeilen je Aufbau (Einzelmessung, kein Grenzwert) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) in `done/` (erfüllt) und die Telemetrie (`SPEC-017`..`SPEC-019`) liefert Dauer je Schicht (erfüllt, Beleg in [Referenz-Workload](../../referenz-workload.md) Abschnitt 4: Spans mit `durationMs` gebaut, auf `DEBUG`, ganze Millisekunden) **und** der Nutzer hat die offenen Fragen O1 bis O7 des Workload-Dokuments beantwortet (erfüllt am 2026-11-15, Wortlaut „01-07 Deine Empfehlungen“, festgehalten im Workload-Dokument; die Freigabe des fertigen Dokuments folgte dem ersten Liefer-Punkt und ist am 2026-11-16 erteilt, siehe §7) (Entscheidung und Lieferung getrennt: die Antworten sind Eingang, ihre Einarbeitung ist der erste Liefer-Punkt).

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

- Ohne freigegebenen Referenz-Workload sind die Grenzwerte nicht begründbar. — **Ausgang: entfallen.** [Referenz-Workload](../../referenz-workload.md) ist am 2026-11-16 von pt9912 freigegeben (Kopf des Dokuments); die Grenzwerte selbst fehlen weiter und liegen bei [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md).
- Rückstau im Produktivbetrieb, Heap und zurückgehaltener Abschluss ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)) sind bisher nur am Fake oder auf einer Maschine belegt (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`); die Messung braucht dieses Werkzeug. — **Ausgang:** weiter offen → [slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md) und [slice-qa-performance-heap-abschluss](../done/slice-qa-performance-heap-abschluss.md) (Träger der Messung, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` und `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig` im Register).
- Der Host läuft unter Last 5 bis 9 und swappt; ob `--memory-swap` und die Limits den Container von ihm trennen, ist ungemessen, und die Streuung kann Stufen unbrauchbar machen. — **Ausgang: teils belegt, Rest weiter offen.** Belegt (Einzelmessung 2026-11-15, [Messprotokoll](../../../messungen/lasttest-probe-2026-11-15.md)): `--memory-swap` gleich `--memory` wird angenommen, im Container steht `memory.swap.max` 0. Streuung: jede Zeile trägt `streuung`, `last-hoch` und `swap-gewachsen` als Vermerk statt Mittelung; im Lauf der Verifikation (Stufe 9) stand die Streuung in jeder Zeile auf `ok`, vier Zeilen von Aufbau C trugen `swap-gewachsen` (fremde Prozesse auf dem Host). **Weiter offen:** ob der Container unter Speicherdruck des Hosts dennoch ausgelagert würde (Swap des Containers ungemessen, nur sein Limit gelesen), und die Streuung über mehrere Tage und Hosts; Träger: [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) (erste Messreihe, Setzung §1: Slice 2 zuerst, damit die Streuung des Hosts an der einfacheren Messung bekannt wird) und [slice-qa-performance-heap-abschluss](../done/slice-qa-performance-heap-abschluss.md) (die Heap-Aussagen setzen auf die Angabe, nicht auf eine Messung).
- Die Laufzeit einer Stufe mit 1 Mio Zeilen und 5 Wiederholungen auf diesem Host ist ungemessen; sie bestimmt die Staffel der Folge-Slices. — **Ausgang: entfallen (gemessen, Einzelmessungen).** 1 Mio Zeilen, 5 Läufe, Aufbau C und B je mit Senke: 173 s für das Werkzeug (Bau 7 s), rund 91 s für Aufbau C und 64 s für Aufbau B (Last 4,3 bis 5,7, [Messprotokoll](../../../messungen/lasttest-probe-2026-11-15.md), Abschnitt „Größere Probe“); 100 000 Zeilen: 92 s (Bau 36 s, Messung 56 s, Last 5,8 bis 6,5); E2E-Stufe 9 (20 000 Zeilen, 3 Läufe) 56 s im Lauf der Verifikation 2026-11-16 (Bau 30 s, Messung 25 s). Die Streuung der Laufzeit ist ungemessen (je eine Zahl).
- Der Speicherwächter von `tools/lasttest.sh` prüft 4096 MiB verfügbar nur beim Start eines Containers, nicht als Laufüberwachung und nicht die Summe der Limits von 6 GiB (Server 4g, Client 2g); die Spitze des Clients ist ungemessen (gemessen: Server 101 bis 178 MB). Re-Review (F-12) und Verifikation: Einschränkung im Skript und in S8 benannt, keine Anhebung. — **Ausgang: weiter offen.** Träger: [slice-qa-performance-heap-abschluss](../done/slice-qa-performance-heap-abschluss.md) (Heap-Lauf mit 10 Mio Zeilen braucht die Spitze des Clients) und [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) (mehrere Sitzungen).
- Das Werkzeug liest `durationMs` der Spans (`SPEC-017` bis `SPEC-019`) nicht aus; die Trennung von Protokollschicht und Backend ist die Differenz der Aufbauten A, B und C, mit den im Dokument und im Messprotokoll genannten Grenzen (der Client liest mit, der Zeilenbau von A-Import und Server ist ungleich und ungemessen). „Mit Senke“ lässt die Telemetrie (`LOG_LEVEL=DEBUG`) laufen, liest sie nicht. (Verifikation F-7.) — **Ausgang: weiter offen.** Träger: [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md), erste Messreihe; ob die Spans zur Aufschlüsselung gelesen werden, entscheidet dieser Slice bei seiner Planung (Setzung des Planners, kein Eintrag im Folge-Slice nachgezogen).

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

Geschlossen 2026-11-16 (Planner, Slice der Welle `welle-qualitaet-grundlage`, die Welle bleibt offen). Geprüfter Stand: Zweig `slice-qa-performance`, Code- und Werkzeugstand `47e8e45e` (Basis `main` `321fd7b1`), danach `dd0d80c8` (Freigabe des Workload-Dokuments) und die Closure-Commits. Prüfung: Review [2026-11-16-b163a5ce](../../../reviews/2026-11-16-b163a5ce-slice-qa-performance-review.md) (F-1 bis F-12: fünf MEDIUM, fünf LOW, zwei INFO, behoben in `008bc6f4` und `77d485a6`), Re-Review [2026-11-16-77d485a6](../../../reviews/2026-11-16-77d485a6-slice-qa-performance-re-review.md) (N-1 bis N-4: ein MEDIUM, zwei LOW, ein INFO; behoben in `195d1cca`), Verifikation [2026-11-16-195d1cca](../../../reviews/2026-11-16-195d1cca-slice-qa-performance-verifikation.md) (kein HIGH, kein MEDIUM; F-1 bis F-7, behoben bzw. entschieden, siehe unten), Messprotokoll [lasttest-probe-2026-11-15](../../../messungen/lasttest-probe-2026-11-15.md), [Referenz-Workload](../../referenz-workload.md).

**Geliefert.** (1) Workload-Dokument, freigegeben am 2026-11-16 von pt9912. (2) Lasttest-Werkzeug: `make lasttest` (`tools/lasttest.sh`, Erzeuger `ReferenzZeilen.kt` und `ReferenzZeilen.cs`, Aufbauten A bis C, Median, Minimum, Maximum, Vermerke), ohne Aufnahme in `make gates`, Zeile in `harness/README.md` §Sensors. (3) E2E-Stufe 9 (`tools/e2e.sh`, Kennung [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance)) führt den Lasttest in Probengröße aus (20 000 Zeilen, 3 Läufe) und prüft keine Schwelle. Die Zahlen des Messprotokolls sind Einzelmessungen, kein Grenzwert.

**Entscheidungen des Nutzers, 2026-11-16, im Slice als solche benannt.** (1) Freigabe von `docs/plan/referenz-workload.md` durch pt9912 (Kopf des Dokuments, DoD Punkt 1). (2) Host je Lauf genügt: die DoD-Zeile „Messzeile mit Datum, Host, Last und Swap“ ist gelesen als Host im Kopf des Laufs, Datum, Last und Swap je Zeile; keine Änderung am Werkzeug (Verifikation F-3; DoD Punkt 2). (3) Stub-Fälle für die Auswertung von Aufbau A, den Vermerk in den A-Zeilen und den Zweig „Import in B nicht abgewiesen“ stehen im Selbsttest (`47e8e45e`, Verifikation F-1), je Mutation rot gesehen. (4) Setzung O5 (Stufe 9 führt aus, Probengröße, keine Schwelle) und S11 (das Werkzeug verwirft und wiederholt keinen Lauf selbst, die Entscheidung liegt beim Menschen) bestätigt; Aufbau A trägt den Import am Callback (Zielbild, keine Änderung der öffentlichen Fläche, kein ADR).

**Belege je DoD-Punkt.** 1: Kopf von `docs/plan/referenz-workload.md` (2026-11-16, pt9912). 2, 3: Verifikation §1 (Lauf `make e2e` Exit 0, 9 Stufen, Stufe 9 56 s; `make clean-build` Exit 0, fünf Fälle `LasttestTest`), Selbsttest `make skript-selbsttest` mit den Fällen aus `47e8e45e`. 4: `make gates` Exit 0 auf `dd0d80c8` (Code- und Werkzeugstand wie `47e8e45e`, seither nur Doku) in 9,5 s: die Klassen `werkzeug`, `tests` und `stempel` sind **übersprungen auf Beleg** (Stempel `werkzeug` auf `47e8e45e`, Stempel `tests` auf `77d485a6` mit demselben Eingangs-Hash `a6064bc9…`, weil `src/`, `testserver/` und die Test-Stages seither unverändert sind), die Klasse `immer` lief; ein Frischlauf der Klasse `tests` auf dem End-Stand ist `make clean-build` der Verifikation auf `195d1cca` (Exit 0), kein erneuter Lauf bei dieser Closure. 5: drei Berichte unter `docs/reviews/`. 6: `harness/README.md`, `README.md`, `tools/mk/*.mk` (Verifikation §1 Zeile 6). Offene Verifier-Punkte: F-1 erledigt (`47e8e45e`), F-2 erledigt (`awk` im Kopf des Selbsttests genannt und durch die Stub-Fälle geübt), F-3 durch Nutzerentscheidung geklärt (Lesart je Lauf), F-4 erledigt (Messprotokoll „Nicht belegt“ nachgezogen), F-5 und F-6 Hinweise ohne Handlung, F-7 als Risiko in §6 mit Träger.

**Nachbesserung ohne zweiten Review (Vermerk).** Die Nachbesserung nach dem Erst-Review (`008bc6f4`, `77d485a6`) hatte den Re-Review. Zwei Nachbesserungen mit Skript-Änderung hatten **keinen** Review in frischem Kontext: `195d1cca` (nach dem Re-Review: Prüfung der Umgebungswerte, `tools/lasttest.sh` und Selbsttest, Gate-Beschreibung) und `47e8e45e` (nach der Verifikation: Stub-Fälle im Selbsttest, `LASTTEST_PROC` in `tools/lasttest.sh`). Stattdessen geprüft: `195d1cca` durch die Verifikation in frischem Kontext (kein Review: andere Frage, andere Prüfliste), `47e8e45e` durch je eine rot gesehene Mutation je Fall und `make gates` grün; keine Prüfung durch einen dritten Reviewer. Die Änderung an `tools/lasttest.sh` in `47e8e45e` ist die Umleitung der Lesestellen von `/proc` auf `LASTTEST_PROC` (Standard `/proc`).

**Risiken (Ausgänge in §6).** Entfallen: Workload ohne Freigabe; Laufzeit der Hauptstufe (gemessen). Teils belegt, Rest weiter offen mit Träger: Host, Swap und Streuung ([slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md), [slice-qa-performance-heap-abschluss](../done/slice-qa-performance-heap-abschluss.md)). Weiter offen mit Träger: Rückstau, Heap und zurückgehaltener Abschluss ([slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md), [slice-qa-performance-heap-abschluss](../done/slice-qa-performance-heap-abschluss.md)); Wächter nur beim Start eines Containers, Summe von 6 GiB ungeprüft, Spitze des Clients ungemessen (heap-abschluss, durchsatz-sitzungen); Spans nicht ausgelesen (durchsatz-sitzungen).

**Register.** Fortgeschrieben (je eine Datei `evidence/slice-qa-performance.md`, kein Zähler gesetzt): `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (F-3 und N-1: die Schlussaussage widersprach der Einschränkung im selben Absatz; jetzt 44 Dateien), `BEO-TDS/aussagen-ueber-messung-hinaus` (F-2, F-3, F-5, F-7, F-8, N-1; 88), `BEO-TDS/nachbesserung-ohne-zweiten-review` (`195d1cca`, `47e8e45e`; 16), `BEO-TDS/haken-vor-verifikation-gesetzt` (F-1; 7), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (F-4; 22), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (F-9, F-11, N-4, Verifikation F-1; 26), `BEO-TDS/dod-wortlaut-ueber-der-bauart` (Verifikation F-3; 9). Gesichtet, kein neues Auftreten gezählt: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11, geplant: dieser Slice liefert das Werkzeug, keine Rückstau-Messung), `BEO-TDS/rueckstau-zusage-nur-test-backend-traeger-unvollstaendig`, `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (verkörpert: der Erzeuger der Zahlen liegt im Repo), `BEO-TDS/annahme-ueber-puffer-der-umgebung` (für die Folge-Slices), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (die Lesarten O5, S11 und Host je Lauf waren Setzungen des Slice und sind vom Nutzer bestätigt; der Plan hielt sie als Setzung benannt; kein Auftreten gezählt, Stand `offen` bleibt nach der Entscheidung des Nutzers vom 2026-11-06).

**Lese-Schritt (Register, Einträge mit 3 oder mehr Dateien in `evidence/`).** 44 Einträge gelesen. Mit Ausgang (`verkörpert`, `geplant`, `gestrichen`, gebaut, Träger entschieden): 31; die sieben oben fortgeschriebenen sind wiedergekehrt und jedes Mal von Reviewer, Re-Review oder Verifier gefangen, nicht vom Implementer. Retirement-Check („ist die Beobachtung seit der Verkörperung wieder aufgetreten?“): ja für alle sieben, die Regeln bleiben, keine Lockerung; der Schritt 20c in `.claude/commands/implement-slice.md` hat `fortschreibung-laesst-zweite-fundstelle-stehen` auch hier nicht vor dem Review gefangen (kein Beleg über seine Wirkung, `AGENTS.md` §3.8). Ohne Ausgang (`offen` oder nur teils gemessen): 13 (`dod-haengt-an-offenem-slice-ohne-rueckfuehrung`, `eingangsliste-je-stage-von-hand`, `festlegung-gilt-nur-fuer-das-clientprofil`, `geheimnis-kopien-im-speicher-nicht-gemessen`, `katalog-elemente-grenzen-der-angleichung`, `lesart-einer-nutzerentscheidung-unbestaetigt`, `live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`, `messung-am-echten-sql-server-fehlt`, `parametertyp-des-clients-passt-nicht-zur-spalte`, `schranken-test-mit-ungeprueftem-zaehler`, `spec-einzelfaelle-ohne-lastenheft-deckung`, `tds-8-0-und-pooling-nicht-gemessen`, `zusage-ohne-sensor-in-der-ci-kette`); keiner erhielt durch diesen Slice eine Datei, keiner wird hier verkörpert (Entscheidung beim Nutzer wie bei den früheren Lese-Schritten: Regel, Sensor, Träger-Slice oder Streichung). Die Zählung steht in diesem Absatz, sie ist nicht gesetzt.

**Folge-Slices:** die vier Slices der Tabelle in §1 liegen unverändert in `open/` und sind mit dieser Closure startbar (Start-Trigger „`slice-qa-performance` in `done/`“ erfüllt, der Nutzer verschiebt sie nach `next/` bzw. `in-progress/`): [slice-qa-performance-durchsatz-sitzungen](../done/slice-qa-performance-durchsatz-sitzungen.md) zuerst (Setzung §1), danach [slice-qa-performance-rueckstau](../done/slice-qa-performance-rueckstau.md) und [slice-qa-performance-heap-abschluss](../done/slice-qa-performance-heap-abschluss.md), zuletzt [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md).

**Lerneintrag (Steering Loop).**

- **Geschärft, keine neue Regel:** (i) Die Nachbesserung einer Fortschreibung sucht die Kernaussage im ganzen Absatz und im Repo, nicht nur die Stelle des Befunds: die Einschränkung in 3.6 stand, der Schlusssatz „der Server tut dasselbe“ blieb (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/aussagen-ueber-messung-hinaus`; verkörpert in `.harness/skills/reviewer.md` und `.claude/commands/implement-slice.md` Schritt 20c; Retirement-Check: die Klasse ist wieder aufgetreten, die Regel bleibt). (ii) Die Skript-Nachbesserungen nach dem Re-Review und nach der Verifikation hatten keinen dritten Review (`BEO-TDS/nachbesserung-ohne-zweiten-review`, Vermerk oben); die Regel verlangt den Vermerk, und er steht.
- **Neuer Sensor im Repo, als Behebung dieses Auftretens, ohne Regel-Eintrag und deshalb ohne Herkunfts-Anker:** `tools/lasttest-selbsttest.sh` (gehalten von `make skript-selbsttest`, Klasse `werkzeug`) prüft neben der Statistik und den Umgebungswerten jetzt die Auswertung der A-Zeilen, den Vermerk `last-hoch` und `swap-gewachsen` in allen drei A-Zeilen und den Zweig „Import in B nicht abgewiesen“, gegen einen `docker`-Stub und eine Nachbildung von `meminfo` und `loadavg`. Auslöser: die Mutation `$9` zu `$7` des Verifiers blieb grün, obwohl Review und Re-Review das Skript gelesen hatten (Review F-11 hatte die Gegenprobe ausdrücklich an den Verifier gegeben, `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`). Der Fall, dass ein Review die fehlende Gegenprobe benennt und die Verifikation sie findet, ist der vorgesehene Weg der Rollenteilung (Modul 8); er hat getragen. Der Selbsttest sieht die Spalte der Messzeile des Skripts, nicht die Zahlen eines echten Laufs.
- **Benannte Spec-Lücke, unverändert:** [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) trägt noch keine Grenzwerte für Durchsatz, p95-Latenz und gleichzeitige Sitzungen (Tabelle der Abnahmeunterlagen); sie entstehen nach den Messungen und der Entscheidung des Nutzers, Träger [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md). Kein `liegt in`, nichts verkörpert, daher kein Herkunfts-Anker.
- **Wortlaut einer DoD-Zeile gegen die Bauart:** „Messzeile mit … Host“ stand über dem Werkzeug, das den Host je Lauf trägt; erst die Verifikation legte die Frage vor (`BEO-TDS/dod-wortlaut-ueber-der-bauart`, verkörpert in `.claude/commands/plan-welle.md` Schritt 6b). Für künftige Pläne mit Messzeilen: je Feld sagen, ob es je Zeile oder je Lauf steht.
- **Nicht gemessen, benannt:** `make gates` hat bei dieser Closure keine Klasse frisch gefahren (Belege, siehe DoD Punkt 4); die Spitze des Clients; die Streuung über Tage und Hosts; der Swap des Containers unter Speicherdruck; die Zahlen eines Laufs mit dem Stub-Fall-Selbsttest (er prüft die Auswertung, nicht die Messung).

**Drei Paarungen.** Anker: keine Regel mit `liegt in` verkörpert, daher nichts zu paaren. Folge-Slice: die vier Slices aus §1 sind Dateien in `open/` und tragen die Risiken oben. Register: sieben `evidence/slice-qa-performance.md` unter `../observations/BEO-TDS/`, Link auf diese Datei in `done/`.

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
