# Slice slice-qa-performance-durchsatz-sitzungen: Performance: Durchsatz, p95-Latenz und gleichzeitige Sitzungen am Referenz-Workload

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

**Berührte Spec-Stellen:** `SPEC-017` · `SPEC-018` · `SPEC-019` · `SPEC-034` (Telemetrie als Quelle der Dauer je Schicht, `MAX_CONNECTIONS` als obere Grenze der Staffel)

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-11-15.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der freigegebene [Referenz-Workload](../../referenz-workload.md) läuft mit dem Werkzeug aus [slice-qa-performance](../done/slice-qa-performance.md) in den Aufbauten B (Protokollschicht, Export) und C (Gesamt, Export und Import): Zeilen/s je Richtung, p95 der Punktabfrage und die Staffel gleichzeitiger Sitzungen sind als Messzeilen mit Streuung und Bedingungen festgehalten.

**Übernimmt:** aus dem DoD-Punkt „Lasttest“ von [slice-qa-performance](../done/slice-qa-performance.md) (Fassung vor der Zerlegung vom 2026-11-15) den Teil Durchsatz, p95-Latenz und gleichzeitige Sitzungen; der Rest liegt bei [slice-qa-performance-rueckstau](../open/slice-qa-performance-rueckstau.md), [slice-qa-performance-heap-abschluss](../open/slice-qa-performance-heap-abschluss.md) und [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Rückstau am Socket, blockierendes Backend und Simulations-Backend unter Last — liegen bei [slice-qa-performance-rueckstau](../open/slice-qa-performance-rueckstau.md); die Staffel hier misst Durchsatz, keinen Rückstau.
- Heap beim Strom großer Ergebnisse und Latenz des zurückgehaltenen Abschlusses — liegen bei [slice-qa-performance-heap-abschluss](../open/slice-qa-performance-heap-abschluss.md) (anderer Aufbau, andere Zeilenzahl).
- Grenzwerte im Lastenheft — liegen bei [slice-qa-performance-grenzwerte](../open/slice-qa-performance-grenzwerte.md); dieser Slice liefert Messzeilen, keine Zahl, die schon Grenzwert ist.
- Import in ein zählendes eigenes Backend (Aufbau B) — setzt die öffentliche Fläche für das Massenladen eigener Backends voraus und liegt bei dem Slice zur Fläche nach [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) oder einem Nachmess-Slice; hier gelten für den Import nur Aufbau C und die Staffel.
- Optimierung bei einer verfehlten oder schlechten Zahl — ein Slice je Befund nach der Messung (Welle §6).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Export:** Zeilen/s, erste Zeile und Dauer je Schicht (`tds.query`, `backend.execute`) für `SELECT * FROM ref_zeilen` in den Aufbauten B und C, je Stufe der Staffel, mit Median, Minimum und Maximum der Wiederholungen und den Bedingungen (Datum, Host, Last, Limits).
- [ ] **Import:** Zeilen/s für `SqlBulkCopy` (`BatchSize` 1000) in das Simulations-Backend mit `onInsertBatch` (Aufbau C), je Stufe der Staffel, mit Streuung und Bedingungen. Das Ziel ist ein Zähler; der Bestand des Testservers mit seiner Grenze von 2 097 152 Zellen ist **nicht** das Ziel.
- **Ausdrücklich offen, nicht Teil dieses Slice (Nutzerentscheidung 2026-11-17 „DoD teilen, B später“):** der Import in ein zählendes eigenes Backend (Aufbau B). Er wird Liefer-Punkt des Slice zur Fläche nach [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) oder eines Nachmess-Slice; bis dahin ist der Aufbau B im Import nicht gemessen.
- [ ] **Sitzungen und p95:** p50, p95 und p99 der Punktabfrage (aus der Messung des Clients) während die übrigen Sitzungen streamen oder laden, für die Staffel 1, 8, 16, 64; jede Stufe mit und ohne Senke für Telemetrie; Messprotokoll unter `docs/messungen/` nach `AGENTS.md` §5 Regel 7, jede Zahl mit Erzeuger im Repo.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `docs/plan/referenz-workload.md` (Abschnitte, die eine Messung berührt) und `harness/README.md` §Sensors, falls ein Target dazukommt; Nutzer-Doku unter `docs/user/` nur, wenn sich Verhalten, Konfiguration oder Ereignisse ändern (nicht zu erwarten, der Slice misst).
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
| Läufe des Werkzeugs aus [slice-qa-performance](../done/slice-qa-performance.md) (Stufen, Staffel, Wiederholungen) | update | Messung nach [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance), keine neue Mechanik |
| `docs/messungen/performance-durchsatz-<datum>.md` | neu | Messprotokoll, Einzelmessung mit Datum, Host, Last (`AGENTS.md` §5 Regel 7) |
| `docs/plan/referenz-workload.md` | update | Messzeilen und Zeilengröße auf dem Draht nachtragen, Setzungen, die die Messung widerlegt, ändern |
| `tools/lasttest.sh`, `tools/lasttest-selbsttest.sh` | update | das Werkzeug wächst um Sitzungen, Phasen (`export`, `import`, `punkt-export`, `punkt-import`), Punktabfrage, Dauer je Schicht aus dem Log und `LASTTEST_QUERY_TIMEOUT`; der Selbsttest hält die Auswertung (Handläufe, Mutationen) |
| `referenztest-dotnet/Lasttest/LasttestTests.cs`, `referenztest-dotnet/Lasttest/ReferenzZeilen.cs` | update | Client mit N Sitzungen und der Punktabfrage; die Prüfung, dass die Punktabfrage genau die Zeile der Id liefert, läuft in keinem Gate, nur in `make lasttest` und Stufe 9 von `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/LasttestBackends.kt`, `testserver/test/kotlin/io/github/pt9912/tdswire/testserver/LasttestTest.kt` | update | Punktabfrage `WHERE Id = @id` in Aufbau B und C, Tests |
| `harness/README.md` | update | Zeile `make lasttest` (Parameter der Staffel) |
| `docs/plan/adr/` ([ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) und Index) | nicht dieser Slice | vom Architect auf demselben Zweig angelegt (Nutzerentscheidung zum Import in Aufbau B) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-performance](../done/slice-qa-performance.md) in `done/` (Werkzeug, Aufbauten A bis C, freigegebener Workload).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Das Werkzeug aus [slice-qa-performance](../done/slice-qa-performance.md) liefert für eine Stufe keine wiederholbare Zahl (die Streuung überschreitet die Schwelle der Setzung S11 auf diesem Host, auch nach Verwerfen der Läufe unter Swap); dann steht die Stufe als „nicht belegt“ mit der Streuung im Protokoll, und ein Carveout oder ein Folge-Slice trägt die Umgebung (fester Runner, offene Frage O6 im Workload).

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, das Messprotokoll liegt unter `docs/messungen/` und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Importzahl gegen ein zählendes Backend trägt nicht für ein Backend, das Zeilen speichert; der Bestand des Testservers ist mit 2 097 152 Zellen begrenzt (`BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt`). — **Ausgang:** weiter offen: der Import in Aufbau B ist aus diesem Slice herausgenommen (Nutzerentscheidung 2026-11-17) und liegt beim Slice zur Fläche nach [ADR-0052](../../adr/0052-oeffentliche-flaeche-fuer-massenladen-eigener-backends.md) oder einem Nachmess-Slice; das Register führt die Beobachtung weiter.
- Der Host läuft unter Last 5 bis 9 und swappt; die Streuung kann jede Stufe unbrauchbar machen, ohne dass das Werkzeug fehlerhaft ist. — **Ausgang:** offen bis zur Closure.
- Die Kosten der Telemetrie selbst sind ungemessen (Abschnitt 5 der Spezifikation); die Messung „mit und ohne Senke“ trennt sie nur für die Stufen, die sie fährt. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (11 Dateien, Träger dieser Zerlegung; der Teil „gleichzeitige Sitzungen“ aus `slice-transport-listener-lebenszyklus` liegt hier), `BEO-TDS/testserver-bestand-nicht-am-heap-begrenzt` (1 Datei, Risiko oben) und `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (Erzeuger im Repo, Regel 6d des Plan-Commands) sind Treffer; die Zahl der Dateien unter `evidence/` ist der Zähler, hier nicht gesetzt.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
